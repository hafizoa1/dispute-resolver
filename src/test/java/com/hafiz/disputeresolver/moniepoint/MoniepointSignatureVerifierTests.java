package com.hafiz.disputeresolver.moniepoint;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoniepointSignatureVerifierTests {
    // Computed independently using Python hmac/hashlib, including UTF-8 and exact whitespace.
    private static final byte[] BODY = " {\"message\":\"₦ café\"}\n".getBytes(StandardCharsets.UTF_8);
    private static final String SIGNATURE = "GoiVbMgXdjSRNiF+59WxXIDmce9vmSkhacAGr9XJsD8=";
    private final MoniepointSignatureVerifier verifier = new MoniepointSignatureVerifier("test-secret");

    @Test
    void acceptsIndependentSignatureVector() {
        assertThatCode(() -> verifier.verify("evt-1", "1728651860073", SIGNATURE, BODY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsChangedRawBytesEvenWhenJsonMeaningIsUnchanged() {
        assertUnauthorized(() -> verifier.verify("evt-1", "1728651860073", SIGNATURE,
                "{\"message\":\"₦ café\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsChangedSignedHeaders() {
        assertUnauthorized(() -> verifier.verify("evt-2", "1728651860073", SIGNATURE, BODY));
        assertUnauthorized(() -> verifier.verify("evt-1", "1728651860074", SIGNATURE, BODY));
    }

    @Test
    void rejectsMissingMalformedOrOverflowingHeaders() {
        assertUnauthorized(() -> verifier.verify(null, "1728651860073", SIGNATURE, BODY));
        assertUnauthorized(() -> verifier.verify("evt-1", null, SIGNATURE, BODY));
        assertUnauthorized(() -> verifier.verify("evt-1", "1728651860073", null, BODY));
        assertUnauthorized(() -> verifier.verify("evt-1", "1728651860073", "not-base64!", BODY));
        assertUnauthorized(() -> verifier.verify("evt-1", "9999999999999999999", SIGNATURE, BODY));
    }

    @Test
    void failsClosedWithoutSecret() {
        assertThatThrownBy(() -> new MoniepointSignatureVerifier("")
                .verify("evt-1", "1728651860073", SIGNATURE, BODY))
                .isInstanceOfSatisfying(WebhookException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void assertUnauthorized(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WebhookException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
