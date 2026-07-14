---
plan_version: 33
active_phase: P12
active_slice: P12-C1
active_status: IN_PROGRESS
active_branch: codex/P12-C1-reconcile-plan-ledger
active_pull_request: ""
active_head: c64be3f381a48a31e6b7fa85337129ad7116515c
next_action: "Review and merge the P12 plan-reconciliation pull request; then activate P12-S3 Company lifecycle and active-company authority from fresh current main."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision reconciles three overlapping P12 pull requests that reused `P12-S1`. It records the Fund lifecycle work from PR #159 as `P12-S1`, the Administration workspace hub from PR #160 as `P12-S2`, and PR #161 as a merged plan-only activation that did not implement company lifecycle. Company lifecycle is therefore assigned the new unimplemented slice `P12-S3`.

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
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | IN_PROGRESS; P12-C1 plan reconciliation active; P12-S1 and P12-S2 merged/VERIFYING; P12-S3 READY after reconciliation |
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
- `doc/administration/fund-lifecycle.md`
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
- Transaction supplemental schedule/detail panels are per-transaction detail editors and viewers, not the eliminated generic Schedules function.
- Period close uses calculated or custom date ranges rather than an accounting-period table as the business authority.
- Reopening is supported and creates factual audit history; P10 must not expose approval/rejection semantics.
- The desktop JPA bootstrap explicitly selects the Hibernate provider configured by `persistence.xml`; it does not rely on launcher-sensitive Jakarta Persistence service discovery.
- Report preview, export, and drill-through use one immutable validated report request.
- Visible report dates and money follow active-company preferences; machine CSV remains unadorned and stable.
- Funds are edited by stable database ID. Referenced funds are retained and deactivated; physical deletion is limited to unreferenced funds after explicit confirmation.
- The stable `SETTINGS` workspace destination hosts the Administration workspace; Preferences, Company Admin, and User Admin are tabs rather than separate shell identifiers.
- Company records in H2 are authoritative for company existence and active/inactive lifecycle; shell recent-company state may remember selections but must not create fictional companies.
- Companies and funds are deactivated rather than hard-deleted when referenced by accounting or operational records.

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
- P03-C9 Remove visible Journal table commentary: DONE through PR #154.

Known remaining P03 limitation:

- `TransactionView.Line` does not yet expose authoritative line-level cleared state. The Journal must not claim authoritative mixed cleared/uncleared transaction detail until that projection is added.

### P10 — Period close, reopening, and factual audit history

Status: DONE through P10-S1 / PR #156 and corrective P10-C1 / PR #157.

#### P10-S1 — Calculated period close and reopen service

Branch: `codex/P10-S1-period-close-implementation`  
Pull request: #156, merged into `main` at `fc9e8ddb5bb2583ec744ff7fe6e9ce7ba07a5e8a`  
Tested implementation head: `2462a591de7965d69c8909991443011665daba8a`

Completed deliverables:

- Added H2-backed `period_close_range` and `period_close_event` records.
- Added company-scoped close, overlap validation, list, lookup, reopen-policy, and factual event/audit operations.
- Enforced authoritative close ranges inside canonical transaction entry and correction services.
- Replaced run/approval controls with calculated/custom Close Range, Reopen Selected, Refresh, range-state, and factual-history controls.
- Preserved completed-reconciliation protection and open-destination reversal behavior.
- Added focused persistence, service, correction, migration, UI, and documentation coverage.

#### P10-C1 — Explicit desktop JPA provider bootstrap

Branch: `codex/P10-C1-explicit-jpa-provider`  
Pull request: #157, merged into `main` at `78cd26748181af9665229318231c5bf8ae4a7d0c`  
Tested head: `0d82d960d2a47529eb5883fe8ccf388eb8bc2551`

Completed deliverables:

