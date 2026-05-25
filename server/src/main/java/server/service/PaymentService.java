package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Auction;
import server.common.entity.Notification;
import server.common.entity.User;
import server.common.entity.manager.AuctionManager;
import server.common.enums.NotificationType;
import server.common.enums.PaymentStatus;
import server.common.enums.WalletTransactionType;
import server.common.model.PaymentDTO;
import server.common.model.WalletDTO;
import server.common.model.WalletUpdateEvent;
import server.database.DBConnection;
import server.network.NotificationDispatcher;
import server.repository.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class PaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentDAO           paymentDAO    = new PaymentDAO();
  private final WalletDAO            walletDAO     = new WalletDAO();
  private final WalletTransactionDAO walletTxDAO   = new WalletTransactionDAO();
  private final NotificationService  notificationService = new NotificationService();
  private final AuctionDAO           auctionDAO    = new AuctionDAO();

  // ==================== PUBLIC API ====================

  /** Idempotency check – dùng cho Observer, tránh tạo trùng payment record */
  public boolean isPaymentExistsForAuction(int auctionId) {
    return paymentDAO.existsByAuctionId(auctionId);
  }

  /**
   * Tạo bản ghi PENDING payment. Chỉ gọi khi auction kết thúc có người thắng.
   * Idempotent: kiểm tra exists trước khi tạo.
   */
  public boolean createPendingPayment(int auctionId, String itemName) {
    Optional<Auction> opt = AuctionManager.getInstance().getAuction(auctionId);
    if (opt.isEmpty()) {
      logger.warn("createPendingPayment() – Auction {} not found", auctionId);
      return false;
    }

    Auction auction = opt.get();

    // 1. Lấy thông tin người thắng (nếu không có → không tạo payment)
    User winner = auction.getCurrentLeader();
    if (winner == null) {
      logger.warn("createPendingPayment() – No winner for auction {}", auctionId);
      return false;
    }

    // 2. Parse buyerId và sellerId từ String sang int (an toàn)
    int buyerId, sellerId;
    try {
      buyerId = winner.getId();
      sellerId = auction.getSellerId();
    } catch (NumberFormatException e) {
      logger.error("createPendingPayment() – Invalid ID format. WinnerId: {}, SellerId: {}",
          winner.getId(), auction.getSellerId());
      return false;
    }

    if (buyerId <= 0 || sellerId <= 0) {
      logger.warn("createPendingPayment() – Invalid buyer/seller for auction {}", auctionId);
      return false;
    }

    // 3. Idempotency check
    if (isPaymentExistsForAuction(auctionId)) {
      logger.info("createPendingPayment() – Payment already exists for auction {}", auctionId);
      return true;   // Idempotent: coi như thành công
    }

    // 4. Tạo payment PENDING với amount = currentPrice (giá cuối)
    BigDecimal finalPrice = auction.getCurrentPrice();
    return paymentDAO.createPayment(auctionId, buyerId, sellerId, finalPrice);
  }

  /**
   * Xử lý thanh toán từ buyer cho auction (PENDING → COMPLETED).
   * Sử dụng transaction + lock để chống xử lý trùng.
   */
  public boolean processPayment(int auctionId, String itemName) {
    Connection conn = null;
    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      // 1. Lock & validate payment PENDING
      PaymentDTO payment = lockAndVerifyPayment(conn, auctionId, PaymentStatus.PENDING);
      if (payment == null) {
        conn.rollback();
        return false;
      }

      int buyerId   = payment.getBuyerId();
      int sellerId  = payment.getSellerId();
      BigDecimal amount = payment.getAmount();

      // 2. Lock ví buyer & seller
      WalletDTO[] wallets = lockWallets(conn, buyerId, sellerId);
      if (wallets == null) {
        conn.rollback();
        return false;
      }
      WalletDTO buyerWallet = wallets[0];
      WalletDTO sellerWallet = wallets[1];

      // 3. Kiểm tra số dư buyer, nếu thiếu → fail payment
      if (buyerWallet.getBalance().compareTo(amount) < 0) {
        paymentDAO.failPaymentInTx(conn, payment.getPaymentId());
        conn.commit();

        notificationService.push(buyerId, "Payment failed",
            String.format("Your wallet balance is not enough to pay %s VND for %s.",
                amount.stripTrailingZeros().toPlainString(), itemName),
            NotificationType.PAYMENT_DUE, auctionId);
        return false;
      }

      // 4. Chuyển tiền trong transaction
      walletDAO.withdrawInTx(conn, buyerWallet.getWalletId(), amount);
      walletDAO.depositInTx(conn, sellerWallet.getWalletId(), amount);

      // 5. Ghi log giao dịch
      walletTxDAO.logTransactionInTx(conn, buyerWallet.getWalletId(), buyerId,
          WalletTransactionType.PAYMENT, amount, auctionId,
          "Payment for auction #" + auctionId + " – " + itemName);
      walletTxDAO.logTransactionInTx(conn, sellerWallet.getWalletId(), sellerId,
          WalletTransactionType.PAYMENT, amount, auctionId,
          "Received payment for auction #" + auctionId + " – " + itemName);

      // 6. Cập nhật payment → COMPLETED
      paymentDAO.completePaymentInTx(conn, payment.getPaymentId());

      conn.commit();

      // 7. Post-commit: đồng bộ auction + notification + wallet update
      syncAuctionToPaid(auctionId, itemName);
      notifyPaymentSuccess(buyerId, sellerId, amount, itemName, auctionId);
      pushWalletUpdate(buyerId, buyerWallet.getBalance().subtract(amount));
      pushWalletUpdate(sellerId, sellerWallet.getBalance().add(amount));

      return true;

    } catch (SQLException e) {
      rollbackQuietly(conn);
      logger.error("processPayment() – DB error for auction {}", auctionId, e);
      return false;
    } finally {
      closeQuietly(conn);
    }
  }

  /**
   * Hoàn tiền (COMPLETED → REFUNDED). Dùng transaction + lock tương tự.
   */
  public boolean refundPayment(int auctionId, String itemName) {
    Connection conn = null;
    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      // 1. Lock & verify payment COMPLETED
      PaymentDTO payment = lockAndVerifyPayment(conn, auctionId, PaymentStatus.COMPLETED);
      if (payment == null) {
        conn.rollback();
        return false;
      }

      int buyerId   = payment.getBuyerId();
      int sellerId  = payment.getSellerId();
      BigDecimal amount = payment.getAmount();

      // 2. Lock ví seller & buyer (ngược hướng)
      WalletDTO[] wallets = lockWallets(conn, sellerId, buyerId);
      if (wallets == null) {
        conn.rollback();
        return false;
      }
      WalletDTO sellerWallet = wallets[0];
      WalletDTO buyerWallet = wallets[1];

      // 3. Kiểm tra số dư seller
      if (sellerWallet.getBalance().compareTo(amount) < 0) {
        // Không đủ tiền hoàn → rollback, không thay đổi gì
        conn.rollback();
        logger.warn("refundPayment() – Seller insufficient balance for refund");
        return false;
      }

      // 4. Chuyển tiền ngược: seller → buyer
      walletDAO.withdrawInTx(conn, sellerWallet.getWalletId(), amount);
      walletDAO.depositInTx(conn, buyerWallet.getWalletId(), amount);

      // 5. Ghi log
      walletTxDAO.logTransactionInTx(conn, sellerWallet.getWalletId(), sellerId,
          WalletTransactionType.REFUND, amount, auctionId,
          "Refund issued for auction #" + auctionId + " – " + itemName);
      walletTxDAO.logTransactionInTx(conn, buyerWallet.getWalletId(), buyerId,
          WalletTransactionType.REFUND, amount, auctionId,
          "Refund received for auction #" + auctionId + " – " + itemName);

      // 6. Cập nhật payment → REFUNDED
      paymentDAO.refundPaymentInTx(conn, payment.getPaymentId());

      conn.commit();

      // 7. Post-commit notification + wallet update
      notificationService.push(buyerId, "Refund processed",
          "You have been refunded " + formatMoney(amount) + " for " + itemName + ".",
          NotificationType.SYSTEM, auctionId);
      notificationService.push(sellerId, "Refund withdrawn",
          "The system returned " + formatMoney(amount) + " to the buyer for " + itemName + ".",
          NotificationType.SYSTEM, auctionId);

      pushWalletUpdate(buyerId, buyerWallet.getBalance().add(amount));
      pushWalletUpdate(sellerId, sellerWallet.getBalance().subtract(amount));

      return true;

    } catch (SQLException e) {
      rollbackQuietly(conn);
      logger.error("refundPayment() – DB error for auction {}", auctionId, e);
      return false;
    } finally {
      closeQuietly(conn);
    }
  }

  // ==================== HELPER METHODS ====================

  /**
   * Lock dòng payment theo auctionId và kiểm tra trạng thái mong đợi.
   * @return PaymentDTO nếu đúng trạng thái, null nếu không tồn tại hoặc sai trạng thái.
   *         Caller phải rollback nếu cần.
   */
  private PaymentDTO lockAndVerifyPayment(Connection conn, int auctionId, PaymentStatus expected) throws SQLException {
    PaymentDTO payment = paymentDAO.lockPaymentByAuctionId(conn, auctionId);
    if (payment == null) {
      logger.warn("lockAndVerifyPayment() – No payment for auctionId={}", auctionId);
      return null;
    }
    if (payment.getStatus() != expected) {
      logger.warn("lockAndVerifyPayment() – Payment {} is {} (expected {})",
          payment.getPaymentId(), payment.getStatus(), expected);
      return null;
    }
    return payment;
  }

  /**
   * Lock ví của hai user theo thứ tự ID để tránh deadlock.
   * @return mảng [firstUserWallet, secondUserWallet] hoặc null nếu lỗi.
   */
  private WalletDTO[] lockWallets(Connection conn, int userId1, int userId2) throws SQLException {
    WalletDTO w1, w2;
    if (userId1 < userId2) {
      w1 = walletDAO.lockByUserId(conn, userId1);
      w2 = walletDAO.lockByUserId(conn, userId2);
    } else {
      w2 = walletDAO.lockByUserId(conn, userId2);
      w1 = walletDAO.lockByUserId(conn, userId1);
    }
    if (w1 == null || w2 == null) {
      logger.error("lockWallets() – Wallet not found for userId1={}, userId2={}", userId1, userId2);
      return null;
    }
    return new WalletDTO[] { w1, w2 }; // theo đúng thứ tự userId1, userId2
  }

  /** Gửi thông báo thanh toán thành công cho buyer & seller */
  private void notifyPaymentSuccess(int buyerId, int sellerId, BigDecimal amount, String itemName, int auctionId) {
    notificationService.push(buyerId, "Payment successful",
        "You completed payment of " + formatMoney(amount) + " for " + itemName + ".",
        NotificationType.SYSTEM, auctionId);
    notificationService.push(sellerId, "Payment received",
        "You received " + formatMoney(amount) + " for " + itemName + ".",
        NotificationType.PAYMENT_RECEIVED, auctionId);
  }

  private String formatMoney(BigDecimal amount) {
    if (amount == null) {
      return "0 VND";
    }
    return amount.stripTrailingZeros().toPlainString() + " VND";
  }

  /** Đồng bộ Auction → PAID (RAM + DB) */
  private void syncAuctionToPaid(int auctionId, String itemName) {
    try {
      Optional<Auction> auctionOpt = AuctionManager.getInstance().getAuction(auctionId);
      if (auctionOpt.isPresent()) {
        Auction auction = auctionOpt.get();
        auction.markPaid();
        auctionDAO.updateStatus(auctionId, server.common.enums.AuctionStatus.PAID);
        logger.info("syncAuctionToPaid() – Auction {} state set to PAID", auctionId);
      } else {
        logger.warn("syncAuctionToPaid() – Auction {} not found in manager", auctionId);
      }
    } catch (Exception e) {
      logger.error("syncAuctionToPaid() – Failed to update auction status for id={}", auctionId, e);
    }
  }

  private void pushWalletUpdate(int userId, BigDecimal newBalance) {
    NotificationDispatcher.getInstance().submit(new WalletUpdateEvent(userId, newBalance));
  }

  private void rollbackQuietly(Connection conn) {
    if (conn == null) return;
    try { conn.rollback(); } catch (SQLException e) { logger.error("rollbackQuietly() failed", e); }
  }

  private void closeQuietly(Connection conn) {
    if (conn == null) return;
    try {
      conn.setAutoCommit(true);
      conn.close();
    } catch (SQLException ignored) {}
  }
}