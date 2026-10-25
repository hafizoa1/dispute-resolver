import com.hafiz.disputeresolver.assessment.ServiceNotReceivedAssessment;
import com.hafiz.disputeresolver.assessment.ServiceNotReceivedAssessment.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executable specification, no Spring, database, sandbox or external libraries. */
public class RestaurantAssessmentExperiment {
    private static final String REF = "RST-20261015-0077";
    private static final String MERCHANT = "The party attended our restaurant on 15 October 2026 and we served "
            + "their dinner. I dispute the claim that no food or service was received.";
    private static final ServiceNotReceivedAssessment ENGINE = new ServiceNotReceivedAssessment();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of("docs/fixtures/service-not-received-example");
        String billText = Files.readString(fixtures.resolve("bill.txt"));
        String orderText = Files.readString(fixtures.resolve("order-record.txt"));
        String messageText = Files.readString(fixtures.resolve("post-visit-message.txt"));
        Evidence bill = new Evidence("B1", billText, List.of(new Observation(Kind.BILLING_ACCOUNT,
                "1x Seabass main          18,000.00", REF, null)));
        Evidence order = new Evidence("O1", orderText, List.of(
                new Observation(Kind.RECORDED_SERVICE_ACCOUNT, "Course 1 served: 20:02", REF, null),
                new Observation(Kind.RECORDED_SERVICE_ACCOUNT, "Dessert served: 20:44", REF, null)));
        Evidence message = new Evidence("C1", messageText, List.of(new Observation(Kind.CORRESPONDENCE,
                "Thanks for having us last night, the steak was excellent", null, null)));

        Result extensive = run("Extensive", List.of(bill, order, message));
        Result moderate = run("Moderate", List.of(bill));
        Result minimal = run("Minimal", List.of());
        check(extensive.deliveryBasis() == DeliveryBasis.RECORD_ACCOUNT_AVAILABLE,
                "extensive has a documentary account, not a verified delivery finding");
        check(moderate.deliveryBasis() == DeliveryBasis.MERCHANT_ASSERTION_ONLY,
                "bill alone must not become a delivery record");
        check(minimal.deliveryBasis() == DeliveryBasis.MERCHANT_ASSERTION_ONLY,
                "merchant statement remains an assertion");
        check(extensive.draftStatements().stream().anyMatch(s -> s.recordId().equals("O1")
                && s.text().contains("Dessert served: 20:44")), "service passage cited");
        check(extensive.draftStatements().stream().noneMatch(s -> s.recordId().equals("C1")),
                "unlinked message does not enter the defence as an acknowledgment");
        check(has(extensive, "CORRESPONDENCE_LINK_UNESTABLISHED"), "message linkage gap retained");
        check(has(moderate, "NO_UNCHALLENGED_SERVICE_RECORD"), "moderate delivery gap retained");
        check(has(minimal, "NO_UNCHALLENGED_SERVICE_RECORD"), "minimal delivery gap retained");

