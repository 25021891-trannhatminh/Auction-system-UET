package server.repository;

import server.common.model.WalletDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Data Access Object cho bảng {@code WALLETS}.
 *
 * <h2>Nguyên tắc thiết kế</h2>
 * <p>WalletDAO chỉ chịu trách nhiệm đọc/ghi bảng {@code wallets}.
 * Mọi thao tác ghi log vào {@code wallet_transactions} đều do
 * {@link WalletTransactionDAO} đảm nhận — và được gọi từ
 * {@link server.service.PaymentService}, không phải từ đây.</p>
 *
 * <h2>Hai nhóm method</h2>
 * <ul>
 *   <li><b>Standalone</b> ({@link #deposit}, {@link #withdraw}, {@link #transfer}):
 *       tự quản lý Connection, dùng cho nạp tiền / rút tiền đơn lẻ từ admin
 *       hoặc các flow không liên quan đến thanh toán đấu giá.</li>
 *   <li><b>In-transaction</b> ({@link #depositInTx}, {@link #withdrawInTx}):
 *       nhận Connection từ ngoài, không commit/rollback/close — dùng khi
 *       PaymentService cần gộp nhiều thao tác vào 1 DB transaction duy nhất.</li>
 * </ul>
 */
public class WalletDAO {

    private static final Logger logger = LoggerFactory.getLogger(WalletDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE =
        "SELECT wallet_id, user_id, balance, updated_at FROM wallets";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE wallet_id = ?";

    private static final String SQL_SELECT_BY_USER_ID =
        SQL_SELECT_BASE + " WHERE user_id = ?";

    /**
     * FOR UPDATE — dùng trong transaction để lock row, tránh race condition
     * khi nhiều luồng đọc/ghi cùng 1 ví đồng thời.
     */
    private static final String SQL_SELECT_BY_USER_ID_FOR_UPDATE =
        SQL_SELECT_BASE + " WHERE user_id = ? FOR UPDATE";

    private static final String SQL_SELECT_BALANCE =
        "SELECT balance FROM wallets WHERE wallet_id = ?";

    private static final String SQL_INSERT =
        "INSERT INTO wallets (user_id, balance) VALUES (?, 0.00)";

    /**
     * Cộng tiền — dùng cho cả standalone lẫn in-transaction.
     * Không có điều kiện phụ vì deposit không thể thất bại về mặt logic.
     */
    private static final String SQL_DEPOSIT =
        "UPDATE wallets SET balance = balance + ?, updated_at = NOW() WHERE wallet_id = ?";

    /**
     * Trừ tiền — điều kiện {@code balance >= amount} nằm tại tầng SQL
     * để chặn race condition ngay cả khi Java-level check đã qua.
     * Nếu {@code executeUpdate()} trả về 0 → số dư không đủ.
     */
    private static final String SQL_WITHDRAW =
        "UPDATE wallets SET balance = balance - ?, updated_at = NOW() " +
            "WHERE wallet_id = ? AND balance >= ?";

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Lấy thông tin ví theo walletId.
     */
    public WalletDTO getById(int walletId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, walletId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getById failed for walletId={}", walletId, e);
        }
        return null;
    }

    /**
     * Lấy thông tin ví theo userId.
     */
    public WalletDTO getByUserId(int userId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USER_ID)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getByUserId failed for userId={}", userId, e);
        }
        return null;
    }

    /**
     * Lấy số dư nhanh theo walletId (không lấy toàn bộ WalletDTO).
     * Trả về {@code 0.00} nếu có lỗi hoặc không tìm thấy.
     */
    public BigDecimal getBalance(int walletId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BALANCE)) {

            ps.setInt(1, walletId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance");
            }
        } catch (SQLException e) {
            logger.error("getBalance failed for walletId={}", walletId, e);
        }
        return BigDecimal.ZERO;
    }

    // ============================================================
    // INSERT Method
    // ============================================================

    /**
     * Tạo ví mới với số dư ban đầu = 0.
     *
     * @return walletId vừa tạo, hoặc {@code -1} nếu thất bại.
     */
    public int createWallet(int userId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, userId);
            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("createWallet failed for userId={}", userId, e);
        }
        return -1;
    }

    // ============================================================
    // Standalone UPDATE Methods
    // (tự quản lý Connection — dùng cho nạp tiền / rút tiền đơn lẻ)
    // ============================================================

    /**
     * Nạp tiền vào ví (standalone — tự commit).
     *
     * @param walletId ID ví nhận tiền
     * @param amount   Số tiền nạp (phải > 0)
     * @return {@code true} nếu thành công
     */
    public boolean deposit(int walletId, BigDecimal amount) {
        if (isInvalidAmount(amount)) {
            logger.warn("deposit() aborted: invalid amount={}", amount);
            return false;
        }
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DEPOSIT)) {

            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("deposit() failed for walletId={}", walletId, e);
            return false;
        }
    }

    /**
     * Rút tiền từ ví (standalone — tự commit).
     * Chỉ thành công khi {@code balance >= amount}.
     *
     * @param walletId ID ví rút tiền
     * @param amount   Số tiền rút (phải > 0)
     * @return {@code true} nếu thành công; {@code false} nếu không đủ số dư
     */
    public boolean withdraw(int walletId, BigDecimal amount) {
        if (isInvalidAmount(amount)) {
            logger.warn("withdraw() aborted: invalid amount={}", amount);
            return false;
        }
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_WITHDRAW)) {

            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            ps.setBigDecimal(3, amount); // check balance >= amount tại SQL
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("withdraw() failed for walletId={}", walletId, e);
            return false;
        }
    }

    /**
     * Chuyển tiền giữa hai ví (standalone — tự quản lý transaction).
     *
     * <p>Dùng cho các flow không liên quan đến payment đấu giá (ví dụ: admin
     * điều chỉnh ví). Nếu cần gộp với update payments trong 1 transaction,
     * dùng {@link #withdrawInTx} và {@link #depositInTx} thay thế.</p>
     *
     * @param fromWalletId ID ví gửi
     * @param toWalletId   ID ví nhận
     * @param amount       Số tiền chuyển
     * @return {@code true} nếu hoàn tất
     */
    public boolean transfer(int fromWalletId, int toWalletId, BigDecimal amount) {
        if (isInvalidAmount(amount)) {
            logger.warn("transfer() aborted: invalid amount={}", amount);
            return false;
        }
        if (fromWalletId == toWalletId) {
            logger.warn("transfer() aborted: fromWalletId == toWalletId = {}", fromWalletId);
            return false;
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Trừ ví gửi (check balance >= amount tại SQL)
            try (PreparedStatement ps = conn.prepareStatement(SQL_WITHDRAW)) {
                ps.setBigDecimal(1, amount);
                ps.setInt(2, fromWalletId);
                ps.setBigDecimal(3, amount);
                if (ps.executeUpdate() == 0) {
                    logger.warn("transfer() failed: insufficient balance in walletId={}", fromWalletId);
                    conn.rollback();
                    return false;
                }
            }

            // Cộng ví nhận
            try (PreparedStatement ps = conn.prepareStatement(SQL_DEPOSIT)) {
                ps.setBigDecimal(1, amount);
                ps.setInt(2, toWalletId);
                ps.executeUpdate();
            }

            conn.commit();
            logger.info("transfer() SUCCESS: from={}, to={}, amount={}", fromWalletId, toWalletId, amount);
            return true;

        } catch (SQLException e) {
            logger.error("transfer() failed between walletId={} and walletId={}", fromWalletId, toWalletId, e);
            rollbackQuietly(conn);
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    // ============================================================
    // In-Transaction Methods
    // (nhận Connection từ ngoài — KHÔNG commit/rollback/close)
    // Dùng khi PaymentService cần gộp nhiều thao tác vào 1 transaction
    // ============================================================

    /**
     * Khóa row ví của {@code userId} bằng {@code SELECT FOR UPDATE}.
     *
     * <p>Phải gọi method này trong transaction đang mở (autoCommit = false)
     * TRƯỚC khi đọc balance để đảm bảo không có luồng nào khác thay đổi
     * số dư trong khoảng thời gian từ lúc đọc đến lúc ghi.</p>
     *
     * @param conn   Connection đang trong transaction
     * @param userId ID của user cần lock ví
     * @return {@link WalletDTO} với số dư đã được lock, hoặc {@code null} nếu không tìm thấy
     * @throws SQLException nếu có lỗi DB
     */
    public WalletDTO lockByUserId(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USER_ID_FOR_UPDATE)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Cộng tiền vào ví trong transaction đang mở.
     *
     * <p><b>Không gọi commit/rollback/close — trách nhiệm của caller.</b></p>
     *
     * @param conn     Connection đang trong transaction
     * @param walletId ID ví nhận tiền
     * @param amount   Số tiền cộng thêm
     * @throws SQLException nếu có lỗi DB
     */
    public void depositInTx(Connection conn, int walletId, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_DEPOSIT)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            ps.executeUpdate();
        }
    }

    /**
     * Trừ tiền khỏi ví trong transaction đang mở.
     * Điều kiện {@code balance >= amount} nằm tại tầng SQL.
     *
     * <p><b>Không gọi commit/rollback/close — trách nhiệm của caller.</b></p>
     *
     * @param conn     Connection đang trong transaction
     * @param walletId ID ví bị trừ tiền
     * @param amount   Số tiền cần trừ
     * @return {@code true} nếu trừ thành công; {@code false} nếu số dư không đủ
     * @throws SQLException nếu có lỗi DB
     */
    public boolean withdrawInTx(Connection conn, int walletId, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_WITHDRAW)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            ps.setBigDecimal(3, amount); // check balance >= amount tại SQL
            return ps.executeUpdate() > 0;
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    private boolean isInvalidAmount(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    private void rollbackQuietly(Connection conn) {
        if (conn == null) return;
        try { conn.rollback(); } catch (SQLException e) {
            logger.error("rollback failed", e);
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) return;
        try {
            conn.setAutoCommit(true);
            conn.close();
        } catch (SQLException ignored) {}
    }

    private WalletDTO mapRow(ResultSet rs) throws SQLException {
        return new WalletDTO(
            rs.getInt("wallet_id"),
            rs.getInt("user_id"),
            rs.getBigDecimal("balance"),
            rs.getTimestamp("updated_at")
        );
    }
}