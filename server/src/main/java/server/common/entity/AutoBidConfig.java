package server.common.entity;


import server.common.enums.AutoBidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 Setup Auto-Bid cho 1 bidder trong 1 Auction.

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

    private final String        auctionId;
    private final String        bidderId;
    private BigDecimal          maxBid;
    private BigDecimal              increment;
    private AutoBidStatus status;
    private final LocalDateTime registeredAt;

    public AutoBidConfig(String auctionId, String bidderId,
                         BigDecimal maxBid, BigDecimal increment) {
        if (maxBid.compareTo(BigDecimal.ZERO) <= 0)      throw new IllegalArgumentException("maxBid must be positive");
        if (increment.compareTo(BigDecimal.ZERO) <= 0)   throw new IllegalArgumentException("increment must be positive");
        if (maxBid.compareTo(increment) < 0) throw new IllegalArgumentException("maxBid must be >= increment");

        this.auctionId    = auctionId;
        this.bidderId     = bidderId;
        this.maxBid       = maxBid;
        this.increment    = increment;
        this.status       = AutoBidStatus.ACTIVE;
        this.registeredAt = LocalDateTime.now();
    }

    /* Load từ DB — registeredAt được truyền vào */
    public AutoBidConfig(String auctionId, String bidderId,
                         BigDecimal maxBid, BigDecimal increment,
                         AutoBidStatus status, LocalDateTime registeredAt) {
        this.auctionId    = auctionId;
        this.bidderId     = bidderId;
        this.maxBid       = maxBid;
        this.increment    = increment;
        this.status       = status;
        this.registeredAt = registeredAt;
    }

    // ── Bid calculation ──

    /*
     Kiểm tra config này có thể đặt giá tại mức currentPrice không.
     Điều kiện: config phải đang ACTIVE và nextBid = currentPrice + increment <= maxBid.
    */
    public boolean canBid(BigDecimal currentPrice) {
        return status == AutoBidStatus.ACTIVE
            && (currentPrice.add(increment)).compareTo(maxBid) <= 0;
    }

    /*
     Tính giá đặt kế tiếp = currentPrice + increment.
     Gọi canBid() trước để đảm bảo không vượt maxBid.
     */
    public BigDecimal nextBidAmount(BigDecimal currentPrice) {
        return currentPrice.add(increment);
    }

    // ── Status management ──

    public void cancel()   { this.status = AutoBidStatus.CANCELED; }
    public void complete() { this.status = AutoBidStatus.COMPLETED; }

    // ── Comparable: dùng cho PriorityQueue trong AutoBidEngine ──

    /*
     So sánh ngược (max-heap behavior):
       - maxBid cao hơn → compareTo trả về âm → đứng trước trong PriorityQueue
       - Nếu bằng nhau → registeredAt sớm hơn → đứng trước (FIFO tie-breaking)
     */
    @Override
    public int compareTo(AutoBidConfig other) {
        int compare = other.maxBid.compareTo(this.maxBid); // desc by maxBid
        if (compare != 0) return compare;
        return this.registeredAt.compareTo(other.registeredAt);
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String        getAuctionId()    { return auctionId; }
    public String        getBidderId()     { return bidderId; }
    public BigDecimal        getMaxBid()       { return maxBid; }
    public BigDecimal        getIncrement()    { return increment; }
    public AutoBidStatus getStatus()       { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }

//    public void setMaxBid(BigDecimal maxBid) {  // set MaxBid làm thay đổi AutoBidEngine
//        if (maxBid < this.increment)
//            throw new IllegalArgumentException("maxBid must be >= increment");
//        this.maxBid = maxBid;
//    }

//    public void setIncrement(BigDecimal increment) {  // set Increment làm thay đổi AutoBidEngine
//        if (increment <= 0)
//            throw new IllegalArgumentException("increment must be positive");
//        this.increment = increment;
//    }

    @Override
    public String toString() {
        return String.format("AutoBidConfig{bidder=%s, max=%.2f, inc=%.2f, status=%s}",
            bidderId, maxBid, increment, status);
    }
}
