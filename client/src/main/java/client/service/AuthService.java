package client.service;

public class AuthService {

    private NetworkManager networkManager;

    // constructor nhận NetworkManager
    public AuthService(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    // LOGIN → gửi lên server
    public void login(String identity, String password) {
        networkManager.send("LOGIN " + identity + " " + password);
    }
    public void register(String username, String email, String password) {
        networkManager.send("REGISTER " + username + " " + email + " " + password);
    }
}