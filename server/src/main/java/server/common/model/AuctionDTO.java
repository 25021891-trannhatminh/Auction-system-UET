package server.common.model;

import server.common.enums.AuctionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO cho bảng AUCTIONS.
 *
 * <p>Dùng để truyền dữ liệu phiên đấu giá giữa các tầng (DAO, Service, Controller)
 * và trả về client.</p>
 */
public class AuctionDTO {

    /** ID phiên đấu giá (Primary Key) */
    private int auctionId;

    /** ID sản phẩm được đấu giá (FK -> ITEMS) */
    private int itemId;

    /** ID người bán (FK -> USERS) */
    private int sellerId;

    /** Thời điểm bắt đầu đấu giá */
    private Timestamp startTime;

    /** Thời điểm kết thúc đấu giá */
    private Timestamp endTime;

    /** Thời điểm có bid gần nhất */
    private Timestamp lastBidTime;

    /** Bước giá tối thiểu mỗi lần bid */
    private BigDecimal minBidIncrement;

    /** Giá tối thiểu chấp nhận (reserve price) */
    private BigDecimal reservePrice;

    /** Khoảng thời gian kích hoạt anti-sniping (giây) */
    private short snipeWindowSeconds;

    /** Thời gian gia hạn khi có bid cuối (giây) */
    private short snipeExtensionSeconds;

    /** Giá hiện tại cao nhất */
    private BigDecimal currentPrice;

    /** ID người đang dẫn đầu (nullable) */
    private Integer currentWinnerId;

    /** Trạng thái phiên đấu giá (RUNNING, ENDED, ...) */
    private AuctionStatus status;

    /** Thời điểm tạo phiên đấu giá */
    private Timestamp createdAt;

    // ========================== Constructors ==========================

    public AuctionDTO() {}

    public AuctionDTO(int auctionId, int itemId, int sellerId,
        Timestamp startTime, Timestamp endTime, Timestamp lastBidTime,
        BigDecimal minBidIncrement, BigDecimal reservePrice,
        short snipeWindowSeconds, short snipeExtensionSeconds,
        BigDecimal currentPrice, Integer currentWinnerId,
        AuctionStatus status, Timestamp createdAt) {

        this.auctionId = auctionId;
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.lastBidTime = lastBidTime;
        this.minBidIncrement = minBidIncrement;
        this.reservePrice = reservePrice;
        this.snipeWindowSeconds = snipeWindowSeconds;
        this.snipeExtensionSeconds = snipeExtensionSeconds;
        this.currentPrice = currentPrice;
        this.currentWinnerId = currentWinnerId;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ========================== Getters & Setters ==========================

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public int getItemId() { return itemId; }
    public void setItemId(int itemId) { this.itemId = itemId; }

    public int getSellerId() { return sellerId; }
    public void setSellerId(int sellerId) { this.sellerId = sellerId; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public Timestamp getLastBidTime() { return lastBidTime; }
    public void setLastBidTime(Timestamp lastBidTime) { this.lastBidTime = lastBidTime; }

    public BigDecimal getMinBidIncrement() { return minBidIncrement; }
    public void setMinBidIncrement(BigDecimal minBidIncrement) { this.minBidIncrement = minBidIncrement; }

    public BigDecimal getReservePrice() { return reservePrice; }
    public void setReservePrice(BigDecimal reservePrice) { this.reservePrice = reservePrice; }

    public short getSnipeWindowSeconds() { return snipeWindowSeconds; }
    public void setSnipeWindowSeconds(short snipeWindowSeconds) { this.snipeWindowSeconds = snipeWindowSeconds; }

    public short getSnipeExtensionSeconds() { return snipeExtensionSeconds; }
    public void setSnipeExtensionSeconds(short snipeExtensionSeconds) { this.snipeExtensionSeconds = snipeExtensionSeconds; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public Integer getCurrentWinnerId() { return currentWinnerId; }
    public void setCurrentWinnerId(Integer currentWinnerId) { this.currentWinnerId = currentWinnerId; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}