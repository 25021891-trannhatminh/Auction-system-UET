package server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import server.common.entity.User;
import server.common.util.AccountValidator;
import server.database.DBConnection;
import server.repository.UserDAO;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username = "Guest"; // Tên hiển thị mặc định

    private UserDAO userDAO = new UserDAO();
    private int userId;
    // Giả sử AuctionItem là một class hỗ trợ lưu thông tin vật phẩm
    private static Map<String, AuctionItem> items = new HashMap<>();

    static {
        items.put("1", new AuctionItem("1", "Item-1"));
        items.put("2", new AuctionItem("2", "Item-2"));
    }

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                handle(msg);
            }
        } catch (Exception e) {
            System.out.println("Client " + username + " disconnected");
        } finally {
            if(this.userId != 0){
                ClientManager.remove(this.userId);
            }
            try {
                if(socket != null && !socket.isClosed()){
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handle(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;

        // Dùng giới hạn split để tránh lỗi khi description hoặc name có khoảng trắng
        String[] p = msg.split(" ", 6);
        if ("PING".equals(p[0])){
            send("PONG");
            return;
        }
        System.out.println("MSG FROM CLIENT: " + msg);

        switch (p[0]) {
            case "LOGIN":
                if (p.length < 3) {
                    send("LOGIN_FAIL MISSING_CREDENTIALS");
                    return;
                }
                String identifier = p[1]; // Có thể là username hoặc email theo UserDAO
                String pass = p[2];

                try {
                    User u = userDAO.login(identifier, pass);
                    if (u != null) {
                        // Cập nhật username của session này từ DB
                        this.userId = u.getUserId();
                        this.username = u.getUsername();
                        ClientManager.add(this.userId,this);
                        send("LOGIN_SUCCESS " + fields(
                                u.getUserId(),
                                u.getUsername(),
                                u.getEmail(),
                                u.getFullName(),
                                u.getPhone(),
                                u.getRole(),
                                u.getStatus(),
                                u.isActive() ? "true" : "false"
                        ));
                    } else {
                        send("LOGIN_FAIL INVALID_AUTH");
                    }
                } catch (Exception e) {
                    send("LOGIN_FAIL SERVER_ERROR");
                    e.printStackTrace();
                }
                break;

            case "REGISTER":
                // Format mong muốn: REGISTER <user> <pass> <email> <fullName> <phone>
                if (p.length < 6) {
                    send("REGISTER_FAIL INVALID_FORMAT_REQUIRES_5_FIELDS");
                    return;
                }

                String regUser = p[1];
                String regPass = p[2];
                String regEmail = AccountValidator.normalizeEmail(p[3]);
                String regFullName = decodeProtocolSpaces(p[4]);
                String regPhone = AccountValidator.normalizePhone(p[5]);

                if (regUser.isBlank() || regPass.isBlank() || regFullName.isBlank()
                        || containsWhitespace(regUser) || containsWhitespace(regPass)
                        || regPass.length() < 6
                        || !AccountValidator.isValidGmailAddress(regEmail)
                        || !AccountValidator.isValidVietnamesePhone(regPhone)) {
                    send("REGISTER_FAIL INVALID_FORMAT");
                    return;
                }

                try {
                    // Gọi đúng hàm register đã sửa trong UserDAO (5 tham số)
                    boolean ok = userDAO.register(regUser, regPass, regEmail, regFullName, regPhone);
                    if (ok) {
                        send("REGISTER_SUCCESS");
                    } else {
                        send("REGISTER_FAIL_EXIST_OR_ERROR");
                    }
                } catch (Exception e) {
                    send("REGISTER_FAIL_SERVER_ERROR");
                    e.printStackTrace();
                }
                break;

            case "ADMIN_LIST_USERS":
                sendAdminUsers();
                break;

            case "ADMIN_LIST_ITEMS":
                sendAdminItems();
                break;

            case "ADMIN_LIST_AUCTIONS":
                sendAdminAuctions();
                break;

            case "LIST":
                for (AuctionItem item : items.values()) {
                    send("ITEM " + item.getInfo());
                }
                break;

            case "BID":
                if (p.length < 3) {
                    send("FAIL INVALID_BID_FORMAT");
                    return;
                }
                String itemId = p[1];
                try {
                    int price = Integer.parseInt(p[2]);
                    AuctionItem itemObj = items.get(itemId);

                    if (itemObj != null && itemObj.bid(this.username, price)) {
                        ClientManager.broadcast("NEW_BID " + this.username + " " + itemObj.getInfo());
                    } else {
                        send("FAIL Bid too low or item not found");
                    }
                } catch (NumberFormatException e) {
                    send("FAIL PRICE_MUST_BE_NUMBER");
                }
                break;

            case "MSG":
                if (msg.length() > 4) {
                    ClientManager.broadcast("MSG " + this.username + ": " + msg.substring(4).trim());
                }
                break;

            default:
                send("UNKNOWN_COMMAND");
                break;
        }
    }

    private String decodeProtocolSpaces(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private boolean containsWhitespace(String value) {
        return value != null && value.matches(".*\\s+.*");
    }

    private void sendAdminUsers() {
        Map<Integer, Integer> itemCounts = loadIntCounts("""
            SELECT seller_id AS user_id, COUNT(*) AS value
            FROM items
            GROUP BY seller_id
            """);
        Map<Integer, Integer> runningCounts = loadIntCounts("""
            SELECT seller_id AS user_id, COUNT(*) AS value
            FROM auctions
            WHERE status = 'RUNNING'
            GROUP BY seller_id
            """);
        Map<Integer, Integer> bidCounts = loadIntCounts("""
            SELECT bidder_id AS user_id, COUNT(*) AS value
            FROM bids
            GROUP BY bidder_id
            """);

        String sql = """
            SELECT user_id,
                   username,
                   COALESCE(email, '') AS email,
                   COALESCE(full_name, '') AS full_name,
                   COALESCE(phone, '') AS phone,
                   COALESCE(role, 'USER') AS role,
                   COALESCE(status, 'ACTIVE') AS status,
                   is_active,
                   last_login,
                   created_at
            FROM users
            ORDER BY created_at DESC, user_id DESC
            """;

        send("ADMIN_USERS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int userId = rs.getInt("user_id");
                send("ADMIN_USER " + fields(
                        userId,
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getBoolean("is_active") ? "true" : "false",
                        rs.getTimestamp("last_login"),
                        rs.getTimestamp("created_at"),
                        itemCounts.getOrDefault(userId, 0),
                        runningCounts.getOrDefault(userId, 0),
                        bidCounts.getOrDefault(userId, 0)
                ));
            }
        } catch (SQLException e) {
            send("ADMIN_DATA_ERROR " + fields("USERS", e.getMessage()));
            e.printStackTrace();
        }
        send("ADMIN_USERS_END");
    }

    private Map<Integer, Integer> loadIntCounts(String sql) {
        Map<Integer, Integer> counts = new HashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getInt("user_id"), rs.getInt("value"));
            }
        } catch (SQLException e) {
            // Related tables must not block the main users table from loading.
            System.out.println("Admin count query skipped: " + e.getMessage());
        }
        return counts;
    }

    private void sendAdminItems() {
        String sql = """
            SELECT i.item_id, i.seller_id, COALESCE(seller.username, '') AS seller_username,
                   COALESCE(category.name, 'Uncategorized') AS category_name,
                   i.name, i.description, i.starting_price, i.status, i.created_at,
                   a.auction_id, a.status AS auction_status, a.current_price,
                   COALESCE(bid_counts.bid_count, 0) AS bid_count
            FROM items i
            LEFT JOIN users seller ON seller.user_id = i.seller_id
            LEFT JOIN item_categories category ON category.category_id = i.category_id
            LEFT JOIN auctions a ON a.item_id = i.item_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bids
                GROUP BY auction_id
            ) bid_counts ON bid_counts.auction_id = a.auction_id
            ORDER BY i.created_at DESC, i.item_id DESC
            """;

        send("ADMIN_ITEMS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                send("ADMIN_ITEM " + fields(
                        rs.getInt("item_id"),
                        rs.getInt("seller_id"),
                        rs.getString("seller_username"),
                        rs.getString("category_name"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("starting_price"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        nullableInt(rs, "auction_id"),
                        rs.getString("auction_status"),
                        rs.getBigDecimal("current_price"),
                        rs.getInt("bid_count")
                ));
            }
        } catch (SQLException e) {
            send("ADMIN_DATA_ERROR " + fields("ITEMS", e.getMessage()));
            e.printStackTrace();
        }
        send("ADMIN_ITEMS_END");
    }

    private void sendAdminAuctions() {
        String sql = """
            SELECT a.auction_id, a.item_id, COALESCE(i.name, '') AS item_name,
                   a.seller_id, COALESCE(seller.username, '') AS seller_username,
                   a.current_price, COALESCE(bid_counts.bid_count, 0) AS bid_count,
                   a.status, a.start_time, a.end_time,
                   COALESCE(winner.username, '') AS winner_username
            FROM auctions a
            LEFT JOIN items i ON i.item_id = a.item_id
            LEFT JOIN users seller ON seller.user_id = a.seller_id
            LEFT JOIN users winner ON winner.user_id = a.current_winner_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bids
                GROUP BY auction_id
            ) bid_counts ON bid_counts.auction_id = a.auction_id
            ORDER BY a.created_at DESC, a.auction_id DESC
            """;

        send("ADMIN_AUCTIONS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                send("ADMIN_AUCTION " + fields(
                        rs.getInt("auction_id"),
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getInt("seller_id"),
                        rs.getString("seller_username"),
                        rs.getBigDecimal("current_price"),
                        rs.getInt("bid_count"),
                        rs.getString("status"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getString("winner_username")
                ));
            }
        } catch (SQLException e) {
            send("ADMIN_DATA_ERROR " + fields("AUCTIONS", e.getMessage()));
            e.printStackTrace();
        }
        send("ADMIN_AUCTIONS_END");
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String fields(Object... values) {
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            encoded.add(encodeField(value));
        }
        return String.join("|", encoded);
    }

    private String encodeField(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}