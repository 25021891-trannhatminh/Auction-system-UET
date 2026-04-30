package exception;

/*
    Ném ra khi số tiền bid không hợp lệ:
        - amount <= currentHighestBid
        - amount <= 0
        - amount không đủ bước nhảy tối thiểu (minBidIncrement)
 */
public class InvalidBidException extends RuntimeException {
    private final double attemptedAmount;
    private final double currentPrice;

    public InvalidBidException(String message, double attemptedAmount, double currentPrice) {
        super(message);
        this.attemptedAmount = attemptedAmount;
        this.currentPrice = currentPrice;
    }

    public double getAttemptedAmount() { return attemptedAmount; }
    public double getCurrentPrice()    { return currentPrice; }
}
