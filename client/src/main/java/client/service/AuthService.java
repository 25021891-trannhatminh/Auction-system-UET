package client.service;

public class AuthService {

    private NetworkManager networkManager;

    public AuthService(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void login(String identity, String password) {
        networkManager.send("LOGIN " + identity + " " + password);
    }

    public void register(String username, String email, String password) {
        networkManager.send("REGISTER " + username + " " + email + " " + password);
    }
}