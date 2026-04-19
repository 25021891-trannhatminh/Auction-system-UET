package server.repository;

import server.database.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BidDAO {

    public boolean placeBid(int auctionId, int bidderId, BigDecimal amount) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return false;

            conn.setAutoCommit(false);

            try {
                String lockSql = "SELECT current_price, status FROM auctions WHERE auction_id = ? FOR UPDATE";
                BigDecimal currentPrice;

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, auctionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }


                        if (!"RUNNING".equals(rs.getString("status"))) {
                            System.err.println("Lỗi: Cuộc đấu giá không ở trạng thái RUNNING.");
                            conn.rollback();
                            return false;
                        }

                        currentPrice = rs.getBigDecimal("current_price");
                    }
                }

                if (amount.compareTo(currentPrice) <= 0) {
                    System.err.println("Lỗi: Giá đặt " + amount + " phải lớn hơn giá hiện tại " + currentPrice);
                    conn.rollback();
                    return false;
                }

                String updateSql = "UPDATE auctions SET current_price = ?, current_winner_id = ?, last_bid_time = CURRENT_TIMESTAMP WHERE auction_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBigDecimal(1, amount);
                    ps.setInt(2, bidderId);
                    ps.setInt(3, auctionId);
                    ps.executeUpdate();
                }

                String insertSql = "INSERT INTO bids (auction_id, bidder_id, amount, bid_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setInt(1, auctionId);
                    ps.setInt(2, bidderId);
                    ps.setBigDecimal(3, amount);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println(">>> [Transaction Error]: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println(">>> [Connection Error]: " + e.getMessage());
            return false;
        }
    }
}