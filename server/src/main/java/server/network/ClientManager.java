package server.network;

import server.handler.ClientHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private static final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void add(int userId, ClientHandler handler) {
        clients.put(userId, handler);
    }

    public static void remove(int userId) {
        clients.remove(userId);
    }
    public static ClientHandler getHandler(int userId) {
        return clients.get(userId);
    }

    public static boolean sendToUser(int userId, String msg) {
        ClientHandler handler = clients.get(userId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false; // Người dùng không online
    }

    public static void broadcast(String msg) {
        clients.values().forEach(handler -> handler.send(msg));
    }
    public static boolean isOnline(int userId) {
        return clients.containsKey(userId);
    }
}