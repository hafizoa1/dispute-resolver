package com.hafiz.disputeresolver.dto;

import jakarta.validation.constraints.NotBlank;

// Both fields required when saving an observation at all - an optional
// documentedTransactionReference would defeat the point of having it.
public record SaveObservationRequest(
        @NotBlank String observationText,
        @NotBlank String documentedTransactionReference) {
}
