package server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import server.common.ProtocolConstants;
import server.common.entity.Notification;
import server.common.entity.User;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.common.enums.NotificationType;
import server.common.model.AuctionDTO;
import server.network.ClientManager;
import server.repository.AccountDAO;
import server.repository.AuctionDAO;
import server.repository.AutoBidConfigDAO;
import server.repository.BidTransactionDAO;
import server.repository.ItemDAO;
import server.repository.PaymentDAO;
import server.repository.WalletDAO;
import server.repository.WalletTransactionDAO;
import server.common.model.BidHistoryDTO;
import server.service.AdminService;
import server.service.AuctionService;
import server.service.ItemService;
import server.service.NotificationService;
import server.service.PaymentService;
import server.service.ServerAuthService;

public class ClientHandler implements Runnable{
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

    // DAOs
    private final AccountDAO accountDAO          = new AccountDAO();
    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAO();
    private final AuctionDAO auctionDAO          = new AuctionDAO();
    private final AutoBidConfigDAO autoBidConfigDAO   = new AutoBidConfigDAO();
    private final WalletDAO walletDAO            = new WalletDAO();
    private final WalletTransactionDAO walletTransactionDAO = new WalletTransactionDAO();
    private final PaymentDAO paymentDAO          = new PaymentDAO();
    private final NotificationService notificationService  = new NotificationService();

    // Services
    private final ServerAuthService authService;
    private final ItemService itemService;
    private final AuctionService auctionService;
    private final PaymentService paymentService;
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

