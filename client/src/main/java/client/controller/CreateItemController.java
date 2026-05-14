package client.controller;

import client.SceneNavigator;
import client.model.User;
import client.service.NetworkManager;
import client.service.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller cho màn Create Item của seller.
 *
 * <p>Màn này được mở từ nút {@code Create Listing} ở user-home. Nó cho user nhập
 * thông tin item, chọn ảnh, lưu draft hoặc submit lên admin review. Khi submit,
 * controller encode payload và gửi command {@code CREATE_ITEM} tới server.</p>
 */
public class CreateItemController {

  private static final DecimalFormat PREVIEW_PRICE = new DecimalFormat("#,##0.##");

  @FXML private Label sellerNameLabel;
  @FXML private Label sellerRoleLabel;
  @FXML private Label sellerInitialsLabel;
  @FXML private Label breadcrumbLabel;
  @FXML private TextField titleField;
  @FXML private TextField priceField;
  @FXML private TextField sizeField;
  @FXML private TextField propertyField;
  @FXML private TextField royaltyField;
  @FXML private TextField currencyField;
  @FXML private TextArea descriptionArea;
  @FXML private RadioButton fixedPriceRadio;
  @FXML private RadioButton timedAuctionRadio;
  @FXML private RadioButton openForBidsRadio;
  @FXML private ImageView previewImageView;
  @FXML private Label previewTitleLabel;
  @FXML private Label previewSellerLabel;
  @FXML private Label previewPriceLabel;
  @FXML private Label previewStatusLabel;
  @FXML private Label fileNameLabel;
  @FXML private Label helperTextLabel;
  @FXML private Label messageLabel;
  @FXML private Button saveDraftButton;
  @FXML private Button submitItemButton;

  private final ToggleGroup purchaseTypeGroup = new ToggleGroup();
  private final List<Path> persistedImagePaths = new ArrayList<>();
  private NetworkManager networkManager;

  /**
   * Khởi tạo form, preview realtime và kết nối network riêng cho màn create item.
   */
  @FXML
  public void initialize() {
    PREVIEW_PRICE.setGroupingUsed(true);
    setupPurchaseTypeGroup();
    bindPreview();
    loadUserMeta();

    networkManager = new NetworkManager();
    networkManager.setMessageHandler(this::handleServerMessage);
  }

  /**
   * Gom ba radio button purchase type vào một group để chỉ chọn được một loại listing.
   */
  private void setupPurchaseTypeGroup() {
    fixedPriceRadio.setToggleGroup(purchaseTypeGroup);
    timedAuctionRadio.setToggleGroup(purchaseTypeGroup);
    openForBidsRadio.setToggleGroup(purchaseTypeGroup);
    timedAuctionRadio.setSelected(true);
  }

  /**
   * Đồng bộ dữ liệu form sang preview card ở panel phải.
   *
   * <p>Đây chỉ là preview UI, dữ liệu thật vẫn được validate và gửi trong
   * {@link #submitCreateItem(boolean)}.</p>
   */
  private void bindPreview() {
    titleField.textProperty().addListener((obs, oldValue, newValue) -> previewTitleLabel.setText(fallback(newValue, "Untitled listing")));
    priceField.textProperty().addListener((obs, oldValue, newValue) -> previewPriceLabel.setText(formatPreviewPrice(newValue)));
    currencyField.textProperty().addListener((obs, oldValue, newValue) -> previewPriceLabel.setText(formatPreviewPrice(priceField.getText())));
    descriptionArea.textProperty().addListener((obs, oldValue, newValue) -> previewStatusLabel.setText(isBlank(newValue) ? "Waiting for review" : "Ready to submit"));
  }

  /**
   * Đọc user hiện tại từ {@link SessionManager} để hiển thị seller meta và preview seller.
   */
  private void loadUserMeta() {
    User currentUser = SessionManager.getCurrentUser();
    if (currentUser == null) {
      sellerNameLabel.setText("Guest User");
      sellerRoleLabel.setText("SELLER");
      sellerInitialsLabel.setText("GU");
      previewSellerLabel.setText("Guest User");
      breadcrumbLabel.setText("Dashboard / Create Item");
      return;
    }

    String username = fallback(currentUser.getUsername(), "Seller");
    sellerNameLabel.setText(username);
    sellerRoleLabel.setText(currentUser.getSystemRole() == null ? "USER" : currentUser.getSystemRole().name());
    sellerInitialsLabel.setText(buildInitials(username));
    previewSellerLabel.setText(username);
    breadcrumbLabel.setText("Dashboard / My Items / Create Item");
  }

