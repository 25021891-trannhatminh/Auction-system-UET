package server.repository;

import server.common.enums.PaymentStatus;
import server.common.model.PaymentDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code PAYMENTS}.
 *
 * <h2>Nguyên tắc thiết kế</h2>
 * <p>PaymentDAO chỉ đọc/ghi bảng {@code payments}.
 * Mọi logic nghiệp vụ (chuyển tiền wallet, ghi log wallet_transactions,
 * push notification) đều thuộc về {@link server.service.PaymentService}.</p>
 *
 * <h2>Hai nhóm method UPDATE</h2>
 * <ul>
 *   <li><b>Standalone</b> ({@link #completePayment}, {@link #failPayment},
 *       {@link #refundPayment}): tự quản lý Connection, dùng cho các trường
 *       hợp cần cập nhật status đơn lẻ (admin override, scheduled job...).</li>
 *   <li><b>In-transaction</b> ({@link #completePaymentInTx}, {@link #failPaymentInTx},
 *       {@link #refundPaymentInTx}): nhận Connection từ PaymentService, không
 *       commit/rollback/close — để toàn bộ thanh toán nằm trong 1 transaction.</li>
 * </ul>
 *
 * <h2>State machine</h2>
 * <pre>
 *   PENDING ──→ COMPLETED  (buyer thanh toán thành công)
 *   PENDING ──→ FAILED     (buyer không đủ tiền hoặc timeout)
 *   COMPLETED ──→ REFUNDED (admin hoàn tiền)
 * </pre>
 */
public class PaymentDAO {

    private static final Logger logger = LoggerFactory.getLogger(PaymentDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE = """
        SELECT payment_id, auction_id, buyer_id, seller_id, amount, status, paid_at, created_at
        FROM payments
        """;

    private static final String SQL_INSERT = """
        INSERT INTO payments (auction_id, buyer_id, seller_id, amount, status)
        VALUES (?, ?, ?, ?, ?)
        """;

    /** PENDING → COMPLETED: ghi nhận thời gian thanh toán, chặn double-processing bằng AND status = PENDING */
    private static final String SQL_COMPLETE =
        "UPDATE payments SET status = 'COMPLETED', paid_at = CURRENT_TIMESTAMP " +
            "WHERE payment_id = ? AND status = 'PENDING'";

    /** PENDING → FAILED: chỉ áp dụng khi còn PENDING */
    private static final String SQL_FAIL =
        "UPDATE payments SET status = 'FAILED' WHERE payment_id = ? AND status = 'PENDING'";

    /** COMPLETED → REFUNDED: chỉ hoàn tiền khi đã COMPLETED */
    private static final String SQL_REFUND =
        "UPDATE payments SET status = 'REFUNDED' WHERE payment_id = ? AND status = 'COMPLETED'";

    private static final String SQL_SELECT_BY_AUCTION =
        SQL_SELECT_BASE + " WHERE auction_id = ?";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE payment_id = ?";

    private static final String SQL_SELECT_BY_BUYER =
        SQL_SELECT_BASE + " WHERE buyer_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_BY_SELLER =
        SQL_SELECT_BASE + " WHERE seller_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_PENDING =
        SQL_SELECT_BASE + " WHERE status = 'PENDING' ORDER BY created_at ASC";

    private static final String SQL_FIND_BY_AUCTION_ID =
        "SELECT 1 FROM payments WHERE auction_id = ? LIMIT 1";

    // ============================================================
    // SQL tách từ ClientHandler - SRP
    // ============================================================

    /**
     * UNION ALL: lấy tất cả giao dịch liên quan đến user (payments + wallet deposit/withdraw).
     * Tách từ ClientHandler.sendUserTransactions().
     */
    private static final String SQL_USER_TRANSACTION_LIST = """
        SELECT *
        FROM (
            SELECT p.payment_id,
                   p.auction_id,
                   CASE WHEN p.buyer_id = ? THEN 'BUYER' ELSE 'SELLER' END AS user_role,
                   COALESCE(i.name, CONCAT('Auction #', p.auction_id)) AS item_name,
                   CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END AS counterpart_id,
                   COALESCE(counterpart.username,
                            CONCAT('User #', CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END))
                            AS counterpart_name,
                   p.amount,
                   p.status AS payment_status,
                   a.status AS auction_status,
                   p.created_at,
                   p.paid_at,
                   COALESCE(wt.tx_id, 0) AS wallet_tx_id,
                   COALESCE(wt.type, '') AS wallet_tx_type,
                   COALESCE(wt.note, '') AS wallet_note
            FROM payments p
            JOIN auctions a ON a.auction_id = p.auction_id
            LEFT JOIN items i ON i.item_id = a.item_id
            LEFT JOIN accounts counterpart ON counterpart.user_id =
                CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END
            LEFT JOIN (
                SELECT wt1.tx_id, wt1.user_id, wt1.ref_auction_id, wt1.type, wt1.note
                FROM wallet_transactions wt1
                JOIN (
                    SELECT user_id, ref_auction_id, MAX(tx_id) AS latest_tx_id
                    FROM wallet_transactions
                    WHERE type IN ('PAYMENT', 'REFUND')
                    GROUP BY user_id, ref_auction_id
                ) latest ON latest.latest_tx_id = wt1.tx_id
            ) wt ON wt.ref_auction_id = p.auction_id AND wt.user_id = ?
            WHERE p.buyer_id = ? OR p.seller_id = ?

            UNION ALL

            SELECT 0 AS payment_id,
                   COALESCE(wt.ref_auction_id, 0) AS auction_id,
                   'WALLET' AS user_role,
                   CASE
                       WHEN wt.type = 'DEPOSIT' THEN 'Wallet top-up'
                       WHEN wt.ref_auction_id IS NULL THEN 'Wallet'
                       ELSE COALESCE(i.name, CONCAT('Auction #', wt.ref_auction_id))
                   END AS item_name,
                   0 AS counterpart_id,
                   'Wallet' AS counterpart_name,
                   wt.amount,
                   'COMPLETED' AS payment_status,
                   'WALLET' AS auction_status,
                   wt.created_at,
                   wt.created_at AS paid_at,
                   wt.tx_id AS wallet_tx_id,
                   wt.type AS wallet_tx_type,
                   COALESCE(wt.note, '') AS wallet_note
            FROM wallet_transactions wt
            LEFT JOIN auctions a ON a.auction_id = wt.ref_auction_id
            LEFT JOIN items i ON i.item_id = a.item_id
            WHERE wt.user_id = ?
              AND wt.type IN ('DEPOSIT', 'WITHDRAW')
        ) tx
        ORDER BY COALESCE(tx.paid_at, tx.created_at) DESC, tx.payment_id DESC, tx.wallet_tx_id DESC
        """;

    /** Tách từ ClientHandler.resolvePaymentCompletionFailure() - SRP */
    private static final String SQL_PAYMENT_WALLET_INFO = """
        SELECT p.status,
               p.amount,
               bw.wallet_id AS buyer_wallet_id,
               bw.balance   AS buyer_balance,
               sw.wallet_id AS seller_wallet_id
        FROM payments p
        LEFT JOIN wallets bw ON bw.user_id = p.buyer_id
        LEFT JOIN wallets sw ON sw.user_id = p.seller_id
        WHERE p.auction_id = ?
        """;

    /** Tách từ ClientHandler.resolvePaymentAuthorization() - SRP */
    private static final String SQL_PAYMENT_BUYER_STATUS =
        "SELECT buyer_id, status FROM payments WHERE auction_id = ?";
    // ============================================================
    // INSERT Method
    // ============================================================

    /**
     * Tạo bản ghi thanh toán mới ở trạng thái PENDING.
     * Gọi từ {@code AuctionService.onAuctionClosed()} khi có người thắng.
     *
     * @param auctionId ID phiên đấu giá
     * @param buyerId   ID người mua (người thắng)
     * @param sellerId  ID người bán
     * @param amount    Số tiền phải thanh toán
     * @return {@code true} nếu tạo thành công
     */
    public boolean createPayment(int auctionId, int buyerId, int sellerId, BigDecimal amount) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, auctionId);
            ps.setInt(2, buyerId);
            ps.setInt(3, sellerId);
            ps.setBigDecimal(4, amount);
            ps.setString(5, PaymentStatus.PENDING.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("createPayment() failed for auctionId={}", auctionId, e);
            return false;
        }
    }

    // ============================================================
    // Standalone UPDATE Methods
    // (tự quản lý Connection)
    // ============================================================

    /**
     * PENDING → COMPLETED (standalone).
     * Điều kiện {@code AND status = 'PENDING'} chặn double-processing.
     */
    public boolean completePayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_COMPLETE)) {

            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("completePayment() failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * PENDING → FAILED (standalone).
     */
    public boolean failPayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_FAIL)) {

            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("failPayment() failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * COMPLETED → REFUNDED (standalone).
     */
    public boolean refundPayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_REFUND)) {

            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("refundPayment() failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    // ============================================================
    // In-Transaction UPDATE Methods
    // (nhận Connection từ ngoài — KHÔNG commit/rollback/close)
    // Dùng từ PaymentService để gộp vào 1 transaction với wallet operations
    // ============================================================

    /**
     * PENDING → COMPLETED trong transaction đang mở.
     *
     * <p>Trả về {@code false} nếu payment không còn ở PENDING
     * (ví dụ: luồng khác đã xử lý trước — concurrent race condition).</p>
     *
     * <p><b>Không gọi commit/rollback/close.</b></p>
     *
     * @param conn      Connection đang trong transaction
     * @param paymentId ID payment cần cập nhật
     * @return {@code true} nếu update thành công (đúng 1 row bị ảnh hưởng)
     * @throws SQLException nếu có lỗi DB
     */
    public boolean completePaymentInTx(Connection conn, int paymentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_COMPLETE)) {
            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * PENDING → FAILED trong transaction đang mở.
     *
     * <p><b>Không gọi commit/rollback/close.</b></p>
     *
     * @param conn      Connection đang trong transaction
     * @param paymentId ID payment cần cập nhật
     * @throws SQLException nếu có lỗi DB
     */
    public void failPaymentInTx(Connection conn, int paymentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_FAIL)) {
            ps.setInt(1, paymentId);
            ps.executeUpdate();
        }
    }

    /**
     * COMPLETED → REFUNDED trong transaction đang mở.
     *
     * <p>Trả về {@code false} nếu payment không còn ở COMPLETED.</p>
     *
     * <p><b>Không gọi commit/rollback/close.</b></p>
     *
     * @param conn      Connection đang trong transaction
     * @param paymentId ID payment cần cập nhật
     * @return {@code true} nếu update thành công
     * @throws SQLException nếu có lỗi DB
     */
    public boolean refundPaymentInTx(Connection conn, int paymentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_REFUND)) {
            ps.setInt(1, paymentId);
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Tìm payment theo auctionId.
     * Vì auction_id là UNIQUE trong bảng payments, luôn trả về nhiều nhất 1 bản ghi.
     */
    public PaymentDTO getPaymentByAuctionId(int auctionId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getPaymentByAuctionId() failed for auctionId={}", auctionId, e);
        }
        return null;
    }

    /**
     * Tìm payment theo paymentId.
     */
    public PaymentDTO getPaymentById(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getPaymentById() failed for paymentId={}", paymentId, e);
        }
        return null;
    }

    /**
     * Lịch sử thanh toán của buyer, mới nhất trước.
     */
    public List<PaymentDTO> getPaymentsByBuyer(int buyerId) {
        List<PaymentDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_BUYER)) {

            ps.setInt(1, buyerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getPaymentsByBuyer() failed for buyerId={}", buyerId, e);
        }
        return list;
    }
    public boolean existsByAuctionId(int auctionId) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_AUCTION_ID)) {

            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // Nếu tìm thấy dù chỉ 1 dòng => trả về true
            }
        } catch (SQLException e) {
            // Log lỗi nếu cần thiết
            return false;
        }
    }

    /**
     * Lịch sử giao dịch của seller, mới nhất trước.
     */
    public List<PaymentDTO> getPaymentsBySeller(int sellerId) {
        List<PaymentDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SELLER)) {

            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getPaymentsBySeller() failed for sellerId={}", sellerId, e);
        }
        return list;
    }

    public PaymentDTO lockPaymentByAuctionId(Connection conn, int auctionId) throws SQLException {
        String sql = SQL_SELECT_BASE + " WHERE auction_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public boolean createPayment(PaymentDTO payment) {
        return createPayment(
            payment.getAuctionId(),
            payment.getBuyerId(),
            payment.getSellerId(),
            payment.getAmount()
        );
    }

    /**
     * Danh sách các payment đang chờ xử lý (PENDING), cũ nhất trước.
     * Dùng cho admin dashboard hoặc scheduled job đối soát.
     */
    public List<PaymentDTO> getPendingPayments() {
        List<PaymentDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_PENDING);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            logger.error("getPendingPayments() failed", e);
        }
        return list;
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    private PaymentDTO mapRow(ResultSet rs) throws SQLException {
        return new PaymentDTO(
            rs.getInt("payment_id"),
            rs.getInt("auction_id"),
            rs.getInt("buyer_id"),
            rs.getInt("seller_id"),
            rs.getBigDecimal("amount"),
            PaymentStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("paid_at"),
            rs.getTimestamp("created_at")
        );
    }

    // ============================================================
    // Methods tách từ ClientHandler - SRP
    // ============================================================

    /**
     * Lấy danh sách giao dịch (payment + wallet) của user cho Transaction history.
     * Tách từ ClientHandler.sendUserTransactions() – SQL UNION ALL lớn.
     *
     * @param userId ID người dùng
     * @return danh sách {@link UserTransactionRow} đã sắp xếp mới nhất trước
     */
    public List<UserTransactionRow> getUserTransactionRows(int userId) {
        List<UserTransactionRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_USER_TRANSACTION_LIST)) {
            // 8 tham số cho câu UNION ALL (giữ nguyên thứ tự như ClientHandler gốc)
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            ps.setInt(5, userId);
            ps.setInt(6, userId);
            ps.setInt(7, userId);
            ps.setInt(8, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new UserTransactionRow(
                        rs.getInt("payment_id"),
                        rs.getInt("auction_id"),
                        rs.getString("user_role"),
                        rs.getString("item_name"),
                        rs.getInt("counterpart_id"),
                        rs.getString("counterpart_name"),
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_status"),
                        rs.getString("auction_status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("paid_at"),
                        rs.getInt("wallet_tx_id"),
                        rs.getString("wallet_tx_type"),
                        rs.getString("wallet_note")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("getUserTransactionRows() failed for userId={}", userId, e);
        }
        return rows;
    }

    /**
     * Lấy thông tin payment + wallet để phân tích nguyên nhân thất bại khi thanh toán.
     * Tách từ ClientHandler.resolvePaymentCompletionFailure().
     *
     * @param auctionId ID phiên đấu giá
     * @return {@link PaymentWalletInfo} hoặc {@code null} nếu không có payment
     */
    public PaymentWalletInfo getPaymentWalletInfo(int auctionId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_PAYMENT_WALLET_INFO)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int bwId = rs.getInt("buyer_wallet_id");
                Integer buyerWalletId = rs.wasNull() ? null : bwId;
                int swId = rs.getInt("seller_wallet_id");
                Integer sellerWalletId = rs.wasNull() ? null : swId;
                return new PaymentWalletInfo(
                    rs.getString("status"),
                    rs.getBigDecimal("amount"),
                    buyerWalletId,
                    rs.getBigDecimal("buyer_balance"),
                    sellerWalletId
                );
            }
        } catch (SQLException e) {
            logger.error("getPaymentWalletInfo() failed for auctionId={}", auctionId, e);
        }
        return null;
    }

    /**
     * Lấy buyer_id và status của payment theo auctionId để kiểm tra authorization.
     * Tách từ ClientHandler.resolvePaymentAuthorization().
     *
     * @param auctionId ID phiên đấu giá
     * @return {@link PaymentBuyerStatus} hoặc {@code null} nếu không tìm thấy
     */
    public PaymentBuyerStatus getPaymentBuyerStatus(int auctionId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_PAYMENT_BUYER_STATUS)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PaymentBuyerStatus(rs.getInt("buyer_id"), rs.getString("status"));
                }
            }
        } catch (SQLException e) {
            logger.error("getPaymentBuyerStatus() failed for auctionId={}", auctionId, e);
        }
        return null;
    }

    // ============================================================
    // Inner Records (kết quả truy vấn tách từ ClientHandler)
    // ============================================================

    /** Một dòng giao dịch (payment hoặc wallet) trong màn hình Transaction history. */
    public record UserTransactionRow(
        int paymentId, int auctionId, String userRole, String itemName,
        int counterpartId, String counterpartName, java.math.BigDecimal amount,
        String paymentStatus, String auctionStatus,
        java.sql.Timestamp createdAt, java.sql.Timestamp paidAt,
        int walletTxId, String walletTxType, String walletNote
    ) {}

    /** Thông tin payment + wallet dùng để xác định nguyên nhân thất bại. */
    public record PaymentWalletInfo(
        String status,
        java.math.BigDecimal amount,
        Integer buyerWalletId,      // null nếu không tồn tại
        java.math.BigDecimal buyerBalance,
        Integer sellerWalletId      // null nếu không tồn tại
    ) {}

    /** buyer_id và status của payment, dùng để kiểm tra quyền truy cập. */
    public record PaymentBuyerStatus(int buyerId, String status) {}
}