package model.item;

import java.time.LocalDateTime;

/*
 Thuộc tính riêng: brand, warrantyMonths — ánh xạ sang item_attributes trong DB.
 */
public class Electronics extends Item {

    private String brand;
    private int    warrantyMonths;

    public Electronics(String sellerId, String name, String description,
                       double startingPrice, String brand, int warrantyMonths) {
        super(sellerId, name, description, startingPrice);
        this.brand          = brand;
        this.warrantyMonths = warrantyMonths;
    }

    /** Load từ DB */
    public Electronics(String id, LocalDateTime createdAt,
                       String sellerId, String name, String description,
                       double startingPrice,
                       String brand, int warrantyMonths) {
        super(id, createdAt, sellerId, name, description, startingPrice);
        this.brand          = brand;
        this.warrantyMonths = warrantyMonths;
    }

    @Override public String  getCategory()      { return "ELECTRONICS"; }
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
