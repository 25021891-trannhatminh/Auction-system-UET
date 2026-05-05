package server.repository;

import server.common.model.ItemAttributeDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object cho bảng {@code ITEM_ATTRIBUTES}.
 *
 * <p>Quản lý thuộc tính mở rộng của sản phẩm theo mô hình EAV
 * (Entity - Attribute - Value). Mọi lỗi {@link SQLException} được xử lý nội bộ
 * và ghi log qua SLF4J.</p>
 */
public class ItemAttributeDAO {

  private static final Logger logger = LoggerFactory.getLogger(ItemAttributeDAO.class);

  // ==========================================================
  // SQL Constants
  // ==========================================================

  private static final String SQL_SELECT_BY_ITEM = """
        SELECT attr_id, item_id, attr_key, attr_value
        FROM ITEM_ATTRIBUTES
        WHERE item_id = ?
        ORDER BY attr_key ASC
        """;

  private static final String SQL_SELECT_BY_KEY = """
        SELECT attr_id, item_id, attr_key, attr_value
        FROM ITEM_ATTRIBUTES
        WHERE item_id = ? AND attr_key = ?
        """;

  private static final String SQL_INSERT = """
        INSERT INTO ITEM_ATTRIBUTES (item_id, attr_key, attr_value)
        VALUES (?, ?, ?)
        """;

  private static final String SQL_UPDATE = """
        UPDATE ITEM_ATTRIBUTES
        SET attr_key = ?, attr_value = ?
        WHERE attr_id = ?
        """;

  private static final String SQL_DELETE_BY_ID =
      "DELETE FROM ITEM_ATTRIBUTES WHERE attr_id = ?";

  private static final String SQL_DELETE_BY_ITEM =
      "DELETE FROM ITEM_ATTRIBUTES WHERE item_id = ?";

  // ==========================================================
  // Public Methods
  // ==========================================================

