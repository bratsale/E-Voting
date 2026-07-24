package org.etf.evoting.repository;

import org.etf.evoting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

  // Za autentifikaciju i provjeru sertifikata
  Optional<User> findByUsername(String username);

  // Brza provjera postojanja
  boolean existsByUsername(String username);
}
