package server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import server.common.entity.Auction;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemStatus;
import server.common.model.AuctionDTO;
import server.database.DBConnection;
import server.network.ClientManager;
import server.repository.AuctionDAO;
import server.repository.AccountDAO;
import server.repository.BidTransactionDAO;
import server.service.ItemService;
import server.service.ServerAuthService;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username = "Guest"; // Tên hiển thị mặc định
    private int userId = -1 ;

    private AccountDAO accountDAO = new AccountDAO();
    private BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
    private AuctionDAO auctionDAO = new AuctionDAO();
    private final ServerAuthService authService = new ServerAuthService();
    private final ItemService itemService = new ItemService();

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
            if(this.userId != -1){
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
        String[] request = msg.split(" ", 6);

        // Phản hồi kết nối với Client
        if ("PING".equals(request[0])){
            send("PONG");
            return;
        }
        System.out.println("MSG FROM CLIENT: " + msg);

        // Xử lý request
        switch (request[0]) {
            case "LOGIN":
                String identifier = request[1]; // Có thể là username hoặc email theo UserDAO
                String password = request[2];
                send(authService.login(request));

                // User login -> đăng ký ClientHandler support luồng của User này
                User loginUser = accountDAO.login(identifier, password);
                if (loginUser != null) {
                    this.userId = Integer.parseInt(loginUser.getId());
                    this.username = loginUser.getUsername();
                    ClientManager.add(this.userId, this);
                }
                break;

            case "REGISTER":
                // Format mong muốn: REGISTER <user> <pass> <email> <fullName> <phone>
                send(authService.register(request));
                break;
            case "CREATE_ITEM":
                // Format: CREATE_ITEM sellerId|title|description|price|status|listingFlow|size|currency|imageUris
                // The dashboard opens a separate lightweight socket for this command, so handleCreateItem()
                // will prefer this.userId when the socket is authenticated and otherwise fallback to sellerId
                // from the persisted SessionManager payload.
                handleCreateItem(msg.length() > "CREATE_ITEM".length()
                    ? msg.substring("CREATE_ITEM".length()).trim()
                    : "");
                break;

            case "USER_ITEM_STATS":
                sendUserItemStats(request.length > 1 ? request[1] : "");
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
                //✅ Lấy từ DB thay vì RAM
                List<Auction> auctions = auctionDAO.getByStatus(AuctionStatus.RUNNING);
                for (Auction a : auctions){
                    send("ITEM "+ a.getId()+ " "+ a.getCurrentPrice());
                }
                break;

            case "BID":
                if (request.length < 3) {
                    send("FAIL INVALID_BID_FORMAT");
                    return;
                }
                try {
                    int auctionId = Integer.parseInt(request[1]);
                    BigDecimal amount = new BigDecimal(request[2]);

                    // ✅ Gọi BidTransactionDAO.placeBid() — có Transaction + Row Locking sẵn!
                    // Không cần synchronized nữa vì BidTransactionDAO đã xử lý bằng SELECT FOR UPDATE
                    boolean ok = bidTransactionDAO.placeBid(auctionId, this.userId, amount, false);
                    if (ok) {
                        ClientManager.broadcast("NEW_BID " + this.username + " " + auctionId + " " + amount);
                    } else {
                        send("FAIL BID_TOO_LOW_OR_NOT_RUNNING");
                    }
                } catch (NumberFormatException e) {
                    send("FAIL INVALID_FORMAT");
                }
                break;

            case "MSG":
                if (msg.length() > 4) {
                    String content = String.join(" ",Arrays.copyOfRange(request,1,request.length));
                    ClientManager.broadcast("MSG " + this.username + ": " + content);
                }
                break;

            default:
                send("UNKNOWN_COMMAND");
                break;
        }
    }

    /**
     * Handles seller item creation from the inline Create Listing form.
     *
     * <p>Important flow note: sellers create items, not auction sessions. Submitted items are
     * stored with {@code PENDING_REVIEW}, then admin-home shows them in Pending Approval. Admin
     * later approves the item and creates the actual auction session.</p>
     */
    private void handleCreateItem(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 9) {
            send("CREATE_ITEM_FAIL INVALID_PAYLOAD");
            return;
        }

        int requestedSellerId = parseIntOrDefault(safeField(fields, 0), 0);
        int sellerId = this.userId > 0 ? this.userId : requestedSellerId;
        if (sellerId <= 0) {
            send("CREATE_ITEM_FAIL NOT_AUTHENTICATED");
            return;
        }

        String name = safeField(fields, 1);
        String description = safeField(fields, 2);
        BigDecimal startingPrice = parseBigDecimal(safeField(fields, 3));
        ItemStatus status = parseItemStatus(safeField(fields, 4));
        String listingFlow = safeField(fields, 5);
        String size = safeField(fields, 6);
        String currency = safeField(fields, 7);
        String imagePayload = safeField(fields, 8);

        if (name.isBlank() || startingPrice == null || startingPrice.compareTo(BigDecimal.ZERO) < 0) {
            send("CREATE_ITEM_FAIL INVALID_DATA");
            return;
        }

        List<String> imageUrls = new ArrayList<>();
        if (!imagePayload.isBlank()) {
            for (String image : imagePayload.split("\n")) {
                if (image != null && !image.isBlank()) {
                    imageUrls.add(image.trim());
                }
            }
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("listing_flow", listingFlow.isBlank() ? "Timed Auction" : listingFlow);
        if (!size.isBlank()) attributes.put("size_condition", size);
        if (!currency.isBlank()) attributes.put("currency", currency);

        int itemId = itemService.createItem(sellerId, null, name, description, startingPrice, status, imageUrls, attributes);
        if (itemId > 0) {
            send("CREATE_ITEM_SUCCESS " + fields(itemId, status.name()));
            if (status == ItemStatus.PENDING_REVIEW) {
                // Ask online admin dashboards to reload ADMIN_LIST_ITEMS so the new item appears in Pending Approval.
                ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
            }
        } else {
            send("CREATE_ITEM_FAIL SAVE_ERROR");
        }
    }

    /**
     * Decodes pipe-delimited payloads produced by UserDashboardController#encodeField.
     */
    private List<String> splitPayload(String payload) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < payload.length(); i++) {
            char character = payload.charAt(i);

            if (escaped) {
                switch (character) {
                    case 'p' -> current.append('|');
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    case '\\' -> current.append('\\');
                    default -> current.append(character);
                }
                escaped = false;
                continue;
            }

            if (character == '\\') {
                escaped = true;
                continue;
            }

            if (character == '|') {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(character);
        }

        if (escaped) {
            current.append('\\');
        }

        fields.add(current.toString());
        return fields;
    }

    private String safeField(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ItemStatus parseItemStatus(String value) {
        try {
            return value == null || value.isBlank()
                ? ItemStatus.PENDING_REVIEW
                : ItemStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ItemStatus.PENDING_REVIEW;
        }
    }

    /**
     * Sends seller listing counters used by the My Items statistic cards.
     *
     * <p>Counts are based on real item statuses in the cloud DB, not the demo values in the UI.</p>
     */
    private void sendUserItemStats(String sellerIdText) {
        int sellerId = this.userId > 0 ? this.userId : parseIntOrDefault(sellerIdText, 0);
        if (sellerId <= 0) {
            send("USER_ITEM_STATS " + fields(0, 0, 0, 0));
            return;
        }

        String sql = """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END) AS drafts,
                   SUM(CASE WHEN status = 'IN_AUCTION' THEN 1 ELSE 0 END) AS active_sales,
                   SUM(CASE WHEN status = 'SOLD' THEN 1 ELSE 0 END) AS sold
            FROM items
            WHERE seller_id = ?
            """;

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    send("USER_ITEM_STATS " + fields(
                        rs.getInt("total"),
                        rs.getInt("drafts"),
                        rs.getInt("active_sales"),
                        rs.getInt("sold")
                    ));
                    return;
                }
            }
        } catch (SQLException e) {
            send("USER_ITEM_STATS " + fields(0, 0, 0, 0));
            e.printStackTrace();
        }
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
            FROM bid_transactions
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
                   last_login,
                   created_at
            FROM accounts
            ORDER BY created_at DESC, user_id DESC
            """;

        send("ADMIN_USERS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int userId = rs.getInt("user_id");
                String statusStr = rs.getString("status");
                String isActiveStr = "ACTIVE".equals(statusStr) ? "true" : "false";

                send("ADMIN_USER " + fields(
                    userId,
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("role"),
                    statusStr,
                    isActiveStr,
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
                   COALESCE(i.category, 'Uncategorized') AS category_name,
                   i.name, i.description, i.starting_price, i.status, i.created_at,
                   a.auction_id, a.status AS auction_status, a.current_price,
                   COALESCE(bid_counts.bid_count, 0) AS bid_count
            FROM items i
            LEFT JOIN accounts seller ON seller.user_id = i.seller_id
            LEFT JOIN auctions a ON a.item_id = i.item_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bid_transactions
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
            LEFT JOIN accounts seller ON seller.user_id = a.seller_id
            LEFT JOIN accounts winner ON winner.user_id = a.current_winner_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bid_transactions
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
                    String.valueOf(rs.getInt("auction_id")),
                    String.valueOf(rs.getInt("item_id")),
                    rs.getString("item_name"),
                    String.valueOf(rs.getInt("seller_id")),
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