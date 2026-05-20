package client.controller;


import client.enums.AccountStatus;
import client.enums.SystemRole;
import client.model.User;
import client.service.NetworkManager;
import client.service.SessionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

    private static final double ACTION_PRIMARY_WIDTH = 58;
    private static final double ACTION_MORE_WIDTH = 26;
    private static final double ACTION_GAP = 6;
    private static final double REVIEW_IMAGE_HEIGHT = 330;
    private static final double AUCTION_IMAGE_HEIGHT = 360;
    private static final double PREVIEW_IMAGE_WIDTH_RATIO = 1.55;
    private static final double PREVIEW_CARD_HORIZONTAL_PADDING = 32;
    private static final double THUMB_SIZE = 74;

    private final Map<String, SectionContent> sections = buildSections();
    private final List<AdminRow> liveUserRows = new ArrayList<>();
    private final List<AdminRow> liveItemRows = new ArrayList<>();
    private final List<AdminRow> liveAuctionRows = new ArrayList<>();
    private final Map<String, Image> adminImageCache = new HashMap<>();

    private List<AdminRow> incomingUserRows = new ArrayList<>();
    private List<AdminRow> incomingItemRows = new ArrayList<>();
    private List<AdminRow> incomingAuctionRows = new ArrayList<>();

    private boolean usersLoaded;
    private boolean itemsLoaded;
    private boolean auctionsLoaded;
    private String currentSectionKey = "dashboard";
    private String activeFilter = "All";

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
                renderWorkspace(currentSectionKey, activeFilter);
            });
        }

        if (dataRowsBox != null) {
            dataRowsBox.setFillWidth(true);
        }

        hideAdminDescriptions();
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
    protected void showSection(String sectionKey) {
        currentSectionKey = sectionKey;
        activeFilter = getDefaultFilter(sectionKey);

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
                Platform.runLater(() -> notifUIHandler.handle(message));
                return;
            }

            if (message.startsWith("ADMIN_")) {
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
            // CREATE_ITEM broadcast from server: refresh items so Pending Approval updates live.
            adminNetworkManager.send("ADMIN_LIST_ITEMS");
            return;
        }

        if (msg.equals("ADMIN_APPROVE_SUCCESS")) {
            requestLiveAdminData();
            showDetail(
                "Item approved",
                "Database đã chuyển item từ PENDING_REVIEW sang AVAILABLE. "
                    + "Create Auction đang mở ở chế độ UI preview, chưa ghi bảng auctions."
            );
            return;
        }

        if (msg.startsWith("ADMIN_APPROVE_FAIL")) {
            showDetail(
                "Approve failed",
                "Server không approve được item. Có thể item không còn ở PENDING_REVIEW."
            );
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
            + " (" + itemName +
            "). Dữ liệu lấy từ bảng auctions join items/users/bids của cloud database.";

        if (normalize(status).equals("running") || normalize(status).equals("open")) {
            return row(
                "AUC-" + auctionId + " - " + itemName,
                meta,
                currentPrice,
                bidCount + " bids",
                status,
                detail,
                "View",
                "Cancel",
                "Copy ID"
            );
        }

        return row(
            "AUC-" + auctionId + " - " + itemName,
            meta,
            currentPrice,
            bidCount + " bids",
            status,
            detail,
            "View",
            "Copy ID"
        );
    }

    private String[] actionsForUser(String role, String status) {
        String normalizedRole = normalize(role);
        String normalizedStatus = normalize(status);

        if (normalizedRole.equals("admin")) {
            return new String[]{"View"};
        }

        if (normalizedStatus.equals("active")) {
            return new String[]{"View", "Suspend"};
        }

        if (normalizedStatus.equals("suspended")) {
            return new String[]{"View", "Restore", "Ban"};
        }

        return new String[]{"View"};
    }

    private String[] actionsForItem(String status, String auctionId) {
        String normalizedStatus = normalize(status);
        boolean hasAuction = auctionId != null && !auctionId.isBlank();

        if (normalizedStatus.equals("pending review")) {
            return new String[]{"Review"};
        }

        if (normalizedStatus.equals("available")) {
            return new String[]{"Create Auction", "View"};
        }

        if ((normalizedStatus.equals("in auction") || normalizedStatus.equals("sold"))
            && hasAuction) {
            return new String[]{"View Auction", "View"};
        }

        return new String[]{"View"};
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

    private Map<String, SectionContent> buildSections() {
        Map<String, SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", page(
            "Admin Dashboard",
            "Monitor live auctions, pending item reviews, user accounts, and platform activity.",
            "Control Center",
            "A practical admin workspace for auction health, review queues, and recent operational " +
                "events.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Users", "Running Auctions", "Listed Items", "Total Bids"},
            new String[]{"Live auction health", "Moderation queue", "Recent activity"},
            new String[]{
                "Track RUNNING auctions, current prices, bid counts, and ending-soon sessions.",
                "Review AVAILABLE items, flagged auctions, suspended accounts, and payment follow-ups.",
                "Surface audit events, bid events, auction status changes, and item updates."
            },
            new String[]{
                "Admin tables now prefer shared database data when the server is available.",
                "Item Review and Auction Sessions stay linked by ITEM/AUC IDs.",
                "User Accounts are requested from the shared cloud database.",
                "Fallback demo rows stay available when the server is offline."
            }
        ));

        map.put("users", page(
            "Users",
            "Manage user accounts, role scopes, and status actions from one screen.",
            "User Management",
            "Inspect accounts from the shared cloud database with ACTIVE, SUSPENDED, and BANNED " +
                "states.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Total Users", "Active", "Suspended", "Banned"},
            new String[]{"Account table", "Role controls", "Risk review"},
            new String[]{
                "Search users by username, email, role, or account status.",
                "Rows are populated from the server database instead of static fake accounts.",
                "Keep SUSPENDED and BANNED accounts visible for audit and restore workflows."
            },
            new String[]{
                "Filter by ACTIVE, SUSPENDED, or BANNED before batch review.",
                "Open a user row to inspect wallet, auctions, bids, and account history.",
                "Use Suspend, Restore, or Ban only after checking recent activity.",
                "Role changes stay inside the user detail flow, not as a quick row action."
            }
        ));

        map.put("auctions", page(
            "Auctions",
            "Supervise auction sessions, timing, bid activity, and intervention actions.",
            "Auction Monitoring",
            "The list is for quick monitoring. Use View to open a focused auction detail screen " +
                "with edit, payment, bids, and logs.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Auctions", "Running", "Open", "Finished/Paid"},
            new String[]{"Auction table", "Linked item", "Bid history"},
            new String[]{
                "Auction rows are linked to item rows by ITEM ID.",
                "Every auction row keeps item ID and seller visible so admin can trace ownership.",
                "Bid count and current price are surfaced for abnormal bidding checks."
            },
            new String[]{
                "RUNNING auctions show current price, bid count, and end-time pressure.",
                "OPEN auctions are scheduled or ready before the bidding window starts.",
                "FINISHED and PAID auctions need winner/payment follow-up.",
                "CANCELED auctions remain visible for audit and dispute review."
            }
        ));

        map.put("items", page(
            "Item Review",
            "Review listed items, item statuses, categories, seller links, and auction readiness.",
            "Item Review & Catalog",
            "Items are seller listings. Auctions are bidding sessions created from approved items.",
            new String[]{"0", "0", "0", "0"},
            new String[]{"Items", "Available", "In Auction", "Sold/Removed"},
            new String[]{"Item table", "Auction linkage", "Catalog quality"},
            new String[]{
                "Manage DRAFT, AVAILABLE, IN_AUCTION, SOLD, and REMOVED item states.",
                "Approved AVAILABLE items can be converted into OPEN auction sessions.",
                "Flagged or REMOVED items stay visible for moderation notes and seller review."
            },
            new String[]{
                "AVAILABLE items are ready for Create Auction after admin inspection.",
                "IN_AUCTION items should link back to their active auction session.",
                "SOLD items should match a FINISHED or PAID auction record.",
                "REMOVED items should keep a reason for audit history."
            }
        ));

        map.put("reports", page(
            "Reports",
            "Track auction KPIs, bidding activity, growth signals, and export-ready summaries.",
            "Reporting Workspace",
            "Reports focus on analytics: date range, KPIs, trends, top records, and exports.",
            new String[]{"7d", "912", "92%", "+14%"},
            new String[]{"Range", "Bid Volume", "Completion", "Growth"},
            new String[]{"KPI review", "Trend breakdown", "Export tools"},
            new String[]{
                "Use weekly and monthly ranges to compare auction volume and closing health.",
                "Summarize bids, completed auctions, revenue proxy, and flagged cases.",
                "Prepare CSV/PDF export hooks once backend report endpoints are available."
            },
            new String[]{
                "Bid volume increased by 14% compared with the previous 7-day range.",
                "92% of ending auctions reached FINISHED, PAID, or payment follow-up states.",
                "Top sellers and top categories should sit below KPI cards for quick review.",
                "Exports should use the selected date range and current filters."
            }
        ));

        map.put("settings", page(
            "Settings",
            "Configure auction rules, access controls, item rules, moderation, and admin preferences.",
            "System Settings",
            "Settings are configuration groups with clear Edit/Save workflows instead of " +
                "dashboard-style row menus.",
            new String[]{"05", "05", "03", "04"},
            new String[]{"Auction States", "Item States", "User Roles", "Bid States"},
            new String[]{"Auction rules", "Access control", "Moderation rules"},
            new String[]{
                "Minimum bid increment, reserve price, anti-sniping window, and auto-close behaviour.",
                "ADMIN and USER scopes should be explicit and easy to audit.",
                "Flag suspicious bidding, removed items, and suspended accounts for review."
            },
            new String[]{
                "Auction status flow: OPEN -> RUNNING -> FINISHED -> PAID or CANCELED.",
                "Item status flow: DRAFT -> AVAILABLE -> IN_AUCTION -> SOLD or REMOVED.",
                "User statuses remain ACTIVE, SUSPENDED, and BANNED for account control.",
                "Bid statuses remain WINNING, OUTBID, WON, and LOST."
            }
        ));

        return map;
    }

    private SectionContent page(
        String title,
        String subtitle,
        String surfaceTitle,
        String surfaceDescription,
        String[] statValues,
        String[] statLabels,
        String[] featureTitles,
        String[] featureDescriptions,
        String[] activityLines) {
        return new SectionContent(
            title,
            "",
            surfaceTitle,
            "",
            "",
            "",
            statValues,
            statLabels,
            featureTitles,
            featureDescriptions,
            activityLines,
            new String[0],
            new String[0],
            new String[0]
        );
    }

    private String getDefaultFilter(String sectionKey) {
        return switch (sectionKey) {
            case "dashboard" -> "Overview";
            case "reports" -> "Last 7 days";
            case "items" -> "All";
            case "settings" -> "General";
            default -> "All";
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
                "Request latest users from shared cloud database."
            );
            case "auctions" -> configurePrimaryAction(
                "Create Auction",
                "Create auction",
                "Create an OPEN auction from an AVAILABLE item after admin review."
            );
            case "items" -> configurePrimaryAction(
                "Refresh Items",
                "Refresh items",
                "Request latest item rows from the shared cloud database."
            );
            case "reports" -> configurePrimaryAction(
                "Export CSV",
                "Export reports",
                "Export the selected report range as CSV when report endpoints are wired."
            );
            case "settings" -> configurePrimaryAction(
                "Save Settings",
                "Save settings",
                "Persist auction rules, access defaults, and moderation preferences."
            );
            default -> configurePrimaryAction(
                "Refresh",
                "Refresh dashboard",
                "Refresh operational counters and review queues."
            );
        }
    }

    private void configurePrimaryAction(String text, String detailTitle, String detailDescription) {
        primaryActionButton.setText(text);
        primaryActionButton.setOnAction(event -> {
            if ("Refresh Users".equals(text)
                || "Refresh Items".equals(text)
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
            case "reports" -> renderReports(filter);
            case "settings" -> renderSettings(filter);
            default -> renderDashboard(filter);
        }
    }

    private void renderDashboard(String filter) {
        setTableTitle("Operational Queue");
        renderChips(filter, "Overview", "Ending soon", "Needs review", "Audit log");
        updateDashboardStats();

        addHeader("Focus", "Count", "Signal");

        int runningCount = countStatus(currentAuctionRows(), "RUNNING");
        int availableCount = countStatus(currentItemRows(), "AVAILABLE");
        int riskUsers = countStatus(currentUserRows(), "SUSPENDED") +
            countStatus(currentUserRows(), "BANNED");
        int paymentFollowUp = countStatus(currentAuctionRows(), "FINISHED");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Ending soon auctions",
            "RUNNING sessions with high time pressure",
            String.valueOf(runningCount),
            "Live",
            "RUNNING",
            "Review auctions close to closing. Check current price, last bid time, and bid count " +
                "before they move to FINISHED.",
            "View"
        ));
        rows.add(row(
            "Item review queue",
            "AVAILABLE items not yet linked to auction",
            String.valueOf(availableCount),
            "Ready",
            "AVAILABLE",
            "These items can become OPEN auction sessions after category, seller, and starting " +
                "price checks.",
            "Review"
        ));
        rows.add(row(
            "Account audit queue",
            "SUSPENDED and BANNED accounts",
            String.valueOf(riskUsers),
            "Audit",
            "SUSPENDED",
            "Review account history, bid activity, and seller records before Restore or Ban actions.",
            "Open"
        ));
        rows.add(row(
            "Payment follow-up",
            "FINISHED auctions waiting payment flow",
            String.valueOf(paymentFollowUp),
            "Due",
            "FINISHED",
            "Check winner, current price, payment status, and seller fulfilment before marking as " +
                "PAID.",
            "Inspect"
        ));

        addFilteredRows(rows, filter);

        showDetail("Control Center ready", dataSourceNote() + " Filter đang áp dụng: " + filter + ".");
    }

    private void renderUsers(String filter) {
        setTableTitle("User Accounts");
        renderChips(filter, "All", "ACTIVE", "SUSPENDED", "BANNED", "USER", "ADMIN");

        List<AdminRow> rows = currentUserRows();
        updateStats(
            new String[] {
                String.valueOf(rows.size()),
                String.valueOf(countStatus(rows, "ACTIVE")),
                String.valueOf(countStatus(rows, "SUSPENDED")),
                String.valueOf(countStatus(rows, "BANNED"))
            },
            new String[]{"Total Users", "Active", "Suspended", "Banned"}
        );

        addHeader("Account", "Role", "Last Login");
        addFilteredRows(rows, filter);

        showDetail(
            "User management",
            dataSourceNote(usersLoaded) + " Filter đang áp dụng: " + filter + "."
        );
    }

    private void renderAuctions(String filter) {
        setTableTitle("Auction Sessions");
        renderChips(filter, "All", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED", "Ending soon");

        List<AdminRow> rows = currentAuctionRows();
        updateStats(
            new String[] {
                String.valueOf(rows.size()),
                String.valueOf(countStatus(rows, "RUNNING")),
                String.valueOf(countStatus(rows, "OPEN")),
                String.valueOf(countStatus(rows, "FINISHED") + countStatus(rows, "PAID"))
            },
            new String[]{"Auctions", "Running", "Open", "Finished/Paid"}
        );

        addHeader("Auction", "Current Price", "Bids");
        addFilteredRows(rows, filter);

        showDetail(
            "Auction links",
            dataSourceNote(auctionsLoaded) +
                " Bấm View để mở màn Auction Detail riêng trong admin workspace."
        );
    }

    private void renderItems(String filter) {
        setTableTitle("Item Review");
        renderChips(
            filter,
            "All",
            "PENDING_REVIEW",
            "AVAILABLE",
            "IN_AUCTION",
            "SOLD",
            "REMOVED",
            "DRAFT",
            "No Auction"
        );

        List<AdminRow> rows = currentItemRows();
        updateStats(
            new String[] {
                String.valueOf(rows.size()),
                String.valueOf(countStatus(rows, "PENDING_REVIEW")),
                String.valueOf(countStatus(rows, "AVAILABLE")),
                String.valueOf(countStatus(rows, "IN_AUCTION"))
            },
            new String[]{"Items", "Pending", "Available", "In Auction"}
        );

        addHeader("Item", "Category", "Starting Price", "Status", "Action");
        addFilteredRows(rows, filter);

        showDetail(
            "Item review",
            dataSourceNote(itemsLoaded) +
                " Table đang đọc items thật từ database và lọc theo đúng enum trạng thái: "
                    + "DRAFT, PENDING_REVIEW, AVAILABLE, IN_AUCTION, SOLD, REMOVED."
        );
    }

    private void renderReports(String filter) {
        setTableTitle("Analytics & Exports");
        renderChips(filter, "Last 7 days", "Last 30 days", "Auctions", "Bids", "Sellers", "Export");

        addHeader("Report", "Value", "Trend");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Bid volume",
            "Bids report - Total bids placed in selected range",
            "912 bids",
            "+14%",
            "UP",
            "Aggregate BidHistoryDTO rows by bidTime and status to show WINNING, OUTBID, WON, LOST " +
                "changes.",
            "Export"
        ));
        rows.add(row(
            "Auction completion",
            "Auctions report - Finished or paid auctions vs ended sessions",
            "92%",
            "Stable",
            "HEALTHY",
            "Compare FINISHED and PAID auctions against CANCELED auctions for the selected date range.",
            "Export"
        ));
        rows.add(row(
            "Seller performance",
            "Sellers report - Top sellers by completed auctions",
            "18 sellers",
            "+6%",
            "UP",
            "Rank sellers by completed auctions, paid auctions, and item quality.",
            "Export"
        ));
        rows.add(row(
            "Revenue proxy",
            "Auctions report - Sum currentPrice from FINISHED and PAID auctions",
            "186,500,000 VND",
            "+8%",
            "UP",
            "Use currentPrice from closed auctions until payment/revenue services are wired.",
            "Export"
        ));
        rows.add(row(
            "Risk queue",
            "Auctions, removed items, suspended accounts",
            "18 records",
            "Review",
            "FLAGGED",
            "Combine CANCELED auctions, REMOVED items, and SUSPENDED/BANNED users into an admin " +
                "risk summary.",
            "Export"
        ));

        addFilteredRows(rows, filter);

        showDetail(
            "Reports workspace",
            "Filter đang áp dụng: " + filter +
                ". Reports dùng một nút Export duy nhất trên từng row, không cần menu phụ."
        );
    }

    private void renderSettings(String filter) {
        setTableTitle("Configuration Groups");
        renderChips(
            filter,
            "General",
            "Auction Rules",
            "Roles",
            "Items",
            "Moderation",
            "Notifications"
        );

        addHeader("Setting", "Current Rule", "Scope");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "General settings",
            "General platform preferences and admin defaults",
            "Configured",
            "General",
            "READY",
            "General settings for admin dashboard defaults.",
            "Edit"
        ));
        rows.add(row(
            "Auction rules",
            "Minimum increment, reserve price, anti-sniping, auto-close",
            "Configured",
            "Auctions",
            "READY",
            "Auction flow stays OPEN -> RUNNING -> FINISHED -> PAID, with CANCELED for admin " +
                "intervention.",
            "Edit"
        ));
        rows.add(row(
            "Roles & permissions",
            "ADMIN manages platform, USER can bid/list based on feature flow",
            "2 roles",
            "Access",
            "ADMIN",
            "Destructive actions should be restricted to ADMIN.",
            "Edit"
        ));
        rows.add(row(
            "Item rules",
            "Only AVAILABLE items can become OPEN auctions",
            "5 states",
            "Catalog",
            "READY",
            "Item flow stays DRAFT -> AVAILABLE -> IN_AUCTION -> SOLD, with REMOVED for " +
                "moderation/audit.",
            "Edit"
        ));
        rows.add(row(
            "Moderation triggers",
            "Suspicious bidding, removed content, account violations",
            "Enabled",
            "System",
            "ACTIVE",
            "Suspicious bids should surface without hiding original BidHistoryDTO records.",
            "Edit"
        ));
        rows.add(row(
            "Notification settings",
            "Email alerts, report reminders, auction warnings",
            "Prepared",
            "System",
            "READY",
            "Notification settings for auction ending, payment, and moderation events.",
            "Edit"
        ));

        addFilteredRows(rows, filter);

        showDetail(
            "Settings workspace",
            "Filter đang áp dụng: " + filter +
                ". Settings dùng một nút Edit duy nhất trên từng row, không cần menu phụ."
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
            "Loading users from cloud database...",
            "Server command ADMIN_LIST_USERS chưa trả dữ liệu. Kiểm tra server đã copy " +
                "ClientHandler.java mới và restart chưa.",
            "DB",
            "Waiting",
            "LOADING",
            "Admin-home sẽ không hiển thị user giả nữa. Khi server phản hồi, bảng này sẽ hiện đúng " +
                "các account trong bảng users như ngoc, môn_lý, siunhangao, chau, my, t1win.",
            "Refresh"
        ));
        return rows;
    }

    private List<AdminRow> defaultAuctionRows() {
        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Loading auctions from cloud database...",
            "Server command ADMIN_LIST_AUCTIONS chưa trả dữ liệu. Khi server phản hồi, bảng này " +
                "join auctions với items/users/bids thật.",
            "DB",
            "Waiting",
            "LOADING",
            "Không dùng auction giả hard-code nữa. Auction Sessions sẽ lấy từ bảng auctions và " +
                "liên kết item bằng item_id.",
            "Refresh"
        ));
        return rows;
    }

    private List<AdminRow> defaultItemRows() {
        List<AdminRow> rows = new ArrayList<>();
        rows.add(row(
            "Loading items from cloud database...",
            "Server command ADMIN_LIST_ITEMS chưa trả dữ liệu. Khi server phản hồi, bảng này đọc " +
                "trực tiếp items do user tạo và join auction nếu có.",
            "DB",
            "Waiting",
            "LOADING",
            "Không dùng item giả hard-code nữa. Item Review sẽ lấy từ bảng items và liên kết sang " +
                "auctions bằng item_id.",
            "Refresh"
        ));
        return rows;
    }

    private void updateDashboardStats() {
        List<AdminRow> users = currentUserRows();
        List<AdminRow> auctions = currentAuctionRows();
        List<AdminRow> items = currentItemRows();
        updateStats(
            new String[] {
                String.valueOf(users.size()),
                String.valueOf(countStatus(auctions, "RUNNING")),
                String.valueOf(items.size()),
                String.valueOf(totalBidCount(auctions))
            },
            new String[]{"Users", "Running Auctions", "Listed Items", "Total Bids"}
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
            : "Đang dùng fallback demo rows trong lúc chờ server/cloud database.";
    }

    private String dataSourceNote(boolean loaded) {
        return loaded
            ? "Đang dùng dữ liệu từ shared cloud database."
            : "Đang dùng fallback demo rows trong lúc chờ server/cloud database.";
    }

    private void setTableTitle(String title) {
        if (tableTitleLabel != null) {
            tableTitleLabel.setText(title);
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

            chip.setOnAction(event -> {
                activeFilter = labelText;
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
        header.add(secondHeader, 2, 0);
        header.add(statusHeader, 3, 0);
        header.add(actionHeader, 4, 0);

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

        ColumnConstraints mainColumn = percentColumn(42);
        ColumnConstraints firstColumn = percentColumn(13);
        ColumnConstraints secondColumn = percentColumn(15);
        ColumnConstraints statusColumn = percentColumn(15);
        ColumnConstraints actionColumn = percentColumn(15);

        grid.getColumnConstraints().addAll(
            mainColumn,
            firstColumn,
            secondColumn,
            statusColumn,
            actionColumn
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
        int count = 0;

        for (AdminRow row : rows) {
            if (matchesFilter(row, filter)) {
                addRow(row);
                count++;
            }
        }

        if (count == 0) {
            addEmptyRow(filter);
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
            || normalized.equals("overview")
            || normalized.equals("last 7 days")
            || normalized.equals("general");
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

    private void addEmptyRow(String filter) {
        GridPane row = createTableGrid("data-row");

        Label empty = new Label("No records found for filter: " + filter);
        empty.getStyleClass().add("row-meta");
        empty.setWrapText(true);
        empty.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(empty, 5);

        row.add(empty, 0, 0);
        dataRowsBox.getChildren().add(row);
    }

    private void addRow(AdminRow data) {
        GridPane row = createTableGrid("data-row");
        row.setOnMouseClicked(event -> {
            if (data.itemData) {
                openItemReview(data);
                return;
            }
            showDetail(data.title, data.detail);
        });

        VBox mainCell = new VBox(2);
        mainCell.setMinWidth(0);
        mainCell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(mainCell, Priority.ALWAYS);

        if (data.actions.length > 0) {
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

    private String resolvePrimaryAction(AdminRow data) {
        return data.actions.length > 0 ? data.actions[0] : "Open";
    }

    private GridPane rowActions(AdminRow data) {
        GridPane actions = new GridPane();
        actions.setHgap(ACTION_GAP);
        actions.setAlignment(Pos.CENTER);
        actions.setMinWidth(0);
        actions.setMaxWidth(Double.MAX_VALUE);

        actions.getColumnConstraints().addAll(
            fixedColumn(ACTION_PRIMARY_WIDTH),
            fixedColumn(ACTION_MORE_WIDTH)
        );

        if (data.actions.length == 0) {
            Region emptyAction = new Region();
            emptyAction.setMinWidth(ACTION_PRIMARY_WIDTH + ACTION_MORE_WIDTH + ACTION_GAP);
            emptyAction.setPrefWidth(ACTION_PRIMARY_WIDTH + ACTION_MORE_WIDTH + ACTION_GAP);
            GridPane.setColumnSpan(emptyAction, 2);
            actions.add(emptyAction, 0, 0);
            return actions;
        }

        String primaryAction = resolvePrimaryAction(data);

        Button primary = new Button(primaryAction);
        primary.setMnemonicParsing(false);
        primary.getStyleClass().add("mini-action-btn");
        primary.setMinWidth(ACTION_PRIMARY_WIDTH);
        primary.setPrefWidth(ACTION_PRIMARY_WIDTH);
        primary.setMaxWidth(ACTION_PRIMARY_WIDTH);
        primary.setTextOverrun(OverrunStyle.ELLIPSIS);
        primary.setOnAction(event -> handlePrimaryAction(primaryAction, data));

        actions.add(primary, 0, 0);
        GridPane.setHalignment(primary, HPos.CENTER);

        if (data.actions.length > 1) {
            MenuButton more = new MenuButton("...");
            more.setMnemonicParsing(false);
            more.getStyleClass().add("more-action-btn");
            more.setMinWidth(ACTION_MORE_WIDTH);
            more.setPrefWidth(ACTION_MORE_WIDTH);
            more.setMaxWidth(ACTION_MORE_WIDTH);

            for (int i = 1; i < data.actions.length; i++) {
                String action = data.actions[i];
                MenuItem item = new MenuItem(action);
                item.setOnAction(event -> handlePrimaryAction(action, data));
                more.getItems().add(item);
            }

            actions.add(more, 1, 0);
            GridPane.setHalignment(more, HPos.CENTER);
        } else {
            Region placeholder = new Region();
            placeholder.setMinWidth(ACTION_MORE_WIDTH);
            placeholder.setPrefWidth(ACTION_MORE_WIDTH);
            placeholder.setMaxWidth(ACTION_MORE_WIDTH);
            placeholder.setOpacity(0);
            actions.add(placeholder, 1, 0);
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

        if (normalizedAction.equals("create auction") && data.itemData) {
            openCreateAuctionDraft(data);
            return;
        }

        if ("users".equals(currentSectionKey) && normalizedAction.equals("view")) {
            showDetail(
                data.title,
                data.detail +
                    " Role changes nằm trong detail flow, không đặt thành quick action ngoài table."
            );
            return;
        }

        showDetail(action + " - " + data.title, data.detail);
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
        addDetailAction("Edit Auction", data);
        addDetailAction("Payment", data);
        addDetailAction("Bid History", data);
        addDetailAction("Timeline / Logs", data);

        addDetailHero(data.title, data.status, data.firstValue, data.secondValue, data.meta);
        addDetailBlock("Overview", "Status: " + data.status, "Current price: " +
            data.firstValue, "Bids: " +
            data.secondValue, "Session health: check timing, last bid, and abnormal activity " +
            "before intervention.");
        addDetailBlock("Item", extractItemId(data.meta), "Seller: " +
            extractSeller(data.meta), "Auction detail stays linked to Item Review through ITEM ID.");
        addDetailBlock("Bids", "Latest activity: " +
            data.secondValue, "Use this tab for bid history, winning bid, outbid chain, and " +
            "suspicious bid review.");
        addDetailBlock(
            "Payment",
            "For FINISHED/PAID auctions, inspect winner, payment pending/paid state, "
                + "and seller fulfilment."
        );
        addDetailBlock(
            "Timeline / Logs",
            "Show admin actions, status changes, cancel reason, refund/wallet release notes, "
                + "and audit trail."
        );

        showDetail(
            data.title,
            "Đang ở màn Auction Detail. Edit, Payment, Bids và Logs nằm trong màn này thay vì nhét " +
                "hết vào table."
        );
    }

    private void openLinkedAuctionDetail(AdminRow itemData) {
        String auctionId = itemData.secondValue == null ? "AUC detail" : itemData.secondValue;
        AdminRow linkedAuction = row(
            auctionId + " - Linked from " + itemData.title,
            itemData.meta,
            "From item",
            "Linked",
            itemData.status,
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

        VBox shell = new VBox(14);
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
        VBox card = new VBox(13);
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
        addInfoCell(facts, 0, 2, "Auction link", fallback(data.secondValue, "No auction"));
        addInfoCell(facts, 1, 2, "Database status", data.status);

        VBox attributesBox = buildAttributesBox(data.attributes);
        HBox actions = buildItemDetailActions(data);

        card.getChildren().addAll(statusLine, name, price, facts, attributesBox, actions);
        return card;
    }

    private HBox buildItemDetailActions(AdminRow data) {
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        String normalizedStatus = normalize(data.status);

        if (normalizedStatus.equals("pending review")) {
            Button reject = new Button("Reject later");
            reject.setMnemonicParsing(false);
            reject.getStyleClass().add("create-secondary-btn");
            reject.setDisable(true);

            Button accept = new Button("Accept & Create Auction");
            accept.setMnemonicParsing(false);
            accept.getStyleClass().add("create-primary-btn");
            accept.setOnAction(event -> handleAcceptAndOpenCreateAuction(data));
            actions.getChildren().addAll(reject, accept);
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

        if (data.auctionId != null && !data.auctionId.isBlank()) {
            Button viewAuction = new Button("View Auction");
            viewAuction.setMnemonicParsing(false);
            viewAuction.getStyleClass().add("create-primary-btn");
            viewAuction.setOnAction(event -> openLinkedAuctionDetail(data));
            actions.getChildren().add(viewAuction);
            return actions;
        }

        Button noAction = new Button("No direct action");
        noAction.setMnemonicParsing(false);
        noAction.getStyleClass().add("admin-disabled-primary-btn");
        noAction.setDisable(true);
        actions.getChildren().add(noAction);
        return actions;
    }

    private void handleAcceptAndOpenCreateAuction(AdminRow data) {
        if (adminNetworkManager != null && data.itemId != null && !data.itemId.isBlank()) {
            adminNetworkManager.send("ADMIN_APPROVE_ITEM " + data.itemId);
        }
        openCreateAuctionDraft(data);
    }

    private void openCreateAuctionDraft(AdminRow data) {
        currentSectionKey = "createAuction";
        setTableTitle("Create Auction Room");
        clearWorkspaceForDetail();
        addBackButton("Back to Pending Items", "items");

        VBox shell = new VBox(14);
        shell.getStyleClass().add("admin-workspace-shell");

        Label title = new Label("Create Auction Room");
        title.getStyleClass().add("admin-page-title");

        Label note = new Label(
            "UI này bám đúng schema auctions: item_id, seller_id, start/end time, "
                + "min_bid_increment, reserve_price, snipe window và current_price. "
                + "Nút Create Auction đang disable nên chưa ghi database."
        );
        note.getStyleClass().add("admin-page-note");
        note.setWrapText(true);

        GridPane content = createDetailGrid();
        VBox gallery = buildItemGallery(data, AUCTION_IMAGE_HEIGHT, "Item preview");
        VBox form = buildCreateAuctionForm(data);
        content.add(gallery, 0, 0);
        content.add(form, 1, 0);
        keepDetailGridChildrenResizable(gallery, form);

        shell.getChildren().addAll(title, note, content);
        dataRowsBox.getChildren().add(shell);

        showDetail(
            "Create auction preview",
            "Item đã được đưa vào màn tạo auction trong admin-home. Create Auction chưa được wire."
        );
    }

    private VBox buildCreateAuctionForm(AdminRow data) {
        VBox card = new VBox(13);
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
        addFormCell(formGrid, 0, 0, "Start time", "yyyy-MM-dd HH:mm:ss");
        addFormCell(formGrid, 1, 0, "End time", "yyyy-MM-dd HH:mm:ss");
        addFormCell(formGrid, 0, 1, "Minimum bid increment", "Example: 10000");
        addFormCell(formGrid, 1, 1, "Reserve price", stripCurrency(data.startingPrice));
        addFormCell(formGrid, 0, 2, "Snipe window seconds", "300");
        addFormCell(formGrid, 1, 2, "Snipe extension seconds", "60");

        Button create = new Button("Create Auction");
        create.setMnemonicParsing(false);
        create.getStyleClass().add("admin-disabled-primary-btn");
        create.setDisable(true);

        Label disabledNote = new Label(
            "Create Auction đang bị disable: phần này mới dựng UI, chưa insert vào bảng auctions."
        );
        disabledNote.getStyleClass().add("admin-page-note");
        disabledNote.setWrapText(true);

        card.getChildren().addAll(titleLine, name, price, facts, formGrid, create, disabledNote);
        return card;
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

        VBox gallery = new VBox(10);
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

        if (images.isEmpty()) {
            StackPane emptyThumb = new StackPane(new Label("No image"));
            emptyThumb.getStyleClass().add("item-thumb-empty");
            emptyThumb.setMinSize(THUMB_SIZE, THUMB_SIZE);
            emptyThumb.setPrefSize(THUMB_SIZE, THUMB_SIZE);
            emptyThumb.setMaxSize(THUMB_SIZE, THUMB_SIZE);
            thumbs.getChildren().add(emptyThumb);
        } else {
            for (String imageUrl : images) {
                StackPane thumb = buildThumb(imageUrl, mainFrame);
                thumbs.getChildren().add(thumb);
            }
        }

        if (title == null) {
            gallery.getChildren().addAll(mainFrame, thumbs);
        } else {
            gallery.getChildren().addAll(title, mainFrame, thumbs);
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

    private void addFormCell(GridPane grid, int column, int row, String label, String prompt) {
        VBox box = new VBox(5);
        Label key = new Label(label);
        key.getStyleClass().add("create-field-label");
        TextField field = new TextField();
        field.getStyleClass().add("admin-auction-field");
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().addAll(key, field);
        grid.add(box, column, row);
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

    private static class AdminRow {
        private final String title;
        private final String meta;
        private final String firstValue;
        private final String secondValue;
        private final String status;
        private final String detail;
        private final String[] actions;
        private final boolean itemData;
        private final String itemId;
        private final String sellerId;
        private final String sellerName;
        private final String itemName;
        private final String description;
        private final String startingPrice;
        private final String createdAt;
        private final String auctionId;
        private final String auctionStatus;
        private final String currentPrice;
        private final String bidCount;
        private final String imagePayload;
        private final String attributes;

        private AdminRow(
            String title,
            String meta,
            String firstValue,
            String secondValue,
            String status,
            String detail,
            String... actions) {
            this.title = title;
            this.meta = meta;
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.status = status;
            this.detail = detail;
            this.actions = actions;
            this.itemData = false;
            this.itemId = "";
            this.sellerId = "";
            this.sellerName = "";
            this.itemName = title;
            this.description = detail;
            this.startingPrice = secondValue;
            this.createdAt = "";
            this.auctionId = "";
            this.auctionStatus = "";
            this.currentPrice = "";
            this.bidCount = "";
            this.imagePayload = "";
            this.attributes = "";
        }

        private AdminRow(
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
            this.title = title;
            this.meta = meta;
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.status = status;
            this.detail = detail;
            this.actions = actions;
            this.itemData = true;
            this.itemId = itemId;
            this.sellerId = sellerId;
            this.sellerName = sellerName;
            this.itemName = itemName;
            this.description = description;
            this.startingPrice = startingPrice;
            this.createdAt = createdAt;
            this.auctionId = auctionId;
            this.auctionStatus = auctionStatus;
            this.currentPrice = currentPrice;
            this.bidCount = bidCount;
            this.imagePayload = imagePayload;
            this.attributes = attributes;
        }
    }
}
