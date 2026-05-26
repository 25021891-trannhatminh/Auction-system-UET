package client.controller;

/**
 * Immutable row model for the user Transactions table.
 *
 * <p>The controller receives these rows from USER_LIST_TRANSACTIONS. It intentionally keeps only
 * presentation-ready values so payment rendering stays separated from domain/payment services.</p>
 */
final class TransactionData {
  final String paymentId;
  final String auctionId;
  final String role;
  final String itemName;
  final String counterpartId;
  final String counterpartName;
  final String amount;
  final String paymentStatus;
  final String auctionStatus;
  final String createdAt;
  final String paidAt;
  final String walletTxId;
  final String walletTxType;
  final String walletNote;

  TransactionData(
      String paymentId,
      String auctionId,
      String role,
      String itemName,
      String counterpartId,
      String counterpartName,
      String amount,
      String paymentStatus,
      String auctionStatus,
      String createdAt,
      String paidAt,
      String walletTxId,
      String walletTxType,
      String walletNote
  ) {
    this.paymentId = safe(paymentId);
    this.auctionId = safe(auctionId);
    this.role = safe(role);
    this.itemName = safe(itemName);
    this.counterpartId = safe(counterpartId);
    this.counterpartName = safe(counterpartName);
    this.amount = safe(amount);
    this.paymentStatus = safe(paymentStatus);
    this.auctionStatus = safe(auctionStatus);
    this.createdAt = safe(createdAt);
    this.paidAt = safe(paidAt);
    this.walletTxId = safe(walletTxId);
    this.walletTxType = safe(walletTxType);
    this.walletNote = safe(walletNote);
  }

  boolean isBuyer() {
    return "BUYER".equalsIgnoreCase(role);
  }

  boolean isSeller() {
    return "SELLER".equalsIgnoreCase(role);
  }

  boolean isPayable() {
    return isBuyer()
        && "PENDING".equalsIgnoreCase(paymentStatus)
        && "FINISHED".equalsIgnoreCase(auctionStatus);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
