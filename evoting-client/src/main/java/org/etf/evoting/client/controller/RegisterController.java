package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RegisterController {

    @FXML private RadioButton voterRadio;
    @FXML private RadioButton organizerRadio;
    @FXML private ToggleGroup roleGroup;

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    // Voter
    @FXML private Label firstNameLabel;
    @FXML private TextField firstNameField;
    @FXML private Label lastNameLabel;
    @FXML private TextField lastNameField;

    // Organizer
    @FXML private Label orgNameLabel;
    @FXML private TextField orgNameField;
    @FXML private Label orgIdLabel;
    @FXML private TextField orgIdField;

    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    public void initialize() {
        roleGroup = new ToggleGroup();
        voterRadio.setToggleGroup(roleGroup);
        organizerRadio.setToggleGroup(roleGroup);
    }

    @FXML
    private void handleRoleChange() {
        boolean isVoter = voterRadio.isSelected();

        firstNameLabel.setVisible(isVoter);
        firstNameLabel.setManaged(isVoter);
        firstNameField.setVisible(isVoter);
        firstNameField.setManaged(isVoter);

        lastNameLabel.setVisible(isVoter);
        lastNameLabel.setManaged(isVoter);
        lastNameField.setVisible(isVoter);
        lastNameField.setManaged(isVoter);

        orgNameLabel.setVisible(!isVoter);
        orgNameLabel.setManaged(!isVoter);
        orgNameField.setVisible(!isVoter);
        orgNameField.setManaged(!isVoter);

        orgIdLabel.setVisible(!isVoter);
        orgIdLabel.setManaged(!isVoter);
        orgIdField.setVisible(!isVoter);
        orgIdField.setManaged(!isVoter);
    }

    @FXML
    private void handleRegister() {
        hideMessages();

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        boolean isVoter = voterRadio.isSelected();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Lozinka i korisničko ime su obavezni.");
            return;
        }

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("username", username);
        requestBody.put("password", password);
        requestBody.put("role", isVoter ? "VOTER" : "ORGANIZER");

        if (isVoter) {
            String firstName = firstNameField.getText().trim();
            String lastName = lastNameField.getText().trim();
            if (firstName.isEmpty() || lastName.isEmpty()) {
                showError("Ime i prezime su obavezni za glasača.");
                return;
            }
            requestBody.put("firstName", firstName);
            requestBody.put("lastName", lastName);
        } else {
            String orgName = orgNameField.getText().trim();
            String orgId = orgIdField.getText().trim();
            if (orgName.isEmpty() || orgId.isEmpty()) {
                showError("Naziv i ID organizacije su obavezni za organizatora.");
                return;
            }
            requestBody.put("orgName", orgName);
            requestBody.put("orgId", orgId);
        }

        try {
            String jsonRequest = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                // Backend nam vraća Base64 sačuvan .p12 sertifikat paket koji klijent treba da snimi na disk!
                Map<String, Object> respMap = objectMapper.readValue(response.body(), Map.class);
                if (respMap.containsKey("p12File")) {
                    byte[] p12Bytes = Base64.getDecoder().decode((String) respMap.get("p12File"));
                    saveP12File(username, p12Bytes);
                }

                showSuccess("Registracija uspješna! Sertifikat je sačuvan.");
            } else {
                showError("Greška pri registraciji: " + response.body());
            }

        } catch (Exception e) {
            showError("Greška u komunikaciji sa serverom: " + e.getMessage());
        }
    }

    private void saveP12File(String username, byte[] p12Bytes) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sačuvajte Vaš Digitalni Sertifikat (.p12)");
        fileChooser.setInitialFileName(username + "_cert.p12");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PKCS12 KeyStore (*.p12)", "*.p12"));

        File file = fileChooser.showSaveDialog(usernameField.getScene().getWindow());
        if (file != null) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(p12Bytes);
            } catch (Exception e) {
                showError("Nije uspjelo snimanje sertifikata na disk: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            stage.setScene(new Scene(root, 500, 420));
            stage.setTitle("E-Voting System - Prijava");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void showSuccess(String msg) {
        successLabel.setText(msg);
        successLabel.setVisible(true);
    }

    private void hideMessages() {
        errorLabel.setVisible(false);
        successLabel.setVisible(false);
    }
}