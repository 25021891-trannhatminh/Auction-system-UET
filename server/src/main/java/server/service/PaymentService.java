package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Auction;
import server.common.entity.Notification;
import server.common.entity.manager.AuctionManager;
import server.common.enums.NotificationType;
import server.common.enums.PaymentStatus;
import server.common.enums.WalletTransactionType;
import server.common.model.NotificationEvent;
import server.common.model.PaymentDTO;
import server.common.model.WalletDTO;
import server.common.model.WalletUpdateEvent;
import server.database.DBConnection;
import server.network.NotificationDispatcher;
import server.repository.NotificationDAO;
import server.repository.PaymentDAO;
import server.repository.WalletDAO;
import server.repository.WalletTransactionDAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * PaymentService — điều phối toàn bộ vòng đời thanh toán.
 *
 * <h2>Nguyên tắc cốt lõi</h2>
 * <p>Mọi thao tác tài chính nằm trong <b>một DB transaction duy nhất</b> dùng
 * chung 1 {@link Connection}. Nếu bất kỳ bước nào thất bại → rollback toàn bộ.
 * Chỉ sau khi commit thành công mới push notification và WALLET_UPDATE realtime.</p>
 *
 * <h2>Phân công với DAOs</h2>
 * <ul>
 *   <li>{@link WalletDAO#lockByUserId} — lock row trước khi đọc balance (FOR UPDATE)</li>
 *   <li>{@link WalletDAO#withdrawInTx} / {@link WalletDAO#depositInTx} — thao tác tiền</li>
 *   <li>{@link WalletTransactionDAO#logTransactionInTx} — ghi audit log</li>
 *   <li>{@link PaymentDAO#completePaymentInTx} / {@link PaymentDAO#refundPaymentInTx} — cập nhật status</li>
 * </ul>
 * Tất cả nhận chung 1 Connection từ PaymentService → đảm bảo atomic.
 *
 * <h2>Flow processPayment</h2>
 * <pre>
 *  VALIDATE (ngoài transaction)
 *    ├─ payment tồn tại và đang PENDING
 *    ├─ buyer có ví, seller có ví
 *    └─ buyer đủ số dư (early check — trả lỗi nhanh)
 *
 *  BEGIN TRANSACTION
 *    ├─ lockByUserId(buyer)  ← FOR UPDATE — chặn concurrent write
 *    ├─ lockByUserId(seller) ← FOR UPDATE
 *    ├─ withdrawInTx(buyer)  ← check balance >= amount lần 2 tại SQL
 *    ├─ depositInTx(seller)
 *    ├─ logTransactionInTx(buyer,  PAYMENT, tiền ra)
 *    ├─ logTransactionInTx(seller, PAYMENT, tiền vào)
 *    └─ completePaymentInTx()  ← AND status = PENDING chặn double-processing
 *  COMMIT
 *
 *  SAU COMMIT
 *    ├─ PUSH_NOTIF  → buyer  (Payment Successful)
 *    ├─ PUSH_NOTIF  → seller (Payment Received)
 *    ├─ WALLET_UPDATE → buyer  (số dư mới — cập nhật UI ngay)
 *    └─ WALLET_UPDATE → seller (số dư mới — cập nhật UI ngay)
 * </pre>
 */
public class PaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

  // ============================================================
  // Dependencies — mỗi DAO chỉ lo đúng bảng của nó
  // ============================================================

  private final PaymentDAO           paymentDAO    = new PaymentDAO();
  private final WalletDAO            walletDAO     = new WalletDAO();
  private final WalletTransactionDAO walletTxDAO   = new WalletTransactionDAO();
  private final NotificationDAO      notifDAO      = new NotificationDAO();

  // ============================================================
  // Public API
  // ============================================================

  /**
   * Xử lý thanh toán cho phiên đấu giá.
   *
   * @param auctionId ID phiên đấu giá
   * @param itemName  Tên vật phẩm (dùng trong nội dung thông báo)
   * @return {@code true} nếu thanh toán hoàn tất thành công
   */
  public boolean processPayment(int auctionId, String itemName) {

    // ── Validate payment record ──────────────────────────────────────────
    PaymentDTO payment = paymentDAO.getPaymentByAuctionId(auctionId);
    if (payment == null) {
      logger.warn("processPayment() — No payment record for auctionId={}", auctionId);
      return false;
    }
    if (payment.getStatus() != PaymentStatus.PENDING) {
      logger.warn("processPayment() — Payment {} is {} (expected PENDING)",
          payment.getPaymentId(), payment.getStatus());
      return false;
    }

    int        buyerId   = payment.getBuyerId();
    int        sellerId  = payment.getSellerId();
    int        paymentId = payment.getPaymentId();
    BigDecimal amount    = payment.getAmount();

    // ── Validate ví tồn tại (ngoài transaction — early check) ────────────
    WalletDTO buyerWalletCheck  = walletDAO.getByUserId(buyerId);
    WalletDTO sellerWalletCheck = walletDAO.getByUserId(sellerId);

    if (buyerWalletCheck == null) {
      logger.error("processPayment() — Buyer {} has no wallet", buyerId);
      return false;
    }
    if (sellerWalletCheck == null) {
      logger.error("processPayment() — Seller {} has no wallet", sellerId);
      return false;
    }

    // Early check số dư — trả lỗi nhanh trước khi mở transaction
    if (buyerWalletCheck.getBalance().compareTo(amount) < 0) {
      logger.warn("processPayment() — Buyer {} insufficient balance: have={}, need={}",
          buyerId, buyerWalletCheck.getBalance(), amount);
      // Đánh dấu FAILED (standalone — không cần transaction vì không có tiền nào di chuyển)
      paymentDAO.failPayment(paymentId);
      pushNotif(buyerId,
          "Payment Failed",
          String.format("Insufficient balance to pay %s for [%s]. Please top up your wallet.",
              amount.toPlainString(), itemName),
          NotificationType.PAYMENT_DUE, auctionId);
      return false;
    }

    // ── Transaction ──────────────────────────────────────────────────────
    Connection conn = null;
    WalletDTO  buyerWalletLocked  = null;
    WalletDTO  sellerWalletLocked = null;

    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      // Lock cả 2 ví TRƯỚC khi đọc balance — tránh race condition
      // Thứ tự lock buyerId < sellerId để tránh deadlock khi 2 transaction nghịch chiều
      if (buyerId < sellerId) {
        buyerWalletLocked  = walletDAO.lockByUserId(conn, buyerId);
        sellerWalletLocked = walletDAO.lockByUserId(conn, sellerId);
      } else {
        sellerWalletLocked = walletDAO.lockByUserId(conn, sellerId);
        buyerWalletLocked  = walletDAO.lockByUserId(conn, buyerId);
      }

      if (buyerWalletLocked == null || sellerWalletLocked == null) {
        conn.rollback();
        logger.error("processPayment() — Could not lock wallets: buyer={}, seller={}",
            buyerWalletLocked, sellerWalletLocked);
        return false;
      }

      int buyerWalletId  = buyerWalletLocked.getWalletId();
      int sellerWalletId = sellerWalletLocked.getWalletId();

      // Trừ tiền buyer — check balance >= amount lần 2 tại SQL (chặn concurrent race)
      boolean withdrawn = walletDAO.withdrawInTx(conn, buyerWalletId, amount);
      if (!withdrawn) {
        conn.rollback();
        logger.warn("processPayment() — Concurrent withdraw failed: buyerWalletId={}", buyerWalletId);
        return false;
      }

      // Cộng tiền seller
      walletDAO.depositInTx(conn, sellerWalletId, amount);

      // Ghi audit log buyer (tiền ra)
      walletTxDAO.logTransactionInTx(conn,
          buyerWalletId, buyerId,
          WalletTransactionType.PAYMENT,
          amount,
          auctionId,
          "Payment for auction #" + auctionId + " — " + itemName);

      // Ghi audit log seller (tiền vào)
      walletTxDAO.logTransactionInTx(conn,
          sellerWalletId, sellerId,
          WalletTransactionType.PAYMENT,
          amount,
          auctionId,
          "Received payment for auction #" + auctionId + " — " + itemName);

      // Cập nhật payment → COMPLETED (AND status = PENDING chặn double-processing)
      boolean completed = paymentDAO.completePaymentInTx(conn, paymentId);
      if (!completed) {
        conn.rollback();
        logger.warn("processPayment() — Payment {} already processed (concurrent)", paymentId);
        return false;
      }

      conn.commit();
      logger.info("processPayment() — SUCCESS auctionId={}, buyer={}, seller={}, amount={}",
          auctionId, buyerId, sellerId, amount);

    } catch (SQLException e) {
      logger.error("processPayment() — DB error for auctionId={}", auctionId, e);
      rollbackQuietly(conn);
      return false;
    } finally {
      closeQuietly(conn);
    }

    // ── Sau commit: push realtime ─────────────────────────────────────────
    BigDecimal newBuyerBalance  = buyerWalletLocked.getBalance().subtract(amount);
    BigDecimal newSellerBalance = sellerWalletLocked.getBalance().add(amount);

    pushNotif(buyerId,
        "Payment Successful 🎉",
        String.format("You paid %s for [%s]. Enjoy your win!", amount.toPlainString(), itemName),
        NotificationType.PAYMENT_DUE, auctionId);

    pushNotif(sellerId,
        "Payment Received 💰",
        String.format("You received %s for [%s].", amount.toPlainString(), itemName),
        NotificationType.PAYMENT_RECEIVED, auctionId);

    pushWalletUpdate(buyerId,  newBuyerBalance);
    pushWalletUpdate(sellerId, newSellerBalance);

    return true;
  }

  /**
   * Hoàn tiền cho buyer (COMPLETED → REFUNDED).
   * Seller bị trừ tiền, buyer được cộng tiền — trong 1 transaction duy nhất.
   *
   * @param auctionId ID phiên đấu giá
   * @param itemName  Tên vật phẩm
   * @return {@code true} nếu hoàn tiền thành công
   */
  public boolean refundPayment(int auctionId, String itemName) {

    // ── Validate ─────────────────────────────────────────────────────────
    PaymentDTO payment = paymentDAO.getPaymentByAuctionId(auctionId);
    if (payment == null) {
      logger.warn("refundPayment() — No payment record for auctionId={}", auctionId);
      return false;
    }
    if (payment.getStatus() != PaymentStatus.COMPLETED) {
      logger.warn("refundPayment() — Payment {} is {} (expected COMPLETED)",
          payment.getPaymentId(), payment.getStatus());
      return false;
    }

    int        buyerId   = payment.getBuyerId();
    int        sellerId  = payment.getSellerId();
    int        paymentId = payment.getPaymentId();
    BigDecimal amount    = payment.getAmount();

    WalletDTO buyerWalletCheck  = walletDAO.getByUserId(buyerId);
    WalletDTO sellerWalletCheck = walletDAO.getByUserId(sellerId);

    if (buyerWalletCheck == null || sellerWalletCheck == null) {
      logger.error("refundPayment() — Missing wallet: buyerId={}, sellerId={}", buyerId, sellerId);
      return false;
    }
    if (sellerWalletCheck.getBalance().compareTo(amount) < 0) {
      logger.warn("refundPayment() — Seller {} insufficient balance for refund: have={}, need={}",
          sellerId, sellerWalletCheck.getBalance(), amount);
      return false;
    }

    // ── Transaction ──────────────────────────────────────────────────────
    Connection conn = null;
    WalletDTO  buyerWalletLocked  = null;
    WalletDTO  sellerWalletLocked = null;

    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      // =========================================================================
      // CHỐNG DEADLOCK BẰNG CƠ CHẾ SẮP XẾP THỨ TỰ KHÓA (LOCK ORDERING)
      // -------------------------------------------------------------------------
      // Mục đích: Ép tất cả các luồng giao dịch đồng thời phải chiếm khóa (Lock)
      // các tài khoản theo cùng một thứ tự ID tăng dần, triệt tiêu vòng lặp chờ nhau.
      //
      // Ví dụ kịch bản lỗi nếu KHÔNG sắp xếp (Mặc định khóa Buyer trước):
      //   - Luồng 1 (A chuyển tiền cho B): Khóa Buyer A -> Đợi khóa Seller B
      //   - Luồng 2 (B chuyển tiền cho A): Khóa Buyer B -> Đợi khóa Seller A
      //   ==> KẾT QUẢ: Hai luồng đứng đợi nhau mãi mãi -> Sập Connection Pool.
      //
      // Giải pháp (Cố định thứ tự): Nếu A < B, cả Luồng 1 và Luồng 2 đều phải
      // ưu tiên tranh chấp khóa tài khoản A trước. Luồng nào đến sau sẽ bị chặn (Block)
      // ngay từ bước 1, luồng đến trước xử lý xong xuôi sẽ giải phóng cho luồng sau.
      // =========================================================================
      if (buyerId < sellerId) {
        buyerWalletLocked  = walletDAO.lockByUserId(conn, buyerId);
        sellerWalletLocked = walletDAO.lockByUserId(conn, sellerId);
      } else {
        sellerWalletLocked = walletDAO.lockByUserId(conn, sellerId);
        buyerWalletLocked  = walletDAO.lockByUserId(conn, buyerId);
      }

      if (buyerWalletLocked == null || sellerWalletLocked == null) {
        conn.rollback();
        logger.error("refundPayment() — Could not lock wallets");
        return false;
      }

      int buyerWalletId  = buyerWalletLocked.getWalletId();
      int sellerWalletId = sellerWalletLocked.getWalletId();

      // Trừ tiền seller
      boolean withdrawn = walletDAO.withdrawInTx(conn, sellerWalletId, amount);
      if (!withdrawn) {
        conn.rollback();
        logger.warn("refundPayment() — Seller {} concurrent withdraw failed", sellerId);
        return false;
      }

      // Cộng tiền buyer
      walletDAO.depositInTx(conn, buyerWalletId, amount);

      // Audit log seller (tiền ra)
      walletTxDAO.logTransactionInTx(conn,
          sellerWalletId, sellerId,
          WalletTransactionType.REFUND,
          amount,
          auctionId,
          "Refund issued for auction #" + auctionId + " — " + itemName);

      // Audit log buyer (tiền vào)
      walletTxDAO.logTransactionInTx(conn,
          buyerWalletId, buyerId,
          WalletTransactionType.REFUND,
          amount,
          auctionId,
          "Refund received for auction #" + auctionId + " — " + itemName);

      // COMPLETED → REFUNDED
      boolean refunded = paymentDAO.refundPaymentInTx(conn, paymentId);
      if (!refunded) {
        conn.rollback();
        logger.warn("refundPayment() — Payment {} already refunded (concurrent)", paymentId);
        return false;
      }

      conn.commit();
      logger.info("refundPayment() — SUCCESS auctionId={}, buyer={}, seller={}, amount={}",
          auctionId, buyerId, sellerId, amount);

    } catch (SQLException e) {
      logger.error("refundPayment() — DB error for auctionId={}", auctionId, e);
      rollbackQuietly(conn);
      return false;
    } finally {
      closeQuietly(conn);
    }

    // ── Sau commit: push realtime ─────────────────────────────────────────
    BigDecimal newBuyerBalance  = buyerWalletLocked.getBalance().add(amount);
    BigDecimal newSellerBalance = sellerWalletLocked.getBalance().subtract(amount);

    pushNotif(buyerId,
        "Refund Received 💸",
        String.format("You received a refund of %s for [%s].", amount.toPlainString(), itemName),
        NotificationType.SYSTEM, auctionId);

    pushNotif(sellerId,
        "Refund Issued",
        String.format("A refund of %s was deducted for [%s].", amount.toPlainString(), itemName),
        NotificationType.SYSTEM, auctionId);

    pushWalletUpdate(buyerId,  newBuyerBalance);
    pushWalletUpdate(sellerId, newSellerBalance);

    return true;
  }


  /**
   * Tạo bản ghi Payment ở trạng thái PENDING khi auction kết thúc có người thắng.
   * Được gọi từ PaymentTriggerObserver hoặc AuctionService.onAuctionClosed()
   */
  public boolean createPendingPayment(int auctionId, String itemName) {
    try {
      // Lấy thông tin auction để lấy winner và seller
      Optional<Auction> auctionOpt = AuctionManager.getInstance().getAuction(String.valueOf(auctionId));
      if (auctionOpt.isEmpty()) {
        logger.warn("createPendingPayment - Auction not found: {}", auctionId);
        return false;
      }

      Auction auction = auctionOpt.get();
      if (auction.getCurrentLeader() == null) {
        logger.warn("createPendingPayment - No winner for auction {}", auctionId);
        return false;
      }

      int buyerId = Integer.parseInt(auction.getCurrentLeader().getId());
      int sellerId = Integer.parseInt(auction.getSellerId());
      BigDecimal amount = auction.getCurrentPrice();

      boolean created = paymentDAO.createPayment(auctionId, buyerId, sellerId, amount);

      if (created) {
        logger.info("✅ Created PENDING Payment for auctionId={}, buyerId={}, amount={}",
            auctionId, buyerId, amount);

        // Notify cho buyer
        pushNotif(buyerId, "Payment Required",
            String.format("You won [%s] for $%s. Please complete payment.", itemName, amount),
            NotificationType.PAYMENT_DUE, auctionId);
      }

      return created;

    } catch (Exception e) {
      logger.error("Failed to create pending payment for auctionId={}", auctionId, e);
      return false;
    }
  }

  // ============================================================
  // Private Helpers
  // ============================================================

  /**
   * Ghi notification vào DB + đẩy realtime qua Dispatcher.
   * DB fail chỉ log warning — không ảnh hưởng kết quả thanh toán.
   */
  private void pushNotif(int userId, String title, String message,
      NotificationType type, int relatedId) {
    try {
      Notification n = new Notification();
      n.setUserId(userId);
      n.setTitle(title);
      n.setContent(message);
      n.setType(type);
      n.setRead(false);
      n.setRelatedId(relatedId);
      notifDAO.insert(n);
    } catch (Exception e) {
      logger.warn("pushNotif() — DB insert failed for userId={} (non-critical)", userId, e);
    }
    NotificationDispatcher.getInstance().submit(
        new NotificationEvent(userId, title, message, type, relatedId)
    );
  }

  /**
   * Gửi WALLET_UPDATE về client để cập nhật Label số dư ngay lập tức.
   * Protocol: {@code WALLET_UPDATE|userId|newBalance}
   */
  private void pushWalletUpdate(int userId, BigDecimal newBalance) {
    NotificationDispatcher.getInstance().submit(
        new WalletUpdateEvent(userId, newBalance)
    );
  }

  private void rollbackQuietly(Connection conn) {
    if (conn == null) return;
    try { conn.rollback(); }
    catch (SQLException e) { logger.error("rollbackQuietly() failed", e); }
  }

  private void closeQuietly(Connection conn) {
    if (conn == null) return;
    try {
      conn.setAutoCommit(true);
      conn.close();
    } catch (SQLException ignored) {}
  }
}