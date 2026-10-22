package com.hafiz.disputeresolver.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// evidenceIds must be resubmitted here, not reused from a prior preview call: the
// preview is stateless, so the fingerprint is only meaningful when checked against a
// freshly rebuilt preview of exactly this disputeId/evidenceIds pair.
public record DefenceApprovalRequest(@NotNull List<UUID> evidenceIds, @NotBlank String fingerprint) {
}
