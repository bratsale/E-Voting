package org.etf.evoting.controller;

import org.etf.evoting.model.Role;
import org.etf.evoting.model.User;
import org.etf.evoting.security.JwtUtil;
import org.etf.evoting.service.CryptoService;
import org.etf.evoting.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;
  private final JwtUtil jwtUtil;
  private final CryptoService cryptoService;

  public AuthController(UserService userService, JwtUtil jwtUtil, CryptoService cryptoService) {
    this.userService = userService;
    this.jwtUtil = jwtUtil;
    this.cryptoService = cryptoService;
  }

  /**
   * DTO za registraciju korisnika.
   * Ugrađena polja za Glasača (firstName, lastName) i Organizatora (orgName, orgId).
   */
  public static class RegisterRequest {
    public String username;
    public String password;
    public Role role;

    // Za Glasače
    public String firstName;
    public String lastName;

    // Za Organizatore
    public String orgName;
    public String orgId;

    public String certificatePem; // Ako se šalje eksterni sertifikat (opciono)
  }

  /**
   * DTO za login.
   */
  public static class LoginRequest {
    public String username;
    public String password;
    public String certificatePem;
  }

  /**
   * DTO za login odgovor.
   */
  public static class LoginResponse {
    public String token;
    public String username;
    public String role;
    public Integer userId; // ✅ Dodato polje

    public LoginResponse(String token, String username, String role, Integer userId) {
      this.token = token;
      this.username = username;
      this.role = role;
      this.userId = userId; // ✅ Postavljanje ID-a
    }
  }

  /**
   * Endpoint za registraciju novog korisnika.
   */
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    try {
      // Prilagodi poziv registracije u UserService zavisno od toga kako si tamo definisao metod
      User registeredUser = userService.registerUser(
              request.username,
              request.password,
              request.role,
              request.firstName,
              request.lastName,
              request.orgName,
              request.orgId,
              request.certificatePem
      );

      // ✅ Vraćamo JSON: {"message": "Korisnik 'sasa' uspješno registrovan!"}
      return ResponseEntity.ok(Map.of(
              "message", "Korisnik '" + registeredUser.getUsername() + "' uspješno registrovan!"
      ));
    } catch (IllegalArgumentException e) {
      // ✅ Vraćamo JSON grešku: {"message": "Korisničko ime je već zauzeto."}
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("message", "Greška na serveru: " + e.getMessage()));
    }
  }

  /**
   * Endpoint za prijavu korisnika.
   */
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    try {
      // 1. Provjera lozinke i korisnika u bazi
      Optional<User> userOpt = userService.login(request.username, request.password);

      if (userOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Neispravno korisničko ime ili lozinka."));
      }

      User user = userOpt.get();

      // 2. VERIFIKACIJA DIGITALNOG SERTIFIKATA (2FA)
      if (request.certificatePem == null || request.certificatePem.isBlank()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Niste priložili digitalni sertifikat."));
      }

      // Pozivamo proveru da li je sertifikat validan i pripada li prijavljenom korisniku
      cryptoService.validateUserCertificate(request.certificatePem, user.getUsername(), user.getRole().name());

      // 3. Ako su i lozinka i sertifikat ispravni, izdajemo JWT token
      String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole().name());

      return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name(), user.getId()));

    } catch (SecurityException | IllegalArgumentException e) {
      // Odbijamo login ako sertifikat nije od tog korisnika ili nije validan
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(Map.of("message", "Greška pri verifikaciji sertifikata: " + e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("message", "Neuspješna verifikacija sertifikata: " + e.getMessage()));
    }
  }
}