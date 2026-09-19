# 3 — Expose `GET /rates` backed by the Frankfurter provider, with timeouts, graceful failure and a TTL cache

| Field | Value |
|---|---|
| Story Points | 5 |
| Priority | Critical |
| Status | Done |

**Story points rationale:** this is the first vertical slice that leaves the database behind and crosses
the network. It spans three layers (`rest`, `core`, `common`), stands up four things the service has never
had — an outbound HTTP client, a `@ConfigurationProperties` record, a cache manager, and a provider-failure
error path — and it makes two dependency decisions (HTTP client, cache implementation) that every later
story inherits. That breadth is what lifts it above Seq 2's 3. It stays below an 8 because there is no
persistence, no money mutation, no transaction and no concurrency concern: one read-only GET, one
downstream call, one mapping chain. The single largest source of effort is not the happy path — it is
proving the TTL actually short-circuits the provider without the test suite reaching the real internet.

---

## User Story

**As a** foreign-exchange service
**I want** to answer `GET /rates?from=USD&to=EUR` with the current exchange rate, fetched from Frankfurter
behind a bounded timeout and served from a short-lived TTL cache
**So that** REQ-1, REQ-12 and REQ-13 are satisfied, and Seq 4 (`POST /conversions`) has a rate source it can
call before it opens a balance-mutating transaction.

---

## Context

**Current behavior before this story:** nothing in the service talks to the network. Three files exist as
empty shells — `common/integrations/frankfurter/FrankfurterFeignClient.java` (`public interface
FrankfurterFeignClient {}`), `core/service/RateService.java` (an empty interface), and
`core/service/implementation/RateServiceImpl.java` (a `@Service` with no methods). `RateController` is an
annotated class with an empty body, so `GET /rates` currently 404s. There was no `common/config/` package,
no `common/properties/` package, no cache of any kind, and no HTTP-client or caching dependency in
`pom.xml` — see "Spike Already Landed" at the end of this story for what has since been wired up and
proven. `application.yaml` does hold a `frankfurter.client.url`, but it is wrong — see below.

**Required behavior:** `GET /rates?from=USD&to=EUR` returns `200` with the source currency, the target
currency and the rate as a `BigDecimal`. When the provider is slow, unreachable or returns `5xx`, the
caller gets a structured `502 RATE_UNAVAILABLE` problem body — never a stack trace, and never a request
that hangs indefinitely. When the provider rejects a currency code, the caller gets
`422 UNSUPPORTED_CURRENCY_PAIR`, which is a distinct outcome from "the provider is down". A second
identical request inside the TTL window is answered from cache without touching the provider at all.

**The provider contract is the v2 single-pair endpoint.** The configured URL was
`https://api.frankfurter.dev/v2/rates/` (plural, trailing slash), which returns **404**. The correct base
URL is `https://api.frankfurter.dev/v2` and the correct path is `/rate/{base}/{quote}` — **`rate`,
singular**. This endpoint returns one pair per call as a flat object, which is a better fit for this
service than v1's `/latest?base=&symbols=` map payload: one call, one rate, no keyed lookup, no
missing-entry case.

```
GET https://api.frankfurter.dev/v2/rate/USD/EUR
200 {"date":"2026-09-20","base":"USD","quote":"EUR","rate":0.86984}
```

Every behaviour below was probed live against the provider, and the error paths in this story are shaped
around them rather than around guesswork:

| Request | Provider response |
|---|---|
| `/v2/rate/USD/EUR` | `200` `{"date":"2026-09-20","base":"USD","quote":"EUR","rate":0.86984}` |
| `/v2/rate/eur/usd` (lower case) | `200` — codes are case-insensitive, echoed back upper case |
| `/v2/rate/USD/XXX` (unknown code) | **`422`** `{"status":422,"message":"invalid currency: XXX"}` |
| `/v2/rate/US/EUR` (malformed code) | **`422`** `{"status":422,"message":"invalid currency: US"}` |
| `/v2/rate/USD/USD` (identical pair) | **`200`** with `"rate":1.0` — **not** an error |
| `/v2/rate/USD/EUR?date=2026-09-18` | `200` for that date — available for a later story, unused here |
| `/v2/rate/USD` or `/v2/rate/USD/EUR/GBP` | `404` `{"status":404,"message":"not found"}` — wrong route shape |
| `/v1/rate/...`, `/v2/rates/...` (plural), `/v2/latest?...` | `404` — these do not exist |

**The two consequences that shape the error design:** `422` is the *only* status that means "the caller
named a currency I cannot quote", and a `404` means our own URL was malformed — our bug, never the
caller's — so it must surface as a provider failure, not as `UNSUPPORTED_CURRENCY_PAIR`. And because
`USD -> USD` answers `200 rate=1.0`, an identical pair is a **success**, not an error.

**What this unlocks:** Seq 4 — `POST /conversions` calls `RateService.getRate(...)` to obtain the rate
*before* opening its transaction, which is the concrete reason the `CLAUDE.md` database rule "do not open
transactions before external calls" is satisfiable at all. It also gives Seq 9 its second and third
`@RestControllerAdvice` handlers to build on, and Seq 13 the TTL/invalidation decision to write up.

**Grading axis:** production sense above all (timeouts, caching, graceful degradation, config externalised
rather than hardcoded), plus correctness (REQ-1's contract) and testing discipline (the TTL has to be
*proved*, not asserted by inspection, and the suite must not depend on internet access).

---

## Scope

### In Scope
- [ ] `GET /rates?from=&to=` end to end, returning `200` with `{sourceCurrency, targetCurrency, rate}`
- [ ] `Rate` domain record in `core/model`, with the rate held as a `BigDecimal` at an explicit scale
- [ ] `RateService.getRate(String sourceCurrency, String targetCurrency)` plus its implementation
- [ ] Frankfurter client behind an interface in `common/integrations/frankfurter`, plus the response record
      shaped around the real `{amount, base, date, rates:{CODE:value}}` payload
- [ ] Correcting `frankfurter.client.url` to `https://api.frankfurter.dev/v1`
- [ ] `FrankfurterClientProperties` `@ConfigurationProperties` record carrying url, connect timeout, read timeout,
  cache TTL and cache max size
- [ ] Connect and read timeouts wired onto the HTTP client from those properties
- [ ] TTL cache at the `RateService` boundary, keyed on the currency pair
- [ ] `ExchangeRateUnavailableException` → `502 RATE_UNAVAILABLE` for timeout / connect failure / provider `5xx`
- [ ] `UnsupportedCurrencyPairException` → `422 UNSUPPORTED_CURRENCY_PAIR` for provider `4xx` on the pair
- [ ] SpringDoc annotations on the endpoint at the same density as `ClientController`
- [ ] Unit, controller and full-stack tests — including a test that proves the second call inside the TTL
      does not reach the provider

### Out of Scope
- `POST /conversions`, any balance mutation, and the conversion record — Seq 4 (REQ-2, REQ-6, REQ-11)
- The full validation matrix (ISO-4217 `@Pattern`, positive amounts, sane bounds, non-blank client id) and
  the complete exception-to-status table — Seq 9 (REQ-14, REQ-15). This story adds exactly the two handlers
  its own two exceptions need and touches nothing else in `ForeignExchangeControllerAdvice`