  /**
   * Lấy tất cả thuộc tính của một item.
   *
   * @param itemId ID của sản phẩm cần lấy thuộc tính.
   * @return Danh sách các đối tượng {@link ItemAttributeDTO}.
   */
  public List<ItemAttributeDTO> getAttributesByItemId(int itemId) {
    List<ItemAttributeDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ITEM)) {

      ps.setInt(1, itemId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("getAttributesByItemId failed for itemId={}", itemId, e);
    }
    return list;
  }

  /**
   * Lấy thuộc tính dưới dạng Map (key-value) để dễ dàng truy xuất trong logic nghiệp vụ.
   *
   * @param itemId ID của sản phẩm.
   * @return Một {@link Map} chứa cặp Key-Value của các thuộc tính.
   */
  public Map<String, String> getAttributeMapByItemId(int itemId) {
    Map<String, String> map = new LinkedHashMap<>();
    for (ItemAttributeDTO attr : getAttributesByItemId(itemId)) {
      map.put(attr.getAttrKey(), attr.getAttrValue());
    }
    return map;
  }

  /**
   * Lấy một thuộc tính cụ thể theo key của một sản phẩm.
   *
   * @param itemId  ID của sản phẩm.
   * @param attrKey Khóa của thuộc tính cần tìm.
   * @return Đối tượng {@link ItemAttributeDTO} nếu tìm thấy, ngược lại trả về {@code null}.
   */
  public ItemAttributeDTO getAttributeByKey(int itemId, String attrKey) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_KEY)) {

      ps.setInt(1, itemId);
      ps.setString(2, attrKey);

      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (SQLException e) {
      logger.error("getAttributeByKey failed for itemId={}, key={}", itemId, attrKey, e);
    }
    return null;
  }

  /**
   * Thêm một thuộc tính mới và trả về ID tự sinh.
   *
   * @param dto Đối tượng chứa thông tin thuộc tính cần thêm.
   * @return ID của bản ghi mới được tạo, hoặc {@code -1} nếu có lỗi.
   */
  public int insert(ItemAttributeDTO dto) {
    if (dto == null || dto.getAttrKey() == null) return -1;

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

      ps.setInt(1, dto.getItemId());
      ps.setString(2, dto.getAttrKey());
      ps.setString(3, dto.getAttrValue());

      if (ps.executeUpdate() > 0) {
        try (ResultSet rs = ps.getGeneratedKeys()) {
          if (rs.next()) return rs.getInt(1);
        }
      }
    } catch (SQLException e) {
      logger.error("insert failed for itemId={}", dto.getItemId(), e);
    }
    return -1;
  }

  /**
   * Thực hiện chèn nhiều thuộc tính cùng lúc bằng Batch Processing để tối ưu hiệu năng.
   *
   * @param attributes Danh sách các thuộc tính cần chèn.
   * @return Số lượng bản ghi đã được chèn thành công.
   */
  public int insertBatch(List<ItemAttributeDTO> attributes) {
    if (attributes == null || attributes.isEmpty()) return 0;

    int count = 0;
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

      conn.setAutoCommit(false);
      for (ItemAttributeDTO dto : attributes) {
        ps.setInt(1, dto.getItemId());
        ps.setString(2, dto.getAttrKey());
        ps.setString(3, dto.getAttrValue());
        ps.addBatch();
      }

      int[] results = ps.executeBatch();
      conn.commit();

      for (int r : results) {
        if (r > 0 || r == Statement.SUCCESS_NO_INFO) count++;
      }
    } catch (SQLException e) {
      logger.error("insertBatch failed", e);
    }
    return count;
  }

  /**
   * Cập nhật giá trị thuộc tính. Nếu chưa tồn tại cặp (itemId, attrKey) thì thực hiện thêm mới.
   *
   * @param dto Đối tượng thuộc tính cần cập nhật hoặc thêm mới.
   * @return {@code true} nếu thao tác thành công.
   */
  public boolean upsert(ItemAttributeDTO dto) {
    ItemAttributeDTO existing = getAttributeByKey(dto.getItemId(), dto.getAttrKey());
    if (existing != null) {
      existing.setAttrValue(dto.getAttrValue());
      return update(existing);
    }
    return insert(dto) != -1;
  }

  /**
   * Cập nhật thông tin của một thuộc tính đã tồn tại.
   *
   * @param dto Đối tượng chứa thông tin và ID thuộc tính cần cập nhật.
   * @return {@code true} nếu cập nhật thành công ít nhất một dòng.
   */
  public boolean update(ItemAttributeDTO dto) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

      ps.setString(1, dto.getAttrKey());
      ps.setString(2, dto.getAttrValue());
      ps.setInt(3, dto.getAttrId());

      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("update failed for attrId={}", dto.getAttrId(), e);
    }
    return false;
  }

  /**
   * Xóa một thuộc tính dựa trên ID của nó.
   *
   * @param attrId ID của thuộc tính cần xóa.
   * @return {@code true} nếu xóa thành công.
   */
  public boolean delete(int attrId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_DELETE_BY_ID)) {
      ps.setInt(1, attrId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("delete failed for attrId={}", attrId, e);
    }
    return false;
  }

  /**
   * Xóa tất cả các thuộc tính liên quan đến một sản phẩm.
   *
   * @param itemId ID của sản phẩm.
   * @return Số lượng thuộc tính đã bị xóa.
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

  // ==========================================================
  // Private Helpers
  // ==========================================================

  /**
   * Ánh xạ một dòng kết quả từ ResultSet sang đối tượng DTO.
   *
   * @param rs ResultSet đang trỏ tới dòng hiện tại.
   * @return Đối tượng {@link ItemAttributeDTO} đã được điền dữ liệu.
   * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột.
   */
  private ItemAttributeDTO mapRow(ResultSet rs) throws SQLException {
    return new ItemAttributeDTO(
        rs.getInt("attr_id"),
        rs.getInt("item_id"),
        rs.getString("attr_key"),
        rs.getString("attr_value")
    );
  }
}