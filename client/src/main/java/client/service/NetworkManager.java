package client.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class NetworkManager {

    private static final String HOST = ServerFinder.find();
    private static final int PORT = 6666;

    private static final int BASE_RETRY_DELAY = 1000;
    private static final int MAX_RETRY_DELAY = 30000;

    private static final int HEARTBEAT_INTERVAL = 5000; // 5s

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private Consumer<String> messageHandler;

    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile boolean isConnecting = false;

    // Queue để giữ message khi mất mạng
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public NetworkManager() {
        connect();
        startHeartbeat();
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public void disconnect() {
        shouldReconnect = false;
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    // ================== CONNECT ==================
    private void connect() {
        if (isConnecting) return;

        new Thread(() -> {
            isConnecting = true;
            int delay = BASE_RETRY_DELAY;

            while (shouldReconnect && !connected) {
                try {
                    System.out.println("Connecting to " + HOST + ":" + PORT);

                    socket = new Socket();
                    socket.connect(new InetSocketAddress(HOST, PORT), 5000);

                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream(), true);

                    connected = true;
                    delay = BASE_RETRY_DELAY;

                    System.out.println("✅ Connected");

                    // gửi lại message bị pending
                    flushQueue();

                    listen();

                } catch (Exception e) {
                    connected = false;

                    if (!shouldReconnect) break;

                    System.out.println("⚠️ Reconnect in " + (delay / 1000) + "s");

                    sleep(delay);
                    delay = Math.min(delay * 2, MAX_RETRY_DELAY);
                }
            }

            isConnecting = false;
        }).start();
    }

    // ================== LISTEN ==================
    private void listen() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {

                // xử lý heartbeat response
                if ("PONG".equals(msg)) continue;

                if (messageHandler != null) {
                    messageHandler.accept(msg);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Disconnected");
        } finally {
            connected = false;
            closeSocket();
        }
    }

    // ================== SEND ==================
    public void send(String msg) {
        if (!connected || out == null) {
            System.out.println("⚠️ Queue message: " + msg);
            messageQueue.add(msg);
            return;
        }

        out.println(msg);
    }

    // ================== FLUSH QUEUE ==================
    private void flushQueue() {
        System.out.println("🔄 Resending queued messages...");

        while (!messageQueue.isEmpty()) {
            String msg = messageQueue.poll();
            if (msg != null) {
                out.println(msg);
            }
        }
    }

    // ================== HEARTBEAT ==================
    private void startHeartbeat() {
        new Thread(() -> {
            while (shouldReconnect) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);

                    if (connected && out != null) {
                        out.println("PING");
                    }

                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    // ================== UTILS ==================
    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    public boolean isConnected() {
        return connected;
    }
}