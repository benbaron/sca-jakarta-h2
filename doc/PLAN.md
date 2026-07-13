---
plan_version: 32
active_phase: P12
active_slice: P12-S1
active_status: IN_PROGRESS
active_branch: codex/P12-S1-fund-lifecycle-rules
active_pull_request: "TBD"
active_head: eb0ff9a2769d9935ba2ba89a74152dc6a8ad57f7
next_action: "Open the P12-S1 draft PR, then implement stable-ID fund editing, lifecycle fields, protected deletion, deactivation, service tests, and a service-backed Funds workspace."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P11-S1 as user-verified and merged through PR #158, then activates P12-S1 for stable-ID Funds editing and safe delete/deactivate lifecycle rules.

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
| P03 | Journal workspace and canonical transaction operations | P01, P02 | DONE through corrective P03-C9 / PR #154 |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | DONE through PR #137; corrective P05-C5 DONE through PR #148 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147 |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140; corrective P08-C1 DONE through PR #144 |
| P09 | Inventory and supplies | P02 | DONE through PR #142; corrective P09-C1 DONE through PR #143 |
| P10 | Period close, reopening, and factual audit history | P02, P06 | DONE through P10-S1 / PR #156 and P10-C1 / PR #157 |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | DONE through P11-S1 / PR #158 |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | IN_PROGRESS; P12-S1 active |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | BLOCKED by P12 |
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
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/reporting/report-library.md`
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
- Bank Reconciliation must be a full statement-to-ledger matching workspace, not only a saved comparison table.
- Transaction supplemental schedule/detail panels are not the eliminated generic Schedules function; they are per-transaction detail editors and viewers.
- Period close uses calculated or custom date ranges rather than an accounting-period table as the business authority.
- Reopening is supported and creates factual audit history; P10 must not expose approval/rejection semantics.
- The desktop JPA bootstrap explicitly selects the Hibernate provider configured by `persistence.xml`; it does not rely on launcher-sensitive Jakarta Persistence service discovery.
- Report preview, export, and drill-through use one immutable validated report request.
- Visible report dates and money follow active-company preferences; machine CSV remains unadorned and stable.
- Funds are edited by stable database ID. Referenced funds are retained and deactivated; physical deletion is limited to unreferenced funds after explicit confirmation.

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

Status: DONE through corrective P03-C9 / PR #154.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.
- P03-C4 Transaction Editor and Journal Pane redesign: DONE through PR #149.
- P03-C5 Persisted Transaction Editor supplemental details: DONE through PR #150.
- P03-C6 Unified Journal workspace port: DONE through PR #151.
- P03-C7 Journal UI design-rule compliance: DONE through PR #152.
- P03-C8 Journal compliance cleanup and verification: DONE through PR #153.
- P03-C9 Remove visible Journal table commentary: DONE through PR #154; Maven PR Tests runs `29132445663` and `29132508479` passed before merge.

Known remaining P03 limitation:

- `TransactionView.Line` does not yet expose the authoritative line-level cleared flag. The Journal does not claim authoritative mixed cleared/uncleared transaction detail; an explicit line-level projection remains a later corrective slice.

### P10 — Period close, reopening, and factual audit history

Status: DONE through P10-S1 / PR #156 and corrective P10-C1 / PR #157.

#### P10-S1 — Calculated period close and reopen service

Branch: `codex/P10-S1-period-close-implementation`
Pull request: #156, merged into `main` at `fc9e8ddb5bb2583ec744ff7fe6e9ce7ba07a5e8a`
Tested implementation head: `2462a591de7965d69c8909991443011665daba8a`

Purpose: replace the run-record/approval-oriented placeholder with authoritative calculated or custom date-range close state, reopening, and factual audit history while preserving canonical ledger, reconciliation protection, and transaction-service boundaries.

Completed deliverables:

- Added nondestructive V60 H2 tables `period_close_range` and `period_close_event`, scoped by company and date range.
- Added `PeriodCloseRangeService` close, overlap validation, list, lookup, reopen-policy, and factual event/audit operations.
- Kept legacy `PeriodCloseService`/run records separate as compatibility-only data instead of mixing two authorities in one service.
- Added company-scoped `ClosedPeriodRangeException` and read projections for close ranges and events.
- Wired `TransactionEntryService` and `TransactionCorrectionService` to authoritative range checks inside canonical ledger transactions.
- Preserved completed-reconciliation protection and allowed reversal of a prior-period transaction into an open destination date.
- Replaced `PeriodCloseRunsPanel` run/approval controls with calculated/custom Close Range, Reopen Selected, Refresh, range-state table, and factual event-history table.
- Removed approval/rejection and direct repository-write behavior from the production Period Close workspace.
- Added service restart/history/company/policy tests, transaction entry enforcement tests, correction rollback/reversal tests, migration uniqueness coverage, and UI source guardrails.
- Updated the operation matrix, persistence authority inventory, and period/correction policy.

Validation:

- Maven PR Tests run `29164587670` passed for authoritative range service, persistence, restart, and audit behavior.
- Maven PR Tests run `29164655355` passed after canonical transaction-entry/correction enforcement.
- Maven PR Tests run `29164725119` passed after JavaFX runtime wiring and Period Close workspace replacement.
- Maven PR Tests run `29164894557` passed on the complete implementation, test, and documentation head.
- PR #156 merged on 2026-07-11.
- Desktop validation exposed the provider-discovery startup failure addressed by P10-C1.

#### P10-C1 — Explicit desktop JPA provider bootstrap

Branch: `codex/P10-C1-explicit-jpa-provider`
Pull request: #157, merged into `main` at `78cd26748181af9665229318231c5bf8ae4a7d0c`
Tested head: `0d82d960d2a47529eb5883fe8ccf388eb8bc2551`

Completed deliverables:

- Replaced `Persistence.createEntityManagerFactory(...)` with explicit `HibernatePersistenceProvider` bootstrap while retaining `META-INF/persistence.xml`, `scaLedgerPU`, RESOURCE_LOCAL transactions, and JDBC overrides.
- Added focused diagnostics for a missing or incompatible Hibernate runtime dependency.
- Added a regression test that empties the global provider resolver and verifies file-mode `Jpa` still starts.
- Maven PR Tests runs `29177308667` and `29177357943` passed.
- PR #157 merged and the user directed that the slice be marked DONE.

Known P10 follow-up:

- `AccountingPeriod`/`AccountingPeriodService` and legacy period-close run artifacts remain compatibility structures, not close authority.
- `REQUIRE_FORMAL_ADJUSTMENT` blocks direct reopen; a specialized formal-adjustment workflow remains a deliberate later slice rather than a prerequisite for P11.

### P11 — Report Library

Status: DONE through P11-S1 / PR #158.

#### P11-S1 — Typed report catalog and parameters

Branch: `codex/P11-S1-report-catalog-parameters`
Pull request: #158, merged into `main` at `eb0ff9a2769d9935ba2ba89a74152dc6a8ad57f7`
Tested head: `4f779e64b7ce8ec8d64a748c9b513a3b983463bf`

Purpose: replace string-based report selection and duplicated parameter construction with a typed catalog and one validated request shared by preview, export, and Journal drill-through.

Completed deliverables:

- Added typed `ReportDefinition`, stable `ReportFundOption`, validated `ReportRequest`, immutable `ReportResult`, and `ReportExecutionService`.
- Cataloged the four core H2-backed reports and all existing workbook-semantic templates; no selectable placeholder report remains.
- Added report-specific as-of/date-range controls, active-fund filtering with All Funds, and conditional row limits.
- Extended semantic reports to accept applicable fund and row-limit parameters.
- Added active-company date/money formatting for visible core report text while preserving ISO/raw numeric CSV.
- Reused the same request/result for preview and export and included request context in Journal drill-through.
- Added company-owned Report Library divider state.
- Added catalog/request validation, H2 fund-filter/format integration, and source guardrail tests.
- Added `doc/reporting/report-library.md` and updated the Report Library operation-matrix row.

Validation:

- Maven PR Tests run `29178671839` passed after the typed panel integration.
- Maven PR Tests run `29178741120` passed with catalog/request, fund-filter/format integration, and source guardrail tests.
- Maven PR Tests run `29178922845` passed after restoring focused plan/matrix scope.
- Maven PR Tests run `29178972494` passed on the focused implementation, tests, report documentation, and operation-matrix head `9c8d6f3c7ea32772345444d6f44d542212ddcc70`.
- Maven PR Tests run `29179022737` passed on final implementation head `4f779e64b7ce8ec8d64a748c9b513a3b983463bf`.
- User desktop acceptance was recorded on PR #158 and the PR merged on 2026-07-12.

### P12 — Administration, company lifecycle, preferences, and Funds edit

Status: IN_PROGRESS; P12-S1 active.

#### P12-S1 — Stable-ID Funds editing and lifecycle rules

Branch: `codex/P12-S1-fund-lifecycle-rules`
Pull request: TBD
Base: current `main` at `eb0ff9a2769d9935ba2ba89a74152dc6a8ad57f7`

Purpose: replace code-keyed pseudo-editing with stable-ID fund create/update behavior and enforce safe deactivate/delete rules through `FundAdminService`.

Planned deliverables:

- Add a stable-ID fund command covering code, name, type, active state, parent, effective dates, and restriction text.
- Validate code uniqueness, effective-date ordering, and parent/self/cycle rules in the service transaction.
- Calculate authoritative usage across transaction splits, budgets, assets, inventory, aliases, transfers, and child funds.
- Allow physical deletion only when the fund has no references; direct users to deactivation when history exists.
- Replace the one-page Funds panel with separate table/editor regions, company-aware date controls, explicit New/Save/Delete Unused operations, and honest validation/status text.
- Add service integration tests, UI guardrails, fund-lifecycle documentation, and focused matrix/inventory updates.

Remaining before DONE:

- Open the draft PR.
- Implement and validate the service, panel, tests, and documentation.
- Perform laptop-width desktop validation and merge the PR.

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
