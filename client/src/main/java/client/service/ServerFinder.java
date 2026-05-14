package client.service;

import java.net.*;

public class ServerFinder {
    private static final int CONNECT_PORT = 9999;
    private static final int TIMEOUT_MS = 5000;
    public static String find() {
        try {
            DatagramSocket udp = new DatagramSocket(CONNECT_PORT);
            udp.setSoTimeout(TIMEOUT_MS); // chờ tối đa 5 giây nếu không có Data từ Server throw Exception
            DatagramPacket packet = new DatagramPacket(new byte[256], 256);
            udp.receive(packet);
            String serverIP = packet.getAddress().getHostAddress();
            udp.close();
            return serverIP;
        } catch (Exception e) {
            System.out.println("Không tìm thấy server-> dùng localhost");
            return "localhost"; // fallback nếu không tìm thấy
        }
    }
}