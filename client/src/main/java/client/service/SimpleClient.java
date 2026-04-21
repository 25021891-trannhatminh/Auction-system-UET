package client.service;

import java.util.Scanner;

public class SimpleClient {
    public static void main(String[] args) {
        NetworkManager net = new NetworkManager();

        Scanner sc = new Scanner(System.in);

        while (true) {
            String input = sc.nextLine();
            net.send(input);
        }
    }
}