- `GET /conversions` history — Seq 8
- Persisting rates. Rates are read-through only; no table, no entity, no repository. The `rate` column on
  `conversions` is written by Seq 4 from the value this story returns
- Retries, circuit breakers, bulkheads or Resilience4j. REQ-12 asks for a timeout and a graceful failure,
  not a resilience framework — adding one would be the pattern-stacking the brief calls a non-goal
- A stale-while-revalidate / fallback-to-last-known-rate layer. Serving a stale rate for a money movement
  is a business decision nobody asked for; failing loudly with `502` is the honest behaviour
- Auth on the endpoint — an explicit assignment non-goal

---

## Acceptance Criteria

- [ ] `GET /rates?from=USD&to=EUR` returns `200` with a JSON body containing `baseCurrency`,
      `quoteCurrency` and `rate`
- [ ] `rate` is serialised from a `BigDecimal` at scale `5` (`EntityConstant.RATE_SCALE`) with
      `RoundingMode.HALF_UP` — matching the `NUMERIC(19, 5)` column Seq 4 writes it into; no `double` or
      `float` appears anywhere on the path, including in the provider response record
- [ ] The endpoint is mapped at exactly `/rates` with query parameters named `from` and `to` — the wire
  names the brief fixes — bound to method parameters named `baseCurrency` and `quoteCurrency`
- [ ] The provider base URL, connect timeout, read timeout, cache TTL and cache maximum size all come from
      a `@ConfigurationProperties` record; none of them is a `@Value`, a string literal or a magic number
- [ ] `frankfurter.client.url` in `application.yaml` is `https://api.frankfurter.dev/v2`, and the client
      path is `/rate/{base}/{quote}` — singular. The previously configured `/v2/rates/` returns `404`
- [ ] When the provider does not answer within the configured read timeout, the response is `502` with body
      `code: RATE_UNAVAILABLE`, a message naming the currency pair, the status and the request path, sent as
      `application/problem+json` — and the request completes in timeout-bounded time rather than hanging
- [ ] When the provider returns `5xx`, the response is the same `502 RATE_UNAVAILABLE` body
- [ ] The provider's own stack trace, response body and URL never appear in the HTTP response; the cause is
      chained onto the domain exception so it reaches the log instead
- [ ] `GET /rates?from=USD&to=XXX` returns `422` with body `code: UNSUPPORTED_CURRENCY_PAIR` — not `502`,
      because an unknown currency code is the caller's problem, not the provider's
- [ ] `GET /rates?from=USD&to=USD` returns **`200` with `rate` = `1.0`** — the v2 provider treats an
      identical pair as a valid quote, so this is a success path, not an error path
- [ ] A provider `404` surfaces as `502 RATE_UNAVAILABLE`, never as `422` — on this API a `404` means the
      request URL was malformed, which is our defect and must not be reported as the caller's bad pair
- [ ] A second `GET /rates` for the same pair within the TTL returns the same `200` body without a second
      provider call — asserted with `verify(frankfurterFeignClient).fetchLatestRates(...)` on a mocked client
- [ ] The cache is bounded by a configured maximum entry count, so a caller cannot grow it without limit by
      varying the `from`/`to` query parameters
- [ ] The whole test suite passes with no outbound network access — no test calls the real Frankfurter API
- [ ] `FrankfurterRatePairResponse` never leaves `common` — `RateService` returns the `Rate` domain record
- [ ] The endpoint appears in the generated OpenAPI spec under a `Rates` tag with the `200`, `422`, `502` and
      `500` bodies documented

---

## Technical Notes

### Layers Affected
- [x] `core` — `Rate` domain record, `RateService` + impl, `ExchangeRateUnavailableException`,
      `UnsupportedCurrencyPairException`
- [ ] `persistence` — untouched. `EntityConstant.RATE_SCALE` is read, nothing is written
- [x] `rest` — `RateController`, `ExchangeRateResponse`, `ExchangeRateResponseMapper`, two new advice handlers
- [x] `common` — `FrankfurterFeignClient`, `FrankfurterConfiguration`, `FrankfurterRatePairResponse`,
  the two provider-neutral exceptions, `FrankfurterClientProperties` and the cache configuration. Creates
      `common/config/` and `common/properties/`, neither of which exists yet

### Files to Create / Modify

**Create**
- `src/main/java/zetta/foreignexchange/common/integrations/frankfurter/configuration/FrankfurterProperties.java`
- `cache`
- `integrations`
- `integrations`
- `integrations`
- `integrations`
- `src/main/java/zetta/foreignexchange/core/model/Rate.java`
- `src/main/java/zetta/foreignexchange/core/exception/RateUnavailableException.java`
- `src/main/java/zetta/foreignexchange/core/exception/UnsupportedCurrencyPairException.java`
- `src/main/java/zetta/foreignexchange/rest/model/RateResponse.java`
- `src/main/java/zetta/foreignexchange/core/mapper/RateMapper.java`
- `src/main/java/zetta/foreignexchange/rest/mapper/RateResponseMapper.java`
- `src/test/java/zetta/foreignexchange/core/service/RateServiceTest.java`
- `src/test/java/zetta/foreignexchange/rest/controller/RateControllerTest.java`
- `src/test/java/zetta/foreignexchange/core/mapper/RateMapperTest.java`
- `src/test/java/zetta/foreignexchange/rest/mapper/RateResponseMapperTest.java`
- `src/test/java/zetta/foreignexchange/integration/RateIntegrationTest.java`

**Modify**
- `src/main/java/zetta/foreignexchange/core/service/RateService.java` — add `getRate`
- `src/main/java/zetta/foreignexchange/core/service/implementation/RateServiceImpl.java` — implement it
- `src/main/java/zetta/foreignexchange/rest/controller/RateController.java` — add the mapping
- `src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java` — add
  exactly two handlers; leave the existing `ClientNotFoundException` handler and the `TODO` block untouched
- `integrations` — fill in
  the empty placeholder with the real `@FeignClient` contract; the name stays as it is
- `src/main/java/zetta/foreignexchange/ForeignExchangeApplication.java` — add `@EnableFeignClients` and
  `@ConfigurationPropertiesScan`
- `src/main/resources/application.yaml` — correct the URL, add timeouts and cache settings
- `pom.xml` — add the Spring Cloud BOM, `spring-cloud-starter-openfeign`, and the caching dependencies

**Nothing is deleted.** The `FrankfurterFeignClient` placeholder keeps its name — it really is a Feign
client now.

### Existing Pattern Reference

The service implementation is a `@Service` with `@RequiredArgsConstructor`, exposed only through a public
interface — `src/main/java/zetta/foreignexchange/core/service/implementation/BalanceServiceImpl.java`:

```java
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final ClientRepository clientRepository;
    private final BalanceRepository balanceRepository;
    private final BalanceMapper balanceMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Balance> getClientBalances(String clientId) {
        validateClientExists(clientId);
        List<BalanceEntity> balanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
        return balanceMapper.mapToBalances(balanceEntities);
    }
```

The controller holds no logic — it delegates, maps, and returns
(`src/main/java/zetta/foreignexchange/rest/controller/ClientController.java`):

