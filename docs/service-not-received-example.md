# Worked example: "service not received" defence

**This is a fictional, synthetic example, built to design against — not a real case, not real
customer or merchant data.** It's the hand-written expected output for the "service not
received" review/draft milestone: the automated version (once built) is judged against
reproducing this document's statements and gaps from the same underlying records, not
against a freshly invented format.

Grounded against the actual primary source, not secondary summaries: Mastercard
*Chargeback Guide — Merchant Edition*, 13 May 2025, message reason code 4853
("Cardholder Dispute"), "Goods or Services Not Provided" → Acquirer Second Presentment,
pp. 152–154.

## The scenario

A cardholder disputes a card payment of **NGN 79,500.00** made at The Riverside Bistro,
Lagos, on 15 October 2026, claiming the service was not received. The merchant disputes
this and wants to submit a defence. Naira, deliberately — Moniepoint is a Nigerian
provider, and the merchants actually taking payment through it charge in NGN, not GBP.

## Reproducing this fixture

The four source attachments are committed alongside this document, in
`docs/fixtures/service-not-received-example/`. The record ids quoted throughout this
document are from one specific run — **rerunning the commands below generates new ids**,
since nothing here is seeded or fixed; capture the new ids from each response if you
rerun it. With the app running locally (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` or the
defaults in `application.properties`, `MONIEPOINT_WEBHOOK_SECRET`/`MONIEPOINT_BUSINESS_ID`
set, same as the main README):

```bash
# 1. Create the dispute
curl -s -X POST localhost:8080/api/disputes -H 'Content-Type: application/json' \
  -d '{"transactionReference":"RST-20261015-0077","amountMinor":7950000,"currency":"NGN",
       "description":"Cardholder disputes charge for dinner at The Riverside Bistro on 15 Oct 2026, claims service was not received."}'
# -> note the returned "id"

# 2. Ingest the matching purchase event through the real signed webhook path
python3 scripts/send_moniepoint_sample.py \
  --webhook-id fixture-restaurant-ngn-1 \
  --transaction-reference RST-20261015-0077 \
  --merchant-reference table-14-20261015 \
  --amount 7950000 \
  --transaction-status APPROVED

# 3. Attach the four committed source records (replace <id> with the dispute id from step 1)
curl -s -X POST localhost:8080/api/disputes/<id>/evidence \
  -F "file=@docs/fixtures/service-not-received-example/bill.txt" \
  -F "description=Itemized bill/receipt for the meal, matching the disputed transaction reference"
curl -s -X POST localhost:8080/api/disputes/<id>/evidence \
  -F "file=@docs/fixtures/service-not-received-example/order-record.txt" \
  -F "description=Venue's own dated table/order record for the same reference and date"
curl -s -X POST localhost:8080/api/disputes/<id>/evidence \
  -F "file=@docs/fixtures/service-not-received-example/booking-confirmation-email.txt" \
  -F "description=Booking confirmation email sent to the customer ahead of the visit"
curl -s -X POST localhost:8080/api/disputes/<id>/evidence \
  -F "file=@docs/fixtures/service-not-received-example/post-visit-message.txt" \
  -F "description=Customer's own message after the visit"

# 4. Inspect the aggregate
curl -s localhost:8080/api/disputes/<id>/case

# 5. Save each evidence item's observation (see "Evidence records" below for the
#    exact text/reference used for each - PUT is idempotent, safe to rerun)
curl -s -X PUT localhost:8080/api/disputes/<id>/evidence/<evidenceId>/observation \
  -H 'Content-Type: application/json' \
  -d '{"observationText":"...","documentedTransactionReference":"RST-20261015-0077"}'
