package client.service;

import java.net.Socket;
import java.util.function.Consumer;
import java.io.*;

public class NetworkManager {

    private static final String HOST = "192.168.1.16";
    private static final int PORT = 6666;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private Consumer<String> messageHandler;

    public NetworkManager() {
        connect();
    }

    public void setMessageHandler(Consumer<String> handler){
        this.messageHandler = handler;
    }
    private void connect() {
        new Thread(() -> {
            while (true) {
                try {
                    socket = new Socket(HOST, PORT);

                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream(), true);

                    System.out.println("Connected to server");

                    listen();

                } catch (Exception e) {
                    System.out.println("Reconnect in 3s...");
                    try { Thread.sleep(3000); } catch (Exception ex) {}
                }
            }
        }).start();
    }

    private void listen() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println(msg);
                // quan trọng
                if (messageHandler != null){
                    messageHandler.accept(msg);
                }
            }
        } catch (Exception e) {
            System.out.println("Disconnected");
        }
    }

    public void send(String msg) {
        if (out == null) {
            System.out.println("Not connect yet!");
            return;
        }
        out.println(msg);
    }
}