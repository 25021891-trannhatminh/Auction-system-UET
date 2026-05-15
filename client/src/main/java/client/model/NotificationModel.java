package client.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * View model for notification messages received from the auction server.
 */
public class NotificationModel {
  private String type;
  private String title;
  private String message;
  private LocalDateTime timestamp;

  /**
   * Creates a notification model with the current client timestamp.
   *
   * @param type notification type from the server protocol
   * @param title short notification title
   * @param message user-facing notification body
   */
  public NotificationModel(String type, String title, String message) {
    this.type = type;
    this.title = title;
    this.message = message;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * Returns the notification type.
   *
   * @return server notification type
   */
  public String getType() {
    return type;
  }

  /**
   * Updates the notification type.
   *
   * @param type server notification type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Returns the notification title.
   *
   * @return notification title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Updates the notification title.
   *
   * @param title notification title
   */
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * Returns the notification body.
   *
   * @return notification message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Updates the notification body.
   *
   * @param message notification message
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Formats the notification timestamp for compact UI display.
   *
   * @return timestamp in HH:mm:ss format
   */
  public String getFormattedTime() {
    return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "[" + type + "] " + title + ": " + message;
  }
}
