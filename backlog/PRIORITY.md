# Foreign-Exchange Service — Priority Roadmap

> **Maintained by:** @planner (sequencing, status) + @architect (risk flags, blocker validation)
> **Last updated:** 2026-09-21
> **Update triggers:**
> - @planner: after every planning cycle (new story added, story moved to in-progress)
> - @architect: after every architecture review (risk flags, blocker corrections, priority changes)
> - @planner: when a story moves to done (Status → Done, update Sprint Recommendation)

---

## North Star

> Ship a correct, well-tested foreign-exchange conversion service that satisfies every requirement in
> `.claude/Java-Assignment.pdf`, using the simplest structure that does the job — no auth, no UI, no
> pattern-stacking.

Every story in this roadmap must close at least one row in the requirement tracker (`backlog/BACKLOG.md`).
Requirements are referenced here as `REQ-N` — deliberately distinct from `Seq`, which is this table's
own, unrelated numbering.

---

## Implementation Sequence

> Pick the lowest Seq number whose blockers are resolved (story file in `backlog/stories/done/`).
> `Seq` is the story's permanent ID — there is no separate Epic/Story ID scheme. A story file is
> named `{seq}-{short-title}.story.md`.

| Seq | Title | Tier | Closes Requirement(s) | Blockers | Status |
|---|---|---|---|---|---|
| 1 | Database schema — `client_balances` + `conversions` tables designed together (columns, relationship, locking/idempotency/index columns), plus demo client/balance seed data | Critical | REQ-5 (also lays the groundwork for REQ-6, REQ-9, REQ-10, REQ-11) | — | Done |
| 2 | `GET /clients/{clientId}/balances` | Critical | REQ-4 | Seq 1 | Done |
| 3 | `GET /rates` — provider integration, timeout, graceful failure, TTL cache | Critical | REQ-1, REQ-12, REQ-13 | — | Done |
| 4 | `POST /conversions` **complete** — atomic debit/credit + response shape, insufficient-funds / not-found error paths, concurrency control, `Idempotency-Key` replay (consolidates former Seq 5, 6, 7) | Critical | REQ-2, REQ-6, REQ-7, REQ-8, REQ-9, REQ-10, REQ-11, REQ-14, REQ-17, REQ-18 | Seq 1, Seq 3 | Done |
| 5 | ~~Insufficient funds / unknown client / unknown currency error paths~~ | Critical | REQ-7, REQ-8 | Seq 4 | Merged into Seq 4 |
| 6 | ~~Concurrency control on balance updates (no double-spend)~~ | Critical | REQ-9 | Seq 4 | Merged into Seq 4 |
| 7 | ~~`Idempotency-Key` replay handling~~ | Critical | REQ-10 | Seq 4 | Merged into Seq 4 |
| 8 | `GET /conversions` — paginated, filtered history | Critical | REQ-3 | Seq 4 | Done |
| 9 | Global error handling (`@ControllerAdvice`) + request validation | High | REQ-14, REQ-15 | Seq 2, Seq 4 | Done |
| 10 | OpenAPI / Swagger UI | Medium | REQ-16 | Seq 2, 3, 4, 8 | Done |
| 11 | Dockerfile (multi-stage, non-root) + `docker compose up` wiring | High | REQ-19, REQ-20 | Seq 1 | Done |
| 12 | Test coverage hardening — explicit idempotency/insufficient-funds/happy-path/concurrency assertions | Critical | REQ-17, REQ-18 | Seq 4 | Done |
| 13 | README — run instructions, trade-offs, concurrency choice, what's next | Critical | REQ-21 | All above | Done |

---

## Tiers

| Tier | Meaning |
|---|---|
| Critical | Required for the service to satisfy the assignment's core spec — correctness axis |
| High | Required for the "production sense" grading axis |
| Medium | Improves the submission but is not spec-breaking if slightly incomplete |
| Internal Quality | Non-spec improvements: refactoring, extra coverage, observability |

---

## Dependency Graph

```
[1] Database schema (both tables) + seed data
        │
        +──► [2] GET /clients/{clientId}/balances
        │
        +──► [4] POST /conversions (complete) ◄──── [3] GET /rates + provider + cache  [DONE]
                    │      · atomic debit/credit + response shape   (was [4])
                    │      · insufficient funds / not-found errors  (was [5])
                    │      · concurrency control                    (was [6])
                    │      · Idempotency-Key replay                 (was [7])
                    │
                    └──► [8] GET /conversions history

[2]+[4] ──► [9] Global error handling + validation ──► [10] OpenAPI / Swagger UI
[1] ──► [11] Dockerfile + docker compose
[4] ──► [12] Test coverage hardening ──► [13] README
```

