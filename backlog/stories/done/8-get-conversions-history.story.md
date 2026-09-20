# 8 — Expose `GET /conversions` — paginated, filtered conversion history

| Field | Value |
|---|---|
| Story Points | 5 |
| Priority | Critical |
| Status | Done |

**Story points rationale:** a read-only vertical slice across `rest → core → persistence` with no money
arithmetic, no write, no locking and no idempotency. What lifts it above the 3 of Seq 2 (`GET
/clients/{clientId}/balances`): three optional filters and every combination of them, a date-to-instant
range semantic, a pagination envelope with its own defaults/bounds, a new error code + advice handler, and
a new Flyway test fixture so paging and ordering can be asserted deterministically. What keeps it below an
8: no state is mutated, the `Conversion` domain record and its entity mapper already exist, and every
layer's pattern is already established by Seq 2 and Seq 4.

---

## User Story

**As a** foreign-exchange service
**I want** to return a paginated, filtered history of previously executed conversions
**So that** REQ-3 is satisfied — the brief's `GET /conversions?transactionId=&date=&clientId=&page=&size=`
endpoint, which must require at least one filter.

---

## Context

**Current behavior.** Seq 4 persists one `conversions` row per successful `POST /conversions`
(`ConversionProcessor.saveConversion`), and Seq 1 created the indices those rows would be read back
through — but nothing reads them. `ConversionController` exposes only `POST`, and `ConversionRepository`
only has the two idempotency-lookup methods plus the test-support `deleteByClientClientId`. A caller who
executed a conversion has no way to look it up again: the sole read path today is
`GET /clients/{clientId}/balances`, which shows the *effect* of conversions, never the conversions
themselves.

**Required behavior.** `GET /conversions` returns `200` with a page of conversion records, narrowed by any
combination of `transactionId`, `date` and `clientId`, with pagination metadata in the body. A request
carrying **none** of the three filters is rejected — the brief states at least one filter is required, and
an unfiltered full-table scan is exactly what that rule exists to prevent.

**What this unlocks.** REQ-3 is the last unclosed *endpoint* requirement; closing it means all four
endpoints from the brief exist, which is the precondition for Seq 10 (OpenAPI), Seq 12 (coverage
hardening) and Seq 13 (README).

**Grading axes affected:** correctness (the filter contract and the ≥1-filter rule), production sense
(pagination bounds, indexed access, no raw `Page` serialisation leaking Spring internals onto the wire)
and testing (filter-combination and paging coverage).

---

## Scope

### In Scope

- [ ] `GET /conversions` returning a paginated, filtered conversion history
- [ ] Filters `transactionId` (UUID), `date` (ISO `yyyy-MM-dd`, whole UTC day) and `clientId` (string),
      all optional individually, combinable, and **at least one required**
- [ ] `page` / `size` query parameters with named-constant defaults and bounds
- [ ] A dedicated pagination response envelope — the raw Spring `Page` is never serialised
- [ ] A deterministic sort order (newest conversion first) so paging is stable
- [ ] New domain exception + `ErrorCode` + advice handler for "no filter supplied"
- [ ] `clientId` added to the `Conversion` domain record and to `ConversionMapper`, so a history row read
      back by `transactionId` or `date` alone still says who it belongs to
- [ ] Flyway **test** fixture (`V901`) seeding deterministic historical conversions
- [ ] SpringDoc annotations on the new operation, matching the style already on the other three endpoints

### Out of Scope

- Any change to `POST /conversions`, its request/response JSON, its transaction boundary, its locking
  strategy or its idempotency handling — this story adds a read path alongside them (Backward
  Compatibility rule, `CLAUDE.md`)
- Locking, `@Version`, `Idempotency-Key` or any concurrency machinery: this endpoint moves no money and
  reads no row it then writes. A `@Transactional(readOnly = true)` service method is the whole story.
- Authentication / ownership checks — `clientId` is caller-supplied, per the assignment's non-goals. The
  `X-Client-Id` header used by `POST /conversions` is **not** read by this endpoint.
- Filtering by currency, amount, rate or a date *range* — the brief names exactly three filters
- Cursor/keyset pagination, sort-order query parameters, projections, or an `ETag`/caching layer
- A new production Flyway migration — see *Indices already exist* below

---

## Acceptance Criteria

- [ ] `GET /conversions?clientId=CLIENT-TEST-HISTORY-001` returns `200` and every conversion belonging to
      that client, newest first
- [ ] `GET /conversions?transactionId={uuid}` returns `200` with exactly one item whose `transactionId`
      equals the supplied value
- [ ] `GET /conversions?date=2020-01-15` returns `200` with exactly the conversions whose `created_at`
      falls in `[2020-01-15T00:00Z, 2020-01-16T00:00Z)` — a conversion recorded at `2020-01-16T00:00:00Z`
      is excluded
- [ ] Filters combine as AND: `?clientId=X&date=Y` returns only rows matching both; a combination matching
      nothing returns `200` with `content: []`
- [ ] A request with none of `transactionId`, `date`, `clientId` returns `400` with body
      `code: CONVERSION_FILTER_REQUIRED`, and `path: /conversions`
- [ ] An unknown `clientId` returns `200` with `content: []`, `totalElements: 0` — **not** `404`
- [ ] The response body is `{ "content": [...], "page", "size", "totalElements", "totalPages" }`; no Spring
      `Page` fields (`pageable`, `numberOfElements`, `first`, `last`, `sort`, `empty`) appear
- [ ] Each item serialises as `transactionId`, `clientId`, `sourceCurrency`, `sourceAmount`,
      `targetCurrency`, `targetAmount`, `rate`, `timestamp` — D1's wire convention (Java `base*`/`quote*`,
      JSON `source*`/`target*`)
- [ ] Omitting `page` and `size` yields `page: 0` and `size: 20`
- [ ] `size` above the maximum (100), `size` below 1, or a negative `page` returns `400` — the value is
      rejected, never silently clamped
