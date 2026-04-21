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

import java.nio.channels.NetworkChannel;

import client.model.AccountStatus;
import client.model.SystemRole;
import client.model.User;
import client.service.AuthService;
import client.service.NetworkManager;
import client.service.SessionManager;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    private NetworkManager networkManager;
    private AuthService authService;

    @FXML
    public void initialize(){
        networkManager = new NetworkManager();

        networkManager.setMessageHandler(this::handleServerResponse);

        authService = new AuthService(networkManager);
    }
    @FXML
    public void handleLogin() {
        String usernameOrEmail = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (usernameOrEmail.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        authService.login(usernameOrEmail, password);
        messageLabel.setText("Đang đăng nhập...");
    }
    private void handleServerResponse(String msg){
        System.out.println("SERVER: "+ msg);
        String[] p = msg.split(" ");
        if (p[0].equals("LOGIN_SUCCESS")){
            String username = p[1];
            SystemRole role = SystemRole.valueOf(p[2]);
            AccountStatus status = AccountStatus.valueOf(p[3]);

            User user = new User(username, role, status);
            SessionManager.setCurrentUser(user);

        try {
            if (role == SystemRole.ADMIN) {
                switchScene("/com/example/auction/admin-home.fxml", "Admin Home");
            } else {
                switchScene("/com/example/auction/user-home.fxml", "User Home");
            }
        } catch (Exception e) {
            messageLabel.setText("Không thể chuyển màn hình.");
            e.printStackTrace();
        }
        }else if(p[0].equals("LOGIN_FAIL")){
            messageLabel.setText("Sai tài khoản hoặc mật khẩu.");
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