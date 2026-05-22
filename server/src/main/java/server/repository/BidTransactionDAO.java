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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BidTransactionDAO - Phiên bản hợp nhất 10/10
 * Giữ nguyên khóa hàng (Pessimistic Locking) chống Race Condition của bạn
 * Đồng thời chạy đồng bộ với Core Logic của đối tượng Auction.
 */
public class BidTransactionDAO {

    private static final Logger logger = LoggerFactory.getLogger(BidTransactionDAO.class);

    // Giữ nguyên các hằng số SQL bảo mật của bạn
    private static final String SQL_LOCK_AUCTION = """
            SELECT current_price, status, min_bid_increment, current_winner_id,
                   seller_id, start_time, end_time
            FROM auctions WHERE auction_id = ? FOR UPDATE
            """;

    private static final String SQL_MARK_RUNNING = """
            UPDATE auctions SET status = 'RUNNING'
            WHERE auction_id = ? AND status = 'OPEN'
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
     * Đặt giá đấu giá - Hợp nhất Khóa Transaction DB của bạn và Core Logic Auction
     */
    public boolean placeBid(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("placeBid() - Invalid amount: {}", amount);
            return false;
        }

        // BƯỚC 1: Mở kết nối và bật Transaction chặn Race Condition y như lúc đầu của bạn
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Thao tác khóa hàng trong DB để không ai chen ngang được
                try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
                    ps.setInt(1, auctionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("placeBid() - Auction {} not found in DB", auctionId);
                            conn.rollback();
                            return false;
                        }

                        // Tự động kích hoạt trạng thái RUNNING nếu đến giờ (Giữ logic tiện ích của bạn)
                        String status = rs.getString("status");
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
                        LocalDateTime endTime = rs.getTimestamp("end_time").toLocalDateTime();

                        if ("OPEN".equals(status) && !now.isBefore(startTime) && now.isBefore(endTime)) {
                            executeStatement(conn, SQL_MARK_RUNNING, auctionId);
                        }
                    }
                }

                // BƯỚC 2: Gọi đối tượng trong bộ nhớ để đồng bộ Core Logic (Xử lý anti-sniping, bộ test)
                Auction auction = AuctionManager.getInstance().getAuction(String.valueOf(auctionId)).orElse(null);
                User bidder = AuctionManager.getInstance().findUserById(String.valueOf(bidderId)).orElse(null);

                if (auction == null || bidder == null) {
                    logger.warn("placeBid() - Auction or Bidder memory object not found");
                    conn.rollback();
                    return false;
                }

                // Chạy trực tiếp hàm nghiệp vụ (nếu sai luật, hàm này tự ném Exception và nhảy xuống rollback)
                BidTransaction tx = auction.placeBid(bidder, amount, isAutoBid);
                if (tx == null) {
                    conn.rollback();
                    return false;
                }

                // BƯỚC 3: Ghi các thay đổi vào DB trong cùng một Transaction an toàn
                // Cập nhật giá mới cho Auction
                executeStatement(conn, SQL_UPDATE_AUCTION, tx.getAmount(), bidderId, auctionId);

                // Chuyển người thắng cũ thành OUTBID
                executeStatement(conn, SQL_OUTBID_PREVIOUS, BidStatus.OUTBID.name(), auctionId, BidStatus.WINNING.name());

                // Chèn lượt đặt giá mới vào lịch sử
                executeStatement(conn, SQL_INSERT_BID, auctionId, bidderId, tx.getAmount(), isAutoBid, tx.getStatus().name());

                // COMMIT thành công toàn bộ!
                conn.commit();
                logger.info("placeBid() - Hợp nhất thành công: Auction {}, Bidder {}, Amount {}", auctionId, bidderId, amount);
                return true;

            } catch (AuctionClosedException | InvalidBidException e) {
                logger.warn("placeBid() - Từ chối đặt giá do vi phạm luật đấu giá: {}", e.getMessage());
                conn.rollback();
                return false;
            } catch (SQLException e) {
                logger.error("placeBid() - Lỗi hệ thống cơ sở dữ liệu, thực hiện rollback: {}", e.getMessage());
                conn.rollback();
                return false;
            }
        } catch (Exception e) {
            logger.error("placeBid() - Lỗi kết nối DB nghiêm trọng: {}", e.getMessage());
            return false;
        }
    }

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