```java
@GetMapping(value = "/{clientId}/balances", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ClientBalancesResponse> getClientBalances(@PathVariable String clientId) {
    List<Balance> balances = balanceService.getClientBalances(clientId);
    return ResponseEntity.ok(clientBalancesResponseMapper.mapToClientBalancesResponse(clientId, balances));
}
```

Error bodies are built from constants and returned as `application/problem+json`
(`src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java`) — the two
new handlers must copy this shape exactly, including the `log.error` call and the content type:

```java
@ExceptionHandler(ClientNotFoundException.class)
public ResponseEntity<ErrorResponse> handleClientNotFoundException(
        ClientNotFoundException exception, HttpServletRequest request) {

    log.error("ClientNotFoundException thrown", exception);

    ErrorResponse errorResponse = ErrorResponse.builder()
            .code(CLIENT_NOT_FOUND_CODE)
            .message(format(CLIENT_NOT_FOUND_MESSAGE, exception.getClientId()))
            .status(HttpStatus.NOT_FOUND.value())
            .path(request.getRequestURI())
            .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(errorResponse);
}
```

The domain exception carries the offending identifiers and nothing else
(`src/main/java/zetta/foreignexchange/core/exception/ClientNotFoundException.java`):

```java
@AllArgsConstructor
@Getter
public class ClientNotFoundException extends RuntimeException {

    private final String clientId;
}
```

Service unit tests mock the collaborators and `@Spy` the real MapStruct mapper
(`src/test/java/zetta/foreignexchange/core/service/BalanceServiceTest.java`):

```java
@Spy
private BalanceMapper balanceMapper = Mappers.getMapper(BalanceMapper.class);

@InjectMocks
private BalanceServiceImpl balanceService;
```

Full-stack tests extend the shared base and assert through MockMvc
(`src/test/java/zetta/foreignexchange/integration/BalanceIntegrationTest.java`):

```java
result.andExpect(status().isNotFound())
        .andExpectAll(
                jsonPath("$.code").value(CLIENT_NOT_FOUND_CODE),
                jsonPath("$.message").value(format(CLIENT_NOT_FOUND_MESSAGE, nonExistingClientId)),
                jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()),
                jsonPath("$.path").value(buildClientBalancesPath(nonExistingClientId)));
```

Money precision constants already exist and must be reused rather than redeclared
(`src/main/java/zetta/foreignexchange/persistence/constant/EntityConstant.java`):

```java
public static final int MONEY_SCALE = 4;
public static final int RATE_SCALE = 5;
```

### TDD Steps

- **Red 1 — the domain call.** Write `RateServiceTest.getRate_withKnownCurrencyPair_returnRate`: mock
  `FrankfurterFeignClient.fetchLatestRates("USD", "EUR")` to return a `FrankfurterRatePairResponse` whose
  `rate` field is `0.86984`, assert the returned `Rate` carries `USD`, `EUR` and `0.86984000`.
  It fails to compile — `RateService.getRate` and `Rate` do not exist.
- **Green 1.** Add `Rate`, the `getRate` signature, the `@FeignClient` interface and the response record;
  implement `getRate` as a single call plus `setScale(RATE_SCALE, HALF_UP)`.
- **Red 2 — the provider is down.** `getRate_whenProviderTimesOut_throwRateUnavailableException` — stub the
  client to throw `FrankfurterGeneralException`, expect `ExchangeRateUnavailableException` carrying both currency
  codes and the original exception as its cause. The `5xx` sibling
  (`getRate_whenProviderReturnsServerError_throwRateUnavailableException`) stubs that same neutral exception —
  the `ErrorDecoder` has already collapsed status into it, which is precisely why `core` needs no Feign
  import.
- **Green 2.** Add `ExchangeRateUnavailableException` and the `try`/`catch` that translates.
- **Red 3 — the pair is not quotable.**
  `getExchangeRate_whenProviderRejectsCurrencyPair_throwUnsupportedCurrencyPairException` (stub
  `FrankfurterPairNotQuotableException`) and
  `getRate_whenProviderReturnsNullExchangeRate_throwUnsupportedCurrencyPairException` (a `200` whose `rate` field is
  null — a `null` here would otherwise become an NPE and a `500`).
- **Green 3.** Add `UnsupportedCurrencyPairException`, catch the two neutral exceptions separately, and
  null-check the `rate` field. The `422`-vs-everything-else split itself lives in
  `FrankfurterConfiguration.FrankfurterErrorDecoder` and is already covered by the spike's assertions.
- **Red 4 — the wire contract.** `RateControllerTest.getRate_withKnownCurrencyPair_returnRateResponse`,
  following `ClientControllerTest`: `@Mock RateService`, `@Spy RateResponseMapper`,
  `@InjectMocks RateController`, assert `200` and the mapped body. Plus `ExchangeRateResponseMapperTest` on the
  mapper itself.
- **Green 4.** Add `ExchangeRateResponse`, `ExchangeRateResponseMapper`, and the `@GetMapping` on `RateController`.
- **Red 5 — the error bodies through the real stack.** `RateIntegrationTest` with
  `@MockitoBean FrankfurterFeignClient`: one test per error path asserting `502 RATE_UNAVAILABLE` and
  `422 UNSUPPORTED_CURRENCY_PAIR` with the full four-field body and the request path.
- **Green 5.** Add the two handlers to `ForeignExchangeControllerAdvice`.
- **Red 6 — the TTL, and this is the REQ-13 proof.**
  `getRate_withSecondCallInsideTtl_serveFromCacheWithoutCallingProvider`: hit `/rates?from=USD&to=EUR`
  twice, assert both responses are `200` with the same body, then
  `verify(frankfurterFeignClient).fetchLatestRates(SOURCE_CURRENCY, TARGET_CURRENCY)` — the plain
  single-invocation form, no `times(1)`. It fails with "Wanted 1 time but was 2 times" until the cache
  exists.
- **Green 6.** Add the caching dependencies, `CacheConfiguration`, and `@Cacheable` on `getRate`.
- **Refactor.** Pull the error codes, messages and the cache name into `private static final` constants (the
  advice already does this for `CLIENT_NOT_FOUND_CODE`); collapse the two `catch` blocks into the smallest
  readable form; confirm the timeout and TTL values are read from `FrankfurterClientProperties` and appear nowhere
  as literals; re-check the all-or-one parameter wrapping on every signature that crossed 125 characters.

### Implementation Notes

**Decision 1 — HTTP client: Spring Cloud OpenFeign. User-directed, and empirically proven on this
classpath.**

This is the user's explicit decision, for consistency with the `@FeignClient` style used across their other
services, and it is **settled** — not a trade-off for the coder to re-open. The one real risk was Boot 4.1
compatibility, and that risk has been **retired by a working spike**, not by reasoning:

| Fact | Verified how |
|---|---|
| Latest Spring Cloud GA train `2025.1.3` declares `spring-boot.version` **4.0.8** | read from the published BOM |
| This project runs Boot **4.1.1** — one minor ahead of what the train was built against | `pom.xml` |
| Only `2026.0.0-SNAPSHOT` targets Boot 4.1, and a snapshot is not acceptable in a take-home | Spring snapshot repo listing |
| `spring-cloud-starter-openfeign:5.0.3` + `feign-core:13.6.1` resolve and compile under the 4.1.1 parent | `mvn dependency:tree` |
| A real `@FeignClient` call against the live provider returns `200` and deserialises into `BigDecimal` | spike test, passed |
| Provider `404`, provider `422`, and a read timeout each translate to the intended exception | spike test, passed |
| The 16 existing unit tests and `mvn checkstyle:check` stay green with the train added | full run |

