package server.common.model;

import server.common.enums.NotificationType;

import java.math.BigDecimal;

/**
 * Event đặc biệt dùng để đẩy lệnh cập nhật số dư ví về client ngay lập tức.
 *
 * <p>NotificationDispatcher nhận dạng class này qua {@code instanceof}
 * và gửi protocol {@code WALLET_UPDATE|userId|balance} thay vì {@code PUSH_NOTIF}.
 *
 * <p>Client (UserDashboardController) lắng nghe và cập nhật Label số dư ngay
 * trên JavaFX thread mà không cần logout hay refresh trang.
 *
 * <p><b>Cách hoạt động:</b>
 * <pre>
 *   PaymentService.processPayment()
 *     └─ COMMIT thành công
 *         └─ dispatcher.submit(new WalletUpdateEvent(buyerId,  newBuyerBalance))
 *         └─ dispatcher.submit(new WalletUpdateEvent(sellerId, newSellerBalance))
 *
 *   NotificationDispatcher.dispatch()
 *     └─ if (event instanceof WalletUpdateEvent walletEvent)
 *         └─ ClientManager.sendToUser(userId, "WALLET_UPDATE|userId|balance")
 *
 *   UserDashboardController.handleWalletUpdate()
 *     └─ Platform.runLater(() -> walletBalanceLabel.setText(formatBalance(newBalance)))
 * </pre>
 * </p>
 */
public class WalletUpdateEvent extends NotificationEvent {

  private final BigDecimal newBalance;

  /**
   * Tạo event cập nhật số dư ví.
   *
   * @param userId     ID của user cần cập nhật (buyer hoặc seller)
   * @param newBalance Số dư mới sau khi giao dịch hoàn tất
   */
  public WalletUpdateEvent(int userId, BigDecimal newBalance) {
    // Dispatcher nhận dạng class này qua instanceof trước khi đọc bất kỳ field nào,
    // nên type=SYSTEM và title rỗng không ảnh hưởng đến logic phân nhánh.
    super(userId, "WALLET_UPDATE", "", NotificationType.SYSTEM, null);
    this.newBalance = newBalance;
  }

  /**
   * Số dư mới của ví sau khi giao dịch hoàn tất.
   *
   * @return BigDecimal số dư — không bao giờ null
   */
  public BigDecimal getNewBalance() {
    return newBalance;
  }

  @Override
  public String toString() {
    return "WalletUpdateEvent{userId=" + getUserId()
        + ", newBalance=" + newBalance + "}";
  }
}