        // Behavioural counterexamples: these are not additional business features.
        Result duplicated = assess(List.of(bill, order, message, order));
        check(duplicated.equals(extensive), "duplicating a record must not change assessment");
        Evidence wrongBooking = new Evidence("O2", orderText.replace(REF, "UNRELATED-BOOKING"), List.of(new Observation(
                Kind.RECORDED_SERVICE_ACCOUNT, "Course 1 served: 20:02", "UNRELATED-BOOKING", null)));
        Result mismatch = assess(List.of(wrongBooking));
        check(mismatch.deliveryBasis() == DeliveryBasis.MERCHANT_ASSERTION_ONLY
                && has(mismatch, "REFERENCE_MISMATCH"), "wrong-booking declaration cannot support this delivery");
        Evidence disputedOrder = new Evidence("O1", orderText, List.of(new Observation(
                Kind.RECORDED_SERVICE_ACCOUNT, "Course 1 served: 20:02", REF,
                "Reviewer identified a conflicting visit date in another supplied record.")));
        Result conflict = assess(List.of(disputedOrder));
        check(conflict.deliveryBasis() == DeliveryBasis.CHALLENGED
                && has(conflict, "UNRESOLVED_CHALLENGE"), "reviewed challenge surfaced");
        check(conflict.draftStatements().stream().noneMatch(s -> s.recordId().equals("O1")),
                "challenged passage kept in review context rather than unqualified draft");
        Result unreviewed = assess(List.of(new Evidence("O1", orderText, List.of())));
        check(unreviewed.deliveryBasis() == DeliveryBasis.MERCHANT_ASSERTION_ONLY
                && has(unreviewed, "NEEDS_CONTENT_REVIEW"), "file presence is not content assessment");
        Result paymentOnly = ENGINE.assess(new Input(REF, List.of("P0"), false, null, List.of()));
        check(paymentOnly.deliveryBasis() == DeliveryBasis.NO_SERVICE_ACCOUNT_SUPPLIED,
                "payment without any delivery account must not imply service");
        Result missingPayment = ENGINE.assess(new Input(REF, List.of(), false, MERCHANT, List.of(order)));
        check(missingPayment.deliveryBasis() == DeliveryBasis.RECORD_ACCOUNT_AVAILABLE
                && has(missingPayment, "NO_PAYMENT_EVENT"), "payment-feed gap separate from service account");
        Result paymentConflict = ENGINE.assess(new Input(REF, List.of("P0", "P1"), true, MERCHANT, List.of(order)));
        check(paymentConflict.paymentEventIds().equals(List.of("P0", "P1"))
                && has(paymentConflict, "PAYMENT_CONFLICT"), "retain all event references and payment conflict");
        Evidence unknownRef = new Evidence("O1", orderText, List.of(new Observation(
                Kind.RECORDED_SERVICE_ACCOUNT, "Course 1 served: 20:02", " ", null)));
        check(has(assess(List.of(unknownRef)), "REFERENCE_UNKNOWN"), "blank reference is unknown");
        rejects(() -> new Evidence("O1", orderText, List.of(new Observation(
                Kind.RECORDED_SERVICE_ACCOUNT, "Customer identity verified", REF, null))),
                "invented quote rejected");
        rejects(() -> assess(List.of(order, disputedOrder)), "same ID with conflicting versions rejected");

        // Re-run shared input without merchant assertion: content support still remains attributed.
        Result withoutStatement = ENGINE.assess(new Input(REF, List.of("P0"), false, null, List.of(order)));
        check(withoutStatement.deliveryBasis() == DeliveryBasis.RECORD_ACCOUNT_AVAILABLE,
                "supplementary record does not require a generic merchant assertion to be present");
        var mutable = new ArrayList<>(List.of("P0"));
        Input frozen = new Input(REF, mutable, false, MERCHANT, List.of());
        mutable.add("P1");
        check(ENGINE.assess(frozen).paymentEventIds().equals(List.of("P0")), "input event list defensively copied");
        System.out.println("\nPASS: " + checks + " behavioural checks. No network decision or win probability assessed.");
    }

    private static Result run(String name, List<Evidence> evidence) {
        Result result = assess(evidence);
        System.out.println("\n" + name + ": " + result.deliveryBasis());
        for (Statement statement : result.draftStatements()) {
            System.out.println("  [" + statement.recordId() + "] " + statement.text());
        }
        for (Finding finding : result.findings()) {
            System.out.println("  " + finding.code() + ": " + finding.message());
        }
        check(result.profile().equals(ServiceNotReceivedAssessment.PROFILE), "same profile for " + name);
        check(result.paymentEventIds().equals(List.of("P0")), "same payment baseline for " + name);
        check(result.limitation().contains("Submission readiness is not assessed"), "scope retained for " + name);
        return result;
    }

    private static Result assess(List<Evidence> evidence) {
        return ENGINE.assess(new Input(REF, List.of("P0"), false, MERCHANT, evidence));
    }

    private static boolean has(Result result, String code) {
        return result.findings().stream().anyMatch(f -> f.code().equals(code));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }
}
