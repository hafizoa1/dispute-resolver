package com.hafiz.disputeresolver.dto;

import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.model.DefenceApproval;
import tools.jackson.databind.JsonNode;

// snapshot is read back from the approval's own stored JSON text, never reconstructed
// from live Dispute/Evidence rows - so later edits to those rows can never change what
// this returns for an already-approved snapshot.
public record DefenceApprovalResponse(UUID id, UUID disputeId, String fingerprint, Instant approvedAt,
        JsonNode snapshot) {

    public static DefenceApprovalResponse from(DefenceApproval approval, JsonNode snapshot) {
        return new DefenceApprovalResponse(approval.getId(), approval.getDisputeId(), approval.getFingerprint(),
                approval.getApprovedAt(), snapshot);
    }
}
