package com.hafiz.disputeresolver.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    private final EvidenceRepository evidenceRepository;
    private final DisputeService disputeService;
    private final Path uploadDir;

    public EvidenceService(EvidenceRepository evidenceRepository, DisputeService disputeService,
            @Value("${app.evidence.upload-dir}") String uploadDir) {
        this.evidenceRepository = evidenceRepository;
        this.disputeService = disputeService;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public EvidenceResponse upload(UUID disputeId, MultipartFile file, String description) {
        Dispute dispute = disputeService.findDispute(disputeId);
        UUID evidenceId = UUID.randomUUID();
        // Stored under the generated id, never the client-supplied filename, to avoid path traversal.
        Path target = uploadDir.resolve(disputeId.toString()).resolve(evidenceId.toString());
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store evidence file", e);
        }
        Evidence evidence = new Evidence(evidenceId, dispute, file.getOriginalFilename(), file.getContentType(),
                file.getSize(), description);
        return EvidenceResponse.from(evidenceRepository.saveAndFlush(evidence));
    }

    public List<EvidenceResponse> listForDispute(UUID disputeId) {
        disputeService.findDispute(disputeId);
        return evidenceRepository.findByDisputeIdOrderByUploadedAtAsc(disputeId).stream()
                .map(EvidenceResponse::from)
                .toList();
    }
}
