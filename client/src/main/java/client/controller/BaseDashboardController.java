package client.controller;

import client.SceneNavigator;
import client.model.NotificationModel;
import client.model.User;
import client.service.NetworkManager;
import client.service.NotificationUIHandler;
import client.service.SessionManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shared base controller for role-specific dashboards.
 */
public abstract class BaseDashboardController {
    // Khởi tạo đối tượng xử lý Pop-up Alert bạn đã làm
    protected final NotificationUIHandler notifUIHandler = new NotificationUIHandler();

    protected NetworkManager networkManager;
    private java.util.function.Consumer<String> realtimeNotificationHandler;
    private final List<NotificationModel> notificationInbox = new ArrayList<>();
    private int unreadNotificationCount = 0;
    private Popup notificationPopup;

    protected static class SectionContent {
        protected final String title;
        protected final String subtitle;
        protected final String surfaceTitle;
        protected final String surfaceDescription;
        protected final String rightTitle;
        protected final String rightDescription;
        protected final String[] statValues;
        protected final String[] statLabels;
        protected final String[] featureTitles;
        protected final String[] featureDescriptions;
        protected final String[] activityLines;
        protected final String[] quickTags;
        protected final String[] insightTitles;
        protected final String[] insightValues;

        protected SectionContent(
            String title,
            String subtitle,
            String surfaceTitle,
            String surfaceDescription,
            String rightTitle,
            String rightDescription,
            String[] statValues,
            String[] statLabels,
            String[] featureTitles,
            String[] featureDescriptions,
            String[] activityLines,
            String[] quickTags,
            String[] insightTitles,
            String[] insightValues) {
            this.title = title;
            this.subtitle = subtitle;
            this.surfaceTitle = surfaceTitle;
            this.surfaceDescription = surfaceDescription;
            this.rightTitle = rightTitle;
            this.rightDescription = rightDescription;
            this.statValues = statValues;
            this.statLabels = statLabels;
            this.featureTitles = featureTitles;
            this.featureDescriptions = featureDescriptions;
            this.activityLines = activityLines;
            this.quickTags = quickTags;
            this.insightTitles = insightTitles;
            this.insightValues = insightValues;
        }
    }

    @FXML protected Label usernameLabel;
    @FXML protected Label emailLabel;
    @FXML protected Label roleLabel;
    @FXML protected Label avatarInitialsLabel;
    @FXML protected Button notificationBellButton;
    @FXML protected Label notificationBadgeLabel;

    @FXML protected Label headerTitleLabel;
    @FXML protected Label headerSubtitleLabel;
    @FXML protected Label sectionTitleLabel;
    @FXML protected Label sectionDescriptionLabel;
    @FXML protected Label surfaceTitleLabel;
    @FXML protected Label surfaceDescriptionLabel;
    @FXML protected Label rightPanelTitleLabel;
    @FXML protected Label rightPanelDescriptionLabel;

    @FXML protected Label statValue1;
    @FXML protected Label statValue2;
    @FXML protected Label statValue3;
    @FXML protected Label statValue4;
    @FXML protected Label statLabel1;
    @FXML protected Label statLabel2;
    @FXML protected Label statLabel3;
    @FXML protected Label statLabel4;

    @FXML protected Label featureTitle1;
    @FXML protected Label featureTitle2;
    @FXML protected Label featureTitle3;
    @FXML protected Label featureDescription1;
    @FXML protected Label featureDescription2;
    @FXML protected Label featureDescription3;

    @FXML protected Label activityLine1;
    @FXML protected Label activityLine2;
    @FXML protected Label activityLine3;
    @FXML protected Label activityLine4;

    @FXML protected Label quickTag1;
    @FXML protected Label quickTag2;
    @FXML protected Label quickTag3;

    @FXML protected Label insightTitle1;
    @FXML protected Label insightTitle2;
    @FXML protected Label insightTitle3;
    @FXML protected Label insightValue1;
    @FXML protected Label insightValue2;
    @FXML protected Label insightValue3;

    @FXML protected TextField searchField;

    @FXML protected Button dashboardBtn;
    @FXML protected Button auctionsBtn;
    @FXML protected Button myBidsBtn;
    @FXML protected Button autoBidsBtn;
    @FXML protected Button myItemsBtn;
    @FXML protected Button winnersBtn;
    @FXML protected Button usersBtn;
    @FXML protected Button itemsBtn;
    @FXML protected Button reportsBtn;
    @FXML protected Button settingsBtn;