  /**
   * Cho seller chọn một hoặc nhiều ảnh item.
   *
   * <p>Ảnh được copy vào thư mục local {@code ~/.auction-system/uploads} trước khi
   * submit. DB hiện lưu URI/đường dẫn ảnh trong bảng {@code item_images}; chưa upload
   * binary ảnh lên cloud storage.</p>
   */
  @FXML
  private void handleBrowseFiles() {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Choose item images");
    chooser.getExtensionFilters().add(
        new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp")
    );

    Stage stage = (Stage) submitItemButton.getScene().getWindow();
    List<File> files = chooser.showOpenMultipleDialog(stage);
    if (files == null || files.isEmpty()) {
      return;
    }

    try {
      persistedImagePaths.clear();
      persistedImagePaths.addAll(persistSelectedFiles(files));
      updatePreviewImage(persistedImagePaths.get(0));
      fileNameLabel.setText(files.size() == 1
          ? files.get(0).getName()
          : files.get(0).getName() + " +" + (files.size() - 1) + " more");
      helperTextLabel.setText("Images copied to local uploads storage and ready for submit.");
      showNeutralMessage("Ảnh item đã sẵn sàng để gửi cùng listing.");
    } catch (IOException e) {
      showErrorMessage("Không thể lưu ảnh đã chọn. Vui lòng thử lại.");
      e.printStackTrace();
    }
  }

  /**
   * Quay lại user-home và đóng network riêng của màn create item.
   */
  @FXML
  private void handleBack() {
    navigateToUserHome();
  }

  /**
   * Lưu item với status {@code DRAFT}; item chưa vào queue duyệt admin.
   */
  @FXML
  private void handleSaveDraft() {
    submitCreateItem(true);
  }

  /**
   * Submit item với status {@code PENDING_REVIEW}; admin-home sẽ thấy trong Pending Approval.
   */
  @FXML
  private void handleSubmitItem() {
    submitCreateItem(false);
  }

  /**
   * Validate form và gửi command tạo item tới server.
   *
   * <p>{@code draftMode = true} lưu item ở {@code DRAFT}. {@code draftMode = false}
   * submit item ở {@code PENDING_REVIEW} để admin-home thấy trong Pending Approval.</p>
   *
   * @param draftMode {@code true} khi bấm Save Draft, {@code false} khi bấm Submit Item
   */
  private void submitCreateItem(boolean draftMode) {
    String title = normalize(titleField.getText());
    String description = normalize(descriptionArea.getText());
    String price = normalize(priceField.getText());

    if (title.isBlank()) {
      showErrorMessage("Vui lòng nhập title cho item.");
      return;
    }

    BigDecimal parsedPrice = parsePrice(price);
    if (parsedPrice == null || parsedPrice.compareTo(BigDecimal.ZERO) < 0) {
      showErrorMessage("Price phải là số hợp lệ và không âm.");
      return;
    }

    int sellerId = SessionManager.getCurrentUser() == null ? 0 : SessionManager.getCurrentUser().getUserId();

    String payload = fields(
        String.valueOf(sellerId),
        title,
        description,
        parsedPrice.toPlainString(),
        draftMode ? "DRAFT" : "PENDING_REVIEW",
        selectedPurchaseType(),
        normalize(sizeField.getText()),
        normalize(propertyField.getText()),
        normalize(royaltyField.getText()),
        normalize(currencyField.getText()),
        joinImageUris()
    );

    saveDraftButton.setDisable(true);
    submitItemButton.setDisable(true);
    showNeutralMessage(draftMode ? "Đang lưu draft item..." : "Đang submit item lên pending approval...");
    networkManager.send("CREATE_ITEM " + payload);
  }

  /**
   * Đưa response socket về JavaFX thread trước khi đụng UI.
   *
   * @param message message từ server
   */
  private void handleServerMessage(String message) {
    Platform.runLater(() -> applyServerMessage(message));
  }

