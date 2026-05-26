package server.common.entity.exception;

import java.math.BigDecimal;

/**
 * Ném ra khi tham số cấu hình Auto-Bid vi phạm rule nghiệp vụ.
 *
 * Phân biệt với IllegalArgumentException (lỗi lập trình) —
 * AutoBidConfigException là lỗi do input người dùng, cần trả message rõ ràng ra UI.
 *
 * 3. AuctionService.registerAutoBid() — propagate lên BidHandler để trả lỗi có cấu trúc.
 */
public class AutoBidConfigException extends RuntimeException {

  private final BigDecimal maxBid;
  private final BigDecimal increment;
  private final BigDecimal currentPrice;  // null nếu lỗi không liên quan đến currentPrice

  public AutoBidConfigException(String message,
                                BigDecimal maxBid,
                                BigDecimal increment,
                                BigDecimal currentPrice) {
    super(message);
    this.maxBid       = maxBid;
    this.increment    = increment;
    this.currentPrice = currentPrice;
  }

  public BigDecimal getMaxBid()       { return maxBid; }
  public BigDecimal getIncrement()    { return increment; }
  public BigDecimal getCurrentPrice() { return currentPrice; }
}
