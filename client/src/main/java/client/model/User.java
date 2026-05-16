package client.model;

/**
 * Client-side user model used for authentication state and dashboard rendering.
 */
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

  /**
   * Creates an empty user for JavaFX binding and incremental population.
   */
  public User() {
  }

  /**
   * Creates a compact user containing identity, role, and status.
   *
   * @param username display username
   * @param systemRole role used for dashboard routing
   * @param accountStatus account state returned by the server
   */
  public User(String username, SystemRole systemRole, AccountStatus accountStatus) {
    this.username = username;
    this.systemRole = systemRole;
    this.accountStatus = accountStatus;
  }

  /**
   * Creates a user with credentials for authentication-related flows.
   *
   * @param username display username
   * @param email contact email
   * @param passwordHash hashed password value
   * @param systemRole role used for dashboard routing
   * @param accountStatus account state returned by the server
   */
  public User(
      String username,
      String email,
      String passwordHash,
      SystemRole systemRole,
      AccountStatus accountStatus) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.systemRole = systemRole;
    this.accountStatus = accountStatus;
  }

  /**
   * Creates a user profile for dashboard display.
   *
   * @param username display username
   * @param email contact email
   * @param fullName full name shown in profile cards
   * @param phone phone number shown in profile cards
   * @param systemRole role used for dashboard routing
   * @param accountStatus account state returned by the server
   */
  public User(
      String username,
      String email,
      String fullName,
      String phone,
      SystemRole systemRole,
      AccountStatus accountStatus) {
    this.username = username;
    this.email = email;
    this.fullName = fullName;
    this.phone = phone;
    this.systemRole = systemRole;
    this.accountStatus = accountStatus;
  }

  /**
   * Creates a complete user restored from the session or server payload.
   *
   * @param userId database user id
   * @param username display username
   * @param email contact email
   * @param fullName full name shown in profile cards
   * @param phone phone number shown in profile cards
   * @param systemRole role used for dashboard routing
   * @param accountStatus account state returned by the server
   * @param active whether the user is treated as active in the UI
   */
  public User(
      int userId,
      String username,
      String email,
      String fullName,
      String phone,
      SystemRole systemRole,
      AccountStatus accountStatus,
      boolean active) {
    this.userId = userId;
    this.username = username;
    this.email = email;
    this.fullName = fullName;
    this.phone = phone;
    this.systemRole = systemRole;
    this.accountStatus = accountStatus;
    this.active = active;
  }

  /**
   * Returns the database user id.
   *
   * @return user id
   */
  public int getUserId() {
    return userId;
  }

  /**
   * Updates the database user id.
   *
   * @param userId user id
   */
  public void setUserId(int userId) {
    this.userId = userId;
  }

  /**
   * Returns the display username.
   *
   * @return username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Updates the display username.
   *
   * @param username username
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Returns the contact email.
   *
   * @return email address
   */
  public String getEmail() {
    return email;
  }

  /**
   * Updates the contact email.
   *
   * @param email email address
   */
  public void setEmail(String email) {
    this.email = email;
  }

  /**
   * Returns the password hash used by authentication flows.
   *
   * @return password hash
   */
  public String getPasswordHash() {
    return passwordHash;
  }

  /**
   * Updates the password hash used by authentication flows.
   *
   * @param passwordHash password hash
   */
  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  /**
   * Returns the full profile name.
   *
   * @return full name
   */
  public String getFullName() {
    return fullName;
  }

  /**
   * Updates the full profile name.
   *
   * @param fullName full name
   */
  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  /**
   * Returns the phone number.
   *
   * @return phone number
   */
  public String getPhone() {
    return phone;
  }

  /**
   * Updates the phone number.
   *
   * @param phone phone number
   */
  public void setPhone(String phone) {
    this.phone = phone;
  }

  /**
   * Returns the system role.
   *
   * @return system role
   */
  public SystemRole getSystemRole() {
    return systemRole;
  }

  /**
   * Returns the system role using the legacy accessor name.
   *
   * @return system role
   */
  public SystemRole getRole() {
    return systemRole;
  }

  /**
   * Updates the system role.
   *
   * @param systemRole role used for dashboard routing
   */
  public void setSystemRole(SystemRole systemRole) {
    this.systemRole = systemRole;
  }

  /**
   * Updates the system role using the legacy mutator name.
   *
   * @param systemRole role used for dashboard routing
   */
  public void setRole(SystemRole systemRole) {
    this.systemRole = systemRole;
  }

  /**
   * Returns the account status.
   *
   * @return account status
   */
  public AccountStatus getAccountStatus() {
    return accountStatus;
  }

  /**
   * Returns the account status using the legacy accessor name.
   *
   * @return account status
   */
  public AccountStatus getStatus() {
    return accountStatus;
  }

  /**
   * Updates the account status.
   *
   * @param accountStatus account status
   */
  public void setAccountStatus(AccountStatus accountStatus) {
    this.accountStatus = accountStatus;
  }

  /**
   * Updates the account status using the legacy mutator name.
   *
   * @param accountStatus account status
   */
  public void setStatus(AccountStatus accountStatus) {
    this.accountStatus = accountStatus;
  }

  /**
   * Returns whether the user is active.
   *
   * @return true when active
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Updates whether the user is active.
   *
   * @param active true when active
   */
  public void setActive(boolean active) {
    this.active = active;
  }
}
