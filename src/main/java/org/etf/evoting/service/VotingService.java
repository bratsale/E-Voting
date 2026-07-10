package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VotingService {

  private final UserRepository userRepository;
  private final ElectionRepository electionRepository;
  private final ElectionOptionRepository optionRepository;
  private final VotingRegistryRepository registryRepository;
  private final BallotRepository ballotRepository;
  private final CryptoService cryptoService;

  public VotingService(UserRepository userRepository,
      ElectionRepository electionRepository,
      ElectionOptionRepository optionRepository,
      VotingRegistryRepository registryRepository,
      BallotRepository ballotRepository,
      CryptoService cryptoService) {
    this.userRepository = userRepository;
    this.electionRepository = electionRepository;
    this.optionRepository = optionRepository;
    this.registryRepository = registryRepository;
    this.ballotRepository = ballotRepository;
    this.cryptoService = cryptoService;
  }

  /**
   * Izvršavanje sigurnog, kriptografski verifikovanog i anonimnog glasanja.
   */
  @Transactional
  public void castVote(Integer userId, Integer electionId, Integer optionId, String digitalSignature) {

    // 1. Dobavi korisnika i provjeri da li postoji (treba nam njegov sertifikat)
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("Korisnik sa ID-jem " + userId + " ne postoji."));

    // 2. Dobavi izbore i provjeri da li su aktivni
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new IllegalArgumentException("Izbori sa ID-jem " + electionId + " ne postoje."));

    if (election.getStatus() != ElectionStatus.ACTIVE) {
      throw new IllegalStateException("Glasanje nije dozvoljeno jer izbori nisu aktivni.");
    }

    LocalDateTime sada = LocalDateTime.now();
    if (sada.isBefore(election.getStartDate()) || sada.isAfter(election.getEndDate())) {
      throw new IllegalStateException("Izbori su van definisanog vremenskog roka.");
    }

    // 3. Provjeri da li je korisnik već glasao (Zaštita od duplog glasanja)
    boolean vecGlasao = registryRepository.existsByUserIdAndElectionId(userId, electionId);
    if (vecGlasao) {
      throw new IllegalStateException("Korisnik je već iskoristio pravo glasa na ovim izborima.");
    }

    // 4. Provjeri da li opcija pripada tim izborima
    ElectionOption option = optionRepository.findById(optionId)
        .orElseThrow(() -> new IllegalArgumentException("Izabrana opcija ne postoji."));

    if (!option.getElection().getId().equals(electionId)) {
      throw new IllegalArgumentException("Izabrana opcija ne pripada ovim izborima.");
    }

    // 5. KRIPTOGRAFSKA VERIFIKACIJA: Provjeri digitalni potpis glasačkog listića
    // Protokol: Klijent potpisuje string u formatu "electionId:optionId"
    String dataToVerify = electionId + ":" + optionId;
    boolean isSignatureValid = cryptoService.verifySignature(user.getCertificatePem(), dataToVerify, digitalSignature);

    if (!isSignatureValid) {
      throw new SecurityException("Kritična greška: Digitalni potpis glasačkog listića nije validan!");
    }

    // 6. Zabilježi u registar da je korisnik glasao (Sprečavanje duplog glasanja)
    VotingRegistry registryEntry = new VotingRegistry(user, election);
    registryRepository.save(registryEntry);

    // 7. Ubaci potpuno anonimni glasački listić (Nema reference ka korisniku!)
    Ballot ballot = new Ballot(election, option, digitalSignature);
    ballotRepository.save(ballot);
  }

  /**
   * Rezultati izbora za prebrojavanje glasova.
   */
  public List<Ballot> getBallotsForElection(Integer electionId) {
    Election election = electionRepository.findById(electionId)
        .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));
    return ballotRepository.findByElection(election);
  }
}
