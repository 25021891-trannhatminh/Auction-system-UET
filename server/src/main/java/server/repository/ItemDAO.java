package server.repository;

import server.common.enums.ItemStatus;
import server.common.model.ItemDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng {@code ITEMS}.
 *
 * <p>Chịu trách nhiệm các thao tác CRUD với dữ liệu sản phẩm.
 * Mọi lỗi {@link SQLException} được xử lý nội bộ và ghi log qua SLF4J.</p>
 */
public class ItemDAO {

    private static final Logger logger = LoggerFactory.getLogger(ItemDAO.class);

    private static final String SQL_SELECT_BASE = """
        SELECT item_id, seller_id, category_id, name, description,
               starting_price, status, created_at
        FROM items
        """;

    private static final String SQL_INSERT = """
        INSERT INTO items (seller_id, category_id, name, description, starting_price, status)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SQL_UPDATE = """
        UPDATE items
        SET category_id = ?, name = ?, description = ?, starting_price = ?
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
        SQL_SELECT_BASE + " WHERE category_id = ? ORDER BY created_at DESC";

    // Lấy items theo category cha + tất cả category con (chỉ 1 cấp)
    private static final String SQL_SELECT_BY_CATEGORY_INCLUDE_SUBS = """
        SELECT i.item_id, i.seller_id, i.category_id, i.name,
               i.description, i.starting_price, i.status, i.created_at
        FROM items i
        JOIN item_categories c ON i.category_id = c.category_id
        WHERE c.category_id = ? OR c.parent_id = ?
        ORDER BY i.created_at DESC
        """;

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
     * @param categoryId    ID của danh mục sản phẩm
     * @param name          Tên sản phẩm
     * @param description   Mô tả chi tiết sản phẩm
     * @param startingPrice Giá khởi điểm để đấu giá
     * @return {@code true} nếu thêm thành công, {@code false} nếu dữ liệu không hợp lệ hoặc lỗi DB
     */
    public boolean addItem(int sellerId, int categoryId, String name,
        String description, BigDecimal startingPrice) {

        if (isInvalidItemData(name, startingPrice)) return false;

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, sellerId);
            ps.setInt(2, categoryId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setBigDecimal(5, startingPrice);
            ps.setString(6, ItemStatus.PENDING_REVIEW.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("addItem failed for sellerId={}", sellerId, e);
            return false;
        }
    }

    /**
     * Lấy danh sách tất cả sản phẩm đang chờ admin kiểm duyệt.
     * Sắp xếp cũ nhất lên đầu để duyệt theo thứ tự FIFO.
     *
     * @return Danh sách {@link ItemDTO} có status {@code PENDING_REVIEW}
     */
    public List<ItemDTO> getPendingReviewItems() {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_PENDING_REVIEW);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));
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
     * @return {@link ItemDTO} chứa thông tin sản phẩm, hoặc {@code null} nếu không tìm thấy
     */
    public ItemDTO getById(int itemId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
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
     * @return Danh sách {@link ItemDTO}, trả về list rỗng nếu không có dữ liệu
     */
    public List<ItemDTO> getBySeller(int sellerId) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SELLER)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
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
     * @return Danh sách {@link ItemDTO} thỏa điều kiện
     */
    public List<ItemDTO> getBySellerAndStatus(int sellerId, ItemStatus status) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_SELLER_STATUS)) {
            ps.setInt(1, sellerId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
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
     * @return Danh sách {@link ItemDTO} của trang đó
     */
    public List<ItemDTO> getAll(int page, int pageSize) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL_PAGED)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getAll (paged) failed for page={}, pageSize={}", page, pageSize, e);
        }
        return list;
    }

    /**
     * Lấy toàn bộ sản phẩm không phân trang (dùng nội bộ hoặc admin).
     *
     * @return Danh sách {@link ItemDTO}
     */
    public List<ItemDTO> getAll() {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
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
     * @param categoryId ID danh mục cần lấy sản phẩm
     * @return Danh sách {@link ItemDTO} thuộc danh mục đó
     */
    public List<ItemDTO> getByCategory(int categoryId) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_CATEGORY)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getByCategory failed for categoryId={}", categoryId, e);
        }
        return list;
    }

    /**
     * Lấy danh sách sản phẩm thuộc danh mục cha lẫn tất cả danh mục con (1 cấp).
     *
     * <p>Ví dụ: click vào "Điện tử" sẽ thấy cả sản phẩm thuộc "Điện thoại"
     * và "Laptop" mà không cần query từng danh mục con.</p>
     *
     * @param categoryId ID danh mục cha
     * @return Danh sách {@link ItemDTO} của danh mục cha và các danh mục con
     */
    public List<ItemDTO> getByCategoryIncludeSubs(int categoryId) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_CATEGORY_INCLUDE_SUBS)) {
            ps.setInt(1, categoryId);
            ps.setInt(2, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getByCategoryIncludeSubs failed for categoryId={}", categoryId, e);
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
     * @return Danh sách {@link ItemDTO} có trạng thái tương ứng
     */
    public List<ItemDTO> getByStatus(ItemStatus status) {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_STATUS)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
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
     * @return Danh sách {@link ItemDTO} có tên chứa từ khóa
     */
    public List<ItemDTO> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) return new ArrayList<>();

        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SEARCH_BY_NAME)) {
            ps.setString(1, "%" + keyword.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
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
     * @return Danh sách {@link ItemDTO} thỏa cả hai điều kiện
     */
    public List<ItemDTO> searchByNameAndStatus(String keyword, ItemStatus status) {
        if (keyword == null || keyword.isBlank()) return getByStatus(status);

        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SEARCH_BY_NAME_AND_STATUS)) {
            ps.setString(1, "%" + keyword.trim() + "%");
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
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
     * Chuyển đổi một hàng dữ liệu từ ResultSet sang đối tượng DTO.
     *
     * @param rs ResultSet đang trỏ tới hàng hiện tại
     * @return Đối tượng {@link ItemDTO}
     * @throws SQLException Nếu có lỗi khi đọc tên cột hoặc dữ liệu
     */
    private ItemDTO mapRow(ResultSet rs) throws SQLException {
        return new ItemDTO(
            rs.getInt("item_id"),
            rs.getInt("seller_id"),
            rs.getInt("category_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBigDecimal("starting_price"),
            ItemStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at")
        );
    }
}