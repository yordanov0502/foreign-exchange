# Foreign-Exchange Service — Priority Roadmap

> **Maintained by:** @planner (sequencing, status) + @architect (risk flags, blocker validation)
> **Last updated:** 2026-09-18
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
| 3 | `GET /rates` — provider integration, timeout, graceful failure, TTL cache | Critical | REQ-1, REQ-12, REQ-13 | — | Not planned |
| 4 | `POST /conversions` happy path — atomic debit/credit, response shape | Critical | REQ-2, REQ-6, REQ-11 | Seq 1, Seq 3 | Not planned |
| 5 | Insufficient funds / unknown client / unknown currency error paths | Critical | REQ-7, REQ-8 | Seq 4 | Not planned |
| 6 | Concurrency control on balance updates (no double-spend) | Critical | REQ-9 | Seq 4 | Not planned |
| 7 | `Idempotency-Key` replay handling | Critical | REQ-10 | Seq 4 | Not planned |
| 8 | `GET /conversions` — paginated, filtered history | Critical | REQ-3 | Seq 4 | Not planned |
| 9 | Global error handling (`@ControllerAdvice`) + request validation | High | REQ-14, REQ-15 | Seq 2, Seq 4 | Not planned |
| 10 | OpenAPI / Swagger UI | Medium | REQ-16 | Seq 2, 3, 4, 8 | Not planned |
| 11 | Dockerfile (multi-stage, non-root) + `docker compose up` wiring | High | REQ-19, REQ-20 | Seq 1 | Not planned |
| 12 | Test coverage hardening — explicit idempotency/insufficient-funds/happy-path/concurrency assertions | Critical | REQ-17, REQ-18 | Seq 4, 5, 6, 7 | Not planned |
| 13 | README — run instructions, trade-offs, concurrency choice, what's next | Critical | REQ-21 | All above | Not planned |

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
        +──► [4] POST /conversions happy path ◄──── [3] GET /rates + provider + cache
                    │
                    ├──► [5] Insufficient funds / not-found errors
                    ├──► [6] Concurrency control
                    ├──► [7] Idempotency-Key replay
                    └──► [8] GET /conversions history

[2]+[4] ──► [9] Global error handling + validation ──► [10] OpenAPI / Swagger UI
[1] ──► [11] Dockerfile + docker compose
[4]+[5]+[6]+[7] ──► [12] Test coverage hardening ──► [13] README
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
