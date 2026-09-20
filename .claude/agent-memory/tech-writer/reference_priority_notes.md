---
name: reference-priority-notes
description: backlog/PRIORITY.md "Re-prioritisation Notes" section is the authoritative decision log for trade-offs, concurrency/idempotency/caching rationale in this repo
metadata:
  type: reference
---

`backlog/PRIORITY.md`'s "Re-prioritisation Notes" section (appended chronologically, never overwritten)
is the authoritative record of architectural decisions and their reasoning for this project — including
the concurrency strategy choice (pessimistic locking, `@Version` dropped in `V5`), the wire-format
vocabulary asymmetry between `POST /conversions` and `GET /rates`, the `SAME_CURRENCY` rejection rule,
the OpenFeign vs `RestClient` decision and the Spring Cloud/Boot version-skew risk, and known gaps (no
JaCoCo, `postgres:latest` unpinned, tests skipped in Docker image build).

**How to apply:** Before writing the README or any `docs/` content that documents a trade-off or
decision, read this section first — it usually already contains the "why" in the user's/architect's own
words, which should be verified against current code (things move) but rarely needs to be invented from
scratch. See [[readme-style-notes]] for how this was used when writing the initial README.
