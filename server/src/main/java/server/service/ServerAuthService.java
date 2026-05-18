package server.service;


import server.common.entity.User;
import server.repository.AccountDAO;

public class ServerAuthService {

  private final AccountDAO accountDAO = new AccountDAO();

  /**
   * Xử lý đăng nhập
   * @return chuỗi response để gửi trực tiếp cho client
   */
  public String login(String[] request) {
    if (request.length < 3) {
      return "LOGIN_FAIL MISSING_CREDENTIALS";
    }

    String identifier = request[1]; // Có thể là username hoặc email theo UserDAO
    String password = request[2];
    try {
      User user = accountDAO.login(identifier, password);
      if (user != null) {
        return "LOGIN_SUCCESS " + fields(
            Integer.parseInt(user.getId()),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getRole() != null ? user.getRole().name() : "USER",
            user.getStatus() != null ? user.getStatus().name() : "ACTIVE"
        );
      } else {
        return "LOGIN_FAIL INVALID_AUTH";
      }
    } catch (Exception e) {
      e.printStackTrace();
      return "LOGIN_FAIL SERVER_ERROR";
    }
  }
  public String login(String identifier, String password) {
    try {
      User user = accountDAO.login(identifier, password);
      if (user != null) {
        return "LOGIN_SUCCESS " + fields(
            Integer.parseInt(user.getId()),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getRole() != null ? user.getRole().name() : "USER",
            user.getStatus() != null ? user.getStatus().name() : "ACTIVE"
        );
      } else {
        return "LOGIN_FAIL INVALID_AUTH";
      }
    } catch (Exception e) {
      e.printStackTrace();
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
      return success ? "REGISTER_SUCCESS" : "REGISTER_FAIL EXIST_OR_ERROR";
    } catch (Exception e) {
      e.printStackTrace();
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
