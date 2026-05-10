package server.service.listeners;

public interface AuctionEventListener {
  void onOutbid(int userId, int auctionId, String itemName, double newPrice);

  void onAuctionEnded(int winnerId, int auctionId, String itemName, double finalPrice);

  void onBidPlaced(int bidderId, int auctionId, String itemName, double amount);

  void onAuctionStarted(int userId, int auctionId, String itemName);

  void onAuctionWon(int winnerId, int auctionId, String itemName, double finalPrice);

  void onAuctionLost(int loserId, int auctionId, String itemName);

  void onPaymentReceived(int sellerId, int auctionId, String itemName, double amount);

  void onPaymentDue(int buyerId, int auctionId, String itemName, double amount);

  void onItemApproved(int sellerId, int itemId, String itemName);

  void onItemRejected(int sellerId, int itemId, String itemName);

  void onSystemNotification(int userId, String title, String message);
}