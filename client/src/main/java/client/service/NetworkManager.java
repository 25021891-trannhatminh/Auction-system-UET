package client.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Maintains the TCP connection between the JavaFX client and auction server.
 */
public class NetworkManager {

  private static final String HOST = ServerFinder.find();
  private static final int PORT = 6666;

  private static final int BASE_RETRY_DELAY = 1000;
  private static final int MAX_RETRY_DELAY = 30000;

  private static final int HEARTBEAT_INTERVAL = 5000;

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;

  private Consumer<String> messageHandler;

  private volatile boolean connected = false;
  private volatile boolean shouldReconnect = true;
  private volatile boolean isConnecting = false;

  private final Queue<String> messageQueue = new ConcurrentLinkedQueue<>();

  /**
   * Opens the client connection and starts the heartbeat loop.
   */
  public NetworkManager() {
    connect();
    startHeartbeat();
  }

  /**
   * Registers a callback for server messages.
   *
   * @param handler callback invoked for each non-heartbeat server message
   */
  public void setMessageHandler(Consumer<String> handler) {
    this.messageHandler = handler;
  }

  /**
   * Stops reconnect attempts and closes the current socket.
   */
  public void disconnect() {
    shouldReconnect = false;
    connected = false;
    try {
      if (socket != null) {
        socket.close();
      }
    } catch (Exception ignored) {
      // Best-effort disconnect only.
    }
  }

  private void connect() {
    if (isConnecting) {
      return;
    }

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

          flushQueue();
          listen();
        } catch (Exception e) {
          connected = false;

          if (!shouldReconnect) {
            break;
          }

          System.out.println("⚠️ Reconnect in " + (delay / 1000) + "s");

          sleep(delay);
          delay = Math.min(delay * 2, MAX_RETRY_DELAY);
        }
      }

      isConnecting = false;
    }).start();
  }

  private void listen() {
    try {
      String msg;
      while ((msg = in.readLine()) != null) {
        if ("PONG".equals(msg)) {
          continue;
        }

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

  /**
   * Sends a message immediately or queues it while reconnecting.
   *
   * @param msg protocol message to send to the server
   */
  public void send(String msg) {
    if (!connected || out == null) {
      System.out.println("⚠️ Queue message: " + msg);
      messageQueue.add(msg);
      return;
    }

    out.println(msg);
  }

  private void flushQueue() {
    System.out.println("🔄 Resending queued messages...");

    while (!messageQueue.isEmpty()) {
      String msg = messageQueue.poll();
      if (msg != null) {
        out.println(msg);
      }
    }
  }

  private void startHeartbeat() {
    new Thread(() -> {
      while (shouldReconnect) {
        try {
          Thread.sleep(HEARTBEAT_INTERVAL);

          if (connected && out != null) {
            out.println("PING");
          }
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }).start();
  }

  private void closeSocket() {
    try {
      if (socket != null) {
        socket.close();
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
   * Indicates whether the current socket is connected.
   *
   * @return true when the client is connected to the server
   */
  public boolean isConnected() {
    return connected;
  }
}
