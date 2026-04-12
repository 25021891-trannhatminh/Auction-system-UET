package com.example.auction.model;

public class User {
    private String username;
    private String email;
    private String passwordHash;
    private SystemRole systemRole;
    private AccountStatus accountStatus;

    public User(String username, String email, String passwordHash,
                SystemRole systemRole, AccountStatus accountStatus) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.systemRole = systemRole;
        this.accountStatus = accountStatus;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public SystemRole getSystemRole() {
        return systemRole;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }
}
