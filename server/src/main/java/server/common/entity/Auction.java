package server.common.entity;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import server.common.ProtocolConstants;
import server.common.entity.exception.AuctionStateException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.exception.SelfBidException;
import server.common.enums.AuctionStatus;
import server.common.enums.BidStatus;
import server.service.listeners.RealTimeObserver;

/*
  ═══════════════════════════════════════════════════════════
   Auction — Logic 1 phòng đấu giá.

  Chịu trách nhiệm: Update Auction state trong RAM
    1. Nhận và validate bid (placeBid)
    2. Đảm bảo thread-safety bằng ReentrantLock
    3. Anti-sniping: gia hạn thời gian khi bid vào phút cuối
    4. Quản lý vòng đời trạng thái (OPEN → RUNNING → FINISHED → PAID/CANCELED)
    5. Thông báo tới Observer sau mỗi sự kiện
    6. Lưu toàn bộ lịch sử bid (bidHistory)

   CONCURRENCY:
    ReentrantLock bảo vệ toàn bộ vùng critical section (validateBid) trong placeBid().
    Quy tắc bắt buộc: LUÔN gọi lock.unlock() trong khối finally.
        → Tránh deadlock toàn bộ phiên đấu giá, không bid được nữa.

 */
public class Auction extends Entity {

    // ── Identity ─────────────────────────────────────────────────────────────
    private final Item          item;
    private final int        sellerId;

    // ── Time config ───────────────────────────────────────────────────────────
    private final LocalDateTime startTime;
    private       LocalDateTime endTime;

    /*  ── Anti-snipping config ───────────────────────────────────────────────────────────
     *   snipeWindowSeconds   — nếu bid trong khoảng này trước endTime → gia hạn
     *   snipeExtensionSeconds — số giây được thêm vào endTime
     */
    private final int snipeWindowSeconds;   // Số giây cuối cùng kích hoạt Anti-snipping
    private final int snipeExtensionSeconds;    // Thời gian cộng thêm nếu Anti-snipping
    private transient volatile boolean isTimeExtendedDuringRuntime = false;

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
    private final List<RealTimeObserver> observers;

    // ── Concurrency lock ──────────────────────────────────────────────────────
    /**
     * ReentrantLock thay vì synchronized vì:
     *   - Cho phép tryLock() với timeout (không block vô hạn)
     *   - Hỗ trợ fairness (fair=true → FIFO order cho waiting threads)
     *   - Dễ debug hơn synchronized
     */
    private final ReentrantLock lock = new ReentrantLock(true);



