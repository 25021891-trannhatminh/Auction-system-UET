package com.example.auction.controller;

import com.example.auction.service.AuthService;

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

public class RegisterController {

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void handleRegister() {
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            messageLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        if (!email.contains("@")) {
            messageLabel.setText("Email không hợp lệ.");
            return;
        }

        if (password.length() < 6) {
            messageLabel.setText("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            messageLabel.setText("Mật khẩu xác nhận không khớp.");
            return;
        }

        boolean success = authService.register(username, email, password);

        if (!success) {
            messageLabel.setText("Username hoặc email đã tồn tại.");
            return;
        }

        messageLabel.setText("Đăng ký thành công. Quay lại đăng nhập.");
    }

    @FXML
    public void goToLogin(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/example/auction/login.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Login");
            stage.show();
        } catch (Exception e) {
            messageLabel.setText("Không mở được màn hình đăng nhập.");
            e.printStackTrace();
        }
    }
}
