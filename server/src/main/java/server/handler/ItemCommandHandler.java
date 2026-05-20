package server.handler;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;
import server.database.DBConnection;
import server.network.ClientManager;
import server.service.AuctionService;
import server.service.ItemService;

/**
 * Handles item-related socket commands so {@link ClientHandler} stays focused on routing.
 *
 * <p>Supported flows:</p>
 * <ul>
 *   <li>Seller creates an item with category enum values matching {@code database/01_schema.sql}.</li>
 *   <li>Seller dashboard loads real item counters.</li>
 *   <li>Admin dashboard lists, approves, and rejects pending items.</li>
 * </ul>
 */
public class ItemCommandHandler {

    private static final int MAX_IMAGE_COUNT = 5;

    private final ClientHandler client;
    private final ItemService itemService;
    private final AuctionService auctionService;

    public ItemCommandHandler(ClientHandler client, ItemService itemService, AuctionService auctionService) {
        this.client = client;
        this.itemService = itemService;
        this.auctionService = auctionService;
    }

    /**
     * Handles seller item creation.
     *
     * <p>Payload format:</p>
     * <pre>
     * sellerId|category|title|description|price|status|listingFlow|size|property|royalty|currency|imageUris
     * </pre>
     */
    public void handleCreateItem(String payload, int authenticatedUserId) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 12) {
            client.send("CREATE_ITEM_FAIL INVALID_PAYLOAD");
            return;
        }

        int requestedSellerId = parseIntOrDefault(safeField(fields, 0), 0);
        int sellerId = authenticatedUserId > 0 ? authenticatedUserId : requestedSellerId;
        if (sellerId <= 0) {
            client.send("CREATE_ITEM_FAIL NOT_AUTHENTICATED");
            return;
        }

        ItemCategory category = parseItemCategory(safeField(fields, 1));
        String name = safeField(fields, 2);
        String description = safeField(fields, 3);
        BigDecimal startingPrice = parseBigDecimal(safeField(fields, 4));
        ItemStatus status = parseItemStatus(safeField(fields, 5));
        String listingFlow = safeField(fields, 6);
        String size = safeField(fields, 7);
        String property = safeField(fields, 8);
        String royalty = safeField(fields, 9);
        String currency = safeField(fields, 10);
        String imagePayload = safeField(fields, 11);

        if (category == null || name.isBlank()
                || startingPrice == null || startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            client.send("CREATE_ITEM_FAIL INVALID_DATA");
            return;
        }

        List<String> imageUrls = parseImageUrls(imagePayload);
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("listing_flow", listingFlow.isBlank() ? "Timed Auction" : listingFlow);
        if (!size.isBlank()) attributes.put("size_condition", size);
        if (!property.isBlank()) attributes.put("property", property);
        if (!royalty.isBlank()) attributes.put("royalty", royalty);
        if (!currency.isBlank()) attributes.put("currency", currency);

        int itemId = itemService.createItem(
            sellerId,
            category,
            name,
            description,
            startingPrice,
            status,
            imageUrls,
            attributes
        );

        if (itemId > 0) {
            client.send("CREATE_ITEM_SUCCESS " + fields(itemId, status.name()));
            if (status == ItemStatus.PENDING_REVIEW) {
                ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
            }
            return;
        }

        client.send("CREATE_ITEM_FAIL SAVE_ERROR");
    }

    public void sendUserItemStats(String sellerIdText, int authenticatedUserId) {
        int sellerId = authenticatedUserId > 0 ? authenticatedUserId : parseIntOrDefault(sellerIdText, 0);
        if (sellerId <= 0) {
            client.send("USER_ITEM_STATS " + fields(0, 0, 0, 0));
            return;
        }

        String sql = """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END) AS drafts,
                   SUM(CASE WHEN status = 'IN_AUCTION' THEN 1 ELSE 0 END) AS active_sales,
                   SUM(CASE WHEN status = 'SOLD' THEN 1 ELSE 0 END) AS sold
            FROM items
            WHERE seller_id = ?
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    client.send("USER_ITEM_STATS " + fields(
                        rs.getInt("total"),
                        rs.getInt("drafts"),
                        rs.getInt("active_sales"),
                        rs.getInt("sold")
                    ));
                    return;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        client.send("USER_ITEM_STATS " + fields(0, 0, 0, 0));
    }

    public void sendAdminItems() {
        String sql = """
            SELECT i.item_id, i.seller_id, COALESCE(seller.username, '') AS seller_username,
                   COALESCE(i.category, 'Uncategorized') AS category_name,
                   i.name, i.description, i.starting_price, i.status, i.created_at,
                   a.auction_id, a.status AS auction_status, a.current_price,
                   COALESCE(bid_counts.bid_count, 0) AS bid_count,
                   COALESCE(imgs.image_urls, '') AS image_urls,
                   COALESCE(attrs.attribute_lines, '') AS attribute_lines
            FROM items i
            LEFT JOIN accounts seller ON seller.user_id = i.seller_id
            LEFT JOIN auctions a ON a.item_id = i.item_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bid_transactions
                GROUP BY auction_id
            ) bid_counts ON bid_counts.auction_id = a.auction_id
            LEFT JOIN (
                SELECT item_id,
                       GROUP_CONCAT(url ORDER BY is_primary DESC, sort_order ASC, image_id ASC
                                    SEPARATOR '\\n') AS image_urls
                FROM item_images
                GROUP BY item_id
            ) imgs ON imgs.item_id = i.item_id
            LEFT JOIN (
                SELECT item_id,
                       GROUP_CONCAT(CONCAT(attr_key, ': ', attr_value) ORDER BY attr_id ASC
                                    SEPARATOR '\\n') AS attribute_lines
                FROM item_attributes
                GROUP BY item_id
            ) attrs ON attrs.item_id = i.item_id
            ORDER BY i.created_at DESC, i.item_id DESC
            """;

        client.send("ADMIN_ITEMS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                client.send("ADMIN_ITEM " + fields(
                    rs.getInt("item_id"),
                    rs.getInt("seller_id"),
                    rs.getString("seller_username"),
                    rs.getString("category_name"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getBigDecimal("starting_price"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at"),
                    nullableInt(rs, "auction_id"),
                    rs.getString("auction_status"),
                    rs.getBigDecimal("current_price"),
                    rs.getInt("bid_count"),
                    rs.getString("image_urls"),
                    rs.getString("attribute_lines")
                ));
            }
        } catch (SQLException e) {
            client.send("ADMIN_DATA_ERROR " + fields("ITEMS", e.getMessage()));
            e.printStackTrace();
        }
        client.send("ADMIN_ITEMS_END");
    }

    public void approveItem(String itemIdText, int adminId) {
        int itemId = parseIntOrDefault(itemIdText, 0);
        if (itemId <= 0) {
            client.send("ADMIN_APPROVE_FAIL INVALID_FORMAT");
            return;
        }
        boolean success = auctionService.getAdminService().approveItem(adminId, itemId);
        client.send(success ? "ADMIN_APPROVE_SUCCESS" : "ADMIN_APPROVE_FAIL");
        if (success) {
            ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
        }
    }

    public void rejectItem(String itemIdText, String reason, int adminId) {
        int itemId = parseIntOrDefault(itemIdText, 0);
        if (itemId <= 0) {
            client.send("ADMIN_REJECT_FAIL INVALID_FORMAT");
            return;
        }
        boolean success = auctionService.getAdminService().rejectItem(adminId, itemId, reason == null ? "" : reason);
        client.send(success ? "ADMIN_REJECT_SUCCESS" : "ADMIN_REJECT_FAIL");
        if (success) {
            ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
        }
    }

    private List<String> parseImageUrls(String imagePayload) {
        List<String> imageUrls = new ArrayList<>();
        if (imagePayload == null || imagePayload.isBlank()) {
            return imageUrls;
        }
        for (String image : imagePayload.split("\n")) {
            if (image != null && !image.isBlank()) {
                imageUrls.add(image.trim());
                if (imageUrls.size() == MAX_IMAGE_COUNT) {
                    break;
                }
            }
        }
        return imageUrls;
    }

    private List<String> splitPayload(String payload) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < payload.length(); i++) {
            char character = payload.charAt(i);
            if (escaped) {
                switch (character) {
                    case 'p' -> current.append('|');
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    case '\\' -> current.append('\\');
                    default -> current.append(character);
                }
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '|') {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        if (escaped) {
            current.append('\\');
        }
        fields.add(current.toString());
        return fields;
    }

    private String safeField(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ItemStatus parseItemStatus(String value) {
        try {
            return value == null || value.isBlank()
                ? ItemStatus.PENDING_REVIEW
                : ItemStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ItemStatus.PENDING_REVIEW;
        }
    }

    private ItemCategory parseItemCategory(String value) {
        try {
            return value == null || value.isBlank()
                ? null
                : ItemCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String fields(Object... values) {
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            encoded.add(encodeField(value));
        }
        return String.join("|", encoded);
    }

    private String encodeField(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
            .replace("\\", "\\\\")
            .replace("|", "\\p")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
