---
plan_version: 31
active_phase: P11
active_slice: P11-S1
active_status: IN_PROGRESS
active_branch: codex/P11-S1-report-catalog-parameters
active_pull_request: "TBD"
active_head: 78cd26748181af9665229318231c5bf8ae4a7d0c
next_action: "Open the P11-S1 draft PR, then implement a typed report catalog, report-specific parameters, fund filtering, company-aware formatting, and focused preview/export tests."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for work in `benbaron/sca-jakarta-h2`. Execute one phase and one mergeable slice at a time under `AGENTS.md`.

This revision records P10-S1 and corrective P10-C1 as DONE through merged PRs #156 and #157, then activates P11-S1 as the first newly unblocked Report Library slice.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former function is not part of the product plan.

## 3. Phase index

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update as touched |
| P01 | Production shell and workspace composition | P00 | DONE through corrective P01-C1 / PR #141 |
| P02 | Canonical ledger and transaction operations | P00 | DONE; retain |
| P03 | Journal workspace and canonical transaction operations | P01, P02 | DONE through corrective P03-C9 / PR #154 |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03 | DONE through PR #137 and corrective P05-C5 / PR #148 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138 and corrective PRs #146–#147 |
| P07 | Eliminated former Schedules phase | n/a | ELIMINATED through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140 and corrective PR #144 |
| P09 | Inventory and supplies | P02 | DONE through PR #142 and corrective PR #143 |
| P10 | Period close, reopening, and factual audit history | P02, P06 | DONE through P10-S1 / PR #156 and P10-C1 / PR #157 |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | IN_PROGRESS; P11-S1 active |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | READY |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | BLOCKED by P12 |
| P14 | End-to-end hardening | P03–P13 except eliminated P07 | BLOCKED |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

For UI/accounting/report work also read:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/workflow/development-workflow.md`

`doc/architecture/production-workspace.md` was removed. Do not restore it.

## 5. Established product decisions

- Maintain one JavaFX/H2 application and one canonical ledger.
- H2 is authoritative for accepted operational and accounting data.
- Use the existing JPA/Hibernate/Flyway foundation.
- Write services own validation and transactions; query services return projections.
- Reports belong in `REPORT_LIBRARY` and read authoritative services/projections.
- No approval queue, separate posting workflow, generic Schedules function, or Import/Export Jobs function.
- Notes and factual audit history remain in scope.
- Production UI follows `doc/ui_design_rules.md` and `doc/ui/editor-guidelines.md`.
- Company-specific date, money, table, and divider preferences are company-owned.
- The desktop JPA bootstrap explicitly selects the configured Hibernate provider.

## 6. Completed phase handoffs

### P03 — Journal workspace

DONE through P03-C9 / PR #154. The unified Journal uses canonical `Txn`/`TxnSplit` services and H2-backed supplemental transaction records. A later corrective slice may add authoritative line-level cleared-state projection.

### P05/P06 — Banking and reconciliation

DONE through PRs #137, #138, #146–#148. Banking configuration, statement facts, matching, resolution, reconciliation sessions, and comparison reports are H2-backed. Later work may extend specialized reconciliation reporting.

### P08 — Fixed assets

DONE through PRs #140 and #144. Asset records and depreciation runs are H2-backed; depreciation creates canonical transactions.

### P09 — Inventory

DONE through PRs #142 and #143. Inventory items and movements are H2-backed. Later work may automate financially relevant movement transactions and reporting.

### P10 — Period close and factual audit history

DONE through P10-S1 / PR #156 and corrective P10-C1 / PR #157.

Completed behavior:

- company-scoped calculated or custom close ranges in V60;
- factual close/reopen events and audit records;
- canonical transaction-entry/correction enforcement;
- service-backed Period Close workspace without approval/rejection semantics;
- explicit Hibernate bootstrap for reliable JavaFX desktop startup.

Validation:

- P10-S1 Maven PR Tests passed through final implementation head `2462a591de7965d69c8909991443011665daba8a`.
- P10-C1 Maven PR Tests runs `29177308667` and `29177357943` passed on head `0d82d960d2a47529eb5883fe8ccf388eb8bc2551`.
- PR #157 merged as `78cd26748181af9665229318231c5bf8ae4a7d0c`.

Known later P10 cleanup:

- legacy `AccountingPeriod` and period-close run artifacts remain compatibility structures, not close authority;
- `REQUIRE_FORMAL_ADJUSTMENT` blocks direct reopening; a specialized formal-adjustment workflow may be added only as a later deliberate slice.

## 7. Active phase contract

# P11 — Report Library

**Selector:** `PHASE=P11`

**Depends on:** P02, P04, P06, P08, P09, P10 — all satisfied.

### P11-S1 — Typed report catalog and parameters

**Status:** IN_PROGRESS

**Branch:** `codex/P11-S1-report-catalog-parameters`

**Pull request:** TBD

#### Current implementation

- `ReportLibraryPanel` owns a string list of four core financial reports plus workbook-semantic reports.
- Core Trial Balance, General Ledger Detail, Balance Sheet, and Income Statement queries are real H2-backed projections from `FinancialReportService`.
- Preview and export support TEXT, CSV, PDF, and XLSX.
- The panel has only the global `DateRangeContext`; it lacks report-specific parameter metadata, a fund selector, and a single immutable request shared by preview/export.
- Core text renderers print ISO dates and raw `BigDecimal` values instead of active-company display preferences.
- Donor `NonprofitAccounting` reporting code is design reference only; do not port its persistence model or create a parallel reporting framework.

#### Required deliverables

- Replace stringly report dispatch with a typed report catalog covering existing core and semantic reports.
- Define report-specific parameter requirements: as-of date, date range, optional fund, and General Ledger row limit where applicable.
- Add an active-company fund selector using stable IDs/codes and an explicit All Funds option.
- Build one immutable report request and use it for preview, export, and drill-through context.
- Apply active-company date and money formatting to visible core report previews without changing authoritative numeric values or CSV machine data.
- Preserve existing semantic-template rendering and TEXT/CSV/PDF/XLSX export paths.
- Remove or disable no visible report unless its real service/template is absent; never show a selectable “Report not implemented” path.
- Add focused catalog, parameter validation, fund-filter, formatting, preview/export consistency, and UI-source tests.
- Update the operation matrix, reporting documentation, and this plan with actual results.

#### Definition of done

- Every selectable report maps to a real service or semantic template.
- Report-specific controls are visible only when applicable and survive normal selection changes without corrupting the request.
- Preview and export consume the same validated report request.
- Fund-filtered core reports return only the selected fund’s authoritative ledger activity.
- Visible dates and money follow active-company preferences; CSV remains stable machine-readable data.
- Maven PR Tests pass, final diff is reviewed, and laptop-width visual checks are recorded.

#### Next exact action

Open the draft PR, inspect fund lookup and company-format utilities plus current report tests/templates, then implement the typed catalog and request model before changing the JavaFX controls.
