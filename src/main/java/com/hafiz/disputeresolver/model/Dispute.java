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

    @Column(columnDefinition = "text")
    private String providerDisputeReference;

    @Column(columnDefinition = "text")
    private String customerClaim;

    private Instant responseDueAt;

    // The merchant's own free-text defence, saved verbatim - never generated or
    // rewritten here. Nullable: a dispute has none until the merchant saves one.
    @Column(columnDefinition = "text")
    private String defenceText;

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

    // Shared by create (where an omitted field simply stays null) and update (where it
    // must stay whatever it already was) - both are "if supplied, set" against a target
    // that starts out null for a fresh dispute, so one method covers both call sites.
    public void recordProviderCaseDetails(String providerDisputeReference, String customerClaim,
            Instant responseDueAt) {
        if (providerDisputeReference != null) {
            this.providerDisputeReference = providerDisputeReference;
        }
        if (customerClaim != null) {
            this.customerClaim = customerClaim;
        }
        if (responseDueAt != null) {
            this.responseDueAt = responseDueAt;
        }
    }

    // Dedicated save action, not a partial update - the merchant always sends the
    // full text, and resaving simply overwrites whatever draft was there before.
    public void recordDefenceText(String defenceText) {
        this.defenceText = defenceText;
    }
}
