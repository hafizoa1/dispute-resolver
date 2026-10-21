package com.hafiz.disputeresolver.moniepoint;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MoniepointSignatureVerifier {
    private final String secret;

    public MoniepointSignatureVerifier(@Value("${app.moniepoint.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(String webhookId, String timestamp, String signature, byte[] body) {
        if (secret.isBlank()) {
            throw new WebhookException(HttpStatus.SERVICE_UNAVAILABLE, "Moniepoint webhook is not configured");
        }
        if (webhookId == null || webhookId.isBlank() || webhookId.length() > 128
                || webhookId.contains("__") || timestamp == null || timestamp.length() > 19
                || !timestamp.matches("[0-9]+") || signature == null || signature.length() > 64) {
            throw unauthorized();
        }
        try {
            if (Long.parseLong(timestamp) <= 0) {
                throw unauthorized();
            }
            byte[] supplied = Base64.getDecoder().decode(signature);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((webhookId + "__" + timestamp + "__").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw unauthorized();
            }
        } catch (IllegalArgumentException ex) {
            throw unauthorized();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private WebhookException unauthorized() {
        return new WebhookException(HttpStatus.UNAUTHORIZED, "Invalid Moniepoint webhook authentication");
    }
}
