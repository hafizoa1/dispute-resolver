package com.hafiz.disputeresolver.moniepoint;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Read-only view of a row written by MoniepointPurchaseEventRepository's
// raw-JDBC idempotent upsert - this entity is never persisted through JPA,
// only queried. The write path is untouched and stays on JdbcTemplate.
@Entity
@Table(name = "moniepoint_purchase_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MoniepointPurchaseEventRecord {

    @Id
    private UUID id;

    @Column(name = "webhook_id", nullable = false)
    private String webhookId;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Column(name = "business_id", nullable = false)
    private long businessId;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "merchant_reference")
    private String merchantReference;

    @Column(name = "amount_raw", nullable = false)
    private long amountRaw;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "payload_sha256", nullable = false)
    private String payloadSha256;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
