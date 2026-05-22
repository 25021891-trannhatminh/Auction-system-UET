package server.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.common.entity.Item;
import server.common.entity.factory.ItemFactory;
import server.common.enums.ItemCategory;
import server.common.enums.ItemStatus;
import server.database.DBConnection;

/**
 * Data Access Object cho bảng {@code ITEMS}.
 *
 * <p>Chịu trách nhiệm các thao tác CRUD với dữ liệu sản phẩm.
 * Mọi lỗi {@link SQLException} được xử lý nội bộ và ghi log qua SLF4J.</p>
 */
public class ItemDAO {

    private static final Logger logger = LoggerFactory.getLogger(ItemDAO.class);

    // Khởi tạo 1 lần duy nhất thay vì new mỗi lần -> tránh N + 1 obj creation
    private final ItemAttributeDAO itemAttributeDAO = new ItemAttributeDAO();

    private static final String SQL_NEXT_ITEM_ID = "SELECT nextval(seq_items)";

    private static final String SQL_SELECT_BASE = """
        SELECT item_id, seller_id, category, name, description,
               starting_price, status, created_at
        FROM items
        """;

    private static final String SQL_INSERT = """
        INSERT INTO items (item_id, seller_id, category, name, description, starting_price, status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_UPDATE = """
        UPDATE items
        SET category = ?, name = ?, description = ?, starting_price = ?
        WHERE item_id = ?
        """;

    private static final String SQL_UPDATE_STATUS =
        "UPDATE items SET status = ? WHERE item_id = ?";

    private static final String SQL_CHECK_AUCTION_RELIANCE =
        "SELECT COUNT(*) FROM auctions WHERE item_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM items WHERE item_id = ?";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE item_id = ?";

    private static final String SQL_SELECT_BY_SELLER =
        SQL_SELECT_BASE + " WHERE seller_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_BY_SELLER_STATUS =
        SQL_SELECT_BASE + " WHERE seller_id = ? AND status = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_ALL =
        SQL_SELECT_BASE + " ORDER BY created_at DESC";

    private static final String SQL_SELECT_ALL_PAGED =
        SQL_SELECT_BASE + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

    private static final String SQL_COUNT_ALL =
        "SELECT COUNT(*) FROM items";

    // Lấy items theo đúng category
    private static final String SQL_SELECT_BY_CATEGORY =
        SQL_SELECT_BASE + " WHERE category = ? ORDER BY created_at DESC";

    // Lấy items theo status
    private static final String SQL_SELECT_BY_STATUS =
        SQL_SELECT_BASE + " WHERE status = ? ORDER BY created_at DESC";

    // Tìm kiếm theo tên (LIKE)
    private static final String SQL_SEARCH_BY_NAME =
        SQL_SELECT_BASE + " WHERE LOWER(name) LIKE LOWER(?) ORDER BY created_at DESC";

    // Tìm kiếm theo tên + lọc theo status
    private static final String SQL_SEARCH_BY_NAME_AND_STATUS =
        SQL_SELECT_BASE + " WHERE LOWER(name) LIKE LOWER(?) AND status = ? ORDER BY created_at DESC";

    // SQL mới
    private static final String SQL_SELECT_PENDING_REVIEW =
        SQL_SELECT_BASE + " WHERE status = 'PENDING_REVIEW' ORDER BY created_at ASC";

    private static final String SQL_APPROVE_ITEM =
        "UPDATE items SET status = 'AVAILABLE' WHERE item_id = ? AND status = 'PENDING_REVIEW'";

    private static final String SQL_REJECT_ITEM =
        "UPDATE items SET status = 'REMOVED' WHERE item_id = ? AND status = 'PENDING_REVIEW'";

    /**
     * Thêm một sản phẩm mới vào hệ thống.
     *
     * @param sellerId      ID của người bán (người sở hữu sản phẩm)
     * @param category    Loại danh mục sản phẩm
     * @param name          Tên sản phẩm
     * @param description   Mô tả chi tiết sản phẩm
     * @param startingPrice Giá khởi điểm để đấu giá
     * @return {@code true} nếu thêm thành công, {@code false} nếu dữ liệu không hợp lệ hoặc lỗi DB
     */
    public boolean addItem(int sellerId, ItemCategory category, String name,
                           String description, BigDecimal startingPrice) {
        return addItem(sellerId, category, name, description, startingPrice, ItemStatus.PENDING_REVIEW) > 0;
    }

    /**
     * Adds a new item to the database and returns its generated item id.
     *
     * <p>This method opens its own database connection and delegates the actual insert operation to
     * the transactional {@link #addItem(Connection, int, ItemCategory, String, String, BigDecimal,
     * ItemStatus)} overload. Use this method when the item does not need to be created together with
     * images, attributes, or notifications in the same transaction.
     *
     * @param sellerId the id of the account that owns the item
     * @param category the category of the item
     * @param name the display name of the item
     * @param description the detailed description of the item
     * @param startingPrice the initial auction price of the item
     * @param status the initial item status; defaults to {@code PENDING_REVIEW} when {@code null}
     * @return the generated item id if the insert succeeds, or {@code -1} if validation or database
     *     insertion fails
     */
    public int addItem(int sellerId, ItemCategory category, String name, String description, BigDecimal startingPrice, ItemStatus status) {
    try (Connection conn = DBConnection.getConnection()) {
        return addItem(conn, sellerId, category, name, description, startingPrice, status);
    } catch (SQLException e) {
        System.err.println("Failed to add item: " + e.getMessage());
        return -1;
    }
    }

    /**
     * Adds a new item using an existing database connection.
     *
     * <p>This overload is intended for service-layer workflows that need to keep the item row, item
     * images, item attributes, and notifications inside the same transaction. The caller owns the
     * connection and is responsible for committing or rolling back the transaction.
     *
     * @param conn the active database connection used for the current transaction
     * @param sellerId the id of the account that owns the item
     * @param category the category of the item
     * @param name the display name of the item
     * @param description the detailed description of the item
     * @param startingPrice the initial auction price of the item
     * @param status the initial item status; defaults to {@code PENDING_REVIEW} when {@code null}
     * @return the generated item id if the insert succeeds, or {@code -1} if validation fails or no id
     *     can be generated
     * @throws SQLException if the id generation or database insertion fails
     */
    public int addItem(Connection conn, int sellerId, ItemCategory category, String name, String description, BigDecimal startingPrice, ItemStatus status)
        throws SQLException {
    if (isInvalidItemData(name, startingPrice)) {
        return -1;
    }

    int itemId;
    try (PreparedStatement idStatement = conn.prepareStatement(SQL_NEXT_ITEM_ID);
        ResultSet resultSet = idStatement.executeQuery()) {
        if (!resultSet.next()) {
        return -1;
        }
        itemId = resultSet.getInt(1);
    }

    ItemStatus initialStatus = status == null ? ItemStatus.PENDING_REVIEW : status;

    try (PreparedStatement statement = conn.prepareStatement(SQL_INSERT)) {
        statement.setInt(1, itemId);
        statement.setInt(2, sellerId);
        statement.setString(3, category.name());
        statement.setString(4, name.trim());
        statement.setString(5, description == null ? "" : description.trim());
        statement.setBigDecimal(6, startingPrice);
        statement.setString(7, initialStatus.name());

        return statement.executeUpdate() > 0 ? itemId : -1;
    }
    }

    /**
     * Lấy danh sách tất cả sản phẩm đang chờ admin kiểm duyệt.
     * Sắp xếp cũ nhất lên đầu để duyệt theo thứ tự FIFO.
     *
     * @return Danh sách {@link Item} có status {@code PENDING_REVIEW}
     */
    public List<Item> getPendingReviewItems() {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_PENDING_REVIEW);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(getItemByRow(rs));
            logger.debug("getPendingReviewItems() – Found {} item(s) pending review", list.size());

        } catch (SQLException e) {
            logger.error("getPendingReviewItems() – DB error", e);
        }
        return list;
    }

    /**
     * Admin duyệt sản phẩm — chuyển {@code PENDING_REVIEW} → {@code AVAILABLE}.
     *
     * <p>Điều kiện {@code AND status = 'PENDING_REVIEW'} đảm bảo không duyệt
     * nhầm sản phẩm đã được xử lý trước đó.</p>
     *
     * @param itemId ID sản phẩm cần duyệt
     * @return {@code true} nếu duyệt thành công;
     *         {@code false} nếu không tìm thấy hoặc không ở PENDING_REVIEW
     */
    public boolean approveItem(int itemId) {
        logger.debug("approveItem() – itemId={}", itemId);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_APPROVE_ITEM)) {

            ps.setInt(1, itemId);

            boolean approved = ps.executeUpdate() > 0;
            if (approved) {
                logger.info("approveItem() – itemId={} approved → AVAILABLE", itemId);
            } else {
                logger.warn("approveItem() – itemId={} not found or not PENDING_REVIEW", itemId);
            }
            return approved;

        } catch (SQLException e) {
            logger.error("approveItem() – DB error for itemId={}", itemId, e);
            return false;
        }
    }

    /**
     * Admin từ chối sản phẩm — chuyển {@code PENDING_REVIEW} → {@code REMOVED}.
     *
     * <p>Điều kiện {@code AND status = 'PENDING_REVIEW'} đảm bảo không từ chối
     * nhầm sản phẩm đã được xử lý trước đó.</p>
     *
     * @param itemId ID sản phẩm cần từ chối
     * @return {@code true} nếu từ chối thành công;
     *         {@code false} nếu không tìm thấy hoặc không ở PENDING_REVIEW
     */
    public boolean rejectItem(int itemId) {
        logger.debug("rejectItem() – itemId={}", itemId);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_REJECT_ITEM)) {

            ps.setInt(1, itemId);

            boolean rejected = ps.executeUpdate() > 0;
            if (rejected) {
                logger.warn("rejectItem() – itemId={} rejected → REMOVED", itemId);
            } else {
                logger.warn("rejectItem() – itemId={} not found or not PENDING_REVIEW", itemId);
            }
            return rejected;

        } catch (SQLException e) {
            logger.error("rejectItem() – DB error for itemId={}", itemId, e);
            return false;
        }
    }

    /**
     * Cập nhật thông tin chi tiết của một sản phẩm hiện có.
     *
     * @param itemId        ID của sản phẩm cần cập nhật
     * @param categoryId    ID danh mục mới
     * @param name          Tên sản phẩm mới
     * @param description   Mô tả sản phẩm mới
     * @param startingPrice Giá khởi điểm mới
     * @return {@code true} nếu cập nhật thành công ít nhất một hàng
     */
    public boolean updateItem(int itemId, int categoryId, String name,
                              String description, BigDecimal startingPrice) {

        if (name == null || name.isBlank()) return false;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            ps.setInt(1, categoryId);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setBigDecimal(4, startingPrice);
            ps.setInt(5, itemId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("updateItem failed for itemId={}", itemId, e);
            return false;
        }
    }

    /**
     * Cập nhật trạng thái của một sản phẩm.
     *
     * <p>Dùng khi chuyển trạng thái sản phẩm, ví dụ:
     * {@code AVAILABLE} → {@code IN_AUCTION} khi tạo phiên đấu giá,
     * {@code IN_AUCTION} → {@code SOLD} khi đấu giá kết thúc.</p>
     *
     * @param itemId ID sản phẩm cần cập nhật
     * @param status Trạng thái mới
     * @return {@code true} nếu cập nhật thành công
     */
    public boolean updateStatus(int itemId, ItemStatus status) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, status.name());
            ps.setInt(2, itemId);

            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logger.info("updateStatus() – itemId={} -> {}", itemId, status);
            } else {
                logger.warn("updateStatus() – No rows affected for itemId={}", itemId);
            }
            return updated;
        } catch (SQLException e) {
            logger.error("updateStatus failed for itemId={}", itemId, e);
            return false;
        }
    }

    /**
     * Xóa sản phẩm khỏi hệ thống nếu sản phẩm đó chưa từng tham gia phiên đấu giá nào.
     *
     * @param itemId ID của sản phẩm cần xóa
     * @return {@code true} nếu xóa thành công, {@code false} nếu có ràng buộc dữ liệu hoặc lỗi SQL
     */
    public boolean deleteItem(int itemId) {
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_CHECK_AUCTION_RELIANCE)) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        logger.warn("Cannot delete itemId={}: linked to existing auctions", itemId);
                        return false;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
                ps.setInt(1, itemId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.error("deleteItem failed for itemId={}", itemId, e);
            return false;
        }
    }

    // ==========================================================
    // SELECT Methods
    // ==========================================================

    /**
     * Truy vấn thông tin chi tiết của một sản phẩm theo ID.
     *
     * @param itemId ID sản phẩm cần tìm
     * @return {@link Item} chứa thông tin sản phẩm, hoặc {@code null} nếu không tìm thấy
     */
    public Item getById(int itemId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return getItemByRow(rs);
            }
        } catch (SQLException e) {
            logger.error("getById failed for itemId={}", itemId, e);
        }
        return null;
    }

    /**
     * Lấy danh sách tất cả sản phẩm của một người bán cụ thể.
     *
     * @param sellerId ID của người bán
     * @return Danh sách {@link Item}, trả về list rỗng nếu không có dữ liệu
     */
    public List<Item> getBySeller(int sellerId) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SELLER)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getBySeller failed for sellerId={}", sellerId, e);
        }
        return list;
    }

    /**
     * Lấy danh sách sản phẩm của người bán theo trạng thái cụ thể.
     *
     * <p>Ví dụ: lấy các sản phẩm đang {@code AVAILABLE} của người bán để hiển thị
     * danh sách chờ đấu giá.</p>
     *
     * @param sellerId ID của người bán
     * @param status   Trạng thái cần lọc
     * @return Danh sách {@link Item} thỏa điều kiện
     */
    public List<Item> getBySellerAndStatus(int sellerId, ItemStatus status) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SELLER_STATUS)) {
            ps.setInt(1, sellerId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getBySellerAndStatus failed for sellerId={}, status={}", sellerId, status, e);
        }
        return list;
    }

    /**
     * Lấy danh sách toàn bộ sản phẩm có trong hệ thống có phân trang.
     *
     * <p>Tránh load toàn bộ dữ liệu khi bảng lớn. Dùng {@link #countAll()}
     * để biết tổng số trang.</p>
     *
     * @param page     Trang hiện tại, bắt đầu từ 0
     * @param pageSize Số sản phẩm mỗi trang
     * @return Danh sách {@link Item} của trang đó
     */
    public List<Item> getAll(int page, int pageSize) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL_PAGED)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getAll (paged) failed for page={}, pageSize={}", page, pageSize, e);
        }
        return list;
    }

    /**
     * Lấy toàn bộ sản phẩm không phân trang (dùng nội bộ hoặc admin).
     *
     * @return Danh sách {@link Item}
     */
    public List<Item> getAllItem() {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(getItemByRow(rs));
        } catch (SQLException e) {
            logger.error("getAll failed", e);
        }
        return list;
    }

    /**
     * Đếm tổng số sản phẩm trong hệ thống (hỗ trợ phân trang phía client).
     *
     * @return Tổng số bản ghi trong bảng {@code items}
     */
    public int countAll() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COUNT_ALL);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("countAll failed", e);
        }
        return 0;
    }

    /**
     * Lấy danh sách sản phẩm thuộc đúng một danh mục cụ thể.
     *
     * @param category Loại danh mục cần lấy sản phẩm
     * @return Danh sách {@link Item} thuộc danh mục đó
     */
    public List<Item> getByCategory(ItemCategory category) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_CATEGORY)) {
            ps.setString(1, category.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getByCategory failed for categoryId={}", category, e);
        }
        return list;
    }


    /**
     * Lấy danh sách sản phẩm theo trạng thái.
     *
     * <p>Ví dụ: lấy tất cả sản phẩm đang {@code IN_AUCTION} để hiển thị
     * trang đấu giá đang diễn ra.</p>
     *
     * @param status Trạng thái cần lọc
     * @return Danh sách {@link Item} có trạng thái tương ứng
     */
    public List<Item> getByStatus(ItemStatus status) {
        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_STATUS)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getByStatus failed for status={}", status, e);
        }
        return list;
    }

    /**
     * Tìm kiếm sản phẩm theo tên (không phân biệt hoa thường).
     *
     * <p>Dùng {@code LIKE} với ký tự {@code %} ở hai đầu để tìm kiếm
     * tên chứa từ khóa bất kỳ vị trí.</p>
     *
     * @param keyword Từ khóa tìm kiếm
     * @return Danh sách {@link Item} có tên chứa từ khóa
     */
    public List<Item> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) return new ArrayList<>();

        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEARCH_BY_NAME)) {
            ps.setString(1, "%" + keyword.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("searchByName failed for keyword={}", keyword, e);
        }
        return list;
    }

    /**
     * Tìm kiếm sản phẩm theo tên và lọc thêm theo trạng thái.
     *
     * <p>Ví dụ: tìm sản phẩm tên chứa "iPhone" và đang ở trạng thái
     * {@code AVAILABLE}.</p>
     *
     * @param keyword Từ khóa tìm kiếm tên sản phẩm
     * @param status  Trạng thái cần lọc
     * @return Danh sách {@link Item} thỏa cả hai điều kiện
     */
    public List<Item> searchByNameAndStatus(String keyword, ItemStatus status) {
        if (keyword == null || keyword.isBlank()) return getByStatus(status);

        List<Item> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SEARCH_BY_NAME_AND_STATUS)) {
            ps.setString(1, "%" + keyword.trim() + "%");
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(getItemByRow(rs));
            }
        } catch (SQLException e) {
            logger.error("searchByNameAndStatus failed for keyword={}, status={}", keyword, status, e);
        }
        return list;
    }

    // ==========================================================
    // Private Helpers
    // ==========================================================

    /**
     * Kiểm tra tính hợp lệ sơ bộ của dữ liệu sản phẩm.
     *
     * @param name  Tên sản phẩm
     * @param price Giá khởi điểm
     * @return {@code true} nếu dữ liệu không hợp lệ
     */
    private boolean isInvalidItemData(String name, BigDecimal price) {
        return name == null || name.isBlank()
            || price == null
            || price.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Chuyển đổi một hàng dữ liệu từ {@link ResultSet} sang đúng subtype {@link Item}.
     *
     * <p>Luồng mới dùng {@link ItemFactory#createFromDb} để giữ nguyên {@code item_id}
     * và {@code created_at} trong DB. Nếu dùng factory tạo mới như trước, item sẽ nhận UUID mới
     * và khi tạo auction sẽ không map được về khóa ngoại {@code items.item_id}.</p>
     *
     * @param rs ResultSet đang trỏ tới hàng item hiện tại
     * @return item domain giữ đúng ID DB
     * @throws SQLException nếu có lỗi khi đọc tên cột hoặc dữ liệu
     */
    private Item getItemByRow(ResultSet rs) throws SQLException {

        Map<String,String> mapAttributeItem = itemAttributeDAO.getAttributeMapByItemId(rs.getInt("item_id"));
        return ItemFactory.createFromDb(ItemCategory.valueOf(rs.getString("category")),
            String.valueOf(rs.getInt("item_id")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            String.valueOf(rs.getInt("seller_id")),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("starting_price"),
            ItemStatus.valueOf(rs.getString("status")), mapAttributeItem
        );
    }
}