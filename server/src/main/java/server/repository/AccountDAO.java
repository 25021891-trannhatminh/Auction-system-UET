package server.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Account;
import server.common.entity.Admin;
import server.common.entity.User;
import server.common.enums.AccountRole;
import server.common.enums.UserStatus;
import server.database.DBConnection;

/**
 * Data Access Object xử lý các thao tác với bảng users.
 *
 * <p>Chức năng chính:
 * <ul>
 *     <li>Đăng ký tài khoản</li>
 *     <li>Đăng nhập người dùng</li>
 *     <li>Lấy danh sách người dùng</li>
 *     <li>Cập nhật trạng thái tài khoản</li>
 *     <li>Khóa tài khoản người dùng</li>
 * </ul>
 *
 * <p>Tối ưu:
 * <ul>
 *     <li>Dùng BCrypt để mã hóa mật khẩu</li>
 *     <li>Sử dụng PreparedStatement chống SQL Injection</li>
 *     <li>Ghi log bằng SLF4J</li>
 * </ul>
 */
public class AccountDAO {

    /**
     * Logger ghi log hệ thống.
     */
    private static final Logger logger = LoggerFactory.getLogger(AccountDAO.class);

    /**
     * Số vòng hash BCrypt.
     * 8 giúp login/register nhanh hơn.
     */
    private static final int BCRYPT_ROUNDS = 8;

    /**
     * Câu lệnh SELECT cơ bản.
     */
    private static final String SQL_SELECT_BASE = """
        SELECT user_id, username, email, full_name, phone,
               role, status, last_login, created_at
        FROM accounts
        """;

