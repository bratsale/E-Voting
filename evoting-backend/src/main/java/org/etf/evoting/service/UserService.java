package org.etf.evoting.service;

import org.etf.evoting.model.User;
import org.etf.evoting.model.Role;
import org.etf.evoting.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final CryptoService cryptoService;

  public UserService(UserRepository userRepository, CryptoService cryptoService) {
    this.userRepository = userRepository;
    this.cryptoService = cryptoService;
  }

  /**
   * Registracija novog korisnika sa X.509 sertifikatom.
   */
  public User registerUser(
          String username,
          String rawPassword,
          Role role,
          String firstName,
          String lastName,
          String orgName,
          String orgId,
          String certificatePem) {

    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Korisničko ime '" + username + "' je već zauzeto.");
    }

    String finalFirstName = null;
    String finalLastName = null;
    String finalOrgName = null;
    String finalOrgId = null;

    if (role == Role.ORGANIZER || "ORGANIZER".equalsIgnoreCase(String.valueOf(role))) {
      if (orgName == null || orgName.isBlank() || orgId == null || orgId.isBlank()) {
        throw new IllegalArgumentException("Za organizatora su obavezni naziv organizacije i identifikacioni broj.");
      }
      finalOrgName = orgName;
      finalOrgId = orgId;
    } else {
      if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
        throw new IllegalArgumentException("Za glasača su obavezni ime i prezime.");
      }
      finalFirstName = firstName;
      finalLastName = lastName;
    }

    String passwordHash = fakeBCryptHash(rawPassword);

    if (certificatePem == null || certificatePem.isBlank()) {
      try {
        // Generišemo sertifikat potpisan od CA i čuvamo .p12 u pki/korisnici/
        String roleStr = (role != null) ? role.name() : "VOTER";
        certificatePem = cryptoService.generateAndSaveUserCertificate(username, roleStr, rawPassword);
      } catch (Exception e) {
        System.err.println("❌ GREŠKA PRILIKOM GENERISANJA SERTIFIKATA U USER SERVICE:");
        e.printStackTrace(); // Ispisuje tačnu liniju i izuzetak u terminalu backenda
        throw new RuntimeException("Greška pri generisanju sertifikata za korisnika: " + username + " -> " + e.getMessage(), e);
      }
    }

    User newUser = new User();
    newUser.setUsername(username);
    newUser.setPasswordHash(passwordHash);
    newUser.setRole(role);
    newUser.setCertificatePem(certificatePem);
    newUser.setFirstName(finalFirstName);
    newUser.setLastName(finalLastName);
    newUser.setOrgName(finalOrgName);
    newUser.setOrgId(finalOrgId);

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
   * Pronalaženje korisnika po ID-ju.
   */
  public Optional<User> getUserById(Integer id) {
    return userRepository.findById(id);
  }

  // Pomoćne metode za simulaciju/hash-ovanje lozinke
  private String fakeBCryptHash(String password) {
    return "{bcrypt}" + Integer.toHexString(password.hashCode());
  }

  private boolean checkPassword(String rawPassword, String encodedPassword) {
    return fakeBCryptHash(rawPassword).equals(encodedPassword);
  }
}