package com.hafiz.disputeresolver.service;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.CreateDisputeRequest;
import com.hafiz.disputeresolver.dto.DisputeResponse;
import com.hafiz.disputeresolver.dto.UpdateDisputeRequest;
import com.hafiz.disputeresolver.exception.DisputeNotFoundException;
import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.DisputeStatus;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisputeService {

    private final DisputeRepository disputeRepository;

    @Transactional
    public DisputeResponse create(CreateDisputeRequest request) {
        Dispute dispute = new Dispute(request.transactionReference(), request.amountMinor(), request.currency(),
                request.description());
        dispute.recordProviderCaseDetails(request.providerDisputeReference(), request.customerClaim(),
                request.responseDueAt());
        return DisputeResponse.from(disputeRepository.saveAndFlush(dispute));
    }

    public DisputeResponse getById(UUID id) {
        return DisputeResponse.from(findDispute(id));
    }

    public List<DisputeResponse> list(DisputeStatus status) {
        List<Dispute> disputes = status == null
                ? disputeRepository.findAllByOrderByCreatedAtDesc()
                : disputeRepository.findByStatusOrderByCreatedAtDesc(status);
        return disputes.stream().map(DisputeResponse::from).toList();
    }

    @Transactional
    public DisputeResponse update(UUID id, UpdateDisputeRequest request) {
        Dispute dispute = findDispute(id);
        dispute.correctDetails(request.transactionReference(), request.amountMinor(), request.currency(),
                request.description());
        dispute.recordProviderCaseDetails(request.providerDisputeReference(), request.customerClaim(),
                request.responseDueAt());
        return DisputeResponse.from(disputeRepository.saveAndFlush(dispute));
    }

    Dispute findDispute(UUID id) {
        return disputeRepository.findById(id).orElseThrow(() -> new DisputeNotFoundException(id));
    }
}
