package server.repository;

import server.common.enums.AutoBidStatus;
import server.common.model.AutoBidDTO;
import server.database.DBConnection;

import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AutoBidDAO {

    public boolean setupAutoBid(int auctionId, int bidderId, BigDecimal maxBid, BigDecimal increment) {

        if (maxBid == null || increment == null ||
                maxBid.compareTo(BigDecimal.ZERO) <= 0 ||
                increment.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String sql = """
            INSERT INTO auto_bids (auction_id, bidder_id, max_bid, increment, status)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionId);
            ps.setInt(2, bidderId);
            ps.setBigDecimal(3, maxBid);
            ps.setBigDecimal(4, increment);
            ps.setString(5, AutoBidStatus.ACTIVE.name());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    public boolean cancelAutoBid(int autoBidId) {

        String sql = "UPDATE auto_bids SET status = ? WHERE auto_bid_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, AutoBidStatus.CANCELED.name());
            ps.setInt(2, autoBidId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean markAsCompleted(int autoBidId) {

        String sql = "UPDATE auto_bids SET status = ? WHERE auto_bid_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, AutoBidStatus.COMPLETED.name());
            ps.setInt(2, autoBidId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logError("markAsCompleted", e);
            return false;
        }
    }

    public List<AutoBidDTO> getActiveAutoBids(int auctionId) {

        List<AutoBidDTO> list = new ArrayList<>();

        String sql = """
            SELECT bidder_id, max_bid, increment 
            FROM auto_bids 
            WHERE auction_id = ? AND status = ?
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionId);
            ps.setString(2, AutoBidStatus.ACTIVE.name());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new AutoBidDTO(
                        rs.getInt("bidder_id"),
                        rs.getBigDecimal("max_bid"),
                        rs.getBigDecimal("increment")
                ));
            }

        } catch (SQLException e) {
            logError("getActiveAutoBids", e);
        }

        return list;
    }

    private void logError(String method, SQLException e) {
        System.err.println("[AutoBidDAO ERROR] " + method + ": " + e.getMessage());
    }
}