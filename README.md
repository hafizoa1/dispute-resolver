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

## Planned

- **Moniepoint integration** - not started yet.
