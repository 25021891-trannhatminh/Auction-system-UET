package server.listeners;

import java.math.BigDecimal;
import server.common.enums.AuctionStatus;

/**
 * MARKER INTERFACE cho Business Logic.
 * Chỉ NotificationEventHandler và PaymentTriggerObserver được phép implement.
 * KHÔNG được dùng cho realtime UI.
 */
public interface BusinessEventListener{
  /**
   * Gọi đúng một lần khi auction đóng, SAU KHI DB đã persist trạng thái cuối.
   * Chỉ fire khi status là FINISHED hoặc CANCELED — không bao giờ fire giữa chừng.
   *
   * @param finalStatus FINISHED (có winner) hoặc CANCELED
   */
  default void onAuctionSessionClosed(int auctionId, String itemName,
      BigDecimal finalPrice,
      AuctionStatus finalStatus) {}

  void onAuctionEnded(int winnerId, int auctionId, String itemName, BigDecimal finalPrice);

  void onAuctionStarted(int userId, int auctionId, String itemName);

  void onAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice);

  void onAuctionLost(int loserId, int auctionId, String itemName);

  void onPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount);

  void onPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount);

  void onItemApproved(int sellerId, int itemId, String itemName);

  void onItemRejected(int sellerId, int itemId, String itemName);

  void onSystemNotification(int userId, String title, String message);
}