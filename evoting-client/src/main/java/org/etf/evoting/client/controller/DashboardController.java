package org.etf.evoting.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.application.Platform;
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

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
    private final ObjectMapper objectMapper;

    public DashboardController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }



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
            String token = UserSession.getInstance().getToken();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/api/elections/active"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(json -> {
                        try {
                            // Deserijalizacija u listu ElectionDTO objekata
                            List<ElectionDTO> elections = objectMapper.readValue(
                                    json,
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, ElectionDTO.class)
                            );

                            // Ažuriranje UI-ja na JavaFX Application Thread-u
                            Platform.runLater(() -> {
                                electionsTable.setItems(FXCollections.observableArrayList(elections));
                            });
                        } catch (Exception e) {
                            System.err.println("Greška pri parsiranju izbora: " + e.getMessage());
                            e.printStackTrace();
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("Greška u mrežnom zahtjevu: " + ex.getMessage());
                        ex.printStackTrace();
                        return null;
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleOpenVotingScreen(ElectionDTO election) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/voting_dialog.fxml"));
            Parent root = loader.load();

            VotingDialogController controller = loader.getController();
            controller.setElectionData(election);

            // Nakon glasanja osvježi listu glasanja na dashboardu
            controller.setOnVoteSubmittedCallback(this::loadActiveElections);

            Stage stage = new Stage();
            stage.setTitle("Glasanje: " + election.getTitle());
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            System.err.println("Greška pri otvaranju prozora za glasanje: " + e.getMessage());
            e.printStackTrace();
        }
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