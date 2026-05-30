package server.common.entity;

import org.mindrot.jbcrypt.BCrypt;
import server.common.enums.AccountRole;
import server.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * Lớp trừu tượng đại diện cho một tài khoản trong hệ thống.
 *
 * <p>Mọi {@code Account} đều phải là {@link User} hoặc {@link Admin}.
 * Mật khẩu được lưu dưới dạng bcrypt hash — KHÔNG lưu plaintext.</p>
 *
 * <p>DB: {@code AccountRole} được map sang cột {@code role (ENUM)} trong bảng {@code accounts}.</p>
 */
public abstract class Account extends Entity {

  private String   username;
  private String   email;
  private String   passwordHash;  // bcrypt hash — không lưu plaintext
  private String   fullName;
  private String   phone;
  private AccountRole role;
  private UserStatus status;
  private LocalDateTime lastLogin;

  protected Account(String username, String email, String passwordHash,
                    String fullName, String phone, AccountRole role) {
    super();
    this.username     = username;
    this.email        = email;
    this.passwordHash = passwordHash;
    this.fullName     = fullName;
    this.phone        = phone;
    this.role         = role;
    this.status     = UserStatus.ACTIVE;
    this.lastLogin    = null;
  }

  protected Account(Account other){
    super(other.getId(),other.getCreatedAt());
    this.username     = other.username;
    this.email        = other.email;
    this.passwordHash = other.passwordHash;
    this.fullName     = other.fullName;
    this.phone        = other.phone;
    this.role         = other.role;
    this.status     = other.status;
    this.lastLogin    = other.lastLogin;
  }

  /** Constructor dùng khi load từ DB — toàn bộ trường đã được persist. */
  protected Account(int id, LocalDateTime createdAt,
                    String username, String email, String passwordHash,
                    String fullName, String phone, AccountRole role,
                    UserStatus status, LocalDateTime lastLogin) {
    super(id, createdAt);
    this.username     = username;
    this.email        = email;
    this.passwordHash = passwordHash;
    this.fullName     = fullName;
    this.phone        = phone;
    this.role         = role;
    this.status     = status;
    this.lastLogin    = lastLogin;
  }

  // ── Core logic ───────────────────────────────────────────────────────────

  /**
   * Kiểm tra mật khẩu người dùng nhập có khớp hash không.
   *
   * <p>Sử dụng {@link BCrypt#checkpw(String, String)} để so sánh an toàn.
   * KHÔNG so sánh plaintext trực tiếp.</p>
   *
   * @param plainPassword mật khẩu người dùng nhập vào (plaintext)
   * @return {@code true} nếu khớp hash, {@code false} nếu không
   */
  public boolean verifyPassword(String plainPassword) {
    return BCrypt.checkpw(plainPassword, passwordHash);
  }

  /**
   * Ghi lại thời điểm đăng nhập gần nhất.
   */
  public void recordLogin() {
    this.lastLogin = LocalDateTime.now();
  }

  // ── Getters / Setters ────────────────────────────────────────────────────

  public String      getUsername()     { return username; }
  public String      getEmail()        { return email; }
  public String      getPasswordHash() { return passwordHash; }
  public String      getFullName()     { return fullName; }
  public String      getPhone()        { return phone; }
  public AccountRole getRole()         { return role; }
  public UserStatus     getStatus()        { return status; }
  public LocalDateTime getLastLogin()  { return lastLogin; }

  public void setActive()   { this.status = UserStatus.ACTIVE; }
  public void setFullName(String name)    { this.fullName = name; }
  public void setPhone(String phone)      { this.phone = phone; }
  public void setEmail(String email)      { this.email = email; }
  public void setPasswordHash(String hash)   { this.passwordHash = hash; }
  public void setStatus (UserStatus status) {this.status = status;}

  @Override
  public void printInfo() {
    System.out.printf("[%s] %s (%s) | email: %s | active: %s%n",
        role, username, fullName != null ? fullName : "—", email, status);
  }
}

