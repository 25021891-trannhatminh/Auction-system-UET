package client.service;

import java.net.*;

public class ServerFinder {
    private static final String MULTICAST_GROUP = "239.1.2.3";
    private static final int MULTICAST_PORT = 9999;
    private static final int TIMEOUT_MS = 5000;
    public static String find() {
        try (MulticastSocket multicast = new MulticastSocket(MULTICAST_PORT)){
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            multicast.joinGroup(group);
            multicast.setSoTimeout(TIMEOUT_MS); // chờ tối đa 5 giây nếu không nhận được Data throw SocketTimeoutException

            DatagramPacket packet = new DatagramPacket(new byte[256], 256);
            multicast.receive(packet);
            String serverIp = packet.getAddress().getHostAddress();
            return serverIp;
        } catch (Exception e) {
            System.out.println("Không tìm thấy server-> dùng localhost");
            return "localhost"; // fallback nếu không tìm thấy
        }
    }
}