---
name: document
description: 'Generate technical documentation for the foreign-exchange service by analyzing the codebase. Usage: /document <type> [topic]. Types: readme, architecture, guide, overview, code, feature, pr, bugfix.'
---

Generate documentation for: $ARGUMENTS

If `$ARGUMENTS` is empty, default to `overview`.

Use the **tech-writer** sub-agent to produce documentation following `../../../../../../../foreign-exchange/.claude/agents`.

---

## Step 1 — Parse Arguments

Route `$ARGUMENTS` to documentation type and output directory:

| Input pattern | Type | Output | Example |
|---|---|---|---|
| `readme` | The assignment-mandated README | `README.md` (repo root) | `/document readme` |
| `architecture [topic]` | Architecture doc | `docs/` | `/document architecture conversion-flow` |
| `guide <topic>` | Developer guide | `docs/` | `/document guide testing` |
| `overview` | Project overview | `docs/` | `/document overview` |
| `code <Class>` | Code explanation | `docs/` (lasting) or `.claude/tmp/` (ephemeral) | `/document code ConversionService` |
| `feature <name>` | Feature documentation | `.claude/tmp/` | `/document feature conversion-endpoint` |
| `pr <branch>` | PR explanation | `.claude/tmp/` | `/document pr feature/101-04-idempotency` |
| `bugfix <name>` | Bug fix explanation | `.claude/tmp/` | `/document bugfix idempotency-race` |

**Routing decision:** If the document has lasting value for the repository → `docs/`. If it explains a
specific change or a specific topic → `.claude/tmp/`. The README is a special case — it always lives at
the repo root and is the assignment's graded deliverable.

---

## Step 2 — Codebase Discovery (tech-writer sub-agent)

Before writing, the tech-writer must:

1. **Read the existing `README.md` and `docs/`** — list all files, read relevant ones to avoid duplication and match tone
2. **Discover source files** based on doc type:
   - **README:** read the concurrency-critical code (`ConversionServiceImpl` or equivalent), the
     idempotency check, the rate cache config, the Flyway seed migration, `application.yaml`, `Dockerfile`, `docker-compose.yaml`
   - **Architecture:** read package structure, key services, configuration classes
   - **Code:** find the target class and its tests, read related interfaces and implementations
   - **Guide:** read `pom.xml`, `application.yaml`, test configuration
   - **Overview:** scan all top-level packages, read `CLAUDE.md` and `README.md`
   - **Feature/PR/Bugfix:** use `git log` and `git diff` to find related commits and changed files

---

## Step 3 — Generate Documentation

For the README, follow the assignment's required sections (see `tech-writer.md` — Output Routing Rules):
how to run it, demo clients, client-id header choice, concurrency strategy + why, idempotency approach,
caching TTL + invalidation choice, provider setup, trade-offs, what's next with more time.

For everything else, apply the document template from `../../../../../../../foreign-exchange/.claude/agents`:

```markdown
# Title
## Purpose
## Context
## Explanation
## Implementation in Code
## References
```

**File naming (non-README docs):** Title-Case-Hyphens (e.g., `System-Architecture.md`, `Concurrency-And-Idempotency.md`).

Write the document to the appropriate directory based on routing rules from Step 1.

---

## Step 4 — Cross-Reference Check

Before finalizing, verify:

- [ ] No duplication with existing documents in `docs/` or the current `README.md`
- [ ] All referenced file paths exist in the codebase
- [ ] Every trade-off states the alternative(s) considered and why this one was chosen
- [ ] Code snippets match the current source (not outdated)
- [ ] File naming follows Title-Case-Hyphens convention (non-README docs)

---

## Step 5 — Output to User

**For the README:**

```
README updated: README.md

Sections:
1. How to run it
2. Demo clients
3. Client identification (X-Client-Id)
4. Concurrency strategy + why
5. Idempotency approach
6. Rate caching + invalidation choice
7. Trade-offs
8. What's next with more time

YOUR TURN:
1. Review:  cat README.md
2. Edit:    Make any adjustments
3. Commit:  git add README.md && git commit

Waiting for you...
```

**For permanent docs (`docs/`):**

```
Documentation generated: docs/{Document-Name}.md

Sections:
1. Purpose
2. Context
3. Explanation
4. Implementation in Code
5. References

Source files analyzed:
- src/main/java/zetta/foreignexchange/{path}
- src/test/java/zetta/foreignexchange/{path}

YOUR TURN:
1. Review:  cat docs/{Document-Name}.md
2. Edit:    Make any adjustments
3. Commit:  git add docs/{Document-Name}.md && git commit
4. Push:    git push

Waiting for you...
```

**For ephemeral docs (`.claude/tmp/`):**

```
Explanation generated: .claude/tmp/{Document-Name}.md

This is an ephemeral document — it explains a specific change/topic
and is not intended for permanent repository documentation.

Read it: cat .claude/tmp/{Document-Name}.md
```
