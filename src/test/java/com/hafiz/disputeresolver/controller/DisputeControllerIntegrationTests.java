package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL configuration as the application; no mocks,
// matching every other integration test in this suite. No dedicated test class for
// DisputeController existed before this - disputes were only ever created via direct
// repository fixtures elsewhere, never through the real create/update endpoints.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DisputeControllerIntegrationTests {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Value("${local.server.port}") private int port;

    @Test
    void createWithProviderCaseDetailsPersistsAndReturnsThemThroughASeparateGet() throws Exception {
        Instant dueAt = Instant.parse("2026-11-15T12:00:00Z");
        JsonNode created = create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture", "providerDisputeReference": "MPT-CASE-1",
                 "customerClaim": "Customer says the meal was never served.", "responseDueAt": "%s"}
                """.formatted(newReference(), dueAt));

        assertThat(created.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-1");
        assertThat(created.get("customerClaim").asString()).isEqualTo("Customer says the meal was never served.");
        assertThat(created.get("responseDueAt").asString()).isEqualTo(dueAt.toString());

        JsonNode fetched = getById(idOf(created));
        assertThat(fetched.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-1");
        assertThat(fetched.get("customerClaim").asString()).isEqualTo("Customer says the meal was never served.");
        assertThat(fetched.get("responseDueAt").asString()).isEqualTo(dueAt.toString());
    }

    @Test
    void createWithoutProviderCaseDetailsLeavesThemNull() throws Exception {
        // Shaped exactly like a request predating this change - no new fields at all.
        JsonNode created = create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture"}
                """.formatted(newReference()));

        assertThat(created.get("providerDisputeReference").isNull()).isTrue();
        assertThat(created.get("customerClaim").isNull()).isTrue();
        assertThat(created.get("responseDueAt").isNull()).isTrue();

        JsonNode fetched = getById(idOf(created));
        assertThat(fetched.get("providerDisputeReference").isNull()).isTrue();
        assertThat(fetched.get("customerClaim").isNull()).isTrue();
        assertThat(fetched.get("responseDueAt").isNull()).isTrue();
    }

    @Test
    void existingFourFieldCreateRequestStillWorks() throws Exception {
        String reference = newReference();
        HttpResponse<String> response = send(postRequest("""
                {"transactionReference": "%s", "amountMinor": 2500, "currency": "GBP",
                 "description": "pre-existing request shape"}
                """.formatted(reference)));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("transactionReference").asString()).isEqualTo(reference);
        assertThat(body.get("currency").asString()).isEqualTo("GBP");
        assertThat(body.get("description").asString()).isEqualTo("pre-existing request shape");
    }

    @Test
    void updateSetsProviderCaseDetailsAndPersistsThroughASeparateGet() throws Exception {
        UUID id = idOf(create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture"}
                """.formatted(newReference())));

        Instant dueAt = Instant.parse("2026-12-01T00:00:00Z");
        JsonNode updated = update(id, """
                {"providerDisputeReference": "MPT-CASE-2", "customerClaim": "Claims non-delivery.",
                 "responseDueAt": "%s"}
                """.formatted(dueAt));

        assertThat(updated.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-2");
        assertThat(updated.get("customerClaim").asString()).isEqualTo("Claims non-delivery.");
        assertThat(updated.get("responseDueAt").asString()).isEqualTo(dueAt.toString());

        JsonNode fetched = getById(id);
        assertThat(fetched.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-2");
        assertThat(fetched.get("customerClaim").asString()).isEqualTo("Claims non-delivery.");
        assertThat(fetched.get("responseDueAt").asString()).isEqualTo(dueAt.toString());
    }

    @Test
    void updateWithOnlyOneOfTheNewFieldsLeavesTheOthersUnchanged() throws Exception {
        UUID id = idOf(create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture", "providerDisputeReference": "MPT-CASE-3",
                 "customerClaim": "Original claim.", "responseDueAt": "2026-11-01T00:00:00Z"}
                """.formatted(newReference())));

        JsonNode updated = update(id, """
                {"customerClaim": "Revised claim."}
                """);

        assertThat(updated.get("customerClaim").asString()).isEqualTo("Revised claim.");
        assertThat(updated.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-3");
        assertThat(updated.get("responseDueAt").asString()).isEqualTo("2026-11-01T00:00:00Z");

        JsonNode fetched = getById(id);
        assertThat(fetched.get("customerClaim").asString()).isEqualTo("Revised claim.");
        assertThat(fetched.get("providerDisputeReference").asString()).isEqualTo("MPT-CASE-3");
        assertThat(fetched.get("responseDueAt").asString()).isEqualTo("2026-11-01T00:00:00Z");
    }

    @Test
    void blankProviderDisputeReferenceOnUpdateIsRejected() throws Exception {
        UUID id = idOf(create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture"}
                """.formatted(newReference())));

        HttpResponse<String> response = send(patchRequest(id, """
                {"providerDisputeReference": ""}
                """));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void blankCustomerClaimOnUpdateIsRejected() throws Exception {
        UUID id = idOf(create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture"}
                """.formatted(newReference())));

        HttpResponse<String> response = send(patchRequest(id, """
                {"customerClaim": "   "}
                """));

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void existingUpdateRequestWithoutNewFieldsStillWorks() throws Exception {
        UUID id = idOf(create("""
                {"transactionReference": "%s", "amountMinor": 5000, "currency": "NGN",
                 "description": "integration test fixture"}
                """.formatted(newReference())));

        JsonNode updated = update(id, """
                {"description": "revised description"}
                """);

        assertThat(updated.get("description").asString()).isEqualTo("revised description");
        assertThat(updated.get("providerDisputeReference").isNull()).isTrue();
        assertThat(updated.get("customerClaim").isNull()).isTrue();
        assertThat(updated.get("responseDueAt").isNull()).isTrue();
    }

    private String newReference() {
        return "dispute-test-ref-" + UUID.randomUUID();
    }

    private UUID idOf(JsonNode disputeResponse) {
        return UUID.fromString(disputeResponse.get("id").asString());
    }

    private JsonNode create(String json) throws Exception {
        HttpResponse<String> response = send(postRequest(json));
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body());
    }

    private JsonNode update(UUID id, String json) throws Exception {
        HttpResponse<String> response = send(patchRequest(id, json));
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private JsonNode getById(UUID id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/" + id))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private HttpRequest postRequest(String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private HttpRequest patchRequest(UUID id, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/disputes/" + id))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
