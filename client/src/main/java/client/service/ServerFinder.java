package client.service;

import java.net.*;

public class ServerFinder {
    public static String find() {
        try {
            DatagramSocket udp = new DatagramSocket(9999);
            udp.setSoTimeout(5000); // chờ tối đa 5 giây
            DatagramPacket packet = new DatagramPacket(new byte[256], 256);
            udp.receive(packet);
            udp.close();
            return packet.getAddress().getHostAddress();
        } catch (Exception e) {
            System.out.println("Không tìm thấy server-> dùng localhost");
            return "localhost"; // fallback nếu không tìm thấy
        }
    }
}