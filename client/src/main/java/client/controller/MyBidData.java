package client.controller;

/**
 * Lightweight data holder used by {@link UserDashboardController}.
 */
final class MyBidData {
  final String bidId;
  final String auctionId;
  final String itemId;
  final String itemName;
  final String category;
  final String currentPrice;
  final String userBid;
  final String bidStatus;
  final String auctionStatus;
  final boolean autoBid;
  final String bidTime;
  final String endTime;
  final String winnerId;
  final String imagePath;

  MyBidData(
        String bidId,
        String auctionId,
        String itemId,
        String itemName,
        String category,
        String currentPrice,
        String userBid,
        String bidStatus,
        String auctionStatus,
        boolean autoBid,
        String bidTime,
        String endTime,
        String winnerId,
        String imagePath) {
      this.bidId = bidId == null ? "" : bidId;
      this.auctionId = auctionId == null ? "" : auctionId;
      this.itemId = itemId == null ? "" : itemId;
      this.itemName = itemName == null ? "Auction" : itemName;
      this.category = category == null ? "" : category;
      this.currentPrice = currentPrice == null ? "" : currentPrice;
      this.userBid = userBid == null ? "" : userBid;
      this.bidStatus = bidStatus == null ? "" : bidStatus;
      this.auctionStatus = auctionStatus == null ? "" : auctionStatus;
      this.autoBid = autoBid;
      this.bidTime = bidTime == null ? "" : bidTime;
      this.endTime = endTime == null ? "" : endTime;
      this.winnerId = winnerId == null ? "" : winnerId;
      this.imagePath = imagePath == null ? "" : imagePath;
    }
}
