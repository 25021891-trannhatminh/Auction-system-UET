package client;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneNavigator {

    public static final double DEFAULT_WIDTH = 1150.0;
    public static final double DEFAULT_HEIGHT = 720.0;
    public static final double MIN_WIDTH = 860.0;
    public static final double MIN_HEIGHT = 560.0;

    private SceneNavigator() {
    }

    public static Scene createInitialScene(Parent root) {
        return new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static void applyStageBounds(Stage stage) {
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
    }

    public static void switchSceneKeepingWindow(Stage stage, Parent root, String title) {
        boolean wasMaximized = stage.isMaximized();
        boolean wasFullScreen = stage.isFullScreen();

        double sceneWidth = readCurrentSceneWidth(stage);
        double sceneHeight = readCurrentSceneHeight(stage);
        double windowWidth = readCurrentWindowWidth(stage, sceneWidth);
        double windowHeight = readCurrentWindowHeight(stage, sceneHeight);

        Scene nextScene = new Scene(root, sceneWidth, sceneHeight);

        stage.setTitle(title);
        applyStageBounds(stage);
        stage.setScene(nextScene);

        if (!wasMaximized && !wasFullScreen) {
            stage.setWidth(windowWidth);
            stage.setHeight(windowHeight);
        }

        stage.setMaximized(wasMaximized);
        stage.setFullScreen(wasFullScreen);
        stage.show();
    }

    private static double readCurrentSceneWidth(Stage stage) {
        double width = stage.getScene() != null ? stage.getScene().getWidth() : DEFAULT_WIDTH;
        if (Double.isNaN(width) || width <= 0) {
            width = DEFAULT_WIDTH;
        }
        return Math.max(MIN_WIDTH, width);
    }

    private static double readCurrentSceneHeight(Stage stage) {
        double height = stage.getScene() != null ? stage.getScene().getHeight() : DEFAULT_HEIGHT;
        if (Double.isNaN(height) || height <= 0) {
            height = DEFAULT_HEIGHT;
        }
        return Math.max(MIN_HEIGHT, height);
    }

    private static double readCurrentWindowWidth(Stage stage, double fallback) {
        double width = stage.getWidth();
        if (Double.isNaN(width) || width <= 0) {
            width = fallback;
        }
        return Math.max(MIN_WIDTH, width);
    }

    private static double readCurrentWindowHeight(Stage stage, double fallback) {
        double height = stage.getHeight();
        if (Double.isNaN(height) || height <= 0) {
            height = fallback;
        }
        return Math.max(MIN_HEIGHT, height);
    }
}
