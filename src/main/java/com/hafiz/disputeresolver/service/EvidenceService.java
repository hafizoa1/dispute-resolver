package com.hafiz.disputeresolver.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.hafiz.disputeresolver.dto.EvidenceFile;
import com.hafiz.disputeresolver.dto.EvidenceResponse;
import com.hafiz.disputeresolver.dto.SaveObservationRequest;
import com.hafiz.disputeresolver.exception.EvidenceNotFoundException;
import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class EvidenceService {

    // "Allow PDF/JPEG/PNG previews; download other types as attachments." - an
    // allowlist, not a block list: anything not explicitly known-safe to render
    // inline is forced to download instead, regardless of what content type was
    // recorded at upload time.
    private static final Set<String> PREVIEWABLE_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");

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

    @Transactional
    public EvidenceResponse saveObservation(UUID disputeId, UUID evidenceId, SaveObservationRequest request) {
        Evidence evidence = findEvidenceForDispute(disputeId, evidenceId);
        evidence.recordObservation(request.observationText(), request.documentedTransactionReference());
        return EvidenceResponse.from(evidenceRepository.saveAndFlush(evidence));
    }

    public EvidenceFile getFile(UUID disputeId, UUID evidenceId) {
        Evidence evidence = findEvidenceForDispute(disputeId, evidenceId);

        // Located by the stored dispute/evidence ids, never the original filename -
        // same reasoning as upload(): the id is what's trustworthy, the filename isn't.
        Path filePath = uploadDir.resolve(disputeId.toString()).resolve(evidenceId.toString());
        if (!Files.isRegularFile(filePath)) {
            // The DB row exists but the file on disk doesn't - same 404 as a row that
            // doesn't exist at all, per the "reuse the existing pattern" instruction.
            throw new EvidenceNotFoundException(evidenceId);
        }

        boolean previewable = evidence.getContentType() != null
                && PREVIEWABLE_CONTENT_TYPES.contains(evidence.getContentType().toLowerCase(Locale.ROOT));
        MediaType mediaType = previewable
                ? MediaType.parseMediaType(evidence.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        String downloadName = evidence.getFileName() == null ? evidenceId.toString() : evidence.getFileName();
        ContentDisposition contentDisposition = (previewable ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(downloadName, StandardCharsets.UTF_8)
                .build();

        return new EvidenceFile(new FileSystemResource(filePath), mediaType, contentDisposition);
    }

    // "Belongs to a different dispute" and "doesn't exist" collapse into the same
    // 404 - never confirms evidence exists on a dispute the caller didn't name.
    private Evidence findEvidenceForDispute(UUID disputeId, UUID evidenceId) {
        disputeService.findDispute(disputeId);
        return evidenceRepository.findById(evidenceId)
                .filter(found -> found.getDispute().getId().equals(disputeId))
                .orElseThrow(() -> new EvidenceNotFoundException(evidenceId));
    }
}
