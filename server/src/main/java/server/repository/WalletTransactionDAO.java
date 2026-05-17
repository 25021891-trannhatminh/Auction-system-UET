package server.repository;

import server.common.enums.WalletTransactionType;
import server.common.model.WalletTransactionDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code WALLET_TRANSACTIONS}.
 *
 * <p>Lớp này đóng vai trò là nhật ký kiểm toán (Audit Log) cho mọi biến động số dư ví.
 * Theo nguyên tắc an toàn dữ liệu, lớp này chỉ hỗ trợ thao tác {@code INSERT} và {@code SELECT}.
 * Tuyệt đối không cung cấp các phương thức {@code UPDATE} hoặc {@code DELETE}.</p>
 */
public class WalletTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(WalletTransactionDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE = """
        SELECT tx_id, wallet_id, type, amount, ref_auction_id, note, created_at
        FROM wallet_transactions
        """;

    private static final String SQL_INSERT = """
        INSERT INTO wallet_transactions (wallet_id, user_id, type, amount, ref_auction_id, note)
        SELECT ?, user_id, ?, ?, ?, ?
        FROM wallets
        WHERE wallet_id = ?
        """;

    private static final String SQL_SELECT_BY_USER = """
        SELECT wt.tx_id, wt.wallet_id, wt.type, wt.amount, 
               wt.ref_auction_id, wt.note, wt.created_at
        FROM wallet_transactions wt
        JOIN wallets w ON wt.wallet_id = w.wallet_id
        WHERE w.user_id = ?
        ORDER BY wt.created_at DESC
        LIMIT ?
        """;

    private static final String SQL_SELECT_BY_AUCTION =
        SQL_SELECT_BASE + " WHERE ref_auction_id = ? ORDER BY created_at DESC";

    // ============================================================
    // INSERT Method
    // ============================================================

    /**
     * Ghi lại một giao dịch mới vào lịch sử ví.
     *
     * @param transaction Đối tượng chứa thông tin giao dịch cần lưu.
     * @return {@code true} nếu ghi log thành công.
     */
    public boolean logTransaction(WalletTransactionDTO transaction) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, transaction.getWalletId());
            ps.setString(2, transaction.getType().name());
            ps.setBigDecimal(3, transaction.getAmount());

            if (transaction.getRefAuctionId() != null) {
                ps.setInt(4, transaction.getRefAuctionId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }

            ps.setString(5, transaction.getNote());
            ps.setInt(6,transaction.getWalletId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("logTransaction failed for walletId={}", transaction.getWalletId(), e);
            return false;
        }
    }

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Truy vấn lịch sử giao dịch của một người dùng cụ thể.
     *
     * @param userId ID người dùng cần xem lịch sử.
     * @param limit  Số lượng giao dịch tối đa hiển thị (phân trang cơ bản).
     * @return Danh sách {@link WalletTransactionDTO} sắp xếp theo thời gian mới nhất.
     */
    public List<WalletTransactionDTO> getTransactionsByUser(int userId, int limit) {
        List<WalletTransactionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USER)) {

            ps.setInt(1, userId);
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("getTransactionsByUser failed for userId={}, limit={}", userId, limit, e);
        }
        return list;
    }

    /**
     * Lấy các giao dịch tài chính liên quan đến một phiên đấu giá cụ thể.
     *
     * @param refAuctionId ID của phiên đấu giá.
     * @return Danh sách các giao dịch liên quan (ví dụ: đặt cọc, thanh toán thắng thầu).
     */
    public List<WalletTransactionDTO> getTransactionsByAuction(int refAuctionId) {
        List<WalletTransactionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

            ps.setInt(1, refAuctionId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("getTransactionsByAuction failed for refAuctionId={}", refAuctionId, e);
        }
        return list;
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /**
     * Ánh xạ dữ liệu từ {@link ResultSet} sang thực thể {@link WalletTransactionDTO}.
     * Xử lý an toàn cho cột {@code ref_auction_id} vì trường này có thể mang giá trị NULL.
     *
     * @param rs ResultSet đang trỏ tới dòng dữ liệu hiện tại.
     * @return Đối tượng WalletTransactionDTO đã điền đủ thông tin.
     * @throws SQLException Nếu có lỗi khi truy xuất các cột.
     */
    private WalletTransactionDTO mapRow(ResultSet rs) throws SQLException {
        int refAuctionId = rs.getInt("ref_auction_id");
        Integer refAuctionIdOrNull = rs.wasNull() ? null : refAuctionId;

        return new WalletTransactionDTO(
            rs.getInt("tx_id"),
            rs.getInt("wallet_id"),
            WalletTransactionType.valueOf(rs.getString("type")),
            rs.getBigDecimal("amount"),
            refAuctionIdOrNull,
            rs.getString("note"),
            rs.getTimestamp("created_at")
        );
    }
}