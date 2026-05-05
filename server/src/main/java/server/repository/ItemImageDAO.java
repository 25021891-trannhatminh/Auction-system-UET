package server.repository;

import server.common.model.ItemImageDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code ITEM_IMAGES}.
 *
 * <p>Quản lý danh sách hình ảnh của sản phẩm, hỗ trợ thiết lập ảnh đại diện (primary)
 * và thứ tự sắp xếp hiển thị. Mọi lỗi {@link SQLException} được ghi log qua SLF4J.</p>
 */
public class ItemImageDAO {

  private static final Logger logger = LoggerFactory.getLogger(ItemImageDAO.class);

  // ============================================================
  // SQL Constants
  // ============================================================

  private static final String SQL_SELECT_BASE = """
        SELECT image_id, item_id, url, is_primary, sort_order
        FROM item_images
        """;

  private static final String SQL_SELECT_BY_ITEM =
      SQL_SELECT_BASE + " WHERE item_id = ? ORDER BY sort_order ASC";

  private static final String SQL_SELECT_PRIMARY =
      SQL_SELECT_BASE + " WHERE item_id = ? AND is_primary = TRUE LIMIT 1";

  private static final String SQL_SELECT_BY_ID =
      SQL_SELECT_BASE + " WHERE image_id = ?";

  private static final String SQL_COUNT_BY_ITEM =
      "SELECT COUNT(*) FROM item_images WHERE item_id = ?";

  private static final String SQL_INSERT = """
        INSERT INTO item_images (item_id, url, is_primary, sort_order)
        VALUES (?, ?, ?, ?)
        """;

  private static final String SQL_UPDATE = """
        UPDATE item_images SET url = ?, is_primary = ?, sort_order = ?
        WHERE image_id = ?
        """;

  private static final String SQL_RESET_PRIMARY =
      "UPDATE item_images SET is_primary = FALSE WHERE item_id = ?";

  private static final String SQL_SET_PRIMARY =
      "UPDATE item_images SET is_primary = TRUE WHERE image_id = ?";

  private static final String SQL_DELETE_BY_ID =
      "DELETE FROM item_images WHERE image_id = ?";

  private static final String SQL_DELETE_BY_ITEM =
      "DELETE FROM item_images WHERE item_id = ?";

  // ============================================================
  // SELECT Methods
  // ============================================================

