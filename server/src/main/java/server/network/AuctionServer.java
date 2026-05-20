package server.network;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

import server.handler.ClientHandler;
import server.service.AuctionService;
import server.service.NotificationService;
import server.service.PaymentService;
import server.service.listeners.NotificationEventHandler;
import server.service.listeners.PaymentTriggerObserver;

public class AuctionServer {
    public static void main(String[] args) {
        ServerDiscovery.start();
        int port = 6666;
        PaymentTriggerObserver paymentObserver = null;
        try {
            // Khởi tạo các cục máy xử lý chính
            NotificationService notifService = new NotificationService();
            PaymentService paymentService = new PaymentService();
            AuctionService auctionService = new AuctionService();

            // Đăng ký "Người nghe": Khi AuctionService có tin mới, nó sẽ tự báo cho NotificationHandler
            NotificationEventHandler notifHandler = new NotificationEventHandler(notifService);
            auctionService.addListener(notifHandler);

            paymentObserver = new PaymentTriggerObserver(paymentService);
            auctionService.addListener(paymentObserver);


            // ── Load trạng thái đang chạy từ DB vào RAM ───────────────────────
            // Cần gọi TRƯỚC khi nhận request, để các auction RUNNING/OPEN đã có
            // scheduler và autoBid engine sẵn sàng
            auctionService.loadAllFromDatabase();

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

                ClientHandler handler = new ClientHandler(socket,auctionService);
                // Dùng Virtual Thread thay Thread thường
                // → Nhẹ hơn ~1000 lần, phù hợp khi nhiều client chờ DB cùng lúc
                Thread.ofVirtual().start(handler);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}