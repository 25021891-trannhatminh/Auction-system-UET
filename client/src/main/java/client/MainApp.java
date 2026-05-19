package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application entry point for the auction client.
 */
public class MainApp extends Application {

    /**
     * Loads the authentication screen and applies shared stage sizing rules.
     *
     * @param stage primary JavaFX stage provided by the runtime
     * @throws Exception when the authentication FXML cannot be loaded
     */
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(
            MainApp.class.getResource("/client/user-home.fxml")
        );

        Scene scene = SceneNavigator.createInitialScene(fxmlLoader.load());

        stage.setTitle("Auction System");
        stage.setScene(scene);
        SceneNavigator.applyStageBounds(stage);
        stage.show();
    }

    /**
     * Launches the JavaFX client.
     *
     * @param args command-line arguments passed to JavaFX
     */
    public static void main(String[] args) {
        launch();
    }
}
