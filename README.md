# Product Catalog Backend

A complete Java/Spring Boot REST backend for a read-heavy e-commerce catalog. No frontend.

## 1. Technology and scope

- Java **21**, Maven **3.9+**, Spring Boot **3.5.7** (pinned reproducible baseline; not a claim of latest release).
- Spring MVC, Spring JDBC/JdbcClient, PostgreSQL **17**, Flyway, HikariCP.
- Bean Validation, Spring Security OAuth2 Resource Server, Actuator, Micrometer/Prometheus.
- JUnit 5, AssertJ, MockMvc, Testcontainers PostgreSQL integration tests.

Supports product CRUD, category/price filtering, price-ascending cursor pagination, signed cursors, optimistic concurrency, validation, and structured errors. Single currency **EUR**; one category per product. Categories are a small externally managed taxonomy exposed through a read-only endpoint. Local mode seeds Electronics, Books and Home; production does not seed business data.

Not included: inventory, carts, checkout, variants, pricing rules, full-text relevance search, category maintenance, multi-currency conversion, or image uploads. These require separate business decisions.

## 2. Quick start — Docker Compose

Prerequisites: Docker Engine/Desktop and Compose v2. Host Java and Maven are not required for this route.

```bash
cp .env.example .env
# Edit .env: set DB_PASSWORD and a random CURSOR_SECRET of at least 32 UTF-8 bytes.
# Example key generation: openssl rand -hex 32

docker compose up --build -d
docker compose logs -f app
```

Windows PowerShell: use `Copy-Item .env.example .env` for the first command.

The Compose application explicitly uses the **local** profile: writes are unauthenticated and ports bind only to loopback. Do not use this profile for a shared deployment.

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/categories
```

The application can take a little time after PostgreSQL becomes healthy. Follow the logs until Spring Boot reports startup completion. Flyway creates tables and local categories automatically. Products are initially empty.

```bash
docker compose down       # keep data
# Destructive local reset, only if wanted:
# docker compose down -v
```

## 3. Run Java locally

Prerequisites: JDK 21, Maven 3.9+, Docker (for the supplied database).

```bash
cp .env.example .env
# Edit the values first.
docker compose up -d db
# Bash only: load the trusted local .env file into this shell.
set -a
. ./.env
set +a
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

On PowerShell, set `$env:DB_PASSWORD`, `$env:CURSOR_SECRET` and `$env:SPRING_PROFILES_ACTIVE="local"` directly. A locally run Java process does **not** automatically load `.env`.

Build an executable jar:

```bash
mvn clean verify
java -jar target/product-catalog-1.0.0.jar --spring.profiles.active=local
```

`verify` requires Docker for integration tests by default. `mvn test` runs unit/MVC security tests without Docker. See testing below for an external-database alternative.

## 4. Project structure

```text
product-catalog/
├── pom.xml
├── Dockerfile
├── compose.yaml
├── .env.example
├── .gitignore
├── .dockerignore
├── README.md
├── VERIFICATION.md
├── requests/
│   └── catalog.http
├── scripts/
│   └── load-products.sql
├── src/main/java/com/example/catalog/
│   ├── CatalogApplication.java
│   ├── category/
│   │   └── CategoryController.java
│   ├── product/
│   │   ├── api/
│   │   │   ├── ProductController.java
│   │   │   ├── ProductWriteRequest.java
│   │   │   ├── ProductResponse.java
│   │   │   └── ProductSummary.java
│   │   ├── application/
│   │   │   ├── ProductQuery.java
│   │   │   └── ProductService.java
│   │   ├── domain/
│   │   │   └── Product.java
│   │   └── persistence/
│   │       └── ProductRepository.java
│   └── shared/
│       ├── error/
│       │   ├── ApiException.java
│       │   └── ApiExceptionHandler.java
│       ├── http/
│       │   └── ProductEtags.java
│       ├── pagination/
│       │   ├── CursorCodec.java
│       │   ├── CursorPage.java
│       │   └── CursorPosition.java
│       └── security/
│           └── SecurityConfig.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   ├── application-prod.yml
│   └── db/
│       ├── migration/V1__create_catalog.sql
│       └── local/V2__seed_local_categories.sql
└── src/test/java/com/example/catalog/
    ├── CursorCodecTest.java
    ├── ProductEtagsTest.java
    ├── ProductQueryTest.java
    ├── SecurityConfigTest.java
    └── CatalogIT.java
```