    // ─────────────────────────────────────────────────────────────────────────
    //  Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo auction mới ở tầng domain trước khi persist xuống DB.
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
    public Auction(Item item, int sellerId,
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
        this.observers             = new CopyOnWriteArrayList<>();
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
    public Auction(int id, LocalDateTime createdAt,
        Item item, int sellerId,
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
    //  PLACE BID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Đặt giá vào phiên đấu giá.
     *
     * Luồng xử lý :
     *   Step 1 — Kiểm tra trạng thái phiên, trạng thái bidder
     *   Step 2 — Acquire lock (chặn các thread khác)
     *   Step 3 — Recheck state
     *   Step 4 — Validate state giá trong vùng lock
     *   Step 5 — Cập nhật state
     *   Step 6 — Anti-sniping check
     *   Step 7 — Release lock
     *
     * @param bidder    Người đặt giá
     * @param amount    Số tiền muốn đặt
     * @param isAutoBid True nếu đặt tự động bởi AutoBidEngine
     * @return BidTransaction vừa được tạo
     * @throws AuctionStateException nếu phiên không ở trạng thái RUNNING
     * @throws InvalidBidException    nếu amount không hợp lệ
     */
    public PlaceBidResult placeBid(User bidder, BigDecimal amount, boolean isAutoBid) {

         // ── Step 1: Kiểm tra trạng thái — TRƯỚC khi lock ─────────────────────
        // Tối ưu: không cần lock để đọc status (read-only check)
        // Nếu đã FINISHED/CANCELED thì từ chối ngay, không tốn tài nguyên
        if (status != AuctionStatus.RUNNING || getSecondsRemaining() <= 0) {
            throw new AuctionStateException(getId(), status);
        }

        // Không cho phép seller tự bid vào phiên của mình hoặc winner self bid
        if (!isAutoBid && currentLeader != null){
            int winnerID = currentLeader.getId();
            if (bidder.getId() == winnerID){
                throw new SelfBidException(sellerId, this.getId());
            }
        }

        if (bidder.getId() == sellerId ) {
            throw new SelfBidException(sellerId, this.getId());
        }

         // ── Step 2: Acquire lock ──────────────────────────────────────────────
        BidTransaction tx = null;   // output
        boolean timeExtended = false;   // Anti-snipping flag = false (default)

        lock.lock();
        try {
            // ── Step 3: Validate state ──────────────────────────────────────────────
            // Kiểm tra lại status sau khi lock (có thể phiên vừa đóng trong khoảng thời gian giữa Step 1 và Step 2)
            if (status != AuctionStatus.RUNNING || getSecondsRemaining() <= 0) {
                throw new AuctionStateException(getId(), status);
            }

            // Kiểm tra endTime — guard chống scheduler trễ
            // Dù status RUNNING, nếu đã qua endTime thì từ chối bid.
            // Xử lý khi GC pause hoặc thread pool bận closeSession() chưa kịp chạy.
            if (LocalDateTime.now().isAfter(endTime)) {
                throw new AuctionStateException(getId(), status);
            }

            // ── Step 4: Validate amount ───────────────────────────────────────
            validateBid(amount); // False -> throw Exception

            // ── Step 5: Cập nhật state ────────────────────────────────────────
            // Snapshot Auction state trước khi cập nhật để return
            BigDecimal previousPrice   = this.currentPrice;
            User previousLeader        = this.currentLeader;
            LocalDateTime previousEnd  = this.endTime;
            LocalDateTime previousLast = this.lastBidTime;

            // Đánh dấu bid cũ là OUTBID, giữ lại reference để rollback đúng
            BidTransaction outbidTx = markCurrentWinnerOutbidAndReturn();

            // Tạo transaction mới
            tx = new BidTransaction(
                getId(), bidder.getId(), bidder.getUsername(), amount, isAutoBid
            );
            bidHistory.add(tx);

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
            return new PlaceBidResult(tx, outbidTx, timeExtended, previousPrice, previousLeader, previousEnd, previousLast);

        } finally {
            // ── Step 7: LUÔN LUÔN unlock, dù có exception ────────────────────
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validate bid amount.
     * @param amount    tiền đặt bid
     * @return InvalidBidException
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

    /**
     * Validate Auction time.
     * @param start     thời gian bắt đầu
     * @param end       thời gian kết thúc
     * @return IllegalArgumentException
     */
    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start))
            throw new IllegalArgumentException("end_time must be after start_time");
    }

    /**
     * Validate anti-snipping.
     * @param snipeExtensionSeconds     thời gian cộng thêm
     * @param snipeWindowSeconds        thời gian cuối cùng kích hoạt Anti-snipping
     * @return IllegalArgumentException
     */
    private void validateSnippingTimes(int snipeWindowSeconds,int snipeExtensionSeconds){
        if (snipeWindowSeconds < 0 || snipeExtensionSeconds < 0  ){
            throw new IllegalArgumentException("snipping_times must be positive");
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    //  Result wrapper — tránh trả nhiều giá trị rời rạc
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kết quả trả về của placeBid().
     * Service dùng để notify observer SAU khi DB commit thành công.
     *
     * @param tx                BidTransaction vừa được tạo
     * @param outbidTx          bid vừa bị markOutbid(), null nếu không có
     * @param timeExtended      true nếu anti-sniping đã kích hoạt và endTime bị kéo dài
     * @param previousPrice     giá trước khi bid
     * @param previousLeader    người dẫn đầu trước khi bid
     * @param previousEndTime   thời gian kết thúc trước khi bid (Check Anti-Snipping)
     * @param previousLastBid   thời gian đặt bid trước đó
     */
    public record PlaceBidResult(
        BidTransaction tx,
        BidTransaction outbidTx,        // ←
        boolean timeExtended,
        BigDecimal previousPrice,
        User previousLeader,
        LocalDateTime previousEndTime,
        LocalDateTime previousLastBid
    ) {}



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
     */
    public void closeSession() {
        lock.lock();
        try {
            // Kiểm tra trạng thái
            if (status != AuctionStatus.RUNNING)
                throw new IllegalStateException("Can only close a RUNNING auction. Current: " + status);

            // Check bid đã đặt
            boolean hasBids     = !bidHistory.isEmpty();

            // Check chạm giá sàn (Reserve Price)
            boolean meetsReserve = (reservePrice.compareTo(BigDecimal.ZERO) == 0) || (currentPrice.compareTo(reservePrice) >= 0);

            // Cập nhật Auction Status
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
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin Control
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Admin force-cancel phiên bất kỳ lúc nào.
     * @param reason    Lý do cancel
     */
    public void forceCancel(String reason) {
        lock.lock();
        try {
            // Check AuctionStatus
            if (status == AuctionStatus.PAID)
                throw new IllegalStateException("Cannot cancel a PAID auction");

            // Update AuctionStatus + đánh dấu bid LOST
            status = AuctionStatus.CANCELED;
            bidHistory.forEach(BidTransaction::markLost);
        } finally {
            lock.unlock();
        }
        System.out.println("[Auction " + getId() + "] Force canceled. Reason: " + reason);
    }



    // ─────────────────────────────────────────────────────────────────────────
    //  Anti-sniping — gọi trong vùng lock
    // ─────────────────────────────────────────────────────────────────────────

    /** Gia hạn endTime thêm seconds giây. Chỉ gọi trong vùng lock. */
    private void extendTime(int seconds) {
        this.endTime = this.endTime.plusSeconds(seconds);
        System.out.printf("[Anti-snipe] Auction %s extended by %ds. New endTime: %s%n",
            getId(), seconds, endTime);
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

    public void addObserver(RealTimeObserver observer) {
        synchronized (observers) { observers.add(observer); }
    }

    public void removeObserver(RealTimeObserver observer) {
        synchronized (observers) { observers.remove(observer); }
    }

// ─────────────────────────────────────────────────────────────────────────
//  Notify — chỉ gọi từ Service SAU KHI DB commit thành công
// ─────────────────────────────────────────────────────────────────────────

    /**
     * Phát sự kiện tới các Observer sau khi DB đã commit thành công.
     *
     * <p>Nhận toàn bộ {@link PlaceBidResult} thay vì các tham số rời rạc để
     * {@code notifyBidUpdated()} có thể đọc {@code previousLeader} từ snapshot
     * đúng thời điểm trước khi {@code placeBid()} ghi đè {@code currentLeader}.
     * Điều này ngăn bug gửi {@code onOutbid} nhầm cho người vừa thắng.</p>
     *
     * <p>Gọi ngoài lock — Observer có thể gọi lại các method khác của Auction
     * mà không gây deadlock.</p>
     *
     * @param result kết quả đầy đủ từ {@link #placeBid}, chứa tx, outbidTx,
     *               previousLeader và flag timeExtended
     */
    public void notifyBidCommitted(PlaceBidResult result) {
        notifyBidUpdated(result.tx(), result.previousLeader());
        if (result.timeExtended()) {
            notifyTimeExtended(snipeExtensionSeconds);
        }
    }

//    /**
//     * Notify toàn bộ RealTimeObserver (ClientHandler) rằng phiên đã đóng.
//     * Chỉ được gọi từ AuctionService.onAuctionClosed() — sau khi DB persist xong —
//     * đảm bảo client query lại luôn thấy dữ liệu nhất quán.
//     * KHÔNG gọi từ closeSession() hay forceCancel().
//     */
//    public void notifyRealTimeAuctionClosed(int winnerId, String itemName, BigDecimal finalPrice) {
//        List<RealTimeObserver> snapshot;
//        synchronized (observers) { snapshot = new ArrayList<>(observers); }
//        snapshot.forEach(obs -> {
//            try {
//                obs.onAuctionEnded(winnerId, getId(), itemName, finalPrice);
//            } catch (Exception e) {
//                System.err.println("[Auction] RealTimeObserver error: " + e.getMessage());
//            }
//        });
//    }
    /**
     * Gửi các sự kiện real-time sau khi một bid được commit thành công xuống DB.
     *
     * <p><b>Tại sao nhận {@code previousLeader} thay vì đọc {@code this.currentLeader}?</b><br>
     * {@code placeBid()} đã ghi đè {@code currentLeader = bidder} trước khi method này
     * được gọi. Nếu đọc {@code this.currentLeader} tại đây, ta sẽ lấy được người vừa
     * thắng — và gửi {@code onOutbid} nhầm cho chính họ. {@code previousLeader} là
     * snapshot được capture bên trong lock của {@code placeBid()}, đảm bảo luôn trỏ
     * đúng người vừa bị vượt qua.</p>
     *
     * <p><b>Tại sao guard {@code previousLeader != null}?</b><br>
     * Bid đầu tiên của phiên không có người dẫn trước ({@code previousLeader == null}).
     * Không guard → {@code NullPointerException} hoặc gửi {@code onOutbid} cho userId 0.</p>
     *
     * <p><b>Tại sao guard {@code previousLeader.getId() != bidderId}?</b><br>
     * Trường hợp phòng thủ: nếu cùng một người bid lại chính mình (không thể xảy ra do
     * {@code validateBid()} chặn, nhưng guard rõ ràng giúp unit test và review dễ hơn).</p>
     *
     * @param transaction  BidTransaction vừa được commit
     * @param previousLeader người đang dẫn đầu TRƯỚC khi bid này được đặt; null nếu là bid đầu tiên
     */
    private void notifyBidUpdated(BidTransaction transaction, User previousLeader) {
        List<RealTimeObserver> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }

        int bidderId  = transaction.getBidderId();
        int auctionId = this.getId();
        String itemName = this.item.getName();
        BigDecimal amount = transaction.getAmount();

        snapshot.forEach(obs -> {
            try {

                // 1. Broadcast toàn phòng (userId = NOTIFICATION_AUCTION_USER_ID là sentinel "all")
                obs.onBidPlacedSuccess(ProtocolConstants.NOTIFICATION_AUCTION_USER_ID, auctionId, itemName, amount);

                // 2. Thông báo OUTBID cho người vừa bị vượt qua.
                //    Guard: chỉ gửi khi thực sự có người bị vượt VÀ người đó khác người vừa thắng.
                if (previousLeader != null && previousLeader.getId() != bidderId) {
                    obs.onOutbid(previousLeader.getId(), auctionId, itemName, amount);
                }

            } catch (Exception e) {
                System.err.println("Observer error: " + e.getMessage());
            }
        });
    }


    private void notifyTimeExtended(int seconds) {
        List<RealTimeObserver> snapshot;
        synchronized (observers) { snapshot = new ArrayList<>(observers); }
        snapshot.forEach(obs -> {
            try {
                int auctionId = this.getId();
                obs.onTimeExtended(auctionId,this.getItem().getName(), seconds);
            } catch (Exception e) { System.err.println("Observer error: " + e.getMessage()); }

        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper queries
    // ─────────────────────────────────────────────────────────────────────────

    /** Đánh dấu bid WINNING hiện tại thành OUTBID.*/
    private BidTransaction markCurrentWinnerOutbidAndReturn() {
        for (int i = bidHistory.size() - 1; i >= 0; i--) {
            BidTransaction tx = bidHistory.get(i);
            if (tx.getStatus() == BidStatus.WINNING) {
                tx.markOutbid();
                return tx;  // ← trả về đúng bid vừa bị mark
            }
        }
        return null;  // không có winner trước đó
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

    /**
     * Đánh dấu phiên đã được thanh toán.
     * Gọi sau khi Payment được xác nhận.
     */
    public void markPaid() {
        if (status != AuctionStatus.FINISHED)
            throw new IllegalStateException("Can only mark FINISHED auction as PAID");
        this.status = AuctionStatus.PAID;
    }


    public boolean isLeading(int bidderId) {
        return currentLeader != null && (currentLeader.getId() == bidderId);
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
    public int          getSellerId()             { return sellerId; }
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
    public List<RealTimeObserver> getObservers()     { return new ArrayList<>(observers);}

    // ─────────────────────────────────────────────────────────────────────────
    //  Database
    // ─────────────────────────────────────────────────────────────────────────
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
     * Rollback RAM state.
     *
     * Gọi bơi AuctionService để Cancel Auto-Bid WINNING
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
    public void rollbackLastBid(BidTransaction failedTx,BidTransaction outbidTx, BigDecimal previousPrice, User previousLeader,LocalDateTime previousEndTime, LocalDateTime previousLastBidTime){ // thêmLocalDateTime previousLastBidTime) {
        lock.lock();
        try {
            // Xoá tx thất bại khỏi bidHistory
            // failedTx có thể null nếu caller không giữ được tham chiếu → xoá phần tử cuối
            if (failedTx != null) {
                bidHistory.remove(failedTx);
            } else if (!bidHistory.isEmpty()) {
                bidHistory.removeLast();
            }

            // Khôi phục currentPrice và currentLeader về snapshot trước bid
            this.currentPrice  = previousPrice;
            this.currentLeader = previousLeader;
            this.endTime       = previousEndTime;
            this.lastBidTime   = previousLastBidTime;

            // Restore đúng bid bằng reference, không tìm kiếm mò
            if (outbidTx != null) {
                outbidTx.restoreWinning();
            }
        } finally {
            lock.unlock();
        }
    }
    /**
     * Tìm bid OUTBID gần nhất của một bidder.
     * Dùng bởi AuctionService để lấy outbidTx khi rollback auto-bid.
     */
    public BidTransaction findLastOutbidOf(int bidderId) {
        for (int i = bidHistory.size() - 1; i >= 0; i--) {
            BidTransaction tx = bidHistory.get(i);
            if (tx.getBidderId() == bidderId && tx.getStatus() == BidStatus.OUTBID) {
                return tx;
            }
        }
        return null;
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  Override
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("Auction[%s] %s | Price: %.2f | Status: %s | Bids: %d",
            getId(), item.getName(), currentPrice, status, bidHistory.size());
    }
    @Override
    public void printInfo(){
        System.out.println(this);
    }
}