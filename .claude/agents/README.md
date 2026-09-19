# Foreign-Exchange Service — Agent Team

Specialized agents for the foreign-exchange take-home assignment (Spring Boot + Java 21 + JPA/PostgreSQL).
Full brief: `.claude/Java-Assignment.pdf`.

---

## Team Overview

| Agent | Model  | Role | When to Use |
|-------|--------|------|-------------|
| `@planner` | opus   | Agile Technical Lead — breaks the assignment brief into sprint-ready TDD tasks | Before any implementation: turn a requirement or feature idea into sprint-ready tasks |
| `@architect` | opus   | Architecture & Security Lead — REST API design, JPA schema, concurrency/idempotency strategy | On-demand: complex feature design, locking strategy, schema decisions, ADR authorship |
| `@coder` | sonnet | Senior Java Engineer — TDD implementation, Spring Boot patterns, JPA persistence | All implementation tasks |
| `@tester` | sonnet | Senior QA Engineer — money-movement test scenarios, concurrency/idempotency coverage | Writing tests at the correct pyramid level; asserting assignment requirements as executable specs |
| `@reviewer` | sonnet | Senior Code Reviewer — correctness, checkstyle compliance, TDD discipline | Before any PR is merged: full code review with structured report (issues, severity, required fixes) |
| `@tech-writer` | sonnet | Technical Writer — README trade-off write-up, architecture docs, developer guides | Documentation: the graded README, system design, setup guides |

---

## Agent Capabilities

### @planner

- Reads the assignment requirement tracker in `backlog/BACKLOG.md` to understand what's done before planning anything
- Discovers existing codebase patterns (services, entities, mappers, controllers) before proposing any file location or structure
- Maps every story to the exact assignment requirement it satisfies — no story is created without justification
- Produces: one story file per `backlog/PRIORITY.md` Seq row, at `backlog/stories/open/{seq}-{short-title}.story.md` using `backlog/templates/story.template.md` — no Epic layer, the story file is the whole task
- Each story contains: requirement context, story points (Fibonacci 1–8), exact files to create/modify, existing pattern references, and explicit TDD Red→Green→Refactor steps
- Updates `backlog/PRIORITY.md` (Status → In Progress) as part of planning output

### @architect

- Reviews layer boundaries and package placement against the project's layered structure rules
- OWASP Top 10 security review adapted for a caller-supplied-identity REST API with money movement
- Signs off on: the concurrency strategy, the idempotency approach, new external integrations, schema/index changes
- Authors ADRs in `docs/adr/` for significant decisions
- Checks: money as `BigDecimal` with explicit scale, atomic debit/credit, no double-spend, no secrets in code

### @coder

- Self-learning first: reads an existing similar class before writing anything new
- Strict TDD: failing test → minimum implementation → refactor
- Follows exact project patterns: service interface + package-private Impl, MapStruct at every layer boundary, `@ConfigurationProperties` (never `@Value`)
- Code must pass `mvn checkstyle:check` before declaring done
- Never uses `double`/`float` for money — always `BigDecimal` with explicit scale and rounding

### @tester

- Writes tests before any implementation — the test must fail first (TDD red phase)
- Chooses the correct test pyramid level: unit (`@ExtendWith(MockitoExtension.class)`) / controller slice (`@WebMvcTest`) / integration (`@SpringBootTest` + Testcontainers PostgreSQL)
- Maps every assignment requirement to at least one executable test assertion
- Test naming: `methodName_condition_expectedOutcome()` — no `should_` or `given_` prefixes
- Test body structure: `// when` → `// then` → `// verify` (omit `// verify` when there are no meaningful mock interactions)
- Covers: happy path, null/missing inputs, insufficient funds, unknown client/currency, concurrency, idempotency replay
- Runs `mvn test` and `mvn checkstyle:check` before declaring done

### @reviewer

- Checks `backlog/BACKLOG.md` before reviewing to distinguish pre-existing tracked gaps from new regressions — never blocks a PR for a known tracked gap
- Enforces business-rule correctness: atomic debit/credit, insufficient-funds handling, concurrency safety, idempotency replay
- Enforces all checkstyle rules: `ReturnCount` (max 3), `CyclomaticComplexity` (max 10), `ParameterNumber`, `MethodLength`, nesting depth (max 2), `AnnotationLocation`, line length (max 125) — note: `final` on parameters/locals is deliberately not enforced (dropped for the time budget)
- Validates REST API contract discipline: `@Valid` on all endpoints, MapStruct at every layer boundary, consistent error-response shape
- Verifies TDD discipline: test written before implementation, `methodName_condition_expectedOutcome` naming, Given/When/Then structure, Instancio for unit test data
- Produces a structured **Review Report** with issues table (severity: BLOCKING / HIGH / MEDIUM / LOW), positive observations, and action required checklist
- Verdict: `BLOCKED` (any BLOCKING issue) → `REQUEST CHANGES` (any HIGH) → `APPROVED WITH COMMENTS` (MEDIUM/LOW only) → `APPROVED`

