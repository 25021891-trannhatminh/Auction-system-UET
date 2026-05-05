package server.common.model;

import server.common.enums.NotificationType;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * DTO cho bảng NOTIFICATIONS.
 *
 * <p>Dùng để truyền dữ liệu thông báo giữa các tầng trong hệ thống
 * (DAO → Service → Controller → Client).</p>
 */
public class NotificationDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** ID thông báo (Primary Key) */
  private int notifId;

  /** ID người nhận (FK -> USERS) */
  private int userId;

  /** Loại thông báo (BID_OUTBID, AUCTION_WON, ...) */
  private NotificationType type;

  /** Tiêu đề thông báo */
  private String title;

  /** Nội dung thông báo */
  private String content;

  /** Trạng thái đã đọc hay chưa */
  private boolean isRead;

  /** ID liên quan (auction / bid / payment), có thể null */
  private Integer relatedId;

  /** Thời điểm tạo thông báo */
  private Timestamp createdAt;

  // ========================== Constructors ==========================

  public NotificationDTO() {}

  /**
   * Constructor dùng khi load dữ liệu từ database.
   *
   * @param notifId   ID thông báo
   * @param userId    ID người nhận
   * @param type      Loại thông báo
   * @param title     Tiêu đề
   * @param content   Nội dung
   * @param isRead    Trạng thái đã đọc
   * @param relatedId ID liên quan (nullable)
   * @param createdAt Thời điểm tạo
   */
  public NotificationDTO(int notifId, int userId, NotificationType type,
      String title, String content,
      boolean isRead, Integer relatedId, Timestamp createdAt) {
    this.notifId = notifId;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.isRead = isRead;
    this.relatedId = relatedId;
    this.createdAt = createdAt;
  }

  /**
   * Constructor dùng khi tạo thông báo mới.
   *
   * @param userId    ID người nhận
   * @param type      Loại thông báo
   * @param title     Tiêu đề
   * @param content   Nội dung
   * @param relatedId ID liên quan (nullable)
   */
  public NotificationDTO(int userId, NotificationType type,
      String title, String content, Integer relatedId) {
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.relatedId = relatedId;
    this.isRead = false;
  }

  // ========================== Getters & Setters ==========================

  public int getNotifId() { return notifId; }

  public void setNotifId(int notifId) { this.notifId = notifId; }

  public int getUserId() { return userId; }

  public void setUserId(int userId) { this.userId = userId; }

  public NotificationType getType() { return type; }

  public void setType(NotificationType type) { this.type = type; }

  public String getTitle() { return title; }

  public void setTitle(String title) { this.title = title; }

  public String getContent() { return content; }

  public void setContent(String content) { this.content = content; }

  public boolean isRead() { return isRead; }

  public void setRead(boolean read) { isRead = read; }

  public Integer getRelatedId() { return relatedId; }

  public void setRelatedId(Integer relatedId) { this.relatedId = relatedId; }

  public Timestamp getCreatedAt() { return createdAt; }

  public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

  // ========================== Override ==========================

  @Override
  public String toString() {
    return "NotificationDTO{" +
        "notifId=" + notifId +
        ", userId=" + userId +
        ", type=" + type +
        ", title='" + title + '\'' +
        ", isRead=" + isRead +
        ", relatedId=" + relatedId +
        ", createdAt=" + createdAt +
        '}';
  }
}