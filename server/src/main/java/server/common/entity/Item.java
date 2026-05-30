package server.common.entity;

import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lớp trừu tượng đại diện cho một sản phẩm có thể đưa ra đấu giá.
 *
 * <p>Các lớp con cụ thể (Electronics, Art, Vehicle, …) phải override
 * {@link #getCategory()} và {@link #validate()} để cung cấp logic riêng.</p>
 *
 * <p>UI:
 * <ul>
 *   <li>{@link #getCategory()} dùng để filter/sort trên danh sách auction.</li>
 *   <li>{@link #getPrimaryImageUrl()} dùng để hiển thị thumbnail.</li>
 * </ul>
 * </p>
 */
public abstract class Item extends Entity {

    private final int   sellerId;       // FK → users.user_id
    private String   name;
    private String   description;
    private BigDecimal startingPrice;
    private final List<String> imageUrls;
    private ItemStatus status;
    private final ItemCategory category;

    protected Item(int sellerId, String name, String description, BigDecimal startingPrice, ItemStatus status, ItemCategory category) {
        super();
        validateStartingPrice(startingPrice);
        this.sellerId      = sellerId;
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.imageUrls     = new ArrayList<>();
        this.status = status;
        this.category = category;
    }

    /** Constructor load từ DB — {@code id} và {@code createdAt} đã có sẵn. */
    protected Item(int id, LocalDateTime createdAt,
                   int sellerId, String name, String description,
                   BigDecimal startingPrice, ItemStatus status, ItemCategory category) {
        super(id, createdAt);
        this.sellerId      = sellerId;
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.imageUrls     = new ArrayList<>();
        this.status = status;
        this.category = category;
    }

    protected Item(Item other){
        super(other.getId(),other.getCreatedAt());
        this.sellerId = other.sellerId;
        this.name = other.name;
        this.description = other.description;
        this.startingPrice = other.startingPrice;
        this.imageUrls = new ArrayList<>(other.imageUrls);
        this.status = other.status;
        this.category = other.category;
    }

    private void validateStartingPrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Starting price must be positive");
    }

    // ── Abstract methods ──────────────────────────────────────────────────────

    /**
     * Trả về tên danh mục của item.
     * Dùng để filter/sort trên danh sách auction trong UI.
     *
     * @return tên danh mục (ví dụ: "Electronics", "Art", "Vehicle")
     */
    public abstract String getCategory();

    /**
     * Kiểm tra tính hợp lệ của các thuộc tính đặc thù từng loại item.
     * Mỗi subclass tự định nghĩa rule validation riêng.
     *
     * @return {@code true} nếu tất cả thuộc tính hợp lệ
     */
    public abstract boolean validate();

    // ── Image management ──────────────────────────────────────────────────────

    public void addImageUrl(String url)    { imageUrls.add(url); }
    public void removeImageUrl(String url) { imageUrls.remove(url); }

    /**
     * Lấy URL ảnh đầu tiên để hiển thị thumbnail.
     *
     * @return URL ảnh chính, hoặc {@code null} nếu chưa có ảnh nào
     */
    public String getPrimaryImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.getFirst();
    }

    /** Trả về view bất biến của danh sách URL ảnh — caller không thể sửa trực tiếp. */
    public List<String> getImageUrls() { return Collections.unmodifiableList(imageUrls); }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public int getSellerId()     { return sellerId; }
    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public BigDecimal getStartingPrice(){ return startingPrice; }
    public ItemStatus getStatus()   { return status;}

    public void setName(String name)            { this.name = name; }
    public void setDescription(String desc)     { this.description = desc; }
    public void setStartingPrice(BigDecimal price)  {
        validateStartingPrice(price);
        this.startingPrice = price;
    }
    public void setStatus(ItemStatus status)    { this.status = status;}

    @Override
    public void printInfo() {
        System.out.printf("[%s] %s — %.2f | %s%n",
            getCategory(), name, startingPrice,status);
    }
}