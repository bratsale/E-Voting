package org.etf.evoting.repository;

import org.etf.evoting.model.Election;
import org.etf.evoting.model.ElectionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ElectionOptionRepository extends JpaRepository<ElectionOption, Integer> {

  // Izvlačenje svih opcija za specifične izbore
  List<ElectionOption> findByElection(Election election);

  // Alternativna metoda preko ID-ja izbora, ako nam zatreba
  List<ElectionOption> findByElectionId(Integer electionId);
}