**The 4.0.8-vs-4.1.1 skew is therefore a known, tested-green risk rather than an unknown.** Pin
`spring-cloud.version` explicitly so it cannot drift, and record the skew in the README (Seq 13).

```xml
<spring-cloud.version>2025.1.3</spring-cloud.version>
```

Imported as the `spring-cloud-dependencies` BOM in `<dependencyManagement>`, with
`org.springframework.cloud:spring-cloud-starter-openfeign` declared version-less.
**`@EnableFeignClients` on `ForeignExchangeApplication` is required** — the starter alone does not scan for
clients, and its absence fails at injection time, not at startup.

The client interface, in the user's established style:

```java
@FeignClient(name = "frankfurter", url = "${frankfurter.client.url}", configuration = FrankfurterConfiguration.class)
public interface FrankfurterFeignClient {

    @GetMapping(value = "/rate/{base}/{quote}", produces = MediaType.APPLICATION_JSON_VALUE)
    FrankfurterRatePairResponse fetchLatestRates(@PathVariable String base, @PathVariable String quote);
}
```

The v2 response is flat, so the provider record is four fields and needs no map:

```java
public record FrankfurterRatePairResponse(LocalDate date, String base, String quote, BigDecimal rate) {
}
```

**`FrankfurterConfiguration` must NOT be annotated `@Configuration`.** Feign registers a `configuration`
class into that client's own child context; annotating it would *also* let component scanning pick it up
into the main context, making its `Request.Options`, `Retryer` and `ErrorDecoder` the silent defaults for
every Feign client added later. Unannotated, with `@Bean` methods, is correct — and is the form the spike
proved working. Four beans:

1. **`Request.Options`** built from `FrankfurterProperties.connectTimeout()` / `readTimeout()` — this is how
   REQ-12's timeout is met, and why no timeout is a literal anywhere.
2. **`Retryer.NEVER_RETRY`** — the caller is waiting, and Seq 4 needs one predictable timeout rather than a
   multiplied one. Feign's default retries, so this must be explicit.
3. **`ErrorDecoder`** mapping provider **`422` only** to `FrankfurterPairNotQuotableException`, and every
   other status — `404` included — to `FrankfurterGeneralException`. On this API `422` is the sole
   "invalid currency" signal, and `404` means our URL was wrong.
4. **A delegating `Client`** that catches `IOException` and rethrows `FrankfurterGeneralException`.

**Why bean 4 exists — the non-obvious part the spike uncovered.** An `ErrorDecoder` only ever sees an HTTP
*response*. A connect failure or read timeout never reaches it: Feign wraps the `IOException` into
`feign.RetryableException` at the client level. Without bean 4, `RateServiceImpl` would have to
`catch (RetryableException ...)`, dragging a third-party HTTP library into the domain layer. Wrapping the
default client keeps every failure mode translated **inside `common`**:

```java
@Bean
Client frankfurterHttpClient() {
    Client defaultClient = new Client.Default(null, null);
    return (request, options) -> {
        try {
            return defaultClient.execute(request, options);
        } catch (IOException ioException) {
            throw new FrankfurterProviderException(UNREACHABLE_MESSAGE, ioException);
        }
    };
}
```

Verified in the spike: with `read-timeout=1ms` the caller receives
`FrankfurterProviderException: Frankfurter was unreachable or timed out`, cause
`java.net.SocketTimeoutException: Read timed out` — chained, so the root cause reaches the log while the
HTTP body stays the four-field `ErrorResponse`.

**Consequence for what `core` catches.** `RateServiceImpl` catches exactly two provider-neutral exceptions
from `common/integrations/frankfurter` — `FrankfurterGeneralException` and
`FrankfurterPairNotQuotableException` — and maps them onto `ExchangeRateUnavailableException` (`502`) and
`UnsupportedCurrencyPairException` (`422`), attaching the currency pair the decoder cannot know. **No Feign
type and no Spring `RestClient` exception type appears anywhere under `core/`.** The neutral exceptions carry
only the HTTP status in their message, never the provider's response body — which is what keeps the
provider's payload out of our error responses by construction rather than by discipline.

**The placeholder keeps its name.** `FrankfurterFeignClient` is now accurate, so the earlier plan to rename
it to `FrankfurterClient` and delete the file is withdrawn. Nothing is deleted by this story.

**Decision 2 — TTL cache: Caffeine behind the Spring Cache abstraction.**

Use `@Cacheable` from `spring-context`, because the abstraction keeps the cache out of the business code
entirely. The default `ConcurrentMapCacheManager`, however, **has no TTL at all** — entries live for the
lifetime of the context — so it cannot satisfy REQ-13 on its own. That leaves two options: hand-roll an
expiring map, or add Caffeine. Against the `CLAUDE.md` dependency criteria, Caffeine passes all three: it
sits entirely behind `CacheManager`, correct concurrent expiry with size bounds is exactly the kind of thing
not worth reinventing inside a 7-hour budget, and it is the de-facto JVM caching library with Spring Boot
providing first-class auto-configuration and dependency management for it. Add
`com.github.ben-manes.caffeine:caffeine` and the Spring Boot caching starter **without explicit versions** —
both are managed by the Boot parent. Verify the starter artifact id resolves under the `4.1.1` parent before
going further; if the id was reorganised in Boot 4, `org.springframework:spring-context-support` plus
`@EnableCaching` and a hand-declared `CaffeineCacheManager` bean is the equivalent fallback.

```java
@Cacheable(cacheNames = RATES_CACHE, key = "#baseCurrency + '-' + #quoteCurrency")
public Rate getRate(String sourceCurrency, String targetCurrency) {
```

- **The cache key is the currency pair — `baseCurrency` + `quoteCurrency` — and nothing else.** A rate is
  a property of the pair alone.
- **The cache sits at the `RateService` boundary, not on the controller and not inside the HTTP client.**
  On the controller it would cache a serialisation concern and Seq 4 (which calls the service directly,
  never the controller) would miss the cache entirely. Inside the client it would be invisible to the layer
  that owns the freshness decision.
- **Self-invocation defeats the proxy.** `@Cacheable` works through a Spring proxy, so the annotation must
  sit on the public `getRate` that callers enter through. Do not split the logic into a private helper and
  annotate that — it will silently never cache, and the Red 6 test is what catches it.
- **`Rate` is an immutable `record`**, so the shared cached instance cannot be mutated by one caller and
  observed by another. Do not introduce a mutable rate holder later without revisiting this.
- **TTL: 60 seconds.** Frankfurter republishes ECB reference rates roughly once per working day, so even a
  multi-minute TTL is not economically stale. 60s is chosen as a deliberately conservative ceiling on how
  wrong a Seq 4 conversion can be, while still collapsing a burst of conversions on the same pair into one
  outbound call. The value lives in `frankfurter.client.cache-ttl` so it can be tuned without a rebuild.
