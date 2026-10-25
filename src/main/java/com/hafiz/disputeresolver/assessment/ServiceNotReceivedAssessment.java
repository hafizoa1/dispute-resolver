package com.hafiz.disputeresolver.assessment;

import com.hafiz.disputeresolver.dto.EvidenceCitation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A fixed service-not-received preparation experiment, not a network decision engine. */
public final class ServiceNotReceivedAssessment {
    public enum Claim { SERVICE_PROVIDED, CARDHOLDER_RECEIVED_SERVICE }
    public enum Relation { SUPPORTS, CONTRADICTS }

    /** Passage is quoted from the saved merchant observation, NOT extracted from a file.
     * linkageExplanation is a reviewer's asserted connection, not verified identity. */
    public record Interpretation(UUID evidenceId, Claim claim, Relation relation, String passage,
            String linkageExplanation, String unresolvedQuestion) {
        public Interpretation {
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(claim, "claim");
            Objects.requireNonNull(relation, "relation");
            requireText(passage, "passage");
        }
    }
    public record Request(List<Interpretation> interpretations) {
        public Request { interpretations = List.copyOf(interpretations); }
    }
    /** Named rule application: premises are asserted inputs, blockers prevent reliance.
     * This is a bounded defeasible rule, not an implementation of ASPIC+ semantics. */
    public record Inference(String ruleId, List<String> premises, List<String> blockers) { }
    public record Passage(EvidenceCitation citation, String quotedObservation,
            String reviewerLinkageExplanation, String unresolvedQuestion, Inference inference) { }
    public record ClaimAssessment(Claim claim, List<Passage> supportingPassages,
            List<Passage> conflictingPassages, List<Passage> unresolvedPassages,
            List<String> questions) { }
    public record Result(List<ClaimAssessment> claims, String scopeDisclaimer) { }

    public Result assess(List<EvidenceCitation> citations, boolean paymentEventConflict, Request request) {
        citations = List.copyOf(citations);
        Objects.requireNonNull(request, "request");
        // Resolve only against the existing case-scoped citations. Do not read files,
        // perform another reference comparison, or infer meaning from filename/type.
        for (Interpretation interpretation : request.interpretations()) {
            EvidenceCitation citation = citation(citations, interpretation.evidenceId());
            if (citation.observationText() == null
                    || !citation.observationText().contains(interpretation.passage())) {
                throw new IllegalArgumentException("Passage must occur verbatim in the saved merchant observation: "
                        + interpretation.evidenceId());
            }
        }
        List<ClaimAssessment> claims = new ArrayList<>();
        for (Claim claim : Claim.values()) {
            List<Passage> support = new ArrayList<>();
            List<Passage> conflicts = new ArrayList<>();
            List<Passage> unresolved = new ArrayList<>();
            var questions = new LinkedHashSet<String>();
            for (Interpretation interpretation : new LinkedHashSet<>(request.interpretations())) {
                if (interpretation.claim() != claim) continue;
                EvidenceCitation citation = citation(citations, interpretation.evidenceId());
                List<String> premises = new ArrayList<>();
                List<String> blockers = new ArrayList<>();
                premises.add("Reviewer interprets the cited merchant-observation passage as "
                        + interpretation.relation() + " " + claim);
                boolean linked = Boolean.TRUE.equals(citation.referenceMatches());
                if (linked) premises.add("Existing review reports a declared transaction-reference match (not verified linkage).");
                else blockers.add("Establish the transaction connection for evidence " + citation.evidenceId());
                if (claim == Claim.CARDHOLDER_RECEIVED_SERVICE && !known(interpretation.linkageExplanation())) {
                    blockers.add("Explain the connection to the cardholder and disputed visit for evidence "
                            + citation.evidenceId());
                }
                if (claim == Claim.CARDHOLDER_RECEIVED_SERVICE && known(interpretation.linkageExplanation())) {
                    premises.add("Reviewer asserts cardholder/visit linkage: " + interpretation.linkageExplanation());
                }
                if (known(interpretation.unresolvedQuestion())) blockers.add(interpretation.unresolvedQuestion());
                if (paymentEventConflict) blockers.add("Payment identity is conflicting in the existing review.");
                questions.addAll(blockers);
                Passage passage = new Passage(citation, interpretation.passage(),
                        interpretation.linkageExplanation(), interpretation.unresolvedQuestion(),
                        new Inference(claim == Claim.SERVICE_PROVIDED ? "SERVICE_ACCOUNT_V1" : "RECEIPT_ACCOUNT_V1",
                                List.copyOf(premises), List.copyOf(blockers)));
                // Conflicting accounts stay visible even if their relevance is unresolved.
                if (interpretation.relation() == Relation.CONTRADICTS) {
                    conflicts.add(passage);
                } else if (!blockers.isEmpty()) {
                    unresolved.add(passage);
                } else {
                    support.add(passage);
                }
            }
            if (support.isEmpty()) questions.add("No linked, unchallenged supporting passage identified for " + claim
                    + ". This does not establish non-delivery.");
            if (!conflicts.isEmpty()) questions.add("Review the conflicting accounts and their relevance before relying on this claim.");
            if (paymentEventConflict) questions.add("Resolve the payment-identity conflict; the complete history remains in review.");
            claims.add(new ClaimAssessment(claim, List.copyOf(support), List.copyOf(conflicts),
                    List.copyOf(unresolved), List.copyOf(questions)));
        }
        return new Result(List.copyOf(claims),
                "Experimental service-not-received assessment of human interpretations of saved merchant observations. "
                + "Supporting passages remain attributed merchant assertions, not verified file contents or facts. "
                + "Reference matches and reviewer linkage explanations do not authenticate records or identity. "
                + "No document-type ranking, win score, current Mastercard compliance or submission readiness is assessed. "
                + "The existing review draft is unchanged; conflicts and questions must be read alongside support.");
    }
    private static EvidenceCitation citation(List<EvidenceCitation> citations, UUID id) {
        return citations.stream().filter(c -> c.evidenceId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Evidence is not in this dispute: " + id));
    }
    private static boolean known(String value) { return value != null && !value.isBlank(); }
    private static void requireText(String value, String name) {
        if (!known(value)) throw new IllegalArgumentException(name + " must not be blank");
    }
}
