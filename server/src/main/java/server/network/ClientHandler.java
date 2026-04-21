package server.network;

import java.net.Socket;
import java.io.*;
import java.util.*;

import server.common.model.User;
import server.repository.UserDAO;

public class ClientHandler implements Runnable {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username = "Guest";

    private UserDAO userDAO = new UserDAO();

    private static Map<String, AuctionItem> items = new HashMap<>();

    static {

        items.put("1", new AuctionItem("1","Item-1"));
        items.put("2", new AuctionItem("2","Item-2"));
    }

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String msg;

            while ((msg = in.readLine()) != null) {
                handle(msg);
            }

        } catch (Exception e) {
            System.out.println("Client disconnected");
        } finally {
            ClientManager.remove(this);
        }
    }

    private void handle(String msg) {
        String[] p = msg.split(" ");
        System.out.println("MSG FROM CLIENT: " + msg);
        switch (p[0]) {

            case "LOGIN":
                if (p.length < 3) {
                    send("LOGIN_FAIL");
                    return;
                }
                String user = p[1];
                String pass = p[2];
                 
                try{
                    User u = userDAO.login(user,pass);
                    if (u != null) {
                        send("LOGIN_SUCCESS " + u.getUsername()+" "+u.getRole()+" "+u.getStatus());
                    }else{
                        send("LOGIN_FAIL");
                    }                
                }catch(Exception e){
                    send("LOGIN_FAIL");
                    e.printStackTrace();
                }
                break;

            case "LIST":
                for (AuctionItem item : items.values()) {
                    send("ITEM " + item.getInfo());
                }
                break;

            case "BID":
                String id = p[1];
                int price = Integer.parseInt(p[2]);

                AuctionItem item = items.get(id);

                if (item != null && item.bid(username, price)) {
                    ClientManager.broadcast(
                        "NEW_BID " + username + " " + item.getInfo());
                } else {
                    send("FAIL Bid too low");
                }
                break;

            case "MSG":
                ClientManager.broadcast("MSG " + username + ": " + msg.substring(4));
                break;
            case "REGISTER":
                if(p.length <4){
                    send("REGISTER_FAIL INVALID_FORMAT");
                    return;
                }

                String regUser = p[1];
                String regEmail = p[2];
                String regPass = p[3];

                try{
                    boolean ok = userDAO.register(regUser, regPass, regEmail);
                    if (ok){
                        send("REGISTER_SUCCESS");
                    }else{
                        send("REGISTER_FAIL EXIST");
                    }
                }catch (Exception e){
                    send("REGISTER_FAIL ERROR");
                    e.printStackTrace();
                }
                break;
        }
    }
}