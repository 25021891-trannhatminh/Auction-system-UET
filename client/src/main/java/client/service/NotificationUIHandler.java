package client.service;

import client.model.NotificationModel;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Converts server notification protocol messages into lightweight in-app toasts.
 */
public class NotificationUIHandler {

  private static final double TOAST_WIDTH = 370;
  private static final double TOAST_RIGHT_MARGIN = 34;
  private static final double TOAST_TOP_MARGIN = 56;

  private enum ToastType {
    SUCCESS("\u2713", "#f7f3e9", "#d1b15d", "#17352c", "#183b34", "#536961"),
    INFO("i", "#edf3ef", "#315d52", "#f7f3e9", "#183b34", "#536961"),
    WARNING("!", "#fff7e1", "#caa64d", "#17352c", "#4b3b15", "#746437"),
    ERROR("x", "#fff0eb", "#b86d5c", "#fff7f0", "#6a2f25", "#86584f");

    final String icon;
    final String background;
    final String accent;
    final String iconText;
    final String titleText;
    final String bodyText;

    ToastType(
        String icon,
        String background,
        String accent,
        String iconText,
        String titleText,
        String bodyText) {
      this.icon = icon;
      this.background = background;
      this.accent = accent;
      this.iconText = iconText;
      this.titleText = titleText;
      this.bodyText = bodyText;
    }
  }

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
        runOnFxThread(() -> processNotificationUi(notif));
      }
    } catch (Exception e) {
      System.err.println("Error parsing notification: " + e.getMessage());
    }
  }

  /**
   * Shows a success toast for local UI actions.
   *
   * @param title short toast title
   * @param message supporting message
   */
  public void showSuccess(String title, String message) {
    runOnFxThread(() -> showToast(cleanTitle(title, "Success!"), message, ToastType.SUCCESS));
  }

  /**
   * Shows an error toast for local UI actions.
   *
   * @param title short toast title
   * @param message supporting message
   */
  public void showError(String title, String message) {
    runOnFxThread(() -> showToast(cleanTitle(title, "Action failed"), message, ToastType.ERROR));
  }

  /**
   * Shows an informational toast for local UI actions.
   *
   * @param title short toast title
   * @param message supporting message
   */
  public void showInfo(String title, String message) {
    runOnFxThread(() -> showToast(cleanTitle(title, "Notification"), message, ToastType.INFO));
  }

  private void processNotificationUi(NotificationModel notif) {
    ToastType toastType = mapType(notif.getType());
    String title = cleanTitle(notif.getTitle(), defaultTitle(notif.getType()));
    showToast(title, notif.getMessage(), toastType);
  }

  private ToastType mapType(String type) {
    if (type == null) {
      return ToastType.INFO;
    }

    return switch (type) {
      case "OUTBID", "PAYMENT_DUE" -> ToastType.WARNING;
      case "AUCTION_LOST", "ITEM_REJECTED" -> ToastType.ERROR;
      case "AUCTION_WON", "ITEM_APPROVED", "BID_PLACED", "AUCTION_STARTED",
          "PAYMENT_RECEIVED" -> ToastType.SUCCESS;
      default -> ToastType.INFO;
    };
  }

  private String defaultTitle(String type) {
    if (type == null) {
      return "Notification";
    }

    return switch (type) {
      case "OUTBID" -> "Outbid alert";
      case "PAYMENT_DUE" -> "Payment reminder";
      case "AUCTION_WON" -> "Auction won";
      case "ITEM_APPROVED" -> "Item approved";
      case "AUCTION_LOST" -> "Auction result";
      case "ITEM_REJECTED" -> "Item rejected";
      case "BID_PLACED" -> "Bid placed";
      case "AUCTION_STARTED" -> "Auction started";
      case "AUCTION_ENDED" -> "Auction closed";
      case "PAYMENT_RECEIVED" -> "Payment received";
      case "SYSTEM" -> "System message";
      default -> "Notification";
    };
  }

  private void showToast(String title, String message, ToastType type) {
    Window owner = findOwnerWindow();
    if (owner == null) {
      System.out.println(title + " - " + message);
      return;
    }

    Popup popup = new Popup();
    popup.setAutoFix(true);
    popup.setAutoHide(true);
    popup.setHideOnEscape(true);

    HBox card = buildToastCard(title, message, type);
    card.setOpacity(0);
    popup.getContent().add(card);

    double x = owner.getX() + Math.max(18, owner.getWidth() - TOAST_WIDTH - TOAST_RIGHT_MARGIN);
    double y = owner.getY() + TOAST_TOP_MARGIN;
    popup.show(owner, x, y);

    FadeTransition fadeIn = new FadeTransition(Duration.millis(160), card);
    fadeIn.setFromValue(0);
    fadeIn.setToValue(1);
    fadeIn.play();

    PauseTransition delay = new PauseTransition(Duration.seconds(4.0));
    delay.setOnFinished(event -> {
      FadeTransition fadeOut = new FadeTransition(Duration.millis(190), card);
      fadeOut.setFromValue(1);
      fadeOut.setToValue(0);
      fadeOut.setOnFinished(done -> popup.hide());
      fadeOut.play();
    });
    delay.play();
  }

  private HBox buildToastCard(String title, String message, ToastType type) {
    HBox card = new HBox(13);
    card.setAlignment(Pos.TOP_LEFT);
    card.setPadding(new Insets(14, 18, 14, 16));
    card.setPrefWidth(TOAST_WIDTH);
    card.setMaxWidth(TOAST_WIDTH);
    card.setStyle(
        "-fx-background-color: " + type.background + ";"
            + "-fx-background-radius: 18;"
            + "-fx-border-color: " + type.accent + ";"
            + "-fx-border-width: 0 0 0 4;"
            + "-fx-border-radius: 18;"
            + "-fx-effect: dropshadow(gaussian, rgba(18, 35, 30, 0.22), 22, 0.18, 0, 8);"
    );

    Label icon = new Label(type.icon);
    icon.setAlignment(Pos.CENTER);
    icon.setMinSize(30, 30);
    icon.setPrefSize(30, 30);
    icon.setMaxSize(30, 30);
    icon.setStyle(
        "-fx-background-color: " + type.accent + ";"
            + "-fx-background-radius: 999;"
            + "-fx-text-fill: " + type.iconText + ";"
            + "-fx-font-size: 15px;"
            + "-fx-font-weight: 900;"
    );

    Label titleLabel = new Label(title);
    titleLabel.setStyle(
        "-fx-text-fill: " + type.titleText + ";"
            + "-fx-font-size: 14px;"
            + "-fx-font-weight: 800;"
    );

    Label messageLabel = new Label(cleanMessage(message));
    messageLabel.setWrapText(true);
    messageLabel.setMaxWidth(285);
    messageLabel.setStyle(
        "-fx-text-fill: " + type.bodyText + ";"
            + "-fx-font-size: 12px;"
            + "-fx-font-weight: 500;"
            + "-fx-line-spacing: 2;"
    );

    VBox copy = new VBox(3, titleLabel, messageLabel);
    copy.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(copy, Priority.ALWAYS);

    card.getChildren().addAll(icon, copy);
    return card;
  }

  private Window findOwnerWindow() {
    for (Window window : Window.getWindows()) {
      if (window != null && window.isShowing() && window.isFocused()) {
        return window;
      }
    }

    for (Window window : Window.getWindows()) {
      if (window != null && window.isShowing()) {
        return window;
      }
    }

    return null;
  }

  private String cleanTitle(String title, String fallback) {
    return title == null || title.isBlank() ? fallback : title.trim();
  }

  private String cleanMessage(String message) {
    return message == null || message.isBlank() ? "Action completed successfully." : message.trim();
  }

  private void runOnFxThread(Runnable task) {
    if (Platform.isFxApplicationThread()) {
      task.run();
    } else {
      Platform.runLater(task);
    }
  }
}
