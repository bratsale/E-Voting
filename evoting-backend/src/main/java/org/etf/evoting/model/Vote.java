package org.etf.evoting.model;

import jakarta.persistence.*;

@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Lob
    @Column(name = "encrypted_vote", nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedVoteBase64; // AES enkriptovan ID opcije

    @Lob
    @Column(name = "encrypted_sym_key", nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedSymKeyBase64; // AES ključ enkriptovan RSA javnim ključem organizatora

    @Column(name = "iv_base64", nullable = false)
    private String ivBase64; // IV za AES/GCM

    @Lob
    @Column(name = "voter_signature", nullable = false, columnDefinition = "LONGTEXT")
    private String voterSignatureBase64; // Digitalni potpis glasača

    @Column(name = "receipt_code", nullable = false, unique = true)
    private String receiptCode; // Nasumični UUID kod koji se predaje glasaču za verifikaciju

    public Vote() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getElectionId() { return electionId; }
    public void setElectionId(Long electionId) { this.electionId = electionId; }

    public String getEncryptedVoteBase64() { return encryptedVoteBase64; }
    public void setEncryptedVoteBase64(String encryptedVoteBase64) { this.encryptedVoteBase64 = encryptedVoteBase64; }

    public String getEncryptedSymKeyBase64() { return encryptedSymKeyBase64; }
    public void setEncryptedSymKeyBase64(String encryptedSymKeyBase64) { this.encryptedSymKeyBase64 = encryptedSymKeyBase64; }

    public String getIvBase64() { return ivBase64; }
    public void setIvBase64(String ivBase64) { this.ivBase64 = ivBase64; }

    public String getVoterSignatureBase64() { return voterSignatureBase64; }
    public void setVoterSignatureBase64(String voterSignatureBase64) { this.voterSignatureBase64 = voterSignatureBase64; }

    public String getReceiptCode() { return receiptCode; }
    public void setReceiptCode(String receiptCode) { this.receiptCode = receiptCode; }
}