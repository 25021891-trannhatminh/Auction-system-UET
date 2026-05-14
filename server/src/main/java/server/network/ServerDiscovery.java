package server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerDiscovery {
    public static void start() {
        new Thread(() -> {
            try {
                DatagramSocket udp = new DatagramSocket();
                udp.setBroadcast(true);
                byte[] data = "AUCTION_SERVER:6666".getBytes();
                while (true) {
                    DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"), 9999
                    );
                    udp.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}