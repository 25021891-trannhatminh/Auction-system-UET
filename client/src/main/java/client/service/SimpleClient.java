package client.service;

import java.util.Scanner;
import java.util.function.Consumer;

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
        NetworkManager net = NetworkManager.getInstance();
        NotificationUIHandler notifHandler = new NotificationUIHandler();

        Consumer<String> consoleHandler = msg -> {
            if (msg.startsWith("PUSH_NOTIF|")) {
                notifHandler.handle(msg);
            } else {
                System.out.println("📩 Server: " + msg);
            }
        };
        net.addMessageHandler(consoleHandler);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            String input = scanner.nextLine();
            net.send(input);
        }
    }
}
