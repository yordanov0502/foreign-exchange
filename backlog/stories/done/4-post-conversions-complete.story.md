# 4 — Implement `POST /conversions` end to end: atomic debit/credit, error paths, concurrency, idempotency

| Field | Value |
|---|---|
| Story Points | 21 |
| Priority | Critical |
| Status | Done |

**Story points rationale — a deliberate consolidation, not an estimation slip.** This story merges the
former Seq 4 (happy path, 8), Seq 5 (error paths, 3), Seq 6 (concurrency, 5) and Seq 7 (idempotency, 3)
into one unit, at the **user's explicit instruction** to plan the complete endpoint "regardless of story
points". 21 is the honest Fibonacci total; the planner's normal ≤ 8 rule is knowingly suspended. The
justification for merging rather than splitting: all four slices write the same method, the same
transaction boundary and the same response record — splitting them would mean implementing
`ConversionServiceImpl` and its test suite three times over. TDD discipline is preserved *inside* the
story as seven ordered Red→Green→Refactor slices (see TDD Steps); only the merge/review unit is one.

> **Planned against commit `14f324c`** (`main`, after PR #1 merged Seq 3). Seq 3 is **Done** — this story
> consumes the real `RateService`; it does not define or stub a rate contract of its own.

---

## User Story

**As a** foreign-exchange service
**I want** to convert an amount for a client — debiting the source currency, crediting the target currency
and recording the conversion in a single database transaction, rejecting insufficient funds and unknown
clients/currencies, never double-spending under concurrent calls, and returning the original result when
an `Idempotency-Key` is replayed
**So that** the assignment's `POST /conversions` endpoint, its per-client-balance rules, its concurrency
requirement and its idempotency requirement are all satisfied
(`.claude/Java-Assignment.pdf` — "What you will build" → `POST /conversions`; "Per-client account
balances" → successful conversion / insufficient funds / unknown client / concurrency; "Technical
requirements" → money as `BigDecimal`, idempotency, error handling).

---

## Context

**Current behavior.** The endpoint does not exist. `rest/controller/ConversionController.java` is an empty
`@RestController` mapped at `/conversions` with no handler; `core/service/ConversionService.java` is an
empty interface and `ConversionServiceImpl` an empty `@Service`. `ConversionRepository` has no query
methods. Nothing has ever written to the `conversions` table or mutated a `balances` row — `BalanceEntity`
is setter-free and has no mutating method at all.

**What already exists and must be reused, not rebuilt** (this is the single biggest change since the last
planning pass — Seq 3 landed in PR #1):

| Capability | Where it lives now |
|---|---|
| Rate lookup, cached, provider-fault-translated | `core/service/RateService.getExchangeRate(baseCurrency, quoteCurrency)` → `ExchangeRate(date, baseCurrency, quoteCurrency, rate)` |
| TTL cache | `common/cache/CacheConfiguration` (Caffeine, cache name `exchangeRate`, `@Cacheable(sync = true)`, TTL/max-size from `cache.currency-rate-pair`) |
| ISO-4217 + shape validation | `core/validator/CurrencyValidator.validateCurrencyPair(...)` → throws `UnsupportedCurrencyPairException` (this story adds D2's identical-pair rule to it) |
| Provider failure / unusable answer | `ExchangeRateUnavailableException` → already mapped to `502 EXCHANGE_RATE_UNAVAILABLE` |
| Unquotable or invalid pair | `UnsupportedCurrencyPairException` → already mapped to `422 UNSUPPORTED_CURRENCY_PAIR` |
| Error body + advice | `rest/error/ErrorResponse`, `rest/controlleradvice/ForeignExchangeControllerAdvice` (3 handlers) |

**Required behavior.** `POST /conversions`, carrying `X-Client-Id` and an optional `Idempotency-Key`
header plus a JSON body of `{sourceCurrency, targetCurrency, sourceAmount}`:

- resolves the rate through `RateService` **before** any database transaction is opened,
- inside one transaction: locks the client's two balance rows, debits the source, credits the target, and
  inserts the conversion row — all of it or none of it,
- returns `transactionId`, `sourceCurrency`, `sourceAmount`, `targetCurrency`, `targetAmount`, `rate`,
  `timestamp` and the client's **updated** balances,
- returns `404 CLIENT_NOT_FOUND` / `404 BALANCE_NOT_FOUND`, `422 INSUFFICIENT_FUNDS` (with **no**
  conversion row persisted), and `400` for a malformed or invalid request; a bad currency code already
  yields `422 UNSUPPORTED_CURRENCY_PAIR` and a dead provider `502 EXCHANGE_RATE_UNAVAILABLE` through
  Seq 3's existing handlers,
- never double-spends when two requests for the same client run at once,
- returns the *original* conversion when the same `(clientId, Idempotency-Key)` pair is replayed, with no
  second row and no second debit.

**What this unlocks.** Seq 8 (`GET /conversions` history) gets rows to page over; Seq 12 gets the
correctness scenarios it audits; Seq 13's README gets the concurrency and idempotency trade-offs.

**Grading axis.** Four of the five *Correctness* bullets in the brief's rubric are won or lost here
("money math is precise; idempotency works; balances debit/credit atomically; insufficient funds is
rejected"), plus much of *Testing discipline* (the brief demands idempotency replay, insufficient funds
and happy-path debit/credit be tested **explicitly**) and *Production sense* (concurrency, error handling).

---

## Two Decisions Taken — user ruling, 2026-09-20

Both were raised as open questions during planning and have been **decided by the user**. They are no
longer proposals; everything below implements them.

**D1 — `base`/`quote` is the vocabulary everywhere in Java, including the REST records; the wire
format is renamed to `source`/`target` with Jackson annotations.**
`V3` renamed the `conversions` columns to `base_currency`/`base_amount`/`quote_currency`/`quote_amount`,
and `ExchangeRate`, `RateService`, `CurrencyValidator` and the advice messages all speak `base`/`quote`.
The brief, however, names the `POST /conversions` fields verbatim: *"Returns transactionId, **sourceAmount,
sourceCurrency, targetAmount, targetCurrency**, rate, timestamp, and the client's updated balances."*

**The ruling satisfies both at once:** `rest/model/ConversionRequest` and `rest/model/ConversionResponse`
declare their components as `baseCurrency` / `baseAmount` / `quoteCurrency` / `quoteAmount` — consistent
with every other layer — and each carries `@JsonProperty("sourceCurrency")`, `@JsonProperty("sourceAmount")`,
`@JsonProperty("targetCurrency")`, `@JsonProperty("targetAmount")`, so Postman, curl and the generated
Swagger schema all show the brief's names. See **R15** for the mechanics and the traps.

Consequence: `ConversionResponseMapper` needs **no** renaming `@Mapping` for the currency/amount fields —
the names match on both sides and Jackson does the renaming at the edge. The brief's contract is honoured
on the wire, and Java stays internally consistent.

**D2 — identical base and quote currency is rejected on *both* endpoints, through one shared
`SameCurrencyException`.**
Rejecting it only on `POST /conversions` would leave the two endpoints disagreeing about what a valid pair
is. The check therefore moves **into `CurrencyValidator.validateCurrencyPair`**, which both
`RateServiceImpl` and `ConversionServiceImpl` call, so one rule covers both entry points and the
conversion path gets it for free.

> **This is an approved behaviour change to Seq 3.** `GET /rates?from=USD&to=USD` currently returns
> `200` with `rate = 1` — Seq 3's "Seq 3 correction" note in `PRIORITY.md` recorded that as a deliberate
> success path. It now returns `422 SAME_CURRENCY` without calling the provider. Per the Backward
> Compatibility rule in `.claude/CLAUDE.md`, a behaviour change needs explicit user approval; **it has
> been given**. Three existing Seq 3 tests assert the old behaviour and must be rewritten, and
> `RateController`'s `@Operation` description must stop advertising it — all listed below.

Exception naming is the user's: **`SameCurrencyException`** (not `SameCurrencyConversionException`), shared
by both endpoints, mapped to `422 SAME_CURRENCY`.

---

## Scope

### In Scope

- [ ] `ConversionService` contract + package-private implementation covering the whole flow
- [ ] A separate, package-private `@Transactional` collaborator owning the atomic
      lock → debit → credit → insert unit of work, so the rate call stays outside the transaction
- [ ] Core domain records: `ConversionCommand`, `Conversion`, `ConversionResult`
- [ ] REST DTOs: `ConversionRequest` (body) and `ConversionResponse` — `base`/`quote` component names,
      `@JsonProperty` renaming them to `source`/`target` on the wire (D1) — reusing `BalanceResponse`
- [ ] MapStruct mappers at both boundaries: `ConversionMapper` (entity → domain) and
      `ConversionResponseMapper` (request + headers → command, result → response)
- [ ] `SameCurrencyException` + the identical-pair check inside `CurrencyValidator`, applying to **both**
      `POST /conversions` and `GET /rates` (D2), including the rework of the three Seq 3 tests and the
      `RateController` description that assert the superseded rate-of-1 behaviour
- [ ] `BalanceEntity.debit(BigDecimal baseAmount)` / `credit(BigDecimal quoteAmount)` — the only way a
      balance is ever mutated (entities stay setter-free)
- [ ] Money arithmetic: `quoteAmount = baseAmount × rate`, with explicit scale and rounding mode as named
      constants (REQ-11)
- [ ] Domain exceptions `BalanceNotFoundException`, `InsufficientFundsException`, `SameCurrencyException`;
      `ClientNotFoundException`, `UnsupportedCurrencyPairException` and `ExchangeRateUnavailableException`
      are reused untouched
- [ ] New `@RestControllerAdvice` handlers for those three, plus request-validation failures and a
      catch-all `500`, all returning the established `ErrorResponse` body as `application/problem+json`
- [ ] `core/model/ErrorCode` enum replacing the loose string constants in the advice — **without changing
      any value already emitted** by Seq 2/Seq 3 (see R9; deferrable to Seq 9 if the reviewer prefers)
- [ ] Repository additions: a pessimistic-write balance finder, an idempotency-key finder, `findByClientId`
- [ ] Bean validation on the `POST /conversions` request: non-blank `X-Client-Id`, positive and bounded
      `sourceAmount`, three-letter currency codes (ISO-4217 *membership* is already `CurrencyValidator`'s job)
- [ ] SpringDoc annotations matching the depth already set by `ClientController` and `RateController`
- [ ] Tests: mapper unit tests on both layers, entity `debit`/`credit` unit tests, service unit tests,
      controller slice tests, advice unit tests, and integration tests through the Spring context covering
      happy-path debit/credit, insufficient funds, both not-found paths, idempotent replay, and a genuinely
      parallel no-double-spend test

### Out of Scope

- **Anything about the rate provider** — `RateService`, the Feign client, timeouts, the TTL cache, the
  rate DTO/mapper, and the `UNSUPPORTED_CURRENCY_PAIR` / `EXCHANGE_RATE_UNAVAILABLE` handlers are **Seq 3,
  already Done**. Consume them; change none of them. **The single, user-approved exception is D2**: the
  identical-pair rule added to `CurrencyValidator`, which necessarily changes `GET /rates`' answer for
  `from == to`. Nothing else in the rate path may be touched.
- **Renaming `ExchangeRateResponse`'s wire fields.** `GET /rates` keeps answering `baseCurrency` /
  `quoteCurrency`; the brief never names the rate endpoint's response fields, `RateIntegrationTest`
  asserts them, and D1's `@JsonProperty` aliasing is scoped to the conversion DTOs. The resulting
  asymmetry (`/rates` says base/quote on the wire, `/conversions` says source/target) is deliberate —
  record it in the README (REQ-21) rather than "fixing" it here.
- `GET /conversions` history, paging and filters — Seq 8 (REQ-3)
- Generalising validation and the exception-to-status matrix across every endpoint — Seq 9
  (REQ-14, REQ-15); this story adds only what `POST /conversions` needs
- A full OpenAPI polish pass — Seq 10 (REQ-16)
- Deposit/withdraw endpoints, a ledger/journal model, authentication — explicit assignment Non-Goals
- **Any new production migration.** `V1` gave us the `balances.version` column, the
  `balances_amount_non_negative` CHECK, the partial unique index on `(client_id, idempotency_key)` and the
  unique `transaction_id`; `V3` renamed the conversion columns to `base`/`quote`; `V4` narrowed
  `conversions.rate` to `NUMERIC(19,5)`. All four are applied and frozen. The only SQL this story touches
  is the **test fixture** `src/test/resources/db/testdata/V900__*.sql`.

---

## Acceptance Criteria

**Happy path — REQ-2, REQ-6, REQ-11**

- [ ] `POST /conversions` with `X-Client-Id: CLIENT-001` and body
      `{"sourceCurrency":"USD","targetCurrency":"EUR","sourceAmount":100.00}` returns `201` with
      `transactionId`, `sourceCurrency`, `sourceAmount`, `targetCurrency`, `targetAmount`, `rate`,
      `timestamp` and `balances`
- [ ] `balances` is the client's **post-conversion** state, one entry per currency held, ordered by
      currency ascending — the source currency lower by exactly `sourceAmount`, the target currency higher
      by exactly `targetAmount`
- [ ] Exactly one `conversions` row is persisted, its `transaction_id` equal to the returned
      `transactionId`, its `base_currency`/`base_amount`/`quote_currency`/`quote_amount`/`rate` equal to the
      values returned, and its `created_at` equal to the returned `timestamp`
- [ ] `targetAmount` equals `sourceAmount × rate` rounded to scale 4 `HALF_UP`; every amount on the path is
      `BigDecimal` — no `double` or `float` in any new code
- [ ] The rate used is the one `RateService` returns, already at scale 5 — this story does not re-round it
- [ ] Debit, credit and the conversion insert are one transaction: forcing a failure at the insert leaves
      both balances unchanged
- [ ] `RateService` is called **outside** the balance transaction (no DB transaction is open across the
      provider call)
- [ ] The JSON body and response use the brief's `sourceCurrency` / `sourceAmount` / `targetCurrency` /
      `targetAmount` names, while the Java record components are `baseCurrency` / `baseAmount` /
      `quoteCurrency` / `quoteAmount` (D1) — proven by a test that posts the brief's field names and reads
      them back, and by the generated OpenAPI schema showing the same names

**Error paths — REQ-7, REQ-8**

- [ ] Source balance below `sourceAmount` returns `422` with `code: INSUFFICIENT_FUNDS`, **no**
      `conversions` row persisted and **neither** balance changed
- [ ] Unknown `X-Client-Id` returns `404 CLIENT_NOT_FOUND`, and the rate provider is never called
- [ ] A known client holding no row in the source currency returns `404 BALANCE_NOT_FOUND`; likewise when
      the client holds no row in the **target** currency
- [ ] `sourceCurrency` equal to `targetCurrency` returns `422 SAME_CURRENCY` (per D2) without calling the
      provider, without touching the database, and without caching anything
- [ ] **`GET /rates?from=USD&to=USD` also returns `422 SAME_CURRENCY`** and never calls the provider —
      the same `SameCurrencyException` from the same `CurrencyValidator` rule (D2), superseding the
      previous `200` with `rate = 1`
- [ ] An unknown or malformed currency code returns `422 UNSUPPORTED_CURRENCY_PAIR` and a dead provider
      returns `502 EXCHANGE_RATE_UNAVAILABLE` — both through Seq 3's existing handlers, unchanged
- [ ] A zero, negative, absent or over-scaled `sourceAmount`, a blank or absent `X-Client-Id`, and an
      unparseable JSON body each return `400` with a structured `ErrorResponse` — never a stack trace
- [ ] Every error body keeps the `code`/`message`/`status`/`path` shape as `application/problem+json`, and
      the codes, messages, statuses and paths already asserted by `BalanceIntegrationTest`,
      `RateIntegrationTest` and `ForeignExchangeControllerAdviceTest` stay byte-identical

**Concurrency — REQ-9**

- [ ] Two conversions submitted in parallel for the same client, each affordable alone but not together,
      produce exactly one `201` and one `422 INSUFFICIENT_FUNDS`; exactly one `conversions` row exists and
      the source balance is debited exactly once
- [ ] Two parallel conversions that are both affordable both succeed, and the final source balance equals
      the start minus the sum of both amounts — no lost update
- [ ] The balance is never left negative under any interleaving

**Idempotency — REQ-10**

- [ ] Replaying the same `Idempotency-Key` for the same client returns the **original** `transactionId`,
      amounts and rate; the `conversions` row count and the balances are unchanged
- [ ] Two requests with the same `(clientId, Idempotency-Key)` sent in parallel produce exactly one
      `conversions` row and exactly one debit — the loser of the race returns the winner's result, not an error
- [ ] The same `Idempotency-Key` used by a *different* client is not treated as a replay
- [ ] A request with **no** `Idempotency-Key` succeeds and is never treated as a replay

---

## Technical Notes

### Layers Affected

- [x] `core` — `ConversionService` + impl, the transactional executor, three domain records, `ErrorCode`,
      three new exceptions, `ConversionMapper`, the rounding-mode constant, and one new rule inside the
      existing `CurrencyValidator` (D2)
- [x] `persistence` — `BalanceEntity.debit`/`credit`, three repository query methods (no migration)
- [x] `rest` — `ConversionController`, request/response DTOs with `@JsonProperty` aliasing (D1),
      `ConversionResponseMapper`, advice handlers, and a corrected `RateController` description (D2)
- [ ] `common` — untouched; the cache and the Frankfurter client are Seq 3's and stay as they are

### Files to Create / Modify

> Paths are relative to the repository root `C:\Users\yorda\Desktop\foreign-exchange`.

**Create — `core`**
- `src/main/java/zetta/foreignexchange/core/constant/MoneyConstant.java`
- `src/main/java/zetta/foreignexchange/core/model/ConversionCommand.java`
- `src/main/java/zetta/foreignexchange/core/model/Conversion.java`
- `src/main/java/zetta/foreignexchange/core/model/ConversionResult.java`
- `src/main/java/zetta/foreignexchange/core/model/ErrorCode.java`
- `src/main/java/zetta/foreignexchange/core/exception/BalanceNotFoundException.java`
- `src/main/java/zetta/foreignexchange/core/exception/InsufficientFundsException.java`
- `src/main/java/zetta/foreignexchange/core/exception/SameCurrencyException.java`
- `src/main/java/zetta/foreignexchange/core/mapper/ConversionMapper.java`
- `src/main/java/zetta/foreignexchange/core/service/implementation/ConversionExecutor.java`

**Create — `rest`**
- `src/main/java/zetta/foreignexchange/rest/model/ConversionRequest.java`
- `src/main/java/zetta/foreignexchange/rest/model/ConversionResponse.java`
- `src/main/java/zetta/foreignexchange/rest/mapper/ConversionResponseMapper.java`

**Modify — `main`**
- `src/main/java/zetta/foreignexchange/core/service/ConversionService.java` — replace the empty interface
  with `ConversionResult convert(ConversionCommand conversionCommand)`
- `src/main/java/zetta/foreignexchange/core/service/implementation/ConversionServiceImpl.java` — the
  orchestration: same-currency guard → client existence → idempotency pre-check → `RateService` → delegate
  to `ConversionExecutor` → duplicate-key fallback
- `src/main/java/zetta/foreignexchange/persistence/entity/BalanceEntity.java` — add `debit` / `credit`
- `src/main/java/zetta/foreignexchange/persistence/repository/BalanceRepository.java` — add the
  pessimistic-write single-currency finder
- `src/main/java/zetta/foreignexchange/persistence/repository/ConversionRepository.java` — add
  `findByClientClientIdAndIdempotencyKey`
- `src/main/java/zetta/foreignexchange/persistence/repository/ClientRepository.java` — add
  `findByClientId` (the conversion row needs the managed `ClientEntity`, not just an existence check)
- `src/main/java/zetta/foreignexchange/rest/controller/ConversionController.java` — the handler +
  SpringDoc annotations
- `src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java` — new
  handlers; the three existing handlers keep their exact behaviour

**Modify — `main`, under D2 only (the one approved incursion into Seq 3's code)**
- `src/main/java/zetta/foreignexchange/core/validator/CurrencyValidator.java` — add a private
  `validateCurrenciesAreDifferent(baseCurrency, quoteCurrency)` called from `validateCurrencyPair`,
  throwing `SameCurrencyException`. Order it **after** the regex and ISO-4217 checks, so `usd`/`usd`
  still reports `UNSUPPORTED_CURRENCY_PAIR` rather than being excused as "same". Leave
  `validateCurrencyPairsMatch` alone.
- `src/main/java/zetta/foreignexchange/rest/controller/RateController.java` — **documentation only**: the
  `@Operation` description currently ends *"An identical source and target currency is a valid quote of
  1."*, which D2 falsifies. Replace that sentence and add `SAME_CURRENCY` to the documented `422`
  example. No change to the handler method.

**Do not touch:** `core/service/RateService.java`, `core/service/implementation/RateServiceImpl.java`,
`core/model/ExchangeRate.java`, `core/mapper/RateMapper.java`,
`core/exception/ExchangeRateUnavailableException.java`,
`core/exception/UnsupportedCurrencyPairException.java`, everything under `common/`,
`rest/model/ExchangeRateResponse.java`, `rest/mapper/ExchangeRateResponseMapper.java`, and
`src/main/resources/db/migration/*`.

**Create — tests**
- `src/test/java/zetta/foreignexchange/persistence/entity/BalanceEntityTest.java`
- `src/test/java/zetta/foreignexchange/core/mapper/ConversionMapperTest.java`
- `src/test/java/zetta/foreignexchange/core/service/ConversionServiceTest.java`
- `src/test/java/zetta/foreignexchange/core/service/ConversionExecutorTest.java`
- `src/test/java/zetta/foreignexchange/rest/mapper/ConversionResponseMapperTest.java`
- `src/test/java/zetta/foreignexchange/rest/controller/ConversionControllerTest.java`
- `src/test/java/zetta/foreignexchange/integration/ConversionIntegrationTest.java`
- `src/test/java/zetta/foreignexchange/integration/ConversionConcurrencyIntegrationTest.java`

**Modify — tests (additive)**
- `src/test/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdviceTest.java` —
  one test per new handler; the three existing tests must keep passing untouched
- `src/test/java/zetta/foreignexchange/integration/BaseIntegrationTestSetUp.java` — add the new fixture
  client-id constants alongside `CLIENT_TEST_ID` / `CLIENT_TEST_WITHOUT_BALANCES_ID`
- `src/test/resources/db/testdata/V900__seed_integration_test_clients_and_balances.sql` — fixture clients
  for the conversion and concurrency tests (see R4)
- `src/test/java/zetta/foreignexchange/core/validator/CurrencyValidatorTest.java` — add
  `validateCurrencyPair_withIdenticalCurrencies_throwSameCurrencyException` and a lowercase-identical case
  (`usd`/`usd`) proving `UNSUPPORTED_CURRENCY_PAIR` still wins on a malformed pair

**Modify — tests (rewriting an assertion, under D2's approved behaviour change)**

These three assert the superseded `USD/USD → rate 1` behaviour and **will fail** until rewritten. They are
the reason D2 needed explicit approval; do not "fix" them by weakening the new rule.
- `src/test/java/zetta/foreignexchange/core/service/RateServiceTest.java` —
  `getExchangeRate_withIdenticalCurrencyPair_returnExchangeRateOfOne` becomes
  `getExchangeRate_withIdenticalCurrencyPair_throwSameCurrencyException`, with
  `verifyNoInteractions(frankfurterFeignClient)`
- `src/test/java/zetta/foreignexchange/rest/controller/RateControllerTest.java` —
  `getExchangeRate_withIdenticalCurrencyPair_returnExchangeRateResponse` becomes a propagation test
  (`rateService` stubbed to throw `SameCurrencyException`, assert it surfaces)
- `src/test/java/zetta/foreignexchange/integration/RateIntegrationTest.java` —
  `getExchangeRate_withIdenticalCurrencyPair_returnRateOfOne` becomes
  `getExchangeRate_withIdenticalCurrencyPair_returnSameCurrencyWithoutCallingProvider`, asserting `422`,
  `code: SAME_CURRENCY`, the message, the status and `path: /rates`, plus
  `verifyNoInteractions(frankfurterFeignClient)`

### Existing Pattern Reference

**Service shape** — `core/service/implementation/BalanceServiceImpl.java`: constructor-injected
collaborators, a guard clause throwing a domain exception, entities mapped before leaving the service.

```java
@Override
@Transactional(readOnly = true)
public List<Balance> getClientBalances(String clientId) {
    validateClientExists(clientId);
    List<BalanceEntity> balanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
    return balanceMapper.mapToBalances(balanceEntities);
}
```

**Service that orchestrates and delegates faults** — `core/service/implementation/RateServiceImpl.java`
shows the established shape for a service that calls out, catches foreign exceptions and rethrows domain
ones; `ConversionServiceImpl` follows the same structure for `DataIntegrityViolationException`.

**Entity → domain mapper, with a renaming `@Mapping` and a scaling helper** — `core/mapper/RateMapper.java`.
This is the exact pattern `ConversionMapper` and `ConversionResponseMapper` need, and it also shows that
`core` reusing `persistence.constant.EntityConstant` is already accepted here (a legal `core → persistence`
dependency):

```java
@Mapping(target = "baseCurrency", source = "base")
@Mapping(target = "quoteCurrency", source = "quote")
@Mapping(target = "rate", source = "rate", qualifiedByName = "scaleRate")
ExchangeRate mapToRate(FrankfurterRatePairResponse frankfurterRatePairResponse);
```

**Multi-source mapper method** — `rest/mapper/ClientBalancesResponseMapper.java`; exactly the trick
`ConversionResponseMapper` needs to fold header values into the command:

```java
ClientBalancesResponse mapToClientBalancesResponse(String clientId, List<Balance> balances);
```

**Controller shape** — `rest/controller/RateController.java` is the most recent and most complete example:
zero logic, `@Tag`/`@Operation`/`@ApiResponses` with per-status schemas and JSON examples, error responses
documented as `APPLICATION_PROBLEM_JSON_VALUE`. Match that depth. `rest/model/ExchangeRateResponse.java`
shows the per-field `@Schema` style the new DTOs should follow.

**Advice shape** — `rest/controlleradvice/ForeignExchangeControllerAdvice.java`, e.g.:

```java
ErrorResponse errorResponse = ErrorResponse.builder()
        .code(UNSUPPORTED_CURRENCY_PAIR_CODE)
        .message(format(UNSUPPORTED_CURRENCY_PAIR_MESSAGE, exception.getBaseCurrency(), exception.getQuoteCurrency()))
        .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
        .path(request.getRequestURI())
        .build();

return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(errorResponse);
```

Note `HttpStatus.UNPROCESSABLE_CONTENT` (not `UNPROCESSABLE_ENTITY`) is the constant this codebase uses.

**Domain exception shape** — `core/exception/UnsupportedCurrencyPairException.java`: `@Getter`, explicit
constructor, a `Throwable cause`, a Javadoc sentence saying whose fault it is. The new exceptions follow it.

**Service unit test shape** — `core/service/BalanceServiceTest.java` and `core/service/RateServiceTest.java`:
`@ExtendWith(MockitoExtension.class)`, `@Mock` collaborators, the **real** mapper as a `@Spy` via
`Mappers.getMapper(...)`, `Instancio.create(...)` where values do not matter, `verify(mock)` with no
`times(1)`, `verifyNoInteractions(...)` on negative paths.

**Mapper unit test shape** — `core/mapper/BalanceMapperTest.java`: one test per method plus a `null` case
and an empty-collection case, money as `new BigDecimal("8000.0000")` hoisted into constants.

**Integration test shape** — `integration/RateIntegrationTest.java` is the model for this story: it extends
`BaseIntegrationTestSetUp`, keeps **everything real** and replaces only the outermost edge with
`@MockitoBean FrankfurterFeignClient`, and clears the shared cache in `@BeforeEach`:

```java
@MockitoBean
private FrankfurterFeignClient frankfurterFeignClient;

@BeforeEach
void clearRatesCache() {
    Objects.requireNonNull(cacheManager.getCache(CacheConfiguration.EXCHANGE_RATE)).clear();
}
```

Money is asserted with `.value(comparesEqualTo(expected), BigDecimal.class)`, never string equality.

### TDD Steps

Seven ordered slices. Each is a full Red → Green → Refactor cycle; do not start the next until the current
one is green.

**Slice 1 — money arithmetic, balance mutation, mappers**
- **Red:** `BalanceEntityTest` — `debit_withAmountBelowBalance_reduceAmount`,
  `debit_withAmountEqualToBalance_leaveZeroAmount`,
  `debit_withAmountAboveBalance_throwIllegalStateException`, `credit_withAmount_increaseAmount`.
  Then `ConversionMapperTest` and `ConversionResponseMapperTest` — field-by-field mapping, a `null` input,
  an empty balance list. Add one **serialisation** test per REST record (plain `ObjectMapper`, no Spring)
  proving D1's aliasing in both directions: a body using `sourceCurrency`/`sourceAmount`/`targetCurrency`
  deserialises into the `base*`/`quote*` components, and a `ConversionResponse` serialises back out under
  the `source*`/`target*` names.
- **Green:** `MoneyConstant`, `BalanceEntity.debit`/`credit`, the three core records, the two DTOs, the
  two mappers.
- **Refactor:** hoist repeated currency/amount literals into constants (checkstyle forbids duplicate
  string literals); confirm no `double`/`float` slipped in.

**Slice 2 — happy-path atomic debit/credit**
- **Red:** `ConversionExecutorTest` —
  `executeConversion_withSufficientFunds_debitSourceCreditTargetAndPersistConversion`, asserting the source
  entity's amount fell by `baseAmount`, the target's rose by `quoteAmount`, and `saveAndFlush` was called
  with the right row. Then
  `ConversionServiceTest.convert_withSufficientFunds_returnConversionResult`, proving the rate is fetched
  through `RateService` and the executor delegated to.
- **Green:** `ConversionExecutor`, `ConversionService.convert`, `ConversionServiceImpl`, the three new
  repository methods.
- **Refactor:** extract the quote-amount computation into `computeQuoteAmount`; keep `executeConversion`
  under the 40-line checkstyle limit.

**Slice 3 — validation and error paths (REQ-7, REQ-8)**
- **Red:** `convert_withUnknownClientId_throwClientNotFoundException` **plus**
  `verifyNoInteractions(rateService)`;
  `convert_withCurrencyTheClientDoesNotHold_throwBalanceNotFoundException` (source and target as two
  cases); `convert_withInsufficientSourceBalance_throwInsufficientFundsException` **plus**
  `verifyNoInteractions(conversionRepository)` to pin "no conversion record persisted".

  Then **D2, which spans both endpoints** — start at `CurrencyValidatorTest`
  (`validateCurrencyPair_withIdenticalCurrencies_throwSameCurrencyException`, plus `usd`/`usd` still
  raising `UNSUPPORTED_CURRENCY_PAIR`), then
  `convert_withIdenticalBaseAndQuoteCurrency_throwSameCurrencyException` with
  `verifyNoInteractions(rateService, clientRepository, conversionExecutor)`, then rewrite the three Seq 3
  tests listed above so the `GET /rates` half of the same rule is red for the right reason.
- **Green:** the three new exceptions, the guard clauses in the order fixed by R2, and
  `validateCurrenciesAreDifferent` inside `CurrencyValidator`. Update `RateController`'s `@Operation`
  description in the same step — a doc string that contradicts the code is a defect, not a cosmetic.
- **Refactor:** one private `validate*` method per rule, mirroring `BalanceServiceImpl.validateClientExists`
  and `CurrencyValidator`'s own private-method style.

**Slice 4 — idempotent replay (REQ-10)**
- **Red:** `convert_withReplayedIdempotencyKey_returnOriginalConversionWithoutSecondDebit` (executor never
  invoked, rate never fetched); `convert_withoutIdempotencyKey_executeConversion`;
  `convert_withIdempotencyKeyOfAnotherClient_executeConversion`;
  `convert_withConcurrentDuplicateIdempotencyKey_returnOriginalConversion` (executor throws
  `DataIntegrityViolationException`; the service re-reads and returns the winner's result).
- **Green:** the pre-check lookup, the `DataIntegrityViolationException` catch outside the transaction, the
  replay-result assembly.
- **Refactor:** one `findExistingConversionResult(clientId, idempotencyKey)` used by both the pre-check and
  the catch block.

**Slice 5 — concurrency (REQ-9)**
- **Red:** `ConversionConcurrencyIntegrationTest` —
  `createConversion_withTwoParallelRequestsExceedingBalance_persistOnlyOneConversion`,
  `createConversion_withTwoParallelAffordableRequests_debitBothWithoutLostUpdate`,
  `createConversion_withTwoParallelRequestsSharingIdempotencyKey_persistOnlyOneConversion`. These must run
  **outside** a test transaction (R4) or they cannot observe a real race.
- **Green:** the `@Lock(LockModeType.PESSIMISTIC_WRITE)` balance finder and the fixed lock ordering (R1).
- **Refactor:** pull the parallel-request harness into a private `runConversionsInParallel(...)` helper.

**Slice 6 — controller and advice wiring**
- **Red:** `ConversionControllerTest` (plain Mockito, mirroring `ClientControllerTest`/`RateControllerTest`)
  — `createConversion_withValidRequest_returnConversionResponse`, asserting status and every response
  field. Then `ForeignExchangeControllerAdviceTest` — one test per new handler, asserting code, message,
  status and path.
- **Green:** the controller handler, the DTO validation annotations, `@Validated` on the controller, the
  new advice handlers, the `ErrorCode` enum.
- **Refactor:** collapse the repeated `ErrorResponse` assembly in the advice into one private
  `buildErrorResponse(ErrorCode errorCode, String message, HttpStatus status, HttpServletRequest request)`
  — four parameters, exactly at the checkstyle limit — and re-point the three existing handlers at it
  without changing a single emitted value.

**Slice 7 — full-stack integration (REQ-17, REQ-18)**
- **Red:** `ConversionIntegrationTest` with `@MockitoBean FrankfurterFeignClient` and the cache cleared in
  `@BeforeEach` — `createConversion_withSufficientFunds_returnConversionAndUpdatedBalances` (assert the
  response body **and** re-read `GET /clients/{clientId}/balances` to prove persistence),
  `createConversion_withInsufficientFunds_returnUnprocessableContentAndPersistNoConversion`,
  `createConversion_withUnknownClientId_returnNotFound`,
  `createConversion_withCurrencyNotHeldByClient_returnNotFound`,
  `createConversion_withReplayedIdempotencyKey_returnOriginalConversion`,
  `createConversion_withIdenticalCurrencies_returnUnprocessableContent`,
  `createConversion_withUnknownCurrencyCode_returnUnsupportedCurrencyPair`,
  `createConversion_whenProviderIsUnavailable_returnBadGateway`,
  `createConversion_withNegativeSourceAmount_returnBadRequest`,
  `createConversion_withBlankClientIdHeader_returnBadRequest`,
  `createConversion_withMalformedJsonBody_returnBadRequest`.
- **Green:** whatever wiring gaps these surface.
- **Refactor:** extract `performConversion(clientId, idempotencyKey, body)` and a body builder into private
  helpers; hoist the endpoint path and header names into constants.

### Implementation Notes

**R1 — Concurrency strategy: pessimistic row lock, with `@Version` retained as a backstop.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<BalanceEntity> findAndLockByClientClientIdAndCurrency(String clientId, String currency);
```

*Why pessimistic, when Seq 1 chose `@Version`?* Seq 1 added the `version` column and justified optimistic
locking at the schema level; that column stays and keeps guarding every other write path. For this specific
read-modify-write, optimistic locking has a cost the schema story could not see: the conflict only surfaces
at flush, so recovering means **restarting the transaction**, which a `@Transactional` method cannot do by
self-invocation — it needs `spring-retry` (a new dependency) or a hand-rolled retry loop in an outer bean.
Without a retry, a perfectly valid request gets a `409` and the no-double-spend test asserts a lock
exception instead of a business outcome. A pessimistic lock serialises conversions **per client** for a
short, DB-only transaction, needs one annotation, and lets the parallel test assert exactly what the brief
asks for: one `201`, one `422 INSUFFICIENT_FUNDS`. Money semantics want serialisation here. This is a
deliberate revision of Seq 1's default for this code path and **should be signed off by @architect**
(`architect.md` Pattern 5) before Slice 5 — PRIORITY.md already names that sign-off as a precondition.
Record the choice and the rejected alternative in the README (REQ-21).

**Deadlock avoidance:** a conversion touches two rows. `CLIENT-001` running `USD→EUR` concurrently with
`EUR→USD` deadlocks if each locks its own source first. **Always acquire the two locks in ascending
currency-code order**, regardless of which is base and which is quote — two calls in a sorted order, not
one `IN (...)` query, because Postgres does not guarantee lock acquisition follows an `ORDER BY`. Comment
that line; it looks like pointless sorting otherwise.

**R2 — Check order is part of the contract.** Validate in exactly this sequence so the right code wins when
several conditions hold at once:
1. `currencyValidator.validateCurrencyPair(baseCurrency, quoteCurrency)` — free, no I/O. One call now
   covers three rules: shape (`UnsupportedCurrencyPairException`, 422), ISO-4217 membership (same), and
   D2's identical-pair rule (`SameCurrencyException`, 422). **Call it explicitly here**, do not rely on
   `RateServiceImpl` calling it later — the point is to fail before the client lookup and the provider call.
2. client exists (`clientRepository.findByClientId`) → `ClientNotFoundException` (404)
3. idempotency replay lookup → return the original result and stop
4. `rateService.getExchangeRate(baseCurrency, quoteCurrency)` — **outside any transaction**; this is also
   where `ExchangeRateUnavailableException` (502) comes from, already handled by Seq 3
5. *transaction opens* — lock the two balance rows; either missing → `BalanceNotFoundException` (404)
6. source balance ≥ `baseAmount` → else `InsufficientFundsException` (422)
7. debit, credit, insert the conversion

Steps 1–3 run before the rate call, so an obviously bad request never reaches the provider and never
pollutes the rate cache. `RateServiceImpl.getExchangeRate` validates the pair again internally — that
double call is deliberate and costs nothing: it is a pure, allocation-free check, and it keeps `GET /rates`
self-validating rather than trusting every caller to have validated first. Do not "optimise" it away by
removing the validation from either side.

**R3 — Transaction boundary, and why there are two beans.** `.claude/CLAUDE.md` forbids holding a
transaction open across the provider call. `ConversionServiceImpl.convert` is therefore **not**
`@Transactional`: it validates, checks idempotency, calls `RateService`, then delegates to a
package-private `@Component ConversionExecutor` whose `@Transactional`
`executeConversion(ConversionCommand conversionCommand, ExchangeRate exchangeRate)` owns steps 5–7. A
`@Transactional` method invoked from inside the same bean is never intercepted by Spring's proxy, so a
second bean is the simplest correct way to get a real boundary — and it is also where the
`DataIntegrityViolationException` catch must sit (outside the rolled-back transaction). This is the one
extra class in the story and it earns its keep as *the* transaction boundary; add no others.

Give `ConversionExecutor` a second `@Transactional(readOnly = true)`
`findExistingConversionResult(String clientId, String idempotencyKey)` returning
`Optional<ConversionResult>`, used by both the pre-check and the duplicate-key fallback. That keeps
`ConversionServiceImpl`'s constructor down to exactly four dependencies — `currencyValidator`,
`clientRepository`, `rateService`, `conversionExecutor` — and keeps every mapper and the remaining
repositories behind the executor (`clientRepository`, `balanceRepository`, `conversionRepository`,
`conversionMapper`, `balanceMapper` — five). Checkstyle allows 7 constructor parameters, so neither class
is near the limit, but adding a fifth dependency to either is a signal to re-read this note first.

**R4 — Two integration test classes, because one of them must not be transactional.**
`BaseIntegrationTestSetUp` is `@Transactional`, so every test rolls back — which keeps the shared fixtures
intact and is right for `ConversionIntegrationTest`. A concurrency test cannot work that way: parallel
threads do not join the test's transaction and would not see its uncommitted rows.
`ConversionConcurrencyIntegrationTest` must therefore override with
`@Transactional(propagation = Propagation.NOT_SUPPORTED)` and **commit**, so it needs its own dedicated
fixture clients that no other test reads, and must reset their balances in an `@AfterEach`.

Add to `src/test/resources/db/testdata/V900__seed_integration_test_clients_and_balances.sql` — a test-only
fixture, safe to edit because Testcontainers starts a fresh database each run (`V1`–`V4` under `src/main`
stay frozen):
- a client for the conversion happy path holding two currencies,
- a single-currency client to drive `BALANCE_NOT_FOUND` (`CLIENT-TEST-002` holds nothing at all, so it
  currently drives the empty-balances case, not this one),
- one or more clients used **only** by the concurrency test, with balances chosen so two parallel
  conversions are individually affordable but not jointly.

Stub the edge in both classes with `@MockitoBean FrankfurterFeignClient` — **not** `RateService`. Mocking
the Feign client keeps the real `RateService`, the real `CurrencyValidator` and the real cache in the
assertion path, which is what `RateIntegrationTest` already does. Clear the `exchangeRate` cache in
`@BeforeEach` exactly as `RateIntegrationTest` does, or a rate cached by an earlier test will silently
bypass the mock.

**R5 — Visibility.** `ConversionServiceImpl` and `ConversionExecutor` are package-private classes in
`core.service.implementation`, reachable only through the public `ConversionService` interface — matching
`.claude/CLAUDE.md`'s layer rule. Their unit tests live in `zetta.foreignexchange.core.service`, so note
that `ConversionExecutorTest` needs to sit in the `...core.service.implementation` package to see the
class, or the class must be `public`; prefer moving the test, not widening the class.

**R6 — Money: scale and rounding, stated once.** `EntityConstant.MONEY_SCALE` is `4` and
`EntityConstant.RATE_SCALE` is `5`, and `core/mapper/RateMapper` already imports `EntityConstant` — so
reuse it for scale rather than redeclaring a number. Add only what is missing: `core/constant/MoneyConstant`
holding `public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;`. Apply it at exactly two
points:

```java
BigDecimal quoteAmount = baseAmount.multiply(exchangeRate.rate())
        .setScale(EntityConstant.MONEY_SCALE, MoneyConstant.ROUNDING_MODE);
```

and normalise the inbound base amount to the same scale before debiting, so the debited value, the
persisted `base_amount` and the value echoed in the response are one number.

**Do not re-round the rate.** `RateMapper.scaleRate` already sets it to `RATE_SCALE` `HALF_UP` before it
ever reaches this story; rounding it again is a no-op at best and a second rounding error at worst.
Seq 1's note still applies: Postgres silently rounds an over-scaled `NUMERIC` insert without complaining,
so the rounding must happen in Java. Compare money with `compareTo`, never `equals` — `100.00` and
`100.0000` are `equals`-unequal but the same amount. `HALF_UP` is the conventional commercial rounding and
is already what `RateMapper` uses; name the choice in the README.

**R7 — `BalanceEntity.debit` must not import `core`.** The layer rule is `core → persistence`, never the
reverse, so the entity cannot throw `InsufficientFundsException`. Split the responsibility: the **service**
performs the business check and throws `InsufficientFundsException` (it has the `clientId`, the currency
and both amounts needed for a useful message), while `debit` keeps a last-resort invariant guard that
throws a plain JDK `IllegalStateException` if ever called with more than the balance holds. Three layers of
defence, no cross-layer dependency: service check → entity guard → the `balances_amount_non_negative`
CHECK constraint `V1` already shipped. The entity guard is unreachable through the service, which is
exactly why it gets its own direct unit test.

**R8 — Idempotency, precisely.** The database is the authority: `V1`'s partial unique index
`conversions (client_id, idempotency_key) WHERE idempotency_key IS NOT NULL`. The flow is *look up first,
and be ready to lose the race*:
- pre-check `conversionRepository.findByClientClientIdAndIdempotencyKey(clientId, idempotencyKey)` — a hit
  short-circuits to the replay result: no rate call, no transaction, no debit;
- otherwise proceed; the executor uses `saveAndFlush` so a concurrent duplicate surfaces as
  `DataIntegrityViolationException` **inside** the call rather than at commit;
- catch it in `ConversionServiceImpl` (outside the now-rolled-back transaction), re-read by
  `(clientId, idempotencyKey)` and return the winner's result. The loser's debit rolled back with its
  transaction, so no double-debit is possible;
- a null or absent header means no key: unconstrained by the partial index, never a replay.

**Documented trade-off for the README:** a replay returns the *original* conversion's `transactionId`,
amounts, rate and timestamp, but the client's **current** balances — the `conversions` row does not
snapshot balances (Seq 1's deliberate no-ledger decision), so if other conversions ran in between, the
`balances` block of a replay reflects the present, not the moment of the original call. The conversion
facts, which are what idempotency actually protects, are identical.

**R9 — `ErrorCode` enum, added without breaking Seq 2 or Seq 3.** The advice currently holds six loose
`private static final String` constants; this story roughly doubles the number of codes, which is the point
at which an enum starts paying for itself. `core/model/ErrorCode` carries one constant per emitted code:
`CLIENT_NOT_FOUND`, `BALANCE_NOT_FOUND`, `INSUFFICIENT_FUNDS`, `SAME_CURRENCY`,
`UNSUPPORTED_CURRENCY_PAIR`, `EXCHANGE_RATE_UNAVAILABLE`, `VALIDATION_FAILED`, `MALFORMED_REQUEST`,
`INTERNAL_SERVER_ERROR`. `SAME_CURRENCY` is emitted by **both** endpoints, so its message must read
naturally for each — follow the existing pair-message style, e.g.
`"Currency pair %s/%s must contain two different currencies."` The **HTTP status stays in the advice** — `HttpStatus` is a `rest`-layer type and
importing it into `core` would invert the layer dependency. `ErrorResponse.code` stays a `String`; the
advice writes `errorCode.name()`.

Backward compatibility is mandatory and already covered by tests: `ForeignExchangeControllerAdviceTest`,
`BalanceIntegrationTest` and `RateIntegrationTest` assert the existing codes, messages, statuses and paths
for `CLIENT_NOT_FOUND`, `UNSUPPORTED_CURRENCY_PAIR` and `EXCHANGE_RATE_UNAVAILABLE` — all must come out
unchanged and those tests must pass without edits. If the reviewer would rather not touch Seq 3's handlers
in this story, dropping the enum and adding plain string constants in the same style is an acceptable
fallback, and Seq 9 becomes its owner.

**R10 — Controller signature.** Checkstyle allows 4 parameters per method, so headers plus body fit
directly:

```java
@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ConversionResponse> createConversion(
        @RequestHeader(CLIENT_ID_HEADER) @NotBlank String clientId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @Valid @RequestBody ConversionRequest conversionRequest) { ... }
```

`X-Client-Id` via header is the option the brief offers first; the README must say so (REQ-21).
Constraints on method parameters only fire when the class carries `@Validated`, and they then raise
`HandlerMethodValidationException`, **not** `MethodArgumentNotValidException` — the advice needs handlers
for both, plus `HttpMessageNotReadableException` for an unparseable body and `MissingRequestHeaderException`
for an absent `X-Client-Id`. `ConversionCommand` exists partly because the 4-parameter limit makes a
five-argument service method impossible — a constraint-driven decision, not gold plating.

**R11 — Status code: `201 Created`, for the replay as well.** A conversion is a created resource, so `201`
is the honest status. A replay returns `201` with the original body too, because idempotency means "the
same response as the original call" — returning `200` on a replay would make the response depend on
whether the caller had retried, which is the opposite of the point. No `Location` header: there is no
`GET /conversions/{id}` route to point at. State this in the README; it is the kind of small decision the
brief's *Communication* axis rewards.

**R12 — Response assembly.** `ConversionResult` is `Conversion` + `List<Balance> updatedBalances`. Read the
updated balances back inside the same transaction through the existing
`balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId)`, so the response shows **all**
currencies the client holds in the same shape and order as `GET /clients/{clientId}/balances` — the brief
says "the client's updated balances", and matching the other endpoint's shape means `BalanceResponse` and
its mapper are reused rather than duplicated. Let `ConversionResponseMapper` declare
`uses = ClientBalancesResponseMapper.class` so `Balance → BalanceResponse` has exactly one definition.

**R13 — `timestamp` comes from the database write.** `ConversionEntity.createdAt` is populated by
`@CreationTimestamp` and is only readable after the flush — another reason for `saveAndFlush`. Do not
generate a separate `OffsetDateTime.now()`: the returned `timestamp` and the persisted `created_at` (the
column `GET /conversions?date=` will filter on in Seq 8) must be the same instant. `transactionId` is
`UUID.randomUUID()` assigned in the service, per Seq 1's decision that it is application-assigned and not
the primary key. Note `ConversionEntity` is `@Immutable` — inserts are fine, updates are silently ignored,
so never try to mutate a persisted conversion.

**R14 — Validation split, reusing what Seq 3 built.** The request record carries only *syntactic*
constraints: `sourceAmount` → `@NotNull`, `@Positive`, `@Digits(integer = 15, fraction = 4)` (matching
`NUMERIC(19,4)` — the brief's "sane bounds"); currencies → `@NotBlank` and `@Pattern(regexp = "^[A-Z]{3}$")`.
**Do not write a new ISO-4217 validator** — `core/validator/CurrencyValidator` already does the regex *and*
the `Currency.getInstance` membership check, and after D2 it also owns the identical-pair rule, so one
`validateCurrencyPair` call in `ConversionServiceImpl` covers all three and an unknown code surfaces as
`422 UNSUPPORTED_CURRENCY_PAIR` for free. Hoist the regex and the header names into constants —
checkstyle rejects duplicate string literals.

**R15 — D1's wire aliasing: how, and the two traps.** Annotate the **record component**; Java propagates
the annotation to the field, the accessor and the canonical-constructor parameter, so one annotation
covers both serialisation and deserialisation:

```java
public record ConversionRequest(
        @JsonProperty("sourceCurrency")
        @NotBlank @Pattern(regexp = CURRENCY_CODE_PATTERN)
        @Schema(description = "Currency being converted from.", example = "USD")
        String baseCurrency,
        ...) { }
```

- **Trap 1 — do not use `@JsonAlias`.** It only affects reading; the response would still serialise as
  `baseCurrency` and the brief's field names would never appear on the way out. `@JsonProperty` renames in
  both directions.
- **Trap 2 — the OpenAPI schema follows Jackson.** SpringDoc reads `@JsonProperty`, so Swagger UI shows
  `sourceCurrency`/`targetAmount` automatically; keep `@Schema(description = ..., example = ...)` for the
  prose but **do not** also set `@Schema(name = ...)`, or the two renaming mechanisms can disagree and the
  documented schema stops matching the wire. One renaming mechanism only.
- Assert the aliasing directly (Slice 1 serialisation tests) — it is the kind of thing a later refactor
  silently reverts, and a `ConversionResponse` that serialises as `baseAmount` breaks the brief's contract
  without breaking a single mapper test.
- `rest/mapper/ConversionResponseMapper` therefore needs **no** renaming `@Mapping` for these fields: the
  Java names match on both sides of the mapper, and the rename happens only at the JSON edge.

---

## Dependencies

- **Depends On:**
  - `Seq 1` — **Done.** Everything this story writes to already exists: the `clients`/`balances`/
    `conversions` tables, the `balances.version` column, the `balances_amount_non_negative` CHECK, the
    partial unique index on `(client_id, idempotency_key)`, the unique `transaction_id`, and the seeded
    demo clients. Extended since by `V3` (conversion columns renamed to `base`/`quote`) and `V4`
    (`conversions.rate` narrowed to `NUMERIC(19,5)`). **No new production migration is required.**
  - `Seq 2` — **Done.** Supplies `Balance`, `BalanceMapper`, `BalanceResponse`, `ClientNotFoundException`,
    the `ErrorResponse` shape, the advice, and the `GET /clients/{clientId}/balances` endpoint the
    integration tests read back through.
  - `Seq 3` — **Done** (merged in PR #1, commit `14f324c`; verified by reading the code). Supplies the
    entire rate path this story consumes: `RateService.getExchangeRate(baseCurrency, quoteCurrency)`
    returning `ExchangeRate`, Caffeine TTL caching via `CacheConfiguration.EXCHANGE_RATE`, the Frankfurter
    Feign client with connect/read timeouts, `CurrencyValidator`, and the `UNSUPPORTED_CURRENCY_PAIR` /
    `EXCHANGE_RATE_UNAVAILABLE` exceptions and handlers. **Seq 4 is unblocked and defines no rate contract
    of its own.** Seq 4 makes exactly one user-approved change inside Seq 3's code — D2's identical-pair
    rule in `CurrencyValidator`, which narrows `GET /rates?from=X&to=X` from `200 rate=1` to
    `422 SAME_CURRENCY`. REQ-1/REQ-12/REQ-13 stay `Done` in `BACKLOG.md`; this refines one case, it does
    not reopen the requirement. Seq 13's README must describe the endpoint's post-D2 behaviour, not the
    behaviour Seq 3 shipped.

- **Blocks:**
  - `Seq 8` — `GET /conversions` history needs rows in `conversions` and the `Conversion` domain record
  - `Seq 9` — generalises the `ErrorCode` enum and the validation handlers introduced here
  - `Seq 10` — documents this endpoint in the OpenAPI spec
  - `Seq 12` — audits the REQ-17/REQ-18 scenarios this story implements
  - `Seq 13` — the README's concurrency, idempotency, rounding and status-code trade-offs come from R1,
    R6, R8 and R11

---

## Definition of Ready

- [x] Acceptance criteria defined and mapped to REQ-2, REQ-6, REQ-7, REQ-8, REQ-9, REQ-10, REQ-11
- [x] Existing patterns referenced with file paths and snippets, verified against commit `14f324c`
- [x] Estimate set (21 — a deliberate consolidation; see the header rationale)
- [x] Files to create/modify enumerated exactly
- [x] **D1 decided (user, 2026-09-20)** — `base`/`quote` in Java everywhere including the REST records;
      `@JsonProperty` renames them to `source`/`target` on the wire and in Swagger (R15)
- [x] **D2 decided (user, 2026-09-20)** — identical currencies rejected on both `POST /conversions` and
      `GET /rates`, via one shared `SameCurrencyException` raised inside `CurrencyValidator`; the
      resulting change to `GET /rates`' existing behaviour is explicitly approved
- [x] **@architect sign-off on R1** (pessimistic lock in place of Seq 1's optimistic default) before Slice 5
      — **APPROVED 2026-09-20**, with conditions: default `@Transactional` isolation on `ConversionExecutor`
      (no explicit isolation level); the locking finder must be the FIRST read of each balance row in the
      transaction (no prior unlocked read — first-level cache would return a stale instance) and the R12
      balance read-back stays strictly after debit/credit; mutate the managed entity returned by the locking
      finder (never a `toBuilder()` copy); executor transaction stays DB-only; plain `PESSIMISTIC_WRITE`
      (no `PESSIMISTIC_FORCE_INCREMENT`); verify the derived finder name resolves at startup, else keep
      `@Lock` with an explicit `@Query`; concurrency test asserts with `comparesEqualTo` and Hikari pool >
      thread count; if the post-`DataIntegrityViolationException` re-read returns empty, fail with a
      distinct error rather than retrying

## Definition of Done

- [ ] Every acceptance criterion above is met and covered by a test
- [ ] Coverage ≥ 80% (`.claude/CLAUDE.md`)
- [ ] Test names follow `methodName_condition_expectedOutcome`
- [ ] Instancio is used for unit-test data wherever the field values are not significant to the assertion
      (follow `BalanceServiceTest`); money under assertion stays as explicit `BigDecimal` string literals
- [ ] `verify(mock)` with no redundant `times(1)`
- [ ] Seq 2's and Seq 3's behaviour is unchanged **except for D2**. These must still pass without
      modification: `BalanceIntegrationTest`, `ClientControllerTest`, `BalanceMapperTest`, `RateMapperTest`,
      `BalanceServiceTest`, `ClientBalancesResponseMapperTest`, `ExchangeRateResponseMapperTest`,
      `CacheConfigurationTest`, `RateCacheExpiryTest`, `FrankfurterErrorDecoderTest`
- [ ] Exactly four existing test classes may be edited, and only as listed under "Files to Create /
      Modify": `ForeignExchangeControllerAdviceTest` and `CurrencyValidatorTest` (additive only), and
      `RateServiceTest`, `RateControllerTest`, `RateIntegrationTest` (one identical-pair method each,
      rewritten for D2). Every other method in those classes is untouched. An edit anywhere else in the
      Seq 2/Seq 3 test suite means something went wrong — stop and re-read the scope.
- [ ] `GET /rates` still returns `200` for every non-identical pair, `422 UNSUPPORTED_CURRENCY_PAIR` for a
      bad code and `502 EXCHANGE_RATE_UNAVAILABLE` for a dead provider — D2 narrows one case, it does not
      reshape the endpoint
- [ ] The concurrency, idempotency, rounding, `201`-on-replay, D1 aliasing and D2 same-currency decisions
      are written down for Seq 13's README

---

## References

- `.claude/Java-Assignment.pdf` — "What you will build" (`POST /conversions` response fields);
  "Per-client account balances" (atomic debit/credit, insufficient funds → 422, unknown client/currency →
  404, concurrency strategy); "Technical requirements" (`BigDecimal` scale and rounding, idempotency key,
  `@ControllerAdvice` with distinct codes, tests for idempotency replay / insufficient funds / happy path)
- `backlog/BACKLOG.md` — closes REQ-2, REQ-6, REQ-7, REQ-8, REQ-9, REQ-10, REQ-11; contributes to REQ-14,
  REQ-17, REQ-18
- `backlog/PRIORITY.md` — Seq 4, the "Seq 5, 6, 7 consolidated into Seq 4 — 2026-09-20" note (user
  decision, verified Seq 3 state, the `base`/`quote` and `RATE_SCALE = 5` conventions), and the two Seq 3
  notes recording the Feign and provider-contract decisions
- `backlog/stories/done/1-database-schema.story.md` — the column contract, the partial unique index, the
  `@Version` column, and the "round in Java, not in the column" note
- `backlog/stories/done/2-client-balances-endpoint.story.md` — the service/mapper/controller/advice patterns
- `backlog/stories/done/3-rates-endpoint.story.md` — the rate contract, cache and error semantics consumed here
- `.claude/CLAUDE.md` — Database (no transaction across an external call; idempotency keys; locking
  choice), Layer Placement Rule, Method naming, Spec-Aligned Variable Names, Mockito verify style,
  Backward Compatibility
- `.claude/agents/architect.md` — Pattern 5 (locking strategy), the sign-off named in DOR

---

## Test Results

**Iterations:** 1/3
**Reviewer verdict:** ✅ Approved
**@architect R1 sign-off:** ✅ Approved 2026-09-20 (pessimistic `PESSIMISTIC_WRITE` row lock, `@Version`
retained as backstop; all implementation conditions honoured — see Definition of Ready)

**Tests (full suite 124/124 ✅, `mvn checkstyle:check` 0 violations ✅):**
- BalanceEntityTest: 4/4 ✅
- ConversionMapperTest: 2/2 ✅
- ConversionResponseMapperTest: 7/7 ✅ (incl. 2 D1 wire-aliasing serialisation tests, both directions)
- ConversionServiceTest: 9/9 ✅
- ConversionExecutorTest: 6/6 ✅
- ConversionControllerTest: 2/2 ✅
- ForeignExchangeControllerAdviceTest: 13/13 ✅ (3 existing untouched + 10 new)
- CurrencyValidatorTest: 11/11 ✅ (2 additive for D2)
- ConversionIntegrationTest: 16/16 ✅
- ConversionConcurrencyIntegrationTest: 3/3 ✅ (real parallel requests, committing, non-transactional)
- RateServiceTest / RateControllerTest / RateIntegrationTest: ✅ (one identical-pair method each rewritten for D2)
- All ten protected Seq 2/Seq 3 test classes: unmodified, green ✅

**Assignment coverage:**
- ✅ Happy-path debit/credit: 201 with transactionId/amounts/rate/timestamp/updated balances; exactly one
  `conversions` row; `targetAmount = sourceAmount × rate` at scale 4 HALF_UP; rate fetched outside the tx
- ✅ Insufficient funds → 422 `INSUFFICIENT_FUNDS`, no row persisted, neither balance changed
- ✅ Unknown client → 404 `CLIENT_NOT_FOUND`, provider never called
- ✅ Missing balance row (source AND target cases) → 404 `BALANCE_NOT_FOUND`
- ✅ D2: identical pair → 422 `SAME_CURRENCY` on BOTH `POST /conversions` and `GET /rates`, provider
  never called; `usd`/`usd` still reports `UNSUPPORTED_CURRENCY_PAIR`
- ✅ Concurrency: exceeding-balance race → exactly one 201 + one 422, one row, one debit; affordable race
  → no lost update; balance never negative
- ✅ Idempotency: replay returns original result (201, no second row/debit); concurrent duplicate key
  resolves to winner's result; different client same key not a replay; absent key never a replay
- ✅ D1: wire format `source*`/`target*` via `@JsonProperty`, Java components `base*`/`quote*`

**Edge cases covered:**
- ✅ Zero / negative / absent / over-scaled `sourceAmount` → 400 structured `ErrorResponse`
- ✅ Blank AND absent `X-Client-Id` header → 400
- ✅ Malformed JSON body → 400; unhandled exception → 500, never a stack trace
- ✅ Dead provider → 502 `EXCHANGE_RATE_UNAVAILABLE` (Seq 3 handler, unchanged)
- ✅ `BalanceEntity.debit` invariant guard (`IllegalStateException`) unit-tested directly (R7)
- ✅ Empty re-read after `DataIntegrityViolationException` fails distinctly, never retries

**Implementation deviations from story prose (all reviewed and accepted):**
1. `ConversionServiceTest` lives in `core.service.implementation` (Impl is package-private — R5 reasoning)
2. `buildErrorResponse` takes 3 params + `Map<ErrorCode, HttpStatus>` — repo checkstyle caps method params at 3
3. Extra `ConstraintViolationException` handler — Spring Boot 4.1.1 raises it (not
   `HandlerMethodValidationException`) for a blank `@RequestHeader` under `@Validated`; both handlers kept
4. Extra `MissingServletRequestParameterException` handler — keeps `GET /rates` missing-param → 400 from
   falling into the new catch-all 500
