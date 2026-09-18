# CLAUDE.md

## Project Overview

This service implements the **foreign-exchange take-home assignment** (see `.claude/Java-Assignment.pdf`
for the full brief). It is a Spring Boot service, built with Java 21 as a Maven-based web application, that:

- Fetches live exchange rates from a public provider (Frankfurter) and caches them with a TTL
- Converts amounts for a client against per-client account balances, debiting the source currency and
  crediting the target currency atomically
- Persists a history of conversions for later lookup
- Exposes:
  - `GET /rates?from=&to=` — current exchange rate between two currencies
  - `POST /conversions` — convert an amount for a client (client id via `X-Client-Id` header)
  - `GET /conversions?transactionId=&date=&clientId=&page=&size=` — paginated, filtered history
  - `GET /clients/{clientId}/balances` — a client's current balances

There is **no authentication** in this service, by design — the client identifier is supplied by the
caller. Do not add auth machinery; it is an explicit non-goal of the assignment.

### REST API

This is a self-contained service — REST endpoints, request/response models, and error bodies are defined
and implemented directly in this codebase. There is no external API-definition repository and no code
generation step. OpenAPI documentation is auto-generated from the controllers via SpringDoc
(`springdoc-openapi-starter-webmvc-ui`, exposed under `/swagger-ui.html`). Controller signatures and
`@Schema`/validation annotations are the source of truth for the generated spec — keep them accurate.

## Code Quality

### Formatting (Spotless + Google Java Format)
- Style: **Google Java Format**
- Enforced via `spotless-maven-plugin` (runs on `verify`)
- Fix formatting: `mvn spotless:apply`
- Check only: `mvn spotless:check`
- Import order: `,javax,java` — default group (com.*, org.*, lombok, etc.) first, then javax, then java
- Static imports go at the very top, separated by a blank line from regular imports
- Imports within each group are alphabetical

### Checkstyle (`checkstyle.xml`)

> **MANDATORY**: Run `mvn checkstyle:check` before every commit. Do NOT commit code with checkstyle violations.

- **Code generation rule**: when writing Java code, apply the **all-or-one** wrapping rule for parameter/argument lists:
  - If ALL parameters fit on a single line within 125 chars → keep them on one line.
  - If they do NOT all fit → put EACH parameter on its own line (one per line).
  - Never mix: some params on the same line and others on the next line.
- Check: `mvn checkstyle:check`
- No magic numbers — use `private static final` named constants
- No duplicate string literals — use constants
- Max line length: 125 chars; method length: 40 lines; 4 params per method, 7 per constructor
- No `final` requirement on method/constructor parameters or local variables — dropped deliberately
  to keep implementation friction low for the assignment's time budget; `ParameterAssignment` still
  forbids reassigning a parameter, so accidental mutation is still caught
- `static final` fields: `UPPER_SNAKE_CASE`
- Declaration order: static fields → instance fields → constructors → methods

### TDD
Development is following strictly the TDD (Test Driven Development) methodology with ALWAYS doing the
red - green - refactor cycle for any piece of new code.

In the cycle above:
Red - means write a test and ensures it fails.
Green - write code that makes the test pass.
Refactor - make changes to improve readability and quality.

### Package Structure
Use mapstruct to define mappers for structs between layers.

### KISS
Keep it Simple Stupid - This is a principle enforced always.
Try to fit only the requirements provided, do not overdesign solutions.
Always try to achieve a working solution with the least amount of code possible.

> **KISS does not justify removing or replacing existing features.** Simplification applies to
> new code only. Existing behavior is governed by the Backward Compatibility rule below.

### Backward Compatibility (MANDATORY)

Agents MUST NOT remove, break, or change existing features unless explicitly instructed by the user.

This includes:
- Supported endpoints and response shapes
- Existing APIs and contracts
- Current flows and integrations

When introducing new features:
- They MUST be added alongside existing ones
- Existing behavior MUST remain unchanged
- Feature support MUST be additive, not replacing

Any change that removes a feature, alters existing behaviour, or breaks compatibility
MUST be explicitly approved by the user.

If unsure → STOP and ask.

### Record vs Class

Use a **record** when:
- The data is strictly immutable
- No state changes are expected now or in the future

Use a **class** when:
- There is any possibility of future mutability
- The object may evolve to include behavior, state changes, or lifecycle logic
- The design is uncertain or expected to change

**Guideline:** Prefer `record` by default when immutability is guaranteed and stable over time. If there is any doubt, use a class.

### Single Responsibility Principle
Classes and methods should have a very clear responsibility. Encapsulation is very important.
Try to always use the minimal needed visibility level for classes and methods.

### External Dependencies Usage
Try to minimize the need for external dependencies. In order to determine if an external dependency is needed, use the
following criteria:
- is a functionality that can be isolated and hidden behind an interface
- is extremely complex and not worth 'inventing the wheel' on our own
- is a dependency with a strong support and active community

### Design Patterns
Follow standard Gang of Four patterns — but apply one only where it earns its keep. The assignment
explicitly calls out inventing or stacking design patterns as a non-goal: a plain service +
repository + mapper structure is the default here, not a placeholder for a future pattern.

## Database
When designing a database schema for a functionality consider:
- make database migrations idempotent and backward compatible - always
- consider accessibility patterns with composite indices (e.g. `clientId`+`currency`, `transactionId`, `date`)
- do not open transactions before external calls (e.g. never hold a DB transaction open while calling
  the rate provider)
