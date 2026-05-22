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
}