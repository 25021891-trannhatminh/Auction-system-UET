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
import server.database.DBConnection;

/**
 * Data Access Object cho bảng {@code USERS}.
 *
 * <p>Quản lý thông tin tài khoản, xác thực và phân quyền trong hệ thống.
 * Mật khẩu được bảo mật bằng thuật toán BCrypt. Mọi lỗi {@link SQLException}
 * được ghi log chi tiết qua SLF4J.</p>
 */
public class UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    // ============================================================
    // SQL Constants
    // ============================================================

    private static final String SQL_SELECT_BASE = """
        SELECT user_id, username, email, full_name, phone, 
               role, status, is_active, last_login, created_at
        FROM users
        """;

    private static final String SQL_INSERT = """
        INSERT INTO users (username, password, email, full_name, phone, role, status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_SELECT_AUTH =
        "SELECT user_id, password FROM users WHERE (username = ? OR email = ?) AND is_active = TRUE";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE user_id = ?";

    private static final String SQL_SELECT_ALL =
        SQL_SELECT_BASE + " ORDER BY created_at DESC";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE users SET status = ? WHERE user_id = ?";

    private static final String SQL_BAN_USER =
        "UPDATE users SET status = ?, is_active = FALSE WHERE user_id = ?";

    // ============================================================
    // INSERT / AUTH Methods
    // ============================================================

    /**
     * Đăng ký tài khoản người dùng mới với vai trò mặc định là USER.
     * Mật khẩu sẽ được băm (hash) tự động trước khi lưu vào cơ sở dữ liệu.
     *
     * @param username Username duy nhất.
     * @param password Mật khẩu dạng văn bản thuần (sẽ được hash).
     * @param email    Địa chỉ email.
     * @param fullName Họ và tên đầy đủ.
     * @param phone    Số điện thoại liên lạc.
     * @return {@code true} nếu đăng ký thành công.
     */
    public boolean register(String username, String password, String email, String fullName, String phone) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setString(1, username);
            ps.setString(2, hashedPassword);
            ps.setString(3, email);
            ps.setString(4, fullName);
            ps.setString(5, phone);
            ps.setString(6, UserRole.USER.name());
            ps.setString(7, UserStatus.ACTIVE.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("register failed for username={}", username, e);
            return false;
        }
    }

    /**
     * Thực hiện đăng nhập bằng cách kiểm tra username/email và mật khẩu.
     *
     * @param identifier Username hoặc Email của người dùng.
     * @param password   Mật khẩu người dùng nhập vào.
     * @return Đối tượng {@link User} nếu thông tin chính xác, ngược lại trả về {@code null}.
     */
    public User login(String identifier, String password) {
        int userId = -1;

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_AUTH)) {

            ps.setString(1, identifier);
            ps.setString(2, identifier);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("Login attempt failed: user not found or inactive [{}]", identifier);
                    return null;
                }

                String storedHash = rs.getString("password");
                if (!BCrypt.checkpw(password, storedHash)) {
                    logger.warn("Login attempt failed: invalid password for [{}]", identifier);
                    return null;
                }

                userId = rs.getInt("user_id");
            }
        } catch (SQLException e) {
            logger.error("login process encountered an error for [{}]", identifier, e);
            return null;
        }

        return getById(userId);
    }

    // ============================================================
    // SELECT Methods
    // ============================================================

    /**
     * Truy vấn thông tin chi tiết của người dùng theo ID.
     *
     * @param userId ID định danh người dùng.
     * @return Đối tượng {@link User} hoặc {@code null} nếu không tìm thấy.
     */
    public User getById(int userId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getById failed for userId={}", userId, e);
        }
        return null;
    }

    /**
     * Lấy toàn bộ danh sách người dùng trong hệ thống.
     * Thường được sử dụng cho các chức năng quản trị (Admin Panel).
     *
     * @return Danh sách các đối tượng {@link User}.
     */
    public List<User> getAll() {
        List<User> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getAll users failed", e);
        }
        return list;
    }

    // ============================================================
    // UPDATE Methods
    // ============================================================

    /**
     * Cập nhật trạng thái hoạt động của người dùng.
     *
     * @param userId ID người dùng.
     * @param status Trạng thái mới (ACTIVE, SUSPENDED, BANNED).
     * @return {@code true} nếu cập nhật thành công.
     */
    public boolean updateStatus(int userId, UserStatus status) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, status.name());
            ps.setInt(2, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("updateStatus failed for userId={}", userId, e);
            return false;
        }
    }

    /**
     * Vô hiệu hóa tài khoản người dùng và đánh dấu là bị cấm (BANNED).
     *
     * @param userId ID người dùng cần xử lý.
     * @return {@code true} nếu thao tác thành công.
     */
    public boolean banUser(int userId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_BAN_USER)) {

            ps.setString(1, UserStatus.BANNED.name());
            ps.setInt(2, userId);

            logger.warn("User with ID {} has been banned from the system", userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("banUser failed for userId={}", userId, e);
            return false;
        }
    }

    // ============================================================
    // Private Helpers
    // ============================================================

    /**
     * Ánh xạ dữ liệu từ {@link ResultSet} sang thực thể {@link User}.
     *
     * @param rs ResultSet đang trỏ tới dòng dữ liệu hiện tại.
     * @return Đối tượng User đã được điền thông tin.
     * @throws SQLException Nếu có lỗi khi đọc các cột dữ liệu.
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