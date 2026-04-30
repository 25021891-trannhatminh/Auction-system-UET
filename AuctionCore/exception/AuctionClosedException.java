package exception;

import enums.AuctionStatus;


//  Ném ra khi cố đặt giá vào một phiên không ở trạng thái RUNNING.

public class AuctionClosedException extends RuntimeException {
    private final String auctionId;
    private final AuctionStatus currentStatus;

    public AuctionClosedException(String auctionId, AuctionStatus currentStatus) {
        super("Auction [" + auctionId + "] is not RUNNING. Current status: " + currentStatus);
        this.auctionId = auctionId;
        this.currentStatus = currentStatus;
    }

    public String getAuctionId()           { return auctionId; }
    public AuctionStatus getCurrentStatus(){ return currentStatus; }
}
