package server.common.entity;


import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.enums.AuctionStatus;
import server.common.enums.BidStatus;
import server.service.listeners.AuctionEventListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/*
  ═══════════════════════════════════════════════════════════
   Auction — Logic 1 phòng đấu giá.

  Chịu trách nhiệm:
    1. Nhận và validate bid (placeBid)
    2. Đảm bảo thread-safety bằng ReentrantLock
    3. Anti-sniping: gia hạn thời gian khi bid vào phút cuối
    4. Quản lý vòng đời trạng thái (OPEN → RUNNING → FINISHED → PAID/CANCELED)
    5. Thông báo tới Observer sau mỗi sự kiện
    6. Lưu toàn bộ lịch sử bid (bidHistory)

   CONCURRENCY — ĐỌC KỸ TRƯỚC KHI SỬA:
    ReentrantLock bảo vệ toàn bộ vùng critical section trong placeBid().
    Quy tắc bắt buộc: LUÔN gọi lock.unlock() trong khối finally.
    Nếu quên → deadlock toàn bộ phiên đấu giá, không bid được nữa.

   Kết nối DB (tầng DAO cần xử lý):
    - Mỗi lần placeBid() thành công → INSERT vào bảng bids
    - Mỗi lần state thay đổi → UPDATE bảng auctions
    - closeSession() → UPDATE status, current_winner_id

   Kết nối UI:
    - UI đăng ký observer qua addObserver() để nhận realtime update
    - UI đọc getCurrentPrice(), getCurrentLeader(), getStatus() để render
    - UI gọi getRecentBids(n) để hiển thị bảng lịch sử bid
    - UI dùng getBidHistory() để vẽ LineChart giá theo thời gian
 */
public class Auction extends Entity {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final Item          item;
    private final String        sellerId;

    // ── Time config ───────────────────────────────────────────────────────────
    private final LocalDateTime startTime;
    private       LocalDateTime endTime;

    private transient volatile boolean isTimeExtendedDuringRuntime = false;

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
    private final int snipeWindowSeconds;   // Số giây cuối cùng trong Anti-snipping
    private final int snipeExtensionSeconds;    // Thêm bao nhiêu giây nếu Anti-snipping

    // ── Bid config ────────────────────────────────────────────────────────────
    private final BigDecimal startingPrice;     // giá khởi điểm
    private final BigDecimal minBidIncrement;  // độ tăng tối thiểu giữa mỗi lần đặt bid
    private final BigDecimal reservePrice;     // null = không có giá sàn

    // ── Runtime state ─────────────────────────────────────────────────────────
    private volatile AuctionStatus status;
    private          BigDecimal    currentPrice;
    private          User          currentLeader;   // người đang dẫn đầu
    private          LocalDateTime lastBidTime;

    // ── Bid history ───────────────────────────────────────────────────────────
    private final List<BidTransaction> bidHistory;

    // ── Observer list ─────────────────────────────────────────────────────────
    private final List<AuctionEventListener> observers;

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

    /**
     * Tạo auction mới ở tầng domain trước khi persist xuống DB.
     *
     * <p>{@code reservePrice} được chuẩn hóa về {@link BigDecimal#ZERO} khi client
     * gửi trống để các bước so sánh giá sau này không bị lỗi {@code null}.</p>
     *
     * @param item item được đem ra đấu giá
     * @param sellerId ID người bán sở hữu item
     * @param startTime thời điểm bắt đầu phiên
     * @param endTime thời điểm kết thúc phiên
     * @param minBidIncrement bước nhảy giá tối thiểu
     * @param reservePrice giá sàn; có thể {@code null}
     * @param snipeWindowSeconds khoảng thời gian chống đặt giá sát giờ
     * @param snipeExtensionSeconds thời gian gia hạn khi có bid sát giờ
     */
    public Auction(Item item, String sellerId,
        LocalDateTime startTime, LocalDateTime endTime,
        BigDecimal minBidIncrement, BigDecimal reservePrice,
        int snipeWindowSeconds, int snipeExtensionSeconds) {

        super();
        validateTimes(startTime, endTime);
        validateSnippingTimes(snipeWindowSeconds,snipeExtensionSeconds);
        this.item                  = item;
        this.sellerId                = sellerId;
        this.startTime             = startTime;
        this.endTime               = endTime;
        this.startingPrice         = item.getStartingPrice();
        this.minBidIncrement       = minBidIncrement;
        this.reservePrice          = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        this.snipeWindowSeconds    = snipeWindowSeconds;
        this.snipeExtensionSeconds = snipeExtensionSeconds;
        this.status                = AuctionStatus.OPEN;
        this.currentPrice          = item.getStartingPrice();
        this.currentLeader         = null;
        this.lastBidTime           = null;
        this.bidHistory            = new ArrayList<>();
        this.observers             = new ArrayList<>();
    }

