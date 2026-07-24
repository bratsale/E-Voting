package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.etf.evoting.client.model.UserSession;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField certPathField;
    @FXML private Label errorLabel;

    private File selectedCertFile;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    private void handleBrowseCertificate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Izaberite vaš digitalni sertifikat");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Sertifikati (*.p12, *.pfx)", "*.p12", "*.pfx")
        );
        selectedCertFile = fileChooser.showOpenDialog(usernameField.getScene().getWindow());
        if (selectedCertFile != null) {
            certPathField.setText(selectedCertFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Molimo unesite korisničko ime i lozinku.");
            return;
        }

        try {
            // 1. Slanje autentifikacionog zahtjeva na backend
            String jsonBody = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", username, password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 2. Parsiranje JSON odgovora (izvlačenje JWT tokena i uloge)
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String token = jsonResponse.has("token") ? jsonResponse.get("token").asText() : "";
                String role = jsonResponse.has("role") ? jsonResponse.get("role").asText() : "ROLE_VOTER";

                UserSession.getInstance().setUsername(username);
                UserSession.getInstance().setToken(token);
                UserSession.getInstance().setRole(role);

                // 3. Ako je izabran digitalni sertifikat, učitavamo ga u sesiju
                if (selectedCertFile != null && selectedCertFile.exists()) {
                    loadCertificateIntoSession(selectedCertFile, password);
                }

                // 4. Otvaranje Dashboard-a
                Stage stage = (Stage) usernameField.getScene().getWindow();
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("E-Voting System - Glavni Meni");
                stage.centerOnScreen();

            } else {
                showError("Neispravno korisničko ime ili lozinka!");
            }

        } catch (Exception e) {
            showError("Greška pri povezivanju sa serverom: " + e.getMessage());
        }
    }

    private void loadCertificateIntoSession(File certFile, String password) {
        try (FileInputStream fis = new FileInputStream(certFile)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fis, password.toCharArray());

            String alias = keyStore.aliases().nextElement();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);

            UserSession.getInstance().setPrivateKey(privateKey);
            UserSession.getInstance().setCertificate(cert);
        } catch (Exception e) {
            System.err.println("Upozorenje: Nije uspjelo učitavanje sertifikata: " + e.getMessage());
        }
    }

    @FXML
    private void handleGoToRegister() {
        try {
            Stage stage = (Stage) usernameField.getScene().getWindow();
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/register.fxml"));
            stage.setScene(new Scene(root, 520, 650));
            stage.setTitle("E-Voting System - Registracija");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}