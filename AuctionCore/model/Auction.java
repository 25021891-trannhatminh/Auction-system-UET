package model;

import enums.AuctionStatus;
import exception.AuctionClosedException;
import exception.InvalidBidException;
import model.item.Item;
import model.user.Bidder;
import model.user.Seller;
import observer.AuctionObserver;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ═══════════════════════════════════════════════════════════
 *  Auction — Trung tâm nghiệp vụ của toàn bộ hệ thống.
 * ═══════════════════════════════════════════════════════════
 *
 * Chịu trách nhiệm:
 *   1. Nhận và validate bid (placeBid)
 *   2. Đảm bảo thread-safety bằng ReentrantLock
 *   3. Anti-sniping: gia hạn thời gian khi bid vào phút cuối
 *   4. Quản lý vòng đời trạng thái (OPEN → RUNNING → FINISHED → PAID/CANCELED)
 *   5. Thông báo tới Observer sau mỗi sự kiện
 *   6. Lưu toàn bộ lịch sử bid (bidHistory)
 *
 * ⚠️  CONCURRENCY — ĐỌC KỸ TRƯỚC KHI SỬA:
 *   ReentrantLock bảo vệ toàn bộ vùng critical section trong placeBid().
 *   Quy tắc bắt buộc: LUÔN gọi lock.unlock() trong khối finally.
 *   Nếu quên → deadlock toàn bộ phiên đấu giá, không bid được nữa.
 *
 * ⚠️  Kết nối DB (tầng DAO cần xử lý):
 *   - Mỗi lần placeBid() thành công → INSERT vào bảng bids
 *   - Mỗi lần state thay đổi → UPDATE bảng auctions
 *   - closeSession() → UPDATE status, current_winner_id
 *
 * ⚠️  Kết nối UI:
 *   - UI đăng ký observer qua addObserver() để nhận realtime update
 *   - UI đọc getCurrentPrice(), getCurrentLeader(), getStatus() để render
 *   - UI gọi getRecentBids(n) để hiển thị bảng lịch sử bid
 *   - UI dùng getBidHistory() để vẽ LineChart giá theo thời gian
 */
public class Auction {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final String        id;
    private final Item          item;
    private final Seller        seller;
    private final LocalDateTime createdAt;

    // ── Time config ───────────────────────────────────────────────────────────
    private       LocalDateTime startTime;
    private       LocalDateTime endTime;

    /**
     * Anti-sniping config:
     *   snipeWindowSeconds   — nếu bid trong khoảng này trước endTime → gia hạn
     *   snipeExtensionSeconds — số giây được thêm vào endTime
     *
     * Ví dụ: window=30, extension=60
     *   Nếu còn < 30 giây mà có bid → endTime += 60 giây.
     *
     * ⚠️  Kết nối DB: ánh xạ sang auctions.snipe_window_seconds
     *     và auctions.snipe_extension_seconds
     */
    private final int snipeWindowSeconds;
    private final int snipeExtensionSeconds;

    // ── Bid config ────────────────────────────────────────────────────────────
    private final double startingPrice;
    private final double minBidIncrement;  // ánh xạ auctions.min_bid_increment
    private final Double reservePrice;     // null = không có giá sàn

    // ── Runtime state ─────────────────────────────────────────────────────────
    private       AuctionStatus  status;
    private       double         currentPrice;
    private       Bidder         currentLeader;   // người đang dẫn đầu
    private       LocalDateTime  lastBidTime;     // ánh xạ auctions.last_bid_time

    // ── Bid history ───────────────────────────────────────────────────────────
    private final List<BidTransaction> bidHistory;

    // ── Observer list ─────────────────────────────────────────────────────────
    private final List<AuctionObserver> observers;

