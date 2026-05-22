package server.repository;

import server.common.entity.Auction;
import server.common.entity.Item;
import server.common.enums.AuctionStatus;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
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

    private static final String SQL_NEXT_AUCTION_ID = "SELECT nextval(seq_auctions)";

    private static final String SQL_INSERT = """
      INSERT INTO auctions (
          auction_id, item_id, seller_id, start_time, end_time,
          min_bid_increment, reserve_price,
          snipe_window_seconds, snipe_extension_seconds,
          current_price, current_winner_id, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

    private static final String SQL_LOCK_ITEM_FOR_CREATE = """
      SELECT seller_id, status, starting_price
      FROM items
      WHERE item_id = ?
      FOR UPDATE
      """;

    private static final String SQL_UPDATE_ITEM_STATUS =
        "UPDATE items SET status = ? WHERE item_id = ?";

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
     *
     * <p>Method này vẫn giữ signature cũ để các caller cũ không vỡ code, nhưng bên trong
     * sẽ dùng flow mới: insert auction + chuyển item sang IN_AUCTION trong cùng transaction,
     * rồi load lại auction có ID thật từ DB.</p>
     *
     * @param auction thông tin phiên đấu giá cần tạo; không được {@code null}
     * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
     */
    public boolean create(Auction auction) {
        return createAndLoad(auction) != null;
    }

    /**
     * Tạo auction và trả về entity đã được reload từ DB với {@code auction_id} thật.
     *
     * <p>Method này là cầu nối cho các caller cũ vẫn truyền vào {@link Auction} domain.
     * Dữ liệu sẽ được bóc ra rồi chuyển sang {@link #createAuction(Item, String, LocalDateTime,
     * LocalDateTime, BigDecimal, BigDecimal, int, int)} để đảm bảo chỉ có một flow persist chuẩn.</p>
     *
     * @param auction auction domain cần lưu
     * @return auction đã reload từ DB, hoặc {@code null} nếu dữ liệu không hợp lệ/lưu thất bại
     */
    public Auction createAndLoad(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            logger.warn("createAndLoad() – auction/item is null");
            return null;
        }
        return createAuction(
            auction.getItem(),
            auction.getSellerId(),
            auction.getStartTime(),
            auction.getEndTime(),
            auction.getMinBidIncrement(),
            auction.getReservePrice(),
            auction.getSnipeWindowSeconds(),
            auction.getSnipeExtensionSeconds()
        );
    }

    /**
     * Tạo auction từ item {@code AVAILABLE} trong một transaction duy nhất.
     *
     * <p>Transaction sẽ khóa row item bằng {@code SELECT ... FOR UPDATE}, kiểm tra seller/status,
     * tự sinh {@code auction_id}, insert auction và đổi item sang {@code IN_AUCTION}. Cách này tránh
     * trạng thái nửa vời như DB đã có auction nhưng item chưa bị khóa, hoặc AuctionManager giữ UUID tạm
     * khác với ID thật trong DB.</p>
     *
     * @param item item đã được duyệt và đang AVAILABLE
     * @param sellerId ID người bán dưới dạng chuỗi số trong DB
     * @param startTime thời điểm bắt đầu phiên
     * @param endTime thời điểm kết thúc phiên
     * @param minBidIncrement bước nhảy giá tối thiểu
     * @param reservePrice giá sàn; {@code null} sẽ được lưu là {@code BigDecimal.ZERO}
     * @param snipeWindowSeconds khoảng thời gian chống đặt giá sát giờ
     * @param snipeExtensionSeconds thời gian gia hạn nếu có bid trong snipe window
     * @return auction đã reload từ DB với ID thật, hoặc {@code null} nếu tạo thất bại
     */
    public Auction createAuction(Item item, String sellerId,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 BigDecimal minBidIncrement, BigDecimal reservePrice,
                                 int snipeWindowSeconds, int snipeExtensionSeconds) {
        if (item == null || sellerId == null || startTime == null || endTime == null) {
            logger.warn("createAuction() – invalid null input");
            return null;
        }

        int itemId;
        int sellerIdInt;
        try {
            itemId = Integer.parseInt(item.getId());
            sellerIdInt = Integer.parseInt(sellerId);
        } catch (NumberFormatException e) {
            logger.warn("createAuction() – itemId/sellerId must be database integer IDs. itemId={}, sellerId={}",
                item.getId(), sellerId);
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!endTime.isAfter(startTime) || !endTime.isAfter(now)) {
            logger.warn("createAuction() – invalid time range. start={}, end={}", startTime, endTime);
            return null;
        }

        AuctionStatus initialStatus = startTime.isAfter(now) ? AuctionStatus.OPEN : AuctionStatus.RUNNING;
        BigDecimal safeReservePrice = reservePrice == null ? BigDecimal.ZERO : reservePrice;
        BigDecimal safeMinIncrement = minBidIncrement == null ? BigDecimal.ZERO : minBidIncrement;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal startingPrice;
                try (PreparedStatement lockItemPs = conn.prepareStatement(SQL_LOCK_ITEM_FOR_CREATE)) {
                    lockItemPs.setInt(1, itemId);
                    try (ResultSet rs = lockItemPs.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            logger.warn("createAuction() – itemId={} not found", itemId);
                            return null;
                        }
                        int databaseSellerId = rs.getInt("seller_id");
                        if (databaseSellerId != sellerIdInt) {
                            conn.rollback();
                            logger.warn("createAuction() – seller mismatch. itemId={}, requestSeller={}, dbSeller={}",
                                itemId, sellerIdInt, databaseSellerId);
                            return null;
                        }
                        String itemStatus = rs.getString("status");
                        if (!"AVAILABLE".equalsIgnoreCase(itemStatus)) {
                            conn.rollback();
                            logger.warn("createAuction() – itemId={} is not AVAILABLE, currentStatus={}",
                                itemId, itemStatus);
                            return null;
                        }
                        startingPrice = rs.getBigDecimal("starting_price");
                    }
                }

                int auctionId = nextAuctionId(conn);
                try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                    ps.setInt(1, auctionId);
                    ps.setInt(2, itemId);
                    ps.setInt(3, sellerIdInt);
                    ps.setTimestamp(4, Timestamp.valueOf(startTime));
                    ps.setTimestamp(5, Timestamp.valueOf(endTime));
                    ps.setBigDecimal(6, safeMinIncrement);
                    ps.setBigDecimal(7, safeReservePrice);
                    ps.setShort(8, (short) snipeWindowSeconds);
                    ps.setShort(9, (short) snipeExtensionSeconds);
                    ps.setBigDecimal(10, startingPrice);
                    ps.setNull(11, Types.INTEGER);
                    ps.setString(12, initialStatus.name());
                    ps.executeUpdate();
                }

                try (PreparedStatement updateItemPs = conn.prepareStatement(SQL_UPDATE_ITEM_STATUS)) {
                    updateItemPs.setString(1, "IN_AUCTION");
                    updateItemPs.setInt(2, itemId);
                    updateItemPs.executeUpdate();
                }

                conn.commit();
                logger.info("createAuction() – auctionId={} created for itemId={} with status={}",
                    auctionId, itemId, initialStatus);
                return getById(auctionId);
            } catch (SQLException e) {
                conn.rollback();
                logger.error("createAuction() – DB transaction failed for itemId={}", itemId, e);
                return null;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("createAuction() – DB connection error for itemId={}", itemId, e);
            return null;
        }
    }

    /**
     * Lấy ID kế tiếp từ sequence {@code seq_auctions} ngay trong transaction hiện tại.
     *
     * <p>Project đang dùng sequence riêng thay vì generated keys tự động, nên cần lấy ID trước
     * rồi truyền trực tiếp vào câu INSERT để entity reload ra có cùng ID với DB.</p>
     *
     * @param conn connection đang nằm trong transaction tạo auction
     * @return auction_id kế tiếp
     * @throws SQLException nếu sequence không trả về giá trị hợp lệ
     */
    private int nextAuctionId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_NEXT_AUCTION_ID);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Cannot generate next auction id");
            }
            return rs.getInt(1);
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
     * <p>Sau khi dựng auction, method này load lại bid history từ {@code bid_transactions}
     * để AuctionManager/scheduler không mất ngữ cảnh người thắng và số lượng bid sau khi restart.</p>
     *
     * @param rs result set đã được định vị tại hàng cần đọc
     * @return {@link Auction} được điền đầy đủ dữ liệu chính và lịch sử bid
     * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
     */
    private Auction getAuctionByRow(ResultSet rs) throws SQLException {
        ItemDAO itemDAO = new ItemDAO();
        AccountDAO accountDAO = new AccountDAO();

        Timestamp lastBidTs = rs.getTimestamp("last_bid_time");// last_bid_time là Null khi chưa có bid

        int auctionId = rs.getInt("auction_id");
        Auction auction = new Auction(
            String.valueOf(auctionId),
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
            accountDAO.getUserById(rs.getInt("current_winner_id"))
        );
        auction.restoreBidHistory(new BidTransactionDAO().getBidHistory(auctionId));
        return auction;
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