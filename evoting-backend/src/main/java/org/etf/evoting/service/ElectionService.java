package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.BallotRepository;
import org.etf.evoting.repository.ElectionOptionRepository;
import org.etf.evoting.repository.ElectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElectionService {

  private final ElectionRepository electionRepository;
  private final ElectionOptionRepository optionRepository;
  private final BallotRepository ballotRepository;

  public ElectionService(ElectionRepository electionRepository,
                         ElectionOptionRepository optionRepository,
                         BallotRepository ballotRepository) {
    this.electionRepository = electionRepository;
    this.optionRepository = optionRepository;
    this.ballotRepository = ballotRepository;
  }

  @Transactional
  public Election createElection(String title, String description, LocalDateTime startDate,
                                 LocalDateTime endDate, User organizer, List<String> options) {

    Election election = new Election();
    election.setTitle(title);
    election.setDescription(description);
    election.setStartDate(startDate);
    election.setEndDate(endDate);
    election.setOrganizer(organizer);
    election.setStatus(ElectionStatus.ACTIVE);

    // 1. Prvo sačuvamo izbor da dobije svoj ID
    Election savedElection = electionRepository.save(election);

    // 2. Prolazimo kroz sve opcije iz liste, povezujemo ih sa izborom i spašavamo u bazu
    if (options != null && !options.isEmpty()) {
      for (String optionText : options) {
        ElectionOption option = new ElectionOption(savedElection, optionText);
        optionRepository.save(option);
      }
    }

    return savedElection;
  }
  @Transactional
  public void activateElection(Integer electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    if (election.getStatus() != ElectionStatus.CREATED) {
      throw new IllegalStateException("Mogu se aktivirati samo izbori koji su u statusu CREATED.");
    }

    election.setStatus(ElectionStatus.ACTIVE);
    electionRepository.save(election);
  }

  @Transactional
  public void finishElection(Integer electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    if (election.getStatus() != ElectionStatus.ACTIVE) {
      throw new IllegalStateException("Mogu se završiti samo izbori koji su trenutno ACTIVE.");
    }

    election.setStatus(ElectionStatus.FINISHED);
    electionRepository.save(election);
  }

  public List<ElectionDTO> getActiveElections() {
    // 1. Dohvatamo entitete iz baze
    List<Election> elections = electionRepository.findByStatus(ElectionStatus.ACTIVE);
    // Napomena: prilagodi naziv metode svom ElectionRepository-ju

    // 2. Mapiramo listu entiteta u listu DTO objekata
    return elections.stream()
            .map(this::mapToDTO)
            .toList();
  }
  /**
   * PREBROJAVANJE GLASOVA:
   * Računa ukupan broj glasova i raspodjelu po kandidatima/opcijama za date izbore.
   */
  @Transactional(readOnly = true)
  public ElectionResultDTO getElectionResults(Integer electionId) {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    // Preuzmi sve izborne opcije za ove izbore
    List<ElectionOption> options = optionRepository.findByElection(election);
    List<Ballot> ballots = ballotRepository.findByElection(election);

    Map<String, Long> voteCounts = new HashMap<>();

    // Inicijalizuj sve opcije na 0 glasova
    for (ElectionOption option : options) {
      voteCounts.put(option.getOptionText(), 0L);
    }

    // Prebroj pristigle glasačke listiće
    for (Ballot ballot : ballots) {
      String optionText = ballot.getElectionOption().getOptionText();
      voteCounts.put(optionText, voteCounts.getOrDefault(optionText, 0L) + 1);
    }

    return new ElectionResultDTO(
            election.getId(),
            election.getTitle(),
            ballots.size(),
            voteCounts
    );
  }

  private ElectionDTO mapToDTO(Election election) {
    // 1. Dovuci opcije iz baze i mapiraj u ElectionOptionDTO
    List<ElectionOptionDTO> optionDTOs = optionRepository.findByElection(election)
            .stream()
            .map(opt -> new ElectionOptionDTO(opt.getId(), opt.getOptionText(), election.getId()))
            .toList();

    // 2. Vrati novu instancu record-a sa opcijama
    return new ElectionDTO(
            election.getId(),
            election.getTitle(),
            election.getDescription(),
            election.getStatus() != null ? election.getStatus().name() : null,
            election.getStartDate(),
            election.getEndDate(),
            election.getOrganizer() != null ? election.getOrganizer().getId() : null,
            election.getOrganizer() != null ? election.getOrganizer().getUsername() : null,
            optionDTOs // Proslijeđena lista opcija
    );
  }
}