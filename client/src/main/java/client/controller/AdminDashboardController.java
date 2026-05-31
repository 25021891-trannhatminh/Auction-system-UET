package client.controller;


import client.enums.AccountStatus;
import client.enums.SystemRole;
import client.model.NotificationModel;
import client.model.User;
import client.service.NetworkManager;
import client.service.SessionManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Controller for the administrator dashboard and moderation workspace.
 */
public class AdminDashboardController extends BaseDashboardController {

    @FXML private FlowPane adminActionBar;
    @FXML private VBox dataRowsBox;
    @FXML private Label tableTitleLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Button primaryActionButton;

    private static final double ACTION_PRIMARY_WIDTH = 88;
    private static final double ACTION_MORE_WIDTH = 28;
    private static final double ITEM_ACTION_PRIMARY_WIDTH = 158;
    private static final double ITEM_ACTION_MORE_WIDTH = 30;
    private static final double ACTION_GAP = 6;
    private static final double REVIEW_IMAGE_HEIGHT = 265;
    private static final double AUCTION_IMAGE_HEIGHT = 310;
    private static final double PREVIEW_IMAGE_WIDTH_RATIO = 1.55;
    private static final double PREVIEW_CARD_HORIZONTAL_PADDING = 28;
    private static final double THUMB_SIZE = 58;
    private static final int ADMIN_ROWS_PER_PAGE = 6;
    private static final Pattern MONEY_PATTERN = Pattern.compile("^[0-9]+(?:\\.[0-9]{1,2})?$");
    private static final DateTimeFormatter AUCTION_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal MAX_AUCTION_MONEY = new BigDecimal("9999999999999.99");
    private static final BigDecimal MIN_INCREMENT = new BigDecimal("1.00");
    private static final int MAX_AUCTION_SECONDS = 32767;
    private static final int MAX_MONEY_INPUT_LENGTH = 16;
    private static final int MAX_SECONDS_INPUT_LENGTH = 5;
    private static final int MAX_AUCTION_DURATION_DAYS = 365;

    private final Map<String, SectionContent> sections = AdminDashboardSections.buildSections();
    private final List<AdminRow> liveUserRows = new ArrayList<>();
    private final List<AdminRow> liveItemRows = new ArrayList<>();
    private final List<AdminRow> liveAuctionRows = new ArrayList<>();
    private final Map<String, Image> adminImageCache = new HashMap<>();
    private final Set<String> pendingAdminActions = new HashSet<>();

    private List<AdminRow> incomingUserRows = new ArrayList<>();
    private List<AdminRow> incomingItemRows = new ArrayList<>();
    private List<AdminRow> incomingAuctionRows = new ArrayList<>();

    private boolean usersLoaded;
    private boolean itemsLoaded;
    private boolean auctionsLoaded;
    private String currentSectionKey = "dashboard";
    private String activeFilter = "All";
    private int dashboardPage = 1;
    private int usersPage = 1;
    private int auctionsPage = 1;
    private int itemsPage = 1;

    private NetworkManager adminNetworkManager;
    private Consumer<String> adminNetworkHandler;