`src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` selects Mockito subclass mocks, avoiding a requirement for dynamic agent attachment for these non-final collaborators.

## 5. Architecture and request flow

One deployable service, organized by feature. Controllers own HTTP concerns; the service owns transaction boundaries and pagination orchestration; the repository owns parameterized SQL. Immutable records separate API contracts from persisted data. Category listing is intentionally a small direct read; it has no business workflow requiring additional layers.

For writes, validation happens at the API boundary, then a short database transaction performs the mutation. Database constraints remain the final integrity guard. For reads, a read-only transaction executes a bounded query and returns DTOs.

Spring JDBC is deliberate: a handful of tables and important listing queries benefit from explicit SQL. JPA could also implement this design; JDBC avoids lazy loading/N+1 concerns and makes seek pagination clear. Synchronous MVC matches blocking JDBC; WebFlux adds no benefit while database access remains blocking.

No Redis, event broker, search cluster, partitioning, distributed transactions, or independently deployed microservices are necessary for this scope.

## 6. Why PostgreSQL

A few hundred thousand products is a reasonable starting dataset for a single correctly indexed PostgreSQL primary. Actual capacity depends on request rate, hardware, row width, update contention and latency objectives; no throughput guarantee is implied.

| Option | Assessment |
|---|---|
| PostgreSQL | Preferred: transactions, unique/FK/check constraints, range filtering, ordered B-tree access, operational simplicity |
| MongoDB | Useful for variable documents; structured CRUD and category/price filtering do not require a document database |
| Redis | Potential hot-detail cache later; avoid unnecessary list-cache invalidation now |

Optional heterogeneous attributes could later live in PostgreSQL JSONB. Do not use unindexed JSON filtering as a replacement for explicit columns for common query predicates.

## 7. Schema and indexes

`category`: identity ID and unique name.

`product`: identity ID, unique normalized SKU, name, optional description, category FK, `NUMERIC(12,2)` price, version, creation/update timestamps.

Java uses `BigDecimal`; never `double` for exact money. Maximum price is 9,999,999,999.99 EUR. The API rejects fractional precision beyond two digits. Product names are trimmed. SKU input uses ASCII letters, digits, dots, underscores and hyphens; the first character must be alphanumeric. SKU storage is uppercase using `Locale.ROOT`, so `phone-001` conflicts with `PHONE-001`.

| Access pattern | Index |
|---|---|
| Detail by ID | Primary key `(id)` |
| Unique SKU / future SKU lookup | Unique `(sku)` |
| Category with optional price range, sorted by price and ID | `(category_id, price, id)` |
| Global list with optional price range | `(price, id)` |

The category-leading index serves equality on category followed by range/ordering on price. The separate global index avoids relying on category-leading access for queries without category. Extra indexes incur write/storage costs and should follow measured query demand.

One category per product is an explicit scope decision. Many-to-many categories would need a join table, duplicate-safe listing and a revised query/index strategy.

Hard deletion is used. There is no order service in this scope; a real checkout/order system should preserve purchased product descriptions/prices in order-line snapshots.

## 8. REST API

Base path `/api/v1`.

| Method | Path | Result |
|---|---|---|
| POST | `/products` | 201, product body, Location and ETag |
| GET | `/products/{id}` | 200 and ETag; 404 when missing |
| GET | `/products` | 200, cursor page |
| PUT | `/products/{id}` | Full replacement of mutable fields; 200 and new ETag |
| DELETE | `/products/{id}` | Conditional hard delete; 204 |
| GET | `/categories` | Read-only taxonomy list |

PUT replaces SKU, name, description, category and price. It does not upsert a missing product. ID, timestamps and version are server-controlled. Unknown JSON fields are rejected. A null or omitted description clears it. PATCH is intentionally not implemented.

Read-only taxonomy listing assumes a small bounded category set; if taxonomy becomes large, paginate it separately.

### Create

