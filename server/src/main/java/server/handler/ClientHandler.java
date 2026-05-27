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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import server.common.ProtocolConstants;
import server.common.entity.Auction;
import server.common.entity.Item;
import server.common.entity.Notification;
import server.common.entity.User;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.enums.ItemStatus;
import server.common.enums.NotificationType;
import server.common.model.AuctionDTO;
import server.database.DBConnection;
import server.network.ClientManager;
import server.repository.AuctionDAO;
import server.repository.BidTransactionDAO;
import server.repository.ItemDAO;
import server.common.model.BidHistoryDTO;
import server.service.AdminService;
import server.service.AuctionService;
import server.service.ItemService;
import server.service.NotificationService;
import server.service.PaymentService;
import server.service.ServerAuthService;
import server.service.listeners.RealTimeObserver;

public class ClientHandler implements Runnable{
//    private static final int GLOBAL_USER_ID = -1;
    private static final DateTimeFormatter AUCTION_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LOG_MESSAGE_LIMIT = 500;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final BidHandler bidHandler;
    private final AutoBidHandler autoBidHandler;
    private final AuthHandler authHandler;
    private final AdminHandler adminHandler;

    // Infor Account connect với Server
    private String username = "Guest"; // Tên hiển thị mặc định
    private int userId = ProtocolConstants.NOTIFICATION_GLOBAL_USER_ID ;   // Default UserID

    // DAOs (Refactor sau không cho DAO vào Handler)
    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemDAO itemDAO = new ItemDAO();
    private final NotificationService notificationService = new NotificationService();

    // Services
    private final ServerAuthService authService;
    private final ItemService itemService;
    private final AuctionService auctionService;
    private final PaymentService paymentService;
    private final AdminService adminService;
    private final ItemCommandHandler itemCommandHandler;
    private final AuctionHandler auctionHandler;
    private final AuctionVisualisationHandler auctionVisualisationHandler;

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
        this.autoBidHandler = new AutoBidHandler(auctionService);
        this.authHandler = new AuthHandler();
        this.adminHandler = new AdminHandler(auctionService);
        this.auctionHandler = new AuctionHandler(AuctionManager.getInstance());
        this.auctionVisualisationHandler = new AuctionVisualisationHandler();

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

