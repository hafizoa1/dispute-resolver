# dispute-resolver

Spring Boot backend (Java 17, Maven). Postgres, with Liquibase owning the schema.

## Run

Needs a local Postgres with a `disputes` database and a `dispute_resolver` role
(password `dispute_resolver`) - both are only defaults, override via `DB_URL`
/`DB_USERNAME`/`DB_PASSWORD` for anything beyond a laptop.

```bash
brew install postgresql@17
brew services start postgresql@17
createdb disputes
psql -d disputes -c "CREATE ROLE dispute_resolver LOGIN PASSWORD 'dispute_resolver';"
psql -d disputes -c "GRANT ALL PRIVILEGES ON DATABASE disputes TO dispute_resolver;"
psql -d disputes -c "GRANT ALL ON SCHEMA public TO dispute_resolver;"

./mvnw spring-boot:run
```

Starts on http://localhost:8080. Liquibase runs its changelog
(`src/main/resources/db/changelog`) on startup; Hibernate only validates
entities against it (`ddl-auto=validate`), it never generates DDL.

## Endpoints

| Method | Path                           | Description                                         |
|--------|--------------------------------|-----------------------------------------------------|
| POST   | `/api/disputes`                | Create a dispute                                    |
| GET    | `/api/disputes`                | List disputes, newest first (optional `?status=OPEN`) |
| GET    | `/api/disputes/{id}`           | Fetch a dispute by id                               |
| PATCH  | `/api/disputes/{id}`           | Update any of `transactionReference`, `amountMinor`, `currency`, `description`                 |
| POST   | `/api/disputes/{id}/evidence`  | Upload evidence (multipart: `file`, optional `description`) |
| GET    | `/api/disputes/{id}/evidence`  | List a dispute's evidence                           |

```bash
curl -X POST localhost:8080/api/disputes -H 'Content-Type: application/json' \
  -d '{"transactionReference":"txn_123","amountMinor":4999,"currency":"GBP","description":"Item never arrived"}'
```

```bash
curl -X POST localhost:8080/api/disputes/<id>/evidence -F file=@receipt.pdf -F description="Courier receipt"
```

Evidence files are written to `uploads/` (configurable via `app.evidence.upload-dir`, git-ignored); max 10MB.

## Moniepoint purchase-event receiver (local prototype)

`POST /api/webhooks/moniepoint` receives purchase events for **one configured business**.
No provider credentials, outgoing payment calls, subscription setup, dispute import or bank
submission are included. A real Moniepoint-generated purchase event has not been tested.

The receiver verifies the Base64 HMAC-SHA256 signature using the **exact received bytes**,
then checks `data.businessId`. It saves purchase snapshots in `moniepoint_purchase_events`,
independently of disputes. The original JSON, its SHA-256 hash and receipt time are retained.
Nothing marks a dispute as won or proves service delivery.

This follows the [official POS webhook guide](https://teamapt.atlassian.net/wiki/spaces/EI/pages/1492648078/Webhooks),
reviewed 21 October 2026. Its amount description says minor units but its numerical example
is inconsistent. `amount_raw` therefore preserves the supplied integer **without conversion**;
confirm units and currency with a known sandbox purchase before mapping into monetary facts.
Payment status/code are retained as supplied, including pending/declined events. Missing or
empty transaction references remain unmatched; the raw record is not discarded. Differently
formatted provider timestamps stay in the original JSON until their timezone is confirmed.

### Try locally

In the terminal used to start the app (and the terminal used to send the sample):

```bash
# Synthetic local settings only. Use the real subscription secret/business ID for a provider test.
export MONIEPOINT_WEBHOOK_SECRET='local-synthetic-secret'
export MONIEPOINT_BUSINESS_ID='123'
./mvnw spring-boot:run
```

In the second terminal, with those same environment variables:

```bash
python3 scripts/send_moniepoint_sample.py
python3 scripts/send_moniepoint_sample.py           # same event: still only one saved row
python3 scripts/send_moniepoint_sample.py --tamper  # expect 401, no additional row
```

The script sends **invented signed fixtures**, not a Moniepoint sandbox payment. Inspect:

```sql
SELECT webhook_id, transaction_reference, amount_raw, transaction_status, received_at
FROM moniepoint_purchase_events;
```

| Response | Meaning |
|---|---|
| 204 | Purchase saved, exact redelivery acknowledged, or authenticated unsupported type ignored |
| 400 | Invalid JSON or purchase fields |
| 401 | Missing/invalid authentication |
| 403 | Purchase belongs to another business |
| 409 | Same webhook ID reused with different content; existing snapshot preserved |
| 413 | Body exceeds the local 64 KiB limit |
| 503 | Secret or business configuration missing |

The database unique constraint and `ON CONFLICT` make simultaneous retries idempotent by
`moniepoint-webhook-id`. Separate event IDs for the same transaction are **separate snapshots**,
not additional sales to sum. The header ID and body `eventId` are retained separately because
the documentation's examples do not always match. Timestamp participates in the signature;
there is no invented short expiry window that would reject delayed provider retries. Retain
event IDs for deduplication and confirm retry/replay policy before live use.

The sample payload is synthetic; the public example is a transfer, not a verified card purchase.
Actual purchase shape, optional references, amount units/currency, payment status semantics and
sandbox event generation still need verification. This is a local single-business prototype,
not merchant onboarding or a terminal app. For provider delivery, configure a public HTTPS
callback/tunnel and verify its subscription authentication against an actual event.

### Tests

Against a dedicated local test PostgreSQL database using `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`:

```bash
./mvnw test
```

New tests cover an independently calculated UTF-8 signature vector, byte/header tampering,
missing configuration, persisted raw data, malformed events, wrong business, concurrent retries,
conflicting event IDs and separate status snapshots. Integration tests clear rows with the
reserved `test-` webhook-ID prefix; use a test database.
