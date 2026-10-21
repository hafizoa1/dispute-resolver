package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.model.Evidence;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import com.hafiz.disputeresolver.repository.EvidenceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL configuration as the application; no mocks,
// matching every other integration test in this suite.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.moniepoint.business-id=123")
class ReviewControllerIntegrationTests {

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private DisputeRepository disputeRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private JdbcTemplate jdbc;
    @Value("${local.server.port}") private int port;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM moniepoint_purchase_events WHERE webhook_id LIKE 'review-test-%'");
    }

    @Test
    void observedEvidenceIsCitedVerbatimAndFlaggedAsMatching() throws Exception {
        Dispute dispute = createDispute("review-test-observed-ref");
        insertEvent("review-test-observed-1", dispute.getTransactionReference(), "booking-1", 5000);
        String originalText = "Order record shows table 14, seated 19:40, cleared 21:05.";
        createObservedEvidence(dispute, "order-record.txt", originalText, dispute.getTransactionReference());

        JsonNode body = getReview(dispute.getId());

        JsonNode citation = body.get("evidenceCitations").get(0);
        assertThat(citation.get("observationText").asString()).isEqualTo(originalText);
        assertThat(citation.get("referenceMatches").asBoolean()).isTrue();
        // Verbatim, not paraphrased: the exact original sentence must appear unchanged
        // in the rendered draft text, proving nothing rewrote it into a new claim.
        assertThat(body.get("draftText").asString()).contains("\"" + originalText + "\"");
        assertThat(body.get("gaps")).isEmpty();
    }

    @Test
    void unobservedEvidenceHasNullReferenceMatchAndItsOwnGap() throws Exception {
        Dispute dispute = createDispute("review-test-unobserved-ref");
        insertEvent("review-test-unobserved-1", dispute.getTransactionReference(), "booking-1", 5000);
        Evidence evidence = createEvidence(dispute, "receipt.txt");

        JsonNode body = getReview(dispute.getId());

        JsonNode citation = body.get("evidenceCitations").get(0);
        assertThat(citation.get("observationText").isNull()).isTrue();
        assertThat(citation.get("referenceMatches").isNull()).isTrue();

        JsonNode gaps = body.get("gaps");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).get("message").asString()).isEqualTo("No observation recorded for this evidence.");
        assertThat(gaps.get(0).get("evidenceId").asString()).isEqualTo(evidence.getId().toString());
        assertThat(body.get("draftText").asString()).contains("No observation recorded.");
    }

    @Test
    void mismatchedReferenceIsFlaggedFalseNotSilentlyAccepted() throws Exception {
        Dispute dispute = createDispute("review-test-mismatch-ref");
        insertEvent("review-test-mismatch-1", dispute.getTransactionReference(), "booking-1", 5000);
        createObservedEvidence(dispute, "wrong-booking.txt", "Shows a different booking entirely.",
                "SOME-OTHER-REFERENCE");

        JsonNode body = getReview(dispute.getId());

        JsonNode citation = body.get("evidenceCitations").get(0);
        assertThat(citation.get("referenceMatches").asBoolean()).isFalse();

        JsonNode gaps = body.get("gaps");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).get("message").asString())
                .isEqualTo("Documented transaction reference does not match the dispute's reference.");
        String draftText = body.get("draftText").asString();
        assertThat(draftText).contains("DOES NOT MATCH dispute reference");
        assertNoUnsupportedDeliveryClaims(draftText);
    }

    @Test
    void conflictingPaymentEventsProduceAGap() throws Exception {
        Dispute dispute = createDispute("review-test-conflict-ref");
        insertEvent("review-test-c1", dispute.getTransactionReference(), "booking-1", 5000);
        insertEvent("review-test-c2", dispute.getTransactionReference(), "booking-1", 9000);

        JsonNode body = getReview(dispute.getId());

        assertThat(body.get("paymentEventConflict").asBoolean()).isTrue();
        JsonNode gaps = body.get("gaps");
        boolean hasConflictGap = false;
        for (JsonNode gap : gaps) {
            if (gap.get("message").asString().equals("Conflicting payment events were found for this transaction reference.")) {
                hasConflictGap = true;
            }
        }
        assertThat(hasConflictGap).isTrue();
        String draftText = body.get("draftText").asString();
        assertThat(draftText).contains("CONFLICTING");
        // Both conflicting events must be shown, not just one picked as "the" status.
        assertThat(draftText).contains("5000");
        assertThat(draftText).contains("9000");
        assertNoUnsupportedDeliveryClaims(draftText);
    }

    @Test
    void missingEvidenceIsVisibleAndDraftMakesNoUnsupportedClaims() throws Exception {
        Dispute dispute = createDispute("review-test-no-evidence-ref");
        insertEvent("review-test-no-evidence-1", dispute.getTransactionReference(), "booking-1", 5000);
        // Deliberately no evidence attached at all.

        JsonNode body = getReview(dispute.getId());

        assertThat(body.get("evidenceCitations")).isEmpty();
        JsonNode gaps = body.get("gaps");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).get("message").asString()).isEqualTo("No evidence has been attached to this dispute.");
        assertThat(gaps.get(0).get("evidenceId").isNull()).isTrue();

        String draftText = body.get("draftText").asString();
        assertThat(draftText).contains("No evidence has been attached to this dispute.");
        assertNoUnsupportedDeliveryClaims(draftText);
    }

    // The template's own fixed wording must never affirmatively claim delivery, proof, or
    // strength - chosen to avoid any substring collision with the scope disclaimer's own
    // (negated) phrasing, which is present on every response and does contain words like
    // "delivered"/"strong"/"ready to submit" inside a sentence that explicitly says the
    // opposite.
    private void assertNoUnsupportedDeliveryClaims(String draftText) {
        assertThat(draftText.toLowerCase()).doesNotContain("this proves")
                .doesNotContain("this confirms")
                .doesNotContain("clearly establishes")
                .doesNotContain("service has been confirmed")
                .doesNotContain("delivery is confirmed")
                .doesNotContain("proves delivery")
                .doesNotContain("confirms delivery");
    }

    @Test
    void duplicateFilenamesStayDisambiguatedByIdInDraftText() throws Exception {
        Dispute dispute = createDispute("review-test-duplicate-filename-ref");
        insertEvent("review-test-dup-1", dispute.getTransactionReference(), "booking-1", 5000);
        Evidence first = createObservedEvidence(dispute, "receipt.pdf", "First receipt - the correct one.",
                dispute.getTransactionReference());
        Evidence second = createEvidence(dispute, "receipt.pdf");

        JsonNode body = getReview(dispute.getId());
        String draftText = body.get("draftText").asString();

        // Same filename twice is exactly the ambiguous case: the draft must still let a
        // reader tell the two apart by id, and the payment event's own id must also appear.
        assertThat(draftText).contains(first.getId().toString());
        assertThat(draftText).contains(second.getId().toString());
        assertThat(draftText).contains(body.get("paymentEvents").get(0).get("id").asString());
    }

    @Test
    void noGapsStillCarriesTheScopeDisclaimer() throws Exception {
        Dispute dispute = createDispute("review-test-clean-ref");
        insertEvent("review-test-clean-1", dispute.getTransactionReference(), "booking-1", 5000);
        createObservedEvidence(dispute, "bill.txt", "Clean matching record.", dispute.getTransactionReference());

        JsonNode body = getReview(dispute.getId());

        assertThat(body.get("gaps")).isEmpty();
        assertThat(body.get("scopeDisclaimer").asString()).contains("does not mean this defence is strong");
        assertThat(body.get("draftText").asString()).contains("does not mean this defence is strong");
    }

    @Test
    void missingDisputeReturns404() throws Exception {
        HttpResponse<String> response = send(UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private Dispute createDispute(String transactionReference) {
        return disputeRepository.saveAndFlush(
                new Dispute(transactionReference, 5000L, "NGN", "integration test fixture"));
    }

    private Evidence createEvidence(Dispute dispute, String fileName) {
        return evidenceRepository.saveAndFlush(
                new Evidence(UUID.randomUUID(), dispute, fileName, "text/plain", 100L, "test fixture"));
    }

    private Evidence createObservedEvidence(Dispute dispute, String fileName, String observationText,
            String documentedTransactionReference) {
        Evidence evidence = createEvidence(dispute, fileName);
        evidence.recordObservation(observationText, documentedTransactionReference);
        return evidenceRepository.saveAndFlush(evidence);
    }

    private void insertEvent(String webhookId, String transactionReference, String merchantReference,
            long amountRaw) {
        jdbc.update("""
                INSERT INTO moniepoint_purchase_events
                    (id, webhook_id, provider_event_id, business_id, transaction_reference,
                     merchant_reference, amount_raw, transaction_status, response_code,
                     raw_payload, payload_sha256, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), webhookId, webhookId, 123L, transactionReference, merchantReference,
                amountRaw, "APPROVED", "00", "{}", "sha", Timestamp.from(Instant.now()));
    }

    private JsonNode getReview(UUID disputeId) throws Exception {
        HttpResponse<String> response = send(disputeId);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> send(UUID disputeId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/disputes/" + disputeId + "/review"))
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
