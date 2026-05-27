package client.controller;

import client.model.User;
import client.service.CloudMediaApiClient;
import client.service.NetworkManager;
import client.service.SessionManager;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Dieu phoi dashboard nguoi dung cho luong bidder va seller.
 *
 * <p>Class nay giu entry controller cua FXML, lang nghe cap nhat dau gia thoi
 * gian thuc, render danh sach auction, My Bids, My Items va form Create Listing.
 */
public class UserDashboardController extends BaseDashboardController {

  @FXML private FlowPane userActionBar;
  @FXML private VBox workspaceBox;
  @FXML private Label workspaceTitleLabel;
  @FXML private Button primaryActionButton;
  @FXML private Button createListingFloatingButton;
  @FXML private Button depositFloatingButton;

  private static final int AUCTIONS_PER_PAGE = 6;
  private static final double USER_ACTION_PRIMARY_WIDTH = 84;
  private static final double USER_ACTION_MORE_WIDTH = 28;
  private static final double USER_ACTION_GAP = 6;
  private static final double PRODUCT_IMAGE_INITIAL_WIDTH = 360;
  private static final double PRODUCT_IMAGE_HEIGHT = 155;
  private static final double AUCTION_DETAIL_INFO_WIDTH = 318;
  private static final double AUCTION_DETAIL_GAP = 14;
  private static final double AUCTION_DETAIL_IMAGE_HEIGHT = 318;
  private static final double AUCTION_DETAIL_THUMB_WIDTH = 76;
  private static final double AUCTION_DETAIL_THUMB_HEIGHT = 66;
  private static final int MAX_CREATE_ITEM_IMAGES = 5;
  private static final double CREATE_UPLOAD_CARD_MAX_WIDTH = 520;
  private static final double CREATE_PREVIEW_CARD_MAX_WIDTH = 460;
  private static final double CREATE_UPLOAD_ZONE_HEIGHT = 220;
  private static final double CREATE_PREVIEW_IMAGE_HEIGHT = 250;
  private static final double CREATE_FILE_ROW_HEIGHT = 52;
  private static final double CREATE_FILE_LIST_MAX_HEIGHT = 118;
  private static final double CREATE_FILE_LIST_GAP = 8;
  private static final String CREATE_CARD_STYLE = "-fx-background-color: #171717; "
      + "-fx-background-radius: 20; -fx-border-color: #303030; -fx-border-radius: 20; "
      + "-fx-padding: 16;";
  private static final String CREATE_SECTION_TITLE_STYLE = "-fx-text-fill: #f7f3e9; "
      + "-fx-font-size: 15px; -fx-font-weight: bold;";
  private static final String CREATE_MUTED_TEXT_STYLE = "-fx-text-fill: #a7a7a7; "
      + "-fx-font-size: 11px;";
  private static final String CREATE_FORM_FIELD_STYLE = "-fx-background-color: #202020; "
      + "-fx-background-radius: 15; -fx-border-color: #3a3a3a; -fx-border-radius: 15; "
      + "-fx-padding: 10 13 10 13; -fx-text-fill: #f7f3e9; "
      + "-fx-prompt-text-fill: #858585; -fx-min-height: 50; -fx-pref-height: 50;";
  private static final String CREATE_TEXT_AREA_STYLE = "-fx-background-color: #202020; "
      + "-fx-control-inner-background: #202020; -fx-background-insets: 0; "
      + "-fx-background-radius: 15; -fx-border-color: transparent; "
      + "-fx-border-radius: 15; -fx-padding: 10 13 10 13; "
      + "-fx-text-fill: #f7f3e9; -fx-prompt-text-fill: #858585;";
  private static final String CREATE_COMBO_STYLE = "-fx-background-color: #202020; "
      + "-fx-background-radius: 15; -fx-border-color: #3a3a3a; -fx-border-radius: 15; "
      + "-fx-padding: 0 12 0 12; -fx-min-height: 50; -fx-pref-height: 50;";
  private static final String CREATE_DARK_BUTTON_STYLE = "-fx-background-color: #2a2a2a; "
      + "-fx-text-fill: #f7f3e9; -fx-background-radius: 16; -fx-padding: 10 16 10 16; "
      + "-fx-border-color: #3f3f3f; -fx-border-radius: 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
  private static final String CREATE_PRIMARY_BUTTON_STYLE = "-fx-background-color: "
      + "linear-gradient(to right, #d1b15d, #a78634); "
      + "-fx-text-fill: #111111; -fx-background-radius: 16; -fx-padding: 10 16 10 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
  private static final String CREATE_SECONDARY_BUTTON_STYLE = "-fx-background-color: transparent; "
      + "-fx-border-color: #444444; -fx-border-radius: 16; -fx-background-radius: 16; "
      + "-fx-text-fill: #f7f3e9; -fx-padding: 10 16 10 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
  private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");
  private static final DateTimeFormatter AUCTION_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final String AUCTION_IMAGE_FALLBACK = "/client/images/overlay2.jpg";

