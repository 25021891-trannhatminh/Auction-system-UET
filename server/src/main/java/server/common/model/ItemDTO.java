package server.common.model;

import server.common.enums.ItemStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * DTO cho bảng ITEMS.
 *
 * <p>Dùng để truyền dữ liệu sản phẩm giữa các layer
 * (DAO, Service, Controller).</p>
 */
public class ItemDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** ID sản phẩm (Primary Key) */
  private int itemId;

  /** ID người bán (FK -> USERS) */
  private int sellerId;

  /** ID danh mục (FK -> ITEM_CATEGORIES) */
  private int categoryId;

  /** Tên sản phẩm */
  private String name;

  /** Mô tả sản phẩm */
  private String description;

  /** Giá khởi điểm */
  private BigDecimal startingPrice;

  /** Trạng thái sản phẩm */
  private ItemStatus status;

  /** Thời điểm tạo */
  private Timestamp createdAt;

  // ========================== Constructors ==========================

  /** Constructor rỗng */
  public ItemDTO() {}

  /**
   * Constructor đầy đủ.
   *
   * @param itemId ID sản phẩm
   * @param sellerId ID người bán
   * @param categoryId ID danh mục
   * @param name tên sản phẩm
   * @param description mô tả
   * @param startingPrice giá khởi điểm
   * @param status trạng thái sản phẩm
   * @param createdAt thời điểm tạo
   */
  public ItemDTO(int itemId, int sellerId, int categoryId,
      String name, String description,
      BigDecimal startingPrice, ItemStatus status,
      Timestamp createdAt) {
    this.itemId = itemId;
    this.sellerId = sellerId;
    this.categoryId = categoryId;
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.status = status;
    this.createdAt = createdAt;
  }

  // ========================== Getters & Setters ==========================

  /** @return ID sản phẩm */
  public int getItemId() { return itemId; }

  /** @param itemId ID sản phẩm */
  public void setItemId(int itemId) { this.itemId = itemId; }

  /** @return ID người bán */
  public int getSellerId() { return sellerId; }

  /** @param sellerId ID người bán */
  public void setSellerId(int sellerId) { this.sellerId = sellerId; }

  /** @return ID danh mục */
  public int getCategoryId() { return categoryId; }

  /** @param categoryId ID danh mục */
  public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

  /** @return tên sản phẩm */
  public String getName() { return name; }

  /** @param name tên sản phẩm */
  public void setName(String name) { this.name = name; }

  /** @return mô tả sản phẩm */
  public String getDescription() { return description; }

  /** @param description mô tả sản phẩm */
  public void setDescription(String description) { this.description = description; }

  /** @return giá khởi điểm */
  public BigDecimal getStartingPrice() { return startingPrice; }

  /** @param startingPrice giá khởi điểm */
  public void setStartingPrice(BigDecimal startingPrice) { this.startingPrice = startingPrice; }

  /** @return trạng thái sản phẩm */
  public ItemStatus getStatus() { return status; }

  /** @param status trạng thái sản phẩm */
  public void setStatus(ItemStatus status) { this.status = status; }

  /** @return thời điểm tạo */
  public Timestamp getCreatedAt() { return createdAt; }

  /** @param createdAt thời điểm tạo */
  public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}