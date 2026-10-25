# Restaurant assessment experiment v1

Run from the repository root with Java 17:

```sh
bash scripts/run_restaurant_assessment.sh
```

This is a pure Java preparation evaluator with an executable experiment. It has no
HTTP endpoint, schema migration, Spring context or provider call. It is not connected
to the existing review endpoint yet. The experiment uses the existing fictional
restaurant text files and a synthetic payment-event label P0.

## Frozen scenario and manual expectations

The same fictional restaurant and transaction RST-20261015-0077 are used throughout.
The fixed allegation is: “I paid for dinner but received no food or service.”
The evaluator does not interpret or route arbitrary complaint text; the chosen profile
and the fixed allegation are explicit experimental preconditions.
The intended cross-border Mastercard dual-message route is a prototype assumption;
applicable current rules and merchant deadline have not been confirmed.

Every variant has the same payment-event reference and the same attributed merchant
explanation. Extra evidence is removed as a controlled change:

| Variant | Supplementary records | Manual expected delivery basis |
| --- | --- | --- |
| Extensive | Bill, order record, unlinked message | RECORD_ACCOUNT_AVAILABLE |
| Moderate | Bill | MERCHANT_ASSERTION_ONLY |
| Minimal | None | MERCHANT_ASSERTION_ONLY |

These expectations were specified before the implementation. The bill records charges;
it does not trigger a service-performance conclusion. The order record explicitly lists
service times. The correspondence is candidate context: its author and transaction
association are unestablished. A documentary account does not verify that delivery
occurred, and no variant gets a submission-readiness or recovery-probability result.

## Algorithm contract

1. Receive case-scoped payment IDs, the existing conflict flag, merchant explanation
   and evidence with manually reviewed observations.
2. Check each observation's quotation occurs in its supplied source text. This is an
   integrity check on the quotation, not verification of its interpretation or truth.
3. Compare declared references exactly. Missing/blank is unknown; mismatch stays visible.
4. Attribute billing observations to billing records without inferring service delivery.
5. An unchallenged, reference-matched reviewed service observation contributes a
   documentary service account. A declared match is not independently verified linkage.
6. Keep unverified correspondence and challenged/unmatched records in review context.
7. Return cited, attributed statements plus gaps, challenges and follow-up requests.

`Kind` selects the meaning of a **manually assessed observation** for this experiment.
It must not be populated directly from an attachment category or filename. Even a
copied quotation does not establish authenticity. Observations are intentionally
trusted human inputs here; automated extraction and reviewer workflow are separate.

The implementation is one fixed prototype policy, not a generic AND/OR graph engine
or a complete Mastercard rule implementation. Source anchor: Mastercard Chargeback
Guide, Merchant Edition, 13 May 2025, pp. 153–154:
https://www.mastercard.us/content/dam/public/mastercardcom/na/global-site/documents/chargeback-guide.pdf
The guide informs the need to address service provision; these exact labels and checks
are our policy and require domain review.

## Verification boundaries

The runner checks the three expected results and behavioural counterexamples including
wrong references, unreviewed uploads, a reviewed contradiction, duplicate records,
missing payment data, conflicting payment histories and an invented source quotation.
It deliberately does not use Java's optional `assert` keyword.

Passing demonstrates agreement with the experimental policy. It does not validate
network acceptance, content extraction, evidence authenticity or live integration.
Because there is no database work in this slice, the experiment does not require
Postgres. API/database end-to-end verification is a later integration step.

The repository's existing GitHub Actions workflow also runs this experiment before
the backend tests. Local verification on 24 October 2026: the standalone Java 17
experiment passed 30 behavioural checks. Full Maven compilation was attempted but
could not resolve repo.maven.apache.org in this environment; the complete Spring
build and PostgreSQL test suite have not been verified here.

Next integration boundary: build this input from CaseService.getCase(id), preserving
the existing full payment history alongside the assessment, and supply scoped reviewed
observations. There is no persisted observation-review model or public annotation
endpoint in this change. The experiment does not modify existing review semantics,
merchant approval or submission behaviour.
