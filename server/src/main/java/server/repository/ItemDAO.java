package server.repository;

import server.common.model.ItemDTO;
import server.database.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    public boolean addItem(int sellerId, String name, String category, BigDecimal price) {
        if (name == null || name.isEmpty() || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        String sql = "INSERT INTO items (seller_id, name, category, starting_price) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            ps.setString(2, name);
            ps.setString(3, category);
            ps.setBigDecimal(4, price);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateItem(int itemId, String name, String category) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String sql = "UPDATE items SET name=?, category=? WHERE item_id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, category);
            ps.setInt(3, itemId);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteItem(int itemId) {

        String checkSql = "SELECT COUNT(*) FROM auctions WHERE item_id = ?";
        String deleteSql = "DELETE FROM items WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, itemId);
                ResultSet rs = ps.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, itemId);
                return ps.executeUpdate() > 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<ItemDTO> getBySeller(int sellerId){
        List<ItemDTO> list = new ArrayList<>();

        String sql = "SELECT item_id, name, category, starting_price, created_at FROM items WHERE seller_id=?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, sellerId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new ItemDTO(
                        rs.getInt("item_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getBigDecimal("starting_price"),
                        rs.getTimestamp("created_at")
                ));
            }
        }catch(SQLException e){
            e.printStackTrace();
        }
        return list;
    }
}