package client.model;

public class User {
    private int userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private SystemRole systemRole;
    private AccountStatus accountStatus;
    private boolean active;

    public User() {
    }

    public User(String username, SystemRole systemRole, AccountStatus accountStatus) {
        this.username = username;
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

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(SystemRole systemRole) {
        this.systemRole = systemRole;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getRole() {
        return systemRole != null ? systemRole.name() : null;
    }

    public String getStatus() {
        return accountStatus != null ? accountStatus.name() : null;
    }

    public boolean isAdmin() {
        return systemRole == SystemRole.ADMIN;
    }

    public boolean canLogin() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean canBid() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public boolean canSell() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public String getDisplayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return username;
    }

    public String getInitials() {
        String source = getDisplayName();
        if (source == null || source.isBlank()) {
            return "U";
        }

        String[] parts = source.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
}