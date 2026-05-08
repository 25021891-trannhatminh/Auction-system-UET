package server.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

public class AuctionServer {
    public static void main(String[] args) {
        ServerDiscovery.start();
        int port = 6666;

        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            System.out.println("Server IP: " + ip);
            System.out.println("Port: " + port);

            ServerSocket server = new ServerSocket(port);

            while (true) {
                Socket socket = server.accept();
                System.out.println("Client connected");

                ClientHandler handler = new ClientHandler(socket);
                ClientManager.add(handler);

                new Thread(handler).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}