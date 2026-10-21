package com.hafiz.disputeresolver.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.hafiz.disputeresolver.model.Dispute;
import com.hafiz.disputeresolver.repository.DisputeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// Separate Spring context from CaseControllerIntegrationTests specifically to
// exercise app.moniepoint.business-id left blank - the endpoint must fail
// clearly, never query across businesses or return an empty history that
// looks like "no payment found." Explicitly forced blank here rather than
// just omitted: application.properties reads this from MONIEPOINT_BUSINESS_ID,
// so a developer with that exported locally (as for manual testing against
// the sample webhook script) would otherwise get a real value here too, and
// this test would silently stop testing what its name says it tests.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.moniepoint.business-id=")
class CaseControllerUnconfiguredBusinessTests {

    private final HttpClient client = HttpClient.newHttpClient();
    @Autowired private DisputeRepository disputeRepository;
    @Value("${local.server.port}") private int port;

    @Test
    void failsClearlyWhenBusinessIdIsUnconfigured() throws Exception {
        Dispute dispute = disputeRepository.saveAndFlush(
                new Dispute("case-test-unconfigured-ref", 5000L, "GBP", "integration test fixture"));

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/disputes/" + dispute.getId() + "/case")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
    }
}
