# URL Shortener

A production-style URL shortener built with **Java 21, Spring Boot 3, PostgreSQL and Redis**. It creates short links, redirects with low latency using a Redis cache, supports custom aliases and expiry, tracks clicks asynchronously, and rate-limits abusive clients.

**Live demo:** _coming Day 6_
**API docs (Swagger UI):** _coming Day 5_

---

## Table of Contents

1. [Scope](#scope)
2. [Tech Stack](#tech-stack)
3. [Architecture](#architecture)
4. [API Contract](#api-contract)
5. [Database Schema](#database-schema)
6. [Key Design Decisions](#key-design-decisions)
7. [Running Locally](#running-locally)
8. [Configuration](#configuration)
9. [Testing](#testing)
10. [Performance](#performance)
11. [Project Structure](#project-structure)
12. [Roadmap](#roadmap)
13. [Future Improvements](#future-improvements)

---

## Scope

**In scope**
- Create a short URL from a long URL
- Redirect short URL to the original URL
- Optional custom alias and optional expiry date
- Click statistics per link
- Redis caching on the redirect path
- Per-IP rate limiting on link creation

**Deliberately out of scope**
- User accounts, authentication, JWT
- Frontend UI (Swagger UI is the interface)
- QR codes, link editing, bulk import

_Cutting auth keeps the core small, well-tested and deployable within a week. It is the first item under Future Improvements._

---

## Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 | Current LTS, records for immutable DTOs |
| Framework | Spring Boot 3.x | Embedded Tomcat, auto-configuration |
| Database | PostgreSQL 16 | Reliable source of truth, strong constraints |
| Cache | Redis 7 | Sub-millisecond lookups for the read-heavy redirect path |
| Migrations | Flyway | Versioned, repeatable schema changes |
| Testing | JUnit 5, MockMvc, Testcontainers | Integration tests against real Postgres and Redis |
| Packaging | Docker, docker-compose | Same environment everywhere |
| CI | GitHub Actions | Build and test on every push |

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
                      └─────────┘    │ of truth) │
                                     └───────────┘
```

_Replace this with `docs/architecture.png` once drawn._

**Redirect path**
1. Request arrives at `GET /{code}`.
2. Look up `code` in Redis.
3. Cache hit: redirect immediately.
4. Cache miss: query PostgreSQL, populate Redis (TTL capped by link expiry), redirect.
5. Record the click asynchronously so it never slows the redirect.

Redis is a cache, not the source of truth. If Redis goes down, the system gets slower but loses no data.

---

## API Contract

Base path for management endpoints: `/api/v1`

### Create a short URL

`POST /api/v1/urls`

Request:
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

`GET /{code}`

Returns `302 Found` with a `Location` header pointing to the original URL.

### Link statistics

`GET /api/v1/urls/{code}/stats`

Response `200 OK`:
```json
{
  "shortCode": "my-link",
  "longUrl": "https://example.com/some/very/long/path?x=1",
  "clickCount": 42,
  "createdAt": "2026-10-26T17:00:00Z",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

### Status codes

| Situation | Code | Reason |
|---|---|---|
| Link created | `201` | A new resource was created |
| Invalid URL or alias format | `400` | Client sent bad input |
| Custom alias already taken | `409` | Conflict with existing resource |
| Code does not exist | `404` | Unknown resource |
| Link has expired | `410` | Existed once, intentionally gone |
| Rate limit exceeded | `429` | Includes a `Retry-After` header |

### Error format

All errors share one JSON shape:
```json
{
  "timestamp": "2026-10-26T17:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Alias 'my-link' is already in use",
  "path": "/api/v1/urls"
}
```

### Reserved aliases

`api`, `actuator`, `swagger-ui`, `v3`, `health`, `admin` cannot be used as custom aliases, so they never shadow real routes.

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

Managed by Flyway (`src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`, so it only verifies the schema and never changes it.

---

## Key Design Decisions

**1. Short codes: Base62 of the database ID**
The auto-increment ID is encoded with `0-9a-zA-Z`. Seven characters give about 3.5 trillion combinations. IDs are unique, so codes never collide and no retry loop is needed.
_Tradeoff:_ codes are sequential and guessable. Mitigations: shuffled alphabet, or random codes with collision retry.

**2. Why not hash and truncate (MD5/SHA)?**
Truncating a hash causes collisions that must be detected and resolved. An ID-based approach avoids the problem entirely.

**3. 302 instead of 301**
Browsers cache 301 redirects, so repeat clicks would never reach the server and analytics would undercount. 302 keeps every click visible.
_Tradeoff:_ slightly more load on the server.

**4. Redis cache-aside on the redirect path**
Redirects vastly outnumber creates. Caching `shortCode -> longUrl` keeps the hot path off the database. TTL is capped by the link's expiry so expired links are never served from cache.

**5. Asynchronous click tracking**
Click recording runs off the request thread so redirect latency stays low.
_Tradeoff:_ a crash can lose a few in-flight clicks. Acceptable for analytics, and a queue would remove the risk.

**6. 410 Gone for expired links**
404 means "never existed", 410 means "existed and was removed". Clients and crawlers can tell the difference.

**7. Flyway over Hibernate auto-DDL**
The schema is version-controlled code, applied identically in every environment.

**8. Configuration through environment variables**
Every setting has a local default (`${DB_URL:...}`), so the same build runs on a laptop and in production.

---

## Running Locally

**Prerequisites:** JDK 21, Docker Desktop, Git

```bash
# 1. Clone
git clone https://github.com/<your-username>/url-shortener.git
cd url-shortener

# 2. Start PostgreSQL and Redis
docker compose up -d
docker compose ps          # both should show "healthy"

# 3. Run the app
./mvnw spring-boot:run

# 4. Check health
curl http://localhost:8080/actuator/health
```

Try it (once Day 2 is done):
```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://example.com"}'

curl -i http://localhost:8080/<shortCode>
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
| `APP_BASE_URL` | `http://localhost:8080` | Base used to build `shortUrl` (added Day 2) |

Local defaults are for development only. Never commit real secrets.

---

## Testing

```bash
./mvnw test
```

- Unit tests: Base62 encoding, service logic
- Integration tests: Testcontainers (real Postgres and Redis) with MockMvc
- Cases covered: happy path, expired link, missing link, duplicate alias, invalid URL, rate limit

_Coverage and CI badge: added Day 5-6._

---

## Performance

_Filled in on Day 7 after load testing with k6._

| Scenario | Requests/sec | p95 latency |
|---|---|---|
| Redirect, no cache | TBD | TBD |
| Redirect, Redis cache | TBD | TBD |

---

## Project Structure

```
src/main/java/com/yourname/shortener/
├── controller/    HTTP layer: parse request, call service, return response
├── service/       Business logic
├── repository/    Database access (Spring Data JPA)
├── model/         JPA entities
├── dto/           Request and response objects (the API contract)
├── exception/     Custom exceptions and global handler
├── config/        Redis, rate limiter, async configuration
└── util/          Base62 and other pure helpers
```

Entities are never returned from the API. DTOs are the public contract, entities are a storage detail.

---

## Roadmap

- [x] Day 1: Project setup, Docker Compose, Flyway schema, API design
- [ ] Day 2: Entity, repository, service, controller, Base62, redirect
- [ ] Day 3: URL validation, custom alias, expiry, error handling
- [ ] Day 4: Redis caching, async click analytics, stats endpoint
- [ ] Day 5: Rate limiting, tests, Swagger/OpenAPI
- [ ] Day 6: Dockerfile, CI, deployment
- [ ] Day 7: Load test, README polish

---

## Future Improvements

- User accounts and JWT auth, with per-user link management
- Non-guessable codes (shuffled alphabet or random generation)
- Distributed ID generation (Snowflake-style) for multi-instance scale
- Database read replicas and sharding by short code
- Message queue (Kafka or RabbitMQ) for click events
- Cache stampede protection on hot keys
- Malicious-link detection (Google Safe Browsing)
- CDN or edge caching for redirects

---

## License

MIT
