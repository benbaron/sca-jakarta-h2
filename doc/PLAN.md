---
plan_version: 18
active_phase: P03
active_slice: P03-C4
active_status: READY
active_branch: pending
active_pull_request: pending
active_head: pending
next_action: "Start P03-C4 from current main: create codex/P03-C4-transaction-editor-journal-redesign, inspect current TransactionEditorPanel and Journal/Inspect Journal panes plus the legacy NonprofitAccounting UI design reference, then implement the redesigned Transaction Editor and Journal panes."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P05-C5 as DONE through PR #148 and activates P03-C4 as the next unblocked corrective slice. P03-C4 is expanded from Transaction Editor table-state hardening into a Transaction Editor and Journal Pane redesign, using `benbaron/NonprofitAccounting` `src/main/java/nonprofitbookkeeping/ui` only as a design reference.

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
| P03 | Transaction Editor, Ledger Register, and Journal Pane | P01, P02 | READY; corrective P03-C4 READY |
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

### P03 — Transaction Editor, Ledger Register, and Journal Pane

Status: READY; corrective P03-C4 READY.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.

### P03-C4 — Transaction Editor and Journal Pane redesign

Status: READY.
Branch: pending
Pull request: pending

Purpose: redesign the Transaction Editor and Journal Pane for usable, laptop-width transaction entry and review, using the legacy `benbaron/NonprofitAccounting` JavaFX UI package only as a design reference. This slice replaces the narrow table-state-only scope formerly recorded for P03-C4.

Design-reference rules:

- Inspect `benbaron/NonprofitAccounting` `src/main/java/nonprofitbookkeeping/ui` for layout and interaction ideas only.
- Do not port the legacy persistence, model, static services, SQL access, or sidecar behavior.
- Keep `sca-jakarta-h2` H2/JPA, service/query/domain/repository boundaries authoritative.
- JavaFX panels must not contain SQL and must not become the accounting authority.

Required Transaction Editor behavior:

- Preserve existing transaction-entry behavior, service boundaries, validation, and canonical transaction persistence.
- Replace the overcrowded single scrollable transaction editor with separate pages, tabs, or subpanels so the editor is usable on laptop-width workspaces.
- Provide a header/setup page or subpanel containing:
  - New/Edit Journal Entry title;
  - transaction date;
  - memo;
  - live Debit total;
  - live Credit total;
  - live Difference;
  - status badge such as balanced/needs attention;
  - validation message such as transaction not balanced.
- Provide an Entry Lines page or subpanel containing:
  - entry-line table;
  - Add Line;
  - Duplicate;
  - Remove;
  - account selector/display;
  - one-sided Debit and Credit money entry;
  - blank-entry-row behavior where appropriate;
  - per-company table-state persistence for column width/order/sort;
  - sortable, resizable, reorderable columns unless a column must be fixed for editor-cell correctness.
- Provide an Additional Details page or subpanel containing cards or grouped sections for:
  - Party / Document: To / From and Check #;
  - Bank / Reconciliation: Bank, Clearing Bank, and Reconciled state;
  - Budget / Fund: Budget Tracking and Fund Name.
- Provide a Donation Subschedule page or subpanel containing:
  - Use Donation Schedule toggle;
  - Donation ID;
  - Donor ID;
  - Donor Name;
  - Edit Selected Donor action if donor editing is currently supported by H2/service boundaries, otherwise disabled with a clear explanation.
- Provide Supplemental Schedule/detail pages or subpanels for:
  - Receivable;
  - Payable;
  - Prepaid Expense;
  - Deferred Revenue;
  - Other Asset;
  - Other Liability.
- Treat supplemental schedules as transaction supplemental detail panels, not as the eliminated generic Schedules module.
- Each supplemental detail table must provide only real supported actions. Unsupported actions must be absent or disabled with clear explanation.
- Do not reintroduce the eliminated Schedules navigation item, old schedule runbook sidecars, or generic durable job/schedule tracking.