- [ ] A `page` beyond the last page returns `200` with `content: []` while `totalElements` / `totalPages`
      still report the full result set
- [ ] A malformed `date` (e.g. `2020-13-45`, `15-01-2020`) and a malformed `transactionId` each return
      `400` with a consistent `ErrorResponse` body — never a `500` and never a stack trace
- [ ] Paging is stable: with `size=2` over 3 matching rows, page 0 and page 1 together contain each row
      exactly once, ordered newest-first
- [ ] `POST /conversions` responses are byte-identical to before this story (no `clientId` added to
      `ConversionResponse`)

---

## Technical Notes

### Layers Affected

- [x] `core` — `ConversionHistoryQuery`, the ≥1-filter rule, the UTC-day range, the sort decision
- [x] `persistence` — `JpaSpecificationExecutor` + `ConversionSpecification`
- [x] `rest` — controller operation, filter request object, response envelope, mappers, advice handler
- [ ] `common` — untouched

### Files to Create

| Path | Purpose |
|---|---|
| `src/main/java/zetta/foreignexchange/core/constant/PaginationConstant.java` | `DEFAULT_PAGE_NUMBER`, `DEFAULT_PAGE_SIZE`, `MIN_PAGE_NUMBER`, `MIN_PAGE_SIZE`, `MAX_PAGE_SIZE` |
| `src/main/java/zetta/foreignexchange/core/exception/ConversionFilterRequiredException.java` | Thrown when no filter is supplied |
| `src/main/java/zetta/foreignexchange/core/model/ConversionHistoryQuery.java` | `(UUID transactionId, LocalDate date, String clientId, int page, int size)` |
| `src/main/java/zetta/foreignexchange/persistence/specification/ConversionSpecification.java` | One single-argument `Specification<ConversionEntity>` factory per filter |
| `src/main/java/zetta/foreignexchange/rest/model/ConversionHistoryFilterRequest.java` | Query-param object: `transactionId`, `date`, `clientId` |
| `src/main/java/zetta/foreignexchange/rest/model/ConversionHistoryItemResponse.java` | One history row on the wire |
| `src/main/java/zetta/foreignexchange/rest/model/ConversionHistoryResponse.java` | `content` + pagination metadata |
| `src/main/java/zetta/foreignexchange/rest/mapper/ConversionHistoryQueryMapper.java` | filter request + page + size → `ConversionHistoryQuery` |
| `src/main/java/zetta/foreignexchange/rest/mapper/ConversionHistoryResponseMapper.java` | `Page<Conversion>` → `ConversionHistoryResponse` |
| `src/test/resources/db/testdata/V901__seed_conversion_history.sql` | Deterministic historical conversions |
| `src/test/java/zetta/foreignexchange/integration/ConversionHistoryIntegrationTest.java` | Full-stack filter/paging coverage |
| `src/test/java/zetta/foreignexchange/rest/mapper/ConversionHistoryResponseMapperTest.java` | Envelope mapping |
| `src/test/java/zetta/foreignexchange/rest/mapper/ConversionHistoryQueryMapperTest.java` | Query-object mapping |

### Files to Modify

| Path | Change |
|---|---|
| `src/main/java/zetta/foreignexchange/core/model/Conversion.java` | Add `String clientId` |
| `src/main/java/zetta/foreignexchange/core/mapper/ConversionMapper.java` | `@Mapping(target = "clientId", source = "client.clientId")` |
| `src/main/java/zetta/foreignexchange/core/model/ErrorCode.java` | Add `CONVERSION_FILTER_REQUIRED` |
| `src/main/java/zetta/foreignexchange/core/service/ConversionService.java` | Add `Page<Conversion> getConversionHistory(ConversionHistoryQuery conversionHistoryQuery)` |
| `src/main/java/zetta/foreignexchange/core/service/implementation/ConversionServiceImpl.java` | Implement it; inject `ConversionRepository` |
| `src/main/java/zetta/foreignexchange/persistence/repository/ConversionRepository.java` | Extend `JpaSpecificationExecutor<ConversionEntity>`; override `findAll(Specification, Pageable)` with `@EntityGraph` |
| `src/main/java/zetta/foreignexchange/rest/controller/ConversionController.java` | Add the `@GetMapping` operation + SpringDoc annotations |
| `src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java` | Handle `ConversionFilterRequiredException` → `400`; register it in `STATUS_BY_ERROR_CODE` |
| `src/test/java/zetta/foreignexchange/core/mapper/ConversionMapperTest.java` | Assert `clientId` is mapped from the nested `ClientEntity` |
| `src/test/java/zetta/foreignexchange/core/service/implementation/ConversionServiceTest.java` | Unit tests for the new service method |
| `src/test/java/zetta/foreignexchange/rest/controller/ConversionControllerTest.java` | Slice tests for the new operation |
| `src/test/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdviceTest.java` | Handler test for the new code |

> Note: `CLAUDE.md` names the REST DTO package `rest/dto`; the code actually uses `rest/model`
> (`ConversionRequest`, `ConversionResponse`, `BalanceResponse`, …). Follow the **code** — new DTOs go in
> `rest/model`.

---

## Design Decisions

### D8.1 — Query strategy: `JpaSpecificationExecutor` + one specification per filter

The instinctive KISS answer is a single `@Query` with null-guards:

```java
// REJECTED
@Query("... WHERE (:transactionId IS NULL OR c.transactionId = :transactionId) AND ...")
Page<ConversionEntity> findHistory(UUID transactionId, String clientId,
        OffsetDateTime fromTimestamp, OffsetDateTime toTimestamp, Pageable pageable);
```

It is not available here. `checkstyle.xml` line 112 sets `ParameterNumber max=3` for `METHOD_DEF`, and
that method needs five parameters. (`CLAUDE.md` says "4 params per method"; the actual rule is **3** —
trust `checkstyle.xml`.) Every workaround that keeps the single-query shape — SpEL parameter extraction,
a persistence-side parameter record — costs more code than the alternative and, in the SpEL case, would
drag a `core` model into `persistence`, reversing the layer dependency.

