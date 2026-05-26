package server.handler;

import server.common.ProtocolConstants;
import server.common.entity.User;
import server.common.entity.exception.AuctionStateException;
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
     * Response thành công : "BID_SUCCESS auctionId amount"
     * Response thất bại   : "BID_FAIL    auctionId reason"
     *   - auctionId = -1 khi chưa parse được từ request (lỗi format)
     *   - reason là một trong các BID_REASON_* trong ProtocolConstants
     *
     * @param request  Mảng lệnh đã được tách bởi ClientHandler
     * @param userId   ID người dùng lấy từ session
     * @param username Tên hiển thị (dùng cho log)
     * @return Chuỗi phản hồi chuẩn Protocol qua ResponseBuilder
     */
    public String handleBid(String[] request, int userId, String username) {
        // 1. Kiểm tra đăng nhập — chưa có auctionId, dùng -1
        if (userId <= 0) {
            return ResponseBuilder.bidFail(ProtocolConstants.BID_REASON_NOT_LOGGED_IN);
        }

        // 2. Kiểm tra định dạng tham số — chưa có auctionId, dùng -1
        if (request == null || request.length < 3) {
            return ResponseBuilder.bidFail(ProtocolConstants.BID_REASON_INVALID_FORMAT);
        }

        String auctionIdStr = request[1];
        String amountStr    = request[2];

        if (auctionIdStr == null || auctionIdStr.trim().isEmpty()) {
            return ResponseBuilder.bidFail(ProtocolConstants.BID_REASON_INVALID_FORMAT);
        }

        // Parse auctionId sớm để tất cả bidFail phía sau đều mang đúng context
        int auctionId;
        try {
            auctionId = Integer.parseInt(auctionIdStr);
        } catch (NumberFormatException e) {
            return ResponseBuilder.bidFail(ProtocolConstants.BID_REASON_INVALID_FORMAT);
        }

        try {
            BigDecimal amount = new BigDecimal(amountStr);

            // 3. Kiểm tra số tiền dương — đã có auctionId
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_AMOUNT_NOT_POSITIVE);
            }

            // 4. Kiểm tra trạng thái online
            if (!ClientManager.isOnline(userId)) {
                return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_USER_OFFLINE);
            }

            // 5. Kiểm tra user tồn tại trong RAM cache
            User bidder = auctionService.findUserById(userId);
            if (bidder == null) {
                return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_USER_NOT_FOUND);
            }

            // 6. Gọi logic đặt giá
            boolean success = auctionService.placeBid(auctionId, userId, amount, false);

            // 7. Phản hồi kết quả
            if (success) {
                return ResponseBuilder.bidSuccess(auctionId, amount);
            }
            return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_DB_FAILED);

        } catch (NumberFormatException e) {
            return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_INVALID_FORMAT);
        } catch (AuctionStateException e) {
            return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_AUCTION_CLOSED);
        } catch (InvalidBidException e) {
            // Map exception message sang reason constant đã định nghĩa sẵn.
            // Ưu tiên dùng constant thay vì transform string thô để client không bị phụ thuộc
            // vào format message tiếng Việt/Anh của exception.
            String reason = mapInvalidBidReason(e.getMessage());
            return ResponseBuilder.bidFail(auctionId, reason);
        } catch (Exception e) {
            return ResponseBuilder.bidFail(auctionId, ProtocolConstants.BID_REASON_SYSTEM_ERROR);
        }
    }

    /**
     * Map exception message của InvalidBidException sang BID_REASON_* constant.
     *
     * <p>Tách riêng thành method để BidHandler không chứa logic string-matching,
     * và dễ mở rộng khi thêm loại lỗi mới.</p>
     *
     * @param exceptionMessage getMessage() từ InvalidBidException
     * @return BID_REASON_* constant phù hợp
     */
    private String mapInvalidBidReason(String exceptionMessage) {
        if (exceptionMessage == null) {
            return ProtocolConstants.BID_REASON_SYSTEM_ERROR;
        }
        String lower = exceptionMessage.toLowerCase();
        if (lower.contains("increment")) {
            return ProtocolConstants.BID_REASON_BELOW_MIN_INCREMENT;
        }
        if (lower.contains("higher") || lower.contains("current price")) {
            return ProtocolConstants.BID_REASON_BELOW_CURRENT_PRICE;
        }
        if (lower.contains("own auction")) {
            return ProtocolConstants.BID_REASON_OWN_AUCTION;
        }
        if (lower.contains("không tìm thấy") || lower.contains("not found")) {
            return ProtocolConstants.BID_REASON_AUCTION_NOT_FOUND;
        }
        // Fallback an toàn — client vẫn nhận được token BID_FAIL với auctionId đúng
        return ProtocolConstants.BID_REASON_SYSTEM_ERROR;
    }
}