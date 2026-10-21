package com.hafiz.disputeresolver.moniepoint;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MoniepointWebhookController {
    static final int MAX_BODY_BYTES = 65_536;
    private final MoniepointWebhookService service;

    public MoniepointWebhookController(MoniepointWebhookService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/webhooks/moniepoint", consumes = "application/json")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "moniepoint-webhook-id", required = false) String webhookId,
            @RequestHeader(name = "moniepoint-webhook-timestamp", required = false) String timestamp,
            @RequestHeader(name = "moniepoint-webhook-signature", required = false) String signature,
            HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new WebhookException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds 64 KiB");
        }
        service.receive(webhookId, timestamp, signature, body);
        return ResponseEntity.noContent().build();
    }
}
