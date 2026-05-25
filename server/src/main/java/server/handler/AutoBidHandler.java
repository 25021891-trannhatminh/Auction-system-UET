package server.handler;

import server.common.entity.AutoBidConfig;
import server.common.entity.User;
import server.service.AuctionService;

import java.math.BigDecimal;

public class AutoBidHandler {
    private final AuctionService auctionService;

    public AutoBidHandler(AuctionService auctionService){
      this.auctionService = auctionService;
    }

  /**
   * Xử lý AUTO_BID_REGISTER và AUTO_BID_CANCEL
   * Format: AUTO_BID_REGISTER <auctionId> <userId> <maxBid> <increment>
   *         AUTO_BID_CANCEL <auctionId> <userId>
   */
  public String handleAutoBidRegister(String[] request) {
    if (request.length < 5) {
      return "AUTO_BID_FAIL INVALID_FORMAT";
    }

    int auctionId = Integer.parseInt(request[1]);
    int userId = Integer.parseInt(request[2]);
    BigDecimal maxBid = new BigDecimal(request[3]);
    BigDecimal increment = new BigDecimal(request[4]);
    if (userId <= 0 || auctionId <= 0) {
      return "AUTO_BID_FAIL NOT_LOGGED_IN";
    }
    if (maxBid.compareTo(BigDecimal.ZERO) <= 0 || increment.compareTo(BigDecimal.ZERO) <= 0) {
      return "AUTO_BID_FAIL INVALID_AMOUNT";
    }

    // Lấy User từ AuctionManager
    User bidder = auctionService.findUserById(userId);
    if (bidder == null) {
      return "AUTO_BID_FAIL USER_NOT_FOUND";
    }

    // Add Config vào Server
    AutoBidConfig config = new AutoBidConfig(auctionId, userId, maxBid, increment);
    auctionService.registerAutoBid(config, bidder);
    return "AUTO_BID_REGISTER_SUCCESS ";

  }

  public String handleAutoBidCancel(String[] request) {
    if (request.length < 3) {
      return "AUTO_BID_FAIL INVALID_FORMAT";
    }

    int auctionId = Integer.parseInt(request[1]);
    int userId = Integer.parseInt(request[2]);
    if (userId <= 0 || auctionId <= 0) {
      return "AUTO_BID_FAIL NOT_LOGGED_IN";
    }

    // Lấy User từ AuctionManager
    User bidder = auctionService.findUserById(userId);
    if (bidder == null) {
      return "AUTO_BID_FAIL USER_NOT_FOUND";
    }

    auctionService.cancelAutoBid(auctionId,bidder);
    return "AUTO_BID_REGISTER_SUCCESS ";

  }
}
