---
name: project-foreign-exchange-tdd-loop
description: How the foreign-exchange take-home repo runs its TDD quality loop and where the ground truth lives for each story review
metadata:
  type: project
---

This repo (`C:\Users\yorda\Desktop\foreign-exchange`) implements a Spring Boot FX take-home assignment
(`.claude/Java-Assignment.pdf`). Work is planned story-by-story in `backlog/stories/{open,in-progress,done}/`,
sequenced in `backlog/PRIORITY.md`, tracked against requirements in `backlog/BACKLOG.md`.

Each story goes through a coder -> tester -> reviewer TDD loop, up to 3 iterations, all on one feature
branch, uncommitted until the loop passes. As reviewer, the story file itself (in `in-progress/`) is the
authoritative spec — it typically embeds not just acceptance criteria but exact architect sign-off
conditions (see Seq 4 / story 4, which recorded pessimistic-lock conditions from `@architect` directly in
its Definition of Ready). Always read the full story before diffing code — it often supersedes what
`BACKLOG.md`'s one-line requirement summary implies.

**Why:** the user explicitly asked the planner to consolidate multiple small sequences (Seq 5/6/7) into one
story (Seq 4, 21 points) "regardless of story points" to avoid rewriting the same transactional method three
times. This is recorded as a deliberate, user-approved exception to the normal <=8-point rule — don't flag
oversized stories as a process smell if `PRIORITY.md` documents the same override.

**How to apply:** when reviewing, cross-reference the story's own "Technical Notes" / "Implementation Notes"
(R1, R2, R3... numbered notes) — they often encode very specific, already-negotiated design decisions
(exact check order, exact transaction boundary shape, exact locking order) that look like arbitrary
implementation choices unless you've read that section. Verify the code against those notes literally, not
just against general concurrency/idempotency best practice.

See also [[feedback_known_deviations]].
