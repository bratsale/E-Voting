package org.etf.evoting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ballots")
public class Ballot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "election_id", nullable = false)
  private Election election;

  // Enkriptovan ID opcije (AES-GCM)
  @Column(name = "encrypted_vote", nullable = false, columnDefinition = "TEXT")
  private String encryptedVote;

  // AES ključ enkriptovan RSA javnim ključem organizatora
  @Column(name = "encrypted_sym_key", nullable = false, columnDefinition = "TEXT")
  private String encryptedSymKey;

  // Initialization Vector za AES/GCM
  @Column(name = "iv_base64", nullable = false)
  private String ivBase64;

  // Digitalni potpis glasača
  @Column(name = "digital_signature", nullable = false, columnDefinition = "TEXT")
  private String digitalSignature;

  // Nasumični kod koji se vraća glasaču za verifikaciju
  @Column(name = "receipt_code", nullable = false, unique = true)
  private String receiptCode;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // Konstruktori
  public Ballot() {
  }

  public Ballot(Election election, String encryptedVote, String encryptedSymKey,
                String ivBase64, String digitalSignature, String receiptCode) {
    this.election = election;
    this.encryptedVote = encryptedVote;
    this.encryptedSymKey = encryptedSymKey;
    this.ivBase64 = ivBase64;
    this.digitalSignature = digitalSignature;
    this.receiptCode = receiptCode;
  }

  // Getteri i Setteri
  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public Election getElection() {
    return election;
  }

  public void setElection(Election election) {
    this.election = election;
  }

  public String getEncryptedVote() {
    return encryptedVote;
  }

  public void setEncryptedVote(String encryptedVote) {
    this.encryptedVote = encryptedVote;
  }

  public String getEncryptedSymKey() {
    return encryptedSymKey;
  }

  public void setEncryptedSymKey(String encryptedSymKey) {
    this.encryptedSymKey = encryptedSymKey;
  }

  public String getIvBase64() {
    return ivBase64;
  }

  public void setIvBase64(String ivBase64) {
    this.ivBase64 = ivBase64;
  }

  public String getDigitalSignature() {
    return digitalSignature;
  }

  public void setDigitalSignature(String digitalSignature) {
    this.digitalSignature = digitalSignature;
  }

  public String getReceiptCode() {
    return receiptCode;
  }

  public void setReceiptCode(String receiptCode) {
    this.receiptCode = receiptCode;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}