    /**
     * Khởi tạo lại auction từ dữ liệu đã lưu trong DB.
     *
     * <p>Constructor này giữ nguyên ID, thời gian tạo, current price, leader và status
     * để AuctionManager có thể schedule tiếp sau khi server restart.
     * {@code reservePrice} cũng được chuẩn hóa về {@link BigDecimal#ZERO} nếu DB trả về null.</p>
     *
     * @param id ID auction trong DB
     * @param createdAt thời điểm auction được tạo
     * @param item item thuộc phiên đấu giá
     * @param sellerId ID người bán
     * @param startTime thời điểm bắt đầu phiên
     * @param endTime thời điểm kết thúc phiên
     * @param lastBidTime thời điểm bid gần nhất, có thể {@code null}
     * @param currentPrice giá hiện tại
     * @param minBidIncrement bước nhảy giá tối thiểu
     * @param reservePrice giá sàn, có thể {@code null}
     * @param snipeWindowSeconds khoảng thời gian chống đặt giá sát giờ
     * @param snipeExtensionSeconds thời gian gia hạn khi có bid sát giờ
     * @param status trạng thái auction đã lưu
     * @param currentLeader user đang thắng, có thể {@code null}
     */
    public Auction(String id, LocalDateTime createdAt,
        Item item, String sellerId,
        LocalDateTime startTime, LocalDateTime endTime,
        LocalDateTime lastBidTime,
        BigDecimal currentPrice,
        BigDecimal minBidIncrement, BigDecimal reservePrice,
        int snipeWindowSeconds, int snipeExtensionSeconds,
        AuctionStatus status, User currentLeader
    ) {
        super(id,createdAt);
        this.item                  = item;
        this.sellerId                = sellerId;
        this.startTime             = startTime;
        this.endTime               = endTime;
        this.startingPrice         = item.getStartingPrice();
        this.currentPrice          = currentPrice;
        this.minBidIncrement       = minBidIncrement;
        this.reservePrice          = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        this.snipeWindowSeconds    = snipeWindowSeconds;
        this.snipeExtensionSeconds = snipeExtensionSeconds;
        this.status                = status;
        this.currentLeader         = currentLeader;
        this.lastBidTime           = lastBidTime;
        this.bidHistory            = new ArrayList<>();
        this.observers             = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CORE: placeBid

    /**
     * Đặt giá vào phiên đấu giá.
     *
     * Luồng xử lý :
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
    public BidTransaction placeBid(User bidder, BigDecimal amount, boolean isAutoBid) {

        // ── Step 1: Kiểm tra trạng thái — TRƯỚC khi lock ─────────────────────
        // Tối ưu: không cần lock để đọc status (read-only check)
        // Nếu đã FINISHED/CANCELED thì từ chối ngay, không tốn tài nguyên
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException(getId(), status);
        }

        // Không cho phép seller tự bid vào phiên của mình
        if (bidder.getId().equals(sellerId)) {
            throw new InvalidBidException("User cannot bid on their own auction", amount, currentPrice);
        }

        BidTransaction bidTransaction = null;   // output
        boolean timeExtended = false;   // Anti-snipping = false

        // ── Step 2: Acquire lock ──────────────────────────────────────────────
        lock.lock();
        try {

            // ── Step 3: Kiểm tra lại status SAU khi lock ─────────────────────
            //Vì có thể phiên vừa đóng trong khoảng thời gian giữa Step 1 và Step 2
            if (status != AuctionStatus.RUNNING) {
                throw new AuctionClosedException(getId(), status);
            }

            // ── Step 4: Validate amount ───────────────────────────────────────
            validateBid(amount); // False -> throw Exception

            // ── Step 5: Cập nhật state ────────────────────────────────────────

            // Đánh dấu bid cũ là OUTBID
            markCurrentWinnerOutbid();

            // Tạo transaction mới
            bidTransaction = new BidTransaction(
                getId(), bidder.getId(), bidder.getUsername(), amount, isAutoBid
            );
            bidHistory.add(bidTransaction);

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
        if (bidTransaction != null) {
            notifyBidUpdated(bidTransaction);
            if (timeExtended) {
                notifyTimeExtended(snipeExtensionSeconds);
            }
        }

        return bidTransaction;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validate bid amount.
     * @return Exception
     */
    private void validateBid(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBidException("Bid amount must be positive", amount, currentPrice);
        }
        if (amount.compareTo(currentPrice) <= 0) {
            throw new InvalidBidException(
                String.format("Bid %.2f must be higher than current price %.2f", amount, currentPrice),
                amount, currentPrice
            );
        }
        if (minBidIncrement.compareTo(BigDecimal.ZERO) > 0 && (amount.subtract(currentPrice).compareTo(minBidIncrement) < 0)) {
            throw new InvalidBidException(
                String.format("Bid must be at least %.2f above current price (%.2f). " +
                        "Minimum next bid: %.2f",
                    minBidIncrement, currentPrice, currentPrice.add(minBidIncrement)),
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
    public void startRunning() {
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

            // Có bid nào đặt không ?
            boolean hasBids     = !bidHistory.isEmpty();

            // Có chạm giá sàn (Reserve Price) không ?
            boolean meetsReserve = (reservePrice.compareTo(BigDecimal.ZERO) == 0) || (currentPrice.compareTo(reservePrice) >= 0);

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
                    .filter(tx -> tx.getStatus() != BidStatus.WON)
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
        System.out.println("[Auction " + getId() + "] Force canceled. Reason: " + reason);
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
            getId().substring(0, 8), seconds, endTime);
    }

    /** Tính số giây còn lại. Thread-safe (đọc LocalDateTime là atomic). */
    public long getSecondsRemaining() {
        if (status != AuctionStatus.RUNNING) return 0;
        long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), endTime);
        return Math.max(0, secs);
    }

