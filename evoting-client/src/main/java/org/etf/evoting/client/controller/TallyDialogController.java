package org.etf.evoting.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.etf.evoting.client.model.UserSession;
import org.etf.evoting.client.util.KeyStoreHelper;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Optional;

public class TallyDialogController {

    @FXML private Label electionTitleLabel;
    @FXML private Label totalVotesLabel;
    @FXML private ListView<String> resultsListView;
    @FXML private Label statusLabel;

    private Integer electionId;

    public void setElectionData(Integer electionId, String electionTitle) {
        this.electionId = electionId;
        this.electionTitleLabel.setText("Prebrojavanje: " + electionTitle);
    }

    @FXML
    private void handleTallyVotes() {
        try {
            // 1. Odabir .p12 fajla organizatora
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Odaberite Vaš .p12 sertifikat (Organizator)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PKCS12 Certifikat (*.p12)", "*.p12"));

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            File p12File = fileChooser.showOpenDialog(stage);

            if (p12File == null) return;

            // 2. Unos lozinke
            TextInputDialog passwordDialog = new TextInputDialog();
            passwordDialog.setTitle("Autentifikacija Organizatora");
            passwordDialog.setHeaderText("Unesite lozinku za Vaš privatni ključ:");
            passwordDialog.setContentText("Lozinka:");

            Optional<String> passwordResult = passwordDialog.showAndWait();
            if (passwordResult.isEmpty() || passwordResult.get().isBlank()) return;

            String password = passwordResult.get();
            String username = UserSession.getUsername();

            // 3. Izvlačenje privatnog ključa i konverzija u PEM format
            PrivateKey privateKey = KeyStoreHelper.loadPrivateKeyFromP12(p12File, username, password);
            String privateKeyPem = convertPrivateKeyToPem(privateKey);

            // 4. Slanje na backend za prebrojavanje (/api/voting/tally/{electionId})
            sendTallyRequest(privateKeyPem);

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setTextFill(Color.RED);
            statusLabel.setText("Greška pri prebrojavanju: " + e.getMessage());
        }
    }

    private void sendTallyRequest(String privateKeyPem) throws Exception {
        URL url = new URL("http://localhost:8080/api/voting/tally/" + electionId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Escape-ujemo nove redove u PEM stringu za JSON format
        String jsonPayload = String.format("{\"organizerPrivateKeyPem\":\"%s\"}",
                privateKeyPem.replace("\n", "\\n").replace("\r", ""));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code == 200) {
            try (InputStream is = conn.getInputStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                parseAndDisplayResults(response);
            }
        } else {
            try (InputStream es = conn.getErrorStream()) {
                String errorMsg = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText("Greška sa servera: " + errorMsg);
            }
        }
    }

    private void parseAndDisplayResults(String jsonResponse) {
        resultsListView.getItems().clear();
        statusLabel.setTextFill(Color.GREEN);
        statusLabel.setText("Glasovi uspješno dešifrovani i prebrojani!");

        // Jednostavno ekstrakovanje za prikaz
        int totalVotesIndex = jsonResponse.indexOf("\"totalVotes\":");
        if (totalVotesIndex != -1) {
            int start = totalVotesIndex + 13;
            int end = jsonResponse.indexOf(",", start);
            if (end == -1) end = jsonResponse.indexOf("}", start);
            totalVotesLabel.setText("Ukupno prebrojano glasova: " + jsonResponse.substring(start, end).trim());
        }

        // Prikaz raw ili formatiranog rezultata u ListView
        resultsListView.getItems().add("Rezultati glasanja (po opcijama):");
        resultsListView.getItems().add(jsonResponse);
    }

    private String convertPrivateKeyToPem(PrivateKey privateKey) {
        String base64Key = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----";
    }
}