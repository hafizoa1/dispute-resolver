package com.hafiz.disputeresolver.controller;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.service.EvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/disputes/{disputeId}/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EvidenceResponse upload(@PathVariable UUID disputeId, @RequestParam MultipartFile file,
            @RequestParam(required = false) String description) {
        return evidenceService.upload(disputeId, file, description);
    }

    @GetMapping
    public List<EvidenceResponse> list(@PathVariable UUID disputeId) {
        return evidenceService.listForDispute(disputeId);
    }
}
