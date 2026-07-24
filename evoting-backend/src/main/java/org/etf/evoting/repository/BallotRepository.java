package org.etf.evoting.repository;

import org.etf.evoting.model.Ballot;
import org.etf.evoting.model.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BallotRepository extends JpaRepository<Ballot, Integer> {

  // Povlačenje svih listića za određene izbore radi brojanja/verifikacije
  List<Ballot> findByElection(Election election);

  // Brojanje glasova za konkretnu opciju (kandidata) unutar izbora
  long countByElectionOptionId(Integer optionId);
}
