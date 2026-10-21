package com.hafiz.disputeresolver.dto;

import java.util.List;

// paymentEventConflict is true only when two events disagree on a field they
// both actually have a value for - a missing field on one side is unknown,
// never a mismatch. No resolved "status" (found/missing/ambiguous) is exposed:
// the caller reads the raw history and the conflict flag and decides what it means.
public record CaseResponse(DisputeResponse dispute, List<PaymentEventSummary> paymentEvents,
        boolean paymentEventConflict, List<EvidenceResponse> evidence) {
}