So: `ConversionRepository extends JpaSpecificationExecutor<ConversionEntity>` and the service composes

```java
Specification<ConversionEntity> conversionSpecification = Specification.allOf(
        ConversionSpecification.hasTransactionId(conversionHistoryQuery.transactionId()),
        ConversionSpecification.hasClientId(conversionHistoryQuery.clientId()),
        ConversionSpecification.isCreatedOnOrAfter(dayRange.startOfDay()),
        ConversionSpecification.isCreatedBefore(dayRange.startOfNextDay()));
```

Each factory takes exactly one argument and returns a no-op specification
(`(root, criteriaQuery, criteriaBuilder) -> null`) when its value is `null`, which `Specification.allOf`
drops. Two things this buys beyond passing checkstyle, both worth a README line:

1. **Only the supplied predicates reach SQL.** The `:param IS NULL OR column = :param` form emits all four
   comparisons every time and routinely stops Postgres using an index; the specification form emits
   `WHERE client_id = ?` alone when that is the only filter, so `ix_conversions_client_created_at` is used.
2. It sidesteps Postgres's "could not determine data type of parameter" failure mode for a **null UUID**
   bind — a real trap with the null-guard form.

This is not pattern-stacking: it is the only composition mechanism that satisfies the constraints, and it
is three trivial lambdas plus one `allOf` call. Do **not** add a generic/reflective specification builder.

### D8.2 — `date` means one whole UTC day

`conversions.created_at` is `TIMESTAMPTZ` (`V1`), mapped as `OffsetDateTime` via `@CreationTimestamp`, and
Hibernate is pinned to UTC (`spring.jpa.properties.hibernate.jdbc.time_zone: UTC`). `date` is therefore
defined as a **half-open UTC day**:

```java
OffsetDateTime startOfDay     = date.atStartOfDay().atOffset(ZoneOffset.UTC);
OffsetDateTime startOfNextDay = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
// created_at >= startOfDay AND created_at < startOfNextDay
```

- **Half-open, not `BETWEEN`.** `BETWEEN` is inclusive at both ends and would double-count a conversion
  recorded exactly at midnight on two consecutive days.
- **Range, not `CAST(created_at AS date) = :date`.** Casting the column is not sargable (the index is
  unusable) and, on `TIMESTAMPTZ`, its result depends on the session `TimeZone` — the same row would be
  filed under different dates on different machines.
- **The conversion lives in `core`**, not the controller: "a date means the UTC day" is a business rule,
  and `rest` holds no business logic (`CLAUDE.md`).
- **Parsing** is Spring's: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date` on the filter
  request component. `LocalDate` rejects both malformed shapes and impossible dates (`2020-02-30`), so no
  hand-written date validator is needed. See D8.7 for how the parse failure is reported.

### D8.3 — "At least one filter required" → `CONVERSION_FILTER_REQUIRED`, HTTP 400

A new `ConversionFilterRequiredException` (no fields — nothing about the request is worth echoing back), a
new `ErrorCode.CONVERSION_FILTER_REQUIRED`, and a handler in `ForeignExchangeControllerAdvice` mapped to
`HttpStatus.BAD_REQUEST` in `STATUS_BY_ERROR_CODE`.

- **Why a distinct code, not `VALIDATION_FAILED`:** the brief asks for "distinct codes"; every existing
  domain rule has its own (`SAME_CURRENCY`, `INSUFFICIENT_FUNDS`, `IDEMPOTENCY_KEY_CONFLICT`). A caller
  must be able to tell "you sent no filter" from "your date was malformed" without parsing prose.
- **Why 400, not 422:** the existing advice already answers *missing required input*
  (`MissingServletRequestParameterException`, `MissingRequestHeaderException`) with `400`, and reserves
  `422` for requests that are well-formed but semantically unprocessable (`INSUFFICIENT_FUNDS`,
  `SAME_CURRENCY`). An absent filter is missing input.
- **Validated in `core`**, at the top of `getConversionHistory`, so the rule is covered by a service unit
  test and cannot be bypassed by a future caller of the service.
- Message constant: `"At least one of transactionId, date or clientId must be supplied."` — a fixed
  string, no `format` arguments.

### D8.4 — Unknown `clientId` is `200` with an empty page, not `404`

`GET /clients/{clientId}/balances` returns `404 CLIENT_NOT_FOUND` because the client *is* the addressed
resource. Here the addressed resource is the conversion collection and `clientId` is a *filter*; a filter
matching nothing is an empty result, not a missing resource. This also keeps all three filters behaving
identically — an unmatched `transactionId` or `date` cannot 404 either. Record the asymmetry in the README
(Seq 13).

### D8.5 — Response shape: a dedicated item record, and a dedicated envelope

**Item: `ConversionHistoryItemResponse`, not a reuse of `ConversionResponse`.** `ConversionResponse`
carries `List<BalanceResponse> balances` — *the balances immediately after that conversion*. That value is
unreconstructable from history: the balance rows hold only the current amount, and no ledger exists (a
full journal model is an explicit non-goal, `BACKLOG.md`). Reusing the record would force every history
item to carry either `null` or, worse, today's balances masquerading as the post-conversion state. A
7-field record without `balances` is both smaller and honest.

**`clientId` is on the item.** `date`- and `transactionId`-only queries return rows across clients; without
`clientId` those rows are unattributable. This is why `Conversion` gains a `clientId` component. Impact is
contained: `ConversionMapper` gains one `@Mapping`, and `ConversionResponse` is **not** touched, so the
`POST /conversions` wire format is unchanged (MapStruct ignores unmapped *source* properties by default).

**Envelope: `ConversionHistoryResponse`, never a serialised `Page`.** Spring's `PageImpl` serialises
`pageable`, `sort`, `numberOfElements`, `first`, `last`, `empty` and an unstable `Sort` structure — Spring
internals on a public contract, and Spring Boot logs a warning about exactly this. The envelope is:

