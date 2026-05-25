package client.controller;

/**
 * Lightweight data holder used by {@link UserDashboardController}.
 */
final class AuctionCardData {
  final String auctionId;
  final String itemId;
  final String title;
  final String category;
  final String description;
  final String price;
  final String minimumIncrement;
  final String reservePrice;
  final int bidCount;
  final String bids;
  final String endsIn;
  final long secondsLeft;
  final String badge;
  final String imagePath;
  final String imagePayload;
  final String detail;
  final String status;
  final String startTime;
  final String endTime;
  final String seller;
  final String winner;
  final String attributes;
  final String snipeWindowSeconds;
  final String snipeExtensionSeconds;

  AuctionCardData(
        String auctionId,
        String itemId,
        String title,
        String category,
        String description,
        String price,
        String minimumIncrement,
        String reservePrice,
        int bidCount,
        String bids,
        String endsIn,
        long secondsLeft,
        String badge,
        String imagePath,
        String imagePayload,
        String detail,
        String status,
        String startTime,
        String endTime,
        String seller,
        String winner,
        String attributes,
        String snipeWindowSeconds,
        String snipeExtensionSeconds) {
      this.auctionId = auctionId;
      this.itemId = itemId;
      this.title = title;
      this.category = category;
      this.description = description;
      this.price = price;
      this.minimumIncrement = minimumIncrement;
      this.reservePrice = reservePrice;
      this.bidCount = bidCount;
      this.bids = bids;
      this.endsIn = endsIn;
      this.secondsLeft = secondsLeft;
      this.badge = badge;
      this.imagePath = imagePath;
      this.imagePayload = imagePayload;
      this.detail = detail;
      this.status = status;
      this.startTime = startTime;
      this.endTime = endTime;
      this.seller = seller;
      this.winner = winner;
      this.attributes = attributes;
      this.snipeWindowSeconds = snipeWindowSeconds;
      this.snipeExtensionSeconds = snipeExtensionSeconds;
    }
}
