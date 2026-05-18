package server.common.entity;


import org.mindrot.jbcrypt.BCrypt;
import server.common.enums.AccountRole;
import server.common.enums.UserStatus;
import server.repository.AccountDAO;

import java.time.LocalDateTime;

/*
    Account:
    Mọi Account đều phải là User hoặc Admin.
    password dưới dạng hash (bcrypt) — KHÔNG lưu plaintext.

    UI: AccountRole được map sang cột role (ENUM) trong DB.

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
  /* Constructor dùng khi load từ DB */
  protected Account(String id, LocalDateTime createdAt,
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


  /*
       Kiểm tra mật khẩu người dùng nhập có khớp hash không.
       Tầng thực tế sẽ dùng BCrypt.checkpw(rawPassword, passwordHash).
   */
  public boolean verifyPassword(String plainPassword) {
    return BCrypt.checkpw(plainPassword, passwordHash);
  }

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


  @Override
  public void printInfo() {
    System.out.printf("[%s] %s (%s) | email: %s | active: %s%n",
        role, username, fullName != null ? fullName : "—", email, status);
  }
}

