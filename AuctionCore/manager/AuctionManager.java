package manager;

import engine.AutoBidEngine;
import enums.AuctionStatus;
import model.Auction;
import model.AutoBidConfig;
import model.BidTransaction;
import model.item.Item;
import model.user.Bidder;
import model.user.Seller;
import model.user.User;
import observer.AuctionObserver;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/*
    AuctionManager — Singleton điều phối toàn bộ hệ thống.

    Lý do: nếu có 2 instance → auctionMap bị phân mảnh → mỗi instance chỉ biết về một nửa số phiên đấu giá → data inconsistency.

    1. Registry toàn bộ Auction (auctionMap)
    2. Tự động chuyển trạng thái OPEN → RUNNING → FINISHED theo thời gian (sử dụng ScheduledExecutorService)
    3. Điều phối AutoBidEngine sau mỗi bid thành công
    4. Quản lý danh sách Users trong memory
    5. Cung cấp các query method cho UI và Server layer

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

    private static volatile AuctionManager instance;

    private AuctionManager() {}

    /*
        Double-checked locking Singleton — thread-safe, lazy initialization.
        volatile đảm bảo visibility trên đa CPU core.
     */
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

    // ── Data stores ───────────────────────────────────────────────────────────

    /** Registry <AuctionID, Auction> */
    private final Map<String, Auction> auctionMap
        = new ConcurrentHashMap<>();

    /** Registry <UserID, User> */
    private final Map<String, User> userMap
        = new ConcurrentHashMap<>();

    /** AutoBidEngine xử lý cascade auto-bid */
    private final AutoBidEngine autoBidEngine = new AutoBidEngine();

    /*
    Global observers — nhận sự kiện từ MỌI phiên đấu giá.
     * Ví dụ: NotificationService đăng ký ở đây để lưu notification vào DB.
     *
     * ⚠️  Kết nối UI/Server:
     *   ServerBroadcaster implements AuctionObserver và đăng ký ở đây
     *   để push update đến toàn bộ client đang connect.
     */
    private final List<AuctionObserver> globalObservers
        = new CopyOnWriteArrayList<>();

    /**
     * ScheduledExecutorService quản lý timer tự động đóng/mở phiên.
     *
     * Dùng ScheduledThreadPool thay vì Timer vì:
     *   - Timer dùng 1 thread → nếu task trước chạy lâu, task sau bị delay
     *   - ScheduledThreadPool dùng nhiều thread → các phiên không block nhau
     *   - ScheduledThreadPool xử lý exception tốt hơn (Timer sẽ chết nếu 1 task throw)
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

    // ─────────────────────────────────────────────────────────────────────────
    //  User management
    // ─────────────────────────────────────────────────────────────────────────

    public void registerUser(User user) {
        userMap.put(user.getId(), user);
    }

    public Optional<User> findUserById(String userId) {
        return Optional.ofNullable(userMap.get(userId));
    }

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
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo mới phiên đấu giá và lên lịch tự động mở/đóng.
     *
     * ⚠️  Sau khi gọi method này, tầng Service phải:
     *     1. AuctionDAO.insert(auction) — lưu vào DB
     *     2. ItemDAO.updateStatus(item.getId(), "IN_AUCTION")
     *
     * @return Auction vừa được tạo
     */
    public Auction createAuction(Item item, Seller seller,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 double minBidIncrement, Double reservePrice,
                                 int snipeWindowSeconds, int snipeExtensionSeconds) {
        Auction auction = new Auction(
            item, seller, startTime, endTime,
            minBidIncrement, reservePrice,
            snipeWindowSeconds, snipeExtensionSeconds
        );

        // Đăng ký global observers vào phiên mới
        globalObservers.forEach(auction::addObserver);

        auctionMap.put(auction.getId(), auction);

        // Lên lịch tự động
        scheduleOpen(auction);
        scheduleClose(auction);

        System.out.printf("[AuctionManager] Created auction %s for '%s'. Start: %s, End: %s%n",
            auction.getId().substring(0, 8), item.getName(), startTime, endTime);

        return auction;
    }

    /**
     * Load auction từ DB vào memory (dùng khi server khởi động).
     * Nếu auction đang RUNNING → lên lịch đóng theo endTime còn lại.
     */
    public void loadAuction(Auction auction) {
        globalObservers.forEach(auction::addObserver);
        auctionMap.put(auction.getId(), auction);

        // Nếu đang RUNNING → lên lịch đóng lại
        if (auction.getStatus() == AuctionStatus.RUNNING) {
            scheduleClose(auction);
        }
    }

    public Optional<Auction> getAuction(String auctionId) {
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
    public List<Auction> getAuctionsForBidder(String bidderId) {
        return auctionMap.values().stream()
            .filter(a -> a.getBidHistory().stream()
                .anyMatch(tx -> tx.getBidderId().equals(bidderId)))
            .collect(Collectors.toList());
    }

    /**
     * Lấy các auction của một seller.
     * ⚠️  Kết nối UI: dùng cho màn hình "My Listings" của Seller.
     */
    public List<Auction> getAuctionsForSeller(String sellerId) {
        return auctionMap.values().stream()
            .filter(a -> a.getSeller().getId().equals(sellerId))
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bid operations — điểm entry cho server layer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Entry point cho mọi yêu cầu đặt giá từ client.
     *
     * Flow:
     *   1. Tìm auction
     *   2. Gọi auction.placeBid() → xử lý trong vùng lock
     *   3. Trigger AutoBidEngine → cascade
     *   4. (Tầng DAO bên ngoài sẽ persist tx vào DB)
     *
     * @return BidTransaction của bid thủ công vừa thực hiện
     *         (các auto-bid cascade sẽ được notify qua Observer)
     */
    public BidTransaction placeBid(String auctionId, Bidder bidder, double amount) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + auctionId);

        // Đặt giá thủ công
        BidTransaction tx = auction.placeBid(bidder, amount, false);

        // Trigger auto-bid cascade (chạy sau khi lock đã release)
        List<BidTransaction> autoBids = autoBidEngine.trigger(auction, bidder);
        if (!autoBids.isEmpty()) {
            System.out.printf("[AuctionManager] Auto-bid cascade: %d auto-bids triggered%n",
                autoBids.size());
        }

        return tx;
    }

    /**
     * Đăng ký auto-bid cho một bidder trong một phiên.
     *
     * ⚠️  Tầng DAO sau khi gọi method này phải:
     *     AutoBidDAO.insertOrUpdate(config)
     */
    public void registerAutoBid(AutoBidConfig config, Bidder bidder) {
        Auction auction = auctionMap.get(config.getAuctionId());
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + config.getAuctionId());
        if (!auction.isRunning())
            throw new IllegalStateException("Can only register auto-bid on RUNNING auction");

        // Lưu vào Bidder object
        bidder.setAutoBid(config);
        // Đăng ký vào Engine
        autoBidEngine.register(config, bidder);

        // Nếu giá hiện tại < startingPrice + increment → thử bid ngay
        if (config.canBid(auction.getCurrentPrice())) {
            autoBidEngine.trigger(auction, bidder);
        }
    }

    /**
     * Admin force-close một phiên đấu giá.
     * ⚠️  Tầng DAO sau khi gọi method này phải: AuctionDAO.updateStatus()
     */
    public void forceCloseAuction(String auctionId, String reason) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        auction.forceCancel(reason);
        autoBidEngine.cleanupAutoBids(auctionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Scheduler — tự động chuyển trạng thái theo thời gian
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lên lịch tự động mở phiên khi đến startTime.
     */
    private void scheduleOpen(Auction auction) {
        long delaySeconds = ChronoUnit.SECONDS.between(
            LocalDateTime.now(), auction.getStartTime()
        );
        if (delaySeconds < 0) delaySeconds = 0; // startTime đã qua → mở ngay

        scheduler.schedule(() -> {
            try {
                if (auction.getStatus() == AuctionStatus.OPEN) {
                    auction.open();
                    System.out.printf("[Scheduler] Auction %s OPENED%n",
                        auction.getId().substring(0, 8));
                }
            } catch (Exception e) {
                System.err.println("[Scheduler] Error opening auction: " + e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Lên lịch tự động đóng phiên khi đến endTime.
     *
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
                        System.out.printf("[Scheduler] Auction %s CLOSED. Winner: %s, Price: %.2f%n",
                            auction.getId().substring(0, 8),
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Observer management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thêm global observer — nhận event từ TẤT CẢ phiên.
     * ⚠️  Kết nối Server: ClientHandler/NotificationService gọi method này khi connect.
     */
    public void addGlobalObserver(AuctionObserver observer) {
        globalObservers.add(observer);
        // Đăng ký vào tất cả auction đang active
        auctionMap.values().stream()
            .filter(a -> a.getStatus() == AuctionStatus.RUNNING
                      || a.getStatus() == AuctionStatus.OPEN)
            .forEach(a -> a.addObserver(observer));
    }

    public void removeGlobalObserver(AuctionObserver observer) {
        globalObservers.remove(observer);
        auctionMap.values().forEach(a -> a.removeObserver(observer));
    }

    /**
     * Thêm observer cho một phiên cụ thể.
     * ⚠️  Kết nối UI: Bidder "vào xem" một phiên → addObserver cho phiên đó.
     */
    public void addObserverToAuction(String auctionId, AuctionObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) auction.addObserver(observer);
    }

    public void removeObserverFromAuction(String auctionId, AuctionObserver observer) {
        Auction auction = auctionMap.get(auctionId);
        if (auction != null) auction.removeObserver(observer);
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
        System.out.println("[AuctionManager] Shutting down...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[AuctionManager] Shutdown complete.");
    }

    public int getActiveAuctionCount() {
        return (int) auctionMap.values().stream()
            .filter(Auction::isRunning).count();
    }

    public AutoBidEngine getAutoBidEngine() { return autoBidEngine; }
}
