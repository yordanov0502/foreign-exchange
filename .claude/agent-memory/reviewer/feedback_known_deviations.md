---
name: feedback-known-deviations
description: Reviewer prompts for this repo include a pre-approved "known deviations" list — verify each item is actually implemented as described, then treat it as non-blocking, don't re-argue it
metadata:
  type: feedback
---

The orchestrator's review prompt for this repo includes an explicit "Known, accepted deviations from the
story (do NOT flag these)" list — e.g. a test class living in a different package for visibility reasons,
a mapper method param count differing from the story prose because checkstyle.xml caps it lower, extra
exception handlers added to protect an existing test from a new catch-all handler, tests renamed for
clarity.

**Why:** these deviations were already justified and verified by the user/architect in a prior step. Two
past reviews (Seq 3 rate endpoint, Seq 4 conversions) both had 3-5 such items each, and every one checked
out on inspection — they are not review shortcuts, they're accurate descriptions of intentional, sound
choices (usually driven by this repo's stricter-than-story-prose checkstyle limits, e.g. 3-param cap on
private methods vs the story's stated 4).

**How to apply:** still verify each listed deviation is actually present and matches the description (don't
take it purely on faith) — but once confirmed, do not list it as an issue in the review report, and do not
downgrade the verdict because of it. Only escalate if the code diverges from what the deviation note
describes (e.g. the "extra handler" is missing, or the renamed tests don't actually cover both directional
cases as claimed).
