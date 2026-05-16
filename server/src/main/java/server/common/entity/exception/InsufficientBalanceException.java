package server.common.entity.exception;

import java.math.BigDecimal;

// Ném ra khi Bidder không đủ số dư để đặt giá
public class InsufficientBalanceException extends RuntimeException {
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientBalanceException(BigDecimal required, BigDecimal available) {
        super("Insufficient balance. Required: " + required + ", Available: " + available);
        this.required = required;
        this.available = available;
    }

    public BigDecimal getRequired()  { return required; }
    public BigDecimal getAvailable() { return available; }
}
