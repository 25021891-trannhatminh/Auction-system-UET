package server.service.listeners;

import java.math.BigDecimal;
import server.common.enums.AuctionStatus;

/**
 * MARKER INTERFACE cho Business Logic.
 * Chỉ NotificationEventHandler và PaymentTriggerObserver được phép implement.
 * KHÔNG được dùng cho realtime UI.
 */
public interface BusinessEventListener extends AuctionEventListener {
  /**
   * Gọi đúng một lần khi auction đóng, SAU KHI DB đã persist trạng thái cuối.
   * Chỉ fire khi status là FINISHED hoặc CANCELED — không bao giờ fire giữa chừng.
   *
   * @param finalStatus FINISHED (có winner) hoặc CANCELED
   */
  default void onAuctionSessionClosed(int auctionId, String itemName,
      BigDecimal finalPrice,
      AuctionStatus finalStatus) {}
}