  private final Map<String, SectionContent> sections = UserDashboardSections.buildSections();
  private final List<AuctionCardData> liveAuctionCards = new ArrayList<>();
  private List<AuctionCardData> incomingAuctionCards = new ArrayList<>();
  private boolean userAuctionsLoaded;
  private boolean userAuctionsLoading;
  private final List<SellerItemData> sellerItems = new ArrayList<>();
  private List<SellerItemData> incomingSellerItems = new ArrayList<>();
  private boolean sellerItemsLoaded;
  private boolean sellerItemsLoading;
  private final List<MyBidData> myBids = new ArrayList<>();
  private List<MyBidData> incomingMyBids = new ArrayList<>();
  private boolean myBidsLoaded;
  private boolean myBidsLoading;
  private final List<AutoBidData> autoBids = new ArrayList<>();
  private List<AutoBidData> incomingAutoBids = new ArrayList<>();
  private boolean autoBidsLoaded;
  private boolean autoBidsLoading;
  private final List<TransactionData> transactions = new ArrayList<>();
  private List<TransactionData> incomingTransactions = new ArrayList<>();
  private boolean transactionsLoaded;
  private boolean transactionsLoading;
  private boolean transactionsReloadPending;
  private final Map<String, Boolean> paymentSubmittingByAuction = new HashMap<>();
  private BigDecimal currentWalletBalance = BigDecimal.ZERO;
  private boolean walletBalanceKnown;
  private final Map<String, Long> auctionSecondsSyncedAtMillis = new HashMap<>();
  private final Map<String, List<BidHistoryData>> sellerBidHistoryByAuction = new HashMap<>();
  private final Map<String, Boolean> sellerBidHistoryLoaded = new HashMap<>();
  private List<BidHistoryData> incomingSellerBidHistory = new ArrayList<>();
  private String incomingSellerBidAuctionId = "";
  private SellerItemData activeSellerDetailItem;
  private VBox activeSellerBidHistorySection;
  private final Map<String, Image> imageCache = new LinkedHashMap<String, Image>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
      return size() > 50; // Giữ tối đa 50 ảnh trong cache
    }
  };
  private final CloudMediaApiClient cloudMediaApiClient =
      new CloudMediaApiClient();
  /**
   * Kết nối dùng lại cho form Create Listing đặt trực tiếp trong dashboard.
   *
   * <p>Controller này mở socket riêng, nên payload CREATE_ITEM vẫn gửi kèm user id
   * trong session. Server sẽ ưu tiên user đã xác thực qua socket và chỉ dùng id này
   * như phương án dự phòng cho kết nối tạo listing từ dashboard.</p>
   */
  // Tạo biến instance để gỡ listener khi logout hoặc chuyển cảnh.
  private Consumer<String> userNetworkHandler;
  private NetworkManager createItemNetworkManager;
  private java.util.function.Consumer<String> createItemHandler;
  private final List<CreateItemUpload> pendingCreateItemUploads = new ArrayList<>();
  private int pendingCreateItemPreviewIndex;
  private boolean sellerItemStatsLoaded;
  private int sellerItemTotal;
  private int sellerItemDrafts;
  private int sellerItemActiveSales;
  private int sellerItemSold;

  private String currentSectionKey = "auctions";
  private String activeFilter = "All";
  private int auctionPage = 1;
  private Timeline auctionDetailCountdownTimeline;
  private String activeAuctionDetailId;
  private Label activeAuctionPriceLabel;
  private Label activeAuctionBidCountLabel;
  private TextField activeAuctionBidInput;
  private Button activeAuctionBidButton;
  private TextField activeAutoBidMaxInput;
  private TextField activeAutoBidIncrementInput;
  private Button activeAutoBidRegisterButton;
  private Button activeAutoBidCancelButton;
  private Label activeAuctionBidMessageLabel;
  private Label activeCountdownDayLabel;
  private Label activeCountdownHourLabel;
  private Label activeCountdownMinuteLabel;
  private Label activeCountdownSecondLabel;
  private long activeAuctionCountdownSeconds = -1L;



  /**
   * Khoi tao dashboard user, ket noi listener live va nap du lieu can hien thi.
   */
  @Override
  @FXML
  protected void initialize() {
    this.networkManager = NetworkManager.getInstance();
    setupCreateItemNetwork();
    setupCreateListingFloatingButton();
    setupDepositFloatingButton();

    this.userNetworkHandler = msg -> {
      if (msg == null || msg.isBlank()) {
        return;
      }
      if (msg.startsWith("PUSH_NOTIF|")) {
        Platform.runLater(() -> processPushNotification(msg));
        return;
      }
      if (isNotificationHistoryMessage(msg)) {
        Platform.runLater(() -> handleNotificationHistoryMessage(msg));
        return;
      }
      if (isSellerBidHistoryMessage(msg)) {
        Platform.runLater(() -> handleSellerBidHistoryMessage(msg));
        return;
      }
      if (isUserBidMessage(msg)) {
        Platform.runLater(() -> handleUserBidServerMessage(msg));
        return;
      }
      if (isAutoBidListMessage(msg)) {
        Platform.runLater(() -> handleAutoBidListMessage(msg));
        return;
      }
      if (isTransactionMessage(msg)) {
        Platform.runLater(() -> handleTransactionServerMessage(msg));
        return;
      }
      if (isRealtimeBidMessage(msg)) {
        Platform.runLater(() -> handleRealtimeBidMessage(msg));
        return;
      }
      if (isAuctionListMessage(msg)) {
        Platform.runLater(() -> handleUserAuctionServerMessage(msg));
      }
    };
    this.networkManager.addMessageHandler(userNetworkHandler);
    super.initialize();
    requestNotificationHistory();
  }

  private void setupCreateListingFloatingButton() {
    if (createListingFloatingButton == null) {
      return;
    }
    createListingFloatingButton.setVisible(false);
    createListingFloatingButton.setManaged(false);
    createListingFloatingButton.setOnAction(event -> handleOpenCreateListing());
  }

  private void setupDepositFloatingButton() {
    if (depositFloatingButton == null) {
      return;
    }
    depositFloatingButton.setVisible(false);
    depositFloatingButton.setManaged(false);
    depositFloatingButton.setOnAction(event -> handleOpenDepositDialog());
  }

  @FXML
  private void handleOpenCreateListing() {
    renderCreateItemForm();
  }

  @FXML
  private void handleOpenDepositDialog() {
    Dialog<BigDecimal> dialog = new Dialog<>();
    dialog.initStyle(StageStyle.UNDECORATED);
    dialog.setHeaderText(null);
    dialog.setGraphic(null);
    dialog.setResizable(false);

    DialogPane dialogPane = dialog.getDialogPane();
    dialogPane.getStyleClass().add("wallet-deposit-dialog-pane");
    dialogPane.setPadding(Insets.EMPTY);
    dialogPane.setMinWidth(360);
    dialogPane.setPrefWidth(360);
    dialogPane.setMaxWidth(360);
    String dashboardCss = getClass().getResource("/client/dashboard.css").toExternalForm();
    dialogPane.getStylesheets().add(dashboardCss);
    dialogPane.getButtonTypes().setAll(ButtonType.CANCEL);
    Node hiddenCancelButton = dialogPane.lookupButton(ButtonType.CANCEL);
    if (hiddenCancelButton != null) {
      hiddenCancelButton.setVisible(false);
      hiddenCancelButton.setManaged(false);
    }
    dialog.setResultConverter(buttonType -> null);

    TextField amountField = new TextField();
    amountField.setPromptText("Enter amount");
    amountField.getStyleClass().add("wallet-deposit-input");

    Label errorLabel = new Label("Enter a valid amount greater than 0 VND.");
    errorLabel.getStyleClass().add("wallet-deposit-error");
    errorLabel.setVisible(false);
    errorLabel.setManaged(true);
    errorLabel.setMinHeight(18);
    errorLabel.setPrefHeight(18);
    errorLabel.setMaxHeight(18);
    amountField.textProperty().addListener((obs, oldValue, newValue) -> {
      amountField.getStyleClass().remove("wallet-deposit-input-invalid");
      errorLabel.setVisible(false);
    });

    Button depositButton = new Button("Deposit");
    depositButton.getStyleClass().add("wallet-deposit-primary-btn");
    depositButton.setDefaultButton(true);

    Button cancelButton = new Button("Cancel");
    cancelButton.getStyleClass().add("wallet-deposit-secondary-btn");
    cancelButton.setCancelButton(true);
    cancelButton.setDisable(false);
    cancelButton.setFocusTraversable(true);

    Runnable closeDepositDialog = () -> {
      dialog.setResult(null);
      dialog.close();
    };

    Runnable submitDeposit = () -> {
      BigDecimal amount = parsePositiveMoney(amountField.getText());
      if (amount == null) {
        amountField.getStyleClass().remove("wallet-deposit-input-invalid");
        amountField.getStyleClass().add("wallet-deposit-input-invalid");
        errorLabel.setVisible(true);
        amountField.requestFocus();
        return;
      }
      dialog.setResult(amount);
      dialog.close();
    };

    depositButton.setOnAction(event -> submitDeposit.run());
    amountField.setOnAction(event -> submitDeposit.run());
    cancelButton.setOnAction(event -> {
      closeDepositDialog.run();
      event.consume();
    });
    dialogPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        closeDepositDialog.run();
        event.consume();
      }
    });

    dialogPane.setContent(buildDepositDialogContent(
        amountField,
        errorLabel,
        depositButton,
        cancelButton
    ));

    dialog.setOnShown(event -> {
      if (dialogPane.getScene() != null) {
        dialogPane.getScene().setFill(Color.web("#171717"));
      }
      amountField.requestFocus();
    });

    dialog.showAndWait().ifPresent(amount -> {
      if (networkManager == null) {
        networkManager = NetworkManager.getInstance();
      }
      if (networkManager == null) {
        notifUIHandler.showError("Deposit failed", "Cannot connect to the server.");
        return;
      }
      networkManager.send(buildDepositCommand(amount));
      notifUIHandler.showSuccess(
          "Deposit processing",
          "The server is updating your wallet balance."
      );
    });
  }

  private StackPane buildDepositDialogContent(
      TextField amountField,
      Label errorLabel,
      Button depositButton,
      Button cancelButton
  ) {
    VBox card = new VBox(14);
    card.getStyleClass().add("wallet-deposit-card");
    card.setPadding(new Insets(26, 24, 20, 24));
    card.setMinWidth(360);
    card.setPrefWidth(360);
    card.setMaxWidth(360);

    VBox header = new VBox(5);
    header.setPadding(new Insets(0, 0, 4, 0));
    Label eyebrow = new Label("Wallet deposit");
    eyebrow.getStyleClass().add("wallet-deposit-eyebrow");
    Label title = new Label("Top up wallet");
    title.getStyleClass().add("wallet-deposit-title");
    title.setMaxWidth(Double.MAX_VALUE);
    title.setTextOverrun(OverrunStyle.ELLIPSIS);
    Label balanceCaption = new Label("Add VND to your bidding balance.");
    balanceCaption.getStyleClass().add("wallet-deposit-caption");
    balanceCaption.setWrapText(true);
    header.getChildren().addAll(eyebrow, title, balanceCaption);

    VBox amountBlock = new VBox(9);
    amountBlock.setFillWidth(true);
    HBox amountHeader = new HBox(10);
    amountHeader.setAlignment(Pos.CENTER_LEFT);
    Label amountLabel = new Label("Amount");
    amountLabel.getStyleClass().add("wallet-deposit-field-label");
    Region labelSpacer = new Region();
    HBox.setHgrow(labelSpacer, Priority.ALWAYS);
    Label currencyPill = new Label("VND");
    currencyPill.getStyleClass().add("wallet-deposit-currency-pill");
    amountHeader.getChildren().addAll(amountLabel, labelSpacer, currencyPill);
    amountField.setMinHeight(48);
    amountField.setPrefHeight(52);
    amountField.setMaxWidth(Double.MAX_VALUE);
    amountBlock.getChildren().addAll(amountHeader, amountField);

    HBox buttonRow = new HBox(12);
    buttonRow.setAlignment(Pos.CENTER_RIGHT);
    buttonRow.setPadding(new Insets(10, 0, 0, 0));
    buttonRow.getStyleClass().add("wallet-deposit-action-row");
    buttonRow.getChildren().addAll(cancelButton, depositButton);

    card.getChildren().addAll(header, amountBlock, errorLabel, buttonRow);

    StackPane shell = new StackPane(card);
    shell.getStyleClass().add("wallet-deposit-shell");
    shell.setPadding(Insets.EMPTY);
    shell.setMinWidth(360);
    shell.setPrefWidth(360);
    shell.setMaxWidth(360);
    return shell;
  }

  private String buildDepositCommand(BigDecimal amount) {
    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      return "DEPOSIT_WALLET " + currentUser.getUserId() + " " + amount.toPlainString();
    }
    return "DEPOSIT_WALLET " + amount.toPlainString();
  }

  private void preloadAuctionImages() {
    // Auction images can come from remote Cloudinary/http URLs. Loading all of them
    // synchronously when USER_AUCTIONS_END arrives blocks the JavaFX thread and makes
    // user-home feel slow. Cards now request images lazily with JavaFX background
    // loading, so auction data appears first and images fill in as they are ready.
  }

  private void setupCreateItemNetwork() {
    createItemNetworkManager = NetworkManager.getInstance();
    createItemHandler = message -> Platform.runLater(() -> handleCreateItemServerMessage(message));
    createItemNetworkManager.addMessageHandler(createItemHandler);
  }

  /**
   * Loads data needed by the inline Create Listing form.
   *
   * <p>The dashboard uses a lightweight socket, so the current user id is included in
   * seller-specific requests. The server still prefers the authenticated socket user when it has
   * one.</p>
   */
  private void requestCreateListingMetadata() {
    if (createItemNetworkManager == null) {
      return;
    }
    if (sellerItemsLoading) {
      return;
    }

    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      sellerItemsLoading = true;
      createItemNetworkManager.send("USER_ITEM_STATS " + currentUser.getUserId());
      createItemNetworkManager.send("USER_LIST_ITEMS " + currentUser.getUserId());
    }
  }

  private void requestUserAuctions() {
    if (userAuctionsLoading) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager != null) {
      userAuctionsLoading = true;
      networkManager.send("USER_LIST_AUCTIONS");
    }
  }

  private void requestUserBids() {
    if (myBidsLoading) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      return;
    }

    myBidsLoading = true;
    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      networkManager.send("USER_LIST_BIDS " + currentUser.getUserId());
    } else {
      networkManager.send("USER_LIST_BIDS");
    }
  }

  private void requestUserAutoBids() {
    if (autoBidsLoading) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      return;
    }

    autoBidsLoading = true;
    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      networkManager.send("USER_LIST_AUTOBIDS " + currentUser.getUserId());
    } else {
      networkManager.send("USER_LIST_AUTOBIDS");
    }
  }

  private void requestUserTransactions() {
    if (transactionsLoading) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      return;
    }

    transactionsLoading = true;
    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      networkManager.send("USER_LIST_TRANSACTIONS " + currentUser.getUserId());
    } else {
      networkManager.send("USER_LIST_TRANSACTIONS");
    }
  }

  private void requestSellerBidHistory(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      return;
    }

    String trimmedAuctionId = auctionId.trim();
    sellerBidHistoryLoaded.put(trimmedAuctionId, false);
    networkManager.send("SELLER_AUCTION_BIDS " + trimmedAuctionId);
  }

  private boolean isSellerBidHistoryMessage(String message) {
    return message != null && (message.startsWith("SELLER_AUCTION_BIDS_BEGIN")
        || message.startsWith("SELLER_AUCTION_BID ")
        || message.startsWith("SELLER_AUCTION_BIDS_END")
        || message.startsWith("SELLER_AUCTION_BIDS_ERROR"));
  }

  private boolean isUserBidMessage(String message) {
    return message != null && (message.equals("USER_BIDS_BEGIN")
        || message.equals("USER_BIDS_END")
        || message.startsWith("USER_BID ")
        || message.startsWith("USER_BIDS_ERROR"));
  }

  private boolean isAutoBidListMessage(String message) {
    return message != null && (message.equals("USER_AUTOBIDS_BEGIN")
        || message.equals("USER_AUTOBIDS_END")
        || message.startsWith("USER_AUTOBID ")
        || message.startsWith("USER_AUTOBIDS_ERROR"));
  }

  private boolean isTransactionMessage(String message) {
    return message != null && (message.equals("USER_TRANSACTIONS_BEGIN")
        || message.equals("USER_TRANSACTIONS_END")
        || message.equals("USER_TRANSACTIONS_DIRTY")
        || message.startsWith("USER_TRANSACTION ")
        || message.startsWith("USER_TRANSACTIONS_ERROR")
        || message.startsWith("PAYMENT_SUCCESS")
        || message.startsWith("PAYMENT_FAIL")
        || message.startsWith("DEPOSIT_SUCCESS")
        || message.startsWith("DEPOSIT_FAIL")
        || message.startsWith("WALLET_UPDATE|"));
  }

  private boolean isAuctionListMessage(String message) {
    return message.startsWith("USER_AUCTION")
        || message.equals("USER_AUCTIONS_DIRTY")
        || message.startsWith("ADMIN_AUCTION")
        || message.equals("ADMIN_AUCTIONS_BEGIN")
        || message.equals("ADMIN_AUCTIONS_END")
        || message.startsWith("ADMIN_DATA_ERROR");
  }

  private boolean isRealtimeBidMessage(String message) {
    return message.startsWith("AUCTION_SNAPSHOT|")
        || message.startsWith("BID_UPDATE|")
        || message.startsWith("AUCTION_BID_UPDATE|")
        || message.startsWith("TIME_EXTENDED|")
        || message.startsWith("AUCTION_CLOSED_UPDATE|")
        || message.startsWith("AUCTION_CLOSED|")
        || message.startsWith("BID_SUCCESS")
        || message.startsWith("BID_FAIL")
        || message.startsWith("AUTOBID_SUCCESS")
        || message.startsWith("AUTOBID_FAIL")
        || message.startsWith("FAIL ")
        || message.startsWith("JOIN_AUCTION_FAIL")
        || message.startsWith("LEAVE_AUCTION_SUCCESS");
  }

  private void handleUserAuctionServerMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.equals("USER_AUCTIONS_DIRTY")) {
      requestUserAuctions();
      return;
    }

    if (message.equals("USER_AUCTIONS_BEGIN") || message.equals("ADMIN_AUCTIONS_BEGIN")) {
      userAuctionsLoading = true;
      incomingAuctionCards = new ArrayList<>();
      return;
    }

    if (message.startsWith("USER_AUCTION ")) {
      AuctionCardData parsed = parseAuctionCard(message.substring("USER_AUCTION ".length()));
      if (parsed != null) {
        incomingAuctionCards.add(parsed);
      }
      return;
    }

    if (message.startsWith("ADMIN_AUCTION ")) {
      AuctionCardData parsed = parseLegacyAdminAuctionCard(
          message.substring("ADMIN_AUCTION ".length())
      );
      if (parsed != null) {
        incomingAuctionCards.add(parsed);
      }
      return;
    }

    if (message.equals("USER_AUCTIONS_END") || message.equals("ADMIN_AUCTIONS_END")) {
      userAuctionsLoading = false;
      liveAuctionCards.clear();
      liveAuctionCards.addAll(incomingAuctionCards);
      userAuctionsLoaded = true;
      preloadAuctionImages();
      applyUserAuctionStatsIfVisible();
      if (("auctions".equals(currentSectionKey) || "dashboard".equals(currentSectionKey))
          && activeAuctionDetailId == null) {
        renderWorkspace(currentSectionKey, activeFilter);
      }
      return;
    }

    if (message.startsWith("USER_AUCTIONS_ERROR") || message.startsWith("ADMIN_DATA_ERROR")) {
      userAuctionsLoading = false;
      liveAuctionCards.clear();
      userAuctionsLoaded = true;
      applyUserAuctionStatsIfVisible();
      if (("auctions".equals(currentSectionKey) || "dashboard".equals(currentSectionKey))
          && activeAuctionDetailId == null) {
        renderWorkspace(currentSectionKey, activeFilter);
      }
    }
  }

  private void handleSellerBidHistoryMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.startsWith("SELLER_AUCTION_BIDS_BEGIN")) {
      incomingSellerBidHistory = new ArrayList<>();
      incomingSellerBidAuctionId = message.substring("SELLER_AUCTION_BIDS_BEGIN".length())
          .trim();
      return;
    }

    if (message.startsWith("SELLER_AUCTION_BID ")) {
      BidHistoryData parsed = parseBidHistory(
          message.substring("SELLER_AUCTION_BID ".length())
      );
      if (parsed != null) {
        incomingSellerBidHistory.add(parsed);
      }
      return;
    }

    if (message.startsWith("SELLER_AUCTION_BIDS_END")) {
      String auctionId = message.substring("SELLER_AUCTION_BIDS_END".length()).trim();
      if (auctionId.isBlank()) {
        auctionId = incomingSellerBidAuctionId;
      }
      sellerBidHistoryByAuction.put(auctionId, new ArrayList<>(incomingSellerBidHistory));
      sellerBidHistoryLoaded.put(auctionId, true);
      refreshActiveSellerBidHistorySection(auctionId);
      return;
    }

    if (message.startsWith("SELLER_AUCTION_BIDS_ERROR")) {
      String auctionId = incomingSellerBidAuctionId;
      if (!auctionId.isBlank()) {
        sellerBidHistoryByAuction.put(auctionId, new ArrayList<>());
        sellerBidHistoryLoaded.put(auctionId, true);
      }
    }
  }

  private BidHistoryData parseBidHistory(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 8) {
      return null;
    }

    return new BidHistoryData(
        safeField(fields, 0),
        safeField(fields, 1),
        safeField(fields, 2),
        fallback(safeField(fields, 3), "User #" + safeField(fields, 2)),
        formatMoney(safeField(fields, 4)),
        prettyStatus(safeField(fields, 5)),
        Boolean.parseBoolean(safeField(fields, 6)),
        shortTimestamp(safeField(fields, 7))
    );
  }

  private void handleUserBidServerMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.equals("USER_BIDS_BEGIN")) {
      myBidsLoading = true;
      incomingMyBids = new ArrayList<>();
      return;
    }

    if (message.startsWith("USER_BID ")) {
      MyBidData parsed = parseMyBid(message.substring("USER_BID ".length()));
      if (parsed != null) {
        incomingMyBids.add(parsed);
      }
      return;
    }

    if (message.equals("USER_BIDS_END")) {
      myBidsLoading = false;
      myBids.clear();
      myBids.addAll(incomingMyBids);
      myBidsLoaded = true;
      applyMyBidStatsIfVisible();
      if ("myBids".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
        applyMyBidStatsIfVisible();
      }
      return;
    }

    if (message.startsWith("USER_BIDS_ERROR")) {
      myBidsLoading = false;
      myBids.clear();
      myBidsLoaded = true;
      applyMyBidStatsIfVisible();
      if ("myBids".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
        applyMyBidStatsIfVisible();
      }
    }
  }

  private void handleAutoBidListMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.equals("USER_AUTOBIDS_BEGIN")) {
      autoBidsLoading = true;
      incomingAutoBids = new ArrayList<>();
      return;
    }

    if (message.startsWith("USER_AUTOBID ")) {
      AutoBidData parsed = parseAutoBid(message.substring("USER_AUTOBID ".length()));
      if (parsed != null) {
        incomingAutoBids.add(parsed);
      }
      return;
    }

    if (message.equals("USER_AUTOBIDS_END")) {
      autoBidsLoading = false;
      autoBids.clear();
      for (AutoBidData rule : incomingAutoBids) {
        if (isRenderableAutoBidRule(rule)) {
          autoBids.add(rule);
        }
      }
      autoBidsLoaded = true;
      applyAutoBidStatsIfVisible();
      if ("autoBids".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
        applyAutoBidStatsIfVisible();
      }
      return;
    }

    if (message.startsWith("USER_AUTOBIDS_ERROR")) {
      autoBidsLoading = false;
      autoBids.clear();
      autoBidsLoaded = true;
      applyAutoBidStatsIfVisible();
      if ("autoBids".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
        applyAutoBidStatsIfVisible();
      }
    }
  }

  private void handleTransactionServerMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.equals("USER_TRANSACTIONS_DIRTY")) {
      reloadTransactionsFromServer();
      return;
    }

    if (message.startsWith("PAYMENT_SUCCESS")) {
      String[] parts = message.trim().split("\\s+", 3);
      String auctionId = parts.length > 1 ? parts[1] : "";
      clearPaymentSubmitting(auctionId);
      notifUIHandler.showSuccess(
          "Payment completed",
          auctionId.isBlank()
              ? "Your transaction has been paid successfully."
              : "Payment completed for auction AUC-" + auctionId + "."
      );
      reloadTransactionsFromServer();
      requestUserBids();
      requestUserAuctions();
      return;
    }

    if (message.startsWith("PAYMENT_FAIL")) {
      String[] parts = message.trim().split("\\s+", 3);
      String auctionId = parts.length > 2 ? parts[1] : "";
      String reason = parts.length > 2
          ? parts[2]
          : parts.length > 1 ? parts[1] : "PAYMENT_NOT_COMPLETED";
      clearPaymentSubmitting(auctionId);
      notifUIHandler.showError("Payment failed", readablePaymentFailure(reason));
      reloadTransactionsFromServer();
      return;
    }

    if (message.startsWith("DEPOSIT_SUCCESS")) {
      List<String> fields = splitPayload(message.substring("DEPOSIT_SUCCESS".length()).trim());
      String amount = fields.isEmpty() ? "" : formatMoney(fields.get(0));
      String balance = fields.size() > 1 ? formatMoney(fields.get(1)) : "";
      if (fields.size() > 1) {
        updateKnownWalletBalance(fields.get(1));
      }
      notifUIHandler.showSuccess(
          "Deposit completed",
          balance.isBlank()
              ? "Your wallet was topped up successfully."
              : "Added " + amount + ". New balance: " + balance + "."
      );
      reloadTransactionsFromServer();
      return;
    }

    if (message.startsWith("DEPOSIT_FAIL")) {
      String reason = message.substring("DEPOSIT_FAIL".length()).trim();
      notifUIHandler.showError("Deposit failed", readablePaymentFailure(reason));
      reloadTransactionsFromServer();
      return;
    }

    if (message.startsWith("WALLET_UPDATE|")) {
      updateKnownWalletBalanceFromMessage(message);
      refreshTransactionWorkspaceIfVisible();
      return;
    }

    if (message.equals("USER_TRANSACTIONS_BEGIN")) {
      transactionsLoading = true;
      incomingTransactions = new ArrayList<>();
      return;
    }

    if (message.startsWith("USER_TRANSACTION ")) {
      TransactionData parsed = parseTransaction(message.substring("USER_TRANSACTION ".length()));
      if (parsed != null) {
        incomingTransactions.add(parsed);
      }
      return;
    }

    if (message.equals("USER_TRANSACTIONS_END")) {
      transactionsLoading = false;
      transactions.clear();
      transactions.addAll(incomingTransactions);
      syncWalletBalanceFromTransactionRows();
      dropResolvedPaymentSubmissions();
      transactionsLoaded = true;
      applyTransactionStatsIfVisible();
      if ("winners".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
        applyTransactionStatsIfVisible();
      }
      requestPendingTransactionReloadIfNeeded();
      return;
    }

    if (message.startsWith("USER_TRANSACTIONS_ERROR")) {
      transactionsLoading = false;
      transactions.clear();
      paymentSubmittingByAuction.clear();
      transactionsLoaded = true;
      applyTransactionStatsIfVisible();
      if ("winners".equals(currentSectionKey)) {
        renderWorkspace(currentSectionKey, activeFilter);
      }
      requestPendingTransactionReloadIfNeeded();
    }
  }

  private TransactionData parseTransaction(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 14) {
      return null;
    }

    String rawBalanceAfter = fields.size() > 14 ? safeField(fields, 14) : "";
    return new TransactionData(
        safeField(fields, 0),
        safeField(fields, 1),
        safeField(fields, 2),
        fallback(safeField(fields, 3), "Auction #" + safeField(fields, 1)),
        safeField(fields, 4),
        fallback(safeField(fields, 5), "User #" + safeField(fields, 4)),
        formatMoney(safeField(fields, 6)),
        safeField(fields, 7),
        safeField(fields, 8),
        shortTimestamp(safeField(fields, 9)),
        shortTimestamp(safeField(fields, 10)),
        safeField(fields, 11),
        safeField(fields, 12),
        safeField(fields, 13),
        rawBalanceAfter.isBlank() ? "" : formatMoney(rawBalanceAfter)
    );
  }

  private boolean isRenderableAutoBidRule(AutoBidData rule) {
    if (rule == null) {
      return false;
    }
    String normalizedStatus = normalize(rule.status);
    return !normalizedStatus.equals("canceled")
        && !normalizedStatus.equals("cancelled")
        && !normalizedStatus.equals("deleted")
        && !normalizedStatus.equals("inactive");
  }

  private AutoBidData parseAutoBid(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 13) {
      return null;
    }

    return new AutoBidData(
        safeField(fields, 0),
        safeField(fields, 1),
        safeField(fields, 2),
        fallback(safeField(fields, 3), "Auction #" + safeField(fields, 1)),
        safeField(fields, 4),
        formatMoney(safeField(fields, 5)),
        formatMoney(safeField(fields, 6)),
        formatMoney(safeField(fields, 7)),
        prettyStatus(safeField(fields, 8)),
        shortTimestamp(safeField(fields, 9)),
        fallback(safeField(fields, 10), "Seller"),
        firstImage(safeField(fields, 11)),
        parseLongOrDefault(safeField(fields, 12), 0L)
    );
  }

  private MyBidData parseMyBid(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 14) {
      return null;
    }

    return new MyBidData(
        safeField(fields, 0),
        safeField(fields, 1),
        safeField(fields, 2),
        fallback(safeField(fields, 3), "Auction #" + safeField(fields, 1)),
        safeField(fields, 4),
        formatMoney(safeField(fields, 5)),
        formatMoney(safeField(fields, 6)),
        safeField(fields, 7),
        safeField(fields, 8),
        Boolean.parseBoolean(safeField(fields, 9)),
        shortTimestamp(safeField(fields, 10)),
        shortTimestamp(safeField(fields, 11)),
        safeField(fields, 12),
        fallback(safeField(fields, 13), AUCTION_IMAGE_FALLBACK)
    );
  }

  private AuctionCardData parseLegacyAdminAuctionCard(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 11) {
      return null;
    }

    String auctionId = safeField(fields, 0);
    String itemId = safeField(fields, 1);
    String title = fallback(safeField(fields, 2), "Auction #" + auctionId);
    String sellerId = safeField(fields, 3);
    String seller = fallback(safeField(fields, 4), "Seller #" + sellerId);
    String currentPrice = formatMoney(safeField(fields, 5));
    int bidCount = parseIntOrDefault(safeField(fields, 6), 0);
    String status = fallback(safeField(fields, 7), "OPEN");
    String startTime = shortTimestamp(safeField(fields, 8));
    String endTime = shortTimestamp(safeField(fields, 9));
    String winner = safeField(fields, 10);
    String badge = status;

    return new AuctionCardData(
        auctionId,
        itemId,
        title,
        "Auction",
        "Auction loaded from refactor(2) ADMIN_LIST_AUCTIONS stream.",
        currentPrice,
        "0 VND",
        "No reserve",
        bidCount,
        formatBidCount(bidCount),
        fallback(endTime, "Not available"),
        0L,
        badge,
        AUCTION_IMAGE_FALLBACK,
        AUCTION_IMAGE_FALLBACK,
        "AUC-" + auctionId + " - ITEM-" + itemId + " - Seller " + seller
            + " - status " + status + " - ends " + endTime,
        status,
        startTime,
        endTime,
        seller,
        winner,
        "",
        "300",
        "60"
    );
  }

  private AuctionCardData parseAuctionCard(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 19) {
      return null;
    }

    String auctionId = safeField(fields, 0);
    String itemId = safeField(fields, 1);
    String title = fallback(safeField(fields, 2), "Auction #" + auctionId);
    String category = normalizeCategoryLabel(safeField(fields, 3));
    String description = safeField(fields, 4);
    String currentPrice = formatMoney(safeField(fields, 5));
    String minimumIncrement = formatMoney(safeField(fields, 6));
    String reservePrice = safeField(fields, 7).isBlank()
        ? "No reserve"
        : formatMoney(safeField(fields, 7));
    int bidCount = parseIntOrDefault(safeField(fields, 8), 0);
    String status = fallback(safeField(fields, 9), "OPEN");
    String startTime = shortTimestamp(safeField(fields, 10));
    String endTime = shortTimestamp(safeField(fields, 11));
    long secondsLeft = parseLongOrDefault(safeField(fields, 12), 0L);
    String seller = fallback(safeField(fields, 13), "Seller #" + itemId);
    String winner = safeField(fields, 14);
    String imagePayload = safeField(fields, 15);
    String attributes = safeField(fields, 16);
    String snipeWindow = fallback(safeField(fields, 17), "300");
    String snipeExtension = fallback(safeField(fields, 18), "60");
    String badge = status;
    rememberAuctionClock(auctionId, secondsLeft);

    return new AuctionCardData(
        auctionId,
        itemId,
        title,
        category,
        description,
        currentPrice,
        minimumIncrement,
        reservePrice,
        bidCount,
        formatBidCount(bidCount),
        formatTimeLeft(secondsLeft),
        secondsLeft,
        badge,
        firstImage(imagePayload),
        imagePayload,
        "AUC-" + auctionId + " - ITEM-" + itemId + " - Seller " + seller
            + " - status " + status + " - ends " + endTime,
        status,
        startTime,
        endTime,
        seller,
        winner,
        attributes,
        snipeWindow,
        snipeExtension
    );
  }

  /**
   * Don dep listener realtime, listener tao item va thoat session nguoi dung.
   */
  @Override
  protected void handleLogout() {
    stopAuctionDetailCountdown();
    leaveActiveAuctionRoom();
    // 1. Dọn dẹp, gỡ bỏ bộ lắng nghe Real-time của User khỏi danh sách tổng
    if (networkManager != null && userNetworkHandler != null) {
      networkManager.removeMessageHandler(userNetworkHandler);
    }

    // 2. Gỡ bỏ bộ lắng nghe nghiệp vụ Tạo sản phẩm khỏi danh sách tổng
    if (createItemNetworkManager != null && createItemHandler != null) {
      createItemNetworkManager.removeMessageHandler(createItemHandler);
    }

    // 3. Gọi logout của lớp cha để xóa Session và chuyển Scene về auth.fxml.
    super.handleLogout();
  }

  /**
   * Tao map section tinh cho lop cha dung khi chuyen tab dashboard.
   *
   * @return map section cua dashboard user
   */
  @Override
  protected Map<String, SectionContent> createSections() {
    return sections;
  }

  /**
   * Lay section mac dinh khi user vua vao dashboard.
   *
   * @return khoa section auction
   */
  @Override
  protected String getDefaultSectionKey() {
    return "auctions";
  }

  /**
   * Lay nhan vai tro hien thi tren sidebar cua user.
   *
   * @return ten vai tro hien thi
   */
  @Override
  protected String getRoleTitle() {
    return "BIDDER / SELLER";
  }

  /**
   * Chuyen section user va render lai workspace, filter, pagination theo section moi.
   *
   * @param sectionKey khoa section can hien thi
   */
  @Override
  protected void showSection(String sectionKey) {
    stopAuctionDetailCountdown();
    leaveActiveAuctionRoom();
    currentSectionKey = sectionKey;
    activeFilter = getDefaultFilter(sectionKey);
    auctionPage = 1;

    if (workspaceTitleLabel != null) {
      workspaceTitleLabel.setVisible(true);
      workspaceTitleLabel.setManaged(true);
    }
    if (surfaceTitleLabel != null) {
      surfaceTitleLabel.setVisible(true);
      surfaceTitleLabel.setManaged(true);
    }
    if (userActionBar != null) {
      userActionBar.setVisible(true);
      userActionBar.setManaged(true);
    }

    super.showSection(sectionKey);
    hidePageDescriptions();
    resetActivityLinesToLiveDataState();
    updatePrimaryAction(sectionKey);
    setCreateListingFloatingButtonVisible("myItems".equals(sectionKey));
    setDepositFloatingButtonVisible("winners".equals(sectionKey));

    if ("myItems".equals(sectionKey)) {
      if (!sellerItemsLoaded && !sellerItemsLoading) {
        requestCreateListingMetadata();
      }
    } else if (("dashboard".equals(sectionKey) || "auctions".equals(sectionKey))
        && !userAuctionsLoaded && !userAuctionsLoading) {
      requestUserAuctions();
    } else if ("myBids".equals(sectionKey)) {
      if (!myBidsLoaded && !myBidsLoading) {
        requestUserBids();
      }
    } else if ("autoBids".equals(sectionKey)) {
      if (!autoBidsLoaded && !autoBidsLoading) {
        requestUserAutoBids();
      }
    } else if ("winners".equals(sectionKey)) {
      if (!transactionsLoaded && !transactionsLoading) {
        requestUserTransactions();
      }
    }

    renderWorkspace(sectionKey, activeFilter);
  }

  private void resetActivityLinesToLiveDataState() {
    setLabelText(activityLine1, "Auction data loads from database.");
    setLabelText(activityLine2, "No fake auction activity is shown.");
    setLabelText(activityLine3, "Create listings and admin auction rooms to populate this view.");
    setLabelText(
        activityLine4,
        "Manual and auto bidding are wired to the server and update in real time."
    );
  }

  private void setCreateListingFloatingButtonVisible(boolean visible) {
    if (createListingFloatingButton == null) {
      return;
    }
    createListingFloatingButton.setVisible(visible);
    createListingFloatingButton.setManaged(visible);
  }

  private void setDepositFloatingButtonVisible(boolean visible) {
    if (depositFloatingButton == null) {
      return;
    }
    depositFloatingButton.setVisible(visible);
    depositFloatingButton.setManaged(visible);
  }

  private List<CategoryData> buildCategories() {
    return new ArrayList<>();
  }

  private List<AuctionCardData> buildAuctionCards() {
    return new ArrayList<>();
  }

  private CategoryData category(String title, String description, String count, String initials) {
    return new CategoryData(title, description, count, initials);
  }

  private AuctionCardData auction(
      String title,
      String category,
      String price,
      String bids,
      String endsIn,
      String badge,
      String imagePath,
      String detail
  ) {
    return new AuctionCardData(
        "",
        "",
        title,
        category,
        "",
        price,
        "0 VND",
        "No reserve",
        0,
        bids,
        endsIn,
        0,
        badge,
        imagePath,
        imagePath,
        detail,
        badge,
        "not available",
        "not available",
        "not available",
        "",
        "",
        "300",
        "60"
    );
  }

  private String getDefaultFilter(String sectionKey) {
    return "All";
  }

  private void updatePrimaryAction(String sectionKey) {
    if (primaryActionButton == null) {
      return;
    }

    primaryActionButton.setVisible(false);
    primaryActionButton.setManaged(false);
  }

  private void configurePrimaryAction(String text, Runnable action) {
    primaryActionButton.setText(text);
    primaryActionButton.setOnAction(event -> action.run());
  }

  private void applyFilter(String filter) {
    activeFilter = filter;
    auctionPage = 1;
    renderWorkspace(currentSectionKey, activeFilter);
  }

  private void renderWorkspace(String sectionKey, String filter) {
    stopAuctionDetailCountdown();
    if (workspaceBox == null) {
      return;
    }

    workspaceBox.getChildren().clear();

    if (userActionBar != null) {
      userActionBar.getChildren().clear();
    }

    switch (sectionKey) {
      case "auctions" -> renderAuctions(filter);
      case "myBids" -> renderMyBids(filter);
      case "autoBids" -> renderAutoBids(filter);
      case "myItems" -> renderMyItemsLiveSummary(filter);
      case "winners" -> renderTransactions(filter);
      default -> renderDashboard(filter);
    }
  }

  private void renderDashboard(String filter) {
    setWorkspaceTitle("Live Auction Rooms");
    renderChips(
        filter,
        "All",
        "Open",
        "Running",
        "Finished",
        "Paid",
        "Canceled",
        "Vehicle",
        "Art",
        "Electronic"
    );

    addHeader("Auction", "Current Bid", "Time Left");

    List<UserRow> rows = new ArrayList<>();
    for (AuctionCardData card : liveAuctionCards) {
      if (isOwnAuction(card)) {
        continue;
      }
      rows.add(row(
          card.title,
          "AUC-" + card.auctionId + " - ITEM-" + card.itemId + " - Seller " + card.seller,
          card.price,
          formatTimeLeft(currentSecondsLeft(card)),
          card.badge,
          card.detail,
          initialsFor(card.title),
          "View"
      ));
      if (rows.size() == 5) {
        break;
      }
    }

    if (!userAuctionsLoaded) {
      rows.add(row(
          "Loading auctions from database...",
          "Đang lấy dữ liệu từ server refactor(2).",
          "DB",
          "Waiting",
          "Loading",
          "Đang lấy danh sách auction thật từ USER_LIST_AUCTIONS.",
          "DB",
          "Refresh"
      ));
    }

    addFilteredRows(rows, filter);
  }

  private void renderAuctions(String filter) {
    setWorkspaceTitle("Live Auctions From Database");
    renderChips(
        filter,
        "All",
        "Open",
        "Running",
        "Finished",
        "Paid",
        "Canceled",
        "Vehicle",
        "Art",
        "Electronic"
    );

    HBox browseHeader = new HBox(10);
    browseHeader.setAlignment(Pos.CENTER_RIGHT);
    browseHeader.getStyleClass().add("browse-header");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Label sortLabel = new Label(userAuctionsLoaded
        ? "Sorted by nearest end time"
        : "Loading database auctions...");
    sortLabel.getStyleClass().add("sort-pill");

    browseHeader.getChildren().addAll(spacer, sortLabel);
    workspaceBox.getChildren().add(browseHeader);

    List<AuctionCardData> filtered = filterAuctionCards(filter);
    int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) AUCTIONS_PER_PAGE));
    auctionPage = Math.max(1, Math.min(auctionPage, totalPages));

    int fromIndex = (auctionPage - 1) * AUCTIONS_PER_PAGE;
    int toIndex = Math.min(fromIndex + AUCTIONS_PER_PAGE, filtered.size());

    GridPane productGrid = createThreeColumnGrid("auction-grid");

    if (!userAuctionsLoaded) {
      addGridCell(productGrid, emptyCard("Loading real auctions from database..."), 0);
    } else if (filtered.isEmpty()) {
      addGridCell(
          productGrid,
          emptyCard("No live auctions found in database for filter: " + filter),
          0
      );
    } else {
      int index = 0;
      for (AuctionCardData card : filtered.subList(fromIndex, toIndex)) {
        addGridCell(productGrid, buildAuctionProductCard(card), index++);
      }
    }

    workspaceBox.getChildren().add(productGrid);
    workspaceBox.getChildren().add(buildPagination(totalPages, filtered.size()));
  }

  private void renderNoLiveData(String title, String message) {
    setWorkspaceTitle(title);
    renderChips("All", "All");
    VBox card = emptyCard(message);
    workspaceBox.getChildren().add(card);
  }

  private void renderMyItemsLiveSummary(String filter) {
    setWorkspaceTitle("My Items");
    renderChips(
        filter,
        "All",
        "Draft",
        "Pending Review",
        "Available",
        "In Auction",
        "Sold",
        "Removed"
    );
    addHeader("Item", "Starting Price", "Created");

    if (!sellerItemsLoaded) {
      List<UserRow> rows = new ArrayList<>();
      rows.add(row(
          "Loading seller items from database...",
          "Đang lấy dữ liệu thật bằng USER_LIST_ITEMS.",
          "DB",
          "Waiting",
          "Loading",
          "Không dùng item giả trong My Items.",
          "DB",
          "Refresh"
      ));
      addFilteredRows(rows, filter);
      return;
    }

    List<SellerItemData> filteredItems = filterSellerItems(filter);
    if (filteredItems.isEmpty()) {
      addEmptyRow(filter);
      return;
    }

    for (SellerItemData item : filteredItems) {
      addSellerItemRow(item);
    }
  }

  private List<SellerItemData> filterSellerItems(String filter) {
    List<SellerItemData> filtered = new ArrayList<>();
    String normalizedFilter = normalize(filter);

    for (SellerItemData item : sellerItems) {
      String normalizedStatus = normalizeStatusForFilter(item.status);
      String haystack = normalize(String.join(" ",
          item.name,
          item.category,
          item.status,
          normalizedStatus,
          item.description,
          item.attributes,
          item.auctionStatus
      ));

      if (isAllLikeFilter(filter)
          || normalizedStatus.equals(normalizedFilter)
          || haystack.contains(normalizedFilter)) {
        filtered.add(item);
      }
    }

    return filtered;
  }

  private String normalizeStatusForFilter(String status) {
    return normalize(status).replace("_", " ");
  }

  private void addSellerItemRow(SellerItemData item) {
    GridPane row = createTableGrid("data-row");
    row.setOnMouseClicked(event -> openSellerItemDetail(item));

    HBox mainCell = new HBox(9);
    mainCell.setAlignment(Pos.CENTER_LEFT);
    mainCell.setMinWidth(0);
    mainCell.setMaxWidth(Double.MAX_VALUE);
    mainCell.getStyleClass().add("row-main-cell");
    GridPane.setHgrow(mainCell, Priority.ALWAYS);

    StackPane thumbnail = buildImageThumbnail(item.imagePath, item.name);

    VBox textCell = new VBox(2);
    textCell.setMinWidth(0);
    textCell.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(textCell, Priority.ALWAYS);

    Button link = new Button(item.name);
    link.setMnemonicParsing(false);
    link.getStyleClass().add("row-link");
    link.setMinWidth(0);
    link.setMaxWidth(Double.MAX_VALUE);
    link.setTextOverrun(OverrunStyle.ELLIPSIS);
    link.setOnAction(event -> openSellerItemDetail(item));

    Label meta = new Label(item.category + " • ITEM-" + item.itemId
        + auctionMetaSuffix(item));
    meta.getStyleClass().add("row-meta");
    meta.setWrapText(false);
    meta.setMinWidth(0);
    meta.setMaxWidth(Double.MAX_VALUE);
    meta.setTextOverrun(OverrunStyle.ELLIPSIS);

    textCell.getChildren().addAll(link, meta);
    mainCell.getChildren().addAll(thumbnail, textCell);

    Label firstMetric = rowMetric(item.startingPrice);
    Label secondMetric = rowMetric(item.createdAt);
    Label status = statusBadge(prettyStatus(item.status));
    GridPane actions = sellerItemActions(item);

    row.add(mainCell, 0, 0);
    row.add(firstMetric, 1, 0);
    row.add(secondMetric, 2, 0);
    row.add(status, 3, 0);
    row.add(actions, 4, 0);

    GridPane.setHalignment(firstMetric, HPos.CENTER);
    GridPane.setHalignment(secondMetric, HPos.CENTER);
    GridPane.setHalignment(status, HPos.CENTER);
    GridPane.setHalignment(actions, HPos.CENTER);

    workspaceBox.getChildren().add(row);
  }

  private String auctionMetaSuffix(SellerItemData item) {
    if (item.auctionId == null || item.auctionId.isBlank()) {
      return "";
    }
    return " • AUC-" + item.auctionId + " • " + item.bidCount + " bids";
  }

  private GridPane sellerItemActions(SellerItemData item) {
    GridPane actions = new GridPane();
    actions.setHgap(USER_ACTION_GAP);
    actions.setAlignment(Pos.CENTER);
    actions.setMinWidth(0);
    actions.setMaxWidth(Double.MAX_VALUE);
    actions.getStyleClass().add("user-row-actions");
    actions.getColumnConstraints().addAll(
        fixedColumn(USER_ACTION_PRIMARY_WIDTH),
        fixedColumn(USER_ACTION_MORE_WIDTH)
    );

    String primaryAction = normalize(item.status).equals("draft") ? "Edit Draft" : "View";
    Button primary = new Button(primaryAction);
    primary.setMnemonicParsing(false);
    primary.getStyleClass().addAll("mini-action-btn", "user-primary-action-btn");
    primary.setMinWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setPrefWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setMaxWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setTextOverrun(OverrunStyle.ELLIPSIS);
    primary.setOnAction(event -> {
      if (normalize(item.status).equals("draft")) {
        renderCreateItemForm(item);
      } else {
        openSellerItemDetail(item);
      }
    });
    actions.add(primary, 0, 0);

    Region placeholder = new Region();
    placeholder.setMinWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setPrefWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setMaxWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setOpacity(0);
    actions.add(placeholder, 1, 0);
    return actions;
  }

  private void openSellerItemDetail(SellerItemData item) {
    if (item == null) {
      return;
    }
    if (item.auctionId == null || item.auctionId.isBlank()) {
      showTemporaryDetail(item.name, buildSellerItemDetailText(item));
      return;
    }
    renderSellerAuctionDetailPage(item);
  }

  private StackPane buildImageThumbnail(String imagePath, String fallbackText) {
    StackPane thumbnail = buildThumbnail(initialsFor(fallbackText));
    Image image = getCachedImage(imagePath);
    if (image == null || image.isError()) {
      return thumbnail;
    }

    thumbnail.getChildren().clear();
    ImageView imageView = new ImageView(image);
    imageView.setPreserveRatio(false);
    imageView.setFitWidth(54);
    imageView.setFitHeight(46);
    imageView.setSmooth(true);
    imageView.setCache(true);
    Rectangle clip = new Rectangle(54, 46);
    clip.setArcWidth(18);
    clip.setArcHeight(18);
    imageView.setClip(clip);
    thumbnail.getChildren().add(imageView);
    return thumbnail;
  }

  private String buildSellerItemDetailText(SellerItemData item) {
    StringBuilder builder = new StringBuilder();
    builder.append("Item ID: ITEM-").append(item.itemId).append('\n');
    builder.append("Category: ").append(item.category).append('\n');
    builder.append("Status: ").append(prettyStatus(item.status)).append('\n');
    builder.append("Starting price: ").append(item.startingPrice).append('\n');
    builder.append("Created at: ").append(item.createdAt).append('\n');
    if (!item.auctionId.isBlank()) {
      builder.append("Auction ID: AUC-").append(item.auctionId).append('\n');
      builder.append("Auction status: ").append(item.auctionStatus).append('\n');
      builder.append("Current price: ").append(item.currentPrice).append('\n');
      builder.append("Highest bidder: ")
          .append(fallback(item.highestBidder, "No bids yet"))
          .append('\n');
      builder.append("Bid count: ").append(item.bidCount).append('\n');
      builder.append("Ends at: ").append(fallback(item.auctionEndTime, "Not scheduled"))
          .append('\n');
    }
    if (!item.description.isBlank()) {
      builder.append("Description: ").append(item.description).append('\n');
    }
    if (!item.attributes.isBlank()) {
      builder.append("Attributes:\n").append(item.attributes);
    }
    return builder.toString();
  }

  private String prettyStatus(String status) {
    String normalized = normalize(status);
    return switch (normalized) {
      case "pending review" -> "Pending Review";
      case "in auction" -> "In Auction";
      default -> {
        if (normalized.isBlank()) {
          yield "Unknown";
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
          if (part.isBlank()) {
            continue;
          }
          if (builder.length() > 0) {
            builder.append(' ');
          }
          builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        yield builder.toString();
      }
    };
  }

  private void renderMyBids(String filter) {
    setWorkspaceTitle("My Bids");
    renderChips(filter, "All", "Winning", "Outbid", "Won", "Lost");
    addHeader("Auction", "Current Bid", "Your Bid");

    if (!myBidsLoaded) {
      requestUserBids();
      addLoadingRow("Loading your bid history...");
      return;
    }

    List<UserRow> rows = new ArrayList<>();
    for (MyBidData bid : myBids) {
      String status = resolveMyBidStatus(bid);
      rows.add(row(
          bid.itemName,
          buildMyBidMeta(bid),
          bid.currentPrice,
          bid.userBid,
          status,
          buildMyBidDetailText(bid, status),
          initialsFor(bid.itemName),
          isAuctionStillOpen(bid) ? "Open" : "View"
      ));
    }

    addFilteredRows(rows, filter);
  }

  private void addLoadingRow(String message) {
    GridPane row = createTableGrid("data-row");
    Label loading = new Label(message);
    loading.getStyleClass().add("row-meta");
    loading.setWrapText(true);
    loading.setMaxWidth(Double.MAX_VALUE);
    GridPane.setColumnSpan(loading, 5);
    row.add(loading, 0, 0);
    workspaceBox.getChildren().add(row);
  }

  private String buildMyBidMeta(MyBidData bid) {
    String category = fallback(prettyStatus(bid.category), "Auction");
    String mode = bid.autoBid ? "Auto bid" : "Manual bid";
    return category + " • AUC-" + bid.auctionId + " • " + mode + " • " + bid.bidTime;
  }

  private String buildMyBidDetailText(MyBidData bid, String status) {
    StringBuilder builder = new StringBuilder();
    builder.append("Auction ID: AUC-").append(bid.auctionId).append('\n');
    builder.append("Item ID: ITEM-").append(bid.itemId).append('\n');
    builder.append("Bid ID: BID-").append(bid.bidId).append('\n');
    builder.append("Category: ").append(prettyStatus(bid.category)).append('\n');
    builder.append("Current price: ").append(bid.currentPrice).append('\n');
    builder.append("Your latest bid: ").append(bid.userBid).append('\n');
    builder.append("Status: ").append(status).append('\n');
    builder.append("Auction status: ").append(prettyStatus(bid.auctionStatus)).append('\n');
    builder.append("Bid type: ").append(bid.autoBid ? "Auto bid" : "Manual bid").append('\n');
    builder.append("Bid time: ").append(bid.bidTime).append('\n');
    builder.append("End time: ").append(bid.endTime);
    return builder.toString();
  }

  private String resolveMyBidStatus(MyBidData bid) {
    String auctionStatus = normalize(bid.auctionStatus);
    boolean isWinner = bid.winnerId != null && !bid.winnerId.isBlank()
        && bid.winnerId.equals(currentUserIdString());

    if (auctionStatus.equals("finished") || auctionStatus.equals("paid")) {
      return isWinner ? "Won" : "Lost";
    }
    if (auctionStatus.equals("canceled")) {
      return "Canceled";
    }
    if (isWinner || normalize(bid.bidStatus).equals("winning")) {
      return "Winning";
    }
    return "Outbid";
  }

  private boolean isAuctionStillOpen(MyBidData bid) {
    String auctionStatus = normalize(bid.auctionStatus);
    return auctionStatus.equals("open") || auctionStatus.equals("running");
  }

  private String currentUserIdString() {
    User currentUser = SessionManager.getCurrentUser();
    return currentUser == null ? "" : String.valueOf(currentUser.getUserId());
  }

  private void renderAutoBids(String filter) {
    setWorkspaceTitle("Auto Bid Rules");
    renderChips(filter, "All", "Active", "Completed", "Canceled");
    addHeader("Auto Rule", "Max Bid", "Increment");

    if (!autoBidsLoaded) {
      requestUserAutoBids();
      addLoadingRow("Loading your auto-bid rules...");
      return;
    }

    List<UserRow> rows = new ArrayList<>();
    for (AutoBidData rule : autoBids) {
      String status = resolveAutoBidStatus(rule);
      rows.add(row(
          rule.itemName,
          buildAutoBidMeta(rule),
          rule.maxBid,
          rule.increment,
          status,
          buildAutoBidDetailText(rule, status),
          initialsFor(rule.itemName),
          "View"
      ));
    }

    addFilteredRows(rows, filter);
  }

  private String buildAutoBidMeta(AutoBidData rule) {
    return fallback(prettyStatus(rule.category), "Auction")
        + " • AUC-" + rule.auctionId
        + " • Current " + rule.currentPrice
        + " • Seller " + rule.seller;
  }

  private String buildAutoBidDetailText(AutoBidData rule, String status) {
    StringBuilder builder = new StringBuilder();
    builder.append("Auto Bid ID: AB-").append(rule.autoBidId).append('\n');
    builder.append("Auction ID: AUC-").append(rule.auctionId).append('\n');
    builder.append("Item ID: ITEM-").append(rule.itemId).append('\n');
    builder.append("Category: ").append(prettyStatus(rule.category)).append('\n');
    builder.append("Current price: ").append(rule.currentPrice).append('\n');
    builder.append("Max bid: ").append(rule.maxBid).append('\n');
    builder.append("Increment: ").append(rule.increment).append('\n');
    builder.append("Status: ").append(status).append('\n');
    builder.append("Ends at: ").append(fallback(rule.endTime, "Not scheduled")).append('\n');
    builder.append("Seller: ").append(rule.seller);
    return builder.toString();
  }

  private String resolveAutoBidStatus(AutoBidData rule) {
    return prettyStatus(rule.status);
  }

  private void renderMyItems(String filter) {
    renderMyItemsLiveSummary(filter);
  }

  private HBox buildCreateListingActionBar() {
    HBox actionBar = new HBox();
    actionBar.setAlignment(Pos.CENTER_RIGHT);
    actionBar.setMaxWidth(Double.MAX_VALUE);
    actionBar.getStyleClass().add("floating-create-bar");

    Button createListingButton = new Button("Create Listing");
    createListingButton.setMnemonicParsing(false);
    createListingButton.getStyleClass().add("floating-create-btn");
    createListingButton.setOnAction(event -> renderCreateItemForm());

    actionBar.getChildren().add(createListingButton);
    return actionBar;
  }

  private void renderTransactions(String filter) {
    setWorkspaceTitle("Transactions");
    renderChips(
        filter,
        "All",
        "Pending",
        "Completed",
        "Failed",
        "Refunded",
        "Deposit",
        "Withdraw",
        "Hold",
        "Release",
        "Payment",
        "Refund"
    );
    addTransactionOverviewCard();
    addTransactionHeader();

    if (!transactionsLoaded) {
      requestUserTransactions();
      addLoadingRow("Loading post-auction transactions from database...");
      return;
    }

    int count = 0;
    for (TransactionData transaction : transactions) {
      if (matchesTransactionFilter(transaction, filter)) {
        addTransactionRow(transaction);
        count++;
      }
    }

    if (count == 0) {
      addEmptyRow(filter);
    }
  }

  private void addTransactionOverviewCard() {
    BigDecimal completedPayments = BigDecimal.ZERO;
    BigDecimal pendingDue = BigDecimal.ZERO;

    for (TransactionData transaction : transactions) {
      if (transaction == null) {
        continue;
      }
      BigDecimal amount = moneyValue(transaction.amount);
      if (transaction.isWallet()) {
        continue;
      }
      if (transaction.isPayable()) {
        pendingDue = pendingDue.add(amount);
      } else {
        String status = normalize(resolveTransactionStatus(transaction));
        if (status.equals("completed") || status.equals("refunded")) {
          completedPayments = completedPayments.add(amount);
        }
      }
    }

    HBox card = new HBox(18);
    card.setAlignment(Pos.CENTER_LEFT);
    card.setMaxWidth(Double.MAX_VALUE);
    card.getStyleClass().add("transaction-wallet-card");

    VBox main = new VBox(14);
    main.setMinWidth(0);
    main.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(main, Priority.ALWAYS);

    HBox titleRow = new HBox(10);
    titleRow.setAlignment(Pos.CENTER_LEFT);
    Label title = new Label("Wallet");
    title.getStyleClass().add("transaction-wallet-title");
    Label icon = new Label("✦");
    icon.getStyleClass().add("transaction-wallet-icon");
    titleRow.getChildren().addAll(title, icon);

    HBox amountRow = new HBox(12);
    amountRow.setAlignment(Pos.CENTER_LEFT);
    Label total = new Label(walletBalanceKnown
        ? formatMoney(currentWalletBalance.toPlainString())
        : "Updating...");
    total.getStyleClass().add("transaction-wallet-total");
    total.setTextOverrun(OverrunStyle.ELLIPSIS);
    Label status = new Label(walletBalanceKnown ? "Ready to bid" : "Updating");
    status.getStyleClass().add("transaction-wallet-status");
    amountRow.getChildren().addAll(total, status);

    Label caption = new Label(walletBalanceKnown
        ? "Available for bidding and payments"
        : "Updating your wallet balance...");
    caption.getStyleClass().add("transaction-wallet-caption");
    main.getChildren().addAll(titleRow, amountRow, caption);

    VBox metrics = new VBox(12);
    metrics.setMinWidth(0);
    metrics.setPrefWidth(360);
    metrics.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(metrics, Priority.ALWAYS);

    HBox topMetrics = new HBox(12);
    topMetrics.setAlignment(Pos.CENTER_RIGHT);
    Region topSpacer = new Region();
    HBox.setHgrow(topSpacer, Priority.ALWAYS);
    topMetrics.getChildren().addAll(
        topSpacer,
        transactionMetricCard("Paid", formatMoney(completedPayments.toPlainString())),
        transactionMetricCard("Pending", formatMoney(pendingDue.toPlainString()))
    );

    metrics.getChildren().add(topMetrics);
    card.getChildren().addAll(main, metrics);
    workspaceBox.getChildren().add(card);
  }

  private VBox transactionMetricCard(String labelText, String valueText) {
    VBox metric = new VBox(6);
    metric.setMinWidth(0);
    metric.setPrefWidth(166);
    metric.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(metric, Priority.ALWAYS);
    metric.getStyleClass().add("transaction-wallet-metric");

    Label label = new Label(labelText);
    label.getStyleClass().add("transaction-wallet-metric-label");
    Label value = new Label(valueText);
    value.getStyleClass().add("transaction-wallet-metric-value");
    value.setTextOverrun(OverrunStyle.ELLIPSIS);
    value.setMaxWidth(Double.MAX_VALUE);

    metric.getChildren().addAll(label, value);
    return metric;
  }

  private void updateKnownWalletBalanceFromMessage(String message) {
    String[] parts = message == null ? new String[0] : message.split("\\|", 3);
    if (parts.length >= 3) {
      updateKnownWalletBalance(parts[2]);
    }
  }

  private void updateKnownWalletBalance(String rawBalance) {
    currentWalletBalance = moneyValue(rawBalance);
    walletBalanceKnown = true;
  }

  private void syncWalletBalanceFromTransactionRows() {
    for (TransactionData transaction : transactions) {
      if (transaction == null || transaction.balanceAfter.isBlank()) {
        continue;
      }
      updateKnownWalletBalance(transaction.balanceAfter);
      return;
    }
  }

  private boolean isPaymentSubmitting(TransactionData transaction) {
    return transaction != null && isPaymentSubmitting(transaction.auctionId);
  }

  private boolean isPaymentSubmitting(String auctionId) {
    return paymentSubmittingByAuction.containsKey(normalizeAuctionKey(auctionId));
  }

  private void markPaymentSubmitting(String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    if (!key.isBlank()) {
      paymentSubmittingByAuction.put(key, Boolean.TRUE);
    }
  }

  private void clearPaymentSubmitting(String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    if (!key.isBlank()) {
      paymentSubmittingByAuction.remove(key);
    }
  }

  private void dropResolvedPaymentSubmissions() {
    if (paymentSubmittingByAuction.isEmpty()) {
      return;
    }

    paymentSubmittingByAuction.keySet().removeIf(key -> {
      TransactionData latest = findTransactionByAuctionId(key);
      return latest == null || !latest.isPayable();
    });
  }

  private String normalizeAuctionKey(String auctionId) {
    return auctionId == null ? "" : auctionId.trim();
  }

  private void reloadTransactionsFromServer() {
    transactionsLoaded = false;
    if (transactionsLoading) {
      transactionsReloadPending = true;
      refreshTransactionWorkspaceIfVisible();
      return;
    }
    requestUserTransactions();
    refreshTransactionWorkspaceIfVisible();
  }

  private void requestPendingTransactionReloadIfNeeded() {
    if (!transactionsReloadPending) {
      return;
    }
    transactionsReloadPending = false;
    reloadTransactionsFromServer();
  }

  private void refreshTransactionWorkspaceIfVisible() {
    if ("winners".equals(currentSectionKey)) {
      renderWorkspace(currentSectionKey, activeFilter);
      applyTransactionStatsIfVisible();
    }
  }

  private boolean matchesTransactionFilter(TransactionData transaction, String filter) {
    if (transaction == null || filter == null || isAllLikeFilter(filter)) {
      return true;
    }

    String normalizedFilter = normalize(filter);
    String status = normalize(resolveTransactionStatus(transaction));
    String haystack = normalize(String.join(" ",
        transaction.itemName,
        transaction.role,
        transaction.counterpartName,
        transaction.amount,
        transaction.paymentStatus,
        transaction.auctionStatus,
        transaction.walletTxType,
        transaction.walletNote,
        status
    ));

    String paymentStatus = normalize(transaction.paymentStatus);
    String walletType = normalize(transaction.walletTxType);

    return switch (normalizedFilter) {
      case "pending", "completed", "failed", "refunded" -> paymentStatus.equals(normalizedFilter)
          || status.equals(normalizedFilter);
      case "deposit", "withdraw", "hold", "release", "payment", "refund" ->
          walletType.equals(normalizedFilter);
      default -> haystack.contains(normalizedFilter);
    };
  }

  private void addTransactionHeader() {
    GridPane header = createTransactionTableGrid("data-header");

    header.add(headerLabel("Type", HPos.CENTER), 0, 0);
    header.add(headerLabel("Description", HPos.CENTER), 1, 0);
    header.add(headerLabel("Amount", HPos.CENTER), 2, 0);
    header.add(headerLabel("Balance After", HPos.CENTER), 3, 0);
    header.add(headerLabel("Time", HPos.CENTER), 4, 0);
    header.add(headerLabel("Action", HPos.CENTER), 5, 0);

    workspaceBox.getChildren().add(header);
  }

  private GridPane createTransactionTableGrid(String styleClass) {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setAlignment(Pos.CENTER_LEFT);
    grid.getStyleClass().addAll(styleClass, "transaction-table-row");
    grid.setMaxWidth(Double.MAX_VALUE);
    grid.setMinWidth(0);

    grid.getColumnConstraints().addAll(
        percentColumn(12),
        percentColumn(30),
        percentColumn(14),
        percentColumn(14),
        percentColumn(19),
        percentColumn(11)
    );
    return grid;
  }

  private void addTransactionRow(TransactionData transaction) {
    GridPane row = createTransactionTableGrid("data-row");
    String detail = buildTransactionDetailText(transaction);
    row.setOnMouseClicked(event -> showTemporaryDetail(
        resolveTransactionTypeText(transaction),
        detail
    ));

    Label type = transactionTableLabel(resolveTransactionTypeText(transaction), "transaction-type-cell");
    Label description = transactionTableLabel(
        buildTransactionDescriptionText(transaction),
        "transaction-description-cell"
    );
    Label amount = transactionTableLabel(
        buildSignedTransactionAmount(transaction),
        "transaction-amount-cell"
    );
    Label balanceAfter = transactionTableLabel(
        fallback(transaction.balanceAfter, "—"),
        "transaction-balance-cell"
    );
    Label time = transactionTableLabel(
        buildTransactionTimeText(transaction),
        "transaction-time-cell"
    );
    Node action = transactionActionCell(transaction);

    type.setWrapText(false);
    time.setWrapText(false);
    time.setTextOverrun(OverrunStyle.CLIP);

    row.add(type, 0, 0);
    row.add(description, 1, 0);
    row.add(amount, 2, 0);
    row.add(balanceAfter, 3, 0);
    row.add(time, 4, 0);
    row.add(action, 5, 0);

    GridPane.setHalignment(type, HPos.CENTER);
    GridPane.setHalignment(description, HPos.CENTER);
    GridPane.setHalignment(amount, HPos.CENTER);
    GridPane.setHalignment(balanceAfter, HPos.CENTER);
    GridPane.setHalignment(time, HPos.CENTER);
    GridPane.setHalignment(action, HPos.CENTER);

    workspaceBox.getChildren().add(row);
  }

  private Label transactionTableLabel(String text, String styleClass) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add(styleClass);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setMinWidth(0);
    label.setWrapText(true);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    return label;
  }

  private Node transactionActionCell(TransactionData transaction) {
    if (transaction.isPayable()) {
      boolean submitting = isPaymentSubmitting(transaction);
      Button payButton = new Button(submitting ? "Paying" : "Pay");
      payButton.setMnemonicParsing(false);
      payButton.getStyleClass().addAll("mini-action-btn", "transaction-pay-btn");
      payButton.setMaxWidth(Double.MAX_VALUE);
      payButton.setDisable(submitting);
      payButton.setOnAction(event -> {
        submitTransactionPayment(transaction);
        event.consume();
      });
      return payButton;
    }

    Label status = transactionTableLabel(resolveTransactionStatus(transaction), "transaction-status-cell");
    status.setWrapText(false);
    status.setTextOverrun(OverrunStyle.ELLIPSIS);
    return status;
  }

  private String resolveTransactionTypeText(TransactionData transaction) {
    if (transaction.isDeposit()) {
      return "Deposit";
    }
    if (transaction.isWallet()) {
      return prettyStatus(transaction.walletTxType);
    }
    if (normalize(transaction.paymentStatus).equals("refunded")
        || normalize(transaction.walletTxType).equals("refund")) {
      return "Refund";
    }
    return "Payment";
  }

  private String buildTransactionDescriptionText(TransactionData transaction) {
    if (transaction.isDeposit()) {
      return "Wallet top-up";
    }
    if (transaction.isWallet()) {
      String note = cleanWalletNote(transaction.walletNote);
      return note.isBlank() ? prettyStatus(transaction.walletTxType) : note;
    }

    String itemName = fallback(transaction.itemName, "Auction #" + transaction.auctionId);
    String status = normalize(transaction.paymentStatus);
    if (status.equals("pending")) {
      return transaction.isBuyer()
          ? "Pending payment • " + itemName
          : "Pending buyer payment • " + itemName;
    }
    if (status.equals("refunded")) {
      return transaction.isBuyer()
          ? "Refunded • " + itemName
          : "Refunded to buyer • " + itemName;
    }
    if (transaction.isSeller()) {
      return "Received from sold auction • " + itemName;
    }
    return "Paid for winning auction • " + itemName;
  }

  private String cleanWalletNote(String note) {
    String trimmed = note == null ? "" : note.trim();
    if (trimmed.equalsIgnoreCase("Wallet top-up from user dashboard")) {
      return "Wallet top-up";
    }
    return trimmed;
  }

  private String buildSignedTransactionAmount(TransactionData transaction) {
    BigDecimal amount = moneyValue(transaction.amount);
    String formatted = formatMoney(amount.abs().toPlainString());
    if (amount.compareTo(BigDecimal.ZERO) == 0) {
      return formatted;
    }
    String sign = transactionAmountSign(transaction);
    return sign.isBlank() ? formatted : sign + formatted;
  }

  private String transactionAmountSign(TransactionData transaction) {
    String walletType = normalize(transaction.walletTxType);
    String note = normalize(transaction.walletNote);
    String paymentStatus = normalize(transaction.paymentStatus);

    if (transaction.isWallet()) {
      if (walletType.equals("deposit") || walletType.equals("release")) {
        return "+";
      }
      if (walletType.equals("withdraw") || walletType.equals("hold")) {
        return "-";
      }
    }

    if (paymentStatus.equals("refunded") || walletType.equals("refund")) {
      return transaction.isBuyer() || note.contains("received") ? "+" : "-";
    }
    if (transaction.isSeller() || note.startsWith("received")) {
      return "+";
    }
    if (transaction.isBuyer()) {
      return "-";
    }
    return "";
  }

  private String buildTransactionTimeText(TransactionData transaction) {
    String rawTime = transaction.paidAt.isBlank()
        || transaction.paidAt.equalsIgnoreCase("not available")
        ? transaction.createdAt
        : transaction.paidAt;
    LocalDateTime parsed = parseTimestamp(rawTime);
    if (parsed == null) {
      return fallback(rawTime, "Not available");
    }
    return parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
  }


  private GridPane transactionActions(TransactionData transaction, String detail) {
    GridPane actions = new GridPane();
    actions.setHgap(USER_ACTION_GAP);
    actions.setAlignment(Pos.CENTER);
    actions.setMinWidth(0);
    actions.setMaxWidth(Double.MAX_VALUE);
    actions.getStyleClass().add("user-row-actions");
    actions.getColumnConstraints().addAll(
        fixedColumn(USER_ACTION_PRIMARY_WIDTH),
        fixedColumn(USER_ACTION_MORE_WIDTH)
    );

    Button primary = new Button(transaction.isPayable() ? "Pay Now" : "View");
    primary.setMnemonicParsing(false);
    primary.getStyleClass().addAll("mini-action-btn", "user-primary-action-btn");
    primary.setMinWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setPrefWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setMaxWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setTextOverrun(OverrunStyle.ELLIPSIS);
    primary.setOnAction(event -> {
      if (transaction.isPayable()) {
        submitTransactionPayment(transaction);
      } else {
        showTemporaryDetail(transaction.itemName, detail);
      }
    });

    actions.add(primary, 0, 0);
    GridPane.setHalignment(primary, HPos.CENTER);

    Region placeholder = new Region();
    placeholder.setMinWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setPrefWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setMaxWidth(USER_ACTION_MORE_WIDTH);
    placeholder.setOpacity(0);
    actions.add(placeholder, 1, 0);
    return actions;
  }

  private String buildTransactionMeta(TransactionData transaction) {
    String role = transaction.isWallet()
        ? prettyStatus(transaction.walletTxType)
        : transaction.isBuyer() ? "Won auction" : "Sold auction";
    String status = prettyStatus(transaction.paymentStatus);
    String date = transaction.paidAt.isBlank() ? transaction.createdAt : transaction.paidAt;
    String reference = transaction.isWallet() ? "Wallet" : "AUC-" + transaction.auctionId;
    return role + " • " + reference + " • " + status + " • "
        + fallback(date, "No timestamp");
  }

  private String buildTransactionDetailText(TransactionData transaction) {
    StringBuilder builder = new StringBuilder();
    if (transaction.isWallet()) {
      builder.append("Wallet transaction: WT-").append(transaction.walletTxId).append('\n');
      builder.append("Type: ").append(prettyStatus(transaction.walletTxType)).append('\n');
    } else {
      builder.append("Payment ID: PAY-").append(transaction.paymentId).append('\n');
      builder.append("Auction ID: AUC-").append(transaction.auctionId).append('\n');
      builder.append("Role: ").append(transaction.isBuyer() ? "Buyer" : "Seller").append('\n');
      builder.append("Counterparty: ").append(transaction.counterpartName).append('\n');
    }
    builder.append("Amount: ").append(transaction.amount).append('\n');
    builder.append("Payment status: ").append(prettyStatus(transaction.paymentStatus)).append('\n');
    builder.append("Auction status: ").append(prettyStatus(transaction.auctionStatus)).append('\n');
    builder.append("Created at: ").append(fallback(transaction.createdAt, "Not available")).append('\n');
    builder.append("Paid at: ").append(fallback(transaction.paidAt, "Not paid yet"));
    if (!transaction.walletTxId.isBlank() && !"0".equals(transaction.walletTxId)) {
      builder.append('\n').append("Wallet transaction: WT-").append(transaction.walletTxId);
      if (!transaction.walletTxType.isBlank()) {
        builder.append(" (").append(prettyStatus(transaction.walletTxType)).append(')');
      }
    }
    if (!transaction.walletNote.isBlank()) {
      builder.append('\n').append("Note: ").append(transaction.walletNote);
    }
    return builder.toString();
  }

  private String resolveTransactionStatus(TransactionData transaction) {
    if (transaction.isWallet()) {
      return prettyStatus(fallback(transaction.paymentStatus, "COMPLETED"));
    }
    return prettyStatus(transaction.paymentStatus);
  }

  private TransactionData findTransactionByAuctionId(String auctionId) {
    String key = normalizeAuctionKey(auctionId);
    if (key.isBlank()) {
      return null;
    }
    for (TransactionData transaction : transactions) {
      if (transaction != null && key.equals(normalizeAuctionKey(transaction.auctionId))) {
        return transaction;
      }
    }
    return null;
  }

  private void submitTransactionPayment(TransactionData transaction) {
    TransactionData latestTransaction = findTransactionByAuctionId(
        transaction == null ? "" : transaction.auctionId
    );
    if (latestTransaction == null) {
      latestTransaction = transaction;
    }

    if (latestTransaction == null || !latestTransaction.isPayable()) {
      showTemporaryDetail("Payment unavailable", "This transaction is not payable in its current state.");
      return;
    }
    if (latestTransaction.auctionId.isBlank() || "0".equals(latestTransaction.auctionId)) {
      showTemporaryDetail("Payment unavailable", "This payable row is missing auction information.");
      return;
    }
    if (isPaymentSubmitting(latestTransaction)) {
      showTemporaryDetail("Payment processing", "This payment request is already being sent to the server.");
      return;
    }

    BigDecimal amount = moneyValue(latestTransaction.amount);
    if (walletBalanceKnown && currentWalletBalance.compareTo(amount) < 0) {
      notifUIHandler.showError(
          "Payment blocked",
          "Wallet balance is not enough for this payment. Please deposit before paying."
      );
      showTemporaryDetail(
          "Payment blocked",
          "Required: " + formatMoney(amount.toPlainString()) + "\n"
              + "Available: " + formatMoney(currentWalletBalance.toPlainString())
      );
      return;
    }

    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      notifUIHandler.showError("Payment failed", "Cannot connect to the server.");
      return;
    }

    markPaymentSubmitting(latestTransaction.auctionId);
    refreshTransactionWorkspaceIfVisible();
    networkManager.send(buildConfirmPaymentCommand(latestTransaction));
  }

  private String buildConfirmPaymentCommand(TransactionData transaction) {
    String auctionId = normalizeAuctionKey(transaction.auctionId);
    String itemName = commandSafeText(fallback(transaction.itemName, "Auction #" + auctionId));
    return itemName.isBlank()
        ? "CONFIRM_PAYMENT " + auctionId
        : "CONFIRM_PAYMENT " + auctionId + " " + itemName;
  }

  private String commandSafeText(String value) {
    return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
  }


  private void renderSettings(String filter) {
    setWorkspaceTitle("Settings Groups");
    renderChips(filter, "Account", "Notifications", "Bidding", "Seller", "Payment", "Preferences");

    addHeader("Setting Group", "Configured", "Scope");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "Account",
        "Display name, email, phone, avatar, and password",
        "Profile",
        "User",
        "Ready",
        "Account settings should manage profile and password changes.",
        "AC",
        "Edit"
    ));
    rows.add(row(
        "Notifications",
        "Outbid, won auction, new bid, payment reminders",
        "8 alerts",
        "Email / in-app",
        "Active",
        "Notification settings are critical in auction flows because timing matters.",
        "NO",
        "Edit"
    ));
    rows.add(row(
        "Bidding Preferences",
        "Confirm bid, quick bid, default increment, auto-bid warning threshold",
        "4 rules",
        "Bidding",
        "Ready",
        "Bidding preferences reduce mistakes before placing manual or automated bids.",
        "BP",
        "Edit"
    ));
    rows.add(row(
        "Seller Profile",
        "Seller name, bio, pickup location, verification status",
        "Seller",
        "Listings",
        "Ready",
        "Seller profile supports My Items and Sold Auctions fulfilment.",
        "SP",
        "Edit"
    ));
    rows.add(row(
        "Payment & Payout",
        "Payment method, payout method, invoices, receipts",
        "Prepared",
        "Transactions",
        "Pending",
        "Payment and payout settings support Won Auctions and Sold Auctions.",
        "PP",
        "Edit"
    ));
    rows.add(row(
        "App Preferences",
        "Theme, language, currency, timezone, default auction view and sort",
        "VND",
        "Dashboard",
        "Ready",
        "App preferences should persist default grid/list sorting.",
        "AP",
        "Edit"
    ));

    addFilteredRows(rows, filter);
  }

  private VBox sectionHeader(String titleText, String descriptionText) {
    VBox box = new VBox(3);
    Label title = new Label(titleText);
    title.getStyleClass().add("section-mini-title");
    box.getChildren().add(title);

    if (descriptionText != null && !descriptionText.isBlank()) {
      Label description = new Label(descriptionText);
      description.getStyleClass().add("row-meta");
      description.setWrapText(true);
      box.getChildren().add(description);
    }

    return box;
  }

  private VBox buildCategoryCard(CategoryData data) {
    VBox card = new VBox(8);
    card.getStyleClass().add("category-card");
    card.setMinWidth(0);
    card.setMaxWidth(Double.MAX_VALUE);

    HBox top = new HBox(8);
    top.setAlignment(Pos.CENTER_LEFT);

    StackPane icon = buildThumbnail(data.initials);
    Label title = new Label(data.title);
    title.getStyleClass().add("category-title");
    title.setWrapText(true);
    top.getChildren().addAll(icon, title);

    Label description = new Label(data.description);
    description.getStyleClass().add("category-desc");
    description.setWrapText(true);

    HBox bottom = new HBox(8);
    bottom.setAlignment(Pos.CENTER_LEFT);
    Label count = new Label(data.count);
    count.getStyleClass().add("category-count");
    Button open = new Button("View >");
    open.setMnemonicParsing(false);
    open.getStyleClass().add("category-link");
    open.setOnAction(event -> applyFilter(data.title));
    bottom.getChildren().addAll(count, open);

    card.getChildren().addAll(top, description, bottom);
    return card;
  }

  private VBox buildAuctionProductCard(AuctionCardData data) {
    VBox card = new VBox(10);
    card.getStyleClass().add("auction-market-card");
    card.setMinWidth(0);
    card.setMaxWidth(Double.MAX_VALUE);

    StackPane imageWrap = createAuctionImageWrap(data.imagePath, 205);

    Label reserveBadge = new Label(isNoReserve(data.reservePrice) ? "No Reserve" : "Reserve Set");
    reserveBadge.getStyleClass().add("auction-market-badge");
    reserveBadge.getStyleClass().add(
        isNoReserve(data.reservePrice) ? "status-neutral" : "status-good"
    );
    StackPane.setAlignment(reserveBadge, Pos.TOP_LEFT);

    Label timeBadge = new Label(formatTimeLeft(currentSecondsLeft(data)));
    timeBadge.getStyleClass().add("auction-market-time");
    StackPane.setAlignment(timeBadge, Pos.TOP_RIGHT);
    imageWrap.getChildren().addAll(reserveBadge, timeBadge);

    VBox content = new VBox(8);
    content.getStyleClass().add("auction-market-content");

    Label title = new Label(data.title);
    title.getStyleClass().add("auction-market-title");
    title.setWrapText(true);

    Label meta = new Label(data.category + " • " + data.status + " • Seller " + data.seller);
    meta.getStyleClass().add("auction-market-meta");
    meta.setWrapText(true);

    VBox priceBox = new VBox(2);
    Label currentBidLabel = new Label("Current Bid");
    currentBidLabel.getStyleClass().add("auction-market-label");
    Label currentBid = new Label(data.price);
    currentBid.getStyleClass().add("auction-market-price");
    priceBox.getChildren().addAll(currentBidLabel, currentBid);

    HBox facts = new HBox(12);
    facts.getStyleClass().add("auction-market-facts");
    Label bidCount = new Label(data.bids);
    bidCount.getStyleClass().add("auction-market-small-fact");
    Label increment = new Label("Min increment " + data.minimumIncrement);
    increment.getStyleClass().add("auction-market-small-fact");
    Region factSpacer = new Region();
    HBox.setHgrow(factSpacer, Priority.ALWAYS);
    facts.getChildren().addAll(bidCount, factSpacer, increment);

    Button bid = new Button("Place Bid");
    bid.setMnemonicParsing(false);
    bid.getStyleClass().add("auction-market-bid-btn");
    bid.setDisable(!isAuctionBidEnabled(data));
    bid.setMaxWidth(Double.MAX_VALUE);
    bid.setOnAction(event -> renderAuctionDetailPage(latestAuctionCard(data)));

    Button view = new Button("View Auction");
    view.setMnemonicParsing(false);
    view.getStyleClass().add("auction-market-view-btn");
    view.setMaxWidth(Double.MAX_VALUE);
    view.setOnAction(event -> renderAuctionDetailPage(latestAuctionCard(data)));

    content.getChildren().addAll(title, meta, priceBox, facts, bid, view);
    card.getChildren().addAll(imageWrap, content);
    return card;
  }

  private StackPane createAuctionImageWrap(String imagePath, double height) {
    StackPane imageWrap = new StackPane();
    imageWrap.getStyleClass().add("auction-market-image-wrap");
    imageWrap.setMinWidth(0);
    imageWrap.setPrefHeight(height);
    imageWrap.setMinHeight(height);
    imageWrap.setMaxHeight(height);
    imageWrap.setMaxWidth(Double.MAX_VALUE);

    Rectangle wrapClip = new Rectangle();
    wrapClip.widthProperty().bind(imageWrap.widthProperty());
    wrapClip.heightProperty().bind(imageWrap.heightProperty());
    wrapClip.setArcWidth(30);
    wrapClip.setArcHeight(30);
    imageWrap.setClip(wrapClip);

    setAuctionImageContent(imageWrap, imagePath, height);
    return imageWrap;
  }

  private void setAuctionImageContent(StackPane imageWrap, String imagePath, double height) {
    imageWrap.getChildren().clear();
    Image image = getCachedImage(imagePath);
    if (image == null || image.isError()) {
      image = getCachedImage(AUCTION_IMAGE_FALLBACK);
    }
    if (image != null && !image.isError()) {
      ImageView imageView = new ImageView(image);
      imageView.getStyleClass().add("product-image");
      imageView.setSmooth(true);
      imageView.setCache(true);
      imageView.setPreserveRatio(false);
      imageView.setFitWidth(PRODUCT_IMAGE_INITIAL_WIDTH);
      imageView.setFitHeight(height);
      imageView.fitWidthProperty().bind(imageWrap.widthProperty());
      imageView.fitHeightProperty().bind(imageWrap.heightProperty());
      imageWrap.widthProperty().addListener((observable, oldValue, newValue) ->
          updateCoverViewport(imageView, imageWrap));
      imageWrap.heightProperty().addListener((observable, oldValue, newValue) ->
          updateCoverViewport(imageView, imageWrap));
      imageWrap.getChildren().add(imageView);
      imageWrap.applyCss();
      imageWrap.layout();
      updateCoverViewport(imageView, imageWrap);
    } else {
      imageWrap.getChildren().add(buildThumbnail("IMG"));
    }
  }

  private boolean isNoReserve(String reservePrice) {
    return reservePrice == null
        || reservePrice.isBlank()
        || normalize(reservePrice).equals("no reserve")
        || normalize(reservePrice).equals("0 vnd");
  }

  private void renderAuctionDetailPage(AuctionCardData data) {
    activeSellerDetailItem = null;
    renderAuctionDetailPage(data, false);
  }

  private void renderSellerAuctionDetailPage(SellerItemData item) {
    if (item == null || item.auctionId == null || item.auctionId.isBlank()) {
      return;
    }
    activeSellerDetailItem = item;
    if (!Boolean.TRUE.equals(sellerBidHistoryLoaded.get(item.auctionId))) {
      requestSellerBidHistory(item.auctionId);
    }
    renderAuctionDetailPage(toSellerAuctionCard(item), true);
  }

  private void renderAuctionDetailPage(AuctionCardData data, boolean sellerView) {
    if (workspaceBox == null || data == null) {
      return;
    }

    AuctionCardData displayData = sellerView ? data : latestAuctionCard(data);
    long displaySecondsLeft = currentSecondsLeft(displayData);

    stopAuctionDetailCountdown();
    currentSectionKey = sellerView ? "myItems" : "auctions";
    setDepositFloatingButtonVisible(false);
    workspaceBox.getChildren().clear();

    if (surfaceTitleLabel != null) {
      surfaceTitleLabel.setVisible(false);
      surfaceTitleLabel.setManaged(false);
    }
    if (workspaceTitleLabel != null) {
      workspaceTitleLabel.setText("");
      workspaceTitleLabel.setVisible(false);
      workspaceTitleLabel.setManaged(false);
    }
    if (userActionBar != null) {
      userActionBar.getChildren().clear();
      userActionBar.setVisible(true);
      userActionBar.setManaged(true);
      userActionBar.getChildren().add(
          sellerView ? buildSellerAuctionBreadcrumb(displayData) : buildAuctionBreadcrumb(displayData)
      );
    }

    VBox detailShell = new VBox(12);
    detailShell.getStyleClass().add("auction-detail-shell");
    detailShell.setAlignment(Pos.TOP_LEFT);
    detailShell.setFillWidth(true);
    detailShell.setMinWidth(0);
    detailShell.setMaxWidth(Double.MAX_VALUE);

    Label pageTitle = new Label(displayData.title);
    pageTitle.getStyleClass().add("auction-detail-page-title");
    pageTitle.setWrapText(true);
    pageTitle.setMaxWidth(Double.MAX_VALUE);

    HBox detailContent = new HBox(AUCTION_DETAIL_GAP);
    detailContent.getStyleClass().add("auction-detail-content");
    detailContent.setAlignment(Pos.TOP_LEFT);
    detailContent.setMinWidth(0);
    detailContent.setMaxWidth(Double.MAX_VALUE);

    VBox mediaColumn = new VBox(10);
    mediaColumn.getStyleClass().add("auction-detail-media-column");
    mediaColumn.setMinWidth(0);
    mediaColumn.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(mediaColumn, Priority.ALWAYS);

    HBox gallery = new HBox(10);
    gallery.getStyleClass().add("auction-detail-gallery");
    gallery.setAlignment(Pos.TOP_LEFT);
    gallery.setMinWidth(0);
    gallery.setMaxWidth(Double.MAX_VALUE);

    StackPane mainImage = createAuctionImageWrap(displayData.imagePath, AUCTION_DETAIL_IMAGE_HEIGHT);
    mainImage.getStyleClass().add("auction-detail-main-image");
    mainImage.setMinWidth(0);
    mainImage.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(mainImage, Priority.ALWAYS);

    VBox thumbnails = new VBox(8);
    thumbnails.getStyleClass().add("auction-detail-side-thumbnails");
    thumbnails.setAlignment(Pos.TOP_CENTER);
    lockRegionWidth(thumbnails, AUCTION_DETAIL_THUMB_WIDTH);

    List<String> imagePaths = imagePathsFromPayload(displayData.imagePayload);
    if (imagePaths.isEmpty()) {
      imagePaths.add(displayData.imagePath);
    }
    for (String path : imagePaths.subList(0, Math.min(4, imagePaths.size()))) {
      StackPane thumb = createAuctionImageWrap(path, AUCTION_DETAIL_THUMB_HEIGHT);
      thumb.getStyleClass().add("auction-detail-thumb");
      lockRegionWidth(thumb, AUCTION_DETAIL_THUMB_WIDTH);
      thumb.setOnMouseClicked(event ->
          setAuctionImageContent(mainImage, path, AUCTION_DETAIL_IMAGE_HEIGHT));
      thumbnails.getChildren().add(thumb);
    }

    gallery.getChildren().addAll(mainImage, thumbnails);

    Label descriptionLine = new Label(
        fallback(displayData.description, "No description stored for this item."));
    descriptionLine.getStyleClass().add("auction-detail-description-line");
    descriptionLine.setWrapText(true);
    descriptionLine.setMaxWidth(Double.MAX_VALUE);
    descriptionLine.maxWidthProperty().bind(mediaColumn.widthProperty());

    mediaColumn.getChildren().addAll(gallery, descriptionLine);

    VBox sidePanel = new VBox(14);
    sidePanel.getStyleClass().add("auction-detail-side-panel");
    sidePanel.setMinWidth(AUCTION_DETAIL_INFO_WIDTH);
    sidePanel.setPrefWidth(AUCTION_DETAIL_INFO_WIDTH);
    sidePanel.setMaxWidth(AUCTION_DETAIL_INFO_WIDTH);

    Label timeTitle = new Label("Time Left");
    timeTitle.getStyleClass().add("auction-detail-panel-title");

    HBox countdown = buildCountdown(displaySecondsLeft);
    countdown.setMaxWidth(Double.MAX_VALUE);

    HBox metaRow = buildAuctionMetaRow(displayData);

    VBox priceBox = new VBox(5);
    Label currentLabel = new Label("Current Bid");
    currentLabel.getStyleClass().add("auction-market-label");
    Label price = new Label(displayData.price);
    price.getStyleClass().add("auction-detail-price");
    Label bids = new Label(displayData.bids);
    bids.getStyleClass().add("auction-detail-text");
    priceBox.getChildren().addAll(currentLabel, price, bids);

    HBox bidRow = new HBox(10);
    bidRow.getStyleClass().add("auction-detail-bid-row");
    TextField bidInput = new TextField();
    Button placeBidButton = new Button("Place Bid");
    TextField autoBidMaxInput = new TextField();
    TextField autoBidIncrementInput = new TextField();
    Button autoBidRegisterButton = new Button("Enable Auto");
    Button autoBidCancelButton = new Button("Cancel Auto");
    VBox autoBidPanel = null;
    Label bidMessage = new Label();

    if (!sellerView) {
      bidInput.setPromptText("Enter bid amount");
      bidInput.setDisable(!isAuctionBidEnabled(displayData));
      bidInput.getStyleClass().add("auction-detail-bid-input");
      bidInput.setMinWidth(0);
      HBox.setHgrow(bidInput, Priority.ALWAYS);

      placeBidButton.setMnemonicParsing(false);
      placeBidButton.getStyleClass().add("auction-market-bid-btn");
      placeBidButton.setDisable(!isAuctionBidEnabled(displayData));
      lockRegionWidth(placeBidButton, 104);
      placeBidButton.setOnAction(event -> submitManualBid(displayData, bidInput));

      bidRow.getChildren().addAll(bidInput, placeBidButton);
      autoBidPanel = buildAutoBidPanel(
          displayData, autoBidMaxInput, autoBidIncrementInput, autoBidRegisterButton, autoBidCancelButton);
      bidMessage.setText(isAuctionBidEnabled(displayData)
          ? "Minimum increment: " + displayData.minimumIncrement
          : bidDisabledReason(displayData));
    }

    bidMessage.getStyleClass().add("auction-detail-end-note");
    bidMessage.setWrapText(true);

    Label endNote = new Label(displayData.endTime == null || displayData.endTime.isBlank()
        ? "Auction schedule is not available yet."
        : "This auction will end on " + displayData.endTime + ".");
    endNote.getStyleClass().add("auction-detail-end-note");
    endNote.setWrapText(true);

    sidePanel.getChildren().addAll(timeTitle, countdown, metaRow, priceBox);
    if (sellerView) {
      sidePanel.getChildren().add(buildSellerPerformancePanel(displayData));
    } else {
      sidePanel.getChildren().add(bidRow);
      if (autoBidPanel != null) {
        sidePanel.getChildren().add(autoBidPanel);
      }
      sidePanel.getChildren().add(bidMessage);
    }
    sidePanel.getChildren().add(endNote);

    activeAuctionDetailId = displayData.auctionId;
    activeAuctionPriceLabel = price;
    activeAuctionBidCountLabel = bids;
    activeAuctionBidInput = sellerView ? null : bidInput;
    activeAuctionBidButton = sellerView ? null : placeBidButton;
    activeAutoBidMaxInput = sellerView ? null : autoBidMaxInput;
    activeAutoBidIncrementInput = sellerView ? null : autoBidIncrementInput;
    activeAutoBidRegisterButton = sellerView ? null : autoBidRegisterButton;
    activeAutoBidCancelButton = sellerView ? null : autoBidCancelButton;
    activeAuctionBidMessageLabel = sellerView ? null : bidMessage;

    detailContent.getChildren().addAll(mediaColumn, sidePanel);
    detailShell.getChildren().addAll(pageTitle, detailContent);
    if (sellerView) {
      detailShell.getChildren().add(buildSellerBidHistorySection(displayData.auctionId));
    }
    workspaceBox.getChildren().add(detailShell);
    joinAuctionRoom(displayData.auctionId);
  }


  private VBox buildAutoBidPanel(
      AuctionCardData data,
      TextField maxInput,
      TextField incrementInput,
      Button registerButton,
      Button cancelButton) {
    AuctionCardData latestData = latestAuctionCard(data);
    VBox panel = new VBox(8);
    panel.getStyleClass().add("auction-detail-auto-bid-panel");
    panel.setMaxWidth(Double.MAX_VALUE);

    Label title = new Label("Auto Bid");
    title.getStyleClass().add("auction-detail-panel-title");

    Label hint = new Label("Set a max limit and step. The server will raise your bid only when another bidder beats your current price.");
    hint.getStyleClass().add("auction-detail-end-note");
    hint.setWrapText(true);

    HBox inputRow = new HBox(8);
    inputRow.getStyleClass().add("auction-detail-auto-bid-input-row");
    maxInput.setPromptText("Max bid");
    incrementInput.setPromptText("Increment");
    for (TextField input : new TextField[] {maxInput, incrementInput}) {
      input.getStyleClass().add("auction-detail-bid-input");
      input.setMinWidth(0);
      input.setDisable(!isAuctionBidEnabled(latestData));
      HBox.setHgrow(input, Priority.ALWAYS);
    }
    incrementInput.setText(normalize(latestData.minimumIncrement).equals("0 vnd") ? "" : latestData.minimumIncrement);
    inputRow.getChildren().addAll(maxInput, incrementInput);

    HBox actions = new HBox(8);
    actions.setMaxWidth(Double.MAX_VALUE);
    registerButton.setMnemonicParsing(false);
    registerButton.getStyleClass().add("auction-market-bid-btn");
    registerButton.setDisable(!isAuctionBidEnabled(latestData));
    registerButton.setOnAction(event -> submitAutoBid(latestData, maxInput, incrementInput));

    cancelButton.setMnemonicParsing(false);
    cancelButton.getStyleClass().add("auction-detail-secondary-btn");
    cancelButton.setDisable(!isAuctionBidEnabled(latestData));
    cancelButton.setOnAction(event -> cancelAutoBid(latestData));

    HBox.setHgrow(registerButton, Priority.ALWAYS);
    HBox.setHgrow(cancelButton, Priority.ALWAYS);
    registerButton.setMaxWidth(Double.MAX_VALUE);
    cancelButton.setMaxWidth(Double.MAX_VALUE);
    actions.getChildren().addAll(registerButton, cancelButton);

    panel.getChildren().addAll(title, hint, inputRow, actions);
    return panel;
  }

  private AuctionCardData toSellerAuctionCard(SellerItemData item) {
    String price = item.currentPrice == null || item.currentPrice.isBlank()
        || "0 VND".equals(item.currentPrice)
        ? item.startingPrice
        : item.currentPrice;
    String status = item.auctionStatus == null || item.auctionStatus.isBlank()
        ? item.status
        : item.auctionStatus;

    return new AuctionCardData(
        item.auctionId,
        item.itemId,
        item.name,
        item.category,
        item.description,
        price,
        "0 VND",
        "No reserve",
        item.bidCount,
        formatBidCount(item.bidCount),
        formatTimeLeft(item.secondsLeft),
        item.secondsLeft,
        prettyStatus(status),
        item.imagePath,
        item.imagePayload,
        buildSellerItemDetailText(item),
        status,
        item.auctionStartTime,
        item.auctionEndTime,
        currentUsername(),
        item.highestBidder,
        item.attributes,
        "300",
        "60"
    );
  }

  private VBox buildSellerPerformancePanel(AuctionCardData data) {
    VBox panel = new VBox(8);
    panel.getStyleClass().add("seller-performance-panel");

    Label title = new Label("Seller View");
    title.getStyleClass().add("auction-detail-panel-title");

    Label ownerNote = new Label("You own this listing, so bidding is disabled.");
    ownerNote.getStyleClass().add("auction-detail-end-note");
    ownerNote.setWrapText(true);

    SellerItemData item = activeSellerDetailItem;
    String highestBidder = item == null
        ? "No bids yet"
        : fallback(item.highestBidder, "No bids yet");
    VBox highest = sellerMetric("Highest bidder", highestBidder);
    VBox totalBids = sellerMetric("Total bids", data.bids);
    VBox status = sellerMetric("Auction status", prettyStatus(data.status));

    panel.getChildren().addAll(title, ownerNote, highest, totalBids, status);
    return panel;
  }

  private VBox sellerMetric(String labelText, String valueText) {
    VBox metric = new VBox(3);
    metric.getStyleClass().add("seller-performance-metric");
    Label label = new Label(labelText);
    label.getStyleClass().add("auction-detail-meta-key");
    Label value = new Label(fallback(valueText, "Not available"));
    value.getStyleClass().add("auction-detail-meta-value");
    value.setWrapText(true);
    metric.getChildren().addAll(label, value);
    return metric;
  }

  private VBox buildSellerBidHistorySection(String auctionId) {
    VBox section = new VBox(10);
    section.getStyleClass().add("seller-bid-history-card");
    section.setMaxWidth(Double.MAX_VALUE);
    populateSellerBidHistorySection(section, auctionId);
    activeSellerBidHistorySection = section;
    return section;
  }

  private void refreshActiveSellerBidHistorySection(String auctionId) {
    if (activeSellerDetailItem == null
        || activeSellerBidHistorySection == null
        || !auctionId.equals(activeSellerDetailItem.auctionId)) {
      return;
    }
    populateSellerBidHistorySection(activeSellerBidHistorySection, auctionId);
  }

  private void populateSellerBidHistorySection(VBox section, String auctionId) {
    section.getChildren().clear();

    Label title = new Label("Bid History");
    title.getStyleClass().add("auction-detail-panel-title");
    section.getChildren().add(title);

    if (auctionId == null || auctionId.isBlank()) {
      section.getChildren().add(sellerHistoryMessage("This item is not in an auction yet."));
      return;
    }

    Boolean loaded = sellerBidHistoryLoaded.get(auctionId);
    List<BidHistoryData> history = sellerBidHistoryByAuction.getOrDefault(
        auctionId,
        new ArrayList<>()
    );

    if (!Boolean.TRUE.equals(loaded)) {
      section.getChildren().add(sellerHistoryMessage("Loading bid history from database..."));
      return;
    }
    if (history.isEmpty()) {
      section.getChildren().add(sellerHistoryMessage("No bids have been placed yet."));
      return;
    }

    section.getChildren().add(sellerBidHistoryHeader());
    for (BidHistoryData row : history) {
      section.getChildren().add(sellerBidHistoryRow(row));
    }
  }

  private Label sellerHistoryMessage(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("auction-detail-end-note");
    label.setWrapText(true);
    return label;
  }

  private GridPane sellerBidHistoryHeader() {
    GridPane header = sellerBidHistoryGrid("seller-bid-history-header");
    header.add(sellerHistoryHeaderLabel("Time"), 0, 0);
    header.add(sellerHistoryHeaderLabel("Bidder"), 1, 0);
    header.add(sellerHistoryHeaderLabel("Amount"), 2, 0);
    header.add(sellerHistoryHeaderLabel("Status"), 3, 0);
    header.add(sellerHistoryHeaderLabel("Type"), 4, 0);
    return header;
  }

  private GridPane sellerBidHistoryRow(BidHistoryData data) {
    GridPane row = sellerBidHistoryGrid("seller-bid-history-row");
    row.add(sellerHistoryValue(data.bidTime), 0, 0);
    row.add(sellerHistoryValue(data.bidderName), 1, 0);
    row.add(sellerHistoryValue(data.amount), 2, 0);
    row.add(statusBadge(data.status), 3, 0);
    row.add(sellerHistoryValue(data.autoBid ? "Auto" : "Manual"), 4, 0);
    return row;
  }

  private GridPane sellerBidHistoryGrid(String styleClass) {
    GridPane grid = new GridPane();
    grid.getStyleClass().add(styleClass);
    grid.setHgap(10);
    grid.setMaxWidth(Double.MAX_VALUE);
    grid.setMinWidth(0);
    grid.getColumnConstraints().addAll(
        percentColumn(20),
        percentColumn(24),
        percentColumn(22),
        percentColumn(18),
        percentColumn(16)
    );
    return grid;
  }

  private Label sellerHistoryHeaderLabel(String text) {
    Label label = headerLabel(text, HPos.LEFT);
    GridPane.setHalignment(label, HPos.LEFT);
    return label;
  }

  private Label sellerHistoryValue(String text) {
    Label label = new Label(fallback(text, "-"));
    label.getStyleClass().add("auction-detail-text");
    label.setWrapText(true);
    label.setMinWidth(0);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }

  private boolean isAuctionBidEnabled(AuctionCardData data) {
    AuctionCardData latestData = latestAuctionCard(data);
    return latestData != null
        && normalize(latestData.status).equals("running")
        && currentSecondsLeft(latestData) > 0
        && !isOwnAuction(latestData);
  }

  private String bidDisabledReason(AuctionCardData data) {
    AuctionCardData latestData = latestAuctionCard(data);
    if (latestData == null) {
      return "Auction data is not available yet.";
    }
    if (!normalize(latestData.status).equals("running") || currentSecondsLeft(latestData) <= 0) {
      return "This auction is not accepting bids right now.";
    }
    if (isOwnAuction(latestData)) {
      return "You cannot bid on your own auction.";
    }
    if (isCurrentUserWinning(latestData)) {
      return "You are currently leading this auction.";
    }
    return "This auction is not accepting bids right now.";
  }

  private void joinAuctionRoom(String auctionId) {
    requestFreshAuctionState(auctionId);
  }

  private void requestFreshAuctionState(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager != null) {
      networkManager.send("JOIN_AUCTION " + auctionId.trim());
    }
  }

  private void leaveActiveAuctionRoom() {
    if (activeAuctionDetailId == null || activeAuctionDetailId.isBlank()) {
      clearActiveAuctionDetailRefs();
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager != null) {
      networkManager.send("LEAVE_AUCTION " + activeAuctionDetailId);
    }
    clearActiveAuctionDetailRefs();
  }

  private void clearActiveAuctionDetailRefs() {
    activeAuctionDetailId = null;
    activeSellerDetailItem = null;
    activeSellerBidHistorySection = null;
    activeAuctionPriceLabel = null;
    activeAuctionBidCountLabel = null;
    activeAuctionBidInput = null;
    activeAuctionBidButton = null;
    activeAutoBidMaxInput = null;
    activeAutoBidIncrementInput = null;
    activeAutoBidRegisterButton = null;
    activeAutoBidCancelButton = null;
    activeAuctionBidMessageLabel = null;
    activeCountdownDayLabel = null;
    activeCountdownHourLabel = null;
    activeCountdownMinuteLabel = null;
    activeCountdownSecondLabel = null;
    activeAuctionCountdownSeconds = -1L;
  }

  private void submitManualBid(AuctionCardData data, TextField bidInput) {
    AuctionCardData latestData = latestAuctionCard(data);
    if (latestData == null || bidInput == null) {
      return;
    }

    if (!isAuctionBidEnabled(latestData)) {
      showBidFeedback(bidDisabledReason(latestData), true);
      updateActiveBidControls(latestData);
      return;
    }

    String amount = normalizeBidAmount(bidInput.getText());
    if (amount.isBlank()) {
      showBidFeedback("Please enter a valid bid amount.", true);
      return;
    }

    BigDecimal bidValue;
    try {
      bidValue = new BigDecimal(amount);
      if (bidValue.compareTo(BigDecimal.ZERO) <= 0) {
        showBidFeedback("Bid amount must be greater than 0.", true);
        return;
      }
    } catch (NumberFormatException exception) {
      showBidFeedback("Invalid bid amount format.", true);
      return;
    }

    BigDecimal currentBidValue = moneyValue(latestData.price);
    if (bidValue.compareTo(currentBidValue) <= 0) {
      showBidFeedback("Bid must be greater than current price "
          + formatMoney(currentBidValue.toPlainString()) + ".", true);
      return;
    }

    BigDecimal minimumIncrement = moneyValue(latestData.minimumIncrement);
    BigDecimal minimumAcceptedBid = currentBidValue.add(minimumIncrement);
    if (minimumIncrement.compareTo(BigDecimal.ZERO) > 0
        && bidValue.compareTo(minimumAcceptedBid) < 0) {
      showBidFeedback("Bid must be at least " + formatMoney(minimumAcceptedBid.toPlainString()) + ".", true);
      return;
    }

    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      showBidFeedback("Cannot connect to the server.", true);
      return;
    }

    if (activeAuctionBidButton != null) {
      activeAuctionBidButton.setDisable(true);
    }
    showBidFeedback("Sending bid to the server...", false);
    networkManager.send("BID " + latestData.auctionId + " " + amount);
  }

  private void submitAutoBid(AuctionCardData data, TextField maxInput, TextField incrementInput) {
    AuctionCardData latestData = latestAuctionCard(data);
    if (latestData == null || maxInput == null || incrementInput == null) {
      return;
    }

    if (!isAuctionBidEnabled(latestData)) {
      showBidFeedback(bidDisabledReason(latestData), true);
      updateActiveBidControls(latestData);
      return;
    }

    String maxBid = normalizeBidAmount(maxInput.getText());
    String increment = normalizeBidAmount(incrementInput.getText());
    if (maxBid.isBlank() || increment.isBlank()) {
      showBidFeedback("Please enter both max bid and increment for auto bid.", true);
      return;
    }

    BigDecimal maxBidValue;
    BigDecimal incrementValue;
    try {
      maxBidValue = new BigDecimal(maxBid);
      incrementValue = new BigDecimal(increment);
    } catch (NumberFormatException exception) {
      showBidFeedback("Invalid auto-bid amount format.", true);
      return;
    }

    if (maxBidValue.compareTo(BigDecimal.ZERO) <= 0 || incrementValue.compareTo(BigDecimal.ZERO) <= 0) {
      showBidFeedback("Auto-bid max and increment must be greater than 0.", true);
      return;
    }
    if (maxBidValue.compareTo(moneyValue(latestData.price)) <= 0) {
      showBidFeedback("Auto-bid max must be higher than the current price.", true);
      return;
    }
    if (incrementValue.compareTo(moneyValue(latestData.minimumIncrement)) < 0) {
      showBidFeedback("Auto-bid increment must meet the minimum increment.", true);
      return;
    }

    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      showBidFeedback("Cannot connect to the server.", true);
      return;
    }

    setAutoBidControlsDisabled(true);
    showBidFeedback("Saving auto-bid rule to the server...", false);
    networkManager.send("AUTOBID_REGISTER " + latestData.auctionId + " " + maxBid + " " + increment);
  }

  private void cancelAutoBid(AuctionCardData data) {
    AuctionCardData latestData = latestAuctionCard(data);
    if (latestData == null || latestData.auctionId == null || latestData.auctionId.isBlank()) {
      return;
    }
    if (networkManager == null) {
      networkManager = NetworkManager.getInstance();
    }
    if (networkManager == null) {
      showBidFeedback("Cannot connect to the server.", true);
      return;
    }

    setAutoBidControlsDisabled(true);
    showBidFeedback("Canceling auto-bid rule...", false);
    networkManager.send("AUTOBID_CANCEL " + latestData.auctionId);
  }

  private void setAutoBidControlsDisabled(boolean disabled) {
    if (activeAutoBidMaxInput != null) {
      activeAutoBidMaxInput.setDisable(disabled);
    }
    if (activeAutoBidIncrementInput != null) {
      activeAutoBidIncrementInput.setDisable(disabled);
    }
    if (activeAutoBidRegisterButton != null) {
      activeAutoBidRegisterButton.setDisable(disabled);
    }
    if (activeAutoBidCancelButton != null) {
      activeAutoBidCancelButton.setDisable(disabled);
    }
  }

  private BigDecimal parsePositiveMoney(String rawValue) {
    BigDecimal value = moneyValue(rawValue);
    return value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
  }

  private String normalizeBidAmount(String rawValue) {
    if (rawValue == null) {
      return "";
    }
    return rawValue.trim()
        .replace("VND", "")
        .replace("USD", "")
        .replace(",", "")
        .replace(" ", "");
  }

  private BigDecimal moneyValue(String rawValue) {
    String normalized = normalizeBidAmount(rawValue);
    if (normalized.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(normalized);
    } catch (NumberFormatException exception) {
      return BigDecimal.ZERO;
    }
  }

  private void showBidFeedback(String message, boolean error) {
    if (activeAuctionBidMessageLabel != null) {
      activeAuctionBidMessageLabel.setText(message == null ? "" : message);
      activeAuctionBidMessageLabel.setStyle(error
          ? "-fx-text-fill: #a34f4f; -fx-font-size: 11px; -fx-font-weight: bold;"
          : "-fx-text-fill: #738581; -fx-font-size: 11px;");
    }
    if (error) {
      notifUIHandler.showError("Bid failed", message);
    }
  }

  private void handleRealtimeBidMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    if (message.startsWith("AUCTION_SNAPSHOT|")) {
      handleAuctionSnapshot(message.substring("AUCTION_SNAPSHOT|".length()));
      return;
    }
    if (message.startsWith("BID_UPDATE|")) {
      handleBidUpdate(message.substring("BID_UPDATE|".length()));
      return;
    }
    if (message.startsWith("AUCTION_BID_UPDATE|")) {
      handleAuctionBidUpdate(message.substring("AUCTION_BID_UPDATE|".length()));
      return;
    }
    if (message.startsWith("TIME_EXTENDED|")) {
      handleTimeExtended(message.substring("TIME_EXTENDED|".length()));
      return;
    }
    if (message.startsWith("AUCTION_CLOSED_UPDATE|")) {
      handleAuctionClosedUpdate(message.substring("AUCTION_CLOSED_UPDATE|".length()));
      return;
    }
    if (message.startsWith("AUCTION_CLOSED|")) {
      handleAuctionClosed(message.substring("AUCTION_CLOSED|".length()));
      return;
    }
    if (message.startsWith("BID_SUCCESS")) {
      String[] parts = message.trim().split("\\s+", 3);
      String auctionId = parts.length > 1 ? parts[1] : activeAuctionDetailId;
      if (activeAuctionBidInput != null) {
        activeAuctionBidInput.clear();
      }
      if (auctionId != null && !auctionId.isBlank()) {
        requestFreshAuctionState(auctionId);
        updateActiveBidControls(latestAuctionCardById(auctionId));
      }
      showBidFeedback("Your bid has been recorded. Syncing the latest auction price...", false);
      notifUIHandler.showSuccess("Bid accepted", "The server confirmed your bid and is syncing the final price.");
      requestUserBids();
      return;
    }
    if (message.startsWith("AUTOBID_SUCCESS")) {
      handleAutoBidSuccess(message);
      return;
    }
    if (message.startsWith("AUTOBID_FAIL")) {
      handleAutoBidFail(message);
      return;
    }
    if (message.startsWith("BID_FAIL")) {
      handleBidFailMessage(message);
      return;
    }
    if (message.startsWith("FAIL ") && activeAuctionDetailId != null) {
      if (activeAuctionBidButton != null) {
        activeAuctionBidButton.setDisable(false);
      }
      showBidFeedback(readableBidFailure(message.substring("FAIL ".length())), true);
      return;
    }
    if (message.startsWith("JOIN_AUCTION_FAIL")) {
      showBidFeedback(message, true);
    }
  }

  private void handleAutoBidSuccess(String message) {
    String[] parts = message == null ? new String[0] : message.trim().split("\\s+", 3);
    String auctionId = parts.length > 1 ? parts[1] : "";
    String action = parts.length > 2 ? parts[2] : "REGISTERED";

    if (activeAuctionDetailId == null || auctionId.isBlank() || activeAuctionDetailId.equals(auctionId)) {
      setAutoBidControlsDisabled(false);
      if ("REGISTERED".equalsIgnoreCase(action)) {
        if (activeAutoBidMaxInput != null) {
          activeAutoBidMaxInput.clear();
        }
        showBidFeedback("Auto-bid rule saved. The server will bid for you when needed.", false);
        notifUIHandler.showSuccess("Auto bid enabled", "Your max bid rule is now active.");
      } else {
        removeAutoBidRule(auctionId);
        showBidFeedback("Auto-bid rule canceled.", false);
        notifUIHandler.showSuccess("Auto bid canceled", "The server will stop bidding for this auction.");
      }
    }

    if (!auctionId.isBlank()) {
      requestFreshAuctionState(auctionId);
    }
    requestUserAutoBids();
    requestUserBids();
    requestUserAuctions();
  }

  private void handleAutoBidFail(String message) {
    String[] parts = message == null ? new String[0] : message.trim().split("\\s+", 3);
    String auctionId = parts.length > 1 ? parts[1] : "";
    String reason = parts.length > 2 ? parts[2] : "";

    if (activeAuctionDetailId == null
        || auctionId.isBlank()
        || "-1".equals(auctionId)
        || activeAuctionDetailId.equals(auctionId)) {
      setAutoBidControlsDisabled(false);
      showBidFeedback(readableAutoBidFailure(reason), true);
    }
  }

  private void handleBidFailMessage(String message) {
    String[] parts = message == null ? new String[0] : message.trim().split("\\s+", 3);
    String auctionId = parts.length > 1 ? parts[1] : "";
    String reason = parts.length > 2 ? parts[2] : "";

    if (activeAuctionDetailId != null
        && (auctionId.isBlank()
        || "-1".equals(auctionId)
        || activeAuctionDetailId.equals(auctionId))) {
      if (activeAuctionBidButton != null) {
        activeAuctionBidButton.setDisable(false);
      }
      showBidFeedback(readableBidFailure(reason), true);
    }
  }

  private void handleAuctionSnapshot(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 7) {
      return;
    }
    String auctionId = safeField(fields, 0);
    String status = safeField(fields, 1);
    String currentPrice = safeField(fields, 2);
    String leaderName = safeField(fields, 4);
    String endTime = safeField(fields, 5);
    int totalBids = parseIntOrDefault(safeField(fields, 6), 0);
    long secondsLeft = fields.size() >= 8
        ? parseLongOrDefault(safeField(fields, 7), -1L)
        : -1L;
    if (secondsLeft < 0) {
      secondsLeft = secondsUntil(endTime, -1L);
    }

    replaceAuctionCardBid(auctionId, currentPrice, totalBids, secondsLeft, endTime, leaderName, status);

    if (isActiveAuction(auctionId)) {
      updateActiveAuctionPrice(currentPrice, totalBids);
      updateActiveAuctionCountdown(secondsLeft);
      updateActiveBidControls(latestAuctionCardById(auctionId));
    }
  }

  private void handleBidUpdate(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 8) {
      return;
    }
    String auctionId = safeField(fields, 0);
    String amount = safeField(fields, 2);
    int totalBids = parseIntOrDefault(safeField(fields, 3), 0);
    String leaderName = safeField(fields, 4);
    long secondsLeft = parseLongOrDefault(safeField(fields, 5), -1L);
    String endTime = safeField(fields, 6);

    replaceAuctionCardBid(auctionId, amount, totalBids, secondsLeft, endTime, leaderName, "");
    if (myBidsLoaded || "myBids".equals(currentSectionKey)) {
      requestUserBids();
    }
    if (activeSellerDetailItem != null
        && auctionId.equals(activeSellerDetailItem.auctionId)) {
      requestSellerBidHistory(auctionId);
    }

    if (isActiveAuction(auctionId)) {
      updateActiveAuctionPrice(amount, totalBids);
      updateActiveAuctionCountdown(secondsLeft);
      updateActiveBidControls(latestAuctionCardById(auctionId));
      showBidFeedback(isCurrentUserWinning(latestAuctionCardById(auctionId))
          ? "You are currently leading this auction."
          : "Current price updated in real time.", false);
    } else if ("auctions".equals(currentSectionKey) || "dashboard".equals(currentSectionKey)) {
      renderWorkspace(currentSectionKey, activeFilter);
    }
  }

  private void handleAuctionBidUpdate(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 7) {
      return;
    }
    String auctionId = safeField(fields, 0);
    String currentPrice = safeField(fields, 1);
    String leaderName = safeField(fields, 3);
    String endTime = safeField(fields, 4);
    int totalBids = parseIntOrDefault(safeField(fields, 5), 0);
    long secondsLeft = secondsUntil(endTime, -1L);

    replaceAuctionCardBid(auctionId, currentPrice, totalBids, secondsLeft, endTime, leaderName, "");
    if (myBidsLoaded || "myBids".equals(currentSectionKey)) {
      requestUserBids();
    }
    if (activeSellerDetailItem != null
        && auctionId.equals(activeSellerDetailItem.auctionId)) {
      requestSellerBidHistory(auctionId);
    }

    if (isActiveAuction(auctionId)) {
      updateActiveAuctionPrice(currentPrice, totalBids);
      if (secondsLeft >= 0) {
        updateActiveAuctionCountdown(secondsLeft);
      }
      updateActiveBidControls(latestAuctionCardById(auctionId));
      showBidFeedback(isCurrentUserWinning(latestAuctionCardById(auctionId))
          ? "You are currently leading this auction."
          : "Current price updated in real time.", false);
    } else if ("auctions".equals(currentSectionKey) || "dashboard".equals(currentSectionKey)) {
      renderWorkspace(currentSectionKey, activeFilter);
    }
  }

  private void handleTimeExtended(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 4) {
      return;
    }
    String auctionId = safeField(fields, 0);
    long secondsLeft = parseLongOrDefault(safeField(fields, 1), -1L);
    String endTime = safeField(fields, 2);
    int addedSeconds = parseIntOrDefault(safeField(fields, 3), 0);
    replaceAuctionCardBid(auctionId, null, -1, secondsLeft, endTime, "", "");
    if (isActiveAuction(auctionId)) {
      updateActiveAuctionCountdown(secondsLeft);
      updateActiveBidControls(latestAuctionCardById(auctionId));
      showBidFeedback("Auction extended by " + addedSeconds + " seconds.", false);
    }
  }

  private void handleAuctionClosed(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 4) {
      return;
    }
    String auctionId = safeField(fields, 0);
    String finalPrice = safeField(fields, 2);
    String status = safeField(fields, 3);
    replaceAuctionCardStatus(auctionId, finalPrice, status);
    applyClosedAuctionUiState(auctionId, finalPrice, status);
  }

  private void handleAuctionClosedUpdate(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 5) {
      return;
    }
    String auctionId = safeField(fields, 0);
    String status = safeField(fields, 1);
    String finalPrice = safeField(fields, 2);
    String winnerName = safeField(fields, 4);
    replaceAuctionCardBid(auctionId, finalPrice, -1, 0L, "", winnerName, status);
    applyClosedAuctionUiState(auctionId, finalPrice, status);
  }

  private void applyClosedAuctionUiState(String auctionId, String finalPrice, String status) {
    if (isActiveAuction(auctionId)) {
      if (activeAuctionBidInput != null) {
        activeAuctionBidInput.setDisable(true);
      }
      if (activeAuctionBidButton != null) {
        activeAuctionBidButton.setDisable(true);
      }
      setAutoBidControlsDisabled(true);
      updateActiveAuctionPrice(finalPrice, -1);
      updateActiveAuctionCountdown(0);
      showBidFeedback("This auction has closed with status " + status + ".", false);
    }
    requestUserAuctions();
    requestUserBids();
    requestUserAutoBids();
    requestUserTransactions();
  }

  private boolean isActiveAuction(String auctionId) {
    return activeAuctionDetailId != null && activeAuctionDetailId.equals(auctionId);
  }

  private void updateActiveAuctionPrice(String rawAmount, int totalBids) {
    if (activeAuctionPriceLabel != null && rawAmount != null && !rawAmount.isBlank()) {
      activeAuctionPriceLabel.setText(formatMoney(rawAmount));
    }
    if (activeAuctionBidCountLabel != null && totalBids >= 0) {
      activeAuctionBidCountLabel.setText(formatBidCount(totalBids));
    }
  }

  private void replaceAuctionCardBid(String auctionId, String rawAmount, int totalBids,
      long secondsLeft, String endTime, String winner, String status) {
    for (int i = 0; i < liveAuctionCards.size(); i++) {
      AuctionCardData card = liveAuctionCards.get(i);
      if (!card.auctionId.equals(auctionId)) {
        continue;
      }
      long effectiveSecondsLeft = secondsLeft < 0 ? currentSecondsLeft(card) : secondsLeft;
      rememberAuctionClock(auctionId, effectiveSecondsLeft);
      liveAuctionCards.set(i, copyAuctionCard(
          card,
          rawAmount == null || rawAmount.isBlank() ? card.price : formatMoney(rawAmount),
          totalBids < 0 ? card.bidCount : totalBids,
          effectiveSecondsLeft,
          endTime == null || endTime.isBlank()
              ? card.endTime
              : shortTimestamp(endTime.replace('T', ' ')),
          status,
          winner
      ));
      return;
    }
  }

  private void replaceAuctionCardStatus(String auctionId, String rawAmount, String status) {
    for (int i = 0; i < liveAuctionCards.size(); i++) {
      AuctionCardData card = liveAuctionCards.get(i);
      if (!card.auctionId.equals(auctionId)) {
        continue;
      }
      rememberAuctionClock(auctionId, 0L);
      liveAuctionCards.set(i, copyAuctionCard(
          card,
          rawAmount == null || rawAmount.isBlank() ? card.price : formatMoney(rawAmount),
          card.bidCount,
          0,
          card.endTime,
          status,
          card.winner
      ));
      return;
    }
  }

  private AuctionCardData copyAuctionCard(AuctionCardData card, String price, int bidCount,
      long secondsLeft, String endTime, String status, String winner) {
    String normalizedStatus = fallback(status, card.status);
    String badge = normalizedStatus;
    return new AuctionCardData(
        card.auctionId,
        card.itemId,
        card.title,
        card.category,
        card.description,
        fallback(price, card.price),
        card.minimumIncrement,
        card.reservePrice,
        bidCount,
        formatBidCount(bidCount),
        secondsLeft < 0 ? card.endsIn : formatTimeLeft(secondsLeft),
        secondsLeft < 0 ? card.secondsLeft : secondsLeft,
        badge,
        card.imagePath,
        card.imagePayload,
        card.detail,
        normalizedStatus,
        card.startTime,
        fallback(endTime, card.endTime),
        card.seller,
        fallback(winner, card.winner),
        card.attributes,
        card.snipeWindowSeconds,
        card.snipeExtensionSeconds
    );
  }

  private String readableAutoBidFailure(String reason) {
    String normalized = reason == null ? "" : reason.trim().toUpperCase();
    return switch (normalized) {
      case "NOT_LOGGED_IN" -> "Please sign in before enabling auto bid.";
      case "INVALID_FORMAT" -> "Invalid auto-bid command format.";
      case "INVALID_AMOUNT" -> "Auto-bid max and increment must be greater than 0.";
      case "AUCTION_NOT_RUNNING" -> "Auto bid can only be enabled while the auction is running.";
      case "AUCTION_CLOSED" -> "This auction is closed.";
      case "AUCTION_NOT_FOUND" -> "This auction could not be found.";
      case "OWN_AUCTION" -> "You cannot auto bid on your own auction.";
      case "BELOW_CURRENT_PRICE" -> "Auto-bid max must be higher than the current price.";
      case "BELOW_MIN_INCREMENT" -> "Auto-bid increment must meet the minimum increment.";
      case "AUTO_BID_NOT_FOUND" -> "No active auto-bid rule was found for this auction.";
      case "DB_PERSIST_FAILED" -> "The server could not save your auto-bid rule. Please try again.";
      case "SYSTEM_ERROR" -> "The server encountered an error while processing auto bid.";
      default -> normalized.isBlank()
          ? "Auto bid failed."
          : toSentenceCase(normalized.replace('_', ' '));
    };
  }

  private String readableBidFailure(String reason) {
    String normalized = reason == null ? "" : reason.trim();
    return switch (normalized) {
      case "NOT_LOGGED_IN" -> "Please sign in before placing a bid.";
      case "INVALID_FORMAT" -> "Invalid bid format.";
      case "AUCTION_CLOSED" -> "This auction is closed.";
      case "USER_CANNOT_BID_ON_THEIR_OWN_AUCTION" -> "You cannot bid on your own auction.";
      case "BID_AMOUNT_MUST_BE_POSITIVE" -> "Bid amount must be greater than 0.";
      case "AMOUNT_MUST_BE_POSITIVE" -> "Bid amount must be greater than 0.";
      case "AMOUNT_NOT_POSITIVE" -> "Bid amount must be greater than 0.";
      case "OWN_AUCTION" -> "You cannot bid on your own auction.";
      case "BELOW_MIN_INCREMENT" -> "Your bid must meet the minimum increment.";
      case "BELOW_CURRENT_PRICE" -> "Your bid must be higher than the current price.";
      case "AUCTION_NOT_FOUND" -> "This auction could not be found.";
      case "USER_OFFLINE" -> "Your session is not registered as online. Please sign in again.";
      case "USER_NOT_FOUND" -> "Your account could not be found on the server.";
      case "DB_PERSIST_FAILED" -> "The server could not save your bid. Please try again.";
      case "SYSTEM_ERROR" -> "The server encountered an error while processing your bid.";
      default -> normalized.isBlank()
          ? "Bid failed."
          : toSentenceCase(normalized.replace('_', ' '));
    };
  }

  private String toSentenceCase(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String lower = value.toLowerCase();
    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
  }

  private void lockRegionWidth(Region region, double width) {
    region.setMinWidth(width);
    region.setPrefWidth(width);
    region.setMaxWidth(width);
  }

  private HBox buildAuctionBreadcrumb(AuctionCardData data) {
    HBox breadcrumb = new HBox(9);
    breadcrumb.getStyleClass().add("auction-breadcrumb");
    breadcrumb.setAlignment(Pos.CENTER_LEFT);
    breadcrumb.setMaxWidth(Double.MAX_VALUE);

    Label home = new Label("Home");
    home.getStyleClass().add("auction-breadcrumb-muted");

    Label firstSeparator = new Label("/");
    firstSeparator.getStyleClass().add("auction-breadcrumb-separator");

    Button liveAuctions = new Button("Live Auctions");
    liveAuctions.setMnemonicParsing(false);
    liveAuctions.getStyleClass().add("auction-breadcrumb-link");
    liveAuctions.setOnAction(event -> {
      showSection("auctions");
    });

    Label secondSeparator = new Label("/");
    secondSeparator.getStyleClass().add("auction-breadcrumb-separator");

    Label currentTitle = new Label(data.title);
    currentTitle.getStyleClass().add("auction-breadcrumb-current");
    currentTitle.setWrapText(true);
    currentTitle.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(currentTitle, Priority.ALWAYS);

    breadcrumb.getChildren().addAll(
        home,
        firstSeparator,
        liveAuctions,
        secondSeparator,
        currentTitle
    );
    return breadcrumb;
  }

  private HBox buildSellerAuctionBreadcrumb(AuctionCardData data) {
    HBox breadcrumb = new HBox(9);
    breadcrumb.getStyleClass().add("auction-breadcrumb");
    breadcrumb.setAlignment(Pos.CENTER_LEFT);
    breadcrumb.setMaxWidth(Double.MAX_VALUE);

    Label home = new Label("Home");
    home.getStyleClass().add("auction-breadcrumb-muted");

    Label firstSeparator = new Label("/");
    firstSeparator.getStyleClass().add("auction-breadcrumb-separator");

    Button myItems = new Button("My Items");
    myItems.setMnemonicParsing(false);
    myItems.getStyleClass().add("auction-breadcrumb-link");
    myItems.setOnAction(event -> showSection("myItems"));

    Label secondSeparator = new Label("/");
    secondSeparator.getStyleClass().add("auction-breadcrumb-separator");

    Label currentTitle = new Label(data.title);
    currentTitle.getStyleClass().add("auction-breadcrumb-current");
    currentTitle.setWrapText(true);
    currentTitle.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(currentTitle, Priority.ALWAYS);

    breadcrumb.getChildren().addAll(
        home,
        firstSeparator,
        myItems,
        secondSeparator,
        currentTitle
    );
    return breadcrumb;
  }

  private HBox buildAuctionMetaRow(AuctionCardData data) {
    HBox row = new HBox(18);
    row.getStyleClass().add("auction-detail-meta-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setMaxWidth(Double.MAX_VALUE);

    addAuctionMetaCell(row, "Category", data.category);
    if (isArtCategory(data.category)) {
      addAuctionMetaCell(row, "Artist", attributeValue(data.attributes, "artist"));
      addAuctionMetaCell(row, "Year", attributeValue(data.attributes, "year_created", "year"));
    }

    return row;
  }

  private void addAuctionMetaCell(HBox row, String labelText, String valueText) {
    VBox cell = new VBox(5);
    cell.getStyleClass().add("auction-detail-meta-cell");
    Label label = new Label(labelText);
    label.getStyleClass().add("auction-detail-meta-key");
    Label value = new Label(fallback(valueText, "not available"));
    value.getStyleClass().add("auction-detail-meta-value");
    value.setWrapText(true);
    cell.getChildren().addAll(label, value);
    HBox.setHgrow(cell, Priority.ALWAYS);
    row.getChildren().add(cell);
  }

  private boolean isArtCategory(String category) {
    return normalize(category).equals("art");
  }

  private String attributeValue(String attributes, String... keys) {
    if (attributes == null || attributes.isBlank() || keys == null || keys.length == 0) {
      return "";
    }

    Map<String, String> values = new LinkedHashMap<>();
    String normalizedPayload = attributes.replace("\\n", "\n");
    for (String line : normalizedPayload.split("\\R")) {
      if (line == null || line.isBlank()) {
        continue;
      }
      int separator = line.indexOf(':');
      if (separator <= 0) {
        continue;
      }
      String key = normalizeAttributeKey(line.substring(0, separator));
      String value = line.substring(separator + 1).trim();
      if (!key.isBlank() && !value.isBlank()) {
        values.put(key, value);
      }
    }

    for (String key : keys) {
      String value = values.get(normalizeAttributeKey(key));
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String normalizeAttributeKey(String key) {
    return key == null ? "" : key.trim().toLowerCase().replace('-', '_').replace(' ', '_');
  }

  private HBox buildCountdown(long secondsLeft) {
    activeAuctionCountdownSeconds = Math.max(0, secondsLeft);

    HBox box = new HBox(10);
    box.getStyleClass().add("auction-detail-countdown");

    Label dayNumber = countdownNumberLabel();
    Label hourNumber = countdownNumberLabel();
    Label minuteNumber = countdownNumberLabel();
    Label secondNumber = countdownNumberLabel();
    activeCountdownDayLabel = dayNumber;
    activeCountdownHourLabel = hourNumber;
    activeCountdownMinuteLabel = minuteNumber;
    activeCountdownSecondLabel = secondNumber;
    updateCountdownLabels(dayNumber, hourNumber, minuteNumber, secondNumber, activeAuctionCountdownSeconds);

    box.getChildren().addAll(
        countdownUnit(dayNumber, "Day"),
        countdownSeparator(),
        countdownUnit(hourNumber, "Hours"),
        countdownSeparator(),
        countdownUnit(minuteNumber, "Minutes"),
        countdownSeparator(),
        countdownUnit(secondNumber, "Seconds")
    );

    startActiveCountdownTimeline();
    return box;
  }

  private void stopAuctionDetailCountdown() {
    if (auctionDetailCountdownTimeline != null) {
      auctionDetailCountdownTimeline.stop();
      auctionDetailCountdownTimeline = null;
    }
  }

  private void startActiveCountdownTimeline() {
    if (auctionDetailCountdownTimeline != null) {
      auctionDetailCountdownTimeline.stop();
      auctionDetailCountdownTimeline = null;
    }
    if (activeAuctionCountdownSeconds <= 0) {
      updateActiveAuctionCountdown(0);
      return;
    }

    auctionDetailCountdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
      activeAuctionCountdownSeconds = Math.max(0, activeAuctionCountdownSeconds - 1);
      updateActiveAuctionCountdown(activeAuctionCountdownSeconds);
      if (activeAuctionCountdownSeconds <= 0) {
        stopAuctionDetailCountdown();
        updateActiveBidControls(latestAuctionCardById(activeAuctionDetailId));
      }
    }));
    auctionDetailCountdownTimeline.setCycleCount(Timeline.INDEFINITE);
    auctionDetailCountdownTimeline.play();
  }

  private void updateActiveAuctionCountdown(long secondsLeft) {
    if (secondsLeft < 0) {
      return;
    }
    activeAuctionCountdownSeconds = Math.max(0, secondsLeft);
    if (activeCountdownDayLabel != null && activeCountdownHourLabel != null
        && activeCountdownMinuteLabel != null && activeCountdownSecondLabel != null) {
      updateCountdownLabels(
          activeCountdownDayLabel,
          activeCountdownHourLabel,
          activeCountdownMinuteLabel,
          activeCountdownSecondLabel,
          activeAuctionCountdownSeconds
      );
    }
  }

  private void updateCountdownLabels(Label dayNumber, Label hourNumber, Label minuteNumber,
      Label secondNumber, long secondsLeft) {
    long safeSeconds = Math.max(0, secondsLeft);
    long days = safeSeconds / 86400;
    long hours = (safeSeconds % 86400) / 3600;
    long minutes = (safeSeconds % 3600) / 60;
    long seconds = safeSeconds % 60;

    dayNumber.setText(String.format("%02d", days));
    hourNumber.setText(String.format("%02d", hours));
    minuteNumber.setText(String.format("%02d", minutes));
    secondNumber.setText(String.format("%02d", seconds));
  }

  private Label countdownNumberLabel() {
    Label number = new Label("00");
    number.getStyleClass().add("auction-detail-countdown-number");
    return number;
  }

  private VBox countdownUnit(Label number, String labelText) {
    VBox unit = new VBox(2);
    unit.getStyleClass().add("auction-detail-countdown-unit");
    Label label = new Label(labelText);
    label.getStyleClass().add("auction-detail-countdown-label");
    unit.getChildren().addAll(number, label);
    return unit;
  }
  private Label countdownSeparator() {
    Label separator = new Label(":");
    separator.getStyleClass().add("auction-detail-countdown-separator");
    return separator;
  }

  private List<String> imagePathsFromPayload(String payload) {
    List<String> paths = new ArrayList<>();
    if (payload == null || payload.isBlank()) {
      return paths;
    }
    String normalizedPayload = payload.replace("\\n", "\n");
    for (String image : normalizedPayload.split("\\R")) {
      if (image != null && !image.isBlank()) {
        paths.add(image.trim());
      }
    }
    return paths;
  }

  private Image getCachedImage(String imagePath) {
    if (imagePath == null || imagePath.isBlank()) {
      return null;
    }

    if (imageCache.containsKey(imagePath)) {
      return imageCache.get(imagePath);
    }

    Image image = loadImage(imagePath);
    imageCache.put(imagePath, image);
    return image;
  }

  private Image loadImage(String imagePath) {
    try {
      if (imagePath == null || imagePath.isBlank()) {
        return null;
      }
      String normalizedPath = imagePath.trim();
      if (normalizedPath.startsWith("file:")
          || normalizedPath.startsWith("http://")
          || normalizedPath.startsWith("https://")) {
        return new Image(normalizedPath, true);
      }

      File localFile = new File(normalizedPath);
      if (localFile.exists()) {
        return new Image(localFile.toURI().toString(), true);
      }

      if (getClass().getResource(normalizedPath) == null) {
        return null;
      }

      return new Image(getClass().getResource(normalizedPath).toExternalForm(), true);
    } catch (IllegalArgumentException exception) {
      return null;
    }
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

  private VBox emptyCard(String text) {
    VBox card = new VBox(8);
    card.getStyleClass().add("auction-product-card");
    card.setMinWidth(0);
    card.setMaxWidth(Double.MAX_VALUE);
    Label label = new Label(text);
    label.getStyleClass().add("row-meta");
    label.setWrapText(true);
    card.getChildren().add(label);
    return card;
  }

  private HBox buildPagination(int totalPages, int totalItems) {
    HBox pagination = new HBox(7);
    pagination.setAlignment(Pos.CENTER_RIGHT);
    pagination.getStyleClass().add("pagination-bar");

    Label total = new Label(totalItems + " results");
    total.getStyleClass().add("row-meta");
    HBox.setHgrow(total, Priority.ALWAYS);

    Button previous = paginationButton("< Previous", auctionPage <= 1);
    previous.setOnAction(event -> {
      if (auctionPage > 1) {
        auctionPage--;
        renderWorkspace("auctions", activeFilter);
      }
    });

    pagination.getChildren().addAll(total, previous);

    for (int page = 1; page <= totalPages; page++) {
      final int targetPage = page;
      Button button = paginationButton(String.valueOf(page), false);
      button.getStyleClass().add(page == auctionPage ? "pagination-active" : "pagination-btn");
      button.setOnAction(event -> {
        auctionPage = targetPage;
        renderWorkspace("auctions", activeFilter);
      });
      pagination.getChildren().add(button);
    }

    Button next = paginationButton("Next >", auctionPage >= totalPages);
    next.setOnAction(event -> {
      if (auctionPage < totalPages) {
        auctionPage++;
        renderWorkspace("auctions", activeFilter);
      }
    });

    pagination.getChildren().add(next);
    return pagination;
  }

  private Button paginationButton(String text, boolean disabled) {
    Button button = new Button(text);
    button.setMnemonicParsing(false);
    button.getStyleClass().add("pagination-btn");
    button.setDisable(disabled);
    return button;
  }

  private GridPane createThreeColumnGrid(String styleClass) {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(14);
    grid.getStyleClass().add(styleClass);
    grid.setMaxWidth(Double.MAX_VALUE);

    for (int index = 0; index < 3; index++) {
      ColumnConstraints column = new ColumnConstraints();
      column.setPercentWidth(33.3333);
      column.setHgrow(Priority.ALWAYS);
      grid.getColumnConstraints().add(column);
    }

    return grid;
  }

  private void addGridCell(GridPane grid, Node node, int index) {
    grid.add(node, index % 3, index / 3);
    GridPane.setHgrow(node, Priority.ALWAYS);
    GridPane.setFillWidth(node, true);

    if (node instanceof Region region) {
      region.setMaxWidth(Double.MAX_VALUE);
    }
  }

  private boolean isOwnAuction(AuctionCardData card) {
    if (card == null || card.seller == null || card.seller.isBlank()) {
      return false;
    }
    String username = currentUsername();
    return !username.isBlank() && normalize(username).equals(normalize(card.seller));
  }

  private String currentUsername() {
    User currentUser = SessionManager.getCurrentUser();
    return currentUser == null ? "" : fallback(currentUser.getUsername(), "");
  }

  private AuctionCardData latestAuctionCard(AuctionCardData data) {
    if (data == null) {
      return null;
    }
    AuctionCardData latest = latestAuctionCardById(data.auctionId);
    return latest == null ? data : latest;
  }

  private AuctionCardData latestAuctionCardById(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return null;
    }
    for (AuctionCardData card : liveAuctionCards) {
      if (auctionId.equals(card.auctionId)) {
        return card;
      }
    }
    return null;
  }

  private boolean isCurrentUserWinning(AuctionCardData data) {
    String current = normalize(currentUsername());
    String winner = normalize(data == null ? "" : data.winner);
    return !current.isBlank() && !winner.isBlank() && current.equals(winner);
  }

  private void updateActiveBidControls(AuctionCardData data) {
    boolean disabled = !isAuctionBidEnabled(data);
    if (activeAuctionBidInput != null) {
      activeAuctionBidInput.setDisable(disabled);
    }
    if (activeAuctionBidButton != null) {
      activeAuctionBidButton.setDisable(disabled);
    }
    if (activeAutoBidMaxInput != null) {
      activeAutoBidMaxInput.setDisable(disabled);
    }
    if (activeAutoBidIncrementInput != null) {
      activeAutoBidIncrementInput.setDisable(disabled);
    }
    if (activeAutoBidRegisterButton != null) {
      activeAutoBidRegisterButton.setDisable(disabled);
    }
    if (activeAutoBidCancelButton != null) {
      activeAutoBidCancelButton.setDisable(disabled);
    }
    if (disabled && activeAuctionBidMessageLabel != null && data != null) {
      activeAuctionBidMessageLabel.setText(bidDisabledReason(data));
    }
  }

  private void removeAutoBidRule(String auctionId) {
    if (auctionId == null || auctionId.isBlank()) {
      return;
    }
    autoBids.removeIf(rule -> auctionId.equals(rule.auctionId));
    incomingAutoBids.removeIf(rule -> auctionId.equals(rule.auctionId));
    if ("autoBids".equals(currentSectionKey)) {
      renderWorkspace(currentSectionKey, activeFilter);
      applyAutoBidStatsIfVisible();
    }
  }

  private List<AuctionCardData> filterAuctionCards(String filter) {
    List<AuctionCardData> filtered = new ArrayList<>();
    String normalizedFilter = normalize(filter);

    for (AuctionCardData card : liveAuctionCards) {
      if (isOwnAuction(card)) {
        continue;
      }
      String haystack = normalize(String.join(" ",
          card.title,
          card.category,
          card.badge,
          card.status,
          card.seller,
          card.detail,
          card.attributes
      ));

      if (isAllLikeFilter(filter)
          || haystack.contains(normalizedFilter)
          || normalize(card.category).equals(normalizedFilter)) {
        filtered.add(card);
      }
    }

    return filtered;
  }

  private List<CategoryData> buildLiveCategories() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (AuctionCardData card : liveAuctionCards) {
      if (isOwnAuction(card)) {
        continue;
      }
      String category = fallback(card.category, "Uncategorized");
      counts.put(category, counts.getOrDefault(category, 0) + 1);
    }

    List<CategoryData> result = new ArrayList<>();
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      String category = entry.getKey();
      result.add(category(
          category,
          "Real auction rows from database category " + category,
          entry.getValue() + " live",
          initialsFor(category)
      ));
    }
    return result;
  }

  private void setWorkspaceTitle(String title) {
    if (workspaceTitleLabel != null) {
      workspaceTitleLabel.setText(title);
    }
  }

  private void renderChips(String selectedFilter, String... labels) {
    if (userActionBar == null) {
      return;
    }

    userActionBar.getChildren().clear();

    for (String labelText : labels) {
      Button chip = new Button(labelText);
      chip.setMnemonicParsing(false);

      if (labelText.equalsIgnoreCase(selectedFilter)) {
        chip.getStyleClass().add("filter-chip-active");
      } else {
        chip.getStyleClass().add("filter-chip");
      }

      chip.setOnAction(event -> applyFilter(labelText));
      userActionBar.getChildren().add(chip);
    }
  }

  private void addHeader(String main, String first, String second) {
    GridPane header = createTableGrid("data-header");

    Label mainHeader = headerLabel(main, HPos.LEFT);
    Label firstHeader = headerLabel(first, HPos.CENTER);
    Label secondHeader = headerLabel(second, HPos.CENTER);
    Label statusHeader = headerLabel("Status", HPos.CENTER);
    Label actionHeader = headerLabel("Action", HPos.CENTER);

    header.add(mainHeader, 0, 0);
    header.add(firstHeader, 1, 0);
    header.add(secondHeader, 2, 0);
    header.add(statusHeader, 3, 0);
    header.add(actionHeader, 4, 0);

    workspaceBox.getChildren().add(header);
  }

  private Label headerLabel(String text, HPos alignment) {
    Label label = new Label(text);
    label.getStyleClass().add("table-header-label");
    label.setMaxWidth(Double.MAX_VALUE);
    label.setMinWidth(0);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    if (alignment == HPos.CENTER) {
      label.setAlignment(Pos.CENTER);
    } else if (alignment == HPos.RIGHT) {
      label.setAlignment(Pos.CENTER_RIGHT);
    } else {
      label.setAlignment(Pos.CENTER_LEFT);
    }
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
    ColumnConstraints statusColumn = percentColumn(14);
    ColumnConstraints actionColumn = percentColumn(16);

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

  private UserRow row(
      String title,
      String meta,
      String firstValue,
      String secondValue,
      String status,
      String detail,
      String thumbnail,
      String... actions
  ) {
    return new UserRow(title, meta, firstValue, secondValue, status, detail, thumbnail, actions);
  }

  private void addFilteredRows(List<UserRow> rows, String filter) {
    int count = 0;

    for (UserRow row : rows) {
      if (matchesFilter(row, filter)) {
        addRow(row);
        count++;
      }
    }

    if (count == 0) {
      addEmptyRow(filter);
    }
  }

  private boolean matchesFilter(UserRow row, String filter) {
    if (filter == null || isAllLikeFilter(filter)) {
      return true;
    }

    String normalizedFilter = normalize(filter);
    String haystack = normalize(String.join(" ",
        row.title,
        row.meta,
        row.firstValue,
        row.secondValue,
        row.status,
        row.detail,
        String.join(" ", row.actions)
    ));


    return haystack.contains(normalizedFilter);
  }

  private boolean isAllLikeFilter(String filter) {
    String normalized = normalize(filter);

    return normalized.equals("all")
        || normalized.equals("overview");
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
    workspaceBox.getChildren().add(row);
  }

  private void addRow(UserRow data) {
    GridPane row = createTableGrid("data-row");
    row.setOnMouseClicked(event -> showTemporaryDetail(data.title, data.detail));

    HBox mainCell = new HBox(9);
    mainCell.setAlignment(Pos.CENTER_LEFT);
    mainCell.setMinWidth(0);
    mainCell.setMaxWidth(Double.MAX_VALUE);
    mainCell.getStyleClass().add("row-main-cell");
    GridPane.setHgrow(mainCell, Priority.ALWAYS);

    StackPane thumbnail = buildThumbnail(data.thumbnail);

    VBox textCell = new VBox(2);
    textCell.setMinWidth(0);
    textCell.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(textCell, Priority.ALWAYS);

    Button link = new Button(data.title);
    link.setMnemonicParsing(false);
    link.getStyleClass().add("row-link");
    link.setMinWidth(0);
    link.setMaxWidth(Double.MAX_VALUE);
    link.setTextOverrun(OverrunStyle.ELLIPSIS);
    link.setOnAction(event -> handleRowAction(resolvePrimaryAction(data), data));

    Label meta = new Label(data.meta);
    meta.getStyleClass().add("row-meta");
    meta.setWrapText(false);
    meta.setMinWidth(0);
    meta.setMaxWidth(Double.MAX_VALUE);
    meta.setTextOverrun(OverrunStyle.ELLIPSIS);

    textCell.getChildren().addAll(link, meta);
    mainCell.getChildren().addAll(thumbnail, textCell);

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

    workspaceBox.getChildren().add(row);
  }

  private String resolvePrimaryAction(UserRow data) {
    return data.actions.length > 0 ? data.actions[0] : "View";
  }

  private GridPane rowActions(UserRow data) {
    GridPane actions = new GridPane();
    actions.setHgap(USER_ACTION_GAP);
    actions.setAlignment(Pos.CENTER);
    actions.setMinWidth(0);
    actions.setMaxWidth(Double.MAX_VALUE);
    actions.getStyleClass().add("user-row-actions");
    actions.getColumnConstraints().addAll(
        fixedColumn(USER_ACTION_PRIMARY_WIDTH),
        fixedColumn(USER_ACTION_MORE_WIDTH)
    );

    String primaryAction = resolvePrimaryAction(data);
    Button primary = new Button(primaryAction);
    primary.setMnemonicParsing(false);
    primary.getStyleClass().addAll("mini-action-btn", "user-primary-action-btn");
    primary.setMinWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setPrefWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setMaxWidth(USER_ACTION_PRIMARY_WIDTH);
    primary.setTextOverrun(OverrunStyle.ELLIPSIS);
    primary.setOnAction(event -> handleRowAction(primaryAction, data));

    actions.add(primary, 0, 0);
    GridPane.setHalignment(primary, HPos.CENTER);

    if (data.actions.length > 1) {
      MenuButton more = new MenuButton("...");
      more.setMnemonicParsing(false);
      more.getStyleClass().add("more-action-btn");
      more.setMinWidth(USER_ACTION_MORE_WIDTH);
      more.setPrefWidth(USER_ACTION_MORE_WIDTH);
      more.setMaxWidth(USER_ACTION_MORE_WIDTH);

      for (int i = 1; i < data.actions.length; i++) {
        String action = data.actions[i];
        MenuItem item = new MenuItem(action);
        item.setOnAction(event -> handleRowAction(action, data));
        more.getItems().add(item);
      }

      actions.add(more, 1, 0);
      GridPane.setHalignment(more, HPos.CENTER);
    } else {
      Region placeholder = new Region();
      placeholder.setMinWidth(USER_ACTION_MORE_WIDTH);
      placeholder.setPrefWidth(USER_ACTION_MORE_WIDTH);
      placeholder.setMaxWidth(USER_ACTION_MORE_WIDTH);
      placeholder.setOpacity(0);
      actions.add(placeholder, 1, 0);
    }

    return actions;
  }

  private void handleRowAction(String action, UserRow data) {
    showTemporaryDetail(action + " - " + data.title, data.detail);
  }

  private StackPane buildThumbnail(String text) {
    StackPane thumbnail = new StackPane();
    thumbnail.setMinSize(54, 46);
    thumbnail.setPrefSize(54, 46);
    thumbnail.setMaxSize(54, 46);
    thumbnail.setStyle("-fx-background-color: linear-gradient(to bottom right, #d1b15d, " +
        "#8fb1a4); -fx-background-radius: 14; -fx-border-color: rgba(39, 75, 69, 0.16); " +
        "-fx-border-radius: 14;");

    Label label = new Label(text == null || text.isBlank() ? "IT" : text);
    label.setStyle("-fx-text-fill: #17352c; -fx-font-size: 12px; -fx-font-weight: bold;");

    thumbnail.getChildren().add(label);
    return thumbnail;
  }

  private Label rowMetric(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("row-metric");
    label.setMinWidth(0);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setWrapText(false);
    return label;
  }

  private Label statusBadge(String status) {
    Label label = new Label(status);
    label.getStyleClass().add("status-badge");
    label.setMinWidth(0);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.getStyleClass().add(statusStyle(status));
    return label;
  }

  private String statusStyle(String status) {
    String normalized = normalize(status);

    if (normalized.contains("winning")
        || normalized.contains("running")
        || normalized.contains("active")
        || normalized.contains("won")
        || normalized.contains("sold")
        || normalized.contains("completed")
        || normalized.contains("ready")) {
      return "status-good";
    }

    if (normalized.contains("outbid")
        || normalized.contains("pending")
        || normalized.contains("to ship")
        || normalized.contains("no reserve")) {
      return "status-warn";
    }

    if (normalized.contains("lost")
        || normalized.contains("failed")
        || normalized.contains("unsold")) {
      return "status-danger";
    }

    return "status-neutral";
  }

  private void hidePageDescriptions() {
    if (headerSubtitleLabel != null) {
      headerSubtitleLabel.setText("");
      headerSubtitleLabel.setVisible(false);
      headerSubtitleLabel.setManaged(false);
    }

    if (surfaceDescriptionLabel != null) {
      surfaceDescriptionLabel.setText("");
      surfaceDescriptionLabel.setVisible(false);
      surfaceDescriptionLabel.setManaged(false);
    }
  }


  /**
   * Render form Create Listing ngay trong workspace hiện tại của user dashboard.
   *
   * <p>Cách này giữ nguyên sidebar và bám đúng flow project: seller tạo item,
   * admin duyệt item, sau đó admin tạo phiên auction từ item đã được duyệt.</p>
   */
  private void renderCreateItemForm() {
    renderCreateItemForm(null);
  }

  private void renderCreateItemForm(SellerItemData editItem) {
    if (workspaceBox == null) {
      return;
    }

    setWorkspaceTitle("");
    if (workspaceTitleLabel != null) {
      workspaceTitleLabel.setVisible(false);
      workspaceTitleLabel.setManaged(false);
    }
    if (surfaceTitleLabel != null) {
      surfaceTitleLabel.setVisible(false);
      surfaceTitleLabel.setManaged(false);
    }

    workspaceBox.getChildren().clear();
    setCreateListingFloatingButtonVisible(false);
    setDepositFloatingButtonVisible(false);
    if (userActionBar != null) {
      userActionBar.getChildren().clear();
      userActionBar.setVisible(false);
      userActionBar.setManaged(false);
    }
    pendingCreateItemUploads.clear();
    pendingCreateItemPreviewIndex = 0;
    if (editItem != null) {
      for (String imagePath : imagePathsFromPayload(editItem.imagePayload)) {
        pendingCreateItemUploads.add(new CreateItemUpload(
            imagePath,
            imagePath,
            fileNameFromPath(imagePath),
            0
        ));
      }
    }

    VBox formShell = new VBox(16);
    formShell.getStyleClass().add("create-listing-shell");
    formShell.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
    formShell.setMaxWidth(Double.MAX_VALUE);

    Label formTitle = new Label(editItem == null ? "Create Listing" : "Edit Draft Item");
    formTitle.getStyleClass().add("create-listing-title");
    formTitle.setStyle("-fx-text-fill: #1f3e37; -fx-font-size: 24px; -fx-font-weight: bold;");

    Label formNote = new Label(editItem == null
        ? "Submit an item for admin review. Approved items become available for auction creation."
        : "Only DRAFT items are editable here. Save keeps it as DRAFT; "
            + "Submit sends it to admin review.");
    formNote.getStyleClass().add("create-listing-note");
    formNote.setStyle("-fx-text-fill: #a7a7a7; -fx-font-size: 11px;");
    formNote.setWrapText(true);

    GridPane topRow = new GridPane();
    topRow.setHgap(18);
    topRow.setAlignment(Pos.TOP_CENTER);
    topRow.setMaxWidth(Double.MAX_VALUE);

    ColumnConstraints uploadColumn = new ColumnConstraints();
    uploadColumn.setPercentWidth(52);
    uploadColumn.setHgrow(Priority.ALWAYS);
    uploadColumn.setFillWidth(true);

    ColumnConstraints previewColumn = new ColumnConstraints();
    previewColumn.setPercentWidth(48);
    previewColumn.setHgrow(Priority.ALWAYS);
    previewColumn.setFillWidth(true);
    topRow.getColumnConstraints().addAll(uploadColumn, previewColumn);

    VBox uploadPanel = new VBox(12);
    uploadPanel.getStyleClass().add("create-listing-card");
    uploadPanel.setStyle(CREATE_CARD_STYLE);
    uploadPanel.setMinWidth(0);
    uploadPanel.setPrefWidth(CREATE_UPLOAD_CARD_MAX_WIDTH);
    uploadPanel.setMaxWidth(CREATE_UPLOAD_CARD_MAX_WIDTH);

    Label uploadTitle = new Label("Upload File");
    uploadTitle.getStyleClass().add("create-section-title");
    uploadTitle.setStyle(CREATE_SECTION_TITLE_STYLE);

    StackPane uploadZone = new StackPane();
    uploadZone.getStyleClass().add("create-upload-zone");
    uploadZone.setStyle("-fx-background-color: linear-gradient(to bottom right, #1b1b1b, #222222); "
        + "-fx-background-radius: 18; -fx-border-color: #3a3a3a; "
        + "-fx-border-style: segments(8, 8); -fx-border-radius: 18; -fx-padding: 24 34 24 34;"
        + "-fx-cursor: hand;");
    uploadZone.setMinHeight(CREATE_UPLOAD_ZONE_HEIGHT);
    uploadZone.setPrefHeight(CREATE_UPLOAD_ZONE_HEIGHT);
    uploadZone.setMaxHeight(CREATE_UPLOAD_ZONE_HEIGHT);
    uploadZone.setMaxWidth(Double.MAX_VALUE);

    VBox uploadContent = new VBox(12);
    uploadContent.setAlignment(Pos.CENTER);

    Label uploadIcon = new Label("↑");
    uploadIcon.getStyleClass().add("create-upload-icon");
    uploadIcon.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 12; "
        + "-fx-text-fill: #f7f3e9; -fx-font-size: 18px; -fx-font-weight: bold; "
        + "-fx-padding: 7 12 7 12;");

    Label uploadHint = new Label("Drop your files here or browse");
    uploadHint.getStyleClass().add("create-upload-title");
    uploadHint.setStyle("-fx-text-fill: #f7f3e9; -fx-font-size: 12px; -fx-font-weight: bold;");

    Label selectedFileLabel = new Label("No file selected");
    selectedFileLabel.getStyleClass().add("create-muted-text");
    selectedFileLabel.setStyle(CREATE_MUTED_TEXT_STYLE);

    Button browseButton = new Button("Browse File");
    browseButton.setMnemonicParsing(false);
    browseButton.getStyleClass().add("create-dark-btn");
    browseButton.setStyle(CREATE_DARK_BUTTON_STYLE);

    Label uploadLimit = new Label(
        "PNG, JPG, JPEG, WEBP · up to " + MAX_CREATE_ITEM_IMAGES + " images");
    uploadLimit.getStyleClass().add("create-muted-text");
    uploadLimit.setStyle(CREATE_MUTED_TEXT_STYLE);

    uploadContent.getChildren().addAll(
        uploadIcon, uploadHint, browseButton, uploadLimit, selectedFileLabel);
    uploadZone.getChildren().add(uploadContent);

    VBox fileListBox = new VBox(8);
    fileListBox.getStyleClass().add("create-file-list");
    fileListBox.setMaxWidth(Double.MAX_VALUE);

    ScrollPane fileListScroll = new ScrollPane(fileListBox);
    fileListScroll.getStyleClass().add("create-file-list-scroll");
    fileListScroll.setFitToWidth(true);
    fileListScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    fileListScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    fileListScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; "
        + "-fx-padding: 0;");
    fileListScroll.setPrefHeight(0);
    fileListScroll.setMinHeight(0);
    fileListScroll.setMaxHeight(0);
    fileListScroll.setMaxWidth(Double.MAX_VALUE);
    fileListScroll.setVisible(false);
    fileListScroll.setManaged(false);

    uploadPanel.getChildren().addAll(uploadTitle, uploadZone, fileListScroll);

    ImageView previewImage = new ImageView();
    previewImage.getStyleClass().add("create-preview-image");
    previewImage.setPreserveRatio(false);
    previewImage.setSmooth(true);
    previewImage.setCache(true);

    Label previewPlaceholder = new Label("No image selected");
    previewPlaceholder.getStyleClass().add("create-muted-text");
    previewPlaceholder.setStyle(CREATE_MUTED_TEXT_STYLE);

    VBox previewPanel = new VBox(12);
    previewPanel.getStyleClass().add("create-listing-card");
    previewPanel.setStyle(CREATE_CARD_STYLE);
    previewPanel.setMinWidth(0);
    previewPanel.setPrefWidth(CREATE_PREVIEW_CARD_MAX_WIDTH);
    previewPanel.setMaxWidth(CREATE_PREVIEW_CARD_MAX_WIDTH);

    Label previewTitle = new Label("Preview File");
    previewTitle.getStyleClass().add("create-section-title");
    previewTitle.setStyle(CREATE_SECTION_TITLE_STYLE);

    StackPane previewImageWrap = new StackPane();
    previewImageWrap.getStyleClass().add("create-preview-image-wrap");
    previewImageWrap.setStyle("-fx-background-color: #111111; -fx-background-radius: 18; "
        + "-fx-border-color: #303030; -fx-border-radius: 18; -fx-padding: 0;");
    previewImageWrap.setMinWidth(0);
    previewImageWrap.setMaxWidth(Double.MAX_VALUE);
    previewImageWrap.setMinHeight(CREATE_PREVIEW_IMAGE_HEIGHT);
    previewImageWrap.setPrefHeight(CREATE_PREVIEW_IMAGE_HEIGHT);
    previewImageWrap.setMaxHeight(CREATE_PREVIEW_IMAGE_HEIGHT);
    previewImage.fitWidthProperty().bind(previewImageWrap.widthProperty());
    previewImage.fitHeightProperty().bind(previewImageWrap.heightProperty());
    previewImage.fitWidthProperty().addListener((observable, oldValue, newValue) ->
        applyCoverImageViewport(previewImage));
    previewImage.fitHeightProperty().addListener((observable, oldValue, newValue) ->
        applyCoverImageViewport(previewImage));
    Rectangle previewClip = new Rectangle();
    previewClip.widthProperty().bind(previewImageWrap.widthProperty());
    previewClip.heightProperty().bind(previewImageWrap.heightProperty());
    previewClip.setArcWidth(28);
    previewClip.setArcHeight(28);
    previewImageWrap.setClip(previewClip);
    previewImageWrap.getChildren().addAll(previewPlaceholder, previewImage);

    Button previousImageButton = new Button("‹");
    previousImageButton.setMnemonicParsing(false);
    previousImageButton.getStyleClass().add("create-secondary-btn");
    previousImageButton.setStyle(CREATE_SECONDARY_BUTTON_STYLE);

    Label imageCounterLabel = new Label("0/" + MAX_CREATE_ITEM_IMAGES);
    imageCounterLabel.getStyleClass().add("create-muted-text");
    imageCounterLabel.setStyle(CREATE_MUTED_TEXT_STYLE);

    Button nextImageButton = new Button("›");
    nextImageButton.setMnemonicParsing(false);
    nextImageButton.getStyleClass().add("create-secondary-btn");
    nextImageButton.setStyle(CREATE_SECONDARY_BUTTON_STYLE);

    HBox imageControls = new HBox(8);
    imageControls.setAlignment(Pos.CENTER);
    imageControls.getChildren().addAll(previousImageButton, imageCounterLabel, nextImageButton);

    Label previewName = new Label("Untitled listing");
    previewName.getStyleClass().add("create-preview-title");
    previewName.setStyle("-fx-text-fill: #f7f3e9; -fx-font-size: 17px; -fx-font-weight: bold;");
    previewName.setWrapText(true);

    Label previewPrice = new Label("Starting price: 0 VND");
    previewPrice.getStyleClass().add("create-muted-text");
    previewPrice.setStyle(CREATE_MUTED_TEXT_STYLE);


    previewPanel.getChildren().addAll(
        previewTitle,
        previewImageWrap,
        imageControls,
        previewName,
        previewPrice
    );
    topRow.add(uploadPanel, 0, 0);
    topRow.add(previewPanel, 1, 0);
    GridPane.setFillWidth(uploadPanel, true);
    GridPane.setFillWidth(previewPanel, true);
    GridPane.setHgrow(uploadPanel, Priority.ALWAYS);
    GridPane.setHgrow(previewPanel, Priority.ALWAYS);
    GridPane.setHalignment(uploadPanel, HPos.RIGHT);
    GridPane.setHalignment(previewPanel, HPos.LEFT);

    VBox detailsPanel = new VBox(12);
    detailsPanel.getStyleClass().add("create-listing-card");
    detailsPanel.setStyle(CREATE_CARD_STYLE);

    Label detailsTitle = new Label("Main Details");
    detailsTitle.getStyleClass().add("create-section-title");
    detailsTitle.setStyle(CREATE_SECTION_TITLE_STYLE);

    ComboBox<String> categoryBox = new ComboBox<>();
    categoryBox.getStyleClass().add("create-combo-box");
    categoryBox.setStyle(CREATE_COMBO_STYLE);
    categoryBox.setMinWidth(0);
    categoryBox.setMaxWidth(Double.MAX_VALUE);
    categoryBox.setMinHeight(50);
    categoryBox.setPrefHeight(50);
    categoryBox.setMaxHeight(50);
    categoryBox.setVisibleRowCount(3);
    categoryBox.getItems().setAll("ART", "VEHICLE", "ELECTRONIC");
    categoryBox.getSelectionModel().select("ART");

    TextField titleField = createFormField("Item title");
    TextField priceField = createFormField("Starting price in VND, e.g. 2500000");
    TextField sizeField = createFormField("Size / condition");
    TextField artistField = createFormField("Artist name");
    TextField yearCreatedField = createFormField("Year created, e.g. 1889");

    ComboBox<String> currencyBox = new ComboBox<>();
    currencyBox.getStyleClass().add("create-combo-box");
    currencyBox.setStyle(CREATE_COMBO_STYLE);
    currencyBox.setMinWidth(0);
    currencyBox.setMaxWidth(Double.MAX_VALUE);
    currencyBox.setMinHeight(50);
    currencyBox.setPrefHeight(50);
    currencyBox.setMaxHeight(50);
    currencyBox.setVisibleRowCount(1);
    currencyBox.getItems().setAll("VND");
    currencyBox.getSelectionModel().select("VND");
    currencyBox.setMouseTransparent(true);
    currencyBox.setFocusTraversable(false);

    TextArea descriptionArea = new TextArea();
    descriptionArea.setPromptText("Tell buyers about condition, provenance, and key item notes...");
    descriptionArea.setWrapText(true);
    descriptionArea.setPrefRowCount(4);
    descriptionArea.getStyleClass().add("create-text-area");
    descriptionArea.setStyle(CREATE_TEXT_AREA_STYLE);

    GridPane primaryFields = new GridPane();
    primaryFields.setHgap(18);
    primaryFields.setMaxWidth(Double.MAX_VALUE);
    for (int column = 0; column < 3; column++) {
      ColumnConstraints constraints = new ColumnConstraints();
      constraints.setPercentWidth(100.0 / 3.0);
      constraints.setHgrow(Priority.ALWAYS);
      constraints.setFillWidth(true);
      primaryFields.getColumnConstraints().add(constraints);
    }
    VBox categoryFieldBox = fieldBox("Category", categoryBox);
    VBox titleFieldBox = fieldBox("Title", titleField);
    VBox priceFieldBox = fieldBox("Price", priceField);
    primaryFields.add(categoryFieldBox, 0, 0);
    primaryFields.add(titleFieldBox, 1, 0);
    primaryFields.add(priceFieldBox, 2, 0);
    GridPane.setHgrow(categoryFieldBox, Priority.ALWAYS);
    GridPane.setHgrow(titleFieldBox, Priority.ALWAYS);
    GridPane.setHgrow(priceFieldBox, Priority.ALWAYS);

    GridPane artFields = new GridPane();
    artFields.setHgap(18);
    artFields.setMaxWidth(Double.MAX_VALUE);
    for (int column = 0; column < 2; column++) {
      ColumnConstraints constraints = new ColumnConstraints();
      constraints.setPercentWidth(50);
      constraints.setHgrow(Priority.ALWAYS);
      constraints.setFillWidth(true);
      artFields.getColumnConstraints().add(constraints);
    }
    VBox artistFieldBox = fieldBox("Artist", artistField);
    VBox yearCreatedFieldBox = fieldBox("Year Created", yearCreatedField);
    artFields.add(artistFieldBox, 0, 0);
    artFields.add(yearCreatedFieldBox, 1, 0);
    GridPane.setHgrow(artistFieldBox, Priority.ALWAYS);
    GridPane.setHgrow(yearCreatedFieldBox, Priority.ALWAYS);

    GridPane secondaryFields = new GridPane();
    secondaryFields.setHgap(18);
    secondaryFields.setMaxWidth(Double.MAX_VALUE);
    ColumnConstraints secondaryConstraints = new ColumnConstraints();
    secondaryConstraints.setPercentWidth(100);
    secondaryConstraints.setHgrow(Priority.ALWAYS);
    secondaryConstraints.setFillWidth(true);
    secondaryFields.getColumnConstraints().add(secondaryConstraints);
    VBox sizeFieldBox = fieldBox("Size / Condition", sizeField);
    secondaryFields.add(sizeFieldBox, 0, 0);
    GridPane.setHgrow(sizeFieldBox, Priority.ALWAYS);

    categoryBox.valueProperty().addListener((observable, oldValue, newValue) -> {
      boolean isArt = "ART".equalsIgnoreCase(newValue);
      artFields.setVisible(isArt);
      artFields.setManaged(isArt);
      if (!isArt) {
        artistField.clear();
        yearCreatedField.clear();
      }
    });
    artFields.setVisible(true);
    artFields.setManaged(true);

    Label messageLabel = new Label("");
    messageLabel.getStyleClass().add("create-message");
    messageLabel.setStyle(CREATE_MUTED_TEXT_STYLE);

    Button saveDraft = new Button(editItem == null ? "Save Draft" : "Save Draft");
    saveDraft.setMnemonicParsing(false);
    saveDraft.getStyleClass().add("create-secondary-btn");
    saveDraft.setStyle(CREATE_SECONDARY_BUTTON_STYLE);

    Button submitItem = new Button(editItem == null ? "Submit Item" : "Submit Draft");
    submitItem.setMnemonicParsing(false);
    submitItem.getStyleClass().add("create-primary-btn");
    submitItem.setStyle(CREATE_PRIMARY_BUTTON_STYLE);

    HBox actions = new HBox(12);
    actions.setAlignment(Pos.CENTER_LEFT);
    actions.getChildren().addAll(saveDraft, submitItem, messageLabel);
    HBox.setHgrow(messageLabel, Priority.ALWAYS);

    titleField.textProperty().addListener((observable, oldValue, newValue) ->
        previewName.setText(
            newValue == null || newValue.isBlank() ? "Untitled listing" : newValue.trim()
        ));
    priceField.textProperty().addListener((observable, oldValue, newValue) ->
        previewPrice.setText("Starting price: "
            + (newValue == null || newValue.isBlank() ? "0" : newValue.trim()) + " "
            + currencyBox.getValue()));
    currencyBox.valueProperty().addListener((observable, oldValue, newValue) ->
        previewPrice.setText("Starting price: "
            + (priceField.getText().isBlank() ? "0" : priceField.getText().trim()) + " "
            + (newValue == null ? "VND" : newValue)));

    if (editItem != null) {
      categoryBox.getSelectionModel().select(categoryEnumValue(editItem.category));
      titleField.setText(editItem.name);
      priceField.setText(stripCurrency(editItem.startingPrice));
      sizeField.setText(attributeValue(editItem.attributes, "size_condition"));
      artistField.setText(attributeValue(editItem.attributes, "artist"));
      yearCreatedField.setText(attributeValue(editItem.attributes, "year_created"));
      String currencyValue = attributeValue(editItem.attributes, "currency");
      if (!currencyValue.isBlank()
          && currencyBox.getItems().contains(currencyValue.toUpperCase())) {
        currencyBox.getSelectionModel().select(currencyValue.toUpperCase());
      }
      descriptionArea.setText(editItem.description);
      rebuildCreateFileList(
          fileListBox,
          fileListScroll,
          selectedFileLabel,
          previewImage,
          previewPlaceholder,
          imageCounterLabel
      );
      refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
    }

    browseButton.setOnAction(event -> chooseItemImages(
        selectedFileLabel,
        previewImage,
        previewPlaceholder,
        imageCounterLabel,
        messageLabel,
        fileListBox,
        fileListScroll
    ));
    uploadZone.setOnMouseClicked(event -> browseButton.fire());

    previousImageButton.setOnAction(event -> showCreatePreviewImage(
        previewImage, previewPlaceholder, imageCounterLabel, -1));
    nextImageButton.setOnAction(event -> showCreatePreviewImage(
        previewImage, previewPlaceholder, imageCounterLabel, 1));

    saveDraft.setOnAction(event -> submitCreateItem(
        true, categoryBox, titleField, descriptionArea, priceField,
        sizeField, artistField, yearCreatedField, currencyBox,
        saveDraft, submitItem, messageLabel, editItem
    ));

    submitItem.setOnAction(event -> submitCreateItem(
        false, categoryBox, titleField, descriptionArea, priceField,
        sizeField, artistField, yearCreatedField, currencyBox,
        saveDraft, submitItem, messageLabel, editItem
    ));

    detailsPanel.getChildren().addAll(
        detailsTitle,
        primaryFields,
        artFields,
        secondaryFields,
        fieldBox("Description", descriptionArea),
        actions
    );
    formShell.getChildren().addAll(formTitle, formNote, topRow, detailsPanel);
    workspaceBox.getChildren().add(formShell);

    if (primaryActionButton != null) {
      primaryActionButton.setVisible(false);
      primaryActionButton.setManaged(false);
    }
  }

  private TextField createFormField(String prompt) {
    TextField field = new TextField();
    field.setPromptText(prompt);
    field.getStyleClass().add("create-form-field");
    field.setStyle(CREATE_FORM_FIELD_STYLE);
    field.setMinWidth(0);
    field.setMaxWidth(Double.MAX_VALUE);
    field.setMinHeight(50);
    field.setPrefHeight(50);
    field.setMaxHeight(50);
    return field;
  }

  private VBox fieldBox(String labelText, Node input) {
    VBox box = new VBox(6);
    box.setMinWidth(0);
    box.setMaxWidth(Double.MAX_VALUE);
    if (input instanceof Region region) {
      region.setMinWidth(0);
      region.setMaxWidth(Double.MAX_VALUE);
    }
    Label label = new Label(labelText);
    label.getStyleClass().add("create-field-label");
    label.setStyle("-fx-text-fill: #f7f3e9; -fx-font-size: 11px; -fx-font-weight: bold;");
    box.getChildren().addAll(label, input);
    return box;
  }

  private void chooseItemImages(
      Label selectedFileLabel, ImageView previewImage,
      Label previewPlaceholder, Label imageCounterLabel,
      Label messageLabel, VBox fileListBox, ScrollPane fileListScroll) {

    int remainingSlots = MAX_CREATE_ITEM_IMAGES - pendingCreateItemUploads.size();
    if (remainingSlots <= 0) {
      showCreateMessage(messageLabel, "Tối đa 5 ảnh cho mỗi listing.", true);
      return;
    }

    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose item images");
    chooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.webp")
    );
    File home = new File(System.getProperty("user.home"));
    if (home.exists()) chooser.setInitialDirectory(home);

    List<File> selected = chooser.showOpenMultipleDialog(workspaceBox.getScene().getWindow());
    if (selected == null || selected.isEmpty()) return;

    List<File> accepted = selected.size() > remainingSlots
        ? new ArrayList<>(selected.subList(0, remainingSlots)) : selected;

    // Copy local trước — nhanh, preview ngay
    List<CreateItemUpload> localUploads = new ArrayList<>();
    try {
      Path uploadRoot = Path.of(System.getProperty("user.home"), ".auction-system", "uploads");
      Files.createDirectories(uploadRoot);
      long batch = System.currentTimeMillis();
      for (int i = 0; i < accepted.size(); i++) {
        File file = accepted.get(i);
        String safeName = file.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = uploadRoot.resolve(batch + "_" + i + "_" + safeName);
        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        localUploads.add(new CreateItemUpload("", target.toUri().toString(),
            file.getName(), file.length()));
      }
    } catch (IOException e) {
      showCreateMessage(messageLabel, "Không thể lưu ảnh.", true);
      return;
    }

    // Thêm vào list với publicUri rỗng, preview local ngay
    pendingCreateItemUploads.addAll(localUploads);
    pendingCreateItemPreviewIndex = Math.max(0, pendingCreateItemUploads.size() - localUploads.size());
    rebuildCreateFileList(fileListBox, fileListScroll, selectedFileLabel,
        previewImage, previewPlaceholder, imageCounterLabel);
    refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
    showCreateMessage(messageLabel, "Đang upload ảnh lên cloud...", false);

    Task<List<String>> task = new Task<>() {
      @Override protected List<String> call() {
        List<String> uris = new ArrayList<>();
        for (CreateItemUpload u : localUploads) {
          String uri = cloudMediaApiClient.upload(new File(java.net.URI.create(u.previewUri)));
          uris.add(uri != null ? uri : "");
        }
        return uris;
      }
    };

    task.setOnSucceeded(e -> {
      List<String> uris = task.getValue();
      int fail = 0;
      for (int i = 0; i < localUploads.size(); i++) {
        int idx = pendingCreateItemUploads.indexOf(localUploads.get(i));
        if (idx < 0) continue;
        if (uris.get(i).isBlank()) {
          pendingCreateItemUploads.remove(idx);
          fail++;
        } else {
          pendingCreateItemUploads.set(idx, new CreateItemUpload(
              uris.get(i), localUploads.get(i).previewUri,
              localUploads.get(i).fileName, localUploads.get(i).sizeBytes));
        }
      }
      pendingCreateItemPreviewIndex = Math.min(pendingCreateItemPreviewIndex,
          Math.max(0, pendingCreateItemUploads.size() - 1));
      rebuildCreateFileList(fileListBox, fileListScroll, selectedFileLabel,
          previewImage, previewPlaceholder, imageCounterLabel);
      refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
      int ok = localUploads.size() - fail;
      showCreateMessage(messageLabel,
          fail > 0 ? "Upload xong " + ok + "/" + localUploads.size() + " ảnh."
              : "Upload " + ok + " ảnh thành công.", fail > 0);
    });

    task.setOnFailed(e -> {
      pendingCreateItemUploads.removeIf(u -> u.publicUri == null || u.publicUri.isBlank());
      rebuildCreateFileList(fileListBox, fileListScroll, selectedFileLabel,
          previewImage, previewPlaceholder, imageCounterLabel);
      showCreateMessage(messageLabel, "Upload thất bại. Vui lòng thử lại.", true);
    });

    new Thread(task, "upload-thread").start();
  }

  private void rebuildCreateFileList(
      VBox fileListBox,
      ScrollPane fileListScroll,
      Label selectedFileLabel,
      ImageView previewImage,
      Label previewPlaceholder,
      Label imageCounterLabel) {
    fileListBox.getChildren().clear();

    selectedFileLabel.setText(pendingCreateItemUploads.isEmpty()
        ? "No file selected"
        : pendingCreateItemUploads.size() + "/" + MAX_CREATE_ITEM_IMAGES + " images selected");

    fileListScroll.setVisible(!pendingCreateItemUploads.isEmpty());
    fileListScroll.setManaged(!pendingCreateItemUploads.isEmpty());

    updateCreateFileListHeight(fileListScroll);

    for (int index = 0; index < pendingCreateItemUploads.size(); index++) {
      final int imageIndex = index;
      CreateItemUpload upload = pendingCreateItemUploads.get(index);

      HBox fileRow = new HBox(10);
      fileRow.getStyleClass().add("create-file-row");
      fileRow.setAlignment(Pos.CENTER_LEFT);
      fileRow.setMinHeight(CREATE_FILE_ROW_HEIGHT);
      fileRow.setPrefHeight(CREATE_FILE_ROW_HEIGHT);
      fileRow.setMaxHeight(CREATE_FILE_ROW_HEIGHT);
      fileRow.setMaxWidth(Double.MAX_VALUE);
      fileRow.setStyle("-fx-background-color: #202020; -fx-background-radius: 12; "
          + "-fx-border-color: #343434; -fx-border-radius: 12; -fx-padding: 8 10 8 10;");

      Label typeBadge = new Label(getFileExtension(upload.fileName));
      typeBadge.getStyleClass().add("create-file-type");
      typeBadge.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8; "
          + "-fx-border-color: #444444; -fx-border-radius: 8; -fx-text-fill: #d1b15d; "
          + "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 8 9 8 9;");

      Label fileName = new Label(upload.fileName);
      fileName.getStyleClass().add("create-file-name");
      fileName.setTextOverrun(OverrunStyle.ELLIPSIS);
      fileName.setMaxWidth(Double.MAX_VALUE);
      fileName.setStyle("-fx-text-fill: #f7f3e9; -fx-font-size: 12px; -fx-font-weight: bold;");

      Label fileSize = new Label(formatFileSize(upload.sizeBytes));
      fileSize.getStyleClass().add("create-muted-text");
      fileSize.setStyle(CREATE_MUTED_TEXT_STYLE);

      VBox fileText = new VBox(2);
      fileText.setMinWidth(0);
      fileText.setMaxWidth(Double.MAX_VALUE);
      fileText.getChildren().addAll(fileName, fileSize);
      HBox.setHgrow(fileText, Priority.ALWAYS);

      Button removeButton = new Button("×");
      removeButton.setMnemonicParsing(false);
      removeButton.getStyleClass().add("create-file-remove-btn");
      removeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #f7f3e9; "
          + "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand;");
      removeButton.setOnAction(event -> {
        pendingCreateItemUploads.remove(imageIndex);
        if (pendingCreateItemPreviewIndex >= pendingCreateItemUploads.size()) {
          pendingCreateItemPreviewIndex = Math.max(0, pendingCreateItemUploads.size() - 1);
        }
        rebuildCreateFileList(
            fileListBox,
            fileListScroll,
            selectedFileLabel,
            previewImage,
            previewPlaceholder,
            imageCounterLabel
        );
        refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
      });

      fileRow.setOnMouseClicked(event -> {
        if (removeButton.isHover()) {
          return;
        }
        pendingCreateItemPreviewIndex = imageIndex;
        refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
      });
      fileRow.getChildren().addAll(typeBadge, fileText, removeButton);
      fileListBox.getChildren().add(fileRow);
    }
  }

  private void showCreatePreviewImage(
      ImageView previewImage,
      Label previewPlaceholder,
      Label imageCounterLabel,
      int direction) {
    if (pendingCreateItemUploads.isEmpty()) {
      return;
    }
    pendingCreateItemPreviewIndex = (pendingCreateItemPreviewIndex + direction
        + pendingCreateItemUploads.size()) % pendingCreateItemUploads.size();
    refreshCreatePreviewImage(previewImage, previewPlaceholder, imageCounterLabel);
  }

  private void refreshCreatePreviewImage(
      ImageView previewImage,
      Label previewPlaceholder,
      Label imageCounterLabel) {
    if (pendingCreateItemUploads.isEmpty()) {
      previewImage.setImage(null);
      previewImage.setViewport(null);
      previewPlaceholder.setVisible(true);
      previewPlaceholder.setManaged(true);
      updateCreatePreviewCounter(imageCounterLabel);
      return;
    }

    pendingCreateItemPreviewIndex = Math.max(0,
        Math.min(pendingCreateItemPreviewIndex, pendingCreateItemUploads.size() - 1));
    previewPlaceholder.setVisible(false);
    previewPlaceholder.setManaged(false);
    Image image = new Image(
        pendingCreateItemUploads.get(pendingCreateItemPreviewIndex).previewUri,
        true
    );
    setCoverImage(previewImage, image);
    updateCreatePreviewCounter(imageCounterLabel);
  }

  private void updateCreateFileListHeight(ScrollPane fileListScroll) {
    if (fileListScroll == null || pendingCreateItemUploads.isEmpty()) {
      return;
    }
    double rowCount = pendingCreateItemUploads.size();
    double contentHeight = rowCount * CREATE_FILE_ROW_HEIGHT
        + Math.max(0, rowCount - 1) * CREATE_FILE_LIST_GAP;
    double viewportHeight = Math.min(CREATE_FILE_LIST_MAX_HEIGHT, contentHeight);
    fileListScroll.setMinHeight(viewportHeight);
    fileListScroll.setPrefHeight(viewportHeight);
    fileListScroll.setMaxHeight(viewportHeight);
  }

  private void setCoverImage(ImageView previewImage, Image image) {
    previewImage.setViewport(null);
    previewImage.setImage(image);
    image.progressProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue.doubleValue() >= 1.0) {
        applyCoverImageViewport(previewImage);
      }
    });
    Platform.runLater(() -> applyCoverImageViewport(previewImage));
  }

  private void applyCoverImageViewport(ImageView previewImage) {
    Image image = previewImage.getImage();
    if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
        || previewImage.getFitWidth() <= 0 || previewImage.getFitHeight() <= 0) {
      return;
    }

    double targetRatio = previewImage.getFitWidth() / previewImage.getFitHeight();
    double imageRatio = image.getWidth() / image.getHeight();
    double viewportWidth = image.getWidth();
    double viewportHeight = image.getHeight();

    if (imageRatio > targetRatio) {
      viewportWidth = image.getHeight() * targetRatio;
    } else if (imageRatio < targetRatio) {
      viewportHeight = image.getWidth() / targetRatio;
    }

    double viewportX = (image.getWidth() - viewportWidth) / 2;
    double viewportY = (image.getHeight() - viewportHeight) / 2;
    previewImage.setViewport(
        new Rectangle2D(viewportX, viewportY, viewportWidth, viewportHeight));
  }

  private void updateCreatePreviewCounter(Label imageCounterLabel) {
    if (imageCounterLabel == null) {
      return;
    }
    int total = pendingCreateItemUploads.size();
    imageCounterLabel.setText(total == 0
        ? "0/" + MAX_CREATE_ITEM_IMAGES
        : (pendingCreateItemPreviewIndex + 1) + "/" + total);
  }

  private String pendingCreateItemImagePayload() {
    List<String> uris = new ArrayList<>();
    for (CreateItemUpload upload : pendingCreateItemUploads) {
      uris.add(upload.publicUri);
    }
    return String.join("\n", uris);
  }

  private String getFileExtension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "IMG";
    }

    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
      return "IMG";
    }

    String extension = fileName.substring(dotIndex + 1).toUpperCase();
    return extension.length() > 4 ? extension.substring(0, 4) : extension;
  }

  private String formatFileSize(long sizeBytes) {
    if (sizeBytes <= 0) {
      return "0 KB";
    }

    double size = sizeBytes;
    String[] units = {"B", "KB", "MB", "GB"};
    int unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    if (unitIndex == 0) {
      return Math.round(size) + " " + units[unitIndex];
    }
    return String.format("%.1f %s", size, units[unitIndex]);
  }

  private void submitCreateItem(boolean draftMode,
      ComboBox<String> categoryBox,
      TextField titleField,
      TextArea descriptionArea,
      TextField priceField,
      TextField sizeField,
      TextField artistField,
      TextField yearCreatedField,
      ComboBox<String> currencyBox,
      Button saveDraftButton,
      Button submitItemButton,
      Label messageLabel,
      SellerItemData editItem) {
    String title = safeTrim(titleField.getText());
    String category = categoryBox.getValue() == null ? "" : categoryBox.getValue().trim();
    BigDecimal price = parseCreateItemPrice(priceField.getText());

    if (category.isBlank()) {
      showCreateMessage(messageLabel, "Vui lòng chọn category cho item.", true);
      return;
    }

    String artist = safeTrim(artistField.getText());
    String yearCreated = safeTrim(yearCreatedField.getText());
    if ("ART".equalsIgnoreCase(category)) {
      if (artist.isBlank()) {
        showCreateMessage(messageLabel, "Vui lòng nhập artist cho item ART.", true);
        return;
      }
      if (!isValidCreateItemYear(yearCreated)) {
        showCreateMessage(messageLabel, "Year phải là số hợp lệ và lớn hơn 0.", true);
        return;
      }
    } else {
      artist = "";
      yearCreated = "";
    }

    if (title.isBlank()) {
      showCreateMessage(messageLabel, "Vui lòng nhập title cho item.", true);
      return;
    }

    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
      showCreateMessage(messageLabel, "Price phải là số hợp lệ và lớn hơn 0.", true);
      return;
    }

    User currentUser = SessionManager.getCurrentUser();
    int sellerId = currentUser == null ? 0 : currentUser.getUserId();
    String currency = currencyBox.getValue() == null ? "VND" : currencyBox.getValue();

    String status = draftMode ? "DRAFT" : "PENDING_REVIEW";
    String payload = fields(
        sellerId,
        category,
        title,
        safeTrim(descriptionArea.getText()),
        price.toPlainString(),
        status,
        "Timed Auction",
        safeTrim(sizeField.getText()),
        "",
        "",
        currency,
        artist,
        yearCreated,
        pendingCreateItemImagePayload()
    );

    showCreateMessage(
        messageLabel,
        editItem == null
            ? (draftMode
            ? "Đang lưu draft item..."
            : "Đang submit item lên Pending Approval...")
            : (draftMode
                ? "Đang cập nhật draft item..."
                : "Đang submit draft item lên Pending Approval..."),
        false
    );

    if (editItem == null) {
      createItemNetworkManager.send("CREATE_ITEM " + payload);
    } else {
      createItemNetworkManager.send(
          "USER_UPDATE_DRAFT_ITEM " + fields(editItem.itemId) + "|" + payload
      );
    }
  }

  private void handleCreateItemServerMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.startsWith("USER_ITEM_STATS ")) {
      List<String> fields = splitPayload(message.substring("USER_ITEM_STATS ".length()));
      if (fields.size() >= 4) {
        sellerItemTotal = parseIntOrDefault(fields.get(0), 0);
        sellerItemDrafts = parseIntOrDefault(fields.get(1), 0);
        sellerItemActiveSales = parseIntOrDefault(fields.get(2), 0);
        sellerItemSold = parseIntOrDefault(fields.get(3), 0);
        sellerItemStatsLoaded = true;
        applySellerItemStatsIfAvailable();
      }
      return;
    }

    if (message.equals("USER_ITEMS_BEGIN")) {
      sellerItemsLoading = true;
      incomingSellerItems = new ArrayList<>();
      return;
    }

    if (message.startsWith("USER_ITEM ")) {
      SellerItemData parsed = parseSellerItem(message.substring("USER_ITEM ".length()));
      if (parsed != null) {
        incomingSellerItems.add(parsed);
      }
      return;
    }

    if (message.equals("USER_ITEMS_END")) {
      sellerItemsLoading = false;
      sellerItems.clear();
      sellerItems.addAll(incomingSellerItems);
      sellerItemsLoaded = true;
      if ("myItems".equals(currentSectionKey)) {
        renderWorkspace("myItems", activeFilter);
        applySellerItemStatsIfAvailable();
      }
      return;
    }

    if (message.startsWith("USER_ITEMS_ERROR")) {
      sellerItemsLoading = false;
      sellerItems.clear();
      sellerItemsLoaded = true;
      if ("myItems".equals(currentSectionKey)) {
        renderWorkspace("myItems", activeFilter);
      }
      return;
    }

    if (message.startsWith("CREATE_ITEM_SUCCESS")
        || message.startsWith("USER_UPDATE_DRAFT_SUCCESS")) {
      sellerItemsLoaded = false;
      sellerItemStatsLoaded = false;
      requestCreateListingMetadata();
      showTemporaryDetail(
          "My Items",
          message.startsWith("USER_UPDATE_DRAFT_SUCCESS")
              ? "Cập nhật draft item thành công."
              : "Tạo item thành công. Nếu submit item, admin sẽ thấy nó trong "
                  + "Pending Approval."
      );
      if ("myItems".equals(currentSectionKey)) {
        showSection("myItems");
      }
      return;
    }

    if (message.startsWith("CREATE_ITEM_FAIL")
        || message.startsWith("USER_UPDATE_DRAFT_FAIL")) {
      showTemporaryDetail("My Items failed", message);
    }
  }

  private SellerItemData parseSellerItem(String payload) {
    List<String> fields = splitPayload(payload);
    if (fields.size() < 14) {
      return null;
    }

    String itemId = safeField(fields, 0);
    String category = normalizeCategoryLabel(safeField(fields, 2));
    String name = fallback(safeField(fields, 3), "Item #" + itemId);
    String description = safeField(fields, 4);
    String startingPrice = formatMoney(safeField(fields, 5));
    String status = prettyStatus(safeField(fields, 6));
    String createdAt = shortTimestamp(safeField(fields, 7));
    String auctionId = safeField(fields, 8);
    String auctionStatus = fallback(safeField(fields, 9), "No auction");
    String currentPrice = formatMoney(safeField(fields, 10));
    int bidCount = parseIntOrDefault(safeField(fields, 11), 0);
    String imagePayload = safeField(fields, 12);
    String attributes = safeField(fields, 13);
    String highestBidderId = safeField(fields, 14);
    String highestBidder = safeField(fields, 15);
    String auctionStartTime = shortTimestamp(safeField(fields, 16));
    String auctionEndTime = shortTimestamp(safeField(fields, 17));
    long secondsLeft = parseLongOrDefault(safeField(fields, 18), 0L);

    return new SellerItemData(
        itemId,
        safeField(fields, 1),
        category,
        name,
        description,
        startingPrice,
        status,
        createdAt,
        auctionId,
        auctionStatus,
        currentPrice,
        bidCount,
        firstImage(imagePayload),
        imagePayload,
        attributes,
        highestBidderId,
        highestBidder,
        auctionStartTime,
        auctionEndTime,
        secondsLeft
    );
  }

  private void applySellerItemStatsIfAvailable() {
    if (!sellerItemStatsLoaded || !"myItems".equals(currentSectionKey)) {
      return;
    }

    setLabelText(statValue1, twoDigit(sellerItemTotal));
    setLabelText(statValue2, twoDigit(sellerItemDrafts));
    setLabelText(statValue3, twoDigit(sellerItemActiveSales));
    setLabelText(statValue4, twoDigit(sellerItemSold));
    setLabelText(statLabel1, "Items");
    setLabelText(statLabel2, "Drafts");
    setLabelText(statLabel3, "In Auction");
    setLabelText(statLabel4, "Sold");
  }

  private String twoDigit(int value) {
    return value < 10 ? "0" + value : String.valueOf(value);
  }

  private void setLabelText(Label label, String value) {
    if (label != null) {
      label.setText(value == null ? "" : value);
    }
  }

  private void applyMyBidStatsIfVisible() {
    if (!"myBids".equals(currentSectionKey)) {
      return;
    }

    int total = myBids.size();
    int winning = 0;
    int outbid = 0;
    int completed = 0;
    for (MyBidData bid : myBids) {
      String status = resolveMyBidStatus(bid);
      if (normalize(status).equals("winning")) {
        winning++;
      } else if (normalize(status).equals("outbid")) {
        outbid++;
      }
      if (normalize(status).equals("won") || normalize(status).equals("lost")
          || normalize(status).equals("canceled")) {
        completed++;
      }
    }

    setLabelText(statValue1, twoDigit(total));
    setLabelText(statValue2, twoDigit(winning));
    setLabelText(statValue3, twoDigit(outbid));
    setLabelText(statValue4, twoDigit(completed));
    setLabelText(statLabel1, "Total Bids");
    setLabelText(statLabel2, "Winning");
    setLabelText(statLabel3, "Outbid");
    setLabelText(statLabel4, "Completed");
  }

  private void applyAutoBidStatsIfVisible() {
    if (!"autoBids".equals(currentSectionKey)) {
      return;
    }

    int total = autoBids.size();
    int active = 0;
    int completed = 0;
    int canceled = 0;
    for (AutoBidData rule : autoBids) {
      String normalized = normalize(rule.status);
      if (normalized.equals("active")) {
        active++;
      } else if (normalized.equals("completed")) {
        completed++;
      } else if (normalized.equals("canceled")) {
        canceled++;
      }
    }

    setLabelText(statValue1, twoDigit(total));
    setLabelText(statValue2, twoDigit(active));
    setLabelText(statValue3, twoDigit(completed));
    setLabelText(statValue4, twoDigit(canceled));
    setLabelText(statLabel1, "Rules");
    setLabelText(statLabel2, "Active");
    setLabelText(statLabel3, "Completed");
    setLabelText(statLabel4, "Canceled");
  }

  private void applyUserAuctionStatsIfVisible() {
    if ("myItems".equals(currentSectionKey)) {
      return;
    }
    int total = liveAuctionCards.size();
    int running = 0;
    int open = 0;
    int totalBids = 0;
    for (AuctionCardData card : liveAuctionCards) {
      String status = normalize(card.status);
      if (status.equals("running")) {
        running++;
      } else if (status.equals("open")) {
        open++;
      }
      totalBids += card.bidCount;
    }

    setLabelText(statValue1, twoDigit(total));
    setLabelText(statValue2, twoDigit(running));
    setLabelText(statValue3, twoDigit(open));
    setLabelText(statValue4, String.valueOf(totalBids));
    setLabelText(statLabel1, "Live Auctions");
    setLabelText(statLabel2, "Running");
    setLabelText(statLabel3, "Open");
    setLabelText(statLabel4, "Total Bids");
  }

  private void applyTransactionStatsIfVisible() {
    if (!"winners".equals(currentSectionKey)) {
      return;
    }

    int total = transactions.size();
    int pending = 0;
    int completed = 0;
    int refunded = 0;
    for (TransactionData transaction : transactions) {
      String status = normalize(transaction.paymentStatus);
      if (status.equals("pending")) {
        pending++;
      } else if (status.equals("completed")) {
        completed++;
      } else if (status.equals("refunded")) {
        refunded++;
      }
    }

    setLabelText(statValue1, twoDigit(total));
    setLabelText(statValue2, twoDigit(pending));
    setLabelText(statValue3, twoDigit(completed));
    setLabelText(statValue4, twoDigit(refunded));
    setLabelText(statLabel1, "Transactions");
    setLabelText(statLabel2, "Pending");
    setLabelText(statLabel3, "Completed");
    setLabelText(statLabel4, "Refunded");
  }

  private void applyEmptyStats(String first, String second, String third, String fourth) {
    setLabelText(statValue1, "00");
    setLabelText(statValue2, "00");
    setLabelText(statValue3, "00");
    setLabelText(statValue4, "00");
    setLabelText(statLabel1, first);
    setLabelText(statLabel2, second);
    setLabelText(statLabel3, third);
    setLabelText(statLabel4, fourth);
  }

  private void addAuctionFact(GridPane grid, int column, int row, String label, String value) {
    VBox box = new VBox(2);
    box.getStyleClass().add("auction-live-fact");
    Label key = new Label(label);
    key.getStyleClass().add("auction-live-fact-key");
    Label val = new Label(fallback(value, "not available"));
    val.getStyleClass().add("auction-live-fact-value");
    val.setWrapText(true);
    box.getChildren().addAll(key, val);
    grid.add(box, column, row);
  }

  private String buildAuctionDetailText(AuctionCardData data) {
    StringBuilder builder = new StringBuilder();
    builder.append("Auction ID: AUC-").append(data.auctionId).append('\n');
    builder.append("Item ID: ITEM-").append(data.itemId).append('\n');
    builder.append("Seller: ").append(data.seller).append('\n');
    builder.append("Category: ").append(data.category).append('\n');
    builder.append("Status: ").append(data.status).append('\n');
    builder.append("Current price: ").append(data.price).append('\n');
    builder.append("Minimum increment: ").append(data.minimumIncrement).append('\n');
    builder.append("Reserve price: ").append(data.reservePrice).append('\n');
    builder.append("Start: ").append(data.startTime).append('\n');
    builder.append("End: ").append(data.endTime).append('\n');
    builder.append("Time left: ").append(data.endsIn).append('\n');
    builder.append("Snipe window: ").append(data.snipeWindowSeconds).append(" seconds\n");
    builder.append("Snipe extension: ").append(data.snipeExtensionSeconds).append(" seconds\n");
    if (!data.description.isBlank()) {
      builder.append("Description: ").append(data.description).append('\n');
    }
    if (!data.attributes.isBlank()) {
      builder.append("Attributes:\n").append(data.attributes);
    }
    return builder.toString();
  }

  private String safeField(List<String> fields, int index) {
    return index >= 0 && index < fields.size() ? fields.get(index) : "";
  }

  private String fallback(String value, String fallbackValue) {
    return value == null || value.isBlank() ? fallbackValue : value;
  }

  private String normalizeCategoryLabel(String rawCategory) {
    String normalized = rawCategory == null ? "" : rawCategory.trim().toUpperCase();
    return switch (normalized) {
      case "ELECTRONIC", "ELECTRONICS" -> "Electronic";
      case "VEHICLE" -> "Vehicle";
      case "ART" -> "Art";
      default -> normalized.isBlank() ? "Uncategorized" : normalized.substring(0, 1)
          + normalized.substring(1).toLowerCase();
    };
  }

  private String firstImage(String imagePayload) {
    if (imagePayload == null || imagePayload.isBlank()) {
      return AUCTION_IMAGE_FALLBACK;
    }
    String normalizedPayload = imagePayload.replace("\\n", "\n");
    for (String image : normalizedPayload.split("\\R")) {
      if (image != null && !image.isBlank()) {
        return image.trim();
      }
    }
    return AUCTION_IMAGE_FALLBACK;
  }


  private String formatMoney(String value) {
    try {
      String normalized = value == null ? "" : value.replace(",", "").trim();
      if (normalized.isBlank()) {
        return "0 VND";
      }
      return MONEY_FORMAT.format(new BigDecimal(normalized)) + " VND";
    } catch (NumberFormatException exception) {
      return fallback(value, "0") + " VND";
    }
  }

  private String formatBidCount(int bidCount) {
    return bidCount + (bidCount == 1 ? " bid" : " bids");
  }

  private void rememberAuctionClock(String auctionId, long secondsLeft) {
    if (auctionId == null || auctionId.isBlank() || secondsLeft < 0) {
      return;
    }
    auctionSecondsSyncedAtMillis.put(auctionId, System.currentTimeMillis());
  }

  private long secondsFromServerClock(AuctionCardData data) {
    if (data == null || data.auctionId == null || data.auctionId.isBlank() || data.secondsLeft < 0) {
      return -1L;
    }
    Long syncedAt = auctionSecondsSyncedAtMillis.get(data.auctionId);
    if (syncedAt == null) {
      return -1L;
    }
    long elapsed = Math.max(0L, (System.currentTimeMillis() - syncedAt) / 1000L);
    return Math.max(0L, data.secondsLeft - elapsed);
  }

  private long currentSecondsLeft(AuctionCardData data) {
    if (data == null) {
      return 0L;
    }
    long serverClockSeconds = secondsFromServerClock(data);
    if (serverClockSeconds >= 0) {
      return serverClockSeconds;
    }
    return secondsUntil(data.endTime, data.secondsLeft);
  }

  private long secondsUntil(String endTime, long fallbackValue) {
    LocalDateTime parsed = parseTimestamp(endTime);
    if (parsed == null) {
      return Math.max(0, fallbackValue);
    }
    return Math.max(0, java.time.Duration.between(LocalDateTime.now(), parsed).getSeconds());
  }

  private LocalDateTime parseTimestamp(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().replace('T', ' ');
    int dotIndex = normalized.indexOf('.');
    if (dotIndex > 0) {
      normalized = normalized.substring(0, dotIndex);
    }
    try {
      return LocalDateTime.parse(normalized, AUCTION_TIME_FORMATTER);
    } catch (DateTimeParseException exception) {
      return null;
    }
  }

  private String formatTimeLeft(long secondsLeft) {
    if (secondsLeft <= 0) {
      return "Ended";
    }
    long days = secondsLeft / 86400;
    long hours = (secondsLeft % 86400) / 3600;
    long minutes = (secondsLeft % 3600) / 60;
    long seconds = secondsLeft % 60;
    if (days > 0) {
      return days + "d " + hours + "h " + minutes + "m";
    }
    if (hours > 0) {
      return hours + "h " + minutes + "m " + seconds + "s";
    }
    if (minutes > 0) {
      return minutes + "m " + seconds + "s";
    }
    return seconds + "s";
  }

  private String readablePaymentFailure(String reason) {
    String normalized = reason == null ? "" : reason.trim().toUpperCase();
    return switch (normalized) {
      case "NOT_LOGGED_IN" -> "Please sign in before paying for a transaction.";
      case "INVALID_FORMAT" -> "Payment request is missing auction information.";
      case "PAYMENT_NOT_FOUND" -> "No payable transaction exists for this auction.";
      case "NOT_BUYER" -> "Only the winning buyer can pay this transaction.";
      case "PAYMENT_COMPLETED" -> "This transaction has already been paid.";
      case "PAYMENT_FAILED" -> "This transaction is already marked as failed.";
      case "PAYMENT_REFUNDED" -> "This transaction has already been refunded.";
      case "PAYMENT_NOT_COMPLETED" -> "Payment could not be completed. Please check wallet balance and transaction status.";
      default -> normalized.isBlank()
          ? "Payment could not be completed."
          : "Payment could not be completed: " + normalized.replace('_', ' ').toLowerCase() + ".";
    };
  }

  private long parseLongOrDefault(String value, long fallbackValue) {
    try {
      return value == null || value.isBlank() ? fallbackValue : Long.parseLong(value.trim());
    } catch (NumberFormatException exception) {
      return fallbackValue;
    }
  }

  private String shortTimestamp(String value) {
    if (value == null || value.isBlank()) {
      return "not available";
    }
    LocalDateTime parsed = parseTimestamp(value);
    if (parsed != null) {
      return parsed.format(AUCTION_TIME_FORMATTER);
    }
    return value.trim();
  }

  private String initialsFor(String text) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank()) {
      return "AU";
    }
    String[] parts = normalized.split("\\s+");
    if (parts.length == 1) {
      return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }
    return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
  }

  private String fields(Object... values) {
    List<String> encoded = new ArrayList<>();
    for (Object value : values) {
      encoded.add(encodeField(value));
    }
    return String.join("|", encoded);
  }

  /**
   * Escape giá trị trước khi ghép vào protocol socket đơn giản của ClientHandler.
   *
   * <p>Logic này cần khớp với ClientHandler#splitPayload để mô tả và danh sách
   * URI ảnh có thể chứa khoảng trắng, dấu pipe hoặc xuống dòng mà vẫn giữ
   * payload hợp lệ.</p>
   */
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

  private int parseIntOrDefault(String value, int fallbackValue) {
    try {
      return value == null || value.isBlank() ? fallbackValue : Integer.parseInt(value.trim());
    } catch (NumberFormatException exception) {
      return fallbackValue;
    }
  }

  private BigDecimal parseCreateItemPrice(String rawValue) {
    try {
      String normalized = rawValue == null ? "" : rawValue.trim().replace(",", "");
      return normalized.isBlank() ? null : new BigDecimal(normalized);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private boolean isValidCreateItemYear(String rawValue) {
    try {
      return rawValue != null && Integer.parseInt(rawValue.trim()) > 0;
    } catch (Exception exception) {
      return false;
    }
  }

  private void showCreateMessage(Label label, String message, boolean error) {
    label.setText(message);
    label.getStyleClass().removeAll("create-message-error", "create-message-info");
    label.getStyleClass().add(error ? "create-message-error" : "create-message-info");
    label.setStyle(error
        ? "-fx-text-fill: #a34f4f; -fx-font-size: 11px; -fx-font-weight: bold;"
        : CREATE_MUTED_TEXT_STYLE);
  }

  private String safeTrim(String value) {
    return value == null ? "" : value.trim();
  }

  private String fileNameFromPath(String path) {
    if (path == null || path.isBlank()) {
      return "image";
    }
    String normalized = path.trim();
    int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
    return slash >= 0 && slash < normalized.length() - 1
        ? normalized.substring(slash + 1)
        : normalized;
  }

  private String attributeValue(String attributes, String key) {
    if (attributes == null || attributes.isBlank() || key == null || key.isBlank()) {
      return "";
    }
    String normalizedKey = normalize(key);
    for (String line : attributes.split("\\R")) {
      int colon = line.indexOf(':');
      if (colon <= 0) {
        continue;
      }
      String currentKey = normalize(line.substring(0, colon));
      if (currentKey.equals(normalizedKey)) {
        return line.substring(colon + 1).trim();
      }
    }
    return "";
  }

  private String categoryEnumValue(String category) {
    String normalized = normalize(category);
    return switch (normalized) {
      case "art" -> "ART";
      case "vehicle" -> "VEHICLE";
      case "electronic", "electronics" -> "ELECTRONIC";
      default -> "ART";
    };
  }

  private String stripCurrency(String formattedMoney) {
    if (formattedMoney == null) {
      return "";
    }
    return formattedMoney.replace("VND", "").replace("USD", "").trim();
  }

  private void showTemporaryDetail(String title, String description) {
    if (surfaceTitleLabel != null) {
      surfaceTitleLabel.setText(title);
    }
  }

}