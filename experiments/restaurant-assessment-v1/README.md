# Claim assessment experiment

This revision replaces the original document-kind and delivery-basis classifier.
It extends the existing review instead of duplicating its quotation/reference/gap logic.
No migration, new persistence, provider request, approval change or automatic submission.

## Run and read

Requires Java 17; no Spring context or PostgreSQL for the executable examples:

```sh
bash scripts/run_restaurant_assessment.sh
```

Read in order:

1. `src/main/java/com/hafiz/disputeresolver/assessment/ServiceNotReceivedAssessment.java`
2. `src/test/java/com/hafiz/disputeresolver/assessment/ServiceNotReceivedAssessmentExamples.java`
3. `ReviewService.assessServiceNotReceived` and `ReviewController.assess`.

The same examples also run through `ServiceNotReceivedAssessmentTests` in the normal
Maven suite. The previous extra CI experiment step has been removed.

## API: explicit human interpretations, transient assessment

```http
POST /api/disputes/{id}/review/service-not-received-assessment
Content-Type: application/json
```

```json
{
  "interpretations": [
    {
      "evidenceId": "00000000-0000-0000-0000-000000000002",
      "claim": "SERVICE_PROVIDED",
      "relation": "SUPPORTS",
      "passage": "Course 1 served: 20:02",
      "linkageExplanation": null,
      "unresolvedQuestion": null
    }
  ]
}
```

Replace the example evidence ID with an actual attachment ID on that dispute. Its saved
`observationText` must contain the passage exactly. Existing observation-save endpoints
and existing declared-reference validation remain the input path. A reviewer explicitly
chooses the interpretation; there is no content extraction, semantic classification,
file authentication or automatic Mastercard route selection. Selecting this endpoint
means explicitly choosing the fixed service-not-received experiment.

The response has:

- `review`: the unchanged output of `ReviewService.getReview`, including the dispute,
  full payment history/conflict, evidence citations, structural gaps and original draft.
- `assessment.claims`: fixed claims `SERVICE_PROVIDED` and `CARDHOLDER_RECEIVED_SERVICE`.
  Each exposes supporting, conflicting and unresolved passages and questions.
- Each passage retains the original citation and its exact quoted **merchant observation**.
  It must never be presented as a verified quotation from the uploaded file.
- Each passage also exposes a named `inference`: rule ID, asserted premises and blockers.

Unknown evidence IDs and quotations absent from the saved observation return 400.
Missing disputes and unconfigured business settings retain existing review behavior.
Interpretations and results are not saved. Nothing here is an approved defence or a
replacement for the existing approval snapshot. The existing draft is not rewritten.

## What the algorithm actually does

There are two fixed defeasible preparation rules, with no document-type precedence:

- `SERVICE_ACCOUNT_V1`: a human says a passage supports/opposes service provision;
  the existing review supplies its declared transaction-reference connection.
- `RECEIPT_ACCOUNT_V1`: the same, with an additional human explanation connecting
  the account to the cardholder and disputed visit.

A support inference with an unknown/mismatched reference, an unresolved question,
missing required cardholder linkage, or a payment-identity conflict stays unresolved.
Its blockers become specific review questions. Contradicting accounts remain visible
with their own linkage limitations; they are not assumed true and do not silently
wipe out supporting accounts. Both sides require human review.

The engine checks that a quote occurs in the merchant observation, not whether that
observation faithfully describes an attachment. Likewise a typed linkage explanation
is an asserted premise, not independently verified identity. A human can supply a
bad interpretation: this experiment does not detect that semantic error.

No interpretation is created from file presence or filename. A bill without a reviewed
service-related passage creates no service support. An email can support either claim
when the same required premises are supplied. Missing support is not non-delivery.
Duplicate identical interpretations do not add support. Separate copies under different
IDs are not detected as duplicates; support is not counted into a strength score.

## Relationship to our algorithm research

This implements the first explicit evidence-to-claim rule applications and linkage
blockers. It is a small custom evaluator inspired by defeasible argument reasoning,
not an implementation or validation of ASPIC+, Carneades, formal proof standards,
argumentation acceptance semantics, or Odekerken's stability/relevance algorithms.
Questions identify missing inputs for these two rules; they are not an optimized next
question or a formal claim that a result is stable under all future evidence.

A reviewer can inspect why a passage contributed and which challenge blocked it.
Adding a full framework is deferred until this bounded example demonstrates a need.
These rules are our experimental preparation policy, not an encoded Mastercard
acceptance rule. No current scheme/route compliance or recovery likelihood is claimed.

## Manual oracle and limits

Same fictional restaurant, dinner allegation and payment context throughout:

| Evidence available | Human interpretations | Expected result |
| --- | --- | --- |
| Bill, service log, message | Log supports provision; message supports receipt but lacks identity linkage | Provision has an attributed account; receipt question stays open |
| Bill | None asserting service provision | No documentary service-support passage identified |
| None | None | Same absence of supplementary support; base review still shows payment context |

The examples copy fictional file text into merchant-observation inputs deliberately.
That makes them reproducible; it does not establish a file-reading capability. The
moderate/minimal assessment results are equal while their full reviews would differ.
The existing review retains merchant observations in all cases; a general merchant
explanation is not synthesized into verified support.

Counterexamples cover email support, arbitrary filename changes, wrong/unknown
references, unresolved challenges, visible support and contradiction together,
duplicates, payment conflict, unknown evidence, invented quotes and absent observations.
An opposing synthetic complaint is labeled separately from the real fixture message.

Verification on 25 October 2026: standalone Java examples pass 29 behavioral checks.
Maven/Spring/PostgreSQL verification must also run; see the delivery notes for whether
this environment could fetch the required dependencies. HTTP integration tests cover
reuse of the complete existing review, no persisted observation/draft changes and
invalid evidence/quotation requests. Do not read standalone success as HTTP verification.
