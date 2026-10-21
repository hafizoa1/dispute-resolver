package com.hafiz.disputeresolver.moniepoint;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MoniepointPurchaseEventRepository {
    private final JdbcTemplate jdbc;

    public MoniepointPurchaseEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void saveOnce(MoniepointPurchaseEvent event) {
        int inserted = jdbc.update("""
                INSERT INTO moniepoint_purchase_events
                    (id, webhook_id, provider_event_id, business_id, transaction_reference,
                     merchant_reference, amount_raw, transaction_status, response_code,
                     raw_payload, payload_sha256, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (webhook_id) DO NOTHING
                """, UUID.randomUUID(), event.webhookId(), event.providerEventId(), event.businessId(),
                event.transactionReference(), event.merchantReference(), event.amountRaw(),
                event.transactionStatus(), event.responseCode(), event.rawPayload(), event.payloadSha256());

        if (inserted == 0) {
            String existingHash = jdbc.queryForObject(
                    "SELECT payload_sha256 FROM moniepoint_purchase_events WHERE webhook_id = ?",
                    String.class, event.webhookId());
            if (!event.payloadSha256().equals(existingHash)) {
                throw new WebhookException(HttpStatus.CONFLICT, "Webhook ID was already received with different content");
            }
        }
    }
}
