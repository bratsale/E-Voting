package org.etf.evoting.repository;

import org.etf.evoting.model.Election;
import org.etf.evoting.model.ElectionStatus;
import org.etf.evoting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ElectionRepository extends JpaRepository<Election, Integer> {

  List<Election> findByStatus(ElectionStatus status);

  List<Election> findByOrganizer(User organizer);
}
