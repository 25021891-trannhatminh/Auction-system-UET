package server.common.entity;

public class AuctionItem {
    private String id;
    private String name;
    private int highestBid = 0;
    private String highestBidder = "None";

    public AuctionItem(String id,String name) {
        this.id = id;
        this.name = name;
    }

    public synchronized boolean bid(String user, int amount) {
        if (amount > highestBid) {
            highestBid = amount;
            highestBidder = user;
            return true;
        }
        return false;
    }

    public String getInfo() {
        return id + " " +name+ " " + highestBid + " " + highestBidder;
    }
}
