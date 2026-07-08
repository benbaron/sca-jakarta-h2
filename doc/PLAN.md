---
plan_version: 10
active_phase: P09
active_slice: P09-C1
active_status: VERIFYING
active_branch: codex/P09-C1-inventory-ui-rules
active_pull_request: pending
active_head: pending
next_action: "Open a draft PR for P09-C1, inspect GitHub Actions, fix failures, then perform desktop visual validation."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records merged P09-S1 completion and starts P09-C1 for Inventory UI design-rule corrections and removal of disabled Delete placeholder buttons.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Phase index after requirements clarification

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update matrices as touched |
| P01 | Production shell and workspace composition | P00 | DONE; corrective P01-C1 DONE through PR #141 |
| P02 | Canonical ledger and transaction operations | P00 | DONE; retain |
| P03 | Transaction Editor, Ledger Register, and Journal Pane | P01, P02 | READY for corrective/new slices |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | DONE through PR #137 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138 |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140 |
| P09 | Inventory and supplies | P02 | DONE through PR #142; corrective P09-C1 VERIFYING |
| P10 | Period close, reopening, and factual audit history | P02, P06 | BLOCKED |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | BLOCKED |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | BLOCKED |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | BLOCKED |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | BLOCKED |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Focused documents for current UI/accounting work:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/accounting/transaction-editor-and-journal.md`
- `doc/workflow/development-workflow.md`

`doc/architecture/production-workspace.md` was removed. Do not re-add it or list it as required reading.

## 5. Established product decisions after clarification

- One production JavaFX application.
- H2 is authoritative for accepted operational/accounting data.
- Existing JPA/Hibernate model and Flyway migrations are the schema foundation.
- No parallel ledgers, budget stores, import stores, or panel frameworks.
- Write services own validation and transactions.
- Query services return projections for panels and reports.
- Constructor injection is preferred.
- Left Navigation under Accounting must include Banking and Inspect Journal.
- Left Navigation must not include Schedules.
- Left Navigation must not include Import/Export Jobs as a separate function.
- No approval queue or formal approval/rejection workflow.
- Reconciliation approve/reject semantics are replaced by saved comparison/reconciliation state.
- Notes and factual audit history are in scope.
- Reports belong in `REPORT_LIBRARY`.
- The former Schedules function is eliminated.
- Import/Export Jobs is eliminated as both a panel and generic durable job-tracking function.
- Fixed assets are distinct from inventory and require H2-backed asset/depreciation records.
- Inventory/supplies are distinct from fixed assets and require H2-backed item/movement records.
- Production widgets with visible non-blank display text should expose their full text on hover, except text boxes and custom-help tooltip cases.
- Disabled placeholder Delete buttons are not part of the UI contract; a Delete button must perform a real supported operation.

## 6. Completed phases and slices

### P00 — Documentation and implementation inventory

Status: DONE, with clarification updates as touched.

### P01 — Production shell and workspace composition

Status: DONE, retrofit as touched.

Corrective slices:

- P01-C1 Full-text hover tooltips for production widgets: DONE through PR #141.

### P02 — Canonical ledger and transaction operations

Status: DONE, retain.

### P03 — Transaction Editor, Ledger Register, and Journal Pane

Status: READY for corrective/new slices.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.

### P04 — Persistent budgeting

Status: DONE, retrofit as touched.

### P05 — Banking configuration and statement import

Status: DONE through PR #137.

Completed slices:

- P05-S1 Bank and bank-account model: DONE.
- P05-S2 Banking panel under Accounting: DONE.
- P05-S3 Statement import normalization and matching: DONE.
- P05-S4 Cleared-state mapping to ledger bank lines: DONE.

Validation recorded in PR #137: focused banking/import/reconciliation tests and full local `mvn clean verify` passed with 266 tests run and 9 skipped. No GitHub workflow runs were available for the merge commit.

## 7. Active and recent phase contracts

# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`
**Status:** DONE through PR #138
**Depends on:** P05

Completed deliverables: removed approval semantics; added configured-account reconciliation comparison; added unresolved report summaries backed by H2 run records.
Known follow-up: per-line cleared-state resolution choices and edit-existing reconciliation workflow remain later P06/P10/P11 work.

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`
**Status:** DONE through PR #139

Completed deliverables: removed `SchedulesPanel`, production factory route, navigation item, schedule runbook sidecar methods, schedule runbook formatting tests, and related operation-matrix/persistence-inventory references. `AppPanelId.SCHEDULES` remains only as a retired compatibility enum value for legacy switch/preset paths; it has no product route.

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`
**Status:** DONE through PR #140
**Depends on:** P02

