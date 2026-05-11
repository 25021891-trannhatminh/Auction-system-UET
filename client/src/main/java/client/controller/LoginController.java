package client.controller;

import java.util.ArrayList;
import java.util.List;

import client.SceneNavigator;
import client.model.AccountStatus;
import client.model.SystemRole;
import client.model.User;
import client.service.AuthService;
import client.service.NetworkManager;
import client.service.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
        String[] p = msg.split(" ", 2);
        if (p[0].equals("LOGIN_SUCCESS")){
            try {
                String payload = p.length > 1 ? p[1] : "";
                User user = parseLoginSuccessPayload(payload);
                SessionManager.setCurrentUser(user);

                if (user.getSystemRole() == SystemRole.ADMIN) {
                    switchScene("/client/admin-home.fxml", "Admin Home", user);
                } else {
                    switchScene("/client/user-home.fxml", "User Home", user);
                }
            } catch (Exception e) {
                messageLabel.setText("Không thể chuyển màn hình.");
                e.printStackTrace();
            }
        }else if(p[0].equals("LOGIN_FAIL")){
            messageLabel.setText("Sai tài khoản hoặc mật khẩu.");
        }
    }

    private User parseLoginSuccessPayload(String payload) {
        if (payload != null && payload.contains("|")) {
            List<String> fields = splitPayload(payload);
            if (fields.size() < 8) {
                throw new IllegalArgumentException("Invalid LOGIN_SUCCESS payload");
            }

            int userId = parseIntOrDefault(fields.get(0), 0);
            String username = fallback(fields.get(1), usernameField.getText().trim());
            String email = fields.get(2);
            String fullName = fields.get(3);
            String phone = fields.get(4);
            SystemRole role = SystemRole.valueOf(fallback(fields.get(5), "USER").toUpperCase());
            AccountStatus status = AccountStatus.valueOf(fallback(fields.get(6), "ACTIVE").toUpperCase());
            boolean active = !"0".equals(fields.get(7)) && !"false".equalsIgnoreCase(fields.get(7));

            return new User(userId, username, email, fullName, phone, role, status, active);
        }

        String[] legacy = payload == null ? new String[0] : payload.split(" ");
        if (legacy.length < 3) {
            throw new IllegalArgumentException("Invalid legacy LOGIN_SUCCESS payload");
        }

        User user = new User(
                legacy[0],
                SystemRole.valueOf(legacy[1]),
                AccountStatus.valueOf(legacy[2])
        );

        String identity = usernameField.getText().trim();
        if (identity.contains("@")) {
            user.setEmail(identity);
        }
        return user;
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
        } catch (NumberFormatException ignored) {
            return fallbackValue;
        }
    }

    private String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value;
    }

    private void switchScene(String fxmlPath, String title, User user) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = loader.load();

        Object controller = loader.getController();
        if (controller instanceof BaseDashboardController dashboardController) {
            dashboardController.applyLoggedInUser(user);
        }

        Stage stage = (Stage) usernameField.getScene().getWindow();
        SceneNavigator.switchSceneKeepingWindow(stage, root, title);
    }
}