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
 * <p>Quản lý các giao dịch tài chính liên quan đến phiên đấu giá, bao gồm các trạng thái:
 * PENDING, COMPLETED, FAILED, và REFUNDED. Mọi lỗi {@link SQLException} được ghi log qua SLF4J.</p>
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

    private static final String SQL_COMPLETE = """
        UPDATE payments SET status = ?, paid_at = CURRENT_TIMESTAMP
        WHERE payment_id = ? AND status = ?
        """;

    private static final String SQL_UPDATE_STATUS =
        "UPDATE payments SET status = ? WHERE payment_id = ? AND status = ?";

    private static final String SQL_SELECT_BY_AUCTION =
        SQL_SELECT_BASE + " WHERE auction_id = ?";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE payment_id = ?";

    private static final String SQL_SELECT_BY_BUYER =
        SQL_SELECT_BASE + " WHERE buyer_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_BY_SELLER =
        SQL_SELECT_BASE + " WHERE seller_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_PENDING =
        SQL_SELECT_BASE + " WHERE status = ? ORDER BY created_at ASC";

    // ============================================================
    // INSERT Methods
    // ============================================================

    /**
     * Tạo mới một bản ghi thanh toán với trạng thái mặc định là PENDING.
     *
     * @param auctionId ID của phiên đấu giá liên quan.
     * @param buyerId   ID của người mua (người thắng cuộc).
     * @param sellerId  ID của người bán.
     * @param amount    Số tiền cần thanh toán.
     * @return {@code true} nếu tạo thành công bản ghi.
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
            logger.error("createPayment failed for auctionId={}", auctionId, e);
            return false;
        }
    }

    // ============================================================
    // UPDATE Methods
    // ============================================================

    /**
     * Đánh dấu giao dịch là hoàn tất (COMPLETED) và ghi nhận thời gian thanh toán.
     * Chỉ áp dụng cho các giao dịch đang ở trạng thái PENDING.
     *
     * @param paymentId ID của giao dịch.
     * @return {@code true} nếu cập nhật thành công.
     */
    public boolean completePayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_COMPLETE)) {

            ps.setString(1, PaymentStatus.COMPLETED.name());
            ps.setInt(2, paymentId);
            ps.setString(3, PaymentStatus.PENDING.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("completePayment failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * Chuyển trạng thái giao dịch sang thất bại (FAILED).
     * Chỉ áp dụng cho các giao dịch đang ở trạng thái PENDING.
     *
     * @param paymentId ID của giao dịch.
     * @return {@code true} nếu cập nhật thành công.
     */
    public boolean failPayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, PaymentStatus.FAILED.name());
            ps.setInt(2, paymentId);
            ps.setString(3, PaymentStatus.PENDING.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("failPayment failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    /**
     * Thực hiện hoàn tiền (REFUNDED) cho một giao dịch.
     * Chỉ áp dụng cho các giao dịch đã hoàn tất (COMPLETED).
     *
     * @param paymentId ID của giao dịch.
     * @return {@code true} nếu cập nhật thành công.
     */
    public boolean refundPayment(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, PaymentStatus.REFUNDED.name());
            ps.setInt(2, paymentId);
            ps.setString(3, PaymentStatus.COMPLETED.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("refundPayment failed for paymentId={}", paymentId, e);
            return false;
        }
    }

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Tìm kiếm thông tin thanh toán dựa trên ID phiên đấu giá.
     *
     * @param auctionId ID của phiên đấu giá.
     * @return Đối tượng {@link PaymentDTO} hoặc {@code null} nếu không tìm thấy.
     */
    public PaymentDTO getPaymentByAuctionId(int auctionId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getPaymentByAuctionId failed for auctionId={}", auctionId, e);
        }
        return null;
    }

    /**
     * Truy vấn thông tin giao dịch theo ID định danh.
     *
     * @param paymentId ID của giao dịch.
     * @return Đối tượng {@link PaymentDTO} hoặc {@code null} nếu không tồn tại.
     */
    public PaymentDTO getPaymentById(int paymentId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getPaymentById failed for paymentId={}", paymentId, e);
        }
        return null;
    }

    /**
     * Lấy danh sách lịch sử thanh toán của người mua.
     *
     * @param buyerId ID của người mua.
     * @return Danh sách {@link PaymentDTO} sắp xếp theo thời gian mới nhất.
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
            logger.error("getPaymentsByBuyer failed for buyerId={}", buyerId, e);
        }
        return list;
    }

    /**
     * Lấy danh sách lịch sử giao dịch liên quan đến một người bán.
     *
     * @param sellerId ID của người bán.
     * @return Danh sách {@link PaymentDTO} sắp xếp theo thời gian mới nhất.
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
            logger.error("getPaymentsBySeller failed for sellerId={}", sellerId, e);
        }
        return list;
    }

    /**
     * Truy vấn các giao dịch đang chờ xử lý (PENDING).
     * Thường dùng cho các chức năng quản trị hoặc đối soát hệ thống.
     *
     * @return Danh sách các giao dịch ở trạng thái PENDING.
     */
    public List<PaymentDTO> getPendingPayments() {
        List<PaymentDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_PENDING)) {

            ps.setString(1, PaymentStatus.PENDING.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getPendingPayments failed", e);
        }
        return list;
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /**
     * Ánh xạ một dòng kết quả từ {@link ResultSet} sang đối tượng {@link PaymentDTO}.
     *
     * @param rs ResultSet đang trỏ tới bản ghi hiện tại.
     * @return Đối tượng {@link PaymentDTO} đã điền đủ thông tin.
     * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột.
     */
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