package org.etf.evoting.service;

import org.etf.evoting.model.User;
import org.etf.evoting.model.Role;
import org.etf.evoting.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {

  private final UserRepository userRepository;
  // Ako nemaš Spring Security u pom.xml, ovdje privremeno možemo koristiti obični
  // string,
  // ali za pravi sistem ćemo uvesti BCrypt. Pretpostavimo za sada ručni hash ili
  // plain za test.

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Registracija novog korisnika sa X.509 sertifikatom.
   */
  public User registerUser(String username, String rawPassword, String email, Role role, String certificatePem) {
    // 1. Provjera da li korisničko ime ili email već postoje
    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Korisničko ime '" + username + "' je već zauzeto.");
    }
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email '" + email + "' je već u upotrebi.");
    }

    // 2. Hash-ovanje lozinke (ovdje stavljamo placeholder dok ne uvežemo BCrypt)
    String passwordHash = fakeBCryptHash(rawPassword);

    // 3. Kreiranje i čuvanje korisnika
    User newUser = new User(username, passwordHash, email, role, certificatePem);
    return userRepository.save(newUser);
  }

  /**
   * Autentifikacija korisnika (Login).
   */
  public Optional<User> login(String username, String rawPassword) {
    return userRepository.findByUsername(username)
        .filter(user -> checkPassword(rawPassword, user.getPasswordHash()));
  }

  /**
   * Pronalaženje korisnika po ID-ju (trebaće nam za kontrolere).
   */
  public Optional<User> getUserById(Integer id) {
    return userRepository.findById(id);
  }

  // Pomoćne metode za simulaciju/hash-ovanje lozinke
  private String fakeBCryptHash(String password) {
    // Kada dodamo Spring Security, ovdje ide: return
    // passwordEncoder.encode(password);
    return "{bcrypt}" + Integer.toHexString(password.hashCode());
  }

  private boolean checkPassword(String rawPassword, String encodedPassword) {
    // Kada dodamo Spring Security, ovdje ide: return
    // passwordEncoder.matches(rawPassword, encodedPassword);
    return fakeBCryptHash(rawPassword).equals(encodedPassword);
  }
}
