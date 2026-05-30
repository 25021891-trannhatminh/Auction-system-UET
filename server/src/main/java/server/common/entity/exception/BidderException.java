package server.common.entity.exception;

import server.common.enums.UserStatus;

/**
 * Ném ra khi người bán (Seller) cố đặt giá vào chính phiên đấu giá của mình.
 * Status Bidder không hợp lệ
 */

public class BidderException extends RuntimeException {

  private final int bidderId;

  public BidderException(int bidderId, int auctionId) {
    super(String.format(
        "User [%d] cannot bid on their own auction [%d]", bidderId, auctionId));
    this.bidderId  = bidderId;
  }

  public BidderException(int bidderId, UserStatus status){
    super(String.format(
        "User [%d] cannot bid because UserStatus is [%s]", bidderId, status));
    this.bidderId  = bidderId;
  }

  public int getBidderId()  { return bidderId; }
}
