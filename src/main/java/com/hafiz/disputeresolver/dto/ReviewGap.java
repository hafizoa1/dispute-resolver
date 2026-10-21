package com.hafiz.disputeresolver.dto;

import java.util.UUID;

// evidenceId is null for dispute-level gaps (no payment event, conflicting payment
// events, no evidence attached at all); set for evidence-specific gaps (no observation
// recorded, documented reference doesn't match) so each gap is traceable to a record.
public record ReviewGap(String message, UUID evidenceId) {
}
