package com.hafiz.disputeresolver.dto;

import java.util.UUID;

// referenceMatches is deliberately a nullable Boolean, not a plain boolean:
// null - no documentedTransactionReference supplied, comparison unavailable
//        (in practice this means no observation was ever recorded at all,
//        since SaveObservationRequest requires both fields together).
// true  - documentedTransactionReference exactly matches the dispute's own reference.
// false - a reference was supplied and it differs.
// This only checks the entered metadata against Dispute.transactionReference - it says
// nothing about whether observationText's content actually supports anything. That
// stays a human-review question.
public record EvidenceCitation(UUID evidenceId, String fileName, String observationText,
        String documentedTransactionReference, Boolean referenceMatches) {
}
