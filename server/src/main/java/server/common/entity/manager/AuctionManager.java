package server.common.entity.manager;

import server.common.entity.AutoBidEngine;
import server.common.entity.Auction;
import server.common.entity.AutoBidConfig;
import server.common.entity.Item;
import server.common.entity.User;
import server.common.entity.exception.AuctionStateException;
import server.common.entity.exception.AutoBidConfigException;
import server.common.entity.exception.BidderException;
import server.common.enums.UserStatus;
import server.service.listeners.RealTimeObserver;
import server.common.enums.AuctionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/*
    AuctionManager — Singleton điều phối toàn bộ Auction.
    Lý do: nếu có 2 instance → auctionMap bị phân mảnh → mỗi instance chỉ biết về một nửa số phiên đấu giá → data inconsistency.

    1. Registry toàn bộ Auction (auctionMap)
    2. Hỗ trợ chuyển trạng thái OPEN → RUNNING → FINISHED theo thời gian (sử dụng ScheduledExecutorService)
    3. Điều phối AutoBidEngine hỗ trợ Auto-Bidding
    4. Quản lý danh sách Users trong memory hỗ trợ Notification
    5. Cung cấp các query method cho UI và Server layer
    => Điều khiển logic Auction, không cho client tham chiếu tới Auction (phải thông qua AuctionManager)

    DB (tầng Service/DAO gọi AuctionManager):
        - Khi khởi động server: loadFromDB() → load auctions, users từ DB vào memory
        - Sau mỗi createAuction(): AuctionDAO.insert(auction)
        - Sau mỗi bid: BidDAO.insert(tx) + AuctionDAO.updateState(auction)
        - Sau closeSession(): AuctionDAO.updateStatus(auction)

    Kết nối UI / Server layer:
        - Server gọi getInstance() để lấy manager
        - Server gọi placeBid() → manager gọi auction.placeBid() + trigger AutoBidEngine
        - UI gọi getAuctionsByStatus() để hiển thị danh sách Auctions
        - UI gọi searchAuctions() để tìm kiếm Auction
        - UI đăng ký observer qua addGlobalObserver() hoặc per-auction addObserver()
 */
