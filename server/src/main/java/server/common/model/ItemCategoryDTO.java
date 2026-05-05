package server.common.model;

import java.util.List;

/**
 * DTO cho bảng ITEM_CATEGORIES
 *
 * Bảng ITEM_CATEGORIES lưu danh mục sản phẩm theo cấu trúc cây (tự tham chiếu).
 * Mỗi danh mục có thể có một danh mục cha (parent_id), cho phép tổ chức phân cấp.
 *
 * Cách dùng:
 *   - Khi trả về danh sách danh mục gốc (không có parent), subCategories có thể được populate.
 *   - Khi chỉ cần thông tin cơ bản (vd: dropdown chọn category), dùng ItemCategoryBasicDTO.
 */
public class ItemCategoryDTO {

  /** ID danh mục (PK) */
  private int categoryId;

  /** Tên danh mục */
  private String name;

  /**
   * ID danh mục cha (FK -> ITEM_CATEGORIES.category_id).
   * Null nếu đây là danh mục gốc (root category).
   */
  private Integer parentId;

  /**
   * Tên danh mục cha (JOIN từ bảng ITEM_CATEGORIES).
   * Tiện lợi để hiển thị mà không cần truy vấn thêm.
   */
  private String parentName;

  /**
   * Danh sách danh mục con (nếu cần load cây phân cấp đầy đủ).
   * Chỉ populate khi cần thiết (tránh query thừa).
   */
  private List<ItemCategoryDTO> subCategories;

  // ========================== Constructors ==========================

  public ItemCategoryDTO() {}

  /** Constructor cơ bản - dùng khi không cần parent/sub info */
  public ItemCategoryDTO(int categoryId, String name) {
    this.categoryId = categoryId;
    this.name = name;
  }

  /** Constructor đầy đủ - dùng khi có thông tin parent */
  public ItemCategoryDTO(int categoryId, String name, Integer parentId, String parentName) {
    this.categoryId = categoryId;
    this.name = name;
    this.parentId = parentId;
    this.parentName = parentName;
  }

  // ========================== Getters & Setters ==========================

  public int getCategoryId() { return categoryId; }
  public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public Integer getParentId() { return parentId; }
  public void setParentId(Integer parentId) { this.parentId = parentId; }

  public String getParentName() { return parentName; }
  public void setParentName(String parentName) { this.parentName = parentName; }

  public List<ItemCategoryDTO> getSubCategories() { return subCategories; }
  public void setSubCategories(List<ItemCategoryDTO> subCategories) { this.subCategories = subCategories; }

  // ========================== Helper Methods ==========================

  /** Kiểm tra có phải danh mục gốc không */
  public boolean isRootCategory() {
    return this.parentId == null;
  }

  /** Kiểm tra có danh mục con không */
  public boolean hasSubCategories() {
    return this.subCategories != null && !this.subCategories.isEmpty();
  }

  @Override
  public String toString() {
    return "ItemCategoryDTO{" +
        "categoryId=" + categoryId +
        ", name='" + name + '\'' +
        ", parentId=" + parentId +
        ", parentName='" + parentName + '\'' +
        '}';
  }
}