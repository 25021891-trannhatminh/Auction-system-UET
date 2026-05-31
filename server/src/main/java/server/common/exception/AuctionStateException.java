package server.common.exception;

import server.common.enums.AuctionStatus;


/**
 * Ném ra khi cố chuyển trạng thái Auction không hợp lệ theo vòng đời:
 *   OPEN → RUNNING → FINISHED/CANCELED → PAID
 */

public class AuctionStateException extends RuntimeException {
    private final int auctionId;
    private final AuctionStatus currentStatus;

    public AuctionStateException(int auctionId, AuctionStatus currentStatus) {
        super("Auction [" + auctionId + "] is not RUNNING. Current status: " + currentStatus);
        this.auctionId = auctionId;
        this.currentStatus = currentStatus;
    }

    public int getAuctionId()           { return auctionId; }
    public AuctionStatus getCurrentStatus(){ return currentStatus; }
}
