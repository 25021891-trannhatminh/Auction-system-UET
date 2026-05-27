package server.handler;

import server.common.ProtocolConstants;
import server.network.ClientManager;
import server.service.ServerAuthService;
import server.handler.ClientHandler;

/**
 * Xử lý lệnh LOGIN và REGISTER.
 */
public class AuthHandler {
    
    private final ServerAuthService authService;
    
    public AuthHandler() {
        this.authService = new ServerAuthService();
    }
    
    /**
     * Xử lý lệnh LOGIN.
     * Format: LOGIN username password
     *
     * @param request Mảng lệnh từ client
     * @param client  ClientHandler để cập nhật thông tin user
     * @return Response string
     */
    public String handleLogin(String[] request, ClientHandler client) {
        if (request.length < 3) {
            return ResponseBuilder.loginFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }
        
        String identifier = request[1];
        String password = request[2];
        
        String[] authRequest = {ProtocolConstants.LOGIN, identifier, password};
        String response = authService.login(authRequest);

        if (response.startsWith(ProtocolConstants.LOGIN_SUCCESS)) {
            String[] parts = response.substring(ProtocolConstants.LOGIN_SUCCESS.length() + 1)
                            .split("\\|");
            if (parts.length >= 2) {
                try {
                    int userId = Integer.parseInt(parts[0]);
                    client.setUserId(userId);
                    client.setUsername(parts[1]);
                    // Đăng ký session lên bộ quản lý tập trung toàn cục
                    ClientManager.add(userId, client);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        return response;
    }
    
    /**
     * Xử lý lệnh REGISTER.
     * Format: REGISTER username password email fullName phone
     *
     * @param request Mảng lệnh từ client
     * @return Response string
     */
    public String handleRegister(String[] request) {
        if (request.length < 2) {
            return ResponseBuilder.registerFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }
        return authService.register(request);
    }
}
