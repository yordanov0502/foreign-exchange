---
name: plan
description: Plan the next assignment requirement — @planner reads PRIORITY.md, picks the next unplanned Seq, generates one sprint-ready TDD story, @architect reviews architecture and security upfront before any code is written.
context: fork
---

Plan: $ARGUMENTS

---

## When to Use

- `/plan` — no argument: pick the next unplanned Seq from `backlog/PRIORITY.md` automatically
- `/plan "feature-name"` — plan a specific feature not yet on `PRIORITY.md`

> **Note on architect's role here vs in /code:**
> - `/plan` → @architect reviews the **story file** (upfront design: is it correctly structured,
>   architecturally sound, no requirement gaps missed?)
> - `/code` → @reviewer reviews **actual code** (TDD quality, checkstyle, correctness in implementation)
> These are different agents with different scopes. @architect never touches code. @reviewer never touches planning.

There is no Epic layer in this project — one Seq row = one story file = one `/code` implementation pass.

---

## Step 1 — Determine What to Plan

Use the **planner** sub-agent for all steps below.

### If `$ARGUMENTS` is empty — autonomous requirement-driven mode:

Read `backlog/PRIORITY.md` and find the lowest Seq row whose `Status` is `Not planned` and whose
`Blockers` are all `Done` or `—`.

```bash
cat backlog/PRIORITY.md
cat backlog/BACKLOG.md
```

Pick that Seq row. That is the story to plan. Announce it:
```
Next unplanned story: Seq N — "{title}"
Requirement(s) closed: #X, #Y
Blockers: satisfied ✅
```

If there are multiple unblocked unplanned Seq rows, pick the lowest Seq number.

### If `$ARGUMENTS` is a feature name not yet in `PRIORITY.md`:

Add a new row to `backlog/PRIORITY.md` first (ask the user for its Tier and which requirement(s) it
closes if not obvious), then proceed as above with that Seq.

---

## Step 2 — @planner Discovers Existing Patterns

Before writing the story file, @planner runs the full discovery checklist against the codebase:

```bash
# Services
find src/main/java -name "*Service*.java"

# Entities
find src/main/java -name "*Entity.java"

# Repositories / stores
find src/main/java -name "*Repository.java" -o -name "*Store*.java"

# MapStruct mappers
find src/main/java -name "*Mapper.java"

# REST controllers
find src/main/java -name "*Controller.java"

# Domain models
find src/main/java -name "*.java" -path "*/core/model/*"

# Configuration properties
find src/main/java -name "*Properties.java"

# Test utilities and base classes
find src/test/java -name "Base*.java"
find src/test/java -name "*IntegrationTest.java"

# Persistence and HTTP client dependencies already in the project
grep -A2 "jpa\|flyway\|postgresql\|feign\|resttemplate\|restclient\|springdoc" pom.xml
```

Read at least one existing example of each relevant pattern before proposing file locations or
structure. Never say "similar to X" without including the actual file path and a code snippet.

> For stories involving the rate provider or the concurrency/idempotency strategy: read the existing
> `common/integrations/frankfurter` client and the entity/store classes it touches before proposing any
> new structure.

---

## Step 3 — @planner Writes the Story File

Create `backlog/stories/open/{seq}-{short-title}.story.md` using `backlog/templates/story.template.md`.
This one file **is** the implementation task — fill in every section:

| Field | Requirement |
|---|---|
| Assignment reference | Which requirement row(s) in `BACKLOG.md` this closes |
| Story points | Fibonacci 1–8. If it doesn't fit in 8, split it into a new Seq row instead |
| Files to create / modify | Exact paths, no vague "add a service" |
| Existing pattern reference | File path + minimal code snippet |
| TDD steps | Red (failing test) → Green (minimum code) → Refactor |
| Acceptance criteria | Specific and testable — maps directly to spec behaviour |
| Correctness considerations | Only the ones relevant to this story (concurrency, idempotency, atomicity, validation) |
| Dependencies | Which Seq(s) must be done first |