    private final Map<Button, String> navigationMap = new HashMap<>();
    private final List<Button> navButtons = new ArrayList<>();
    private final List<Label> statValueLabels = new ArrayList<>();
    private final List<Label> statTextLabels = new ArrayList<>();
    private final List<Label> featureTitleLabels = new ArrayList<>();
    private final List<Label> featureDescriptionLabels = new ArrayList<>();
    private final List<Label> activityLabels = new ArrayList<>();
    private final List<Label> quickTagLabels = new ArrayList<>();
    private final List<Label> insightTitleLabels = new ArrayList<>();
    private final List<Label> insightValueLabels = new ArrayList<>();

    private User dashboardUser;

    protected abstract Map<String, SectionContent> createSections();

    protected abstract String getDefaultSectionKey();

    protected abstract String getRoleTitle();

    @FXML
    protected void initialize() {
        refreshUserMeta();
        collectDisplayLabels();
        registerNavigationButtons();
        updateNotificationBadge();
        showSection(getDefaultSectionKey());

        this.networkManager = NetworkManager.getInstance();
    }

    /**
     * Hàm này được gọi ngay sau khi User/Admin đăng nhập thành công
     * và chuyển sang màn hình Dashboard.
     */
    public void setupRealtimeListener() {
        this.networkManager = NetworkManager.getInstance();

        if (this.networkManager != null) {
            // SỬA: Định nghĩa bộ lắng nghe độc lập thông qua biến Consumer
            this.realtimeNotificationHandler = msg -> {
                if (msg == null || msg.isBlank()) return;

                if (msg.startsWith("PUSH_NOTIF|")) {
                    Platform.runLater(() -> processPushNotification(msg));
                } else {
                    // Xử lý các phản hồi nghiệp vụ thông thường
                    Platform.runLater(() -> handleBusinessResponse(msg));
                }
            };

            // SỬA: Sử dụng .addMessageHandler() để chạy song song, KHÔNG DÙNG setMessageHandler()
            this.networkManager.addMessageHandler(realtimeNotificationHandler);
        }
    }

    @FXML
    protected void handleNotificationBell(ActionEvent event) {
        toggleNotificationCenter();
    }

    protected void processPushNotification(String rawMessage) {
        rememberRealtimeNotification(rawMessage);
        notifUIHandler.handle(rawMessage);
        handleRealtimeNotification(rawMessage);
    }

    private void rememberRealtimeNotification(String rawMessage) {
        NotificationModel notification = parseRealtimeNotification(rawMessage);
        if (notification == null) {
            return;
        }

        notificationInbox.add(0, notification);
        while (notificationInbox.size() > 30) {
            notificationInbox.remove(notificationInbox.size() - 1);
        }

        unreadNotificationCount++;
        updateNotificationBadge();

        if (notificationPopup != null && notificationPopup.isShowing()) {
            refreshNotificationPopupContent();
        }
    }

    private NotificationModel parseRealtimeNotification(String rawMessage) {
        if (rawMessage == null || !rawMessage.startsWith("PUSH_NOTIF|")) {
            return null;
        }

        String[] parts = rawMessage.split("\\|", 4);
        if (parts.length < 4) {
            return null;
        }

        return new NotificationModel(parts[1], parts[2], parts[3]);
    }

    private void toggleNotificationCenter() {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide();
            return;
        }

