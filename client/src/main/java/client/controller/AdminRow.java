package client.controller;

/**
 * Row model rendered by {@link AdminDashboardController}.
 *
 * <p>This used to live inside the controller; it is package-private so the controller can keep
 * the same rendering flow without exposing dashboard internals publicly.</p>
 */
final class AdminRow {
  final String title;
  final String meta;
  final String firstValue;
  final String secondValue;
  final String status;
  final String detail;
  final String[] actions;
  final boolean itemData;
  final String itemId;
  final String sellerId;
  final String sellerName;
  final String itemName;
  final String description;
  final String startingPrice;
  final String createdAt;
  final String auctionId;
  final String auctionStatus;
  final String currentPrice;
  final String bidCount;
  final String imagePayload;
  final String attributes;

  AdminRow(
          String title,
          String meta,
          String firstValue,
          String secondValue,
          String status,
          String detail,
          String... actions) {
          this.title = title;
          this.meta = meta;
          this.firstValue = firstValue;
          this.secondValue = secondValue;
          this.status = status;
          this.detail = detail;
          this.actions = actions;
          this.itemData = false;
          this.itemId = "";
          this.sellerId = "";
          this.sellerName = "";
          this.itemName = title;
          this.description = detail;
          this.startingPrice = secondValue;
          this.createdAt = "";
          this.auctionId = "";
          this.auctionStatus = "";
          this.currentPrice = "";
          this.bidCount = "";
          this.imagePayload = "";
          this.attributes = "";
      }

  AdminRow(
          String title,
          String meta,
          String firstValue,
          String secondValue,
          String status,
          String detail,
          String itemId,
          String sellerId,
          String sellerName,
          String itemName,
          String description,
          String startingPrice,
          String createdAt,
          String auctionId,
          String auctionStatus,
          String currentPrice,
          String bidCount,
          String imagePayload,
          String attributes,
          String... actions) {
          this.title = title;
          this.meta = meta;
          this.firstValue = firstValue;
          this.secondValue = secondValue;
          this.status = status;
          this.detail = detail;
          this.actions = actions;
          this.itemData = true;
          this.itemId = itemId;
          this.sellerId = sellerId;
          this.sellerName = sellerName;
          this.itemName = itemName;
          this.description = description;
          this.startingPrice = startingPrice;
          this.createdAt = createdAt;
          this.auctionId = auctionId;
          this.auctionStatus = auctionStatus;
          this.currentPrice = currentPrice;
          this.bidCount = bidCount;
          this.imagePayload = imagePayload;
          this.attributes = attributes;
      }
}
