# 2 — Expose `GET /clients/{clientId}/balances`

| Field | Value |
|---|---|
| Story Points | 3 |
| Priority | Critical |
| Status | Done |

**Story points rationale:** a read-only endpoint with no business rules — one query, one mapping chain,
one error path. It spans three layers (`rest`, `core`, `persistence`) and introduces the first controller,
the first controller advice handler and the first REST DTOs of the service, which is what lifts it above
a 1; there is no money arithmetic, no write, and no concurrency concern, which is what keeps it below a 5.

**Written retroactively.** The implementation was completed before this story file existed; the sections
below describe the work as delivered, so Seq 2 has the same paper trail as every other sequence step.

---

## User Story

**As a** foreign-exchange service
**I want** to return every currency balance a client currently holds, addressed by the client's business
identifier
**So that** REQ-4 is satisfied and a caller can read the starting balances seeded by Seq 1 before, and the
debited/credited balances after, a conversion is executed.

---

## Context

**Current behavior before this story:** Seq 1 left `clients`, `balances` and `conversions` persisted and
seeded, but nothing read them back. `rest/controller/` held only empty `@RestController` shells, there was
no controller advice, no REST DTO, and no `core` service — the seeded demo balances were reachable only
through a direct database query.

**Required behavior:** `GET /clients/{clientId}/balances` returns `200` with one entry per currency the
client holds, ordered by currency code, and `404 CLIENT_NOT_FOUND` when no client carries that identifier.
An existing client holding no currency is a `200` with an empty list — not a `404`.

**What this unlocks:** the read side that Seq 4 (`POST /conversions`) is verified against, and the first
concrete `@RestControllerAdvice` handler plus `ErrorResponse` shape that Seq 9 extends rather than invents.

**Grading axis:** correctness (REQ-4 contract) and production sense (a distinguishable not-found error body
rather than a bare status code).

---

## Scope

### In Scope
- [x] `BalanceService` interface in `core/service` with a package-private implementation
- [x] Client-existence check that distinguishes "unknown client" from "client with no balances"
- [x] `ClientNotFoundException` domain exception carrying the offending `clientId`
- [x] `@RestControllerAdvice` handler translating it to `404` with a structured `ErrorResponse` body
- [x] Currency-ordered repository query for a client's balances
- [x] MapStruct mappers at both layer boundaries — entity to domain, domain to response DTO
- [x] SpringDoc annotations on the endpoint: tag, summary, per-status response schemas and examples

### Out of Scope
- Authentication or authorisation of the caller — an explicit assignment non-goal; `clientId` is supplied
  in the path by the caller and trusted
- Pagination or filtering of the balance list — REQ-4 asks for the balances a client holds, and the row
  count is bounded by the number of currencies
- Creating, updating or zeroing balances — every write lands in Seq 4
- Generic request validation and the full exception-to-status matrix — Seq 9 (REQ-14, REQ-15)
- Currency-code validation against a supported set — Seq 5 (REQ-8)

---

## Acceptance Criteria

- [x] `GET /clients/CLIENT-001/balances` returns `200` with `clientId` and a `balances` array of
      `{currency, amount}` entries
- [x] Balances are ordered by currency code ascending, so the response is stable across calls
- [x] Amounts are serialised from `BigDecimal` — no `double`/`float` anywhere on the path
- [x] A known client holding no currency returns `200` with an empty `balances` array
- [x] An unknown `clientId` returns `404` with body `code: CLIENT_NOT_FOUND`, a message naming the id, the
      status, and the request path
- [x] The endpoint is mapped at exactly `/clients/{clientId}/balances` — no version or context prefix,
      matching the path named in the assignment brief
- [x] `BalanceEntity` never leaves the service layer — the service returns `Balance` domain records
- [x] The endpoint appears in the generated OpenAPI spec under a `Clients` tag with both the `200` and the
      `404` bodies documented

---

## Technical Notes

### Layers Affected
- [x] `core` — `Balance` domain record, `BalanceService` + impl, `ClientNotFoundException`
- [x] `persistence` — `BalanceRepository` derived query, `BalanceMapper`
- [x] `rest` — `ClientController`, response DTOs, `ClientBalancesResponseMapper`, controller advice
- [ ] `common` — untouched

