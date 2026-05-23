package server.common.entity;


import server.common.enums.BidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
      Ghi lại giao dịch đặt BID trong 1 Auction. Chỉ được tạo khi amount > current_price

      Không được sửa chỉ status có thể thay đổi (WINNING → OUTBID khi có bid cao hơn).

      DB:
            bid_id      → id
            auction_id  → auctionId
            bidder_id   → bidderId
            amount      → amount
            bid_time    → bidTime
            is_auto_bid → isAutoBid
            status      → status

       UI:
          Lịch sử bid hiển thị danh sách BidTransaction (sort by bidTime DESC)
          Biểu đồ giá LineChart dùng (bidTime, amount) của từng BidTransaction
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

    /** Load từ DB */
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

    // ── Status transitions ──

    /* Gọi khi có bid mới vượt qua bid này */
    public void markOutbid() { this.status = BidStatus.OUTBID; }

    /* Gọi khi phiên kết thúc và bid này đang dẫn đầu */
    public void markWon()    { this.status = BidStatus.WON; }

    /* Gọi khi phiên kết thúc và bid này không phải cao nhất */
    public void markLost()   { this.status = BidStatus.LOST; }

    /**
     * Hoàn tác OUTBID → WINNING.
     * CHỈ gọi bởi Auction.rollbackLastBid() khi DB commit thất bại
     * và bid này cần được khôi phục làm leader.
     */
    public void restoreWinning() { this.status = BidStatus.WINNING; }

    // ── Getters ──

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
