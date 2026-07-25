package org.etf.evoting.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.etf.evoting.client.model.*; // ili klasa za opciju sa klijenta

import java.util.List;

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

    // Ova metoda se poziva kada se otvara dijalog i proslijeđuju podaci o izboru i opcijama
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

    // Dodaj ovu metodu u VotingDialogController.java:
    public void setElectionData(ElectionDTO election) {
        if (election != null) {
            this.currentElectionId = election.getId();

            if (this.electionTitleLabel != null) {
                this.electionTitleLabel.setText(election.getTitle());
            }
            if (this.electionDescriptionLabel != null) {
                this.electionDescriptionLabel.setText(election.getDescription());
            }

            // Pozivamo geter getOptions() umjesto aksesor metode options()
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

        // Tačan ID opcije iz baze (koji smo stavili u RadioButton.setUserData)
        Integer selectedOptionId = (Integer) selectedToggle.getUserData();

        try {
            // 1. Ovdje pozivaš tvoj klijentski API poziv koji šalje zahtjev na backend VotingService
            // npr: apiClient.castVote(loggedUserId, currentElectionId, selectedOptionId, signature);

            statusLabel.setText("Glas uspješno zabilježen!");

            // 2. Obavijesti Dashboard controller da osvježi prikaz
            if (onVoteSubmittedCallback != null) {
                onVoteSubmittedCallback.run();
            }

            // 3. Zatvori prozor
            closeWindow();

        } catch (Exception e) {
            statusLabel.setText("Greška prilikom glasanja: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    // Pomoćna metoda za zatvaranje Stage-a (dijaloga)
    private void closeWindow() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }
}