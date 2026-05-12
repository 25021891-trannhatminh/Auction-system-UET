package server.repository;

import server.common.model.ItemCategoryDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object cho bảng {@code ITEM_CATEGORIES}.
 *
 * <p>Quản lý danh mục sản phẩm theo cấu trúc cây (self-reference).
 * Mọi thao tác đều xử lý ngoại lệ SQLException nội bộ và ghi log qua SLF4J.</p>
 */
public class ItemCategoryDAO {

  private static final Logger logger = LoggerFactory.getLogger(ItemCategoryDAO.class);

  // ==========================================================
  // SQL Constants
  // ==========================================================

  private static final String SQL_SELECT_ALL = """
        SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name
        FROM ITEM_CATEGORIES c
        LEFT JOIN ITEM_CATEGORIES p ON c.parent_id = p.category_id
        ORDER BY c.parent_id ASC, c.name ASC
        """;

  private static final String SQL_SELECT_BY_ID = """
        SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name
        FROM ITEM_CATEGORIES c
        LEFT JOIN ITEM_CATEGORIES p ON c.parent_id = p.category_id
        WHERE c.category_id = ?
        """;

  private static final String SQL_SELECT_ROOTS = """
        SELECT category_id, name, parent_id
        FROM ITEM_CATEGORIES
        WHERE parent_id IS NULL
        ORDER BY name ASC
        """;

  private static final String SQL_SELECT_SUBS = """
        SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name
        FROM ITEM_CATEGORIES c
        LEFT JOIN ITEM_CATEGORIES p ON c.parent_id = p.category_id
        WHERE c.parent_id = ?
        ORDER BY c.name ASC
        """;

  private static final String SQL_SEARCH = """
        SELECT c.category_id, c.name, c.parent_id, p.name AS parent_name
        FROM ITEM_CATEGORIES c
        LEFT JOIN ITEM_CATEGORIES p ON c.parent_id = p.category_id
        WHERE LOWER(c.name) LIKE LOWER(?)
        ORDER BY c.name ASC
        """;

  private static final String SQL_INSERT =
      "INSERT INTO ITEM_CATEGORIES (name, parent_id) VALUES (?, ?)";

  private static final String SQL_UPDATE =
      "UPDATE ITEM_CATEGORIES SET name = ?, parent_id = ? WHERE category_id = ?";

  private static final String SQL_DELETE =
      "DELETE FROM ITEM_CATEGORIES WHERE category_id = ?";

  private static final String SQL_COUNT_ITEMS =
      "SELECT COUNT(*) FROM ITEMS WHERE category_id = ?";

  // ==========================================================
  // SELECT Methods
  // ==========================================================

