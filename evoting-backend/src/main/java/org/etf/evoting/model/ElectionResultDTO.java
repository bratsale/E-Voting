package org.etf.evoting.model;

import java.util.Map;

public class ElectionResultDTO {

    private Integer electionId;
    private String electionTitle;
    private long totalVotes;
    private Map<String, Long> voteCounts; // Naziv opcije -> Broj glasova

    public ElectionResultDTO(Integer electionId, String electionTitle, long totalVotes, Map<String, Long> voteCounts) {
        this.electionId = electionId;
        this.electionTitle = electionTitle;
        this.totalVotes = totalVotes;
        this.voteCounts = voteCounts;
    }

    // Getters
    public Integer getElectionId() { return electionId; }
    public String getElectionTitle() { return electionTitle; }
    public long getTotalVotes() { return totalVotes; }
    public Map<String, Long> getVoteCounts() { return voteCounts; }
}