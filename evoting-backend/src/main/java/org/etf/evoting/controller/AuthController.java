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

    public String certificatePem; // Opciono
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
    public Integer userId;

    public LoginResponse(String token, String username, String role, Integer userId) {
      this.token = token;
      this.username = username;
      this.role = role;
      this.userId = userId;
    }
  }

  /**
   * Endpoint za registraciju novog korisnika.
   */
  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
    try {
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

      return ResponseEntity.ok(Map.of(
              "message", "Korisnik '" + registeredUser.getUsername() + "' uspješno registrovan!"
      ));
    } catch (IllegalArgumentException e) {
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
      Optional<User> userOpt = userService.login(request.username, request.password);

      if (userOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Neispravno korisničko ime ili lozinka."));
      }

      User user = userOpt.get();

      if (request.certificatePem == null || request.certificatePem.isBlank()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Niste priložili digitalni sertifikat."));
      }

      cryptoService.validateUserCertificate(request.certificatePem, user.getUsername(), user.getRole().name());

      String token = jwtUtil.generateToken(user.getUsername(), user.getId(), user.getRole().name());

      return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name(), user.getId()));

    } catch (SecurityException | IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
              .body(Map.of("message", "Greška pri verifikaciji sertifikata: " + e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("message", "Neuspješna verifikacija sertifikata: " + e.getMessage()));
    }
  }
}