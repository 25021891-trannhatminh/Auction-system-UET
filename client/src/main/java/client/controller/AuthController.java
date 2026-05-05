package client.controller;

import java.net.URL;

import client.MainApp;
import client.model.AccountStatus;
import client.model.SystemRole;
import client.model.User;
import client.service.AuthService;
import client.service.NetworkManager;
import client.service.SessionManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class AuthController {

    private static final double OVERLAY_DISTANCE = 430.0;
    private static final Duration SLIDE_DURATION = Duration.millis(520);

    private static final String OVERLAY_IMAGE_PATH = "/client/images/overlay.jpg";
    private static final String OVERLAY_IMAGE_FALLBACK_PATH = "/client/images/bg.png";

    private NetworkManager networkManager;
    private AuthService authService;

    @FXML private AnchorPane overlayPane;
    @FXML private StackPane overlayViewport;
    @FXML private ImageView overlayImageView;

    @FXML private VBox overlayWelcomePane;
    @FXML private VBox overlayHelloPane;

    @FXML private VBox signInFormPane;
    @FXML private VBox signUpFormPane;

    @FXML private TextField signUpUsernameField;
    @FXML private TextField signUpEmailField;
    @FXML private TextField signUpFullNameField;
    @FXML private TextField signUpPhoneField;
    @FXML private PasswordField signUpPasswordField;
    @FXML private PasswordField signUpConfirmPasswordField;
    @FXML private Label signUpMessageLabel;

    @FXML private TextField signInIdentityField;
    @FXML private PasswordField signInPasswordField;
    @FXML private Label signInMessageLabel;

    private boolean signInMode = true;
    private boolean animating = false;

    @FXML
    public void initialize() {
        networkManager = new NetworkManager();
        networkManager.setMessageHandler(this::handleServerResponse);
        authService = new AuthService(networkManager);

        setupOverlayViewport();
        applyState(true);
    }

    @FXML
    private void showSignIn() {
        switchMode(true);
    }

    @FXML
    private void showSignUp() {
        switchMode(false);
    }

    private void setupOverlayViewport() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(overlayViewport.widthProperty());
        clip.heightProperty().bind(overlayViewport.heightProperty());
        overlayViewport.setClip(clip);

        StackPane.setAlignment(overlayImageView, Pos.CENTER_LEFT);

        Image overlayImage = loadOverlayImage();
        if (overlayImage != null) {
            overlayImageView.setImage(overlayImage);
        }
    }

    private Image loadOverlayImage() {
        URL imageUrl = getClass().getResource(OVERLAY_IMAGE_PATH);
        if (imageUrl == null) {
            imageUrl = getClass().getResource(OVERLAY_IMAGE_FALLBACK_PATH);
        }
        return imageUrl != null ? new Image(imageUrl.toExternalForm()) : null;
    }

    private void switchMode(boolean toSignIn) {
        if (animating || signInMode == toSignIn) {
            return;
        }

        animating = true;
        signInMode = toSignIn;
        clearMessages();

        VBox outgoingOverlayContent = toSignIn ? overlayHelloPane : overlayWelcomePane;
        VBox incomingOverlayContent = toSignIn ? overlayWelcomePane : overlayHelloPane;

        incomingOverlayContent.setManaged(true);
        incomingOverlayContent.setVisible(true);
        incomingOverlayContent.setOpacity(0);

        TranslateTransition overlaySlide = new TranslateTransition(SLIDE_DURATION, overlayPane);
        overlaySlide.setInterpolator(Interpolator.EASE_BOTH);
        overlaySlide.setToX(toSignIn ? OVERLAY_DISTANCE : 0);

        TranslateTransition overlayImageSlide = new TranslateTransition(SLIDE_DURATION, overlayImageView);
        overlayImageSlide.setInterpolator(Interpolator.EASE_BOTH);
        overlayImageSlide.setToX(toSignIn ? -OVERLAY_DISTANCE : 0);

        FadeTransition outgoingOverlayFade = new FadeTransition(Duration.millis(180), outgoingOverlayContent);
        outgoingOverlayFade.setToValue(0);

        FadeTransition incomingOverlayFade = new FadeTransition(Duration.millis(260), incomingOverlayContent);
        incomingOverlayFade.setFromValue(0);
        incomingOverlayFade.setToValue(1);
        incomingOverlayFade.setDelay(Duration.millis(120));

        FadeTransition signInFade = new FadeTransition(Duration.millis(280), signInFormPane);
        signInFade.setToValue(toSignIn ? 1.0 : 0.35);

        FadeTransition signUpFade = new FadeTransition(Duration.millis(280), signUpFormPane);
        signUpFade.setToValue(toSignIn ? 0.35 : 1.0);

        ParallelTransition transition = new ParallelTransition(
                overlaySlide,
                overlayImageSlide,
                outgoingOverlayFade,
                incomingOverlayFade,
                signInFade,
                signUpFade
        );

        transition.setOnFinished(event -> {
            outgoingOverlayContent.setVisible(false);
            outgoingOverlayContent.setManaged(false);

            incomingOverlayContent.setVisible(true);
            incomingOverlayContent.setManaged(true);
            incomingOverlayContent.setOpacity(1);

            signInFormPane.setOpacity(toSignIn ? 1.0 : 0.35);
            signUpFormPane.setOpacity(toSignIn ? 0.35 : 1.0);

            animating = false;
        });

        transition.play();
    }

    private void applyState(boolean showSignIn) {
        signInMode = showSignIn;

        overlayPane.setTranslateX(showSignIn ? OVERLAY_DISTANCE : 0);
        overlayImageView.setTranslateX(showSignIn ? -OVERLAY_DISTANCE : 0);

        overlayWelcomePane.setVisible(showSignIn);
        overlayWelcomePane.setManaged(showSignIn);
        overlayWelcomePane.setOpacity(showSignIn ? 1 : 0);

        overlayHelloPane.setVisible(!showSignIn);
        overlayHelloPane.setManaged(!showSignIn);
        overlayHelloPane.setOpacity(!showSignIn ? 1 : 0);

        signInFormPane.setOpacity(showSignIn ? 1.0 : 0.35);
        signUpFormPane.setOpacity(showSignIn ? 0.35 : 1.0);
    }

    @FXML
    private void handleSignUp() {
        String username = signUpUsernameField.getText().trim();
        String email = signUpEmailField.getText().trim();
        String fullName = signUpFullNameField.getText().trim();
        String phone = signUpPhoneField.getText().trim();
        String password = signUpPasswordField.getText().trim();
        String confirmPassword = signUpConfirmPasswordField.getText().trim();

        clearMessages();

        if (username.isEmpty() || email.isEmpty() || fullName.isEmpty() || phone.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            showError(signUpMessageLabel, "Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        if (containsWhitespace(username) || containsWhitespace(email) || containsWhitespace(password)) {
            showError(signUpMessageLabel, "Username, email và mật khẩu không được chứa khoảng trắng.");
            return;
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            showError(signUpMessageLabel, "Email không hợp lệ.");
            return;
        }

        if (!phone.matches("^[0-9+()\\-. ]{8,20}$")) {
            showError(signUpMessageLabel, "Số điện thoại không hợp lệ.");
            return;
        }

        if (password.length() < 6) {
            showError(signUpMessageLabel, "Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError(signUpMessageLabel, "Mật khẩu xác nhận không khớp.");
            return;
        }

        authService.register(
                username,
                password,
                email,
                normalizeFullNameForProtocol(fullName),
                normalizePhoneForProtocol(phone)
        );

        showSuccess(signUpMessageLabel, "Đang đăng ký...");
    }

    @FXML
    private void handleSignIn() {
        String identity = signInIdentityField.getText().trim();
        String password = signInPasswordField.getText().trim();

        clearMessages();

        if (identity.isEmpty() || password.isEmpty()) {
            showError(signInMessageLabel, "Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        if (containsWhitespace(identity) || containsWhitespace(password)) {
            showError(signInMessageLabel, "Tài khoản/email và mật khẩu không được chứa khoảng trắng.");
            return;
        }

        authService.login(identity, password);
        showSuccess(signInMessageLabel, "Đang đăng nhập...");
    }

    @FXML
    public void handleServerResponse(String msg) {
        javafx.application.Platform.runLater(() -> {
            if (msg == null || msg.isBlank()) {
                return;
            }

            String[] p = msg.split(" ");

            if (p[0].equals("LOGIN_SUCCESS")) {
                if (p.length < 4) {
                    showError(signInMessageLabel, "Server trả dữ liệu đăng nhập không hợp lệ.");
                    return;
                }

                try {
                    String username = p[1];
                    SystemRole role = SystemRole.valueOf(p[2]);
                    AccountStatus status = AccountStatus.valueOf(p[3]);

                    User user = new User(username, role, status);

                    String identity = signInIdentityField.getText().trim();
                    if (identity.contains("@")) {
                        user.setEmail(identity);
                    }

                    SessionManager.setCurrentUser(user);

                    if (role == SystemRole.ADMIN) {
                        switchScene("/client/admin-home.fxml", "Admin Dashboard");
                    } else {
                        switchScene("/client/user-home.fxml", "User Dashboard");
                    }
                } catch (IllegalArgumentException e) {
                    showError(signInMessageLabel, "Role hoặc trạng thái tài khoản không khớp client.");
                } catch (Exception e) {
                    e.printStackTrace();
                    showError(signInMessageLabel, "Không thể chuyển màn hình dashboard.");
                }

                return;
            }

            if (p[0].equals("LOGIN_FAIL")) {
                showError(signInMessageLabel, "Sai tài khoản hoặc mật khẩu.");
                return;
            }

            if (p[0].equals("REGISTER_SUCCESS")) {
                String email = signUpEmailField.getText().trim();
                String password = signUpPasswordField.getText().trim();

                clearSignUpFields();
                showSignIn();

                signInIdentityField.setText(email);
                signInPasswordField.setText(password);

                showSuccess(signInMessageLabel, "Đăng ký thành công. Bấm SIGN IN để vào hệ thống.");
                return;
            }

            if (p[0].equals("REGISTER_FAIL")) {
                String reason = p.length >= 2 ? p[1] : "ERROR";

                if (reason.contains("EXIST")) {
                    showError(signUpMessageLabel, "Username hoặc email đã tồn tại.");
                } else if (reason.contains("INVALID_FORMAT")) {
                    showError(signUpMessageLabel, "Dữ liệu đăng ký chưa đúng định dạng server yêu cầu.");
                } else {
                    showError(signUpMessageLabel, "Đăng ký thất bại.");
                }
            }
        });
    }

    private void switchScene(String fxmlPath, String title) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();

        Stage stage = (Stage) signInIdentityField.getScene().getWindow();
        stage.setScene(new Scene(root, MainApp.APP_WIDTH, MainApp.APP_HEIGHT));
        stage.setTitle(title);
        stage.setMinWidth(MainApp.APP_WIDTH);
        stage.setMinHeight(MainApp.APP_HEIGHT);
        stage.show();
    }

    private void clearSignUpFields() {
        signUpUsernameField.clear();
        signUpEmailField.clear();
        signUpFullNameField.clear();
        signUpPhoneField.clear();
        signUpPasswordField.clear();
        signUpConfirmPasswordField.clear();
    }

    private void clearMessages() {
        signUpMessageLabel.setText("");
        signInMessageLabel.setText("");
    }

    private boolean containsWhitespace(String value) {
        return value != null && value.matches(".*\\s+.*");
    }

    private String normalizeFullNameForProtocol(String fullName) {
        return fullName.trim().replaceAll("\\s+", "\u00A0");
    }

    private String normalizePhoneForProtocol(String phone) {
        return phone.trim().replaceAll("\\s+", "");
    }

    private void showError(Label label, String message) {
        label.setStyle("-fx-text-fill: #e57373;");
        label.setText(message);
    }

    private void showSuccess(Label label, String message) {
        label.setStyle("-fx-text-fill: #81c784;");
        label.setText(message);
    }
}