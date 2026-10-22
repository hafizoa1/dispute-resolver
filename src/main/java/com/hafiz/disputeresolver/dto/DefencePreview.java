package com.hafiz.disputeresolver.dto;

import java.util.List;
import java.util.UUID;

// fingerprint identifies exactly this content: the dispute's current fields (including
// defenceText) plus the selected evidence items (including attachment bytes, decoded as
// text). Approval must resupply this fingerprint; a mismatch means something in that
// content changed since this preview was built, and the caller must request a fresh
// preview rather than approve stale content.
// warnings/scopeDisclaimer are the dispute's existing review output, shown alongside the
// preview unconditionally - kept out of the fingerprint deliberately, since approval is a
// decision about the content being submitted, not about the computed gaps, and must
// never be read as the system having cleared those warnings or verified delivery or
// authenticity.
public record DefencePreview(UUID disputeId, DisputeResponse dispute, List<EvidencePreviewItem> evidence,
        List<ReviewGap> warnings, String scopeDisclaimer, String fingerprint) {
}
