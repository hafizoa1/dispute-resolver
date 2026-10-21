package com.hafiz.disputeresolver.dto;

import java.util.List;

// scopeDisclaimer is unconditional - present whether or not gaps is empty, so an empty
// gaps list never reads as "ready to submit" or "strong defence." draftText is rendered
// from exactly this same data (paymentEvents/evidenceCitations/gaps), never a second,
// independently-maintained version of the same facts.
public record ReviewResponse(DisputeResponse dispute, List<PaymentEventSummary> paymentEvents,
        boolean paymentEventConflict, List<EvidenceCitation> evidenceCitations, List<ReviewGap> gaps,
        String scopeDisclaimer, String draftText) {
}
