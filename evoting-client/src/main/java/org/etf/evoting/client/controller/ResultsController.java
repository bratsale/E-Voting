package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.etf.evoting.client.model.ElectionResultDTO;
import org.etf.evoting.client.util.KeyStoreHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class ResultsController {

    @FXML private Label titleLabel;
    @FXML private Label totalVotesLabel;
    @FXML private Label statusLabel;
    @FXML private PieChart resultsPieChart;
    @FXML private TextArea reportTextArea;

    private Integer electionId;
    private String jwtToken; // Proslijediti token ulogovanog korisnika ako backend koristi Spring Security

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setElectionData(Integer electionId, String jwtToken) {
        this.electionId = electionId;
        this.jwtToken = jwtToken;
    }

    @FXML
    public void handleLoadPrivateKeyAndFetchResults() {
        if (electionId == null) {
            showAlert(Alert.AlertType.ERROR, "Greška", "ID izbora nije postavljen!");
            return;
        }

        Stage stage = (Stage) titleLabel.getScene().getWindow();

        // Koristimo tvoj KeyStoreHelper za odabir i čitanje PEM fajla
        String pemKey = KeyStoreHelper.selectAndReadPemKey(stage);

        if (pemKey == null || pemKey.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Upozorenje", "Niste izabrali privatni ključ!");
            return;
        }

        fetchResultsFromBackend(pemKey);
    }

    private void fetchResultsFromBackend(String pemKey) {
        try {
            // Sastavljanje JSON tijela za request: { "privateKeyPem": "..." }
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("privateKeyPem", pemKey);
            String jsonBody = objectMapper.writeValueAsString(requestMap);

            // POST zahtjev na backend: /api/voting/results/{electionId}
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/voting/results/" + electionId))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (jwtToken != null && !jwtToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + jwtToken);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Deserijalizujemo backend odgovor u klijentski ElectionResultDTO
                ElectionResultDTO result = objectMapper.readValue(response.body(), ElectionResultDTO.class);
                displayResults(result);
            } else {
                statusLabel.setText("Greška na serveru: " + response.statusCode());
                showAlert(Alert.AlertType.ERROR, "Greška", "Server je vratio status: " + response.statusCode() + "\n" + response.body());
            }

        } catch (Exception e) {
            statusLabel.setText("Greška pri komunikaciji sa serverom.");
            showAlert(Alert.AlertType.ERROR, "Greška", "Neuspješno preuzimanje rezultata: " + e.getMessage());
        }
    }

    private void displayResults(ElectionResultDTO result) {
        titleLabel.setText("Rezultati: " + (result.getElectionTitle() != null ? result.getElectionTitle() : "Izbori #" + electionId));
        totalVotesLabel.setText("Ukupno glasova: " + result.getTotalVotes());

        // Prikaz grafikona na osnovu getVoteCounts() iz tvog DTO-a
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        if (result.getVoteCounts() != null) {
            result.getVoteCounts().forEach((optionName, count) -> {
                pieChartData.add(new PieChart.Data(optionName + " (" + count + ")", count));
            });
        }
        resultsPieChart.setData(pieChartData);

        // Prikaz izvještaja i potpisa
        StringBuilder reportSb = new StringBuilder();
        if (result.getReportContent() != null) {
            reportSb.append(result.getReportContent()).append("\n\n");
        }
        if (result.getReportSignatureBase64() != null) {
            reportSb.append("Digitalni potpis (Base64):\n").append(result.getReportSignatureBase64());
        }
        reportTextArea.setText(reportSb.toString());

        statusLabel.setText("Rezultati uspješno dešifrovani i učitani.");
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}