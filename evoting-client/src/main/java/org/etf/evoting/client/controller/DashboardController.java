package org.etf.evoting.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
    @FXML private TabPane mainTabPane;
    @FXML private Tab activeTab;
    @FXML private Tab finishedTab;

    // Tabela za aktivna glasanja
    @FXML private TableView<ElectionDTO> activeElectionsTable;
    @FXML private TableColumn<ElectionDTO, String> activeTitleColumn;
    @FXML private TableColumn<ElectionDTO, String> activeStatusColumn;
    @FXML private TableColumn<ElectionDTO, Void> activeActionColumn;

    // Tabela za završena glasanja
    @FXML private TableView<ElectionDTO> finishedElectionsTable;
    @FXML private TableColumn<ElectionDTO, String> finishedTitleColumn;
    @FXML private TableColumn<ElectionDTO, String> finishedStatusColumn;
    @FXML private TableColumn<ElectionDTO, Void> finishedActionColumn;

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
        String role = UserSession.getInstance().getRole();

        userInfoLabel.setText("Prijavljeni ste kao: " + username + " (" + role + ")");

        if ("ORGANIZER".equalsIgnoreCase(role)) {
            createElectionButton.setVisible(true);
        }

        // Podešavanje kolona za aktivnu tabelu
        activeTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        activeStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        setupActionColumn(activeActionColumn, true);

        // Podešavanje kolona za završenu tabelu
        finishedTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        finishedStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        setupActionColumn(finishedActionColumn, false);

        // Event pri zamjeni taba
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == activeTab) {
                loadActiveElections();
            } else if (newTab == finishedTab) {
                loadFinishedElections();
            }
        });

        // Učitaj početne podatke
        loadActiveElections();
    }

    public void loadActiveElections() {
        fetchElections("http://localhost:8080/api/elections/active", activeElectionsTable);
    }

    public void loadFinishedElections() {
        fetchElections("http://localhost:8080/api/elections/finished", finishedElectionsTable);
    }

    private void fetchElections(String endpointUrl, TableView<ElectionDTO> targetTable) {
        String token = UserSession.getInstance().getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                            targetTable.setItems(FXCollections.observableArrayList());
                            return;
                        }

                        try {
                            List<ElectionDTO> elections = objectMapper.readValue(
                                    response.body(),
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, ElectionDTO.class)
                            );
                            targetTable.setItems(FXCollections.observableArrayList(elections));
                        } catch (Exception e) {
                            e.printStackTrace();
                            targetTable.setItems(FXCollections.observableArrayList());
                        }
                    });
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }

    private void setupActionColumn(TableColumn<ElectionDTO, Void> column, boolean isActiveTab) {
        String role = UserSession.getInstance().getRole();

        Callback<TableColumn<ElectionDTO, Void>, TableCell<ElectionDTO, Void>> cellFactory = param -> new TableCell<>() {
            private final Button voteButton = new Button("Glasaj");
            private final Button finishButton = new Button("Završi");
            private final Button resultsButton = new Button("Rezultati");

            {
                voteButton.setStyle("-fx-background-color: #2E7D32; -fx-text-fill: white; -fx-cursor: hand;");
                finishButton.setStyle("-fx-background-color: #C62828; -fx-text-fill: white; -fx-cursor: hand;");
                resultsButton.setStyle("-fx-background-color: #1565C0; -fx-text-fill: white; -fx-cursor: hand;");

                voteButton.setOnAction(event -> {
                    ElectionDTO selectedElection = getTableView().getItems().get(getIndex());
                    handleOpenVotingScreen(selectedElection);
                });

                finishButton.setOnAction(event -> {
                    ElectionDTO selectedElection = getTableView().getItems().get(getIndex());
                    handleFinishElection(selectedElection);
                });

                resultsButton.setOnAction(event -> {
                    ElectionDTO selectedElection = getTableView().getItems().get(getIndex());
                    handleShowResults(selectedElection);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ElectionDTO election = getTableView().getItems().get(getIndex());

                    if ("ORGANIZER".equalsIgnoreCase(role)) {
                        if (isActiveTab && "ACTIVE".equalsIgnoreCase(election.getStatus())) {
                            setGraphic(finishButton);
                        } else {
                            setGraphic(resultsButton);
                        }
                    } else {
                        if (isActiveTab) {
                            setGraphic(voteButton);
                        } else {
                            setGraphic(resultsButton);
                        }
                    }
                }
            }
        };

        column.setCellFactory(cellFactory);
    }

    private void handleFinishElection(ElectionDTO election) {
        String token = UserSession.getInstance().getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/elections/" + election.getId() + "/finish"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response.statusCode() == 200) {
                            loadActiveElections();
                            handleShowResults(election);
                        } else {
                            showErrorAlert("Greška", "Nije moguće završiti glasanje: " + response.body());
                        }
                    });
                });
    }

    private void handleShowResults(ElectionDTO election) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/results_view.fxml"));
            Parent root = loader.load();

            ResultsController controller = loader.getController();
            String token = UserSession.getInstance().getToken();
            controller.setElectionData(election.getId(), token);

            Stage stage = new Stage();
            stage.setTitle("Rezultati izbora: " + election.getTitle());
            stage.setScene(new Scene(root));
            stage.show();

        } catch (Exception e) {
            System.err.println("Greška pri otvaranju prozora za rezultate: " + e.getMessage());
            e.printStackTrace();
            showErrorAlert("Greška", "Nije moguće otvoriti prozor sa rezultatima: " + e.getMessage());
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleOpenCreateElectionModal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_election.fxml"));
            Parent root = loader.load();

            CreateElectionController controller = loader.getController();
            controller.setOnElectionCreatedCallback(this::loadActiveElections);

            Stage stage = new Stage();
            stage.setTitle("Kreiraj Novo Glasanje");
            stage.setScene(new Scene(root));
            stage.show();
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