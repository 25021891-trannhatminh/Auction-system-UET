package server.repository;

import server.common.entity.Auction;
import server.common.enums.AuctionStatus;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) cho bảng {@code AUCTIONS}.
 *
 * <p>Cung cấp các thao tác CRUD và truy vấn phiên đấu giá. Mọi lỗi
 * {@link SQLException} đều được bắt, ghi log ở mức {@code SEVERE}, và
 * phương thức trả về giá trị mặc định an toàn thay vì ném ngoại lệ ra ngoài.</p>
 *
 * <p>Ví dụ sử dụng:
 * <pre>{@code
 * AuctionDAO dao = new AuctionDAO();
 * Auction auction = dao.getById(42);
 * if (auction != null) {
 *     dao.updateStatus(42, AuctionStatus.FINISHED);
 * }
 * }</pre>
 * </p>
 */
public class AuctionDAO {

    private static final Logger logger = LoggerFactory.getLogger(AuctionDAO.class);

    // ============================================================
    // SQL constants
    // ============================================================

    private static final String SQL_INSERT = """
      INSERT INTO auctions (
          item_id, seller_id, start_time, end_time,
          min_bid_increment, reserve_price,
          snipe_window_seconds, snipe_extension_seconds,
          current_price, current_winner_id, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

    private static final String SQL_SELECT_BY_ID =
        "SELECT * FROM auctions WHERE auction_id = ?";

    private static final String SQL_SELECT_ALL =
        "SELECT * FROM auctions ORDER BY created_at DESC";

    private static final String SQL_SELECT_BY_STATUS =
        "SELECT * FROM auctions WHERE status = ?";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE auctions SET status = ? WHERE auction_id = ?";

    private static final String SQL_UPDATE_PRICE = """
      UPDATE auctions
      SET current_price = ?, current_winner_id = ?, last_bid_time = NOW()
      WHERE auction_id = ?
      """;

    private static final String SQL_FINISH = """
      UPDATE auctions
      SET status = ?, current_winner_id = ?
      WHERE auction_id = ?
      """;

    private static final String SQL_COUNT_BIDS =
        "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";

    private static final String SQL_DELETE =
        "DELETE FROM auctions WHERE auction_id = ?";

    private static final String SQL_CHECK_ITEM_AVAILABLE =
        "SELECT status FROM items WHERE item_id = ? AND status = 'AVAILABLE'";

    private static final String SQL_SELECT_BY_ITEM_ID =
        "SELECT * FROM auctions WHERE item_id = ?";

    // ============================================================
    // Public methods
    // ============================================================

    /**
     * Tạo một phiên đấu giá mới trong cơ sở dữ liệu.
     * AuctionManager gọi hàm này khi tạo 1 Auction mới
     *
     * @param auction thông tin phiên đấu giá cần tạo; không được {@code null}
     * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
     */
    public boolean create(Auction auction) {
        int itemId = Integer.parseInt(auction.getItem().getId());

        if (!isItemAvailable(itemId)) {
            logger.warn("create() – itemId={} is not AVAILABLE", itemId);
            return false;
        }

        logger.debug("create() – itemId={}, sellerId={}, status={}",
            itemId, auction.getSellerId(), auction.getStatus());

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {

            ps.setInt(1, itemId);
            ps.setInt(2, Integer.parseInt(auction.getSellerId()));
            ps.setTimestamp(3, Timestamp.valueOf(auction.getStartTime()));
            ps.setTimestamp(4, Timestamp.valueOf(auction.getEndTime()));
            ps.setBigDecimal(5, auction.getMinBidIncrement());
            ps.setBigDecimal(6, auction.getReservePrice());
            ps.setShort(7, (short) auction.getSnipeWindowSeconds());
            ps.setShort(8, (short) auction.getSnipeExtensionSeconds());
            ps.setBigDecimal(9, auction.getCurrentPrice());
            setNullableInt(ps, 10, auction.getCurrentLeader() != null ?
                                                Integer.parseInt(auction.getCurrentLeader().getId()) : null
            );
            ps.setString(11, auction.getStatus().name());

            boolean created = ps.executeUpdate() > 0;
            if (created) {
                logger.info("create() – Auction created successfully for itemId={}", auction.getId());
            } else {
                logger.warn("create() – No rows affected for itemId={}", auction.getId());
            }
            return created;

        } catch (SQLException e) {
            logger.error("create() – DB error for itemId={}", auction.getId(), e);
            return false;
        }
    }

    /**
     * Kiểm tra xem một sản phẩm có đang ở trạng thái sẵn sàng để bắt đầu đấu giá hay không.
     * * <p>Một sản phẩm được coi là "Available" khi nó tồn tại trong hệ thống và có
     * {@code status = 'AVAILABLE'}. Chỉ những sản phẩm ở trạng thái này mới có thể
     * được gán vào một phiên đấu giá (Auction) mới.</p>
     *
     * @param itemId ID của sản phẩm cần kiểm tra.
     * @return {@code true} nếu sản phẩm tồn tại và sẵn sàng cho đấu giá;
     * {@code false} nếu sản phẩm không tồn tại, đang trong phiên đấu giá khác,
     * hoặc đã bị xóa/đang chờ duyệt.
     */
    private boolean isItemAvailable(int itemId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_CHECK_ITEM_AVAILABLE)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // có row → AVAILABLE
            }
        } catch (SQLException e) {
            logger.error("isItemAvailable() – DB error for itemId={}", itemId, e);
            return false;
        }
    }
    /**
     * Lấy thông tin phiên đấu giá theo ID.
     *
     * @param auctionId khóa chính của phiên đấu giá
     * @return {@link Auction} tương ứng, hoặc {@code null} nếu không tìm thấy
     */
    public Auction getById(int auctionId) {
        logger.debug("getById() – auctionId={}", auctionId);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Auction auction = getAuctionByRow(rs);
                    logger.debug("getById() – Found auction auctionId={}", auctionId);
                    return auction;
                }
            }

            logger.debug("getById() – No auction found for auctionId={}", auctionId);
            return null;

        } catch (SQLException e) {
            logger.error("getById() – DB error for auctionId={}", auctionId, e);
            return null;
        }
    }

    /**
     * Lấy toàn bộ danh sách phiên đấu giá, sắp xếp mới nhất trước.
     *
     * @return danh sách {@link Auction}; trả về list rỗng nếu có lỗi
     */
    public List<Auction> getAllAuction() {
        logger.debug("getAll() – Fetching all auctions");
        List<Auction> results = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                results.add(getAuctionByRow(rs));
            }

            logger.debug("getAll() – Fetched {} auction(s)", results.size());

        } catch (SQLException e) {
            logger.error("getAll() – DB error while fetching all auctions", e);
        }

        return results;
    }

    /**
     * Lấy danh sách phiên đấu giá theo trạng thái.
     *
     * @param status trạng thái cần lọc; không được {@code null}
     * @return danh sách {@link Auction} khớp trạng thái; list rỗng nếu có lỗi
     */
    public List<Auction> getByStatus(AuctionStatus status) {
        logger.debug("getByStatus() – status={}", status);
        List<Auction> results = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_STATUS)) {

            ps.setString(1, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(getAuctionByRow(rs));
                }
            }

            logger.debug("getByStatus() – Found {} auction(s) with status={}", results.size(), status);

        } catch (SQLException e) {
            logger.error("getByStatus() – DB error for status={}", status, e);
        }

        return results;
    }

    /**
     * Cập nhật trạng thái của một phiên đấu giá.
     *
     * @param auctionId id phiên đấu giá cần cập nhật
     * @param status    trạng thái mới; không được {@code null}
     * @return {@code true} nếu cập nhật thành công
     */
    public boolean updateStatus(int auctionId, AuctionStatus status) {
        logger.debug("updateStatus() – auctionId={}, newStatus={}", auctionId, status);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, status.name());
            ps.setInt(2, auctionId);

            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logger.info("updateStatus() – auctionId={} updated to status={}", auctionId, status);
            } else {
                logger.warn("updateStatus() – No rows affected for auctionId={}", auctionId);
            }
            return updated;

        } catch (SQLException e) {
            logger.error("updateStatus() – DB error for auctionId={}", auctionId, e);
            return false;
        }
    }

    /**
     * Cập nhật giá hiện tại và người dẫn đầu của phiên đấu giá.
     *
     * @param auctionId id phiên đấu giá
     * @param winnerId  id người đặt giá cao nhất hiện tại
     * @param newPrice  giá mới; không được {@code null}
     * @return {@code true} nếu cập nhật thành công
     */
    public boolean updateCurrentPrice(int auctionId, int winnerId, BigDecimal newPrice) {
        logger.debug("updateCurrentPrice() – auctionId={}, winnerId={}, newPrice={}",
            auctionId, winnerId, newPrice);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_PRICE)) {

            ps.setBigDecimal(1, newPrice);
            ps.setInt(2, winnerId);
            ps.setInt(3, auctionId);

            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                logger.info("updateCurrentPrice() – auctionId={} price updated to {} by winnerId={}",
                    auctionId, newPrice, winnerId);
            } else {
                logger.warn("updateCurrentPrice() – No rows affected for auctionId={}", auctionId);
            }
            return updated;

        } catch (SQLException e) {
            logger.error("updateCurrentPrice() – DB error for auctionId={}", auctionId, e);
            return false;
        }
    }

    /**
     * Đánh dấu phiên đấu giá là kết thúc ({@code FINISHED}).
     *
     * @param auctionId id phiên đấu giá
     * @param winnerId  id người thắng cuộc; {@code null} nếu không có ai thắng
     * @return {@code true} nếu cập nhật thành công
     */
    public boolean finishAuction(int auctionId, Integer winnerId) {
        logger.debug("finishAuction() – auctionId={}, winnerId={}", auctionId, winnerId);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_FINISH)) {

            ps.setString(1, AuctionStatus.FINISHED.name());
            setNullableInt(ps, 2, winnerId);
            ps.setInt(3, auctionId);

            boolean finished = ps.executeUpdate() > 0;
            if (finished) {
                logger.info("finishAuction() – auctionId={} finished; winnerId={}", auctionId, winnerId);
            } else {
                logger.warn("finishAuction() – No rows affected for auctionId={}", auctionId);
            }
            return finished;

        } catch (SQLException e) {
            logger.error("finishAuction() – DB error for auctionId={}", auctionId, e);
            return false;
        }
    }

    /**
     * Xóa phiên đấu giá nếu chưa có lượt đặt giá nào.
     *
     * <p>Kiểm tra bảng {@code bids} trước khi xóa. Nếu đã có bid thì từ chối
     * xóa và trả về {@code false} để bảo toàn lịch sử.</p>
     *
     * @param auctionId id phiên đấu giá cần xóa
     * @return {@code true} nếu xóa thành công; {@code false} nếu đã có bid hoặc lỗi
     */
    public boolean delete(int auctionId) {
        logger.debug("delete() – auctionId={}", auctionId);

        try (Connection conn = DBConnection.getConnection()) {

            int bidCount = countBids(conn, auctionId);
            if (bidCount > 0) {
                logger.warn("delete() – Refused: auctionId={} already has {} bid(s)", auctionId, bidCount);
                return false;
            }

            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
                ps.setInt(1, auctionId);
                boolean deleted = ps.executeUpdate() > 0;
                if (deleted) {
                    logger.info("delete() – auctionId={} deleted successfully", auctionId);
                } else {
                    logger.warn("delete() – No rows affected for auctionId={}", auctionId);
                }
                return deleted;
            }

        } catch (SQLException e) {
            logger.error("delete() – DB error for auctionId={}", auctionId, e);
            return false;
        }
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Đếm số bid hiện có của một phiên đấu giá trong cùng {@link Connection}.
     *
     * @param conn      connection đang dùng
     * @param auctionId id phiên đấu giá
     * @return số lượng bid; {@code 0} nếu lỗi query
     * @throws SQLException nếu có lỗi kết nối DB
     */
    private int countBids(Connection conn, int auctionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_COUNT_BIDS)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Lấy thông tin phiên đấu giá theo ID của sản phẩm.
     *
     * @param itemId ID của sản phẩm
     * @return {@link Auction} tương ứng, hoặc {@code null}
     * nếu không tìm thấy
     */
    public Auction getByItemId(int itemId) {
        logger.debug("getByItemId() – itemId={}", itemId);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ITEM_ID)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Auction auction = getAuctionByRow(rs);
                    logger.debug("getByItemId() – Found auction for itemId={}", itemId);
                    return auction;
                }
            }
            logger.debug("getByItemId() – No auction found for itemId={}", itemId);
            return null;
        } catch (SQLException e) {
            logger.error("getByItemId() – DB error for itemId={}", itemId, e);
            return null;
        }
    }

    /**
     * Map một hàng {@link ResultSet} thành {@link Auction}.
     *
     * @param rs result set đã được định vị tại hàng cần đọc
     * @return {@link Auction} được điền đầy đủ
     * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
     */
    private Auction getAuctionByRow(ResultSet rs) throws SQLException {
        ItemDAO itemDAO = new ItemDAO();
        AccountDAO accountDAO = new AccountDAO();

        Timestamp lastBidTs = rs.getTimestamp("last_bid_time");// last_bid_time là Null khi chưa có bid

        return new Auction(
            String.valueOf(rs.getInt("auction_id")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            itemDAO.getById(rs.getInt("item_id")),
            String.valueOf(rs.getInt("seller_id")),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            lastBidTs != null ? lastBidTs.toLocalDateTime() : null,
            rs.getBigDecimal("current_price"),
            rs.getBigDecimal("min_bid_increment"),
            rs.getBigDecimal("reserve_price"),
            (int)rs.getShort("snipe_window_seconds"),
            (int)rs.getShort("snipe_extension_seconds"),
            AuctionStatus.valueOf(rs.getString("status")),
            accountDAO.getById(rs.getInt("current_winner_id"))
        );
    }

    /**
     * Gán giá trị {@link Integer} nullable vào {@link PreparedStatement}.
     *
     * <p>Nếu {@code value} là {@code null} thì bind {@code NULL} kiểu {@code INTEGER},
     * ngược lại bind giá trị thực.</p>
     *
     * @param ps           prepared statement
     * @param paramIndex   vị trí tham số (1-based)
     * @param value        giá trị cần bind; có thể {@code null}
     * @throws SQLException nếu bind thất bại
     */
    private void setNullableInt(PreparedStatement ps, int paramIndex, Integer value)
        throws SQLException {
        if (value == null) {
            ps.setNull(paramIndex, Types.INTEGER);
        } else {
            ps.setInt(paramIndex, value);
        }
    }
}