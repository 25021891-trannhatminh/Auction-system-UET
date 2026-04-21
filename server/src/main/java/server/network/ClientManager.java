package server.network;

import java.util.*;

public class ClientManager {
    private static final List<ClientHandler> clients = new ArrayList<>();

    public static synchronized void add(ClientHandler c) {
        clients.add(c);
    }

    public static synchronized void remove(ClientHandler c) {
        clients.remove(c);
    }

    public static synchronized void broadcast(String msg) {
        for (ClientHandler c : clients) {
            c.send(msg);
        }
    }
}
