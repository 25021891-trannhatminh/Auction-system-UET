package client.controller;

import java.math.BigDecimal;

/** Summary values rendered on the wallet/transactions overview card. */
final class TransactionPaymentSummary {
  BigDecimal paidByBuyer = BigDecimal.ZERO;
  BigDecimal receivedBySeller = BigDecimal.ZERO;
  BigDecimal pendingBuyerPay = BigDecimal.ZERO;
  BigDecimal pendingSellerReceive = BigDecimal.ZERO;
}
