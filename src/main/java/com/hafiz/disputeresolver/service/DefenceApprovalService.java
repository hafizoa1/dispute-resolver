package com.hafiz.disputeresolver.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hafiz.disputeresolver.dto.DefenceApprovalResponse;
import com.hafiz.disputeresolver.dto.DefencePreview;
import com.hafiz.disputeresolver.dto.DisputeResponse;
import com.hafiz.disputeresolver.dto.EvidenceCitation;
import com.hafiz.disputeresolver.dto.EvidencePreviewItem;
import com.hafiz.disputeresolver.dto.ReviewResponse;
import com.hafiz.disputeresolver.exception.DefenceApprovalNotFoundException;
import com.hafiz.disputeresolver.exception.DefenceContentChangedException;
import com.hafiz.disputeresolver.exception.DefenceNotSavedException;
import com.hafiz.disputeresolver.exception.EvidenceNotFoundException;
import com.hafiz.disputeresolver.model.DefenceApproval;
import com.hafiz.disputeresolver.repository.DefenceApprovalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Reuses ReviewService for warnings/scopeDisclaimer/evidence citations - no second,
// independently-maintained copy of that logic. The fingerprint covers only the content
// actually being approved (dispute fields, defenceText, selected evidence incl.
// attachment bytes) - never the warnings, which are informational, shown alongside the
// preview, not part of what's approved.
@Service
public class DefenceApprovalService {

    private final DisputeService disputeService;
    private final ReviewService reviewService;
    private final EvidenceService evidenceService;
    private final DefenceApprovalRepository defenceApprovalRepository;
    private final JsonMapper jsonMapper;

    public DefenceApprovalService(DisputeService disputeService, ReviewService reviewService,
            EvidenceService evidenceService, DefenceApprovalRepository defenceApprovalRepository,
            JsonMapper jsonMapper) {
        this.disputeService = disputeService;
        this.reviewService = reviewService;
        this.evidenceService = evidenceService;
        this.defenceApprovalRepository = defenceApprovalRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(readOnly = true)
    public DefencePreview preview(UUID disputeId, List<UUID> evidenceIds) {
        return buildPreview(disputeId, evidenceIds);
    }

    @Transactional
    public DefenceApprovalResponse approve(UUID disputeId, List<UUID> evidenceIds, String fingerprint) {
        DefencePreview freshPreview = buildPreview(disputeId, evidenceIds);
        if (!freshPreview.fingerprint().equals(fingerprint)) {
            throw new DefenceContentChangedException(disputeId);
        }

        // Stored and returned from the exact same JsonNode, so the response an approver
        // sees immediately is provably identical to what a later GET will reconstruct.
        JsonNode snapshot = jsonMapper.valueToTree(freshPreview);
        DefenceApproval approval = new DefenceApproval(disputeId, freshPreview.fingerprint(), snapshot.toString());
        return DefenceApprovalResponse.from(defenceApprovalRepository.saveAndFlush(approval), snapshot);
    }

    @Transactional(readOnly = true)
    public DefenceApprovalResponse getApproval(UUID disputeId, UUID approvalId) {
        DefenceApproval approval = defenceApprovalRepository.findById(approvalId)
                .filter(found -> found.getDisputeId().equals(disputeId))
                .orElseThrow(() -> new DefenceApprovalNotFoundException(approvalId));
        // Read back from the stored text only - never re-resolved against the live
        // Dispute/Evidence rows, so later edits to those rows cannot change this.
        return DefenceApprovalResponse.from(approval, jsonMapper.readTree(approval.getSnapshotJson()));
    }

    private DefencePreview buildPreview(UUID disputeId, List<UUID> evidenceIds) {
        DisputeResponse dispute = disputeService.getById(disputeId);
        if (dispute.defenceText() == null || dispute.defenceText().isBlank()) {
            throw new DefenceNotSavedException(disputeId);
        }

        ReviewResponse review = reviewService.getReview(disputeId);
        Map<UUID, EvidenceCitation> citationsById = review.evidenceCitations().stream()
                .collect(Collectors.toMap(EvidenceCitation::evidenceId, citation -> citation));

        // Sorted and de-duplicated so re-submitting the same set in a different order
        // never produces a spurious fingerprint mismatch.
        List<UUID> sortedIds = evidenceIds.stream().distinct().sorted().toList();
        List<EvidencePreviewItem> items = new ArrayList<>();
        for (UUID evidenceId : sortedIds) {
            // Not present among this dispute's own evidence citations - either it
            // doesn't exist or it belongs to a different dispute; collapse into 404.
            EvidenceCitation citation = citationsById.get(evidenceId);
            if (citation == null) {
                throw new EvidenceNotFoundException(evidenceId);
            }
            byte[] content = evidenceService.readAttachmentBytes(disputeId, evidenceId);
            items.add(new EvidencePreviewItem(evidenceId, citation.fileName(), citation.observationText(),
                    citation.documentedTransactionReference(), citation.referenceMatches(),
                    new String(content, StandardCharsets.UTF_8)));
        }

        String fingerprint = computeFingerprint(dispute, items);
        return new DefencePreview(disputeId, dispute, items, review.gaps(), review.scopeDisclaimer(), fingerprint);
    }

    private String computeFingerprint(DisputeResponse dispute, List<EvidencePreviewItem> items) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("dispute:").append(dispute.id()).append('\n');
        canonical.append("transactionReference:").append(dispute.transactionReference()).append('\n');
        canonical.append("amountMinor:").append(dispute.amountMinor()).append('\n');
        canonical.append("currency:").append(dispute.currency()).append('\n');
        canonical.append("description:").append(dispute.description()).append('\n');
        canonical.append("status:").append(dispute.status()).append('\n');
        canonical.append("providerDisputeReference:").append(dispute.providerDisputeReference()).append('\n');
        canonical.append("customerClaim:").append(dispute.customerClaim()).append('\n');
        canonical.append("responseDueAt:").append(dispute.responseDueAt()).append('\n');
        canonical.append("defenceText:").append(dispute.defenceText()).append('\n');
        for (EvidencePreviewItem item : items) {
            canonical.append("evidence:").append(item.evidenceId()).append('\n');
            canonical.append("fileName:").append(item.fileName()).append('\n');
            canonical.append("observationText:").append(item.observationText()).append('\n');
            canonical.append("documentedTransactionReference:").append(item.documentedTransactionReference())
                    .append('\n');
            canonical.append("attachmentContent:").append(item.attachmentContent()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
