# dispute-resolver

Spring Boot backend (Java 17, Maven).

## Run

```bash
./mvnw spring-boot:run
```

Starts on http://localhost:8080.

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

- **Liquibase** for database schema migrations, to be added along with the database (currently in-memory H2 with Hibernate `ddl-auto=create-drop`; switch that to `validate` once Liquibase owns the schema).
