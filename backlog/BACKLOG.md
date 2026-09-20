# Foreign-Exchange Service — Backlog

## Mission

Ship a correct, well-tested foreign-exchange conversion service that satisfies every requirement in
the take-home brief, within the simplest structure that does the job.

Brief: `.claude/Java-Assignment.pdf`

---

## Assignment Requirement Tracker

Current implementation status. Updated as features are completed.

> Referenced elsewhere as `REQ-N` (not `#N`) — kept distinct from `PRIORITY.md`'s `Seq` numbers, which
> are a different, unrelated sequence.

| Req | Requirement | Area | Priority | Status |
|---|---|---|---|--------|
| REQ-1 | `GET /rates?from=&to=` returns the current exchange rate | Endpoint | Critical | Done |
| REQ-2 | `POST /conversions` converts an amount for a client; returns `transactionId`, amounts, rate, timestamp, updated balances | Endpoint | Critical | Done |
| REQ-3 | `GET /conversions` — paginated history filtered by `transactionId`/`date`/`clientId` (≥ 1 filter required) | Endpoint | Critical | Done |
| REQ-4 | `GET /clients/{clientId}/balances` returns balances per currency held | Endpoint | Critical | Done |
| REQ-5 | Demo clients seeded via Flyway (`CLIENT-001`, `CLIENT-002`) with documented starting balances | Data | Critical | Done |
| REQ-6 | Debit + credit + conversion record are atomic — one DB transaction | Correctness | Critical | Done |
| REQ-7 | Insufficient funds → HTTP 422 `INSUFFICIENT_FUNDS`, no conversion record persisted | Correctness | Critical | Done |
| REQ-8 | Unknown client / unknown currency-for-client → HTTP 404 `CLIENT_NOT_FOUND` / `BALANCE_NOT_FOUND` | Correctness | Critical | Done |
| REQ-9 | Concurrency: two simultaneous conversions for the same client never double-spend | Correctness | Critical | Done |
| REQ-10 | `Idempotency-Key` header: replay returns the original result, no duplicate, no double-debit | Correctness | Critical | Done |
| REQ-11 | Money modeled as `BigDecimal` with explicit scale and rounding mode | Correctness | Critical | Done |
| REQ-12 | External rate-provider integration with timeout and graceful failure | Production | High | Done |
| REQ-13 | Rate caching with a TTL, invalidation choice documented | Production | High | Done |
| REQ-14 | Validation: ISO-4217 currency codes, positive amounts, sane bounds, non-blank client id | Production | High | Done |
| REQ-15 | `@ControllerAdvice` returns consistent error bodies with distinct codes, never raw stack traces | Production | High | Done |
| REQ-16 | OpenAPI / Swagger UI auto-generated (SpringDoc) | Production | Medium | Open |
| REQ-17 | Unit tests for conversion logic + at least one integration test through the Spring context | Testing | Critical | Done |
| REQ-18 | Idempotency replay, insufficient funds, and happy-path debit/credit explicitly tested | Testing | Critical | Done |
| REQ-19 | `docker compose up` boots the service with no manual setup beyond documented env vars | Production | High | Open |
| REQ-20 | Dockerfile — multi-stage build, non-root user | Production | Medium | Open |
| REQ-21 | README explains how to run it, trade-offs, the concurrency choice, and what's next | Communication | Critical | Open |

---

## Non-Goals (do not plan work here — per the assignment)

- Authentication / OAuth — `clientId` is caller-supplied, full stop
- UI or frontend
- Kubernetes manifests, Helm, CI/CD pipelines
- Microservice splitting, event sourcing, CQRS
- A full ledger / journal-entry model — one balance row per client × currency is enough
- Deposit/withdraw endpoints (seeding via Flyway is sufficient; adding one is fine only if it helps testing)
- Inventing or stacking design patterns beyond what a requirement needs

---

## Stories

Stories are planned using `@planner`, one per row in `backlog/PRIORITY.md`. There is no Epic layer —
this task is small enough that a flat story list is the right amount of structure. Story files live
under `backlog/stories/open/`, `backlog/stories/in-progress/`, or `backlog/stories/done/`, named
`{seq}-{short-title}.story.md` using `backlog/templates/story.template.md`.

Story status and sequencing are tracked in `backlog/PRIORITY.md` — that table is the single source of
truth for what's planned, in progress, or done; this file does not duplicate it.

---

## Notes

Architecture decisions, spike results, and trade-off research live in `backlog/notes/`.

| File | Topic |
|---|---|
| — | — |