- Replaced launcher-sensitive provider discovery with explicit `HibernatePersistenceProvider` bootstrap.
- Retained `META-INF/persistence.xml`, `scaLedgerPU`, RESOURCE_LOCAL behavior, migrations, and JDBC overrides.
- Added focused missing-provider diagnostics and a provider-resolver regression test.
- Maven PR Tests runs `29177308667` and `29177357943` passed.
- The user directed that the slice be marked DONE.

Known P10 follow-up:

- `AccountingPeriod`/`AccountingPeriodService` and legacy period-close run artifacts remain compatibility structures, not close authority.
- `REQUIRE_FORMAL_ADJUSTMENT` blocks direct reopen; a specialized formal-adjustment workflow remains a later slice.

### P11 — Report Library

Status: DONE through P11-S1 / merged PR #158 and owner verification.

#### P11-S1 — Typed report catalog and parameters

Branch: `codex/P11-S1-report-catalog-parameters`  
Pull request: #158, merged into `main` at `eb0ff9a2769d9935ba2ba89a74152dc6a8ad57f7`  
Tested head: `4f779e64b7ce8ec8d64a748c9b513a3b983463bf`

Completed deliverables:

- Added typed report definitions, immutable validated requests/results, fund selection, report-specific dates, and conditional row limits.
- Reused one request/result for preview, export, and Journal drill-through.
- Added active-company visible formatting while preserving stable machine CSV.
- Preserved TEXT, CSV, PDF, and XLSX export.
- Added company-owned divider state and focused tests/documentation.
- Maven PR Tests run `29179022737` passed on the final head.
- PR #158 merged on 2026-07-13.
- The owner verified the desktop/laptop-width Report Library behavior.

## 7. P12 — Administration, company lifecycle, preferences, and Funds edit

Status: IN_PROGRESS. The execution ledger is being reconciled by P12-C1. Product implementation already merged through PRs #159 and #160 but still requires the recorded desktop acceptance checks.

### P12-S1 — Stable-ID Funds editing and lifecycle rules

Status: VERIFYING; implementation is merged.

Branch: `codex/P12-S1-fund-lifecycle-rules`  
Pull request: #159, merged into `main` at `affbae227b9751d9f9caad9cd301656c0ac640e7`  
Tested head: `93618fb368b0c50175a912e502e316a8b17ddb94`

Completed deliverables:

- Added stable-ID create/update for code, name, type, active state, parent, effective dates, and restriction text.
- Added uniqueness, field-length, date-order, parent-existence, self-parent, and cycle validation.
- Added authoritative usage assessment across transaction splits, budgets, fixed assets, inventory, aliases, transfers, and child funds.
- Added protected `deleteUnused(...)`; referenced funds are retained and deactivated.
- Added split table/editor UI, company-aware dates, dirty-state handling, real New/Save/Delete Unused/Refresh actions, and company-owned table/divider state.
- Added integration tests, UI source guardrails, `doc/administration/fund-lifecycle.md`, and focused matrix/inventory updates.
- Maven PR Tests runs `29224690525`, `29224900193`, and `29224988928` passed.

Remaining verification:

- At laptop width, create a fund and edit its code while confirming one stable row remains.
- Verify parent selection, effective dates, restriction text, active/inactive display, sorting, resizing, reordering, scrolling, and divider restoration.
- Verify referenced deletion is blocked with deactivation guidance, and unused deletion requires confirmation.

### P12-S2 — Administration workspace hub

Status: VERIFYING; implementation is merged.

Branch: `codex/P12-S1-administration-navigation`  
Pull request: #160, merged into `main` at `04966951a68f2e594ad1bfa289c8026840e9dbd0`  
Tested implementation head: `bc9b65c06e31f6d71032d8ac0f05d6c7a85a262b`

Completed deliverables:

- Added `AdministrationPanel` with Preferences, Company Admin, and User Admin tabs.
- Routed stable `AppPanelId.SETTINGS` to the Administration hub.
- Renamed the visible navigation destination while preserving saved destinations and command-palette compatibility.
- Delegated global Save, New, and dirty-state behavior to the selected tab.
- Preserved existing H2-backed company, user, role-assignment, and preference services.
- Added focused source guardrails and updated the operation matrix.
- Maven PR Tests runs `29259566867` and `29259761711` passed.

