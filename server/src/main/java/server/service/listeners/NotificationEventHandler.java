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
    notificationService.push(userId, "You have been outbid",
        "A higher bid of " + formatMoney(newPrice) + " was placed on "
            + safeItemName(itemName) + ". Open the auction to bid again.",
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
    // Nhánh cá nhân: lưu DB và gửi thông báo riêng cho người vừa đặt giá thành công.
    notificationService.push(bidderId, "Bid placed",
        "Your bid of " + formatMoney(amount) + " for " + safeItemName(itemName)
            + " has been recorded.",
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
    notificationService.pushRealtimeOnly(0, "Auction extended",
        safeItemName(itemName) + " has been extended by "
            + addedSeconds + " seconds.",
        NotificationType.TIME_EXTENDED, auctionId);
  }

  private String safeItemName(String itemName) {
    if (itemName == null || itemName.isBlank()) {
      return "this auction";
    }
    return itemName.trim();
  }

  private String formatMoney(BigDecimal amount) {
    if (amount == null) {
      return "0";
    }
    return amount.stripTrailingZeros().toPlainString() + " VND";
  }
  
  // ==== BusinessEventListener implement ====

  @Override
  public void onAuctionStarted(int userId, int auctionId, String itemName) {
    notificationService.push(userId, "Auction started",
        "The auction for " + safeItemName(itemName) + " is now live.",
        NotificationType.AUCTION_STARTED, auctionId);
  }

  @Override
  public void onAuctionEnded(int userId, int auctionId, String itemName, BigDecimal finalPrice) {
    notificationService.push(userId, "Auction closed",
        "The auction for " + safeItemName(itemName) + " closed at "
            + formatMoney(finalPrice) + ".",
        NotificationType.AUCTION_ENDED, auctionId);
  }

  @Override
  public void onAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
    notificationService.push(winnerId, "Auction won",
        "You won " + safeItemName(itemName) + " for " + formatMoney(finalPrice) + ".",
        NotificationType.AUCTION_WON, auctionId);
  }

  @Override
  public void onAuctionLost(int loserId, int auctionId, String itemName) {
    notificationService.push(loserId, "Auction result",
        "The auction for " + safeItemName(itemName)
            + " has ended. Your bid was not the winning bid.",
        NotificationType.AUCTION_LOST, auctionId);
  }

  @Override
  public void onPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount) {
    notificationService.push(buyerId, "Payment required",
        "Please complete payment of " + formatMoney(amount) + " for "
            + safeItemName(itemName) + ".",
        NotificationType.PAYMENT_DUE, auctionId);
  }

  @Override
  public void onPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount) {
    notificationService.push(sellerId, "Payment received",
        "You received " + formatMoney(amount) + " for " + safeItemName(itemName) + ".",
        NotificationType.PAYMENT_RECEIVED, auctionId);
  }

  @Override
  public void onItemApproved(int sellerId, int itemId, String itemName) {
    notificationService.push(sellerId, "Item approved",
        "Your item " + safeItemName(itemName) + " was approved and can be scheduled for auction.",
        NotificationType.ITEM_APPROVED, itemId);
  }

  @Override
  public void onItemRejected(int sellerId, int itemId, String itemName) {
    notificationService.push(sellerId, "Item rejected",
        "Your item " + safeItemName(itemName)
            + " was not approved. Please review the submission details.",
        NotificationType.ITEM_REJECTED, itemId);
  }



  @Override
  public void onSystemNotification(int userId, String title, String message) {
    notificationService.push(userId, title, message, NotificationType.SYSTEM);
  }
}