```

The four observations below aren't just proposed text - they've been saved through this
exact endpoint against the ids quoted throughout this document, and confirmed back via
both the evidence list and `GET .../case`.

## The disputed transaction

- Dispute id: `97f3bf47-cfb2-44a5-936d-b96110ae1a38`
- Transaction reference: `RST-20261015-0077`
- Disputed amount: NGN 79,500.00 (`amountMinor: 7950000`) — this figure is **our own
  system's field**, set directly when the dispute was created, and its currency/units are
  confirmed by construction.
- Matching Moniepoint purchase event id: `c04061d9-99bf-43f2-bb12-4c27f25838cd` —
  `transactionStatus: APPROVED`, `amountRaw: 7950000`, `merchantReference: table-14-20261015`.
  One event only; no conflicting history.

**Amount/currency caveat, not glossed over**: `amountRaw` is the provider's own raw
reported figure, and per the webhook PR's own README, its unit/currency meaning is
**unconfirmed** — nothing has verified against a real Moniepoint event that `7950000`
means "kobo," let alone that it means naira at all. This fixture's `amountRaw: 7950000`
was chosen to *numerically equal* the dispute's own `amountMinor` so the two line up
cleanly for this worked example — that equality is a **synthetic fixture assumption**,
not a confirmed fact about what Moniepoint actually reports. A real draft must not
silently present a provider's raw amount as a confirmed currency figure; the defence
statement below is phrased to keep that distinction visible.

**Reconciliation note, stated explicitly rather than assumed**: the itemized bill below
sums to NGN 70,500.00 + 12.5% service charge = NGN 79,312.50, not NGN 79,500.00. The
bill itself records "rounded: 79,500.00 on card slip" — that's what connects the itemized
total to the actual disputed amount. Without that one line, the bill and the disputed
transaction would look like a near-miss, not a match. This is exactly the kind of
connection the system can't infer on its own; it has to be read and stated by a person.

## Evidence records

For each record: what it is, how it's identified as belonging to *this* transaction, what
it could establish based on its actual content, and what the application has actually
checked (today: that a file was stored, and a human-entered observation — nothing more,
for any record type, receipt included).

### 1. Bill / receipt (`74433221-6762-422a-9049-09f01b5489eb`, `bill.txt`)

- **Connection to this transaction**: states reference `RST-20261015-0077` directly, same
  date (15 Oct 2026), and reconciles to the disputed amount via the rounding note above.
- **What it could establish**: that a bill for this reference was issued and marked paid
  in full by card at the table. It is *not* a signed proof-of-delivery in the literal
  sense Mastercard's own example describes (a signed UPS-style receipt) — it's a payment
  record, not a signature. Worth stating plainly rather than overclaiming the match to
  that example.
- **What the application has checked**: a file was stored under this dispute, labeled by
  the merchant as "Itemized bill/receipt for the meal, matching the disputed transaction
  reference." The reference match above is a human reading the file, not a system check.
- **Merchant-provided observation** (`documentedTransactionReference: RST-20261015-0077`):
  "This is the bill for the disputed booking, reference and date both match, total
  reconciles to the card charge once the rounding to 79,500.00 is accounted for."

### 2. Table/order record (`e439b654-1c0d-49c6-9618-250718cf4bad`, `order-record.txt`)

- **Connection to this transaction**: states the same reference, table 14, same date.
- **What it could establish**: that the venue's own system recorded a seated party of 2
  at table 14 from 19:40 to 21:05 on the disputed date, with courses fired and served at
  specific times — this is the closest match to Mastercard's own example language,
  "documentation that the services were provided."
- **What the application has checked**: same as above — file stored, merchant-labeled.
  Nothing about the order-system export's authenticity is verified by the application.
- **Merchant-provided observation** (`documentedTransactionReference: RST-20261015-0077`):
  "Our table system's own record of this booking: table 14, party of 2, seated 19:40,
  mains fired 19:48 and served 20:02, dessert served 20:44, table cleared 21:05 — not
  just a reservation, an actual service record with specific times." *(Deliberately
  spelled out in full here, not just asserted in general terms: the defence statement
  below quotes these exact details, and a template assembling that statement from
  observation text plus `documentedTransactionReference` can only reproduce facts that
  are actually present in the observation. A vaguer observation would mean a vaguer, less
  useful statement — not a more detailed one the system invented on its own.)*

### 3. Booking confirmation email (`5254a3f7-a27f-4dc2-a1a2-646f61f29eea`)

- **Connection to this transaction**: states the same reference and date, sent to the
  cardholder ahead of the visit.
- **What it could establish**: that a reservation existed for this date/reference. On its
  own, this is weaker than the order record above — not because correspondence is
  inherently weaker as a document type, but because of what *this specific message*
  actually says: a confirmation of a future booking says nothing about whether the visit
  happened. (A different message that happened to confirm attendance would establish
  something different — the assessment is of content, not category.)
- **What the application has checked**: file stored, merchant-labeled. Identical
  verification level to every other record here.
- **Merchant-provided observation** (`documentedTransactionReference: RST-20261015-0077`):
  "Confirms the booking was made for this reference and date. Doesn't on its own confirm
  the visit happened — see the order record and post-visit message for that."

### 4. Post-visit message (`92e8ea08-6273-4b23-8b1c-f0184eeea54e`)

- **Connection to this transaction**: a reply on the same email thread as the booking
  confirmation (same reference by association, not restated in the message body itself —
  noted honestly, since the message text alone doesn't carry the reference).
- **What it could establish**: the customer's own words, the day after the disputed date,
  thanking the venue and referencing specific dishes from the bill (steak, sticky toffee
  pudding) and describing the service as "great." Read plainly, this is a customer
  acknowledging they attended and were served — closer to the primary source's
  "documentation that the services were provided" than the booking confirmation is,
  precisely because of what it says, not because "correspondence" as a category changed.
- **What the application has checked**: file stored, merchant-labeled. Same as every
  other record.
- **Merchant-provided observation** (`documentedTransactionReference: RST-20261015-0077` —
  **flagged, not just filled in**: the reference isn't restated inside this message; the
  merchant is asserting the thread association, which is itself an unverified claim worth
  surfacing, not quietly assumed): "Customer's own message the day after, referencing
  dishes from the bill (steak, sticky toffee pudding) and describing the service
  positively — read together with the order record, this supports the visit happened as
  billed."

## Expected review/draft output (quoted format — the real PR 2 acceptance target)

This replaces an earlier draft of this section that paraphrased each observation into a
new sentence (e.g. "The merchant's table system recorded a seated party of 2..."). That
was a mistake worth naming, not just quietly fixing: a deterministic template must not
freely rewrite merchant prose into new factual claims. What follows is what
`GET /api/disputes/{id}/review`'s `draftText` actually produces from this fixture —
observations quoted verbatim, reference match checked structurally (a plain comparison
against `Dispute.transactionReference`, nothing inferred from content), an explicit,
unconditional statement of what this review does and doesn't establish, and every
payment event/evidence item tagged with its own id — filenames alone aren't unique (two
uploads can share a name), so the id is what actually disambiguates a citation. This is
copied verbatim from a real local run, not hand-typed:

```
DEFENCE REVIEW - RST-20261015-0077
This review checks completeness and declared consistency only. It does not assess
document contents or establish that the service was delivered. No gaps found does not
mean this defence is strong or ready to submit.