    public boolean checkAndResetExtensionFlag() {
        lock.lock();
        try {
            boolean flag = isTimeExtendedDuringRuntime;
            isTimeExtendedDuringRuntime = false; // Reset sau khi đọc
            return flag;
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Observer management
    // ─────────────────────────────────────────────────────────────────────────

    public void addObserver(AuctionEventListener observer) {
        synchronized (observers) { observers.add(observer); }
    }

    public void removeObserver(AuctionEventListener observer) {
        synchronized (observers) { observers.remove(observer); }
    }

    private void notifyBidUpdated(BidTransaction transaction) {
        List<AuctionEventListener> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try {
                int bidderId = Integer.parseInt(transaction.getBidderId());
                int auctionId = Integer.parseInt(this.getId());
                obs.onBidPlaced(bidderId,auctionId,this.item.getName(),transaction.getAmount());
            } catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }

        });
    }

    private void notifyAuctionClosed() {
        List<AuctionEventListener> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try {
                int winnerId = (this.getCurrentLeader() != null) ? Integer.parseInt(this.getCurrentLeader().getId()) : -1;
                int auctionId = Integer.parseInt(this.getId());
                obs.onAuctionEnded(winnerId,auctionId,this.item.getName(),this.getCurrentPrice());
            } catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }

        });
    }

    private void notifyTimeExtended(int seconds) {
        List<AuctionEventListener> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try {
                int auctionId = Integer.parseInt(this.getId());
                obs.onTimeExtended(auctionId,this.getItem().getName(), seconds);
            } catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }

        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper queries
    // ─────────────────────────────────────────────────────────────────────────
    /** Đánh dấu bid WINNING hiện tại thành OUTBID. Gọi trong vùng lock. */
    private void markCurrentWinnerOutbid() {
        // Tìm bid đang WINNING và chuyển sang OUTBID
        for (int i = bidHistory.size() - 1; i >= 0; i--) {
            BidTransaction tx = bidHistory.get(i);
            if (tx.getStatus() == BidStatus.WINNING) {
                tx.markOutbid();
                break; // Chỉ có tối đa 1 bid WINNING tại mọi thời điểm
            }
        }
    }

    /** Lấy bid đang ở trạng thái WINNING (chỉ có tối đa 1) */
    public BidTransaction getWinningBid() {
        return bidHistory.stream()
            .filter(tx -> tx.getStatus() == BidStatus.WINNING)
            .findFirst()
            .orElse(null);
    }

    /**
     * Lấy n bid gần nhất — dùng cho UI hiển thị bảng lịch sử.
     * UI: truyền vào TableView/ListView của JavaFX
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

    public Item             getItem()               { return item; }
    public String          getSellerId()             { return sellerId; }
    public LocalDateTime    getStartTime()           { return startTime; }
    public LocalDateTime    getEndTime()             { return endTime; }
    public BigDecimal           getStartingPrice()       { return startingPrice; }
    public BigDecimal           getMinBidIncrement()     { return minBidIncrement; }
    public BigDecimal           getReservePrice()        { return reservePrice; }
    public AuctionStatus    getStatus()              { return status; }
    public BigDecimal           getCurrentPrice()        { return currentPrice; }
    public User           getCurrentLeader()       { return currentLeader; }
    public LocalDateTime    getLastBidTime()         { return lastBidTime; }
    public int              getSnipeWindowSeconds()  { return snipeWindowSeconds; }
    public int              getSnipeExtensionSeconds(){ return snipeExtensionSeconds; }
    public int              getTotalBids()           { return bidHistory.size(); }
    public List<BidTransaction> getBidHistory()      { return Collections.unmodifiableList(bidHistory); }

    /**
     * Khôi phục lịch sử bid từ DB khi server restart hoặc khi reload auction sau khi tạo.
     *
     * <p>Danh sách này giúp các logic đóng phiên, xác định người thắng và thống kê số bid
     * nhìn thấy đúng dữ liệu đã persist, thay vì coi auction trong RAM là chưa từng có bid.</p>
     *
     * @param transactions danh sách bid transaction đã đọc từ DB; có thể {@code null}
     */
    public void restoreBidHistory(List<BidTransaction> transactions) {
        lock.lock();
        try {
            bidHistory.clear();
            if (transactions != null) {
                bidHistory.addAll(transactions);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Rollback RAM state sau khi DB commit thất bại.
     *
     * Gọi bởi BidTransactionService trong khối catch khi ghi DB lỗi,
     * sau khi auction.placeBid() đã thay đổi RAM (currentPrice, currentLeader, bidHistory).
     *
     * Cơ chế:
     *   - Xoá tx vừa thêm khỏi bidHistory
     *   - Khôi phục currentPrice và currentLeader về giá trị snapshot trước khi placeBid() chạy
     *
     * @param failedTx        BidTransaction vừa được tạo bởi placeBid() nhưng chưa commit DB
     * @param previousPrice   currentPrice trước khi placeBid() chạy
     * @param previousLeader  currentLeader trước khi placeBid() chạy — có thể null
     */
    public void rollbackLastBid(BidTransaction failedTx, BigDecimal previousPrice, User previousLeader) {
        lock.lock();
        try {
            // Xoá tx thất bại khỏi bidHistory
            // failedTx có thể null nếu caller không giữ được tham chiếu → xoá phần tử cuối
            if (failedTx != null) {
                bidHistory.remove(failedTx);
            } else if (!bidHistory.isEmpty()) {
                bidHistory.remove(bidHistory.size() - 1);
            }

            // Khôi phục currentPrice và currentLeader về snapshot trước bid
            this.currentPrice  = previousPrice;
            this.currentLeader = previousLeader;

            // Bid trước đó đã bị markOutbid() bên trong placeBid() — cần hoàn tác:
            // Tìm bid cuối cùng của previousLeader và đánh dấu lại là WINNING
            if (previousLeader != null) {
                for (int i = bidHistory.size() - 1; i >= 0; i--) {
                    BidTransaction prev = bidHistory.get(i);
                    if (prev.getBidderId().equals(previousLeader.getId())) {
                        prev.restoreWinning(); // hoàn tác OUTBID → WINNING
                        break;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start))
            throw new IllegalArgumentException("end_time must be after start_time");
    }
    private void validateSnippingTimes(int snipeWindowSeconds,int snipeExtensionSeconds){
        if (snipeWindowSeconds < 0 || snipeExtensionSeconds < 0  ){
            throw new IllegalArgumentException("snipping_times must be positive");
        }
    }

    @Override
    public String toString() {
        return String.format("Auction[%s] %s | Price: %.2f | Status: %s | Bids: %d",
            getId().substring(0, 8), item.getName(), currentPrice, status, bidHistory.size());
    }
    @Override
    public void printInfo(){
        System.out.println(this);
    }
}