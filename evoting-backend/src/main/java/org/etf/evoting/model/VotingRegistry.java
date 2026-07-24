package org.etf.evoting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "voting_registry", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "user_id", "election_id" })
})
public class VotingRegistry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "election_id", nullable = false)
  private Election election;

  @Column(name = "voted_at", insertable = false, updatable = false)
  private LocalDateTime votedAt;

  // Konstruktori
  public VotingRegistry() {
  }

  public VotingRegistry(User user, Election election) {
    this.user = user;
    this.election = election;
  }

  // Getteri i Setteri
  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Election getElection() {
    return election;
  }

  public void setElection(Election election) {
    this.election = election;
  }

  public LocalDateTime getVotedAt() {
    return votedAt;
  }

  public void setVotedAt(LocalDateTime votedAt) {
    this.votedAt = votedAt;
  }
}
