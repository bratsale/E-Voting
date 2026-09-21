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
import java.time.format.DateTimeFormatter;

@Service
public class VotingService {

  private static final Logger logger = LoggerFactory.getLogger(VotingService.class);

  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

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

  private PublicKey getElectionPublicKey(Election election) throws Exception {
    String certPem = election.getCertificatePem();

    if (certPem == null || certPem.trim().isEmpty()) {
      User organizer = election.getOrganizer();
      if (organizer != null && organizer.getCertificatePem() != null) {
        certPem = organizer.getCertificatePem();
      }
    }

    if (certPem == null || certPem.trim().isEmpty()) {
      throw new IllegalStateException("Nije pronađen važeći sertifikat/javni ključ za ove izbore.");
    }

    if (certPem.contains("-----BEGIN CERTIFICATE-----")) {
      X509Certificate cert = cryptoService.convertPemToCertificate(certPem);
      return cert.getPublicKey();
    }
    else {
      return cryptoService.convertPemToPublicKey(certPem);
    }
  }

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
    String formattedTimestamp = now.format(TIMESTAMP_FORMATTER);
    String metadataRaw = savedBallot.getId() + ":" + electionId + ":" + formattedTimestamp;
    String hmac = cryptoService.calculateMetadataHMAC(metadataRaw);

    VoteMetadata metadata = new VoteMetadata();
    metadata.setVoteId(savedBallot.getId());
    metadata.setElectionId(electionId);
    metadata.setTimestamp(now);
    metadata.setHmac(hmac);

    voteMetadataRepository.save(metadata);

    return receiptCode;
  }

  @Transactional
  public ElectionResultDTO tallyVotesAndGenerateReport(Integer electionId, PrivateKey organizerPrivateKey) throws Exception {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    PublicKey electionPublicKey = getElectionPublicKey(election);

    // 1. Verifikacija para ključeva (Privatni ključ mora odgovarati javnom ključu izbora)
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

    // 2. Preuzimanje glasačkih listića i opcija
    List<Ballot> ballots = ballotRepository.findByElection(election);
    List<ElectionOption> options = electionOptionRepository.findByElectionId(electionId);

    Map<Integer, Long> rawCounts = new HashMap<>();
    Map<Integer, String> optionNames = new HashMap<>();

    for (ElectionOption opt : options) {
      rawCounts.put(opt.getId(), 0L);
      optionNames.put(opt.getId(), opt.getOptionText());
    }

    logger.info("Započinjem dešifrovanje {} glasačkih listića za izbor ID: {}", ballots.size(), electionId);

    // 3. Dešifrovanje glasova
    for (Ballot ballot : ballots) {
      try {
        byte[] encryptedAesKey = Base64.getDecoder().decode(ballot.getEncryptedSymKey());
        byte[] encryptedVote = Base64.getDecoder().decode(ballot.getEncryptedVote());
        byte[] iv = Base64.getDecoder().decode(ballot.getIvBase64());

        SecretKey aesKey = cryptoService.decryptAESKeyWithOrganizerPrivateKey(encryptedAesKey, organizerPrivateKey);
        String optionIdStr = cryptoService.decryptVoteWithAES(encryptedVote, aesKey, iv);
        Integer optionId = Integer.parseInt(optionIdStr);

        rawCounts.put(optionId, rawCounts.getOrDefault(optionId, 0L) + 1);

      } catch (Exception e) {
        logger.error("GREŠKA PRI DEŠIFROVANJU GLASAČKOG LISTIĆA ID: {} (Receipt: {})", ballot.getId(), ballot.getReceiptCode(), e);
        throw new IllegalStateException("Greška pri dešifrovanju glasa ID " + ballot.getId() + ": " + e.getMessage(), e);
      }
    }

    // Mapiranje rezultata po nazivima opcija
    Map<String, Long> voteCounts = new HashMap<>();
    rawCounts.forEach((optId, count) -> {
      String name = optionNames.getOrDefault(optId, "Opcija (" + optId + ")");
      voteCounts.put(name, count);
    });

    // 4. Generisanje tekstualnog izvještaja
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

    // 5. Potpisivanje izvještaja privatnim ključem organizatora
    Signature signature = Signature.getInstance("SHA256withRSA", "BC");
    signature.initSign(organizerPrivateKey);
    signature.update(reportContent.getBytes(StandardCharsets.UTF_8));
    String reportSignatureBase64 = Base64.getEncoder().encodeToString(signature.sign());

    // 6. Trajno čuvanje izračunatih rezultata i izvještaja u bazi
    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String resultJson = mapper.writeValueAsString(voteCounts);

    election.setResultJson(resultJson);
    election.setReportContent(reportContent);
    election.setReportSignaturePem(reportSignatureBase64);

    // Opciono: prebacivanje statusa izbora u FINISHED ako već nije u tom statusu
    if (election.getStatus() != ElectionStatus.FINISHED) {
      election.setStatus(ElectionStatus.FINISHED);
    }

    electionRepository.save(election);

    // 7. Vraćanje DTO objekta
    return new ElectionResultDTO(
            election.getId(),
            election.getTitle(),
            ballots.size(),
            voteCounts,
            reportContent,
            reportSignatureBase64
    );
  }

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
    String formattedTimestamp = metadata.getTimestamp().format(TIMESTAMP_FORMATTER);
    String expectedRaw = ballot.getId() + ":" + ballot.getElection().getId() + ":" + formattedTimestamp;

    String calculatedHmac = cryptoService.calculateMetadataHMAC(expectedRaw);

    return calculatedHmac.equals(metadata.getHmac());
  }

  public boolean hasUserVoted(Integer userId, Integer electionId) {
    return votingRegistryRepository.existsByUserIdAndElectionId(userId, electionId);
  }
}