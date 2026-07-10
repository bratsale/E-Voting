package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.ElectionOptionRepository;
import org.etf.evoting.repository.ElectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ElectionService {

  private final ElectionRepository electionRepository;
  private final ElectionOptionRepository optionRepository;

  public ElectionService(ElectionRepository electionRepository, ElectionOptionRepository optionRepository) {
    this.electionRepository = electionRepository;
    this.optionRepository = optionRepository;
  }

  /**
   * Kreiranje novih izbora zajedno sa početnim opcijama (kandidatima) unutar
   * jedne transakcije.
   */
  @Transactional
  public Election createElection(String title, String description, LocalDateTime startDate,
      LocalDateTime endDate, User organizer, List<String> optionTexts) {

    if (startDate.isAfter(endDate)) {
      throw new IllegalArgumentException("Početak izbora ne može biti nakon završetka.");
    }
    if (optionTexts == null || optionTexts.size() < 2) {
      throw new IllegalArgumentException("Izbori moraju imati najmanje dvije opcije.");
    }

    // 1. Spasi izbore (Podrazumijevani status je CREATED)
    Election election = new Election(title, description, startDate, endDate, organizer);
    Election savedElection = electionRepository.save(election);

    // 2. Mapiraj i spasi sve ponuđene opcije
    for (String text : optionTexts) {
      ElectionOption option = new ElectionOption(savedElection, text);
      optionRepository.save(option);
    }

    return savedElection;
  }

  /**
   * Ručno ili automatsko aktiviranje izbora (Prebacivanje u ACTIVE).
   */
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

  /**
   * Završavanje izbora (Prebacivanje u FINISHED) - nakon ovoga se broje glasovi.
   */
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

  /**
   * Dobavljanje svih trenutno aktivnih izbora za glasače.
   */
  public List<Election> getActiveElections() {
    return electionRepository.findByStatus(ElectionStatus.ACTIVE);
  }
}
