package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.etf.evoting.client.model.UserSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CreateElectionController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField optionInputField;
    @FXML private ListView<String> optionsListView;
    @FXML private Label errorLabel;

    private final ObservableList<String> optionsList = FXCollections.observableArrayList();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Runnable onElectionCreatedCallback;

    @FXML
    public void initialize() {
        optionsListView.setItems(optionsList);
    }

    public void setOnElectionCreatedCallback(Runnable callback) {
        this.onElectionCreatedCallback = callback;
    }

    @FXML
    private void handleAddOption() {
        String option = optionInputField.getText().trim();
        if (!option.isEmpty()) {
            optionsList.add(option);
            optionInputField.clear();
            errorLabel.setVisible(false);
        }
    }

    @FXML
    private void handleCreateElection() {
        String title = titleField.getText().trim();
        String description = descriptionArea.getText().trim();

        if (title.isEmpty() || startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
            showError("Naziv i datumi su obavezni!");
            return;
        }

        if (optionsList.size() < 2) {
            showError("Morate unijeti bar 2 opcije za glasanje.");
            return;
        }

        // Formatiramo datume u ISO-8601 String bez potrebe za JavaTimeModule
        LocalDateTime startDateTime = LocalDateTime.of(startDatePicker.getValue(), LocalTime.of(0, 0));
        LocalDateTime endDateTime = LocalDateTime.of(endDatePicker.getValue(), LocalTime.of(23, 59, 59));

        String startDateStr = startDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String endDateStr = endDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        Integer organizerId = UserSession.getInstance().getUserId();

        if (organizerId == null) {
            showError("Greška: Sesija ne sadrži ID organizatora.");
            return;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", title);
        requestBody.put("description", description);
        requestBody.put("startDate", startDateStr);
        requestBody.put("endDate", endDateStr);
        requestBody.put("organizerId", organizerId);
        requestBody.put("options", new ArrayList<>(optionsList));

        try {
            String json = objectMapper.writeValueAsString(requestBody);

            String token = UserSession.getInstance().getToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/elections/create"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token) // ✅ DODATO ZAHTIJEVANO ZAGLAVLJE
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                if (onElectionCreatedCallback != null) {
                    onElectionCreatedCallback.run();
                }
                closeWindow();
            } else {
                System.err.println("Backend odgovor (Status " + response.statusCode() + "): " + response.body());
                showError("Greška przy kreiranju: " + response.body());
            }

        } catch (Exception e) {
            showError("Greška u komunikaciji sa serverom: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void closeWindow() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }
}