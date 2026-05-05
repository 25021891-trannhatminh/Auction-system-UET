package client.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AdminDashboardController extends BaseDashboardController {

    @FXML private FlowPane adminActionBar;
    @FXML private VBox dataRowsBox;
    @FXML private Label tableTitleLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailDescriptionLabel;
    @FXML private Button primaryActionButton;

    private final Map<String, SectionContent> sections = buildSections();

    private String currentSectionKey = "dashboard";
    private String activeFilter = "All";

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

        super.showSection(sectionKey);
        updatePrimaryAction(sectionKey);
        renderWorkspace(sectionKey, activeFilter);
    }

    private Map<String, SectionContent> buildSections() {
        Map<String, SectionContent> map = new LinkedHashMap<>();

        map.put("dashboard", page(
                "Admin Dashboard",
                "Monitor live auctions, pending item reviews, user accounts, and platform activity.",
                "Control Center",
                "A practical admin workspace for auction health, review queues, and recent operational events.",
                new String[]{"248", "31", "142", "912"},
                new String[]{"Users", "Running Auctions", "Listed Items", "Total Bids"},
                new String[]{"Live auction health", "Moderation queue", "Recent activity"},
                new String[]{
                        "Track RUNNING auctions, current prices, bid counts, and ending-soon sessions.",
                        "Review AVAILABLE items, flagged auctions, suspended accounts, and payment follow-ups.",
                        "Surface audit events, bid events, auction status changes, and item updates."
                },
                new String[]{
                        "6 AVAILABLE items are ready for auction creation.",
                        "4 RUNNING auctions need attention before closing.",
                        "11 SUSPENDED accounts are waiting for audit.",
                        "3 FINISHED auctions need payment follow-up."
                }
        ));

        map.put("users", page(
                "Users",
                "Manage user accounts, role scopes, and status actions from one screen.",
                "User Management",
                "Inspect BIDDER, SELLER, and ADMIN accounts with ACTIVE, SUSPENDED, and BANNED states.",
                new String[]{"248", "229", "11", "08"},
                new String[]{"Total Users", "Active", "Suspended", "Banned"},
                new String[]{"Account table", "Role controls", "Risk review"},
                new String[]{
                        "Search users by username, email, role, or account status.",
                        "Switch between BIDDER, SELLER, and ADMIN views without mixing auction data.",
                        "Keep SUSPENDED and BANNED accounts visible for audit and restore workflows."
                },
                new String[]{
                        "Filter by ACTIVE, SUSPENDED, or BANNED before batch review.",
                        "Open a user row to inspect wallet, auctions, bids, and account history.",
                        "Use Suspend, Restore, or Ban only after checking recent activity.",
                        "Role changes should be limited to trusted ADMIN operations."
                }
        ));

        map.put("auctions", page(
                "Auctions",
                "Supervise auction sessions, timing, bid activity, and intervention actions.",
                "Auction Monitoring",
                "The main view is a table of auction sessions using OPEN, RUNNING, FINISHED, PAID, and CANCELED.",
                new String[]{"31", "18", "09", "04"},
                new String[]{"Running", "Open", "Finished Today", "Flagged"},
                new String[]{"Auction table", "Linked item", "Bid history"},
                new String[]{
                        "Use the auction title or View button to open the auction detail.",
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
                "Items",
                "Review listed items, item statuses, categories, seller links, and auction readiness.",
                "Item Review & Catalog",
                "Items stay separate from auctions so admins can approve, reject, or create an auction only when the item is ready.",
                new String[]{"142", "06", "11", "03"},
                new String[]{"Items", "Available", "Categories", "Removed"},
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
                "Settings are configuration groups with clear Save workflows instead of dashboard-style summaries.",
                new String[]{"05", "05", "03", "04"},
                new String[]{"Auction States", "Item States", "User Roles", "Bid States"},
                new String[]{"Auction rules", "Access control", "Moderation rules"},
                new String[]{
                        "Minimum bid increment, reserve price, anti-sniping window, and auto-close behaviour.",
                        "ADMIN, SELLER, and BIDDER scopes should be explicit and easy to audit.",
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
                subtitle,
                surfaceTitle,
                surfaceDescription,
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
            case "settings" -> "General";
            default -> "All";
        };
    }

    private void updatePrimaryAction(String sectionKey) {
        if (primaryActionButton == null) {
            return;
        }

        switch (sectionKey) {
            case "users" -> configurePrimaryAction("Add User", "Create account", "Prepared for admin-only create-user workflow.");
            case "auctions" -> configurePrimaryAction("Create Auction", "Create auction", "Create an OPEN auction from an AVAILABLE item after admin review.");
            case "items" -> configurePrimaryAction("Review Items", "Review queue", "Open AVAILABLE and REMOVED item review queue.");
            case "reports" -> configurePrimaryAction("Export CSV", "Export reports", "Export the selected report range as CSV when report endpoints are wired.");
            case "settings" -> configurePrimaryAction("Save Settings", "Save settings", "Persist auction rules, access defaults, and moderation preferences.");
            default -> configurePrimaryAction("Refresh", "Refresh dashboard", "Refresh operational counters and review queues.");
        }
    }

    private void configurePrimaryAction(String text, String detailTitle, String detailDescription) {
        primaryActionButton.setText(text);
        primaryActionButton.setOnAction(event -> showDetail(detailTitle, detailDescription));
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

        addHeader("Focus", "Count", "Signal");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("Ending soon auctions", "RUNNING sessions with high time pressure", "4 auctions", "Next 2h", "RUNNING", "Ending soon auctions. Review auctions close to closing. Check current price, last bid time, and bid count before they move to FINISHED.", "View"));
        rows.add(row("Item review queue", "AVAILABLE items not yet linked to auction", "6 items", "Ready", "AVAILABLE", "Needs review. These items can become OPEN auction sessions after category, seller, and starting price checks.", "Review"));
        rows.add(row("Account audit queue", "SUSPENDED and BANNED accounts", "19 users", "Audit", "SUSPENDED", "Audit log. Review account history, bid activity, and seller records before Restore or Ban actions.", "Open"));
        rows.add(row("Payment follow-up", "FINISHED auctions waiting payment flow", "3 auctions", "Due", "FINISHED", "Needs review. Check winner, current price, payment status, and seller fulfilment before marking as PAID.", "Inspect"));

        addFilteredRows(rows, filter);

        showDetail("Control Center ready", "Filter đang áp dụng: " + filter + ". Table sẽ đổi theo filter đang chọn.");
    }

    private void renderUsers(String filter) {
        setTableTitle("User Accounts");
        renderChips(filter, "All", "ACTIVE", "SUSPENDED", "BANNED", "SELLER", "BIDDER", "ADMIN");

        addHeader("Account", "Role", "Last Login");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("admin01", "admin01@auction.local - Full platform control", "ADMIN", "Today 09:20", "ACTIVE", "ADMIN account can access management screens, reports, settings, and intervention actions.", "View", "Edit role"));
        rows.add(row("minh.seller", "minh.seller@mail.com - 8 items, 3 running auctions", "SELLER", "Yesterday", "ACTIVE", "SELLER account with active item ownership. Review seller items and auction records before restriction.", "View", "Suspend"));
        rows.add(row("linh.bidder", "linh.bidder@mail.com - 29 bids, 2 disputed bids", "BIDDER", "2 days ago", "SUSPENDED", "BIDDER account is SUSPENDED. Review dispute notes before Restore or Ban.", "View", "Restore", "Ban"));
        rows.add(row("quang.test", "quang.test@mail.com - blocked after repeated violations", "BIDDER", "Last week", "BANNED", "BANNED account. Keep history for audit and report traceability.", "View"));

        addFilteredRows(rows, filter);

        showDetail("User management", "Filter đang áp dụng: " + filter + ". User table lọc theo role hoặc account status.");
    }

    private void renderAuctions(String filter) {
        setTableTitle("Auction Sessions");
        renderChips(filter, "All", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELED", "Ending soon");

        addHeader("Auction", "Current Price", "Bids");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("AUC-1008 - MacBook Pro M3", "ITEM-1205 - Seller minh.seller - Ending soon - Ends in 42m", "32,500,000 VND", "18 bids", "RUNNING", "Auction detail: itemId ITEM-1205, seller minh.seller, currentPrice 32,500,000 VND, status RUNNING. Open bid history before closing/canceling.", "View", "Edit", "Cancel"));
        rows.add(row("AUC-1011 - Vintage Camera", "ITEM-1210 - Seller camera.store - Starts tomorrow", "4,000,000 VND", "0 bids", "OPEN", "OPEN auction created from an AVAILABLE item. Verify startTime, endTime, reservePrice, and seller.", "View", "Edit"));
        rows.add(row("AUC-0997 - Signed Art Print", "ITEM-1192 - Winner bidder42 - Payment pending", "8,200,000 VND", "11 bids", "FINISHED", "FINISHED auction. Check winner, BidStatus.WON, and pending payment before marking PAID.", "View", "Payment"));
        rows.add(row("AUC-0977 - Mechanical Keyboard", "ITEM-1176 - Winner thanh.user - Paid", "2,400,000 VND", "9 bids", "PAID", "PAID auction. Keep payment and winner information available for report/export.", "View"));
        rows.add(row("AUC-0980 - Gaming Headset", "ITEM-1184 - Canceled after seller issue", "1,100,000 VND", "5 bids", "CANCELED", "CANCELED auction remains available for audit. Confirm refund or wallet release transactions.", "View"));

        addFilteredRows(rows, filter);

        showDetail("Auction links", "Filter đang áp dụng: " + filter + ". Bấm title/View hiện tại vẫn preview; khi có auction-detail.fxml thì đổi sang load page riêng.");
    }

    private void renderItems(String filter) {
        setTableTitle("Item Catalog");
        renderChips(filter, "All", "DRAFT", "AVAILABLE", "IN_AUCTION", "SOLD", "REMOVED", "No auction");

        addHeader("Item", "Category", "Auction Link");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("ITEM-1205 - MacBook Pro M3", "Seller minh.seller - Starting price 24,000,000 VND", "Electronics", "AUC-1008", "IN_AUCTION", "IN_AUCTION item linked to AUC-1008. Open auction for bidding, timing, current price, and winner details.", "View", "View Auction"));
        rows.add(row("ITEM-1210 - Vintage Camera", "Seller camera.store - Starting price 4,000,000 VND - No auction", "Collectibles", "No auction", "AVAILABLE", "AVAILABLE item is approved for auction creation. Create OPEN auction with startTime, endTime, minBidIncrement, reservePrice.", "View", "Create Auction"));
        rows.add(row("ITEM-1214 - Leather Backpack", "Seller city.bag - Missing image validation - No auction", "Fashion", "No auction", "DRAFT", "DRAFT item should not appear in auction sessions. Seller must finish required fields/images.", "View"));
        rows.add(row("ITEM-1195 - Wireless Mouse", "Seller tech.store - Sold through AUC-0965", "Electronics", "AUC-0965", "SOLD", "SOLD item should match FINISHED or PAID auction record.", "View", "View Auction"));
        rows.add(row("ITEM-1199 - Abstract Painting", "Seller art.house - Removed after content report", "Art", "Audit only", "REMOVED", "REMOVED item keeps moderation notes. Do not create auctions from this item.", "Inspect"));

        addFilteredRows(rows, filter);

        showDetail("Item review", "Filter đang áp dụng: " + filter + ". Item table lọc theo item status hoặc auction linkage.");
    }

    private void renderReports(String filter) {
        setTableTitle("Analytics & Exports");
        renderChips(filter, "Last 7 days", "Last 30 days", "Auctions", "Bids", "Sellers", "Export");

        addHeader("Report", "Value", "Trend");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("Bid volume", "Bids report - Total bids placed in selected range - Export", "912 bids", "+14%", "UP", "Aggregate BidHistoryDTO rows by bidTime and status to show WINNING, OUTBID, WON, LOST changes.", "Open", "Export"));
        rows.add(row("Auction completion", "Auctions report - Finished or paid auctions vs ended sessions", "92%", "Stable", "HEALTHY", "Compare FINISHED and PAID auctions against CANCELED auctions for the selected date range.", "Open"));
        rows.add(row("Seller performance", "Sellers report - Top sellers by completed auctions", "18 sellers", "+6%", "UP", "Rank sellers by completed auctions, paid auctions, and item quality.", "Open", "Export"));
        rows.add(row("Revenue proxy", "Auctions report - Export - Sum currentPrice from FINISHED and PAID auctions", "186,500,000 VND", "+8%", "UP", "Use currentPrice from closed auctions until payment/revenue services are wired.", "Open", "Export"));
        rows.add(row("Risk queue", "Auctions, removed items, suspended accounts", "18 records", "Review", "FLAGGED", "Combine CANCELED auctions, REMOVED items, and SUSPENDED/BANNED users into an admin risk summary.", "Open"));

        addFilteredRows(rows, filter);

        showDetail("Reports workspace", "Filter đang áp dụng: " + filter + ". Reports lọc theo nhóm KPI/report.");
    }

    private void renderSettings(String filter) {
        setTableTitle("Configuration Groups");
        renderChips(filter, "General", "Auction Rules", "Roles", "Items", "Moderation", "Notifications");

        addHeader("Setting", "Current Rule", "Scope");

        List<AdminRow> rows = new ArrayList<>();
        rows.add(row("General settings", "General platform preferences and admin defaults", "Configured", "General", "READY", "General settings for admin dashboard defaults.", "Edit"));
        rows.add(row("Auction rules", "Auction Rules - Minimum increment, reserve price, anti-sniping, auto-close", "Configured", "Auctions", "READY", "Auction flow stays OPEN -> RUNNING -> FINISHED -> PAID, with CANCELED for admin intervention.", "Edit"));
        rows.add(row("Roles & permissions", "Roles - ADMIN manages, SELLER lists, BIDDER bids", "3 roles", "Access", "ADMIN", "Destructive actions should be restricted to ADMIN.", "Edit"));
        rows.add(row("Item rules", "Items - Only AVAILABLE items can become OPEN auctions", "5 states", "Catalog", "READY", "Item flow stays DRAFT -> AVAILABLE -> IN_AUCTION -> SOLD, with REMOVED for moderation/audit.", "Edit"));
        rows.add(row("Moderation triggers", "Moderation - Suspicious bidding, removed content, account violations", "Enabled", "System", "ACTIVE", "Suspicious bids should surface without hiding original BidHistoryDTO records.", "Edit"));
        rows.add(row("Notification settings", "Notifications - Email alerts, report reminders, auction warnings", "Prepared", "System", "READY", "Notification settings for auction ending, payment, and moderation events.", "Edit"));

        addFilteredRows(rows, filter);

        showDetail("Settings workspace", "Filter đang áp dụng: " + filter + ". Settings lọc theo nhóm cấu hình.");
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
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("data-header");

        header.getChildren().addAll(
                headerLabel(main, 390, true),
                headerLabel(first, 170, false),
                headerLabel(second, 130, false),
                headerLabel("Status", 140, false),
                headerLabel("Actions", 300, false)
        );

        dataRowsBox.getChildren().add(header);
    }

    private Label headerLabel(String text, double width, boolean grow) {
        Label label = new Label(text);
        label.getStyleClass().add("table-header-label");
        label.setMinWidth(width);
        label.setPrefWidth(width);

        if (grow) {
            HBox.setHgrow(label, Priority.ALWAYS);
        }

        return label;
    }

    private AdminRow row(String title, String meta, String firstValue, String secondValue, String status, String detail, String... actions) {
        return new AdminRow(title, meta, firstValue, secondValue, status, detail, actions);
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

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim().replace("_", " ");
    }

    private void addEmptyRow(String filter) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("data-row");

        Label empty = new Label("No records found for filter: " + filter);
        empty.getStyleClass().add("row-meta");
        empty.setWrapText(true);
        HBox.setHgrow(empty, Priority.ALWAYS);

        row.getChildren().add(empty);
        dataRowsBox.getChildren().add(row);
    }

    private void addRow(AdminRow data) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("data-row");

        VBox mainCell = new VBox(2);
        mainCell.setMinWidth(390);
        mainCell.setPrefWidth(390);
        HBox.setHgrow(mainCell, Priority.ALWAYS);

        Button link = new Button(data.title);
        link.setMnemonicParsing(false);
        link.getStyleClass().add("row-link");
        link.setMaxWidth(Double.MAX_VALUE);
        link.setOnAction(event -> showDetail(data.title, data.detail));

        Label meta = new Label(data.meta);
        meta.getStyleClass().add("row-meta");
        meta.setWrapText(true);

        mainCell.getChildren().addAll(link, meta);

        Label firstMetric = rowMetric(data.firstValue, 170);
        Label secondMetric = rowMetric(data.secondValue, 130);
        Label status = statusBadge(data.status);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMinWidth(300);
        actions.setPrefWidth(300);

        for (String action : data.actions) {
            Button button = new Button(action);
            button.setMnemonicParsing(false);
            button.getStyleClass().add("mini-action-btn");
            button.setMinWidth(calculateActionButtonWidth(action));
            button.setPrefWidth(calculateActionButtonWidth(action));
            button.setOnAction(event -> showDetail(action + " - " + data.title, data.detail));
            actions.getChildren().add(button);
        }

        row.getChildren().addAll(
                mainCell,
                firstMetric,
                secondMetric,
                status,
                actions
        );

        dataRowsBox.getChildren().add(row);
    }

    private double calculateActionButtonWidth(String text) {
        if (text == null) {
            return 74;
        }

        return Math.max(74, text.length() * 8.5 + 26);
    }

    private Label rowMetric(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("row-metric");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setWrapText(true);
        return label;
    }

    private Label statusBadge(String status) {
        Label label = new Label(status);
        label.getStyleClass().add("status-badge");
        label.setMinWidth(140);
        label.setPrefWidth(140);

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
                || normalized.contains("flagged")) {
            label.getStyleClass().add("status-danger");
        } else if (normalized.contains("open")
                || normalized.contains("draft")
                || normalized.contains("finished")) {
            label.getStyleClass().add("status-warn");
        } else {
            label.getStyleClass().add("status-neutral");
        }

        return label;
    }

    private void showDetail(String title, String description) {
        if (detailTitleLabel != null) {
            detailTitleLabel.setText(title);
        }

        if (detailDescriptionLabel != null) {
            detailDescriptionLabel.setText(description);
        }
    }

    private static class AdminRow {
        private final String title;
        private final String meta;
        private final String firstValue;
        private final String secondValue;
        private final String status;
        private final String detail;
        private final String[] actions;

        private AdminRow(String title, String meta, String firstValue, String secondValue, String status, String detail, String... actions) {
            this.title = title;
            this.meta = meta;
            this.firstValue = firstValue;
            this.secondValue = secondValue;
            this.status = status;
            this.detail = detail;
            this.actions = actions;
        }
    }
}