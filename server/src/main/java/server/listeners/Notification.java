package server.listeners;

import java.time.LocalDateTime;
import server.common.enums.NotificationType;

public class Notification {

  private int notifId;
  private int userId;
  private NotificationType type;
  private String title;
  private String content;
  private boolean isRead;
  private Integer relatedId; // ID của Auction hoặc Bid liên quan
  private LocalDateTime createdAt;

  // Constructor không tham số
  public Notification() {
  }

  // Constructor đầy đủ tham số (hữu ích khi lấy dữ liệu từ Database)
  public Notification(int notifId, int userId, NotificationType type, String title, String content,
      boolean isRead, Integer relatedId, LocalDateTime createdAt) {
    this.notifId = notifId;
    this.userId = userId;
    this.type = type;
    this.title = title;
    this.content = content;
    this.isRead = isRead;
    this.relatedId = relatedId;
    this.createdAt = createdAt;
  }

  // Getter và Setter
  public int getNotifId() {
    return notifId;
  }

  public void setNotifId(int notifId) {
    this.notifId = notifId;
  }

  public int getUserId() {
    return userId;
  }

  public void setUserId(int userId) {
    this.userId = userId;
  }

  public NotificationType getType() {
    return type;
  }

  public void setType(NotificationType type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public boolean isRead() {
    return isRead;
  }

  public void setRead(boolean read) {
    isRead = read;
  }

  public Integer getRelatedId() {
    return relatedId;
  }

  public void setRelatedId(Integer relatedId) {
    this.relatedId = relatedId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

}