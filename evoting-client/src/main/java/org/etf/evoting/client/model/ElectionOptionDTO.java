package org.etf.evoting.client.model;

public class ElectionOptionDTO {
    private Integer id;
    private String optionText;
    private Integer electionId;

    public ElectionOptionDTO() {}

    public ElectionOptionDTO(Integer id, String optionText, Integer electionId) {
        this.id = id;
        this.optionText = optionText;
        this.electionId = electionId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public Integer getElectionId() {
        return electionId;
    }

    public void setElectionId(Integer electionId) {
        this.electionId = electionId;
    }
}