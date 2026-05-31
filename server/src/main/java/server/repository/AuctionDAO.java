package server.repository;

import server.common.enums.AuctionStatus;
import server.common.model.AuctionDTO;
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
 * AuctionDTO auction = dao.getById(42);
 * if (auction != null) {
 * dao.updateStatus(42, AuctionStatus.FINISHED);
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
    // SQL tách từ ClientHandler - SRP
    // ============================================================

    private static final String SQL_IS_OWNED_BY_SELLER =
        "SELECT seller_id FROM auctions WHERE auction_id = ?";

    private static final String SQL_ADMIN_AUCTION_LIST = """
        SELECT a.auction_id, a.item_id, COALESCE(i.name, '') AS item_name,
               a.seller_id, COALESCE(seller.username, '') AS seller_username,
               a.current_price, COALESCE(bid_counts.bid_count, 0) AS bid_count,
               a.status, a.start_time, a.end_time,
               COALESCE(winner.username, '') AS winner_username
        FROM auctions a
        LEFT JOIN items i ON i.item_id = a.item_id
        LEFT JOIN accounts seller ON seller.user_id = a.seller_id
        LEFT JOIN accounts winner ON winner.user_id = a.current_winner_id
        LEFT JOIN (
            SELECT auction_id, COUNT(*) AS bid_count
            FROM bid_transactions
            GROUP BY auction_id
        ) bid_counts ON bid_counts.auction_id = a.auction_id
        ORDER BY a.created_at DESC, a.auction_id DESC
        """;

    private static final String SQL_USER_AUCTION_LIST = """
        SELECT a.auction_id,
               a.item_id,
               COALESCE(i.name, '') AS item_name,
               COALESCE(i.category, '') AS category_name,
               COALESCE(i.description, '') AS description,
               a.current_price,
               a.min_bid_increment,
               a.reserve_price,
               COALESCE(bid_counts.bid_count, 0) AS bid_count,
               CASE
                   WHEN a.status IN ('OPEN', 'RUNNING') AND NOW() >= a.end_time THEN 'FINISHED'
                   WHEN a.status = 'OPEN' AND NOW() >= a.start_time THEN 'RUNNING'
                   ELSE a.status
               END AS display_status,
               a.start_time,
               a.end_time,
               GREATEST(TIMESTAMPDIFF(SECOND, NOW(), a.end_time), 0) AS seconds_left,
               COALESCE(seller.username, '') AS seller_username,
               COALESCE(winner.username, '') AS winner_username,
               COALESCE(imgs.image_urls, '') AS image_urls,
               COALESCE(attrs.attribute_lines, '') AS attribute_lines,
               a.snipe_window_seconds,
               a.snipe_extension_seconds
        FROM auctions a
        LEFT JOIN items i ON i.item_id = a.item_id
        LEFT JOIN accounts seller ON seller.user_id = a.seller_id
        LEFT JOIN accounts winner ON winner.user_id = a.current_winner_id
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
        ) imgs ON imgs.item_id = a.item_id
        LEFT JOIN (
            SELECT item_id,
                   GROUP_CONCAT(CONCAT(attr_key, ': ', attr_value) ORDER BY attr_id ASC
                                SEPARATOR '\\n') AS attribute_lines
            FROM item_attributes
            GROUP BY item_id
        ) attrs ON attrs.item_id = a.item_id
        WHERE a.status IN ('OPEN', 'RUNNING')
          AND a.end_time > NOW()
        ORDER BY a.end_time ASC, a.auction_id DESC
        """;

    // ============================================================
    // Public methods
    // ============================================================

    /**
     * Tạo một phiên đấu giá mới trong cơ sở dữ liệu.
     *
     * <p>Method này nhận dữ liệu thô hoàn chỉnh từ Service Layer truyền xuống qua DTO,
     * thực hiện bóc tách trường thông tin và chuyển tiếp xuống transaction lưu trữ.
     * Toàn bộ việc kiểm tra tính hợp lệ của thời gian hoặc trạng thái ban đầu đều do Service xử lý.</p>
     *
     * @param auction thông tin phiên đấu giá dưới dạng DTO cần tạo; không được {@code null}
     * @return {@code true} nếu insert thành công, {@code false} nếu thất bại
     */
    public boolean create(AuctionDTO auction) {
        if (auction == null) {
            return false;
        }

        // Tầng DAO chấp nhận hoàn toàn giá trị trạng thái do Service Layer chỉ định truyền vào DTO,
        // nếu không truyền thì để mặc định an toàn cho DB chứ không tự so sánh thời gian hệ thống.
        AuctionStatus initialStatus = auction.getStatus() != null ? auction.getStatus() : AuctionStatus.OPEN;

        AuctionDTO created = createAuction(
            auction.getItemId(),
            auction.getSellerId(),
            auction.getStartTime() != null ? auction.getStartTime().toLocalDateTime() : null,
            auction.getEndTime() != null ? auction.getEndTime().toLocalDateTime() : null,
            auction.getMinBidIncrement(),
            auction.getReservePrice(),
            auction.getSnipeWindowSeconds(),
            auction.getSnipeExtensionSeconds(),
            initialStatus
        );
        return created != null;
    }

    /**
     * Tạo auction từ item {@code AVAILABLE} trong một transaction duy nhất.
     *
     * <p>Transaction sẽ khóa row item bằng {@code SELECT ... FOR UPDATE}, kiểm tra seller/status,
     * tự sinh {@code auction_id}, insert auction và đổi item sang {@code IN_AUCTION}. Cách này tránh
     * trạng thái nửa vời như DB đã có auction nhưng item chưa bị khóa, hoặc AuctionManager giữ UUID tạm
     * khác với ID thật trong DB.</p>
     *
     * @param itemId ID của sản phẩm đấu giá
     * @param sellerId ID người bán dưới dạng số nguyên
     * @param startTime thời điểm bắt đầu phiên
     * @param endTime thời điểm kết thúc phiên
     * @param minBidIncrement bước nhảy giá tối thiểu
     * @param reservePrice giá sàn; {@code null} sẽ được lưu là {@code BigDecimal.ZERO}
     * @param snipeWindowSeconds khoảng thời gian chống đặt giá sát giờ
     * @param snipeExtensionSeconds thời gian gia hạn nếu có bid trong snipe window
     * @return AuctionDTO đã reload từ DB với ID thật, hoặc {@code null} nếu tạo thất bại
     */
    public AuctionDTO createAuction(int itemId, int sellerId,
        LocalDateTime startTime, LocalDateTime endTime,
        BigDecimal minBidIncrement, BigDecimal reservePrice,
        int snipeWindowSeconds, int snipeExtensionSeconds,
        AuctionStatus initialStatus) {
        // Không còn validate thời gian hay tự suy luận trạng thái.

        BigDecimal safeReserve = reservePrice != null ? reservePrice : BigDecimal.ZERO;
        BigDecimal safeMinIncr = minBidIncrement != null ? minBidIncrement : BigDecimal.ZERO;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock item chỉ để lấy starting_price, không kiểm tra seller/status
                BigDecimal startingPrice;
                try (PreparedStatement lockPs = conn.prepareStatement(SQL_LOCK_ITEM_FOR_CREATE)) {
                    lockPs.setInt(1, itemId);
                    try (ResultSet rs = lockPs.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            logger.warn("createAuction() – itemId={} not found", itemId);
                            return null;
                        }
                        startingPrice = rs.getBigDecimal("starting_price");
                    }
                }

                int auctionId = nextAuctionId(conn);
                try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
                    ps.setInt(1, auctionId);
                    ps.setInt(2, itemId);
                    ps.setInt(3, sellerId);
                    ps.setTimestamp(4, Timestamp.valueOf(startTime));
                    ps.setTimestamp(5, Timestamp.valueOf(endTime));
                    ps.setBigDecimal(6, safeMinIncr);
                    ps.setBigDecimal(7, safeReserve);
                    ps.setShort(8, (short) snipeWindowSeconds);
                    ps.setShort(9, (short) snipeExtensionSeconds);
                    ps.setBigDecimal(10, startingPrice);
                    ps.setNull(11, Types.INTEGER);
                    ps.setString(12, initialStatus.name()); // nhận từ Service
                    ps.executeUpdate();
                }

                try (PreparedStatement updatePs = conn.prepareStatement(SQL_UPDATE_ITEM_STATUS)) {
                    updatePs.setString(1, "IN_AUCTION");
                    updatePs.setInt(2, itemId);
                    updatePs.executeUpdate();
                }

                conn.commit();
                logger.info("createAuction() – auctionId={} created for itemId={} with status={}",
                    auctionId, itemId, initialStatus);
                return getById(auctionId);
            } catch (SQLException e) {
                conn.rollback();
                logger.error("createAuction() – transaction failed for itemId={}", itemId, e);
                return null;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.error("createAuction() – connection error for itemId={}", itemId, e);
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
     * @return {@link AuctionDTO} tương ứng, hoặc {@code null} nếu không tìm thấy
     */
    public AuctionDTO getById(int auctionId) {
        logger.debug("getById() – auctionId={}", auctionId);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AuctionDTO auction = getAuctionByRow(rs);
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
     * @return danh sách {@link AuctionDTO}; trả về list rỗng nếu có lỗi
     */
    public List<AuctionDTO> getAllAuction() {
        logger.debug("getAll() – Fetching all auctions");
        List<AuctionDTO> results = new ArrayList<>();

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
     * @return danh sách {@link AuctionDTO} khớp trạng thái; list rỗng nếu có lỗi
     */
    public List<AuctionDTO> getByStatus(AuctionStatus status) {
        logger.debug("getByStatus() – status={}", status);
        List<AuctionDTO> results = new ArrayList<>();

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
     * @return {@code true} if deleted successfully; {@code false} if bids already exist or on error
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
     * @return {@link AuctionDTO} tương ứng, hoặc {@code null}
     * nếu không tìm thấy
     */
    public AuctionDTO getByItemId(int itemId) {
        logger.debug("getByItemId() – itemId={}", itemId);

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ITEM_ID)) {

            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AuctionDTO auction = getAuctionByRow(rs);
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
     * Map một hàng {@link ResultSet} thành {@link AuctionDTO}.
     *
     * @param rs result set đã được định vị tại hàng cần đọc
     * @return {@link AuctionDTO} được điền đầy đủ dữ liệu chính sạch từ DB
     * @throws SQLException nếu tên cột không tồn tại hoặc lỗi đọc dữ liệu
     */
    private AuctionDTO getAuctionByRow(ResultSet rs) throws SQLException {
        Integer winnerId = rs.getInt("current_winner_id");
        if (rs.wasNull()) {
            winnerId = null;
        }

        return new AuctionDTO(
            rs.getInt("auction_id"),
            rs.getInt("item_id"),
            rs.getInt("seller_id"),
            rs.getTimestamp("start_time"),
            rs.getTimestamp("end_time"),
            rs.getTimestamp("last_bid_time"),
            rs.getBigDecimal("min_bid_increment"),
            rs.getBigDecimal("reserve_price"),
            rs.getShort("snipe_window_seconds"),
            rs.getShort("snipe_extension_seconds"),
            rs.getBigDecimal("current_price"),
            winnerId,
            AuctionStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at")
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

    // ============================================================
    // Methods tách từ ClientHandler - SRP
    // ============================================================

    /**
     * Kiểm tra một auction có thuộc về seller cho trước không.
     * Tách từ ClientHandler.isAuctionOwnedByUser().
     *
     * @param auctionId ID phiên đấu giá
     * @param sellerId  ID người bán cần kiểm tra
     * @return {@code true} nếu auction thuộc về seller đó
     */
    public boolean isOwnedBySeller(int auctionId, int sellerId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_IS_OWNED_BY_SELLER)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("seller_id") == sellerId;
            }
        } catch (SQLException e) {
            logger.error("isOwnedBySeller() failed for auctionId={}", auctionId, e);
            return false;
        }
    }

    /**
     * Lấy danh sách auction đầy đủ cho Admin panel.
     * Tách từ ClientHandler.sendAdminAuctions().
     *
     * @return danh sách {@link AdminAuctionRow}
     */
    public List<AdminAuctionRow> getAdminAuctionRows() {
        List<AdminAuctionRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_ADMIN_AUCTION_LIST);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new AdminAuctionRow(
                    rs.getInt("auction_id"),
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getInt("seller_id"),
                    rs.getString("seller_username"),
                    rs.getBigDecimal("current_price"),
                    rs.getInt("bid_count"),
                    rs.getString("status"),
                    rs.getTimestamp("start_time"),
                    rs.getTimestamp("end_time"),
                    rs.getString("winner_username")
                ));
            }
        } catch (SQLException e) {
            logger.error("getAdminAuctionRows() failed", e);
        }
        return rows;
    }

    /**
     * Lấy danh sách auction đang mở dành cho User dashboard.
     * Tách từ ClientHandler.sendUserAuctions().
     *
     * @return danh sách {@link UserAuctionRow}
     */
    public List<UserAuctionRow> getUserAuctionRows() {
        List<UserAuctionRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_USER_AUCTION_LIST);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new UserAuctionRow(
                    rs.getInt("auction_id"),
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getString("category_name"),
                    rs.getString("description"),
                    rs.getBigDecimal("current_price"),
                    rs.getBigDecimal("min_bid_increment"),
                    rs.getBigDecimal("reserve_price"),
                    rs.getInt("bid_count"),
                    rs.getString("display_status"),
                    rs.getTimestamp("start_time"),
                    rs.getTimestamp("end_time"),
                    rs.getLong("seconds_left"),
                    rs.getString("seller_username"),
                    rs.getString("winner_username"),
                    rs.getString("image_urls"),
                    rs.getString("attribute_lines"),
                    rs.getInt("snipe_window_seconds"),
                    rs.getInt("snipe_extension_seconds")
                ));
            }
        } catch (SQLException e) {
            logger.error("getUserAuctionRows() failed", e);
        }
        return rows;
    }

    /** Dữ liệu một auction cho Admin panel. */
    public record AdminAuctionRow(
        int auctionId, int itemId, String itemName,
        int sellerId, String sellerUsername,
        java.math.BigDecimal currentPrice, int bidCount,
        String status, java.sql.Timestamp startTime, java.sql.Timestamp endTime,
        String winnerUsername
    ) {}

    /** Dữ liệu một auction đang mở cho User dashboard. */
    public record UserAuctionRow(
        int auctionId, int itemId, String itemName, String categoryName,
        String description, java.math.BigDecimal currentPrice,
        java.math.BigDecimal minBidIncrement, java.math.BigDecimal reservePrice,
        int bidCount, String displayStatus,
        java.sql.Timestamp startTime, java.sql.Timestamp endTime, long secondsLeft,
        String sellerUsername, String winnerUsername,
        String imageUrls, String attributeLines,
        int snipeWindowSeconds, int snipeExtensionSeconds
    ) {}
}