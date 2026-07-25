package org.etf.evoting.model;

public class ElectionOptionDTO {
    private Integer id;
    private String optionText;
    private Integer electionId;

    public ElectionOptionDTO(Integer id, String optionText, Integer electionId) {
        this.id = id;
        this.optionText = optionText;
        this.electionId = electionId;
    }

    // Getter-i i Setter-i...
    public Integer getId() { return id; }
    public String getOptionText() { return optionText; }
    public Integer getElectionId() { return electionId; }
    public void setOptions(String optionText){
        this.optionText = optionText;
    }
}