```json
{
  "content": [
    {
      "transactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "clientId": "CLIENT-001",
      "sourceCurrency": "USD",
      "sourceAmount": 100.0000,
      "targetCurrency": "EUR",
      "targetAmount": 86.9840,
      "rate": 0.86984,
      "timestamp": "2026-09-20T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

Concrete, not a generic `PagedResponse<T>`: there is exactly one paginated endpoint in this service, and a
concrete record gives SpringDoc a nameable schema instead of an erased type variable.

**D1 holds for the item.** Java components are `baseCurrency` / `baseAmount` / `quoteCurrency` /
`quoteAmount`, each annotated `@JsonProperty("sourceCurrency")` … exactly as `ConversionResponse` does.

### D8.6 — Pagination defaults, bounds and sort

`PaginationConstant` (in `core/constant`, beside `IdempotencyConstant`) — no magic numbers anywhere else:

```java
public static final String DEFAULT_PAGE_NUMBER = "0";   // @RequestParam defaultValue needs a String
public static final String DEFAULT_PAGE_SIZE   = "20";
public static final int MIN_PAGE_NUMBER = 0;
public static final int MIN_PAGE_SIZE   = 1;
public static final int MAX_PAGE_SIZE   = 100;
```

- **Explicit `@RequestParam int page/size`, not Spring Data's `Pageable` argument resolver.** The resolver
  silently coerces `page=-1` to `0` and silently caps an oversized `size`; this service validates
  explicitly everywhere else (`@Positive`, `@Digits`, `@Size`) and REQ-14 asks for "sane bounds". `@Min` /
  `@Max` on the parameters raise `HandlerMethodValidationException`, which the advice **already** maps to
  `VALIDATION_FAILED` / `400` — no new handler needed. Using the resolver would also consume a fourth
  controller parameter (see D8.1 on `ParameterNumber max=3`).
- **The `PageRequest` is built in `core`**, from `page`/`size` on `ConversionHistoryQuery` — sort order is
  a domain decision, not a transport one.
- **Sort: `createdAt DESC, id DESC`.** Newest first is what a history reader expects, and it matches
  `ix_conversions_client_created_at (client_id, created_at DESC)`. The `id DESC` tie-break is not
  decoration: `created_at` is not unique, and two rows sharing a timestamp under an unstable sort can
  appear on two pages or on none — which would make the paging acceptance criterion flaky rather than
  failing. Sort is fixed; it is not a query parameter.

### D8.7 — Malformed `date` / `transactionId`

`ConversionHistoryFilterRequest` is a record bound from query parameters (Spring constructor binding for
`@ModelAttribute`-style parameter objects), annotated `@ParameterObject` so SpringDoc flattens it into
three query parameters rather than documenting a body.

A value that will not convert (`date=15-01-2020`, `transactionId=not-a-uuid`) surfaces as a binding
failure. **Expected:** `MethodArgumentNotValidException` → the existing `FIELD_ERROR` handler → `400`.
Write the Red integration test first and let it adjudicate: if the failure instead escapes as
`MethodArgumentTypeMismatchException` or `BindException`, it currently falls through to the catch-all
`Exception` handler and returns **500** — in that case add one handler for it mapped to
`ErrorCode.FIELD_ERROR`. The acceptance criterion is the `400` and the consistent error body; which
handler produces it is an implementation detail decided by the test.

### D8.8 — `clientId` filter is a query parameter, `X-Client-Id` is not read

The brief's endpoint shape is `GET /conversions?transactionId=&date=&clientId=` — `clientId` is a query
parameter, listed alongside the other two filters, and it is *optional* (a `transactionId`-only lookup
must work). `POST /conversions` takes the client via the `X-Client-Id` header because there the client is
the actor being debited; here it is a search term. The header is **not** read, and no ownership check is
performed — there is no authentication in this service by design.

### D8.9 — The history method lives on `ConversionService`

Not a new `ConversionHistoryService`. It reads the same aggregate through the same repository the write
path already uses, the controller is already `ConversionController`, and it adds one public method plus
two private helpers to a 95-line class. `ConversionServiceImpl` gains a `ConversionRepository` dependency
(5 constructor parameters — the limit is 7) and calls it directly, exactly as `BalanceServiceImpl` calls
`BalanceRepository`. No `ConversionProcessor` involvement: the processor exists solely to own a write
transaction boundary.

---

## Indices already exist — no production migration in this story

Verified in `src/main/resources/db/migration/V1__create_clients_balances_and_conversions.sql`:

```sql
CONSTRAINT conversions_transaction_id UNIQUE (transaction_id)        -- backs ?transactionId=
CREATE INDEX ix_conversions_client_created_at ON conversions (client_id, created_at DESC);  -- ?clientId= (+ sort)
CREATE INDEX ix_conversions_created_at ON conversions (created_at);  -- backs ?date=
```

All three filters and the `created_at DESC` sort are covered; the `UNIQUE` constraint creates the index
`transaction_id` lookups need. **Seq 1 delivered what `PRIORITY.md` promised — add no `V6`.**

One join note: `conversions.client_id` is a `BIGINT` FK to `clients.id`, **not** the business identifier.
Filtering by the caller's `clientId` string therefore goes through the association
(`root.get("client").get("clientId")`, i.e. an inner join to `clients`, whose `clients_client_id` unique
constraint resolves it to a single `id` before `ix_conversions_client_created_at` is used).

---

## Existing Pattern Reference

**Controller — GET with `@RequestParam`, SpringDoc, and a mapper call only**
`src/main/java/zetta/foreignexchange/rest/controller/RateController.java:97`

```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ExchangeRateResponse> getExchangeRate(
        @Parameter(description = "Currency to convert from", example = "USD")
        @RequestParam("from") final String baseCurrency, ...) {
    ExchangeRate exchangeRate = rateService.getExchangeRate(baseCurrency, quoteCurrency);
    return ResponseEntity.ok(exchangeRateResponseMapper.mapToExchangeRateResponse(exchangeRate));
}
```

`ConversionController` already carries `@Validated` (needed for `@Min`/`@Max` on the parameters) and the
`@ApiResponses` + `@ExampleObject` house style to copy.

**Service — public interface, read-only transaction, repository called directly**
`src/main/java/zetta/foreignexchange/core/service/implementation/BalanceServiceImpl.java:24`

```java
@Override
@Transactional(readOnly = true)
public List<Balance> getClientBalances(String clientId) {
    validateClientExists(clientId);
    List<BalanceEntity> balanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
    return balanceMapper.mapToBalances(balanceEntities);
}
```

**Repository**
`src/main/java/zetta/foreignexchange/persistence/repository/ConversionRepository.java:9` — add the
specification executor and the fetch-graph override:

```java
public interface ConversionRepository extends JpaRepository<ConversionEntity, Long>,
        JpaSpecificationExecutor<ConversionEntity> {

    @Override
    @EntityGraph(attributePaths = "client")
    Page<ConversionEntity> findAll(Specification<ConversionEntity> specification, Pageable pageable);
    ...
}
```

The `@EntityGraph` override exists because `ConversionEntity.client` is `FetchType.LAZY` and each history
item reads `client.clientId` — without it, a 20-row page issues 21 queries. It is applied to the *content*
query only; the count query is unaffected. If the override turns out not to be honoured, falling back to
lazy loading inside the read-only transaction is acceptable for this assignment — note it in the README
rather than contorting the query.

**Entity — the exact column names to filter and sort on**
`src/main/java/zetta/foreignexchange/persistence/entity/ConversionEntity.java`: `transactionId`
(`transaction_id`, `UUID`), `client` (`@ManyToOne(LAZY)` → `client_id`), `baseCurrency`/`baseAmount`,
`quoteCurrency`/`quoteAmount`, `rate`, `idempotencyKey`, `createdAt` (`created_at`, `OffsetDateTime`,
`@CreationTimestamp`). The entity is `@Immutable` — reads only, never mutate one.

**Response DTO — D1 wire aliasing**
`src/main/java/zetta/foreignexchange/rest/model/ConversionResponse.java:16`

```java
@JsonProperty("sourceCurrency")
@Schema(description = "Currency converted from.", example = "USD")
String baseCurrency,
```

**Mapper — multi-argument MapStruct mapping into a core record**
`src/main/java/zetta/foreignexchange/rest/mapper/ConversionRequestMapper.java:10`

```java
@Mapper(componentModel = "spring")
public interface ConversionRequestMapper {
    ConversionInput mapToConversionInput(String clientId, String idempotencyKey, ConversionRequest conversionRequest);
}
```

`ConversionHistoryQueryMapper.mapToConversionHistoryQuery(ConversionHistoryFilterRequest, int, int)`
follows it exactly (three parameters — the limit).
`ConversionHistoryResponseMapper` maps from `Page<Conversion>`'s bean properties:
`content → content`, `number → page`, `size → size`, `totalElements → totalElements`,
`totalPages → totalPages`. If MapStruct balks at the parameterised source type, implement it as a `default`
method on the interface rather than adding configuration.

**Controller advice — new handler + status entry**
`src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java:96`

```java
@ExceptionHandler(SameCurrencyException.class)
public ResponseEntity<ErrorResponse> handleSameCurrencyException(
        SameCurrencyException exception, HttpServletRequest request) {
    log.error("SameCurrencyException thrown", exception);
    return buildErrorResponse(ErrorCode.SAME_CURRENCY, format(SAME_CURRENCY_MESSAGE, ...), request);
}
```

plus `Map.entry(ErrorCode.CONVERSION_FILTER_REQUIRED, HttpStatus.BAD_REQUEST)` in
`STATUS_BY_ERROR_CODE` (line 50).

**Domain exception**
`src/main/java/zetta/foreignexchange/core/exception/ClientNotFoundException.java` — `@AllArgsConstructor`
`@Getter` over `RuntimeException`. `ConversionFilterRequiredException` has no fields, so it is a bare
`extends RuntimeException`.

**Integration test**
`src/test/java/zetta/foreignexchange/integration/ConversionIntegrationTest.java:32` — extends
`BaseIntegrationTestSetUp`, `MockMvc` + `jsonPath`, every literal hoisted to a constant
(`MultipleStringLiterals`), `comparesEqualTo(new BigDecimal(...), BigDecimal.class)` for money so scale
differences do not produce false failures.

**Controller slice test**
`src/test/java/zetta/foreignexchange/rest/controller/ClientControllerTest.java:45` — `@Mock` the service,
`@Spy Mappers.getMapper(...)` the real mapper, `Instancio.create(...)` where values are insignificant,
plain `verify(mock)` (never `times(1)`).

**Test fixture**
`src/test/resources/db/testdata/V900__seed_integration_test_clients_and_balances.sql` — `V901` sits beside
it (`spring.flyway.locations` in `src/test/resources/application-test.yaml` already includes
`classpath:db/testdata`).

---

## TDD Steps

Strict Red → Green → Refactor per slice; each slice's test must fail for the right reason before any
production code is written.

**Slice 1 — `Conversion` learns its `clientId`**
- **Red:** `ConversionMapperTest.mapToConversion_withConversionEntityCarryingClient_mapClientId` — build a
  `ConversionEntity` with a `ClientEntity`, assert `conversion.clientId()`.
- **Green:** add `String clientId` to `Conversion`; add
  `@Mapping(target = "clientId", source = "client.clientId")` to `ConversionMapper`.
- **Refactor:** confirm the existing `mapToConversion_withNullConversionEntity_returnNull` and the
  client-less builder case still pass (MapStruct guards the nested access), and that
  `ConversionResponse` / `ConversionResponseMapper` are untouched.

**Slice 2 — the ≥1-filter rule**
- **Red:** `ConversionServiceTest.getConversionHistory_withNoFilterSupplied_throwConversionFilterRequiredException`
  — a `ConversionHistoryQuery` with all three filters null; assert the exception and
  `verifyNoInteractions(conversionRepository)`.
- **Green:** `ConversionFilterRequiredException`, the guard at the top of `getConversionHistory`.
- **Refactor:** extract `validateAtLeastOneFilterSupplied`.

**Slice 3 — query composition, paging and sort**
- **Red:** `getConversionHistory_withClientIdFilter_returnPageOfConversions` — mock
  `conversionRepository.findAll(any(Specification.class), any(Pageable.class))`, capture the `Pageable`,
  assert page number, size and `Sort` = `createdAt: DESC, id: DESC`, and that each returned `Conversion`
  is the mapped entity.
- **Green:** `PaginationConstant`, `ConversionHistoryQuery`, `ConversionSpecification`, the repository
  extending `JpaSpecificationExecutor` with the `@EntityGraph` override, the service body.
- **Refactor:** pull the UTC-day computation into a private helper returning a small
  `record DayRange(OffsetDateTime startOfDay, OffsetDateTime startOfNextDay)` (the
  `ConversionProcessor.LockedBalances` private-record precedent); keep the method under 40 lines.

**Slice 4 — the error contract**
- **Red:** `ForeignExchangeControllerAdviceTest.handleConversionFilterRequiredException_returnBadRequest`
  — assert `400`, `code`, message and `path`.
- **Green:** `ErrorCode.CONVERSION_FILTER_REQUIRED`, the `STATUS_BY_ERROR_CODE` entry, the handler.

**Slice 5 — the wire shape**
- **Red:** `ConversionHistoryResponseMapperTest.mapToConversionHistoryResponse_withPopulatedPage_mapContentAndPaginationMetadata`
  (build a `PageImpl` over `Instancio`-generated `Conversion`s) and
  `…_withEmptyPage_returnEmptyContent`. Add
  `ConversionHistoryQueryMapperTest.mapToConversionHistoryQuery_withAllFilters_mapEveryField`.
- **Green:** `ConversionHistoryItemResponse`, `ConversionHistoryResponse`,
  `ConversionHistoryFilterRequest`, both mappers.
- **Refactor:** verify the `@JsonProperty` aliases against `ConversionResponse` field by field.

**Slice 6 — the controller**
- **Red:** `ConversionControllerTest.getConversionHistory_withFilters_returnConversionHistoryResponse` and
  `…_withoutPageAndSize_useDefaults` — `@Mock ConversionService`, `@Spy` the real mappers.
- **Green:** the `@GetMapping` method (three parameters: filter request, `page`, `size`).
- **Refactor:** SpringDoc `@Operation` / `@ApiResponses` / `@ExampleObject` matching the `POST`
  operation's depth (200 plus the 400 variants).

**Slice 7 — full stack**
- **Red:** `ConversionHistoryIntegrationTest` + `V901__seed_conversion_history.sql`, one test per
  acceptance criterion:
  `getConversionHistory_withClientIdFilter_returnClientConversionsNewestFirst`,
  `…_withTransactionIdFilter_returnSingleConversion`,
  `…_withDateFilter_returnConversionsRecordedOnThatUtcDay`,
  `…_withClientIdAndDateFilter_returnIntersection`,
  `…_withUnknownClientId_returnEmptyPage`,
  `…_withNoFilter_returnBadRequest`,
  `…_withMalformedDate_returnBadRequest`,
  `…_withMalformedTransactionId_returnBadRequest`,
  `…_withSizeAboveMaximum_returnBadRequest`,
  `…_withNegativePage_returnBadRequest`,
  `…_withSizeTwo_returnStablePagesCoveringEveryRowOnce`,
  `…_withPageBeyondLastPage_returnEmptyContentAndFullTotals`,
  `…_withoutPageAndSize_returnDefaultPageAndSize`.
- **Green:** whatever the failures demand — in particular D8.7's malformed-input handler, if the
  500-instead-of-400 path materialises.
- **Refactor:** hoist repeated literals into constants; confirm the whole suite (including
  `ConversionIntegrationTest`'s 18 cases) is still green and coverage is ≥ 80%.

### `V901__seed_conversion_history.sql`

Dedicated clients (`CLIENT-TEST-HISTORY-001`, `CLIENT-TEST-HISTORY-002`) and deliberately old,
never-otherwise-produced timestamps, so the fixture is invisible to — and unaffected by — every other test
class. `BaseIntegrationTestSetUp` rolls each test back, so these rows are stable for the whole run.

- `CLIENT-TEST-HISTORY-001`: three conversions with fixed `transaction_id`s — two on `2020-01-15` (e.g.
  `08:00:00Z` and `09:30:00Z`) and one on `2020-01-16T00:00:00Z` **exactly**, which is the row that proves
  the range's exclusive upper bound
- `CLIENT-TEST-HISTORY-002`: one conversion on `2020-01-15`, so `clientId` + `date` together prove the AND
- Insert against the **post-`V3`/`V4`** schema: `base_currency`, `base_amount`, `quote_currency`,
  `quote_amount`, `rate NUMERIC(19,5)`, and explicit `created_at` values (raw SQL bypasses
  `@CreationTimestamp`). All amounts and rates must be `> 0` — `conversions_amounts_and_rate_positive`.
- `client_id` is the `clients.id` FK: `SELECT c.id FROM clients c WHERE c.client_id = '…'`, as `V900` does.

---

## Dependencies

- **Depends On:** `Seq 4` (**Done**) — supplies the persisted `conversions` rows this endpoint reads, the
  `ConversionEntity` → `Conversion` mapping chain extended in Slice 1, and D1's wire convention. Also
  inherits `Seq 1`'s schema and indices transitively.
- **Blocks:** `Seq 10` (OpenAPI — listed as blocked by Seq 8), `Seq 12` (coverage hardening) and `Seq 13`
  (README) need the last endpoint in place.

---

## References

- `.claude/Java-Assignment.pdf` — `GET /conversions?transactionId=&date=&clientId=&page=&size=`:
  paginated conversion history, at least one filter required (REQ-3 in `backlog/BACKLOG.md`)
- `backlog/BACKLOG.md` — REQ-3 (`Open` → `Done` on merge); contributes to REQ-14/REQ-15 without reopening
  them
- `backlog/PRIORITY.md` — Seq 8; the "Seq 5, 6, 7 consolidated into Seq 4" note fixes D1 (base/quote in
  Java, source/target on the wire), which this story's history items follow
- `backlog/stories/done/4-post-conversions-complete.story.md` — R13 (the `timestamp` returned by `POST` and
  the `created_at` this endpoint filters on are the same instant, deliberately) and R15 (`@JsonProperty`
  aliasing on record components)
- `checkstyle.xml:112` — `ParameterNumber max=3` for methods, the constraint behind D8.1

---

## @architect Recommendations

> **Added by @architect during /plan review**

- ✅ D8.1 verified against the real classpath (Spring Boot 4.1.1 → Spring Data JPA 4.1.1):
  `Specification.allOf(Specification<T>...)` and `Specification.unrestricted()` both exist, and
  `Specification.toPredicate` is still `@Nullable` so null-predicate specs are dropped by composition.
  Return `Specification.unrestricted()` from a filter factory whose value is `null` — do **not** hand-roll
  `(root, criteriaQuery, criteriaBuilder) -> null`. Note `allOf` does **not** tolerate a null *Specification
  reference*, only a null predicate, so a factory must never return `null` itself.
- ✅ D8.1's layer reasoning is correct and is the only option: `ConversionHistoryQuery` is a `core` model, so
  composing the specification in `core` (calling one-argument factories that live in
  `persistence/specification/ConversionSpecification.java`) is what keeps `persistence → core` from forming.
  `core` imports only the `Specification` type; the `jakarta.persistence.criteria` API stays in `persistence`.
- ✅ The `@EntityGraph` override is verified sound, not speculative:
  `SimpleJpaRepository#applyRepositoryMethodMetadata` applies `getQueryHints().withFetchGraphs(...)` to the
  **content** query, while `applyRepositoryMethodMetadataForCount` uses `getQueryHintsForCount()` which
  deliberately omits fetch graphs. Override exactly
  `Page<ConversionEntity> findAll(Specification<ConversionEntity> specification, Pageable pageable)` in
  `src/main/java/zetta/foreignexchange/persistence/repository/ConversionRepository.java`. `client` is
  `@ManyToOne`, so a fetch join plus pagination is safe (no in-memory paging).
