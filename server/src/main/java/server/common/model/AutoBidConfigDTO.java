package server.common.model;

import server.common.enums.AutoBidStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO cho bảng AUTO_BID_CONFIGS.
 *
 * <p>Dùng để lưu và truyền cấu hình auto bid của người dùng trong một phiên đấu giá.
 * Hệ thống sẽ tự động đặt giá dựa trên maxBid và increment.</p>
 */
public class AutoBidConfigDTO {


    /** ID cấu hình auto bid (Primary Key) */
    private int autoBidId;

    /** ID phiên đấu giá (FK -> AUCTIONS) */
    private int auctionId;

    /** ID người đặt auto bid (FK -> USERS) */
    private int bidderId;

    /** Giá tối đa mà user chấp nhận */
    private BigDecimal maxBid;

    /** Bước nhảy mỗi lần auto bid */
    private BigDecimal increment;

    /** Trạng thái auto bid (ACTIVE, CANCELED, ...) */
    private AutoBidStatus status;

    /** Thời điểm tạo cấu hình */
    private Timestamp createdAt;

    /** Thời điểm cập nhật gần nhất */
    private Timestamp updatedAt;

    // ========================== Constructors ==========================

    public AutoBidConfigDTO() {}

    public AutoBidConfigDTO(int autoBidId, int auctionId, int bidderId,
                            BigDecimal maxBid, BigDecimal increment,
                            AutoBidStatus status,
                            Timestamp createdAt, Timestamp updatedAt) {

        this.autoBidId = autoBidId;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ========================== Getters & Setters ==========================

    public int getAutoBidId() { return autoBidId; }
    public void setAutoBidId(int autoBidId) { this.autoBidId = autoBidId; }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }

    public AutoBidStatus getStatus() { return status; }
    public void setStatus(AutoBidStatus status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}