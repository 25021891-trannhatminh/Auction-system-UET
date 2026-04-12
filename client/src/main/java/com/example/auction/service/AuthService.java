package com.example.auction.service;

import java.util.ArrayList;
import java.util.List;

import com.example.auction.model.AccountStatus;
import com.example.auction.model.SystemRole;
import com.example.auction.model.User;

public class AuthService {
    private static final List<User> users = new ArrayList<>();

    static {
        users.add(new User("admin", "admin@gmail.com", "123456", SystemRole.ADMIN, AccountStatus.ACTIVE));
        users.add(new User("user1", "user1@gmail.com", "123456", SystemRole.USER, AccountStatus.ACTIVE));
    }

    public User login(String usernameOrEmail, String password) {
        for (User user : users) {
            boolean matchIdentity = user.getUsername().equals(usernameOrEmail)
                    || user.getEmail().equals(usernameOrEmail);

            boolean matchPassword = user.getPasswordHash().equals(password);

            if (matchIdentity && matchPassword) {
                return user;
            }
        }
        return null;
    }

    public boolean usernameExists(String username) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean emailExists(String email) {
        for (User user : users) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return true;
            }
        }
        return false;
    }

    public boolean register(String username, String email, String password) {
        if (usernameExists(username) || emailExists(email)) {
            return false;
        }

        users.add(new User(
                username,
                email,
                password,
                SystemRole.USER,
                AccountStatus.ACTIVE
        ));

        return true;
    }
}
