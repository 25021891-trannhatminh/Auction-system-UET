package server.common.model;

import server.common.entity.User;
import server.common.enums.AccountRole;
import server.common.enums.UserStatus;

import java.time.LocalDateTime;


/**
 * DTO cho bảng USERS
 *
 * Mục đích:
 * - Truyền dữ liệu từ server → client
 * - Không expose thông tin nhạy cảm (password)
 *
 * Mapping từ ERD:
 * USERS (
 *   user_id PK,
 *   username,
 *   email,
 *   password_hash (KHÔNG đưa vào DTO),
 *   full_name,
 *   phone,
 *   role,
 *   is_active / status,
 *   last_login,
 *   created_at
 * )
 *
 * Lưu ý:
 * - DTO KHÔNG chứa password
 * - DTO KHÔNG chứa logic nghiệp vụ
 * - Dùng factory method để convert từ Entity
 */
public class UserDTO{


  /** ID người dùng */
  private int userId;

  /** Username (unique) */
  private String username;

  /** Email */
  private String email;

  /** Họ tên */
  private String fullName;

  /** Số điện thoại */
  private String phone;

  /** Vai trò (USER / ADMIN) */
  private AccountRole role;

  /** Trạng thái tài khoản */
  private UserStatus status;

  /** Thời điểm đăng nhập gần nhất */
  private LocalDateTime lastLogin;

  /** Thời điểm tạo tài khoản */
  private LocalDateTime createdAt;

  // ========================== Constructors ==========================

  public UserDTO() {}

  public UserDTO(int userId, String username, String email,
      String fullName, String phone,
      AccountRole role, UserStatus status,
      LocalDateTime lastLogin, LocalDateTime createdAt) {

    this.userId    = userId;
    this.username  = username;
    this.email     = email;
    this.fullName  = fullName;
    this.phone     = phone;
    this.role      = role;
    this.status    = status;
    this.lastLogin = lastLogin;
    this.createdAt = createdAt;
  }

  // ========================== Factory ==========================

  /**
   * Convert từ Entity -> DTO
   */
  public static UserDTO from(User user) {
    if (user == null) return null;

    return new UserDTO(
        Integer.parseInt(user.getId()),
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getRole(),
        user.getStatus(),
        user.getLastLogin(),
        user.getCreatedAt()
    );
  }

  // ========================== Getters & Setters ==========================

  public int getUserId() { return userId; }
  public void setUserId(int userId) { this.userId = userId; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }

  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }

  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }

  public AccountRole getRole() { return role; }
  public void setRole(AccountRole role) { this.role = role; }

  public UserStatus getStatus() { return status; }
  public void setStatus(UserStatus status) { this.status = status; }

  public LocalDateTime getLastLogin() { return lastLogin; }
  public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

  // ========================== Helper ==========================

  /**
   * Kiểm tra user có active không
   */
  public boolean isActive() {
    return status != null && status == UserStatus.ACTIVE;
  }

  @Override
  public String toString() {
    return "UserDTO{" +
        "userId=" + userId +
        ", username='" + username + '\'' +
        ", email='" + email + '\'' +
        ", fullName='" + fullName + '\'' +
        ", phone='" + phone + '\'' +
        ", role=" + role +
        ", status=" + status +
        ", lastLogin=" + lastLogin +
        ", createdAt=" + createdAt +
        '}';
  }
}