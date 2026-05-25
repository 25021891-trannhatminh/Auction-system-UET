package server.service.listeners;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.entity.Auction;
import server.common.entity.manager.AuctionManager;
import server.handler.ResponseBuilder;
import server.network.NotificationDispatcher;
import server.service.NotificationService;
import server.common.ProtocolConstants;
import server.common.enums.NotificationType;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationEventHandler implements BusinessEventListener, RealTimeObserver {
  private static final Logger logger = LoggerFactory.getLogger(NotificationEventHandler.class);
  private final NotificationService notificationService;

  public NotificationEventHandler(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

// ==== RealTimeObserver implement ====
  @Override
  public void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice) {
    notificationService.pushRealtimeOnly(userId, "Outbid Alert!",
        "Someone bid higher ($" + newPrice + ") on [" + itemName + "].",
        NotificationType.OUTBID, auctionId);
  }

  /**
   * Được gọi 2 lần sau mỗi bid thành công:
   *   (a) bidderId = người vừa thắng  → PUSH_NOTIF cá nhân "Bid Successful"
   *   (b) bidderId = NOTIFICATION_AUCTION_USER_ID (0) → push AUCTION_BID_UPDATE
   *       giàu dữ liệu tới toàn bộ watcher của phiên qua NotificationDispatcher.
   *
   * Tách 2 luồng để PUSH_NOTIF cá nhân và state-sync toàn phòng không phụ thuộc nhau.
   * Nếu một trong hai fail, luồng kia vẫn chạy nhờ try-catch riêng ở Auction.notifyBidUpdated().
   */
  @Override
  public void onBidPlacedSuccess(int bidderId, int auctionId, String itemName, BigDecimal amount) {
    if (bidderId == ProtocolConstants.NOTIFICATION_AUCTION_USER_ID) {
      // Nhánh broadcast: push AUCTION_BID_UPDATE giàu dữ liệu tới toàn bộ watcher
      pushBidUpdateToWatchers(auctionId, amount);
      return;
    }
    // Nhánh cá nhân: thông báo riêng cho người vừa đặt giá thành công
    notificationService.pushRealtimeOnly(bidderId, "Bid Successful",
        "You placed a bid of $" + amount + " on [" + itemName + "].",
        NotificationType.BID_PLACED, auctionId);
  }

  /**
   * Build và push AUCTION_BID_UPDATE tới toàn bộ watcher của phiên.
   *
   * Đọc state mới nhất từ RAM (đã được auction.placeBid() cập nhật và DB commit thành công)
   * thay vì nhận tham số rời rạc — đảm bảo payload luôn nhất quán với state thực tế.
   *
   * @param auctionId ID phiên vừa có bid mới
   * @param amount    Số tiền bid vừa được commit (dùng để xác nhận log, không dùng trong payload)
   */
  private void pushBidUpdateToWatchers(int auctionId, BigDecimal amount) {
    Optional<Auction> opt = AuctionManager.getInstance().getAuction(auctionId);
    if (opt.isEmpty()) {
      logger.warn("pushBidUpdateToWatchers() - auction {} not found in RAM, skip push", auctionId);
      return;
    }
    Auction auction = opt.get();

    int leaderId     = auction.getCurrentLeader() != null ? auction.getCurrentLeader().getId()       : -1;
    String leaderName = auction.getCurrentLeader() != null ? auction.getCurrentLeader().getUsername() : "None";

    // Lấy bid vừa commit: isAutoBid từ winning bid hiện tại
    boolean isAutoBid = auction.getWinningBid() != null && auction.getWinningBid().isAutoBid();

    String message = ResponseBuilder.auctionBidUpdate(
        auctionId,
        auction.getCurrentPrice(),
        leaderId,
        leaderName,
        auction.getEndTime(),
        auction.getTotalBids(),
        isAutoBid
    );

    // pushRawToAuctionWatchers bypass queue của Dispatcher để tránh thêm latency
    // cho message realtime — đây là non-persistent, không cần đảm bảo delivery
    NotificationDispatcher.getInstance().pushRawToAuctionWatchers(auctionId, message);
    logger.debug("pushBidUpdateToWatchers() - pushed AUCTION_BID_UPDATE auctionId={} price={} leader={}",
        auctionId, auction.getCurrentPrice(), leaderName);
  }

  @Override
  public void onTimeExtended(int auctionId, String itemName, int addedSeconds) {
    notificationService.pushRealtimeOnly(0, "Auction Extended Time",
        "Auction for [" + itemName + "] has been extended by "
            + addedSeconds + " seconds.",
        NotificationType.TIME_EXTENDED, auctionId);
  }
  
  // ==== BusinessEventListener implement ====

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