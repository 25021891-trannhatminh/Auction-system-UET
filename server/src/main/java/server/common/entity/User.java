package server.common.entity;

import server.common.enums.UserRole;
import server.common.enums.UserStatus;

import java.io.Serializable;
import java.sql.Timestamp;

public class User {

    private int userId;
    private String username;
    private String email;
    private String fullName;    // ADD
    private String phone;       // ADD
    private UserRole role;
    private UserStatus status;
    private boolean isActive;   // ADD
    private Timestamp lastLogin; // ADD
    private Timestamp createdAt;

    public User() {}

    public User(int userId, String username, String email,
        String fullName, String phone,
        UserRole role, UserStatus status,
        boolean isActive, Timestamp lastLogin, Timestamp createdAt) {
        this.userId    = userId;
        this.username  = username;
        this.email     = email;
        this.fullName  = fullName;
        this.phone     = phone;
        this.role      = role;
        this.status    = status;
        this.isActive  = isActive;
        this.lastLogin = lastLogin;
        this.createdAt = createdAt;
    }

    public int getUserId()                      { return userId; }
    public void setUserId(int userId)           { this.userId = userId; }

    public String getUsername()                 { return username; }
    public void setUsername(String username)    { this.username = username; }

    public String getEmail()                    { return email; }
    public void setEmail(String email)          { this.email = email; }

    public String getFullName()                 { return fullName; }
    public void setFullName(String fullName)    { this.fullName = fullName; }

    public String getPhone()                    { return phone; }
    public void setPhone(String phone)          { this.phone = phone; }

    public UserRole getRole()                   { return role; }
    public void setRole(UserRole role)          { this.role = role; }

    public UserStatus getStatus()               { return status; }
    public void setStatus(UserStatus status)    { this.status = status; }

    public boolean isActive()                   { return isActive; }
    public void setActive(boolean isActive)     { this.isActive = isActive; }

    public Timestamp getLastLogin()             { return lastLogin; }
    public void setLastLogin(Timestamp lastLogin) { this.lastLogin = lastLogin; }

    public Timestamp getCreatedAt()             { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}