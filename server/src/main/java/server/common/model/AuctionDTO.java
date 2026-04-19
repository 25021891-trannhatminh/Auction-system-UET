package server.common.model;

import server.common.enums.AuctionStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class AuctionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int auctionId;
    private String itemName;
    private BigDecimal currentPrice;
    private Timestamp endTime;
    private AuctionStatus status;

    public AuctionDTO() {}

    public AuctionDTO(int auctionId, String itemName,
                      BigDecimal currentPrice,
                      Timestamp endTime,
                      AuctionStatus status) {
        this.auctionId = auctionId;
        this.itemName = itemName;
        this.currentPrice = currentPrice;
        this.endTime = endTime;
        this.status = status;
    }

    public int getAuctionId() { return auctionId; }
    public void setAuctionId(int auctionId) { this.auctionId = auctionId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
}