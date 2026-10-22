package com.hafiz.disputeresolver.dto;

import java.util.UUID;

// Mirrors EvidenceCitation plus the attachment's own content, read fresh from disk at
// preview/approval time - never cached, never extracted or interpreted beyond decoding
// as UTF-8 text.
public record EvidencePreviewItem(UUID evidenceId, String fileName, String observationText,
        String documentedTransactionReference, Boolean referenceMatches, String attachmentContent) {
}
