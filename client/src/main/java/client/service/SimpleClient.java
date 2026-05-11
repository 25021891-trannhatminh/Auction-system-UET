package client.service;

import java.util.Scanner;

public class SimpleClient {
    public static void main(String[] args) {
        NetworkManager net = new NetworkManager();
        NotificationHandler notifHandler = new NotificationHandler();

        net.setMessageHandler(msg -> {
            if (msg.startsWith("PUSH_NOTIF|")) {
                notifHandler.handle(msg);
            }
            else {
                System.out.println("📩 Server: " + msg);
            }
        });

        Scanner sc = new Scanner(System.in);

        while (true) {
            String input = sc.nextLine();
            net.send(input);
        }
    }
}
