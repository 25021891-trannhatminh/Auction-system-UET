package client.controller;

/**
 * Luu mot dong lich su dat gia cua auction trong man seller view.
 *
 * <p>Class nay chi chua du lieu hien thi, khong gui request va khong xu ly
 * logic dat gia.</p>
 */
final class BidHistoryData {
  final String auctionId;
  final String bidId;
  final String bidderId;
  final String bidderName;
  final String amount;
  final String status;
  final boolean autoBid;
  final String bidTime;

  /**
   * Tao mot dong lich su bid da duoc chuan hoa cho bang seller view.
   *
   * @param auctionId ma auction lien quan
   * @param bidId ma bid
   * @param bidderId ma user dat gia
   * @param bidderName ten hien thi cua user dat gia
   * @param amount so tien da dat
   * @param status trang thai bid tai thoi diem hien thi
   * @param autoBid bid co duoc dat tu dong hay khong
   * @param bidTime thoi diem dat gia
   */
  BidHistoryData(
      String auctionId,
      String bidId,
      String bidderId,
      String bidderName,
      String amount,
      String status,
      boolean autoBid,
      String bidTime) {
    this.auctionId = auctionId == null ? "" : auctionId;
    this.bidId = bidId == null ? "" : bidId;
    this.bidderId = bidderId == null ? "" : bidderId;
    this.bidderName = bidderName == null ? "" : bidderName;
    this.amount = amount == null ? "" : amount;
    this.status = status == null ? "" : status;
    this.autoBid = autoBid;
    this.bidTime = bidTime == null ? "" : bidTime;
  }
}
