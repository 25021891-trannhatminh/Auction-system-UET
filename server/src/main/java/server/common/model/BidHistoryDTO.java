package server.common.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class BidHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private BigDecimal amount;
    private Timestamp bidTime;
    private boolean isAutoBid;

    public BidHistoryDTO() {}

    public BidHistoryDTO(String username, BigDecimal amount,
                         Timestamp bidTime, boolean isAutoBid) {
        this.username = username;
        this.amount = amount;
        this.bidTime = bidTime;
        this.isAutoBid = isAutoBid;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Timestamp getBidTime() { return bidTime; }
    public void setBidTime(Timestamp bidTime) { this.bidTime = bidTime; }

    public boolean isAutoBid() { return isAutoBid; }
    public void setAutoBid(boolean autoBid) { isAutoBid = autoBid; }
}