package client.controller;

import client.model.User;
import client.service.NetworkManager;
import client.service.SessionManager;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

/**
 * Controller for bidder and seller dashboard features shown to regular users.
 */
public class UserDashboardController extends BaseDashboardController {

  @FXML private FlowPane userActionBar;
  @FXML private VBox workspaceBox;
  @FXML private Label workspaceTitleLabel;
  @FXML private Button primaryActionButton;
  @FXML private Button createListingFloatingButton;

  private static final int AUCTIONS_PER_PAGE = 6;
  private static final double USER_ACTION_PRIMARY_WIDTH = 84;
  private static final double USER_ACTION_MORE_WIDTH = 28;
  private static final double USER_ACTION_GAP = 6;
  private static final double PRODUCT_IMAGE_INITIAL_WIDTH = 360;
  private static final double PRODUCT_IMAGE_HEIGHT = 155;
  private static final int MAX_CREATE_ITEM_IMAGES = 5;
  private static final double CREATE_UPLOAD_CARD_MAX_WIDTH = 520;
  private static final double CREATE_PREVIEW_CARD_MAX_WIDTH = 460;
  private static final double CREATE_UPLOAD_ZONE_HEIGHT = 220;
  private static final double CREATE_PREVIEW_IMAGE_HEIGHT = 250;
  private static final double CREATE_FILE_ROW_HEIGHT = 52;
  private static final double CREATE_FILE_LIST_MAX_HEIGHT = 118;
  private static final double CREATE_FILE_LIST_GAP = 8;
  private static final String CREATE_CARD_STYLE = "-fx-background-color: #fbfaf6; "
      + "-fx-background-radius: 20; -fx-border-color: #dde7df; -fx-border-radius: 20; "
      + "-fx-padding: 16;";
  private static final String CREATE_SECTION_TITLE_STYLE = "-fx-text-fill: #22433b; "
      + "-fx-font-size: 15px; -fx-font-weight: bold;";
  private static final String CREATE_MUTED_TEXT_STYLE = "-fx-text-fill: #738581; "
      + "-fx-font-size: 11px;";
  private static final String CREATE_FORM_FIELD_STYLE = "-fx-background-color: #fbfaf6; "
      + "-fx-background-radius: 15; -fx-border-color: #d9e2db; -fx-border-radius: 15; "
      + "-fx-padding: 10 13 10 13; -fx-text-fill: #294941; "
      + "-fx-prompt-text-fill: #95a8a2; -fx-min-height: 50; -fx-pref-height: 50;";
  private static final String CREATE_TEXT_AREA_STYLE = "-fx-background-color: #fbfaf6; "
      + "-fx-control-inner-background: #fbfaf6; -fx-background-radius: 15; "
      + "-fx-border-color: #d9e2db; -fx-border-radius: 15; -fx-padding: 10 13 10 13; "
      + "-fx-text-fill: #294941; -fx-prompt-text-fill: #95a8a2;";
  private static final String CREATE_COMBO_STYLE = "-fx-background-color: #fbfaf6; "
      + "-fx-background-radius: 15; -fx-border-color: #d9e2db; -fx-border-radius: 15; "
      + "-fx-padding: 0 12 0 12; -fx-min-height: 50; -fx-pref-height: 50;";
  private static final String CREATE_DARK_BUTTON_STYLE = "-fx-background-color: #183530; "
      + "-fx-text-fill: #f7f3e9; -fx-background-radius: 16; -fx-padding: 10 16 10 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
  private static final String CREATE_PRIMARY_BUTTON_STYLE = "-fx-background-color: "
      + "linear-gradient(to right, #d1b15d, #a78634); "
      + "-fx-text-fill: #17352c; -fx-background-radius: 16; -fx-padding: 10 16 10 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
  private static final String CREATE_SECONDARY_BUTTON_STYLE = "-fx-background-color: transparent; "
      + "-fx-border-color: #bccbc3; -fx-border-radius: 16; -fx-background-radius: 16; "
      + "-fx-text-fill: #294941; -fx-padding: 10 16 10 16; "
      + "-fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";

  private final Map<String, SectionContent> sections = buildSections();
  private final List<AuctionCardData> auctionCards = buildAuctionCards();
  private final List<CategoryData> categories = buildCategories();
  private final Map<String, Image> imageCache = new HashMap<>();
  /**
   * Reused connection for the inline Create Listing form.
   *
   * <p>Note: this dashboard controller opens its own socket, so CREATE_ITEM still sends the
   * persisted session user id in the payload. The server prefers the authenticated socket user
   * when available and falls back to this id only for this dashboard-created connection.</p>
   */
  private NetworkManager createItemNetworkManager;
  private final List<CreateItemUpload> pendingCreateItemUploads = new ArrayList<>();
  private int pendingCreateItemPreviewIndex;
  private boolean sellerItemStatsLoaded;
  private int sellerItemTotal;
  private int sellerItemDrafts;
  private int sellerItemActiveSales;
  private int sellerItemSold;

  private String currentSectionKey = "dashboard";
  private String activeFilter = "Overview";
  private int auctionPage = 1;


  @Override
  @FXML
  protected void initialize() {
    preloadAuctionImages();
    setupCreateItemNetwork();
    setupCreateListingFloatingButton();
    super.initialize();
  }

  private void setupCreateListingFloatingButton() {
    if (createListingFloatingButton == null) {
      return;
    }
    createListingFloatingButton.setVisible(false);
    createListingFloatingButton.setManaged(false);
    createListingFloatingButton.setOnAction(event -> handleOpenCreateListing());
  }

  @FXML
  private void handleOpenCreateListing() {
    renderCreateItemForm();
  }

  private void preloadAuctionImages() {
    for (AuctionCardData card : auctionCards) {
      getCachedImage(card.imagePath);
    }
  }

