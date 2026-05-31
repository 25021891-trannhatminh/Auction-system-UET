package client.service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers possible auction server hosts through UDP broadcast and safe local fallbacks.
 */
public class ServerFinder {
    private static final int DISCOVERY_PORT = 9999;
    private static final int TIMEOUT_MS = 1200;
    private static final String DISCOVERY_PREFIX = "AUCTION_SERVER:";
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK = "127.0.0.1";
    private static final String HOST_PROPERTY = "auction.server.host";
    private static final String HOST_ENV = "AUCTION_SERVER_HOST";

    /**
     * Keeps compatibility with older code that expects a single host string.
     *
     * @return the first candidate host, or localhost when discovery finds nothing
     */
    public static String find() {
        List<String> candidates = findCandidates();
        return candidates.isEmpty() ? LOCALHOST : candidates.get(0);
    }

    /**
     * Finds all useful host candidates in a deterministic order.
     *
     * <p>The server in refactor(2) starts UDP discovery before opening TCP port 6666. Because of
     * that, a broadcast packet can exist even when the TCP server is not ready. This method only
     * collects candidates; NetworkManager still verifies them with a real TCP connection.</p>
     *
     * @return ordered, duplicate-free host candidates
     */
    public static List<String> findCandidates() {
        Set<String> hosts = new LinkedHashSet<>();

        addIfPresent(hosts, System.getProperty(HOST_PROPERTY));
        addIfPresent(hosts, System.getenv(HOST_ENV));

        // Same-machine server should be tried first because it avoids stale LAN broadcasts.
        hosts.add(LOCALHOST);
        hosts.add(LOOPBACK);

        String discovered = receiveDiscoveryPacket();
        addIfPresent(hosts, discovered);

        return List.copyOf(hosts);
    }

    private static String receiveDiscoveryPacket() {
        try (DatagramSocket udp = new DatagramSocket(DISCOVERY_PORT)) {
            udp.setSoTimeout(TIMEOUT_MS);
            DatagramPacket packet = new DatagramPacket(new byte[256], 256);
            udp.receive(packet);

            String payload = new String(packet.getData(), 0, packet.getLength()).trim();
            if (payload.startsWith(DISCOVERY_PREFIX)) {
                return packet.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            System.out.println("No server broadcast found yet; trying local hosts.");
        }
        return null;
    }

    private static void addIfPresent(Set<String> hosts, String host) {
        if (host == null) {
            return;
        }

        String normalized = host.trim();
        if (!normalized.isEmpty()) {
            hosts.add(normalized);
        }
    }
}