**@planner updates `backlog/PRIORITY.md`**: sets the Seq row Status from `Not planned` → `In Progress`.

**@planner output:**
```
@planner — Story Created

backlog/stories/open/{seq}-{short-title}.story.md
Requirement(s) closed: [# — description]
Story points: N

PRIORITY.md: ✅ Updated (Not planned → In Progress)
```

---

## Step 4 — @architect Reviews the Story File (Upfront Design)

Use the **architect** sub-agent.

> @architect reviews the story file only — no code exists yet. The goal is to catch design mistakes,
> correctness gaps, and business-rule misunderstandings before a single line of code is written.
> @architect does NOT review code — that is @reviewer's job inside /code.

@architect reads the story file and checks:

**Architecture:**
- [ ] Package placement: layer-first — matches the project structure in `CLAUDE.md`
- [ ] Service interface `public`; `Impl` class package-private
- [ ] MapStruct mapper at every layer boundary — no manual mapping proposed
- [ ] `@ConfigurationProperties` for all config — never `@Value`
- [ ] External provider stays behind an interface
- [ ] Layer dependencies: `rest → core`; `core → persistence, common` (no reverse; `persistence` never
  depends on `common` — external integrations/utilities are a `core` concern, not persistence's)

**Correctness (money movement — design level):**
- [ ] Debit + credit + conversion record write are planned as one database transaction
- [ ] Concurrency strategy is named explicitly (pessimistic lock / `@Version` / serialized writer) —
      not left implicit
- [ ] Idempotency approach is named explicitly and enforced at the DB layer, not only in-memory
- [ ] Insufficient-funds and not-found paths are both in scope with the correct status codes
- [ ] Rate is fetched before any balance-mutating transaction opens

**Security (design level):**
- [ ] No auth machinery proposed (out of scope)
- [ ] External provider URL sourced from `@ConfigurationProperties`, never request input
- [ ] No secrets to be logged

**@architect output:**
```
🏗️ Architecture & Security Review: {seq}-{short-title}

✅ Architecture: [what is sound]
⚠️ Architecture: [what needs correction and why]
✅ Correctness: [what is covered]
⚠️ Correctness: [risk + required fix before coding]

Overall: ✅ Approved | ⚠️ Approved with N recommendations | ❌ Blocked — fix before /code
```

---

## Step 5 — @architect Appends Recommendations to the Story File

@architect appends to the END of the story file:

```markdown
---

## @architect Recommendations

> **Added by @architect during /plan review**

- ✅ [Recommended pattern with exact file path]
- ⚠️ [Mandatory constraint — must not be skipped during implementation]
```

- ✅ = correct approach to follow (always include a file reference if pointing to an existing pattern)
- ⚠️ = risk or mandatory requirement — coder must not skip this
- Appended to END only — never rewrite existing story content

If @architect finds a BLOCKING issue: rewrite the story file to fix the design before moving on. Do not
leave a broken story for the coder.

---

## Step 6 — Final Summary

```
✅ Story "{seq}-{short-title}" is ready for implementation

🎯 Requirement(s) closed: [#N — description]
📊 Story points: N
🏗️ Architecture: ✅ Approved (or ⚠️ N recommendations appended to the story file)
🔒 Correctness: ✅ Approved (or ⚠️ N warnings appended to the story file)

📁 backlog/stories/open/{seq}-{short-title}.story.md

PRIORITY.md: ✅ Updated (Seq N → In Progress)

─────────────────────────────────────────
🚀 Next: /code
   Picks up this story and runs the TDD quality loop:
   @coder → @tester → @reviewer (up to 3 iterations)
─────────────────────────────────────────

⏸ Please review the story above before starting /code.
Approve? (yes / no / adjust)
```

---

**The planner drives from the assignment requirement tracker — it never waits for the user to define
work. The architect catches design mistakes before any code is written. The reviewer catches
implementation mistakes inside /code. There is no Epic layer — a flat, Seq-ordered story list is enough
for this task.**
