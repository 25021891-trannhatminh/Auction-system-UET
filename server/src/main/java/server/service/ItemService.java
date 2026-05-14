package server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.common.enums.ItemStatus;
import server.database.DBConnection;
import server.repository.ItemDAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates seller items using one database transaction.
 *
 * <p>The project flow is intentionally item-first:</p>
 * <ol>
 *   <li>Seller creates an {@code items} row as {@code DRAFT} or {@code PENDING_REVIEW}.</li>
 *   <li>Optional uploaded image URIs are stored in {@code item_images}.</li>
 *   <li>UI-only metadata such as listing flow, size, property, royalty, and currency is stored
 *       in {@code item_attributes}.</li>
 *   <li>If the seller submits for review, admin notifications are created.</li>
 * </ol>
 *
 * <p>Everything is committed together. If image/attribute/notification insert fails, the item
 * insert is rolled back so admin never sees a half-created listing.</p>
 */
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    private static final String SQL_INSERT_IMAGE = """
        INSERT INTO item_images (item_id, url, is_primary, sort_order)
        VALUES (?, ?, ?, ?)
        """;

    private static final String SQL_INSERT_ATTRIBUTE = """
        INSERT INTO item_attributes (item_id, attr_key, attr_value)
        VALUES (?, ?, ?)
        """;

    private static final String SQL_SELECT_ADMINS = "SELECT user_id FROM users WHERE role = 'ADMIN'";

    private static final String SQL_INSERT_NOTIFICATION = """
        INSERT INTO notifications (user_id, type, title, content, is_read, related_id)
        VALUES (?, 'SYSTEM', ?, ?, FALSE, ?)
        """;

    private final ItemDAO itemDAO = new ItemDAO();

    /**
     * Creates an item and all related rows in a single transaction.
     *
     * @param sellerId      authenticated seller id
     * @param categoryId    optional item category id; {@code null} is allowed
     * @param name          item title
     * @param description   item description shown to admin/review flows
     * @param startingPrice starting price for the future auction
     * @param status        {@code DRAFT} for Save Draft, {@code PENDING_REVIEW} for Submit Item
     * @param imageUrls     local/file/cloud URIs copied from the upload picker
     * @param attributes    dynamic metadata stored as key-value rows
     * @return generated item id, or {@code -1} when validation/DB insert fails
     */
    public int createItem(int sellerId,
                          Integer categoryId,
                          String name,
                          String description,
                          BigDecimal startingPrice,
                          ItemStatus status,
                          List<String> imageUrls,
                          Map<String, String> attributes) {

        if (sellerId <= 0 || name == null || name.isBlank() || startingPrice == null
                || startingPrice.compareTo(BigDecimal.ZERO) < 0) {
            return -1;
        }

        ItemStatus safeStatus = (status == null) ? ItemStatus.PENDING_REVIEW : status;
        List<String> safeImages = imageUrls == null ? Collections.emptyList() : imageUrls;
        Map<String, String> safeAttributes = attributes == null ? Collections.emptyMap() : new LinkedHashMap<>(attributes);

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            int itemId = itemDAO.addItem(conn, sellerId, categoryId, name, description, startingPrice, safeStatus);
            if (itemId <= 0) {
                conn.rollback();
                return -1;
            }

            insertImages(conn, itemId, safeImages);
            insertAttributes(conn, itemId, safeAttributes);

            if (safeStatus == ItemStatus.PENDING_REVIEW) {
                notifyAdmins(conn, itemId, name, sellerId);
            }

            conn.commit();
            logger.info("Created item {} for seller {} with status {}", itemId, sellerId, safeStatus);
            return itemId;
        } catch (SQLException e) {
            logger.error("createItem failed for sellerId={}", sellerId, e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    logger.error("Rollback failed while creating item", rollbackEx);
                }
            }
            return -1;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                    // Best-effort cleanup.
                }
            }
        }
    }

    /**
     * Stores uploaded image URIs. The first uploaded image becomes the primary thumbnail.
     */
    private void insertImages(Connection conn, int itemId, List<String> imageUrls) throws SQLException {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_IMAGE)) {
            int sortOrder = 0;
            for (String url : imageUrls) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                ps.setInt(1, itemId);
                ps.setString(2, url.trim());
                ps.setBoolean(3, sortOrder == 0);
                ps.setInt(4, sortOrder);
                ps.addBatch();
                sortOrder++;
            }
            ps.executeBatch();
        }
    }

    /**
     * Stores flexible item metadata without changing the core {@code items} schema.
     */
    private void insertAttributes(Connection conn, int itemId, Map<String, String> attributes) throws SQLException {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_ATTRIBUTE)) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                ps.setInt(1, itemId);
                ps.setString(2, entry.getKey().trim());
                ps.setString(3, entry.getValue().trim());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Creates a simple admin notification so the pending item can surface beyond the table refresh.
     */
    private void notifyAdmins(Connection conn, int itemId, String itemName, int sellerId) throws SQLException {
        try (PreparedStatement adminPs = conn.prepareStatement(SQL_SELECT_ADMINS);
             ResultSet rs = adminPs.executeQuery();
             PreparedStatement notifPs = conn.prepareStatement(SQL_INSERT_NOTIFICATION)) {

            String title = "New item pending approval";
            String content = "Item #" + itemId + " - " + itemName + " from seller #" + sellerId + " is waiting for review.";

            while (rs.next()) {
                notifPs.setInt(1, rs.getInt("user_id"));
                notifPs.setString(2, title);
                notifPs.setString(3, content);
                notifPs.setInt(4, itemId);
                notifPs.addBatch();
            }
            notifPs.executeBatch();
        }
    }
}
