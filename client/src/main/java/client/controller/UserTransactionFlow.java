package client.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps post-auction payment/refund command state outside the large dashboard controller.
 *
 * <p>The UI remains responsible for validation and rendering, while this helper owns the
 * non-replayable command keys and the protocol strings sent to the server.</p>
 */
final class UserTransactionFlow {
  private final Set<String> payingAuctions = new HashSet<>();
  private final Set<String> refundingAuctions = new HashSet<>();

  boolean isPaymentSubmitting(TransactionData transaction) {
    return transaction != null && isPaymentSubmitting(transaction.auctionId);
  }

  boolean isPaymentSubmitting(String auctionId) {
    return payingAuctions.contains(normalizeAuctionKey(auctionId));
  }

  boolean isRefundSubmitting(TransactionData transaction) {
    return transaction != null && isRefundSubmitting(transaction.auctionId);
  }

  boolean isRefundSubmitting(String auctionId) {
    return refundingAuctions.contains(normalizeAuctionKey(auctionId));
  }

  void markPaymentSubmitting(String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    if (!key.isBlank()) {
      payingAuctions.add(key);
    }
  }

  void clearPaymentSubmitting(String auctionId) {
    payingAuctions.remove(normalizeAuctionKey(auctionId));
  }

  void markRefundSubmitting(String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    if (!key.isBlank()) {
      refundingAuctions.add(key);
    }
  }

  void clearRefundSubmitting(String auctionId) {
    refundingAuctions.remove(normalizeAuctionKey(auctionId));
  }

  void clearAll() {
    payingAuctions.clear();
    refundingAuctions.clear();
  }

  void dropResolvedSubmissions(List<TransactionData> latestTransactions) {
    if (latestTransactions == null || latestTransactions.isEmpty()) {
      clearAll();
      return;
    }

    payingAuctions.removeIf(key -> {
      TransactionData latest = findByAuctionId(latestTransactions, key);
      return latest == null || !latest.isPayable();
    });
    refundingAuctions.removeIf(key -> {
      TransactionData latest = findSellerTransactionByAuctionId(latestTransactions, key);
      return latest == null || !latest.isRefundable();
    });
  }

  String buildConfirmPaymentCommand(TransactionData transaction) {
    String auctionId = normalizeAuctionKey(transaction == null ? "" : transaction.auctionId);
    String itemName = commandSafeText(resolveItemName(transaction, auctionId));
    return itemName.isBlank()
        ? "CONFIRM_PAYMENT " + auctionId
        : "CONFIRM_PAYMENT " + auctionId + " " + itemName;
  }

  String buildRefundPaymentCommand(TransactionData transaction) {
    String auctionId = normalizeAuctionKey(transaction == null ? "" : transaction.auctionId);
    String itemName = commandSafeText(resolveItemName(transaction, auctionId));
    return itemName.isBlank()
        ? "REFUND_PAYMENT " + auctionId
        : "REFUND_PAYMENT " + auctionId + " " + itemName;
  }

  String normalizeAuctionKey(String auctionId) {
    return auctionId == null ? "" : auctionId.trim();
  }

  String commandSafeText(String value) {
    return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
  }

  private TransactionData findByAuctionId(List<TransactionData> transactions, String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    for (TransactionData transaction : transactions) {
      if (transaction != null && key.equals(normalizeAuctionKey(transaction.auctionId))) {
        return transaction;
      }
    }
    return null;
  }

  private TransactionData findSellerTransactionByAuctionId(
      List<TransactionData> transactions, String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    for (TransactionData transaction : transactions) {
      if (transaction != null
          && transaction.isSeller()
          && key.equals(normalizeAuctionKey(transaction.auctionId))) {
        return transaction;
      }
    }
    return null;
  }

  private String resolveItemName(TransactionData transaction, String auctionId) {
    if (transaction != null && transaction.itemName != null && !transaction.itemName.isBlank()) {
      return transaction.itemName;
    }
    return auctionId.isBlank() ? "" : "Auction #" + auctionId;
  }
}
