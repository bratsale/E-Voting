package org.etf.evoting.service;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VotingService {

  private final BallotRepository ballotRepository;
  private final VotingRegistryRepository votingRegistryRepository;
  private final ElectionRepository electionRepository;
  private final UserRepository userRepository;
  private final VoteMetadataRepository voteMetadataRepository;
  private final CryptoService cryptoService;

  public VotingService(BallotRepository ballotRepository,
                       VotingRegistryRepository votingRegistryRepository,
                       ElectionRepository electionRepository,
                       UserRepository userRepository,
                       VoteMetadataRepository voteMetadataRepository,
                       CryptoService cryptoService) {
    this.ballotRepository = ballotRepository;
    this.votingRegistryRepository = votingRegistryRepository;
    this.electionRepository = electionRepository;
    this.userRepository = userRepository;
    this.voteMetadataRepository = voteMetadataRepository;
    this.cryptoService = cryptoService;
  }

  /**
   * 1. Slanje i enkripcija glasa
   */
  @Transactional
  public String castVote(Integer electionId, Integer optionId, Integer userId, String voterSignatureBase64) throws Exception {

    // Provjera dvostrukog glasanja preko VotingRegistry
    if (votingRegistryRepository.existsByUserIdAndElectionId(userId, electionId)) {
      throw new IllegalStateException("Već ste glasali na ovim izborima!");
    }

    User voter = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Korisnik ne postoji."));

    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    // Evidentiramo da je korisnik glasao (bez čuvanja opcije)
    VotingRegistry registry = new VotingRegistry(voter, election);
    votingRegistryRepository.save(registry);

    // Preuzimanje javnog ključa Organizatora
    User organizer = election.getOrganizer();
    if (organizer == null || organizer.getCertificatePem() == null) {
      throw new IllegalStateException("Organizator nema važeći sertifikat za ove izbore.");
    }
    X509Certificate organizerCert = cryptoService.convertPemToCertificate(organizer.getCertificatePem());
    PublicKey organizerPublicKey = organizerCert.getPublicKey();

    // Generisanje simetričnog AES-256 ključa i IV-a
    SecretKey aesKey = cryptoService.generateAESKey();
    byte[] iv = new byte[12]; // GCM IV
    new SecureRandom().nextBytes(iv);

    // Enkripcija opcije (optionId) AES ključem
    byte[] encryptedVoteBytes = cryptoService.encryptVoteWithAES(optionId.toString(), aesKey, iv);

    // Enkripcija AES ključa RSA javnim ključem organizatora
    byte[] encryptedAesKeyBytes = cryptoService.encryptAESKeyWithOrganizerPublicKey(aesKey, organizerPublicKey);

    // Generisanje jedinstvenog koda potvrde (Receipt Code)
    String receiptCode = UUID.randomUUID().toString();

    // Pakovanje u Ballot
    Ballot ballot = new Ballot();
    ballot.setElection(election);
    ballot.setEncryptedVote(Base64.getEncoder().encodeToString(encryptedVoteBytes));
    ballot.setEncryptedSymKey(Base64.getEncoder().encodeToString(encryptedAesKeyBytes));
    ballot.setIvBase64(Base64.getEncoder().encodeToString(iv));
    ballot.setDigitalSignature(voterSignatureBase64);
    ballot.setReceiptCode(receiptCode);

    Ballot savedBallot = ballotRepository.save(ballot);

    // Odvojeno čuvanje metapodataka sa HMAC-om
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
   * 2. Brojanje glasova od strane Organizatora dekripcijom njegovim privatnim ključem
   */
  public Map<Integer, Long> tallyVotes(Integer electionId, PrivateKey organizerPrivateKey) throws Exception {
    Election election = electionRepository.findById(electionId)
            .orElseThrow(() -> new IllegalArgumentException("Izbori ne postoje."));

    List<Ballot> ballots = ballotRepository.findByElection(election);
    Map<Integer, Long> results = new HashMap<>();

    for (Ballot ballot : ballots) {
      byte[] encryptedAesKey = Base64.getDecoder().decode(ballot.getEncryptedSymKey());
      byte[] encryptedVote = Base64.getDecoder().decode(ballot.getEncryptedVote());
      byte[] iv = Base64.getDecoder().decode(ballot.getIvBase64());

      // Dešifrovanje AES ključa pomoću privatnog ključa Organizatora
      SecretKey aesKey = cryptoService.decryptAESKeyWithOrganizerPrivateKey(encryptedAesKey, organizerPrivateKey);

      // Dešifrovanje samog glasa (ID opcije)
      String optionIdStr = cryptoService.decryptVoteWithAES(encryptedVote, aesKey, iv);
      Integer optionId = Integer.parseInt(optionIdStr);

      results.put(optionId, results.getOrDefault(optionId, 0L) + 1);
    }

    return results;
  }

  /**
   * 3. Verifikacija glasa od strane glasača preko receiptCode-a i HMAC metapodataka
   */
  public boolean verifyVoteByReceiptCode(String receiptCode) throws Exception {
    Optional<Ballot> ballotOpt = ballotRepository.findAll().stream()
            .filter(b -> receiptCode.equals(b.getReceiptCode()))
            .findFirst();

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