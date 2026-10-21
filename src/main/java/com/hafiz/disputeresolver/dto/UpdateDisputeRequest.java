package com.hafiz.disputeresolver.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// Null fields are left unchanged, so the client only sends what it wants to edit.
public record UpdateDisputeRequest(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String transactionReference,
        @Positive Long amountMinor,
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code, e.g. GBP") String currency,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String description) {
}
