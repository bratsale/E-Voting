package org.etf.evoting.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Učitavamo login.fxml iz resources/fxml/ foldera
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(root, 500, 400);

        stage.setTitle("E-Voting System - Prijava");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}