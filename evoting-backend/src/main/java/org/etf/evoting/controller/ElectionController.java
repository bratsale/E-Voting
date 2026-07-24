package org.etf.evoting.controller;

import org.etf.evoting.model.Election;
import org.etf.evoting.model.User;
import org.etf.evoting.service.ElectionService;
import org.etf.evoting.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {

  private final ElectionService electionService;
  private final UserService userService;

  public ElectionController(ElectionService electionService, UserService userService) {
    this.electionService = electionService;
    this.userService = userService;
  }

  /**
   * DTO za kreiranje izbora.
   */
  public static class CreateElectionRequest {
    public String title;
    public String description;
    public String startDate; // Promijenjeno u String!
    public String endDate;   // Promijenjeno u String!
    public Integer organizerId;
    public List<String> options;
  }

  /**
   * Endpoint za kreiranje novih izbora (Samo za ORGANIZER ulogu).
   */
  @PostMapping("/create")
  public ResponseEntity<?> createElection(@RequestBody CreateElectionRequest request) {
    try {
      // Pronađi organizatora u bazi
      User organizer = userService.getUserById(request.organizerId)
              .orElseThrow(() -> new IllegalArgumentException("Organizator sa navedenim ID-jem ne postoji."));

      // Parsiranje datuma iz String-a
      LocalDateTime start = LocalDateTime.parse(request.startDate);
      LocalDateTime end = LocalDateTime.parse(request.endDate);

      Election created = electionService.createElection(
              request.title,
              request.description,
              start,
              end,
              organizer,
              request.options);

      return ResponseEntity.ok("Izbori '" + created.getTitle() + "' uspješno kreirani sa ID-jem: " + created.getId());
    } catch (Exception e) {
      e.printStackTrace(); // Ispisaće nam tačan stack trace u konzoli ako išta drugo pukne!
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Endpoint koji vraća sve aktivne izbore (Dostupno svim ulogama).
   */
  @GetMapping("/active")
  public ResponseEntity<List<Election>> getActiveElections() {
    List<Election> active = electionService.getActiveElections();
    return ResponseEntity.ok(active);
  }



  /**
   * Endpoint za završavanje izbora (Iz ACTIVE u FINISHED).
   */
  @PostMapping("/{id}/finish")
  public ResponseEntity<?> finishElection(@PathVariable Integer id) {
    try {
      electionService.finishElection(id);
      return ResponseEntity.ok("Izbori sa ID-jem " + id + " su zvanično završeni.");
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Endpoint za pregled rezultata izbora.
   */
  @GetMapping("/{id}/results")
  public ResponseEntity<?> getResults(@PathVariable Integer id) {
    try {
      org.etf.evoting.model.ElectionResultDTO results = electionService.getElectionResults(id);
      return ResponseEntity.ok(results);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