Required behavior: implement Asset Register add/edit and depreciation behavior through H2-backed records and canonical accounting transactions. Assets are separate from Inventory items. Depreciation schedules define calculation only; running depreciation creates actual accounting transactions through the canonical transaction service.

### P08-S1 — H2 fixed asset register and depreciation runs

Status: DONE through PR #140.

Completed deliverables: V55 fixed asset/depreciation-run migration; `FixedAsset` and `FixedAssetDepreciationRun` JPA entities; `FixedAssetService` create/update/list/depreciation-run behavior; depreciation runs create canonical `Txn`/`TxnSplit` rows; Asset Register and Depreciation Runs panels read/write through `FixedAssetService`; asset/depreciation runbook sidecars removed; docs and focused tests added/updated, including a Flyway migration-version uniqueness guardrail.

# P01-C1 — Full-text hover tooltips

**Selector:** `PHASE=P01`
**Status:** DONE through PR #141

Completed deliverables: `FullTextTooltipInstaller` utility; production `MainApp` installation; UI design-rule documentation; focused JavaFX tests.

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`
**Status:** DONE through PR #142; corrective P09-C1 VERIFYING
**Depends on:** P02

Required behavior: implement genuine Inventory item add and movement history; remove runbook subpane; use canonical transactions when financially relevant.

### P09-S1 — H2 inventory items and movement history

Status: DONE through PR #142.

Completed deliverables: V56 inventory item/movement migration; `InventoryItem` and `InventoryMovement` JPA entities; `InventoryService` create/update/list/movement behavior; `InventoryPanel` reads/writes through `InventoryService`; inventory text runbook sidecar removed from `UiWorkspaceDataStore` and `RunbookPersistence`; focused service tests and docs added/updated.

### P09-C1 — Inventory UI design-rule correction and disabled Delete cleanup

Status: VERIFYING.

Branch: `codex/P09-C1-inventory-ui-rules`
Pull request: pending
Completed deliverables in branch: Inventory global New/Save hooks; dirty-state tracking; validation highlighting; common money/date parsing and formatting; unconstrained inventory tables; per-company table-state persistence; disabled Delete placeholder buttons removed from Banking, Asset Register, Depreciation Runs, Inventory, and Reconciliation; docs and source guardrail tests updated.
Remaining deliverables: open draft PR, inspect GitHub Actions, fix failures, perform desktop visual review, and update this plan.
Next exact action: open draft PR and inspect GitHub Actions output.

# P10 — Period close, reopening, and factual audit history

**Selector:** `PHASE=P10`
**Status:** BLOCKED
**Depends on:** P02, P06

Required behavior: implement calculated period close/reopen while keeping factual Audit History and no approval/rejection workflow.

# P11 — Report Library

**Selector:** `PHASE=P11`
**Status:** BLOCKED
**Depends on:** P02, P04, P06, P08, P09, P10

# P12 — Administration, company lifecycle, preferences, and Funds edit

**Selector:** `PHASE=P12`
**Status:** BLOCKED
**Depends on:** P01, P02

# P13 — Data exchange and diagnostics without Import/Export Jobs

**Selector:** `PHASE=P13`
**Status:** BLOCKED
**Depends on:** P02, P05, P12

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** BLOCKED
**Depends on:** P03-P13 except eliminated P07

## 8. Cross-cutting validation matrix

Every phase adds applicable tests:

- accounting invariants;
- validation;
- state transitions;
- service transaction rollback;
- in-memory H2 repositories;
- migrations;
- JavaFX layout and command routing;
- TestFX workflow checks where UI behavior is central;
- report/export smoke tests.

## 9. Pull-request completion checklist

Before a PR is ready:

- selected phase/slice is recorded;
- branch started from then-current `main`;
- scope is one coherent slice;
- relevant `doc/` files and this plan are updated;
- final diff inspected;
- no unintended/generated/user-data files changed;
- no placeholders or swallowed exceptions;
- no SQL in JavaFX panels;
- no accounting policy in repositories;
- no JavaFX controls in models;
- no sidecar/static store used as authoritative persistence;
- new migrations are nondestructive;
- applicable unit/service/H2/migration/regression/layout tests exist;
- `mvn clean verify` passes;
- GitHub confirms required checks when workflows exist;
- PR description records actual validation;
- required desktop visual check is complete;
- branch/PR/head/test/next-action handoff is recorded here;
- code is reviewed to ensure compliance with design documents.

## 10. Current next action

Execute validation for:

```text
PHASE=P09
SLICE=P09-C1
```

Open a draft PR for `codex/P09-C1-inventory-ui-rules`, inspect GitHub Actions output, and fix any failures.
