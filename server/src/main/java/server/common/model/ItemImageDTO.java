package server.common.model;

/**
 * DTO cho bảng ITEM_IMAGES
 *
 * Bảng ITEM_IMAGES lưu các ảnh của một sản phẩm (item).
 * Mỗi item có thể có nhiều ảnh; một ảnh được đánh dấu là ảnh chính (is_primary = true).
 *
 * Cách dùng:
 *   - Khi hiển thị chi tiết sản phẩm, load danh sách ảnh theo item_id.
 *   - is_primary = true -> ảnh thumbnail đại diện.
 *   - sort_order -> thứ tự hiển thị ảnh trong gallery.
 */
public class ItemImageDTO {

  /** ID ảnh (PK) */
  private int imageId;

  /** ID sản phẩm (FK -> ITEMS.item_id) */
  private int itemId;

  /** URL đường dẫn tới ảnh */
  private String url;

  /**
   * Có phải ảnh chính (thumbnail) không.
   * Mỗi item chỉ nên có duy nhất 1 ảnh primary.
   */
  private boolean isPrimary;

  /**
   * Thứ tự sắp xếp hiển thị trong gallery.
   * Số nhỏ hơn hiển thị trước.
   */
  private int sortOrder;

  // ========================== Constructors ==========================

  public ItemImageDTO() {}

  /** Constructor đầy đủ */
  public ItemImageDTO(int imageId, int itemId, String url, boolean isPrimary, int sortOrder) {
    this.imageId = imageId;
    this.itemId = itemId;
    this.url = url;
    this.isPrimary = isPrimary;
    this.sortOrder = sortOrder;
  }

  /** Constructor tạo mới ảnh (chưa có imageId) */
  public ItemImageDTO(int itemId, String url, boolean isPrimary, int sortOrder) {
    this.itemId = itemId;
    this.url = url;
    this.isPrimary = isPrimary;
    this.sortOrder = sortOrder;
  }

  // ========================== Getters & Setters ==========================

  public int getImageId() { return imageId; }
  public void setImageId(int imageId) { this.imageId = imageId; }

  public int getItemId() { return itemId; }
  public void setItemId(int itemId) { this.itemId = itemId; }

  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }

  public boolean isPrimary() { return isPrimary; }
  public void setPrimary(boolean primary) { isPrimary = primary; }

  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

  @Override
  public String toString() {
    return "ItemImageDTO{" +
        "imageId=" + imageId +
        ", itemId=" + itemId +
        ", url='" + url + '\'' +
        ", isPrimary=" + isPrimary +
        ", sortOrder=" + sortOrder +
        '}';
  }
}