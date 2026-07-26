package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VotingService {

  private static final Logger logger = LoggerFactory.getLogger(VotingService.class);

  private final BallotRepository ballotRepository;
  private final VotingRegistryRepository votingRegistryRepository;
  private final ElectionRepository electionRepository;
  private final UserRepository userRepository;
  private final VoteMetadataRepository voteMetadataRepository;
  private final ElectionOptionRepository electionOptionRepository;
  private final CryptoService cryptoService;

  public VotingService(BallotRepository ballotRepository,
                       VotingRegistryRepository votingRegistryRepository,
                       ElectionRepository electionRepository,
                       UserRepository userRepository,
                       VoteMetadataRepository voteMetadataRepository,
                       ElectionOptionRepository electionOptionRepository,
                       CryptoService cryptoService) {
    this.ballotRepository = ballotRepository;
    this.votingRegistryRepository = votingRegistryRepository;
    this.electionRepository = electionRepository;
    this.userRepository = userRepository;
    this.voteMetadataRepository = voteMetadataRepository;
    this.electionOptionRepository = electionOptionRepository;
    this.cryptoService = cryptoService;
  }

  /**
   * Pomoćna metoda za izvlačenje PublicKey objekta iz Election ili Fallback na Organizatora
   */
  private PublicKey getElectionPublicKey(Election election) throws Exception {
    String certPem = election.getCertificatePem();

    // Fallback na organizatora za starije unose ako sertifikat izbora nije postavljen
    if (certPem == null || certPem.trim().isEmpty()) {
      User organizer = election.getOrganizer();
      if (organizer != null && organizer.getCertificatePem() != null) {
        certPem = organizer.getCertificatePem();
      }
    }

    if (certPem == null || certPem.trim().isEmpty()) {
      throw new IllegalStateException("Nije pronađen važeći sertifikat/javni ključ za ove izbore.");
    }

    // Ako je u pitanju puni X509 sertifikat
    if (certPem.contains("-----BEGIN CERTIFICATE-----")) {
      X509Certificate cert = cryptoService.convertPemToCertificate(certPem);
      return cert.getPublicKey();
    }
    // Ako je u pitanju RSA Public Key poslat sa klijenta
    else {
      return cryptoService.convertPemToPublicKey(certPem);
    }
  }

  /**
   * 1. Slanje i enkripcija glasa
   */
  @Transactional
  public String castVote(Integer electionId, Integer optionId, Integer userId, String voterSignatureBase64) throws Exception {

    if (votingRegistryRepository.existsByUserIdAndElectionId(userId, electionId)) {
      throw new IllegalStateException("Već ste glasali na ovim izborima!");
    }

    User voter = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Korisnik ne postoji."));

    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    VotingRegistry registry = new VotingRegistry(voter, election);
    votingRegistryRepository.save(registry);

    // Izvlačimo javni ključ izbora (umjesto fiksno iz organizatora)
    PublicKey electionPublicKey = getElectionPublicKey(election);

    SecretKey aesKey = cryptoService.generateAESKey();
    byte[] iv = new byte[12]; // GCM IV
    new SecureRandom().nextBytes(iv);

    byte[] encryptedVoteBytes = cryptoService.encryptVoteWithAES(optionId.toString(), aesKey, iv);
    byte[] encryptedAesKeyBytes = cryptoService.encryptAESKeyWithOrganizerPublicKey(aesKey, electionPublicKey);

    String receiptCode = UUID.randomUUID().toString();

    Ballot ballot = new Ballot();
    ballot.setElection(election);
    ballot.setEncryptedVote(Base64.getEncoder().encodeToString(encryptedVoteBytes));
    ballot.setEncryptedSymKey(Base64.getEncoder().encodeToString(encryptedAesKeyBytes));
    ballot.setIvBase64(Base64.getEncoder().encodeToString(iv));
    ballot.setDigitalSignature(voterSignatureBase64);
    ballot.setReceiptCode(receiptCode);

    Ballot savedBallot = ballotRepository.save(ballot);

    LocalDateTime now = LocalDateTime.now();
    String metadataRaw = savedBallot.getId() + ":" + electionId + ":" + now.toString();
    String hmac = cryptoService.calculateMetadataHMAC(metadataRaw);

    VoteMetadata metadata = new VoteMetadata();
    metadata.setVoteId(savedBallot.getId());
    metadata.setElectionId(electionId);
    metadata.setTimestamp(now);
    metadata.setHmac(hmac);

    voteMetadataRepository.save(metadata);

    return receiptCode;
  }

  /**
   * 2. Brojanje glasova, dešifrovanje, generisanje i potpisivanje izvještaja
   */
  @Transactional
  public ElectionResultDTO tallyVotesAndGenerateReport(Integer electionId, PrivateKey organizerPrivateKey) throws Exception {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    PublicKey electionPublicKey = getElectionPublicKey(election);

    // 0. VERIFIKACIJA: Provjera da li proslijeđeni privatni ključ odgovara javnom ključu izbora u bazi
    try {
      Signature checkSig = Signature.getInstance("SHA256withRSA", "BC");
      checkSig.initSign(organizerPrivateKey);
      checkSig.update("test-mismatch-check".getBytes(StandardCharsets.UTF_8));
      byte[] testSignature = checkSig.sign();

      Signature verifySig = Signature.getInstance("SHA256withRSA", "BC");
      verifySig.initVerify(electionPublicKey);
      verifySig.update("test-mismatch-check".getBytes(StandardCharsets.UTF_8));

      if (!verifySig.verify(testSignature)) {
        throw new SecurityException("Proslijeđeni privatni ključ NE ODGOVARA javnom ključu registrovanom na ovim izborima!");
      }
    } catch (Exception e) {
      logger.error("Neuspješna verifikacija para RSA ključeva za izbore: {}", e.getMessage());
      throw new SecurityException("Neodgovarajući ili nevažeći privatni ključ: " + e.getMessage(), e);
    }

    List<Ballot> ballots = ballotRepository.findByElection(election);
    List<ElectionOption> options = electionOptionRepository.findByElectionId(electionId);

    // Inicijalizujemo brojače za sve opcije na 0
    Map<Integer, Long> rawCounts = new HashMap<>();
    Map<Integer, String> optionNames = new HashMap<>();

    for (ElectionOption opt : options) {
      rawCounts.put(opt.getId(), 0L);
      optionNames.put(opt.getId(), opt.getOptionText());
    }

    logger.info("Započinjem dešifrovanje {} glasačkih listića za izbor ID: {}", ballots.size(), electionId);

    // 1. Dešifrovanje svakog glasačkog listića
    for (Ballot ballot : ballots) {
      try {
        byte[] encryptedAesKey = Base64.getDecoder().decode(ballot.getEncryptedSymKey());
        byte[] encryptedVote = Base64.getDecoder().decode(ballot.getEncryptedVote());
        byte[] iv = Base64.getDecoder().decode(ballot.getIvBase64());

        logger.info("Ballot ID: {} | B64 SymKey Len: {} | Decoded Key Bytes Len: {}",
                ballot.getId(),
                ballot.getEncryptedSymKey() != null ? ballot.getEncryptedSymKey().length() : 0,
                encryptedAesKey.length);

        // Dešifruj AES ključ pomoću privatnog ključa
        SecretKey aesKey = cryptoService.decryptAESKeyWithOrganizerPrivateKey(encryptedAesKey, organizerPrivateKey);

        // Dešifruj glas (ID opcije)
        String optionIdStr = cryptoService.decryptVoteWithAES(encryptedVote, aesKey, iv);
        Integer optionId = Integer.parseInt(optionIdStr);

        rawCounts.put(optionId, rawCounts.getOrDefault(optionId, 0L) + 1);

      } catch (Exception e) {
        logger.error("GREŠKA PRI DEŠIFROVANJU GLASAČKOG LISTIĆA ID: {} (Receipt: {})", ballot.getId(), ballot.getReceiptCode(), e);
        throw new IllegalStateException("Greška pri dešifrovanju glasa ID " + ballot.getId() + ": " + e.getMessage(), e);
      }
    }

    // Mapiramo ID-jeve u tekstualne nazive opcija
    Map<String, Long> voteCounts = new HashMap<>();
    rawCounts.forEach((optId, count) -> {
      String name = optionNames.getOrDefault(optId, "Opcija (" + optId + ")");
      voteCounts.put(name, count);
    });

    // 2. Generisanje tekstualnog izvještaja
    StringBuilder reportBuilder = new StringBuilder();
    reportBuilder.append("=== ZAVRŠNI IZVJEŠTAJ GLASANJA ===\n");
    reportBuilder.append("ID Izbora: ").append(election.getId()).append("\n");
    reportBuilder.append("Naziv: ").append(election.getTitle()).append("\n");
    reportBuilder.append("Ukupno glasova: ").append(ballots.size()).append("\n");
    reportBuilder.append("Rezultati po opcijama:\n");
    voteCounts.forEach((optName, count) ->
            reportBuilder.append(" - ").append(optName).append(": ").append(count).append(" glas(a)\n")
    );
    reportBuilder.append("Datum i vrijeme brojanja: ").append(LocalDateTime.now()).append("\n");

    String reportContent = reportBuilder.toString();

    // 3. Digitalno potpisivanje izvještaja (SHA256withRSA)
    Signature signature = Signature.getInstance("SHA256withRSA", "BC");
    signature.initSign(organizerPrivateKey);
    signature.update(reportContent.getBytes(StandardCharsets.UTF_8));
    String reportSignatureBase64 = Base64.getEncoder().encodeToString(signature.sign());

    return new ElectionResultDTO(
            election.getId(),
            election.getTitle(),
            ballots.size(),
            voteCounts,
            reportContent,
            reportSignatureBase64
    );
  }

  /**
   * 3. Verifikacija glasa od strane glasača preko receiptCode-a i HMAC metapodataka
   */
  public boolean verifyVoteByReceiptCode(String receiptCode) throws Exception {
    Optional<Ballot> ballotOpt = ballotRepository.findByReceiptCode(receiptCode);

    if (ballotOpt.isEmpty()) {
      return false;
    }

    Ballot ballot = ballotOpt.get();
    Optional<VoteMetadata> metadataOpt = voteMetadataRepository.findByVoteId(ballot.getId());

    if (metadataOpt.isEmpty()) {
      return false;
    }

    VoteMetadata metadata = metadataOpt.get();
    String expectedRaw = ballot.getId() + ":" + ballot.getElection().getId() + ":" + metadata.getTimestamp().toString();
    String calculatedHmac = cryptoService.calculateMetadataHMAC(expectedRaw);

    return calculatedHmac.equals(metadata.getHmac());
  }

  /**
   * Provjera da li je glasač već glasao
   */
  public boolean hasUserVoted(Integer userId, Integer electionId) {
    return votingRegistryRepository.existsByUserIdAndElectionId(userId, electionId);
  }
}