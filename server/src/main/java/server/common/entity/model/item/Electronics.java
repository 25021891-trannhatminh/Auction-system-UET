package server.common.entity.model.item;

import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 Thuộc tính riêng: brand, warrantyMonths — ánh xạ sang item_attributes trong DB.
 */
public class Electronics extends Item {

    private String brand;
    private int    warrantyMonths;

    public Electronics(int sellerId, String name, String description,
                       BigDecimal startingPrice, ItemStatus status,
                       String brand, int warrantyMonths) {
        super(sellerId, name, description, startingPrice, status, ItemCategory.ELECTRONIC);
        this.brand          = brand;
        this.warrantyMonths = warrantyMonths;
    }

    /** Load từ DB */
    public Electronics(int id, LocalDateTime createdAt,
                       int sellerId, String name, String description,
                       BigDecimal startingPrice,
                       String brand, int warrantyMonths, ItemStatus status) {
        super(id, createdAt, sellerId, name, description, startingPrice, status, ItemCategory.ELECTRONIC);
        this.brand          = brand;
        this.warrantyMonths = warrantyMonths;
    }
    public Electronics(Item item){
        super(item);
    }

    @Override public String  getCategory()      { return "ELECTRONIC"; }
    @Override public boolean validate()         { return brand != null && !brand.isBlank(); }

    public String getBrand()          { return brand; }
    public int    getWarrantyMonths() { return warrantyMonths; }
    public void   setBrand(String b)  { this.brand = b; }
    public void   setWarrantyMonths(int m) { this.warrantyMonths = m; }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.printf("  Brand: %s | Warranty: %d months%n", brand, warrantyMonths);
    }
}