    // ── Concurrency lock ──────────────────────────────────────────────────────
    /**
     * ReentrantLock thay vì synchronized vì:
     *   - Cho phép tryLock() với timeout (không block vô hạn)
     *   - Hỗ trợ fairness (fair=true → FIFO order cho waiting threads)
     *   - Dễ debug hơn synchronized
     *
     * fair=true: đảm bảo không có thread nào bị starvation.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructors
    // ─────────────────────────────────────────────────────────────────────────

    public Auction(Item item, Seller seller,
                   LocalDateTime startTime, LocalDateTime endTime,
                   double minBidIncrement, Double reservePrice,
                   int snipeWindowSeconds, int snipeExtensionSeconds) {
        validateTimes(startTime, endTime);

        this.id                    = UUID.randomUUID().toString();
        this.item                  = item;
        this.seller                = seller;
        this.createdAt             = LocalDateTime.now();
        this.startTime             = startTime;
        this.endTime               = endTime;
        this.startingPrice         = item.getStartingPrice();
        this.minBidIncrement       = minBidIncrement;
        this.reservePrice          = reservePrice;
        this.snipeWindowSeconds    = snipeWindowSeconds;
        this.snipeExtensionSeconds = snipeExtensionSeconds;
        this.status                = AuctionStatus.OPEN;
        this.currentPrice          = item.getStartingPrice();
        this.currentLeader         = null;
        this.lastBidTime           = null;
        this.bidHistory            = new ArrayList<>();
        this.observers             = new ArrayList<>();
    }

    /** Constructor load từ DB — truyền vào state đã lưu */
    public Auction(String id, Item item, Seller seller,
                   LocalDateTime createdAt,
                   LocalDateTime startTime, LocalDateTime endTime,
                   double startingPrice, double currentPrice,
                   double minBidIncrement, Double reservePrice,
                   int snipeWindowSeconds, int snipeExtensionSeconds,
                   AuctionStatus status, Bidder currentLeader,
                   LocalDateTime lastBidTime) {
        this.id                    = id;
        this.item                  = item;
        this.seller                = seller;
        this.createdAt             = createdAt;
        this.startTime             = startTime;
        this.endTime               = endTime;
        this.startingPrice         = startingPrice;
        this.currentPrice          = currentPrice;
        this.minBidIncrement       = minBidIncrement;
        this.reservePrice          = reservePrice;
        this.snipeWindowSeconds    = snipeWindowSeconds;
        this.snipeExtensionSeconds = snipeExtensionSeconds;
        this.status                = status;
        this.currentLeader         = currentLeader;
        this.lastBidTime           = lastBidTime;
        this.bidHistory            = new ArrayList<>();
        this.observers             = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CORE: placeBid — Đây là method quan trọng nhất, đọc kỹ từng bước
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đặt giá vào phiên đấu giá.
     *
     * Luồng xử lý (theo Sequence Diagram đã thiết kế):
     *   Step 1 — Kiểm tra trạng thái phiên (không cần lock)
     *   Step 2 — Acquire lock (chặn các thread khác)
     *   Step 3 — Validate giá trong vùng lock
     *   Step 4 — Cập nhật state
     *   Step 5 — Anti-sniping check
     *   Step 6 — Release lock
     *   Step 7 — Thông báo Observer (ngoài lock để không block thread khác)
     *
     * @param bidder    Người đặt giá
     * @param amount    Số tiền muốn đặt
     * @param isAutoBid True nếu đặt tự động bởi AutoBidEngine
     * @return BidTransaction vừa được tạo
     * @throws AuctionClosedException nếu phiên không ở trạng thái RUNNING
     * @throws InvalidBidException    nếu amount không hợp lệ
     */
    public BidTransaction placeBid(Bidder bidder, double amount, boolean isAutoBid) {

        // ── Step 1: Kiểm tra trạng thái — TRƯỚC khi lock ─────────────────────
        // Tối ưu: không cần lock để đọc status (read-only check)
        // Nếu đã FINISHED/CANCELED thì từ chối ngay, không tốn tài nguyên
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException(id, status);
        }

        // Không cho phép seller tự bid vào phiên của mình
        if (bidder.getId().equals(seller.getId())) {
            throw new InvalidBidException("Seller cannot bid on their own auction", amount, currentPrice);
        }

        BidTransaction newTx = null;
        boolean timeExtended = false;

        // ── Step 2: Acquire lock ──────────────────────────────────────────────
        lock.lock();
        try {

            // ── Step 3: Kiểm tra lại status SAU khi lock ─────────────────────
            // Tại sao cần kiểm tra lại? Vì có thể phiên vừa đóng trong khoảng
            // thời gian giữa Step 1 và Step 2 (TOCTOU race condition)
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException(id, status);
            }

            // ── Step 4: Validate amount ───────────────────────────────────────
            validateBid(amount);

            // ── Step 5: Cập nhật state ────────────────────────────────────────

            // Đánh dấu bid cũ là OUTBID
            if (!bidHistory.isEmpty()) {
                BidTransaction prevWinning = getWinningBid();
                if (prevWinning != null) {
                    prevWinning.markOutbid();
                }
            }

            // Tạo transaction mới
            newTx = new BidTransaction(
                id, bidder.getId(), bidder.getUsername(), amount, isAutoBid
            );
            bidHistory.add(newTx);

            // Cập nhật state phiên
            currentPrice  = amount;
            currentLeader = bidder;
            lastBidTime   = LocalDateTime.now();

            // ── Step 6: Anti-sniping ──────────────────────────────────────────
            long secondsLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
            if (secondsLeft > 0 && secondsLeft <= snipeWindowSeconds) {
                extendTime(snipeExtensionSeconds);
                timeExtended = true;
            }

        } finally {
            // ── Step 7: LUÔN LUÔN unlock, dù có exception ────────────────────
            lock.unlock();
        }

