package com.hafiz.disputeresolver.dto;

import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.DisputeStatus;

public record DisputeResponse(UUID id, String transactionReference, Long amountMinor, String currency,
        String description, DisputeStatus status, Instant createdAt, Instant updatedAt,
        String providerDisputeReference, String customerClaim, Instant responseDueAt) {

    public static DisputeResponse from(Dispute dispute) {
        return new DisputeResponse(dispute.getId(), dispute.getTransactionReference(), dispute.getAmountMinor(),
                dispute.getCurrency(), dispute.getDescription(), dispute.getStatus(), dispute.getCreatedAt(),
                dispute.getUpdatedAt(), dispute.getProviderDisputeReference(), dispute.getCustomerClaim(),
                dispute.getResponseDueAt());
    }
}
