package client.controller;

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
import client.service.AuthService;
import client.service.NetworkManager;

public class RegisterController {
    private NetworkManager networkManager;
    private AuthService authService;
    
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

    @FXML
    public void initialize(){
        networkManager = new NetworkManager();
        networkManager.setMessageHandler(this::handleServerResponse);
        authService = new AuthService(networkManager);
    }

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

        authService.register(username,email,password);
        messageLabel.setText("Đang đăng ký...");
    }

    private void handleServerResponse(String msg){
        System.out.println("SERVER: "+msg);
        String[] p = msg.split(" ");
        if(p[0].equals("REGISTER_SUCCESS")){
            messageLabel.setText("Đăng ký thành công!");

        }else if (p[0].equals("REGISTER_FAIL")){
            messageLabel.setText("Username hoặc email đã tồn tại.");
        }
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
