package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(
                MainApp.class.getResource("/client/auth.fxml")
        );

        Scene scene = SceneNavigator.createInitialScene(fxmlLoader.load());

        stage.setTitle("Auction System");
        stage.setScene(scene);
        SceneNavigator.applyStageBounds(stage);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}