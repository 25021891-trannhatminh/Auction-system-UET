package server.handler;

import server.common.entity.Auction;
import server.common.entity.BidTransaction;
import server.common.entity.manager.AuctionManager;
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
 * Không xử lý BID — đó là trách nhiệm của BidHandler.
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
   * @param observer ClientHandler (đã implement RealTimeObserver)
   * @param userId   ID người dùng từ session — dùng để check đăng nhập
   * @return Chuỗi snapshot hoặc thông báo lỗi gửi về client
   */
  public String handleJoin(String[] request, RealTimeObserver observer, int userId) {
    // 1. Kiểm tra đăng nhập
    if (userId <= 0) {
      return "JOIN_AUCTION_FAIL NOT_LOGGED_IN";
    }

    // 2. Validate tham số
    if (request == null || request.length < 2) {
      return "JOIN_AUCTION_FAIL INVALID_FORMAT";
    }

    int auctionId;
    try {
      auctionId = Integer.parseInt(request[1]);
    } catch (NumberFormatException e) {
      return "JOIN_AUCTION_FAIL INVALID_AUCTION_ID";
    }

    // 3. Tìm auction trong RAM
    Optional<Auction> opt = auctionManager.getAuction(auctionId);
    if (opt.isEmpty()) {
      return "JOIN_AUCTION_FAIL AUCTION_NOT_FOUND";
    }
    Auction auction = opt.get();

    // 4. Đăng ký observer vào đúng phiên này (per-auction, không global)
    auctionManager.addObserverToAuction(auctionId, observer);

    // 5. Gửi snapshot hiện tại của phiên về client ngay sau khi join
    return buildSnapshot(auction);
  }

  /**
   * Xử lý lệnh LEAVE_AUCTION.
   * Format: LEAVE_AUCTION <auctionId>
   *
   * @param request  Mảng lệnh từ ClientHandler
   * @param observer ClientHandler (đã implement RealTimeObserver)
   * @param userId   ID người dùng từ session
   * @return Chuỗi xác nhận hoặc lỗi
   */
  public String handleLeave(String[] request, RealTimeObserver observer, int userId) {
    // 1. Kiểm tra đăng nhập
    if (userId <= 0) {
      return "LEAVE_AUCTION_FAIL NOT_LOGGED_IN";
    }

    // 2. Validate tham số
    if (request == null || request.length < 2) {
      return "LEAVE_AUCTION_FAIL INVALID_FORMAT";
    }

    int auctionId;
    try {
      auctionId = Integer.parseInt(request[1]);
    } catch (NumberFormatException e) {
      return "LEAVE_AUCTION_FAIL INVALID_AUCTION_ID";
    }

    // 3. Hủy đăng ký — không cần kiểm tra auction tồn tại, nếu không có thì removeObserver no-op
    auctionManager.removeObserverFromAuction(auctionId, observer);

    return "LEAVE_AUCTION_SUCCESS " + auctionId;
  }

  /**
   * Hủy đăng ký khỏi tất cả phiên — gọi khi client disconnect.
   * Cách đơn giản: duyệt qua tất cả auction trong RAM và remove observer.
   * Chấp nhận được vì disconnect không phải hot path.
   *
   * @param observer ClientHandler đang disconnect
   */
  public void handleDisconnect(RealTimeObserver observer) {
    auctionManager.getAllAuctions()
        .forEach(auction ->
            auctionManager.removeObserverFromAuction(auction.getId(), observer)
        );
  }

  /**
   * Build snapshot hiện tại của phiên đấu giá để gửi về client ngay sau khi JOIN.
   * Client nhận được trạng thái đầy đủ mà không cần hỏi lại.
   *
   * Format: AUCTION_SNAPSHOT|<auctionId>|<status>|<currentPrice>|<leaderId>|<leaderName>
   *         |<endTime>|<totalBids>|<recentBid1Amount>:<recentBid1BidderId>,...
   */
  private String buildSnapshot(Auction auction) {
    String leaderId   = auction.getCurrentLeader() != null
        ? String.valueOf(auction.getCurrentLeader().getId()) : "-1";
    String leaderName = auction.getCurrentLeader() != null
        ? auction.getCurrentLeader().getUsername() : "None";

    // Lấy 5 bid gần nhất để client render bảng lịch sử ngay
    List<BidTransaction> recentBids = auction.getRecentBids(5);
    StringBuilder bidsSb = new StringBuilder();
    for (int i = 0; i < recentBids.size(); i++) {
      BidTransaction tx = recentBids.get(i);
      bidsSb.append(tx.getAmount().toPlainString())
          .append(":")
          .append(tx.getBidderId())
          .append(":")
          .append(tx.isAutoBid() ? "AUTO" : "MANUAL");
      if (i < recentBids.size() - 1) bidsSb.append(",");
    }

    return String.format(
        "AUCTION_SNAPSHOT|%d|%s|%s|%s|%s|%s|%d|%s",
        auction.getId(),
        auction.getStatus().name(),
        auction.getCurrentPrice().toPlainString(),
        leaderId,
        leaderName,
        auction.getEndTime().toString(),
        auction.getTotalBids(),
        bidsSb.toString()
    );
  }
}