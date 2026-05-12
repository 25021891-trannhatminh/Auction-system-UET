package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.enums.AuctionStatus;
import server.common.model.AuctionDTO;
import server.repository.AuctionDAO;
import server.repository.BidDAO;
import server.service.listeners.AuctionEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AuctionService xử lý business logic chính cho hệ thống đấu giá:
 * - Quản lý đặt giá, bắt đầu/kết thúc phiên và điều phối thông báo.
 * - Áp dụng Observer Pattern để tách biệt logic nghiệp vụ và thông báo.
 */
public class AuctionService {

  private static final Logger logger = LoggerFactory.getLogger(AuctionService.class);

  // Sử dụng CopyOnWriteArrayList để an toàn khi đa luồng đăng ký/hủy listener
  private final List<AuctionEventListener> listeners = new CopyOnWriteArrayList<>();

  private final AuctionDAO auctionDAO;
  private final BidDAO bidDAO;

  public AuctionService() {
    this.auctionDAO = new AuctionDAO();
    this.bidDAO = new BidDAO();
  }

  /**
   * Đăng ký người nghe sự kiện (vd: NotificationHandler)
   */
  public void addListener(AuctionEventListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      listeners.add(listener);
      logger.info("addListener() - Registered: {}", listener.getClass().getSimpleName());
    }
  }

  /**
   * Gỡ bỏ người nghe sự kiện
   */
  public void removeListener(AuctionEventListener listener) {
    if (listeners.remove(listener)) {
      logger.info("removeListener() - Unregistered: {}", listener.getClass().getSimpleName());
    }
  }

  /**
   * Gửi thông báo toàn hệ thống (Broadcast).
   * Thường dùng khi bảo trì hoặc có thông báo chung.
   */
  public void broadcastSystemNotif(String title, String message) {
    logger.info("Broadcasting system notification to all active users");
    // ID -1 hoặc 0 thường được quy ước là Global/System
    notifySystemNotification(-1, title, message);
  }

  // ============================================================
  // Auction Business Logic
  // ============================================================

  /**
   * Xử lý đặt giá thời gian thực (Thread-safe)
   */
  public synchronized boolean processBid(int bidderId, int auctionId, BigDecimal amount, boolean autoBid) {
    try {
      AuctionDTO auction = auctionDAO.getById(auctionId);

      // 1. Kiểm tra sự tồn tại và trạng thái phiên
      if (auction == null) {
        logger.warn("processBid() - Auction {} not found", auctionId);
        return false;
      }

      if (auction.getStatus() != AuctionStatus.RUNNING) {
        logger.warn("processBid() - Auction {} is not in RUNNING status", auctionId);
        return false;
      }

      // 2. KHẮC PHỤC: Kiểm tra người bán không được tự đặt giá
      if (auction.getSellerId() == bidderId) {
        logger.warn("processBid() - Seller (ID:{}) attempted to bid on their own item", bidderId);
        return false;
      }

      // 3. Kiểm tra bước giá tối thiểu
      BigDecimal minRequired = auction.getCurrentPrice().add(auction.getMinBidIncrement());
      if (amount.compareTo(minRequired) <= 0) {
        logger.warn("processBid() - Bid too low. Bid: {}, Required: {}", amount, minRequired);
        return false;
      }

      Integer oldWinnerId = auction.getCurrentWinnerId();
      String itemName = "Auction #" + auctionId;

      // 4. Cập nhật Database qua BidDAO
      boolean success = bidDAO.placeBid(auctionId, bidderId, amount, autoBid);
      if (!success) {
        logger.error("processBid() - DB update failed for Auction: {}", auctionId);
        return false;
      }

      logger.info("processBid() - SUCCESS [Auction:{}, User:{}, Amount:{}]", auctionId, bidderId, amount);

      // 5. Điều phối thông báo
      notifyBidPlaced(bidderId, auctionId, itemName, amount.doubleValue());

      // Thông báo cho người vừa bị vượt giá (nếu có và không phải chính mình)
      if (oldWinnerId != null && oldWinnerId != bidderId) {
        notifyOutbid(oldWinnerId, auctionId, itemName, amount.doubleValue());
      }

      return true;
    } catch (Exception e) {
      logger.error("processBid() - Critical error during bidding process", e);
      return false;
    }
  }

  /**
   * Kích hoạt bắt đầu phiên đấu giá
   */
  public boolean startAuction(int auctionId) {
    AuctionDTO auction = auctionDAO.getById(auctionId);
    if (auction == null) return false;

    boolean updated = auctionDAO.updateStatus(auctionId, AuctionStatus.RUNNING);
    if (updated) {
      notifyAuctionStarted(auction.getSellerId(), auctionId, "Auction #" + auctionId);
      logger.info("startAuction() - Auction {} is LIVE", auctionId);
    }
    return updated;
  }

  /**
   * Kết thúc phiên đấu giá và xác định kết quả
   */
  public boolean endAuction(int auctionId) {
    AuctionDTO auction = auctionDAO.getById(auctionId);
    if (auction == null) return false;

    Integer winnerId = auction.getCurrentWinnerId();
    boolean finished = auctionDAO.finishAuction(auctionId, winnerId);

    if (!finished) {
      logger.error("endAuction() - Failed to finalize auction {}", auctionId);
      return false;
    }

    double finalPrice = auction.getCurrentPrice().doubleValue();
    String itemName = "Auction #" + auctionId;

    // 1. Thông báo cho người bán
    notifyAuctionEnded(auction.getSellerId(), auctionId, itemName, finalPrice);

    // 2. Xử lý thông báo thắng/thua
    if (winnerId != null) {
      // Thông báo thắng cuộc và yêu cầu thanh toán
      notifyAuctionWon(winnerId, auctionId, itemName, finalPrice);
      notifyPaymentDue(winnerId, auctionId, itemName, finalPrice);

      // Gửi thông báo cho những người tham gia nhưng không thắng (Auction Lost)
      List<Integer> allBidders = bidDAO.getBiddersByAuctionId(auctionId);
      for (Integer bidderId : allBidders) {
        if (!bidderId.equals(winnerId)) {
          notifyAuctionLost(bidderId, auctionId, itemName);
        }
      }
    }

    logger.info("endAuction() - Auction {} closed successfully", auctionId);
    return true;
  }

  /**
   * Xác nhận thanh toán thành công và thông báo cho các bên liên quan.
   * @param auctionId ID của phiên đấu giá
   * @return true nếu xử lý thành công
   */
  public boolean confirmPayment(int auctionId) {
    try {
      // 1. Lấy thông tin phiên đấu giá
      AuctionDTO auction = auctionDAO.getById(auctionId);
      if (auction == null) {
        logger.warn("confirmPayment() - Auction {} not found", auctionId);
        return false;
      }

      // 2. Kiểm tra trạng thái (Chỉ cho phép thanh toán khi phiên đã kết thúc và có người thắng)
      if (auction.getStatus() != AuctionStatus.FINISHED || auction.getCurrentWinnerId() == null) {
        logger.warn("confirmPayment() - Auction {} is not in a payable state", auctionId);
        return false;
      }

      // 3. Cập nhật trạng thái thanh toán trong Database (giả sử qua auctionDAO)
      // Lưu ý: Bạn nên có thêm trường paymentStatus trong DB
      boolean isUpdated = auctionDAO.updatePaymentStatus(auctionId, true);

      if (isUpdated) {
        int sellerId = auction.getSellerId();
        int buyerId = auction.getCurrentWinnerId();
        String itemName = "Auction #" + auctionId;
        double finalPrice = auction.getCurrentPrice().doubleValue();

        logger.info("confirmPayment() - Payment SUCCESS [Auction:{}, Buyer:{}, Seller:{}]",
            auctionId, buyerId, sellerId);

        // 4. Thông báo cho người bán: "Tiền đã về, hãy giao hàng"
        notifyPaymentReceived(sellerId, auctionId, itemName, finalPrice);

        // 5. Thông báo cho người mua: "Xác nhận bạn đã thanh toán thành công"
        notifySystemNotification(buyerId, "Thanh toán thành công",
            String.format("Bạn đã thanh toán thành công %.2f cho vật phẩm: %s", finalPrice, itemName));

        return true;
      }

      return false;
    } catch (Exception e) {
      logger.error("confirmPayment() - Critical error during payment confirmation", e);
      return false;
    }
  }

  /**
   * Duyệt vật phẩm (Gọi từ Admin)
   */
  public void approveItem(int sellerId, int itemId, String itemName) {
    notifyItemApproved(sellerId, itemId, itemName);
  }

  /**
   * Từ chối vật phẩm (Gọi từ Admin)
   */
  public void rejectItem(int sellerId, int itemId, String itemName) {
    notifyItemRejected(sellerId, itemId, itemName);
  }

  // ============================================================
  // Notification Dispatchers (Observer Pattern Implementation)
  // ============================================================

  private void notifyBidPlaced(int bidderId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onBidPlaced(bidderId, auctionId, itemName, amount));
  }

  private void notifyOutbid(int userId, int auctionId, String itemName, double newPrice) {
    listeners.forEach(l -> l.onOutbid(userId, auctionId, itemName, newPrice));
  }

  private void notifyAuctionStarted(int userId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionStarted(userId, auctionId, itemName));
  }

  private void notifyAuctionEnded(int userId, int auctionId, String itemName, double finalPrice) {
    listeners.forEach(l -> l.onAuctionEnded(userId, auctionId, itemName, finalPrice));
  }

  private void notifyAuctionWon(int winnerId, int auctionId, String itemName, double finalPrice) {
    listeners.forEach(l -> l.onAuctionWon(winnerId, auctionId, itemName, finalPrice));
  }

  private void notifyAuctionLost(int loserId, int auctionId, String itemName) {
    listeners.forEach(l -> l.onAuctionLost(loserId, auctionId, itemName));
  }

  private void notifyPaymentDue(int buyerId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onPaymentDue(buyerId, auctionId, itemName, amount));
  }

  public void notifyPaymentReceived(int sellerId, int auctionId, String itemName, double amount) {
    listeners.forEach(l -> l.onPaymentReceived(sellerId, auctionId, itemName, amount));
  }

  private void notifyItemApproved(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemApproved(sellerId, itemId, itemName));
  }

  private void notifyItemRejected(int sellerId, int itemId, String itemName) {
    listeners.forEach(l -> l.onItemRejected(sellerId, itemId, itemName));
  }

  public void notifySystemNotification(int userId, String title, String message) {
    listeners.forEach(l -> l.onSystemNotification(userId, title, message));
  }
}