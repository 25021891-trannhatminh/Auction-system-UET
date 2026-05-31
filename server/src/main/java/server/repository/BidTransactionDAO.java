package server.repository;

import server.common.enums.BidStatus;
import server.common.model.BidHistoryDTO;
import server.common.model.BidPointDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * BidTransactionDAO - Chỉ làm nhiệm vụ hạ tầng dữ liệu thuần túy (Persistence Only).
 * Đã được viết lại để tương thích hoàn toàn với cấu trúc String ID của BidTransaction.java
 * và cơ chế quản lý Transaction tập trung của BidTransactionService.
 */
public class BidTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(BidTransactionDAO.class);

    private static final DateTimeFormatter BID_POINT_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record AuctionLockInfo(String status, LocalDateTime endTime) {}

    private static final String SQL_LOCK_AUCTION =
        "SELECT current_price, status, start_time, end_time FROM auctions WHERE auction_id = ? FOR UPDATE";

    // Hàm cập nhật trạng thái tường minh do Service Layer trực tiếp ra lệnh điều phối
    private static final String SQL_UPDATE_AUCTION_STATUS =
        "UPDATE auctions SET status = ? WHERE auction_id = ?";

    private static final String SQL_UPDATE_AUCTION =
        "UPDATE auctions SET current_price = ?, current_winner_id = ?, end_time = ?, last_bid_time = NOW() WHERE auction_id = ?";

    private static final String SQL_OUTBID_PREVIOUS =
        "UPDATE bid_transactions SET status = ? WHERE auction_id = ? AND status = ?";

    // Đã loại bỏ trường tự tăng bid_id ra khỏi câu lệnh INSERT để DB (MySQL/PostgreSQL) tự sinh tự tăng dưới dạng int
    private static final String SQL_INSERT_BID =
        "INSERT INTO bid_transactions (auction_id, bidder_id, amount, is_auto_bid, status, bid_time) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_HISTORY =
        "SELECT bid_id, auction_id, bidder_id, amount, is_auto_bid, status, bid_time FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time ASC";

    private static final String SQL_SELECT_HISTORY_WITH_BIDDERS = """
        SELECT bt.bid_id, bt.auction_id, bt.bidder_id,
               COALESCE(NULLIF(acc.username, ''), CONCAT('User #', bt.bidder_id)) AS bidder_name,
               bt.amount, bt.is_auto_bid, bt.status, bt.bid_time
        FROM bid_transactions bt
        LEFT JOIN accounts acc ON acc.user_id = bt.bidder_id
        WHERE bt.auction_id = ?
        ORDER BY bt.bid_time DESC, bt.bid_id DESC
        """;

    private static final String SQL_SELECT_LATEST_BY_BIDDER = """
        SELECT bt.bid_id, bt.auction_id, bt.bidder_id, bt.amount AS user_bid,
               bt.is_auto_bid, bt.status AS bid_status, bt.bid_time,
               a.current_price, a.status AS auction_status, a.end_time,
               a.current_winner_id, i.item_id, i.name AS item_name, i.category,
               COALESCE((
                   SELECT img.url
                   FROM item_images img
                   WHERE img.item_id = i.item_id
                   ORDER BY img.is_primary DESC, img.sort_order ASC, img.image_id ASC
                   LIMIT 1
               ), '') AS image_url
        FROM bid_transactions bt
        JOIN (
            SELECT auction_id, MAX(bid_id) AS latest_bid_id
            FROM bid_transactions
            WHERE bidder_id = ?
            GROUP BY auction_id
        ) latest ON latest.latest_bid_id = bt.bid_id
        JOIN auctions a ON a.auction_id = bt.auction_id
        JOIN items i ON i.item_id = a.item_id
        WHERE bt.bidder_id = ?
        ORDER BY bt.bid_time DESC, bt.bid_id DESC
        """;

    private static final String SELECT_DISTINCT_BIDDERS =
        "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = ?";

    private static final String SQL_DELETE_LASTBID =
        "DELETE FROM bid_transactions WHERE bid_id = ( SELECT bid_id FROM bid_transactions WHERE auction_id = ? ORDER BY bid_id DESC LIMIT 1 )";

