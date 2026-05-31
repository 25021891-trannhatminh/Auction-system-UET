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
  final String balanceAfter;

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
      String walletNote,
      String balanceAfter
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
    this.balanceAfter = safe(balanceAfter);
  }

  boolean isBuyer() {
    return "BUYER".equalsIgnoreCase(role);
  }

  boolean isSeller() {
    return "SELLER".equalsIgnoreCase(role);
  }

  boolean isPayable() {
    return isBuyer() && isPendingSettlement();
  }

  boolean isPendingSettlement() {
    return !isWallet()
        && "PENDING".equalsIgnoreCase(paymentStatus)
        && "FINISHED".equalsIgnoreCase(auctionStatus);
  }

  boolean isCompletedPayment() {
    return !isWallet() && "COMPLETED".equalsIgnoreCase(paymentStatus);
  }

  boolean isRefundedPayment() {
    return !isWallet() && "REFUNDED".equalsIgnoreCase(paymentStatus);
  }

  boolean isRefundable() {
    return isSeller() && isCompletedPayment();
  }

  boolean isFailedPayment() {
    return !isWallet() && "FAILED".equalsIgnoreCase(paymentStatus);
  }

  boolean isWallet() {
    return "WALLET".equalsIgnoreCase(role);
  }

  boolean isDeposit() {
    return isWallet() && "DEPOSIT".equalsIgnoreCase(walletTxType);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
