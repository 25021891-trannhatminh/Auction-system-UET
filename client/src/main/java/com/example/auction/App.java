package com.example.auction;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(
                App.class.getResource("/com/example/auction/auth.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load(), 1000, 650);

        stage.setTitle("Auction System");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}