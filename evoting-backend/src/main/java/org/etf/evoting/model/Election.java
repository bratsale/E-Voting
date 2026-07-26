package org.etf.evoting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "elections")
public class Election {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "start_date", nullable = false)
  private LocalDateTime startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDateTime endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ElectionStatus status = ElectionStatus.CREATED;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "organizer_id", nullable = false)
  private User organizer;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // Novo polje za sertifikat specifičan za ove izbore
  @Column(name = "certificate_pem", columnDefinition = "TEXT")
  private String certificatePem;

  // Konstruktori
  public Election() {
  }

  public Election(String title, String description, LocalDateTime startDate, LocalDateTime endDate, User organizer) {
    this.title = title;
    this.description = description;
    this.startDate = startDate;
    this.endDate = endDate;
    this.organizer = organizer;
  }

  // Getteri i Setteri
  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDateTime startDate) {
    this.startDate = startDate;
  }

  public LocalDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDateTime endDate) {
    this.endDate = endDate;
  }

  public ElectionStatus getStatus() {
    return status;
  }

  public void setStatus(ElectionStatus status) {
    this.status = status;
  }

  public User getOrganizer() {
    return organizer;
  }

  public void setOrganizer(User organizer) {
    this.organizer = organizer;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getCertificatePem() {
    return certificatePem;
  }

  public void setCertificatePem(String certificatePem) {
    this.certificatePem = certificatePem;
  }
}