- use optimistic locking (`@Version`) or a pessimistic row lock for concurrent balance updates on the
  same client — pick one strategy and justify the choice (see the assignment's concurrency requirement)
- have idempotency keys for critical operations (`POST /conversions` accepts an `Idempotency-Key` header)

## Architecture

### Technology Stack
- **Framework**: Spring Boot (see `pom.xml` for the exact version in use)
- **Java Version**: 21
- **Build Tool**: Maven
- **Web Framework**: Spring Web MVC (REST capabilities)
- **Persistence**: Spring Data JPA, Flyway migrations, PostgreSQL (Testcontainers for integration tests)
- **External integration**: Frankfurter rate provider (see `common/integrations/frankfurter`)
- **API docs**: SpringDoc OpenAPI / Swagger UI

### Package Structure

Base package: `zetta.foreignexchange` — a single bounded context, **layer-first** (no domain
subpackaging). This is a small, cohesive service with one responsibility, not a multi-domain platform —
splitting by domain first would add indirection the assignment does not need.

```
src/
├── main/java/zetta/foreignexchange/
│   ├── core/
│   │   ├── model/          <- Domain records/enums (Money, ConversionResult, ErrorCode, ...)
│   │   ├── service/        <- Service interfaces + package-private impls (RateService, ConversionService, BalanceService)
│   │   └── exception/      <- Domain exceptions (InsufficientFundsException, ClientNotFoundException, BalanceNotFoundException, ...)
│   ├── persistence/
│   │   ├── entity/         <- JPA @Entity classes (ClientBalanceEntity, ConversionEntity)
│   │   ├── repository/     <- Spring Data JPA repositories
│   │   └── mapper/         <- MapStruct entity <-> domain mappers
│   ├── rest/
│   │   ├── controller/     <- @RestController (RateController, ConversionController, ClientController)
│   │   ├── dto/            <- Request/response records
│   │   ├── mapper/         <- MapStruct DTO <-> domain mappers
│   │   └── controlleradvice/ <- @RestControllerAdvice + error response model
│   └── common/
│       ├── config/         <- @Configuration (caching, HTTP client, OpenAPI)
│       ├── properties/     <- @ConfigurationProperties records
│       └── integrations/
│           └── frankfurter/ <- external rate provider client + response mapping
└── test/java/zetta/foreignexchange/
    ├── core/                <- Service unit tests
    ├── persistence/         <- Mapper / repository tests
    ├── rest/                <- Controller slice tests
    ├── integration/         <- Full-stack tests: BaseIntegrationTestSetUp, Testcontainers config
    └── tools/               <- Test data builders
```

### Layer Placement Rule

Place code by **layer**, not by feature — this project is one bounded context:

- New `@RestController` → `rest/controller`
- New service interface + package-private impl → `core/service`
- New domain model (record/enum) → `core/model`
- New domain exception → `core/exception`
- New JPA `@Entity` → `persistence/entity`
- New Spring Data repository → `persistence/repository`
- New MapStruct mapper → `persistence/mapper` (entity ↔ domain) or `rest/mapper` (domain ↔ DTO)
- New `@ConfigurationProperties` → `common/properties`
- New `@Configuration` bean → `common/config`
- New external provider client → `common/integrations/{provider}`

**Layer dependency rule:** `rest → core`; `core → persistence, common`; `persistence` and `common` depend
on nothing else in the platform. `common` is a peer of `persistence` — not something persistence routes
through — it holds external-provider clients and shared utilities that `core` calls directly.
No reverse dependencies; `rest` never touches `persistence` directly (always through a `core` service);
no business logic in `rest/` (controllers validate → map → call a service → return).

## Testing Requirements
- All PRs require passing tests
- Coverage threshold: > 80%
- Integration tests for all API endpoints

### Method naming
Every non-void method must start with a verb. This applies to both production and test code.
- Factory/builder helpers: `build*` (e.g. `buildClientBalance`, `buildConversionRequest`)
- Generator helpers: `generate*` (e.g. `generateTransactionId`)
- Pure computations: `compute*`, `parse*`, `create*`, `make*` — whichever verb best describes the action
- Never name a method as a noun alone (e.g. `convertedAmount`, `clientBalance`) — a reader cannot tell from the name alone that the method constructs something rather than accessing a field

### Spec-Aligned Variable Names

Every variable, field, and lambda parameter name must precisely describe **what it holds**, using the
terminology of the assignment brief (`.claude/Java-Assignment.pdf`) — the same vocabulary the endpoints
and error codes use.

- **No generic accumulator names**
- **Lambda parameters must be named** — `ignored`, `it`, `e`, `s`, `k`, `v` are banned.
- **Collections state their key and value**
- **Assignment terms take precedence over generic Java terms** — prefer `sourceCurrency`/`targetCurrency`,
  `sourceAmount`/`targetAmount`, `transactionId`, `clientId`, `idempotencyKey`, `rate` over generic
  `from`/`to`, `amount`, `id`, `key`, `value`.

### Mockito verify style
- Never use `verify(mock, times(1)).method()` — `times(1)` is Mockito's default and is redundant boilerplate
- Use plain `verify(mock).method()` for exactly-once assertions
- Only use `times(N)` when `N > 1`

## Security
- Never commit secrets
- No authentication/authorization in this service — the client identifier is caller-supplied per the
  assignment spec (see Non-goals). Do not add auth machinery.
