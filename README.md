# URL Shortener

A URL shortener built with **Java 21, Spring Boot 4, PostgreSQL and Redis**. It creates short links (auto-generated or custom alias), redirects them, supports link expiry, validates input defensively, and returns consistent JSON errors. Redis caching, click analytics and rate limiting are in progress (see [Roadmap](#roadmap)).

**Live demo:** _coming Day 6_
**API docs (Swagger UI):** _coming Day 5_

---

## Table of Contents

1. [Features and Status](#features-and-status)
2. [Tech Stack](#tech-stack)
3. [Architecture](#architecture)
4. [API Reference](#api-reference)
5. [Validation Rules](#validation-rules)
6. [Database Schema](#database-schema)
7. [Key Design Decisions](#key-design-decisions)
8. [Running Locally](#running-locally)
9. [Configuration](#configuration)
10. [Testing](#testing)
11. [Performance](#performance)
12. [Project Structure](#project-structure)
13. [Roadmap](#roadmap)
14. [Known Limitations](#known-limitations)
15. [Future Improvements](#future-improvements)

---

## Features and Status

| Feature | Status |
|---|---|
| Create short URL (Base62 of DB id) | Done |
| `302` redirect | Done |
| Custom aliases (with collision-proof namespace) | Done |
| Link expiry (`410 Gone`) | Done |
| URL and alias validation (scheme allowlist, no IPs, no internal hosts) | Done |
| Consistent JSON error format on every error | Done |
| Flyway-managed schema | Done |
| Redis cache on redirect path | Planned (Day 4) |
| Async click analytics and stats endpoint | Planned (Day 4) |
| Per-IP rate limiting | Planned (Day 5) |
| Swagger / OpenAPI | Planned (Day 5) |
| Testcontainers integration tests | Planned (Day 5) |
| Docker image, CI, deployment | Planned (Day 6) |

**Deliberately out of scope:** user accounts and JWT, frontend UI, QR codes, link editing. Keeping auth out keeps the core small and well-tested.

---

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 | Current LTS, records for immutable DTOs |
| Framework | Spring Boot 4.x | Embedded Tomcat, auto-configuration |
| Database | PostgreSQL 16 | Source of truth, strong constraints |
| Cache | Redis 7 | Fast lookups for the read-heavy redirect path (Day 4) |
| Migrations | Flyway | Versioned, repeatable schema changes |
| Tests | JUnit 5, MockMvc, Testcontainers | Real dependencies in integration tests |
| Packaging | Docker, docker-compose | Same environment everywhere |

---

## Architecture

```
                    ┌─────────────────────────────┐
  Client ──HTTP──▶  │   Spring Boot App           │
 (browser/curl)     │  Controller → Service       │
                    └───────┬─────────────┬───────┘
                            │ 1. check    │ 2. on cache miss
                            ▼             ▼
                      ┌─────────┐    ┌───────────┐
                      │  Redis  │    │ PostgreSQL│
                      │ (cache) │    │ (source   │
                      │ Day 4   │    │ of truth) │
                      └─────────┘    └───────────┘
```

_Replace with `docs/architecture.png` once drawn._

**Current redirect path:** request, look up `short_code` in PostgreSQL (unique index), check expiry, respond `302`.
**Planned (Day 4):** check Redis first, fall back to PostgreSQL on a miss, populate the cache with a TTL capped by the link's expiry, and record the click asynchronously. Redis is a cache, never the source of truth.

---

## API Reference

### Create a short URL

`POST /api/v1/urls`

```json
{
  "longUrl": "https://example.com/some/very/long/path?x=1",
  "customAlias": "my-link",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

`customAlias` and `expiresAt` are optional.

Response `201 Created`:

```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "longUrl": "https://example.com/some/very/long/path?x=1",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

### Redirect

`GET /{code}` returns `302 Found` with a `Location` header.

### Link statistics _(Day 4)_

`GET /api/v1/urls/{code}/stats` will return click count, creation time and expiry.

### Status codes

| Situation | Code |
|---|---|
| Link created | `201` |
| Invalid URL, invalid alias, invalid JSON, validation failure | `400` |
| Code does not exist | `404` |
| Wrong HTTP method | `405` |
| Custom alias already taken | `409` |
| Link has expired | `410` |
| Rate limit exceeded _(Day 5)_ | `429` |
| Unexpected server error | `500` (generic message, details only in server logs) |

`404` means the code never existed; `410` means it existed and is intentionally gone.

### Error format

Every error, including Spring's own (bad JSON, wrong method, unknown route), uses one shape:

```json
{
  "timestamp": "2026-10-28T09:11:20.587Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Only http or https URLs are allowed",
  "path": "/api/v1/urls"
}
```

---

## Validation Rules

### Long URL

- Maximum 2048 characters
- Scheme must be `http` or `https` (allowlist, so `javascript:`, `data:`, `file:` and `ftp:` are rejected)
- No embedded credentials (`user:pass@host`)
- No IP address hosts (IPv4 in any encoding, or IPv6)
- Host must be a fully qualified domain name (no `localhost`, no single-label hosts)
- No internal suffixes (`.local`, `.internal`, `.localhost`, `.home.arpa`)
- No links back to this service itself (prevents redirect loops)

### Custom alias

- 3 to 32 characters from `A-Z a-z 0-9 - _`
- Must contain at least one `-` or `_`
- Must not be a reserved word (`api`, `actuator`, `swagger-ui`, `v3`, `health`, `admin`, `error`)
- Case-sensitive (`My-Link` and `my-link` are different links)

The `-`/`_` requirement guarantees a custom alias can never equal an auto-generated code, because Base62 output contains neither character.

### Optional expiry

`expiresAt` must be in the future. A link whose expiry equals the current instant counts as expired.

---

## Database Schema

```sql
CREATE TABLE urls (
    id          BIGSERIAL PRIMARY KEY,
    short_code  VARCHAR(32) UNIQUE,
    long_url    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    click_count BIGINT      NOT NULL DEFAULT 0
);
```

Managed by Flyway (`src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`: it checks the schema but never changes it.

---

## Key Design Decisions

**1. Short codes: Base62 of the database ID.**
The row is inserted first to obtain its ID, then `short_code` is set from `Base62.encode(id)` in the same transaction. IDs come from the database sequence, so two concurrent requests can never get the same code and no retry loop is needed.
_Tradeoff:_ two SQL statements per create, and codes are sequential and guessable.

**2. Hash-and-truncate (MD5/SHA) rejected.**
Truncated hashes collide and need detection and retry logic. Sequence-based IDs avoid the problem entirely.

**3. Custom alias namespace separated by construction.**
Aliases must contain `-` or `_`, which Base62 never emits. This makes a collision between a future generated code and an existing alias impossible, instead of merely unlikely.

**4. The database constraint is the final authority on uniqueness.**
Alias creation pre-checks with `existsByShortCode` for a friendly error, and also catches `DataIntegrityViolationException` to handle the race where two requests pass the check together.

**5. `302` instead of `301`.**
Browsers cache `301`, hiding repeat clicks from analytics. _Tradeoff:_ every click reaches the server.

**6. `410 Gone` for expired links.**
Distinguishes "never existed" (`404`) from "existed and was removed".

**7. Allowlist-based URL validation.**
Only `http`/`https` are accepted, and IP literals and internal hostnames are rejected so the service cannot be used to launder links to internal addresses.

**8. One exception base class and one error handler.**
Business exceptions extend `ApiException`, which carries its HTTP status. `GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`, so framework errors (malformed JSON, `405`, validation) also come back in the same JSON shape. Unexpected errors return a generic message and log the details server-side.

**9. Injectable `Clock`.**
Expiry logic uses an injected `Clock`, so tests can fix time instead of sleeping.

**10. Flyway over Hibernate auto-DDL.**
The schema is version-controlled code applied identically in every environment.

**11. Configuration through environment variables.**
Every setting has a local default (`${DB_URL:...}`), so a fresh clone runs with zero setup, and production overrides come from the environment. Defaults are for local development only; real secrets are never committed.

---

## Running Locally

**Prerequisites:** JDK 21, Docker, Git

```bash
# 1. Clone
git clone https://github.com/<your-username>/url-shortener.git
cd url-shortener

# 2. Start PostgreSQL and Redis
docker compose up -d
docker compose ps          # both should show "healthy"

# 3. Run the app
./mvnw spring-boot:run

# 4. Health check
curl http://localhost:8080/actuator/health
```

Try it:

```bash
# create
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com", "customAlias": "my-link"}'

# follow it
curl -i http://localhost:8080/my-link
```

Verify behaviour end to end with the included script (13 checks, plus an expiry test):

```bash
./verify-day3.sh --expiry
```

Stop everything:

```bash
docker compose down        # keeps data
docker compose down -v     # deletes data too
```

---

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/shortener` | JDBC connection string |
| `DB_USER` | `shortener` | Database user |
| `DB_PASSWORD` | `shortener` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `APP_BASE_URL` | `http://localhost:8080` | Base used to build `shortUrl` |

---

## Testing

```bash
./mvnw test
```

Current tests:
- `Base62Test`: boundary values (digit-count rollovers), negative input
- `LinkPolicyTest`: accepted and rejected URLs and aliases, including SSRF-style bypass attempts
- `UrlTest`: expiry boundary behaviour with a fixed clock
- `verify-day3.sh`: black-box check of status codes and error bodies against a running app

Planned (Day 5): Testcontainers integration tests with real PostgreSQL and Redis, replacing the default `contextLoads` test that depends on a locally running database.

---

## Performance

_Filled in on Day 7 after load testing with k6. Only measured numbers go here._

| Scenario | Requests/sec | p95 latency |
|---|---|---|
| Redirect, no cache | TBD | TBD |
| Redirect, Redis cache | TBD | TBD |

---

## Project Structure

```
src/main/java/com/bhavesh/urlshortner/
├── controller/    HTTP layer: parse request, call service, shape response
├── service/       UrlService (business logic), LinkPolicy (validation rules)
├── repository/    Spring Data JPA repository
├── model/         JPA entity
├── dto/           Request/response records and ApiError
├── exception/     ApiException hierarchy and GlobalExceptionHandler
├── config/        Clock bean (Redis, rate limiter, async to follow)
└── util/          Base62 encoder
```

Entities are never returned from the API. DTOs are the public contract; entities are a storage detail.

---

## Roadmap

- [x] Day 1: Project setup, Docker Compose, Flyway schema, API design
- [x] Day 2: Entity, repository, service, controllers, Base62, create and redirect
- [x] Day 3: URL and alias validation, custom aliases, expiry, global error handling
- [ ] Day 4: Redis caching, async click analytics, stats endpoint
- [ ] Day 5: Rate limiting, Testcontainers tests, Swagger/OpenAPI
- [ ] Day 6: Dockerfile, CI, deployment
- [ ] Day 7: Load test, final README polish

---

## Known Limitations

- **Hostnames that resolve to private IPs** (for example `127.0.0.1.nip.io`) are not blocked. Closing this needs DNS resolution at creation and again at redirect time (to defend against DNS rebinding).
- **Codes are sequential and guessable.** Mitigations: shuffled alphabet or random generation with collision retry.
- **Expired rows are not deleted.** They return `410` forever. A scheduled cleanup job is a future improvement.
- **Aliases are case-sensitive**, a deliberate simplification.

---

## Future Improvements

- User accounts and JWT auth, with per-user link management
- Non-guessable codes
- Distributed ID generation (Snowflake-style) for multi-instance scale
- Read replicas and sharding by short code
- Message queue (Kafka or RabbitMQ) for click events
- Cache stampede protection on hot keys
- Malicious-link detection (for example Google Safe Browsing)
- Scheduled cleanup of expired links

---

## License

MIT
