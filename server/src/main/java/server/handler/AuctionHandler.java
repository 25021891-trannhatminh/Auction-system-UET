package server.handler;

import server.common.ProtocolConstants;
import server.common.entity.Auction;
import server.common.entity.BidTransaction;
import server.common.entity.User;
import server.common.entity.manager.AuctionManager;
import server.common.enums.AuctionStatus;
import server.network.NotificationDispatcher;
import server.service.listeners.RealTimeObserver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Xử lý lệnh JOIN_AUCTION và LEAVE_AUCTION.
 *
 * JOIN_AUCTION  <auctionId> — đăng ký nhận realtime update của phiên, gửi snapshot hiện tại.
 * LEAVE_AUCTION <auctionId> — hủy đăng ký khỏi phiên.
 *
 * Protocol response:
 *   JOIN thành công  → AUCTION_SNAPSHOT|... (phiên đang chạy/mở)
 *                    → AUCTION_CLOSED_UPDATE|... (phiên đã đóng — client render màn kết quả)
 *   JOIN thất bại    → JOIN_AUCTION_FAIL reason
 *   LEAVE thành công → LEAVE_AUCTION_SUCCESS auctionId
 *   LEAVE thất bại   → LEAVE_AUCTION_FAIL reason
 */
public class AuctionHandler {

  private final AuctionManager auctionManager;

  public AuctionHandler(AuctionManager auctionManager) {
    this.auctionManager = auctionManager;
  }

  /**
   * Xử lý lệnh JOIN_AUCTION.
   * Format: JOIN_AUCTION <auctionId>
   *
   * Luồng xử lý:
   *   1. Validate tham số và trạng thái đăng nhập.
   *   2. Tìm auction trong RAM.
   *   3. Đăng ký observer per-auction (không global).
   *   4. Gửi snapshot hiện tại của phiên về client ngay lập tức.
   *
   * @param request  Mảng lệnh từ ClientHandler
   * @param userId   ID người dùng từ session — dùng để check đăng nhập
   * @return Chuỗi snapshot hoặc thông báo lỗi gửi về client
   */
  public String handleJoin(String[] request, int userId) {
    // 1. Kiểm tra đăng nhập
    if (userId <= 0) {
      return ProtocolConstants.JOIN_AUCTION_FAIL + " NOT_LOGGED_IN";
    }

    // 2. Validate tham số
    if (request == null || request.length < 2) {
      return ProtocolConstants.JOIN_AUCTION_FAIL + " INVALID_FORMAT";
    }

    int auctionId;
    try {
      auctionId = Integer.parseInt(request[1]);
    } catch (NumberFormatException e) {
      return ProtocolConstants.JOIN_AUCTION_FAIL + " INVALID_AUCTION_ID";
    }

    // 3. Tìm auction trong RAM
    Optional<Auction> opt = auctionManager.getAuction(auctionId);
    if (opt.isEmpty()) {
      return ProtocolConstants.JOIN_AUCTION_FAIL + " AUCTION_NOT_FOUND";
    }
    Auction auction = opt.get();

    // 4. Phiên đã đóng: trả closed update ngay để client render màn kết quả,
    //    không đăng ký watcher vì không còn event nào sẽ được push.
    if (auction.isFinished()) {
      return buildClosedUpdate(auction);
    }

    // 5. Phiên đang OPEN hoặc RUNNING: đăng ký watcher rồi trả snapshot đầy đủ
    NotificationDispatcher.getInstance().subscribeAuction(auctionId, userId);
    return buildSnapshot(auction);
  }

