package server.network;

import java.net.Socket;
import java.io.*;
import java.util.*;

import server.common.entity.User;
import server.repository.UserDAO;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username = "Guest"; // Tên hiển thị mặc định

    private UserDAO userDAO = new UserDAO();

    // Giả sử AuctionItem là một class hỗ trợ lưu thông tin vật phẩm
    private static Map<String, AuctionItem> items = new HashMap<>();

    static {
        items.put("1", new AuctionItem("1", "Item-1"));
        items.put("2", new AuctionItem("2", "Item-2"));
    }

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                handle(msg);
            }
        } catch (Exception e) {
            System.out.println("Client " + username + " disconnected");
        } finally {
            ClientManager.remove(this);
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void handle(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;

        // Dùng giới hạn split để tránh lỗi khi description hoặc name có khoảng trắng
        String[] p = msg.split(" ", 6);
        if ("PING".equals(p[0])){
            send("PONG");
            return;
        }
        System.out.println("MSG FROM CLIENT: " + msg);

        switch (p[0]) {
            case "LOGIN":
                if (p.length < 3) {
                    send("LOGIN_FAIL MISSING_CREDENTIALS");
                    return;
                }
                String identifier = p[1]; // Có thể là username hoặc email theo UserDAO
                String pass = p[2];

                try {
                    User u = userDAO.login(identifier, pass);
                    if (u != null) {
                        // Cập nhật username của session này từ DB
                        this.username = u.getUsername();
                        send("LOGIN_SUCCESS " + u.getUsername() + " " + u.getRole() + " " + u.getStatus());
                    } else {
                        send("LOGIN_FAIL INVALID_AUTH");
                    }
                } catch (Exception e) {
                    send("LOGIN_FAIL SERVER_ERROR");
                    e.printStackTrace();
                }
                break;

            case "REGISTER":
                // Format mong muốn: REGISTER <user> <pass> <email> <fullName> <phone>
                if (p.length < 6) {
                    send("REGISTER_FAIL INVALID_FORMAT_REQUIRES_5_FIELDS");
                    return;
                }

                String regUser = p[1];
                String regPass = p[2];
                String regEmail = p[3];
                String regFullName = p[4];
                String regPhone = p[5];

                try {
                    // Gọi đúng hàm register đã sửa trong UserDAO (5 tham số)
                    boolean ok = userDAO.register(regUser, regPass, regEmail, regFullName, regPhone);
                    if (ok) {
                        send("REGISTER_SUCCESS");
                    } else {
                        send("REGISTER_FAIL_EXIST_OR_ERROR");
                    }
                } catch (Exception e) {
                    send("REGISTER_FAIL_SERVER_ERROR");
                    e.printStackTrace();
                }
                break;

            case "LIST":
                for (AuctionItem item : items.values()) {
                    send("ITEM " + item.getInfo());
                }
                break;

            case "BID":
                if (p.length < 3) {
                    send("FAIL INVALID_BID_FORMAT");
                    return;
                }
                String itemId = p[1];
                try {
                    int price = Integer.parseInt(p[2]);
                    AuctionItem itemObj = items.get(itemId);

                    if (itemObj != null && itemObj.bid(this.username, price)) {
                        ClientManager.broadcast("NEW_BID " + this.username + " " + itemObj.getInfo());
                    } else {
                        send("FAIL Bid too low or item not found");
                    }
                } catch (NumberFormatException e) {
                    send("FAIL PRICE_MUST_BE_NUMBER");
                }
                break;

            case "MSG":
                if (msg.length() > 4) {
                    ClientManager.broadcast("MSG " + this.username + ": " + msg.substring(4).trim());
                }
                break;
                

            default:
                send("UNKNOWN_COMMAND");
                break;
        }
    }
}