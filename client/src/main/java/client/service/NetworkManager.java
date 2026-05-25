package client.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Maintains the TCP connection between the JavaFX client and auction server.
 */
public class NetworkManager {
    private static NetworkManager instance;

    private static final int PORT = 6666;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int BASE_RETRY_DELAY = 1000;
    private static final int MAX_RETRY_DELAY = 30000;
    private static final int HEARTBEAT_INTERVAL = 5000;
    private static final int LOG_MESSAGE_LIMIT = 500;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final List<Consumer<String>> handlers = new CopyOnWriteArrayList<>();
    private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile boolean isConnecting = false;
    private volatile String activeHost;

    /**
     * Opens the client connection and starts the heartbeat loop.
     */
    public NetworkManager() {
        connect();
        startHeartbeat();
    }

    /**
     * Returns the shared network manager so every screen uses the same socket and message queue.
     *
     * @return singleton {@link NetworkManager} instance
     */
    public static synchronized NetworkManager getInstance() {
        if (instance == null) {
            instance = new NetworkManager();
        }
        return instance;
    }

    /**
     * Registers a callback for server messages.
     *
     * @param handler callback invoked for each non-heartbeat server message
     */
    public void addMessageHandler(Consumer<String> handler) {
        if (handler != null && !handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    /**
     * Removes a previously registered server-message callback.
     *
     * @param handler callback to remove from the listener list
     */
    public void removeMessageHandler(Consumer<String> handler) {
        handlers.remove(handler);
    }

    /**
     * Stops reconnect attempts and closes the current socket.
     */
    public void disconnect() {
        shouldReconnect = false;
        markDisconnected();
    }

    /**
     * Starts a reconnect attempt if the socket is not currently writable.
     */
    public void ensureConnected() {
        if (!isConnected()) {
            connect();
        }
    }

    private synchronized void connect() {
        if (isConnecting || isConnected()) {
            return;
        }

        shouldReconnect = true;
        isConnecting = true;

        Thread connectionThread = new Thread(this::runConnectionLoop, "NetworkManager-Connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void runConnectionLoop() {
        int delay = BASE_RETRY_DELAY;

        try {
            while (shouldReconnect && !Thread.currentThread().isInterrupted()) {
                if (isConnected()) {
                    sleep(BASE_RETRY_DELAY);
                    continue;
                }

                boolean connectedThisRound = false;
                for (String host : resolveHostCandidates()) {
                    if (!shouldReconnect || isConnected()) {
                        break;
                    }

                    if (tryConnect(host)) {
                        connectedThisRound = true;
                        activeHost = host;
                        delay = BASE_RETRY_DELAY;
                        flushQueue();
                        listen();
                    }
                }

                if (shouldReconnect && !isConnected()) {
                    if (!connectedThisRound) {
                        System.out.println(
                            "No TCP server is reachable on port " + PORT
                                + ". Start AuctionServer and wait until it prints Port: " + PORT + ".");
                        System.out.println("Reconnect in " + (delay / 1000) + "s");
                    }
                    sleep(delay);
                    delay = Math.min(delay * 2, MAX_RETRY_DELAY);
                }
            }
        } finally {
            isConnecting = false;
        }
    }

    private boolean tryConnect(String host) {
        Socket nextSocket = new Socket();
        try {
            System.out.println("Connecting to " + host + ":" + PORT);
            nextSocket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);

            socket = nextSocket;
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("Connected to " + host + ":" + PORT);
            return true;
        } catch (Exception e) {
            closeQuietly(nextSocket);
            System.out.println("Cannot connect to " + host + ":" + PORT + " - " + e.getMessage());
            return false;
        }
    }

    private List<String> resolveHostCandidates() {
        Set<String> hosts = new LinkedHashSet<>();

        if (activeHost != null && !activeHost.isBlank()) {
            hosts.add(activeHost);
        }

        hosts.addAll(ServerFinder.findCandidates());
        return List.copyOf(hosts);
    }

    private void listen() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                if ("PONG".equals(msg)) {
                    continue;
                }

                for (Consumer<String> handler : handlers) {
                    handler.accept(msg);
                }
            }
        } catch (Exception e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        } finally {
            markDisconnected();
        }
    }

    /**
     * Sends a message immediately or queues it while reconnecting.
     *
     * @param msg protocol message to send to the server
     */
    public void send(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }

        if (!hasWritableSocket()) {
            System.out.println("Queue message: " + safeMessageForLog(msg));
            messageQueue.add(msg);
            ensureConnected();
            return;
        }

        out.println(msg);
        out.flush();
        System.out.println("Sent: " + safeMessageForLog(msg));

        if (out.checkError()) {
            System.out.println("Send failed, queue again: " + safeMessageForLog(msg));
            messageQueue.add(msg);
            markDisconnected();
            ensureConnected();
        }
    }

    private void flushQueue() {
        if (messageQueue.isEmpty()) {
            return;
        }

        System.out.println("Resending queued messages...");

        while (!messageQueue.isEmpty() && hasWritableSocket()) {
            String msg = messageQueue.poll();
            if (msg == null) {
                continue;
            }

            out.println(msg);
            out.flush();
            System.out.println("Sent queued: " + safeMessageForLog(msg));

            if (out.checkError()) {
                System.out.println("Failed to send queued message, put back: " + safeMessageForLog(msg));
                messageQueue.add(msg);
                markDisconnected();
                break;
            }
        }
    }

    /**
     * Builds a compact log entry for socket messages. Image upload commands carry large Base64
     * payloads, so the raw data is hidden to keep the terminal readable.
     *
     * @param msg raw protocol message
     * @return safe message text for debug logs
     */
    private String safeMessageForLog(String msg) {
        if (msg == null) {
            return "";
        }

        if (msg.startsWith("UPLOAD_IMAGE ")) {
            String[] parts = msg.split(" ", 3);
            String fileName = parts.length > 1 ? parts[1] : "unknown";
            int base64Length = parts.length > 2 ? parts[2].length() : 0;
            return "UPLOAD_IMAGE " + fileName + " <base64:" + base64Length + " chars>";
        }

        if (msg.length() > LOG_MESSAGE_LIMIT) {
            return msg.substring(0, LOG_MESSAGE_LIMIT)
                + "... <truncated, " + msg.length() + " chars>";
        }

        return msg;
    }

    private void startHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (shouldReconnect) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);

                    if (hasWritableSocket()) {
                        out.println("PING");
                        out.flush();

                        if (out.checkError()) {
                            markDisconnected();
                            ensureConnected();
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "NetworkManager-Heartbeat");

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private boolean hasWritableSocket() {
        return connected
            && out != null
            && socket != null
            && socket.isConnected()
            && !socket.isClosed()
            && !socket.isOutputShutdown();
    }

    private void markDisconnected() {
        connected = false;
        closeSocket();
        socket = null;
        in = null;
        out = null;
    }

    private void closeSocket() {
        closeQuietly(socket);
    }

    private void closeQuietly(Socket targetSocket) {
        try {
            if (targetSocket != null) {
                targetSocket.close();
            }
        } catch (Exception ignored) {
            // Socket may already be closed by the server or another client path.
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Indicates whether the current socket is connected and writable.
     *
     * @return true when the client can currently send messages to the server
     */
    public boolean isConnected() {
        return hasWritableSocket();
    }
}
