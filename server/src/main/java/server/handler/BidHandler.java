package server.handler;

import server.common.ProtocolConstants;
import server.common.entity.User;
import server.common.entity.exception.AuctionClosedException;
import server.common.entity.exception.InvalidBidException;
import server.common.model.BidResultDTO;
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
     * Format gói tin từ client: BID auctionId amount
     * * @param request Mảng lệnh đã được split bằng dấu cách (" ") từ ClientHandler
     * @param userId ID người dùng lấy từ Session của ClientHandler
     * @return Chuỗi phản hồi chuẩn Protocol do ResponseBuilder sinh ra
     */
    public String handleBid(String[] request, int userId, String username) {
        // 1. Kiểm tra trạng thái đăng nhập (Giống logic AdminHandler của nhóm)
        if (userId <= 0) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_NOT_LOGGED_IN);
        }

        // 2. Kiểm tra độ dài mảng tham số (Format: [0]=BID, [1]=auctionId, [2]=amount)
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

            // Kiểm tra số tiền cơ bản đầu va
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseBuilder.bidFail("AMOUNT_MUST_BE_POSITIVE");
            }

            // 3. Lấy thông tin đối tượng User từ Service
            User bidder = auctionService.findUserById(String.valueOf(userId));
            if (bidder == null) {
                return ResponseBuilder.bidFail("USER_NOT_FOUND");
            }

            // 4. Gọi hàm "điều phối ở Service (Gộp cả thủ công và auto-bid )
            // Hàm placeBid của Service đang nhận vào String đấu giá, ta truyền auctionIdStr
            BidResultDTO result = auctionService.placeBid(auctionIdStr, bidder, amount);

            // 5. Trả về response thành công thông qua ResponseBuilder của nhóm
            if (result != null && result.getManualTransaction() != null) {
                return ResponseBuilder.bidSuccess(auctionId, amount);
                // Sinh ra chuỗi: "BID_SUCCESS auctionId amount"
            }

            return ResponseBuilder.bidFail("BID_FAILED");

        } catch (NumberFormatException e) {
            return ResponseBuilder.bidFail(ProtocolConstants.FAIL_INVALID_FORMAT);
        } catch (AuctionClosedException e) {
            return ResponseBuilder.bidFail("AUCTION_CLOSED");
        } catch (InvalidBidException e) {
            // Lấy lý do lỗi nghiệp vụ cụ thể từ Core RAM (ví dụ: viết hoa và nối dấu _)
            String reason = e.getMessage() != null ? e.getMessage().toUpperCase().replace(" ", "_") : "INVALID_BID";
            return ResponseBuilder.bidFail(reason);
        } catch (Exception e) {
            return ResponseBuilder.bidFail("SYSTEM_ERROR");
        }
    }
}