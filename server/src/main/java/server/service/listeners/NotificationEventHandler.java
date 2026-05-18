package server.service.listeners;

import server.service.NotificationService;
import server.common.enums.NotificationType;

import java.math.BigDecimal;

public class NotificationEventHandler implements AuctionEventListener {
  private final NotificationService notificationService;

  public NotificationEventHandler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Override
  public void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice) {
    notificationService.push(userId, "Outbid Alert!",
        "Someone bid higher ($" + newPrice + ") on [" + itemName + "].",
        NotificationType.OUTBID, auctionId);
  }

  @Override
  public void onBidPlaced(int bidderId, int auctionId, String itemName, BigDecimal amount) {
    notificationService.push(bidderId, "Bid Successful",
        "You placed a bid of $" + amount + " on [" + itemName + "].",
        NotificationType.BID_PLACED, auctionId);
  }

  @Override
  public void onAuctionStarted(int userId, int auctionId, String itemName) {
    notificationService.push(userId, "Auction Started",
        "The auction for [" + itemName + "] is now LIVE!",
        NotificationType.AUCTION_STARTED, auctionId);
  }

  @Override
  public void onAuctionEnded(int userId, int auctionId, String itemName, BigDecimal finalPrice) {
    notificationService.push(userId, "Auction Ended",
        "Auction for [" + itemName + "] has closed at $" + finalPrice + ".",
        NotificationType.AUCTION_ENDED, auctionId);
  }

  @Override
  public void onAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
    notificationService.push(winnerId, "Congratulations!",
        "You won [" + itemName + "] for $" + finalPrice + "!",
        NotificationType.AUCTION_WON, auctionId);
  }

  @Override
  public void onAuctionLost(int loserId, int auctionId, String itemName) {
    notificationService.push(loserId, "Auction Result",
        "The auction for [" + itemName + "] ended. Better luck next time!",
        NotificationType.AUCTION_LOST, auctionId);
  }

  @Override
  public void onPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount) {
    notificationService.push(buyerId, "Payment Required",
        "Please pay $" + amount + " for your won item: [" + itemName + "].",

        NotificationType.PAYMENT_DUE, auctionId);
  }

  @Override
  public void onPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount) {
    notificationService.push(sellerId, "Payment Received",
        "You received $" + amount + " for [" + itemName + "].",
        NotificationType.PAYMENT_RECEIVED, auctionId);
  }

  @Override
  public void onItemApproved(int sellerId, int itemId, String itemName) {
    notificationService.push(sellerId, "Item Approved",
        "Your item [" + itemName + "] was approved and is ready for auction.",
        NotificationType.ITEM_APPROVED, itemId);
  }

  @Override
  public void onItemRejected(int sellerId, int itemId, String itemName) {
    notificationService.push(sellerId, "Item Rejected",
        "Your item [" + itemName + "] was not approved. Please check the guidelines.",
        NotificationType.ITEM_REJECTED, itemId);
  }

  @Override
  public void onSystemNotification(int userId, String title, String message) {
    notificationService.push(userId, title, message, NotificationType.SYSTEM);
  }
}