public class AuctionManager {
    // ─────────────────────────────────────────────────────────────────────────
    //  Singleton
    // ─────────────────────────────────────────────────────────────────────────
    private static volatile AuctionManager instance;
    /**
        Double-checked locking Singleton — thread-safe, lazy initialization.
        volatile đảm bảo visibility trên đa CPU core.
     */
    private AuctionManager() {}
    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) {
                    instance = new AuctionManager();
                }
            }
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Data stores
    // ─────────────────────────────────────────────────────────────────────────

    /** Save User theo pair <AuctionID, Auction> */
    private final Map<Integer, Auction> auctionMap
        = new ConcurrentHashMap<>();

    /** Save User theo pair <UserID, User> */
    private final Map<Integer, User> userMap
        = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Tools
    // ─────────────────────────────────────────────────────────────────────────

    /** AutoBidEngine xử lý auto-bid */
    private final AutoBidEngine autoBidEngine = new AutoBidEngine();

    /** Quản lý các Token tác vụ đóng phiên đấu giá thời gian thực để hỗ trợ hủy/lập lịch lại */
    private final Map<String, ScheduledFuture<?>> endAuctionTasks = new ConcurrentHashMap<>();

    /**
     * ScheduledExecutorService quản lý timer tự động đóng/mở phiên.
     * Dùng ScheduledThreadPool :
     *  - ScheduledThreadPool dùng nhiều thread → các phiên không block nhau
     *  - ScheduledThreadPool xử lý exception tốt hơn
     */
    private final ScheduledExecutorService scheduler
        = Executors.newScheduledThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        r -> {
            Thread t = new Thread(r, "AuctionScheduler");
            t.setDaemon(true); // thread daemon → tự tắt khi JVM tắt
            return t;
        }
    );

    /**
     * Service layer callbacks — inject sau khi khởi tạo để tránh circular dependency.
     * AuctionService gọi setOnAuctionStartedCallback(this::onAuctionStarted)
     * và setOnAuctionClosedCallback(this::onAuctionClosed) trong constructor.
     */
    private volatile Consumer<Auction> onAuctionStartedCallback;
    private volatile Consumer<Auction> onAuctionClosedCallback;
    public void setOnAuctionStartedCallback(Consumer<Auction> callback) {
        this.onAuctionStartedCallback = callback;
    }
    public void setOnAuctionClosedCallback(Consumer<Auction> callback) {
        this.onAuctionClosedCallback = callback;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Observer Management
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Global observers — nhận sự kiện từ MỌI phiên đấu giá.
     */
    private final List<RealTimeObserver> globalObservers
        = new CopyOnWriteArrayList<>();

    /**
     * Thêm global observer — nhận event từ TẤT CẢ phiên.
     */
    public void addGlobalObserver(RealTimeObserver observer) {
        globalObservers.add(observer);
    }
    /**
     * Xóa global observer ra khỏi list.
     */
    public void removeGlobalObserver(RealTimeObserver observer) {
        globalObservers.remove(observer);
        auctionMap.values().forEach(a -> a.removeObserver(observer));
    }

    /**
     * Thêm observer cho một phiên cụ thể.
     */
    public void addObserverToAuction(int auctionId, RealTimeObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) auction.addObserver(observer);
    }

    /**
     * Xóa observer cho một phiên cụ thể.
     */
    public void removeObserverFromAuction(int auctionId, RealTimeObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) auction.removeObserver(observer);
    }


    public AutoBidEngine getAutoBidEngine() { return autoBidEngine; }
    // ─────────────────────────────────────────────────────────────────────────
    //  User Management

    /** Đăng ký người dùng */
    public void registerUser(User user) {
        userMap.put(user.getId(), user);
    }
    /** Đăng ký người dùng
     * @return user/null
     */
    public User registerOrGetUser(User user) {
        return userMap.computeIfAbsent(
            user.getId(),
            id -> user
        );
    }

    /** Tìm kiếm người dùng bằng ID */
    public Optional<User> findUserById(int userId) {
        return Optional.ofNullable(userMap.get(userId));
    }
