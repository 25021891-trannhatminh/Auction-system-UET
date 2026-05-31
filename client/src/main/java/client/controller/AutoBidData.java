package client.controller;

/**
 * Lightweight data holder for persisted auto-bid rules displayed in the user dashboard.
 */
final class AutoBidData {
  final String autoBidId;
  final String auctionId;
  final String itemId;
  final String itemName;
  final String category;
  final String currentPrice;
  final String maxBid;
  final String increment;
  final String status;
  final String endTime;
  final String seller;
  final String imagePath;
  final long secondsLeft;

  AutoBidData(
      String autoBidId,
      String auctionId,
      String itemId,
      String itemName,
      String category,
      String currentPrice,
      String maxBid,
      String increment,
      String status,
      String endTime,
      String seller,
      String imagePath,
      long secondsLeft) {
    this.autoBidId = autoBidId == null ? "" : autoBidId;
    this.auctionId = auctionId == null ? "" : auctionId;
    this.itemId = itemId == null ? "" : itemId;
    this.itemName = itemName == null ? "Auction" : itemName;
    this.category = category == null ? "" : category;
    this.currentPrice = currentPrice == null ? "" : currentPrice;
    this.maxBid = maxBid == null ? "" : maxBid;
    this.increment = increment == null ? "" : increment;
    this.status = status == null ? "" : status;
    this.endTime = endTime == null ? "" : endTime;
    this.seller = seller == null ? "" : seller;
    this.imagePath = imagePath == null ? "" : imagePath;
    this.secondsLeft = secondsLeft;
  }
}
