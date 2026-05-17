package server.repository;

import server.common.enums.AccountRole;
import server.common.enums.BidStatus;
import server.common.entity.BidTransaction;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * BidTransactionDAO - Optimized Version 10/10
 * Xử lý đấu giá thời gian thực với Pessimistic Locking & Transaction Integrity.
 */
public class BidTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(BidTransactionDAO.class);

    // SQL Constants - Sử dụng Text Block để dễ quản lý
    private static final String SQL_LOCK_AUCTION = """
            SELECT current_price, status, min_bid_increment, current_winner_id
            FROM auctions WHERE auction_id = ? FOR UPDATE
            """;

    private static final String SQL_UPDATE_AUCTION = """
            UPDATE auctions SET current_price = ?, current_winner_id = ?, last_bid_time = NOW()
            WHERE auction_id = ?
            """;

    private static final String SQL_OUTBID_PREVIOUS = """
            UPDATE bid_transactions SET status = ? WHERE auction_id = ? AND status = ?
            """;

    private static final String SQL_INSERT_BID = """
            INSERT INTO bid_transactions (auction_id, bidder_id, amount, is_auto_bid, status, bid_time)
            VALUES (?, ?, ?, ?, ?, NOW())
            """;

    private static final String SQL_SELECT_HISTORY = """
            SELECT bid_id, auction_id, bidder_id, amount, is_auto_bid, status, bid_time
            FROM bid_transactions WHERE auction_id = ? ORDER BY bid_time DESC
            """;
    private static final String SELECT_DISTINCT_BIDDERS =
        "SELECT DISTINCT user_id FROM bid_transactions WHERE auction_id = ?";
    /**
     * Đặt giá đấu giá - Đảm bảo tính nguyên tử và chống Race Condition.
     */
    public boolean placeBid(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
        // 1. Fail-fast: Kiểm tra dữ liệu đầu vào ngay lập tức để tiết kiệm tài nguyên
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("placeBid() - Invalid amount: {}", amount);
            return false;
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Tắt Auto-commit để bắt đầu Transaction
            conn.setAutoCommit(false);

            try {
                // Bước 1: Khóa hàng (Row-level Lock) để ngăn Race Condition
                BigDecimal currentPrice;
                BigDecimal minIncrement;
                int currentWinnerId;

                try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
                    ps.setInt(1, auctionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("placeBid() - Auction {} not found", auctionId);
                            conn.rollback();
                            return false;
                        }

                        // Kiểm tra trạng thái phiên đấu giá
                        if (!"RUNNING".equals(rs.getString("status"))) {
                            logger.warn("placeBid() - Auction {} is not RUNNING", auctionId);
                            conn.rollback();
                            return false;
                        }

                        currentPrice = rs.getBigDecimal("current_price");
                        minIncrement = rs.getBigDecimal("min_bid_increment");
                        currentWinnerId = rs.getInt("current_winner_id");
                    }
                }

                // Bước 2: Logic nghiệp vụ nâng cao
                // Kiểm tra nếu người dùng hiện tại đang là người giữ giá cao nhất (Chống Self-outbid)
                if (currentWinnerId == bidderId) {
                    logger.info("placeBid() - Bidder {} is already the winner for auction {}", bidderId, auctionId);
                    conn.rollback();
                    return false;
                }

                // Kiểm tra bước giá tối thiểu
                BigDecimal minRequired = currentPrice.add(minIncrement);
                if (amount.compareTo(minRequired) < 0) {
                    logger.warn("placeBid() - Amount {} too low (min: {})", amount, minRequired);
                    conn.rollback();
                    return false;
                }

                // Bước 3: Cập nhật thông tin Auction (Giá mới, người thắng mới)
                executeStatement(conn, SQL_UPDATE_AUCTION, amount, bidderId, auctionId);

                // Bước 4: Chuyển các lượt thắng cũ thành OUTBID
                executeStatement(conn, SQL_OUTBID_PREVIOUS, BidStatus.OUTBID.name(), auctionId, BidStatus.WINNING.name());

                // Bước 5: Ghi nhận lượt bid mới là WINNING
                executeStatement(conn, SQL_INSERT_BID, auctionId, bidderId, amount, isAutoBid, BidStatus.WINNING.name());

                // Kết thúc giao dịch thành công
                conn.commit();
                logger.info("placeBid() - Success: Auction {}, Bidder {}, Amount {}", auctionId, bidderId, amount);
                return true;

            } catch (SQLException e) {
                logger.error("placeBid() - Transaction failed, rolling back. Error: {}", e.getMessage());
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            logger.error("placeBid() - Connection error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Truy vấn lịch sử đặt giá.
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

    public List<Integer> getBiddersByAuctionId(int auctionId) {
        List<Integer> bidderIds = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(SELECT_DISTINCT_BIDDERS)) {

            pstmt.setInt(1, auctionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    bidderIds.add(rs.getInt("user_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("getBiddersByAuctionId() - Error", e);
        }
        return bidderIds;
    }

    // ============================================================
    // Private Helper Methods (Giúp code sạch và tái sử dụng)
    // ============================================================

    private void executeStatement(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private BidTransaction getBidTransactionByRow(ResultSet rs) throws SQLException {
        AccountDAO accountDAO = new AccountDAO();
        String bidderName = accountDAO.getById(rs.getInt("bidder_id")).getFullName();
        return new BidTransaction(
            String.valueOf(rs.getInt("bid_id")),
            String.valueOf(rs.getInt("auction_id")),
            String.valueOf(rs.getInt("bidder_id")),
            bidderName,
            rs.getBigDecimal("amount"),
            rs.getTimestamp("bid_time").toLocalDateTime(),
            rs.getBoolean("is_auto_bid"),
            BidStatus.valueOf(rs.getString("status"))
        );
    }
}