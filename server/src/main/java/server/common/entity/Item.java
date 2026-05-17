package server.common.entity;


import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
    UI:
        getCategory() dùng để filter/sort trên danh sách auction
        getPrimaryImageUrl() dùng để hiển thị thumbnail
 */
public abstract class Item extends Entity {

    private String   sellerId;       // FK → users.user_id
    private String   name;
    private String   description;
    private BigDecimal startingPrice;
    private final List<String> imageUrls;
    private ItemStatus status;
    private ItemCategory category;

    protected Item(String sellerId, String name, String description, BigDecimal startingPrice, ItemStatus status, ItemCategory category) {
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

    /* Constructor load từ DB */
    protected Item(String id, LocalDateTime createdAt,
                   String sellerId, String name, String description,
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

    // ── Abstract methods ──

    /* Trả về tên danh mục để hiển thị và filter trên UI */
    public abstract String getCategory();

    /* Check các thuộc tính riêng của từng loại item */
    public abstract boolean validate();

    // ── Image management ──

    public void addImageUrl(String url)    { imageUrls.add(url); }
    public void removeImageUrl(String url) { imageUrls.remove(url); }

    /* Ảnh chính để hiển thị thumbnail */
    public String getPrimaryImageUrl() {
        return imageUrls.isEmpty() ? null : imageUrls.get(0);
    }

    public List<String> getImageUrls() { return Collections.unmodifiableList(imageUrls); }  // Return 1 tham chiếu (view) đến List (nhưng đã Override các phương thức để không thể sửa đổi)

    // ── Getters / Setters ──

    public String getSellerId()     { return sellerId; }
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