  /**
   * Lấy toàn bộ danh sách ảnh của một sản phẩm.
   *
   * @param itemId ID của sản phẩm cần lấy ảnh.
   * @return Danh sách {@link ItemImageDTO}, trả về danh sách rỗng nếu không có dữ liệu.
   */
  public List<ItemImageDTO> getImagesByItemId(int itemId) {
    List<ItemImageDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ITEM)) {
      ps.setInt(1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("getImagesByItemId failed for itemId={}", itemId, e);
    }
    return list;
  }

  /**
   * Truy vấn ảnh đại diện (Primary Image) của sản phẩm.
   *
   * @param itemId ID của sản phẩm.
   * @return Đối tượng {@link ItemImageDTO} là ảnh đại diện, hoặc {@code null} nếu chưa thiết lập.
   */
  public ItemImageDTO getPrimaryImage(int itemId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_PRIMARY)) {
      ps.setInt(1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (SQLException e) {
      logger.error("getPrimaryImage failed for itemId={}", itemId, e);
    }
    return null;
  }

  /**
   * Lấy thông tin chi tiết của một tấm ảnh theo ID.
   *
   * @param imageId ID của tấm ảnh.
   * @return Đối tượng {@link ItemImageDTO} hoặc {@code null} nếu không tìm thấy.
   */
  public ItemImageDTO getImageById(int imageId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {
      ps.setInt(1, imageId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (SQLException e) {
      logger.error("getImageById failed for imageId={}", imageId, e);
    }
    return null;
  }

  /**
   * Đếm tổng số lượng hình ảnh đang có của một sản phẩm.
   *
   * @param itemId ID của sản phẩm.
   * @return Số lượng ảnh tìm thấy.
   */
  public int countImagesByItemId(int itemId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_COUNT_BY_ITEM)) {
      ps.setInt(1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt(1);
      }
    } catch (SQLException e) {
      logger.error("countImagesByItemId failed for itemId={}", itemId, e);
    }
    return 0;
  }

  // ============================================================
  // INSERT Methods
  // ============================================================

  /**
   * Thêm một hình ảnh mới vào cơ sở dữ liệu.
   *
   * @param dto Đối tượng chứa thông tin ảnh (URL, trạng thái primary, thứ tự).
   * @return ID tự sinh của tấm ảnh mới, hoặc {@code -1} nếu thất bại.
   */
  public int insert(ItemImageDTO dto) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
      ps.setInt(1, dto.getItemId());
      ps.setString(2, dto.getUrl());
      ps.setBoolean(3, dto.isPrimary());
      ps.setInt(4, dto.getSortOrder());

      if (ps.executeUpdate() > 0) {
        try (ResultSet keys = ps.getGeneratedKeys()) {
          if (keys.next()) return keys.getInt(1);
        }
      }
    } catch (SQLException e) {
      logger.error("insert image failed for itemId={}", dto.getItemId(), e);
    }
    return -1;
  }

  /**
   * Chèn hàng loạt hình ảnh vào database sử dụng Batch Processing.
   *
   * @param images Danh sách các đối tượng {@link ItemImageDTO} cần thêm.
   * @return Số lượng hình ảnh được thêm thành công.
   */
  public int insertBatch(List<ItemImageDTO> images) {
    if (images == null || images.isEmpty()) return 0;

    int successCount = 0;
    Connection conn = null;
    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
        for (ItemImageDTO dto : images) {
          ps.setInt(1, dto.getItemId());
          ps.setString(2, dto.getUrl());
          ps.setBoolean(3, dto.isPrimary());
          ps.setInt(4, dto.getSortOrder());
          ps.addBatch();
        }

        int[] results = ps.executeBatch();
        conn.commit();

        for (int r : results) {
          if (r > 0 || r == Statement.SUCCESS_NO_INFO) successCount++;
        }
      }
    } catch (SQLException e) {
      logger.error("insertBatch images failed", e);
      if (conn != null) {
        try { conn.rollback(); } catch (SQLException ex) { logger.error("Rollback failed", ex); }
      }
    } finally {
      closeConnection(conn);
    }
    return successCount;
  }

  // ============================================================
  // UPDATE Methods
  // ============================================================

  /**
   * Cập nhật thông tin chi tiết (URL, thứ tự...) của một tấm ảnh.
   *
   * @param dto Đối tượng ảnh chứa dữ liệu mới và ID tấm ảnh.
   * @return {@code true} nếu cập nhật thành công ít nhất một hàng.
   */
  public boolean update(ItemImageDTO dto) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
      ps.setString(1, dto.getUrl());
      ps.setBoolean(2, dto.isPrimary());
      ps.setInt(3, dto.getSortOrder());
      ps.setInt(4, dto.getImageId());
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("update image failed for imageId={}", dto.getImageId(), e);
    }
    return false;
  }

  /**
   * Thiết lập một tấm ảnh cụ thể làm ảnh đại diện cho sản phẩm.
   * Phương thức này sẽ tự động đặt các ảnh khác của sản phẩm về {@code is_primary = FALSE}.
   *
   * @param itemId  ID của sản phẩm.
   * @param imageId ID của tấm ảnh muốn đặt làm primary.
   * @return {@code true} nếu thao tác cập nhật thành công.
   */
  public boolean setPrimaryImage(int itemId, int imageId) {
    Connection conn = null;
    try {
      conn = DBConnection.getConnection();
      conn.setAutoCommit(false);

      try (PreparedStatement psReset = conn.prepareStatement(SQL_RESET_PRIMARY);
          PreparedStatement psSet = conn.prepareStatement(SQL_SET_PRIMARY)) {

        psReset.setInt(1, itemId);
        psReset.executeUpdate();

        psSet.setInt(1, imageId);
        psSet.executeUpdate();
      }

      conn.commit();
      return true;
    } catch (SQLException e) {
      logger.error("setPrimaryImage failed for itemId={}, imageId={}", itemId, imageId, e);
      if (conn != null) {
        try { conn.rollback(); } catch (SQLException ex) { logger.error("Rollback failed", ex); }
      }
    } finally {
      closeConnection(conn);
    }
    return false;
  }

  // ============================================================
  // DELETE Methods
  // ============================================================

  /**
   * Xóa một tấm ảnh khỏi hệ thống dựa trên ID.
   *
   * @param imageId ID của ảnh cần xóa.
   * @return {@code true} nếu xóa thành công.
   */
  public boolean delete(int imageId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_ID)) {
      ps.setInt(1, imageId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("delete image failed for imageId={}", imageId, e);
    }
    return false;
  }

  /**
   * Xóa toàn bộ hình ảnh liên quan đến một sản phẩm.
   *
   * @param itemId ID của sản phẩm.
   * @return Số lượng ảnh đã bị xóa.
   */
  public int deleteAllByItemId(int itemId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_ITEM)) {
      ps.setInt(1, itemId);
      return ps.executeUpdate();
    } catch (SQLException e) {
      logger.error("deleteAllByItemId failed for itemId={}", itemId, e);
    }
    return 0;
  }

  // ============================================================
  // Private Helpers
  // ============================================================

  /**
   * Ánh xạ dữ liệu từ ResultSet sang đối tượng DTO.
   *
   * @param rs ResultSet đang trỏ tới hàng hiện tại.
   * @return Đối tượng {@link ItemImageDTO}.
   * @throws SQLException Nếu có lỗi khi đọc cột dữ liệu.
   */
  private ItemImageDTO mapRow(ResultSet rs) throws SQLException {
    return new ItemImageDTO(
        rs.getInt("image_id"),
        rs.getInt("item_id"),
        rs.getString("url"),
        rs.getBoolean("is_primary"),
        rs.getInt("sort_order")
    );
  }

  /**
   * Đóng kết nối an toàn và trả lại trạng thái autoCommit mặc định.
   *
   * @param conn Connection cần đóng.
   */
  private void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        conn.setAutoCommit(true);
        conn.close();
      } catch (SQLException e) {
        logger.error("closeConnection failed", e);
      }
    }
  }
}