  private void setupCreateItemNetwork() {
    createItemNetworkManager = new NetworkManager();
    createItemNetworkManager.setMessageHandler(
        message -> Platform.runLater(() -> handleCreateItemServerMessage(message))
    );
    requestCreateListingMetadata();
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

    User currentUser = SessionManager.getCurrentUser();
    if (currentUser != null && currentUser.getUserId() > 0) {
      createItemNetworkManager.send("USER_ITEM_STATS " + currentUser.getUserId());
    }
  }

  @Override
  protected void handleLogout() {
    if (createItemNetworkManager != null) {
      createItemNetworkManager.disconnect();
    }
    super.handleLogout();
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
    return "BIDDER / SELLER";
  }

  @Override
  protected void showSection(String sectionKey) {
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
    updatePrimaryAction(sectionKey);
    renderWorkspace(sectionKey, activeFilter);
    setCreateListingFloatingButtonVisible("myItems".equals(sectionKey));
    if ("myItems".equals(sectionKey)) {
      requestCreateListingMetadata();
      applySellerItemStatsIfAvailable();
    }
  }

  private void setCreateListingFloatingButtonVisible(boolean visible) {
    if (createListingFloatingButton == null) {
      return;
    }
    createListingFloatingButton.setVisible(visible);
    createListingFloatingButton.setManaged(visible);
  }

