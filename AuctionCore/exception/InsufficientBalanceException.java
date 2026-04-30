package exception;

// Ném ra khi Bidder không đủ số dư để đặt giá 
public class InsufficientBalanceException extends RuntimeException {
    private final double required;
    private final double available;

    public InsufficientBalanceException(double required, double available) {
        super("Insufficient balance. Required: " + required + ", Available: " + available);
        this.required = required;
        this.available = available;
    }

    public double getRequired()  { return required; }
    public double getAvailable() { return available; }
}
