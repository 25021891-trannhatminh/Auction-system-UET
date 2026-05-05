package server.repository;

import server.common.enums.NotificationType;
import server.common.model.NotificationDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code NOTIFICATIONS}.
 *
 * <p>Quản lý việc đọc/ghi thông báo của người dùng trong hệ thống.
 * Mọi lỗi {@link SQLException} được xử lý nội bộ và ghi log qua SLF4J.</p>
 */
public class NotificationDAO {

  private static final Logger logger = LoggerFactory.getLogger(NotificationDAO.class);

  // ============================================================
  // SQL Constants
  // ============================================================

  private static final String SQL_SELECT_BASE = """
        SELECT notif_id, user_id, type, title, content,
               is_read, related_id, created_at
        FROM NOTIFICATIONS
        """;

  private static final String SQL_SELECT_BY_USER_LIMIT =
      SQL_SELECT_BASE + " WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";

  private static final String SQL_SELECT_UNREAD =
      SQL_SELECT_BASE + " WHERE user_id = ? AND is_read = FALSE ORDER BY created_at DESC";

  private static final String SQL_SELECT_BY_ID =
      SQL_SELECT_BASE + " WHERE notif_id = ?";

  private static final String SQL_COUNT_UNREAD =
      "SELECT COUNT(*) FROM NOTIFICATIONS WHERE user_id = ? AND is_read = FALSE";

  private static final String SQL_SELECT_BY_TYPE =
      SQL_SELECT_BASE + " WHERE user_id = ? AND type = ? ORDER BY created_at DESC";

  private static final String SQL_INSERT = """
        INSERT INTO NOTIFICATIONS (user_id, type, title, content, is_read, related_id)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

  private static final String SQL_MARK_AS_READ =
      "UPDATE NOTIFICATIONS SET is_read = TRUE WHERE notif_id = ?";

  // ============================================================
  // SELECT Methods
  // ============================================================

  /**
   * Lấy danh sách thông báo của người dùng với giới hạn số lượng.
   *
   * @param userId ID của người dùng cần lấy thông báo.
   * @param limit  Số lượng thông báo tối đa muốn lấy (mới nhất lên đầu).
   * @return Danh sách {@link NotificationDTO}, trả về list rỗng nếu không có dữ liệu.
   */
  public List<NotificationDTO> getByUserId(int userId, int limit) {
    List<NotificationDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_USER_LIMIT)) {

      ps.setInt(1, userId);
      ps.setInt(2, limit);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(mapRow(rs));
      }
    } catch (SQLException e) {
      logger.error("getByUserId failed for userId={}", userId, e);
    }
    return list;
  }

  /**
   * Lấy tất cả thông báo của một người dùng cụ thể.
   *
   * @param userId ID của người dùng.
   * @return Danh sách toàn bộ thông báo của người dùng đó.
   */
  public List<NotificationDTO> getAllByUserId(int userId) {
    return getByUserId(userId, Integer.MAX_VALUE);
  }

  /**
   * Lấy danh sách các thông báo chưa đọc của người dùng.
   *
   * @param userId ID của người dùng.
   * @return Danh sách các {@link NotificationDTO} có trạng thái {@code is_read = FALSE}.
   */
  public List<NotificationDTO> getUnreadByUserId(int userId) {
    List<NotificationDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_UNREAD)) {

      ps.setInt(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(mapRow(rs));
      }
    } catch (SQLException e) {
      logger.error("getUnreadByUserId failed for userId={}", userId, e);
    }
    return list;
  }

  /**
   * Lấy thông tin chi tiết của một thông báo theo ID.
   *
   * @param notifId ID của thông báo cần tìm.
   * @return Đối tượng {@link NotificationDTO} hoặc {@code null} nếu không tồn tại.
   */
  public NotificationDTO getById(int notifId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

      ps.setInt(1, notifId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (SQLException e) {
      logger.error("getById failed for notifId={}", notifId, e);
    }
    return null;
  }

  /**
   * Đếm tổng số lượng thông báo chưa đọc của một người dùng.
   *
   * @param userId ID của người dùng cần kiểm tra.
   * @return Số lượng thông báo chưa đọc.
   */
  public int countUnread(int userId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_COUNT_UNREAD)) {

      ps.setInt(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt(1);
      }
    } catch (SQLException e) {
      logger.error("countUnread failed for userId={}", userId, e);
    }
    return 0;
  }

  /**
   * Lấy danh sách thông báo của người dùng dựa trên loại thông báo cụ thể.
   *
   * @param userId ID của người dùng.
   * @param type   Loại thông báo (ví dụ: AUCTION_END, NEW_BID, v.v.).
   * @return Danh sách thông báo tương ứng với loại yêu cầu.
   */
  public List<NotificationDTO> getByType(int userId, NotificationType type) {
    List<NotificationDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_TYPE)) {

      ps.setInt(1, userId);
      ps.setString(2, type.name());

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(mapRow(rs));
      }
    } catch (SQLException e) {
      logger.error("getByType failed for userId={} and type={}", userId, type, e);
    }
    return list;
  }

  // ============================================================
  // INSERT Methods
  // ============================================================

  /**
   * Tạo một thông báo mới trong hệ thống.
   *
   * @param dto Đối tượng chứa thông tin thông báo cần lưu.
   * @return ID tự sinh của thông báo mới, hoặc {@code -1} nếu thất bại.
   */
  public int insert(NotificationDTO dto) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

      ps.setInt(1, dto.getUserId());
      ps.setString(2, dto.getType().name());
      ps.setString(3, dto.getTitle());
      ps.setString(4, dto.getContent());
      ps.setBoolean(5, dto.isRead());

      if (dto.getRelatedId() != null) {
        ps.setInt(6, dto.getRelatedId());
      } else {
        ps.setNull(6, Types.INTEGER);
      }

      if (ps.executeUpdate() > 0) {
        try (ResultSet rs = ps.getGeneratedKeys()) {
          if (rs.next()) return rs.getInt(1);
        }
      }
    } catch (SQLException e) {
      logger.error("insert notification failed for userId={}", dto.getUserId(), e);
    }
    return -1;
  }

  // ============================================================
  // UPDATE Methods
  // ============================================================

  /**
   * Cập nhật trạng thái thông báo thành đã đọc.
   *
   * @param notifId ID của thông báo.
   * @return {@code true} nếu cập nhật thành công.
   */
  public boolean markAsRead(int notifId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_MARK_AS_READ)) {

      ps.setInt(1, notifId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("markAsRead failed for notifId={}", notifId, e);
    }
    return false;
  }

  // ============================================================
  // Private Helpers
  // ============================================================

  /**
   * Ánh xạ một dòng dữ liệu từ {@link ResultSet} sang đối tượng {@link NotificationDTO}.
   *
   * @param rs ResultSet đang trỏ tới dòng hiện tại.
   * @return Đối tượng {@link NotificationDTO} đã được điền dữ liệu.
   * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột SQL.
   */
  private NotificationDTO mapRow(ResultSet rs) throws SQLException {
    return new NotificationDTO(
        rs.getInt("notif_id"),
        rs.getInt("user_id"),
        NotificationType.valueOf(rs.getString("type")),
        rs.getString("title"),
        rs.getString("content"),
        rs.getBoolean("is_read"),
        (Integer) rs.getObject("related_id"),
        rs.getTimestamp("created_at")
    );
  }
}