package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VotingService {

  private final ElectionRepository electionRepository;
  private final ElectionOptionRepository optionRepository;
  private final VotingRegistryRepository registryRepository;
  private final BallotRepository ballotRepository;

  public VotingService(ElectionRepository electionRepository,
      ElectionOptionRepository optionRepository,
      VotingRegistryRepository registryRepository,
      BallotRepository ballotRepository) {
    this.electionRepository = electionRepository;
    this.optionRepository = optionRepository;
    this.registryRepository = registryRepository;
    this.ballotRepository = ballotRepository;
  }

  /**
   * Izvršavanje sigurnog i anonimnog glasanja unutar jedne transakcije.
   */
  @Transactional
  public void castVote(Integer userId, Integer electionId, Integer optionId, String digitalSignature) {

    // 1. Dobavi izbore i provjeri da li postoje i da li su aktivni
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new IllegalArgumentException("Izbori sa ID-jem " + electionId + " ne postoje."));

    if (election.getStatus() != ElectionStatus.ACTIVE) {
      throw new IllegalStateException("Glasanje nije dozvoljeno jer izbori nisu aktivni.");
    }

    LocalDateTime sada = LocalDateTime.now();
    if (sada.isBefore(election.getStartDate()) || sada.isAfter(election.getEndDate())) {
      throw new IllegalStateException("Izbori su van definisanog vremenskog roka.");
    }

    // 2. Provjeri da li je korisnik već glasao (Zaštita od duplog glasanja)
    boolean vecGlasao = registryRepository.existsByUserIdAndElectionId(userId, electionId);
    if (vecGlasao) {
      throw new IllegalStateException("Korisnik je već iskoristio pravo glasa na ovim izborima.");
    }

    // 3. Provjeri da li opcija pripada tim izborima
    ElectionOption option = optionRepository.findById(optionId)
        .orElseThrow(() -> new IllegalArgumentException("Izabrana opcija ne postoji."));

    if (!option.getElection().getId().equals(electionId)) {
      throw new IllegalArgumentException("Izabrana opcija ne pripada ovim izborima.");
    }

    // 4. Kreiraj lažni "User" objekat samo sa ID-jem za potrebe kreiranja registra
    // (Izbjegavamo puni SELECT korisnika ako nije potreban, radi performansi)
    User voter = new User();
    voter.setId(userId);

    // 5. Zabilježi u registar da je korisnik glasao
    VotingRegistry registryEntry = new VotingRegistry(voter, election);
    registryRepository.save(registryEntry);

    // 6. Ubaci potpuno anonimni glasački listić (Nema reference ka korisniku!)
    Ballot ballot = new Ballot(election, option, digitalSignature);
    ballotRepository.save(ballot);
  }

  /**
   * Pomoćna metoda za dobavljanje rezultata izbora nakon što se završe.
   */
  public List<Ballot> getBallotsForElection(Integer electionId) {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));
    return ballotRepository.findByElection(election);
  }
}
