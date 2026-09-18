# Foreign-Exchange Service — Development Workflow

---

## Agent Roles

| Agent | Status | Responsibility |
|---|---|---|
| `@planner` | Active | Reads the assignment requirement tracker, discovers codebase patterns, breaks features into sprint-ready TDD tasks, maintains `BACKLOG.md` and `PRIORITY.md` |
| `@architect` | Active | Reviews architecture, concurrency/idempotency strategy, security, layer boundaries, JPA schema decisions; appends to `PRIORITY.md` re-prioritisation notes |
| `@coder` | Active | Implements tasks strictly following TDD (Red → Green → Refactor), existing patterns and the backend checklist below |
| `@reviewer` | Active | Performs code review against the correctness checklist and project patterns before PR merge |
| `@tester` | Active | Writes and fills missing tests after coder; verifies assignment requirement coverage at the correct pyramid level |
| `@tech-writer` | Active | Owns the graded README and maintains `docs/` |

---

## Development Cycle

There is no Epic layer — the task is small enough that a flat story list is the right amount of
structure. One story = one row in `backlog/PRIORITY.md` = one story file = one TDD implementation pass.

```
@planner proposes a Story    User approves    @planner writes the story file
                          →                →   backlog/stories/open/{seq}-{title}.story.md
                                                             │
                                                   @coder implements it
                                                   (open/ → in-progress/)
                                                             │
                                                   @reviewer + @architect review
                                                             │
                                                   commit → push → open a PR (gh pr create)
                                                             │
                                                   Mark Done → in-progress/ → done/
```

### Step 1 — Plan a Story (@planner)

Invoke `@planner` with a feature description, or ask it to pick the next unplanned Seq from `PRIORITY.md`.

`@planner` will:
1. Read `backlog/BACKLOG.md` — confirm which requirement(s) are being closed
2. Read `PRIORITY.md` — confirm sequencing and blockers are satisfied
3. Discover existing patterns in the codebase
4. **Propose** the story — no file written yet
5. Wait for user approval, then write `backlog/stories/open/{seq}-{short-title}.story.md`
6. Update `PRIORITY.md` Status → `In Progress`

### Step 2 — Implement the Story (@coder)

Move the story file `open/ → in-progress/` when starting, set `Status: In Progress`.

Create a feature branch if one for this story doesn't already exist:
```bash
git checkout -b feature/{seq}-{short-title}
```

Follow TDD strictly:
```
Red:     write the failing test first  →  mvn test  (must FAIL)
Green:   write minimum code to pass    →  mvn test  (must PASS)
Refactor: improve readability          →  mvn test  (must PASS)
```

### Step 3 — Review (@reviewer + @architect)

Invoke `@reviewer` on the completed implementation.
`@reviewer` produces a structured Review Report (BLOCKED / REQUEST CHANGES / APPROVED).
Invoke `@architect` for concurrency/idempotency correctness, layer boundaries, and security sign-off.
If any BLOCKING or HIGH issues are found, fix them and re-review before proceeding.

### Step 4 — Commit and Open a PR

After `@reviewer` issues APPROVED or APPROVED WITH COMMENTS verdict:
```bash
git add <files>
git commit -m "feat(scope): short description"
git push -u origin feature/{seq}-{short-title}
gh pr create --title "..." --body "..."
```

### Step 5 — User Validates and Merges (manual)

The user reviews the PR, resolves any remaining comments, and merges it.
**No automation here** — the merge decision is always made by a human.

### Step 6 — Close the Story

After the PR is merged:
1. Set `Status: Done` in the story file, move it `in-progress/ → done/`
2. Update `BACKLOG.md` requirement row(s) → Done
3. Update `PRIORITY.md` Seq Status → Done

---

## Story Lifecycle Rules

| Event | Action |
|---|---|
| Implementation complete, tests green | Story ready for review — invoke `@reviewer` |
| Review APPROVED | Commit, push, open the PR |
| Any BLOCKING review issue | Fix before opening the PR — do NOT open a PR on a blocked story |
| PR merged | Set `Status: Done`, move the story file `in-progress/ → done/`, update `BACKLOG.md`/`PRIORITY.md` |

---

## Package Placement Rules

This is a **single-module** Spring Boot project. All code lives under `zetta.foreignexchange`.
Package structure is **layer-first** — one bounded context, no domain subpackaging.

