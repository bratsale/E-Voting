package org.etf.evoting.client.model;

import java.util.Map;

public class ElectionResultDTO {

    private Integer electionId;
    private String electionTitle;
    private long totalVotes;
    private Map<String, Long> voteCounts; // Naziv opcije -> Broj glasova
    private String reportContent;
    private String reportSignatureBase64;

    public ElectionResultDTO() {}

    public ElectionResultDTO(Integer electionId, String electionTitle, long totalVotes, Map<String, Long> voteCounts) {
        this.electionId = electionId;
        this.electionTitle = electionTitle;
        this.totalVotes = totalVotes;
        this.voteCounts = voteCounts;
    }

    public ElectionResultDTO(Integer electionId, String electionTitle, long totalVotes,
                             Map<String, Long> voteCounts, String reportContent, String reportSignatureBase64) {
        this.electionId = electionId;
        this.electionTitle = electionTitle;
        this.totalVotes = totalVotes;
        this.voteCounts = voteCounts;
        this.reportContent = reportContent;
        this.reportSignatureBase64 = reportSignatureBase64;
    }

    // Getteri i Setteri
    public Integer getElectionId() { return electionId; }
    public void setElectionId(Integer electionId) { this.electionId = electionId; }

    public String getElectionTitle() { return electionTitle; }
    public void setElectionTitle(String electionTitle) { this.electionTitle = electionTitle; }

    public long getTotalVotes() { return totalVotes; }
    public void setTotalVotes(long totalVotes) { this.totalVotes = totalVotes; }

    public Map<String, Long> getVoteCounts() { return voteCounts; }
    public void setVoteCounts(Map<String, Long> voteCounts) { this.voteCounts = voteCounts; }

    public String getReportContent() { return reportContent; }
    public void setReportContent(String reportContent) { this.reportContent = reportContent; }

    public String getReportSignatureBase64() { return reportSignatureBase64; }
    public void setReportSignatureBase64(String reportSignatureBase64) { this.reportSignatureBase64 = reportSignatureBase64; }
}