        unreadNotificationCount = 0;
        updateNotificationBadge();
        showNotificationCenter();
    }

    private void showNotificationCenter() {
        if (notificationBellButton == null || notificationBellButton.getScene() == null) {
            return;
        }

        if (notificationPopup == null) {
            notificationPopup = new Popup();
            notificationPopup.setAutoFix(true);
            notificationPopup.setAutoHide(true);
            notificationPopup.setHideOnEscape(true);
        }

        refreshNotificationPopupContent();

        Window owner = notificationBellButton.getScene().getWindow();
        if (owner == null) {
            return;
        }

        Bounds bounds = notificationBellButton.localToScreen(notificationBellButton.getBoundsInLocal());
        double x = bounds == null ? owner.getX() + 18 : bounds.getMaxX() - 334;
        double y = bounds == null ? owner.getY() + 80 : bounds.getMaxY() + 10;
        notificationPopup.show(owner, Math.max(owner.getX() + 14, x), y);
    }

    private void refreshNotificationPopupContent() {
        if (notificationPopup == null) {
            return;
        }

        notificationPopup.getContent().setAll(buildNotificationCenter());
    }

    private VBox buildNotificationCenter() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("notification-popup");

        var stylesheet = getClass().getResource("/client/dashboard.css");
        if (stylesheet != null) {
            panel.getStylesheets().add(stylesheet.toExternalForm());
        }

        Label title = new Label("Notifications");
        title.getStyleClass().add("notification-popup-title");

        Button markReadButton = new Button("Mark read");
        markReadButton.getStyleClass().add("notification-clear-btn");
        markReadButton.setOnAction(event -> {
            unreadNotificationCount = 0;
            updateNotificationBadge();
            if (notificationPopup != null) {
                notificationPopup.hide();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, markReadButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("notification-popup-header");

        VBox list = new VBox(8);
        list.getStyleClass().add("notification-list");

        if (notificationInbox.isEmpty()) {
            Label empty = new Label("No notifications yet. New auction, item, and payment alerts will appear here.");
            empty.setWrapText(true);
            empty.getStyleClass().add("notification-empty");
            list.getChildren().add(empty);
        } else {
            for (NotificationModel notification : notificationInbox) {
                list.getChildren().add(buildNotificationRow(notification));
            }
        }

        ScrollPane scrollPane = new ScrollPane(list);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("notification-list-scroll");

        panel.getChildren().addAll(header, scrollPane);
        return panel;
    }

    private HBox buildNotificationRow(NotificationModel notification) {
        Label icon = new Label(notificationIcon(notification.getType()));
        icon.setAlignment(Pos.CENTER);
        icon.getStyleClass().add("notification-item-icon");

        Label title = new Label(cleanNotificationText(notification.getTitle(), "Notification"));
        title.getStyleClass().add("notification-item-title");

        Label message = new Label(cleanNotificationText(notification.getMessage(), ""));
        message.setWrapText(true);
        message.getStyleClass().add("notification-item-message");

        Label time = new Label(notification.getFormattedTime());
        time.getStyleClass().add("notification-item-time");

        VBox copy = new VBox(3, title, message, time);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox row = new HBox(10, icon, copy);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("notification-item");
        return row;
    }

    private String notificationIcon(String type) {
        if (type == null) {
            return "i";
        }

        return switch (type) {
            case "OUTBID", "PAYMENT_DUE" -> "!";
            case "AUCTION_LOST", "ITEM_REJECTED" -> "x";
            case "AUCTION_WON", "ITEM_APPROVED", "BID_PLACED", "AUCTION_STARTED",
                "PAYMENT_RECEIVED" -> "✓";
            default -> "i";
        };
    }

    private String cleanNotificationText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace("\\n", " ").trim();
    }

    private void updateNotificationBadge() {
        if (notificationBadgeLabel == null) {
            return;
        }

        boolean visible = unreadNotificationCount > 0;
        notificationBadgeLabel.setVisible(visible);
        notificationBadgeLabel.setManaged(visible);
        notificationBadgeLabel.setText(
            unreadNotificationCount > 99 ? "99+" : String.valueOf(unreadNotificationCount)
        );
    }

    /**
     * Lớp con (Admin/User Controller) sẽ override hàm này nếu muốn
     * tự động thêm/xóa dòng hoặc cập nhật số dư, giá tiền trên UI theo thời gian thực.
     */
    protected void handleRealtimeNotification(String rawMessage) {
        // Mặc định không làm gì, lớp con sẽ ghi đè logic
    }

    /**
     * Xử lý phản hồi nghiệp vụ trực tiếp (kết quả của gói tin request chủ động gửi lên)
     */
    protected void handleBusinessResponse(String msg) {
        // Mặc định không làm gì, lớp con sẽ ghi đè logic
    }

    /**
     * Applies the authenticated user to the dashboard and persists the session.
     *
     * @param user user returned by the authentication flow
     */
    public void applyLoggedInUser(User user) {
        if (user != null) {
            dashboardUser = user;
            SessionManager.setCurrentUser(user);
        }
        refreshUserMeta();
    }

    protected void refreshUserMeta() {
        User user = SessionManager.getCurrentUser();
        if (user == null) {
            user = dashboardUser;
        } else {
            dashboardUser = user;
        }

        String username = hasText(user == null ? null : user.getUsername())
            ? user.getUsername().trim()
            : "";

        String email = hasText(user == null ? null : user.getEmail())
            ? user.getEmail().trim()
            : "";

        String role = user != null && user.getSystemRole() != null
            ? user.getSystemRole().name()
            : "";

        setText(usernameLabel, username);
        setText(emailLabel, email);
        setText(roleLabel, role);
        setText(avatarInitialsLabel, buildInitials(username));
    }

    private void collectDisplayLabels() {
        addIfPresent(statValueLabels, statValue1, statValue2, statValue3, statValue4);
        addIfPresent(statTextLabels, statLabel1, statLabel2, statLabel3, statLabel4);
        addIfPresent(featureTitleLabels, featureTitle1, featureTitle2, featureTitle3);
        addIfPresent(
            featureDescriptionLabels,
            featureDescription1,
            featureDescription2,
            featureDescription3
        );
        addIfPresent(activityLabels, activityLine1, activityLine2, activityLine3, activityLine4);
        addIfPresent(quickTagLabels, quickTag1, quickTag2, quickTag3);
        addIfPresent(insightTitleLabels, insightTitle1, insightTitle2, insightTitle3);
        addIfPresent(insightValueLabels, insightValue1, insightValue2, insightValue3);
    }

    private void addIfPresent(List<Label> target, Label... labels) {
        for (Label label : labels) {
            if (label != null) {
                target.add(label);
            }
        }
    }

    private void registerNavigationButtons() {
        putButton(dashboardBtn, "dashboard");
        putButton(auctionsBtn, "auctions");
        putButton(myBidsBtn, "myBids");
        putButton(autoBidsBtn, "autoBids");
        putButton(myItemsBtn, "myItems");
        putButton(winnersBtn, "winners");
        putButton(usersBtn, "users");
        putButton(itemsBtn, "items");
        putButton(reportsBtn, "reports");
        putButton(settingsBtn, "settings");
    }

    private void putButton(Button button, String sectionKey) {
        if (button != null) {
            navigationMap.put(button, sectionKey);
            navButtons.add(button);
        }
    }

    @FXML
    protected void handleSidebarNavigation(ActionEvent event) {
        Object source = event.getSource();

        if (source instanceof Button clickedButton) {
            String sectionKey = navigationMap.get(clickedButton);

            if (sectionKey != null) {
                showSection(sectionKey);
            }
        }
    }

    protected void showSection(String sectionKey) {
        SectionContent content = createSections().get(sectionKey);

        if (content == null) {
            return;
        }

        updateActiveButton(sectionKey);

        setText(headerTitleLabel, content.title);
        setText(headerSubtitleLabel, content.subtitle);
        setText(sectionTitleLabel, content.title);
        setText(sectionDescriptionLabel, content.subtitle);
        setText(surfaceTitleLabel, content.surfaceTitle);
        setText(surfaceDescriptionLabel, content.surfaceDescription);
        setText(rightPanelTitleLabel, content.rightTitle);
        setText(rightPanelDescriptionLabel, content.rightDescription);

        applyTextArray(statValueLabels, content.statValues);
        applyTextArray(statTextLabels, content.statLabels);
        applyTextArray(featureTitleLabels, content.featureTitles);
        applyTextArray(featureDescriptionLabels, content.featureDescriptions);
        applyTextArray(activityLabels, content.activityLines);
        applyTextArray(quickTagLabels, content.quickTags);
        applyTextArray(insightTitleLabels, content.insightTitles);
        applyTextArray(insightValueLabels, content.insightValues);

        if (searchField != null) {
            searchField.setPromptText("Search within " + content.title.toLowerCase() + "...");
        }
    }

    private void updateActiveButton(String sectionKey) {
        for (Button button : navButtons) {
            button.getStyleClass().remove("nav-btn-active");

            String key = navigationMap.get(button);

            if (sectionKey.equals(key)) {
                button.getStyleClass().add("nav-btn-active");
            }
        }
    }

    private void applyTextArray(List<Label> labels, String[] values) {
        for (int i = 0; i < labels.size(); i++) {
            labels.get(i).setText(values != null && i < values.length ? values[i] : "");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void setText(Label label, String value) {
        if (label != null) {
            String safeValue = value == null ? "" : value;
            label.setText(safeValue);
            boolean visible = !safeValue.isBlank();
            label.setVisible(visible);
            label.setManaged(visible);
        }
    }

    private String buildInitials(String username) {
        String[] parts = username == null ? new String[0] : username.trim().split("\\s+");

        if (parts.length == 0 || parts[0].isBlank()) {
            return "U";
        }

        if (parts.length == 1) {
            String word = parts[0].toUpperCase();
            return word.length() >= 2 ? word.substring(0, 2) : word;
        }

        return parts[0].substring(0, 1).toUpperCase()
            + parts[1].substring(0, 1).toUpperCase();
    }

    @FXML
    protected void handleLogout() {
        // Dọn dẹp bộ lắng nghe thông báo real-time (nếu có)
        // Giả sử realtimeNotificationHandler là biến bạn đã khai báo trong Controller
        if (this.networkManager != null && this.realtimeNotificationHandler != null) {
            this.networkManager.removeMessageHandler(this.realtimeNotificationHandler);
            this.realtimeNotificationHandler = null; // Reset để tránh dọn dẹp nhầm lần sau
        }

        if (notificationPopup != null) {
            notificationPopup.hide();
            notificationPopup = null;
        }

        SessionManager.clear();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/auth.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            SceneNavigator.switchSceneKeepingWindow(stage, root, "Auction System - Sign In");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}