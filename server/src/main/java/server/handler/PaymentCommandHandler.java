package server.handler;

import java.util.Arrays;
import server.service.PaymentService;

/**
 * Bộ xử lý độc lập chuyên trách các lệnh liên quan đến Thanh toán (Payment).
 * Giúp giảm tải và làm sạch code cho ClientHandler.
 */
public class PaymentCommandHandler {

    private final ClientHandler clientHandler;
    private final PaymentService paymentService;

    /**
     * Constructor nhận vào ClientHandler (để mượn hàm send gửi mạng)
     * và PaymentService (để gọi nghiệp vụ logic xuống DB)
     */
    public PaymentCommandHandler(ClientHandler clientHandler, PaymentService paymentService) {
        this.clientHandler = clientHandler;
        this.paymentService = paymentService;
    }

    /**
     * Xử lý lệnh CONFIRM_PAYMENT từ Client gửi lên
     */
    public void handleConfirmPayment(String[] request) {
        if (request.length < 2) {
            clientHandler.send("PAYMENT_FAIL INVALID_FORMAT");
            return;
        }
        try {
            int auctionId = Integer.parseInt(request[1]);
            String itemName = request.length > 2
                    ? String.join(" ", Arrays.copyOfRange(request, 2, request.length))
                    : "Auction #" + auctionId;

            boolean paymentSuccess = paymentService.processPayment(auctionId, itemName);
            if (paymentSuccess) {
                clientHandler.send("PAYMENT_SUCCESS " + auctionId);
            } else {
                clientHandler.send("PAYMENT_FAIL " + auctionId);
            }
        } catch (NumberFormatException e) {
            clientHandler.send("PAYMENT_FAIL INVALID_FORMAT");
        }
    }

    /**
     * Xử lý lệnh REFUND_PAYMENT từ Client gửi lên
     */
    public void handleRefundPayment(String[] request) {
        if (request.length < 2) {
            clientHandler.send("REFUND_FAIL INVALID_FORMAT");
            return;
        }
        try {
            int auctionId = Integer.parseInt(request[1]);
            String itemName = request.length > 2
                    ? String.join(" ", Arrays.copyOfRange(request, 2, request.length))
                    : "Auction #" + auctionId;

            boolean refundSuccess = paymentService.refundPayment(auctionId, itemName);
            if (refundSuccess) {
                clientHandler.send("REFUND_SUCCESS " + auctionId);
            } else {
                clientHandler.send("REFUND_FAIL " + auctionId);
            }
        } catch (NumberFormatException e) {
            clientHandler.send("REFUND_FAIL INVALID_FORMAT");
        }
    }
}