- ✅ D8.7's "Expected" branch is verified, not a coin-flip: Spring Framework 7.0.9
  `ModelAttributeMethodProcessor` throws `MethodArgumentNotValidException` when constructor binding or
  conversion of a parameter object fails. A malformed `date` or `transactionId` therefore hits the existing
  `FIELD_ERROR` handler in `ForeignExchangeControllerAdvice` and returns `400`. No new handler is needed
  **for those two**.
- ✅ `BaseIntegrationTestSetUp` is `@Transactional`, so `V901` rows survive the whole run as the story
  assumes. Add `CLIENT_TEST_HISTORY_FIRST_ID` / `CLIENT_TEST_HISTORY_SECOND_ID` constants to
  `src/test/java/zetta/foreignexchange/integration/BaseIntegrationTestSetUp.java` beside the existing
  `CLIENT_DEFAULT_ID` family, and have `V901` insert the two rows into `clients` first, resolving the FK with
  the `JOIN clients c ON v.client_id = c.client_id` form used by
  `src/test/resources/db/testdata/V900__seed_integration_test_clients_and_balances.sql`.
- ⚠️ **`?page=abc` / `?size=abc` currently returns `500`, and the story has no acceptance criterion for it.**
  `page`/`size` are plain `@RequestParam int`, so a non-numeric value raises
  `MethodArgumentTypeMismatchException`. `ForeignExchangeControllerAdvice` does **not** extend
  `ResponseEntityExceptionHandler`, so its catch-all `@ExceptionHandler(Exception.class)` wins and produces
  `INTERNAL_SERVER_ERROR`. Add `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` mapped to
  `ErrorCode.FIELD_ERROR` (already `400` in `STATUS_BY_ERROR_CODE`), plus two acceptance criteria and two
  integration tests (`…_withNonNumericPage_returnBadRequest`, `…_withNonNumericSize_returnBadRequest`).
  This story introduces the service's first typed query parameters — the gap does not exist anywhere else.
