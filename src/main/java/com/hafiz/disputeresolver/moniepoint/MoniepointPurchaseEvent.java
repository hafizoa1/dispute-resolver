package com.hafiz.disputeresolver.moniepoint;

// A provider event snapshot, not a verified service-delivery record or a final payment ledger.
public record MoniepointPurchaseEvent(
        String webhookId,
        String providerEventId,
        long businessId,
        String transactionReference,
        String merchantReference,
        long amountRaw,
        String transactionStatus,
        String responseCode,
        String rawPayload,
        String payloadSha256) {
}
