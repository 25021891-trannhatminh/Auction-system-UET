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
 * <p>Xử lý toàn bộ các thao tác tài chính cốt lõi:
 * <ul>
 *     <li>Truy vấn số dư và thông tin ví.</li>
 *     <li>Nạp tiền (Deposit) và Rút tiền (Withdraw).</li>
 *     <li>Chuyển tiền nội bộ giữa các ví (Transfer) với cơ chế Transaction đảm bảo an toàn.</li>
 * </ul>
 * </p>
 */
public class WalletDAO {

    private static final Logger logger = LoggerFactory.getLogger(WalletDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE =
        "SELECT wallet_id, user_id, balance, updated_at FROM WALLETS";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE wallet_id = ?";

    private static final String SQL_SELECT_BY_USER_ID =
        SQL_SELECT_BASE + " WHERE user_id = ?";

    private static final String SQL_SELECT_BALANCE =
        "SELECT balance FROM WALLETS WHERE wallet_id = ?";

    private static final String SQL_INSERT =
        "INSERT INTO WALLETS (user_id, balance) VALUES (?, 0.00)";

    private static final String SQL_DEPOSIT =
        "UPDATE WALLETS SET balance = balance + ?, updated_at = NOW() WHERE wallet_id = ?";

    private static final String SQL_WITHDRAW = """
        UPDATE WALLETS 
        SET balance = balance - ?, updated_at = NOW()
        WHERE wallet_id = ? AND balance >= ?
        """;

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Lấy thông tin ví dựa trên mã định danh ví (walletId).
     *
     * @param walletId ID định danh của ví.
     * @return Đối tượng {@link WalletDTO} hoặc {@code null} nếu không tìm thấy.
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
     * Truy vấn ví điện tử thuộc sở hữu của một người dùng cụ thể.
     *
     * @param userId ID định danh của người dùng.
     * @return Đối tượng {@link WalletDTO} hoặc {@code null} nếu người dùng chưa có ví.
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
     * Lấy số dư hiện tại của ví một cách nhanh chóng.
     *
     * @param walletId ID định danh của ví.
     * @return {@link BigDecimal} số dư ví, mặc định trả về {@code 0.00} nếu có lỗi hoặc không tìm thấy.
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
    // INSERT / UPDATE Methods
    // ============================================================

    /**
     * Khởi tạo một ví điện tử mới cho người dùng với số dư ban đầu bằng 0.
     *
     * @param userId ID người dùng cần tạo ví.
     * @return Mã ID của ví mới được tạo, hoặc {@code -1} nếu thao tác thất bại.
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

    /**
     * Nạp thêm tiền vào ví.
     *
     * @param walletId ID ví nhận tiền.
     * @param amount   Số tiền nạp (phải lớn hơn 0).
     * @return {@code true} nếu nạp tiền thành công.
     */
    public boolean deposit(int walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Deposit aborted: invalid amount={}", amount);
            return false;
        }

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_DEPOSIT)) {

            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Deposit failed for walletId={}", walletId, e);
            return false;
        }
    }

    /**
     * Rút tiền từ ví. Chỉ thành công khi số dư ví lớn hơn hoặc bằng số tiền cần rút.
     *
     * @param walletId ID ví thực hiện rút tiền.
     * @param amount   Số tiền cần rút (phải lớn hơn 0).
     * @return {@code true} nếu rút tiền thành công.
     */
    public boolean withdraw(int walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Withdraw aborted: invalid amount={}", amount);
            return false;
        }

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_WITHDRAW)) {

            ps.setBigDecimal(1, amount);
            ps.setInt(2, walletId);
            ps.setBigDecimal(3, amount); // Dùng để check balance >= amount ở tầng SQL
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Withdraw failed for walletId={}", walletId, e);
            return false;
        }
    }

    /**
     * Chuyển tiền giữa hai ví nội bộ.
     * Sử dụng Database Transaction để đảm bảo tiền không bị mất mát nếu một trong hai bước thất bại.
     *
     * @param fromWalletId ID ví gửi.
     * @param toWalletId   ID ví nhận.
     * @param amount       Số tiền chuyển.
     * @return {@code true} nếu giao dịch hoàn tất thành công.
     */
    public boolean transfer(int fromWalletId, int toWalletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Transfer aborted: invalid amount={}", amount);
            return false;
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu Transaction

            // Bước 1: Trừ tiền ví gửi
            try (PreparedStatement psWithdraw = conn.prepareStatement(SQL_WITHDRAW)) {
                psWithdraw.setBigDecimal(1, amount);
                psWithdraw.setInt(2, fromWalletId);
                psWithdraw.setBigDecimal(3, amount);

                if (psWithdraw.executeUpdate() == 0) {
                    logger.warn("Transfer failed: Insufficient balance in walletId={}", fromWalletId);
                    conn.rollback();
                    return false;
                }
            }

            // Bước 2: Cộng tiền ví nhận
            try (PreparedStatement psDeposit = conn.prepareStatement(SQL_DEPOSIT)) {
                psDeposit.setBigDecimal(1, amount);
                psDeposit.setInt(2, toWalletId);
                psDeposit.executeUpdate();
            }

            conn.commit(); // Hoàn tất Transaction
            return true;

        } catch (SQLException e) {
            logger.error("Transfer failed between wallet {} and {}", fromWalletId, toWalletId, e);
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                logger.error("Rollback failed", ex);
            }
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException ignored) {}
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /**
     * Ánh xạ một dòng dữ liệu từ {@link ResultSet} sang thực thể {@link WalletDTO}.
     *
     * @param rs ResultSet đang trỏ tới dòng hiện tại.
     * @return Đối tượng WalletDTO đã điền đủ thông tin.
     * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột.
     */
    private WalletDTO mapRow(ResultSet rs) throws SQLException {
        return new WalletDTO(
            rs.getInt("wallet_id"),
            rs.getInt("user_id"),
            rs.getBigDecimal("balance"),
            rs.getTimestamp("updated_at")
        );
    }
}