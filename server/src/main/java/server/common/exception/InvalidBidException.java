package server.common.exception;

import java.math.BigDecimal;

/*
    Ném ra khi số tiền bid không hợp lệ:
        - amount <= currentHighestBid
        - amount <= 0
        - amount không đủ bước nhảy tối thiểu (minBidIncrement)
 */
public class InvalidBidException extends RuntimeException {
    private final BigDecimal attemptedAmount;
    private final BigDecimal currentPrice;

    public InvalidBidException(String message, BigDecimal attemptedAmount, BigDecimal currentPrice) {
        super(message);
        this.attemptedAmount = attemptedAmount;
        this.currentPrice = currentPrice;
    }

    public BigDecimal getAttemptedAmount() { return attemptedAmount; }
    public BigDecimal getCurrentPrice()    { return currentPrice; }
}
