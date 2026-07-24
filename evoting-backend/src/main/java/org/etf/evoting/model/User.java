package org.etf.evoting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, unique = true, length = 50)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(name = "certificate_pem", nullable = false, columnDefinition = "TEXT")
  private String certificatePem;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "first_name", nullable = true)
  private String firstName;

  @Column(name = "last_name", nullable = true)
  private String lastName;

  @Column(name = "org_name", nullable = true)
  private String orgName;

  @Column(name = "org_id", nullable = true)
  private String orgId;

// + odgovarajući getter-i i setter-i za ova polja

  // Konstruktori
  public User() {
  }

  public User(String username, String passwordHash, Role role, String certificatePem) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.certificatePem = certificatePem;
  }

  // Getteri i Setteri
  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public String getCertificatePem() {
    return certificatePem;
  }

  public void setCertificatePem(String certificatePem) {
    this.certificatePem = certificatePem;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public void setFirstName(String finalFirstName) {
    this.firstName = finalFirstName;
  }

  public void setLastName(String finalLastName) {
    this.lastName = finalLastName;
  }

  public void setOrgName(String finalOrgName) {
    this.orgName = finalOrgName;
  }

  public void setOrgId(String finalOrgId) {
    this.orgId = finalOrgId;
  }
}
