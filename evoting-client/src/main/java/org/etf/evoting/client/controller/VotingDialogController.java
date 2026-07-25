package org.etf.evoting.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.etf.evoting.client.model.*;
import org.etf.evoting.client.util.KeyStoreHelper;
import org.etf.evoting.client.model.UserSession; // Ili klasa gde čuvaš trenutno ulogovanog korisnika

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.List;
import java.util.Optional;

public class VotingDialogController {

    @FXML private Label electionTitleLabel;
    @FXML private Label electionDescriptionLabel;
    @FXML private VBox optionsContainer;
    @FXML private Label statusLabel;

    private ToggleGroup voteGroup = new ToggleGroup();
    private Integer currentElectionId;
    private Runnable onVoteSubmittedCallback;

    public void setOnVoteSubmittedCallback(Runnable callback) {
        this.onVoteSubmittedCallback = callback;
    }

    public void setElectionData(Integer electionId, String title, String description, List<ElectionOptionDTO> options) {
        this.currentElectionId = electionId;
        this.electionTitleLabel.setText(title);
        this.electionDescriptionLabel.setText(description);

        optionsContainer.getChildren().clear();

        for (ElectionOptionDTO option : options) {
            RadioButton rb = new RadioButton(option.getOptionText());
            rb.setToggleGroup(voteGroup);
            rb.setUserData(option.getId());

            optionsContainer.getChildren().add(rb);
        }
    }

    public void setElectionData(ElectionDTO election) {
        if (election != null) {
            this.currentElectionId = election.getId();

            if (this.electionTitleLabel != null) {
                this.electionTitleLabel.setText(election.getTitle());
            }
            if (this.electionDescriptionLabel != null) {
                this.electionDescriptionLabel.setText(election.getDescription());
            }

            List<ElectionOptionDTO> options = election.getOptions() != null ? election.getOptions() : List.of();

            setElectionData(
                    election.getId(),
                    election.getTitle(),
                    election.getDescription(),
                    options
            );
        }
    }

    @FXML
    private void handleSubmitVote() {
        Toggle selectedToggle = voteGroup.getSelectedToggle();
        if (selectedToggle == null) {
            statusLabel.setText("Molimo izaberite opciju za glasanje.");
            return;
        }

        Integer selectedOptionId = (Integer) selectedToggle.getUserData();
        Integer loggedUserId = UserSession.getUserId(); // Prilagodi vašoj Session klasi
        String username = UserSession.getUsername();   // Prilagodi vašoj Session klasi

        try {
            // 1. Odabir .p12 fajla preko FileChooser-a
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Odaberite Vaš .p12 sertifikat za potpisivanje");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PKCS12 Certifikat (*.p12)", "*.p12"));

            Stage stage = (Stage) statusLabel.getScene().getWindow();
            File p12File = fileChooser.showOpenDialog(stage);

            if (p12File == null) {
                statusLabel.setText("Glasanje otkazano (nije izabran .p12 fajl).");
                return;
            }

            // 2. Traženje lozinke za .p12 fajl
            TextInputDialog passwordDialog = new TextInputDialog();
            passwordDialog.setTitle("Verifikacija identiteta");
            passwordDialog.setHeaderText("Unesite lozinku za Vaš .p12 kontejner:");
            passwordDialog.setContentText("Lozinka:");

            Optional<String> passwordResult = passwordDialog.showAndWait();
            if (passwordResult.isEmpty() || passwordResult.get().isBlank()) {
                statusLabel.setText("Glasanje otkazano (prazna lozinka).");
                return;
            }
            String p12Password = passwordResult.get();

            // 3. Digitalno potpisivanje glasa
            String rawDataToSign = currentElectionId + ":" + selectedOptionId + ":" + loggedUserId;
            PrivateKey privateKey = KeyStoreHelper.loadPrivateKeyFromP12(p12File, username, p12Password);
            String signatureBase64 = KeyStoreHelper.signData(rawDataToSign, privateKey);

            // 4. Slanje glasa na Backend (/api/voting/cast)
            String receiptCode = sendCastVoteRequest(currentElectionId, selectedOptionId, loggedUserId, signatureBase64);

            // 5. Prikaz potvrde sa receipt code-om
            showReceiptAlert(receiptCode);

            // 6. Callback i zatvaranje
            if (onVoteSubmittedCallback != null) {
                onVoteSubmittedCallback.run();
            }
            closeWindow();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Greška pri glasanju: " + e.getMessage());
        }
    }

    /**
     * Pomoćna metoda za slanje POST zahtjeva na backend
     */
    private String sendCastVoteRequest(Integer electionId, Integer optionId, Integer userId, String signatureBase64) throws Exception {
        URL url = new URL("http://localhost:8080/api/voting/cast");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        // DODAJ OVO (pretpostavljajući da UserSession čuva JWT token iz prijave):
        if (UserSession.getToken() != null) {
            conn.setRequestProperty("Authorization", "Bearer " + UserSession.getToken());
        }

        conn.setDoOutput(true);

        String jsonInputString = String.format(
                "{\"electionId\":%d, \"optionId\":%d, \"userId\":%d, \"voterSignatureBase64\":\"%s\"}",
                electionId, optionId, userId, signatureBase64
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            String errorMsg = "";
            try (InputStream es = conn.getErrorStream()) {
                if (es != null) {
                    errorMsg = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}

            throw new RuntimeException("Server vratio status code " + code + (errorMsg.isBlank() ? "" : ": " + errorMsg));
        }

        try (InputStream is = conn.getInputStream()) {
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // Ekstrakcija receiptCode-a iz JSON-a
            return extractJsonValue(response, "receiptCode");
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return "";
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private void showReceiptAlert(String receiptCode) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Glasanje Uspješno");
        alert.setHeaderText("Vaš glas je enkriptovan i sačuvan!");
        alert.setContentText("Sačuvajte Vaš potvrdni kod (Receipt Code) za kasniju verifikaciju:\n\n" + receiptCode);

        // Mogućnost lakog kopiranja
        TextArea textArea = new TextArea(receiptCode);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxHeight(60);
        alert.getDialogPane().setExpandableContent(textArea);
        alert.getDialogPane().setExpanded(true);

        alert.showAndWait();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}