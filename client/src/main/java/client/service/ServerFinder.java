package client.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Discovers the auction server address through UDP broadcast fallback logic.
 */
public class ServerFinder {
  private static final int CONNECT_PORT = 9999;
  private static final int TIMEOUT_MS = 5000;

  /**
   * Finds the server IP address or falls back to localhost.
   *
   * @return discovered server address, or localhost when discovery fails
   */
  public static String find() {
    try {
      DatagramSocket udp = new DatagramSocket(CONNECT_PORT);
      udp.setSoTimeout(TIMEOUT_MS);
      DatagramPacket packet = new DatagramPacket(new byte[256], 256);
      udp.receive(packet);
      String serverIp = packet.getAddress().getHostAddress();
      udp.close();
      return serverIp;
    } catch (Exception e) {
      System.out.println("Không tìm thấy server -> dùng localhost");
      return "localhost";
    }
  }
}
