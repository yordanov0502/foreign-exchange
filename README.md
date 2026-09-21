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

Stop with `Ctrl+C`. `docker compose down -v` removes the containers and the Postgres volume — also the
fix if Flyway ever reports a `Migration checksum mismatch` from a volume left by an older checkout.

No environment variables are needed: the demo database credentials are baked into the compose files,
which a plain `docker compose up` merges automatically (`docker-compose.yaml` for Postgres,
`docker-compose.override.yaml` for the `app` service). Host port `8081` must be free.

### Option 2 — local dev

```bash
./mvnw spring-boot:run          # Linux/macOS
.\mvnw.cmd spring-boot:run      # Windows (PowerShell and cmd)
```

Requires a **running Docker daemon** — Spring Boot's docker-compose integration starts Postgres for you,
and only Postgres: `application.yaml` pins `spring.docker.compose.file: docker-compose.yaml`, which skips
the override file, so the containerized `app` service never starts here — the JVM you're running locally
is the app.

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
| `CLIENT-003` | EUR | 7,000.0000 |
| `CLIENT-003` | CAD | 6,000.0000 |
| `CLIENT-003` | CNY | 20,000.0000 |

`CLIENT-001` can convert USD↔EUR immediately, `CLIENT-002` GBP↔CHF, and `CLIENT-003` any pair among
EUR, CAD and CNY. Converting *from or into* a currency a client doesn't already hold (e.g. `CLIENT-002`
GBP→EUR) returns `404 BALANCE_NOT_FOUND` — a deliberate choice: the service never opens balance rows on
the fly, so the set of currencies a client holds stays under explicit (seeded) control. Auto-creating a
zero-balance target row on first credit would have been a defensible alternative reading of the spec.

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

**Timestamps:** all timestamps are stored as `TIMESTAMPTZ` (Postgres normalizes to a UTC instant — the
original offset is not kept) and mapped to `java.time.OffsetDateTime` end-to-end (entity → domain → API),
so responses serialize as ISO-8601 with an explicit offset. Since an instant alone doesn't define a
"day", the `date` filter on `GET /conversions` explicitly interprets its value as a whole UTC calendar
day.

## Client identification

`POST /conversions` and `GET /conversions` take the client via `X-Client-Id` (header) / `clientId` (query
param), not the request body — "who is calling" stays orthogonal to "what they're asking for", mirroring
how `Idempotency-Key` is also carried as a header. With no authentication layer to derive identity from,
this header **is** the identity claim — trusted as-is, per the assignment's non-goals.

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
method. Both balance rows (source and target currency) are locked before either is touched, always in
ascending currency-code order — Postgres does not guarantee `ORDER BY` determines lock acquisition order,
so the two rows are locked via two sequential calls. The fixed order is what makes opposite-direction
conversions for the same client (USD→EUR racing EUR→USD) deadlock-free: both requests contend on the same
first lock, so one queues behind the other instead of each holding one row and waiting on the other's.

**Alternatives considered, and why they were not chosen:**

- **Optimistic locking (`@Version` on `balances`)** — was in the schema initially, removed in
  `V5__drop_balances_version_column.sql`. A conversion touches *two* balance rows, so any collision on
  either means retrying the whole rate-lookup + compute + write flow, and the retry logic itself has to
  be written and tested. The write hotspot here is "many conversions for the same client in a short
  window" — contention is expected, not exceptional — which favors a blocking lock: the loser just waits,
  it doesn't fail and redo.
- **Single serialized writer per client (thread/queue)** — in-process coordination that has to survive
  restarts and doesn't extend to multiple app instances. `SELECT ... FOR UPDATE` gets the same per-client
  serialization for free from Postgres, and keeps working if the service is ever scaled out, since the
  lock lives in the database.

**Trade-off accepted:** the row lock is held for the transaction's duration, throttling a client issuing
many concurrent conversions. Acceptable here — correctness (no lost updates, no double-spend) matters
more than raw throughput, and the lock scope is exactly two rows for one client, not a table-wide lock.

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