- ⚠️ **Missing from "Files to Modify": `src/test/java/zetta/foreignexchange/rest/mapper/ConversionResponseMapperTest.java`.**
  It calls the canonical `new Conversion(TRANSACTION_ID, USD, BASE_AMOUNT, EUR, QUOTE_AMOUNT, RATE, TIMESTAMP)`
  at lines 51 and 72 and will not compile once `Conversion` gains `clientId`. Fix both call sites in Slice 1,
  and assert there that `ConversionResponse` still has no `clientId` field — that is the backward-compatibility
  guard for the `POST` wire shape.
- ⚠️ **Checkstyle limits the story states wrongly or omits — `checkstyle.xml` is authoritative:**
  `MethodLength max=30` (line 109), not 40 as Slice 3 says. `FileLength max=300` (line 130):
  `ConversionController.java` is already **193 lines**, so a SpringDoc block "matching the POST operation's
  depth" will breach it — budget the whole GET (annotations + body) at roughly 100 lines by using one `200`
  example and a single `400` `@ApiResponse` carrying several named `@ExampleObject`s.
  `LambdaBodyLength max=1` (line 103): every `ConversionSpecification` lambda body must sit on exactly one
  line — wrapping after `->` is fine, wrapping the body itself is a violation.
- ⚠️ **`MultipleStringLiterals` will fire on the attribute names.** `"createdAt"` is used twice inside
  `ConversionSpecification` (`isCreatedOnOrAfter` + `isCreatedBefore`) and a third time in the `core`-built
  `Sort`. Hoist `client`, `clientId`, `transactionId`, `createdAt` and `id` into constants in
  `src/main/java/zetta/foreignexchange/persistence/constant/` (beside `EntityConstant`) and use the *same*
  constants for both the specification paths and the `Sort` — otherwise the sort field and the filter field
  can silently drift apart. There is no JPA static metamodel on this build (no `hibernate-jpamodelgen` in the
  `annotationProcessorPaths`), so string attribute names are the only option.
