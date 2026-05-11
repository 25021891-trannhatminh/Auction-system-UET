package client.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NotificationModel {
  private String type;
  private String title;
  private String message;
  private LocalDateTime timestamp;

  public NotificationModel(String type, String title, String message) {
    this.type = type;
    this.title = title;
    this.message = message;
    this.timestamp = LocalDateTime.now();
  }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }

  public String getFormattedTime() {
    return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
  }

  @Override
  public String toString() {
    return "[" + type + "] " + title + ": " + message;
  }
}