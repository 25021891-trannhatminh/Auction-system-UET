package client.controller;

/**
 * Lightweight data holder used by {@link UserDashboardController}.
 */
final class UserRow {
  final String title;
  final String meta;
  final String firstValue;
  final String secondValue;
  final String status;
  final String detail;
  final String thumbnail;
  final AuctionCardData linkedAuction;
  final String[] actions;

  UserRow(
      String title,
      String meta,
      String firstValue,
      String secondValue,
      String status,
      String detail,
      String thumbnail,
      String... actions) {
    this(title, meta, firstValue, secondValue, status, detail, thumbnail, null, actions);
  }

  UserRow(
      String title,
      String meta,
      String firstValue,
      String secondValue,
      String status,
      String detail,
      String thumbnail,
      AuctionCardData linkedAuction,
      String... actions) {
    this.title = title;
    this.meta = meta;
    this.firstValue = firstValue;
    this.secondValue = secondValue;
    this.status = status;
    this.detail = detail;
    this.thumbnail = thumbnail;
    this.linkedAuction = linkedAuction;
    this.actions = actions;
  }
}