  /**
   * Lấy tất cả các danh mục dưới dạng danh sách phẳng.
   *
   * @return Danh sách {@link ItemCategoryDTO}, trả về list rỗng nếu có lỗi hoặc không có dữ liệu.
   */
  public List<ItemCategoryDTO> getAllCategories() {
    List<ItemCategoryDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        list.add(mapRow(rs));
      }
    } catch (SQLException e) {
      logger.error("getAllCategories failed", e);
    }
    return list;
  }

  /**
   * Lấy thông tin chi tiết của một danh mục theo ID.
   *
   * @param categoryId ID của danh mục cần tìm.
   * @return {@link ItemCategoryDTO} nếu tìm thấy, ngược lại trả về {@code null}.
   */
  public ItemCategoryDTO getCategoryById(int categoryId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {
      ps.setInt(1, categoryId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return mapRow(rs);
      }
    } catch (SQLException e) {
      logger.error("getCategoryById failed for id={}", categoryId, e);
    }
    return null;
  }

  /**
   * Xây dựng cấu trúc cây danh mục hoàn chỉnh từ danh sách phẳng (O(n)).
   * Phương thức này tự động gán các danh mục con vào thuộc tính {@code subCategories} của danh mục cha.
   *
   * @return Danh sách các danh mục gốc (Root Categories) đã chứa các nhánh con.
   */
  public List<ItemCategoryDTO> getCategoryTree() {
    List<ItemCategoryDTO> all = getAllCategories();
    Map<Integer, ItemCategoryDTO> map = new HashMap<>();
    List<ItemCategoryDTO> roots = new ArrayList<>();

    for (ItemCategoryDTO c : all) {
      map.put(c.getCategoryId(), c);
      c.setSubCategories(new ArrayList<>());
    }

    for (ItemCategoryDTO c : all) {
      if (c.getParentId() == null) {
        roots.add(c);
      } else {
        ItemCategoryDTO parent = map.get(c.getParentId());
        if (parent != null) {
          parent.getSubCategories().add(c);
        }
      }
    }
    return roots;
  }

  public List<ItemCategoryDTO> getRootCategories() {
    List<ItemCategoryDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ROOTS);
        ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        list.add(mapRow(rs));
      }
    } catch (SQLException e) {
      logger.error("getRootCategories failed", e);
    }
    return list;
  }

  public List<ItemCategoryDTO> getSubCategories(int parentId) {
    List<ItemCategoryDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SELECT_SUBS)) {
      ps.setInt(1, parentId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("getSubCategories failed for parentId={}", parentId, e);
    }
    return list;
  }

  public List<ItemCategoryDTO> searchCategories(String keyword) {
    List<ItemCategoryDTO> list = new ArrayList<>();
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_SEARCH)) {
      // Thêm dấu % để tìm kiếm theo kiểu "chứa ký tự này"
      ps.setString(1, "%" + keyword + "%");
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(mapRow(rs));
        }
      }
    } catch (SQLException e) {
      logger.error("searchCategories failed for keyword={}", keyword, e);
    }
    return list;
  }

  // ==========================================================
  // INSERT / UPDATE / DELETE
  // ==========================================================

  /**
   * Thêm một danh mục mới vào cơ sở dữ liệu.
   *
   * @param dto Đối tượng chứa thông tin danh mục mới (tên và ID cha nếu có).
   * @return ID tự sinh của danh mục mới, hoặc {@code -1} nếu thất bại.
   */
  public int insert(ItemCategoryDTO dto) {
    if (dto == null || dto.getName() == null) return -1;
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, dto.getName());
      if (dto.getParentId() != null) {
        ps.setInt(2, dto.getParentId());
      } else {
        ps.setNull(2, Types.INTEGER);
      }

      if (ps.executeUpdate() > 0) {
        try (ResultSet rs = ps.getGeneratedKeys()) {
          if (rs.next()) return rs.getInt(1);
        }
      }
    } catch (SQLException e) {
      logger.error("insert category failed: {}", dto.getName(), e);
    }
    return -1;
  }

  /**
   * Cập nhật thông tin của một danh mục hiện có.
   *
   * @param dto Đối tượng chứa thông tin cần cập nhật.
   * @return {@code true} nếu cập nhật thành công ít nhất một hàng.
   */
  public boolean update(ItemCategoryDTO dto) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {
      ps.setString(1, dto.getName());
      if (dto.getParentId() != null) {
        ps.setInt(2, dto.getParentId());
      } else {
        ps.setNull(2, Types.INTEGER);
      }
      ps.setInt(3, dto.getCategoryId());
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("update category failed for id={}", dto.getCategoryId(), e);
    }
    return false;
  }

  /**
   * Xóa một danh mục khỏi hệ thống.
   * Ràng buộc: Không thể xóa danh mục nếu đang có sản phẩm (Items) thuộc danh mục đó.
   *
   * @param categoryId ID của danh mục cần xóa.
   * @return {@code true} nếu xóa thành công, {@code false} nếu vi phạm ràng buộc hoặc lỗi SQL.
   */
  public boolean delete(int categoryId) {
    // Kiểm tra ràng buộc sản phẩm trước khi xóa
    if (countItemsInCategory(categoryId) > 0) {
      logger.warn("Cannot delete: categoryId={} still has items associated", categoryId);
      return false;
    }

    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
      ps.setInt(1, categoryId);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.error("delete category failed for id={}", categoryId, e);
    }
    return false;
  }

  // ==========================================================
  // Helpers
  // ==========================================================

  /**
   * Đếm số lượng sản phẩm (Items) đang thuộc về một danh mục cụ thể.
   *
   * @param categoryId ID của danh mục cần kiểm tra.
   * @return Số lượng sản phẩm tìm thấy.
   */
  public int countItemsInCategory(int categoryId) {
    try (Connection conn = DBConnection.getConnection();
        PreparedStatement ps = conn.prepareStatement(SQL_COUNT_ITEMS)) {
      ps.setInt(1, categoryId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getInt(1);
      }
    } catch (SQLException e) {
      logger.error("countItemsInCategory failed", e);
    }
    return 0;
  }

  /**
   * Chuyển đổi một hàng từ {@link ResultSet} sang đối tượng {@link ItemCategoryDTO}.
   *
   * @param rs ResultSet đang trỏ tới hàng hiện tại.
   * @return Đối tượng {@link ItemCategoryDTO}.
   * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột.
   */
  private ItemCategoryDTO mapRow(ResultSet rs) throws SQLException {
    ItemCategoryDTO dto = new ItemCategoryDTO();
    dto.setCategoryId(rs.getInt("category_id"));
    dto.setName(rs.getString("name"));

    int pId = rs.getInt("parent_id");
    dto.setParentId(rs.wasNull() ? null : pId);

    // Cột parent_name chỉ tồn tại trong các câu lệnh SELECT có JOIN
    try {
      dto.setParentName(rs.getString("parent_name"));
    } catch (SQLException ignored) {
      // Bỏ qua nếu query không có alias parent_name
    }

    return dto;
  }
}