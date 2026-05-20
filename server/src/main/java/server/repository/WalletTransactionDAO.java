package server.repository;

import server.common.enums.WalletTransactionType;
import server.common.model.WalletTransactionDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code WALLET_TRANSACTIONS}.
 *
 * <h2>Vai trò</h2>
 * <p>Đây là nhật ký kiểm toán (Audit Log) bất biến cho mọi biến động số dư ví.
 * Nguyên tắc: <b>chỉ INSERT và SELECT</b>, tuyệt đối không UPDATE hoặc DELETE.</p>
 *
 * <h2>Hai cách ghi log</h2>
 * <ul>
 *   <li>{@link #logTransaction(WalletTransactionDTO)} — standalone, tự quản lý Connection.
 *       Dùng cho các luồng đơn lẻ (nạp/rút tiền thủ công).</li>
 *   <li>{@link #logTransactionInTx(Connection, int, int, WalletTransactionType, BigDecimal, Integer, String)}
 *       — in-transaction, nhận Connection từ {@link server.service.PaymentService}.
 *       Dùng khi cần ghi log trong cùng transaction với withdraw/deposit/update payments
 *       để đảm bảo tính nguyên vẹn: nếu bất kỳ bước nào rollback → log cũng bị rollback.</li>
 * </ul>
 */
public class WalletTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(WalletTransactionDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE = """
        SELECT tx_id, wallet_id, user_id, type, amount, ref_auction_id, note, created_at
        FROM wallet_transactions
        """;

    /**
     * INSERT standalone — dùng khi gọi ngoài transaction.
     * Tự JOIN wallets để lấy user_id từ wallet_id, tránh caller phải truyền thêm.
     */
    private static final String SQL_INSERT = """
        INSERT INTO wallet_transactions (wallet_id, user_id, type, amount, ref_auction_id, note)
        SELECT ?, user_id, ?, ?, ?, ?
        FROM wallets
        WHERE wallet_id = ?
        """;

    /**
     * INSERT in-transaction — caller truyền trực tiếp cả walletId lẫn userId
     * vì trong transaction đã có WalletDTO (từ lockByUserId) nên không cần JOIN.
     */
    private static final String SQL_INSERT_IN_TX = """
        INSERT INTO wallet_transactions (wallet_id, user_id, type, amount, ref_auction_id, note)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_SELECT_BY_WALLET = """
        SELECT tx_id, wallet_id, user_id, type, amount, ref_auction_id, note, created_at
        FROM wallet_transactions
        WHERE wallet_id = ?
        ORDER BY created_at DESC
        LIMIT ?
        """;

    private static final String SQL_SELECT_BY_USER = """
        SELECT wt.tx_id, wt.wallet_id, wt.user_id, wt.type, wt.amount,
               wt.ref_auction_id, wt.note, wt.created_at
        FROM wallet_transactions wt
        JOIN wallets w ON wt.wallet_id = w.wallet_id
        WHERE w.user_id = ?
        ORDER BY wt.created_at DESC
        LIMIT ?
        """;

    private static final String SQL_SELECT_BY_AUCTION = """
        SELECT tx_id, wallet_id, user_id, type, amount, ref_auction_id, note, created_at
        FROM wallet_transactions
        WHERE ref_auction_id = ?
        ORDER BY created_at DESC
        """;

    // ============================================================
    // Standalone INSERT
    // (tự quản lý Connection)
    // ============================================================

    /**
     * Ghi một giao dịch vào lịch sử ví (standalone).
     *
     * <p>Tự lấy Connection, tự đóng. Dùng cho nạp/rút tiền đơn lẻ không nằm
     * trong payment transaction.</p>
     *
     * @param transaction DTO chứa đầy đủ thông tin giao dịch
     * @return {@code true} nếu ghi thành công
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
            ps.setInt(6, transaction.getWalletId()); // JOIN wallets WHERE wallet_id = ?

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("logTransaction() failed for walletId={}", transaction.getWalletId(), e);
            return false;
        }
    }

    // ============================================================
    // In-Transaction INSERT
    // (nhận Connection từ ngoài — KHÔNG commit/rollback/close)
    // ============================================================

    /**
     * Ghi giao dịch vào lịch sử ví trong transaction đang mở.
     *
     * <p>Phải gọi method này sau khi withdraw/deposit đã thực hiện thành công
     * trên cùng {@code conn}. Nếu caller rollback, dòng log này cũng bị hủy
     * → lịch sử luôn đồng bộ với số dư thực tế.</p>
     *
     * <p><b>Không gọi commit/rollback/close — trách nhiệm của caller.</b></p>
     *
     * @param conn         Connection đang trong transaction (autoCommit = false)
     * @param walletId     ID ví phát sinh giao dịch
     * @param userId       ID user sở hữu ví
     * @param type         Loại giao dịch (DEPOSIT, WITHDRAW, PAYMENT, REFUND...)
     * @param amount       Số tiền giao dịch (luôn dương)
     * @param refAuctionId ID phiên đấu giá liên quan (có thể null)
     * @param note         Ghi chú mô tả giao dịch
     * @throws SQLException nếu có lỗi DB
     */
    public void logTransactionInTx(Connection conn,
        int walletId,
        int userId,
        WalletTransactionType type,
        BigDecimal amount,
        Integer refAuctionId,
        String note) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_IN_TX)) {
            ps.setInt(1, walletId);
            ps.setInt(2, userId);
            ps.setString(3, type.name());
            ps.setBigDecimal(4, amount);

            if (refAuctionId != null) {
                ps.setInt(5, refAuctionId);
            } else {
                ps.setNull(5, Types.INTEGER);
            }

            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Lấy lịch sử giao dịch theo walletId.
     *
     * @param walletId ID ví cần xem lịch sử
     * @param limit    Số giao dịch tối đa trả về
     * @return Danh sách sắp xếp theo thời gian mới nhất
     */
    public List<WalletTransactionDTO> getTransactionsByWallet(int walletId, int limit) {
        List<WalletTransactionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_WALLET)) {

            ps.setInt(1, walletId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getTransactionsByWallet() failed for walletId={}", walletId, e);
        }
        return list;
    }

    /**
     * Lấy lịch sử giao dịch theo userId (JOIN qua bảng wallets).
     *
     * @param userId ID user cần xem lịch sử
     * @param limit  Số giao dịch tối đa trả về
     * @return Danh sách sắp xếp theo thời gian mới nhất
     */
    public List<WalletTransactionDTO> getTransactionsByUser(int userId, int limit) {
        List<WalletTransactionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USER)) {

            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getTransactionsByUser() failed for userId={}", userId, e);
        }
        return list;
    }

    /**
     * Lấy các giao dịch liên quan đến một phiên đấu giá.
     * Hữu ích cho việc audit / đối soát sau khi phiên kết thúc.
     *
     * @param refAuctionId ID phiên đấu giá
     * @return Danh sách giao dịch liên quan (PAYMENT + REFUND nếu có)
     */
    public List<WalletTransactionDTO> getTransactionsByAuction(int refAuctionId) {
        List<WalletTransactionDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_AUCTION)) {

            ps.setInt(1, refAuctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getTransactionsByAuction() failed for refAuctionId={}", refAuctionId, e);
        }
        return list;
    }

    // ============================================================
    // Private Helpers
    // ============================================================

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