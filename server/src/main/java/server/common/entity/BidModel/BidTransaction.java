package server.common.entity.BidModel;

import server.common.enums.BidStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ghi lại một giao dịch đặt giá (bid) trong một phiên đấu giá.
 *
 * <p>Chỉ được tạo khi {@code amount > currentPrice}.
 * Sau khi tạo, các trường đều bất biến — chỉ {@link #status} có thể thay đổi
 * (WINNING → OUTBID → WON/LOST) theo vòng đời của phiên đấu giá.</p>
 *
 * <p>DB:
 * <ul>
 *   <li>{@code bid_id}      → {@code id}</li>
 *   <li>{@code auction_id}  → {@code auctionId}</li>
 *   <li>{@code bidder_id}   → {@code bidderId}</li>
 *   <li>{@code amount}      → {@code amount}</li>
 *   <li>{@code bid_time}    → {@code bidTime}</li>
 *   <li>{@code is_auto_bid} → {@code isAutoBid}</li>
 *   <li>{@code status}      → {@code status}</li>
 * </ul></p>
 *
 * <p>UI:
 * <ul>
 *   <li>Lịch sử bid: hiển thị danh sách {@code BidTransaction} (sort by {@code bidTime DESC}).</li>
 *   <li>Biểu đồ giá LineChart: dùng cặp ({@code bidTime}, {@code amount}) của từng transaction.</li>
 * </ul></p>
 */
public class BidTransaction {

    private final int        id;
    private final int        auctionId;
    private final int        bidderId;
    private final String        bidderName;  // cache để hiển thị UI mà không cần JOIN DB
    private final BigDecimal    amount;
    private final LocalDateTime bidTime;
    private final boolean       isAutoBid;
    private BidStatus status;

    public BidTransaction(int auctionId, int bidderId, String bidderName,
                          BigDecimal amount, boolean isAutoBid) {
        this.id          = 0;
        this.auctionId   = auctionId;
        this.bidderId    = bidderId;
        this.bidderName  = bidderName;
        this.amount      = amount;
        this.bidTime     = LocalDateTime.now();
        this.isAutoBid   = isAutoBid;
        this.status      = BidStatus.WINNING;
    }

    /** Constructor load từ DB — toàn bộ trường đã được persist. */
    public BidTransaction(int id, int auctionId, int bidderId, String bidderName,
                          BigDecimal amount, LocalDateTime bidTime,
                          boolean isAutoBid, BidStatus status) {
        this.id          = id;
        this.auctionId   = auctionId;
        this.bidderId    = bidderId;
        this.bidderName  = bidderName;
        this.amount      = amount;
        this.bidTime     = bidTime;
        this.isAutoBid   = isAutoBid;
        this.status      = status;
    }

    // ── Status transitions ────────────────────────────────────────────────────

    /** Gọi khi có bid mới vượt qua bid này — chuyển WINNING → OUTBID. */
    public void markOutbid() { this.status = BidStatus.OUTBID; }

    /** Gọi khi phiên kết thúc và bid này đang dẫn đầu — chuyển WINNING → WON. */
    public void markWon()    { this.status = BidStatus.WON; }

    /** Gọi khi phiên kết thúc và bid này không phải cao nhất — chuyển thành LOST. */
    public void markLost()   { this.status = BidStatus.LOST; }

    /**
     * Hoàn tác OUTBID → WINNING.
     *
     * <p><b>CHỈ</b> gọi bởi {@code Auction.rollbackLastBid()} khi DB commit thất bại
     * và bid này cần được khôi phục làm leader.</p>
     */
    public void restoreWinning() { this.status = BidStatus.WINNING; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int        getId()          { return id; }
    public int        getAuctionId()   { return auctionId; }
    public int        getBidderId()    { return bidderId; }
    public String        getBidderName()  { return bidderName; }
    public BigDecimal        getAmount()      { return amount; }
    public LocalDateTime getBidTime()     { return bidTime; }
    public boolean       isAutoBid()     { return isAutoBid; }
    public BidStatus     getStatus()      { return status; }

    @Override
    public String toString() {
        return String.format("Bid[%s] by %s: %.2f at %s [%s]%s",
            id, bidderName, amount, bidTime, status,
            isAutoBid ? " (AUTO)" : "");
    }
}