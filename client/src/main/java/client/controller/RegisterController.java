package client.controller;

import client.SceneNavigator;
import client.service.AuthService;
import client.service.NetworkManager;
import client.util.AccountValidator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    private NetworkManager networkManager;
    private AuthService authService;

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    @FXML
    public void initialize() {
        networkManager = new NetworkManager();
        networkManager.setMessageHandler(this::handleServerResponse);
        authService = new AuthService(networkManager);
    }

    @FXML
    public void handleRegister() {
        String username = usernameField.getText().trim();
        String email = AccountValidator.normalizeEmail(emailField.getText());
        String fullName = fullNameField.getText().trim();
        String phone = AccountValidator.normalizePhone(phoneField.getText());
        String password = passwordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty() || email.isEmpty() || fullName.isEmpty() || phone.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        if (containsWhitespace(username) || containsWhitespace(email) || containsWhitespace(password)) {
            showError("Username, email và mật khẩu không được chứa khoảng trắng.");
            return;
        }

        if (!AccountValidator.isValidGmailAddress(email)) {
            showError("Email phải là Gmail hợp lệ, ví dụ: tennguoidung@gmail.com.");
            return;
        }

        if (!AccountValidator.isValidVietnamesePhone(phone)) {
            showError("Số điện thoại phải là số di động VN");
            return;
        }

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp.");
            return;
        }

        authService.register(
                username,
                password,
                email,
                normalizeFullNameForProtocol(fullName),
                phone
        );

        showSuccess("Đang đăng ký...");
    }

    private void handleServerResponse(String msg) {
        Platform.runLater(() -> {
            System.out.println("SERVER: " + msg);

            if (msg == null || msg.isBlank()) {
                return;
            }

            String[] p = msg.split(" ");

            if (p[0].equals("REGISTER_SUCCESS")) {
                clearFields();
                showSuccess("Đăng ký thành công!");
                return;
            }

            if (p[0].equals("REGISTER_FAIL")) {
                String reason = p.length >= 2 ? p[1] : "ERROR";

                if (reason.contains("EXIST")) {
                    showError("Username hoặc email đã tồn tại.");
                } else if (reason.contains("INVALID_FORMAT")) {
                    showError("Dữ liệu đăng ký chưa đúng định dạng server yêu cầu.");
                } else {
                    showError("Đăng ký thất bại.");
                }
            }
        });
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/client/auth.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            SceneNavigator.switchSceneKeepingWindow(stage, root, "Auction System");
        } catch (Exception e) {
            showError("Không mở được màn hình đăng nhập.");
            e.printStackTrace();
        }
    }

    private void clearFields() {
        usernameField.clear();
        emailField.clear();
        fullNameField.clear();
        phoneField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private boolean containsWhitespace(String value) {
        return value != null && value.matches(".*\\s+.*");
    }

    private String normalizeFullNameForProtocol(String fullName) {
        return fullName.trim().replaceAll("\\s+", "\u00A0");
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: #e57373;");
        messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        messageLabel.setStyle("-fx-text-fill: #81c784;");
        messageLabel.setText(message);
    }
}