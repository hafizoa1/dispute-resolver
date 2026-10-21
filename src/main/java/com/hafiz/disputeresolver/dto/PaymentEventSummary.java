package com.hafiz.disputeresolver.dto;

import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.moniepoint.MoniepointPurchaseEventRecord;

// Verbatim view of one purchase-event snapshot. receivedAt orders the list as
// arrival order only - it is never a claim about which event reflects the
// current payment state (delivery can be delayed or arrive out of order).
public record PaymentEventSummary(UUID id, String webhookId, String merchantReference, long amountRaw,
        String transactionStatus, String responseCode, Instant receivedAt) {

    public static PaymentEventSummary from(MoniepointPurchaseEventRecord event) {
        return new PaymentEventSummary(event.getId(), event.getWebhookId(), event.getMerchantReference(),
                event.getAmountRaw(), event.getTransactionStatus(), event.getResponseCode(), event.getReceivedAt());
    }
}
