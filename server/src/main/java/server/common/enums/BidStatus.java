package server.common.enums;

/*
 * Trạng thái của từng lần đặt giá.
 * WINNING  → bid này đang dẫn đầu
 * OUTBID   → đã bị người khác vượt qua
 * WON      → phiên kết thúc, bid này thắng
 * LOST     → phiên kết thúc, bid này thua
 */
public enum BidStatus {
  WINNING,
  OUTBID,
  WON,
  LOST
}