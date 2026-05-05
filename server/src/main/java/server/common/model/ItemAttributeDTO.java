package server.common.model;

/**
 * DTO cho bảng ITEM_ATTRIBUTES
 *
 * Bảng ITEM_ATTRIBUTES lưu các thuộc tính mở rộng của sản phẩm theo dạng key-value.
 * Thiết kế EAV (Entity-Attribute-Value) cho phép mỗi item có các thuộc tính linh hoạt,
 * không bị giới hạn bởi số cột cố định trong bảng ITEMS.
 *
 * Ví dụ:
 *   item_id=1, attr_key="color",  attr_value="red"
 *   item_id=1, attr_key="weight", attr_value="2.5kg"
 *   item_id=1, attr_key="brand",  attr_value="Sony"
 *
 * Cách dùng:
 *   - Lấy tất cả thuộc tính của 1 item: getAttributesByItemId(itemId)
 *   - Lấy thuộc tính cụ thể: getAttributeByKey(itemId, attrKey)
 */
public class ItemAttributeDTO {

  /** ID thuộc tính (PK) */
  private int attrId;

  /** ID sản phẩm (FK -> ITEMS.item_id) */
  private int itemId;

  /**
   * Tên thuộc tính (key).
   * Ví dụ: "color", "size", "brand", "material", "weight"
   */
  private String attrKey;

  /**
   * Giá trị thuộc tính (value).
   * Ví dụ: "red", "XL", "Nike", "cotton", "500g"
   */
  private String attrValue;

  // ========================== Constructors ==========================

  public ItemAttributeDTO() {}

  /** Constructor đầy đủ */
  public ItemAttributeDTO(int attrId, int itemId, String attrKey, String attrValue) {
    this.attrId = attrId;
    this.itemId = itemId;
    this.attrKey = attrKey;
    this.attrValue = attrValue;
  }

  /** Constructor tạo mới (chưa có attrId) */
  public ItemAttributeDTO(int itemId, String attrKey, String attrValue) {
    this.itemId = itemId;
    this.attrKey = attrKey;
    this.attrValue = attrValue;
  }

  // ========================== Getters & Setters ==========================

  public int getAttrId() { return attrId; }
  public void setAttrId(int attrId) { this.attrId = attrId; }

  public int getItemId() { return itemId; }
  public void setItemId(int itemId) { this.itemId = itemId; }

  public String getAttrKey() { return attrKey; }
  public void setAttrKey(String attrKey) { this.attrKey = attrKey; }

  public String getAttrValue() { return attrValue; }
  public void setAttrValue(String attrValue) { this.attrValue = attrValue; }

  @Override
  public String toString() {
    return "ItemAttributeDTO{" +
        "attrId=" + attrId +
        ", itemId=" + itemId +
        ", attrKey='" + attrKey + '\'' +
        ", attrValue='" + attrValue + '\'' +
        '}';
  }
}