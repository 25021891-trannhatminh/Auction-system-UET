package client.service;

import java.util.Scanner;

/**
 * Console client used for quick manual testing of server protocol messages.
 */
public class SimpleClient {
    /**
     * Starts a console loop that forwards typed lines to the server.
     *
     * @param args command-line arguments, currently unused
     */
    public static void main(String[] args) {
        NetworkManager net = new NetworkManager();
        NotificationUIHandler notifHandler = new NotificationUIHandler();

        net.setMessageHandler(msg -> {
            if (msg.startsWith("PUSH_NOTIF|")) {
                notifHandler.handle(msg);
            } else {
                System.out.println("📩 Server: " + msg);
            }
        });

        Scanner scanner = new Scanner(System.in);

        while (true) {
            String input = scanner.nextLine();
            net.send(input);
        }
    }
}
