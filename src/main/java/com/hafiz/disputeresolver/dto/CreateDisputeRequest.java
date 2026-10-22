package com.hafiz.disputeresolver.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateDisputeRequest(
        @NotBlank String transactionReference,
        @NotNull @Positive Long amountMinor,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code, e.g. GBP") String currency,
        @NotBlank String description,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String providerDisputeReference,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String customerClaim,
        Instant responseDueAt) {
}
