package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL configuration as the application; no mocks,
// matching the case-view and webhook integration tests' approach.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvidenceObservationIntegrationTests {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Value("${local.server.port}") private int port;

    @Test
    void savesAndReturnsTheObservation() throws Exception {
        Evidence evidence = createEvidence(createDispute());

        JsonNode body = putObservation(evidence.getDispute().getId(), evidence.getId(),
                "Shows table 14, 2 covers, seated 19:40.", "RST-20261015-0077");

        assertThat(body.get("observationText").asString())
                .isEqualTo("Shows table 14, 2 covers, seated 19:40.");
        assertThat(body.get("documentedTransactionReference").asString()).isEqualTo("RST-20261015-0077");
        assertThat(body.get("id").asString()).isEqualTo(evidence.getId().toString());
    }

    @Test
    void updatingOverwritesThePreviousObservation() throws Exception {
        Evidence evidence = createEvidence(createDispute());
        putObservation(evidence.getDispute().getId(), evidence.getId(), "First pass.", "RST-A");

        JsonNode body = putObservation(evidence.getDispute().getId(), evidence.getId(),
                "Corrected observation.", "RST-B");

        assertThat(body.get("observationText").asString()).isEqualTo("Corrected observation.");
        assertThat(body.get("documentedTransactionReference").asString()).isEqualTo("RST-B");
    }

    @Test
    void blankObservationTextIsRejected() throws Exception {
        Evidence evidence = createEvidence(createDispute());

        HttpResponse<String> response = send(evidence.getDispute().getId(), evidence.getId(), "", "RST-A");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void blankDocumentedTransactionReferenceIsRejected() throws Exception {
        Evidence evidence = createEvidence(createDispute());

        HttpResponse<String> response = send(evidence.getDispute().getId(), evidence.getId(), "Some text", "");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void evidenceFromAnotherDisputeIsNotFound() throws Exception {
        Evidence evidence = createEvidence(createDispute());
        Dispute otherDispute = createDispute();

        HttpResponse<String> response = send(otherDispute.getId(), evidence.getId(), "text", "RST-A");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void nonexistentEvidenceIsNotFound() throws Exception {
        Dispute dispute = createDispute();

        HttpResponse<String> response = send(dispute.getId(), UUID.randomUUID(), "text", "RST-A");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void nonexistentDisputeIsNotFound() throws Exception {
        Evidence evidence = createEvidence(createDispute());

        HttpResponse<String> response = send(UUID.randomUUID(), evidence.getId(), "text", "RST-A");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void evidenceWithoutAnObservationStillListsCleanly() throws Exception {
        Dispute dispute = createDispute();
        createEvidence(dispute);

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/disputes/" + dispute.getId() + "/evidence"))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode list = mapper.readTree(response.body());
        assertThat(list.get(0).get("observationText").isNull()).isTrue();
        assertThat(list.get(0).get("documentedTransactionReference").isNull()).isTrue();
    }

    private Dispute createDispute() {
        return disputeRepository.saveAndFlush(
                new Dispute("case-test-observation-ref-" + UUID.randomUUID(), 5000L, "NGN", "integration test fixture"));
    }

    private Evidence createEvidence(Dispute dispute) {
        return evidenceRepository.saveAndFlush(
                new Evidence(UUID.randomUUID(), dispute, "bill.txt", "text/plain", 100L, "test fixture"));
    }

    private JsonNode putObservation(UUID disputeId, UUID evidenceId, String text, String reference) throws Exception {
        HttpResponse<String> response = send(disputeId, evidenceId, text, reference);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> send(UUID disputeId, UUID evidenceId, String text, String reference)
            throws Exception {
        String json = """
                {"observationText": %s, "documentedTransactionReference": %s}
                """.formatted(quote(text), quote(reference));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/"
                        + disputeId + "/evidence/" + evidenceId + "/observation"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
