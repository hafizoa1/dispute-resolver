package com.hafiz.disputeresolver.assessment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Experimental preparation policy, not Mastercard adjudication or submission readiness.
 * Observations are manually reviewed interpretations, not upload categories or NLP output.
 * The caller supplies case-scoped payment IDs; CaseService remains responsible for history.
 */
public final class ServiceNotReceivedAssessment {
    public static final String PROFILE = "prototype-service-not-received-v1";
    public static final String LIMITATION = "Experimental profile based on the Mastercard Merchant Edition "
            + "of 13 May 2025, pp. 153-154. Applicable current route is not confirmed. "
            + "Recorded accounts do not verify delivery, identity or authenticity. Submission readiness is not assessed.";

    public enum Kind { BILLING_ACCOUNT, RECORDED_SERVICE_ACCOUNT, CORRESPONDENCE }
    public enum DeliveryBasis { RECORD_ACCOUNT_AVAILABLE, MERCHANT_ASSERTION_ONLY,
        NO_SERVICE_ACCOUNT_SUPPLIED, CHALLENGED }

    public record Observation(Kind kind, String sourceQuote, String declaredTransactionReference,
            String unresolvedChallenge) {
        public Observation {
            Objects.requireNonNull(kind, "kind");
            requireText(sourceQuote, "sourceQuote");
        }
    }

    public record Evidence(String id, String originalText, List<Observation> observations) {
        public Evidence {
            requireText(id, "evidence id");
            Objects.requireNonNull(originalText, "originalText");
            observations = List.copyOf(observations);
            for (Observation observation : observations) {
                if (!originalText.contains(observation.sourceQuote())) {
                    throw new IllegalArgumentException("Source quotation not present in evidence " + id);
                }
            }
        }
    }

    public record Input(String transactionReference, List<String> paymentEventIds,
            boolean paymentEventConflict, String merchantExplanation, List<Evidence> evidence) {
        public Input {
            requireText(transactionReference, "transactionReference");
            paymentEventIds = List.copyOf(paymentEventIds);
            paymentEventIds.forEach(id -> requireText(id, "payment event id"));
            evidence = List.copyOf(evidence);
        }
    }

    public record Statement(String recordId, String text) { }
    public record Finding(String code, String recordId, String message) { }
    public record Result(String profile, DeliveryBasis deliveryBasis, List<String> paymentEventIds,
            List<Statement> draftStatements, List<Statement> candidateContext,
            List<Finding> findings, String limitation) {
        public Result {
            paymentEventIds = List.copyOf(paymentEventIds);
            draftStatements = List.copyOf(draftStatements);
            candidateContext = List.copyOf(candidateContext);
            findings = List.copyOf(findings);
        }
    }

    public Result assess(Input input) {
        Objects.requireNonNull(input, "input");
        List<Statement> draft = new ArrayList<>();
        List<Statement> context = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        boolean serviceAccount = false;
        boolean challenged = false;

        if (input.paymentEventIds().isEmpty()) {
            findings.add(new Finding("NO_PAYMENT_EVENT", null,
                    "No matching provider event supplied; this is a payment-context gap, not evidence of non-delivery."));
        } else {
            for (String id : input.paymentEventIds()) {
                draft.add(new Statement(id, "The supplied provider history contains an event for reference "
                        + input.transactionReference() + "."));
            }
        }
        if (input.paymentEventConflict()) {
            findings.add(new Finding("PAYMENT_CONFLICT", null,
                    "The case view reports conflicting payment identities; resolve before relying on that identity."));
        }
        boolean merchantAccount = known(input.merchantExplanation());
        if (merchantAccount) {
            draft.add(new Statement("merchant-explanation", "Merchant states: \""
                    + input.merchantExplanation() + "\""));
        }

        // Duplicate uploads of the same record cannot add support or repeat draft statements.
        var unique = new LinkedHashMap<String, Evidence>();
        for (Evidence evidence : input.evidence()) {
            Evidence previous = unique.putIfAbsent(evidence.id(), evidence);
            if (previous != null && !previous.equals(evidence)) {
                throw new IllegalArgumentException("Conflicting evidence versions for id " + evidence.id());
            }
        }

        for (Evidence evidence : unique.values()) {
            if (evidence.observations().isEmpty()) {
                findings.add(new Finding("NEEDS_CONTENT_REVIEW", evidence.id(),
                        "Attachment supplied without a reviewed interpretation; its presence adds no delivery conclusion."));
            }
            for (Observation observation : evidence.observations().stream().distinct().toList()) {
                boolean referenceKnown = known(observation.declaredTransactionReference());
                boolean referenceMatches = referenceKnown
                        && input.transactionReference().equals(observation.declaredTransactionReference());
                String quoted = "Supplied record states: \"" + observation.sourceQuote() + "\"";

                if (observation.kind() == Kind.CORRESPONDENCE) {
                    context.add(new Statement(evidence.id(), quoted));
                    findings.add(new Finding("CORRESPONDENCE_LINK_UNESTABLISHED", evidence.id(),
                            "Connect the sender and visit/payment before treating this as a cardholder acknowledgment. "
                            + "A declared reference alone does not establish the sender's identity."));
                    if (known(observation.unresolvedChallenge())) {
                        findings.add(new Finding("CONTEXT_CHALLENGE", evidence.id(), observation.unresolvedChallenge()));
                    }
                    continue;
                }
                if (!referenceMatches) {
                    context.add(new Statement(evidence.id(), quoted));
                    findings.add(new Finding(referenceKnown ? "REFERENCE_MISMATCH" : "REFERENCE_UNKNOWN",
                            evidence.id(), "Establish which booking/payment this record concerns; do not assume relevance."));
                    continue;
                }
                if (known(observation.unresolvedChallenge())) {
                    context.add(new Statement(evidence.id(), quoted));
                    findings.add(new Finding("UNRESOLVED_CHALLENGE", evidence.id(), observation.unresolvedChallenge()));
                    if (observation.kind() == Kind.RECORDED_SERVICE_ACCOUNT) {
                        challenged = true;
                    }
                    continue;
                }
                String attribution = observation.kind() == Kind.BILLING_ACCOUNT
                        ? "Merchant billing record states: \"" : "Merchant service record states: \"";
                draft.add(new Statement(evidence.id(), attribution + observation.sourceQuote() + "\""));
                if (observation.kind() == Kind.RECORDED_SERVICE_ACCOUNT) {
                    serviceAccount = true;
                }
            }
        }

        DeliveryBasis basis = challenged ? DeliveryBasis.CHALLENGED
                : serviceAccount ? DeliveryBasis.RECORD_ACCOUNT_AVAILABLE
                : merchantAccount ? DeliveryBasis.MERCHANT_ASSERTION_ONLY
                : DeliveryBasis.NO_SERVICE_ACCOUNT_SUPPLIED;
        if (!serviceAccount) {
            findings.add(new Finding("NO_UNCHALLENGED_SERVICE_RECORD", null,
                    "No unchallenged, reference-matched documentary service account identified. "
                    + "Ask for existing service records or communications; accept that none may be available. "
                    + "Absence does not establish non-delivery."));
        }
        return new Result(PROFILE, basis, input.paymentEventIds(), draft, context, findings, LIMITATION);
    }

    private static boolean known(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireText(String value, String name) {
        if (!known(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
