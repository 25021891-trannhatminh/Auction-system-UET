package client.service;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import client.model.AccountStatus;
import client.model.SystemRole;
import client.model.User;

public class SessionManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(SessionManager.class);
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_ROLE = "role";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ACTIVE = "active";

    private static User currentUser;

    public static void setCurrentUser(User user) {
        currentUser = user;
        persist(user);
    }

    public static User getCurrentUser() {
        if (currentUser == null) {
            currentUser = restoreCurrentUser();
        }
        return currentUser;
    }

    public static User restoreCurrentUser() {
        String username = PREFS.get(KEY_USERNAME, "");
        String email = PREFS.get(KEY_EMAIL, "");

        if (username.isBlank() && email.isBlank()) {
            return null;
        }

        int userId = PREFS.getInt(KEY_USER_ID, 0);
        String fullName = PREFS.get(KEY_FULL_NAME, "");
        String phone = PREFS.get(KEY_PHONE, "");
        SystemRole role = parseRole(PREFS.get(KEY_ROLE, "USER"));
        AccountStatus status = parseStatus(PREFS.get(KEY_STATUS, "ACTIVE"));
        boolean active = PREFS.getBoolean(KEY_ACTIVE, true);

        return new User(userId, username, email, fullName, phone, role, status, active);
    }

    public static void clear() {
        currentUser = null;
        try {
            PREFS.clear();
            PREFS.flush();
        } catch (BackingStoreException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static void persist(User user) {
        if (user == null) {
            return;
        }

        PREFS.putInt(KEY_USER_ID, user.getUserId());
        put(KEY_USERNAME, user.getUsername());
        put(KEY_EMAIL, user.getEmail());
        put(KEY_FULL_NAME, user.getFullName());
        put(KEY_PHONE, user.getPhone());
        put(KEY_ROLE, user.getSystemRole() == null ? "USER" : user.getSystemRole().name());
        put(KEY_STATUS, user.getAccountStatus() == null ? "ACTIVE" : user.getAccountStatus().name());
        PREFS.putBoolean(KEY_ACTIVE, user.isActive());

        try {
            PREFS.flush();
        } catch (BackingStoreException ignored) {
            // Preferences still remain available in-memory for this run.
        }
    }

    private static void put(String key, String value) {
        PREFS.put(key, value == null ? "" : value);
    }

    private static SystemRole parseRole(String value) {
        try {
            return SystemRole.valueOf(value == null ? "USER" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SystemRole.USER;
        }
    }

    private static AccountStatus parseStatus(String value) {
        try {
            return AccountStatus.valueOf(value == null ? "ACTIVE" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AccountStatus.ACTIVE;
        }
    }
}