---
name: code
description: Implement a story file using the TDD quality loop — coder implements, tester verifies, reviewer approves. Max 3 iterations.
context: fork
---

Implement the story: $ARGUMENTS

Orchestrate up to 3 quality loop iterations. Stop early if the reviewer approves.

There is no Epic layer in this project — a story file is the whole task.

---

## Step 1 — Find and Prepare the Story

If `$ARGUMENTS` is empty, find the next unstarted story automatically — lowest Seq number first:
```bash
ls backlog/stories/open/ | sort | head -1
```

If a Seq number or title fragment is provided, search both `open/` and `in-progress/`:
```bash
find backlog/stories/open backlog/stories/in-progress -iname "*$ARGUMENTS*"
```

**If the story file is found under `backlog/stories/open/`**, move it to `backlog/stories/in-progress/`
before proceeding:
```bash
mv backlog/stories/open/{seq}-{short-title}.story.md backlog/stories/in-progress/
```

Read the full story file, including requirement context, acceptance criteria and any architect
recommendations appended by `/plan`.

Check all `Depends On` entries — every dependency's Seq must be `Status: Done` in `backlog/PRIORITY.md`
before proceeding.

Mark the story as in progress — set `| Status | In Progress |` in the story file's header table, and
`Status: In Progress` on its row in `backlog/PRIORITY.md`.

---

## Quality Loop — Max 3 Iterations

### Coder (use the **coder** sub-agent)

**Self-learning first — before writing anything:**
```bash
find src/main/java -name "*Service*.java"      # if adding a service
find src/main/java -name "*Controller.java"    # if adding a controller
find src/main/java -name "*Entity.java"        # if adding a JPA entity
find src/main/java -name "*Mapper.java"        # if adding a mapper
find src/test/java -name "*Test.java" | grep -i "<similar-name>"
```
Read one existing example. Copy its structure exactly — annotations, visibility, naming, injection style.

**TDD cycle — strictly in this order:**

1. **Red** — write the failing test first. Run `mvn test` — it must FAIL before any implementation.
2. **Green** — write the minimum code to make the test pass. Run `mvn test` — it must PASS.
3. **Refactor** — improve readability, remove duplication. Run `mvn test && mvn checkstyle:check` — both must pass.

**Iteration 1:** implement from scratch following task file.
**Iteration 2+:** fix the specific issues listed by the reviewer — nothing else.

**Package placement** (from `backlog/WORKFLOW.md`):

| What | Package |
|---|---|
| `@RestController` | `rest.controller` |
| Service interface + impl | `core.service` |
| Domain exception | `core.exception` |
| JPA `@Entity` | `persistence.entity` |
| REST mapper | `rest.mapper` |
| Persistence mapper | `persistence.mapper` |
| `@ConfigurationProperties` | `common.properties` |
| External provider client | `common.integrations.{provider}` |

**Rules (enforced — any violation is a reviewer BLOCKING):**
- Service interface `public`; `Impl` class **package-private**
- `@RequiredArgsConstructor` constructor injection — never `@Autowired` on fields
- `@ConfigurationProperties` records for all config — never `@Value`
- `private static final String` for all string literals used in code
- Money as `BigDecimal` with explicit scale and rounding mode — never `double`/`float`
- Debit + credit + conversion-record write in one database transaction
- Explicit types always — no `var`
- `ErrorResponse` (hand-written, consistent shape) for all error bodies

**After implementation:**
```bash
mvn spotless:apply
mvn checkstyle:check
```

**Coder output:**
```
Coder (Iteration N/3)

Files created/modified:
- src/main/java/zetta/foreignexchange/{path}/{ClassName}.java
- src/test/java/zetta/foreignexchange/{path}/{ClassNameTest}.java

TDD:
- Red:   mvn test → FAILED on {TestClass}#{method} ✅
- Green: mvn test → PASSED ✅
- Refactor: mvn test && mvn checkstyle:check → PASSED ✅

Spotless: applied ✅
```

---

### Tester (use the **tester** sub-agent)

