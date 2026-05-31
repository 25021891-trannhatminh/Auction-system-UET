package server.common.entity.BidModel;


import server.common.exception.AutoBidConfigException;
import server.common.enums.AutoBidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 Cấu hình Auto-Bid cho 1 bidder trong 1 Auction.

 Implements Comparable để dùng trong PriorityQueue của AutoBidEngine.
 Thứ tự ưu tiên (priority cao nhất được xử lý trước):
   1. maxBid cao hơn → ưu tiên hơn
   2. Nếu maxBid bằng nhau → đăng ký sớm hơn (registeredAt nhỏ hơn) → ưu tiên hơn (tie-breaking)


  DB:
   auto_bid_id  → id (từ Entity)
   auction_id   → auctionId
   bidder_id    → bidderId
   max_bid      → maxBid
   increment    → increment
   status       → status
   created_at   → registeredAt
 */
public class AutoBidConfig implements Comparable<AutoBidConfig> {

    private final int        auctionId;
    private final int        bidderId;
    private BigDecimal          maxBid;
    private BigDecimal              increment;
    private AutoBidStatus status;
    private final LocalDateTime registeredAt;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────
    public AutoBidConfig(int auctionId, int bidderId,
                         BigDecimal maxBid, BigDecimal increment) {
        validateConfig(maxBid,increment);
        this.auctionId    = auctionId;
        this.bidderId     = bidderId;
        this.maxBid       = maxBid;
        this.increment    = increment;
        this.status       = AutoBidStatus.ACTIVE;
        this.registeredAt = LocalDateTime.now();
    }

    /* Load từ DB — registeredAt được truyền vào */
    public AutoBidConfig(int auctionId, int bidderId,
                         BigDecimal maxBid, BigDecimal increment,
                         AutoBidStatus status, LocalDateTime registeredAt) {
        this.auctionId    = auctionId;
        this.bidderId     = bidderId;
        this.maxBid       = maxBid;
        this.increment    = increment;
        this.status       = status;
        this.registeredAt = registeredAt;
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  Bid calculation
    // ─────────────────────────────────────────────────────────────────────────
    /**
     Kiểm tra config này có thể đặt giá tại mức currentPrice không.
        Điều kiện: config phải đang ACTIVE và nextBid = currentPrice + increment <= maxBid.
    */
    public boolean canBid(BigDecimal currentPrice) {
        return status == AutoBidStatus.ACTIVE
            && (currentPrice.add(increment)).compareTo(maxBid) <= 0;
    }

    /**
     Tính giá đặt kế tiếp = currentPrice + increment.
        Gọi canBid() trước để đảm bảo không vượt maxBid.
     */
    public BigDecimal nextBidAmount(BigDecimal currentPrice) {
        return currentPrice.add(increment);
    }

    /**
     Kiểm tra config: maxBid , increment hợp lệ
     */
    private void validateConfig(BigDecimal maxBid, BigDecimal increment){
        if (maxBid.compareTo(BigDecimal.ZERO) <= 0 ||
            increment.compareTo(BigDecimal.ZERO) <= 0 ||
            maxBid.compareTo(increment) < 0) {
            throw new AutoBidConfigException("maxBid must be positive", maxBid, increment, null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Status management
    // ─────────────────────────────────────────────────────────────────────────

    public void cancel()   { this.status = AutoBidStatus.CANCELED; }
    public void complete() { this.status = AutoBidStatus.COMPLETED; }

    // ─────────────────────────────────────────────────────────────────────────
    //  Comparable
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Dùng cho PriorityQueue trong AutoBidEngine
     * So sánh ngược (max-heap behavior):
     * - maxBid cao hơn → compareTo trả về âm → đứng trước trong PriorityQueue
     * - Nếu bằng nhau → registeredAt sớm hơn → đứng trước (FIFO tie-breaking)
     */
    @Override
    public int compareTo(AutoBidConfig other) {
        int compare = other.maxBid.compareTo(this.maxBid); // desc by maxBid
        if (compare != 0) return compare;
        return this.registeredAt.compareTo(other.registeredAt);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Getters / Setters
    // ─────────────────────────────────────────────────────────────────────────
    public int        getAuctionId()    { return auctionId; }
    public int        getBidderId()     { return bidderId; }
    public BigDecimal        getMaxBid()       { return maxBid; }
    public BigDecimal        getIncrement()    { return increment; }
    public AutoBidStatus getStatus()       { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }

    @Override
    public String toString() {
        return String.format("AutoBidConfig{bidder=%s, max=%.2f, inc=%.2f, status=%s}",
            bidderId, maxBid, increment, status);
    }
}
