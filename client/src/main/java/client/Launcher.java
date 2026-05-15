package client;

import javafx.application.Application;

/**
 * Starts the JavaFX auction client through the real {@link MainApp} entry point.
 */
public class Launcher {
  /**
   * Delegates application startup to JavaFX.
   *
   * @param args command-line arguments passed by the launcher
   */
  public static void main(String[] args) {
    Application.launch(MainApp.class, args);
  }
}
