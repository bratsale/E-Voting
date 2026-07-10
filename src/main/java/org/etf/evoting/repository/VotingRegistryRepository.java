package org.etf.evoting.repository;

import org.etf.evoting.model.Election;
import org.etf.evoting.model.User;
import org.etf.evoting.model.VotingRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VotingRegistryRepository extends JpaRepository<VotingRegistry, Integer> {

  // Provjera da li par (korisnik, izbor) već postoji u registru
  boolean existsByUserAndElection(User user, Election election);

  boolean existsByUserIdAndElectionId(Integer userId, Integer electionId);
}
