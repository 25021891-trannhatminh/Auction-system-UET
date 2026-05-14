package server.common.entity;


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
    private double   startingPrice;
    private final List<String> imageUrls;

    protected Item(String sellerId, String name, String description, double startingPrice) {
        super();
        validateStartingPrice(startingPrice);
        this.sellerId      = sellerId;
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.imageUrls     = new ArrayList<>();
    }

    /* Constructor load từ DB */
    protected Item(String id, LocalDateTime createdAt,
                   String sellerId, String name, String description,
                   double startingPrice) {
        super(id, createdAt);
        this.sellerId      = sellerId;
        this.name          = name;
        this.description   = description;
        this.startingPrice = startingPrice;
        this.imageUrls     = new ArrayList<>();
    }

    private void validateStartingPrice(double price) {
        if (price <= 0) throw new IllegalArgumentException("Starting price must be positive");
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
    public double getStartingPrice(){ return startingPrice; }

    public void setName(String name)            { this.name = name; }
    public void setDescription(String desc)     { this.description = desc; }
    public void setStartingPrice(double price)  {
        validateStartingPrice(price);
        this.startingPrice = price;
    }

    @Override
    public void printInfo() {
        System.out.printf("[%s] %s — %.2f | %s%n",
            getCategory(), name, startingPrice);
    }
}
