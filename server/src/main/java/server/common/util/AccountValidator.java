package server.common.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountValidator {
    private static final Pattern GMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9](?!.*\\.\\.)[a-z0-9.]{4,28}[a-z0-9]@gmail\\.com$"
    );

    private static final Pattern VIETNAMESE_PHONE_PATTERN = Pattern.compile(
            "^(0[35789]\\d{8}|\\+84[35789]\\d{8})$"
    );

    private AccountValidator() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim().replaceAll("[\\s.\\-]", "");
    }

    public static boolean isValidGmailAddress(String email) {
        return GMAIL_PATTERN.matcher(normalizeEmail(email)).matches();
    }

    public static boolean isValidVietnamesePhone(String phone) {
        return VIETNAMESE_PHONE_PATTERN.matcher(normalizePhone(phone)).matches();
    }
}