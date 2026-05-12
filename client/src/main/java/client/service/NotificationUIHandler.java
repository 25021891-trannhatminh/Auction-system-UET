package client.service;

import client.model.NotificationModel;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class NotificationUIHandler {

  public void handle(String rawMessage) {
    try {
      // Protocol: PUSH_NOTIF|TYPE|TITLE|CONTENT
      String[] parts = rawMessage.split("\\|", 4);

      if (parts.length >= 4) {
        String type = parts[1];
        String title = parts[2];
        String message = parts[3];

        NotificationModel notif = new NotificationModel(type, title, message);

        // Luôn cập nhật UI trong luồng JavaFX
        Platform.runLater(() -> {
          processNotificationUI(notif);
        });
      }
    } catch (Exception e) {
      System.err.println("Error parsing notification: " + e.getMessage());
    }
  }

  private void processNotificationUI(NotificationModel notif) {
    Alert alert = new Alert(AlertType.INFORMATION); // Mặc định là Information

    switch (notif.getType()) {
      case "OUTBID":
        alert.setAlertType(AlertType.WARNING);
        alert.setTitle("Outbid Alert!");
        break;

      case "PAYMENT_DUE":
        alert.setAlertType(AlertType.WARNING); // Hiện dấu chấm than vàng
        alert.setTitle("⚠️ Warning");
        break;

      case "AUCTION_WON":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Congratulations! You Won");
        break;

      case "ITEM_APPROVED":
        alert.setAlertType(AlertType.INFORMATION); // Hiện chữ i xanh (Tin vui)
        alert.setTitle("🎉 Congratulations");
        break;

      case "AUCTION_LOST":
        alert.setAlertType(AlertType.ERROR);
        alert.setTitle("Auction Result");
        break;

      case "ITEM_REJECTED":
        alert.setAlertType(AlertType.ERROR); // Hiện dấu X đỏ (Tin buồn)
        alert.setTitle("❌ Notice");
        break;

      case "SYSTEM":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("💻 System Message");
        break;

      default:
        alert.setTitle("🔔 Notification");
        break;
    }

    alert.setHeaderText(notif.getTitle());
    alert.setContentText(notif.getMessage());

    // Hiển thị thông báo
    alert.show();
  }
}