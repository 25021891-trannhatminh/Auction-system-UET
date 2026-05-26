package server.common.entity.exception;

/**
 * Ném ra khi người bán (Seller) cố đặt giá vào chính phiên đấu giá của mình.
 *
 * => Nên tách ra riêng để BidHandler phân biệt lý do từ chối và trả đúng error code cho client.
 */

public class SelfBidException extends RuntimeException {

  private final int bidderId;
  private final int auctionId;

  public SelfBidException(int bidderId, int auctionId) {
    super(String.format(
        "User [%d] cannot bid on their own auction [%d]", bidderId, auctionId));
    this.bidderId  = bidderId;
    this.auctionId = auctionId;
  }

  public int getBidderId()  { return bidderId; }
  public int getAuctionId() { return auctionId; }
}