  /**
   * Nhận phản hồi từ server sau khi gửi {@code CREATE_ITEM}.
   *
   * <p>Thành công thì clear form để seller tạo tiếp item mới. Thất bại thì mở lại
   * button để user sửa dữ liệu và submit lại.</p>
   *
   * @param message message protocol từ server
   */
  private void applyServerMessage(String message) {
    if (message == null || message.isBlank()) {
      return;
    }

    if (message.startsWith("CREATE_ITEM_SUCCESS ")) {
      List<String> fields = splitPayload(message.substring("CREATE_ITEM_SUCCESS ".length()));
      String itemId = fields.isEmpty() ? "" : fields.get(0);
      String status = fields.size() > 1 ? fields.get(1) : "PENDING_REVIEW";
      showSuccessMessage("Tạo item thành công. Item #" + itemId + " đang ở trạng thái " + status + ".");
      saveDraftButton.setDisable(false);
      submitItemButton.setDisable(false);
      clearForm();
      return;
    }

    if (message.startsWith("CREATE_ITEM_FAIL")) {
      saveDraftButton.setDisable(false);
      submitItemButton.setDisable(false);
      showErrorMessage("Tạo item thất bại: " + message.replace("CREATE_ITEM_FAIL", "").trim());
    }
  }

  /**
   * Reset form sau khi tạo item thành công để seller có thể tạo listing tiếp theo.
   */
  private void clearForm() {
    titleField.clear();
    priceField.clear();
    sizeField.clear();
    propertyField.clear();
    royaltyField.clear();
    currencyField.clear();
    descriptionArea.clear();
    persistedImagePaths.clear();
    fileNameLabel.setText("No file selected yet");
    helperTextLabel.setText("PNG, JPG, JPEG, WEBP - stored locally before submit.");
    previewImageView.setImage(null);
    previewTitleLabel.setText("Untitled listing");
    previewPriceLabel.setText("Starting price: 0");
    previewStatusLabel.setText("Waiting for review");
    timedAuctionRadio.setSelected(true);
  }

  /**
   * Copy ảnh được chọn sang thư mục local ổn định trước khi gửi path lên server.
   *
   * <p>Điểm cần lưu ý: đây là local-file URI, phù hợp khi client/server chạy cùng môi
   * trường dev. Nếu deploy thật nhiều máy, cần thay bằng upload lên storage chung rồi
   * lưu URL public/internal vào {@code item_images.url}.</p>
   *
   * @param files danh sách file user chọn
   * @return danh sách path sau khi copy
   * @throws IOException nếu không tạo/copy được file
   */
  /**
   * Copy ảnh được chọn vào thư mục local upload của app.
   *
   * <p>Hiện tại DB lưu URI local của file. Nếu sau này có cloud storage, hàm này là điểm
   * nên thay bằng upload binary lên storage rồi trả về public URL.</p>
   *
   * @param files danh sách file người dùng chọn
   * @return danh sách path local đã copy
   * @throws IOException nếu không tạo/copy được file
   */
  private List<Path> persistSelectedFiles(List<File> files) throws IOException {
    List<Path> paths = new ArrayList<>();
    Path uploadRoot = Path.of(System.getProperty("user.home"), ".auction-system", "uploads");
    Files.createDirectories(uploadRoot);

    long batch = System.currentTimeMillis();
    for (File file : files) {
      String safeName = file.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
      Path target = uploadRoot.resolve(batch + "_" + safeName);
      Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
      paths.add(target);
    }
    return paths;
  }

  /**
   * Load ảnh đầu tiên vào preview card.
   *
   * @param imagePath path ảnh local đã copy
   */
  private void updatePreviewImage(Path imagePath) {
    try {
      Image image = new Image(imagePath.toUri().toString(), true);
      previewImageView.setImage(image);
    } catch (Exception ignored) {
      previewImageView.setImage(null);
    }
  }

  /**
   * Ghép danh sách ảnh thành một field payload.
   *
   * <p>Các URI được phân tách bằng newline rồi encode bằng {@link #encodeField(String)}
   * để không phá format {@code |}-separated của protocol.</p>
   *
   * @return chuỗi URI ảnh hoặc rỗng nếu chưa upload
   */
  private String joinImageUris() {
    if (persistedImagePaths.isEmpty()) {
      return "";
    }

    List<String> uris = new ArrayList<>();
    for (Path path : persistedImagePaths) {
      uris.add(path.toUri().toString());
    }
    return String.join("\n", uris);
  }