  /**
   * Xử lý lệnh LEAVE_AUCTION.
   * Format: LEAVE_AUCTION <auctionId>
   *
   * @param request  Mảng lệnh từ ClientHandler
   * @param userId   ID người dùng từ session
   * @return Chuỗi xác nhận hoặc lỗi
   */
  public String handleLeave(String[] request, int userId) {
    if (userId <= 0) {
      return ProtocolConstants.LEAVE_AUCTION_FAIL + " NOT_LOGGED_IN";
    }

    if (request == null || request.length < 2) {
      return ProtocolConstants.LEAVE_AUCTION_FAIL + " INVALID_FORMAT";
    }

    int auctionId;
    try {
      auctionId = Integer.parseInt(request[1]);
    } catch (NumberFormatException e) {
      return ProtocolConstants.LEAVE_AUCTION_FAIL + " INVALID_AUCTION_ID";
    }

    // Không cần kiểm tra auction tồn tại — unsubscribe là no-op nếu không có entry
    NotificationDispatcher.getInstance().unsubscribeAuction(auctionId, userId);
    return ProtocolConstants.LEAVE_AUCTION_SUCCESS + " " + auctionId;
  }

  /**
   * Hủy đăng ký khỏi tất cả phiên — gọi khi client disconnect.
   * Cách đơn giản: duyệt qua tất cả auction trong RAM và remove observer.
   * Chấp nhận được vì disconnect không phải hot path.
   *
   */
  public void handleDisconnect(int userID) {
    NotificationDispatcher.getInstance().unsubscribeAll(userID);
  }

  /**
   * Build snapshot đầy đủ cho phiên đang OPEN hoặc RUNNING.
   * Payload giống AUCTION_BID_UPDATE nhưng có thêm recentBids để client render bảng lịch sử ngay.
   *
   * Format:
   *   AUCTION_SNAPSHOT|auctionId|status|currentPrice|leaderId|leaderName
   *                   |endTime|totalBids|recentBids
   *
   * recentBids: chuỗi CSV, mỗi entry là "amount:bidderId:AUTO|MANUAL"
   *   Ví dụ: "1000000:42:MANUAL,950000:17:AUTO"
   */
  private String buildSnapshot(Auction auction) {
    int leaderId      = auction.getCurrentLeader() != null ? auction.getCurrentLeader().getId()       : -1;
    String leaderName = auction.getCurrentLeader() != null ? auction.getCurrentLeader().getUsername() : "None";

    // 5 bid gần nhất để client render bảng lịch sử không cần round-trip thêm
    List<BidTransaction> recentBids = auction.getRecentBids(5);
    StringBuilder bidsSb = new StringBuilder();
    for (int i = 0; i < recentBids.size(); i++) {
      BidTransaction tx = recentBids.get(i);
      bidsSb.append(tx.getAmount().toPlainString())
          .append(":").append(tx.getBidderId())
          .append(":").append(tx.isAutoBid() ? "AUTO" : "MANUAL");
      if (i < recentBids.size() - 1) bidsSb.append(",");
    }

    return String.format("AUCTION_SNAPSHOT|%d|%s|%s|%d|%s|%s|%d|%s",
        auction.getId(),
        auction.getStatus().name(),
        auction.getCurrentPrice().toPlainString(),
        leaderId,
        leaderName,
        auction.getEndTime().toString(),
        auction.getTotalBids(),
        bidsSb.toString());
  }

  /**
   * Build closed update cho phiên đã FINISHED / CANCELED / PAID.
   * Dùng ResponseBuilder để đảm bảo format nhất quán với push realtime từ AuctionService.
   *
   * Client nhận message này khi JOIN một phiên đã đóng — render màn kết quả ngay
   * mà không cần gọi thêm endpoint nào.
   */
  private String buildClosedUpdate(Auction auction) {
    User winner   = auction.getCurrentLeader();
    int winnerId   = winner != null ? winner.getId()       : -1;
    String winnerName = winner != null ? winner.getUsername() : "None";

    // Nếu CANCELED thì không có winner thật sự dù currentLeader có thể != null
    // (leader bị huỷ do không đạt reserve price) → override về -1
    if (auction.getStatus() == AuctionStatus.CANCELED) {
      winnerId   = -1;
      winnerName = "None";
    }

    return ResponseBuilder.auctionClosedUpdate(
        auction.getId(),
        auction.getStatus(),
        auction.getCurrentPrice(),
        winnerId,
        winnerName);
  }
}