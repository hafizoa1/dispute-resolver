package com.hafiz.disputeresolver.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String transactionReference;

    @Column(nullable = false)
    private Long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Dispute(String transactionReference, Long amountMinor, String currency, String description) {
        this.id = UUID.randomUUID();
        this.transactionReference = transactionReference;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.description = description;
        this.status = DisputeStatus.OPEN;
    }

    public void correctDetails(String transactionReference, Long amountMinor, String currency, String description) {
        if (transactionReference != null) {
            this.transactionReference = transactionReference;
        }
        if (amountMinor != null) {
            this.amountMinor = amountMinor;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