Verify coverage of the coder's work — and **write any missing tests** before handing off to the reviewer.

**Step 1 — Assess what the coder produced:**

Review every new/modified class and its test file. For each, check:
- Correct test pyramid level used (unit / controller slice / integration)
- Happy path covered
- Null / missing / invalid input cases covered
- Applicable assignment scenarios covered (see checklist below)
- Test naming follows `methodName_condition_expectedOutcome` — no `should`, no `given`
- Test body uses `// when` → `// then` → `// verify` structure

**Step 2 — Write missing tests (active role):**

If any gap is found, write the missing tests immediately — do not leave them for the reviewer to flag.

**Test pyramid level — use the lowest level that gives confidence:**

| Concern | Level | Annotation |
|---|---|---|
| Service, mapper, provider-adapter logic | Unit | `@ExtendWith(MockitoExtension.class)` |
| HTTP mapping, validation, error responses | Controller slice | `@WebMvcTest` |
| Full HTTP flow with PostgreSQL | Integration | `@SpringBootTest` extending `BaseIntegrationTestSetUp` |

**Test naming:** `methodName_condition_expectedOutcome` — no `should`, no `given`
```
convert_withValidInput_debitsSourceAndCreditsTarget
convert_withInsufficientBalance_throwsInsufficientFundsException
convert_withReplayedIdempotencyKey_returnsOriginalResultWithoutDoubleDebit
fetchRate_whenProviderTimesOut_throwsRateProviderUnavailableException
```

**Test body structure:**
```java
@Test
void convert_withInsufficientBalance_throwsInsufficientFundsException() {
    // when
    ConvertMoneyInput input = buildConvertMoneyInput("CLIENT-001", "USD", "EUR", "999999.00");
    when(clientBalanceStore.findForUpdate("CLIENT-001", "USD"))
            .thenReturn(Optional.of(buildClientBalance("CLIENT-001", "USD", BigDecimal.TEN)));

    // then
    assertThrows(InsufficientFundsException.class, () -> conversionService.convert(input));

    // verify
    verify(conversionStore, never()).save(any());
}
```

**Utilities — always check before writing test data construction code:**
- Shared `build*` test data factories (client balances, conversion inputs/entities, requests)
- `BaseIntegrationTestSetUp` — `convert(clientId, request)`, `getBalances(clientId)`, `getConversionHistory(filters)`
- `Instancio.create(Type.class)` — random test data when field values are irrelevant

**PostgreSQL in integration tests:**
```java
// ✅ Use @ServiceConnection (the pattern used in TestcontainersConfiguration)
// ❌ Never @DynamicPropertySource — not the pattern used here
```

**Step 3 — Run and confirm:**

```bash
mvn test
mvn checkstyle:check
```
Both must pass — including any tests the tester just added.

**Tester output:**
```
Tester (Iteration N/3)

Unit tests:
- {ServiceName}Test: N tests ✅

Controller slice tests:
- {Controller}Test:  N tests ✅

Integration tests:
- {Feature}IntegrationTest: N tests ✅

Assignment scenario coverage:
- ✅ Happy-path debit/credit
- ✅ Insufficient funds → 422
- ✅ Idempotency-Key replay  [added by tester]

Tests added by tester: N  (list file + method names)

mvn test:             PASSED ✅
mvn checkstyle:check: PASSED ✅
```

---

### Reviewer (use the **reviewer** sub-agent)

Apply the full review framework from `../../../../../../../foreign-exchange/.claude/agents`.

Check `backlog/BACKLOG.md` first — never block a PR for a pre-existing tracked requirement gap.

**Pass 1 — Architecture:**
- [ ] Package placement: layer-first
- [ ] Service interface `public`; `Impl` package-private
- [ ] MapStruct mapper at every layer boundary — no manual mapping
- [ ] `@ConfigurationProperties` for all config (no `@Value`)
- [ ] External provider called only through its interface
- [ ] Consistent `ErrorResponse` for error bodies

