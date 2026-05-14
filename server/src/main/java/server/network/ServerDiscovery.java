package server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerDiscovery {
    private static final int CONNECT_PORT = 9999;
    private static final int INTERVAL_MS = 2000;
    public static void start() {
        new Thread(() -> {
            try {
                DatagramSocket udp = new DatagramSocket();
                InetAddress connectIP = InetAddress.getByName("255.255.255.255");
                udp.setBroadcast(true);
                byte[] data = "AUCTION_SERVER:6666".getBytes();
                while (true) {
                    DatagramPacket packet = new DatagramPacket(
                        data, data.length, connectIP
                        , CONNECT_PORT
                    );
                    udp.send(packet);
                    Thread.sleep(INTERVAL_MS);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}