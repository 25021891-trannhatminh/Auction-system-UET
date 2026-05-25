package server.service.listeners;

import java.math.BigDecimal;

/**
 * MARKER INTERFACE cho Realtime UI Update.
 * Chỉ ClientHandler (hoặc các class push socket realtime) được phép implement.
 */
public interface RealTimeObserver {
  void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice);

  void onBidPlacedSuccess(int bidderId, int auctionId, String itemName, BigDecimal amount);

  void onTimeExtended(int auctionId, String itemName, int addedSeconds);

//  void onAuctionEnded(int winnerId, int auctionId, String itemName, BigDecimal finalPrice);
}
