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
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
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
// freshly created temp directory for this class only, cleaned up afterwards, so this
// never touches the real app.evidence.upload-dir.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvidenceFileIntegrationTests {

    private static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) throws IOException {
        tempUploadDir = Files.createTempDirectory("evidence-file-test-");
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
    @Value("${local.server.port}") private int port;

    @Test
    void uploadedFileRoundTripsExactBytesWithPreviewHeaders() throws Exception {
        Dispute dispute = createDispute();
        byte[] content = "%PDF-1.4 synthetic test content, not a real PDF structure".getBytes(StandardCharsets.UTF_8);

        JsonNode uploadResponse = upload(dispute.getId(), "statement.pdf", "application/pdf", content,
                "test receipt");
        UUID evidenceId = UUID.fromString(uploadResponse.get("id").asString());

        HttpResponse<byte[]> response = getFile(dispute.getId(), evidenceId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).isEqualTo("application/pdf");
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("inline").contains("statement.pdf");
    }

    @Test
    void nonPreviewableTypeDownloadsAsAttachmentWithOctetStream() throws Exception {
        Dispute dispute = createDispute();
        byte[] content = "plain text evidence content".getBytes(StandardCharsets.UTF_8);

        JsonNode uploadResponse = upload(dispute.getId(), "notes.txt", "text/plain", content, "test notes");
        UUID evidenceId = UUID.fromString(uploadResponse.get("id").asString());

        HttpResponse<byte[]> response = getFile(dispute.getId(), evidenceId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .isEqualTo("application/octet-stream");
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment").contains("notes.txt");
    }

    @Test
    void evidenceFromAnotherDisputeIsNotFound() throws Exception {
        Dispute dispute = createDispute();
        JsonNode uploadResponse = upload(dispute.getId(), "x.txt", "text/plain",
                "x".getBytes(StandardCharsets.UTF_8), null);
        UUID evidenceId = UUID.fromString(uploadResponse.get("id").asString());
        Dispute otherDispute = createDispute();

        HttpResponse<byte[]> response = getFile(otherDispute.getId(), evidenceId);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void nonexistentEvidenceIsNotFound() throws Exception {
        Dispute dispute = createDispute();

        HttpResponse<byte[]> response = getFile(dispute.getId(), UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void nonexistentDisputeIsNotFound() throws Exception {
        JsonNode uploadResponse = upload(createDispute().getId(), "x.txt", "text/plain",
                "x".getBytes(StandardCharsets.UTF_8), null);
        UUID evidenceId = UUID.fromString(uploadResponse.get("id").asString());

        HttpResponse<byte[]> response = getFile(UUID.randomUUID(), evidenceId);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void missingStoredFileIsNotFound() throws Exception {
        // A DB row with no corresponding file on disk - e.g. the file was lost or
        // never written - must 404 the same way a missing row does, not throw.
        Dispute dispute = createDispute();
        Evidence evidence = evidenceRepository.saveAndFlush(
                new Evidence(UUID.randomUUID(), dispute, "ghost.pdf", "application/pdf", 10L, "no file on disk"));

        HttpResponse<byte[]> response = getFile(dispute.getId(), evidence.getId());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private Dispute createDispute() {
        return disputeRepository.saveAndFlush(
                new Dispute("file-test-ref-" + UUID.randomUUID(), 5000L, "NGN", "integration test fixture"));
    }

    private JsonNode upload(UUID disputeId, String filename, String contentType, byte[] content, String description)
            throws Exception {
        String boundary = "test-boundary-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, filename, contentType, content, description);
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/disputes/" + disputeId + "/evidence"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body());
    }

    private byte[] multipartBody(String boundary, String filename, String contentType, byte[] content,
            String description) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if (description != null) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Disposition: form-data; name=\"description\"\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.write(description.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<byte[]> getFile(UUID disputeId, UUID evidenceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/"
                        + disputeId + "/evidence/" + evidenceId + "/file"))
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