//
//    public User findUserById(int userId){
//        return userMap.get(userId);
//    }

    public Optional<User> findUserByUsername(String username) {
        return userMap.values().stream()
            .filter(u -> u.getUsername().equals(username))
            .findFirst();
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userMap.values());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Auction CRUD

    /**
     * Tạo mới phiên đấu giá và lên lịch tự động mở/đóng.
     * @return Auction trên RAM vừa được tạo
     *      Không tự tạo mới Entity trên RAM , nên dùng loadAuction để đồng bộ với DB
     */
    public Auction createAuction(Item item, int sellerId,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 BigDecimal minBidIncrement, BigDecimal reservePrice,
                                 int snipeWindowSeconds, int snipeExtensionSeconds) {
        Auction auction = new Auction(
            item, sellerId, startTime, endTime,
            minBidIncrement, reservePrice,
            snipeWindowSeconds, snipeExtensionSeconds
        );

        // Đăng ký global observers vào phiên mới
        globalObservers.forEach(auction::addObserver);// Auction add từng Observers vào

        auctionMap.put(auction.getId(), auction);

        // Lên lịch tự động
        scheduleOpen(auction);
        scheduleClose(auction);

        System.out.printf("[AuctionManager] Created auction %s for '%s'. Start: %s, End: %s%n",
            auction.getId(), item.getName(), startTime, endTime);

        return auction;
    }

    /**
     * Load auction đã có ID thật từ DB vào memory.
     *
     * <p>Dùng khi server khởi động hoặc ngay sau khi admin tạo auction qua service.
     * Auction {@code OPEN} được lên lịch cả mở và đóng; auction {@code RUNNING}
     * chỉ cần lên lịch đóng lại theo {@code endTime}.</p>
     *
     * @param auction auction đã được persist hoặc reload từ DB; bỏ qua nếu {@code null}
     */
    public void loadAuction(Auction auction) {
        if (auction == null) {
            return;
        }

        globalObservers.forEach(auction::addObserver);
        auctionMap.put(auction.getId(), auction);

        if (auction.getStatus() == AuctionStatus.OPEN) {
            scheduleOpen(auction);
            scheduleClose(auction);
        } else if (auction.getStatus() == AuctionStatus.RUNNING) {
            scheduleClose(auction);
        }
    }

    public Optional<Auction> getAuction(int auctionId) {
        return Optional.ofNullable(auctionMap.get(auctionId));
    }

    public List<Auction> getAllAuctions() {
        return new ArrayList<>(auctionMap.values());
    }

    /**
     * Lấy danh sách auction theo status.
     * ⚠️  Kết nối UI: UI gọi method này để render danh sách phiên đấu giá.
     */
    public List<Auction> getAuctionsByStatus(AuctionStatus status) {
        return auctionMap.values().stream()
            .filter(a -> a.getStatus() == status)
            .collect(Collectors.toList());
    }

    /**
     * Tìm kiếm auction theo tên item (case-insensitive).
     * ⚠️  Kết nối UI: dùng cho SearchBar trên màn hình danh sách auction.
     */
    public List<Auction> searchAuctions(String keyword) {
        String lower = keyword.toLowerCase();
        return auctionMap.values().stream()
            .filter(a -> a.getItem().getName().toLowerCase().contains(lower)
                      || a.getItem().getCategory().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    /**
     * Lấy các auction mà một bidder đang tham gia (đã bid ít nhất 1 lần).
     * ⚠️  Kết nối UI: dùng cho màn hình "My Bids" của Bidder.
     */
    public List<Auction> getAuctionsForBidder(int bidderId) {
        return auctionMap.values().stream()
            .filter(a -> a.getBidHistory().stream()
                .anyMatch(tx -> tx.getBidderId()== bidderId))
            .collect(Collectors.toList());
    }

    /**
     * Lấy các auction của một seller.
     * ⚠️  Kết nối UI: dùng cho màn hình "My Listings" của Seller.
     */
    public List<Auction> getAuctionsForSeller(int sellerId) {
        return auctionMap.values().stream()
            .filter(a -> a.getSellerId() == sellerId)
            .collect(Collectors.toList());
    }

    /** Lấy Auction Object bằng ID */
    private Auction getAuctionByID(int auctionId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        return auction;
    }

    public int getActiveAuctionCount() {
        return (int) auctionMap.values().stream().filter(Auction::isRunning).count();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Auto-Bid operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký auto-bid cho một bidder trong một phiên.
     */
    public void registerAutoBid(AutoBidConfig config, User bidder) {
        Auction auction = getAuctionByID(config.getAuctionId());

        if (!auction.isRunning()) {
            throw new AuctionStateException(auction.getId(),auction.getStatus());
        }
        if (bidder.getStatus() != UserStatus.ACTIVE){
            throw new BidderException(bidder.getId(), bidder.getStatus());
        }
        BigDecimal currentPrice = auction.getCurrentPrice();
        BigDecimal newMaxBid = config.getMaxBid();
        BigDecimal increment = config.getIncrement();

        if (newMaxBid.compareTo(currentPrice.add(increment)) < 0) {
            throw new AutoBidConfigException(
                "Max bid must be at least current price. Current: " + currentPrice
                    + ", Requested: " + newMaxBid,newMaxBid,increment,currentPrice);
        }

        bidder.setAutoBid(config);
        autoBidEngine.register(config, bidder);

        System.out.printf("[AuctionManager] AutoBid registered: %s on auction %s (max=%.0f, inc=%.0f)%n",
            bidder.getUsername(), config.getAuctionId(),
            config.getMaxBid(), config.getIncrement());
    }

    /**
     * Hủy auto-bid của một bidder trong một phiên.
     */
    public void cancelAutoBid(int auctionId, User bidder) {
        Auction auction = getAuctionByID(auctionId);
        if (!auction.isRunning()){
            bidder.cancelAutoBid(auctionId);
            autoBidEngine.unregister(auctionId, bidder.getId());
            throw new AuctionStateException(auction.getId(),auction.getStatus());
        }
        bidder.cancelAutoBid(auctionId);
        autoBidEngine.unregister(auctionId, bidder.getId());

        System.out.printf("[AuctionManager] AutoBid canceled: %s on auction %s%n",
            bidder.getUsername(), auctionId);

    }



    // ─────────────────────────────────────────────────────────────────────────
    //  Scheduler — tự động chuyển trạng thái theo thời gian
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lên lịch tự động mở phiên khi đến startTime.
     * Sau khi OPEN → RUNNING, fire onAuctionStartedCallback để Service persist DB và notify.
     */
    private void scheduleOpen(Auction auction) {
        long delaySeconds = ChronoUnit.SECONDS.between(
            LocalDateTime.now(), auction.getStartTime()
        );
        if (delaySeconds < 0) delaySeconds = 0; // startTime đã qua → mở ngay

        scheduler.schedule(() -> {
            try {
                if (auction.getStatus() == AuctionStatus.OPEN) {
                    auction.startRunning();
                    // Notify Service để persist RUNNING và gửi thông báo
                    if (onAuctionStartedCallback != null) {
                        onAuctionStartedCallback.accept(auction);
                    }
                    System.out.printf("[Scheduler] Auction %s OPENED%n",
                        auction.getId());
                }
            } catch (Exception e) {
                System.err.println("[Scheduler] Error opening auction: " + e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);


    }

    /**
     * Lên lịch tự động đóng phiên khi đến endTime.
     * Lưu ý: endTime có thể bị gia hạn bởi anti-sniping.
     * ScheduledTask sẽ check lại thực tế khi chạy — nếu endTime đã đổi
     * thì tự lên lịch lại (reschedule).
     */
    private void scheduleClose(Auction auction) {
        long delaySeconds = ChronoUnit.SECONDS.between(
            LocalDateTime.now(), auction.getEndTime()
        );
        if (delaySeconds < 0) delaySeconds = 0;

        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    // Kiểm tra xem endTime có bị gia hạn không
                    long remaining = ChronoUnit.SECONDS.between(
                        LocalDateTime.now(), auction.getEndTime()
                    );

                    if (remaining > 1) {
                        // endTime đã bị gia hạn → lên lịch lại
                        scheduler.schedule(this, remaining, TimeUnit.SECONDS);
                        return;
                    }

                    if (auction.getStatus() == AuctionStatus.RUNNING) {
                        auction.closeSession();
                        autoBidEngine.cleanupAutoBids(auction.getId());

                        // Notify service để persist DB
                        if (onAuctionClosedCallback != null) {
                            onAuctionClosedCallback.accept(auction);
                        }
                        System.out.printf("[Scheduler] Auction %s CLOSED. Winner: %s, Price: %.2f%n",
                            auction.getId(),
                            auction.getCurrentLeader() != null
                                ? auction.getCurrentLeader().getUsername() : "none",
                            auction.getCurrentPrice());
                    }
                } catch (Exception e) {
                    System.err.println("[Scheduler] Error closing auction: " + e.getMessage());
                }
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Cập nhật lại lịch đóng phiên khi endTime bị gia hạn (anti-snipe).
     * Gọi bởi AuctionService sau khi auction.checkAndResetExtensionFlag() trả true.
     */
    public void rescheduleClose(Auction auction) {
        scheduleClose(auction);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dừng tất cả scheduled tasks khi tắt server.
     * Gọi trong shutdown hook của server:
     *   Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown));
     */
    public void shutdown() {
        System.out.println("[AuctionManager] Shutting down scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS))
                scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin Control
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Admin force-close một phiên đấu giá.
     * Sau khi forceCancel(), fire onAuctionClosedCallback để Service xử lý DB và notify.
     * Caller (AdminService) KHÔNG cần gọi onAuctionClosed() thủ công nữa.
     */
    public void forceCloseAuction(int auctionId, String reason) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        auction.forceCancel(reason);
        autoBidEngine.cleanupAutoBids(auctionId);
        // Fire callback — AuctionService sẽ xử lý DB
        if (onAuctionClosedCallback != null) {
            onAuctionClosedCallback.accept(auction);
        }
    }

}
