package com.hafiz.disputeresolver.assessment;

import com.hafiz.disputeresolver.assessment.ServiceNotReceivedAssessment.*;
import com.hafiz.disputeresolver.dto.EvidenceCitation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Shared executable specification for the local runner and the Maven test suite. */
public class ServiceNotReceivedAssessmentExamples {
    private static final ServiceNotReceivedAssessment ENGINE = new ServiceNotReceivedAssessment();
    private static int checks;
    private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EMAIL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    public static void main(String[] args) throws Exception {
        checks = 0;
        Path fixtures = Path.of("docs/fixtures/service-not-received-example");
        var bill = citation(BILL, "bill.txt", Files.readString(fixtures.resolve("bill.txt")), true);
        var log = citation(LOG, "order-record.txt", Files.readString(fixtures.resolve("order-record.txt")), true);
        var email = citation(EMAIL, "post-visit-message.txt", Files.readString(fixtures.resolve("post-visit-message.txt")), true);
        String served = "Course 1 served: 20:02";
        String thanks = "Thanks for having us last night, the steak was excellent";
        var service = interpretation(LOG, Claim.SERVICE_PROVIDED, Relation.SUPPORTS, served, null, null);
        var acknowledgment = interpretation(EMAIL, Claim.CARDHOLDER_RECEIVED_SERVICE, Relation.SUPPORTS,
                thanks, null, null);

        Result extensive = assess(List.of(bill, log, email), service, acknowledgment);
        Result moderate = assess(List.of(bill)); // A bill's presence creates no interpretation.
        Result minimal = assess(List.of());
        check(claim(extensive, Claim.SERVICE_PROVIDED).supportingPassages().size() == 1,
                "service passage contributes attributed support");
        check(claim(extensive, Claim.CARDHOLDER_RECEIVED_SERVICE).supportingPassages().isEmpty(),
                "unlinked acknowledgment stays unresolved");
        check(claim(extensive, Claim.CARDHOLDER_RECEIVED_SERVICE).unresolvedPassages().size() == 1,
                "message remains visible");
        check(moderate.equals(minimal), "bill alone does not add service support");
        for (Claim c : Claim.values()) {
            check(claim(moderate, c).supportingPassages().isEmpty(), "moderate has no support for " + c);
            check(!claim(minimal, c).questions().isEmpty(), "minimal exposes question for " + c);
        }
        String link = "Reviewer states sender is the cardholder and message concerns this dinner; not independently verified.";
        var linked = interpretation(EMAIL, Claim.CARDHOLDER_RECEIVED_SERVICE, Relation.SUPPORTS, thanks, link, null);
        Result linkedEmail = assess(List.of(email), linked);
        check(claim(linkedEmail, Claim.CARDHOLDER_RECEIVED_SERVICE).supportingPassages().size() == 1,
                "email can support receipt when reviewer supplies linkage");
        check(claim(linkedEmail, Claim.CARDHOLDER_RECEIVED_SERVICE).supportingPassages().get(0)
                .reviewerLinkageExplanation().equals(link), "asserted linkage preserved verbatim");
        var emailService = interpretation(EMAIL, Claim.SERVICE_PROVIDED, Relation.SUPPORTS, thanks, null, null);
        check(claim(assess(List.of(email), emailService), Claim.SERVICE_PROVIDED).supportingPassages().size() == 1,
                "email may support provision without document-type discrimination");
        var renamed = citation(EMAIL, "receipt.pdf", email.observationText(), true);
        check(assess(List.of(renamed), linked).claims().get(1).supportingPassages().size() == 1,
                "filename does not determine support");
        var wrong = citation(LOG, "order-record.txt", log.observationText(), false);
        Result mismatch = assess(List.of(wrong), service);
        check(claim(mismatch, Claim.SERVICE_PROVIDED).supportingPassages().isEmpty(), "wrong reference excluded from support");
        check(claim(mismatch, Claim.SERVICE_PROVIDED).unresolvedPassages().size() == 1, "wrong reference visible");
        var unknown = citation(LOG, "order-record.txt", log.observationText(), null);
        check(claim(assess(List.of(unknown), service), Claim.SERVICE_PROVIDED).supportingPassages().isEmpty(),
                "unknown reference not treated as matched");
        var challenged = interpretation(LOG, Claim.SERVICE_PROVIDED, Relation.SUPPORTS, served, null,
                "Visit date disputed by another record.");
        check(claim(assess(List.of(log), challenged), Claim.SERVICE_PROVIDED).unresolvedPassages().size() == 1,
                "challenged account remains unresolved");
        var denial = citation(EMAIL, "complaint.txt", "Customer states: No food arrived.", true);
        var contradiction = interpretation(EMAIL, Claim.SERVICE_PROVIDED, Relation.CONTRADICTS,
                "No food arrived.", null, null);
        Result disputed = assess(List.of(log, denial), service, contradiction);
        check(claim(disputed, Claim.SERVICE_PROVIDED).supportingPassages().size() == 1, "support retained alongside conflict");
        check(claim(disputed, Claim.SERVICE_PROVIDED).conflictingPassages().size() == 1, "conflict explicitly exposed");
        check(claim(disputed, Claim.SERVICE_PROVIDED).questions().stream().anyMatch(q -> q.contains("conflicting accounts")),
                "conflict requires review");
        check(assess(List.of(log), service, service).equals(assess(List.of(log), service)), "duplicate interpretation adds nothing");
        Result paymentConflict = ENGINE.assess(List.of(log), true, new Request(List.of(service)));
        check(claim(paymentConflict, Claim.SERVICE_PROVIDED).supportingPassages().isEmpty(), "payment identity conflict qualifies support");
        rejects(() -> assess(List.of(bill), service), "another dispute's evidence ID rejected");
        rejects(() -> assess(List.of(log), interpretation(LOG, Claim.SERVICE_PROVIDED, Relation.SUPPORTS,
                "Customer identity verified", null, null)), "invented observation quote rejected");
        rejects(() -> assess(List.of(citation(LOG, "file", null, null)), service), "missing observation rejected");
        check(extensive.scopeDisclaimer().contains("not verified file contents"), "attribution limitation retained");
        check(claim(extensive, Claim.SERVICE_PROVIDED).supportingPassages().get(0).quotedObservation().equals(served),
                "passage quoted verbatim");
        var accepted = claim(extensive, Claim.SERVICE_PROVIDED).supportingPassages().get(0).inference();
        check(accepted.ruleId().equals("SERVICE_ACCOUNT_V1") && accepted.blockers().isEmpty(), "named inference records its premises");
        var blocked = claim(mismatch, Claim.SERVICE_PROVIDED).unresolvedPassages().get(0).inference();
        check(!blocked.blockers().isEmpty(), "link challenge blocks this inference");
        check(accepted.premises().size() == 2, "interpretation and declared link are explicit premises");
        System.out.println("Extensive: " + extensive.claims());
        System.out.println("Moderate: " + moderate.claims());
        System.out.println("Minimal: " + minimal.claims());
        System.out.println("PASS: " + checks + " behavioural checks; no win score or verified-delivery conclusion.");
    }
    private static EvidenceCitation citation(UUID id, String file, String text, Boolean matches) {
        return new EvidenceCitation(id, file, text, matches == null ? null : matches ? "RST-20261015-0077" : "OTHER-BOOKING", matches);
    }
    private static Interpretation interpretation(UUID id, Claim claim, Relation relation, String quote, String link, String question) {
        return new Interpretation(id, claim, relation, quote, link, question);
    }
    private static Result assess(List<EvidenceCitation> citations, Interpretation... interpretations) {
        return ENGINE.assess(citations, false, new Request(List.of(interpretations)));
    }
    private static ClaimAssessment claim(Result result, Claim claim) {
        return result.claims().stream().filter(c -> c.claim() == claim).findFirst().orElseThrow();
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
}