- ⚠️ **`ConversionSpecification` and `PaginationConstant` must be `final` with a private no-arg constructor**
  (`FinalClass` + `HideUtilityClassConstructor`), following
  `src/main/java/zetta/foreignexchange/persistence/constant/EntityConstant.java`.
- ⚠️ **Never call `root.fetch("client")` inside the `Specification`.** The same specification is applied to
  the count query, where a fetch join fails outright. The `@EntityGraph` override on the repository is the
  only correct place for the fetch. Filtering via `root.get("client").get("clientId")` additionally emits an
  implicit inner join alongside the graph's fetch join — that is harmless (`conversions.client_id` is
  `NOT NULL`) and must **not** be "optimised away" by fetching inside the specification.
- ⚠️ **Test isolation against committed rows.** `ConversionConcurrencyIntegrationTest` commits real
  `conversions` rows with `created_at = now()` that outlive the rollback transaction. Every history
  integration test must be scoped to a `CLIENT-TEST-HISTORY-*` client or to the 2020 fixture dates. Never
  assert an unscoped `date=<today>` result, and never assert a global row count or `totalElements` that is
  not narrowed by `clientId`.
- ⚠️ **Read-only means read-only.** `@Transactional(readOnly = true)` on the service method and nothing more:
  no `@Lock`, no `@Version`, no `Idempotency-Key`, no `ConversionProcessor`. `ConversionEntity` is
  `@Immutable` — entities read on this path must never be mutated, and no entity or `Specification` type may
  cross into `rest`. `Page<Conversion>` reaching `rest/mapper` is an accepted framework-type exception; a
  `ConversionEntity` there would not be.
