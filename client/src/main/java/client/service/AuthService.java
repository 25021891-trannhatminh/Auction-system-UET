package client.service;

public class AuthService {

    private final NetworkManager networkManager;

    public AuthService(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void login(String identity, String password) {
        networkManager.send("LOGIN " + identity + " " + password);
    }

    public void register(String username, String password, String email, String fullName, String phone) {
        networkManager.send("REGISTER " + username + " " + password + " " + email + " " + fullName + " " + phone);
    }
}