  private Map<String, SectionContent> buildSections() {
    Map<String, SectionContent> map = new LinkedHashMap<>();

    map.put("dashboard", page(
        "Dashboard",
        "Track bids, browse live auctions, manage listings, and follow transactions.",
        "User Auction Workspace",
        "A compact workspace for outbid alerts, ending-soon auctions, unpaid wins, seller " +
            "tasks, and auto-bid warnings.",
        new String[]{"12", "04", "03", "08"},
        new String[]{"Active Bids", "Winning Now", "Outbid", "Ending Soon"},
        new String[]{"Action needed", "Bid tracking", "Seller follow-up"},
        new String[]{
            "Outbid rows and ending-soon auctions are pushed to the top.",
            "My Bids keeps current price, your bid, status, and next action visible.",
            "Sold items and won auctions move into Transactions after bidding ends."
        },
        new String[]{
            "You were outbid on Vintage Camera.",
            "MacBook Pro M3 ends in 42 minutes.",
            "Mechanical Keyboard sale is ready to ship.",
            "Auto bid reached 80% of max limit."
        }
    ));

    map.put("auctions", page(
        "Auctions",
        "Browse by category, filter live auctions, preview items, and place bids quickly.",
        "Auction Browse",
        "Marketplace-style browsing with product images, category cards, status filters, and " +
            "paginated auction cards.",
        new String[]{"32", "10", "08", "06"},
        new String[]{"Live Auctions", "Ending Soon", "Hot Items", "Watched"},
        new String[]{"Shop by Category", "Auction cards", "Pagination"},
        new String[]{
            "Category cards work as practical browse shortcuts instead of decorative sections.",
            "Each auction card shows image, category, current bid, bid count, countdown, " +
                "badge, and actions.",
            "Large result sets stay manageable with page 1, page 2, and Next/Previous controls."
        },
        new String[]{
            "Vintage Camera is ending soon with high demand.",
            "MacBook Pro M3 is the most watched Electronics auction.",
            "Signed Art Print is a no-reserve auction.",
            "Use category cards before opening advanced filters."
        }
    ));

    map.put("myBids", page(
        "My Bids",
        "Track every bid you placed and act when you are winning, outbid, won, or lost.",
        "Bid Tracking Board",
        "A management table is best here: item thumbnail, current price, your bid, status, " +
            "countdown, and quick action.",
        new String[]{"18", "07", "05", "06"},
        new String[]{"Total Bids", "Winning", "Outbid", "Completed"},
        new String[]{"Bid history", "Status badges", "Quick re-bid"},
        new String[]{
            "Rows should compare current price, your latest bid, and closing pressure.",
            "Winning, Outbid, Won, and Lost statuses stay visible as badges.",
            "Outbid rows expose Bid Again without forcing a full detail page first."
        },
        new String[]{
            "You are winning 7 auctions right now.",
            "5 bids are outbid and need action.",
            "2 auctions end within the next hour.",
            "Completed bids move to Transactions."
        }
    ));

    map.put("autoBids", page(
        "Auto Bids",
        "Manage automated bidding rules, maximum limits, increments, and pause or resume controls.",
        "Auto Bid Controls",
        "Auto bids are bidding rules, not just history. Keep max limit, current price, " +
            "increment, warning threshold, and controls visible.",
        new String[]{"03", "01", "02", "01"},
        new String[]{"Active Rules", "Paused", "Near Limit", "Limit Reached"},
        new String[]{"Rule table", "Limit safety", "Pause / resume"},
        new String[]{
            "Show each automated rule beside the related auction item.",
            "Warn when current price approaches the configured max bid.",
            "Allow quick Edit, Pause, Resume, or Delete actions."
        },
        new String[]{
            "MacBook Pro M3 auto bid is near its max limit.",
            "Vintage Camera auto bid is paused by user.",
            "Mechanical Keyboard auto bid ended with a winning result.",
            "Safety warning threshold is currently 80% of max limit."
        }
    ));

    map.put("myItems", page(
        "My Items",
        "Manage seller listings, drafts, active auctions, sold items, and relist actions.",
        "Seller Workspace",
        "Seller management stays practical with item thumbnails, listing status, bids, " +
            "watchers, countdown, winner, and context actions.",
        new String[]{"14", "05", "03", "06"},
        new String[]{"Items", "Drafts", "Active Sales", "Sold"},
        new String[]{"Listing status", "Auction linkage", "Seller actions"},
        new String[]{
            "Draft, Pending, Active, Sold, and Unsold items are separated by filter chips.",
            "Each item shows whether it has bids, watchers, or a winner follow-up task.",
            "Actions change by state: Edit, Publish, View Bids, Contact Winner, Mark Shipped, " +
                "or Relist."
        },
        new String[]{
            "Leather Backpack draft needs image and starting price.",
            "Mechanical Keyboard is sold and ready to ship.",
            "Wireless Mouse has 11 watchers but no bids yet.",
            "Abstract Painting can be relisted after no winning bid."
        }
    ));

    map.put("winners", page(
        "Transactions",
        "Follow auctions you won and auctions you sold after bidding ends.",
        "Post-Auction Transactions",
        "Transactions split bidder and seller follow-up: Won Auctions for purchases, Sold " +
            "Auctions for winners of your listings.",
        new String[]{"06", "02", "03", "01"},
        new String[]{"Won Auctions", "Payment Due", "Sold Auctions", "To Ship"},
        new String[]{"Won auctions", "Sold auctions", "Fulfilment"},
        new String[]{
            "Won Auctions track final price, seller, payment, and pickup or shipping status.",
            "Sold Auctions track winner, winning bid, payment status, and seller fulfilment.",
            "Actions stay clear: Pay Now, Contact Seller, Contact Winner, Mark Shipped, or " +
                "Leave Review."
        },
        new String[]{
            "Canon EOS M50 is waiting for payment.",
            "Mechanical Keyboard winner has paid and needs shipping.",
            "Signed Art Print transaction is completed.",
            "Vintage Camera invoice is ready to download."
        }
    ));

    map.put("settings", page(
        "Settings",
        "Manage account details, notifications, bidding preferences, seller profile, payments, " +
            "and app preferences.",
        "Settings Workspace",
        "Settings should be grouped forms, not auction tables: account, notifications, " +
            "bidding, seller profile, payment, privacy, and preferences.",
        new String[]{"07", "08", "04", "03"},
        new String[]{"Groups", "Alerts", "Bid Rules", "Preferences"},
        new String[]{"Account", "Notifications", "Bidding preferences"},
        new String[]{
            "Profile, email, phone number, avatar, and password change belong in Account.",
            "Outbid, ending soon, won auction, payment, new bid, and auto-bid limit alerts " +
                "belong in Notifications.",
            "Default increment, quick bid confirmation, auto-bid threshold, currency, " +
                "timezone, and default view belong in Preferences."
        },
        new String[]{
            "Outbid and ending-soon alerts are enabled.",
            "Confirm before placing bid is enabled.",
            "Default auction view is Grid/List hybrid.",
            "Currency is set to VND."
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

  private List<CategoryData> buildCategories() {
    List<CategoryData> list = new ArrayList<>();
    list.add(category("Electronics", "Phones, laptops, cameras, audio devices", "5 live", "EL"));
    list.add(category("Art", "Paintings, prints, sculpture, handmade items", "4 live", "AR"));
    list.add(category("Vehicle", "Bikes, motorbikes, cars, vehicle accessories", "3 live", "VE"));
    return list;
  }

  private List<AuctionCardData> buildAuctionCards() {
    List<AuctionCardData> list = new ArrayList<>();
    list.add(auction(
        "MacBook Pro M3",
        "Electronics",
        "32,500,000 VND",
        "18 bids",
        "42m",
        "Hot",
        "/client/images/overlay2.jpg",
        "Seller minh.seller - high value laptop auction with active auto bids."
    ));
    list.add(auction(
        "Vintage Camera",
        "Electronics",
        "4,600,000 VND",
        "21 bids",
        "38m",
        "Ending Soon",
        "/client/images/overlay3.jpg",
        "Camera auction with strong demand and quick final-minute pressure."
    ));
    list.add(auction(
        "Wireless Headset",
        "Electronics",
        "1,100,000 VND",
        "5 bids",
        "1d",
        "Running",
        "/client/images/overlay5.jpg",
        "Audio item with quick bid increment and shipping support."
    ));
    list.add(auction(
        "Mechanical Keyboard",
        "Electronics",
        "2,400,000 VND",
        "9 bids",
        "6h",
        "Watched",
        "/client/images/overlay7.jpg",
        "Watched item with current leading bid and compact bid history."
    ));
    list.add(auction(
        "Smart Speaker",
        "Electronics",
        "1,250,000 VND",
        "7 bids",
        "8h",
        "No Reserve",
        "/client/images/overlay2.jpg",
        "Electronics auction with no reserve and high watcher count."
    ));
    list.add(auction(
        "Signed Art Print",
        "Art",
        "8,200,000 VND",
        "11 bids",
        "3h",
        "No Reserve",
        "/client/images/overlay4.jpg",
        "No-reserve art listing with gallery preview and verified seller note."
    ));
    list.add(auction(
        "Abstract Painting",
        "Art",
        "3,000,000 VND",
        "2 bids",
        "1d",
        "Running",
        "/client/images/overlay.jpg",
        "Painting listing with preview image and artist note."
    ));
    list.add(auction(
        "Ceramic Sculpture",
        "Art",
        "5,900,000 VND",
        "13 bids",
        "2h",
        "Ending Soon",
        "/client/images/overlay4.jpg",
        "Art auction with a detailed preview image and closing pressure."
    ));
    list.add(auction(
        "Digital Artwork Print",
        "Art",
        "1,750,000 VND",
        "6 bids",
        "22h",
        "Watched",
        "/client/images/bg2.jpg",
        "Watched art print with seller notes and shipping support."
    ));
    list.add(auction(
        "City Bike",
        "Vehicle",
        "2,800,000 VND",
        "6 bids",
        "5h",
        "Ending Soon",
        "/client/images/overlay3.jpg",
        "Vehicle listing with pickup information and bidder questions."
    ));
    list.add(auction(
        "Electric Scooter",
        "Vehicle",
        "7,500,000 VND",
        "10 bids",
        "1d",
        "Hot",
        "/client/images/overlay6.jpg",
        "Vehicle auction with active watchers and pickup arrangement."
    ));
    list.add(auction(
        "Vintage Motorbike",
        "Vehicle",
        "18,000,000 VND",
        "16 bids",
        "2d",
        "Running",
        "/client/images/overlay5.jpg",
        "Vehicle auction with seller inspection notes and bidder questions."
    ));
    return list;
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
    return new AuctionCardData(title, category, price, bids, endsIn, badge, imagePath, detail);
  }

  private String getDefaultFilter(String sectionKey) {
    return switch (sectionKey) {
      case "dashboard" -> "Overview";
      case "settings" -> "Account";
      default -> "All";
    };
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
      case "myItems" -> renderMyItems(filter);
      case "winners" -> renderTransactions(filter);
      case "settings" -> renderSettings(filter);
      default -> renderDashboard(filter);
    }
  }

  private void renderDashboard(String filter) {
    setWorkspaceTitle("Action Needed");
    renderChips(filter, "Overview", "Needs Action", "Ending Soon", "Seller", "Auto Bid");

    addHeader("Item / Task", "Value", "Time / Signal");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "Vintage Camera",
        "My bid 4,300,000 VND - current price moved above your bid",
        "4,600,000 VND",
        "Ends in 38m",
        "Outbid",
        "You were outbid. Place a higher bid or configure auto bid before the auction closes.",
        "VC",
        "Bid Again",
        "View"
    ));
    rows.add(row(
        "MacBook Pro M3",
        "Winning now - auto bid active until 35,000,000 VND",
        "32,500,000 VND",
        "Ends in 42m",
        "Winning",
        "You are currently leading. Watch closing pressure and bid history before the final " +
            "minutes.",
        "MB",
        "View",
        "Auto Bid"
    ));
    rows.add(row(
        "Canon EOS M50",
        "Won auction - seller camera.store - payment required",
        "5,200,000 VND",
        "Due today",
        "Payment Due",
        "This won auction should move through payment before fulfilment can continue.",
        "CE",
        "Pay Now",
        "Contact"
    ));
    rows.add(row(
        "Mechanical Keyboard",
        "Sold item - winner thanh.user has paid",
        "2,400,000 VND",
        "Ship today",
        "To Ship",
        "Winner payment is complete. Seller should prepare shipping or pickup confirmation.",
        "MK",
        "Ship",
        "Details"
    ));

    addFilteredRows(rows, filter);
  }

  private void renderAuctions(String filter) {
    setWorkspaceTitle("All Auctions");
    renderChips(filter, "All", "Running", "Ending Soon", "Hot", "No Reserve", "Watched");

    HBox browseHeader = new HBox(10);
    browseHeader.setAlignment(Pos.CENTER_RIGHT);
    browseHeader.getStyleClass().add("browse-header");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Label sortLabel = new Label("Sort: Ending Soon");
    sortLabel.getStyleClass().add("sort-pill");

    browseHeader.getChildren().addAll(spacer, sortLabel);
    workspaceBox.getChildren().add(browseHeader);

    List<AuctionCardData> filtered = filterAuctionCards(filter);
    int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) AUCTIONS_PER_PAGE));
    auctionPage = Math.max(1, Math.min(auctionPage, totalPages));

    int fromIndex = (auctionPage - 1) * AUCTIONS_PER_PAGE;
    int toIndex = Math.min(fromIndex + AUCTIONS_PER_PAGE, filtered.size());

    GridPane productGrid = createThreeColumnGrid("auction-grid");

    if (filtered.isEmpty()) {
      addGridCell(productGrid, emptyCard("No auctions found for filter: " + filter), 0);
    } else {
      int index = 0;
      for (AuctionCardData card : filtered.subList(fromIndex, toIndex)) {
        addGridCell(productGrid, buildAuctionProductCard(card), index++);
      }
    }

    workspaceBox.getChildren().add(productGrid);
    workspaceBox.getChildren().add(buildPagination(totalPages, filtered.size()));

    workspaceBox.getChildren().add(sectionHeader("Shop by Category", ""));

    GridPane categoryGrid = createThreeColumnGrid("category-grid");
    for (int index = 0; index < categories.size(); index++) {
      addGridCell(categoryGrid, buildCategoryCard(categories.get(index)), index);
    }

    workspaceBox.getChildren().add(categoryGrid);
  }

  private void renderMyBids(String filter) {
    setWorkspaceTitle("My Bid Tracking");
    renderChips(filter, "All", "Winning", "Outbid", "Won", "Lost", "Ending Soon");

    addHeader("Auction", "My Bid", "Current / Ends");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "MacBook Pro M3",
        "Latest bid 32,500,000 VND - max auto bid 35,000,000 VND",
        "32,500,000 VND",
        "32,500,000 VND - 42m",
        "Winning",
        "You are currently leading. Keep watching or edit the max auto bid limit.",
        "MB",
        "View",
        "Edit Auto"
    ));
    rows.add(row(
        "Vintage Camera",
        "Latest bid 4,300,000 VND - current price is higher",
        "4,300,000 VND",
        "4,600,000 VND - 38m",
        "Outbid",
        "You need to bid again if you still want this item.",
        "VC",
        "Bid Again",
        "View"
    ));
    rows.add(row(
        "Canon EOS M50",
        "Final winning bid accepted",
        "5,200,000 VND",
        "Won today",
        "Won",
        "Auction is won and should be handled in Transactions for payment.",
        "CE",
        "Pay Now",
        "View"
    ));
    rows.add(row(
        "Gaming Chair",
        "Final bid 1,900,000 VND - another bidder won",
        "1,900,000 VND",
        "Closed",
        "Lost",
        "This auction is closed. Use View for bid history only.",
        "GC",
        "View"
    ));

    addFilteredRows(rows, filter);
  }

  private void renderAutoBids(String filter) {
    setWorkspaceTitle("Auto Bid Rules");
    renderChips(filter, "All", "Active", "Paused", "Near Limit", "Limit Reached", "Ended");

    addHeader("Auction Rule", "Current / Max", "Increment");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "MacBook Pro M3",
        "Auto bid active - stop at 35,000,000 VND - threshold 80%",
        "32.5M / 35M",
        "+500,000 VND",
        "Near Limit",
        "Current price is close to max limit. Edit max bid or watch manually.",
        "MB",
        "Edit",
        "Pause"
    ));
    rows.add(row(
        "Vintage Camera",
        "Auto bid paused by user - can resume before close",
        "4.6M / 5M",
        "+100,000 VND",
        "Paused",
        "Paused auto bid will not respond to new bids until resumed.",
        "VC",
        "Resume",
        "Edit"
    ));
    rows.add(row(
        "Signed Art Print",
        "Auto bid active - no reserve auction",
        "8.2M / 10M",
        "+200,000 VND",
        "Active",
        "Auto bid rule is healthy and still below warning threshold.",
        "AP",
        "Edit",
        "Pause"
    ));
    rows.add(row(
        "Mechanical Keyboard",
        "Auto bid ended after auction close",
        "2.4M / 2.5M",
        "+50,000 VND",
        "Ended",
        "Rule is ended and the transaction should be tracked after close.",
        "MK",
        "View"
    ));

    addFilteredRows(rows, filter);
  }

  private void renderMyItems(String filter) {
    setWorkspaceTitle("Seller Listings");
    renderChips(filter, "All", "Draft", "Pending", "Active", "Sold", "Unsold");

    addHeader("Item", "Price / Bids", "Watchers / Ends");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "Mechanical Keyboard",
        "Sold through AUC-0977 - winner thanh.user",
        "2,400,000 VND",
        "9 bids - paid",
        "Sold",
        "Winner has paid. Prepare shipment or pickup confirmation.",
        "MK",
        "Ship",
        "Contact"
    ));
    rows.add(row(
        "Leather Backpack",
        "Draft listing - missing photo validation and starting price",
        "No price",
        "Draft",
        "Draft",
        "Complete required listing details before publishing or submitting for approval.",
        "LB",
        "Edit",
        "Publish"
    ));
    rows.add(row(
        "Wireless Mouse",
        "Active sale - 11 watchers but no bid yet",
        "450,000 VND",
        "11 watchers - 1d",
        "Active",
        "Consider lowering start price or promoting before the auction ends.",
        "WM",
        "View Bids",
        "Edit"
    ));
    rows.add(row(
        "Abstract Painting",
        "Ended without winner - eligible for relist",
        "3,000,000 VND",
        "0 bids - closed",
        "Unsold",
        "Relist with improved title, image, or starting price.",
        "AR",
        "Relist",
        "Edit"
    ));
    rows.add(row(
        "Vintage Watch",
        "Submitted for review before auction launch",
        "6,000,000 VND",
        "Pending",
        "Pending",
        "Item is waiting review before it can become ACTIVE.",
        "VW",
        "View"
    ));

    addFilteredRows(rows, filter);
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
    setWorkspaceTitle("Won Auctions / Sold Auctions");
    renderChips(
        filter,
        "All",
        "Won Auctions",
        "Sold Auctions",
        "Payment Due",
        "To Ship",
        "Completed"
    );

    addHeader("Transaction", "Final Price", "Next Step");

    List<UserRow> rows = new ArrayList<>();
    rows.add(row(
        "Won - Canon EOS M50",
        "Seller camera.store - invoice ready",
        "5,200,000 VND",
        "Pay today",
        "Payment Due",
        "Bidder flow: pay the final price, then track shipping or pickup.",
        "CE",
        "Pay Now",
        "Contact Seller"
    ));
    rows.add(row(
        "Sold - Mechanical Keyboard",
        "Winner thanh.user - payment completed",
        "2,400,000 VND",
        "Ship today",
        "To Ship",
        "Seller flow: winner has paid, so mark shipped after fulfilment.",
        "MK",
        "Ship",
        "Contact Winner"
    ));
    rows.add(row(
        "Won - Signed Art Print",
        "Seller art.house - completed purchase",
        "8,200,000 VND",
        "Leave review",
        "Completed",
        "Transaction complete. User can leave seller review or download receipt.",
        "AP",
        "Review",
        "Receipt"
    ));
    rows.add(row(
        "Sold - Wireless Mouse",
        "Winner pending payment confirmation",
        "450,000 VND",
        "Payment check",
        "Payment Due",
        "Seller should wait for payment confirmation before shipment.",
        "WM",
        "View",
        "Reminder"
    ));

    addFilteredRows(rows, filter);
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
        "Outbid, ending soon, won auction, new bid, payment reminders",
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
        "App preferences should persist default grid/list and ending-soon sort.",
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
    VBox card = new VBox(8);
    card.getStyleClass().add("auction-product-card");
    card.setMinWidth(0);
    card.setMaxWidth(Double.MAX_VALUE);

    StackPane imageWrap = new StackPane();
    imageWrap.getStyleClass().add("product-image-wrap");
    imageWrap.setMinWidth(0);
    imageWrap.setPrefHeight(PRODUCT_IMAGE_HEIGHT);
    imageWrap.setMinHeight(PRODUCT_IMAGE_HEIGHT);
    imageWrap.setMaxHeight(PRODUCT_IMAGE_HEIGHT);
    imageWrap.setMaxWidth(Double.MAX_VALUE);

    Rectangle wrapClip = new Rectangle();
    wrapClip.widthProperty().bind(imageWrap.widthProperty());
    wrapClip.heightProperty().bind(imageWrap.heightProperty());
    wrapClip.setArcWidth(28);
    wrapClip.setArcHeight(28);
    imageWrap.setClip(wrapClip);

    Image image = getCachedImage(data.imagePath);
    if (image != null && !image.isError()) {
      ImageView imageView = new ImageView(image);
      imageView.getStyleClass().add("product-image");
      imageView.setSmooth(true);
      imageView.setCache(true);
      imageView.setPreserveRatio(false);
      imageView.setFitWidth(PRODUCT_IMAGE_INITIAL_WIDTH);
      imageView.setFitHeight(PRODUCT_IMAGE_HEIGHT);
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
      imageWrap.getChildren().add(
          buildThumbnail(data.title.substring(0, Math.min(2, data.title.length())).toUpperCase())
      );
    }

    Label badge = new Label(data.badge);
    badge.getStyleClass().add("product-badge");
    badge.getStyleClass().add(statusStyle(data.badge));
    StackPane.setAlignment(badge, Pos.TOP_LEFT);
    imageWrap.getChildren().add(badge);

    Label category = new Label(data.category);
    category.getStyleClass().add("product-category");

    Label title = new Label(data.title);
    title.getStyleClass().add("product-title");
    title.setWrapText(true);

    Label price = new Label(data.price);
    price.getStyleClass().add("product-price");

    HBox meta = new HBox(8);
    meta.setAlignment(Pos.CENTER_LEFT);
    Label bids = new Label(data.bids);
    bids.getStyleClass().add("product-meta");
    Label ends = new Label("Ends in " + data.endsIn);
    ends.getStyleClass().add("product-meta");
    meta.getChildren().addAll(bids, ends);

    HBox actions = new HBox(7);
    actions.setAlignment(Pos.CENTER_LEFT);
    Button bid = new Button("Bid Now");
    bid.setMnemonicParsing(false);
    bid.getStyleClass().add("mini-action-btn");
    bid.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(bid, Priority.ALWAYS);
    bid.setOnAction(event -> showTemporaryDetail("Bid Now - " + data.title, data.detail));

    Button view = new Button("View");
    view.setMnemonicParsing(false);
    view.getStyleClass().add("mini-action-btn");
    view.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(view, Priority.ALWAYS);
    view.setOnAction(event -> showTemporaryDetail(data.title, data.detail));
    actions.getChildren().addAll(bid, view);

    card.getChildren().addAll(imageWrap, category, title, price, meta, actions);
    return card;
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
      if (getClass().getResource(imagePath) == null) {
        return null;
      }

      return new Image(getClass().getResource(imagePath).toExternalForm(), false);
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

  private List<AuctionCardData> filterAuctionCards(String filter) {
    List<AuctionCardData> filtered = new ArrayList<>();
    String normalizedFilter = normalize(filter);

    for (AuctionCardData card : auctionCards) {
      String haystack = normalize(card.title + " " + card.category + " " + card.badge + " " +
          card.detail);

      if (isAllLikeFilter(filter)
          || haystack.contains(normalizedFilter)
          || normalize(card.category).equals(normalizedFilter)) {
        filtered.add(card);
      }
    }

    return filtered;
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

    if (normalizedFilter.equals("needs action")) {
      return haystack.contains("outbid")
          || haystack.contains("payment due")
          || haystack.contains("to ship")
          || haystack.contains("near limit")
          || haystack.contains("limit reached");
    }

    if (normalizedFilter.equals("ending soon")) {
      return haystack.contains("ending soon")
          || haystack.contains("ends in")
          || haystack.contains("42m")
          || haystack.contains("38m");
    }

    if (normalizedFilter.equals("won auctions")) {
      return normalize(row.title).startsWith("won")
          || haystack.contains("won auction")
          || normalize(row.status).equals("won");
    }

    if (normalizedFilter.equals("sold auctions")) {
      return normalize(row.title).startsWith("sold")
          || haystack.contains("sold auction")
          || normalize(row.status).equals("sold")
          || normalize(row.status).equals("to ship");
    }

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
        || normalized.contains("ready")
        || normalized.contains("hot")
        || normalized.contains("watched")) {
      return "status-good";
    }

    if (normalized.contains("outbid")
        || normalized.contains("payment due")
        || normalized.contains("near limit")
        || normalized.contains("pending")
        || normalized.contains("to ship")
        || normalized.contains("ending soon")
        || normalized.contains("no reserve")) {
      return "status-warn";
    }

    if (normalized.contains("lost")
        || normalized.contains("limit reached")
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
   * Renders the Create Listing form inside the existing user dashboard workspace.
   *
   * <p>This keeps sidebar navigation visible and follows the project flow: seller creates an
   * item, admin reviews it, then admin creates the auction session from the approved item.</p>
   */
  private void renderCreateItemForm() {
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
    if (userActionBar != null) {
      userActionBar.getChildren().clear();
      userActionBar.setVisible(false);
      userActionBar.setManaged(false);
    }
    pendingCreateItemUploads.clear();
    pendingCreateItemPreviewIndex = 0;

    VBox formShell = new VBox(16);
    formShell.getStyleClass().add("create-listing-shell");
    formShell.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
    formShell.setMaxWidth(Double.MAX_VALUE);

    Label formTitle = new Label("Create Listing");
    formTitle.getStyleClass().add("create-listing-title");
    formTitle.setStyle("-fx-text-fill: #1f3e37; -fx-font-size: 24px; -fx-font-weight: bold;");

    Label formNote = new Label("Submit an item for admin review. Approved items become "
        + "available for auction creation.");
    formNote.getStyleClass().add("create-listing-note");
    formNote.setStyle("-fx-text-fill: #738581; -fx-font-size: 11px;");
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
    uploadZone.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8fbf8, #eef5ef); "
        + "-fx-background-radius: 18; -fx-border-color: rgba(39, 75, 69, 0.34); "
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
    uploadIcon.setStyle("-fx-background-color: #fbfaf6; -fx-background-radius: 12; "
        + "-fx-text-fill: #294941; -fx-font-size: 18px; -fx-font-weight: bold; "
        + "-fx-padding: 7 12 7 12;");

    Label uploadHint = new Label("Drop your files here or browse");
    uploadHint.getStyleClass().add("create-upload-title");
    uploadHint.setStyle("-fx-text-fill: #294941; -fx-font-size: 12px; -fx-font-weight: bold;");

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
    previewImageWrap.setStyle("-fx-background-color: #fbfaf6; -fx-background-radius: 18; "
        + "-fx-border-color: #dbe7df; -fx-border-radius: 18; -fx-padding: 0;");
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
    previewName.setStyle("-fx-text-fill: #22433b; -fx-font-size: 17px; -fx-font-weight: bold;");
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
    TextField priceField = createFormField("Starting price, e.g. 2500000");
    TextField sizeField = createFormField("Size / condition");

    ComboBox<String> currencyBox = new ComboBox<>();
    currencyBox.getStyleClass().add("create-combo-box");
    currencyBox.setStyle(CREATE_COMBO_STYLE);
    currencyBox.setMinWidth(0);
    currencyBox.setMaxWidth(Double.MAX_VALUE);
    currencyBox.setMinHeight(50);
    currencyBox.setPrefHeight(50);
    currencyBox.setMaxHeight(50);
    currencyBox.setVisibleRowCount(2);
    currencyBox.getItems().setAll("VND", "USD");
    currencyBox.getSelectionModel().select("VND");

    TextArea descriptionArea = new TextArea();
    descriptionArea.setPromptText("Description, condition, provenance, and notes for admin "
        + "review...");
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

    GridPane secondaryFields = new GridPane();
    secondaryFields.setHgap(18);
    secondaryFields.setMaxWidth(Double.MAX_VALUE);
    for (int column = 0; column < 2; column++) {
      ColumnConstraints constraints = new ColumnConstraints();
      constraints.setPercentWidth(50);
      constraints.setHgrow(Priority.ALWAYS);
      constraints.setFillWidth(true);
      secondaryFields.getColumnConstraints().add(constraints);
    }
    VBox sizeFieldBox = fieldBox("Size / Condition", sizeField);
    VBox currencyFieldBox = fieldBox("Currency", currencyBox);
    secondaryFields.add(sizeFieldBox, 0, 0);
    secondaryFields.add(currencyFieldBox, 1, 0);
    GridPane.setHgrow(sizeFieldBox, Priority.ALWAYS);
    GridPane.setHgrow(currencyFieldBox, Priority.ALWAYS);

    Label messageLabel = new Label("");
    messageLabel.getStyleClass().add("create-message");
    messageLabel.setStyle(CREATE_MUTED_TEXT_STYLE);

    Button saveDraft = new Button("Save Draft");
    saveDraft.setMnemonicParsing(false);
    saveDraft.getStyleClass().add("create-secondary-btn");
    saveDraft.setStyle(CREATE_SECONDARY_BUTTON_STYLE);

    Button submitItem = new Button("Submit Item");
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
        sizeField, currencyBox,
        saveDraft, submitItem, messageLabel
    ));

    submitItem.setOnAction(event -> submitCreateItem(
        false, categoryBox, titleField, descriptionArea, priceField,
        sizeField, currencyBox,
        saveDraft, submitItem, messageLabel
    ));

    detailsPanel.getChildren().addAll(
        detailsTitle,
        primaryFields,
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
    label.setStyle("-fx-text-fill: #294941; -fx-font-size: 11px; -fx-font-weight: bold;");
    box.getChildren().addAll(label, input);
    return box;
  }

  private void chooseItemImages(
      Label selectedFileLabel,
      ImageView previewImage,
      Label previewPlaceholder,
      Label imageCounterLabel,
      Label messageLabel,
      VBox fileListBox,
      ScrollPane fileListScroll) {
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

    File currentDirectory = new File(System.getProperty("user.home"));
    if (currentDirectory.exists()) {
      chooser.setInitialDirectory(currentDirectory);
    }

    List<File> selectedFiles = chooser.showOpenMultipleDialog(workspaceBox.getScene().getWindow());
    if (selectedFiles == null || selectedFiles.isEmpty()) {
      return;
    }

    try {
      List<File> acceptedFiles = selectedFiles.size() > remainingSlots
          ? new ArrayList<>(selectedFiles.subList(0, remainingSlots))
          : selectedFiles;
      Path uploadRoot = Path.of(System.getProperty("user.home"), ".auction-system", "uploads");
      Files.createDirectories(uploadRoot);

      long batch = System.currentTimeMillis();
      int index = 0;
      for (File file : acceptedFiles) {
        String safeName = file.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = uploadRoot.resolve(batch + "_" + index + "_" + safeName);
        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        pendingCreateItemUploads.add(new CreateItemUpload(
            target.toUri().toString(),
            file.getName(),
            file.length()
        ));
        index++;
      }

      if (pendingCreateItemPreviewIndex < 0
          || pendingCreateItemPreviewIndex >= pendingCreateItemUploads.size()) {
        pendingCreateItemPreviewIndex = 0;
      }
      if (pendingCreateItemUploads.size() == acceptedFiles.size()) {
        pendingCreateItemPreviewIndex = 0;
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
      showCreateMessage(messageLabel, selectedFiles.size() > remainingSlots
          ? "Chỉ thêm được " + acceptedFiles.size() + " ảnh vì mỗi listing tối đa 5 ảnh."
          : "Đã thêm " + acceptedFiles.size()
              + " ảnh. Có thể browse tiếp nếu chưa đủ 5 ảnh.", false);
    } catch (IOException exception) {
      showCreateMessage(messageLabel, "Không thể lưu ảnh đã chọn.", true);
      exception.printStackTrace();
    }
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
      fileRow.setStyle("-fx-background-color: #fbfaf6; -fx-background-radius: 12; "
          + "-fx-border-color: #dfe8e1; -fx-border-radius: 12; -fx-padding: 8 10 8 10;");

      Label typeBadge = new Label(getFileExtension(upload.fileName));
      typeBadge.getStyleClass().add("create-file-type");
      typeBadge.setStyle("-fx-background-color: #f1f1ee; -fx-background-radius: 8; "
          + "-fx-border-color: #e1e1dc; -fx-border-radius: 8; -fx-text-fill: #5e6f68; "
          + "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 8 9 8 9;");

      Label fileName = new Label(upload.fileName);
      fileName.getStyleClass().add("create-file-name");
      fileName.setTextOverrun(OverrunStyle.ELLIPSIS);
      fileName.setMaxWidth(Double.MAX_VALUE);
      fileName.setStyle("-fx-text-fill: #294941; -fx-font-size: 12px; -fx-font-weight: bold;");

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
      removeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #294941; "
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
    Image image = new Image(pendingCreateItemUploads.get(pendingCreateItemPreviewIndex).uri, true);
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
      uris.add(upload.uri);
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
                  ComboBox<String> currencyBox,
                  Button saveDraftButton,
                  Button submitItemButton,
                  Label messageLabel) {
    String title = safeTrim(titleField.getText());
    String category = categoryBox.getValue() == null ? "" : categoryBox.getValue().trim();
    BigDecimal price = parseCreateItemPrice(priceField.getText());

    if (category.isBlank()) {
      showCreateMessage(messageLabel, "Vui lòng chọn category cho item.", true);
      return;
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

    String payload = fields(
        sellerId,
        category,
        title,
        safeTrim(descriptionArea.getText()),
        price.toPlainString(),
        draftMode ? "DRAFT" : "PENDING_REVIEW",
        "Timed Auction",
        safeTrim(sizeField.getText()),
        "",
        "",
        currency,
        pendingCreateItemImagePayload()
    );

    showCreateMessage(
        messageLabel,
        draftMode ? "Đang lưu draft item..." : "Đang submit item lên Pending Approval...",
        false
    );
    createItemNetworkManager.send("CREATE_ITEM " + payload);
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

    if (message.startsWith("CREATE_ITEM_SUCCESS")) {
      requestCreateListingMetadata();
      showTemporaryDetail(
          "Create Listing",
          "Tạo item thành công. Nếu submit item, admin sẽ thấy nó trong Pending Approval."
      );
      if ("myItems".equals(currentSectionKey)) {
        showSection("myItems");
      }
      return;
    }

    if (message.startsWith("CREATE_ITEM_FAIL")) {
      showTemporaryDetail("Create Listing failed", message);
    }
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
    setLabelText(statLabel3, "Active Sales");
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

  private String fields(Object... values) {
    List<String> encoded = new ArrayList<>();
    for (Object value : values) {
      encoded.add(encodeField(value));
    }
    return String.join("|", encoded);
  }

  /**
   * Escapes values for the simple socket protocol used by ClientHandler.
   *
   * <p>Keep this aligned with ClientHandler#splitPayload. It allows descriptions and
   * image URI lists to contain spaces, pipes, and new lines without breaking parsing.</p>
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

  private void showTemporaryDetail(String title, String description) {
    if (surfaceTitleLabel != null) {
      surfaceTitleLabel.setText(title);
    }
  }

  private static class CreateItemUpload {
    private final String uri;
    private final String fileName;
    private final long sizeBytes;

    private CreateItemUpload(String uri, String fileName, long sizeBytes) {
      this.uri = uri;
      this.fileName = fileName;
      this.sizeBytes = sizeBytes;
    }
  }

  private static class CategoryData {
    private final String title;
    private final String description;
    private final String count;
    private final String initials;

    private CategoryData(String title, String description, String count, String initials) {
      this.title = title;
      this.description = description;
      this.count = count;
      this.initials = initials;
    }
  }

  private static class AuctionCardData {
    private final String title;
    private final String category;
    private final String price;
    private final String bids;
    private final String endsIn;
    private final String badge;
    private final String imagePath;
    private final String detail;

    private AuctionCardData(
        String title,
        String category,
        String price,
        String bids,
        String endsIn,
        String badge,
        String imagePath,
        String detail) {
      this.title = title;
      this.category = category;
      this.price = price;
      this.bids = bids;
      this.endsIn = endsIn;
      this.badge = badge;
      this.imagePath = imagePath;
      this.detail = detail;
    }
  }

  private static class UserRow {
    private final String title;
    private final String meta;
    private final String firstValue;
    private final String secondValue;
    private final String status;
    private final String detail;
    private final String thumbnail;
    private final String[] actions;

    private UserRow(
        String title,
        String meta,
        String firstValue,
        String secondValue,
        String status,
        String detail,
        String thumbnail,
        String... actions) {
      this.title = title;
      this.meta = meta;
      this.firstValue = firstValue;
      this.secondValue = secondValue;
      this.status = status;
      this.detail = detail;
      this.thumbnail = thumbnail;
      this.actions = actions;
    }
  }
}
