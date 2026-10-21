package com.hafiz.disputeresolver.service;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.CaseResponse;
import com.hafiz.disputeresolver.dto.DisputeResponse;
import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.dto.PaymentEventSummary;
import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.moniepoint.MoniepointPurchaseEventQueryRepository;
import com.hafiz.disputeresolver.moniepoint.MoniepointPurchaseEventRecord;
import com.hafiz.disputeresolver.moniepoint.WebhookException;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CaseService {

    private final DisputeService disputeService;
    private final MoniepointPurchaseEventQueryRepository purchaseEventRepository;
    private final EvidenceRepository evidenceRepository;
    private final String businessId;

    public CaseService(DisputeService disputeService, MoniepointPurchaseEventQueryRepository purchaseEventRepository,
            EvidenceRepository evidenceRepository, @Value("${app.moniepoint.business-id:}") String businessId) {
        this.disputeService = disputeService;
        this.purchaseEventRepository = purchaseEventRepository;
        this.evidenceRepository = evidenceRepository;
        this.businessId = businessId;
    }

    public CaseResponse getCase(UUID disputeId) {
        if (businessId.isBlank()) {
            throw new WebhookException(HttpStatus.SERVICE_UNAVAILABLE, "Moniepoint business is not configured");
        }

        Dispute dispute = disputeService.findDispute(disputeId);

        List<MoniepointPurchaseEventRecord> events = purchaseEventRepository
                .findByBusinessIdAndTransactionReferenceOrderByReceivedAtAsc(Long.parseLong(businessId),
                        dispute.getTransactionReference());

        List<EvidenceResponse> evidence = evidenceRepository.findByDisputeIdOrderByUploadedAtAsc(disputeId).stream()
                .map(EvidenceResponse::from)
                .toList();

        return new CaseResponse(DisputeResponse.from(dispute),
                events.stream().map(PaymentEventSummary::from).toList(), hasConflict(events), evidence);
    }

    // A conflict is two events disagreeing on a field they both actually have a
    // value for. amount_raw is NOT NULL in the schema, so it's always a known-vs-
    // known comparison; merchant_reference is nullable, so a missing value on
    // either side is unknown and never counted as a mismatch.
    private boolean hasConflict(List<MoniepointPurchaseEventRecord> events) {
        for (int i = 0; i < events.size(); i++) {
            for (int j = i + 1; j < events.size(); j++) {
                if (disagree(events.get(i), events.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean disagree(MoniepointPurchaseEventRecord a, MoniepointPurchaseEventRecord b) {
        if (a.getAmountRaw() != b.getAmountRaw()) {
            return true;
        }
        String merchantRefA = a.getMerchantReference();
        String merchantRefB = b.getMerchantReference();
        return merchantRefA != null && merchantRefB != null && !merchantRefA.equals(merchantRefB);
    }
}
