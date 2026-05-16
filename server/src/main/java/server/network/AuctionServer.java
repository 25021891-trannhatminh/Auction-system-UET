package server.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import server.service.AuctionService;
import server.service.NotificationService;
import server.service.listeners.NotificationEventHandler;

public class AuctionServer {
    public static void main(String[] args) {
        ServerDiscovery.start();
        int port = 6666;

        try {
            // Khởi tạo các cục máy xử lý chính
            NotificationService notifService = new NotificationService();
            AuctionService auctionService = new AuctionService();

            // Đăng ký "Người nghe": Khi AuctionService có tin mới, nó sẽ tự báo cho NotificationHandler
            NotificationEventHandler notifHandler = new NotificationEventHandler(notifService);
            auctionService.addListener(notifHandler);

            // Chạy luồng gửi tin nhắn ngầm (Dispatcher)
            Thread dispatcherThread = new Thread(NotificationDispatcher.getInstance());
            dispatcherThread.setDaemon(true); // Tự tắt khi server tắt
            dispatcherThread.setName("Notif-Dispatcher");
            dispatcherThread.start();


            String ip = InetAddress.getLocalHost().getHostAddress();
            System.out.println("Server IP: " + ip);
            System.out.println("Port: " + port);

            ServerSocket server = new ServerSocket(port);

            while (true) {
                Socket socket = server.accept();
                System.out.println("Client connected");

                ClientHandler handler = new ClientHandler(socket);
                
                // Dùng Virtual Thread thay Thread thường
                // → Nhẹ hơn ~1000 lần, phù hợp khi nhiều client chờ DB cùng lúc
                Thread.ofVirtual().start(handler);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}