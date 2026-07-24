package org.etf.evoting.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.etf.evoting.client.model.ElectionDTO;
import org.etf.evoting.client.model.UserSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class DashboardController {

    @FXML private Label userInfoLabel;

    @FXML private TableView<ElectionDTO> electionsTable;
    @FXML private TableColumn<ElectionDTO, String> titleColumn;
    @FXML private TableColumn<ElectionDTO, String> statusColumn;
    @FXML private TableColumn<ElectionDTO, Void> actionColumn;
    @FXML private Button createElectionButton;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    public void initialize() {
        String username = UserSession.getInstance().getUsername();
        String role = UserSession.getInstance().getRole(); // Uvjeri se da čuvaš ulogu u UserSession pri loginu

        userInfoLabel.setText("Prijavljeni ste kao: " + username + " (" + role + ")");

        // Prikaži dugme za kreiranje samo ako je uloga ORGANIZER
        if ("ORGANIZER".equals(role)) {
            createElectionButton.setVisible(true);
        }

        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        setupActionColumn();
        loadActiveElections();
    }

    @FXML
    private void handleOpenCreateElectionModal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_election.fxml"));
            Parent root = loader.load();

            CreateElectionController controller = loader.getController();
            // Kada se glasanje kreira, osvježi tabelu automatski!
            controller.setOnElectionCreatedCallback(this::loadActiveElections);

            Stage stage = new Stage();
            stage.setTitle("Kreiraj Novo Glasanje");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void setupActionColumn() {
        Callback<TableColumn<ElectionDTO, Void>, TableCell<ElectionDTO, Void>> cellFactory = param -> new TableCell<>() {
            private final Button voteButton = new Button("Glasaj");

            {
                voteButton.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-cursor: hand;");
                voteButton.setOnAction(event -> {
                    ElectionDTO selectedElection = getTableView().getItems().get(getIndex());
                    handleOpenVotingScreen(selectedElection);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(voteButton);
                }
            }
        };

        actionColumn.setCellFactory(cellFactory);
    }

    private void loadActiveElections() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/elections/active"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<ElectionDTO> elections = objectMapper.readValue(
                        response.body(),
                        new TypeReference<List<ElectionDTO>>() {}
                );

                ObservableList<ElectionDTO> observableList = FXCollections.observableArrayList(elections);
                electionsTable.setItems(observableList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleOpenVotingScreen(ElectionDTO election) {
        System.out.println("Otvaram glasanje za izbor sa ID-jem: " + election.getId());
        // Ovde ide prelazak na prozor za glasanje sa ponuđenim kandidatima/opcijama
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