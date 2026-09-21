package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.etf.evoting.client.model.ElectionResultDTO;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ResultsController {

    @FXML private Label titleLabel;
    @FXML private Label totalVotesLabel;
    @FXML private Label statusLabel;
    @FXML private PieChart resultsPieChart;
    @FXML private TextArea reportTextArea;

    private Integer electionId;
    private String jwtToken;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void setElectionData(Integer electionId, String jwtToken) {
        this.electionId = electionId;
        this.jwtToken = jwtToken;

        // Automatski učitaj rezultate čim stignu podaci o izboru
        fetchResultsFromBackend();
    }

    private void fetchResultsFromBackend() {
        if (electionId == null) {
            showAlert(Alert.AlertType.ERROR, "Greška", "ID izbora nije postavljen!");
            return;
        }

        statusLabel.setText("Učitavanje rezultata...");

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/elections/" + electionId + "/results"))
                    .header("Content-Type", "application/json")
                    .GET();

            if (jwtToken != null && !jwtToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + jwtToken);
            }

            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        Platform.runLater(() -> {
                            if (response.statusCode() == 200) {
                                try {
                                    ElectionResultDTO result = objectMapper.readValue(response.body(), ElectionResultDTO.class);
                                    displayResults(result);
                                } catch (Exception e) {
                                    statusLabel.setText("Greška pri obradi podataka.");
                                    showAlert(Alert.AlertType.ERROR, "Greška", "Nije moguće prikazati rezultate: " + e.getMessage());
                                }
                            } else {
                                statusLabel.setText("Greška na serveru: " + response.statusCode());
                                showAlert(Alert.AlertType.ERROR, "Greška", "Server je vratio status: " + response.statusCode() + "\n" + response.body());
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            statusLabel.setText("Greška pri komunikaciji sa serverom.");
                            showAlert(Alert.AlertType.ERROR, "Greška", "Mrežna greška: " + ex.getMessage());
                        });
                        return null;
                    });

        } catch (Exception e) {
            statusLabel.setText("Greška pri kreiranju zahtjeva.");
            showAlert(Alert.AlertType.ERROR, "Greška", "Neuspješno preuzimanje rezultata: " + e.getMessage());
        }
    }

    private void displayResults(ElectionResultDTO result) {
        titleLabel.setText("Rezultati: " + (result.getElectionTitle() != null ? result.getElectionTitle() : "Izbori #" + electionId));
        totalVotesLabel.setText("Ukupno glasova: " + result.getTotalVotes());

        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        if (result.getVoteCounts() != null) {
            result.getVoteCounts().forEach((optionName, count) -> {
                pieChartData.add(new PieChart.Data(optionName + " (" + count + ")", count));
            });
        }
        resultsPieChart.setData(pieChartData);

        StringBuilder reportSb = new StringBuilder();
        if (result.getReportContent() != null) {
            reportSb.append(result.getReportContent()).append("\n\n");
        }
        if (result.getReportSignatureBase64() != null) {
            reportSb.append("Digitalni potpis (Base64):\n").append(result.getReportSignatureBase64());
        }
        reportTextArea.setText(reportSb.toString());

        statusLabel.setText("Rezultati uspješno učitani.");
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