Use a category ID returned by `/categories` (fresh local database IDs begin at 1).

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"PHONE-001","name":"Example Phone","description":"128 GB smartphone","categoryId":1,"price":499.99}'
```

Example response:

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/products/1
ETag: "product-1-v0"
```

```json
{"id":1,"sku":"PHONE-001","name":"Example Phone","description":"128 GB smartphone","categoryId":1,"price":499.99,"currency":"EUR","version":0,"createdAt":"2026-10-27T10:00:00Z","updatedAt":"2026-10-27T10:00:00Z"}
```

IDs/timestamps are illustrative. POST does not implement an Idempotency-Key store. If a create response is lost, retrying the same SKU produces 409 rather than a duplicate. Exact response replay would require a separately specified idempotency contract.

### Retrieve and list

```bash
curl -i http://localhost:8080/api/v1/products/1
curl --get http://localhost:8080/api/v1/products \
  --data-urlencode 'categoryId=1' \
  --data-urlencode 'minPrice=100' \
  --data-urlencode 'maxPrice=700' \
  --data-urlencode 'limit=20'
```

Filters are optional; price boundaries are inclusive. Default limit 20, accepted range 1–100. An empty result returns `200`, `items: []`, `hasNext: false`, `nextCursor: null`. A positive but nonexistent category simply produces an empty list.

```json
{
  "items":[{"id":1,"sku":"PHONE-001","name":"Example Phone","categoryId":1,"price":499.99,"currency":"EUR"}],
  "hasNext":false,
  "nextCursor":null
}
```

Listing omits descriptions to reduce payload and table data transfer. Exact totals and arbitrary sorting are not included.

### Update and delete

Always use the ETag from the latest GET/create/update response:

```bash
curl -i -X PUT http://localhost:8080/api/v1/products/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "product-1-v0"' \
  -d '{"sku":"PHONE-001","name":"Example Phone Updated","description":null,"categoryId":1,"price":459.99}'

curl -i -X DELETE http://localhost:8080/api/v1/products/1 \
  -H 'If-Match: "product-1-v1"'
```

`requests/catalog.http` contains the same flow for IDE HTTP clients.

## 9. Cursor pagination

Ordering is fixed: `price ASC, id ASC`. ID gives a unique tie-breaker for equal prices. SQL includes only predicates actually supplied; values are always bound parameters.

```sql
SELECT id, sku, name, category_id, price
FROM product
WHERE category_id = :categoryId
  AND price >= :minPrice AND price <= :maxPrice
  AND (price, id) > (:lastPrice, :lastId)
ORDER BY price ASC, id ASC
LIMIT :fetchSize;
```

First-page SQL omits the seek predicate. Fetch `limit + 1` to determine `hasNext`; build the next cursor from the last **returned** row, not the extra row. There is no count query on the browsing path.

Pass the returned cursor unchanged with the same filters:

```bash
curl --get http://localhost:8080/api/v1/products \
  --data-urlencode 'categoryId=1' \
  --data-urlencode 'minPrice=100' \
  --data-urlencode 'maxPrice=700' \
  --data-urlencode 'cursor=PASTE_RETURNED_CURSOR'
```

Cursor format is Base64URL(JSON) plus HMAC-SHA256 signature. Payload includes version, last price/ID, filter fingerprint and sort. Decimal filter values are canonicalized, so `10` and `10.00` are equivalent. Page size can change between requests. Signature comparison is constant-time. Tokens larger than 2048 characters are rejected. Encoding is **not encryption**.

All instances must share the same secret. Current cursors have no time expiry. Rotating the signing secret invalidates existing cursors; clients restart from the first page. Overlapping-key rotation is a future enhancement.

Keyset pagination avoids increasing OFFSET work and supports sequential browsing, not arbitrary page-number jumps. It is a **live view**, not a multi-request snapshot: price/category changes can move rows across boundaries and cause omissions or repeats. Equal-price tie-breaking alone does not solve concurrent mutation. Snapshot-consistent exports need a separate design; do not keep database transactions open across HTTP requests.

## 10. Concurrent writes and consistency

Updates use one atomic statement:

```sql
UPDATE product
SET ..., version = version + 1, updated_at = clock_timestamp()
WHERE id = :id AND version = :expectedVersion
RETURNING *;
```

