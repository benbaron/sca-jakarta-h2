---
plan_version: 223
active_phase: P17
active_slice: P17-C6
active_status: VERIFYING
active_branch: codex/P17-C6-fund-hierarchy-lifecycle
active_pull_request: 298
active_head: d114005f4e63374bda56a6c547080b2fe2e95d93
next_action: "Complete the owner Fund hierarchy lifecycle checklist for PR #298 and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. P17-C3 merged through PR #295 at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`. P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`. P17-C5 merged through PR #297 at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`; the owner explicitly accepted C5 and Maven PR Tests push run `33028403587` passed on that exact merged `main` head before C6 began.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE |
| P11 | Report Library | DONE through P11-C2 / PR #284 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 DONE; C6 VERIFYING |

## 4. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, budget stores, import stores, record stores, or prototype maintenance paths.
- Disabled placeholder Delete buttons are not part of the UI contract. A Delete action is shown only when it performs a real governed operation.
- Durable records that must preserve history use deactivation, archive, disposition, correction, reversal, or another governed lifecycle rather than invented generic hard deletion.
- Company-specific money/date/table/divider state remains H2-backed.

## 5. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE.

Completion evidence:

- PRs #290, #291, and #292 merged the authorized shared UI policy corrections.
- Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`.

### P17-C2 — Durable account record lifecycle

Status: DONE.

Completion evidence:

- PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`.
- Exact final C2 head `ce85bad0810ca33de778e4354b8fa4a7ff74f0c5` passed Maven PR Tests run `32923532902`.
- Interactive Chart of Accounts uses stable account ID and governed Active/deactivation lifecycle without placeholder Delete.

### P17-C3 — Budget version lifecycle completion

Status: DONE.

Completion evidence:

- PR #295 merged to `main` at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc` after owner confirmation.
- Budget Editor retains `DRAFT`, `ACTIVE`, and `ARCHIVED` versions by stable `BudgetPlan.id`.
- Explicit archival is draft-only; active versions retire only through governed replacement activation.
- Archived line history remains read-only and visible even when a category later becomes inactive.
- Initial C3 implementation head `47975931f3249667e6c8c0c3e89f0e516b2e7558` passed all Maven PR Tests gates in run `32926472161`; the final audit correction was merged only after owner acceptance.

### P17-C4 — Banking durable-record lifecycle

Status: DONE.

Completion evidence:

- PR #296 merged to `main` at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4` after owner acceptance.
- Bank/configured-account lifecycle changes serialize on company authority; a Bank cannot be inactive while an active configured account points to it, and an active configured account cannot be created/reactivated under an inactive Bank.
- The same active-parent invariant applies to the SCLX/interchange creation seam; inactive records remain durable history and no hard-delete path was introduced.
- Maven PR Tests push run `33020990140` passed on exact merged `main` head `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`.

### P17-C5 — Inventory item lifecycle completion

Status: DONE.

Completion evidence:

- PR #297 merged to `main` at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53` after owner acceptance.
- Inventory lifecycle status is service-owned; ordinary edits cannot silently alter it, zero quantity is required before deactivation/disposal, and disposed items remain terminal retained history.
- Metadata edits, lifecycle transitions, and governed quantity movements serialize on the same durable `InventoryItem` lock; lifecycle transitions write factual audit history.
- Exact merged-main Maven PR Tests run `33028403587` passed on `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: VERIFYING.

Branch: `codex/P17-C6-fund-hierarchy-lifecycle`
Starting base: `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`
Pull request: #298 (draft)
Verified product head: `d114005f4e63374bda56a6c547080b2fe2e95d93`

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/data-exchange/sclx.md`
- donor Fund model only as design reference; donor persistence is not authoritative

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/Fund.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundLookupService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxStructureValidator.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxImportCommitService.java`
- focused Fund and SCLX tests plus current production-route/core-editor compliance tests

Purpose:

- Close the Fund hierarchy lifecycle integrity gap without changing fund accounting authority, transaction posting, or introducing a second fund model.
- Preserve stable Fund identity/history while ensuring an active child cannot be maintained beneath an inactive parent hierarchy.
- Apply the same invariant to SCLX validation/import/export so interchange cannot bypass interactive lifecycle rules.

Audit finding and selected direction:

- `FundsPanel` already used stable IDs, real protected `Delete Unused`, Active/inactive state, retained-history guidance, H2-backed layout state, and company date formatting.
- `FundAdminService.apply(...)` previously copied `command.active()` and a validated parent independently. It allowed an active child beneath an inactive parent and allowed a parent to be deactivated while active children remained.
- `FundLookupService.listActiveFunds()` previously filtered only the child row's Active flag, so a legacy invalid active child beneath an inactive ancestor could remain selectable by production posting/reference-data consumers.
- SCLX previously wrote Fund parent/status directly and validated only that exported parent IDs resolved, so interchange could manufacture or serialize the same invalid hierarchy.
- The donor Fund model contains the same basic parent/Active fields but no stronger lifecycle authority worth porting.

Delivered:

- `FundAdminService.save(...)`, compatibility `upsert(...)`, and protected `deleteUnused(...)` serialize on the owning Company with a pessimistic write lock.
- Every active Fund now requires active parent ancestry. Active creation, reactivation, and reparenting beneath an inactive parent are rejected.
- Parent deactivation is rejected while direct active child Funds remain, establishing child-first retirement/reparenting and parent-first reactivation while preserving inactive hierarchy as retained history.
- Existing self-parent and circular-parent protections remain in force, and the real `Delete Unused` operation remains limited to completely unreferenced Funds.
- `FundLookupService.listActiveFunds()` fails closed for a legacy invalid active row beneath an inactive or circular ancestry, while `listAllFunds()` keeps that row visible for maintenance/repair.
- `FundsPanel` visibly explains hierarchy retirement/reactivation order without adding a placeholder Delete operation.
- SCLX structure validation resolves Fund parent references, rejects circular Fund hierarchies, and rejects active-child/inactive-parent source graphs before commit.
- SCLX export validation refuses to serialize the same invalid Fund hierarchy.
- SCLX Fund creation serializes on the same Company authority and defensively rechecks active parent ancestry during commit.
- Added H2 lifecycle regressions, legacy active-lookup regression, SCLX structure/export regressions, source/UI guardrails, `doc/funds/fund-lifecycle.md`, matrix updates, and the owner checklist `doc/P17-C6-fund-hierarchy-lifecycle-user-testing.md`.
- No schema migration, parallel Fund model, alternate Fund store, or change to posting/budget/report/transfer accounting semantics was introduced.

Validation status:

- Exact starting `main` is `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.
- C5 merged-main Maven PR Tests run `33028403587` succeeded and is the C6 baseline.
- Exact C6 product head `d114005f4e63374bda56a6c547080b2fe2e95d93` passed normal pull-request Maven PR Tests run `33029386185`.
- Run `33029386185` passed all three required gates: clean headless `mvn clean verify`, repeat full `mvn test`, and production JavaFX route compliance under Xvfb.
- The earlier run `33029258888` was cancelled because the PR head moved during the final code audit and is not counted as validation evidence.
- No local Maven result is claimed.
- This controller update and the preceding lifecycle-contract clarification are documentation-only changes after the green product head; final-head GitHub validation is required before owner handoff is complete.

Known failures:

- None currently known.

Owner acceptance:

- Follow `doc/P17-C6-fund-hierarchy-lifecycle-user-testing.md` after final-head GitHub validation is green.
- Do not merge until the owner accepts the checklist.

Next exact action:

- Validate the final documentation/controller head in Maven PR Tests, then stop before merge for owner desktop acceptance.
