---
plan_version: 13
active_phase: P06
active_slice: P06-C1
active_status: IN_PROGRESS
active_branch: codex/P06-C1-bank-reconciliation-workspace
active_pull_request: "#145"
active_head: 148dec7bfbcb2314bc9178cccaea511dc223bf15
next_action: "Inspect current reconciliation, statement import, cleared-state, ledger-line, and period-close code, then implement the full Bank Reconciliation workspace."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P08-C1 as DONE through PR #144 and activates P06-C1 for the full Bank Reconciliation workspace.

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
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138; corrective P06-C1 IN_PROGRESS |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140; corrective P08-C1 DONE through PR #144 |
| P09 | Inventory and supplies | P02 | DONE through PR #142; corrective P09-C1 DONE through PR #143 |
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
- Bank Reconciliation must become a full statement-to-ledger matching workspace, not only a saved comparison table.

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
**Status:** DONE through PR #138; corrective P06-C1 IN_PROGRESS
**Depends on:** P05

Completed deliverables: removed approval semantics; added configured-account reconciliation comparison; added unresolved report summaries backed by H2 run records.
Known follow-up: per-line cleared-state resolution choices and edit-existing reconciliation workflow remain later P06/P10/P11 work.

### P06-C1 — Full Bank Reconciliation workspace

Status: IN_PROGRESS.
Branch: `codex/P06-C1-bank-reconciliation-workspace`
Pull request: #145

Purpose: replace the partial comparison-only surface with the requirements-driven Bank Reconciliation workspace.

Required behavior:

- Show only configured bank accounts linked through the Banking panel, not every chart account of type BANK.
- Read transactions for the selected configured bank account.
- Calculate beginning balance from the most recent closed period ending before the statement date, activity after that close through the statement date, and ending book balance at the statement date.
- Show balance cards for beginning balance, book balance at statement date for all transactions, book balance at statement date for cleared-only transactions, statement ending balance, and difference.
- Store cleared state on the ledger transaction line involving the bank account.
- Treat transactions with multiple bank-account lines by reconciling each bank-account ledger line independently.
- Support statement sources for manual entry, CSV import, OFX import, and QIF import.
- Match imported/manual statement entries to internal ledger bank-account lines using statement matching rules.
- Provide all four cleared-state mismatch policies: warn only; overwrite ledger cleared state; never overwrite and require manual resolution; decide per imported line.
- Provide a two-table matching workspace with imported/manual statement entries on one side and ledger bank-account lines on the other.
- Provide matching actions: auto match, match selected, unmatch, mark cleared, and resolve difference.
- Report unmatched ledger transactions, unmatched statement entries, amount mismatches, date mismatches, duplicate possible matches, cleared-state mismatches, beginning balance differences, and ending balance differences.
- Allow users to save unresolved reconciliation reports.
- Allow users to start a new reconciliation or edit an existing reconciliation.
- Replace the current started/completed/failed run model and any approve/reject semantics with the new reconciliation session and report workflow.

Definition of done for P06-C1:

- JavaFX panel layout matches the approved Bank Reconciliation layout direction, excluding external annotation callouts.
- UI uses service/query boundaries and contains no SQL.
- Durable reconciliation sessions, statement entries, match decisions, mismatch resolutions, and saved reports are H2-backed through nondestructive migrations.
- Cleared-state updates route through the canonical ledger service boundary and apply the selected overwrite/warn/manual policy.
- Tests cover configured-account filtering, balance calculations from last close through statement date, matching categories, mismatch policy behavior, save-unresolved, edit-existing, and no approval/rejection workflow.
- `doc/interface-operation-matrix.md` is updated to mark the old partial surface replaced.

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`
**Status:** DONE through PR #139

Completed deliverables: removed `SchedulesPanel`, production factory route, navigation item, schedule runbook sidecar methods, schedule runbook formatting tests, and related operation-matrix/persistence-inventory references. `AppPanelId.SCHEDULES` remains only as a retired compatibility enum value for legacy switch/preset paths; it has no product route.

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`
**Status:** DONE through PR #140; corrective P08-C1 DONE through PR #144
**Depends on:** P02

Required behavior: implement Asset Register add/edit and depreciation behavior through H2-backed records and canonical accounting transactions. Assets are separate from Inventory items. Depreciation schedules define calculation only; running depreciation creates actual accounting transactions through the canonical transaction service.

### P08-S1 — H2 fixed asset register and depreciation runs

Status: DONE through PR #140.

Completed deliverables: V55 fixed asset/depreciation-run migration; `FixedAsset` and `FixedAssetDepreciationRun` JPA entities; `FixedAssetService` create/update/list/depreciation-run behavior; depreciation runs create canonical `Txn`/`TxnSplit` rows; Asset Register and Depreciation Runs panels read/write through `FixedAssetService`; asset/depreciation runbook sidecars removed; docs and focused tests added/updated, including a Flyway migration-version uniqueness guardrail.

### P08-C1 — Asset Register selector display labels

Status: DONE through PR #144.

Completed deliverables: Added account and fund `StringConverter` display labels for Asset Register selectors so combo boxes show `code — name` instead of Java object identity strings; added focused source-level label-format test.

# P01-C1 — Full-text hover tooltips

**Selector:** `PHASE=P01`
**Status:** DONE through PR #141

Completed deliverables: `FullTextTooltipInstaller` utility; production `MainApp` installation; UI design-rule documentation; focused JavaFX tests.

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`
**Status:** DONE through PR #142; corrective P09-C1 DONE through PR #143
**Depends on:** P02

Required behavior: implement genuine Inventory item add and movement history; remove runbook subpane; use canonical transactions when financially relevant.

### P09-S1 — H2 inventory items and movement history

Status: DONE through PR #142.

Completed deliverables: V56 inventory item/movement migration; `InventoryItem` and `InventoryMovement` JPA entities; `InventoryService` create/update/list/movement behavior; `InventoryPanel` reads/writes through `InventoryService`; inventory text runbook sidecar removed from `UiWorkspaceDataStore` and `RunbookPersistence`; focused service tests and docs added/updated.

### P09-C1 — Inventory UI design-rule correction and disabled Delete cleanup

Status: DONE through PR #143.

Completed deliverables: Inventory global New/Save hooks; item-editor subpanel navigation from New Item, Edit Selected, and table double-click; dirty-state tracking; validation highlighting; common money/date parsing and formatting; unconstrained inventory tables; per-company table-state persistence; disabled Delete placeholder buttons removed from Banking, Asset Register, Depreciation Runs, Inventory, and Reconciliation; docs and source guardrail tests updated.

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

Execute implementation for:

```text
PHASE=P06
SLICE=P06-C1
```

Inspect current reconciliation, statement import, cleared-state, ledger-line, and period-close code, then implement the full Bank Reconciliation workspace.
