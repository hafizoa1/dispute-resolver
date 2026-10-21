package com.hafiz.disputeresolver.dto;

import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Evidence;

public record EvidenceResponse(UUID id, UUID disputeId, String fileName, String contentType, long sizeBytes,
        String description, Instant uploadedAt) {

    public static EvidenceResponse from(Evidence evidence) {
        return new EvidenceResponse(evidence.getId(), evidence.getDispute().getId(), evidence.getFileName(),
                evidence.getContentType(), evidence.getSizeBytes(), evidence.getDescription(),
                evidence.getUploadedAt());
    }
}
