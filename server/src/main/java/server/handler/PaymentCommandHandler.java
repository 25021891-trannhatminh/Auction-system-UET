package server.handler;

import java.util.Arrays;
import server.network.ClientManager;
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
                    ? clientHandler.commandSafeText(String.join(" ", Arrays.copyOfRange(request, 2, request.length)))
                    : "Auction #" + auctionId;

            // 1. GỌI HÀM CHECK QUYỀN (Hàm này chứa SQL nằm bên ClientHandler)
            ClientHandler.PaymentAuthorization authorization = clientHandler.resolvePaymentAuthorization(auctionId);
            if (!authorization.allowed()) {
                clientHandler.send("PAYMENT_FAIL " + auctionId + " " + authorization.reason());
                return;
            }

            // 2. GỌI NGHIỆP VỤ SERVICE
            boolean paymentSuccess = paymentService.processPayment(auctionId, itemName);

            if (paymentSuccess) {
                clientHandler.send("PAYMENT_SUCCESS " + auctionId);

                // 3. THÊM LỆNH BROADCAST ĐỂ UPDATE GIAO DIỆN CLIENT THỜI GIAN THỰC
                ClientManager.broadcast("USER_TRANSACTIONS_DIRTY");
                ClientManager.broadcast("USER_AUCTIONS_DIRTY");
            } else {
                // 4. THÊM LỆNH TRẢ VỀ LỖI CHI TIẾT (Hàm này chứa SQL nằm bên ClientHandler)
                clientHandler.send("PAYMENT_FAIL " + auctionId + " " + clientHandler.resolvePaymentCompletionFailure(auctionId));
                server.network.ClientManager.broadcast("USER_TRANSACTIONS_DIRTY");
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