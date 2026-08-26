---
plan_version: 221
active_phase: P17
active_slice: P17-C5
active_status: VERIFYING
active_branch: codex/P17-C5-inventory-item-lifecycle
active_pull_request: 297
active_head: 50986693a28964e25e581d9d61be39efd387a3a6
next_action: "Run Maven PR Tests on PR #297 final documentation-record head, correct any findings, then complete the owner Inventory lifecycle checklist and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. P17-C3 merged through PR #295 at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`. P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`; the owner explicitly accepted C4 and Maven PR Tests push run `33020990140` passed on that exact merged `main` head before C5 began.

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
| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 VERIFYING |

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

Status: VERIFYING.

Branch: `codex/P17-C5-inventory-item-lifecycle`  
Starting base: `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`  
Pull request: #297 (draft)

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/workflow/development-workflow.md`

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/InventoryItem.java`
- `src/main/java/org/nonprofitbookkeeping/model/InventoryMovement.java`
- `src/main/java/org/nonprofitbookkeeping/service/InventoryItemCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/InventoryService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/InventoryPanel.java`
- `src/test/java/org/nonprofitbookkeeping/service/InventoryServiceTest.java`
- `src/test/java/org/nonprofitbookkeeping/service/InventoryLifecycleSourceTest.java`
- `src/test/java/org/nonprofitbookkeeping/ui/InventoryPanelSourceTest.java`
- current production-route/core-editor compliance tests touching Inventory

Purpose:

- Close the Inventory durable-record lifecycle gap without changing movement accounting or adding a second inventory authority.
- Ordinary item editing must not directly consume `INACTIVE`/`DISPOSED` lifecycle state or silently strand quantity/value outside movement history.
- Retain every item by stable ID and preserve all movement/canonical transaction history.

Delivered:

- Interactive item creation is restricted to `ACTIVE`; the SCLX/import historical restore seam remains separately caller-owned and can restore source status without synthesizing local lifecycle history.
- Ordinary `InventoryService.update(...)` preserves existing status and rejects attempted status changes.
- Metadata edits, lifecycle changes, and confirmed quantity movements now serialize on the same pessimistic `InventoryItem` lock, preventing stale metadata writes from undoing a concurrent retirement.
- `InventoryService.changeStatus(...)` validates active-company/stable-ID ownership, requires actor and reason, requires zero on-hand quantity before deactivation/disposal, allows `INACTIVE -> ACTIVE`, and makes `DISPOSED` terminal for interactive lifecycle operations.
- Each successful lifecycle transition writes a factual `INVENTORY_ITEM_STATUS_CHANGED` `AuditEvent` atomically with the status change.
- Inventory UI now displays status read-only and exposes explicit **Deactivate Item**, **Reactivate Item**, and **Dispose Item** actions with retained-history/no-delete guidance.
- H2 lifecycle regressions cover direct-edit rejection, zero-quantity protection, audit history, reactivation, and terminal disposal; source guardrails cover UI lifecycle controls and shared item-lock serialization.
- `doc/inventory/inventory-and-assets.md`, `doc/interface-operation-matrix.md`, and `doc/P17-C5-inventory-item-lifecycle-user-testing.md` record the governing contract and owner acceptance steps.

Validation status:

- Exact starting `main` is `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`.
- C4 merged-main Maven PR Tests run `33020990140` succeeded and is the C5 baseline.
- Temporary publication guard run `33021727554` intentionally failed only on a trailing-blank-line `git diff --check` finding before any product commit was pushed; the whitespace was corrected.
- Corrected publication run `33021774497` passed all publication/source/diff guards and self-removed its temporary artifacts.
- A subsequent source audit found and corrected the metadata-update/lifecycle race; temporary correction run `33021884987` applied the shared item lock and self-removed its temporary artifacts.
- Product code/audit head before this documentation record is `50986693a28964e25e581d9d61be39efd387a3a6`.
- No local Maven result is claimed. Maven PR Tests on the final documentation-record head are required for semantic compile, JUnit/H2, and JavaFX production-route validation.

Known failures:

- None in product behavior currently known; final-head GitHub Maven validation is pending.

Owner acceptance:

- Follow `doc/P17-C5-inventory-item-lifecycle-user-testing.md` only after final-head CI is green.
- Do not merge until the owner accepts the checklist.

Next exact action:

- Run Maven PR Tests on PR #297 final documentation-record head, inspect all required gates, correct every failure, then stop before merge for owner desktop acceptance.
