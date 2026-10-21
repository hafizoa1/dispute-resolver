package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL/DB_* configuration as the application; no
// in-memory SQL substitute, matching the webhook integration tests' approach.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.moniepoint.business-id=123")
class CaseControllerIntegrationTests {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private JdbcTemplate jdbc;
    @Value("${local.server.port}") private int port;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM moniepoint_purchase_events WHERE webhook_id LIKE 'case-test-%'");
    }

    @Test
    void noMatchingPurchaseEventsIsEmptyNotMissing() throws Exception {
        Dispute dispute = createDispute("case-test-unmatched-ref");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).isEmpty();
        assertThat(body.get("paymentEventConflict").asBoolean()).isFalse();
    }

    @Test
    void statusHistoryForOnePaymentIsNotAConflict() throws Exception {
        Dispute dispute = createDispute("case-test-history-ref");
        insertEvent("case-test-h1", 123, dispute.getTransactionReference(), "booking-1", 5000, "PENDING");
        insertEvent("case-test-h2", 123, dispute.getTransactionReference(), "booking-1", 5000, "APPROVED");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).hasSize(2);
        assertThat(body.get("paymentEventConflict").asBoolean()).isFalse();
    }

    @Test
    void differingKnownAmountsAreAConflict() throws Exception {
        Dispute dispute = createDispute("case-test-conflict-ref");
        insertEvent("case-test-c1", 123, dispute.getTransactionReference(), "booking-1", 5000, "APPROVED");
        insertEvent("case-test-c2", 123, dispute.getTransactionReference(), "booking-1", 9000, "APPROVED");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).hasSize(2);
        assertThat(body.get("paymentEventConflict").asBoolean()).isTrue();
    }

    @Test
    void missingMerchantReferenceIsUnknownNotAConflict() throws Exception {
        Dispute dispute = createDispute("case-test-null-ref");
        insertEvent("case-test-n1", 123, dispute.getTransactionReference(), null, 5000, "PENDING");
        insertEvent("case-test-n2", 123, dispute.getTransactionReference(), "booking-1", 5000, "APPROVED");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).hasSize(2);
        assertThat(body.get("paymentEventConflict").asBoolean()).isFalse();
    }

    @Test
    void blankMerchantReferenceIsUnknownNotAConflict() throws Exception {
        Dispute dispute = createDispute("case-test-blank-ref");
        insertEvent("case-test-b1", 123, dispute.getTransactionReference(), "", 5000, "PENDING");
        insertEvent("case-test-b2", 123, dispute.getTransactionReference(), "booking-123", 5000, "APPROVED");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).hasSize(2);
        assertThat(body.get("paymentEventConflict").asBoolean()).isFalse();
    }

    @Test
    void eventsUnderAnotherBusinessAreExcluded() throws Exception {
        Dispute dispute = createDispute("case-test-cross-business-ref");
        insertEvent("case-test-x1", 456, dispute.getTransactionReference(), "booking-1", 5000, "APPROVED");

        JsonNode body = getCase(dispute.getId());

        assertThat(body.get("paymentEvents")).isEmpty();
    }

    @Test
    void missingDisputeReturns404() throws Exception {
        HttpResponse<String> response = send(UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private Dispute createDispute(String transactionReference) {
        return disputeRepository.saveAndFlush(
                new Dispute(transactionReference, 5000L, "GBP", "integration test fixture"));
    }

    private void insertEvent(String webhookId, long businessId, String transactionReference,
            String merchantReference, long amountRaw, String status) {
        jdbc.update("""
                INSERT INTO moniepoint_purchase_events
                    (id, webhook_id, provider_event_id, business_id, transaction_reference,
                     merchant_reference, amount_raw, transaction_status, response_code,
                     raw_payload, payload_sha256, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), webhookId, webhookId, businessId, transactionReference, merchantReference,
                amountRaw, status, "00", "{}", "sha", Timestamp.from(Instant.now()));
    }

    private JsonNode getCase(UUID disputeId) throws Exception {
        HttpResponse<String> response = send(disputeId);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> send(UUID disputeId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/disputes/" + disputeId + "/case")).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
