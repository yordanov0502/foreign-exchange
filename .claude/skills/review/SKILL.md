---
name: review
description: Run a full code review on the current branch (or a named branch) using the reviewer agent. Produces a structured report with severity-rated issues and a final verdict.
---

Review the changes in: $ARGUMENTS

If `$ARGUMENTS` is empty, review the current branch against `main`.

Use the **reviewer** sub-agent to apply the full review framework from `../../../../../../../foreign-exchange/.claude/agents`.

---

## Step 1 — Determine Scope

Resolve the branch to review:

```bash
# If $ARGUMENTS is empty, use the current branch
git rev-parse --abbrev-ref HEAD

# List all files changed compared to main
git diff --name-only main...HEAD

# Show the full diff
git diff main...HEAD
```

---

## Step 2 — Pre-Review Context

Before reviewing, check `backlog/BACKLOG.md`:
- Identify which requirement(s) this branch addresses.
- Note any pre-existing tracked requirement gaps that are NOT in scope — do not raise findings for
  these unless the branch **regresses** existing behaviour.

---

## Step 3 — Full Review (reviewer sub-agent)

Apply every checklist from `../../../../../../../foreign-exchange/.claude/agents` to the changed files:

**Pass 1 — Architecture:**
- [ ] Package placement: layer-first
- [ ] Service interface `public`; `Impl` package-private
- [ ] MapStruct mapper at every layer boundary — no manual mapping
- [ ] `@ConfigurationProperties` for all config (no `@Value`)
- [ ] External provider called only through its interface
- [ ] Consistent `ErrorResponse` shape for error bodies

**Pass 2 — Correctness (money movement):**
- [ ] `BigDecimal` with explicit scale/rounding — never `double`/`float`
- [ ] Debit + credit + conversion record write in one database transaction
- [ ] Concurrency strategy actually applied on the balance-mutating code path
- [ ] Idempotency-Key replay never double-debits or duplicates a record
- [ ] Insufficient funds → 422 `INSUFFICIENT_FUNDS`, no record persisted
- [ ] Unknown client/currency → 404 with the correct distinct code
- [ ] No secrets or sensitive values in logs
- [ ] External provider URL from `@ConfigurationProperties` — never user-supplied

**Pass 3 — Code Quality (Checkstyle):**
- [ ] Explicit types — no `var`
- [ ] Cyclomatic complexity ≤ 10; max 3 return statements per method
- [ ] Line length ≤ 125; no tabs; annotations on own line
- [ ] No duplicate string literals — use constants
- [ ] String equality via `.equals()` — never `==`
- [ ] No wildcard imports

**Pass 4 — Testing (TDD Discipline):**
- [ ] Test written BEFORE implementation (red → green → refactor)
- [ ] Test naming: `methodName_condition_expectedOutcome` — no `should`, no `given`
- [ ] Given/When/Then structure present (logical separation mandatory, comment markers optional)
- [ ] Instancio used for test data generation in unit tests
- [ ] Integration tests extend the project's `BaseIntegrationTestSetUp`
- [ ] Coverage ≥ 80% (per `CLAUDE.md`)

**Pass 5 — Conventional Commits:**
- [ ] All commit messages follow `type[(scope)]: short description`

---

## Step 4 — Produce Review Report

Output the structured report using the template from `../../../../../../../foreign-exchange/.claude/agents` Section 5:

```markdown
## Code Review Report

**PR / Branch:** [branch or PR reference]
**Reviewer:** @reviewer
**Date:** [YYYY-MM-DD]
**Verdict:** APPROVED | APPROVED WITH COMMENTS | REQUEST CHANGES | BLOCKED

---

### Summary

[1–3 sentence overall assessment.]

---

### Issues Found

| # | File | Line | Severity | Rule / Category | Description | Required Fix |
|---|------|------|----------|-----------------|-------------|--------------|

**Severity legend:**
- 🚨 BLOCKING — must be fixed before merge
- 🔴 HIGH — strong recommendation, fix before merge
- 🟡 MEDIUM — should fix, but not blocking
- 🔵 LOW — nice to have / minor style

---

### Positive Observations

- [What was done well]

---

### Action Required

- [ ] [Issue #1 fix]
- [ ] Re-run `mvn checkstyle:check` after changes
- [ ] Re-request review after all BLOCKING issues are resolved
```

**Verdict rules:**
- `BLOCKED` — any 🚨 BLOCKING issue exists
- `REQUEST CHANGES` — any 🔴 HIGH issue exists
- `APPROVED WITH COMMENTS` — only 🟡 MEDIUM / 🔵 LOW issues
- `APPROVED` — no issues found
