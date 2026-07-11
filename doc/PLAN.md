---
plan_version: 26
active_phase: P03
active_slice: P03-C9
active_status: VERIFYING
active_branch: codex/P03-C9-remove-journal-commentary
active_pull_request: "#154"
active_head: 86820cd514dc00fb71e9d1f3fdd224cb9adb6103
next_action: "Visually confirm the Journal tables no longer show explanatory commentary and retain their scrolling, split-region, formatting, and saved-state behavior; then merge PR #154 and mark P03-C9 DONE."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P03-C6 through P03-C8 as merged through PRs #151–#153. P03-C9 removes visible implementation commentary from Journal table regions while preserving the overall editor scrollbar, table `SplitPane` wrappers, table interaction behavior, company-owned state, company-aware formatting, and canonical accounting services.

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
| P03 | Journal workspace and canonical transaction operations | P01, P02 | READY; corrective P03-C9 VERIFYING |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | DONE through PR #137; corrective P05-C5 DONE through PR #148 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147 |
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
- Left Navigation under Accounting exposes one Journal destination rather than separate Ledger Register, Transaction Editor, and Inspect Journal destinations.
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
- Transaction supplemental schedule/detail panels are not the eliminated generic Schedules function; they are per-transaction detail editors and viewers.

## 6. Completed phases and slices

### P00 — Documentation and implementation inventory

Status: DONE, with clarification updates as touched.

### P01 — Production shell and workspace composition

Status: DONE, retrofit as touched.

Corrective slices:

- P01-C1 Full-text hover tooltips for production widgets: DONE through PR #141.

### P02 — Canonical ledger and transaction operations

Status: DONE, retain.

### P03 — Journal workspace and canonical transaction operations

Status: READY; corrective P03-C9 VERIFYING.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.
- P03-C4 Transaction Editor and Journal Pane redesign: DONE through PR #149.
- P03-C5 Persisted Transaction Editor supplemental details: DONE through PR #150.
- P03-C6 Unified Journal workspace port: DONE through PR #151.
- P03-C7 Journal UI design-rule compliance: DONE through PR #152.
- P03-C8 Journal compliance cleanup and verification: DONE through PR #153.

### P03-C9 — Remove visible Journal table commentary

Status: VERIFYING.
Branch: `codex/P03-C9-remove-journal-commentary`
Pull request: #154
Head before this plan update: `86820cd514dc00fb71e9d1f3fdd224cb9adb6103`

Purpose: remove visible implementation commentary from the Journal table regions without removing the actual table behavior or layout contract.

Completed deliverables:

- Removed the label text stating that columns are sortable, resizable, rearrangeable, and independently scrollable.
- Retained each table inside its dedicated `SplitPane` wrapper without adding an empty commentary pane.
- Preserved the overall Journal editor vertical scrollbar, nested major-section dividers, unconstrained table resize policy, sortable/resizable/reorderable columns, company-owned table state, company-aware date/money formatting, and service-backed Journal operations.
- Updated `JournalWorkspacePortSourceTest` to require the table wrapper and prohibit the removed commentary text.
- Maven PR Tests run `29132445663` completed successfully for head `86820cd514dc00fb71e9d1f3fdd224cb9adb6103`.

Remaining before DONE:

- Visually confirm at laptop width that no table commentary labels remain.
- Confirm the Journal, entry-line, and supplemental tables still scroll and resize normally.
- Merge PR #154, then mark P03-C9 DONE.

Known remaining functional limitation:

- `TransactionView.Line` does not yet expose the authoritative line-level cleared flag. The Journal does not claim authoritative mixed cleared/uncleared transaction detail; an explicit line-level projection remains a later corrective slice.

## 7. Active and recent phase contracts

# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`
**Status:** DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147
**Depends on:** P05

Completed deliverables: removed approval semantics; added configured-account reconciliation comparison; added unresolved report summaries backed by H2 run records.

### P06-C1 — Full Bank Reconciliation workspace

Status: DONE through PR #146.

Completed deliverables: V57 H2 tables for durable reconciliation sessions and match/resolution rows; `BankReconciliationWorkspaceService`; configured-account filtering; session start/load/list; balance calculations; manual statement-line entry; CSV/OFX/QIF import parsing; matching, unmatching, mark-cleared, resolve-difference, save-unresolved, and finalize; JavaFX reconciliation workspace with configured-account selector, session controls, four cleared-state policies, balance cards, statement source tabs, matching tables, comparison report, and saved reconciliation table.

### P06-C2 — Bank Reconciliation laptop layout correction

Status: DONE through PR #147.

Completed deliverables: Replaced the one-page reconciliation canvas with workflow tabs: Setup, Statement, Match, and Review / Save; moved configured-account/session/policy controls to Setup; moved manual/CSV/OFX/QIF statement entry and import controls to Statement; moved statement and ledger tables plus match/unmatch/mark-cleared/resolve controls to Match; moved balance cards, comparison report, and save/finalize controls to Review / Save.

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

Completed deliverables: V55 fixed asset/depreciation-run migration; `FixedAsset` and `FixedAssetDepreciationRun` JPA entities; `FixedAssetService` create/update/list/depreciation-run behavior; depreciation runs create canonical `Txn` and `TxnSplit` rows; Asset Register and Depreciation Runs panels read/write through `FixedAssetService`; asset/depreciation runbook sidecars removed; docs and focused tests added/updated, including a Flyway migration-version uniqueness guardrail.

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