Remaining verification:

- At laptop width, open Administration and switch among all three tabs.
- Verify data loads and global Save/New behavior reaches the selected tab.
- Verify no separate Company Admin or User Admin shell destination was introduced.

### P12-C1 — Reconcile overlapping P12 slice records

Status: IN_PROGRESS.

Branch: `codex/P12-C1-reconcile-plan-ledger`  
Pull request: pending

Purpose: repair the execution ledger after PRs #159, #160, and #161 reused `P12-S1` and overwrote one another’s plan records.

Deliverables:

- Preserve PR #159 as P12-S1 and PR #160 as P12-S2.
- Record PR #161 as a merged plan-only activation; it did not implement company lifecycle.
- Restore the phase-contract tail removed by PR #161.
- Assign unimplemented company lifecycle work to P12-S3.
- Make no product-code changes.

### P12-S3 — Company lifecycle and active-company authority

Status: READY after P12-C1 merges. No branch or pull request exists.

Purpose: make H2 company rows authoritative for company creation, editing, activation/deactivation, and active-company selection through the existing Administration hub.

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`

Required inspection:

- `Company`, `CompanyTaxProfile`, `ChartOfAccounts`, and company-related migrations.
- `CompanyAdminService`, `CompanyAdminPanel`, `AdministrationPanel`, `CompanyWizardDialog`, `MainWindow`, `UiSessionState`, `WorkspaceContext`, `PanelFactory`, and `UiServiceRegistry`.
- Current company/admin tests and donor company-administration UI only as a design reference.

Planned deliverables:

- Use the existing Administration hub and Company Admin tab; do not add a redundant shell destination.
- Replace sidecar-only Add Company and Company Wizard creation with service-backed H2 creation and validation.
- Persist active state, fiscal-year start, default currency, and other supported company profile fields in one service transaction.
- Permit active-company selection only for an existing active H2 company and propagate it through session/workspace context and service composition.
- Deactivate rather than hard-delete companies; prevent deactivating the current company or leaving no active company without an explicit switch.
- Remove or defer enabled non-persistent placeholder tabs and controls.
- Apply UI design rules, company-owned layout state, dirty-state/discard protection, focused service/UI tests, and governing-document updates.

Next exact action after P12-C1:

- Create a fresh branch from current `main` named `codex/P12-S3-company-lifecycle`.
- Establish the Maven baseline and implement P12-S3 as one coherent vertical slice.

## 8. Active and recent phase contracts

# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`  
**Status:** DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147  
**Depends on:** P05

Completed deliverables: configured-account reconciliation, durable sessions and match/resolution rows, statement/manual/import sources, matching and cleared-state workflow, saved unresolved/finalized state, and the Setup/Statement/Match/Review workflow layout.

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`  
**Status:** DONE through PR #139

Completed deliverables: removed the Schedules panel, navigation, production route, runbook sidecars, formatting tests, and related inventory/matrix references. Any retained compatibility enum value has no product route.

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`  
**Status:** DONE through PR #140; corrective P08-C1 DONE through PR #144  
**Depends on:** P02

Completed deliverables: H2 fixed-asset and depreciation-run records, canonical depreciation transactions, service-backed Asset Register and Depreciation Runs panels, removed runbook sidecars, migration guardrails, and readable account/fund selector labels.

# P01-C1 — Full-text hover tooltips

**Selector:** `PHASE=P01`  
**Status:** DONE through PR #141

Completed deliverables: `FullTextTooltipInstaller`, production installation, UI-design documentation, and focused JavaFX tests.

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`  
**Status:** DONE through PR #142; corrective P09-C1 DONE through PR #143  
**Depends on:** P02

Required behavior: genuine Inventory item add/edit and movement history, no runbook subpane, and canonical transactions when movements are financially relevant.
