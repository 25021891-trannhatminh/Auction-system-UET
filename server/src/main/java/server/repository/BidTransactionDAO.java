package server.repository;

import server.common.entity.Auction;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.entity.manager.AuctionManager;
import server.database.DBConnection;
import server.common.enums.BidStatus;
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
        "SELECT DISTINCT bidder_id FROM bid_transactions WHERE auction_id = ?";
    /**
     * Đặt giá - Gọi vào Auction.placeBid() để xử lý logic, sau đó lưu DB.
     */
    public boolean placeBid(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
        // Kiểm tra đầu vào
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("placeBid() - Invalid amount: {}", amount);
            return false;
        }

        try {
            // 1. Lấy Auction từ AuctionManager
            Auction auction = AuctionManager.getInstance()
                    .getAuction(String.valueOf(auctionId))
                    .orElse(null);
            if (auction == null) {
                logger.warn("placeBid() - Auction {} not found", auctionId);
                return false;
            }

            // 2. Lấy User từ AuctionManager
            User bidder = AuctionManager.getInstance()
                    .findUserById(String.valueOf(bidderId))
                    .orElse(null);
            if (bidder == null) {
                logger.warn("placeBid() - Bidder {} not found", bidderId);
                return false;
            }

            // 3.  GỌI CORE LOGIC TRONG AUCTION
            BidTransaction tx = auction.placeBid(bidder, amount, isAutoBid);

            // 4. Lưu vào DB
            if (tx != null) {
                return saveToDatabase(tx);
            }
            return false;

        } catch (AuctionClosedException e) {
            logger.warn("placeBid() - Auction closed: {}", e.getMessage());
            return false;
        } catch (InvalidBidException e) {
            logger.warn("placeBid() - Invalid bid: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("placeBid() - Error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Lưu bid transaction vào database.
     */
    private boolean saveToDatabase(BidTransaction tx) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                int auctionId = Integer.parseInt(tx.getAuctionId());
                int bidderId = Integer.parseInt(tx.getBidderId());

                // Bước 1: Cập nhật auction (giá mới, người thắng mới)
                try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AUCTION)) {
                    ps.setBigDecimal(1, tx.getAmount());
                    ps.setInt(2, bidderId);
                    ps.setInt(3, auctionId);
                    ps.executeUpdate();
                }

                // Bước 2: Đánh dấu bid cũ thành OUTBID
                try (PreparedStatement ps = conn.prepareStatement(SQL_OUTBID_PREVIOUS)) {
                    ps.setString(1, BidStatus.OUTBID.name());
                    ps.setInt(2, auctionId);
                    ps.setString(3, BidStatus.WINNING.name());
                    ps.executeUpdate();
                }

                // Bước 3: Insert bid mới
                try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BID)) {
                    ps.setInt(1, auctionId);
                    ps.setInt(2, bidderId);
                    ps.setBigDecimal(3, tx.getAmount());
                    ps.setBoolean(4, tx.isAutoBid());
                    ps.setString(5, tx.getStatus().name());
                    ps.executeUpdate();
                }

                conn.commit();
                logger.info("saveToDatabase() - Saved bid: auction={}, bidder={}, amount={}",
                        auctionId, bidderId, tx.getAmount());
                return true;

            } catch (SQLException e) {
                conn.rollback();
                logger.error("saveToDatabase() - Transaction failed: {}", e.getMessage());
                return false;
            }

        } catch (SQLException e) {
            logger.error("saveToDatabase() - Connection error: {}", e.getMessage());
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
                    bidderIds.add(rs.getInt("bidder_id"));
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
        String bidderName = accountDAO.getUserById(rs.getInt("bidder_id")).getFullName();
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