package com.hafiz.disputeresolver.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
import com.hafiz.disputeresolver.repository.DefenceApprovalRepository;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL configuration as the application; files go to a
// freshly created temp directory for this class only, cleaned up afterwards - same
// isolation style as EvidenceFileIntegrationTests. app.moniepoint.business-id is set
// because the preview/approve flow reuses ReviewService, which fails closed (503)
// without a configured business.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.moniepoint.business-id=123")
class DefenceApprovalControllerIntegrationTests {

    private static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) throws IOException {
        tempUploadDir = Files.createTempDirectory("defence-approval-test-");
        registry.add("app.evidence.upload-dir", () -> tempUploadDir.toString());
    }

    @AfterAll
    static void deleteTempUploadDir() throws IOException {
        if (tempUploadDir == null) {
            return;
        }
        try (var paths = Files.walk(tempUploadDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private DefenceApprovalRepository defenceApprovalRepository;
    @Value("${local.server.port}") private int port;

    @Test
    void approvingAPreviewSucceeds() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "We contest this chargeback because the meal was provided.");
        UUID evidenceId = uploadEvidenceWithObservation(dispute.getId(), "bill.txt", "Bill content, table 14.",
                "Shows table 14, seated 19:40.", dispute.getTransactionReference());

        JsonNode preview = preview(dispute.getId(), List.of(evidenceId));
        String fingerprint = preview.get("fingerprint").asString();
        assertThat(preview.get("dispute").get("defenceText").asString())
                .isEqualTo("We contest this chargeback because the meal was provided.");
        assertThat(preview.get("evidence").get(0).get("attachmentContent").asString())
                .isEqualTo("Bill content, table 14.");

        JsonNode approval = approve(dispute.getId(), List.of(evidenceId), fingerprint);

        assertThat(approval.get("disputeId").asString()).isEqualTo(dispute.getId().toString());
        assertThat(approval.get("fingerprint").asString()).isEqualTo(fingerprint);
        assertThat(approval.get("approvedAt").isNull()).isFalse();
        assertThat(approval.get("snapshot").get("dispute").get("defenceText").asString())
                .isEqualTo("We contest this chargeback because the meal was provided.");
    }

    @Test
    void changingTheDefenceAfterPreviewMakesApprovalReturn409() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "Original defence text.");

        JsonNode preview = preview(dispute.getId(), List.of());
        String staleFingerprint = preview.get("fingerprint").asString();

        saveDefence(dispute.getId(), "Revised defence text.");

        HttpResponse<String> response = send(approveRequest(dispute.getId(), List.of(), staleFingerprint));
        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void changingSelectedEvidenceAfterPreviewMakesApprovalReturn409() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "We contest this chargeback.");
        UUID evidence1 = uploadEvidenceWithObservation(dispute.getId(), "bill.txt", "Bill content.",
                "Shows the full bill.", dispute.getTransactionReference());

        JsonNode preview = preview(dispute.getId(), List.of(evidence1));
        String staleFingerprint = preview.get("fingerprint").asString();

        UUID evidence2 = uploadEvidenceWithObservation(dispute.getId(), "order-record.txt", "Order record content.",
                "Shows the order record.", dispute.getTransactionReference());

        HttpResponse<String> response = send(
                approveRequest(dispute.getId(), List.of(evidence1, evidence2), staleFingerprint));
        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void evidenceBelongingToAnotherDisputeIsRejected() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "We contest this chargeback.");
        Dispute otherDispute = createDispute();
        UUID otherEvidence = uploadEvidenceWithObservation(otherDispute.getId(), "bill.txt", "Other bill.",
                "Belongs to the other dispute.", otherDispute.getTransactionReference());

        HttpResponse<String> response = send(previewRequest(dispute.getId(), List.of(otherEvidence)));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void editingLiveRecordsAfterApprovalLeavesTheApprovedSnapshotUnchanged() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "Original defence text.");
        UUID evidenceId = uploadEvidenceWithObservation(dispute.getId(), "bill.txt", "Original attachment content.",
                "Original observation.", dispute.getTransactionReference());

        JsonNode preview = preview(dispute.getId(), List.of(evidenceId));
        String fingerprint = preview.get("fingerprint").asString();
        JsonNode approval = approve(dispute.getId(), List.of(evidenceId), fingerprint);
        UUID approvalId = UUID.fromString(approval.get("id").asString());

        saveDefence(dispute.getId(), "Edited after approval.");
        saveObservation(dispute.getId(), evidenceId, "Edited observation after approval.",
                dispute.getTransactionReference());

        JsonNode fetched = getApproval(dispute.getId(), approvalId);
        assertThat(fetched.get("snapshot").get("dispute").get("defenceText").asString())
                .isEqualTo("Original defence text.");
        assertThat(fetched.get("snapshot").get("evidence").get(0).get("observationText").asString())
                .isEqualTo("Original observation.");
        assertThat(fetched.get("fingerprint").asString()).isEqualTo(fingerprint);
    }

    @Test
    void missingAttachmentPreventsApprovalWithoutLeavingAPartialSnapshot() throws Exception {
        Dispute dispute = createDispute();
        saveDefence(dispute.getId(), "We contest this chargeback.");
        // A DB row with no corresponding file on disk - same fixture style as
        // EvidenceFileIntegrationTests.missingStoredFileIsNotFound.
        Evidence ghost = evidenceRepository.saveAndFlush(
                new Evidence(UUID.randomUUID(), dispute, "ghost.pdf", "application/pdf", 10L, "no file on disk"));

        HttpResponse<String> previewResponse = send(previewRequest(dispute.getId(), List.of(ghost.getId())));
        assertThat(previewResponse.statusCode()).isEqualTo(404);

        long approvalsBefore = defenceApprovalRepository.count();
        HttpResponse<String> approveResponse = send(
                approveRequest(dispute.getId(), List.of(ghost.getId()), "irrelevant-fingerprint"));
        assertThat(approveResponse.statusCode()).isEqualTo(404);
        assertThat(defenceApprovalRepository.count()).isEqualTo(approvalsBefore);
        assertThat(defenceApprovalRepository.findAll().stream()
                .filter(a -> a.getDisputeId().equals(dispute.getId()))
                .toList()).isEmpty();
    }

    @Test
    void previewWithoutASavedDefenceIsRejected() throws Exception {
        Dispute dispute = createDispute();

        HttpResponse<String> response = send(previewRequest(dispute.getId(), List.of()));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void previewForNonexistentDisputeReturns404() throws Exception {
        HttpResponse<String> response = send(previewRequest(UUID.randomUUID(), List.of()));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private Dispute createDispute() {
        return disputeRepository.saveAndFlush(
                new Dispute("defence-approval-test-ref-" + UUID.randomUUID(), 5000L, "NGN",
                        "integration test fixture"));
    }

    private void saveDefence(UUID disputeId, String defenceText) throws Exception {
        String json = """
                {"defenceText": %s}
                """.formatted(quote(defenceText));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/disputes/" + disputeId + "/defence"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void saveObservation(UUID disputeId, UUID evidenceId, String observationText, String reference)
            throws Exception {
        String json = """
                {"observationText": %s, "documentedTransactionReference": %s}
                """.formatted(quote(observationText), quote(reference));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/"
                        + disputeId + "/evidence/" + evidenceId + "/observation"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private UUID uploadEvidenceWithObservation(UUID disputeId, String fileName, String attachmentContent,
            String observationText, String reference) throws Exception {
        String boundary = "test-boundary-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, fileName, "text/plain",
                attachmentContent.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/disputes/" + disputeId + "/evidence"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(201);
        UUID evidenceId = UUID.fromString(mapper.readTree(response.body()).get("id").asString());
        saveObservation(disputeId, evidenceId, observationText, reference);
        return evidenceId;
    }

    private byte[] multipartBody(String boundary, String filename, String contentType, byte[] content)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private JsonNode preview(UUID disputeId, List<UUID> evidenceIds) throws Exception {
        HttpResponse<String> response = send(previewRequest(disputeId, evidenceIds));
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private JsonNode approve(UUID disputeId, List<UUID> evidenceIds, String fingerprint) throws Exception {
        HttpResponse<String> response = send(approveRequest(disputeId, evidenceIds, fingerprint));
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body());
    }

    private JsonNode getApproval(UUID disputeId, UUID approvalId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/"
                        + disputeId + "/defence/approvals/" + approvalId))
                .GET().build();
        HttpResponse<String> response = send(request);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private HttpRequest previewRequest(UUID disputeId, List<UUID> evidenceIds) {
        String json = """
                {"evidenceIds": %s}
                """.formatted(idArray(evidenceIds));
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/" + disputeId
                        + "/defence/preview"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private HttpRequest approveRequest(UUID disputeId, List<UUID> evidenceIds, String fingerprint) {
        String json = """
                {"evidenceIds": %s, "fingerprint": %s}
                """.formatted(idArray(evidenceIds), quote(fingerprint));
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/" + disputeId
                        + "/defence/approve"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private String idArray(List<UUID> ids) {
        return "[" + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]";
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
