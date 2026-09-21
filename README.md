# Foreign Exchange Service

A Spring Boot service that fetches live exchange rates, converts an amount between currencies for a
client (debiting the source currency and crediting the target currency against that client's balances),
and records a queryable history of every conversion. Built for the foreign-exchange take-home assignment
(`.claude/Java-Assignment.pdf`).

There is **no authentication** — the client identifier is supplied by the caller via a header. This is a
deliberate non-goal of the assignment, not an oversight.

## Contents

- [How to run it](#how-to-run-it)
- [Demo clients](#demo-clients)
- [API overview](#api-overview)
- [Client identification](#client-identification)
- [Concurrency strategy](#concurrency-strategy)
- [Idempotency](#idempotency)
- [Rate caching](#rate-caching)
- [Rate provider](#rate-provider)
- [Trade-offs](#trade-offs)
- [What's next](#whats-next)

## How to run it

### Option 1 — containerized (recommended)

```bash
git clone https://github.com/yordanov0502/foreign-exchange.git
cd foreign-exchange
docker compose up --build
```

The first build takes a few minutes while Maven resolves dependencies inside the build stage. Once it's
up:

- The API is on `http://localhost:8081`
- Swagger UI is on `http://localhost:8081/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`)

Stop with `Ctrl+C`. Remove the containers with `docker compose down`, or `docker compose down -v` to also
drop the Postgres data volume (undoing the seeded demo data). If a boot ever fails with a Flyway
`Migration checksum mismatch` (a database volume left over from an older checkout), `docker compose down -v`
followed by `docker compose up` resets to a cleanly migrated state.

No environment variables need to be set for this flow. `docker-compose.yaml` (the Postgres service) and
`docker-compose.override.yaml` (the `app` service, built from the `Dockerfile`) are merged automatically
by a plain `docker compose up`, and the demo database credentials (`mydatabase` / `myuser` / `secret`) are
already baked into both files. Host port `8081` must be free.

### Option 2 — local dev

```bash
./mvnw spring-boot:run          # Linux/macOS
.\mvnw.cmd spring-boot:run      # Windows (PowerShell and cmd)
```

This requires a **running Docker daemon** — Spring Boot's docker-compose integration starts Postgres for
you, but only Postgres. `application.yaml` pins `spring.docker.compose.file: docker-compose.yaml`
explicitly, and Docker Compose skips override-file merging whenever `--file` is passed, so the `app`
service in `docker-compose.override.yaml` is never picked up here — the JVM you're running locally is the
app.

### Tests

```bash
./mvnw test          # Linux/macOS
.\mvnw.cmd test      # Windows (PowerShell and cmd)
```

Also requires a Docker daemon: integration tests spin up a real Postgres via Testcontainers rather than
mocking the database.

### Example requests

```bash
# Current rate for a currency pair
curl "http://localhost:8081/rates?from=USD&to=EUR"

# Convert 100 USD to EUR for CLIENT-001, safe to retry with the same Idempotency-Key
curl -X POST http://localhost:8081/conversions \
  -H "X-Client-Id: CLIENT-001" \
  -H "Idempotency-Key: order-42" \
  -H "Content-Type: application/json" \
  -d '{"sourceCurrency":"USD","targetCurrency":"EUR","sourceAmount":100}'

# This client's conversion history
curl "http://localhost:8081/conversions?clientId=CLIENT-001"

# This client's current balances
curl "http://localhost:8081/clients/CLIENT-001/balances"
```

## Demo clients

Seeded by Flyway migration `V2__seed_demo_clients_and_balances.sql` on first startup, so both flows above
have data to convert immediately:

| Client ID | Currency | Balance |
|---|---|---|
| `CLIENT-001` | USD | 10,000.0000 |
| `CLIENT-001` | EUR | 8,000.0000 |
| `CLIENT-002` | GBP | 5,000.0000 |
| `CLIENT-002` | CHF | 3,000.0000 |

`CLIENT-001` can convert USD↔EUR immediately, and `CLIENT-002` GBP↔CHF. Converting into a currency a
client doesn't already hold
(e.g. `CLIENT-002` GBP→EUR) returns `404 BALANCE_NOT_FOUND` — the service only debits/credits balance
rows that already exist for that client, it does not open new ones on the fly. The same applies on the
source side (converting *from* a currency the client holds no balance in is also `404 BALANCE_NOT_FOUND`);
auto-creating a zero-balance target row on first credit would have been a defensible alternative reading
of the spec, but rejecting both sides with 404 was the deliberate choice, so the set of currencies a
client holds stays under explicit (seeded) control.

## API overview

| Method & path | Purpose |
|---|---|
| `GET /rates?from=&to=` | Current exchange rate for a currency pair |
| `POST /conversions` | Convert an amount for a client, debiting source / crediting target |
| `GET /conversions?transactionId=&date=&clientId=&page=&size=` | Paginated, filtered conversion history |
| `GET /clients/{clientId}/balances` | A client's current balances |

Full request/response shapes, validation constraints and error examples are in Swagger UI once the
service is running.

**Pagination:** `GET /conversions` uses offset pagination (`page`/`size`, Spring Data `PageRequest`) as
prescribed by the assignment's endpoint contract. It provides total counts and random page access, which
suit a filtered history view.

**Timestamps:** all timestamps are stored as `TIMESTAMPTZ` and mapped to `java.time.OffsetDateTime`
end-to-end (entity → domain → API), so every instant carries an explicit UTC offset — unambiguous in the
database, in day-boundary filtering, and in ISO-8601 API responses.

## Client identification

`POST /conversions` and `GET /conversions` take the client via `X-Client-Id` (header) / `clientId` (query
param), not the request body. A header keeps "who is calling" orthogonal to "what they're asking for" —
the same `ConversionRequest` body works regardless of caller, and it mirrors how the assignment's other
identifiers (`Idempotency-Key`) are also carried as headers rather than folded into the payload. Since
there's no authentication layer to derive the caller's identity from, this header **is** the identity
claim — trusted as-is, per the assignment's non-goals.

## Concurrency strategy

**Chosen: pessimistic row locking**, via `SELECT ... FOR UPDATE` on the two `balances` rows involved in a
conversion.

```java
// src/main/java/zetta/foreignexchange/persistence/repository/BalanceRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<BalanceEntity> findAndLockByClientClientIdAndCurrency(String clientId, String currency);
```

`ConversionProcessor.processConversion` (`src/main/java/zetta/foreignexchange/core/processor/ConversionProcessor.java:44-60`)
runs the whole lock → validate-funds → debit → credit → insert sequence inside one `@Transactional`
method. Both balance rows for a client (source and target currency) are locked before either is touched,
in a fixed order:

```java
// ConversionProcessor.java:83-98
// Postgres does not guarantee ORDER BY determines lock acquisition order, so the two rows are
// locked via two sequential calls in ascending currency-code order, regardless of which is the
// base or the quote, to avoid a cross-pair deadlock (e.g. USD->EUR racing EUR->USD).
```

Locking both currencies (not just the source) in a fixed, currency-code-sorted order is what makes two
concurrent opposite-direction conversions for the same client (USD→EUR racing EUR→USD) safe from
deadlock: both requests always attempt to acquire the same first lock, so one simply queues behind the
other rather than each holding one row and waiting on the other's.

**Alternatives considered, and why they were not chosen:**

| Alternative | Why not |
|---|---|
| Optimistic locking (`@Version` on `balances`) | Was in the schema initially, but removed in `V5__drop_balances_version_column.sql`. A conversion always touches *two* balance rows (debit one currency, credit another) — under `@Version`, any collision on either row means retrying the whole rate-lookup + compute + write flow, and retry logic itself has to be written and tested. A blocking row lock removes the retry path entirely: the loser just waits, it doesn't fail and redo. For a service whose write hotspot is "many conversions for the same client in a short window" rather than "many independent writers touching unrelated rows," contention is expected, not exceptional — that favors blocking over retry. |
| Single serialized writer (e.g. one thread/queue per client) | Adds an in-process coordination mechanism (locks or a queue keyed by client) that has to survive restarts and doesn't extend to multiple app instances without an external coordinator. `SELECT ... FOR UPDATE` gets the same effective serialization *per client* for free from Postgres, and works correctly if the service is ever scaled to more than one instance, since the lock lives in the database, not in process memory. |

**Trade-off accepted:** pessimistic locking holds a row lock for the duration of the transaction, which
throttles throughput for a client issuing many concurrent conversions. That's an acceptable cost here —
correctness (no lost updates, no double-spend) matters more than raw throughput for a take-home-sized
service, and the lock scope is exactly two rows for one client, not a table-wide lock.

One rule this design depends on: the rate provider is always called *before* the transaction opens
(`ConversionServiceImpl.convert`, `src/main/java/zetta/foreignexchange/core/service/implementation/ConversionServiceImpl.java:52-65`)
— a DB transaction is never held open across an external HTTP call.

## Idempotency

`POST /conversions` accepts an optional `Idempotency-Key` header, validated as `@Size(max = ...)` and
paired with the caller's `clientId`:

```sql
-- V1__create_clients_balances_and_conversions.sql
CREATE UNIQUE INDEX conversions_client_idempotency_key
    ON conversions (client_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

Flow (`ConversionServiceImpl.convert` and `.processConversion`,
`src/main/java/zetta/foreignexchange/core/service/implementation/ConversionServiceImpl.java:51-122`):

1. Before doing any work, look up an existing conversion for `(clientId, idempotencyKey)`. If found,
   return it — no second debit.
2. If the same key is replayed with **different** currencies or amount, that's a genuine conflict, not a
   retry — the service rejects it with `409 IDEMPOTENCY_KEY_CONFLICT` instead of silently returning
   someone else's result.
3. Two concurrent requests carrying the same new key can both pass step 1 before either has committed.
   The loser then hits the unique constraint above on insert, which surfaces as a
   `DataIntegrityViolationException` — instead of propagating that as a 500, the service catches it and
   re-reads the row the winner just persisted, returning the same result to both callers.

This means idempotency correctness rests on the database constraint, not on an in-process check — the
constraint is the actual source of truth under concurrent replay, and the pre-check is purely a fast
path.

## Rate caching

Rates are cached with Caffeine, keyed by currency pair:

```yaml
# application.yaml
cache:
  currency-rate-pair:
    ttl: 1800s
    max-size: 1000
```

```java
// CacheConfiguration.java
private Cache<Object, Object> buildExchangeRateCache() {
    return Caffeine.newBuilder()
            .maximumSize(cacheProperties.getMaxSize())
            .expireAfterWrite(cacheProperties.getTtl())
            .build();
}
```

**Invalidation choice: none — pure TTL expiry (`expireAfterWrite`, 30 minutes), no manual eviction
endpoint or event-driven invalidation.** FX rates from a public provider don't change fast enough, and
this service doesn't need sub-30-minute freshness, to justify the extra moving part of a manual
invalidation path (an admin endpoint, a scheduled refresh job, or a pub/sub invalidation signal). A fixed
TTL is the simplest mechanism that keeps rates reasonably fresh and bounds how long a stale rate can be
served after the true market rate moves — which is exactly the trade-off KISS asks for here: build the
one thing the requirement needs, not a general-purpose cache-invalidation feature no endpoint asks for.

## Rate provider

[Frankfurter](https://frankfurter.dev) — a free, keyless exchange-rate API:

```java
// FrankfurterFeignClient.java
@FeignClient(name = "frankfurter", url = "${frankfurter.client.url}", configuration = FrankfurterConfiguration.class)
public interface FrankfurterFeignClient {
    @GetMapping(value = "/rate/{base}/{quote}", produces = MediaType.APPLICATION_JSON_VALUE)
    FrankfurterRatePairResponse fetchLatestExchangeRates(@PathVariable String base, @PathVariable String quote);
}
```

```yaml
frankfurter:
  client:
    url: https://api.frankfurter.dev/v2
    connect-timeout: 2s
    read-timeout: 3s
```

No API key is required or configured — Frankfurter's v2 API is open. Provider failures are translated
into domain exceptions before they reach `core/`: an invalid/unknown currency from the provider becomes
`422 UNSUPPORTED_CURRENCY_PAIR`, and a timeout, connection failure or malformed provider response becomes
`502 EXCHANGE_RATE_UNAVAILABLE` — the client never sees a raw Feign or HTTP client exception.

## Trade-offs

- **Spring Cloud OpenFeign for the provider client**, chosen for consistency with the `@FeignClient` style
  used elsewhere by the author, over Spring's native `@HttpExchange` + `RestClient`. This has a known,
  empirically tested version skew: `spring-cloud-dependencies` 2025.1.3 (the latest GA train) is built
  against Spring Boot 4.0.8, while this project runs Boot 4.1.1 — the only train that targets 4.1 is a
  `2026.0.0-SNAPSHOT`, which isn't acceptable to depend on. The skew was validated against the live
  provider (200 with `BigDecimal` deserialization, provider 404, provider 422, and a forced 1 ms read
  timeout) with the full test suite and `mvn checkstyle:check` green before committing to it.

- **Deliberate wire vocabulary asymmetry.** Internally, and on `GET /rates`, the vocabulary is
  `baseCurrency`/`quoteCurrency`. The assignment names `POST /conversions`' fields
  `sourceCurrency`/`sourceAmount`/`targetCurrency`/`targetAmount` verbatim, so `ConversionRequest` and
  `ConversionResponse` alias their `base*`/`quote*` Java fields to those names via `@JsonProperty`, while
  `GET /rates` keeps answering `baseCurrency`/`quoteCurrency` (the brief never names those fields). The
  same principle drives the `GET /rates` **request** side: its query parameters are named `from` and `to`
  strictly because the brief specifies the endpoint as `GET /rates?from=USD&to=EUR` verbatim — internally
  they still bind to `baseCurrency`/`quoteCurrency` method parameters via `@RequestParam("from")` /
  `@RequestParam("to")`. The result is that the two endpoints use different JSON vocabulary for the same
  underlying concept — a conscious choice to match the brief's exact wording wherever it specifies one,
  rather than force one vocabulary onto both endpoints.

- **Identical currency pairs are rejected outright.** `POST /conversions` and `GET /rates` both reject a
  request where the source/base and target/quote currency are the same, with `422 SAME_CURRENCY`, without
  ever calling the rate provider. A no-op conversion (or a "rate" of 1.0 to the same currency) isn't a
  meaningful use of either endpoint, and rejecting it early avoids a wasted provider call.

- **Coverage is measured and enforced.** JaCoCo gates the build at ≥ 80% line coverage — `mvn verify`
  fails below it — and the suite (158 tests) currently measures **96% line coverage**. Lombok-generated
  bytecode is excluded via `lombok.config` (`lombok.addLombokGeneratedAnnotation`), so the number reflects
  hand-written logic, not generated getters and builders. The number complements rather than replaces the
  named-scenario discipline: the happy path, insufficient funds, idempotency replay (including conflict
  and concurrent-duplicate cases) and concurrent-conversion races each have an explicitly named test.

- **Tests are skipped inside the Docker image build** (`Dockerfile`, `mvn package -DskipTests`) —
  Testcontainers needs a Docker daemon, which isn't available while building an image. The full suite
  runs in local dev / CI instead, outside the image build.

- **The Postgres image is pinned to `postgres:18` (major version).** Pinning the major means a future
  Postgres major release can never silently break a fresh clone of this repo, while patch releases —
  security fixes included — still flow in automatically. Pinning further, to an exact patch tag or an
  image digest, would be the production choice (fully reproducible deploys); for a take-home, the major
  pin is the deliberate middle ground.

## What's next

With more time, in rough priority order:

1. **Document the remaining `400` validation responses on `POST /conversions` and `GET /rates`** in
   Swagger — currently only `GET /conversions` documents its `FIELD_ERROR` / `VALIDATION_FAILED` /
   `MALFORMED_REQUEST` responses, even though bean validation produces the same responses on all three
   endpoints.
2. **Rate limiting on the public endpoints** — with no authentication layer, `X-Client-Id` is the only
   caller identity, so a per-client (and per-IP) limit is the natural guard against one caller exhausting
   the provider quota or the balance-lock throughput of the service.
3. **Retry mechanism (Resilience4j) for transient provider failures** — a single retry with backoff on
   timeouts/5xx from the rate provider, before giving up with `502 EXCHANGE_RATE_UNAVAILABLE`. Worth
   evaluating rather than assuming: the provider call already happens outside any DB transaction, so a
   retry is safe there, but it stacks on top of the existing 2s/3s timeouts and must not push overall
   request latency past what callers tolerate.
4. **Transaction/query timeout handling** — starting small: a `@Transactional(timeout = ...)` with a
   named constant on `ConversionProcessor.processConversion`, whose pessimistic row locks are exactly
   where a request could block indefinitely behind a stuck writer; then generalized to the remaining
   database interactions (possibly via an aspect rather than per-method annotations), paired with
   dedicated exception handling so a timed-out lock surfaces as a clear error response instead of a
   generic 500.
5. **Consider a scheduled or startup-time refresh for high-traffic currency pairs**, if usage patterns
   ever showed the plain TTL cache causing a noticeable "coldest visitor pays the provider round-trip"
   effect — not needed at this scale, but the natural next step if load grew.
