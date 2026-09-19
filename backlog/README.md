# Foreign-Exchange Service — Backlog

## Purpose

This folder is the single source of truth for all planning, in-flight and completed work on the
foreign-exchange take-home service. Everything here is oriented toward one goal:

> **Satisfy every requirement in the take-home assignment (`.claude/Java-Assignment.pdf`) with the
> simplest structure that does the job — correctness, code clarity, testing discipline, production
> sense, and a README that explains the trade-offs.**

---

## Folder Structure

```
backlog/
├── README.md              ← You are here
├── WORKFLOW.md            ← Full development workflow: agent roles, cycle, checklists, PR rules
├── BACKLOG.md              ← Master requirement tracker
├── PRIORITY.md             ← Authoritative implementation sequence + story status
├── stories/
│   ├── open/               ← Planned; not started
│   │   └── {seq}-{title}.story.md
│   ├── in-progress/        ← Being implemented
│   │   └── {seq}-{title}.story.md
│   └── done/                ← Merged
│       └── {seq}-{title}.story.md
├── notes/                  ← Architecture decisions, spike results, trade-off research
└── templates/
    └── story.template.md   ← Base for every story file
```

There is no Epic layer — one story per row in `PRIORITY.md`, one story file per implementation pass.

---

## Agent Roles in This Backlog

| File / Folder | Created by | Updated by | When                                  |
|---|---|---|---------------------------------------|
| `BACKLOG.md` | @planner | @planner | Requirement status changes            |
| `PRIORITY.md` | @planner | @planner + @architect | After planning or architecture review |
| `stories/open/` | @planner (Phase 2) | — | After user approves Phase 1 proposal  |
| `stories/in-progress/` | @planner / developer | @coder (story status) | When the story starts                 |
| `stories/done/` | @planner / developer | — | When the story is merged              |
| `notes/*.md` | @architect / You | @architect | After design decisions or spikes      |

---

## Workflow

### 1 — Plan a Story (@planner)
Invoke `@planner` with a feature description, or let it pick the next unplanned Seq from `PRIORITY.md`.
`@planner` proposes the story — **no file written until you approve**.
After approval, `@planner` creates `backlog/stories/open/{seq}-{short-title}.story.md`.

### 2 — Start the Story (@coder)
Move the story file: `open/ → in-progress/`. Update `Status: In Progress`.

### 3 — Implement the Story (@coder)
Follow TDD strictly: Red → Green → Refactor.

### 4 — Review (@reviewer + @architect)
`@reviewer` produces a structured Review Report (BLOCKED / REQUEST CHANGES / APPROVED).
`@architect` reviews concurrency/idempotency correctness, layer boundaries, and security.
Fix all BLOCKING and HIGH issues before proceeding.

### 5 — Commit + Open a PR + User Merges
After APPROVED verdict, commit, push, and open a PR (`gh pr create`).
User reviews and merges the PR manually.

### 6 — Close the Story
After the PR is merged, mark the story `Status: Done`, move the story file `in-progress/ → done/`,
and update `BACKLOG.md` + `PRIORITY.md`.

---

## Quality Gates

Every story must satisfy these before it moves to `stories/done/`:

- [ ] All unit tests pass — `mvn test`
- [ ] `mvn checkstyle:check` passes
- [ ] No secrets or credential values in logs
- [ ] The assignment requirement this story addresses is explicitly verified in a test assertion
- [ ] PR reviewed and merged

---

## Key Decisions

| Decision | Rationale | Status |
|---|---|---|
| Client id passed via `X-Client-Id` header (not request body) | No auth in scope; a header keeps the request body focused on the conversion itself | ⏳ To confirm |
| Concurrency strategy (pessimistic lock / `@Version` / serialized writer) | Assignment explicitly requires one strategy, chosen and justified in the README | ⏳ To decide |
| Idempotency key uniqueness enforced at the DB layer, not only in-memory | Correct under concurrent replays and multiple app instances | ⏳ To confirm |
| Frankfurter as the rate provider | Free, no API key required, simple JSON response | ✅ Chosen (see `common/integrations/frankfurter`) |
| JPA + Flyway + PostgreSQL (Testcontainers for integration tests) | Required by the assignment; `ddl-auto=update` never used in production | ✅ Active |
| Layer-first package structure (no domain subpackaging) | Single bounded context — domain-first would add indirection this assignment doesn't need | ✅ Active |

---

## References

- Assignment brief: `.claude/Java-Assignment.pdf`
- Project rules: `.claude/CLAUDE.md`
