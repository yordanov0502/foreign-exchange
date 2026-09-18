# [SEQ] — [Action-Oriented Title]

| Field | Value |
|---|---|
| Story Points | 1 / 2 / 3 / 5 / 8 |
| Priority | Critical / High / Medium |
| Status | Backlog / In Progress / Done |

---

## User Story

**As a** foreign-exchange service
**I want** [specific capability]
**So that** [assignment requirement fulfilled — reference the exact section of `.claude/Java-Assignment.pdf`]

---

## Context

[What is currently missing? What does this unlock when done? State current behavior vs required
behavior, and which grading axis it affects (correctness / code clarity / testing / production
sense / communication).]

---

## Scope

### In Scope
- [ ] [Concrete responsibility 1]
- [ ] [Concrete responsibility 2]


### Out of Scope
- [Explicitly excluded items to prevent scope creep — check against the assignment's Non-goals list]

---

## Acceptance Criteria

- MUST be:
    - Short
    - Specific to the story
    - Business/technical outcome-focused
- MUST NOT include generic/shared rules like:
    - Running unit tests
    - Maven commands (e.g. `mvn clean install`)
    - Code formatting (Spotless, linting, etc.)
    - "code compiles"
    - These are **implicit engineering standards**, not story-level criteria

- [ ] [Clear, testable outcome — status code, response shape, or persisted-state level]
- [ ] [Another outcome]
- [ ] [Edge case or constraint if relevant — e.g. insufficient funds, concurrent requests, replayed idempotency key]

---

## Technical Notes (optional)

### Layers Affected
- [ ] `core` — domain model, service logic, exceptions
- [ ] `persistence` — JPA entities, repositories, migrations
- [ ] `rest` — controller, DTOs, mappers, controller advice
- [ ] `common` — configuration, properties, external integrations

### Files to Create / Modify
- [Exact paths — no vague "add a service"]

### Existing Pattern Reference
- [File path + minimal code snippet this story should follow]

### TDD Steps
- **Red:** [the failing test to write first]
- **Green:** [the minimum implementation to pass it]
- **Refactor:** [what to clean up once green]

### Implementation Notes
- [Constraints, design decisions, pitfalls, or hints — e.g. which concurrency strategy, which error code]

---

## Dependencies (optional)

- **Depends On:** [None | `[SEQ]` — reason]
- **Blocks:** [None | `[SEQ]` — reason]

---

## References

- `.claude/Java-Assignment.pdf` — [section/requirement this story satisfies]
- [Other internal docs or examples]
