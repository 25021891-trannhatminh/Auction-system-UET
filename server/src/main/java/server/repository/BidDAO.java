package server.repository;

import server.common.enums.BidStatus;
import server.common.model.BidHistoryDTO;
import server.database.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) cho bảng {@code BIDS}.
 *
 * <p>Cung cấp các thao tác đặt giá và truy vấn lịch sử bid. Thao tác
 * {@link #placeBid} chạy trong transaction với {@code SELECT ... FOR UPDATE}
 * để tránh race condition khi nhiều người đặt giá đồng thời.</p>
 */
public class BidDAO {

    private static final Logger logger = LoggerFactory.getLogger(BidDAO.class);

    // ============================================================
    // SQL constants
    // ============================================================

    private static final String SQL_BASE_SELECT = """
      SELECT bid_id, auction_id, bidder_id,
             amount, is_auto_bid, status, bid_time
      FROM bids
      """;

    private static final String SQL_LOCK_AUCTION = """
      SELECT current_price, status, min_bid_increment
      FROM auctions
      WHERE auction_id = ?
      FOR UPDATE
      """;

    private static final String SQL_UPDATE_AUCTION = """
      UPDATE auctions
      SET current_price = ?, current_winner_id = ?, last_bid_time = NOW()
      WHERE auction_id = ?
      """;

    private static final String SQL_OUTBID_PREVIOUS = """
      UPDATE bids
      SET status = ?
      WHERE auction_id = ? AND status = ?
      """;

    private static final String SQL_INSERT_BID = """
      INSERT INTO bids (
          auction_id, bidder_id, amount,
          is_auto_bid, status, bid_time
      ) VALUES (?, ?, ?, ?, ?, NOW())
      """;

    private static final String SQL_SELECT_TOP_BID =
        SQL_BASE_SELECT + "WHERE auction_id = ? ORDER BY amount DESC LIMIT 1";

    private static final String SQL_SELECT_HISTORY =
        SQL_BASE_SELECT + "WHERE auction_id = ? ORDER BY bid_time DESC";

    // ============================================================
    // Public methods
    // ============================================================

    /**
     * Thực hiện đặt giá vào một phiên đấu giá trong một Database Transaction.
     *
     * <p>Luồng xử lý bao gồm việc khóa hàng (Locking), kiểm tra trạng thái phiên,
     * xác thực số tiền bid tối thiểu, cập nhật thông tin phiên đấu giá và ghi lại lịch sử bid.</p>
     *
     * @param auctionId ID của phiên đấu giá cần đặt giá.
     * @param bidderId  ID của người thực hiện đặt giá.
     * @param amount    Số tiền đặt giá (phải lớn hơn hoặc bằng giá hiện tại + bước giá tối thiểu).
     * @param isAutoBid Đánh dấu {@code true} nếu đây là lượt đặt giá tự động từ hệ thống AutoBid.
     * @return {@code true} nếu giao dịch đặt giá thành công và đã được commit; ngược lại trả về {@code false}.
     */
    public boolean placeBid(int auctionId, int bidderId, BigDecimal amount, boolean isAutoBid) {
        logger.debug("placeBid() – auctionId={}, bidderId={}, amount={}, isAutoBid={}",
            auctionId, bidderId, amount, isAutoBid);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Step 1 — Lock auction row
                BigDecimal currentPrice;
                BigDecimal minIncrement;

                try (PreparedStatement ps = conn.prepareStatement(SQL_LOCK_AUCTION)) {
                    ps.setInt(1, auctionId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            logger.warn("placeBid() – Auction not found, auctionId={}", auctionId);
                            conn.rollback();
                            return false;
                        }

                        String auctionStatus = rs.getString("status");
                        if (!"RUNNING".equals(auctionStatus)) {
                            logger.warn("placeBid() – Auction not RUNNING (status={}), auctionId={}",
                                auctionStatus, auctionId);
                            conn.rollback();
                            return false;
                        }

                        currentPrice = rs.getBigDecimal("current_price");
                        minIncrement = rs.getBigDecimal("min_bid_increment");
                    }
                }

                // Step 2 — Validate amount
                BigDecimal minRequired = currentPrice.add(minIncrement);
                if (amount.compareTo(minRequired) < 0) {
                    logger.warn("placeBid() – Amount too low: amount={}, required>={}, auctionId={}",
                        amount, minRequired, auctionId);
                    conn.rollback();
                    return false;
                }

                // Step 3 — Update auction current price
                try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AUCTION)) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, bidderId);
                    ps.setInt(3, auctionId);
                    ps.executeUpdate();
                }

                // Step 4 — Mark previous winning bids as OUTBID
                try (PreparedStatement ps = conn.prepareStatement(SQL_OUTBID_PREVIOUS)) {
                    ps.setString(1, BidStatus.OUTBID.name());
                    ps.setInt(2, auctionId);
                    ps.setString(3, BidStatus.WINNING.name());
                    ps.executeUpdate();
                }

                // Step 5 — Insert new winning bid
                try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_BID)) {
                    ps.setInt(1, auctionId);
                    ps.setInt(2, bidderId);
                    ps.setBigDecimal(3, amount);
                    ps.setBoolean(4, isAutoBid);
                    ps.setString(5, BidStatus.WINNING.name());
                    ps.executeUpdate();
                }

                conn.commit();
                logger.info("placeBid() – Bid placed successfully: auctionId={}, bidderId={}, amount={}",
                    auctionId, bidderId, amount);
                return true;

            } catch (SQLException e) {
                logger.error("placeBid() – DB error during transaction, rolling back: auctionId={}, bidderId={}",
                    auctionId, bidderId, e);
                conn.rollback();
                return false;
            }

        } catch (SQLException e) {
            logger.error("placeBid() – Failed to acquire connection: auctionId={}, bidderId={}",
                auctionId, bidderId, e);
            return false;
        }
    }

    /**
     * Lấy lượt đặt giá có giá trị cao nhất hiện tại của một phiên đấu giá.
     *
     * @param auctionId ID của phiên đấu giá cần kiểm tra.
     * @return Đối tượng {@link BidHistoryDTO} đại diện cho bid cao nhất, hoặc {@code null} nếu chưa có ai bid.
     */
    public BidHistoryDTO getTopBid(int auctionId) {
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_TOP_BID)) {

            ps.setInt(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("getTopBid() – DB error for auctionId={}", auctionId, e);
        }
        return null;
    }

    /**
     * Truy vấn toàn bộ lịch sử các lượt đặt giá của một phiên đấu giá.
     * Kết quả được sắp xếp theo thời gian mới nhất lên đầu.
     *
     * @param auctionId ID của phiên đấu giá cần lấy lịch sử.
     * @return Danh sách các đối tượng {@link BidHistoryDTO}. Trả về list rỗng nếu không có dữ liệu hoặc lỗi.
     */
    public List<BidHistoryDTO> getBidHistory(int auctionId) {
        List<BidHistoryDTO> results = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SQL_SELECT_HISTORY)) {

            ps.setInt(1, auctionId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("getBidHistory() – DB error for auctionId={}", auctionId, e);
        }
        return results;
    }

    // ============================================================
    // Private helpers
    // ============================================================

    /**
     * Chuyển đổi một hàng dữ liệu từ ResultSet sang đối tượng DTO.
     *
     * @param rs ResultSet đang được định vị tại hàng cần đọc.
     * @return Đối tượng {@link BidHistoryDTO} chứa dữ liệu từ database.
     * @throws SQLException Nếu có lỗi khi truy xuất dữ liệu từ các cột SQL.
     */
    private BidHistoryDTO mapRow(ResultSet rs) throws SQLException {
        return new BidHistoryDTO(
            rs.getInt("bid_id"),
            rs.getInt("auction_id"),
            rs.getInt("bidder_id"),
            rs.getBigDecimal("amount"),
            rs.getBoolean("is_auto_bid"),
            BidStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("bid_time")
        );
    }
}