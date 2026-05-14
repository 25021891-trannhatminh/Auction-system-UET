package server.common.model;

import server.common.enums.BidStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO cho bảng BIDTRANSACTIONS.
 *
 * <p>Dùng để lưu và truyền thông tin lịch sử đấu giá của một auction,
 * bao gồm số tiền bid, người bid, và trạng thái của bid.</p>
 */
public class BidHistoryDTO{


  /** ID lượt bid (Primary Key) */
  private int bidId;

  /** ID phiên đấu giá (FK -> AUCTIONS) */
  private int auctionId;

  /** ID người đấu giá (FK -> USERS) */
  private int bidderId;

  /** Số tiền bid */
  private BigDecimal amount;

  /** Có phải bid tự động (auto bid) hay không */
  private boolean isAutoBid;

  /**
   * Trạng thái bid:
   * PLACED / WINNING / OUTBID / WON / LOST
   */
  private BidStatus status;

  /** Thời điểm thực hiện bid */
  private Timestamp bidTime;

  // ========================== Constructors ==========================

  public BidHistoryDTO() {}

  public BidHistoryDTO(int bidId, int auctionId, int bidderId,
      BigDecimal amount, boolean isAutoBid,
      BidStatus status, Timestamp bidTime) {
    this.bidId = bidId;
    this.auctionId = auctionId;
    this.bidderId = bidderId;
    this.amount = amount;
    this.isAutoBid = isAutoBid;
    this.status = status;
    this.bidTime = bidTime;
  }

  // ========================== Getters & Setters ==========================

  public int getBidId() { return bidId; }
  public void setBidId(int bidId) { this.bidId = bidId; }

  public int getAuctionId() { return auctionId; }
  public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

  public int getBidderId() { return bidderId; }
  public void setBidderId(int bidderId) { this.bidderId = bidderId; }

  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }

  public boolean isAutoBid() { return isAutoBid; }
  public void setAutoBid(boolean autoBid) { isAutoBid = autoBid; }

  public BidStatus getStatus() { return status; }
  public void setStatus(BidStatus status) { this.status = status; }

  public Timestamp getBidTime() { return bidTime; }
  public void setBidTime(Timestamp bidTime) { this.bidTime = bidTime; }

  @Override
  public String toString() {
    return "BidHistoryDTO{" +
        "bidId=" + bidId +
        ", auctionId=" + auctionId +
        ", bidderId=" + bidderId +
        ", amount=" + amount +
        ", isAutoBid=" + isAutoBid +
        ", status=" + status +
        ", bidTime=" + bidTime +
        '}';
  }
}