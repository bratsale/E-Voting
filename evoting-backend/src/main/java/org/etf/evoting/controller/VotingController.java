package org.etf.evoting.controller;

import org.etf.evoting.model.ElectionResultDTO;
import org.etf.evoting.service.CryptoService;
import org.etf.evoting.service.VotingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.security.PrivateKey;
import java.util.Map;

@RestController
@RequestMapping("/api/voting")
public class VotingController {

  private final VotingService votingService;
  private final CryptoService cryptoService;

  public VotingController(VotingService votingService, CryptoService cryptoService) {
    this.votingService = votingService;
    this.cryptoService = cryptoService;
  }

  // DTO klase za zahteve
  public static class CastVoteRequest {
    public Integer electionId;
    public Integer optionId;
    public Integer userId;
    public String voterSignatureBase64;
  }

  public static class TallyRequest {
    public String privateKeyPem; // Privatni ključ organizatora iz njegovog .p12 kontejnera
  }

  /**
   * 1. Slanje glasa
   */
  @PostMapping("/cast")
  public ResponseEntity<?> castVote(@RequestBody CastVoteRequest request) {
    try {
      String receiptCode = votingService.castVote(
              request.electionId,
              request.optionId,
              request.userId,
              request.voterSignatureBase64
      );
      return ResponseEntity.ok(Map.of(
              "message", "Glas je uspješno enkriptovan i zabilježen!",
              "receiptCode", receiptCode
      ));
    } catch (IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("message", "Greška pri glasanju: " + e.getMessage()));
    }
  }

  /**
   * 2. Provera da li je korisnik već glasao
   */
  @GetMapping("/has-voted")
  public ResponseEntity<?> hasVoted(
          @RequestParam("userId") Integer userId,
          @RequestParam("electionId") Integer electionId) {
    boolean voted = votingService.hasUserVoted(userId, electionId);
    return ResponseEntity.ok(Map.of("hasVoted", voted));
  }

  /**
   * 3. Verifikacija glasa od strane glasača
   */
  @GetMapping("/verify/{receiptCode}")
  public ResponseEntity<?> verifyVote(@PathVariable("receiptCode") String receiptCode) {
    try {
      boolean isValid = votingService.verifyVoteByReceiptCode(receiptCode);
      if (isValid) {
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Vaš glas je ispravno zabilježen u bazi i HMAC metapodataka je potvrdio neizmenjenost."
        ));
      } else {
        return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Glas sa datim kodom nije pronađen ili je integritet metapodataka narušen!"
        ));
      }
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("message", "Greška pri verifikaciji: " + e.getMessage()));
    }
  }

  /**
   * 4. Pokretanje brojanja glasova od strane ORGANIZER-a
   * Organizator šalje svoj privatni ključ (u PEM formatu) u body-ju zahtjeva.
   */
  @PostMapping("/tally/{electionId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<?> tallyVotes(
          @PathVariable("electionId") Integer electionId,
          @RequestBody TallyRequest request) {
    try {
      // Konvertujemo PEM string u PrivateKey objekat
      PrivateKey organizerPrivateKey = cryptoService.convertPemToPrivateKey(request.privateKeyPem);

      // Dešifrujemo glasove, sabiramo i generišemo digitalno potpisan izvještaj
      ElectionResultDTO result = votingService.tallyVotesAndGenerateReport(electionId, organizerPrivateKey);

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
              .body(Map.of("message", "Greška pri brojanju glasova: " + e.getMessage()));
    }
  }

  /**
   * 5. Dohvatanje već izračunatih rezultata za bilo kog prijavljenog korisnika
   */
  @PostMapping("/results/{electionId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<?> getResults(
          @PathVariable("electionId") Integer electionId,
          @RequestBody TallyRequest request) {
    try {
      PrivateKey organizerPrivateKey = cryptoService.convertPemToPrivateKey(request.privateKeyPem);
      ElectionResultDTO result = votingService.tallyVotesAndGenerateReport(electionId, organizerPrivateKey);
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
              .body(Map.of("message", "Greška pri dohvatanju rezultata: " + e.getMessage()));
    }
  }
}