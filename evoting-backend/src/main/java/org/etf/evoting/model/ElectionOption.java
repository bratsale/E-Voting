package org.etf.evoting.model;

import jakarta.persistence.*;

@Entity
@Table(name = "election_options")
public class ElectionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(name = "option_text", nullable = false, length = 255)
    private String optionText;

    // Konstruktori
    public ElectionOption() {}

    public ElectionOption(Election election, String optionText) {
        this.election = election;
        this.optionText = optionText;
    }

    // Getteri i Setteri
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Election getElection() { return election; }
    public void setElection(Election election) { this.election = election; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
}
