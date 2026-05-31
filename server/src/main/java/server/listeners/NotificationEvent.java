package server.listeners;

import server.common.enums.NotificationType;
import server.service.NotificationService;
import server.network.NotificationDispatcher;

/**
 * Đối tượng bất biến (immutable) đại diện cho một sự kiện thông báo cần gửi.
 *
 * <p>Được tạo bởi {@link NotificationService#push} và đưa vào
 * {@link NotificationDispatcher} để gửi real-time đến client.
 * Chỉ chứa đủ thông tin để gửi qua socket — không chứa dữ liệu nhạy cảm.</p>
 */
public class NotificationEvent {

  private final int userId;
  private final String title;
  private final String message;
  private final NotificationType type;
  private final Integer relatedId; // auctionId, itemId... (có thể null)

  public NotificationEvent(int userId, String title, String message,
      NotificationType type, Integer relatedId) {
    this.userId    = userId;
    this.title     = title;
    this.message   = message;
    this.type      = type;
    this.relatedId = relatedId;
  }

  /** Overload không có relatedId */
  public NotificationEvent(int userId, String title, String message, NotificationType type) {
    this(userId, title, message, type, null);
  }

  public int getUserId()          { return userId; }
  public String getTitle()        { return title; }
  public String getMessage()      { return message; }
  public NotificationType getType() { return type; }
  public Integer getRelatedId()   { return relatedId; }

  @Override
  public String toString() {
    return "NotificationEvent{userId=" + userId + ", type=" + type
        + ", title='" + title + "'}";
  }
}