---

## Re-prioritisation Notes

> This section is appended (never overwritten) by @architect after each refinement run.

### Initial setup — 2026-09-18 (@planner)

Roadmap initialized from the assignment brief (`.claude/Java-Assignment.pdf`). No stories planned yet.

Seq 1 (schema + seed data) and Seq 3 (`GET /rates`) are the two critical-path blockers that gate
everything else — Seq 3 has no dependency on the database and can be built in parallel with Seq 1/2.

Seq 1 is scoped to design **both** tables together, not just seed `client_balances` — `ClientBalanceEntity`
and `ConversionEntity` shape every DTO/mapper built afterward, so their columns and relationship must be
decided before any endpoint code exists, not discovered incrementally while building Seq 4. In scope for
Seq 1 specifically:
- Whether `conversions` references `client_balances` by a real FK or by plain `clientId`+`currency`
  strings (a full ledger/journal model is explicitly out of scope — see `BACKLOG.md` Non-Goals — so lean
  toward the simpler option unless a concrete reason emerges)
- Where the concurrency-strategy column lives (`@Version` on `client_balances`, if that's the chosen
  strategy — see `architect.md` Pattern 5)
- The idempotency unique constraint on `conversions` (`client_id` + `idempotency_key`)
- Indices backing the `GET /conversions` filters (`transaction_id`, `date`, `client_id`)

Seq 4 (`POST /conversions` happy path) is the highest-risk story: it is where the concurrency strategy,
the idempotency approach, and atomic debit/credit all meet. Recommend @architect signs off on the
concurrency strategy choice (`architect.md` Pattern 5) **before** Seq 4 starts, not after — retrofitting
a locking strategy onto an already-implemented happy path is more expensive than choosing it upfront.

Seq 12 exists as a distinct sequence step even though TDD means most tests are written alongside Seq
4–8, because the assignment explicitly calls out idempotency replay, insufficient funds, and
happy-path debit/credit as scenarios that must be "explicitly" tested — this step is a checkpoint to
confirm nothing was left only implicitly covered.

### Seq 2 implementation note — 2026-09-18

Entities are deliberately **setter-free** (`@Getter` + `@Builder(toBuilder = true)` only). Seq 4 must
therefore mutate `BalanceEntity.amount` through explicit domain methods on the entity — e.g.
`debit(BigDecimal sourceAmount)` / `credit(BigDecimal targetAmount)` — rather than a reinstated setter.
These are the natural home for the non-negative-balance invariant behind `InsufficientFundsException`
(Seq 5), so Seq 4 should add them rather than rediscover the missing setter as a blocker.


### Seq 3 planning note — 2026-09-19 (user decision + compatibility spike)