### Files Created / Modified
- `src/main/java/zetta/foreignexchange/core/model/Balance.java`
- `src/main/java/zetta/foreignexchange/core/service/BalanceService.java`
- `src/main/java/zetta/foreignexchange/core/service/implementation/BalanceServiceImpl.java`
- `src/main/java/zetta/foreignexchange/core/exception/ClientNotFoundException.java`
- `src/main/java/zetta/foreignexchange/persistence/mapper/BalanceMapper.java`
- `src/main/java/zetta/foreignexchange/persistence/repository/BalanceRepository.java`
- `src/main/java/zetta/foreignexchange/persistence/repository/ClientRepository.java`
- `src/main/java/zetta/foreignexchange/rest/controller/ClientController.java`
- `src/main/java/zetta/foreignexchange/rest/mapper/ClientBalancesResponseMapper.java`
- `src/main/java/zetta/foreignexchange/rest/model/BalanceResponse.java`
- `src/main/java/zetta/foreignexchange/rest/model/ClientBalancesResponse.java`
- `src/main/java/zetta/foreignexchange/rest/error/ErrorResponse.java`
- `src/main/java/zetta/foreignexchange/rest/controlleradvice/ForeignExchangeControllerAdvice.java`

### Existing Pattern Reference

Entities are read through a derived query and mapped before crossing the layer boundary:

```java
@Transactional(readOnly = true)
public List<Balance> getClientBalances(String clientId) {
    validateClientExists(clientId);
    List<BalanceEntity> balanceEntities = balanceRepository.findByClientClientIdOrderByCurrencyAsc(clientId);
    return balanceMapper.mapToBalances(balanceEntities);
}
```

The controller holds no logic — it delegates, maps, and returns:

```java
@GetMapping(value = "/{clientId}/balances", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<ClientBalancesResponse> getClientBalances(@PathVariable String clientId) {
    List<Balance> balances = balanceService.getClientBalances(clientId);
    return ResponseEntity.ok(clientBalancesResponseMapper.mapToClientBalancesResponse(clientId, balances));
}
```

### Implementation Notes

- **The existence check is separate from the balance query.** `clientRepository.existsByClientId` runs
  before `balanceRepository.findByClientClientIdOrderByCurrencyAsc`, because an empty result list is
  ambiguous on its own — it means both "no such client" and "client holds nothing". Seq 1's dedicated
  `clients` table is what makes the two directly distinguishable, and REQ-8 later depends on that
  distinction.
- **`@Transactional(readOnly = true)`** on the service method — no write in this story, and the read-only
  flag keeps the intent explicit next to the transactional writes Seq 4 adds alongside it.
- **Ordering lives in the query**, not in a post-sort, so the database does the work and the response order
  is stable without extra code.
- **`open-in-view: false`** is already set, so the mapping to `Balance` has to happen inside the service —
  it does, via `BalanceMapper`.
- **The advice returns `application/problem+json`**, which is the content type Seq 9 should keep for every
  handler it adds.
- **The service implementation is package-private** (`class BalanceServiceImpl`), reachable only through the
  public `BalanceService` interface, per the layer rules in `WORKFLOW.md`.

---

## Outstanding Follow-up

- **Automated test coverage for this story is still being written** — service-level, controller-slice and
  full-stack integration tests covering the `200`, the empty-balances `200`, and the `404
  CLIENT_NOT_FOUND` path. The story is marked Done on the implementation; REQ-17 / REQ-18 coverage for this
  endpoint remains tracked through Seq 12.

---

## Dependencies

- **Depends On:** `1` — the `clients` and `balances` tables plus the seeded demo clients this endpoint reads
- **Blocks:** `9` — global error handling extends the `ErrorResponse` shape and advice introduced here;
  `10` — the OpenAPI story documents this endpoint among the others

---

## References

- `.claude/Java-Assignment.pdf` — the client balances endpoint
- `backlog/BACKLOG.md` — REQ-4
- `backlog/PRIORITY.md` — Seq 2, and the "Seq 2 implementation note — 2026-09-18" entry recording that
  entities stay setter-free, so Seq 4 mutates balances through explicit domain methods
