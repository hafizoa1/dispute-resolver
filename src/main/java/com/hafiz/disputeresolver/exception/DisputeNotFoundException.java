package com.hafiz.disputeresolver.exception;

import java.util.UUID;

public class DisputeNotFoundException extends RuntimeException {

    public DisputeNotFoundException(UUID id) {
        super("Dispute not found: " + id);
    }
}