Deletes similarly require ID/version. There is no read-then-write race and no long-lived pessimistic lock. Two writers using the same version cannot both win. PostgreSQL performs the conditional mutation atomically; transaction boundaries are at the service layer.

The API accepts exactly **one concrete strong product ETag**. Weak tags, wildcard `*` and lists are outside this API's supported If-Match contract and return 400. An ETag belonging to another product returns 412. A missing header returns 428. A stale version or deleted/missing product on a conditional write returns 412; detail GET returns 404. Repeating a successful DELETE with its old ETag therefore returns 412; the resource remains deleted.

Uniqueness and FK integrity are enforced in PostgreSQL, including under concurrent requests. Do not rely only on pre-insert existence checks. Timestamps are database-generated; updates use database wall-clock time.

All baseline reads use the primary, providing immediate post-commit visibility for subsequent independent reads. This is not a guarantee that no other writer can change the record before that read. There are no replicas or caches in the baseline.

## 11. Errors and validation

Application errors use Spring `ProblemDetail` and `application/problem+json`. Field validation adds an `errors` array. Database details are logged server-side and are not returned to clients.

| Situation | Status |
|---|---|
| Invalid JSON, unknown property, field/filter/cursor error | 400 |
| Missing category on write | 400 |
| Missing product on GET | 404 |
| Duplicate SKU | 409 |
| Failed conditional write | 412 |
| Missing If-Match | 428 |
| Unexpected database access failure | 503 |
| Unrecognized integrity failure | 500 |
| Anonymous write in secured mode | 401 |
| Authenticated caller without required scope | 403 |

Authentication failures are handled by Spring Security's bearer-token handlers, outside MVC advice; they are not promised to use the same ProblemDetail body.

The handler checks SQLState instead of parsing database error text. No SQL values or client-supplied sort expressions are concatenated into queries.

## 12. Security and configuration

`local`: explicit development opt-in, all endpoints permitted, local taxonomy seeds enabled.

`prod`: JWT authentication, signature/issuer/audience validation using the configured provider. Catalog GETs and health probes are public; writes require `catalog.write`; operational endpoints require `catalog.ops`. No cookies/sessions, hence CSRF is disabled for this bearer-token API. Cross-origin access is not enabled because no browser client is in scope.

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL='jdbc:postgresql://db-host:5432/catalog'
export DB_USERNAME='catalog_runtime'
export DB_PASSWORD='...'
export CURSOR_SECRET='...at least 32 bytes of cryptographically random secret...'
export JWT_ISSUER_URI='https://identity-provider.example/issuer'
export JWT_AUDIENCE='product-catalog'
java -jar target/product-catalog-1.0.0.jar
```

Do not combine `local` and `prod`. Without `local`, security requires a JwtDecoder configuration; the supplied `prod` profile configures it from the issuer and audience. Missing required secrets/configuration intentionally prevents startup. Tokens must contain standard `scope` or `scp` claims; Spring maps these to `SCOPE_...` authorities.

Production taxonomy should be provisioned through a controlled migration/admin process before accepting product writes. The supplied application runs Flyway at startup; environments with strict least privilege should run migrations as a separate deployment job and disable application Flyway with `SPRING_FLYWAY_ENABLED=false`.

Use TLS at the ingress/database boundary, secret injection, network-restricted operational endpoints, upstream rate limiting, and an explicit request-body size cap at the gateway. The project does not implement a distributed rate limiter or a streaming body-size filter. Configure the deployment appropriately rather than assuming Tomcat header limits cap JSON bodies.

| Variable | Meaning/default |
|---|---|
| DB_URL | `jdbc:postgresql://localhost:5432/catalog` |
| DB_USERNAME | `catalog` |
| DB_PASSWORD | Required |
| DB_POOL_SIZE | 10 per application instance |
| CURSOR_SECRET | Required, at least 32 UTF-8 bytes; use a random value |
| PORT | 8080 |
| JWT_ISSUER_URI | Required for prod profile |
| JWT_AUDIENCE | Required for prod profile |

Spring Boot is pinned to a reproducible baseline. Before production deployment, review dependency/image security updates and upgrade through the same test suite. Container tags are pinned by version but not digest.

## 13. Observability and scaling

