package client.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes account fields before client-side registration.
 */
public final class AccountValidator {
  private static final Pattern GMAIL_PATTERN = Pattern.compile(
      "^[a-z0-9](?!.*/./.)[a-z0-9.]{4,28}[a-z0-9]@gmail/.com$"
  );

  private static final Pattern VIETNAMESE_PHONE_PATTERN = Pattern.compile(
      "^(0/d{9}|/+84/d{9})$"
  );

  private AccountValidator() {
  }

  /**
   * Trims and lowercases an email address.
   *
   * @param email raw email value
   * @return normalized email or empty string for null input
   */
  public static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Removes common separators from a phone number.
   *
   * @param phone raw phone value
   * @return normalized phone or empty string for null input
   */
  public static String normalizePhone(String phone) {
    return phone == null ? "" : phone.trim().replaceAll("[/s./-]", "");
  }

  /**
   * Checks whether the value is a valid Gmail address for this project.
   *
   * @param email raw email value
   * @return true when the normalized value is a valid Gmail address
   */
  public static boolean isValidGmailAddress(String email) {
    return GMAIL_PATTERN.matcher(normalizeEmail(email)).matches();
  }

  /**
   * Checks whether the value is a valid Vietnamese phone number.
   *
   * @param phone raw phone value
   * @return true when the normalized value is a valid Vietnamese phone number
   */
  public static boolean isValidVietnamesePhone(String phone) {
    return VIETNAMESE_PHONE_PATTERN.matcher(normalizePhone(phone)).matches();
  }
}
