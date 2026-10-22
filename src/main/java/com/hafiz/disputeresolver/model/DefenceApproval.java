package com.hafiz.disputeresolver.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

// Immutable once saved: no mutators, no update endpoint. disputeId is a plain column,
// not a JPA relation to Dispute - the whole point of this record is to freeze exactly
// what was shown and approved, so nothing here may ever be re-resolved against the
// live, possibly-since-edited Dispute/Evidence rows. snapshotJson carries that frozen
// content verbatim, same precedent as MoniepointPurchaseEventRecord.rawPayload.
@Entity
@Table(name = "defence_approvals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefenceApproval {

    @Id
    private UUID id;

    @Column(name = "dispute_id", nullable = false)
    private UUID disputeId;

    @Column(nullable = false, columnDefinition = "text")
    private String fingerprint;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant approvedAt;

    public DefenceApproval(UUID disputeId, String fingerprint, String snapshotJson) {
        this.id = UUID.randomUUID();
        this.disputeId = disputeId;
        this.fingerprint = fingerprint;
        this.snapshotJson = snapshotJson;
    }
}