- **Invalidation: TTL expiry only (`expireAfterWrite`), with no eviction path — and this is the choice
  REQ-13 asks to be documented.** Nothing in this service mutates a rate; the provider is the sole writer,
  so there is no event that could correctly invalidate an entry early. Time is the only signal available,
  which makes time-based expiry the whole strategy rather than a simplification of one. Record this
  reasoning in the README (Seq 13).
- **Bound the cache with `maximumSize`** (`frankfurter.client.cache-max-size`, e.g. `500`). The key space is
  built from raw query parameters, so an unbounded cache is a memory-exhaustion vector a caller controls.

**Error mapping — two codes, because "you typed a bad currency" and "the provider is down" are different
failures.** Blanket-mapping every provider error to `502` would blame the provider for the caller's typo.

| Cause | Domain exception | Status | Code |
|---|---|---|---|
| Read/connect timeout, provider `5xx`, or provider `404` (malformed URL — our bug), all arriving as `FrankfurterGeneralException` | `ExchangeRateUnavailableException` | `502` | `RATE_UNAVAILABLE` |
| Provider `422` (`invalid currency: XXX`), arriving as `FrankfurterPairNotQuotableException`, or a `200` whose `rate` field is null | `UnsupportedCurrencyPairException` | `422` | `UNSUPPORTED_CURRENCY_PAIR` |

`502 Bad Gateway` is preferred over `503` because the failure is an unusable answer from an upstream
dependency, not this service being unavailable. One code covers timeout and `5xx` alike; splitting out `504`
would add a distinction the caller cannot act on differently.

**Both exceptions must chain the cause** — `super(cause)` — so `log.error("…", exception)` in the advice
prints the underlying `SocketTimeoutException` while the HTTP body stays the four-field `ErrorResponse`.
That combination is precisely what REQ-12's "meaningful error, not a stack trace" is asking for. Never put
the provider's URL or raw body into `ErrorResponse.message`; name the currency pair instead.

**`from == to` is a success, not an error — and that is the provider's choice, not ours.** v2 answers
`200 {"rate":1.0}` for `/rate/USD/USD`, so the natural behaviour is a `200` with `rate` = 1. Do not add a
short-circuit in this story; do note the trade-off in the README: a full network round-trip is currently
spent discovering that `USD == USD`, and the cheap local short-circuit (returning `BigDecimal.ONE` without
calling out) is a deliberate Seq 9 follow-up rather than something to pre-empt here.

**Do not normalise the currency codes to upper case here either.** ISO-4217 codes are upper case by
definition, and Seq 9 adds the `^[A-Z]{3}$` constraint that closes the question properly. Until then a
lowercase request happens to work (v2 is case-insensitive and echoes codes back upper case — verified) but
occupies a second cache entry: harmless, because `maximumSize` bounds the damage. Normalising inside `getRate` would not help anyway — the
`@Cacheable` key is evaluated *before* the method body runs, so the key would still see the raw input, and
pushing `.toUpperCase()` into the SpEL key expression introduces a default-locale hazard on codes containing
`I` (`INR`, `ISK`).

