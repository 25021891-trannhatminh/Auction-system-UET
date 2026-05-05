package client.model;

public class User {
    private int userId;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    private SystemRole systemRole;
    private AccountStatus accountStatus;
    private boolean active = true;

    public User() {
    }

    public User(String username, SystemRole systemRole, AccountStatus accountStatus) {
        this.username = username;
        this.systemRole = systemRole;
        this.accountStatus = accountStatus;
    }

    public User(String username, String email, String passwordHash,
                SystemRole systemRole, AccountStatus accountStatus) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.systemRole = systemRole;
        this.accountStatus = accountStatus;
    }

    public User(String username, String email, String fullName, String phone,
                SystemRole systemRole, AccountStatus accountStatus) {
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.systemRole = systemRole;
        this.accountStatus = accountStatus;
    }

    public User(int userId, String username, String email, String fullName, String phone,
                SystemRole systemRole, AccountStatus accountStatus, boolean active) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.systemRole = systemRole;
        this.accountStatus = accountStatus;
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public SystemRole getRole() {
        return systemRole;
    }

    public void setSystemRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    public void setRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public AccountStatus getStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public void setStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}