package com.hafiz.disputeresolver.exception;

import java.util.UUID;

// Thrown when the fingerprint supplied at approval time no longer matches the
// dispute's current content (defence text, dispute fields, or the selected
// evidence/attachments) - the caller must request a fresh preview and resubmit.
public class DefenceContentChangedException extends RuntimeException {

    public DefenceContentChangedException(UUID disputeId) {
        super("Dispute content changed since the defence preview was built: " + disputeId);
    }
}
