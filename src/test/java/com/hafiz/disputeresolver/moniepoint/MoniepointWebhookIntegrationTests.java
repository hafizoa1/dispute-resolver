package com.hafiz.disputeresolver.moniepoint;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the same local PostgreSQL/DB_* configuration as the application; no in-memory SQL substitute.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.moniepoint.webhook-secret=synthetic-test-secret", "app.moniepoint.business-id=123"
})
class MoniepointWebhookIntegrationTests {
    private static final String TIMESTAMP = "1728651860073";
    private static final String BODY = """
            {
              "eventId":"body-event-1", "eventType":"V1_POS_PURCHASE_TRANSACTION",
              "data":{"businessId":123,"amount":5000000,"transactionReference":"synthetic-txn-1",
                      "merchantReference":null,"transactionStatus":"PENDING","responseCode":"09"}
            }
            """;
    private final HttpClient client = HttpClient.newHttpClient();
    @Autowired private JdbcTemplate jdbc;
    @Value("${local.server.port}") private int port;

    @BeforeEach
    void clearPurchaseEvents() {
        jdbc.update("DELETE FROM moniepoint_purchase_events WHERE webhook_id LIKE 'test-%'");
    }

    @Test
    void preservesSourceWithoutCreatingDisputeOrAssumingPaymentSuccess() throws Exception {
        long disputesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM disputes", Long.class);
        assertThat(send("webhook-1", BODY).statusCode()).isEqualTo(204);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM moniepoint_purchase_events WHERE webhook_id LIKE 'test-%'");
        assertThat(row.get("raw_payload")).isEqualTo(BODY);
        assertThat(row.get("amount_raw")).isEqualTo(5000000L);
        assertThat(row.get("transaction_status")).isEqualTo("PENDING");
        assertThat(row.get("merchant_reference")).isNull();
        assertThat(row.get("provider_event_id")).isEqualTo("body-event-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputes", Long.class)).isEqualTo(disputesBefore);
    }

    @Test
    void concurrentRedeliveriesCreateOneRecord() throws Exception {
        HttpRequest request = request("same-webhook", BODY);
        List<CompletableFuture<HttpResponse<String>>> responses = IntStream.range(0, 8)
                .mapToObj(i -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())).toList();
        for (CompletableFuture<HttpResponse<String>> response : responses) {
            assertThat(response.join().statusCode()).isEqualTo(204);
        }
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void rejectsTamperedBodyAndMissingAuthentication() throws Exception {
        HttpRequest valid = request("webhook-1", BODY);
        HttpRequest tampered = HttpRequest.newBuilder(endpoint())
                .header("Content-Type", "application/json")
                .header("moniepoint-webhook-id", "test-webhook-1")
                .header("moniepoint-webhook-timestamp", TIMESTAMP)
                .header("moniepoint-webhook-signature", valid.headers().firstValue("moniepoint-webhook-signature").orElseThrow())
                .POST(HttpRequest.BodyPublishers.ofString(BODY.replace("5000000", "9000000"))).build();
        assertThat(client.send(tampered, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        HttpRequest unsigned = HttpRequest.newBuilder(endpoint()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(BODY)).build();
        assertThat(client.send(unsigned, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(count()).isZero();
    }

    @Test
    void rejectsWrongBusinessMalformedJsonAndNonIntegerAmounts() throws Exception {
        assertThat(send("wrong-business", BODY.replace("123", "456")).statusCode()).isEqualTo(403);
        assertThat(send("bad-json", "{").statusCode()).isEqualTo(400);
        assertThat(send("fractional", BODY.replace("5000000", "12.5")).statusCode()).isEqualTo(400);
        assertThat(send("overflow", BODY.replace("5000000", "9223372036854775808")).statusCode()).isEqualTo(400);
        assertThat(send("negative", BODY.replace("5000000", "-1")).statusCode()).isEqualTo(400);
        assertThat(count()).isZero();
    }

    @Test
    void ignoresOtherEventTypesAndRejectsOversizedBody() throws Exception {
        assertThat(send("transfer", BODY.replace("V1_POS_PURCHASE_TRANSACTION", "V1_POS_TRANSFER_TRANSACTION"))
                .statusCode()).isEqualTo(204);
        assertThat(send("large", " ".repeat(MoniepointWebhookController.MAX_BODY_BYTES + 1))
                .statusCode()).isEqualTo(413);
        assertThat(count()).isZero();
    }

    @Test
    void rejectsReusedWebhookIdWithDifferentSignedContent() throws Exception {
        assertThat(send("webhook-1", BODY).statusCode()).isEqualTo(204);
        assertThat(send("webhook-1", BODY.replace("5000000", "9000000")).statusCode()).isEqualTo(409);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_raw FROM moniepoint_purchase_events WHERE webhook_id LIKE 'test-%'", Long.class))
                .isEqualTo(5000000L);
    }

    @Test
    void keepsDistinctEventsForSameTransactionAsSeparateSnapshots() throws Exception {
        assertThat(send("webhook-1", BODY).statusCode()).isEqualTo(204);
        assertThat(send("webhook-2", BODY.replace("body-event-1", "body-event-2")
                .replace("PENDING", "APPROVED").replace("09", "00")).statusCode()).isEqualTo(204);
        assertThat(count()).isEqualTo(2);
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM moniepoint_purchase_events WHERE webhook_id LIKE 'test-%'", Long.class);
    }

    private HttpResponse<String> send(String id, String body) throws Exception {
        return client.send(request(id, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String id, String body) throws Exception {
        id = "test-" + id;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("synthetic-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal((id + "__" + TIMESTAMP + "__" + body).getBytes(StandardCharsets.UTF_8)));
        return HttpRequest.newBuilder(endpoint()).header("Content-Type", "application/json")
                .header("moniepoint-webhook-id", id).header("moniepoint-webhook-timestamp", TIMESTAMP)
                .header("moniepoint-webhook-signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + port + "/api/webhooks/moniepoint");
    }
}
