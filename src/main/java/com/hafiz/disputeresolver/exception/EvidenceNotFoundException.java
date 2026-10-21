package com.hafiz.disputeresolver.exception;

import java.util.UUID;

// Also thrown when the evidence exists but belongs to a different dispute than
// the one in the URL - deliberately indistinguishable from "doesn't exist" to
// the caller, so this never confirms a piece of evidence exists on another dispute.
public class EvidenceNotFoundException extends RuntimeException {

    public EvidenceNotFoundException(UUID id) {
        super("Evidence not found: " + id);
    }
}
