package org.etf.evoting.controller;

import org.etf.evoting.model.User;
import org.etf.evoting.model.Role;
import org.etf.evoting.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;

  public AuthController(UserService userService) {
    this.userService = userService;
  }

  /**
   * DTO (Data Transfer Object) za registraciju korisnika.
   */
  public static class RegisterRequest {
    public String username;
    public String password;
    public String email;
    public Role role;
    public String certificatePem; // Prosjeđuje se sertifikat izdat od PKI sistema
  }

  /**
   * DTO za login.
   */
  public static class LoginRequest {
    public String username;
    public String password;
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
          request.email,
          request.role,
          request.certificatePem);
      return ResponseEntity.ok("Korisnik '" + registeredUser.getUsername() + "' uspješno registrovan!");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  /**
   * Endpoint za login.
   */
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    Optional<User> userOpt = userService.login(request.username, request.password);

    if (userOpt.isPresent()) {
      User user = userOpt.get();
      // Za sada vraćamo samo bazičnu potvrdu i ulogu.
      // Kasnije ovdje možemo generisati JWT token za potpunu bezbjednost sesije.
      return ResponseEntity.ok("Uspješan login! Dobrodošli, " + user.getUsername() + " [" + user.getRole() + "]");
    } else {
      return ResponseEntity.status(401).body("Neispravno korisničko ime ili lozinka.");
    }
  }
}
