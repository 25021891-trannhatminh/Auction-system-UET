package server.service.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.service.PaymentService;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Observer chuyên lắng nghe sự kiện đóng phiên đấu giá
 * và trigger PaymentService xử lý payment/refund async.
 *
 * Tương thích hoàn toàn với kiến trúc mới:
 * - Hiện thực hóa (implement) AuctionEventListener thay cho AuctionObserver cũ.
 * - Cô lập luồng tài chính qua worker riêng biệt.
 */
public class PaymentTriggerObserver implements AuctionEventListener {

  private static final Logger logger = LoggerFactory.getLogger(PaymentTriggerObserver.class);

  private final PaymentService paymentService;

  /**
   * Hàng đợi xử lý tác vụ tài chính tuần tự (Single Thread),
   * ngăn chặn triệt để rủi ro xung đột dữ liệu (race condition).
   */
  private final ExecutorService paymentExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "payment-trigger-worker");
    t.setDaemon(true);
    return t;
  });

  public PaymentTriggerObserver(PaymentService paymentService) {
    this.paymentService = Objects.requireNonNull(paymentService, "paymentService must not be null");
  }

  // =========================================================================
  // IMPLEMENTED METHODS FROM AuctionEventListener
  // =========================================================================

  /**
   * KÍCH HOẠT QUY TRÌNH THANH TOÁN KHI PHIÊN ĐẤU GIÁ KẾT THÚC.
   * Đẩy tác vụ xử lý tiền tệ sang luồng worker bất đồng bộ (Async).
   */
  @Override
  public void onAuctionEnded(int userId, int auctionId, String itemName, BigDecimal finalPrice) {
    paymentExecutor.submit(() -> processAuctionClosure(auctionId, itemName));
  }

  @Override
  public void onBidPlaced(int bidderId, int auctionId, String itemName, BigDecimal amount) {
    // Không cần xử lý logic thanh toán tại thời điểm đặt giá (NO-OP)
  }

  @Override
  public void onOutbid(int userId, int auctionId, String itemName, BigDecimal newPrice) {
    // Không cần xử lý logic thanh toán khi có giá cao hơn vượt lên (NO-OP)
  }

  @Override
  public void onAuctionStarted(int userId, int auctionId, String itemName) {
    // Không cần xử lý logic thanh toán khi bắt đầu phiên (NO-OP)
  }

  @Override
  public void onAuctionWon(int winnerId, int auctionId, String itemName, BigDecimal finalPrice) {
    // Không cần xử lý logic thanh toán tại hàm thông báo thắng cuộc này (NO-OP)
  }

  @Override
  public void onAuctionLost(int loserId, int auctionId, String itemName) {
    // Không cần xử lý logic thanh toán khi người dùng đấu giá thua (NO-OP)
  }

  @Override
  public void onPaymentDue(int buyerId, int auctionId, String itemName, BigDecimal amount) {
    // Không cần xử lý tại listener này (NO-OP)
  }

  @Override
  public void onPaymentReceived(int sellerId, int auctionId, String itemName, BigDecimal amount) {
    // Không cần xử lý tại listener này (NO-OP)
  }

  @Override
  public void onItemApproved(int sellerId, int itemId, String itemName) {
    // Không cần xử lý tại listener này (NO-OP)
  }

  @Override
  public void onItemRejected(int sellerId, int itemId, String itemName) {
    // Không cần xử lý tại listener này (NO-OP)
  }

  @Override
  public void onSystemNotification(int userId, String title, String message) {
    // Không cần xử lý tại listener này (NO-OP)
  }

  // =========================================================================
  // INTERNAL PAYMENT WORKFLOW
  // =========================================================================

  /**
   * Điều phối luồng nghiệp vụ tài chính dưới tầng Service.
   */
  private void processAuctionClosure(int auctionId, String itemName) {
    try {
      logger.info("Processing payment closure for auction: id={}, item={}", auctionId, itemName);

      // Khi Auction end => Tạo 1 Payment PENDING cho winner
      boolean success = paymentService.createPendingPayment(auctionId, itemName);

      if (success) {
        logger.info("Payment completed successfully for auction id={}", auctionId);
      } else {
        logger.warn("Payment workflow execution failed for auction id={}", auctionId);
      }

    } catch (Exception e) {
      logger.error("Critical error while processing auction closure for auctionId={}", auctionId, e);
    }
  }

  // =========================================================================
  // RESOURCE SHUTDOWN MANAGEMENT
  // =========================================================================

  /**
   * Thực hiện đóng luồng an toàn (Graceful Shutdown) cho hàng đợi tác vụ,
   * cấp tối đa 10 giây để hoàn tất các giao dịch dở dang trước khi đóng hẳn ứng dụng.
   */
  public void shutdown() {
    logger.info("Shutting down PaymentTriggerObserver executor pool...");
    paymentExecutor.shutdown();
    try {
      if (!paymentExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.warn("Payment executor timeout reached -> Forcing immediate shutdown");
        paymentExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted during graceful shutdown of payment executor", e);
      paymentExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}