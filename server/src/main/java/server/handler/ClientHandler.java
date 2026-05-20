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
import server.service.AdminService;
import server.service.AuctionService;
import server.service.ItemService;
import server.service.ServerAuthService;

public class ClientHandler implements Runnable {
    private static final int GLOBAL_USER_ID = -1;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Infor Account connect với Server
    private String username = "Guest"; // Tên hiển thị mặc định
    private int userId = GLOBAL_USER_ID ;   // Default UserID

    // DAOs (Refactor sau không cho DAO vào Handler)
    private BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
    private AuctionDAO auctionDAO = new AuctionDAO();

    // Services
    private final ServerAuthService authService = new ServerAuthService();
    private final ItemService itemService = new ItemService();
    private final AuctionService auctionService = new AuctionService();
    private final AdminService adminService = new AdminService(auctionService);
    private final ItemCommandHandler itemCommandHandler = new ItemCommandHandler(this, itemService, auctionService);

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
        } catch (IOException e) {
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
                // Format: LOGIN <indentifier> <password>
                String identifier = request[1]; // Có thể là username hoặc email theo UserDAO
                String password = request[2];
                send(authService.login(request));

                // User login -> đăng ký ClientHandler cho ClientManager support luồng của User này
                User loginUser = authService.getUser(identifier,password);
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
                itemCommandHandler.handleCreateItem(msg.length() > "CREATE_ITEM".length()
                    ? msg.substring("CREATE_ITEM".length()).trim()
                    : "", this.userId);
                break;

            case "USER_ITEM_STATS":
                itemCommandHandler.sendUserItemStats(request.length > 1 ? request[1] : "", this.userId);
                break;

            case "ADMIN_LIST_USERS":
                sendAdminUsers();
                break;

            case "ADMIN_LIST_ITEMS":
                itemCommandHandler.sendAdminItems();
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

            case "ADMIN_BAN_USER":
                // Format: ADMIN_BAN_USER <UserID> <Reason>
                if (request.length > 2) {
                    int targetUserId = Integer.parseInt(request[1]);
                    // Tạo 1 Array từ request[2] đến hết + nối các element trong Array bởi " " => String reason
                    String reason = String.join(" ", Arrays.copyOfRange(request, 2, request.length));
                    boolean banUserSuccess = adminService.banUser(this.userId, targetUserId, reason);
                    send(banUserSuccess ? "ADMIN_BAN_SUCCESS" : "ADMIN_BAN_FAIL");
                }
                break;

            case "ADMIN_UNBAN_USER":
                if (request.length > 1) {
                    int targetUserId = Integer.parseInt(request[1]);
                    boolean unbanUserSuccess = adminService.unbanUser(this.userId, targetUserId);
                    send(unbanUserSuccess ? "ADMIN_UNBAN_SUCCESS" : "ADMIN_UNBAN_FAIL");
                } else {
                    send("ADMIN_UNBAN_FAIL INVALID_FORMAT");
                }
                break;

            case "ADMIN_FORCE_CLOSE":
                if (request.length > 2) {
                    String auctionId = request[1];
                    String reason = String.join(" ", Arrays.copyOfRange(request, 2, request.length));
                    boolean forceCloseSuccess = adminService.forceCloseAuction(this.userId, auctionId, reason);
                    send(forceCloseSuccess ? "ADMIN_CLOSE_SUCCESS" : "ADMIN_CLOSE_FAIL");
                }
                break;

            case "ADMIN_APPROVE_ITEM":
                itemCommandHandler.approveItem(request.length > 1 ? request[1] : "", this.userId);
                break;

            case "ADMIN_REJECT_ITEM":
                itemCommandHandler.rejectItem(
                    request.length > 1 ? request[1] : "",
                    request.length > 2 ? String.join(" ", Arrays.copyOfRange(request, 2, request.length)) : "",
                    this.userId
                );
                break;
            default:
                send("UNKNOWN_COMMAND");
                break;
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