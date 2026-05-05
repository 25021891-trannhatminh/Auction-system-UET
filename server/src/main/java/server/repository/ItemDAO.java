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

    // ==========================================================
    // SQL Constants
    // ==========================================================

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

    private static final String SQL_CHECK_AUCTION_RELIANCE =
        "SELECT COUNT(*) FROM auctions WHERE item_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM items WHERE item_id = ?";

    private static final String SQL_SELECT_BY_ID =
        SQL_SELECT_BASE + " WHERE item_id = ?";

    private static final String SQL_SELECT_BY_SELLER =
        SQL_SELECT_BASE + " WHERE seller_id = ? ORDER BY created_at DESC";

    private static final String SQL_SELECT_ALL =
        SQL_SELECT_BASE + " ORDER BY created_at DESC";

    // ==========================================================
    // Public Methods
    // ==========================================================

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
            ps.setString(6, ItemStatus.AVAILABLE.name());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("addItem failed for sellerId={}", sellerId, e);
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
     * Xóa sản phẩm khỏi hệ thống nếu sản phẩm đó chưa từng tham gia phiên đấu giá nào.
     *
     * @param itemId ID của sản phẩm cần xóa
     * @return {@code true} nếu xóa thành công, {@code false} nếu có ràng buộc dữ liệu hoặc lỗi SQL
     */
    public boolean deleteItem(int itemId) {
        try (Connection conn = DBConnection.getConnection()) {
            // Kiểm tra xem sản phẩm đã có trong bảng đấu giá chưa
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
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("getBySeller failed for sellerId={}", sellerId, e);
        }
        return list;
    }

    /**
     * Lấy danh sách toàn bộ sản phẩm có trong hệ thống, sắp xếp theo thời gian tạo mới nhất.
     *
     * @return Danh sách {@link ItemDTO}
     */
    public List<ItemDTO> getAll() {
        List<ItemDTO> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("getAll failed", e);
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