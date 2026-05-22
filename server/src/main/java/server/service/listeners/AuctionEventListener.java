package server.service.listeners;

import java.math.BigDecimal;

public interface AuctionEventListener {
  void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice);

  void onAuctionEnded(int winnerId, int auctionId, String itemName, BigDecimal finalPrice);

  void onBidPlaced(int bidderId, int auctionId, String itemName, BigDecimal amount);

  void onAuctionStarted(int userId, int auctionId, String itemName);

  void onAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice);

  void onAuctionLost(int loserId, int auctionId, String itemName);

  void onPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount);

  void onPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount);

  void onItemApproved(int sellerId, int itemId, String itemName);

  void onItemRejected(int sellerId, int itemId, String itemName);

  void onTimeExtended(int auctionId, String itemName, int addedSeconds);

  void onSystemNotification(int userId, String title, String message);
}