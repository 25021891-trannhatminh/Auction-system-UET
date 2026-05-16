package server.common.entity.factory;

import server.common.entity.model.item.Art;
import server.common.entity.model.item.Electronics;
import server.common.entity.Item;
import server.common.entity.model.item.Vehicle;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;

import java.util.Map;

/*
    ItemFactory — Factory pattern:  tạo Item đúng subclass.
        
        Kết nối DB (tầng DAO sử dụng):  DAO đọc DB được category (String).
            ItemDAO.findById() đọc category từ DB → gọi ItemFactory.create()
            với attributes map đọc từ bảng item_attributes.
    
        Kết nối UI/Server:  
            Server khi nhận request từ client cũng dùng Factory để deserialize.
            Khi client gửi request tạo item → Server parse request → gọi ItemFactory.create()
        
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
    public static Item create(ItemCategory category, String sellerId, String name,
                               String description, double startingPrice, ItemStatus status,
                               Map<String, String> attributes) {
        if (category == null)
            throw new IllegalArgumentException("Category cannot be null");

        return switch (category) {

            case ItemCategory.ELECTRONIC -> {
                String brand    = getAttr(attributes, "brand", "Unknown");
                int warranty    = Integer.parseInt(getAttr(attributes, "warranty_months", "0"));
                Electronics electronics   = new Electronics(sellerId, name, description, startingPrice, status, brand, warranty);
                if (!electronics.validate())
                    throw new IllegalArgumentException("Invalid Electronics data: brand is required");
                yield electronics;
            }

            case ItemCategory.ART -> {
                String artist   = getAttr(attributes, "artist", "Unknown");
                int year        = Integer.parseInt(getAttr(attributes, "year_created", "0"));
                String medium   = getAttr(attributes, "medium", "");
                Art art           = new Art(sellerId, name, description, startingPrice, status, artist, year, medium);
                if (!art.validate())
                    throw new IllegalArgumentException("Invalid Art data: artist and year are required");
                yield art;
            }

            case ItemCategory.VEHICLE -> {
                String brand    = getAttr(attributes, "brand", "Unknown");
                String model    = getAttr(attributes, "model", "Unknown");
                int year        = Integer.parseInt(getAttr(attributes, "year_manufactured", "2000"));
                int mileage     = Integer.parseInt(getAttr(attributes, "mileage_km", "0"));
                Vehicle vehicle       = new Vehicle(sellerId, name, description, startingPrice, status, brand, model, year, mileage);
                if (!vehicle.validate())
                    throw new IllegalArgumentException("Invalid Vehicle data");
                yield vehicle;
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
    public static Item create(ItemCategory category, String sellerId,
                               String name, String description, double startingPrice,ItemStatus status) {
        return create(category, sellerId, name, description, startingPrice, status, Map.of());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String getAttr(Map<String, String> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        String val = map.get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
