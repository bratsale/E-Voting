package org.etf.evoting.controller;

import org.etf.evoting.service.VotingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vote")
public class VotingController {

  private final VotingService votingService;

  public VotingController(VotingService votingService) {
    this.votingService = votingService;
  }

  /**
   * DTO za slanje glasačkog listića sa digitalnim potpisom.
   */
  public static class VoteRequest {
    public Integer userId;
    public Integer electionId;
    public Integer optionId;
    public String digitalSignature; // Base64 string potpisan na klijentskoj strani
  }

  /**
   * Endpoint za sigurno i anonimno glasanje.
   */
  @PostMapping
  public ResponseEntity<?> castVote(@RequestBody VoteRequest request) {
    try {
      votingService.castVote(
          request.userId,
          request.electionId,
          request.optionId,
          request.digitalSignature);

      // Namjerno vraćamo generičku poruku bez detalja o samom listiću
      // radi očuvanja potpune anonimnosti glasa.
      return ResponseEntity.ok("Glas je uspješno zabilježen i verifikovan!");

    } catch (IllegalArgumentException | IllegalStateException e) {
      // Standardne validacione greške (npr. isteklo vrijeme, nepostojeća opcija)
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (SecurityException e) {
      // Kriptografska greška ukoliko digitalni potpis padne na verifikaciji
      return ResponseEntity.status(403).body(e.getMessage());
    } catch (Exception e) {
      // Bilo kakav neočekivani sistemski problem
      return ResponseEntity.internalServerError().body("Došlo je do greške na serveru: " + e.getMessage());
    }
  }
}
