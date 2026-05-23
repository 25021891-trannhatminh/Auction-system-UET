package server.handler;

import server.common.ProtocolConstants;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.network.ClientManager;
import server.service.AuctionService;

import java.math.BigDecimal;

/**
 * Xử lý các lệnh liên quan đến đặt giá (BID) dựa trên Protocol mảng String[].
 */
public class BidHandler {

    private final AuctionService auctionService;

    public BidHandler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    /**
     * Xử lý lệnh BID từ client.
     * Format gói tin: BID auctionId amount
     *
     * @param request  Mảng lệnh đã được tách bởi ClientHandler
     * @param userId   ID người dùng lấy từ session
     * @param username Tên hiển thị (dùng cho log hoặc mở rộng sau)
     * @return Chuỗi phản hồi chuẩn Protocol qua ResponseBuilder
     */
    public String handleBid(String[] request, int userId, String username) {
        // 1. Kiểm tra đăng nhập
        if (userId <= 0) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_NOT_LOGGED_IN);
        }

        // 2. Kiểm tra định dạng tham số
        if (request == null || request.length < 3) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }

        String auctionIdStr = request[1];
        String amountStr = request[2];

        if (auctionIdStr == null || auctionIdStr.trim().isEmpty()) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        }

        try {
            int auctionId = Integer.parseInt(auctionIdStr);
            BigDecimal amount = new BigDecimal(amountStr);

            // Kiểm tra số tiền dương
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseBuilder.bidFail("AMOUNT_MUST_BE_POSITIVE");
            }

            // 3. Kiểm tra trạng thái online
            if (!ClientManager.isOnline(userId)) {
                return ResponseBuilder.bidFail("USER_OFFLINE");
            }

            // 4. Kiểm tra user tồn tại trong RAM cache
            User bidder = auctionService.findUserById(userId);
            if (bidder == null) {
                return ResponseBuilder.bidFail("USER_NOT_FOUND");
            }

            // 5. Gọi logic đặt giá – auctionService.placeBid trả về boolean
            boolean success = auctionService.placeBid(auctionId, userId, amount, false);

            // 6. Phản hồi kết quả
            if (success) {
                return ResponseBuilder.bidSuccess(auctionId, amount);
            }
            return ResponseBuilder.bidFail("DB_PERSIST_FAILED");

        } catch (NumberFormatException e) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        } catch (AuctionClosedException e) {
            return ResponseBuilder.bidFail("AUCTION_CLOSED");
        } catch (InvalidBidException e) {
            String reason = e.getMessage() != null
                ? e.getMessage().toUpperCase().replace(" ", "_")
                : "INVALID_BID";
            return ResponseBuilder.bidFail(reason);
        } catch (Exception e) {
            return ResponseBuilder.bidFail("SYSTEM_ERROR");
        }
    }
}