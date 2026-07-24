package org.etf.evoting.repository;

import org.etf.evoting.model.Election;
import org.etf.evoting.model.ElectionStatus;
import org.etf.evoting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Integer> {

  // Pronalaženje svih izbora određenog statusa (npr. ACTIVE)
  List<Election> findByStatus(ElectionStatus status);

  // Pronalaženje svih izbora koje je kreirao određeni organizator
  List<Election> findByOrganizer(User organizer);
}
