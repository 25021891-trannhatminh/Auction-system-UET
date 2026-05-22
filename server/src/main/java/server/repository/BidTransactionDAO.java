package server.repository;

import server.common.entity.BidTransaction;
import server.common.enums.BidStatus;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BidTransactionDAO - Chỉ làm nhiệm vụ hạ tầng dữ liệu thuần túy (Persistence Only).
 * Đã được viết lại để tương thích hoàn toàn với cấu trúc String ID của BidTransaction.java
 * và cơ chế quản lý Transaction tập trung của BidTransactionService.
 */
public class BidTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(BidTransactionDAO.class);

    private static final String SQL_LOCK_AUCTION =
        "SELECT current_price, status, start_time, end_time FROM auctions WHERE auction_id = ? FOR UPDATE";

    private static final String SQL_MARK_RUNNING =
        "UPDATE auctions SET status = 'RUNNING' WHERE auction_id = ? AND status = 'OPEN'";

    private static final String SQL_UPDATE_AUCTION =
        "UPDATE auctions SET current_price = ?, current_winner_id = ?, end_time = ?, last_bid_time = NOW() WHERE auction_id = ?";

    private static final String SQL_OUTBID_PREVIOUS =
        "UPDATE bid_transactions SET status = ? WHERE auction_id = ? AND status = ?";

    // Đã loại bỏ trường tự tăng bid_id ra khỏi câu lệnh INSERT để DB (MySQL/PostgreSQL) tự sinh tự tăng dưới dạng int
    private static final String SQL_INSERT_BID =
        "INSERT INTO bid_transactions (auction_id, bidder_id, amount, is_auto_bid, status, bid_time) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_HISTORY =
        "SELECT bid_id, auction_id, bidder_id, amount, is_auto_bid, status, bid_time FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time DESC";

    private static final String SELECT_DISTINCT_BIDDERS =
        "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = ?";

    /**
     * Kích hoạt khóa hàng vật lý bằng SELECT ... FOR UPDATE.
     * Nếu trạng thái là OPEN và đã đến giờ, tự động chuyển trạng thái sang RUNNING.
     */
    public boolean lockAuctionRow(Connection conn, int auctionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("lockAuctionRow() - Auction {} không tồn tại trong DB", auctionId);
                    return false;
                }

                String status = rs.getString("status");
                Timestamp startTs = rs.getTimestamp("start_time");
                LocalDateTime startTime = startTs != null ? startTs.toLocalDateTime() : null;
                LocalDateTime now = LocalDateTime.now();

                if ("OPEN".equals(status) && startTime != null && !now.isBefore(startTime)) {
                    try (PreparedStatement up = conn.prepareStatement(SQL_MARK_RUNNING)) {
                        up.setInt(1, auctionId);
                        int updated = up.executeUpdate();
                        if (updated > 0) {
                            logger.info("lockAuctionRow() - Tự động chuyển trạng thái Auction {} sang RUNNING", auctionId);
                        }
                    }
                }
                return true;
            }
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
    public void insertBidTransaction(Connection conn, int auctionId, int bidderId, BidTransaction tx) throws SQLException {
        String statusStr = (tx.getStatus() != null) ? tx.getStatus().name() : BidStatus.WINNING.name();
        LocalDateTime bidTime = (tx.getBidTime() != null) ? tx.getBidTime() : LocalDateTime.now();

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BID)) {
            // Chấp nhận tham số đầu vào tường minh từ Service, hoặc parse từ tx nếu cần
            ps.setInt(1, auctionId);
            ps.setInt(2, bidderId);
            ps.setBigDecimal(3, tx.getAmount());
            ps.setBoolean(4, tx.isAutoBid());
            ps.setString(5, statusStr);
            ps.setTimestamp(6, Timestamp.valueOf(bidTime));

            ps.executeUpdate();
            logger.debug("insertBidTransaction() - Đã lưu vết Bid thành công vào DB.");
        }
    }

    /**
     * Lấy lịch sử đặt giá của một phiên đấu giá.
     */
    public List<BidTransaction> getBidHistory(int auctionId) {
        List<BidTransaction> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HISTORY)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(getBidTransactionByRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("getBidHistory() - Error: {}", e.getMessage());
        }
        return results;
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

    private void executeStatement(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Hàm ánh xạ dữ liệu ngược từ DB lên RAM.
     * Giải quyết điểm bất đối xứng bằng cách sử dụng String.valueOf() để chuyển int từ DB
     * khớp hoàn toàn với constructor đầy đủ 8 tham số dạng String của file BidTransaction cũ.
     */
    private BidTransaction getBidTransactionByRow(ResultSet rs) throws SQLException {
        AccountDAO accountDAO = new AccountDAO();
        int dbBidderId = rs.getInt("bidder_id");
        String bidderName = accountDAO.getUserById(dbBidderId).getFullName();

        // Thực hiện mapping an toàn: Ép kiểu int từ DB thành String để đẩy vào Constructor của BidTransaction
        return new BidTransaction(
            String.valueOf(rs.getInt("bid_id")),      // id (String)
            String.valueOf(rs.getInt("auction_id")), // auctionId (String)
            String.valueOf(dbBidderId),              // bidderId (String)
            bidderName,                              // bidderName (String)
            rs.getBigDecimal("amount"),              // amount (BigDecimal)
            rs.getTimestamp("bid_time").toLocalDateTime(), // bidTime (LocalDateTime)
            rs.getBoolean("is_auto_bid"),            // isAutoBid (boolean)
            BidStatus.valueOf(rs.getString("status")) // status (BidStatus)
        );
    }
}