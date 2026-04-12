package com.example.auction.controller;

import com.example.auction.model.AccountStatus;
import com.example.auction.model.SystemRole;
import com.example.auction.model.User;
import com.example.auction.service.AuthService;
import com.example.auction.service.SessionManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void handleLogin() {
        String usernameOrEmail = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (usernameOrEmail.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        User user = authService.login(usernameOrEmail, password);

        if (user == null) {
            messageLabel.setText("Sai tài khoản hoặc mật khẩu.");
            return;
        }

        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            messageLabel.setText("Tài khoản đang bị tạm khóa.");
            return;
        }

        if (user.getAccountStatus() == AccountStatus.BANNED) {
            messageLabel.setText("Tài khoản đã bị cấm.");
            return;
        }

        SessionManager.setCurrentUser(user);

        try {
            if (user.getSystemRole() == SystemRole.ADMIN) {
                switchScene("/com/example/auction/admin-home.fxml", "Admin Home");
            } else {
                switchScene("/com/example/auction/user-home.fxml", "User Home");
            }
        } catch (Exception e) {
            messageLabel.setText("Không thể chuyển màn hình.");
            e.printStackTrace();
        }
    }

    @FXML
    public void goToRegister(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/example/auction/register.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Register");
            stage.show();
        } catch (Exception e) {
            messageLabel.setText("Không mở được màn hình đăng ký.");
            e.printStackTrace();
        }
    }

    private void switchScene(String fxmlPath, String title) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle(title);
        stage.show();
    }
}