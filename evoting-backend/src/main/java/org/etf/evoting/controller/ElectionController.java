package org.etf.evoting.controller;

import org.etf.evoting.model.*;
import org.etf.evoting.repository.ElectionOptionRepository;
import org.etf.evoting.service.ElectionService;
import org.etf.evoting.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/elections")
public class ElectionController {

  private final ElectionService electionService;
  private final UserService userService;
  private final ElectionOptionRepository optionRepository; // Dodata instanca repozitorijuma

  public ElectionController(ElectionService electionService,
                            UserService userService,
                            ElectionOptionRepository optionRepository) {
    this.electionService = electionService;
    this.userService = userService;
    this.optionRepository = optionRepository;
  }

  /**
   * DTO za kreiranje izbora.
   */
  public static class CreateElectionRequest {
    public String title;
    public String description;
    public String startDate;
    public String endDate;
    public Integer organizerId;
    public List<String> options;
  }

  /**
   * Endpoint za kreiranje novih izbora (Samo za ORGANIZER ulogu).
   */
  @PostMapping("/create")
  public ResponseEntity<?> createElection(@RequestBody CreateElectionRequest request) {
    try {
      User organizer = userService.getUserById(request.organizerId)
              .orElseThrow(() -> new IllegalArgumentException("Organizator sa navedenim ID-jem ne postoji."));

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
      e.printStackTrace();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Endpoint koji vraća sve aktivne izbore (Dostupno svim ulogama).
   */
  @GetMapping("/active")
  public ResponseEntity<List<ElectionDTO>> getActiveElections() {
    List<ElectionDTO> activeElections = electionService.getActiveElections();
    return ResponseEntity.ok(activeElections);
  }

  /**
   * Endpoint za završavanje izbora (Iz ACTIVE u FINISHED).
   */
  @PostMapping("/{id}/finish")
  public ResponseEntity<?> finishElection(@PathVariable("id") Integer id) { // Dodato ("id")
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
  public ResponseEntity<?> getResults(@PathVariable("id") Integer id) { // Dodato ("id")
    try {
      org.etf.evoting.model.ElectionResultDTO results = electionService.getElectionResults(id);
      return ResponseEntity.ok(results);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Endpoint za dohvat opcija za glasanje po ID-ju izbora.
   */
  @GetMapping("/{id}/options")
  public ResponseEntity<List<ElectionOptionDTO>> getElectionOptions(@PathVariable("id") Integer id) {
    List<ElectionOption> options = optionRepository.findByElectionId(id);

    List<ElectionOptionDTO> dtos = options.stream()
            .map(opt -> new ElectionOptionDTO(opt.getId(), opt.getOptionText(), opt.getElection().getId()))
            .toList();

    return ResponseEntity.ok(dtos);
  }
}