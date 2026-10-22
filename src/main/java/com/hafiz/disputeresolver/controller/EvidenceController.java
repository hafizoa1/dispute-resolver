package com.hafiz.disputeresolver.controller;

import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.EvidenceFile;
import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.dto.SaveObservationRequest;
import com.hafiz.disputeresolver.service.EvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PutMapping("/{evidenceId}/observation")
    public EvidenceResponse saveObservation(@PathVariable UUID disputeId, @PathVariable UUID evidenceId,
            @Valid @RequestBody SaveObservationRequest request) {
        return evidenceService.saveObservation(disputeId, evidenceId, request);
    }

    @GetMapping("/{evidenceId}/file")
    public ResponseEntity<Resource> getFile(@PathVariable UUID disputeId, @PathVariable UUID evidenceId) {
        EvidenceFile file = evidenceService.getFile(disputeId, evidenceId);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, file.contentDisposition().toString())
                .body(file.resource());
    }
}