| What you are writing | Package |
|---|---|
| `@RestController` | `rest.controller` |
| `@ControllerAdvice` (exception → HTTP) | `rest.controlleradvice` |
| Request/response DTO | `rest.dto` |
| MapStruct mapper (DTO ↔ domain) | `rest.mapper` |
| Service interface + package-private impl | `core.service` |
| Domain model (record, enum) | `core.model` |
| Domain exception | `core.exception` |
| JPA `@Entity` | `persistence.entity` |
| MapStruct mapper (domain ↔ entity) | `persistence.mapper` |
| Spring Data JPA repository | `persistence.repository` |
| `@Configuration` bean class | `common.config` |
| `@ConfigurationProperties` record | `common.properties` |
| External provider client | `common.integrations.{provider}` |
| Unit test | mirrors main package, suffix `Test` |
| Integration test | mirrors main package, suffix `IntegrationTest` |

**Layer rules:**
- `rest` → `core`; `core` → `persistence`, `common`
- `common` is a **peer** of `persistence`, not something persistence routes through — it holds
  external-provider clients and shared utilities that `core` calls directly. `persistence` depends on
  neither `core` nor `common`.
- No reverse dependencies; `rest` never depends on `persistence` directly — always through a `core` service
- `rest` controllers delegate immediately to `core` services — zero business logic in controllers
- Service interfaces are `public`; implementation classes are **package-private**

---

## Backend Implementation Checklist

Apply before declaring any task done:

### Code Style
- [ ] Google Java Format applied — `mvn checkstyle:check` passes, but if the 125-character limit is unreached, code can stay on one line for readability
- [ ] Explicit types — no `var` for non-obvious types
- [ ] Test naming: `methodName_condition_expectedOutcome` — no `should` prefix, no `given` prefix

### Spring Patterns
- [ ] `@ConfigurationProperties` records for all config — never `@Value`
- [ ] Service interface is `public`; Impl class is package-private
- [ ] MapStruct mapper at every layer boundary (REST ↔ domain, domain ↔ entity)
- [ ] JPA `@Entity` classes named with `Entity` suffix (e.g. `ClientBalanceEntity`)
- [ ] Entities never returned by service public methods — always map to domain records first
- [ ] No business logic in `@RestController` — delegate to service immediately

### Assignment Correctness
- [ ] Money as `BigDecimal` with explicit scale and rounding mode — never `double`/`float`
- [ ] Debit + credit + conversion record write in the same database transaction
- [ ] Insufficient funds → 422 `INSUFFICIENT_FUNDS`, no record persisted
- [ ] Unknown client/currency → 404 with the correct distinct code
- [ ] Chosen concurrency strategy applied on every code path that mutates a balance
- [ ] `Idempotency-Key` replay never double-debits or duplicates a record
- [ ] External provider calls are timeout-bound and fail gracefully — never a raw stack trace to the caller
- [ ] Rate cache TTL actually short-circuits repeated provider calls
- [ ] Input validated at the REST boundary — not repeated redundantly in core/persistence
- [ ] No credential/secret values written to logs

### Security
- [ ] No authentication machinery added (out of scope per the assignment)
- [ ] External provider URL comes from `@ConfigurationProperties` — never user-supplied

---

## Story Points

| Points | Effort | Typical scope |
|---|---|---|
| 1 | 20 minutes | Single validation rule or minor tweak |
| 2 | 20-40 minutes | New service method with unit tests |
| 3 | 40-60 minutes | New service method + mapper + unit tests |
| 5 | 60-100 minutes | New endpoint + service + persistence + tests |
| 8 | 100-150 minutes | Full vertical slice (domain + service + REST + persistence + integration test) |

Split any task above 8 points. Remember the assignment's own target is ~7 hours total — most stories
here should land at 1–5 points.

---

## PR Rules

### Title — Conventional Commits

```
type[(scope)]: short description (max 72 chars)
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

Examples:
```
feat(conversion): add POST /conversions happy path with atomic debit/credit
feat(conversion): apply pessimistic row lock to prevent double-spend
feat(rates): cache exchange rates with a configurable TTL
fix(conversion): make idempotency-key replay return the original result
test(conversion): cover insufficient-funds and idempotency replay paths
docs: write README trade-offs section
```

### Body — Three Sections (mandatory)

```
What: [What changed — 1-3 sentences describing the change, not the implementation details]

Why: [Which assignment requirement this closes, with a reference to the brief]

Tested: [Specific test classes or scenarios verified: unit tests, integration tests, manual check if applicable]
```

### Commit Format

```
type[(scope)]: short description

[optional body — explain WHY and which requirement, not WHAT]

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## References

- Assignment brief: `.claude/Java-Assignment.pdf`
- Project rules: `.claude/CLAUDE.md`
