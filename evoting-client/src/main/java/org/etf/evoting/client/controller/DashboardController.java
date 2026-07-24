package org.etf.evoting.client.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.etf.evoting.client.model.UserSession;

public class DashboardController {

    @FXML private Label userInfoLabel;

    @FXML
    public void initialize() {
        String username = UserSession.getInstance().getUsername();
        userInfoLabel.setText("Prijavljeni ste kao: " + username);
    }

    @FXML
    private void handleLogout() {
        try {
            UserSession.getInstance().cleanUserSession();

            Stage stage = (Stage) userInfoLabel.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            stage.setScene(new Scene(root, 500, 400));
            stage.setTitle("E-Voting System - Prijava");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}