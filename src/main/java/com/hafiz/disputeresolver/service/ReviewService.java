package com.hafiz.disputeresolver.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hafiz.disputeresolver.dto.CaseResponse;
import com.hafiz.disputeresolver.dto.EvidenceCitation;
import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.dto.PaymentEventSummary;
import com.hafiz.disputeresolver.dto.ReviewGap;
import com.hafiz.disputeresolver.dto.ReviewResponse;
import org.springframework.stereotype.Service;

// Reuses CaseService for everything - dispute/payment/evidence lookup, 404 on a missing
// dispute, 503 on an unconfigured Moniepoint business - all of it propagates unchanged
// since this never catches those exceptions. No new repository queries, no new tables.
@Service
public class ReviewService {

    // Unconditional - included on every response, gaps empty or not, so "no gaps" is
    // never read as "ready to submit" or "strong defence."
    private static final String SCOPE_DISCLAIMER = "This review checks completeness and declared "
            + "consistency only. It does not assess document contents or establish that the service "
            + "was delivered. No gaps found does not mean this defence is strong or ready to submit.";

    private final CaseService caseService;

    public ReviewService(CaseService caseService) {
        this.caseService = caseService;
    }

    public ReviewResponse getReview(UUID disputeId) {
        CaseResponse caseResponse = caseService.getCase(disputeId);

        List<EvidenceCitation> citations = caseResponse.evidence().stream()
                .map(evidence -> toCitation(evidence, caseResponse.dispute().transactionReference()))
                .toList();

        List<ReviewGap> gaps = computeGaps(caseResponse, citations);

        Map<UUID, String> fileNamesById = citations.stream()
                .collect(Collectors.toMap(EvidenceCitation::evidenceId, EvidenceCitation::fileName));

        String draftText = renderDraftText(caseResponse, citations, gaps, fileNamesById);

        return new ReviewResponse(caseResponse.dispute(), caseResponse.paymentEvents(),
                caseResponse.paymentEventConflict(), citations, gaps, SCOPE_DISCLAIMER, draftText);
    }

    private EvidenceCitation toCitation(EvidenceResponse evidence, String disputeTransactionReference) {
        String documentedReference = evidence.documentedTransactionReference();
        Boolean referenceMatches = documentedReference == null
                ? null
                : documentedReference.equals(disputeTransactionReference);
        return new EvidenceCitation(evidence.id(), evidence.fileName(), evidence.observationText(),
                documentedReference, referenceMatches);
    }

    private List<ReviewGap> computeGaps(CaseResponse caseResponse, List<EvidenceCitation> citations) {
        List<ReviewGap> gaps = new ArrayList<>();
        if (caseResponse.paymentEvents().isEmpty()) {
            gaps.add(new ReviewGap("No payment event found for this dispute's transaction reference.", null));
        }
        if (caseResponse.paymentEventConflict()) {
            gaps.add(new ReviewGap("Conflicting payment events were found for this transaction reference.", null));
        }
        if (citations.isEmpty()) {
            gaps.add(new ReviewGap("No evidence has been attached to this dispute.", null));
        }
        for (EvidenceCitation citation : citations) {
            if (citation.referenceMatches() == null) {
                gaps.add(new ReviewGap("No observation recorded for this evidence.", citation.evidenceId()));
            } else if (!citation.referenceMatches()) {
                gaps.add(new ReviewGap(
                        "Documented transaction reference does not match the dispute's reference.",
                        citation.evidenceId()));
            }
        }
        return gaps;
    }

    private String renderDraftText(CaseResponse caseResponse, List<EvidenceCitation> citations,
            List<ReviewGap> gaps, Map<UUID, String> fileNamesById) {
        String reference = caseResponse.dispute().transactionReference();
        StringBuilder text = new StringBuilder();

        text.append("DEFENCE REVIEW - ").append(reference).append('\n');
        String customerClaim = caseResponse.dispute().customerClaim();
        text.append("Customer claim: ")
                .append(customerClaim == null ? "not recorded." : '"' + customerClaim + '"')
                .append('\n');
        Instant responseDueAt = caseResponse.dispute().responseDueAt();
        text.append("Response due: ")
                .append(responseDueAt == null ? "not recorded - no deadline has been supplied by the provider."
                        : responseDueAt.toString())
                .append('\n');
        text.append(SCOPE_DISCLAIMER).append("\n\n");

        text.append("PAYMENT HISTORY\n");
        List<PaymentEventSummary> events = caseResponse.paymentEvents();
        if (events.isEmpty()) {
            text.append("No payment event found for this dispute's transaction reference.\n");
        } else {
            text.append("Provider-reported transaction ").append(reference).append(": ")
                    .append(events.size()).append(events.size() == 1 ? " event" : " events").append(" recorded");
            if (caseResponse.paymentEventConflict()) {
                text.append(", CONFLICTING");
            }
            text.append(".\n");
            for (PaymentEventSummary event : events) {
                text.append("- [event ").append(event.id()).append("] status ").append(event.transactionStatus())
                        .append(", reported amount ").append(event.amountRaw())
                        .append(" (raw provider figure, currency/unit interpretation not confirmed)")
                        .append(", received ").append(event.receivedAt()).append('\n');
            }
        }
        text.append('\n');

        text.append("EVIDENCE\n");
        if (citations.isEmpty()) {
            text.append("No evidence has been attached to this dispute.\n");
        } else {
            int index = 1;
            for (EvidenceCitation citation : citations) {
                String referenceStatus;
                if (citation.referenceMatches() == null) {
                    referenceStatus = "NO REFERENCE RECORDED";
                } else if (citation.referenceMatches()) {
                    referenceStatus = "MATCHES dispute reference";
                } else {
                    referenceStatus = "DOES NOT MATCH dispute reference";
                }
                text.append(index++).append(". ").append(citation.fileName())
                        .append(" [evidence ").append(citation.evidenceId()).append(']')
                        .append(" (documented reference: ")
                        .append(citation.documentedTransactionReference() == null
                                ? "none" : citation.documentedTransactionReference())
                        .append(" - ").append(referenceStatus).append(")\n");
                if (citation.observationText() == null) {
                    text.append("   No observation recorded.\n");
                } else {
                    text.append("   Merchant states: \"").append(citation.observationText()).append("\"\n");
                }
            }
        }
        text.append('\n');

        text.append("GAPS\n");
        if (gaps.isEmpty()) {
            text.append("None found: every attached evidence item has an observation, and every declared "
                    + "reference matches the dispute's own transaction reference.\n");
        } else {
            for (ReviewGap gap : gaps) {
                text.append("- ").append(gap.message());
                if (gap.evidenceId() != null) {
                    text.append(" (").append(fileNamesById.get(gap.evidenceId()))
                            .append(" [evidence ").append(gap.evidenceId()).append("])");
                }
                text.append('\n');
            }
        }

        return text.toString();
    }
}
