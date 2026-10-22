package com.hafiz.disputeresolver.exception;

import java.util.UUID;

// Also thrown when the approval exists but belongs to a different dispute than
// the one in the URL - same "collapse into 404" precedent as EvidenceNotFoundException.
public class DefenceApprovalNotFoundException extends RuntimeException {

    public DefenceApprovalNotFoundException(UUID id) {
        super("Defence approval not found: " + id);
    }
}
