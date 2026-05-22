package server.handler;

import server.common.AuctionConstants;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.model.BidResultDTO;
import server.service.AuctionService;

import java.math.BigDecimal;

/**
 * Xử lý các lệnh liên quan đến đặt giá.
 */
public class BidHandler {
    
    private final AuctionService auctionService;
    
    public BidHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }
    
    /**
     * Xử lý lệnh BID từ client.
     */
    public String handleBid(String auctionId, String amountStr, int userId, String username) {
        // Validate input
        if (userId <= 0) {
            return AuctionConstants.FAIL_NOT_LOGGED_IN;
        }
        
        if (auctionId == null || auctionId.trim().isEmpty()) {
            return AuctionConstants.FAIL_INVALID_BID_FORMAT;
        }
        
        try {
            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return AuctionConstants.FAIL_AMOUNT_MUST_BE_POSITIVE;
            }
            
            // Lấy User từ service
            User bidder = auctionService.findUserById(String.valueOf(userId));
            if (bidder == null) {
                return AuctionConstants.FAIL_USER_NOT_FOUND;
            }
            
            // Gọi core logic đặt giá - trả về BidResultDTO
            BidResultDTO result = auctionService.placeBid(auctionId, bidder, amount);
            
            if (result != null && result.getManualTransaction() != null) {
                return AuctionConstants.BID_SUCCESS + " " + auctionId + " " + amount;
            }
            
            return AuctionConstants.FAIL_BID_FAILED;
            
        } catch (NumberFormatException e) {
            return AuctionConstants.FAIL_INVALID_AMOUNT;
        } catch (AuctionClosedException e) {
            return AuctionConstants.FAIL_AUCTION_CLOSED;
        } catch (InvalidBidException e) {
            return AuctionConstants.FAIL_BID_FAILED + " " + e.getMessage();
        } catch (Exception e) {
            return AuctionConstants.FAIL_BID_FAILED;
        }
    }
}