- `/actuator/health`: health summary; no sensitive details.
- `/actuator/health/liveness`: application liveness.
- `/actuator/health/readiness`: readiness including database availability.
- `/actuator/prometheus`: metrics (protected outside local).
- Hikari connection acquisition timeout: 3 seconds.
- JDBC query timeout: 3 seconds; PostgreSQL session statement timeout: 3 seconds.

Track p95/p99 endpoint latency, errors, request rate, database query latency, CPU/I/O and connection saturation. Bound total pool capacity across all replicas, leaving PostgreSQL headroom for maintenance and migrations.

Start with a managed HA PostgreSQL primary, backups and tested restores, plus stateless application replicas behind a load balancer. HA/failover, backups, TLS, distributed tracing export and dashboards are deployment concerns, not provisions made by this local Compose stack.

Add read replicas only if observed load warrants the replication-lag trade-off. Add a detail cache for demonstrated hot keys with an explicit invalidation strategy; arbitrary filter-list caching is much harder. Add a search projection only when relevance/facets require it. Sharding is not justified by product count alone.

## 14. Tests

```bash
mvn test           # cursor, query, ETag and secured MVC tests; no Docker
mvn verify         # above + real PostgreSQL integration tests via Testcontainers
```

Integration tests **fail** if Docker/database access is unavailable; they are not silently skipped. They use PostgreSQL rather than H2 because row comparisons, RETURNING, SQLState, migrations and concurrent updates must match the real datastore.

An explicitly supplied external **disposable test database** can replace Docker:

```bash
mvn verify \
  -Dcatalog.it.db-url=jdbc:postgresql://localhost:5432/catalog_test \
  -Dcatalog.it.db-user=catalog \
  -Dcatalog.it.db-password=catalog
```

**Never point these tests at production or a database whose products you need.** `CatalogIT` truncates the product table and restarts its identity before each test. Use a dedicated database. Flyway initializes the schema and local categories. Testcontainers uses an isolated temporary database by default.

Coverage includes:
- CRUD and full replacement; Location/ETag responses.
- Missing/stale preconditions and conditional deletion.
- Case-insensitive unique SKU, including simultaneous creates.
- Missing category, invalid monetary values, invalid filters.
- Equal-price pagination, inclusive boundaries, empty pages and filter-bound cursors.
- Signed cursor round-trip, tampering, malformed/oversized tokens and canonical decimal filters.
- Simultaneous updates using one version: exactly one succeeds.
- Public reads and authenticated/scoped writes in secured mode.

See `VERIFICATION.md` for what was actually executed in the generation environment. Included tests are not the same as executed/passing tests.

## 15. Representative load and index validation

The optional script adds 300,000 generated products to a **development** database. It uses one category and a deterministic price distribution; it is only a baseline. Create more realistic category skew, product sizes and hot keys for capacity planning.

```bash
docker compose exec -T db psql -U catalog -d catalog -v ON_ERROR_STOP=1 < scripts/load-products.sql
```

Example plan inspection (replace category ID if needed):

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, sku, name, category_id, price FROM product
WHERE category_id = 1 AND price BETWEEN 100 AND 700
ORDER BY price, id LIMIT 21;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, sku, name, category_id, price FROM product
WHERE price BETWEEN 100 AND 700 AND (price,id) > (400.00,10000)
ORDER BY price,id LIMIT 21;
```

Expect the planner to choose access based on selectivity and statistics, not a universally forced index. Measure warm/cold reads, deep cursor pages, category/price combinations, realistic concurrency, hot-detail reads and mixed writes. Record p95/p99 latency, errors and database saturation before making capacity claims.

## 16. Design references

- PostgreSQL multicolumn indexes: https://www.postgresql.org/docs/17/indexes-multicolumn.html
- PostgreSQL indexes and ORDER BY: https://www.postgresql.org/docs/17/indexes-ordering.html
- PostgreSQL row comparisons: https://www.postgresql.org/docs/17/functions-comparisons.html
- Spring JDBC: https://docs.spring.io/spring-framework/reference/data-access/jdbc.html
- Spring Security JWT resource server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Testcontainers PostgreSQL: https://java.testcontainers.org/modules/databases/postgres/
