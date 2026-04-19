package server.common.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class AutoBidDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int bidderId;
    private BigDecimal maxBid;
    private BigDecimal increment;

    public AutoBidDTO() {}

    public AutoBidDTO(int bidderId, BigDecimal maxBid, BigDecimal increment) {
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public int getBidderId() { return bidderId; }
    public void setBidderId(int bidderId) { this.bidderId = bidderId; }

    public BigDecimal getMaxBid() { return maxBid; }
    public void setMaxBid(BigDecimal maxBid) { this.maxBid = maxBid; }

    public BigDecimal getIncrement() { return increment; }
    public void setIncrement(BigDecimal increment) { this.increment = increment; }
}