package server.common.entity.exception;

import server.common.enums.AuctionStatus;


//  Ném ra khi cố đặt giá vào một phiên không ở trạng thái RUNNING.

public class AuctionClosedException extends RuntimeException {
    private final int auctionId;
    private final AuctionStatus currentStatus;

    public AuctionClosedException(int auctionId, AuctionStatus currentStatus) {
        super("Auction [" + auctionId + "] is not RUNNING. Current status: " + currentStatus);
        this.auctionId = auctionId;
        this.currentStatus = currentStatus;
    }

    public int getAuctionId()           { return auctionId; }
    public AuctionStatus getCurrentStatus(){ return currentStatus; }
}