**Pass 2 — Correctness (money movement):**
- [ ] `BigDecimal` with explicit scale/rounding — never `double`/`float`
- [ ] Debit + credit + conversion record in one transaction
- [ ] Concurrency strategy actually applied on the balance-mutating code path
- [ ] Idempotency-Key replay never double-debits or duplicates a record
- [ ] Insufficient funds → 422, no record persisted; unknown client/currency → 404
- [ ] No secrets or sensitive values in logs

**Pass 3 — Code Quality:**
- [ ] Explicit types — no `var`
- [ ] Cyclomatic complexity ≤ 10; max 3 return statements per method
- [ ] Line length ≤ 125; no tabs; annotations on own line
- [ ] No duplicate string literals — use constants
- [ ] String equality via `.equals()` — never `==`
- [ ] TDD cycle followed (failing test before implementation)
- [ ] Test naming `methodName_condition_expectedOutcome` — no `should`, no `given`
- [ ] Test body: `// when` → `// then` → `// verify` (omit `// verify` if no meaningful mock interaction)
- [ ] `mvn spotless:apply` applied; `mvn checkstyle:check` passes

**Reviewer output:**

*Scenario A — Approved:*
```
Reviewer (Iteration N/3)

| # | File | Line | Severity | Description |
|---|------|------|----------|-------------|
(none)

Positive observations:
- [What was done well]

Verdict: ✅ APPROVED
```

*Scenario B — Issues found (iteration < 3):*
```
Reviewer (Iteration N/3)

| # | File | Line | Severity | Description | Required Fix |
|---|------|------|----------|-------------|--------------|
| 1 | ConversionServiceImpl.java | 42 | 🚨 BLOCKING | var used | Replace with explicit type |

Verdict: ❌ NOT APPROVED → Iteration N+1
```

*Scenario C — Max iterations reached:*
```
Reviewer (Iteration 3/3)

Remaining issues:
- [list]

Verdict: ❌ NOT APPROVED — MAX ITERATIONS REACHED
⚠️ User intervention required:
  1. Accept current state (document known issue in task file)
  2. Authorise a 4th iteration
  3. Redesign the approach
```

---

## Loop Decision

```
if reviewer.verdict == APPROVED  → proceed to commit
else if iteration < 3            → iteration++ → repeat quality loop
else                             → STOP, present options to user
```

---

## On Approval — Prepare Commit

Set `| Status | Done |` in the story file's header table, then append test results to it:
```markdown
---

## Test Results

**Iterations:** N/3
**Reviewer verdict:** ✅ Approved

**Tests:**
- {TestClass}: N/N ✅

**Assignment coverage:**
- ✅ [scenario]: [assertion]

**Edge cases covered:**
- ✅ [case] → [behaviour]
```

Move the story file and update tracking:
```bash
mv backlog/stories/in-progress/{seq}-{short-title}.story.md backlog/stories/done/
```
Update `backlog/PRIORITY.md`: the Seq row's Status → `Done`.
Update `backlog/BACKLOG.md`: the requirement row(s) this story closes → `Done`.

Output the commit message following `backlog/WORKFLOW.md` PR rules:
```
feat(scope): <short description (max 72 chars)>

What: <what changed — 1-3 sentences>
Why: <assignment requirement closed, with a reference to the brief>
Tested: <test classes and scenarios verified>

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## Final Output to User

```
Story "{seq}-{short-title}" complete! (Iteration N/3)

Changed files:
- src/main/java/zetta/foreignexchange/{path}
- src/test/java/zetta/foreignexchange/{path}

Verification:
- mvn test:            PASSED ✅
- mvn checkstyle:check: PASSED ✅
- mvn spotless:apply:   Applied ✅
- Reviewer:             APPROVED ✅

Story file: backlog/stories/done/{seq}-{short-title}.story.md
PRIORITY.md / BACKLOG.md: ✅ Updated

YOUR TURN:
1. Review:  git diff
2. Test:    mvn test
3. Commit:  git commit -m "$(cat .commit-message)"  (or paste message above)
4. Push:    git push
5. Next:    /code  (picks up the next unstarted story)

⏸ Waiting for you...
```