Required Journal Pane behavior:

- Change the Journal Pane as part of P03-C4, not in a later slice.
- Display transactions as grouped journal-entry blocks rather than isolated raw split rows when practical with current projections.
- Each journal transaction block should show all posting lines in stored order, with debit and credit line amounts aligned in separate columns.
- Journal display should support the following visible columns/regions where data exists:
  - Date;
  - Account Title and Description;
  - Fund;
  - Cleared;
  - Debit;
  - Credit;
  - Transaction ID;
  - Supplemental.
- Transaction ID should navigate to the transaction in the appropriate editor/register path if supported by current routing.
- Supplemental indicator/button such as `Schedules (n)` may be used only as a transaction supplemental detail viewer, not as the eliminated generic Schedules feature.
- Journal toolbar/actions should include New, Edit, Delete, and Refresh only where each action performs a real operation through current services. Delete must continue to route through the supported correction/delete policy and must not be a placeholder.
- Journal display must retain horizontal scrolling where needed and avoid clipping on laptop-width screens.

Definition of done for P03-C4:

- `TransactionEditorPanel` is reorganized into task-sized pages/subpanels and no longer appears as one overcrowded vertical scroll.
- Transaction Editor includes header, entry lines, additional details, donation subschedule, and supplemental detail panels as described above.
- Journal Pane is updated to grouped transaction/journal-entry presentation and supports the required visible regions/actions where current data projections permit.
- Existing transaction save/correction behavior is preserved and remains service-backed.
- Existing Ledger Register and Inspect Journal routing remains coherent after the redesign.
- All tables touched by the redesign meet UI table rules for scrollability and table-state persistence where feasible.
- Focused source/layout/unit tests or guardrails cover the new page/subpanel structure, journal grouping contract, table-state wiring, and absence of reintroduced generic Schedules behavior.
- `doc/interface-operation-matrix.md`, `doc/ui/editor-guidelines.md`, and this plan are updated to reflect the final implemented UI.
- Desktop visual validation confirms Transaction Editor and Journal Pane usability on laptop-width workspaces.

Next exact action: create `codex/P03-C4-transaction-editor-journal-redesign` from current `main`, inspect the current Transaction Editor and Journal/Inspect Journal panes, inspect the legacy design-reference UI files, then implement the redesigned Transaction Editor and Journal panes.

### P04 — Persistent budgeting

Status: DONE, retrofit as touched.

### P05 — Banking configuration and statement import

Status: DONE through PR #137; corrective P05-C5 DONE through PR #148.

Completed slices:

- P05-S1 Bank and bank-account model: DONE.
- P05-S2 Banking panel under Accounting: DONE.
- P05-S3 Statement import normalization and matching: DONE.
- P05-S4 Cleared-state mapping to ledger bank lines: DONE.

Validation recorded in PR #137: focused banking/import/reconciliation tests and full local `mvn clean verify` passed with 266 tests run and 9 skipped. No GitHub workflow runs were available for the merge commit.

### P05-C5 — Banking panel horizontal master-panel layout correction

Status: DONE through PR #148.

Completed deliverables: Banking panel main `SplitPane` now uses `Orientation.VERTICAL`, placing Financial Institutions above Configured Bank Accounts; existing Banking behavior, persistence, buttons, selectors, editor forms, and service boundaries are preserved; bank and configured-account tables grow within their sections; `BankingPanelSourceTest` guards the top/bottom split contract.

Validation: Maven PR Tests run 29030312409 completed successfully, and laptop-width desktop visual validation was approved by the user.

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

Begin implementation for:

```text
PHASE=P03
SLICE=P03-C4
```

Create `codex/P03-C4-transaction-editor-journal-redesign` from current `main`, inspect current `TransactionEditorPanel` and Journal/Inspect Journal panes, inspect the legacy design-reference UI files, then implement the redesigned Transaction Editor and Journal panes.
