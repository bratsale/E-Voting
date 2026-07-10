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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "option_id", nullable = false)
  private ElectionOption electionOption;

  @Column(name = "digital_signature", nullable = false, columnDefinition = "TEXT")
  private String digitalSignature;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  // Konstruktori
  public Ballot() {
  }

  public Ballot(Election election, ElectionOption electionOption, String digitalSignature) {
    this.election = election;
    this.electionOption = electionOption;
    this.digitalSignature = digitalSignature;
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

  public ElectionOption getElectionOption() {
    return electionOption;
  }

  public void setElectionOption(ElectionOption electionOption) {
    this.electionOption = electionOption;
  }

  public String getDigitalSignature() {
    return digitalSignature;
  }

  public void setDigitalSignature(String digitalSignature) {
    this.digitalSignature = digitalSignature;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