PAYMENT HISTORY
Provider-reported transaction RST-20261015-0077: 1 event recorded.
- [event c04061d9-99bf-43f2-bb12-4c27f25838cd] status APPROVED, reported amount
  7950000 (raw provider figure, currency/unit interpretation not confirmed), received
  2026-10-21T20:38:00.383341Z

EVIDENCE
1. bill.txt [evidence 74433221-6762-422a-9049-09f01b5489eb] (documented reference:
   RST-20261015-0077 - MATCHES dispute reference)
   Merchant states: "This is the bill for the disputed booking, reference and date
   both match, total reconciles to the card charge once the rounding to 79,500.00 is
   accounted for."
2. order-record.txt [evidence e439b654-1c0d-49c6-9618-250718cf4bad] (documented
   reference: RST-20261015-0077 - MATCHES dispute reference)
   Merchant states: "Our table system's own record of this booking: table 14, party of
   2, seated 19:40, mains fired 19:48 and served 20:02, dessert served 20:44, table
   cleared 21:05 - not just a reservation, an actual service record with specific
   times."
3. booking-confirmation-email.txt [evidence 5254a3f7-a27f-4dc2-a1a2-646f61f29eea]
   (documented reference: RST-20261015-0077 - MATCHES dispute reference)
   Merchant states: "Confirms the booking was made for this reference and date.
   Doesn't on its own confirm the visit happened - see the order record and
   post-visit message for that."
