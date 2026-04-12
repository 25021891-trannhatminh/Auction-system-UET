module com.example.auction {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.example.auction to javafx.fxml;
    opens com.example.auction.controller to javafx.fxml;

    exports com.example.auction;
}
