package factory;

import model.item.Art;
import model.item.Electronics;
import model.item.Item;
import model.item.Vehicle;

import java.util.Map;

/*
    ItemFactory — Factory pattern:  tạo Item đúng subclass.
        
        Kết nối DB (tầng DAO sử dụng):  DAO đọc DB được category (String).
            ItemDAO.findById() đọc category từ DB → gọi ItemFactory.create()
            với attributes map đọc từ bảng item_attributes.
    
        Kết nối UI/Server:  
            Server khi nhận JSON request từ client cũng dùng Factory để deserialize.
            Khi client gửi request tạo item → Server parse JSON → gọi ItemFactory.create()
        
        Cách dùng:
            // Tạo Electronics
            Map<String, String> attrs = new HashMap<>();
            attrs.put("brand", "Apple");
            attrs.put("warranty_months", "12");
            Item item = ItemFactory.create("ELECTRONICS", sellerId, name, desc, price, attrs);
        
            // Tạo từ DB record
            Item item = ItemFactory.fromDbRecord(resultSet);
 */
public class ItemFactory {

    private ItemFactory() {} // utility class(lớp tiện ích) => không khởi tạo

    /**
    Tạo Item từ category string và attributes map.
    
        @param category  "ELECTRONICS" | "ART" | "VEHICLE"
        @param sellerId  ID của seller
        @param name      Tên sản phẩm
        @param description Mô tả
        @param startingPrice Giá khởi điểm
        @param attributes  Map thuộc tính riêng của từng loại
        @return Item đúng subclass
     */
    public static Item create(String category, String sellerId, String name,
                               String description, double startingPrice,
                               Map<String, String> attributes) {
        if (category == null)
            throw new IllegalArgumentException("Category cannot be null");

        return switch (category.toUpperCase().trim()) {

            case "ELECTRONICS" -> {
                String brand    = getAttr(attributes, "brand", "Unknown");
                int warranty    = Integer.parseInt(getAttr(attributes, "warranty_months", "0"));
                Electronics e   = new Electronics(sellerId, name, description, startingPrice, brand, warranty);
                if (!e.validate())
                    throw new IllegalArgumentException("Invalid Electronics data: brand is required");
                yield e;
            }

            case "ART" -> {
                String artist   = getAttr(attributes, "artist", "Unknown");
                int year        = Integer.parseInt(getAttr(attributes, "year_created", "0"));
                String medium   = getAttr(attributes, "medium", "");
                Art a           = new Art(sellerId, name, description, startingPrice, artist, year, medium);
                if (!a.validate())
                    throw new IllegalArgumentException("Invalid Art data: artist and year are required");
                yield a;
            }

            case "VEHICLE" -> {
                String brand    = getAttr(attributes, "brand", "Unknown");
                String model    = getAttr(attributes, "model", "Unknown");
                int year        = Integer.parseInt(getAttr(attributes, "year_manufactured", "2000"));
                int mileage     = Integer.parseInt(getAttr(attributes, "mileage_km", "0"));
                Vehicle v       = new Vehicle(sellerId, name, description, startingPrice, brand, model, year, mileage);
                if (!v.validate())
                    throw new IllegalArgumentException("Invalid Vehicle data");
                yield v;
            }

            default -> throw new IllegalArgumentException(
                "Unknown item category: '" + category + "'. " +
                "Supported: ELECTRONICS, ART, VEHICLE"
            );
        };
    }

    /*
    Overload tiện lợi — không cần attributes (dùng khi tạo từ UI form đơn giản)
     */
    public static Item create(String category, String sellerId,
                               String name, String description, double startingPrice) {
        return create(category, sellerId, name, description, startingPrice, Map.of());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String getAttr(Map<String, String> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        String val = map.get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
