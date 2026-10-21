package com.hafiz.disputeresolver.moniepoint;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MoniepointWebhookService {
    private static final String PURCHASE_EVENT = "V1_POS_PURCHASE_TRANSACTION";
    private final MoniepointSignatureVerifier verifier;
    private final MoniepointPurchaseEventRepository repository;
    private final JsonMapper mapper;
    private final String businessId;

    public MoniepointWebhookService(MoniepointSignatureVerifier verifier,
            MoniepointPurchaseEventRepository repository, JsonMapper mapper,
            @Value("${app.moniepoint.business-id:}") String businessId) {
        this.verifier = verifier;
        this.repository = repository;
        this.mapper = mapper;
        this.businessId = businessId;
    }

    public void receive(String webhookId, String timestamp, String signature, byte[] body) {
        verifier.verify(webhookId, timestamp, signature, body);
        if (businessId.isBlank()) {
            throw new WebhookException(HttpStatus.SERVICE_UNAVAILABLE, "Moniepoint business is not configured");
        }
        JsonNode root;
        String rawPayload;
        try {
            rawPayload = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            root = mapper.readTree(rawPayload);
        } catch (JacksonException | CharacterCodingException ex) {
            throw badRequest("Expected a UTF-8 JSON object");
        }
        if (root == null || !root.isObject()) {
            throw badRequest("Expected a JSON object");
        }
        String eventType = requiredText(root, "eventType");
        if (!PURCHASE_EVENT.equals(eventType)) {
            return; // Acknowledge authenticated, unsupported event types without treating them as purchases.
        }
        JsonNode data = root.path("data");
        JsonNode sourceBusiness = data.path("businessId");
        if (!data.isObject() || !sourceBusiness.isIntegralNumber() || !sourceBusiness.canConvertToLong()
                || sourceBusiness.longValue() <= 0) {
            throw badRequest("Purchase data requires a numeric businessId");
        }
        if (!Long.toString(sourceBusiness.longValue()).equals(businessId)) {
            throw new WebhookException(HttpStatus.FORBIDDEN, "Webhook business does not match this connection");
        }
        JsonNode amount = data.path("amount");
        if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0) {
            throw badRequest("Purchase amount must be a positive 64-bit integer");
        }
        repository.saveOnce(new MoniepointPurchaseEvent(webhookId, optionalText(root, "eventId"),
                sourceBusiness.longValue(), optionalText(data, "transactionReference"),
                optionalText(data, "merchantReference"), amount.longValue(),
                optionalText(data, "transactionStatus"), optionalText(data, "responseCode"),
                rawPayload, sha256(body)));
    }

    private String requiredText(JsonNode node, String name) {
        String value = optionalText(node, name);
        if (value == null || value.isBlank()) {
            throw badRequest("Missing " + name);
        }
        return value;
    }

    private String optionalText(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw badRequest(name + " must be a string");
        }
        return value.stringValue();
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private WebhookException badRequest(String message) {
        return new WebhookException(HttpStatus.BAD_REQUEST, message);
    }
}
