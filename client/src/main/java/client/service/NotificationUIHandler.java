package client.service;

import client.model.NotificationModel;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Alert;

/**
 * Converts server notification protocol messages into JavaFX alert dialogs.
 */
public class NotificationUIHandler {

  /**
   * Parses and displays a notification message from the server.
   *
   * @param rawMessage raw PUSH_NOTIF protocol message
   */
  public void handle(String rawMessage) {
    try {
      String[] parts = rawMessage.split("\\|", 4);

      if (parts.length >= 4) {
        String type = parts[1];
        String title = parts[2];
        String message = parts[3];

        NotificationModel notif = new NotificationModel(type, title, message);
        Platform.runLater(() -> processNotificationUi(notif));
      }
    } catch (Exception e) {
      System.err.println("Error parsing notification: " + e.getMessage());
    }
  }

  private void processNotificationUi(NotificationModel notif) {
    Alert alert = new Alert(AlertType.INFORMATION);

    switch (notif.getType()) {
      case "OUTBID":
        alert.setAlertType(AlertType.WARNING);
        alert.setTitle("Outbid Alert!");
        break;
      case "PAYMENT_DUE":
        alert.setAlertType(AlertType.WARNING);
        alert.setTitle("⚠️ Warning");
        break;
      case "AUCTION_WON":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Congratulations! You Won");
        break;
      case "ITEM_APPROVED":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("🎉 Congratulations");
        break;
      case "AUCTION_LOST":
        alert.setAlertType(AlertType.ERROR);
        alert.setTitle("Auction Result");
        break;
      case "ITEM_REJECTED":
        alert.setAlertType(AlertType.ERROR);
        alert.setTitle("❌ Notice");
        break;
      case "BID_PLACED":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Bid Placed");
        break;
      case "AUCTION_STARTED":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Live Now! 🚀");
        break;
      case "AUCTION_ENDED":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Auction Closed");
        break;
      case "PAYMENT_RECEIVED":
        alert.setAlertType(AlertType.INFORMATION);
        alert.setTitle("Money Received! 💰");
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
    alert.show();
  }
}
