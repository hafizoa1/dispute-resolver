package com.hafiz.disputeresolver.exception;

import java.util.UUID;

public class DefenceNotSavedException extends RuntimeException {

    public DefenceNotSavedException(UUID disputeId) {
        super("No nonblank defence has been saved for dispute: " + disputeId);
    }
}
