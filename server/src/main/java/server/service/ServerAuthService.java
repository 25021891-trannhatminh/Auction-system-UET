package server.service;


import server.common.entity.User;
import server.repository.AccountDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.repository.WalletDAO;

public class ServerAuthService {

  private static final Logger logger = LoggerFactory.getLogger(ServerAuthService.class);
  private final AccountDAO accountDAO = new AccountDAO();
  private final WalletDAO walletDAO = new WalletDAO();

  /**
   * Xử lý đăng nhập
   * @return chuỗi response để gửi trực tiếp cho client
   */
  public String login(String[] request) {
    if (request.length < 3) {
      return "LOGIN_FAIL MISSING_CREDENTIALS";
    }
    String identifier = request[1];
    String password   = request[2];
    if (identifier == null || identifier.isBlank() || password == null || password.isBlank()) {
      return "LOGIN_FAIL MISSING_CREDENTIALS";
    }
    return login(identifier, password);
  }

  /**
   * Xử lý đăng nhập bằng identifier + password
   */
  public String login(String identifier, String password) {
    try {
      User user = accountDAO.login(identifier, password);
      if (user != null) {
        return "LOGIN_SUCCESS " + fields(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getRole() != null ? user.getRole().name() : "USER",
            user.getStatus() != null ? user.getStatus().name() : "ACTIVE",
            "true"
        );
      } else {
        return "LOGIN_FAIL INVALID_AUTH";
      }
    } catch (Exception e) {
      logger.error("login() — Server error for identifier={}", identifier, e);
      return "LOGIN_FAIL SERVER_ERROR";
    }
  }

  /**
   * Xử lý đăng ký
   * @return chuỗi response để gửi trực tiếp cho client
   */
  public String register(String[] request) {
    if (request.length < 6) {
      return "REGISTER_FAIL INVALID_FORMAT_REQUIRES_5_FIELDS";
    }

    String registerUser = request[1];
    String registerPass = request[2];
    String registerEmail = request[3];
    String registerFullName = request[4];
    String registerPhone = request[5];

    try {
      boolean success = accountDAO.register(registerUser,registerPass,registerEmail,registerFullName,registerPhone);
      if(success){
        int userId = accountDAO.getUserIdByUsername(registerUser);
        walletDAO.createWalletIfNotExists(userId);
        return  "REGISTER_SUCCESS";
      }
      return "REGISTER_FAIL EXIST_OR_ERROR";
    } catch (Exception e) {
      logger.error("register() — Server error for user={}", registerUser, e);
      return "REGISTER_FAIL SERVER_ERROR";
    }
  }

  // Helper method để format fields (copy từ ClientHandler)
  private String fields(Object... values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) sb.append("|");
      sb.append(encodeField(values[i]));
    }
    return sb.toString();
  }

  private String encodeField(Object value) {
    if (value == null) return "";
    return String.valueOf(value)
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
  // Tạo ra 1 clone User từ login
  public User getUser(String identifier, String password){
    return accountDAO.login(identifier, password);
  }
}