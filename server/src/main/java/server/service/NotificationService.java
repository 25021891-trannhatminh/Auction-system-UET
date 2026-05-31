package server.service;

import java.util.List;

import server.listeners.Notification;
import server.listeners.NotificationEvent;
import server.network.NotificationDispatcher;
import server.repository.NotificationDAO;
import server.common.enums.NotificationType;


public class NotificationService {
  private final NotificationDAO notificationDAO = new NotificationDAO();

  // 1. Gửi thông báo mới (Real-time)
  public void push(int userId, String title, String message, NotificationType type,Integer relatedId) {
    // 1. Lưu vào Database (Để người dùng xem lại lịch sử sau này)
    if (userId != -1) {
      Notification notification = new Notification();
      notification.setUserId(userId);
      notification.setTitle(title);
      notification.setContent(message);
      notification.setType(type);
      notification.setRead(false);
      notification.setRelatedId(relatedId);
      notificationDAO.insert(notification);
    }

    NotificationDispatcher.getInstance().submit(
        new NotificationEvent(userId, title, message, type)
    );
  }

  public void push(int userId, String title, String message, NotificationType type) {
    push(userId, title, message, type, null);
  }

  /**
   * Push realtime socket notification mà KHÔNG ghi vào database.
   * Dùng cho onBidPlaced, onTimeExtended.
   */
  public void pushRealtimeOnly(int userId, String title, String message,
                               NotificationType type, Integer relatedId) {
    NotificationDispatcher.getInstance().submit(
        new NotificationEvent(userId, title, message, type, relatedId)
    );
  }

  public void pushRealtimeOnly(int userId, String title, String message, NotificationType type) {
    pushRealtimeOnly(userId, title, message, type, null);
  }

  // Lấy danh sách thông báo bỏ lỡ khi offline
  public List<Notification> getUnreadNotifications(int userId) {
    // Gọi DAO để lấy các thông báo có status 'isRead = false'
    return notificationDAO.getUnreadByUserId(userId);
  }

  // Lấy danh sách để hiển thị khi vừa mở App
  public List<Notification> getAllForUser(int userId) {
    return notificationDAO.getByUserId(userId,50);
  }

  // Đánh dấu đã đọc
  public void markRead(int notifId) {
    notificationDAO.markAsRead(notifId);
  }

  /**
   * Marks every notification of a user as read after the notification center is opened.
   *
   * @param userId authenticated user id
   * @return number of rows updated in the database
   */
  public int markAllRead(int userId) {
    if (userId <= 0) {
      return 0;
    }
    return notificationDAO.markAllAsRead(userId);
  }

  // Xóa thông báo cũ (Nếu cần)
  public void deleteOldNotifications(int userId) {
    notificationDAO.deleteByUserId(userId);
  }

  //  Đếm số thông báo chưa đọc
  public int countUnread(int userId) {
    return notificationDAO.countUnread(userId);
  }
}