**The HTTP client for the Frankfurter provider is Spring Cloud OpenFeign**, by explicit user decision, for
consistency with the `@FeignClient` style used across their other services. The story file's original
recommendation (Spring's native `@HttpExchange` + `RestClient`) is superseded and marked as such; the
`@architect` constraint built on it is marked **VOID** and replaced with Feign-specific constraints.

The compatibility risk was real and has been retired empirically rather than argued: the latest Spring Cloud
GA train, `2025.1.3`, is built against Spring Boot **4.0.8**, while this project runs **4.1.1** (only
`2026.0.0-SNAPSHOT` targets 4.1, and a snapshot is not acceptable here). A spike on the 2025.1.3 train
passed every scenario against the live provider — `200` with `BigDecimal` deserialisation, provider `404`,
provider `422`, and a 1 ms read timeout — with all 16 existing unit tests and `mvn checkstyle:check` green.
**Treat the 4.0.8-vs-4.1.1 skew as a known, tested risk**: `spring-cloud.version` is pinned explicitly, and
Seq 13 must record the skew in the README.

Two structural consequences for Seq 4 and beyond:
- The provider's failure modes are translated into **provider-neutral exceptions inside
  `common/integrations/frankfurter/exception`** (`FrankfurterGeneralException`,
  `FrankfurterPairNotQuotableException`),
  so no Feign type ever appears under `core/`. A timeout only reaches them via a delegating `Client` bean
  that wraps `IOException` — an `ErrorDecoder` alone cannot see timeouts.
- Feign infrastructure (BOM, starter, `@EnableFeignClients`, the client, its configuration, the two
  exceptions, `FrankfurterClientProperties`, corrected `application.yaml`) is already in the working tree,
  uncommitted, and documented in the story's "Spike Already Landed" section. Seq 3's TDD cycle still starts
  at Red 1 for all domain behaviour.


### Seq 3 correction — 2026-09-19 (provider contract + properties style)

Three corrections to the note above, all verified against the live provider and a booted context:

1. **The provider endpoint is v2's single-pair route**, not v1's `/latest`:
   `GET https://api.frankfurter.dev/v2/rate/{base}/{quote}` returns a flat
   `{"date":"2026-09-20","base":"USD","quote":"EUR","rate":0.86984}`. My earlier probe of `/v2/rates/`
   (plural) 404'd and led me to wrongly conclude v2 did not exist — `/rate/` is singular. One call, one
   rate, no keyed map lookup and no missing-entry case, which simplifies Seq 3 and Seq 4.
2. **v2's error semantics differ from v1's and drive the error design**: an unknown or malformed currency
   code is **`422` `invalid currency: XXX`** (never `404`), a `404` means *our* URL was malformed and must
   therefore surface as `502` rather than being blamed on the caller, and an identical pair
   (`/rate/USD/USD`) is a **`200` with `rate=1.0`** — a success path, not an error. The `ErrorDecoder` maps
   `422` only.
3. **Configuration/properties holders are mutable classes, not records** (`@Configuration` +
   `@ConfigurationProperties` + Lombok `@Getter`/`@Setter`). This is a deliberate project convention going
   forward, and it removes the need for `@ConfigurationPropertiesScan`: JavaBean binding works off
   component scanning, whereas a record needs constructor binding and therefore explicit registration.
   Any future properties holder written as a record or with `final` fields **must** be registered with
   `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties`, or it will silently fail to bind.

Note that `.claude/CLAUDE.md`, `.claude/agents/architect.md`, `.claude/agents/coder.md` and
`.claude/agents/README.md` still describe `@ConfigurationProperties` **records** in `common/properties/`,
while the code now uses classes in `common/integrations/frankfurter/configuration/`. Those documents are
the user's own convention files and were left untouched pending a decision.


### Seq 5, 6, 7 consolidated into Seq 4 — 2026-09-20 (@planner, explicit user decision)

**Requested explicitly by the user**, who asked for the complete `POST /conversions` endpoint —
implementation and testing, across `rest → core → persistence` — planned as **one** story "regardless of
story points". Seq 4 is retitled to cover the whole endpoint and now closes REQ-2, REQ-6, REQ-7, REQ-8,
REQ-9, REQ-10 and REQ-11, contributing to REQ-14, REQ-17 and REQ-18.

Seq 5, 6 and 7 are **kept as rows** (struck through, Status `Merged into Seq 4`) rather than deleted, so
the original decomposition stays legible and nobody plans them a second time. Seq numbers remain
permanent; Seq 8 onward are untouched.

The planner's normal ≤ 8-point rule is knowingly suspended — the story is estimated at 21. The
counter-argument the user's decision rests on: the four slices share one transaction boundary, one
locking decision and one response shape, so splitting them would mean three successive rewrites of
`ConversionServiceImpl` and its tests. The story file preserves Red→Green→Refactor slicing internally
(seven ordered slices), so the discipline survives even though the merge/review unit is one story.

**Blocker state verified against the code at commit `14f324c`: Seq 3 is genuinely Done.**
`RateService.getExchangeRate(baseCurrency, quoteCurrency)` returns an `ExchangeRate` record, is
`@Cacheable` on the Caffeine `exchangeRate` cache (TTL and max-size from `cache.currency-rate-pair`), and
translates provider faults into `UnsupportedCurrencyPairException` (422) and
`ExchangeRateUnavailableException` (502), both already handled in `ForeignExchangeControllerAdvice`.
Seq 4 is therefore **unblocked** and consumes that contract directly — it neither defines nor stubs a
rate interface of its own.

**Three conventions Seq 4 must honour** (all recorded in the story file):

1. **D1 — `base`/`quote` is the vocabulary everywhere in Java; the JSON wire format says
   `source`/`target`. Decided by the user, 2026-09-20.** `V3` renamed the `conversions` columns to
   `base_currency`/`base_amount`/`quote_currency`/`quote_amount`, and `ExchangeRate`, `RateService` and the
   error messages all use `baseCurrency`/`quoteCurrency`. The brief names the `POST /conversions` fields
   `sourceAmount`, `sourceCurrency`, `targetAmount`, `targetCurrency` verbatim. **Ruling:** the REST
   records (`ConversionRequest`, `ConversionResponse`) also declare `base*`/`quote*` components, each
   annotated `@JsonProperty("source…"/"target…")`, so Postman and Swagger show the brief's names while
   Java stays internally consistent. `ExchangeRateResponse` is **not** re-aliased — `GET /rates` keeps
   answering `baseCurrency`/`quoteCurrency`; the brief never names those fields and `RateIntegrationTest`
   asserts them. The resulting wire asymmetry between the two endpoints is deliberate; Seq 13 records it.

2. **D2 — identical base and quote currency is rejected on *both* endpoints, via one shared
   `SameCurrencyException`. Decided by the user, 2026-09-20, including explicit approval of the
   behaviour change this causes.** The rule lives in `CurrencyValidator.validateCurrencyPair` (after the
   regex and ISO-4217 checks, so a malformed identical pair still reports `UNSUPPORTED_CURRENCY_PAIR`), so
   both `RateServiceImpl` and `ConversionServiceImpl` get it from one place.

   ⚠ **This supersedes the "Seq 3 correction" note above, point 2**, which recorded `/rate/USD/USD` →
   `200 rate=1` as a deliberate success path. `GET /rates?from=USD&to=USD` now returns
   `422 SAME_CURRENCY` without calling the provider. Three Seq 3 tests
   (`RateServiceTest`, `RateControllerTest`, `RateIntegrationTest` — one identical-pair method each) and
   `RateController`'s `@Operation` description assert or advertise the old behaviour and are rewritten as
   part of Seq 4. REQ-1/REQ-12/REQ-13 stay `Done`; this narrows one case, it does not reopen them.

3. **`EntityConstant.RATE_SCALE` is now 5** (`V4` narrowed `conversions.rate` to `NUMERIC(19,5)`) and
   `RateMapper.scaleRate` already rounds every provider rate to scale 5 `HALF_UP`. Seq 4 consumes an
   already-scaled rate and must not re-round it; it only sets the scale of the computed quote amount.

### Seq 4 — REQ-14/REQ-17/REQ-18 upgraded from "contributes to" to fully closed — 2026-09-20

Follow-on hardening after Seq 4's initial merge (idempotency-key-reuse conflict detection
— `IdempotencyKeyConflictException` / `IDEMPOTENCY_KEY_CONFLICT` / 409 — plus `@Size` validation on the
`Idempotency-Key` header, and the `@Version` removal from `BalanceEntity` in favour of the already-sole
pessimistic-locking strategy) closes out what was previously only partial coverage:

- **REQ-14** (validation): `CurrencyValidator` checks real ISO-4217 validity via `Currency.getInstance`
  (not just regex shape), `ConversionRequest` carries `@Positive`/`@Digits` (bounds derived from
  `EntityConstant.MONEY_PRECISION`/`MONEY_SCALE`, not hand-picked), `@NotBlank` on `clientId`, and
  `@Size` on the idempotency key. Moved `Open → Done`.
- **REQ-17** (unit + integration coverage): `ConversionServiceTest`, `ConversionProcessorTest`,
  `CurrencyValidatorTest`, mapper tests, and `ConversionIntegrationTest` (18 cases against a real Spring
  context + Testcontainers Postgres) are in place. Moved `Open → Done`.
- **REQ-18** (idempotency replay / insufficient funds / happy-path explicitly tested): each has a named
  test at both the unit and full-stack level, plus concurrency variants in
  `ConversionConcurrencyIntegrationTest`. Moved `Open → Done`.

REQ-16 is **not** touched by this note — it remains the formal responsibility of Seq 10 (still "Not
planned"), even though the SpringDoc annotations already sitting on all three controllers substantially
satisfy it in practice. Leaving that call to a future @planner pass rather than reassigning Seq
ownership unilaterally here.

**Update, same day, explicit user confirmation:** REQ-15 is also moved `Open → Done`. Every exception
handler in `ForeignExchangeControllerAdvice` (11 distinct `ErrorCode`s as of this session, including
`FIELD_ERROR` and `IDEMPOTENCY_KEY_CONFLICT`) returns the same `ErrorResponse` shape
(`code`/`message`/`status`/`path`) via one shared `buildErrorResponse` helper, and every exception is
only `log.error`'d server-side — none of it reaches the response body. Seq 9 remains "Not planned" as a
standalone story (it would otherwise cover request validation too, which REQ-14 already closes), but the
`@ControllerAdvice` half of its scope is done in practice.

### Seq 9 and Seq 10 closed — 2026-09-21 (explicit user decision)

**Seq 9 → Done** without a story file: both of its requirements (REQ-14, REQ-15) were already closed by
the Seq 4 hardening notes above, so the row was pure bookkeeping. No code was written for it.

**Seq 10 → Done, REQ-16 `Open → Done`**, closed by a review-and-polish pass rather than a full story:

- SpringDoc was already wired (dependency bumped `3.1.0 → 3.1.1` on 2026-09-20 to fix the
  `cloneViaJson` WARN regression on constrained parameters — springdoc issue #3314); `/swagger-ui.html`
  and `/v3/api-docs` verified serving all four endpoints with parameters, constraints and examples.
- Stale-annotation review across all controllers. Findings, all in `ClientController`: the 404/500
  responses advertised `application/json` while the advice always returns `application/problem+json`,
  and two example messages were missing the trailing period the advice's message constants produce.
  Fixed. `RateController` and `ConversionController` were accurate.
- Added `rest/config/OpenApiConfiguration` — an `OpenAPI` info bean (title / version / description),
  placed under `rest/` by explicit user decision (CLAUDE.md's package table names `common/config` for
  OpenAPI configuration, but no `common/config` package exists; the API-description bean is
  REST-layer-scoped, and the user chose `rest`).

Known, accepted gap (not stale data, deliberately left out of scope): the 400 responses produced by
bean validation (`FIELD_ERROR` / `VALIDATION_FAILED` / `MALFORMED_REQUEST` on bad bodies or missing
`from`/`to` params) are documented on `GET /conversions` but not on `POST /conversions` or `GET /rates`.

### Seq 12 closed — 2026-09-21 (explicit user decision, checkpoint verified)

**Seq 12 → Done.** The checkpoint's purpose was to confirm the assignment's explicitly-called-out
scenarios are asserted by name, not just implicitly covered. Verified against the suite (179 tests,
0 failures, full run 2026-09-21):

- **Idempotency replay** — unit: `convert_withReplayedIdempotencyKey_returnOriginalConversionWithoutSecondDebit`
  (+ cross-client, conflict and concurrent-duplicate variants in `ConversionServiceTest`); full-stack:
  `createConversion_withReplayedIdempotencyKey_returnOriginalConversion` and
  `...ForDifferentAmount_returnConflict` in `ConversionIntegrationTest`.
- **Insufficient funds** — unit: `ConversionServiceTest` and
  `processConversion_withInsufficientSourceBalance_throwInsufficientFundsExceptionAndPersistNothing`;
  full-stack: `createConversion_withInsufficientFunds_returnUnprocessableContentAndPersistNoConversion`.
- **Happy-path debit/credit** — full-stack:
  `createConversion_withSufficientFunds_returnConversionAndUpdatedBalances`.
- **Concurrency** — `ConversionConcurrencyIntegrationTest`: parallel requests exceeding balance persist
  only one conversion (no double-spend), parallel affordable requests debit both without a lost update,
  parallel requests sharing an idempotency key persist only one.

Honest gap, accepted: the > 80% coverage threshold in `.claude/CLAUDE.md` is not machine-verified —
there is no JaCoCo (or other coverage) plugin in `pom.xml`. Closing Seq 12 rests on the named-scenario
audit above, not on a measured percentage. Adding JaCoCo remains an optional hardening item if the
number is wanted for the README.

### Seq 11 closed — 2026-09-21 (Dockerfile + compose wiring, verified end to end)

**Seq 11 → Done; REQ-19 and REQ-20 `Open → Done`.** Files: `Dockerfile`, `docker-compose.override.yaml`,
`.dockerignore`, plus `spring.docker.compose.file: docker-compose.yaml` pinned in `application.yaml`.

**The one structural decision — override file, not one merged compose file.** The assignment needs a
plain `docker compose up` to boot app + postgres, while `./mvnw spring-boot:run` (the existing dev flow,
protected by the Backward Compatibility rule) must keep starting *only* postgres. Docker Compose merges
`docker-compose.override.yaml` automatically **only when no `--file` is given**; Spring Boot's compose
support always passes `--file` (now pinned explicitly). So: `docker-compose.yaml` stays the dev/postgres
file, the override adds the `app` service (built from `Dockerfile`, `SPRING_DATASOURCE_*` pointed at the
`postgres` service DNS name, `SPRING_DOCKER_COMPOSE_ENABLED=false` so the app never looks for a Docker
daemon inside its own container) and a `pg_isready` healthcheck that gates app start via
`depends_on: condition: service_healthy`.

**Dockerfile hygiene** (per the brief's "multi-stage build, non-root user" and the "container hygiene"
grading axis): build stage `maven:3.9-eclipse-temurin-21` with `dependency:go-offline` in its own layer
for cacheable rebuilds and `-DskipTests` (the suite needs a Docker daemon for Testcontainers, unavailable
mid-build); runtime stage `eclipse-temurin:21-jre-alpine` — JRE only, no toolchain — with a dedicated
`spring` system user/group, the jar left root-owned so the process cannot overwrite its own binary,
`-XX:MaxRAMPercentage=75.0` for container-aware heap sizing. The Maven build runs `package`, which stops
short of `verify`, so Checkstyle does not run in-image (it is enforced in dev/CI).

**Verified, both flows:** `docker compose up -d --build` → postgres healthy → app started; inside the
container `whoami` = `spring`, `GET /clients/CLIENT-001/balances` = 200 with seeded (Flyway-migrated)
balances, `/v3/api-docs` = 200. Then `./mvnw spring-boot:run` → log shows
`Using Docker Compose file ...docker-compose.yaml`, only postgres started (app container stayed exited),
local boot OK. No env vars are required for either flow — the demo credentials are baked into the compose
files; Seq 13's README must document them (and may present overriding them as optional).

Remaining open: Seq 13 / REQ-21 (README) only.

### Seq 13 closed — 2026-09-21 (README written and fact-checked)

**Seq 13 → Done; REQ-21 `Open → Done`.** `README.md` (repo root, 309 lines) written via the tech-writer
agent and fact-checked line by line against the code: run instructions for both flows (compose /
`mvnw spring-boot:run`) with the exact commands verified end-to-end against a fresh `git clone` from
GitHub (seeded balances 200, live `POST /conversions` 201 from inside the container); demo-client table
matches `V2`; the pessimistic-locking section quotes the real `BalanceRepository` lock and the
fixed-currency-order double-lock rationale, with the `@Version` (dropped in `V5`) and single-writer
alternatives argued; idempotency documents the pre-check → partial unique index →
`DataIntegrityViolationException` recovery chain; caching documents pure-TTL expiry as the invalidation
choice; the trade-offs section records the Spring Cloud 4.0.8-vs-Boot 4.1.1 skew, the source/target vs
base/quote wire asymmetry, SAME_CURRENCY rejection, the no-JaCoCo gap, in-image test skipping and the
unpinned `postgres:latest`; "What's next" lists five prioritised items. Every requirement row in
`BACKLOG.md` is now Done. Remaining outside the tracker: commit `README.md` + these backlog updates,
and merge the branch.

### Post-close hardening: JaCoCo gate + Postgres pin — 2026-09-21 (explicit user decision)

Two items promoted out of the README's "What's next" into done work:

- **JaCoCo wired and enforcing**: `jacoco-maven-plugin` 0.8.13 in `pom.xml` (prepare-agent / report /
  check at `verify`), gating the build at ≥ 80% **line** coverage (`jacoco.minimum-line-coverage`
  property). New `lombok.config` sets `lombok.addLombokGeneratedAnnotation = true` so Lombok-generated
  bytecode is excluded from measurement. `mvn clean verify`: BUILD SUCCESS, measured **96.2% line
  coverage (535/556)**. Correction to the Seq 12/13 notes above: the "179 tests" figure was an artifact
  of summing stale surefire report files across runs — a clean run counts **158 tests**, all green.
- **Postgres pinned** `postgres:latest → postgres:18` in `docker-compose.yaml` (18.6 is what every
  verified run actually used); compose boot re-verified on the pinned tag (postgres healthy, app 200).
- Seed change from the same session: `V2` now also seeds `CLIENT-002` with `3000.0000 CHF` (verified:
  fresh boot, GBP→CHF conversion 201). Editing applied migration V2 breaks Flyway validation on any
  pre-existing database volume (checksum mismatch) — remedied locally via `docker compose down -v`;
  README carries a troubleshooting line. Future seed changes after this push should be new migrations.
- README "What's next" re-ordered accordingly and extended with: rate limiting per client/IP; a
  Resilience4j retry evaluation for transient provider failures; transaction/query timeout handling
  starting with `@Transactional(timeout)` on `ConversionProcessor` and generalising (possibly via an
  aspect) with dedicated exception handling.
