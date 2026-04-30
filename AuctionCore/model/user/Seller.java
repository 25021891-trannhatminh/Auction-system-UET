package model.user;

import enums.UserRole;
import enums.UserStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
Seller
    DB: listedItemIds ánh xạ sang items.seller_id.
    DAO sẽ lazy-load danh sách item khi cần.
    UI: hiển thị trên profile trang Seller.
 */
public class Seller extends User {

    private double rating;            // 0.0 – 5.0
    private final List<String> listedItemIds; // FK sang items.item_id

    public Seller(String username, String email, String passwordHash,
                  String fullName, String phone) {
        super(username, email, passwordHash, fullName, phone, UserRole.SELLER);
        this.rating        = 0.0;
        this.listedItemIds = new ArrayList<>();
    }

    /** Constructor load từ DB */
    public Seller(String id, LocalDateTime createdAt,
                  String username, String email, String passwordHash,
                  String fullName, String phone, UserStatus status,
                  LocalDateTime lastLogin, double rating) {
        super(id, createdAt, username, email, passwordHash, fullName, phone,
              UserRole.SELLER, status, lastLogin);
        this.rating        = rating;
        this.listedItemIds = new ArrayList<>();
    }

    public void addListedItem(String itemId)    { listedItemIds.add(itemId); }
    public void removeListedItem(String itemId) { listedItemIds.remove(itemId); }

    public double          getRating()       { return rating; }
    public List<String>    getListedItemIds(){ return Collections.unmodifiableList(listedItemIds); }    // Return 1 tham chiếu (view) đến List (nhưng đã Override các phương thức để không thể sửa đổi)

    public void updateRating(double newRating) {
        if (newRating < 0 || newRating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        this.rating = newRating;
    }


    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf("  Rating: %.1f/5.0 | Total sales: %d | Items listed: %d%n",
            rating, listedItemIds.size());
    }
}
