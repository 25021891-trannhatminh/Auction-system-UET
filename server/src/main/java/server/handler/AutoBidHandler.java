package server.handler;

import java.math.BigDecimal;
import server.common.ProtocolConstants;
import server.common.entity.Auction;
import server.common.entity.AutoBidConfig;
import server.common.entity.User;
import server.common.enums.AuctionStatus;
import server.service.AuctionService;

/**
 * Handles user-facing auto-bid commands and keeps the bidder bound to the logged-in socket user.
 */
public class AutoBidHandler {
  private final AuctionService auctionService;

  public AutoBidHandler(AuctionService auctionService) {
    this.auctionService = auctionService;
  }

  /**
   * Registers or updates the current user's auto-bid rule.
   *
   * <p>Expected protocol: {@code AUTOBID_REGISTER auctionId maxBid increment}.</p>
   */
  public String handleAutoBidRegister(String[] request, int authenticatedUserId) {
    if (authenticatedUserId <= 0) {
      return fail("-1", ProtocolConstants.FAIL_NOT_LOGGED_IN);
    }
    if (request == null || request.length < 4) {
      return fail("-1", ProtocolConstants.FAIL_INVALID_FORMAT);
    }

    String auctionIdText = request[1];
    try {
      int auctionId = Integer.parseInt(auctionIdText);
      BigDecimal maxBid = new BigDecimal(request[2]);
      BigDecimal increment = new BigDecimal(request[3]);

      String validationError = validateRegisterRequest(auctionId, authenticatedUserId, maxBid, increment);
      if (validationError != null) {
        return fail(String.valueOf(auctionId), validationError);
      }

      User bidder = auctionService.findUserById(authenticatedUserId);
      if (bidder == null) {
        return fail(String.valueOf(auctionId), ProtocolConstants.BID_REASON_USER_NOT_FOUND);
      }

      AutoBidConfig config = new AutoBidConfig(auctionId, bidder.getId(), maxBid, increment);
      boolean registered = auctionService.registerAutoBid(config, bidder);
      return registered
          ? ProtocolConstants.AUTOBID_SUCCESS + " " + auctionId + " REGISTERED"
          : fail(String.valueOf(auctionId), ProtocolConstants.BID_REASON_DB_FAILED);
    } catch (NumberFormatException exception) {
      return fail(auctionIdText, ProtocolConstants.FAIL_INVALID_FORMAT);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return fail(auctionIdText, normalizeReason(exception.getMessage()));
    } catch (Exception exception) {
      return fail(auctionIdText, ProtocolConstants.BID_REASON_SYSTEM_ERROR);
    }
  }

  /**
   * Cancels the current user's active auto-bid rule for an auction.
   *
   * <p>Expected protocol: {@code AUTOBID_CANCEL auctionId}.</p>
   */
  public String handleAutoBidCancel(String[] request, int authenticatedUserId) {
    if (authenticatedUserId <= 0) {
      return fail("-1", ProtocolConstants.FAIL_NOT_LOGGED_IN);
    }
    if (request == null || request.length < 2) {
      return fail("-1", ProtocolConstants.FAIL_INVALID_FORMAT);
    }

    String auctionIdText = request[1];
    try {
      int auctionId = Integer.parseInt(auctionIdText);
      if (auctionId <= 0) {
        return fail(auctionIdText, ProtocolConstants.FAIL_INVALID_FORMAT);
      }

      User bidder = auctionService.findUserById(authenticatedUserId);
      if (bidder == null) {
        return fail(String.valueOf(auctionId), ProtocolConstants.BID_REASON_USER_NOT_FOUND);
      }

      boolean canceled = auctionService.cancelAutoBid(auctionId, bidder);
      return canceled
          ? ProtocolConstants.AUTOBID_SUCCESS + " " + auctionId + " CANCELED"
          : fail(String.valueOf(auctionId), "AUTO_BID_NOT_FOUND");
    } catch (NumberFormatException exception) {
      return fail(auctionIdText, ProtocolConstants.FAIL_INVALID_FORMAT);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      return fail(auctionIdText, normalizeReason(exception.getMessage()));
    } catch (Exception exception) {
      return fail(auctionIdText, ProtocolConstants.BID_REASON_SYSTEM_ERROR);
    }
  }

  private String validateRegisterRequest(int auctionId, int bidderId, BigDecimal maxBid,
      BigDecimal increment) {
    if (auctionId <= 0) {
      return ProtocolConstants.FAIL_INVALID_FORMAT;
    }
    if (maxBid == null || increment == null
        || maxBid.compareTo(BigDecimal.ZERO) <= 0
        || increment.compareTo(BigDecimal.ZERO) <= 0) {
      return "INVALID_AMOUNT";
    }

    Auction auction = auctionService.findAuctionById(auctionId);
    if (auction == null) {
      return "AUCTION_NOT_FOUND";
    }
    if (auction.getStatus() != AuctionStatus.RUNNING) {
      return "AUCTION_NOT_RUNNING";
    }
    if (auction.getSellerId() == bidderId) {
      return "OWN_AUCTION";
    }
    if (maxBid.compareTo(auction.getCurrentPrice()) <= 0) {
      return "BELOW_CURRENT_PRICE";
    }
    if (increment.compareTo(auction.getMinBidIncrement()) < 0
        || maxBid.compareTo(auction.getCurrentPrice().add(auction.getMinBidIncrement())) < 0) {
      return "BELOW_MIN_INCREMENT";
    }
    return null;
  }

  private String fail(String auctionId, String reason) {
    return ProtocolConstants.AUTOBID_FAIL + " " + auctionId + " " + normalizeReason(reason);
  }

  private String normalizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return ProtocolConstants.BID_REASON_SYSTEM_ERROR;
    }
    return reason.trim().toUpperCase().replace(' ', '_');
  }
}