4. post-visit-message.txt [evidence 92e8ea08-6273-4b23-8b1c-f0184eeea54e] (documented
   reference: RST-20261015-0077 - MATCHES dispute reference)
   Merchant states: "Customer's own message the day after, referencing dishes from the
   bill (steak, sticky toffee pudding) and describing the service positively - read
   together with the order record, this supports the visit happened as billed."

GAPS
None found: every attached evidence item has an observation, and every declared
reference matches the dispute's own transaction reference.
```

Notably, the booking confirmation (evidence 3) still appears — quoted plainly, including
its own hedge ("Doesn't on its own confirm the visit happened"). The earlier paraphrased
version deliberately left it out of the numbered statements because *a person* judged its
content as weaker. A template can't make that judgment — it can only quote what's there
and check the reference, so every observed, correctly-referenced item appears, and any
actual weakness has to be visible in the merchant's own words, not filtered out by the
system on the merchant's behalf.

## What stays explicitly unknown

- No confirmation that the person who dined, paid, and later emailed is the cardholder
  themselves rather than someone authorized to use the card — only that the names/thread
  are internally consistent with each other.
- No independently verified signature, authenticity, or tamper-check on any of the four
  records — every one of them is "merchant uploaded this, and asserted what it says,"
  not a system-verified fact. This applies identically to the bill and to the
  correspondence; neither gets a pass the other doesn't.
- The post-visit message's connection to this specific reference is an asserted thread
  association, not a reference restated in the message text — flagged above, not hidden.

## What this exercise found, feeding back into the plan

- A plain free-text "what does this evidence show" statement was enough to write every
  observation above, confirming the minimal shape doesn't need to be more than: the
  statement text, plus `documentedTransactionReference`.
- `documentedTransactionReference` earned its place: every record above states it
  cleanly *except* record 4, where writing this document surfaced a real, honest gap (an
  asserted thread association, not a restated reference) that a required field would have
  forced into the open rather than left implicit. This is a concrete argument for making
  `documentedTransactionReference` **required**, not optional, on every observation.
- No ranking between document types was needed or used anywhere above — exactly the
  correction applied before this was written. The order record and post-visit message
  both ended up supporting delivery more directly than the booking confirmation, but
  because of what each one specifically says, not because of what kind of document each
  one is.
- A real inconsistency surfaced and got fixed during this exercise, not just in review
  after the fact: the first draft of the table/order observation was a general summary
  ("seated, served, table cleared"), while its defence statement quoted specific details
  (table number, covers, exact times) that weren't actually present in that observation
  text. A template can only assemble a statement from what's actually in the observation
  plus `documentedTransactionReference` — it can't add specificity the merchant never
  typed. The fix was enriching the observation to actually contain those details, not
  vaguing down the statement; the general lesson is that **every defence statement's
  claimed specifics must be checked against the observation text that's supposed to
  produce it**, record by record, not assumed to line up.
- A second real inconsistency was caught after the fact: the bill was originally
  timestamped 19:52 and listed dessert as paid, but the order record shows dessert wasn't
  ordered until 20:35 — the bill claimed to be settled 43 minutes before the item it
  billed for was even ordered. Fixed by moving the bill's time to 20:58, matching the
  order record's own "table closed / bill requested: 20:58." For *this* fixture — the
  baseline supported-defence example — internal timing has to actually hang together
  across every record, the same way the amount reconciliation above does; an unexplained
  contradiction here would undermine the defence it's supposed to support. A genuinely
  unexplained timing conflict is still a useful thing to test — but as a deliberate
  adversarial variant later, not an accident sitting in the baseline case.
- A third correction, this time to the document's own expected-output section rather
  than the fixture: the original "Proposed defence statements" paraphrased every
  observation into new prose and quietly dropped the booking confirmation as "too weak"
  — both judgment calls only a person can make, not something a deterministic template
  can do. Replaced with the quoted format above: verbatim observations, structural
  reference-match checks only, and every observed/correctly-referenced item included,
  weak ones and all. The lesson generalizes past this fixture: anywhere this document
  described what the *system* should produce, it needs to describe only what's
  mechanically derivable from stored fields — never what a careful human reader would
  conclude from the prose.