    /**
     * Builds a compact log entry for incoming protocol messages. Upload requests contain large
     * Base64 image data, so only the command, file name and payload length are printed.
     *
     * @param msg raw message received from the client
     * @return safe message text for server logs
     */
    private String safeMessageForLog(String msg) {
        if (msg == null) {
            return "";
        }

        if (msg.startsWith("UPLOAD_IMAGE ")) {
            String[] parts = msg.split(" ", 3);
            String fileName = parts.length > 1 ? parts[1] : "unknown";
            int base64Length = parts.length > 2 ? parts[2].length() : 0;
            return "UPLOAD_IMAGE " + fileName + " <base64:" + base64Length + " chars>";
        }

        if (msg.length() > LOG_MESSAGE_LIMIT) {
            return msg.substring(0, LOG_MESSAGE_LIMIT)
                + "... <truncated, " + msg.length() + " chars>";
        }

        return msg;
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
            if(this.userId != ProtocolConstants.NOTIFICATION_GLOBAL_USER_ID){
                ClientManager.remove(this.userId);
                auctionHandler.handleDisconnect(userId);
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
        String[] request = msg.split(" ");
        String contextRequest = request[0];// Lấy mã lệnh đầu tiên (LOGIN, REGISTER, BID,...)

        // Phản hồi kết nối với Client
        if (ProtocolConstants.PING.equals(contextRequest)){
            send(ResponseBuilder.pong());
            return;
        }
        System.out.println("MSG FROM CLIENT: " + safeMessageForLog(msg));

        // Xử lý request
        switch (contextRequest) {
            case ProtocolConstants.LOGIN:
                String loginResult = authHandler.handleLogin(request, this);
                send(loginResult);
                // Đồng bộ và lưu giữ trạng thái authenticated user vào bên trong Connection Handler
                if (loginResult.startsWith(ProtocolConstants.LOGIN_SUCCESS)) {
                    try {
                        String[] parts = loginResult.split(" ");
                        String[] fields = parts[1].split("\\|");
                        int loggedInUserId = Integer.parseInt(fields[0]);
                        // Sử dụng identifier và password đã có sẵn trong request LOGIN
                        String identifier = request[1];
                        String password = request[2];
                        User loginUser = authService.getUser(identifier, password); // Lấy đối tượng User đầy đủ
                        if (loginUser != null) {
                            auctionService.registerOrGetUser(loginUser); // Đảm bảo user có trong AuctionManager
                        } else {
                            System.err.println("Không thể lấy thông tin User từ DB sau khi login thành công, userId=" + loggedInUserId);
                        }
                    } catch (Exception e) {
                        System.err.println("Lỗi phân tách chuỗi khi xử lý trạng thái LOGIN: " + e.getMessage());
                    }
                }
                break;
            case ProtocolConstants.REGISTER:
                String registerResult = authHandler.handleRegister(request);
                send(registerResult);
                break;

            case ProtocolConstants.JOIN_AUCTION:
                // 1. Thực hiện logic join phòng ban đầu của bạn
                String joinResponse = auctionHandler.handleJoin(request, this.userId);
                send(joinResponse);

                // 2. KÍCH HOẠT HÀM BIỂU ĐỒ TẠI ĐÂY
                // Nếu join phòng thành công (Chuỗi trả về bắt đầu bằng JOIN_SUCCESS)
                if (joinResponse != null && joinResponse.startsWith("AUCTION_SNAPSHOT")) {
                    try {
                        int auctionId = -1;
                        // Bóc tách lấy mã phòng (auctionId) từ chuỗi phản hồi thực tế (nằm ở vị trí index 1 sau dấu |)
                        if (joinResponse.contains("|")) {
                            String[] snapshotParts = joinResponse.split("\\|");
                            if (snapshotParts.length > 1) {
                                auctionId = Integer.parseInt(snapshotParts[1]);
                            }
                        } else {
                            // Trường hợp dự phòng nếu mảng request từ Client gửi lên chứa ID phòng dạng số
                            auctionId = Integer.parseInt(request[1]);
                        }

                        // Kích hoạt gửi dữ liệu lịch sử giá xuống cho Client vẽ biểu đồ
                        if (auctionId != -1) {
                            sendChartHistory(auctionId);
                            System.out.println("[SUCCESS] Đã kích hoạt và truyền dữ liệu biểu đồ cho phòng: " + auctionId);
                        }
                    } catch (Exception e) {
                        System.err.println("Lỗi bóc tách ID phòng đấu giá để nạp biểu đồ: " + e.getMessage());
                    }
                }
                break;

            case ProtocolConstants.LEAVE_AUCTION:
                // Format: LEAVE_AUCTION <AuctionID>
                String leaveResult = auctionHandler.handleLeave(request, this.userId);
                send(leaveResult);
                break;

            case "CONFIRM_PAYMENT": {
                new PaymentCommandHandler(this, paymentService).handleConfirmPayment(request);
                break;
            }

            case "REFUND_PAYMENT": {
                new PaymentCommandHandler(this, paymentService).handleRefundPayment(request);
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
                if (!isActiveAdmin(this.userId)) {
                    send("ADMIN_DATA_ERROR NOT_ADMIN");
                    break;
                }
                sendAdminUsers();
                break;

            case "ADMIN_LIST_ITEMS":
                if (!isActiveAdmin(this.userId)) { send("ADMIN_DATA_ERROR NOT_ADMIN"); break; }
                itemCommandHandler.sendAdminItems();
                break;

            case "ADMIN_LIST_AUCTIONS":
                if (!isActiveAdmin(this.userId)) { send("ADMIN_DATA_ERROR NOT_ADMIN"); break;}
                sendAdminAuctions();
                break;

            case "USER_LIST_AUCTIONS":
                sendUserAuctions();
                break;

            case "USER_LIST_BIDS":
                sendUserBids(request);
                break;

            case "USER_LIST_AUTOBIDS":
                sendUserAutoBids(request);
                break;

            case "USER_LIST_TRANSACTIONS":
                sendUserTransactions(request);
                break;

            case "DEPOSIT_WALLET":
                handleWalletDeposit(request);
                break;

            case "SELLER_AUCTION_BIDS":
                sendSellerAuctionBids(request);
                break;

            case ProtocolConstants.GET_AUCTION_VISUALISATION:
                send(auctionVisualisationHandler.handle(request, this.userId));
                break;

            case "USER_LIST_NOTIFICATIONS":
                sendUserNotifications(request);
                break;

            case "USER_MARK_NOTIFICATIONS_READ":
                markUserNotificationsRead(request);
                break;

            case "ADMIN_CREATE_AUCTION":
                if (!isActiveAdmin(this.userId)) {
                    send("ADMIN_CREATE_AUCTION_FAIL NOT_ADMIN");
                    break;
                }
                String payload = msg.length() > "ADMIN_CREATE_AUCTION".length() ? msg.substring("ADMIN_CREATE_AUCTION".length()).trim() : "";
                send(adminHandler.handleAdminCreateAuction(payload, this.userId));
                break;


            case "LIST":
                List<AuctionDTO> auctions = auctionDAO.getByStatus(AuctionStatus.RUNNING);
                for (AuctionDTO a : auctions){
                    send("ITEM "+ a.getAuctionId()+ " "+ a.getCurrentPrice());
                }
                break;

            case ProtocolConstants.BID:
                String bidReponse = bidHandler.handleBid(request, this.userId, this.username);
                send(bidReponse);
                if (bidReponse != null && bidReponse.startsWith(ProtocolConstants.BID_SUCCESS)) {
                    ClientManager.broadcast("USER_AUCTIONS_DIRTY");
                }
                break;

            case ProtocolConstants.AUTOBID_REGISTER:
                String autoBidRegisterResponse = autoBidHandler.handleAutoBidRegister(request, this.userId);
                send(autoBidRegisterResponse);
                if (autoBidRegisterResponse != null
                    && autoBidRegisterResponse.startsWith(ProtocolConstants.AUTOBID_SUCCESS)) {
                    ClientManager.broadcast("USER_AUCTIONS_DIRTY");
                }
                break;

            case ProtocolConstants.AUTOBID_CANCEL:
                String autoBidCancelResponse = autoBidHandler.handleAutoBidCancel(request, this.userId);
                send(autoBidCancelResponse);
                if (autoBidCancelResponse != null
                    && autoBidCancelResponse.startsWith(ProtocolConstants.AUTOBID_SUCCESS)) {
                    ClientManager.broadcast("USER_AUCTIONS_DIRTY");
                }
                break;

            case "MSG":
                if (msg.length() > 4) {
                    String content = String.join(" ",Arrays.copyOfRange(request,1,request.length));
                    ClientManager.broadcast("MSG " + this.username + ": " + content);
                }
                break;

            case ProtocolConstants.ADMIN_BAN_USER:
                String banResult = adminHandler.handleBanUser(request, this.userId);
                send(banResult);
                break;

            case ProtocolConstants.ADMIN_UNBAN_USER:
                if (!isActiveAdmin(this.userId)) {
                    send("ADMIN_UNBAN_FAIL NOT_ADMIN");
                    break;
                }
                send(adminHandler.handleUnbanUser(request, this.userId));
                break;

            case "ADMIN_FORCE_CLOSE":
                if (!isActiveAdmin(this.userId)) {
                    send("ADMIN_CLOSE_FAIL NOT_ADMIN");
                    break;
                }
                send(adminHandler.handleForceClose(request, this.userId));
                break;

            case "ADMIN_APPROVE_ITEM":
                if (!isActiveAdmin(this.userId)) { send("ADMIN_APPROVE_FAIL NOT_ADMIN"); break; }
                itemCommandHandler.approveItem(request.length > 1 ? request[1] : "", this.userId);
                break;

            case "ADMIN_REJECT_ITEM":
                if (!isActiveAdmin(this.userId)) { send("ADMIN_REJECT_FAIL NOT_ADMIN"); break; }
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


    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
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

    /**
     * Resolves a user-scoped request using the authenticated socket user first.
     *
     * <p>The fallback argument keeps the existing dashboard flow working after reconnects where
     * queued read-only requests still include the session user id in their payload.</p>
     */
    private int resolveUserScopedRequest(String[] request, int argumentIndex) {
        if (this.userId > 0) {
            return this.userId;
        }
        if (request != null && request.length > argumentIndex) {
            try {
                int parsedUserId = Integer.parseInt(request[argumentIndex]);
                return parsedUserId > 0 ? parsedUserId : -1;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private int parsePositiveInt(String value, int fallbackValue) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallbackValue;
        } catch (NumberFormatException exception) {
            return fallbackValue;
        }
    }

    /**
     * Sends persisted auto-bid rules for the current user.
     *
     * <p>Protocol payload matches the user dashboard auto-bid table:
     * autoBidId|auctionId|itemId|itemName|category|currentPrice|maxBid|increment|status|
     * endTime|sellerUsername|imageUrls|secondsLeft.</p>
     */
    private void sendUserAutoBids(String[] request) {
        int targetUserId = resolveUserScopedRequest(request, 1);
        send("USER_AUTOBIDS_BEGIN");
        if (targetUserId <= 0) {
            send("USER_AUTOBIDS_ERROR " + fields("NOT_LOGGED_IN"));
            send("USER_AUTOBIDS_END");
            return;
        }

        String sql = """
            SELECT cfg.auto_bid_id,
                   cfg.auction_id,
                   a.item_id,
                   COALESCE(i.name, '') AS item_name,
                   COALESCE(i.category, '') AS category_name,
                   a.current_price,
                   cfg.max_bid,
                   cfg.increment,
                   cfg.status,
                   a.end_time,
                   COALESCE(seller.username, '') AS seller_username,
                   COALESCE(imgs.image_urls, '') AS image_urls,
                   GREATEST(TIMESTAMPDIFF(SECOND, NOW(), a.end_time), 0) AS seconds_left
            FROM auto_bid_configs cfg
            JOIN auctions a ON a.auction_id = cfg.auction_id
            LEFT JOIN items i ON i.item_id = a.item_id
            LEFT JOIN accounts seller ON seller.user_id = a.seller_id
            LEFT JOIN (
                SELECT item_id,
                       GROUP_CONCAT(url ORDER BY is_primary DESC, sort_order ASC, image_id ASC
                                    SEPARATOR '\n') AS image_urls
                FROM item_images
                GROUP BY item_id
            ) imgs ON imgs.item_id = a.item_id
            WHERE cfg.bidder_id = ?
            ORDER BY CASE cfg.status
                         WHEN 'ACTIVE' THEN 0
                         WHEN 'COMPLETED' THEN 1
                         ELSE 2
                     END,
                     a.end_time ASC, cfg.updated_at DESC
            """;

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    send("USER_AUTOBID " + fields(
                        rs.getInt("auto_bid_id"),
                        rs.getInt("auction_id"),
                        rs.getInt("item_id"),
                        rs.getString("item_name"),
                        rs.getString("category_name"),
                        rs.getBigDecimal("current_price"),
                        rs.getBigDecimal("max_bid"),
                        rs.getBigDecimal("increment"),
                        rs.getString("status"),
                        rs.getTimestamp("end_time"),
                        rs.getString("seller_username"),
                        rs.getString("image_urls"),
                        rs.getLong("seconds_left")
                    ));
                }
            }
        } catch (SQLException e) {
            send("USER_AUTOBIDS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_AUTOBIDS_END");
    }

    private void handleWalletDeposit(String[] request) {
        int targetUserId = this.userId > 0 ? this.userId : -1;
        int amountIndex = 1;
        if (request != null && request.length >= 3) {
            targetUserId = parsePositiveInt(request[1], targetUserId);
            amountIndex = 2;
        }

        if (targetUserId <= 0) {
            send("DEPOSIT_FAIL NOT_LOGGED_IN");
            return;
        }
        if (request == null || request.length <= amountIndex) {
            send("DEPOSIT_FAIL INVALID_FORMAT");
            return;
        }

        try {
            BigDecimal amount = new BigDecimal(request[amountIndex].replace(",", "").trim());
            BigDecimal newBalance = paymentService.depositToWallet(targetUserId, amount);
            if (newBalance == null) {
                send("DEPOSIT_FAIL DEPOSIT_NOT_COMPLETED");
                return;
            }
            send("DEPOSIT_SUCCESS " + fields(amount, newBalance));
            ClientManager.broadcast("USER_TRANSACTIONS_DIRTY");
        } catch (NumberFormatException exception) {
            send("DEPOSIT_FAIL INVALID_AMOUNT");
        }
    }

    /**
     * Sends post-auction payment rows and standalone wallet rows visible to the current user.
     *
     * <p>The mutation path still goes through PaymentService; this method only exposes the
     * persisted payments/wallet transaction state to the Transactions screen.</p>
     */
    private void sendUserTransactions(String[] request) {
        int targetUserId = resolveUserScopedRequest(request, 1);
        send("USER_TRANSACTIONS_BEGIN");
        if (targetUserId <= 0) {
            send("USER_TRANSACTIONS_ERROR " + fields("NOT_LOGGED_IN"));
            send("USER_TRANSACTIONS_END");
            return;
        }

        String sql = """
            SELECT *
            FROM (
                SELECT p.payment_id,
                       p.auction_id,
                       CASE WHEN p.buyer_id = ? THEN 'BUYER' ELSE 'SELLER' END AS user_role,
                       COALESCE(i.name, CONCAT('Auction #', p.auction_id)) AS item_name,
                       CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END AS counterpart_id,
                       COALESCE(counterpart.username,
                                CONCAT('User #', CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END))
                                AS counterpart_name,
                       p.amount,
                       p.status AS payment_status,
                       a.status AS auction_status,
                       p.created_at,
                       p.paid_at,
                       COALESCE(wt.tx_id, 0) AS wallet_tx_id,
                       COALESCE(wt.type, '') AS wallet_tx_type,
                       COALESCE(wt.note, '') AS wallet_note
                FROM payments p
                JOIN auctions a ON a.auction_id = p.auction_id
                LEFT JOIN items i ON i.item_id = a.item_id
                LEFT JOIN accounts counterpart ON counterpart.user_id =
                    CASE WHEN p.buyer_id = ? THEN p.seller_id ELSE p.buyer_id END
                LEFT JOIN (
                    SELECT wt1.tx_id, wt1.user_id, wt1.ref_auction_id, wt1.type, wt1.note
                    FROM wallet_transactions wt1
                    JOIN (
                        SELECT user_id, ref_auction_id, MAX(tx_id) AS latest_tx_id
                        FROM wallet_transactions
                        WHERE type IN ('PAYMENT', 'REFUND')
                        GROUP BY user_id, ref_auction_id
                    ) latest ON latest.latest_tx_id = wt1.tx_id
                ) wt ON wt.ref_auction_id = p.auction_id AND wt.user_id = ?
                WHERE p.buyer_id = ? OR p.seller_id = ?

                UNION ALL

                SELECT 0 AS payment_id,
                       COALESCE(wt.ref_auction_id, 0) AS auction_id,
                       'WALLET' AS user_role,
                       CASE
                           WHEN wt.type = 'DEPOSIT' THEN 'Wallet top-up'
                           WHEN wt.ref_auction_id IS NULL THEN 'Wallet'
                           ELSE COALESCE(i.name, CONCAT('Auction #', wt.ref_auction_id))
                       END AS item_name,
                       0 AS counterpart_id,
                       'Wallet' AS counterpart_name,
                       wt.amount,
                       'COMPLETED' AS payment_status,
                       'WALLET' AS auction_status,
                       wt.created_at,
                       wt.created_at AS paid_at,
                       wt.tx_id AS wallet_tx_id,
                       wt.type AS wallet_tx_type,
                       COALESCE(wt.note, '') AS wallet_note
                FROM wallet_transactions wt
                LEFT JOIN auctions a ON a.auction_id = wt.ref_auction_id
                LEFT JOIN items i ON i.item_id = a.item_id
                WHERE wt.user_id = ?
                  AND wt.type IN ('DEPOSIT', 'WITHDRAW')
            ) tx
            ORDER BY tx.created_at DESC, tx.payment_id DESC, tx.wallet_tx_id DESC
            """;

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            BigDecimal currentWalletBalance = loadCurrentWalletBalance(conn, targetUserId);
            send("WALLET_UPDATE|" + targetUserId + "|" + currentWalletBalance.toPlainString());

            Map<Integer, BigDecimal> balanceAfterByTx = loadWalletBalanceAfterByTx(
                conn,
                targetUserId,
                currentWalletBalance
            );

            ps.setInt(1, targetUserId);
            ps.setInt(2, targetUserId);
            ps.setInt(3, targetUserId);
            ps.setInt(4, targetUserId);
            ps.setInt(5, targetUserId);
            ps.setInt(6, targetUserId);
            ps.setInt(7, targetUserId);
            ps.setInt(8, targetUserId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int walletTxId = rs.getInt("wallet_tx_id");
                    BigDecimal balanceAfter = balanceAfterByTx.get(walletTxId);
                    send("USER_TRANSACTION " + fields(
                        rs.getInt("payment_id"),
                        rs.getInt("auction_id"),
                        rs.getString("user_role"),
                        rs.getString("item_name"),
                        rs.getInt("counterpart_id"),
                        rs.getString("counterpart_name"),
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_status"),
                        rs.getString("auction_status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("paid_at"),
                        walletTxId,
                        rs.getString("wallet_tx_type"),
                        rs.getString("wallet_note"),
                        balanceAfter == null ? "" : balanceAfter
                    ));
                }
            }
        } catch (SQLException e) {
            send("USER_TRANSACTIONS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_TRANSACTIONS_END");
    }

    /**
     * Builds display-only wallet balances after each persisted wallet transaction.
     * The current wallet balance remains the source of truth; the method walks the audit log
     * backwards and never mutates payment or wallet domain state.
     */
    private Map<Integer, BigDecimal> loadWalletBalanceAfterByTx(
            Connection conn,
            int targetUserId,
            BigDecimal currentWalletBalance
    ) throws SQLException {
        Map<Integer, BigDecimal> balanceAfterByTx = new HashMap<>();
        BigDecimal runningBalance = currentWalletBalance == null
            ? BigDecimal.ZERO
            : currentWalletBalance;
        String sql = """
            SELECT tx_id, type, amount, note
            FROM wallet_transactions
            WHERE user_id = ?
            ORDER BY created_at DESC, tx_id DESC
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int txId = rs.getInt("tx_id");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal delta = walletDisplayDelta(
                        rs.getString("type"),
                        amount == null ? BigDecimal.ZERO : amount,
                        rs.getString("note")
                    );
                    balanceAfterByTx.put(txId, runningBalance);
                    runningBalance = runningBalance.subtract(delta);
                }
            }
        }
        return balanceAfterByTx;
    }

    private BigDecimal loadCurrentWalletBalance(Connection conn, int targetUserId) throws SQLException {
        String sql = "SELECT balance FROM wallets WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    return balance == null ? BigDecimal.ZERO : balance;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal walletDisplayDelta(String type, BigDecimal amount, String note) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase();
        String normalizedNote = note == null ? "" : note.trim().toLowerCase();
        return switch (normalizedType) {
            case "DEPOSIT", "RELEASE" -> amount;
            case "WITHDRAW", "HOLD" -> amount.negate();
            case "PAYMENT" -> normalizedNote.startsWith("received") ? amount : amount.negate();
            case "REFUND" -> normalizedNote.contains("received") ? amount : amount.negate();
            default -> BigDecimal.ZERO;
        };
    }

    public String resolvePaymentCompletionFailure(int auctionId) {
        String sql = """
            SELECT p.status,
                   p.amount,
                   bw.wallet_id AS buyer_wallet_id,
                   bw.balance AS buyer_balance,
                   sw.wallet_id AS seller_wallet_id
            FROM payments p
            LEFT JOIN wallets bw ON bw.user_id = p.buyer_id
            LEFT JOIN wallets sw ON sw.user_id = p.seller_id
            WHERE p.auction_id = ?
            """;
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "PAYMENT_NOT_FOUND";
                }

                int buyerWalletId = rs.getInt("buyer_wallet_id");
                if (rs.wasNull() || buyerWalletId <= 0) {
                    return "BUYER_WALLET_NOT_FOUND";
                }
                int sellerWalletId = rs.getInt("seller_wallet_id");
                if (rs.wasNull() || sellerWalletId <= 0) {
                    return "SELLER_WALLET_NOT_FOUND";
                }

                BigDecimal amount = rs.getBigDecimal("amount");
                BigDecimal buyerBalance = rs.getBigDecimal("buyer_balance");
                String status = rs.getString("status");
                boolean insufficientBalance = amount != null
                    && buyerBalance != null
                    && buyerBalance.compareTo(amount) < 0;
                if (insufficientBalance) {
                    return "INSUFFICIENT_BALANCE";
                }
                if (status != null && !"PENDING".equalsIgnoreCase(status)) {
                    return "PAYMENT_" + status.toUpperCase();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "PAYMENT_DB_ERROR";
        }
        return "PAYMENT_NOT_COMPLETED";
    }

    public String commandSafeText(String value) {
        return value == null ? "" : value.replaceAll("[\r\n\t]+", " ").trim();
    }

    public PaymentAuthorization resolvePaymentAuthorization(int auctionId) {
        if (this.userId <= 0) {
            return PaymentAuthorization.reject("NOT_LOGGED_IN");
        }

        String sql = "SELECT buyer_id, status FROM payments WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PaymentAuthorization.reject("PAYMENT_NOT_FOUND");
                }
                if (rs.getInt("buyer_id") != this.userId) {
                    return PaymentAuthorization.reject("NOT_BUYER");
                }
                String status = rs.getString("status");
                if (!"PENDING".equalsIgnoreCase(status)) {
                    return PaymentAuthorization.reject("PAYMENT_" + status);
                }
                return PaymentAuthorization.allow();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return PaymentAuthorization.reject("PAYMENT_NOT_FOUND");
        }
    }

    /**
     * Sends persisted notification history for the current user.
     *
     * <p>Protocol payload:
     * USER_NOTIFICATION notifId|type|title|content|isRead|createdAt. The client already parses
     * this structure for the notification center, so the history survives app restarts.</p>
     */
    private void sendUserNotifications(String[] request) {
        int targetUserId = resolveUserScopedRequest(request, 1);
        send("USER_NOTIFICATIONS_BEGIN");
        if (targetUserId <= 0) {
            send("USER_NOTIFICATIONS_END");
            return;
        }

        try {
            List<Notification> notifications = notificationService.getAllForUser(targetUserId);
            for (Notification notification : notifications) {
                send("USER_NOTIFICATION " + fields(
                    notification.getNotifId(),
                    notification.getType() == null ? NotificationType.SYSTEM.name()
                        : notification.getType().name(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.isRead(),
                    notification.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            send("USER_NOTIFICATIONS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_NOTIFICATIONS_END");
    }

    /**
     * Marks all notifications of the current user as read in the database.
     */
    private void markUserNotificationsRead(String[] request) {
        int targetUserId = resolveUserScopedRequest(request, 1);
        if (targetUserId <= 0) {
            send("USER_MARK_NOTIFICATIONS_READ_FAIL " + fields("NOT_LOGGED_IN"));
            return;
        }
        int updated = notificationService.markAllRead(targetUserId);
        send("USER_MARK_NOTIFICATIONS_READ_SUCCESS " + fields(updated));
    }

    /**
     * Sends bid history for an auction owned by the current seller.
     *
     * <p>Protocol payload matches the seller view in UserDashboardController:
     * auctionId|bidId|bidderId|bidderName|amount|status|isAutoBid|bidTime.</p>
     */
    private void sendSellerAuctionBids(String[] request) {
        int auctionId = request != null && request.length > 1
            ? parseIntOrDefault(request[1], 0)
            : 0;
        send("SELLER_AUCTION_BIDS_BEGIN " + auctionId);

        if (auctionId <= 0 || this.userId <= 0) {
            send("SELLER_AUCTION_BIDS_ERROR " + fields(auctionId, "NOT_AUTHORIZED"));
            send("SELLER_AUCTION_BIDS_END " + auctionId);
            return;
        }
        if (!isAuctionOwnedByUser(auctionId, this.userId)) {
            send("SELLER_AUCTION_BIDS_ERROR " + fields(auctionId, "NOT_OWNER"));
            send("SELLER_AUCTION_BIDS_END " + auctionId);
            return;
        }

        try {
            List<BidTransactionDAO.AuctionBidHistoryRow> rows =
                bidTransactionDAO.getAuctionBidHistoryRows(auctionId);
            for (BidTransactionDAO.AuctionBidHistoryRow row : rows) {
                send("SELLER_AUCTION_BID " + fields(
                    auctionId,
                    row.getBidId(),
                    row.getBidderId(),
                    row.getBidderName(),
                    row.getAmount(),
                    row.getStatus(),
                    row.isAutoBid(),
                    row.getBidTime()
                ));
            }
        } catch (Exception e) {
            send("SELLER_AUCTION_BIDS_ERROR " + fields(auctionId, e.getMessage()));
            e.printStackTrace();
        }
        send("SELLER_AUCTION_BIDS_END " + auctionId);
    }

    /**
     * Checks that the authenticated seller owns the requested auction.
     */
    private boolean isAuctionOwnedByUser(int auctionId, int sellerId) {
        String sql = "SELECT seller_id FROM auctions WHERE auction_id = ?";
        try (Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("seller_id") == sellerId;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends the latest bid row for each auction the current user has joined.
     *
     * <p>Protocol payload matches UserDashboardController.parseMyBid():
     * bidId|auctionId|itemId|itemName|category|currentPrice|userBid|bidStatus|auctionStatus|
     * isAutoBid|bidTime|endTime|currentWinnerId|imageUrl.</p>
     */
    private void sendUserBids(String[] request) {
        int targetUserId = resolveUserScopedRequest(request, 1);
        send("USER_BIDS_BEGIN");
        if (targetUserId <= 0) {
            send("USER_BIDS_ERROR " + fields("NOT_LOGGED_IN"));
            send("USER_BIDS_END");
            return;
        }

        try {
            List<BidTransactionDAO.UserBidRow> bidRows =
                bidTransactionDAO.getLatestBidsByBidder(targetUserId);
            for (BidTransactionDAO.UserBidRow row : bidRows) {
                send("USER_BID " + fields(
                    row.getBidId(),
                    row.getAuctionId(),
                    row.getItemId(),
                    row.getItemName(),
                    row.getCategory(),
                    row.getCurrentPrice(),
                    row.getUserBid(),
                    row.getBidStatus() == null ? "" : row.getBidStatus().name(),
                    row.getAuctionStatus(),
                    row.isAutoBid(),
                    row.getBidTime(),
                    row.getEndTime(),
                    row.getCurrentWinnerId(),
                    row.getImageUrl()
                ));
            }
        } catch (Exception e) {
            send("USER_BIDS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_BIDS_END");
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
    public record PaymentAuthorization(boolean allowed, String reason) {
        static PaymentAuthorization allow() {
            return new PaymentAuthorization(true, "");
        }

        static PaymentAuthorization reject(String reason) {
            return new PaymentAuthorization(false, reason == null ? "PAYMENT_NOT_COMPLETED" : reason);
        }
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

    // ==================== REALTIME BID EVENTS ====================
    // Implement RealTimeObserver — nhận event từ Auction sau khi DB commit xong.
    // Chỉ push command realtime về client qua socket, không xử lý business logic.
//    @Override
//    public void onBidPlacedSuccess(int bidderId, int auctionId, String itemName, BigDecimal amount) {
//        // Command realtime để UI cập nhật giá, lịch sử bid
//        send(String.format("BID_PLACED|%d|%d|%s|%s",
//            auctionId, bidderId, itemName, amount.toPlainString()));
//    }
//
//    @Override
//    public void onTimeExtended(int auctionId, String itemName, int addedSeconds) {
//        send(String.format("TIME_EXTENDED|%d|%s|%d",
//            auctionId, itemName, addedSeconds));
//    }
//
//    @Override
//    public void onAuctionEnded(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
//        send(String.format("AUCTION_ENDED|%d|%d|%s|%s",
//            auctionId, winnerId, itemName, finalPrice.toPlainString()));
//    }
//
//    @Override
//    public void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice) {
//
//    }
    /**
     * Tự động gửi dữ liệu lịch sử giá xuống cho Client vẽ biểu đồ.
     * Sử dụng trực tiếp tài nguyên nội bộ out và bidTransactionDAO của lớp.
     */
    public void sendChartHistory(int auctionId) {
        try {
            out.println(ResponseBuilder.historyStart());
            List<BidHistoryDTO> historyList = bidTransactionDAO.getBidHistory(auctionId);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            if (historyList != null) {
                for (BidHistoryDTO bid : historyList) {
                    String formattedTime = sdf.format(bid.getBidTime());
                    out.println(ResponseBuilder.historyItem(formattedTime, bid.getAmount()));
                }

            }
            out.println(server.handler.ResponseBuilder.historyEnd());
            out.flush();
        } catch (final Exception e) {
            System.err.println("Lỗi Checkstyle/Đồng bộ luồng dữ liệu biểu đồ: " + e.getMessage());
        }
    }
    public void setUserId(int userId){
        this.userId = userId;
    }
    public void setUsername(String username){
        this.username = username;
    }
    public int getUserId(){
        return userId;
    }
    public String getUsername(){
        return username;
    }


}