package client.controller;

/** Maps payment/refund server reason codes to safe UI messages. */
final class TransactionFailureMessages {
  private TransactionFailureMessages() {
  }

  static String payment(String reason) {
    String normalized = normalize(reason);
    return switch (normalized) {
      case "NOT_LOGGED_IN" -> "Please sign in before paying for a transaction.";
      case "INVALID_FORMAT" -> "Payment request is missing auction information.";
      case "PAYMENT_NOT_FOUND" -> "No payable transaction exists for this auction.";
      case "NOT_BUYER" -> "Only the winning buyer can pay this transaction.";
      case "PAYMENT_COMPLETED" -> "This transaction has already been paid.";
      case "PAYMENT_FAILED" -> "This transaction is already marked as failed.";
      case "PAYMENT_REFUNDED" -> "This transaction has already been refunded.";
      case "PAYMENT_NOT_COMPLETED" ->
          "Payment could not be completed. Please check wallet balance and transaction status.";
      default -> normalized.isBlank()
          ? "Payment could not be completed."
          : "Payment could not be completed: " + readable(normalized) + ".";
    };
  }

  static String refund(String reason) {
    String normalized = normalize(reason);
    return switch (normalized) {
      case "NOT_LOGGED_IN" -> "Please sign in before refunding a transaction.";
      case "INVALID_FORMAT" -> "Refund request is missing auction information.";
      case "PAYMENT_NOT_FOUND" -> "No completed payment exists for this auction.";
      case "PAYMENT_PENDING" -> "This payment is still pending and cannot be refunded yet.";
      case "PAYMENT_FAILED" -> "This payment failed and cannot be refunded.";
      case "PAYMENT_REFUNDED" -> "This payment has already been refunded.";
      case "REFUND_NOT_COMPLETED" ->
          "Refund could not be completed. Check seller wallet balance and payment status.";
      default -> normalized.isBlank()
          ? "Refund could not be completed."
          : "Refund could not be completed: " + readable(normalized) + ".";
    };
  }

  private static String normalize(String reason) {
    return reason == null ? "" : reason.trim().toUpperCase();
  }

  private static String readable(String normalized) {
    return normalized.replace('_', ' ').toLowerCase();
  }
}