//    /**
//     * Thực hiện khóa hàng vật lý bằng câu lệnh SELECT ... FOR UPDATE.
//     * Trả về thông tin trạng thái (status) thô hiện tại dưới DB để tầng Service ra quyết định.
//     *
//     * @param conn Connection đang tham gia Transaction điều phối
//     * @param auctionId ID phiên đấu giá cần khóa
//     * @return Chuỗi trạng thái status hiện tại dưới DB, hoặc null nếu không tồn tại bản ghi
//     * @throws SQLException khi có lỗi kết nối hoặc thực thi truy vấn
//     */
//    public String lockAuctionRowAndGetStatus(Connection conn, int auctionId) throws SQLException {
//        try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
//            ps.setInt(1, auctionId);
//            try (ResultSet rs = ps.executeQuery()) {
//                if (!rs.next()) {
//                    logger.warn("lockAuctionRowAndGetStatus() - Auction {} không tồn tại trong DB", auctionId);
//                    return null;
//                }
//                // Trả về dữ liệu thô, không can thiệp logic so sánh thời gian tại đây
//                return rs.getString("status");
//            }
//        }
//    }

    /**
     * @deprecated Dùng {@link #lockAuctionRowAndGetInfo(Connection, int)} để lấy cả endTime.
     * Giữ lại để không break caller cũ trong thời gian migrate.
     */
    @Deprecated
    public String lockAuctionRowAndGetStatus(Connection conn, int auctionId) throws SQLException {
        AuctionLockInfo info = lockAuctionRowAndGetInfo(conn, auctionId);
        return info == null ? null : info.status();
    }

    /**
     * Thực hiện khóa hàng vật lý bằng SELECT ... FOR UPDATE.
     * Trả về AuctionLockInfo chứa cả status lẫn endTime để Service layer
     * có thể guard đồng thời cả trạng thái lẫn thời gian trong một lần lock.
     *
     * @param conn      Connection đang tham gia Transaction điều phối
     * @param auctionId ID phiên đấu giá cần khóa
     * @return AuctionLockInfo, hoặc null nếu không tồn tại bản ghi
     * @throws SQLException khi có lỗi kết nối hoặc thực thi truy vấn
     */
    public AuctionLockInfo lockAuctionRowAndGetInfo(Connection conn, int auctionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("lockAuctionRowAndGetInfo() - Auction {} không tồn tại", auctionId);
                    return null;
                }
                return new AuctionLockInfo(
                    rs.getString("status"),
                    rs.getTimestamp("end_time").toLocalDateTime()  // đã SELECT sẵn, dùng luôn
                );
            }
        }
    }

    /**
     * Thực hiện cập nhật trạng thái phiên đấu giá theo yêu cầu tường minh từ Service.
     */
    public void updateAuctionStatus(Connection conn, int auctionId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AUCTION_STATUS)) {
            ps.setString(1, status);
            ps.setInt(2, auctionId);
            ps.executeUpdate();
            logger.info("updateAuctionStatus() - Đã cập nhật trạng thái Auction {} thành {} dưới DB.", auctionId, status);
        }
    }

    /**
     * Đồng bộ trạng thái giá và thời gian của Auction vào DB, đồng thời outbid người cũ.
     */
    public void updateAuctionState(Connection conn, int auctionId, int bidderId, BigDecimal amount, LocalDateTime currentEndTime) throws SQLException {
        // 1. Cập nhật thông tin phiên đấu giá (bao gồm cả end_time được gia hạn từ Anti-sniping)
        try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AUCTION)) {
            ps.setBigDecimal(1, amount);
            ps.setInt(2, bidderId);
            // Sử dụng Timestamp trực tiếp để đảm bảo tính toàn vẹn dữ liệu thời gian
            ps.setTimestamp(3, Timestamp.valueOf(currentEndTime));
            ps.setInt(4, auctionId);
            ps.executeUpdate();
        }

        // 2. Chuyển trạng thái người đặt giá cao nhất trước đó thành OUTBID
        try (PreparedStatement ps = conn.prepareStatement(SQL_OUTBID_PREVIOUS)) {
            ps.setString(1, BidStatus.OUTBID.name());
            ps.setInt(2, auctionId);
            ps.setString(3, BidStatus.WINNING.name());
            ps.executeUpdate();
        }
    }

    /**
     * Ghi vết lịch sử đặt giá vào DB tham gia chung vào Connection Transaction từ Service.
     * Thực hiện ép kiểu từ String sang Integer một cách an toàn để ghi xuống các cột khóa ngoại dạng INT.
     */
    public void insertBidTransaction(Connection conn, BidHistoryDTO dto) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BID)) {
            ps.setInt(1, dto.getAuctionId());
            ps.setInt(2, dto.getBidderId());
            ps.setBigDecimal(3, dto.getAmount());
            ps.setBoolean(4, dto.isAutoBid());
            ps.setString(5, dto.getStatus().name()); // Lấy name() từ Enum của DTO gốc
            ps.setTimestamp(6, dto.getBidTime());
            ps.executeUpdate();
        }
    }

    /**
     * Lấy lịch sử đặt giá của một phiên đấu giá.
     */
    public List<BidHistoryDTO> getBidHistory(int auctionId) {
        List<BidHistoryDTO> historyList = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HISTORY)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Ánh xạ trực tiếp sử dụng BidHistoryDTO và BidStatus gốc của bạn
                    BidHistoryDTO dto = new BidHistoryDTO(
                        rs.getInt("bid_id"),
                        rs.getInt("auction_id"),
                        rs.getInt("bidder_id"),
                        rs.getBigDecimal("amount"),
                        rs.getBoolean("is_auto_bid"),
                        BidStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("bid_time")
                    );
                    historyList.add(dto);
                }
            }
        } catch (SQLException e) {
            logger.error("getBidHistory() - Lỗi truy vấn DB: {}", e.getMessage());
        }
        return historyList;
    }

    /**
     * Lấy bid history của 1 auction, sort ASC theo bid_time.
     * Source duy nhất: bảng bid_transactions.
     */
    public List<BidPointDTO> getBidPointHistory(int auctionId) {
        String sql = """
        SELECT bid_time, amount
        FROM bid_transactions
        WHERE auction_id = ?
        ORDER BY bid_time ASC
        """;
        List<BidPointDTO> points = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    points.add(new BidPointDTO(
                        rs.getTimestamp("bid_time").toLocalDateTime().format(BID_POINT_TIME_FORMATTER),
                        rs.getBigDecimal("amount")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("getBidHistory failed for auctionId={}", auctionId, e);
        }
        return points;
    }

    /**
     * Lấy lịch sử đặt giá kèm tên bidder cho màn seller view.
     *
     * @param auctionId ID phiên đấu giá cần xem lịch sử
     * @return danh sách bid mới nhất lên đầu
     */
    public List<AuctionBidHistoryRow> getAuctionBidHistoryRows(int auctionId) {
        List<AuctionBidHistoryRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HISTORY_WITH_BIDDERS)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AuctionBidHistoryRow(
                        rs.getInt("bid_id"),
                        rs.getInt("auction_id"),
                        rs.getInt("bidder_id"),
                        rs.getString("bidder_name"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status"),
                        rs.getBoolean("is_auto_bid"),
                        rs.getTimestamp("bid_time")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("getAuctionBidHistoryRows() - DB error for auctionId={}", auctionId, e);
        }
        return rows;
    }

    /**
     * Lấy mỗi auction mà user đã tham gia với bid mới nhất của chính user đó.
     *
     * <p>Dữ liệu này phục vụ màn My Bids: so sánh bid gần nhất của user với
     * current_price và current_winner_id hiện tại của auction.</p>
     *
     * @param bidderId ID user đang đăng nhập
     * @return danh sách row mới nhất theo từng auction, mới nhất lên đầu
     */
    public List<UserBidRow> getLatestBidsByBidder(int bidderId) {
        List<UserBidRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_LATEST_BY_BIDDER)) {
            ps.setInt(1, bidderId);
            ps.setInt(2, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new UserBidRow(
                        rs.getInt("bid_id"),
                        rs.getInt("auction_id"),
                        rs.getInt("bidder_id"),
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getBigDecimal("current_price"),
                        rs.getBigDecimal("user_bid"),
                        BidStatus.valueOf(rs.getString("bid_status")),
                        rs.getString("auction_status"),
                        nullableInteger(rs, "current_winner_id"),
                        rs.getBoolean("is_auto_bid"),
                        rs.getTimestamp("bid_time"),
                        rs.getTimestamp("end_time"),
                        rs.getString("image_url")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("getLatestBidsByBidder() - DB error for bidderId={}", bidderId, e);
        }
        return rows;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Lấy danh sách ID của những người tham gia đấu giá duy nhất.
     */
    public List<Integer> getBiddersByAuctionId(int auctionId) {
        List<Integer> bidderIds = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(SELECT_DISTINCT_BIDDERS)) {
            pstmt.setInt(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    bidderIds.add(rs.getInt("bidder_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("getBiddersByAuctionId() - Error", e);
        }
        return bidderIds;
    }

    /**
     * Xóa bid transaction mới nhất của auction (dùng để rollback manual bid khi auto-bid fail)
     */
    public boolean rollbackLastBid(Connection conn, int auctionId) throws SQLException{
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_LASTBID)) {
            ps.setInt(1, auctionId);
            int result = ps.executeUpdate();
            logger.info("rollbackLastBid() - Deleted {} manual bid for auction {}", result, auctionId);
            return result > 0;
        }
    }
    private void executeStatement(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }


    /**
     * Projection row cho lịch sử bid trong màn seller view.
     */
    public static final class AuctionBidHistoryRow {
        private final int bidId;
        private final int auctionId;
        private final int bidderId;
        private final String bidderName;
        private final BigDecimal amount;
        private final String status;
        private final boolean autoBid;
        private final Timestamp bidTime;

        public AuctionBidHistoryRow(int bidId, int auctionId, int bidderId,
            String bidderName, BigDecimal amount, String status, boolean autoBid,
            Timestamp bidTime) {
            this.bidId = bidId;
            this.auctionId = auctionId;
            this.bidderId = bidderId;
            this.bidderName = bidderName;
            this.amount = amount;
            this.status = status;
            this.autoBid = autoBid;
            this.bidTime = bidTime;
        }

        public int getBidId() { return bidId; }
        public int getAuctionId() { return auctionId; }
        public int getBidderId() { return bidderId; }
        public String getBidderName() { return bidderName; }
        public BigDecimal getAmount() { return amount; }
        public String getStatus() { return status; }
        public boolean isAutoBid() { return autoBid; }
        public Timestamp getBidTime() { return bidTime; }
    }

    /**
     * Projection row cho màn My Bids của client.
     */
    public static final class UserBidRow {
        private final int bidId;
        private final int auctionId;
        private final int bidderId;
        private final int itemId;
        private final String itemName;
        private final String category;
        private final BigDecimal currentPrice;
        private final BigDecimal userBid;
        private final BidStatus bidStatus;
        private final String auctionStatus;
        private final Integer currentWinnerId;
        private final boolean autoBid;
        private final Timestamp bidTime;
        private final Timestamp endTime;
        private final String imageUrl;

        public UserBidRow(int bidId, int auctionId, int bidderId, int itemId,
            String itemName, String category, BigDecimal currentPrice, BigDecimal userBid,
            BidStatus bidStatus, String auctionStatus, Integer currentWinnerId,
            boolean autoBid, Timestamp bidTime, Timestamp endTime, String imageUrl) {
            this.bidId = bidId;
            this.auctionId = auctionId;
            this.bidderId = bidderId;
            this.itemId = itemId;
            this.itemName = itemName;
            this.category = category;
            this.currentPrice = currentPrice;
            this.userBid = userBid;
            this.bidStatus = bidStatus;
            this.auctionStatus = auctionStatus;
            this.currentWinnerId = currentWinnerId;
            this.autoBid = autoBid;
            this.bidTime = bidTime;
            this.endTime = endTime;
            this.imageUrl = imageUrl;
        }

        public int getBidId() { return bidId; }
        public int getAuctionId() { return auctionId; }
        public int getBidderId() { return bidderId; }
        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getCategory() { return category; }
        public BigDecimal getCurrentPrice() { return currentPrice; }
        public BigDecimal getUserBid() { return userBid; }
        public BidStatus getBidStatus() { return bidStatus; }
        public String getAuctionStatus() { return auctionStatus; }
        public Integer getCurrentWinnerId() { return currentWinnerId; }
        public boolean isAutoBid() { return autoBid; }
        public Timestamp getBidTime() { return bidTime; }
        public Timestamp getEndTime() { return endTime; }
        public String getImageUrl() { return imageUrl; }
    }

}