**Invalidation choice: none — pure TTL expiry (`expireAfterWrite`, 30 minutes).** The 30-minute value is
sized to the provider: Frankfurter v2 blends daily reference rates from multiple central banks, and each
source updates at most once per working day, so the TTL bounds staleness at half an hour while keeping
calls to the free provider minimal — a shorter TTL would buy little real freshness against daily
upstreams. Public FX rates don't
change fast enough, and this service doesn't need sub-30-minute freshness, to justify a manual eviction
endpoint, refresh job, or event-driven invalidation. A fixed TTL is the simplest mechanism that bounds
how long a stale rate can be served — the KISS trade-off: build the one thing the requirement needs, not
a cache-invalidation feature no endpoint asks for.

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
  used elsewhere by the author, over Spring's native `@HttpExchange` + `RestClient`. This carries a known
  version skew — `spring-cloud-dependencies` 2025.1.3 targets Boot 4.0.8 while this project runs 4.1.1,
  and the only 4.1 train is a snapshot — validated empirically against the live provider (success,
  provider 404/422, forced timeout) with the full suite and checkstyle green before committing to it.

- **Deliberate wire vocabulary asymmetry.** Wherever the brief specifies exact wire names, those names
  win: `POST /conversions` uses `sourceCurrency`/`sourceAmount`/`targetCurrency`/`targetAmount` (via
  `@JsonProperty` aliases) and `GET /rates` takes `?from=&to=` (via `@RequestParam` aliases), both
  verbatim from the brief. Everything the brief doesn't name — internal code and the `GET /rates`
  response — keeps the single internal vocabulary `baseCurrency`/`quoteCurrency`. The two endpoints thus
  use different JSON vocabulary for the same concept, a conscious choice over forcing one vocabulary onto
  both.

- **Identical currency pairs are rejected outright.** `POST /conversions` and `GET /rates` both reject a
  request where the source/base and target/quote currency are the same, with `422 SAME_CURRENCY`, without
  ever calling the rate provider. A no-op conversion (or a "rate" of 1.0 to the same currency) isn't a
  meaningful use of either endpoint, and rejecting it early avoids a wasted provider call.

- **Coverage is measured and enforced.** JaCoCo fails `mvn verify` below 80% line coverage; the suite
  (158 tests) currently measures **96%**, with Lombok-generated bytecode excluded via `lombok.config` so
  the number reflects hand-written logic. The number complements named-scenario discipline: happy path,
  insufficient funds, idempotency replay/conflict/concurrent-duplicate, and concurrent-conversion races
  each have an explicitly named test.

- **Tests are skipped inside the Docker image build** (`Dockerfile`, `mvn package -DskipTests`) —
  Testcontainers needs a Docker daemon, which isn't available while building an image. The full suite
  runs via `./mvnw test` on the host instead.

- **The Postgres image is pinned to `postgres:18` (major version).** Pinning the major means a future
  Postgres major release can never silently break a fresh clone of this repo, while patch releases —
  security fixes included — still flow in automatically. Pinning further, to an exact patch tag or an
  image digest, would be the production choice (fully reproducible deploys); for a take-home, the major
  pin is the deliberate middle ground.

## What's next

With more time, in rough priority order:

1. **Document the remaining `400` validation responses in Swagger** — currently only `GET /conversions`
   documents its `FIELD_ERROR` / `VALIDATION_FAILED` / `MALFORMED_REQUEST` responses, though bean
   validation produces the same responses on all three endpoints.
2. **Rate limiting on the public endpoints** — with no auth, `X-Client-Id` is the only caller identity,
   so a per-client (and per-IP) limit is the natural guard against one caller exhausting the provider
   quota or the balance-lock throughput.
3. **Retry (Resilience4j) for transient provider failures** — a single retry with backoff on
   timeouts/5xx before giving up with `502 EXCHANGE_RATE_UNAVAILABLE`. Safe (the call is already outside
   any DB transaction), but it stacks on the existing 2s/3s timeouts and must not push overall latency
   past what callers tolerate.
4. **Transaction/query timeout handling** — starting with `@Transactional(timeout = ...)` on
   `ConversionProcessor.processConversion`, whose pessimistic row locks are exactly where a request could
   block behind a stuck writer, paired with exception handling so a timed-out lock surfaces as a clear
   error instead of a generic 500.
5. **Scheduled or startup-time refresh for high-traffic currency pairs** — only if the plain TTL cache
   ever caused a noticeable "coldest visitor pays the provider round-trip" effect; not needed at this
   scale.
