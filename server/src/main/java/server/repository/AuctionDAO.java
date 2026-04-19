package server.repository;

import server.common.enums.AuctionStatus;
import server.common.model.AuctionDTO;
import server.database.DBConnection;

import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AuctionDAO {


    public boolean createAuction(int itemId, int sellerId, Timestamp startTime, Timestamp endTime, BigDecimal startingPrice) {

        if (startTime.after(endTime) || startingPrice == null
                || startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String sql = " INSERT INTO auctions (item_id, seller_id, start_time, end_time, current_price, status)"
                   + " VALUES (?, ?, ?, ?, ?, ?) ";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, itemId);
            ps.setInt(2, sellerId);
            ps.setTimestamp(3, startTime);
            ps.setTimestamp(4, endTime);
            ps.setBigDecimal(5, startingPrice);
            ps.setString(6, AuctionStatus.OPEN.name());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logError("createAuction", e);
            return false;
        }
    }

    public boolean updateAuctionStatus(int auctionId, AuctionStatus status) {

        String sql = "UPDATE auctions SET status = ? WHERE auction_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setInt(2, auctionId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            logError("updateAuctionStatus", e);
            return false;
        }
    }

    public List<AuctionDTO> getAuctionListForClient() {

        List<AuctionDTO> list = new ArrayList<>();

        String sql = """
            SELECT a.auction_id, i.name AS item_name, 
                   a.current_price, a.end_time, a.status
            FROM auctions a
            JOIN items i ON a.item_id = i.item_id
            WHERE a.status = ? AND a.end_time > NOW()
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, AuctionStatus.RUNNING.name());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapRow(rs));
            }

        } catch (SQLException e) {
            logError("getAuctionListForClient", e);
        }

        return list;
    }

    public boolean deleteAuction(int auctionId) {

        String checkSql = "SELECT COUNT(*) FROM bids WHERE auction_id = ?";
        String deleteSql = "DELETE FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, auctionId);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }

            // delete
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, auctionId);
                return ps.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            logError("deleteAuction", e);
            return false;
        }
    }

    public BigDecimal getCurrentPrice(int auctionId) {

        String sql = "SELECT current_price FROM auctions WHERE auction_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getBigDecimal("current_price");
            }

        } catch (SQLException e) {
            logError("getCurrentPrice", e);
        }

        return null;
    }

    public BigDecimal getCurrentPrice(Connection conn, int auctionId) throws SQLException {

        String sql = "SELECT current_price FROM auctions WHERE auction_id = ? FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getBigDecimal("current_price");
            }
        }

        return null;
    }

    private AuctionDTO mapRow(ResultSet rs) throws SQLException {

        return new AuctionDTO(
                rs.getInt("auction_id"),
                rs.getString("item_name"),
                rs.getBigDecimal("current_price"),
                rs.getTimestamp("end_time"),
                AuctionStatus.valueOf(rs.getString("status"))
        );
    }

    private void logError(String method, SQLException e) {
        System.err.println("[AuctionDAO ERROR] " + method + ": " + e.getMessage());
    }
}