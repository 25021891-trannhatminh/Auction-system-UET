package model.user;

import enums.UserRole;
import enums.UserStatus;
import model.Entity;
import org.mindrot.jbcrypt.BCrypt;
import java.time.LocalDateTime;

/*
    User
    Mọi user đều phải là Bidder, Seller hoặc Admin.
    password dưới dạng hash (bcrypt) — KHÔNG lưu plaintext.

    UI: UserRole được map sang cột role (ENUM) trong DB.
    Tầng DAO sẽ gọi UserFactory để tạo đúng subclass khi load từ DB.
 */
public abstract class User extends Entity {

    private String   username;
    private String   email;
    private String   passwordHash;  // bcrypt hash — không bao giờ lưu plaintext
    private String   fullName;
    private String   phone;
    private UserRole role;
    private UserStatus  status;
    private LocalDateTime lastLogin;

    protected User(String username, String email, String passwordHash,
                   String fullName, String phone, UserRole role) {
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

    /* Constructor dùng khi load từ DB */
    protected User(String id, LocalDateTime createdAt,
                   String username, String email, String passwordHash,
                   String fullName, String phone, UserRole role,
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
    public UserRole    getRole()         { return role; }
    public UserStatus     getStatus()        { return status; }
    public LocalDateTime getLastLogin()  { return lastLogin; }

    public void setActive()   { this.status = UserStatus.ACTIVE; }
    public void setFullName(String name)    { this.fullName = name; }
    public void setPhone(String phone)      { this.phone = phone; }
    public void setEmail(String email)      { this.email = email; }
    public void setPasswordHash(String h)   { this.passwordHash = h; }

    @Override
    public void printInfo() {
        System.out.printf("[%s] %s (%s) | email: %s | active: %s%n",
            role, username, fullName != null ? fullName : "—", email, status);
    }
}
