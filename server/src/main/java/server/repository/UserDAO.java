package server.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.User;
import server.common.enums.UserRole;
import server.common.enums.UserStatus;
import server.common.util.AccountValidator;
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
public class UserDAO {

    /**
     * Logger ghi log hệ thống.
     */
    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

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
               role, status, is_active, last_login, created_at
        FROM users
        """;

    /**
     * SQL đăng ký tài khoản.
     */
    private static final String SQL_REGISTER = """
        INSERT INTO users
        (username, password, email, full_name, phone, role, status, is_active)
        VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
        """;

    /**
     * SQL đăng nhập.
     */
    private static final String SQL_LOGIN = """
        SELECT user_id, username, password, email, full_name,
               phone, role, status, is_active, last_login, created_at
        FROM users
        WHERE (username = ? OR email = ?)
        AND is_active = TRUE
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
        "UPDATE users SET status = ? WHERE user_id = ?";

    /**
     * SQL khóa tài khoản user.
     */
    private static final String SQL_BAN_USER =
        "UPDATE users SET status = ?, is_active = FALSE WHERE user_id = ?";

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
    public boolean register(String username, String password, String email, String fullName, String phone) {
        String normalizedEmail = AccountValidator.normalizeEmail(email);
        String normalizedPhone = AccountValidator.normalizePhone(phone);

        if (isBlank(username) || isBlank(password) || isBlank(fullName)
                || containsWhitespace(username) || containsWhitespace(password)
                || password.length() < 6
                || !AccountValidator.isValidGmailAddress(normalizedEmail)
                || !AccountValidator.isValidVietnamesePhone(normalizedPhone)) {
            logger.warn("register rejected because account data has invalid format: username={}", username);
            return false;
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_REGISTER)) {

            ps.setString(1, username);
            ps.setString(2, hashedPassword);
            ps.setString(3, normalizedEmail);
            ps.setString(4, fullName == null ? "" : fullName.trim());
            ps.setString(5, normalizedPhone);
            ps.setString(6, UserRole.USER.name());
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

                return mapRow(rs);
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
    public User getById(int userId) {

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_GET_BY_ID)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("getById failed for userId={}", userId, e);
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
                users.add(mapRow(rs));
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

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_BAN_USER)) {

            ps.setString(1, UserStatus.BANNED.name());
            ps.setInt(2, userId);

            boolean success = ps.executeUpdate() > 0;

            if (success) {
                logger.warn("User banned: {}", userId);
            }

            return success;

        } catch (SQLException e) {
            logger.error("banUser failed for userId={}", userId, e);
            return false;
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsWhitespace(String value) {
        return value != null && value.matches(".*\\s+.*");
    }


    /**
     * Mapping dữ liệu từ ResultSet sang đối tượng User.
     *
     * @param rs ResultSet hiện tại
     * @return đối tượng User
     * @throws SQLException nếu lỗi truy xuất dữ liệu
     */
    private User mapRow(ResultSet rs) throws SQLException {

        return new User(
            rs.getInt("user_id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("full_name"),
            rs.getString("phone"),
            UserRole.valueOf(rs.getString("role")),
            UserStatus.valueOf(rs.getString("status")),
            rs.getBoolean("is_active"),
            rs.getTimestamp("last_login"),
            rs.getTimestamp("created_at")
        );
    }
}