    /**
     * SQL đăng ký tài khoản.
     */
    private static final String SQL_REGISTER = """
        INSERT INTO accounts
        (username, password, email, full_name, phone, role, status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    /**
     * SQL đăng nhập.
     */
    private static final String SQL_LOGIN = """
        SELECT user_id, username, password, email, full_name,
               phone, role, status, last_login, created_at
        FROM accounts
        WHERE (username = ? OR email = ?)
        AND status = 'ACTIVE'
        LIMIT 1
        """;

    /**
     * SQL lấy user theo ID.
     */
    private static final String SQL_GET_BY_ID =
        SQL_SELECT_BASE + " WHERE user_id = ?";

    /**
     * SQL lấy toàn bộ user.
     */
    private static final String SQL_GET_ALL =
        SQL_SELECT_BASE + " ORDER BY created_at DESC";

    /**
     * SQL cập nhật trạng thái user.
     */
    private static final String SQL_UPDATE_STATUS =
        "UPDATE accounts SET status = ? WHERE user_id = ?";

    // ============================================================
    // SQL cho Admin Panel (được tách ra từ ClientHandler - SRP)
    // ============================================================

    private static final String SQL_IS_ACTIVE_ADMIN =
        "SELECT role, status FROM accounts WHERE user_id = ?";

    private static final String SQL_ITEM_COUNTS_BY_SELLER = """
        SELECT seller_id AS user_id, COUNT(*) AS value
        FROM items
        GROUP BY seller_id
        """;

    private static final String SQL_RUNNING_AUCTION_COUNTS_BY_SELLER = """
        SELECT seller_id AS user_id, COUNT(*) AS value
        FROM auctions
        WHERE status = 'RUNNING'
        GROUP BY seller_id
        """;

    private static final String SQL_BID_COUNTS_BY_BIDDER = """
        SELECT bidder_id AS user_id, COUNT(*) AS value
        FROM bid_transactions
        GROUP BY bidder_id
        """;

    private static final String SQL_ADMIN_USER_LIST = """
        SELECT user_id,
               username,
               COALESCE(email, '') AS email,
               COALESCE(full_name, '') AS full_name,
               COALESCE(phone, '') AS phone,
               COALESCE(role, 'USER') AS role,
               COALESCE(status, 'ACTIVE') AS status,
               last_login,
               created_at
        FROM accounts
        ORDER BY created_at DESC, user_id DESC
        """;

    /**
     * Đăng ký tài khoản mới.
     *
     * @param username username duy nhất
     * @param password mật khẩu gốc
     * @param email email người dùng
     * @param fullName họ tên đầy đủ
     * @param phone số điện thoại
     * @return true nếu đăng ký thành công
     */
    public boolean register(String username, String password,
        String email, String fullName, String phone) {

        String hashedPassword =
            BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_REGISTER)) {

            ps.setString(1, username);
            ps.setString(2, hashedPassword);
            ps.setString(3, email);
            ps.setString(4, fullName);
            ps.setString(5, phone);
            ps.setString(6, AccountRole.USER.name());
            ps.setString(7, UserStatus.ACTIVE.name());

            boolean success = ps.executeUpdate() > 0;

            if (success) {
                logger.info("Register success: {}", username);
            }

            return success;

        } catch (SQLException e) {
            logger.error("Register failed for {}", username, e);
            return false;
        }
    }

    /**
     * Đăng nhập bằng username hoặc email.
     *
     * @param identifier username hoặc email
     * @param password mật khẩu người dùng nhập
     * @return đối tượng User nếu thành công, ngược lại trả về null
     */
    public User login(String identifier, String password) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_LOGIN)) {

            ps.setString(1, identifier);
            ps.setString(2, identifier);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    logger.warn("User not found: {}", identifier);
                    return null;
                }

                String storedHash = rs.getString("password");

                if (!BCrypt.checkpw(password, storedHash)) {
                    logger.warn("Invalid password: {}", identifier);
                    return null;
                }

                logger.info("Login success: {}", identifier);

                return getUserByRow(rs);
            }

        } catch (SQLException e) {
            logger.error("Login failed: {}", identifier, e);
            return null;
        }
    }

    /**
     * Lấy thông tin user theo ID.
     *
     * @param userId ID người dùng
     * @return đối tượng User hoặc null nếu không tồn tại
     */
    public User getUserById(int userId) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_BY_ID)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return getUserByRow(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("getById failed for userId={}", userId, e);
        }

        return null;
    }

    public int getUserIdByUsername(String username) {
        String sql = "SELECT user_id FROM accounts";
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (SQLException e) {
            logger.error("getUserIdByUsername failed", e);
        }
        return -1;
    }

    /**
     * Lấy thông tin admin theo ID.
     *
     * @param adminId ID người dùng
     * @return đối tượng Admin hoặc null nếu không tồn tại
     */
    public Admin getAdminById(int adminId) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_BY_ID)) {

            ps.setInt(1, adminId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return getAdminByRow(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("getById failed for userId={}", adminId, e);
        }

        return null;
    }

    /**
     * Lấy toàn bộ danh sách người dùng.
     *
     * @return danh sách User
     */
    public List<User> getAll() {

        List<User> users = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(getUserByRow(rs));
            }

        } catch (SQLException e) {
            logger.error("getAll users failed", e);
        }

        return users;
    }

    /**
     * Cập nhật trạng thái tài khoản.
     *
     * @param userId ID người dùng
     * @param status trạng thái mới
     * @return true nếu cập nhật thành công
     */
    public boolean updateStatus(int userId, UserStatus status) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, status.name());
            ps.setInt(2, userId);

            boolean success = ps.executeUpdate() > 0;

            if (success) {
                logger.info("Updated status userId={} -> {}", userId, status);
            }

            return success;

        } catch (SQLException e) {
            logger.error("updateStatus failed for userId={}", userId, e);
            return false;
        }
    }

    /**
     * Khóa tài khoản người dùng.
     *
     * @param userId ID người dùng
     * @return true nếu khóa thành công
     */
    public boolean banUser(int userId) {
        return updateStatus(userId, UserStatus.BANNED);
    }

    /**
     * Mapping dữ liệu từ ResultSet sang đối tượng User không chứa passwordHash.
     *
     * @param rs ResultSet hiện tại
     * @return đối tượng User
     * @throws SQLException nếu lỗi truy xuất dữ liệu
     */
    private User getUserByRow(ResultSet rs)
        throws SQLException {

        Timestamp lastLoginTs = rs.getTimestamp("last_login");

        return new User(
            rs.getInt("user_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getString("username"),
            rs.getString("email"),
            null,
            rs.getString("full_name"),
            rs.getString("phone"),
            AccountRole.valueOf(rs.getString("role")),
            UserStatus.valueOf(rs.getString("status")),
            lastLoginTs != null ? lastLoginTs.toLocalDateTime() : null,
            5.0,
            BigDecimal.ZERO
        );
    }


    private Admin getAdminByRow(ResultSet rs) throws SQLException {
        Timestamp lastLoginTs = rs.getTimestamp("last_login");
        AccountRole role = AccountRole.valueOf(rs.getString("role"));
        if (role == AccountRole.ADMIN) {
            return new Admin(rs.getInt("user_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("username"),
                rs.getString("email"),
                null,
                rs.getString("full_name"),
                rs.getString("phone"),
                UserStatus.valueOf(rs.getString("status")),
                lastLoginTs != null ? lastLoginTs.toLocalDateTime() : null
            );
        }
        return null;
    }

    // ============================================================
    // Admin Panel Methods (tách từ ClientHandler - SRP)
    // ============================================================

    /**
     * Kiểm tra user có phải ADMIN đang ACTIVE không.
     * Tách từ ClientHandler.isActiveAdmin() để tuân thủ SRP.
     *
     * @param userId ID người dùng cần kiểm tra
     * @return {@code true} nếu user là ADMIN và đang ACTIVE
     */
    public boolean isActiveAdmin(int userId) {
        if (userId <= 0) return false;
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_IS_ACTIVE_ADMIN)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                    && "ADMIN".equalsIgnoreCase(rs.getString("role"))
                    && "ACTIVE".equalsIgnoreCase(rs.getString("status"));
            }
        } catch (SQLException e) {
            logger.error("isActiveAdmin() failed for userId={}", userId, e);
            return false;
        }
    }

    /**
     * Lấy danh sách user kèm số liệu thống kê cho Admin panel.
     * Tách từ ClientHandler.sendAdminUsers() để tuân thủ SRP.
     *
     * @return danh sách {@link AdminUserRow} đã kèm itemCount, runningAuctionCount, bidCount
     */
    public List<AdminUserRow> getAdminUserRows() {
        Map<Integer, Integer> itemCounts    = loadCountMap(SQL_ITEM_COUNTS_BY_SELLER);
        Map<Integer, Integer> runningCounts = loadCountMap(SQL_RUNNING_AUCTION_COUNTS_BY_SELLER);
        Map<Integer, Integer> bidCounts     = loadCountMap(SQL_BID_COUNTS_BY_BIDDER);

        List<AdminUserRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_ADMIN_USER_LIST);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int uid = rs.getInt("user_id");
                String status = rs.getString("status");
                rows.add(new AdminUserRow(
                    uid,
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("role"),
                    status,
                    "ACTIVE".equals(status),
                    rs.getTimestamp("last_login"),
                    rs.getTimestamp("created_at"),
                    itemCounts.getOrDefault(uid, 0),
                    runningCounts.getOrDefault(uid, 0),
                    bidCounts.getOrDefault(uid, 0)
                ));
            }
        } catch (SQLException e) {
            logger.error("getAdminUserRows() failed", e);
        }
        return rows;
    }

    /**
     * Helper dùng chung để đếm số lượng theo nhóm.
     * Các query đếm phụ không nên chặn query chính nếu có lỗi.
     */
    private Map<Integer, Integer> loadCountMap(String sql) {
        Map<Integer, Integer> counts = new HashMap<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("user_id"), rs.getInt("value"));
            }
        } catch (SQLException e) {
            logger.warn("loadCountMap() skipped: {}", e.getMessage());
        }
        return counts;
    }

    /**
     * Kết quả tổng hợp một dòng user cho Admin panel.
     * Được trả về bởi {@link #getAdminUserRows()}.
     */
    public record AdminUserRow(
        int userId, String username, String email, String fullName,
        String phone, String role, String status, boolean isActive,
        Timestamp lastLogin, Timestamp createdAt,
        int itemCount, int runningAuctionCount, int bidCount
    ) {}
}