### @tech-writer

- Reads codebase before writing — self-learning process is mandatory (read existing `docs/` and `README.md`, discover source files, match tone)
- Owns the assignment-mandated README: how to run it, demo clients, trade-offs (especially the concurrency choice), idempotency approach, caching invalidation, what's next with more time
- 5 permanent documentation types: architecture, code explanation, developer guide, project overview, README
- 3 ephemeral types: feature docs, PR explanations, bug fix explanations
- Maps trade-offs to their reasoning, and explanations to code with `file_path:line_number` references
- Permanent docs → `docs/` (lasting value); ephemeral explanations → `.claude/tmp/`
- File naming: Title-Case-Hyphens (e.g., `Concurrency-And-Idempotency.md`, `Developer-Setup-Guide.md`)
- Invoked via `/document` skill: `/document readme`, `/document architecture`, `/document code ConversionService`, `/document guide testing`

---

## When to Call Which Agent

```
"Plan this feature / requirement"                         → @planner
"What's left from the assignment brief?"                  → @planner
"Break this feature into TDD tasks"                        → @planner
"Is this approach architecturally sound?"                  → @architect
"Which concurrency strategy should we use?"                → @architect
"Review this for security"                                 → @architect
"Add the conversion endpoint"                               → @planner (tasks) → @coder (implement) → @tester (tests) → @architect (sign off)
"Implement this service / controller / mapper / task"      → @coder
"How should I model this table / index / lock?"            → @architect
"Write a unit test for this service"                       → @tester
"Write tests for this feature"                              → @tester
"Which scenarios are missing test coverage?"                → @tester
"Review this PR / branch before merge"                      → @reviewer
"Does this code pass checkstyle and correctness rules?"     → @reviewer
"Is the TDD discipline correct in this implementation?"     → @reviewer
"Write the README / trade-offs section"                     → @tech-writer
"Document the system architecture"                          → @tech-writer
"Explain how the conversion flow works"                      → @tech-writer
"Write a developer setup guide"                              → @tech-writer
"Explain this class / service / feature"                    → @tech-writer
"Explain this PR / bug fix"                                  → @tech-writer
```

---

## Foreign-Exchange Flow Reference

```
Caller
    │
    ▼
GET  /rates?from=USD&to=EUR        ← current rate, cached with a TTL
    │
POST /conversions                  ← X-Client-Id header, optional Idempotency-Key header
    │ 1. resolve rate (RateService, provider call outside any DB transaction)
    │ 2. lock/read source balance (chosen concurrency strategy)
    │ 3. validate: client exists, currency held, sufficient funds
    │ 4. debit source, credit target, persist conversion record — one transaction
    │ 5. return transactionId, amounts, rate, timestamp, updated balances
    │
GET  /conversions?transactionId=&date=&clientId=&page=&size=  ← paginated history
    │
GET  /clients/{clientId}/balances  ← current balances, one row per currency
```

---

## Key Rules (All Agents Enforce These)

- **TDD always**: red → green → refactor. No implementation before a failing test.
- **Package discipline**: layer-first (`core/`, `persistence/`, `rest/`, `common/`) — single bounded context, no domain subpackaging.
- **Layer boundaries**: `rest → core`; `core → persistence, common`. `common` is a peer of `persistence` (external-provider clients + shared utilities, consumed by `core`), not something persistence depends on. No reverse dependencies; `rest` never touches `persistence` directly.
- **Service visibility**: interface `public`, Impl class **package-private**.
- **No `@Value`**: all config via `@ConfigurationProperties` **classes** (mutable, setter-bound — never
  records) in `common/properties/` or beside the integration they configure.
- **JPA + Flyway**: never `ddl-auto=update` in the production profile.
- **Money**: `BigDecimal` with explicit scale and rounding mode — never `double`/`float`.
- **No auth**: `clientId` is caller-supplied per the assignment spec — do not add auth machinery.
- **No secrets in source control.**
