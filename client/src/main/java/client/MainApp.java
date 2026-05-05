package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static final double APP_WIDTH = 1000;
    public static final double APP_HEIGHT = 650;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(
                MainApp.class.getResource("/client/user-home.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load(), APP_WIDTH, APP_HEIGHT);

        stage.setTitle("Auction System");
        stage.setScene(scene);
        stage.setMinWidth(APP_WIDTH);
        stage.setMinHeight(APP_HEIGHT);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}