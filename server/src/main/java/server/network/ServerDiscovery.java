package server.network;

import java.net.*;

public class ServerDiscovery {
    private static final String MULTICAST_GROUP = "239.1.2.3";  // Địa chỉ multicast
    private static final int MULTICAST_PORT = 9999;
    private static final int INTERVAL_MS = 2000;

    public static void start() {
        new Thread(() -> {
            try (MulticastSocket multicast = new MulticastSocket()){
                InetAddress groupIP = InetAddress.getByName(MULTICAST_GROUP);

                byte[] data = "AUCTION_SERVER:6666".getBytes();
                while (true) {
                    DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        groupIP, MULTICAST_PORT
                    );
                    multicast.send(packet);
                    Thread.sleep(INTERVAL_MS);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}