package server.repository;

import server.common.enums.UserRole;
import server.common.enums.UserStatus;
import server.common.model.User;
import server.database.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {


    public boolean register(String username, String password, String email) {

        String sql = "INSERT INTO users (username, password, email, role, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, email);
            ps.setString(4, UserRole.USER.name());
            ps.setString(5, UserStatus.ACTIVE.name());

            return ps.executeUpdate() > 0;
        }catch(SQLException e){
            e.printStackTrace();
            return false;
        }
    }

    public User login(String identifier, String password){

        String sql = """
            SELECT user_id, username, email, role, status, created_at
            FROM users
            WHERE (username = ? OR email = ?) AND password = ?
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, identifier);
            ps.setString(2,identifier);
            ps.setString(3, password);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        UserRole.valueOf(rs.getString("role")),
                        UserStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at")
                );
            }
        }catch(SQLException e){
            e.printStackTrace();
        }
        return null;
    }

    public User getById(int userId){

        String sql = """
            SELECT user_id, username, email, role, status, created_at
            FROM users
            WHERE user_id = ?
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        UserRole.valueOf(rs.getString("role")),
                        UserStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at")
                );
            }
        }catch(SQLException e){
            e.printStackTrace();
        }
        return null;
    }
}