- ⚠️ **README (Seq 13) must record the access consequence, not just the `404`/`200` asymmetry of D8.4.**
  `GET /conversions` performs no ownership check: any caller may read any `clientId`'s history, and a
  `date`-only filter returns every client's conversions for that day. That is a deliberate consequence of the
  assignment's no-authentication non-goal — state it explicitly, together with `MAX_PAGE_SIZE = 100` as the
  bound on how much can be extracted per request.

---

## Test Results

**Iterations:** 1/3
**Reviewer verdict:** ✅ Approved (no blocking issues; one MEDIUM AC-coverage gap closed post-review)

**Tests:**
- ConversionHistoryIntegrationTest: 17/17 ✅ (full-stack, Testcontainers PostgreSQL, V901 fixture)
- ConversionServiceTest: 13/13 ✅ (incl. ≥1-filter rule + Sort `createdAt DESC, id DESC` captured via `ArgumentCaptor<Pageable>`)
- ConversionControllerTest: 4/4 ✅ (incl. default page/size)
- ForeignExchangeControllerAdviceTest: 16/16 ✅ (incl. `CONVERSION_FILTER_REQUIRED` + `MethodArgumentTypeMismatchException`)
- ConversionMapperTest: 2/2 ✅ · ConversionResponseMapperTest: 5/5 ✅ (backward-compat guard: no `clientId` on `ConversionResponse`)
- ConversionHistoryQueryMapperTest: 1/1 ✅ · ConversionHistoryResponseMapperTest: 2/2 ✅
- **Full suite: 158/158 ✅ · `mvn checkstyle:check`: 0 violations ✅**

**Assignment coverage:**
- ✅ `clientId` filter: every conversion of that client, newest first (`createdAt DESC, id DESC`)
- ✅ `transactionId` filter: exactly one item with the supplied UUID
- ✅ `date` filter: half-open UTC day — the fixture row at exactly `2020-01-16T00:00:00Z` is excluded from `date=2020-01-15`
- ✅ Filters combine as AND (intersection asserted); combination matching nothing → `200` with `content: []`
- ✅ No filter → `400` `CONVERSION_FILTER_REQUIRED` with `path: /conversions`, validated in `core` (`verifyNoInteractions` on the repository)
- ✅ Unknown `clientId` → `200` empty page, `totalElements: 0` — not `404`
- ✅ Envelope is `{content, page, size, totalElements, totalPages}`; no Spring `Page` internals (`pageable`, `sort`, `first`, `last`, `empty`, `numberOfElements` all asserted absent)
- ✅ Item wire shape: `transactionId, clientId, sourceCurrency, sourceAmount, targetCurrency, targetAmount, rate, timestamp` (D1 aliasing)
- ✅ `POST /conversions` byte-identical: `ConversionResponse` has no `clientId` (asserted)

**Edge cases covered:**
- ✅ Omitted `page`/`size` → defaults `0`/`20`
- ✅ `size=101`, `size=0`, `page=-1` → `400` (rejected, never clamped)
- ✅ Non-numeric `page`/`size` → `400` via new `MethodArgumentTypeMismatchException` handler (architect-flagged 500 bug closed)
- ✅ Malformed `date` (`2020-13-45`) / malformed `transactionId` → `400`, consistent `ErrorResponse`, never a 500
- ✅ `size=2` over 3 rows: pages 0 and 1 cover each row exactly once, newest-first (stable paging via `id DESC` tie-break)
- ✅ `page` beyond last page → `200`, empty `content`, full `totalElements`/`totalPages`
