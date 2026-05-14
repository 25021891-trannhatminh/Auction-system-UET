package server.common.entity.manager;

import server.common.entity.AutoBidEngine;
import server.common.entity.Auction;
import server.common.entity.AutoBidConfig;
import server.common.entity.BidTransaction;
import server.common.entity.Item;
import server.common.entity.User;
import server.common.entity.AuctionObserver;
import server.common.enums.AuctionStatus;
import server.common.model.BidResultDTO;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/*
    AuctionManager — Singleton điều phối toàn bộ Auction.
    Lý do: nếu có 2 instance → auctionMap bị phân mảnh → mỗi instance chỉ biết về một nửa số phiên đấu giá → data inconsistency.

    1. Registry toàn bộ Auction (auctionMap)
    2. Tự động chuyển trạng thái OPEN → RUNNING → FINISHED theo thời gian (sử dụng ScheduledExecutorService)
    3. Điều phối AutoBidEngine sau mỗi bid thành công
    4. Quản lý danh sách Users trong memory
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

    /** AutoBidEngine xử lý auto-bid */
    private final AutoBidEngine autoBidEngine = new AutoBidEngine();

    /*
    Global observers — nhận sự kiện từ MỌI phiên đấu giá.
      Ví dụ: NotificationService đăng ký ở đây để lưu notification vào DB.

      UI/Server:
        ServerBroadcaster implements AuctionObserver và đăng ký ở đây
        để push update đến toàn bộ client đang connect.
     */
    private final List<AuctionObserver> globalObservers
        = new CopyOnWriteArrayList<>();

    /*
      ScheduledExecutorService quản lý timer tự động đóng/mở phiên.

      Dùng ScheduledThreadPool :
        - ScheduledThreadPool dùng nhiều thread → các phiên không block nhau
        - ScheduledThreadPool xử lý exception tốt hơn
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

    public void registerUser(User user) {
        userMap.put(user.getId(), user);
    }

    // Return User hoặc null
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

    /**
      Tạo mới phiên đấu giá và lên lịch tự động mở/đóng.

        Sau khi gọi method này, tầng Service phải:
          1. AuctionDAO.insert(auction) — lưu vào DB
          2. ItemDAO.updateStatus(item.getId(), "IN_AUCTION")

      @return Auction vừa được tạo
     */
    public Auction createAuction(Item item, User seller,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 double minBidIncrement, Double reservePrice,
                                 int snipeWindowSeconds, int snipeExtensionSeconds) {
        Auction auction = new Auction(
            item, seller, startTime, endTime,
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
            auction.getId().substring(0, 8), item.getName(), startTime, endTime);

        return auction;
    }

    /**
      Load auction từ DB vào memory (dùng khi server khởi động).
      Nếu auction đang RUNNING → lên lịch đóng theo endTime còn lại.
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
    //  Bid operations

    /**
      Entry point cho mọi yêu cầu đặt giá từ client.
     Khi client đặt giá thủ công -> Manager follow flow
      Flow:
        1. Tìm auction
        2. Gọi auction.placeBid(autoBid = false) → xử lý trong vùng lock
        3. Trigger AutoBidEngine → auction.placeBid(autoBid = true)
        4.      *   BidDAO.insert(manualTransaction)
                *   BidDAO.insert(autoBidTransaction) nếu != null
                *   AuctionDAO.updateState(auction)

      @return BidTransaction của bid thủ công vừa thực hiện
              (các auto-bid sẽ được notify qua Observer)
     */
    public BidTransaction placeBid(String auctionId, User bidder, double amount) {
        Auction auction = getAuctionByID(auctionId);

        // Đặt giá thủ công
        BidTransaction manualTransaction = auction.placeBid(bidder, amount, false);

        // Trigger auto-bid cascade (chạy sau khi lock đã release)
        BidTransaction autoBidTransaction = autoBidEngine.trigger(auction, bidder);

        return manualTransaction;
    }

    /**
     * DTO Trả về BidResult đầy đủ.
     *
     * Dùng cho Server layer để serialize toàn bộ thông tin vào response JSON:
     *   - manualTx: bid của người dùng
     *   - autoBidTx: auto-bid của engine (null nếu không có)
     *   - finalPrice: giá cuối sau tất cả
     *   - finalLeader: ai đang dẫn đầu
     *
     *      Kết nối Server:
     *   RequestHandler gọi method này thay vì placeBid() đơn giản.
     *   Dùng BidResult.wasOutbidByAutoBid() để hiển thị cảnh báo cho client:
     *   "Bid thành công nhưng bạn đang bị vượt qua bởi auto-bid."
     */
    public BidResultDTO placeBidFull(String auctionId, User bidder, double amount) {
        Auction auction = getAuctionByID(auctionId);

        // Manual bid
        BidTransaction manualTransaction = auction.placeBid(bidder, amount, false);

        // Jump-to-Winner auto-bid
        BidTransaction autoBidTransaction = autoBidEngine.trigger(auction, bidder);

        String leaderName = auction.getCurrentLeader() != null
            ? auction.getCurrentLeader().getUsername() : "unknown";

        BidResultDTO result = new BidResultDTO(manualTransaction, autoBidTransaction,
            auction.getCurrentPrice(), leaderName);

        System.out.println("[AuctionManager] " + result);
        return result;
    }

    /**
     * Đăng ký auto-bid cho một bidder trong một phiên.
     *   LƯU Ý: nếu bidder đang là winner (top1 queue) -> không cho phép đăng ký maxBid bé hơn currentPrice
     *   Luôn trigger sau đăng ký, engine tự kiểm tra:
     *        - Nếu bidder đã là winner current → engine không bid
     *        - Nếu bidder chưa phải winner → engine bid Jump-to-Winner ngay
     *
     * ⚠️  Tầng DAO sau khi gọi method này:
     *   AutoBidDAO.insertOrUpdate(config)
     *   BidDAO.insert(autoBidTx) nếu autoBidTx != null
     *   AuctionDAO.updateState(auction) nếu có auto-bid
     *
     * @return BidTransaction nếu engine thực hiện auto-bid ngay sau đăng ký, null nếu không
     */
    public Optional<BidTransaction> registerAutoBid(AutoBidConfig config, User bidder) {
        Auction auction = getAuctionByID(config.getAuctionId());

        if (!auction.isRunning()) {
            throw new IllegalStateException(
                "Cannot register auto-bid on auction with status: " + auction.getStatus());
        }
        // Nếu config mới của winner maxBid < CurrentPrice hoặc < maxBid cũ thì không cho đăng ký
        String winnerAutoBidID = autoBidEngine.getWinnerId(auction.getId());
        double winnerMaxBid = autoBidEngine.peekWinner(auction.getId()).getMaxBid();
        if (winnerAutoBidID.equals(bidder.getId())
            && (config.getMaxBid() < auction.getCurrentPrice() || config.getMaxBid() < winnerMaxBid)) {
            return null;
        }
        // Lưu config vào Bidder và Engine
        bidder.setAutoBid(config);
        autoBidEngine.register(config, bidder);

        System.out.printf("[AuctionManager] AutoBid registered: %s on auction %s (max=%.0f, inc=%.0f)%n",
            bidder.getUsername(), config.getAuctionId().substring(0, 8),
            config.getMaxBid(), config.getIncrement());

        // Trigger ngay sau đăng ký
        User currentLeader = auction.getCurrentLeader();

        // Nếu chưa có ai đăng ký auto-bid hoặc bidder = winner mới → dùng currentLeader (winner cũ) làm trigger

        BidTransaction autoBidTransaction;
        if (autoBidEngine.getRegisteredCount(auction.getId()) > 1 && currentLeader != null){
            autoBidTransaction = autoBidEngine.trigger(auction, currentLeader);
        }else {
            autoBidTransaction = autoBidEngine.trigger(auction, null);
        }

        if (autoBidTransaction != null) {
            System.out.printf("[AuctionManager] Immediate auto-bid after registration: %.0f by %s%n",
                autoBidTransaction.getAmount(), autoBidTransaction.getBidderName());
        }

        return Optional.ofNullable(autoBidTransaction);
    }

    /**
     * Hủy auto-bid của một bidder trong một phiên.
     *
     * ⚠️  Tầng DAO: AutoBidDAO.updateStatus(auctionId, bidderId, CANCELED)
     */
    public void cancelAutoBid(String auctionId, User bidder) {
        bidder.cancelAutoBid(auctionId);
        autoBidEngine.unregister(auctionId, bidder.getId());
        System.out.printf("[AuctionManager] AutoBid canceled: %s on auction %s%n",
            bidder.getUsername(), auctionId.substring(0, 8));
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
                    auction.startRunning();
                    System.out.printf("[Scheduler] Auction %s OPENED%n",
                        auction.getId().substring(0, 8));
                }
            } catch (Exception e) {
                System.err.println("[Scheduler] Error opening auction: " + e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);

        // Gọi Trigger để các AutoBid đăng ký trước đó bắt đầu bid
        autoBidEngine.trigger(auction,null);
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

    private Auction getAuctionByID(String auctionId) {
        Auction auction = auctionMap.get(auctionId);
        if (auction == null)
            throw new IllegalArgumentException("Auction not found: " + auctionId);
        return auction;
    }

    public int getActiveAuctionCount() {
        return (int) auctionMap.values().stream().filter(Auction::isRunning).count();
    }

    public AutoBidEngine getAutoBidEngine() { return autoBidEngine; }
}
