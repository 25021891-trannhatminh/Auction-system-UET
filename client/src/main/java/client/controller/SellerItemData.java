package client.controller;

/**
 * Luu du lieu item cua seller de render man hinh My Items.
 */
final class SellerItemData {
  final String itemId;
  final String sellerId;
  final String category;
  final String name;
  final String description;
  final String startingPrice;
  final String status;
  final String createdAt;
  final String auctionId;
  final String auctionStatus;
  final String currentPrice;
  final int bidCount;
  final String imagePath;
  final String imagePayload;
  final String attributes;
  final String highestBidderId;
  final String highestBidder;
  final String auctionStartTime;
  final String auctionEndTime;
  final long secondsLeft;

  /**
   * Tao du lieu item cua seller kem thong tin auction neu item dang duoc dau gia.
   *
   * @param itemId ma item
   * @param sellerId ma seller so huu item
   * @param category danh muc item
   * @param name ten item
   * @param description mo ta item
   * @param startingPrice gia khoi diem
   * @param status trang thai item
   * @param createdAt thoi diem tao item
   * @param auctionId ma auction lien ket neu co
   * @param auctionStatus trang thai auction lien ket
   * @param currentPrice gia hien tai cua auction
   * @param bidCount so bid hien tai
   * @param imagePath duong dan anh dai dien
   * @param imagePayload payload anh tra ve tu server
   * @param attributes thuoc tinh mo rong cua item
   * @param highestBidderId ma user dang giu gia cao nhat neu co
   * @param highestBidder ten user dang giu gia cao nhat neu co
   * @param auctionStartTime thoi gian bat dau auction
   * @param auctionEndTime thoi gian ket thuc auction
   * @param secondsLeft so giay con lai cua auction
   */
  SellerItemData(
      String itemId,
      String sellerId,
      String category,
      String name,
      String description,
      String startingPrice,
      String status,
      String createdAt,
      String auctionId,
      String auctionStatus,
      String currentPrice,
      int bidCount,
      String imagePath,
      String imagePayload,
      String attributes,
      String highestBidderId,
      String highestBidder,
      String auctionStartTime,
      String auctionEndTime,
      long secondsLeft) {
    this.itemId = itemId;
    this.sellerId = sellerId;
    this.category = category;
    this.name = name;
    this.description = description;
    this.startingPrice = startingPrice;
    this.status = status;
    this.createdAt = createdAt;
    this.auctionId = auctionId == null ? "" : auctionId;
    this.auctionStatus = auctionStatus == null ? "" : auctionStatus;
    this.currentPrice = currentPrice;
    this.bidCount = bidCount;
    this.imagePath = imagePath;
    this.imagePayload = imagePayload == null ? "" : imagePayload;
    this.attributes = attributes == null ? "" : attributes;
    this.highestBidderId = highestBidderId == null ? "" : highestBidderId;
    this.highestBidder = highestBidder == null ? "" : highestBidder;
    this.auctionStartTime = auctionStartTime == null ? "" : auctionStartTime;
    this.auctionEndTime = auctionEndTime == null ? "" : auctionEndTime;
    this.secondsLeft = Math.max(0L, secondsLeft);
  }
}