    @FXML
    @Override
    protected void initialize() {
        super.initialize();
        setupAdminNetworkListener();

        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                String query = newValue == null ? "" : newValue.trim();
                activeFilter = query.isEmpty() ? getDefaultFilter(currentSectionKey) : query;
                resetPageForSection(currentSectionKey);
                renderWorkspace(currentSectionKey, activeFilter);
            });
        }

        if (dataRowsBox != null) {
            dataRowsBox.setFillWidth(true);
        }

        hideAdminDescriptions();
        requestNotificationHistory();
        requestLiveAdminData();
    }

    @Override
    protected Map<String, SectionContent> createSections() {
        return sections;
    }

    @Override
    protected String getDefaultSectionKey() {
        return "dashboard";
    }

    @Override
    protected String getRoleTitle() {
        return "SYSTEM ADMIN";
    }

    @Override
    protected boolean shouldDisplayNotification(NotificationModel notification) {
        if (notification == null || notification.getType() == null) {
            return false;
        }
        String type = notification.getType().trim().toUpperCase();
        return type.equals("SYSTEM")
            || type.equals("ADMIN")
            || type.equals("ADMIN_REVIEW")
            || type.equals("ADMIN_ACTION");
    }

    @Override
    protected void showSection(String sectionKey) {
        currentSectionKey = sectionKey;
        activeFilter = getDefaultFilter(sectionKey);
        resetPageForSection(sectionKey);

        String currentSearch = searchField == null ? "" : searchField.getText();
        if (currentSearch != null && !currentSearch.isBlank()) {
            searchField.clear();
        }

        super.showSection(sectionKey);
        hideAdminDescriptions();
        updatePrimaryAction(sectionKey);
        renderWorkspace(sectionKey, activeFilter);
    }

    private void hideAdminDescriptions() {
        hideLabel(headerSubtitleLabel);
        hideLabel(sectionDescriptionLabel);
        hideLabel(surfaceDescriptionLabel);
        hideLabel(rightPanelDescriptionLabel);
    }

    private void hideLabel(Label label) {
        if (label == null) {
            return;
        }
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    @Override
    protected void handleLogout() {
        unregisterAdminNetworkListener();
        super.handleLogout();
    }

    private void setupAdminNetworkListener() {
        adminNetworkManager = NetworkManager.getInstance();
        networkManager = adminNetworkManager;

        adminNetworkHandler = message -> {
            if (message == null || message.isBlank()) {
                return;
            }

            if (message.startsWith("PUSH_NOTIF|")) {
                Platform.runLater(() -> processPushNotification(message));
                return;
            }

            if (isNotificationHistoryMessage(message)) {
                Platform.runLater(() -> handleNotificationHistoryMessage(message));
                return;
            }

            if (isNotificationMarkReadResponse(message)) {
                Platform.runLater(() -> handleNotificationMarkReadResponse(message));
                return;
            }

            if (message.startsWith("ADMIN_") || message.equals("USER_AUCTIONS_DIRTY")) {
                Platform.runLater(() -> applyAdminServerMessage(message));
            }
        };

        adminNetworkManager.addMessageHandler(adminNetworkHandler);
    }

    private void unregisterAdminNetworkListener() {
        if (adminNetworkManager != null && adminNetworkHandler != null) {
            adminNetworkManager.removeMessageHandler(adminNetworkHandler);
            adminNetworkHandler = null;
        }
    }

    private void requestLiveAdminData() {
        if (adminNetworkManager == null) {
            return;
        }

        adminNetworkManager.send("ADMIN_LIST_USERS");
        adminNetworkManager.send("ADMIN_LIST_ITEMS");
        adminNetworkManager.send("ADMIN_LIST_AUCTIONS");
    }

    private void applyAdminServerMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }

        if (msg.equals("ADMIN_USERS_BEGIN")) {
            incomingUserRows = new ArrayList<>();
            return;
        }

        if (msg.startsWith("ADMIN_USER ")) {
            String payload = msg.substring("ADMIN_USER ".length());
            syncCurrentUserFromUserPayload(payload);
            AdminRow parsed = parseUserRow(payload);
            if (parsed != null) {
                incomingUserRows.add(parsed);
            }
            return;
        }

        if (msg.equals("ADMIN_USERS_END")) {
            liveUserRows.clear();
            liveUserRows.addAll(incomingUserRows);
            usersLoaded = true;
            refreshIfShowing("users");
            return;
        }

        if (msg.equals("ADMIN_ITEMS_BEGIN")) {
            incomingItemRows = new ArrayList<>();
            return;
        }

        if (msg.startsWith("ADMIN_ITEM ")) {
            AdminRow parsed = parseItemRow(msg.substring("ADMIN_ITEM ".length()));
            if (parsed != null) {
                incomingItemRows.add(parsed);
            }
            return;
        }

        if (msg.equals("ADMIN_ITEMS_END")) {
            liveItemRows.clear();
            liveItemRows.addAll(incomingItemRows);
            itemsLoaded = true;
            refreshIfShowing("items");
            refreshIfShowing("dashboard");
            return;
        }

        if (msg.equals("ADMIN_AUCTIONS_BEGIN")) {
            incomingAuctionRows = new ArrayList<>();
            return;
        }

        if (msg.startsWith("ADMIN_AUCTION ")) {
            AdminRow parsed = parseAuctionRow(msg.substring("ADMIN_AUCTION ".length()));
            if (parsed != null) {
                incomingAuctionRows.add(parsed);
            }
            return;
        }

        if (msg.equals("ADMIN_AUCTIONS_END")) {
            liveAuctionRows.clear();
            liveAuctionRows.addAll(incomingAuctionRows);
            auctionsLoaded = true;
            refreshIfShowing("auctions");
            refreshIfShowing("dashboard");
            return;
        }

        if (msg.equals("ADMIN_ITEMS_DIRTY")) {
            // CREATE_ITEM/approve/reject broadcasts: refresh item review and related auction links live.
            adminNetworkManager.send("ADMIN_LIST_ITEMS");
            adminNetworkManager.send("ADMIN_LIST_AUCTIONS");
            return;
        }

        if (msg.equals("USER_AUCTIONS_DIRTY")) {
            // Auction lifecycle/payment broadcasts are user-scoped, but admin tables also show auctions.
            adminNetworkManager.send("ADMIN_LIST_AUCTIONS");
            adminNetworkManager.send("ADMIN_LIST_ITEMS");
            return;
        }

        if (msg.equals("ADMIN_APPROVE_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess(
                "Item approved",
                "Item has been approved and is ready for auction."
            );
            showSection("items");
            showDetail(
                "Item approved",
                "Database đã chuyển item từ PENDING_REVIEW sang AVAILABLE. "
                    + "Create Auction sẽ ghi trực tiếp xuống bảng auctions khi bấm tạo phòng."
            );
            return;
        }

        if (msg.startsWith("ADMIN_APPROVE_FAIL")) {
            pendingAdminActions.clear();
            notifUIHandler.showError(
                "Approve failed",
                "Server không approve được item. Có thể item không còn ở PENDING_REVIEW."
            );
            showDetail(
                "Approve failed",
                "Server không approve được item. Có thể item không còn ở PENDING_REVIEW."
            );
            return;
        }

        if (msg.startsWith("ADMIN_CREATE_AUCTION_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess(
                "Auction created",
                "Auction room has been created successfully. Back to Items."
            );
            showSection("items");
            showDetail(
                "Auction created",
                "Đã insert auctions bằng item_id/seller_id thật và chuyển item sang IN_AUCTION."
            );
            return;
        }

        if (msg.startsWith("ADMIN_CREATE_AUCTION_FAIL")) {
            pendingAdminActions.clear();
            String errorMessage = decodeJoinedPayload(
                msg.substring("ADMIN_CREATE_AUCTION_FAIL".length()).trim()
            );
            notifUIHandler.showError("Create auction failed", errorMessage);
            showDetail("Create auction failed", errorMessage);
            return;
        }

        if (msg.equals("ADMIN_REJECT_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess("Item rejected", "The item has been removed from the review queue.");
            showSection("items");
            showDetail("Item rejected", "Server đã xử lý ADMIN_REJECT_ITEM và refresh lại Item Review.");
            return;
        }

        if (msg.startsWith("ADMIN_REJECT_FAIL")) {
            pendingAdminActions.clear();
            String reason = decodeJoinedPayload(msg.substring("ADMIN_REJECT_FAIL".length()).trim());
            notifUIHandler.showError("Reject failed", fallback(reason, "Server rejected the request."));
            showDetail("Reject failed", fallback(reason, "Server không reject được item."));
            return;
        }

        if (msg.startsWith("ADMIN_BAN_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess("User banned", "Account status has been updated by the server.");
            showDetail("User banned", "Server đã xử lý ADMIN_BAN_USER và admin-home đã refresh Users.");
            return;
        }

        if (msg.startsWith("ADMIN_BAN_FAIL")) {
            pendingAdminActions.clear();
            String reason = decodeJoinedPayload(msg.substring("ADMIN_BAN_FAIL".length()).trim());
            notifUIHandler.showError("Ban failed", fallback(reason, "Server rejected the request."));
            showDetail("Ban failed", fallback(reason, "Server không ban được user."));
            return;
        }

        if (msg.startsWith("ADMIN_UNBAN_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess("User restored", "Account has been restored to ACTIVE.");
            showDetail("User restored", "Server đã xử lý ADMIN_UNBAN_USER và admin-home đã refresh Users.");
            return;
        }

        if (msg.startsWith("ADMIN_UNBAN_FAIL")) {
            pendingAdminActions.clear();
            String reason = decodeJoinedPayload(msg.substring("ADMIN_UNBAN_FAIL".length()).trim());
            notifUIHandler.showError("Restore failed", fallback(reason, "Server rejected the request."));
            showDetail("Restore failed", fallback(reason, "Server không restore được user."));
            return;
        }

        if (msg.startsWith("ADMIN_CLOSE_SUCCESS")) {
            pendingAdminActions.clear();
            requestLiveAdminData();
            notifUIHandler.showSuccess("Auction canceled", "Auction has been canceled by the server.");
            showDetail("Auction canceled", "Server đã xử lý ADMIN_FORCE_CLOSE và refresh lại Auctions.");
            return;
        }

        if (msg.startsWith("ADMIN_CLOSE_FAIL")) {
            pendingAdminActions.clear();
            String reason = decodeJoinedPayload(msg.substring("ADMIN_CLOSE_FAIL".length()).trim());
            notifUIHandler.showError("Cancel failed", fallback(reason, "Server rejected the request."));
            showDetail("Cancel failed", fallback(reason, "Server không cancel được auction."));
            return;
        }

        if (msg.startsWith("ADMIN_DATA_ERROR ")) {
            handleAdminDataError(msg.substring("ADMIN_DATA_ERROR ".length()));
        }
    }

    private void handleAdminDataError(String payload) {
        List<String> fields = splitPayload(payload);
        String scope = fields.isEmpty() ? "DATA" : fields.get(0);
        String message = fields.size() > 1 ? fields.get(1) : decodeJoinedPayload(payload);
        AdminRow errorRow = row(
            "Cannot load " + scope.toLowerCase() + " from cloud database",
            message,
            "DB",
            "Error",
            "ERROR",
            "Server trả ADMIN_DATA_ERROR cho " + scope + ". Kiểm tra server console và db.properties.",
            "Refresh"
        );

        switch (scope.toUpperCase()) {
            case "USERS" -> {
                liveUserRows.clear();
                liveUserRows.add(errorRow);
                usersLoaded = true;
                refreshIfShowing("users");
            }
            case "ITEMS" -> {
                liveItemRows.clear();
                liveItemRows.add(errorRow);
                itemsLoaded = true;
                refreshIfShowing("items");
            }
            case "AUCTIONS" -> {
                liveAuctionRows.clear();
                liveAuctionRows.add(errorRow);
                auctionsLoaded = true;
                refreshIfShowing("auctions");
            }
            default -> showDetail("Cloud database chưa sẵn sàng", message);
        }
        showDetail("Cloud database chưa sẵn sàng", scope + " - " + message);
    }

    private void refreshIfShowing(String sectionKey) {
        if (sectionKey.equals(currentSectionKey)) {
            renderWorkspace(currentSectionKey, activeFilter);
        }
    }


    private void syncCurrentUserFromUserPayload(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 8) {
            return;
        }

        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        int userId = parseIntOrDefault(safeField(fields, 0), 0);
        String username = safeField(fields, 1);
        String email = safeField(fields, 2);
        String fullName = safeField(fields, 3);
        String phone = safeField(fields, 4);
        String role = normalizeRole(safeField(fields, 5));
        String status = normalizeUserStatus(safeField(fields, 6), safeField(fields, 7));

        boolean sameUser = (currentUser.getUserId() > 0 && currentUser.getUserId() == userId)
            || equalsIgnoreCase(currentUser.getUsername(), username)
            || (!email.isBlank() && equalsIgnoreCase(currentUser.getEmail(), email));

        if (!sameUser) {
            return;
        }

        currentUser.setUserId(userId);
        currentUser.setUsername(fallback(username, currentUser.getUsername()));
        currentUser.setEmail(fallback(email, currentUser.getEmail()));
        currentUser.setFullName(fallback(fullName, currentUser.getFullName()));
        currentUser.setPhone(fallback(phone, currentUser.getPhone()));
        currentUser.setActive(
            !"0".equals(safeField(fields, 7))
                && !"false".equalsIgnoreCase(safeField(fields, 7))
        );

        try {
            currentUser.setSystemRole(SystemRole.valueOf(role));
        } catch (IllegalArgumentException ignored) {
            // Keep the role parsed during login if DB contains an unexpected value.
        }

        try {
            currentUser.setAccountStatus(AccountStatus.valueOf(status));
        } catch (IllegalArgumentException ignored) {
            // INACTIVE is displayed in admin rows, but the client enum only has ACTIVE/SUSPENDED/BANNED.
        }

        SessionManager.setCurrentUser(currentUser);
        refreshUserMeta();
    }

    private int parseIntOrDefault(String value, int fallbackValue) {
        try {
            return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallbackValue;
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private AdminRow parseUserRow(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 13) {
            return null;
        }

        String userId = fields.get(0);
        String username = fallback(fields.get(1), "user-" + userId);
        String email = safeField(fields, 2);
        String fullName = safeField(fields, 3);
        String phone = safeField(fields, 4);
        String role = normalizeRole(safeField(fields, 5));
        String status = normalizeUserStatus(safeField(fields, 6), safeField(fields, 7));
        String lastLogin = shortTimestamp(safeField(fields, 8));
        String createdAt = shortTimestamp(safeField(fields, 9));
        String itemCount = fallback(safeField(fields, 10), "0");
        String runningCount = fallback(safeField(fields, 11), "0");
        String bidCount = fallback(safeField(fields, 12), "0");

        String meta = joinMeta(
            "ID #" + userId,
            email,
            fullName,
            phone,
            itemCount + " items",
            runningCount + " running auctions",
            bidCount + " bids"
        );

        String detail = "User ID #" + userId
            + " - username: " + username
            + " - email: " + fallback(email, "not available")
            + " - full name: " + fallback(fullName, "not available")
            + " - phone: " + fallback(phone, "not available")
            + " - created: " + fallback(createdAt, "not available")
            + ". Row này lấy trực tiếp từ bảng users trong shared cloud database.";

        return row(
            username,
            meta,
            role,
            lastLogin,
            status,
            detail,
            actionsForUser(role, status)
        );
    }

    private AdminRow parseItemRow(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 13) {
            return null;
        }

        String itemId = safeField(fields, 0);
        String sellerId = safeField(fields, 1);
        String seller = safeField(fields, 2);
        String category = fallback(safeField(fields, 3), "Uncategorized");
        String itemName = fallback(safeField(fields, 4), "Untitled item");
        String description = safeField(fields, 5);

        int statusIndex = isItemStatusValue(safeField(fields, 7)) ? 7 : 8;
        String status = safeField(fields, statusIndex);
        String createdAt = shortTimestamp(safeField(fields, statusIndex + 1));
        String auctionId = safeField(fields, statusIndex + 2);
        String auctionStatus = safeField(fields, statusIndex + 3);
        String bidCount = fallback(safeField(fields, statusIndex + 5), "0");
        String imagePayload = safeField(fields, statusIndex + 6);
        String attributes = safeField(fields, statusIndex + 7);
        String currency = extractCurrency(attributes);
        String startingPrice = formatMoney(safeField(fields, 6), currency);
        String currentPrice = formatMoney(safeField(fields, statusIndex + 4), currency);
        String auctionLink = auctionId.isBlank() ? "No auction" : "AUC-" + auctionId;

        String sellerLabel = fallback(seller, "#" + sellerId);
        String meta = "Seller " + sellerLabel
            + " - Starting " + startingPrice
            + " - Created " + fallback(createdAt, "not available")
            + (auctionId.isBlank() ? " - No auction" : " - " + auctionLink + " " + auctionStatus
            + " - " + bidCount + " bids");

        String detail = "ITEM-" + itemId
            + " - Seller ID #" + sellerId
            + ". Current auction link: " + auctionLink
            + (auctionId.isBlank() ? "." : " - current price " + currentPrice + ".")
            + " Dữ liệu review đọc trực tiếp từ items, item_images, item_attributes "
            + "và auctions trong cloud database.";

        return itemRow(
            "ITEM-" + itemId + " - " + itemName,
            meta,
            category,
            startingPrice,
            status,
            detail,
            itemId,
            sellerId,
            sellerLabel,
            itemName,
            description,
            startingPrice,
            createdAt,
            auctionId,
            auctionStatus,
            currentPrice,
            bidCount,
            imagePayload,
            attributes,
            actionsForItem(status, auctionId)
        );
    }

    private AdminRow parseAuctionRow(String payload) {
        List<String> fields = splitPayload(payload);
        if (fields.size() < 11) {
            return null;
        }

        String auctionId = fields.get(0);
        String itemId = fields.get(1);
        String itemName = fields.get(2);
        String sellerId = fields.get(3);
        String seller = fields.get(4);
        String currentPrice = formatMoney(fields.get(5));
        String bidCount = fallback(fields.get(6), "0");
        String status = fields.get(7);
        String startTime = shortTimestamp(fields.get(8));
        String endTime = shortTimestamp(fields.get(9));
        String winner = fields.get(10);

        String meta = "ITEM-" + itemId
            + " - Seller " + fallback(seller, "#" + sellerId)
            + " - Starts " + fallback(startTime, "not available")
            + " - Ends " + fallback(endTime, "not available")
            + (winner.isBlank() ? "" : " - Winner " + winner);

        String detail = "Auction AUC-" + auctionId
            + " is linked to ITEM-" + itemId
            + " (" + itemName + "). Admin-home chỉ giám sát và cancel khi cần, "
            + "không sửa bid, giá, wallet hay payment.";

        if (isForceClosableStatus(status)) {
            return row(
                "AUC-" + auctionId + " - " + itemName,
                meta,
                currentPrice,
                bidCount + " bids",
                status,
                detail,
                "Cancel"
            );
        }

        return row(
            "AUC-" + auctionId + " - " + itemName,
            meta,
            currentPrice,
            bidCount + " bids",
            status,
            detail
        );
    }


    private String[] actionsForUser(String role, String status) {
        String normalizedRole = normalize(role);
        String normalizedStatus = normalize(status);

        if (normalizedRole.equals("admin")) {
            return new String[]{};
        }

        if (normalizedStatus.equals("active")) {
            return new String[]{"Ban"};
        }

        if (normalizedStatus.equals("banned") || normalizedStatus.equals("suspended")) {
            return new String[]{"Restore"};
        }

        return new String[]{};
    }


    private String[] actionsForItem(String status, String auctionId) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus.equals("pending review")) {
            return new String[]{"Review"};
        }

        if (normalizedStatus.equals("available")) {
            return new String[]{"Create Auction"};
        }

        return new String[]{};
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

    private String decodeJoinedPayload(String payload) {
        List<String> fields = splitPayload(payload);
        return fields.isEmpty() ? payload : String.join(" - ", fields);
    }

    private String getDefaultFilter(String sectionKey) {
        return switch (sectionKey) {
            case "dashboard" -> "overview";
            default -> "all";
        };
    }

    private void updatePrimaryAction(String sectionKey) {
        if (primaryActionButton == null) {
            return;
        }

        switch (sectionKey) {
            case "users" -> configurePrimaryAction(
                "Refresh Users",
                "Refresh users",
                "Request latest account moderation data from the server."
            );
            case "auctions" -> configurePrimaryAction(
                "Refresh Auctions",
                "Refresh auctions",
                "Request latest auction monitoring data from the server."
            );
            case "items" -> configurePrimaryAction(
                "Refresh Items",
                "Refresh items",
                "Request latest item review queue data from the server."
            );
            default -> configurePrimaryAction(
                "Refresh",
                "Refresh dashboard",
                "Refresh operational queues from the server."
            );
        }
    }

    private void configurePrimaryAction(String text, String detailTitle, String detailDescription) {
        primaryActionButton.setText(text);
        primaryActionButton.setOnAction(event -> {
            if ("Refresh Users".equals(text)
                || "Refresh Items".equals(text)
                || "Refresh Auctions".equals(text)
                || "Refresh".equals(text)) {
                requestLiveAdminData();
            }
            showDetail(detailTitle, detailDescription);
        });
    }

    private void renderWorkspace(String sectionKey, String filter) {
        if (dataRowsBox == null) {
            return;
        }

        dataRowsBox.getChildren().clear();

        if (adminActionBar != null) {
            adminActionBar.getChildren().clear();
        }

        switch (sectionKey) {
            case "users" -> renderUsers(filter);
            case "auctions" -> renderAuctions(filter);
            case "items" -> renderItems(filter);
            default -> renderDashboard(filter);
        }
    }

    private void renderDashboard(String filter) {
        setTableTitle("Operational Queue");
        renderChips(filter, "Overview");
        updateDashboardStats();

        addHeader("Queue", "Count", "Signal");

        int pendingCount = itemsLoaded ? countStatus(liveItemRows, "PENDING_REVIEW") : 0;
        int runningCount = auctionsLoaded ? countStatus(liveAuctionRows, "RUNNING") : 0;
        int paymentFollowUp = auctionsLoaded ? countStatus(liveAuctionRows, "FINISHED") : 0;
        int riskUsers = usersLoaded
            ? countStatus(liveUserRows, "SUSPENDED") + countStatus(liveUserRows, "BANNED")
            : 0;

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Items need review",
            "PENDING_REVIEW seller listings waiting for admin decision",
            String.valueOf(pendingCount),
            "Review",
            "PENDING_REVIEW",
            "Open Item Review to approve valid listings or reject invalid ones.",
            "Open"
        ));
        rows.add(row(
            "Running auctions",
            "RUNNING sessions currently receiving bids",
            String.valueOf(runningCount),
            "Live",
            "RUNNING",
            "Open Auctions to supervise current price, bid count, seller, and end time.",
            "Open"
        ));
        rows.add(row(
            "Finished auctions",
            "FINISHED auctions waiting for normal payment/follow-up flow",
            String.valueOf(paymentFollowUp),
            "Payment due",
            "FINISHED",
            "Open Auctions to inspect final status. Admin-home does not manually edit payment.",
            "Open"
        ));
        rows.add(row(
            "Risk accounts",
            "SUSPENDED and BANNED accounts kept visible for audit",
            String.valueOf(riskUsers),
            "Moderate",
            "SUSPENDED",
            "Open Users to inspect risk accounts and ban active users through existing backend commands.",
            "Open"
        ));

        addFilteredRows(rows, filter);

        showDetail("Operation console", dataSourceNote() + " Admin-home chỉ giữ Item Review, Auctions và Users.");
    }


    private void renderUsers(String filter) {
        setTableTitle("User Accounts");
        renderChips(filter, "All", "Active", "Suspended", "Banned", "User", "Admin");

        List<AdminRow> rows = currentUserRows();
        updateStats(
            new String[] {
                usersLoaded ? String.valueOf(liveUserRows.size()) : "0",
                usersLoaded ? String.valueOf(countStatus(liveUserRows, "ACTIVE")) : "0",
                usersLoaded ? String.valueOf(countStatus(liveUserRows, "SUSPENDED")) : "0",
                usersLoaded ? String.valueOf(countStatus(liveUserRows, "BANNED")) : "0"
            },
            new String[]{"Total Users", "Active", "Suspended", "Banned"}
        );

        addHeader("Account", "Role", "");
        addFilteredRows(rows, filter);

        showDetail(
            "User management",
            dataSourceNote(usersLoaded) + " Ban là action thật đang map vào backend."
        );
    }


    private void renderAuctions(String filter) {
        setTableTitle("Auction Sessions");
        renderChips(filter, "All", "Open", "Running", "Finished", "Paid", "Canceled");

        List<AdminRow> rows = currentAuctionRows();
        updateStats(
            new String[] {
                auctionsLoaded ? String.valueOf(liveAuctionRows.size()) : "0",
                auctionsLoaded ? String.valueOf(countStatus(liveAuctionRows, "RUNNING")) : "0",
                auctionsLoaded ? String.valueOf(countStatus(liveAuctionRows, "OPEN")) : "0",
                auctionsLoaded ? String.valueOf(countStatus(liveAuctionRows, "FINISHED") + countStatus(liveAuctionRows, "PAID")) : "0"
            },
            new String[]{"Auctions", "Running", "Open", "Finished/Paid"}
        );

        addHeader("Auction", "Current Price", "Bids");
        addFilteredRows(rows, filter);

        showDetail(
            "Auction monitoring",
            dataSourceNote(auctionsLoaded) + " Chỉ auction OPEN được hiện Cancel; RUNNING/FINISHED/PAID/CANCELED là view-only để không đụng flow bid, payment và wallet."
        );
    }


    private void renderItems(String filter) {
        setTableTitle("Item Review");
        renderChips(
            filter,
            "All",
            "Pending Review",
            "Available",
            "In Auction",
            "Sold",
            "Removed",
            "Draft",
            "No Auction"
        );

        List<AdminRow> rows = currentItemRows();
        updateStats(
            new String[] {
                itemsLoaded ? String.valueOf(liveItemRows.size()) : "0",
                itemsLoaded ? String.valueOf(countStatus(liveItemRows, "PENDING_REVIEW")) : "0",
                itemsLoaded ? String.valueOf(countStatus(liveItemRows, "AVAILABLE")) : "0",
                itemsLoaded ? String.valueOf(countStatus(liveItemRows, "IN_AUCTION")) : "0"
            },
            new String[]{"Items", "Pending", "Available", "In Auction"}
        );

        addHeader("Item", "Category", "Starting Price", "Status", "Action");
        addFilteredRows(rows, filter);

        showDetail(
            "Item review",
            dataSourceNote(itemsLoaded) + " PENDING_REVIEW: Approve/Reject; AVAILABLE: Create Auction."
        );
    }


    private List<AdminRow> currentUserRows() {
        if (usersLoaded) {
            return new ArrayList<>(liveUserRows);
        }
        return defaultUserRows();
    }

    private List<AdminRow> currentItemRows() {
        if (itemsLoaded) {
            return new ArrayList<>(liveItemRows);
        }
        return defaultItemRows();
    }

    private List<AdminRow> pendingItemRows(List<AdminRow> rows) {
        List<AdminRow> pendingRows = new ArrayList<>();
        for (AdminRow row : rows) {
            if (isPendingItemRow(row)) {
                pendingRows.add(row);
            }
        }
        return pendingRows;
    }

    private boolean isPendingItemRow(AdminRow row) {
        return row != null
            && row.itemData
            && normalize(row.status).equals("pending review");
    }

    private List<AdminRow> currentAuctionRows() {
        if (auctionsLoaded) {
            return new ArrayList<>(liveAuctionRows);
        }
        return defaultAuctionRows();
    }

    private List<AdminRow> defaultUserRows() {
        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Loading users...",
            "Waiting for ADMIN_LIST_USERS response from server.",
            "DB",
            "Waiting",
            "LOADING",
            "Admin-home không render user giả; bảng này sẽ tự cập nhật khi server trả dữ liệu."
        ));
        return rows;
    }


    private List<AdminRow> defaultAuctionRows() {
        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Loading auctions...",
            "Waiting for ADMIN_LIST_AUCTIONS response from server.",
            "DB",
            "Waiting",
            "LOADING",
            "Admin-home không render auction giả; bảng này sẽ tự cập nhật khi server trả dữ liệu."
        ));
        return rows;
    }


    private List<AdminRow> defaultItemRows() {
        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Loading items...",
            "Waiting for ADMIN_LIST_ITEMS response from server.",
            "DB",
            "Waiting",
            "LOADING",
            "Admin-home không render item giả; bảng này sẽ tự cập nhật khi server trả dữ liệu.",
            "Refresh"
        ));
        return rows;
    }


    private void updateDashboardStats() {
        updateStats(
            new String[] {
                itemsLoaded ? String.valueOf(countStatus(liveItemRows, "PENDING_REVIEW")) : "0",
                auctionsLoaded ? String.valueOf(countStatus(liveAuctionRows, "RUNNING")) : "0",
                auctionsLoaded ? String.valueOf(countStatus(liveAuctionRows, "FINISHED")) : "0",
                usersLoaded ? String.valueOf(countStatus(liveUserRows, "SUSPENDED") + countStatus(liveUserRows, "BANNED")) : "0"
            },
            new String[]{"Pending Review", "Running Auctions", "Payment Due", "Risk Accounts"}
        );
    }


    private void updateStats(String[] values, String[] labels) {
        if (statValue1 != null && values.length > 0) {
            statValue1.setText(values[0]);
        }
        if (statValue2 != null && values.length > 1) {
            statValue2.setText(values[1]);
        }
        if (statValue3 != null && values.length > 2) {
            statValue3.setText(values[2]);
        }
        if (statValue4 != null && values.length > 3) {
            statValue4.setText(values[3]);
        }
        if (statLabel1 != null && labels.length > 0) {
            statLabel1.setText(labels[0]);
        }
        if (statLabel2 != null && labels.length > 1) {
            statLabel2.setText(labels[1]);
        }
        if (statLabel3 != null && labels.length > 2) {
            statLabel3.setText(labels[2]);
        }
        if (statLabel4 != null && labels.length > 3) {
            statLabel4.setText(labels[3]);
        }
    }

    private int countStatus(List<AdminRow> rows, String status) {
        int count = 0;
        String target = normalize(status);
        for (AdminRow row : rows) {
            if (normalize(row.status).equals(target)) {
                count++;
            }
        }
        return count;
    }

    private int totalBidCount(List<AdminRow> auctionRows) {
        int total = 0;
        for (AdminRow row : auctionRows) {
            String digits = row.secondValue == null ? "" : row.secondValue.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                try {
                    total += Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                    // Keep counter best-effort only.
                }
            }
        }
        return total;
    }

    private String dataSourceNote() {
        return usersLoaded || itemsLoaded || auctionsLoaded
            ? "Đang dùng dữ liệu server/cloud database khi có sẵn."
            : "Đang chờ server trả dữ liệu admin.";
    }


    private String dataSourceNote(boolean loaded) {
        return loaded
            ? "Đang dùng dữ liệu từ shared cloud database."
            : "Đang chờ server trả dữ liệu admin.";
    }


    private void setTableTitle(String title) {
        if (tableTitleLabel != null) {
            String safeTitle = title == null ? "" : title;
            tableTitleLabel.setText(safeTitle);
            boolean visible = !safeTitle.isBlank();
            tableTitleLabel.setVisible(visible);
            tableTitleLabel.setManaged(visible);
        }
    }

    private void renderChips(String selectedFilter, String... labels) {
        if (adminActionBar == null) {
            return;
        }

        for (String labelText : labels) {
            Button chip = new Button(labelText);
            chip.setMnemonicParsing(false);

            if (labelText.equalsIgnoreCase(selectedFilter)) {
                chip.getStyleClass().add("filter-chip-active");
            } else {
                chip.getStyleClass().add("filter-chip");
            }

            if (isDashboardTable()) {
                chip.getStyleClass().add("overview-filter-chip");
            }

            chip.setOnAction(event -> {
                activeFilter = labelText;
                resetPageForSection(currentSectionKey);
                renderWorkspace(currentSectionKey, activeFilter);
                showDetail("Filter: " + activeFilter, "Table đã được lọc theo: " + activeFilter);
            });

            adminActionBar.getChildren().add(chip);
        }
    }

    private void addHeader(String main, String first, String second) {
        addHeader(main, first, second, "Status", "Action");
    }

    private void addHeader(
        String main,
        String first,
        String second,
        String statusText,
        String actionText) {
        GridPane header = createTableGrid("data-header");

        Label mainHeader = headerLabel(main, HPos.LEFT);
        Label firstHeader = headerLabel(first, HPos.CENTER);
        Label secondHeader = headerLabel(second, HPos.CENTER);
        Label statusHeader = headerLabel(statusText, HPos.CENTER);
        Label actionHeader = headerLabel(actionText, HPos.CENTER);

        header.add(mainHeader, 0, 0);
        header.add(firstHeader, 1, 0);

        if (isDashboardTable()) {
            header.add(secondHeader, 2, 0);
            header.add(statusHeader, 3, 0);
        } else if (isUsersTable()) {
            header.add(statusHeader, 2, 0);
            header.add(actionHeader, 3, 0);
        } else {
            header.add(secondHeader, 2, 0);
            header.add(statusHeader, 3, 0);
            header.add(actionHeader, 4, 0);
        }

        dataRowsBox.getChildren().add(header);
    }

    private Label headerLabel(String text, HPos alignment) {
        Label label = new Label(text);
        label.getStyleClass().add("table-header-label");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(0);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        GridPane.setHalignment(label, alignment);
        return label;
    }

    private GridPane createTableGrid(String styleClass) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER_LEFT);
        grid.getStyleClass().add(styleClass);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setMinWidth(0);

        if (isDashboardTable()) {
            grid.getColumnConstraints().addAll(
                percentColumn(50),
                percentColumn(14),
                percentColumn(18),
                percentColumn(18)
            );
            return grid;
        }

        if (isUsersTable()) {
            grid.getColumnConstraints().addAll(
                percentColumn(52),
                percentColumn(16),
                percentColumn(16),
                percentColumn(16)
            );
            return grid;
        }

        if (isItemReviewTable()) {
            grid.getColumnConstraints().addAll(
                percentColumn(35),
                percentColumn(13),
                percentColumn(15),
                percentColumn(14),
                percentColumn(23)
            );
            return grid;
        }

        grid.getColumnConstraints().addAll(
            percentColumn(42),
            percentColumn(13),
            percentColumn(15),
            percentColumn(15),
            percentColumn(15)
        );
        return grid;
    }

    private ColumnConstraints percentColumn(double percent) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setPercentWidth(percent);
        constraints.setMinWidth(0);
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    private ColumnConstraints fixedColumn(double width) {
        ColumnConstraints constraints = new ColumnConstraints(width, width, width);
        constraints.setHgrow(Priority.NEVER);
        return constraints;
    }

    private boolean isDashboardTable() {
        return "dashboard".equals(currentSectionKey);
    }

    private boolean isUsersTable() {
        return "users".equals(currentSectionKey);
    }

    private boolean isItemReviewTable() {
        return "items".equals(currentSectionKey);
    }

    private int tableColumnCount() {
        return isDashboardTable() || isUsersTable() ? 4 : 5;
    }

    private AdminRow row(
        String title,
        String meta,
        String firstValue,
        String secondValue,
        String status,
        String detail,
        String... actions
    ) {
        return new AdminRow(title, meta, firstValue, secondValue, status, detail, actions);
    }

    private AdminRow itemRow(
        String title,
        String meta,
        String firstValue,
        String secondValue,
        String status,
        String detail,
        String itemId,
        String sellerId,
        String sellerName,
        String itemName,
        String description,
        String startingPrice,
        String createdAt,
        String auctionId,
        String auctionStatus,
        String currentPrice,
        String bidCount,
        String imagePayload,
        String attributes,
        String... actions) {
        return new AdminRow(
            title,
            meta,
            firstValue,
            secondValue,
            status,
            detail,
            itemId,
            sellerId,
            sellerName,
            itemName,
            description,
            startingPrice,
            createdAt,
            auctionId,
            auctionStatus,
            currentPrice,
            bidCount,
            imagePayload,
            attributes,
            actions
        );
    }

    private void addFilteredRows(List<AdminRow> rows, String filter) {
        List<AdminRow> filteredRows = new ArrayList<>();

        for (AdminRow row : rows) {
            if (matchesFilter(row, filter)) {
                filteredRows.add(row);
            }
        }

        if (filteredRows.isEmpty()) {
            addEmptyRow(filter);
            return;
        }

        if (isDashboardTable()) {
            for (AdminRow row : filteredRows) {
                addRow(row);
            }
            return;
        }

        int totalPages = totalPages(filteredRows.size(), ADMIN_ROWS_PER_PAGE);
        int currentPage = clampPage(getPageForSection(currentSectionKey), totalPages);
        setPageForSection(currentSectionKey, currentPage);

        for (AdminRow row : pageSlice(filteredRows, currentPage, ADMIN_ROWS_PER_PAGE)) {
            addRow(row);
        }

        if (totalPages > 1) {
            dataRowsBox.getChildren().add(
                buildSectionPagination(currentSectionKey, totalPages, filteredRows.size())
            );
        }
    }

    private HBox buildSectionPagination(String sectionKey, int totalPages, int totalItems) {
        HBox pagination = new HBox(7);
        pagination.setAlignment(Pos.CENTER_RIGHT);
        pagination.getStyleClass().add("pagination-bar");

        int currentPage = clampPage(getPageForSection(sectionKey), totalPages);
        setPageForSection(sectionKey, currentPage);

        Label total = new Label(totalItems + " results");
        total.getStyleClass().add("row-meta");
        HBox.setHgrow(total, Priority.ALWAYS);

        Button previous = paginationButton("< Previous", currentPage <= 1);
        previous.setOnAction(event -> {
            int page = getPageForSection(sectionKey);
            if (page > 1) {
                setPageForSection(sectionKey, page - 1);
                renderWorkspace(sectionKey, activeFilter);
            }
        });

        pagination.getChildren().addAll(total, previous);

        addCompactPageButtons(pagination, sectionKey, currentPage, totalPages);

        Button next = paginationButton("Next >", currentPage >= totalPages);
        next.setOnAction(event -> {
            int page = getPageForSection(sectionKey);
            if (page < totalPages) {
                setPageForSection(sectionKey, page + 1);
                renderWorkspace(sectionKey, activeFilter);
            }
        });

        pagination.getChildren().add(next);
        return pagination;
    }

    private void addCompactPageButtons(
        HBox pagination, String sectionKey, int currentPage, int totalPages) {
        if (totalPages <= 7) {
            for (int page = 1; page <= totalPages; page++) {
                addPaginationPageButton(pagination, sectionKey, page, currentPage);
            }
            return;
        }

        int start = Math.max(2, currentPage - 1);
        int end = Math.min(totalPages - 1, currentPage + 1);

        addPaginationPageButton(pagination, sectionKey, 1, currentPage);
        if (start > 2) {
            pagination.getChildren().add(paginationDots());
        }

        for (int page = start; page <= end; page++) {
            addPaginationPageButton(pagination, sectionKey, page, currentPage);
        }

        if (end < totalPages - 1) {
            pagination.getChildren().add(paginationDots());
        }
        addPaginationPageButton(pagination, sectionKey, totalPages, currentPage);
    }

    private void addPaginationPageButton(
        HBox pagination, String sectionKey, int targetPage, int currentPage) {
        Button button = paginationButton(String.valueOf(targetPage), false);
        button.getStyleClass().add(targetPage == currentPage ? "pagination-active" : "pagination-btn");
        button.setOnAction(event -> {
            setPageForSection(sectionKey, targetPage);
            renderWorkspace(sectionKey, activeFilter);
        });
        pagination.getChildren().add(button);
    }

    private Label paginationDots() {
        Label dots = new Label("...");
        dots.getStyleClass().add("row-meta");
        return dots;
    }

    private Button paginationButton(String text, boolean disabled) {
        Button button = new Button(text);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("pagination-btn");
        button.setDisable(disabled);
        return button;
    }

    private int totalPages(int totalItems, int pageSize) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
    }

    private int clampPage(int page, int totalPages) {
        return Math.max(1, Math.min(page, totalPages));
    }

    private List<AdminRow> pageSlice(List<AdminRow> items, int page, int pageSize) {
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private void resetPageForSection(String sectionKey) {
        setPageForSection(sectionKey, 1);
    }

    private int getPageForSection(String sectionKey) {
        return switch (sectionKey) {
            case "users" -> usersPage;
            case "auctions" -> auctionsPage;
            case "items" -> itemsPage;
            default -> dashboardPage;
        };
    }

    private void setPageForSection(String sectionKey, int page) {
        int safePage = Math.max(1, page);
        switch (sectionKey) {
            case "users" -> usersPage = safePage;
            case "auctions" -> auctionsPage = safePage;
            case "items" -> itemsPage = safePage;
            default -> dashboardPage = safePage;
        }
    }

    private boolean matchesFilter(AdminRow row, String filter) {
        if (filter == null || isAllLikeFilter(filter)) {
            return true;
        }

        String normalizedFilter = normalize(filter);

        if (normalizedFilter.equals("ending soon")) {
            return normalize(row.meta).contains("ending soon")
                || normalize(row.meta).contains("ends in")
                || normalize(row.detail).contains("ending soon");
        }

        if (normalizedFilter.equals("no auction")) {
            return normalize(row.secondValue).equals("no auction")
                || normalize(row.meta).contains("no auction");
        }

        if (normalizedFilter.equals("pending approval")) {
            return normalize(row.status).contains("pending review")
                || normalize(row.meta).contains("pending review")
                || normalize(row.detail).contains("pending review");
        }

        return normalize(row.title).contains(normalizedFilter)
            || normalize(row.meta).contains(normalizedFilter)
            || normalize(row.firstValue).contains(normalizedFilter)
            || normalize(row.secondValue).contains(normalizedFilter)
            || normalize(row.status).contains(normalizedFilter)
            || normalize(row.detail).contains(normalizedFilter)
            || normalize(String.join(" ", row.actions)).contains(normalizedFilter);
    }

    private boolean isAllLikeFilter(String filter) {
        String normalized = normalize(filter);

        return normalized.equals("all")
            || normalized.equals("overview");
    }

    private boolean isItemStatusValue(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.equals("DRAFT")
            || normalized.equals("PENDING_REVIEW")
            || normalized.equals("AVAILABLE")
            || normalized.equals("IN_AUCTION")
            || normalized.equals("SOLD")
            || normalized.equals("REMOVED");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim().replace("_", " ");
    }

    private boolean isForceClosableStatus(String status) {
        return normalize(status).equals("open");
    }

    private String extractUserId(AdminRow data) {
        if (data == null) {
            return "";
        }
        String[] sources = {data.meta, data.detail, data.title};
        for (String source : sources) {
            String id = extractDigitsAfter(source, "ID #");
            if (!id.isBlank()) {
                return id;
            }
        }
        return "";
    }

    private String extractAuctionId(AdminRow data) {
        if (data == null) {
            return "";
        }
        if (data.itemData && data.auctionId != null && !data.auctionId.isBlank()) {
            return data.auctionId.trim();
        }
        String[] sources = {data.title, data.detail, data.meta};
        for (String source : sources) {
            String id = extractDigitsAfter(source, "AUC-");
            if (!id.isBlank()) {
                return id;
            }
        }
        return "";
    }

    private String extractDigitsAfter(String source, String marker) {
        if (source == null || marker == null || marker.isBlank()) {
            return "";
        }
        int index = source.indexOf(marker);
        if (index < 0) {
            return "";
        }
        int cursor = index + marker.length();
        StringBuilder digits = new StringBuilder();
        while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) {
            digits.append(source.charAt(cursor));
            cursor++;
        }
        return digits.toString();
    }

    private void addEmptyRow(String filter) {
        GridPane row = createTableGrid("data-row");

        Label empty = new Label("No records found for filter: " + filter);
        empty.getStyleClass().add("row-meta");
        empty.setWrapText(true);
        empty.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(empty, tableColumnCount());

        row.add(empty, 0, 0);
        dataRowsBox.getChildren().add(row);
    }

    private void addRow(AdminRow data) {
        if (isDashboardTable()) {
            addDashboardRow(data);
            return;
        }

        if (isUsersTable()) {
            addUserRow(data);
            return;
        }

        GridPane row = createTableGrid("data-row");

        VBox mainCell = buildMainCell(data);
        Label firstMetric = rowMetric(data.firstValue);
        Label secondMetric = rowMetric(data.secondValue);
        Label status = statusBadge(data.status);
        GridPane actions = rowActions(data);

        row.add(mainCell, 0, 0);
        row.add(firstMetric, 1, 0);
        row.add(secondMetric, 2, 0);
        row.add(status, 3, 0);
        row.add(actions, 4, 0);

        GridPane.setHalignment(firstMetric, HPos.CENTER);
        GridPane.setHalignment(secondMetric, HPos.CENTER);
        GridPane.setHalignment(status, HPos.CENTER);
        GridPane.setHalignment(actions, HPos.CENTER);

        dataRowsBox.getChildren().add(row);
    }

    private void addDashboardRow(AdminRow data) {
        GridPane row = createTableGrid("data-row");

        VBox mainCell = buildMainCell(data);
        Label countMetric = rowMetric(data.firstValue);
        Label signalMetric = rowMetric(data.secondValue);
        Label status = statusBadge(data.status);

        row.add(mainCell, 0, 0);
        row.add(countMetric, 1, 0);
        row.add(signalMetric, 2, 0);
        row.add(status, 3, 0);

        GridPane.setHalignment(countMetric, HPos.CENTER);
        GridPane.setHalignment(signalMetric, HPos.CENTER);
        GridPane.setHalignment(status, HPos.CENTER);

        dataRowsBox.getChildren().add(row);
    }

    private void addUserRow(AdminRow data) {
        GridPane row = createTableGrid("data-row");

        VBox mainCell = buildMainCell(data);
        Label roleMetric = rowMetric(data.firstValue);
        Label status = statusBadge(data.status);
        GridPane actions = rowActions(data);

        row.add(mainCell, 0, 0);
        row.add(roleMetric, 1, 0);
        row.add(status, 2, 0);
        row.add(actions, 3, 0);

        GridPane.setHalignment(roleMetric, HPos.CENTER);
        GridPane.setHalignment(status, HPos.CENTER);
        GridPane.setHalignment(actions, HPos.CENTER);

        dataRowsBox.getChildren().add(row);
    }

    private VBox buildMainCell(AdminRow data) {
        VBox mainCell = new VBox(2);
        mainCell.setMinWidth(0);
        mainCell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(mainCell, Priority.ALWAYS);

        if (shouldRenderTitleButton(data)) {
            Button link = new Button(data.title);
            link.setMnemonicParsing(false);
            link.getStyleClass().add("row-link");
            link.setMinWidth(0);
            link.setMaxWidth(Double.MAX_VALUE);
            link.setTextOverrun(OverrunStyle.ELLIPSIS);
            link.setOnAction(event -> handlePrimaryAction(resolvePrimaryAction(data), data));
            mainCell.getChildren().add(link);
        } else {
            Label title = new Label(data.title);
            title.getStyleClass().add("row-link");
            title.setMinWidth(0);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            mainCell.getChildren().add(title);
        }

        Label meta = new Label(data.meta);
        meta.getStyleClass().add("row-meta");
        meta.setWrapText(false);
        meta.setMinWidth(0);
        meta.setMaxWidth(Double.MAX_VALUE);
        meta.setTextOverrun(OverrunStyle.ELLIPSIS);

        mainCell.getChildren().add(meta);
        return mainCell;
    }

    private boolean shouldRenderTitleButton(AdminRow data) {
        return false;
    }

    private String resolvePrimaryAction(AdminRow data) {
        return data.actions.length > 0 ? data.actions[0] : "Open";
    }

    private GridPane rowActions(AdminRow data) {
        GridPane actions = new GridPane();
        actions.setHgap(isItemReviewTable() ? 4 : ACTION_GAP);
        actions.setAlignment(Pos.CENTER);
        actions.setMinWidth(0);
        actions.setMaxWidth(Double.MAX_VALUE);

        double primaryWidth = isItemReviewTable() ? ITEM_ACTION_PRIMARY_WIDTH : ACTION_PRIMARY_WIDTH;
        double moreWidth = isItemReviewTable() ? ITEM_ACTION_MORE_WIDTH : ACTION_MORE_WIDTH;

        if (data.actions.length == 0) {
            actions.getColumnConstraints().add(fixedColumn(primaryWidth));
            Region emptyAction = new Region();
            emptyAction.setMinWidth(primaryWidth);
            emptyAction.setPrefWidth(primaryWidth);
            actions.add(emptyAction, 0, 0);
            return actions;
        }

        boolean hasMoreActions = data.actions.length > 1;
        actions.getColumnConstraints().add(fixedColumn(primaryWidth));
        if (hasMoreActions) {
            actions.getColumnConstraints().add(fixedColumn(moreWidth));
        }

        String primaryAction = resolvePrimaryAction(data);

        Button primary = new Button(primaryAction);
        primary.setMnemonicParsing(false);
        primary.getStyleClass().add("mini-action-btn");
        if (isUsersTable() || "auctions".equals(currentSectionKey) || isItemReviewTable()) {
            primary.getStyleClass().add("compact-action-btn");
        }
        if (isItemReviewTable()) {
            primary.getStyleClass().add("item-review-action-btn");
        }
        primary.setMinWidth(primaryWidth);
        primary.setPrefWidth(primaryWidth);
        primary.setMaxWidth(primaryWidth);
        primary.setTextOverrun(OverrunStyle.ELLIPSIS);
        primary.setOnAction(event -> handlePrimaryAction(primaryAction, data));

        actions.add(primary, 0, 0);
        GridPane.setHalignment(primary, HPos.CENTER);

        if (hasMoreActions) {
            MenuButton more = new MenuButton("...");
            more.setMnemonicParsing(false);
            more.getStyleClass().add("more-action-btn");
            if (isItemReviewTable()) {
                more.getStyleClass().add("item-review-action-btn");
            }
            more.setMinWidth(moreWidth);
            more.setPrefWidth(moreWidth);
            more.setMaxWidth(moreWidth);

            for (int i = 1; i < data.actions.length; i++) {
                String action = data.actions[i];
                MenuItem item = new MenuItem(action);
                item.setOnAction(event -> handlePrimaryAction(action, data));
                more.getItems().add(item);
            }

            actions.add(more, 1, 0);
            GridPane.setHalignment(more, HPos.CENTER);
        }

        return actions;
    }

    private void handlePrimaryAction(String action, AdminRow data) {
        String normalizedAction = normalize(action);

        if (normalizedAction.equals("refresh")) {
            requestLiveAdminData();
            showDetail(
                "Refreshing cloud data",
                "Đã gửi lại ADMIN_LIST_USERS, ADMIN_LIST_ITEMS, ADMIN_LIST_AUCTIONS tới server."
            );
            return;
        }

        if ("dashboard".equals(currentSectionKey)) {
            openDashboardQueue(data);
            return;
        }

        if (normalizedAction.equals("force close") || normalizedAction.equals("cancel")) {
            forceCloseAuction(data);
            return;
        }

        if ("auctions".equals(currentSectionKey) && normalizedAction.equals("view")) {
            openAuctionDetail(data);
            return;
        }

        if (("items".equals(currentSectionKey) || "itemReview".equals(currentSectionKey))
            && (normalizedAction.equals("view") || normalizedAction.equals("review"))) {
            openItemReview(data);
            return;
        }

        if (("items".equals(currentSectionKey) || "itemReview".equals(currentSectionKey))
            && normalizedAction.equals("view auction")) {
            openLinkedAuctionDetail(data);
            return;
        }

        if (normalizedAction.equals("approve") && data.itemData) {
            approveItem(data);
            return;
        }

        if (normalizedAction.equals("reject") && data.itemData) {
            rejectItem(data);
            return;
        }

        if (normalizedAction.equals("create auction") && data.itemData) {
            openCreateAuctionDraft(data);
            return;
        }

        if ("users".equals(currentSectionKey)) {
            if (normalizedAction.equals("view")) {
                showDetail(data.title, data.detail);
                return;
            }
            if (normalizedAction.equals("ban")) {
                banUser(data);
                return;
            }
            if (normalizedAction.equals("restore")) {
                restoreUser(data);
                return;
            }
        }

        showDetail(action + " - " + data.title, data.detail);
    }


    private void openDashboardQueue(AdminRow data) {
        String normalizedTitle = normalize(data == null ? "" : data.title);
        String targetSection = "items";
        String targetFilter = "PENDING_REVIEW";

        if (normalizedTitle.contains("running")) {
            targetSection = "auctions";
            targetFilter = "RUNNING";
        } else if (normalizedTitle.contains("finished")) {
            targetSection = "auctions";
            targetFilter = "FINISHED";
        } else if (normalizedTitle.contains("risk")) {
            targetSection = "users";
            targetFilter = "All";
        }

        currentSectionKey = targetSection;
        activeFilter = targetFilter;
        resetPageForSection(targetSection);
        super.showSection(targetSection);
        updatePrimaryAction(targetSection);
        renderWorkspace(targetSection, activeFilter);
    }

    private boolean canSendAdminCommand(String actionKey, String busyTitle, String busyMessage) {
        if (adminNetworkManager == null || !adminNetworkManager.isConnected()) {
            showDetail(busyTitle, "Client chưa kết nối server.");
            return false;
        }
        if (pendingAdminActions.contains(actionKey)) {
            showDetail(busyTitle, busyMessage);
            return false;
        }
        pendingAdminActions.add(actionKey);
        return true;
    }

    private void approveItem(AdminRow data) {
        if (data == null || data.itemId == null || data.itemId.isBlank()) {
            showDetail("Approve failed", "Thiếu item_id từ dữ liệu server.");
            return;
        }
        if (!canSendAdminCommand("APPROVE:" + data.itemId, "Approving item",
            "Yêu cầu approve item này đang được server xử lý.")) {
            return;
        }
        adminNetworkManager.send("ADMIN_APPROVE_ITEM " + data.itemId);
        showDetail("Approving item", "Đã gửi ADMIN_APPROVE_ITEM #" + data.itemId + " tới server.");
    }

    private void rejectItem(AdminRow data) {
        if (data == null || data.itemId == null || data.itemId.isBlank()) {
            showDetail("Reject failed", "Thiếu item_id từ dữ liệu server.");
            return;
        }
        if (!canSendAdminCommand("REJECT:" + data.itemId, "Rejecting item",
            "Yêu cầu reject item này đang được server xử lý.")) {
            return;
        }
        adminNetworkManager.send("ADMIN_REJECT_ITEM " + data.itemId + " Rejected by admin review");
        showDetail("Rejecting item", "Đã gửi ADMIN_REJECT_ITEM #" + data.itemId + " tới server.");
    }

    private void banUser(AdminRow data) {
        String userId = extractUserId(data);
        if (userId.isBlank()) {
            showDetail("Ban failed", "Thiếu user_id từ dữ liệu server.");
            return;
        }
        if (!canSendAdminCommand("BAN:" + userId, "Banning user",
            "Yêu cầu ban user này đang được server xử lý.")) {
            return;
        }
        adminNetworkManager.send("ADMIN_BAN_USER " + userId + " Banned by admin moderation");
        showDetail("Banning user", "Đã gửi ADMIN_BAN_USER #" + userId + " tới server.");
    }

    private void restoreUser(AdminRow data) {
        String userId = extractUserId(data);
        if (userId.isBlank()) {
            showDetail("Restore failed", "Thiếu user_id từ dữ liệu server.");
            return;
        }
        if (!canSendAdminCommand("RESTORE:" + userId, "Restoring user",
            "Yêu cầu restore user này đang được server xử lý.")) {
            return;
        }
        adminNetworkManager.send("ADMIN_UNBAN_USER " + userId);
        showDetail("Restoring user", "Đã gửi ADMIN_UNBAN_USER #" + userId + " tới server.");
    }

    private void forceCloseAuction(AdminRow data) {
        String auctionId = extractAuctionId(data);
        if (auctionId.isBlank()) {
            showDetail("Force close failed", "Thiếu auction_id từ dữ liệu server.");
            return;
        }
        if (!canSendAdminCommand("CLOSE:" + auctionId, "Force closing auction",
            "Yêu cầu cancel auction này đang được server xử lý.")) {
            return;
        }
        adminNetworkManager.send("ADMIN_FORCE_CLOSE " + auctionId + " Force closed from admin-home");
        showDetail("Force closing auction", "Đã gửi ADMIN_FORCE_CLOSE AUC-" + auctionId + " tới server.");
    }

    private Label rowMetric(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("row-metric");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private Label statusBadge(String status) {
        Label label = new Label(status);
        label.getStyleClass().add("status-badge");
        label.setMinWidth(0);
        label.setPrefWidth(88);
        label.setMaxWidth(96);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setAlignment(Pos.CENTER);

        String normalized = status.toLowerCase();

        if (normalized.contains("active")
            || normalized.contains("running")
            || normalized.contains("available")
            || normalized.contains("healthy")
            || normalized.contains("paid")
            || normalized.contains("ready")
            || normalized.contains("admin")
            || normalized.contains("up")) {
            label.getStyleClass().add("status-good");
        } else if (normalized.contains("suspended")
            || normalized.contains("banned")
            || normalized.contains("removed")
            || normalized.contains("canceled")
            || normalized.contains("flagged")
            || normalized.contains("inactive")) {
            label.getStyleClass().add("status-danger");
        } else if (normalized.contains("open")
            || normalized.contains("draft")
            || normalized.contains("pending")
            || normalized.contains("loading")
            || normalized.contains("finished")) {
            label.getStyleClass().add("status-warn");
        } else {
            label.getStyleClass().add("status-neutral");
        }

        return label;
    }

    private void openAuctionDetail(AdminRow data) {
        setTableTitle("Auction Detail");
        clearWorkspaceForDetail();
        addBackButton("Back to Auctions", "auctions");
        if (isForceClosableStatus(data.status)) {
            addForceCloseAction(data);
        }

        addDetailHero(data.title, data.status, data.firstValue, data.secondValue, data.meta);
        addDetailBlock(
            "Overview",
            "Status: " + data.status,
            "Current price: " + data.firstValue,
            "Bids: " + data.secondValue,
            "Admin-home chỉ dùng để giám sát session và cancel khi cần."
        );
        addDetailBlock(
            "Linked item",
            extractItemId(data.meta),
            "Seller: " + extractSeller(data.meta),
            "Item và seller được trace từ dữ liệu auction thật của server."
        );
        addDetailBlock(
            "Allowed admin action",
            isForceClosableStatus(data.status)
                ? "Cancel chỉ hiện với auction OPEN và maps to ADMIN_FORCE_CLOSE with a fixed audit reason."
                : "Auction này ở trạng thái view-only; payment/bid/wallet flow không sửa từ admin-home."
        );

        showDetail(
            data.title,
            "Auction detail đã được thu gọn: chỉ xem trạng thái và cancel khi backend cho phép."
        );
    }


    private void openLinkedAuctionDetail(AdminRow itemData) {
        String auctionId = itemData.auctionId == null || itemData.auctionId.isBlank()
            ? "AUC detail"
            : "AUC-" + itemData.auctionId;
        AdminRow linkedAuction = row(
            auctionId + " - Linked from ITEM-" + itemData.itemId,
            itemData.meta,
            fallback(itemData.currentPrice, "From item"),
            fallback(itemData.bidCount, "0") + " bids",
            fallback(itemData.auctionStatus, itemData.status),
            itemData.detail,
            "View"
        );
        openAuctionDetail(linkedAuction);
    }

    private void openItemReview(AdminRow data) {
        currentSectionKey = "itemReview";
        setTableTitle("");
        clearWorkspaceForDetail();
        addBackButton("Back to Items", "items");

        VBox shell = new VBox(10);
        shell.getStyleClass().add("admin-workspace-shell");

        Label title = new Label("ITEM-" + data.itemId + " - " + data.itemName);
        title.getStyleClass().add("admin-page-title");
        title.setWrapText(true);

        GridPane content = createDetailGrid();
        VBox gallery = buildItemGallery(data, REVIEW_IMAGE_HEIGHT, "");
        VBox infoCard = buildReviewInfoCard(data);
        content.add(gallery, 0, 0);
        content.add(infoCard, 1, 0);
        keepDetailGridChildrenResizable(gallery, infoCard);

        shell.getChildren().addAll(title, content);
        dataRowsBox.getChildren().add(shell);

        showDetail(data.itemName, "");
    }

    private VBox buildReviewInfoCard(AdminRow data) {
        VBox card = new VBox(10);
        card.getStyleClass().add("item-review-card");
        card.setMaxWidth(Double.MAX_VALUE);

        HBox statusLine = new HBox(8);
        statusLine.setAlignment(Pos.CENTER_LEFT);
        statusLine.getChildren().addAll(statusBadge(data.status), statusBadge(data.firstValue));

        Label name = new Label(data.itemName);
        name.getStyleClass().add("item-info-title");
        name.setWrapText(true);

        Label price = new Label(data.startingPrice);
        price.getStyleClass().add("item-info-price");

        GridPane facts = createInfoGrid();
        addInfoCell(facts, 0, 0, "Item ID", "ITEM-" + data.itemId);
        addInfoCell(facts, 1, 0, "Seller", data.sellerName + " (#" + data.sellerId + ")");
        addInfoCell(facts, 0, 1, "Category", data.firstValue);
        addInfoCell(facts, 1, 1, "Submitted", fallback(data.createdAt, "not available"));
        addInfoCell(facts, 0, 2, "Auction link", auctionLinkText(data));
        addInfoCell(facts, 1, 2, "Database status", data.status);

        VBox attributesBox = buildAttributesBox(data.attributes);
        HBox actions = buildItemDetailActions(data);

        card.getChildren().addAll(statusLine, name, price, facts, attributesBox, actions);
        return card;
    }

    private String auctionLinkText(AdminRow data) {
        if (data == null || data.auctionId == null || data.auctionId.isBlank()) {
            return "No auction";
        }
        String status = fallback(data.auctionStatus, "OPEN");
        return "AUC-" + data.auctionId + " / " + status;
    }

    private HBox buildItemDetailActions(AdminRow data) {
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        String normalizedStatus = normalize(data.status);

        if (normalizedStatus.equals("pending review")) {
            Button reject = new Button("Reject");
            reject.setMnemonicParsing(false);
            reject.getStyleClass().add("create-secondary-btn");
            reject.setOnAction(event -> rejectItem(data));

            Button approve = new Button("Approve");
            approve.setMnemonicParsing(false);
            approve.getStyleClass().add("create-primary-btn");
            approve.setOnAction(event -> approveItem(data));
            actions.getChildren().addAll(reject, approve);
            return actions;
        }

        if (normalizedStatus.equals("available")) {
            Button createAuction = new Button("Create Auction");
            createAuction.setMnemonicParsing(false);
            createAuction.getStyleClass().add("create-primary-btn");
            createAuction.setOnAction(event -> openCreateAuctionDraft(data));
            actions.getChildren().add(createAuction);
            return actions;
        }

        return actions;
    }


    private void handleAcceptAndOpenCreateAuction(AdminRow data) {
        approveItem(data);
    }


    private void openCreateAuctionDraft(AdminRow data) {
        currentSectionKey = "createAuction";
        setTableTitle("");
        clearWorkspaceForDetail();
        addBackButton("Back to Items", "items");

        VBox shell = new VBox(10);
        shell.getStyleClass().add("admin-workspace-shell");

        Label title = new Label("Create Auction Room");
        title.getStyleClass().add("admin-page-title");

        GridPane content = createDetailGrid();
        VBox gallery = buildItemGallery(data, AUCTION_IMAGE_HEIGHT, "");
        VBox form = buildCreateAuctionForm(data);
        content.add(gallery, 0, 0);
        content.add(form, 1, 0);
        keepDetailGridChildrenResizable(gallery, form);

        shell.getChildren().addAll(title, content);
        dataRowsBox.getChildren().add(shell);

        showDetail(
            "Create auction",
            "Form dùng đúng schema auctions: item_id/seller_id lấy từ item, current_price lấy từ starting_price, status mặc định OPEN."
        );
    }

    private VBox buildCreateAuctionForm(AdminRow data) {
        VBox card = new VBox(10);
        card.getStyleClass().add("auction-create-card");
        card.setMaxWidth(Double.MAX_VALUE);

        HBox titleLine = new HBox(8);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        Label itemId = new Label("ITEM-" + data.itemId);
        itemId.getStyleClass().add("auction-kicker");
        Label status = statusBadge("AVAILABLE");
        titleLine.getChildren().addAll(itemId, status);

        Label name = new Label(data.itemName);
        name.getStyleClass().add("item-info-title");
        name.setWrapText(true);

        Label price = new Label("Starting price: " + data.startingPrice);
        price.getStyleClass().add("item-info-price");

        GridPane facts = createInfoGrid();
        addInfoCell(facts, 0, 0, "Seller", data.sellerName + " (#" + data.sellerId + ")");
        addInfoCell(facts, 1, 0, "Category", data.firstValue);
        addInfoCell(facts, 0, 1, "Auction status", "OPEN");
        addInfoCell(facts, 1, 1, "Current price", data.startingPrice);

        GridPane formGrid = createInfoGrid();
        TextField startField = addFormCell(formGrid, 0, 0, "Start time", "yyyy-MM-dd HH:mm:ss");
        TextField endField = addFormCell(formGrid, 1, 0, "End time", "yyyy-MM-dd HH:mm:ss");
        TextField incrementField = addFormCell(formGrid, 0, 1, "Minimum bid increment", "Example: 10000");
        TextField reserveField = addFormCell(formGrid, 1, 1, "Reserve price", stripCurrency(data.startingPrice));
        TextField windowField = addFormCell(formGrid, 0, 2, "Snipe window seconds", "300");
        TextField extensionField = addFormCell(formGrid, 1, 2, "Snipe extension seconds", "60");
        LocalDateTime defaultStart = LocalDateTime.now().plusMinutes(5);
        LocalDateTime defaultEnd = defaultStart.plusDays(7);
        startField.setText(defaultStart.format(AUCTION_TIME_FORMATTER));
        endField.setText(defaultEnd.format(AUCTION_TIME_FORMATTER));
        incrementField.setText("10000");
        windowField.setText("300");
        extensionField.setText("60");

        Button create = new Button("Create Auction");
        create.setMnemonicParsing(false);
        create.getStyleClass().add("create-primary-btn");
        create.setOnAction(event -> handleCreateAuction(
            data,
            startField.getText(),
            endField.getText(),
            incrementField.getText(),
            reserveField.getText(),
            windowField.getText(),
            extensionField.getText()
        ));

        card.getChildren().addAll(titleLine, name, price, facts, formGrid, create);
        return card;
    }

    private void handleCreateAuction(
        AdminRow data,
        String startTime,
        String endTime,
        String minimumBidIncrement,
        String reservePrice,
        String snipeWindowSeconds,
        String snipeExtensionSeconds) {
        String validationError = validateAuctionForm(
            data,
            startTime,
            endTime,
            minimumBidIncrement,
            reservePrice,
            snipeWindowSeconds,
            snipeExtensionSeconds
        );
        if (!validationError.isBlank()) {
            showDetail("Create auction failed", validationError);
            return;
        }

        if (!canSendAdminCommand("CREATE_AUCTION:" + data.itemId, "Creating auction",
            "Yêu cầu tạo auction cho item này đang được server xử lý.")) {
            return;
        }

        adminNetworkManager.send("ADMIN_CREATE_AUCTION " + fields(
            data.itemId,
            data.sellerId,
            startTime.trim(),
            endTime.trim(),
            sanitizeMoney(minimumBidIncrement),
            sanitizeMoney(reservePrice),
            snipeWindowSeconds.trim(),
            snipeExtensionSeconds.trim()
        ));
        showDetail("Creating auction", "Đã gửi ADMIN_CREATE_AUCTION tới server để insert bảng auctions.");
    }

    private String validateAuctionForm(
        AdminRow data,
        String startTime,
        String endTime,
        String minimumBidIncrement,
        String reservePrice,
        String snipeWindowSeconds,
        String snipeExtensionSeconds) {
        if (data == null || !isPositiveInteger(data.itemId) || !isPositiveInteger(data.sellerId)) {
            return "Thiếu hoặc sai item_id/seller_id từ database, không được tạo auction.";
        }

        if (!normalize(data.status).equals("available")) {
            return "Item không còn AVAILABLE, không được tạo auction mới.";
        }

        LocalDateTime parsedStart = parseAuctionDateTime(startTime);
        if (parsedStart == null) {
            return "Start time phải đúng format yyyy-MM-dd HH:mm:ss và là ngày giờ hợp lệ.";
        }

        LocalDateTime parsedEnd = parseAuctionDateTime(endTime);
        if (parsedEnd == null) {
            return "End time phải đúng format yyyy-MM-dd HH:mm:ss và là ngày giờ hợp lệ.";
        }

        LocalDateTime now = LocalDateTime.now();
        if (parsedStart.isBefore(now.minusMinutes(1))) {
            return "Start time không được nằm trong quá khứ.";
        }

        if (!parsedEnd.isAfter(parsedStart)) {
            return "End time phải sau Start time.";
        }

        Duration auctionDuration = Duration.between(parsedStart, parsedEnd);
        if (auctionDuration.toMinutes() < 1) {
            return "Auction phải kéo dài tối thiểu 1 phút.";
        }
        if (auctionDuration.toDays() > MAX_AUCTION_DURATION_DAYS) {
            return "Auction không được kéo dài quá " + MAX_AUCTION_DURATION_DAYS + " ngày.";
        }

        BigDecimal minIncrement = parseAuctionMoney(minimumBidIncrement);
        if (minIncrement == null) {
            return "Minimum bid increment phải là số hợp lệ, tối đa 13 chữ số và 2 số thập phân.";
        }
        if (minIncrement.compareTo(MIN_INCREMENT) < 0) {
            return "Minimum bid increment phải lớn hơn hoặc bằng 1.";
        }

        BigDecimal reserve = null;
        if (reservePrice != null && !reservePrice.isBlank()) {
            reserve = parseAuctionMoney(reservePrice);
            if (reserve == null) {
                return "Reserve price phải để trống hoặc là số hợp lệ, tối đa 13 chữ số và 2 số thập phân.";
            }
            if (reserve.compareTo(BigDecimal.ZERO) < 0) {
                return "Reserve price không được âm.";
            }
        }

        BigDecimal startingPrice = parseAuctionMoney(stripCurrency(data.startingPrice));
        if (startingPrice != null && reserve != null && reserve.compareTo(startingPrice) < 0) {
            return "Reserve price phải lớn hơn hoặc bằng Starting price.";
        }

        if (!isSafeAuctionSeconds(snipeWindowSeconds)) {
            return "Snipe window seconds phải là số nguyên từ 0 đến " + MAX_AUCTION_SECONDS + ".";
        }
        if (!isSafeAuctionSeconds(snipeExtensionSeconds)) {
            return "Snipe extension seconds phải là số nguyên từ 0 đến " + MAX_AUCTION_SECONDS + ".";
        }
        return "";
    }

    private LocalDateTime parseAuctionDateTime(String value) {
        if (value == null || value.isBlank() || hasControlCharacters(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() != 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(trimmed, AUCTION_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private BigDecimal parseAuctionMoney(String value) {
        String sanitized = sanitizeMoney(value);
        if (sanitized.isBlank()
            || sanitized.length() > MAX_MONEY_INPUT_LENGTH
            || hasControlCharacters(sanitized)
            || !MONEY_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(sanitized);
            if (amount.compareTo(BigDecimal.ZERO) < 0 || amount.compareTo(MAX_AUCTION_MONEY) > 0) {
                return null;
            }
            return amount;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isMoney(String value) {
        return parseAuctionMoney(value) != null;
    }

    private String sanitizeMoney(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").trim();
    }

    private boolean isSafeAuctionSeconds(String value) {
        if (value == null || value.isBlank() || hasControlCharacters(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_SECONDS_INPUT_LENGTH || !trimmed.matches("[0-9]+")) {
            return false;
        }
        try {
            int seconds = Integer.parseInt(trimmed);
            return seconds >= 0 && seconds <= MAX_AUCTION_SECONDS;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isPositiveInteger(String value) {
        if (value == null || value.isBlank() || hasControlCharacters(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean hasControlCharacters(String value) {
        return value != null && value.chars().anyMatch(character -> character < 32 || character == 127);
    }

    private GridPane createDetailGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("review-create-grid");
        grid.setHgap(18);
        grid.setVgap(14);
        grid.setMinWidth(0);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(48);
        left.setHgrow(Priority.ALWAYS);
        left.setFillWidth(true);

        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(52);
        right.setHgrow(Priority.ALWAYS);
        right.setFillWidth(true);

        grid.getColumnConstraints().addAll(left, right);
        return grid;
    }

    private void keepDetailGridChildrenResizable(Region leftChild, Region rightChild) {
        leftChild.setMinWidth(0);
        rightChild.setMinWidth(0);
        GridPane.setFillWidth(leftChild, true);
        GridPane.setFillWidth(rightChild, true);
        GridPane.setHgrow(leftChild, Priority.ALWAYS);
        GridPane.setHgrow(rightChild, Priority.ALWAYS);
    }

    private VBox buildItemGallery(AdminRow data, double imageHeight, String titleText) {
        double maxImageWidth = imageHeight * PREVIEW_IMAGE_WIDTH_RATIO;
        double maxGalleryWidth = maxImageWidth + PREVIEW_CARD_HORIZONTAL_PADDING;

        VBox gallery = new VBox(8);
        gallery.getStyleClass().add("item-gallery-card");
        gallery.setFillWidth(true);
        gallery.setMinWidth(0);
        gallery.setPrefWidth(maxGalleryWidth);
        gallery.setMaxWidth(maxGalleryWidth);

        Label title = null;
        if (titleText != null && !titleText.isBlank()) {
            title = new Label(titleText);
            title.getStyleClass().add("placeholder-title");
        }

        StackPane mainFrame = new StackPane();
        mainFrame.getStyleClass().add("item-main-image-wrap");
        mainFrame.setMinWidth(0);
        mainFrame.setPrefWidth(maxImageWidth);
        mainFrame.setMaxWidth(maxImageWidth);
        mainFrame.setMinHeight(imageHeight);
        mainFrame.setPrefHeight(imageHeight);
        mainFrame.setMaxHeight(imageHeight);

        List<String> images = parseMultiline(data.imagePayload);
        displayImage(mainFrame, images.isEmpty() ? "" : images.get(0), "No uploaded image");

        FlowPane thumbs = new FlowPane(8, 8);
        thumbs.setAlignment(Pos.CENTER_LEFT);
        thumbs.getStyleClass().add("item-thumb-row");
        thumbs.setMinWidth(0);
        thumbs.setMaxWidth(maxImageWidth);

        if (!images.isEmpty()) {
            for (String imageUrl : images) {
                StackPane thumb = buildThumb(imageUrl, mainFrame);
                thumbs.getChildren().add(thumb);
            }
        }

        if (title != null) {
            gallery.getChildren().add(title);
        }
        gallery.getChildren().add(mainFrame);
        if (!thumbs.getChildren().isEmpty()) {
            gallery.getChildren().add(thumbs);
        }
        return gallery;
    }

    private StackPane buildThumb(String imageUrl, StackPane mainFrame) {
        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("item-thumb");
        thumb.setMinSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setMaxSize(THUMB_SIZE, THUMB_SIZE);
        displayImage(thumb, imageUrl, "");
        thumb.setOnMouseClicked(event -> displayImage(mainFrame, imageUrl, "No uploaded image"));
        return thumb;
    }

    private void displayImage(StackPane frame, String imageUrl, String placeholderText) {
        frame.getChildren().clear();
        Image image = loadAdminImage(imageUrl);
        if (image == null || image.isError()) {
            Label placeholder = new Label(placeholderText == null ? "" : placeholderText);
            placeholder.getStyleClass().add("item-image-placeholder");
            placeholder.setWrapText(true);
            frame.getChildren().add(placeholder);
            return;
        }

        ImageView imageView = new ImageView(image);
        imageView.setManaged(false);
        imageView.setMouseTransparent(true);
        imageView.setSmooth(true);
        imageView.setCache(true);
        imageView.setPreserveRatio(false);
        imageView.fitWidthProperty().bind(frame.widthProperty());
        imageView.fitHeightProperty().bind(frame.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);

        frame.widthProperty().addListener((observable, oldValue, newValue) ->
            updateCoverViewport(imageView, frame));
        frame.heightProperty().addListener((observable, oldValue, newValue) ->
            updateCoverViewport(imageView, frame));
        image.progressProperty().addListener((observable, oldValue, newValue) ->
            updateCoverViewport(imageView, frame));
        frame.getChildren().add(imageView);
        frame.applyCss();
        frame.layout();
        updateCoverViewport(imageView, frame);
    }

    private Image loadAdminImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        if (adminImageCache.containsKey(imagePath)) {
            return adminImageCache.get(imagePath);
        }

        Image image = null;
        try {
            if (imagePath.startsWith("file:") || imagePath.startsWith("http:")) {
                image = new Image(imagePath, false);
            } else if (imagePath.startsWith("https:")) {
                image = new Image(imagePath, true);
            } else if (getClass().getResource(imagePath) != null) {
                image = new Image(getClass().getResource(imagePath).toExternalForm(), false);
            } else {
                image = new Image("file:" + imagePath, false);
            }
        } catch (IllegalArgumentException ignored) {
            image = null;
        }
        adminImageCache.put(imagePath, image);
        return image;
    }

    private void updateCoverViewport(ImageView imageView, Region container) {
        Image image = imageView.getImage();
        if (image == null
            || image.getWidth() <= 0
            || image.getHeight() <= 0
            || container.getWidth() <= 0
            || container.getHeight() <= 0) {
            return;
        }

        double imageRatio = image.getWidth() / image.getHeight();
        double targetRatio = container.getWidth() / container.getHeight();
        double viewportWidth = image.getWidth();
        double viewportHeight = image.getHeight();

        if (imageRatio > targetRatio) {
            viewportWidth = image.getHeight() * targetRatio;
        } else {
            viewportHeight = image.getWidth() / targetRatio;
        }

        double x = (image.getWidth() - viewportWidth) / 2;
        double y = (image.getHeight() - viewportHeight) / 2;
        imageView.setViewport(new Rectangle2D(x, y, viewportWidth, viewportHeight));
    }

    private GridPane createInfoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setHgrow(Priority.ALWAYS);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(left, right);
        return grid;
    }

    private void addInfoCell(GridPane grid, int column, int row, String label, String value) {
        VBox cell = new VBox(3);
        cell.getStyleClass().add("auction-info-cell");
        Label key = new Label(label);
        key.getStyleClass().add("auction-info-key");
        Label val = new Label(fallback(value, "not available"));
        val.getStyleClass().add("auction-info-value");
        val.setWrapText(true);
        cell.getChildren().addAll(key, val);
        grid.add(cell, column, row);
    }

    private TextField addFormCell(GridPane grid, int column, int row, String label, String prompt) {
        VBox box = new VBox(4);
        Label key = new Label(label);
        key.getStyleClass().add("create-field-label");
        TextField field = new TextField();
        field.getStyleClass().add("admin-auction-field");
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(key, field);
        grid.add(box, column, row);
        return field;
    }

    private VBox buildAttributesBox(String attributes) {
        VBox box = new VBox(5);
        box.getStyleClass().add("item-attributes-box");
        Label title = new Label("Item attributes");
        title.getStyleClass().add("placeholder-title");
        box.getChildren().add(title);

        List<String> lines = parseMultiline(attributes);
        if (lines.isEmpty()) {
            Label empty = new Label("No extra attributes stored for this item.");
            empty.getStyleClass().add("detail-page-line");
            empty.setWrapText(true);
            box.getChildren().add(empty);
            return box;
        }

        for (String line : lines) {
            Label label = new Label(line);
            label.getStyleClass().add("detail-page-line");
            label.setWrapText(true);
            box.getChildren().add(label);
        }
        return box;
    }

    private List<String> parseMultiline(String payload) {
        List<String> values = new ArrayList<>();
        if (payload == null || payload.isBlank()) {
            return values;
        }
        String normalizedPayload = payload.replace("\\n", "\n");
        for (String value : normalizedPayload.split("\\R")) {
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private String stripCurrency(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)\\b(VND|USD)\\b", "").replace(",", "").trim();
    }

    private void clearWorkspaceForDetail() {
        if (dataRowsBox != null) {
            dataRowsBox.getChildren().clear();
        }
        if (adminActionBar != null) {
            adminActionBar.getChildren().clear();
        }
    }

    private void addBackButton(String text, String sectionKey) {
        if (adminActionBar == null) {
            return;
        }

        Button back = new Button(text);
        back.setMnemonicParsing(false);
        back.getStyleClass().add("filter-chip");
        back.setOnAction(event -> {
            currentSectionKey = sectionKey;
            activeFilter = getDefaultFilter(sectionKey);
            super.showSection(sectionKey);
            updatePrimaryAction(sectionKey);
            renderWorkspace(sectionKey, activeFilter);
        });
        adminActionBar.getChildren().add(back);
    }

    private void addForceCloseAction(AdminRow data) {
        if (adminActionBar == null) {
            return;
        }
        Button button = new Button("Cancel");
        button.setMnemonicParsing(false);
        button.getStyleClass().add("filter-chip-active");
        button.setOnAction(event -> forceCloseAuction(data));
        adminActionBar.getChildren().add(button);
    }

    private void addDetailAction(String action, AdminRow data) {
        if (adminActionBar == null) {
            return;
        }

        Button button = new Button(action);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("filter-chip-active");
        button.setOnAction(event -> showDetail(action + " - " + data.title, data.detail));
        adminActionBar.getChildren().add(button);
    }

    private void addDetailHero(
        String title,
        String status,
        String firstValue,
        String secondValue,
        String meta) {
        VBox hero = new VBox(8);
        hero.getStyleClass().add("detail-page-hero");

        HBox topLine = new HBox(10);
        topLine.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("detail-page-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label badge = statusBadge(status);
        topLine.getChildren().addAll(titleLabel, badge);

        HBox metrics = new HBox(10);
        metrics.setAlignment(Pos.CENTER_LEFT);
        metrics.getChildren().addAll(
            detailMetric("Primary", firstValue),
            detailMetric("Secondary", secondValue)
        );

        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("detail-page-meta");
        metaLabel.setWrapText(true);

        hero.getChildren().addAll(topLine, metrics, metaLabel);
        dataRowsBox.getChildren().add(hero);
    }

    private VBox detailMetric(String label, String value) {
        VBox box = new VBox(3);
        box.getStyleClass().add("detail-metric-card");
        Label labelText = new Label(label);
        labelText.getStyleClass().add("detail-metric-label");
        Label valueText = new Label(value);
        valueText.getStyleClass().add("detail-metric-value");
        valueText.setWrapText(true);
        box.getChildren().addAll(labelText, valueText);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void addDetailBlock(String title, String... lines) {
        VBox block = new VBox(5);
        block.getStyleClass().add("detail-page-block");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("placeholder-title");
        block.getChildren().add(titleLabel);

        for (String line : lines) {
            Label label = new Label(line);
            label.getStyleClass().add("detail-page-line");
            label.setWrapText(true);
            block.getChildren().add(label);
        }

        dataRowsBox.getChildren().add(block);
    }

    private String extractItemId(String meta) {
        if (meta == null || meta.isBlank()) {
            return "Item: not available";
        }
        int index = meta.indexOf(" - ");
        return index > 0 ? "Item: " + meta.substring(0, index) : "Item: " + meta;
    }

    private String extractSeller(String meta) {
        if (meta == null) {
            return "not available";
        }
        String marker = "Seller ";
        int start = meta.indexOf(marker);
        if (start < 0) {
            return "not available";
        }
        int sellerStart = start + marker.length();
        int sellerEnd = meta.indexOf(" - ", sellerStart);
        return sellerEnd > sellerStart
            ? meta.substring(sellerStart, sellerEnd)
            : meta.substring(sellerStart);
    }

    private void showDetail(String title, String description) {
        if (detailTitleLabel != null) {
            detailTitleLabel.setText(title);
        }

        if (detailDescriptionLabel != null) {
            detailDescriptionLabel.setText(description);
        }
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

    private String safeField(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index) : "";
    }

    private String normalizeUserStatus(String status, String isActive) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("null")) {
            return status.toUpperCase();
        }
        if (isActive != null
            && !isActive.isBlank()
            && (isActive.equals("0") || isActive.equalsIgnoreCase("false"))) {
            return "INACTIVE";
        }
        return "ACTIVE";
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.equalsIgnoreCase("SELLER") || role.equalsIgnoreCase("BIDDER")
            ? "USER"
            : role.toUpperCase();
    }

    private String shortTimestamp(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            return "Never";
        }

        String cleaned = value.replace(".0", "").replace('T', ' ').trim();
        int dot = cleaned.indexOf('.');
        if (dot > 0) {
            cleaned = cleaned.substring(0, dot);
        }

        if (cleaned.length() >= 16) {
            return cleaned.substring(0, 16);
        }

        return cleaned;
    }

    private String formatMoney(String value) {
        return formatMoney(value, "VND");
    }

    private String formatMoney(String value, String currency) {
        String safeCurrency = normalizeCurrency(currency);
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            return "0 " + safeCurrency;
        }

        try {
            String integerPart = value.split("\\.")[0];
            long amount = Long.parseLong(integerPart);
            return String.format("%,d %s", amount, safeCurrency);
        } catch (Exception ignored) {
            String trimmed = value.trim();
            return endsWithKnownCurrency(trimmed) ? trimmed : trimmed + " " + safeCurrency;
        }
    }

    private String extractCurrency(String attributes) {
        for (String line : parseMultiline(attributes)) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (key.equalsIgnoreCase("currency") && !value.isBlank()) {
                return normalizeCurrency(value);
            }
        }
        return "VND";
    }

    private String normalizeCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase();
        return normalized.isBlank() ? "VND" : normalized;
    }

    private boolean endsWithKnownCurrency(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.endsWith(" VND")
            || normalized.endsWith(" USD");
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("null") ? fallback : value;
    }

    private String joinMeta(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !value.equalsIgnoreCase("null")) {
                parts.add(value);
            }
        }
        return String.join(" - ", parts);
    }

}
