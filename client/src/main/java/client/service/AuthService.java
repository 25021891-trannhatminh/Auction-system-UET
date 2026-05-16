package client.service;

/**
 * Sends authentication commands from the JavaFX client to the server.
 */
public class AuthService {

  private final NetworkManager networkManager;

  /**
   * Creates an authentication service over the shared network manager.
   *
   * @param networkManager network connection used to send authentication commands
   */
  public AuthService(NetworkManager networkManager) {
    this.networkManager = networkManager;
  }

  /**
   * Sends a login request to the server.
   *
   * @param identity username or email entered by the user
   * @param password raw password entered by the user
   */
  public void login(String identity, String password) {
    networkManager.send("LOGIN " + identity + " " + password);
  }

  /**
   * Sends a registration request to the server.
   *
   * @param username requested username
   * @param password raw password entered by the user
   * @param email Gmail address entered by the user
   * @param fullName full profile name
   * @param phone phone number entered by the user
   */
  public void register(
      String username,
      String password,
      String email,
      String fullName,
      String phone) {
    networkManager.send(
        "REGISTER " + username + " " + password + " " + email + " " + fullName + " " + phone);
  }
}
