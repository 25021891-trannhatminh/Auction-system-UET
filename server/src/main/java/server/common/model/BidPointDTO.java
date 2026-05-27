package server.common.model;

import java.math.BigDecimal;
public final class BidPointDTO {
  private final String bidTime;
  private final BigDecimal amount;

  public BidPointDTO(String bidTime, BigDecimal amount) {
    this.bidTime = bidTime;
    this.amount = amount;
  }

  public String getBidTime() { return bidTime; }
  public BigDecimal getAmount() { return amount; }

}