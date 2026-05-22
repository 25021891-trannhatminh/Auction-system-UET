package server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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
import server.repository.WalletDAO;
import server.service.AdminService;
import server.service.AuctionService;
import server.service.ItemService;
import server.service.PaymentService;
import server.service.ServerAuthService;

public class ClientHandler implements Runnable {
    private static final int GLOBAL_USER_ID = -1;
    private static final DateTimeFormatter AUCTION_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final BidHandler bidHandler;

    // Infor Account connect với Server
    private String username = "Guest"; // Tên hiển thị mặc định
    private int userId = GLOBAL_USER_ID ;   // Default UserID

    // DAOs (Refactor sau không cho DAO vào Handler)
    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final WalletDAO walletDAO = new WalletDAO();

    // Services
    private final ServerAuthService authService;
    private final ItemService itemService;
    private final AuctionService auctionService;
    private final PaymentService paymentService;
    private final AdminService adminService;
    private final ItemCommandHandler itemCommandHandler;

    /**
     * CONSTRUCTOR ĐÃ ĐƯỢC CHỈNH SỬA:
     * Nhận thực thể auctionService chung từ AuctionServer để đồng bộ bộ nhớ RAM
     */
    public ClientHandler(Socket socket, AuctionService auctionService) {
        this.socket         = socket;
        this.auctionService = auctionService;
        this.authService    = new ServerAuthService();
        this.itemService    = new ItemService();
        this.adminService   = new AdminService(auctionService);
        this.paymentService = new PaymentService();
        this.itemCommandHandler = new ItemCommandHandler(this, itemService, auctionService);
        this.bidHandler = new BidHandler(auctionService);

        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(),  StandardCharsets.UTF_8));
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

            case "CONFIRM_PAYMENT": {
                // Format: CONFIRM_PAYMENT <auctionId> <itemName...>
                //
                // PaymentService xử lý toàn bộ trong 1 DB transaction:
                //   lock wallets → withdraw buyer → deposit seller
                //   → log wallet_transactions → update payments → COMMIT
                // Sau commit: push PUSH_NOTIF + WALLET_UPDATE realtime về buyer và seller.
                //
                // Sau khi PaymentService thành công, gọi auctionService.confirmPayment()
                // để đồng bộ RAM + DB auction status → PAID.
                if (request.length < 2) {
                    send("PAYMENT_FAIL INVALID_FORMAT");
                    break;
                }
                try {
                    int    auctionId = Integer.parseInt(request[1]);
                    String itemName  = request.length > 2
                        ? String.join(" ", Arrays.copyOfRange(request, 2, request.length))
                        : "Auction #" + auctionId;

                    boolean ok = paymentService.processPayment(auctionId, itemName);
                    if (ok) {
                        send("PAYMENT_SUCCESS " + auctionId);
                        // Đồng bộ RAM + DB auction status → PAID
                        auctionService.confirmPayment(auctionId);
                    } else {
                        send("PAYMENT_FAIL " + auctionId);
                    }
                } catch (NumberFormatException e) {
                    send("PAYMENT_FAIL INVALID_FORMAT");
                }
                break;
            }

            case "REFUND_PAYMENT": {
                // Format: REFUND_PAYMENT <auctionId> <itemName...>
                if (request.length < 2) {
                    send("REFUND_FAIL INVALID_FORMAT");
                    break;
                }
                try {
                    int    auctionId = Integer.parseInt(request[1]);
                    String itemName  = request.length > 2
                        ? String.join(" ", Arrays.copyOfRange(request, 2, request.length))
                        : "Auction #" + auctionId;

                    boolean ok = paymentService.refundPayment(auctionId, itemName);
                    send(ok ? "REFUND_SUCCESS " + auctionId : "REFUND_FAIL " + auctionId);
                } catch (NumberFormatException e) {
                    send("REFUND_FAIL INVALID_FORMAT");
                }
                break;
            }

            case "CREATE_ITEM":
                itemCommandHandler.handleCreateItem(msg.length() > "CREATE_ITEM".length()
                    ? msg.substring("CREATE_ITEM".length()).trim()
                    : "", this.userId);
                break;

            case "USER_ITEM_STATS":
                itemCommandHandler.sendUserItemStats(request.length > 1 ? request[1] : "", this.userId);
                break;

            case "USER_LIST_ITEMS":
                itemCommandHandler.sendUserItems(request.length > 1 ? request[1] : "", this.userId);
                break;

            case "USER_UPDATE_DRAFT_ITEM":
                itemCommandHandler.handleUpdateDraftItem(msg.length() > "USER_UPDATE_DRAFT_ITEM".length()
                    ? msg.substring("USER_UPDATE_DRAFT_ITEM".length()).trim()
                    : "", this.userId);
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

            case "USER_LIST_AUCTIONS":
                sendUserAuctions();
                break;

            case "ADMIN_CREATE_AUCTION":
                handleAdminCreateAuction(msg.length() > "ADMIN_CREATE_AUCTION".length()
                    ? msg.substring("ADMIN_CREATE_AUCTION".length()).trim()
                    : "");
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
                String result = bidHandler.handleBid(request[1],request[2],this.userId,this.username);
                send(result);
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

    private void handleAdminCreateAuction(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 8) {
            send("ADMIN_CREATE_AUCTION_FAIL " + fields("INVALID_PAYLOAD"));
            return;
        }
        if (!isActiveAdmin(this.userId)) {
            send("ADMIN_CREATE_AUCTION_FAIL " + fields("NOT_ADMIN"));
            return;
        }

        int itemId = parseIntOrDefault(safeField(fields, 0), 0);
        int sellerId = parseIntOrDefault(safeField(fields, 1), 0);
        LocalDateTime startTime = parseAuctionTime(safeField(fields, 2));
        LocalDateTime endTime = parseAuctionTime(safeField(fields, 3));
        BigDecimal minimumBidIncrement = parseBigDecimal(safeField(fields, 4));
        BigDecimal reservePrice = parseNullableBigDecimal(safeField(fields, 5));
        int snipeWindowSeconds = parseIntOrDefault(safeField(fields, 6), -1);
        int snipeExtensionSeconds = parseIntOrDefault(safeField(fields, 7), -1);

        String validationError = validateCreateAuctionRequest(
            itemId,
            sellerId,
            startTime,
            endTime,
            minimumBidIncrement,
            reservePrice,
            snipeWindowSeconds,
            snipeExtensionSeconds
        );
        if (!validationError.isBlank()) {
            send("ADMIN_CREATE_AUCTION_FAIL " + fields(validationError));
            return;
        }

        CreateAuctionResult result = insertAuctionFromAvailableItem(
            itemId,
            sellerId,
            startTime,
            endTime,
            minimumBidIncrement,
            reservePrice,
            snipeWindowSeconds,
            snipeExtensionSeconds
        );

        if (result.success) {
            send("ADMIN_CREATE_AUCTION_SUCCESS " + fields(result.auctionId, itemId));
            ClientManager.broadcast("ADMIN_ITEMS_DIRTY");
            ClientManager.broadcast("USER_AUCTIONS_DIRTY");
            return;
        }
        send("ADMIN_CREATE_AUCTION_FAIL " + fields(result.message));
    }

    private String validateCreateAuctionRequest(
        int itemId,
        int sellerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal minimumBidIncrement,
        BigDecimal reservePrice,
        int snipeWindowSeconds,
        int snipeExtensionSeconds) {
        if (itemId <= 0 || sellerId <= 0) {
            return "item_id hoặc seller_id không hợp lệ";
        }
        if (startTime == null || endTime == null) {
            return "start_time/end_time phải đúng format yyyy-MM-dd HH:mm:ss";
        }
        if (!endTime.isAfter(startTime)) {
            return "end_time phải sau start_time";
        }
        if (minimumBidIncrement == null || minimumBidIncrement.compareTo(BigDecimal.ZERO) < 0) {
            return "min_bid_increment phải là số không âm";
        }
        if (reservePrice != null && reservePrice.compareTo(BigDecimal.ZERO) < 0) {
            return "reserve_price phải để trống hoặc là số không âm";
        }
        if (snipeWindowSeconds < 0 || snipeExtensionSeconds < 0) {
            return "snipe_window_seconds và snipe_extension_seconds phải không âm";
        }
        return "";
    }

    private CreateAuctionResult insertAuctionFromAvailableItem(
        int itemId,
        int sellerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal minimumBidIncrement,
        BigDecimal reservePrice,
        int snipeWindowSeconds,
        int snipeExtensionSeconds) {
        String selectSql = """
            SELECT seller_id, starting_price, status
            FROM items
            WHERE item_id = ?
            FOR UPDATE
            """;
        String insertSql = """
            INSERT INTO auctions (
                item_id, seller_id, start_time, end_time,
                min_bid_increment, reserve_price,
                snipe_window_seconds, snipe_extension_seconds,
                current_price, current_winner_id, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'OPEN')
            """;
        String updateSql = "UPDATE items SET status = 'IN_AUCTION' WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal startingPrice;
                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setInt(1, itemId);
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return CreateAuctionResult.fail("ITEM_NOT_FOUND");
                        }
                        int databaseSellerId = rs.getInt("seller_id");
                        if (databaseSellerId != sellerId) {
                            conn.rollback();
                            return CreateAuctionResult.fail("SELLER_MISMATCH");
                        }
                        String itemStatus = rs.getString("status");
                        if (!"AVAILABLE".equalsIgnoreCase(itemStatus)) {
                            conn.rollback();
                            return CreateAuctionResult.fail("ITEM_NOT_AVAILABLE");
                        }
                        startingPrice = rs.getBigDecimal("starting_price");
                    }
                }

                int auctionId = 0;
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insertPs.setInt(1, itemId);
                    insertPs.setInt(2, sellerId);
                    insertPs.setTimestamp(3, Timestamp.valueOf(startTime));
                    insertPs.setTimestamp(4, Timestamp.valueOf(endTime));
                    insertPs.setBigDecimal(5, minimumBidIncrement);
                    if (reservePrice == null) {
                        insertPs.setNull(6, Types.DECIMAL);
                    } else {
                        insertPs.setBigDecimal(6, reservePrice);
                    }
                    insertPs.setShort(7, (short) snipeWindowSeconds);
                    insertPs.setShort(8, (short) snipeExtensionSeconds);
                    insertPs.setBigDecimal(9, startingPrice);
                    insertPs.executeUpdate();

                    try (ResultSet keys = insertPs.getGeneratedKeys()) {
                        if (keys.next()) {
                            auctionId = keys.getInt(1);
                        }
                    }
                }

                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setInt(1, itemId);
                    updatePs.executeUpdate();
                }

                conn.commit();
                return CreateAuctionResult.ok(auctionId);
            } catch (SQLException e) {
                conn.rollback();
                return CreateAuctionResult.fail(e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return CreateAuctionResult.fail(e.getMessage());
        }
    }

    private boolean isActiveAdmin(int userId) {
        if (userId <= 0) {
            return false;
        }
        String sql = "SELECT role, status FROM accounts WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                    && "ADMIN".equalsIgnoreCase(rs.getString("role"))
                    && "ACTIVE".equalsIgnoreCase(rs.getString("status"));
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private LocalDateTime parseAuctionTime(String value) {
        try {
            return value == null || value.isBlank()
                ? null
                : LocalDateTime.parse(value.trim(), AUCTION_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private BigDecimal parseNullableBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseBigDecimal(value);
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value == null || value.isBlank()
                ? null
                : new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
        }
    }

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

    private void sendUserAuctions() {
        String sql = """
            SELECT a.auction_id,
                   a.item_id,
                   COALESCE(i.name, '') AS item_name,
                   COALESCE(i.category, '') AS category_name,
                   COALESCE(i.description, '') AS description,
                   a.current_price,
                   a.min_bid_increment,
                   a.reserve_price,
                   COALESCE(bid_counts.bid_count, 0) AS bid_count,
                   CASE
                       WHEN a.status IN ('OPEN', 'RUNNING') AND NOW() >= a.end_time THEN 'FINISHED'
                       WHEN a.status = 'OPEN' AND NOW() >= a.start_time THEN 'RUNNING'
                       ELSE a.status
                   END AS display_status,
                   a.start_time,
                   a.end_time,
                   GREATEST(TIMESTAMPDIFF(SECOND, NOW(), a.end_time), 0) AS seconds_left,
                   COALESCE(seller.username, '') AS seller_username,
                   COALESCE(winner.username, '') AS winner_username,
                   COALESCE(imgs.image_urls, '') AS image_urls,
                   COALESCE(attrs.attribute_lines, '') AS attribute_lines,
                   a.snipe_window_seconds,
                   a.snipe_extension_seconds
            FROM auctions a
            LEFT JOIN items i ON i.item_id = a.item_id
            LEFT JOIN accounts seller ON seller.user_id = a.seller_id
            LEFT JOIN accounts winner ON winner.user_id = a.current_winner_id
            LEFT JOIN (
                SELECT auction_id, COUNT(*) AS bid_count
                FROM bid_transactions
                GROUP BY auction_id
            ) bid_counts ON bid_counts.auction_id = a.auction_id
            LEFT JOIN (
                SELECT item_id,
                       GROUP_CONCAT(url ORDER BY is_primary DESC, sort_order ASC, image_id ASC
                                    SEPARATOR '\n') AS image_urls
                FROM item_images
                GROUP BY item_id
            ) imgs ON imgs.item_id = a.item_id
            LEFT JOIN (
                SELECT item_id,
                       GROUP_CONCAT(CONCAT(attr_key, ': ', attr_value) ORDER BY attr_id ASC
                                    SEPARATOR '\n') AS attribute_lines
                FROM item_attributes
                GROUP BY item_id
            ) attrs ON attrs.item_id = a.item_id
            WHERE a.status IN ('OPEN', 'RUNNING')
              AND a.end_time > NOW()
            ORDER BY a.end_time ASC, a.auction_id DESC
            """;

        send("USER_AUCTIONS_BEGIN");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                send("USER_AUCTION " + fields(
                    rs.getInt("auction_id"),
                    rs.getInt("item_id"),
                    rs.getString("item_name"),
                    rs.getString("category_name"),
                    rs.getString("description"),
                    rs.getBigDecimal("current_price"),
                    rs.getBigDecimal("min_bid_increment"),
                    rs.getBigDecimal("reserve_price"),
                    rs.getInt("bid_count"),
                    rs.getString("display_status"),
                    rs.getTimestamp("start_time"),
                    rs.getTimestamp("end_time"),
                    rs.getLong("seconds_left"),
                    rs.getString("seller_username"),
                    rs.getString("winner_username"),
                    rs.getString("image_urls"),
                    rs.getString("attribute_lines"),
                    rs.getInt("snipe_window_seconds"),
                    rs.getInt("snipe_extension_seconds")
                ));
            }
        } catch (SQLException e) {
            send("USER_AUCTIONS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_AUCTIONS_END");
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
    private static final class CreateAuctionResult {
        private final boolean success;
        private final int auctionId;
        private final String message;

        private CreateAuctionResult(boolean success, int auctionId, String message) {
            this.success = success;
            this.auctionId = auctionId;
            this.message = message;
        }

        private static CreateAuctionResult ok(int auctionId) {
            return new CreateAuctionResult(true, auctionId, "");
        }

        private static CreateAuctionResult fail(String message) {
            return new CreateAuctionResult(false, 0, message == null ? "SAVE_ERROR" : message);
        }
    }

}