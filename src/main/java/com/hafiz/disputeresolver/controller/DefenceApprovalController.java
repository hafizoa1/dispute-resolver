package com.hafiz.disputeresolver.controller;

import java.util.UUID;

import com.hafiz.disputeresolver.dto.DefenceApprovalRequest;
import com.hafiz.disputeresolver.dto.DefenceApprovalResponse;
import com.hafiz.disputeresolver.dto.DefencePreview;
import com.hafiz.disputeresolver.dto.DefencePreviewRequest;
import com.hafiz.disputeresolver.service.DefenceApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/disputes/{id}/defence")
@RequiredArgsConstructor
public class DefenceApprovalController {

    private final DefenceApprovalService defenceApprovalService;

    @PostMapping("/preview")
    public DefencePreview preview(@PathVariable UUID id, @Valid @RequestBody DefencePreviewRequest request) {
        return defenceApprovalService.preview(id, request.evidenceIds());
    }

    @PostMapping("/approve")
    @ResponseStatus(HttpStatus.CREATED)
    public DefenceApprovalResponse approve(@PathVariable UUID id,
            @Valid @RequestBody DefenceApprovalRequest request) {
        return defenceApprovalService.approve(id, request.evidenceIds(), request.fingerprint());
    }

    @GetMapping("/approvals/{approvalId}")
    public DefenceApprovalResponse getApproval(@PathVariable UUID id, @PathVariable UUID approvalId) {
        return defenceApprovalService.getApproval(id, approvalId);
    }
}