**MapStruct for the provider response: YES — decided by the user.**
The earlier "no MapStruct here" decision (and `@architect`'s approval of it) rested on the v1 payload, where
`FrankfurterLatestRatesResponse -> Rate` meant a keyed lookup into a `Map<String, BigDecimal>` plus a null
check plus a scale normalisation — not a field-to-field copy. **The v2 payload is flat**
(`date`, `base`, `quote`, `rate`), so that argument no longer applies: `FrankfurterRatePairResponse -> Rate`
is now a genuine field-to-field mapping, and a MapStruct mapper in `core/mapper` is consistent with
`BalanceMapper`. The only logic left is the null-`rate` guard and `setScale(RATE_SCALE, HALF_UP)`, which fit
an `@AfterMapping` or a small default method. `ExchangeRateResponseMapper` (`Rate -> RateResponse` in `rest/mapper`)
is unchanged and stays MapStruct.

**Concretely:** add `core/mapper/RateMapper.java` mapping `FrankfurterRatePairResponse -> Rate`
(`base -> sourceCurrency`, `quote -> targetCurrency`, `rate -> rate`), alongside `core/mapper/BalanceMapper.java`.
The null-`rate` guard stays in `RateServiceImpl` — it decides an exception, which is control flow, not
mapping — and the `setScale(RATE_SCALE, HALF_UP)` normalisation goes in the mapper via `@AfterMapping` or a
`default` method. A `RateMapperTest` in `src/test/java/zetta/foreignexchange/core/mapper/` follows
`BalanceMapperTest`.

**Package placement note.** `.claude/CLAUDE.md` describes `common/integrations/{provider}`,
`persistence/mapper` and `rest/dto`; the code on disk uses `common/integrations/frankfurter`, `core/mapper`
and `rest/model`. **Follow the code.** Do not relocate existing packages as part of this story. Likewise,
`BalanceServiceImpl` is a `public` class despite the guidance preferring package-private impls — match the
existing convention for `RateServiceImpl` rather than introducing a second style. (`@Cacheable` needs a
proxyable public method regardless.)

**`application.yaml` after this story:**

```yaml
frankfurter:
  client:
    url: https://api.frankfurter.dev/v2
    connect-timeout: 2s
    read-timeout: 3s
    cache-ttl: 60s
    cache-max-size: 500
```

Durations bind natively to `java.time.Duration`. Keep every value overridable by environment variable so
Seq 11 (`docker compose`) needs no code change.

**Build note.** This project has no auto-formatter. Formatting is enforced by `maven-checkstyle-plugin`
only; use `mvn checkstyle:check`.

### Testing Notes

**The suite must not touch the real Frankfurter API.** `RateIntegrationTest` extends
`BaseIntegrationTestSetUp` — which already boots the full context against the Postgres Testcontainer — and
replaces the provider with `@MockitoBean FrankfurterFeignClient`
(`org.springframework.test.context.bean.override.mockito.MockitoBean`). Everything below the mock is real:
the controller, the service, the cache, the advice, the JSON serialisation. That keeps the suite
deterministic, fast and offline. A WireMock stub server would be the alternative and would additionally
exercise the timeout configuration and the JSON deserialisation, but it means another test dependency;
prefer `@MockitoBean` and note the gap in the README.

**Clear the rate cache in `@BeforeEach`.** This is the one trap in the story. `BaseIntegrationTestSetUp` is
`@Transactional`, so database state rolls back between tests — but the cache is a context-level singleton
and does **not** roll back, while Mockito resets the `@MockitoBean` between tests. Without an explicit
clear, a cache entry populated by an earlier test makes a later test observe *zero* provider calls and the
TTL assertion becomes meaningless. Inject `CacheManager` and clear the `rates` cache before each test.

**Tests to write**

`src/test/java/zetta/foreignexchange/core/service/RateServiceTest.java` —
`@ExtendWith(MockitoExtension.class)`, `@Mock FrankfurterFeignClient`, `@InjectMocks RateServiceImpl`; Instancio
for any value whose exact content does not matter, explicit literals for the rate, the currency codes and
the scale, which do:
- `getRate_withKnownCurrencyPair_returnExchangeRate`
- `getRate_withProviderRate_returnRateScaledToRateScale`
- `getRate_whenProviderTimesOut_throwRateUnavailableException`
- `getRate_whenProviderReturnsServerError_throwRateUnavailableException`
- `getExchangeRate_whenProviderRejectsCurrencyPair_throwUnsupportedCurrencyPairException`
- `getRate_whenProviderReturnsNullExchangeRate_throwUnsupportedCurrencyPairException`
- `getRate_withIdenticalCurrencyPair_returnExchangeRateOfOne`

`src/test/java/zetta/foreignexchange/rest/controller/RateControllerTest.java` — mirrors
`ClientControllerTest` exactly: `@Mock RateService`, `@Spy RateResponseMapper =
Mappers.getMapper(RateResponseMapper.class)`, `@InjectMocks RateController`, asserting the `ResponseEntity`
status and body and verifying both collaborators.

`src/test/java/zetta/foreignexchange/rest/mapper/RateResponseMapperTest.java` — follows
`ClientBalancesResponseMapperTest`.

`src/test/java/zetta/foreignexchange/integration/RateIntegrationTest.java` — MockMvc through the real stack:
- `getRate_withKnownCurrencyPair_returnExchangeRate` — `200` and the full body
- `getRate_withSecondCallInsideTtl_serveFromCacheWithoutCallingProvider` — the REQ-13 proof
- `getRate_withDifferentCurrencyPair_callProviderAgain` — proves the key is the pair, not a single slot
- `getRate_whenProviderTimesOut_returnRateUnavailable` — `502`, all four body fields, the request path
- `getRate_whenProviderRejectsCurrencyPair_returnUnsupportedCurrencyPair` — `422`, all four body fields
- `getRate_withIdenticalCurrencyPair_returnExchangeRateOfOne` — `200` with `rate` = 1, the v2 identical-pair path

Coverage stays above 80%: the config classes are exercised by the context boot in the integration test, and
every branch of `getRate` has a named unit test.

---

## Dependencies

- **Depends On:** None. This story touches no table and no entity, which is why `PRIORITY.md` records it as
  buildable in parallel with Seq 1/2. It reads `EntityConstant.RATE_SCALE`, which Seq 1 already delivered
- **Blocks:** `4` — `POST /conversions` calls `RateService.getRate(...)` to obtain the rate before it opens
  the balance-mutating transaction, and writes that rate into `conversions.rate`; `9` — the global error
  handling story extends the two advice handlers added here; `10` — the OpenAPI story documents this
  endpoint; `13` — the README must carry the TTL and invalidation reasoning recorded above

---

## References

- `.claude/Java-Assignment.pdf` — `GET /rates?from=USD&to=EUR`; "External provider integration with
  timeouts and graceful failure"; "Caching of rates with a TTL. Explain your invalidation choice in the
  README"; the suggested provider `https://api.frankfurter.dev/v1/latest?from=USD`
- `backlog/BACKLOG.md` — REQ-1, REQ-12, REQ-13
- `backlog/PRIORITY.md` — Seq 3, and the note that Seq 3 has no database dependency and gates Seq 4
- `backlog/stories/done/2-client-balances-endpoint.story.md` — the controller / advice / `ErrorResponse`
  shape this story extends rather than reinvents
- Live provider probes performed while planning (2026-09-19): `/v1/latest?base=USD&symbols=EUR` → `200`;
  `?symbols=XXX` → `404 {"message":"not found"}`; `?base=USD&symbols=USD` → `422 {"message":"bad currency
  pair"}`; the currently configured `/v2/rates/` → `404`

---

## @architect Recommendations

> **Added by @architect during /plan review**

### Rulings the planner asked for

- ⚠️ **SUPERSEDED by the v2 payload, and the user has decided: use MapStruct.** The ruling below was made for v1's nested
  `rates` map. v2 returns a flat `{date, base, quote, rate}`, so `FrankfurterRatePairResponse -> Rate` *is*
  a field-to-field copy and MapStruct is now a reasonable choice. See "MapStruct for the provider response
  is now a live option" in the Implementation Notes for the concrete shape (`core/mapper/RateMapper`).
  Original ruling, for the record:
- ✅ **No MapStruct for `FrankfurterLatestRatesResponse -> Rate` — approved.** The `CLAUDE.md` rule is
  "mappers for structs between layers"; this is not a struct-to-struct copy but a keyed lookup into
  `Map<String, BigDecimal>` plus a null check plus a scale normalisation. MapStruct would need an
  `expression=` / `@Named` method holding all of that logic anyway, and the generated class would hide the
  null check the error design depends on. MapStruct stays where it earns its keep —
  `Rate -> RateResponse` in `src/main/java/zetta/foreignexchange/rest/mapper/RateResponseMapper.java`,
  mirroring `src/main/java/zetta/foreignexchange/rest/mapper/ClientBalancesResponseMapper.java`.
  **Constraint:** the provider-response translation lives in exactly one private method of
  `src/main/java/zetta/foreignexchange/core/service/implementation/RateServiceImpl.java` — not inlined in
  `getRate` and not duplicated inside a catch block.
- ~~**Deleting `FrankfurterFeignClient.java` — approved**~~ — **WITHDRAWN.** Superseded by the user's
  Feign decision: the placeholder keeps its name, because the name is now accurate. Nothing is deleted.
- ✅ **Caffeine plus the Boot caching starter — approved, and the fallback paragraph can be dropped.**
  Verified against the `4.1.1` parent: `spring-boot-starter-cache:4.1.1` resolves (to `spring-boot-cache`,
  to `spring-context-support`), and `spring-boot-dependencies:4.1.1` manages
  `com.github.ben-manes.caffeine:caffeine` at `3.2.4`. Declare both without an explicit version. There is
  no simpler alternative worth taking: `ConcurrentMapCacheManager` genuinely has no TTL, and a hand-rolled
  expiring map on the path that feeds Seq 4 money movement is the wrong thing to own.
  **Note:** because `CacheConfiguration` declares its own `CaffeineCacheManager` bean built from
  `FrankfurterClientProperties`, Boot cache auto-configuration backs off entirely — `spring.cache.*` and
  `spring.cache.caffeine.spec` will have **no effect**. Do not set them and expect them to apply.
- ✅ **`502` and `422` — both approved.** `503` would be wrong: it claims *this* service is unavailable and
  invites a `Retry-After` we cannot honestly supply. `504` is more literal for the timeout branch, but one
  `RATE_UNAVAILABLE` code the caller acts on identically is the better contract — record that merge in the
  README (Seq 13). `422` is right for `XXX` and for `USD` to `USD`: both are syntactically valid and
  semantically unquotable, which is exactly `422 Unprocessable Content`, not `400`.
- ✅ **Following the code rather than `.claude/CLAUDE.md` for package names — approved.**
  `common/integrations/frankfurter/...`, `core/mapper`, `rest/model` and public service impls are the established
  convention in `src/main/java/zetta/foreignexchange`. Introducing a second style for one story costs
  consistency and buys nothing; the layer *dependencies* — which are what the rule actually protects — are
  unchanged (`rest` to `core` to `common`, with `persistence` untouched).

### Mandatory constraints

- ⚠️ ~~**The Boot request-factory API named in Decision 1 is not on this classpath.**~~ **VOID** — the
  `RestClient` / `HttpServiceProxyFactory` design it constrained was superseded by the user's Feign
  decision. The Feign replacement constraints, each verified by the spike, are:
  **(a)** pin `<spring-cloud.version>2025.1.3</spring-cloud.version>` — it is built against Boot 4.0.8 while
  this project runs 4.1.1, so the version must be explicit and the skew noted in the README;
  **(b)** `@EnableFeignClients` on
  `src/main/java/zetta/foreignexchange/ForeignExchangeApplication.java` is mandatory — the starter alone
  registers nothing and the failure shows up as a missing-bean error, not a startup warning;
  **(c)** `FrankfurterClientProperties` must be *registered* somehow, or it never binds and the `@FeignClient`
  `url` placeholder is the only thing that still resolves — silently leaving default timeouts in place.
  **As now implemented this is already satisfied**: the class carries `@Configuration` +
  `@ConfigurationProperties` and is found by component scanning, so no `@ConfigurationPropertiesScan` and
  no `@EnableConfigurationProperties` is required. That equivalence holds **only because it is a mutable
  class with setters** — see "Properties Style" below;
  **(d)** `FrankfurterConfiguration` must **not** carry `@Configuration`, or its `Request.Options`,
  `Retryer` and `ErrorDecoder` leak into the main context as global Feign defaults;
  **(e)** the delegating `Client` bean that converts `IOException` into `FrankfurterGeneralException` is
  **not optional** — without it a timeout surfaces as `feign.RetryableException` and `core` must import
  Feign to catch it.
- ⚠️ **`getRate` must not carry `@Transactional`.** The "Existing Pattern Reference" section shows
  `BalanceServiceImpl` annotated `@Transactional(readOnly = true)`; copying that onto `getRate` would hold
  a database transaction open across the outbound HTTP call — precisely what the `CLAUDE.md` database rule
  forbids, and the property Seq 4 depends on. `RateServiceImpl` touches no repository and must open no
  transaction.
- ⚠️ **Do not map every provider `4xx` to `UNSUPPORTED_CURRENCY_PAIR`.** Frankfurter is a free public API:
  a `429`, `403` or `408` would be reported to our caller as "unsupported currency pair" for a pair that
  worked a second earlier — a wrong and unactionable answer. Map **only `404` and `422`** to
  `FrankfurterPairNotQuotableException`. **Sharpened by the v2 contract:** map **only `422`**, because on
  this API `422 invalid currency` is the sole caller-fault signal and `404` means our own URL was malformed.
  Every other status — `404`, `429`, `403`, `5xx` — becomes `FrankfurterGeneralException` and therefore
  `502`. This split lives in `FrankfurterConfiguration.FrankfurterErrorDecoder`, and the spike exercises the
  `422` branch. Pin the other side too: add
  `getRate_whenProviderRateLimits_throwRateUnavailableException` to `RateServiceTest`, stubbing the client
  to throw `FrankfurterGeneralException`, plus an `ErrorDecoder`-level unit test asserting that `404` and
  `429` both decode to `FrankfurterGeneralException` rather than to the not-quotable one.
- ⚠️ **Null-check the response body and the `rate` field — and route them differently.** (Restated for the
  flat v2 payload.) A `200` with a null body is the provider returning garbage, so it is
  `ExchangeRateUnavailableException` and `502`. A `200` whose `rate` field is null is the pair not being quotable,
  so it is `UnsupportedCurrencyPairException` and `422`. Without the first check a shape change at the
  provider becomes an NPE and a `500`.
- ⚠️ **Chain the cause with a hand-written constructor — Lombok will not do it.** `@AllArgsConstructor`
  (the pattern in `src/main/java/zetta/foreignexchange/core/exception/ClientNotFoundException.java`) would
  store a `Throwable cause` parameter as a *field* and never call `super(cause)`, so
  `log.error("...", exception)` in the advice would print no underlying stack trace and the REQ-12
  "meaningful error" goal would be silently unmet. Both new exceptions need an explicit constructor that
  calls `super(cause)` and keeps the two currency codes as fields.
- ⚠️ **Never pass the caught exception message into `ErrorResponse`.** `FeignException.getMessage()`
  embeds the provider's raw response body just as Spring's `RestClientResponseException` does, which is the
  second reason the `ErrorDecoder` returns its own neutral exceptions carrying only the HTTP status — the
  raw body never leaves `common`. Verified live: the
  `404` returns `{"message":"not found"}`, which lands inside that message. Build `message` only from the
  two currency codes and a constant format string, exactly as `CLIENT_NOT_FOUND_MESSAGE` does in
  `src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java`.
- ⚠️ **Guard the currency-code format inside `getRate`, before the outbound call.** As designed, any
  `from` / `to` value — a 10 KB string, a fresh random code on every request — reaches the live provider,
  because `@Cacheable` cannot short-circuit a pair it has never successfully fetched and exceptions are
  never cached. `maximumSize` bounds memory but not the outbound-request amplification a caller controls
  against a third-party API. Add a
  `private static final Pattern CURRENCY_CODE_PATTERN = Pattern.compile("^[A-Z]{3}$")` check at the top of
  `getRate`, throwing `UnsupportedCurrencyPairException`. The service is the right place, not the
  controller: it needs no new advice handler, it reuses the `422` body this story already specifies, and —
  because `@Cacheable` stores nothing when the method throws — no cache entry is created either. It also
  makes Seq 9 purely **additive**: the `^[A-Z]{3}$` `@Pattern` added at the edge there upgrades the status
  to `400` without contradicting a single Seq 3 test. Note in the README that `422` is the interim status
  for a malformed code.
- ⚠️ **Prove the TTL expires — Red 6 as written only proves that a cache exists.** If `expireAfterWrite`
  were typed as `expireAfterAccess`, or `FrankfurterProperties.cacheTtl()` never reached the builder, every
  test listed still passes and the README REQ-13 claim would be false. Add
  `getRate_afterTtlExpires_callProviderAgain` to
  `src/test/java/zetta/foreignexchange/integration/RateIntegrationTest.java` with
  `@TestPropertySource(properties = "frankfurter.client.cache-ttl=50ms")` on a nested class, call, wait past
  the window, call again, and `verify(frankfurterFeignClient, times(2))`. Caffeine evaluates `expireAfterWrite`
  on read, so this is reliable for a single key without polling.

### Smaller points

- ✅ **`@Cacheable(sync = true)`.** Without it the story own claim — "collapsing a burst of conversions on
  the same pair into one outbound call" — holds only for *sequential* calls; N simultaneous misses on the
  same pair all reach the provider. Caffeine supports `sync`; it is one attribute on the annotation the
  story already plans to write.
- ✅ **Keep `BigDecimal` off the JSON number path.** Declare both `amount` and the map value in
  `FrankfurterRatePairResponse` as `BigDecimal` (`Map<String, BigDecimal>`) — Jackson then reads the raw
  token text with no binary-float round-trip. `Map<String, Object>` or `JsonNode.asDouble()` would violate
  the acceptance criterion silently. In assertions remember that `BigDecimal.equals` is scale-sensitive:
  `assertEquals(new BigDecimal("0.8726"), rate.rate())` **fails** against a value scaled to `8` — assert
  `new BigDecimal("0.87260000")`.
- ✅ **`@MockitoBean FrankfurterFeignClient` gives `RateIntegrationTest` its own Spring context** (a different
  context cache key from `BalanceIntegrationTest`), so a second Postgres container starts and Flyway re-runs
  against it. That works — simply do not expect container reuse, and do not "fix" it by moving the mock onto
  `src/test/java/zetta/foreignexchange/integration/BaseIntegrationTestSetUp.java`, which would change the
  context every existing integration test already uses.
- ✅ **Correction to the package-placement note:** `@Cacheable` does not require a public *class* — CGLIB
  proxies package-private classes without complaint; only the annotated *method* must be public and
  non-final. The conclusion (keep `RateServiceImpl` public, matching `BalanceServiceImpl`) still stands, on
  consistency grounds alone.
- ✅ **Two lines for the README (Seq 13), not code:** failures are never cached, so during a provider outage
  every inbound request pays the full connect plus read timeout — accepted, since a circuit breaker is an
  explicit non-goal; and `cache-ttl` / `cache-max-size` sit under `frankfurter.client.*` although the cache
  is a `RateService` concern rather than a client concern — acceptable for a single provider, but say so
  rather than leaving it to read as an accident.


---

## Spike Already Landed (uncommitted working tree)

> **Added after the user directed the Feign decision.** A compatibility spike was run to prove OpenFeign
> works on Boot 4.1.1 before `/code` commits to it. The spike passed, and the production-shaped pieces were
> left in the working tree. **Nothing is committed** — no branch has been created yet.

### Already written, checkstyle-clean (0 violations)

| File | State |
|---|---|
| `pom.xml` | `<spring-cloud.version>2025.1.3</spring-cloud.version>`, the `spring-cloud-dependencies` BOM import, and a version-less `spring-cloud-starter-openfeign` |
| `ForeignExchangeApplication.java` | `@EnableFeignClients(basePackages = "zetta.foreignexchange.common.integrations")` — **no** `@ConfigurationPropertiesScan`, see Properties Style |
| `common/integrations/frankfurter/configuration/FrankfurterProperties.java` | **class** (`@Configuration` + `@ConfigurationProperties` + Lombok `@Getter`/`@Setter`): `url`, `connectTimeout`, `readTimeout`, `cacheTtl`, `cacheMaxSize` |
| `common/integrations/frankfurter/FrankfurterFeignClient.java` | the real `@FeignClient`, calling `/rate/{base}/{quote}` |
| `common/integrations/frankfurter/response/FrankfurterRatePairResponse.java` | `date`, `base`, `quote`, `rate` — the flat v2 shape |
| `common/integrations/frankfurter/configuration/FrankfurterConfiguration.java` | `Request.Options` from properties, `Retryer.NEVER_RETRY`, `ErrorDecoder` (**422-only**), `IOException`-wrapping `Client` |
| `common/integrations/frankfurter/exception/FrankfurterProviderException.java` | provider-neutral, cause-chaining |
| `common/integrations/frankfurter/exception/FrankfurterPairNotQuotableException.java` | provider-neutral, cause-chaining |
| `application.yaml` | URL corrected to `https://api.frankfurter.dev/v2`, plus `connect-timeout: 2s`, `read-timeout: 3s`, `cache-ttl: 60s`, `cache-max-size: 500` |

### Proven by the spike, against the live provider

| Scenario | Observed |
|---|---|
| `fetchLatestRates("USD", "EUR")` | `FrankfurterRatePairResponse[date=2026-09-20, base=USD, quote=EUR, rate=0.86984]` |
| `fetchLatestRates("USD", "USD")` | `FrankfurterRatePairResponse[... quote=USD, rate=1.0]` — **a `200`, not an error** |
| `fetchLatestRates("USD", "XXX")` (provider `422`) | `FrankfurterPairNotQuotableException: ... HTTP 422` |
| `read-timeout=1ms` | `FrankfurterProviderException: Frankfurter was unreachable or timed out`, cause `java.net.SocketTimeoutException: Read timed out` |
| Property binding, **without any `@ConfigurationPropertiesScan`** | `url=https://api.frankfurter.dev/v2 connect=PT3S read=PT6S ttl=PT1M maxSize=500` |

The spike test itself was **deleted** — it called the real API over the network, which the suite must never
do. Its scenarios live on as the `RateIntegrationTest` cases listed under Testing Notes, driven by
`@MockitoBean` instead.

### Properties Style — classes, not records

The project convention is now **mutable classes** for configuration/properties holders, not records:

```java
@Configuration
@ConfigurationProperties(prefix = "frankfurter.client")
@Getter
@Setter
public class FrankfurterProperties {

    private String url;
    private Duration connectTimeout;
    // ...
}
```

**This is what removes the need for `@ConfigurationPropertiesScan`.** Binding mode follows the shape of the
class, and the two are not interchangeable:

| Shape | Binding | Registration required |
|---|---|---|
| `record` / immutable class | constructor (value-object) binding | **Must** have `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties` — `@Component`/`@Configuration` alone does **not** work |
| Mutable class, no-arg ctor + setters | JavaBean (setter) binding | `@Configuration` or `@Component` is enough — component scanning registers it |

Verified by booting four contexts: with neither annotation and a *record*, `getBeansOfType` returned **0**
beans and the Feign client failed with `No qualifying bean of type 'FrankfurterProperties'` while creating
`frankfurterRequestOptions`; with the *class* form above and no scan annotation, every value bound.

**Constraint for any future properties holder:** if one is ever written as a record or with `final` fields,
it must be registered explicitly or it will silently fail to bind. Keep the mutable-class form and the
annotation stays unnecessary.

### What `/code` still owns

Everything above is **infrastructure, not the story**. `/code` starts the TDD cycle at Red 1 and still owns:

- [ ] `Rate`, `RateService.getRate` + impl, `core/mapper/RateMapper`, and the neutral-to-domain exception
      mapping — **tests first**
- [ ] `ExchangeRateUnavailableException` / `UnsupportedCurrencyPairException`, with hand-written cause-chaining
  constructors (Lombok will not chain — see the `⚠️` above)
- [ ] The `^[A-Z]{3}$` guard inside `getRate`, before any outbound call (mandatory `⚠️`)
- [ ] `RateController` mapping, `ExchangeRateResponse`, `ExchangeRateResponseMapper`, SpringDoc annotations
- [ ] The two `@RestControllerAdvice` handlers
- [ ] `CacheConfiguration` + Caffeine + `@Cacheable(sync = true)` — the caching dependencies are **not** in
      `pom.xml` yet; only the Feign ones are
- [ ] Every test in Testing Notes, including both TTL proofs
- [ ] A unit test for `FrankfurterErrorDecoder` covering `404`, `422` and a third status such as `429`

**Caveat — the full application context has not booted.** Docker was not running, so
`BalanceIntegrationTest` could not run and no test has yet started the real `ForeignExchangeApplication`
with Feign enabled. The 16 unit tests pass and the spike proved Feign inside a Spring context, but the first
thing `/code` should do with Docker up is run `mvn test` and confirm the full context — Feign plus JPA plus
Flyway together — starts cleanly.