        // ── Step 8: Notify observers NGOÀI lock ──────────────────────────────
        // Quan trọng: notify ngoài lock để tránh deadlock
        // (Observer có thể gọi lại các method khác của Auction)
        if (newTx != null) {
            notifyBidUpdated(newTx);
            if (timeExtended) {
                notifyTimeExtended(snipeExtensionSeconds);
            }
        }

        return newTx;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validate bid amount.
     * Gọi trong vùng lock — không public vì chỉ dùng nội bộ.
     */
    private void validateBid(double amount) {
        if (amount <= 0) {
            throw new InvalidBidException("Bid amount must be positive", amount, currentPrice);
        }
        if (amount <= currentPrice) {
            throw new InvalidBidException(
                String.format("Bid %.2f must be higher than current price %.2f", amount, currentPrice),
                amount, currentPrice
            );
        }
        if (minBidIncrement > 0 && (amount - currentPrice) < minBidIncrement) {
            throw new InvalidBidException(
                String.format("Bid must be at least %.2f above current price (%.2f). " +
                              "Minimum next bid: %.2f",
                              minBidIncrement, currentPrice, currentPrice + minBidIncrement),
                amount, currentPrice
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Session lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Chuyển trạng thái từ OPEN sang RUNNING.
     * Gọi bởi AuctionManager khi đến startTime.
     */
    public void open() {
        if (status != AuctionStatus.OPEN)
            throw new IllegalStateException("Can only open an OPEN auction. Current: " + status);
        this.status = AuctionStatus.RUNNING;
    }

    /**
     * Đóng phiên đấu giá khi hết thời gian.
     * Gọi bởi ScheduledExecutor trong AuctionManager khi đến endTime.
     *
     * Logic:
     *   - Nếu không có bid → CANCELED
     *   - Nếu có reservePrice và currentPrice < reservePrice → CANCELED
     *   - Còn lại → FINISHED, xác định winner
     *
     * ⚠️  Tầng DAO phải UPDATE bảng auctions sau khi gọi method này.
     */
    public void closeSession() {
        lock.lock();
        try {
            if (status != AuctionStatus.RUNNING)
                throw new IllegalStateException("Can only close a RUNNING auction. Current: " + status);

            boolean hasBids     = !bidHistory.isEmpty();
            boolean meetsReserve = (reservePrice == null) || (currentPrice >= reservePrice);

            if (!hasBids || !meetsReserve) {
                status = AuctionStatus.CANCELED;
                // Đánh dấu tất cả bid là LOST (nếu có)
                bidHistory.forEach(BidTransaction::markLost);
            } else {
                status = AuctionStatus.FINISHED;
                // Đánh dấu bid thắng
                BidTransaction winning = getWinningBid();
                if (winning != null) winning.markWon();
                // Đánh dấu tất cả bid khác là LOST
                bidHistory.stream()
                    .filter(tx -> tx.getStatus() != enums.BidStatus.WON)
                    .forEach(BidTransaction::markLost);
            }

        } finally {
            lock.unlock();
        }

        // Notify ngoài lock
        notifyAuctionClosed();
    }

    /**
     * Admin force-cancel phiên bất kỳ lúc nào.
     * ⚠️  Tầng DAO phải UPDATE bảng auctions sau khi gọi method này.
     */
    public void forceCancel(String reason) {
        lock.lock();
        try {
            if (status == AuctionStatus.PAID)
                throw new IllegalStateException("Cannot cancel a PAID auction");
            status = AuctionStatus.CANCELED;
            bidHistory.forEach(BidTransaction::markLost);
        } finally {
            lock.unlock();
        }
        System.out.println("[Auction " + id + "] Force canceled. Reason: " + reason);
        notifyAuctionClosed();
    }

    /**
     * Đánh dấu phiên đã được thanh toán.
     * Gọi sau khi Payment được xác nhận.
     */
    public void markPaid() {
        if (status != AuctionStatus.FINISHED)
            throw new IllegalStateException("Can only mark FINISHED auction as PAID");
        this.status = AuctionStatus.PAID;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Anti-sniping — gọi trong vùng lock
    // ─────────────────────────────────────────────────────────────────────────

    /** Gia hạn endTime thêm seconds giây. Chỉ gọi trong vùng lock. */
    private void extendTime(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
        System.out.printf("[Anti-snipe] Auction %s extended by %ds. New endTime: %s%n",
            id.substring(0, 8), seconds, endTime);
    }

    /** Tính số giây còn lại. Thread-safe (đọc LocalDateTime là atomic). */
    public long getSecondsRemaining() {
        if (status != AuctionStatus.RUNNING) return 0;
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
        return Math.max(0, secs);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Observer management
    // ─────────────────────────────────────────────────────────────────────────

    public void addObserver(AuctionObserver observer) {
        synchronized (observers) { observers.add(observer); }
    }

    public void removeObserver(AuctionObserver observer) {
        synchronized (observers) { observers.remove(observer); }
    }

    private void notifyBidUpdated(BidTransaction tx) {
        List<AuctionObserver> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try { obs.onBidUpdated(this, tx); }
            catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }
        });
    }

    private void notifyAuctionClosed() {
        List<AuctionObserver> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try { obs.onAuctionClosed(this); }
            catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }
        });
    }

    private void notifyTimeExtended(int seconds) {
        List<AuctionObserver> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try { obs.onTimeExtended(this, seconds); }
            catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper queries
    // ─────────────────────────────────────────────────────────────────────────

    /** Lấy bid đang ở trạng thái WINNING (chỉ có tối đa 1) */
    public BidTransaction getWinningBid() {
        return bidHistory.stream()
            .filter(tx -> tx.getStatus() == enums.BidStatus.WINNING)
            .findFirst()
            .orElse(null);
    }

    /**
     * Lấy n bid gần nhất — dùng cho UI hiển thị bảng lịch sử.
     * ⚠️  Kết nối UI: truyền vào TableView/ListView của JavaFX
     */
    public List<BidTransaction> getRecentBids(int n) {
        int size = bidHistory.size();
        if (size == 0) return Collections.emptyList();
        return Collections.unmodifiableList(
            bidHistory.subList(Math.max(0, size - n), size)
        );
    }

    /** Kiểm tra một bidder có đang dẫn đầu không */
    public boolean isLeading(String bidderId) {
        return currentLeader != null && currentLeader.getId().equals(bidderId);
    }

    public boolean hasStarted()  { return status != AuctionStatus.OPEN; }
    public boolean isRunning()   { return status == AuctionStatus.RUNNING; }
    public boolean isFinished()  {
        return status == AuctionStatus.FINISHED
            || status == AuctionStatus.PAID
            || status == AuctionStatus.CANCELED;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────────────────────────────

    public String           getId()                 { return id; }
    public Item             getItem()               { return item; }
    public Seller           getSeller()             { return seller; }
    public LocalDateTime    getCreatedAt()           { return createdAt; }
    public LocalDateTime    getStartTime()           { return startTime; }
    public LocalDateTime    getEndTime()             { return endTime; }
    public double           getStartingPrice()       { return startingPrice; }
    public double           getMinBidIncrement()     { return minBidIncrement; }
    public Double           getReservePrice()        { return reservePrice; }
    public AuctionStatus    getStatus()              { return status; }
    public double           getCurrentPrice()        { return currentPrice; }
    public Bidder           getCurrentLeader()       { return currentLeader; }
    public LocalDateTime    getLastBidTime()         { return lastBidTime; }
    public int              getSnipeWindowSeconds()  { return snipeWindowSeconds; }
    public int              getSnipeExtensionSeconds(){ return snipeExtensionSeconds; }
    public int              getTotalBids()           { return bidHistory.size(); }
    public List<BidTransaction> getBidHistory()      { return Collections.unmodifiableList(bidHistory); }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start))
            throw new IllegalArgumentException("end_time must be after start_time");
    }

    @Override
    public String toString() {
        return String.format("Auction[%s] %s | Price: %.2f | Status: %s | Bids: %d",
            id.substring(0, 8), item.getName(), currentPrice, status, bidHistory.size());
    }
}
