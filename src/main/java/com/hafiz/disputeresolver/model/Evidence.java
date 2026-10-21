package com.hafiz.disputeresolver.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @Column(nullable = false, columnDefinition = "text")
    private String fileName;

    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    // Merchant-provided, not extracted or verified - what the merchant says this
    // evidence shows, and which transaction reference they're asserting it documents.
    // Both nullable: existing evidence can remain without one.
    @Column(columnDefinition = "text")
    private String observationText;

    @Column(columnDefinition = "text")
    private String documentedTransactionReference;

    public Evidence(UUID id, Dispute dispute, String fileName, String contentType, long sizeBytes,
            String description) {
        this.id = id;
        this.dispute = dispute;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.description = description;
    }

    public void recordObservation(String observationText, String documentedTransactionReference) {
        this.observationText = observationText;
        this.documentedTransactionReference = documentedTransactionReference;
    }
}