    /** Ủy quyền kiểm tra admin cho AccountDAO (SRP). */
    private boolean isActiveAdmin(int userId) {
        return accountDAO.isActiveAdmin(userId);
    }


    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallbackValue;
        }
    }

    /** Lấy danh sách user cho Admin panel — gọi AccountDAO (SRP). */
    private void sendAdminUsers() {
        send("ADMIN_USERS_BEGIN");
        try {
            List<AccountDAO.AdminUserRow> rows = accountDAO.getAdminUserRows();
            for (AccountDAO.AdminUserRow row : rows) {
                send("ADMIN_USER " + fields(
                    row.userId(),
                    row.username(),
                    row.email(),
                    row.fullName(),
                    row.phone(),
                    row.role(),
                    row.status(),
                    row.isActive(),
                    row.lastLogin(),
                    row.createdAt(),
                    row.itemCount(),
                    row.runningAuctionCount(),
                    row.bidCount()
                ));
            }
        } catch (Exception e) {
            send("ADMIN_DATA_ERROR " + fields("USERS", e.getMessage()));
            e.printStackTrace();
        }
        send("ADMIN_USERS_END");
    }

    /** Lấy danh sách auction cho Admin panel — gọi AuctionDAO (SRP). */
    private void sendAdminAuctions() {
        send("ADMIN_AUCTIONS_BEGIN");
        try {
            List<AuctionDAO.AdminAuctionRow> rows = auctionDAO.getAdminAuctionRows();
            for (AuctionDAO.AdminAuctionRow row : rows) {
                send("ADMIN_AUCTION " + fields(
                    String.valueOf(row.auctionId()),
                    String.valueOf(row.itemId()),
                    row.itemName(),
                    String.valueOf(row.sellerId()),
                    row.sellerUsername(),
                    row.currentPrice(),
                    row.bidCount(),
                    row.status(),
                    row.startTime(),
                    row.endTime(),
                    row.winnerUsername()
                ));
            }
        } catch (Exception e) {
            send("ADMIN_DATA_ERROR " + fields("AUCTIONS", e.getMessage()));
            e.printStackTrace();
        }
        send("ADMIN_AUCTIONS_END");
    }

    /** Lấy danh sách auction đang mở cho User dashboard — gọi AuctionDAO (SRP). */
    private void sendUserAuctions() {
        send("USER_AUCTIONS_BEGIN");
        try {
            List<AuctionDAO.UserAuctionRow> rows = auctionDAO.getUserAuctionRows();
            for (AuctionDAO.UserAuctionRow row : rows) {
                send("USER_AUCTION " + fields(
                    row.auctionId(),
                    row.itemId(),
                    row.itemName(),
                    row.categoryName(),
                    row.description(),
                    row.currentPrice(),
                    row.minBidIncrement(),
                    row.reservePrice(),
                    row.bidCount(),
                    row.displayStatus(),
                    row.startTime(),
                    row.endTime(),
                    row.secondsLeft(),
                    row.sellerUsername(),
                    row.winnerUsername(),
                    row.imageUrls(),
                    row.attributeLines(),
                    row.snipeWindowSeconds(),
                    row.snipeExtensionSeconds()
                ));
            }
        } catch (Exception e) {
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
     * Gửi danh sách auto-bid của user — gọi AutoBidConfigDAO (SRP).
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
        try {
            List<AutoBidConfigDAO.UserAutoBidRow> rows = autoBidConfigDAO.getUserAutoBidRows(targetUserId);
            for (AutoBidConfigDAO.UserAutoBidRow row : rows) {
                send("USER_AUTOBID " + fields(
                    row.autoBidId(),
                    row.auctionId(),
                    row.itemId(),
                    row.itemName(),
                    row.categoryName(),
                    row.currentPrice(),
                    row.maxBid(),
                    row.increment(),
                    row.status(),
                    row.endTime(),
                    row.sellerUsername(),
                    row.imageUrls(),
                    row.secondsLeft()
                ));
            }
        } catch (Exception e) {
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
     * Gửi lịch sử giao dịch của user — gọi PaymentDAO + WalletDAO + WalletTransactionDAO (SRP).
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
        try {
            BigDecimal currentWalletBalance = walletDAO.getBalanceByUserId(targetUserId);
            send("WALLET_UPDATE|" + targetUserId + "|" + currentWalletBalance.toPlainString());

            Map<Integer, BigDecimal> balanceAfterByTx =
                walletTransactionDAO.buildBalanceAfterMap(targetUserId, currentWalletBalance);

            List<PaymentDAO.UserTransactionRow> rows =
                paymentDAO.getUserTransactionRows(targetUserId);

            for (PaymentDAO.UserTransactionRow row : rows) {
                BigDecimal balanceAfter = balanceAfterByTx.get(row.walletTxId());
                send("USER_TRANSACTION " + fields(
                    row.paymentId(),
                    row.auctionId(),
                    row.userRole(),
                    row.itemName(),
                    row.counterpartId(),
                    row.counterpartName(),
                    row.amount(),
                    row.paymentStatus(),
                    row.auctionStatus(),
                    row.createdAt(),
                    row.paidAt(),
                    row.walletTxId(),
                    row.walletTxType(),
                    row.walletNote(),
                    balanceAfter == null ? "" : balanceAfter
                ));
            }
        } catch (Exception e) {
            send("USER_TRANSACTIONS_ERROR " + fields(e.getMessage()));
            e.printStackTrace();
        }
        send("USER_TRANSACTIONS_END");
    }

    public String resolvePaymentCompletionFailure(int auctionId) {
        PaymentDAO.PaymentWalletInfo info = paymentDAO.getPaymentWalletInfo(auctionId);
        if (info == null) {
            return "PAYMENT_NOT_FOUND";
        }
        if (info.buyerWalletId() == null || info.buyerWalletId() <= 0) {
            return "BUYER_WALLET_NOT_FOUND";
        }
        if (info.sellerWalletId() == null || info.sellerWalletId() <= 0) {
            return "SELLER_WALLET_NOT_FOUND";
        }
        boolean insufficientBalance = info.amount() != null
            && info.buyerBalance() != null
            && info.buyerBalance().compareTo(info.amount()) < 0;
        if (insufficientBalance) {
            return "INSUFFICIENT_BALANCE";
        }
        if (info.status() != null && !"PENDING".equalsIgnoreCase(info.status())) {
            return "PAYMENT_" + info.status().toUpperCase();
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

        PaymentDAO.PaymentBuyerStatus info = paymentDAO.getPaymentBuyerStatus(auctionId);
        if (info == null) {
            return PaymentAuthorization.reject("PAYMENT_NOT_FOUND");
        }
        if (info.buyerId() != this.userId) {
            return PaymentAuthorization.reject("NOT_BUYER");
        }
        if (!"PENDING".equalsIgnoreCase(info.status())) {
            return PaymentAuthorization.reject("PAYMENT_" + info.status());
        }
        return PaymentAuthorization.allow();
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
        return auctionDAO.isOwnedBySeller(auctionId, sellerId);
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