package org.etf.evoting.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElectionDTO {
    private Integer id;
    private String title;
    private String description;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer organizerId;
    private String organizerUsername;
    private List<ElectionOptionDTO> options;
    private String publicKey;

    public ElectionDTO() {}

    public ElectionDTO(Integer id, String title, String description, String status,
                       LocalDateTime startDate, LocalDateTime endDate,
                       Integer organizerId, String organizerUsername,
                       List<ElectionOptionDTO> options) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.organizerId = organizerId;
        this.organizerUsername = organizerUsername;
        this.options = options;
    }

    // Konstruktor sa publicKey (opciono, ako ti zatreba na klijentu)
    public ElectionDTO(Integer id, String title, String description, String status,
                       LocalDateTime startDate, LocalDateTime endDate,
                       Integer organizerId, String organizerUsername,
                       List<ElectionOptionDTO> options, String publicKey) {
        this(id, title, description, status, startDate, endDate, organizerId, organizerUsername, options);
        this.publicKey = publicKey;
    }

    // Geteri i Seteri
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }

    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }

    public Integer getOrganizerId() { return organizerId; }
    public void setOrganizerId(Integer organizerId) { this.organizerId = organizerId; }

    public String getOrganizerUsername() { return organizerUsername; }
    public void setOrganizerUsername(String organizerUsername) { this.organizerUsername = organizerUsername; }

    public List<ElectionOptionDTO> getOptions() { return options; }
    public void setOptions(List<ElectionOptionDTO> options) { this.options = options; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}