package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Base64;

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
        String certPath = certPathField.getText().trim();

        // 1. Provjera osnovnih polja
        if (username.isEmpty() || password.isEmpty()) {
            showError("Molimo unesite korisničko ime i lozinku.");
            return;
        }

        if (certPath.isEmpty()) {
            showError("Obavezno je priložiti digitalni sertifikat!");
            return;
        }

        // Ako fajl nije izabran preko dijaloga već unesen ručno
        if (selectedCertFile == null || !selectedCertFile.getAbsolutePath().equals(certPath)) {
            selectedCertFile = new File(certPath);
        }

        if (!selectedCertFile.exists() || !selectedCertFile.isFile()) {
            showError("Izabrani fajl sertifikata ne postoji na navedenoj putanji.");
            return;
        }

        // 2. Čitanje sertifikata i ključa iz .p12 fajla DOK SE PRIPREMA ZAHTJEV
        X509Certificate cert = null;
        PrivateKey privateKey = null;
        String certPem = null;

        try (FileInputStream fis = new FileInputStream(selectedCertFile)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(fis, password.toCharArray()); // Lozinka .p12 kontejnera

            String alias = keyStore.aliases().nextElement();
            privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
            cert = (X509Certificate) keyStore.getCertificate(alias);

            certPem = convertToPem(cert);

        } catch (Exception e) {
            showError("Neuspješno otvaranje sertifikata! Provjerite lozinku naloga/sertifikata.");
            return;
        }

        try {
            // 3. Formiranje JSON zahtjeva sa username, password I certificatePem
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("username", username);
            payload.put("password", password);
            payload.put("certificatePem", certPem);

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 4. Parsiranje JSON odgovora
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String token = jsonResponse.has("token") ? jsonResponse.get("token").asText() : "";
                String role = jsonResponse.has("role") ? jsonResponse.get("role").asText() : "ROLE_VOTER";

                Integer userId = null;
                if (jsonResponse.has("userId")) {
                    userId = jsonResponse.get("userId").asInt();
                } else if (jsonResponse.has("id")) {
                    userId = jsonResponse.get("id").asInt();
                }

                // 5. Čuvanje svih podataka i sertifikata u klijentsku sesiju
                UserSession.getInstance().setUsername(username);
                UserSession.getInstance().setToken(token);
                UserSession.getInstance().setRole(role);
                UserSession.getInstance().setUserId(userId);
                UserSession.getInstance().setPrivateKey(privateKey);
                UserSession.getInstance().setCertificate(cert);

                // 6. Otvaranje Dashboard-a
                Stage stage = (Stage) usernameField.getScene().getWindow();
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
                stage.setScene(new Scene(root, 800, 600));
                stage.setTitle("E-Voting System - Glavni Meni");
                stage.centerOnScreen();

            } else {
                // Izvlačenje tačne poruke greške sa bekenda (npr. "Priloženi sertifikat pripada korisniku...")
                String serverMsg = "Neispravni podaci za prijavu ili sertifikat!";
                try {
                    JsonNode errJson = objectMapper.readTree(response.body());
                    if (errJson.has("message")) {
                        serverMsg = errJson.get("message").asText();
                    }
                } catch (Exception ignored) {}

                showError(serverMsg);
            }

        } catch (Exception e) {
            showError("Greška pri povezivanju sa serverom: " + e.getMessage());
        }
    }

    /**
     * Pomoćna metoda za prevođenje X509Certificate u PEM string.
     */
    private String convertToPem(X509Certificate cert) throws Exception {
        String base64Cert = Base64.getEncoder().encodeToString(cert.getEncoded());
        return "-----BEGIN CERTIFICATE-----\n" +
                base64Cert.replaceAll("(.{64})", "$1\n") +
                "\n-----END CERTIFICATE-----";
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