  /**
   * Map radio button đang chọn thành attribute {@code purchase_type} lưu ở item_attributes.
   *
   * @return tên purchase type
   */
  private String selectedPurchaseType() {
    if (fixedPriceRadio.isSelected()) {
      return "Fixed Price";
    }
    if (openForBidsRadio.isSelected()) {
      return "Open for Bids";
    }
    return "Timed Auction";
  }

  /**
   * Parse price text thành {@link BigDecimal}; cho phép user nhập dấu phẩy phân tách.
   *
   * @param value text từ input price
   * @return giá hợp lệ hoặc {@code null}
   */
  private BigDecimal parsePrice(String value) {
    try {
      return new BigDecimal(value.replace(",", "").trim());
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Format giá cho preview card, không ảnh hưởng dữ liệu gửi server.
   */
  private String formatPreviewPrice(String raw) {
    BigDecimal price = parsePrice(raw == null ? "" : raw);
    if (price == null) {
      return "Starting price: 0";
    }
    return "Starting price: " + PREVIEW_PRICE.format(price) + " " + fallback(normalize(currencyField.getText()), "VND");
  }

  /**
   * Điều hướng về user-home sau khi user bấm Back.
   */
  private void navigateToUserHome() {
    try {
      if (networkManager != null) {
        networkManager.disconnect();
      }
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/user-home.fxml"));
      Parent root = loader.load();
      Stage stage = (Stage) submitItemButton.getScene().getWindow();
      SceneNavigator.switchSceneKeepingWindow(stage, root, "User Home");
    } catch (Exception e) {
      showErrorMessage("Không thể quay lại user home.");
      e.printStackTrace();
    }
  }

  private void showNeutralMessage(String message) {
    messageLabel.setStyle("-fx-text-fill: #6a7f79;");
    messageLabel.setText(message);
  }

  private void showSuccessMessage(String message) {
    messageLabel.setStyle("-fx-text-fill: #224a40; -fx-font-weight: bold;");
    messageLabel.setText(message);
  }

  private void showErrorMessage(String message) {
    messageLabel.setStyle("-fx-text-fill: #a34f4f; -fx-font-weight: bold;");
    messageLabel.setText(message);
  }

  /**
   * Encode các field của command {@code CREATE_ITEM}.
   *
   * @param values field theo đúng thứ tự server đang parse
   * @return payload đã escape ký tự đặc biệt
   */
  private String fields(String... values) {
    List<String> encoded = new ArrayList<>();
    for (String value : values) {
      encoded.add(encodeField(value));
    }
    return String.join("|", encoded);
  }

  /**
   * Escape ký tự đặc biệt trong protocol tự định nghĩa.
   *
   * <p>Phải giữ đồng bộ với {@code ClientHandler.splitPayload(...)} phía server.</p>
   *
   * @param value field gốc
   * @return field đã escape
   */
  private String encodeField(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  /**
   * Decode response payload từ server.
   *
   * @param payload chuỗi response sau command status
   * @return danh sách field đã unescape
   */
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

  /**
   * Chuẩn hóa text input để tránh null và khoảng trắng thừa.
   */
  private String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Trả về fallback nếu value rỗng.
   */
  private String fallback(String value, String fallback) {
    return isBlank(value) ? fallback : value.trim();
  }

  /**
   * Kiểm tra chuỗi null/rỗng sau trim.
   */
  private boolean isBlank(String value) {
    return value == null || value.trim().isBlank();
  }

  /**
   * Tạo initials cho avatar seller trên topbar.
   */
  private String buildInitials(String username) {
    String[] tokens = username == null ? new String[0] : username.trim().split("\\s+");
    if (tokens.length == 0) {
      return "US";
    }
    if (tokens.length == 1) {
      String token = tokens[0];
      return token.length() >= 2 ? token.substring(0, 2).toUpperCase(Locale.ROOT) : token.toUpperCase(Locale.ROOT);
    }
    return (tokens[0].substring(0, 1) + tokens[tokens.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
  }
}

