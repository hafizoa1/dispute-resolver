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

## The disputed transaction

- Dispute id: `97f3bf47-cfb2-44a5-936d-b96110ae1a38`
- Transaction reference: `RST-20261015-0077`
- Disputed amount: NGN 79,500.00 (`amountMinor: 7950000`)
- Matching Moniepoint purchase event id: `c04061d9-99bf-43f2-bb12-4c27f25838cd` —
  `transactionStatus: APPROVED`, `amountRaw: 7950000`, `merchantReference: table-14-20261015`.
  One event only; no conflicting history.

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
  "Our table system's own record of this booking being seated, served, and the table
  cleared — not just a reservation, an actual service record."

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

## Proposed defence statements

Phrased the way the primary source phrases its own examples — a factual claim plus what
backs it — not an invented stronger claim:

1. "The provider reported this transaction (`RST-20261015-0077`) as `APPROVED` for NGN
   79,500.00, with no conflicting event history." — backed by purchase event
   `c04061d9-99bf-43f2-bb12-4c27f25838cd`. *(States only what was reported — not, on its
   own, proof of service delivery — per the plan's payment-language correction.)*
2. "The merchant's table system recorded a seated party of 2 at table 14 for this
   reference, from 19:40 to 21:05, with courses fired and served." — backed by evidence
   `e439b654-1c0d-49c6-9618-250718cf4bad`.
3. "The bill issued for this reference and date, reconciled for card rounding, matches
   the disputed amount." — backed by evidence `74433221-6762-422a-9049-09f01b5489eb`.
4. "The cardholder's own message the day after the visit describes specific dishes
   matching the bill and describes the service positively." — backed by evidence
   `92e8ea08-6273-4b23-8b1c-f0184eeea54e`, **with the thread-association caveat from
   record 4 stated alongside it, not omitted**.

The booking confirmation (record 3) does **not** get its own numbered defence statement —
on its own content, it only establishes a reservation existed, which isn't in dispute and
doesn't address "service not received." It's still part of the evidence index as context,
but the draft shouldn't manufacture a claim it doesn't support.

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
