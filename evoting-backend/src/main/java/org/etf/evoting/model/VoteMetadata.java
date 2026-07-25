package org.etf.evoting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vote_metadata")
public class VoteMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "vote_id", nullable = false)
    private Integer voteId;

    @Column(name = "election_id", nullable = false)
    private Integer electionId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "hmac", nullable = false)
    private String hmac;

    public VoteMetadata() {}

    public VoteMetadata(Integer voteId, Integer electionId, LocalDateTime timestamp, String hmac) {
        this.voteId = voteId;
        this.electionId = electionId;
        this.timestamp = timestamp;
        this.hmac = hmac;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getVoteId() { return voteId; }
    public void setVoteId(Integer voteId) { this.voteId = voteId; }

    public Integer getElectionId() { return electionId; }
    public void setElectionId(Integer electionId) { this.electionId = electionId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getHmac() { return hmac; }
    public void setHmac(String hmac) { this.hmac = hmac; }
}