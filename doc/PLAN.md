---
plan_version: 214
active_phase: P05
active_slice: P05-C6
active_status: IN_PROGRESS
active_branch: codex/P05-C6-asset-bank-cash-classification
active_pull_request: pending
active_head: local
next_action: "Publish P05-C6, run Maven PR Tests, complete the owner ASSET/BANK/CASH desktop checklist, then merge after acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P16-S17 as DONE through merged PR #269 and owner acceptance, closes P16 through documentation-only P16-C1 / PR #270, records corrective P16-C2 through merged PR #271, records P16-C3 and P16-C4 through merged PRs #272 and #273, records the owner-requested P16-C6 nondestructive existing-company SCLX import through merged PR #274, records P16-C7 ownership diagnostics through merged PR #275, records P16-C8 target-company authority through merged PR #276, records the identity-aware P16-C9 corrections through merged PRs #277-#279, records P16-C10 through merged PR #280, closes P16-C11 through merged PR #281, closes P11-C1 through merged PR #282, and closes P11-C2 through merged PRs #283 and #284 plus owner desktop acceptance. It activates owner-requested corrective P05-C6 to separate accounting asset classification, operational bank function, and cash presentation as `ASSET :: BANK :: CASH`. P16-C5 message guidance remains a separate unpublished local slice and is not mixed into this branch. It does not authorize P17 or any later feature phase.

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
| P05 | Banking configuration and statement import | P02, P03-C1 | Corrective P05-C6 IN_PROGRESS after P05-C5 / PR #148 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147 |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140; corrective P08-C1 DONE through PR #144 |
| P09 | Inventory and supplies | P02 | DONE through PR #142; corrective P09-C1 DONE through PR #143 |
| P10 | Period close, reopening, and factual audit history | P02, P06 | DONE through P10-S1 / PR #156 and P10-C1 / PR #157 |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | DONE through P11-C2 / merged PR #284 |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | DONE through P12-S1, P12-S2, P12-S3, P12-C1, P12-C2, and P12-C3 |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | DONE through P13-S1 / PR #177 and P13-S2 / PR #179 |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | DONE through P14-S1, P14-S2, P14-S3, P14-S4, and P14-C1 |
| P15 | Versioned data interchange and database transfer | P02, P05, P06, P12, P13, P14 | DONE through P15-C1 / PR #250 |
| P16 | Interface-to-authority completion and integrity corrections | P03-P15 except eliminated P07 | DONE through corrective P16-C11 / merged PR #281 |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Focused documents for current UI/accounting work:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/data-exchange/shared-operation-contract.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/audit/audit-history.md`
- `doc/reporting/report-library.md`
- `doc/administration/fund-lifecycle.md`
- `doc/administration/company-lifecycle.md`
- `doc/administration/user-role-maintenance.md`
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

### P05 — Banking configuration and statement import

Status: Corrective P05-C6 IN_PROGRESS after P05-C5 / PR #148.

#### P05-C6 — ASSET / BANK / CASH account classification

Status: IN_PROGRESS.

Branch: `codex/P05-C6-asset-bank-cash-classification`
Starting base: `8cc3a8882df5dee346deb26ec77960f7d3122beb`
Pull request: pending

Purpose:

- Correct the accounting model so `BANK` is not a top-level peer of `ASSET`.
- Represent ordinary deposit accounts as `AccountType.ASSET`, operational `AccountFunction.BANK`, and normally `AccountSubtype.CASH`, with `NormalBalance.DEBIT`.
- Keep operational banking behavior independent of cash-only financial-statement presentation: `ASSET / BANK / !CASH` remains bank/reconciliation eligible but is not cash, while `ASSET / !BANK / CASH` is cash but not a statement/reconciliation account.
- Preserve existing bank configuration, transaction, statement, cleared-state, reconciliation, COA, and SCLX identities without creating a second account model.

Implemented deliverables:

- Added nullable `AccountFunction` to `Account`; `BANK` is currently the governed operational function. Removed `BANK` from `AccountType`, whose values now describe accounting statement classes only.
- Added nondestructive Flyway migration `V73__asset_bank_cash_classification.sql`. Existing `account_type = 'BANK'` rows become `account_type = 'ASSET'` plus `account_function = 'BANK'`; every existing subtype, including non-CASH values, is preserved.
- Banking configuration and its JavaFX existing-account selector now accept `ASSET / BANK-function / DEBIT` accounts without requiring `CASH`. New bank-account creation defaults to `ASSET / BANK / CASH / DEBIT`.
- Chart of Accounts exposes the independent Function field and rejects BANK-function combinations that are not ASSET/DEBIT.
- Journal bank-line projection, cleared-state consumers, dashboard operational bank balances, Bank Account Activity, inventory/fixed-asset bank behavior, and related queries now use the BANK function rather than accounting type.
- Dashboard Book Cash and the Balance Sheet cash breakout use `ASSET + CASH` classification, so a non-CASH bank-function account remains an asset but is excluded from cash presentation; petty cash can be CASH without becoming a reconciliable bank account.
- Existing SCA-COA and SCLX wire contracts retain portable type `BANK`. Import maps that compatibility token to `ASSET + BANK function`; export maps BANK-function accounts back to portable `BANK`, with subtype remaining independent. The simpler legacy COA CSV token `BANK` maps to `ASSET / BANK / CASH` because that format has no subtype column.
- Added migration, account-admin, banking, dashboard, COA CSV/JSON, SCLX, report, and service regression coverage. Raw H2 bank fixtures now represent `ASSET / BANK / CASH`.
- Added owner desktop checklist `doc/P05-C6-asset-bank-cash-classification-user-testing.md`.

Validation status:

- Source-wide guardrail finds no production or test reference to removed `AccountType.BANK`.
- Java Compiler API parse-only validation succeeds for all 728 production/test Java files with zero syntax errors.
- Maven and a Maven wrapper are unavailable in the local execution environment; focused/full JUnit, Flyway, Hibernate query, and JavaFX checks require Maven PR Tests after publication.

Next exact action:

- Publish the focused branch, run Maven PR Tests, complete the owner desktop checklist, and merge only after owner acceptance.

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

Status: DONE through P11-C2 / merged PR #284 and owner desktop acceptance.

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

#### P11-C1 — Formatted core-report previews

Status: DONE through merged PR #282.

Branch: `codex/P11-C1-formatted-report-preview`
Base head: `3e95d42f56800b729cc01c6b7d3741c3b0345fb6`

Purpose:

- Replace every core report's monospaced plain-text preview with a structured JavaFX table matching
  the workbook-style report presentation.
- Preserve the immutable report request, authoritative H2 projections, company money/date formatting,
  machine-stable CSV, and existing TEXT/PDF/XLSX export behavior.
- Give Trial Balance, General Ledger Detail, Balance Sheet, and Income Statement complete named columns,
  colored section/total/status rows, independent table scrolling, and company-owned column state.

Planned deliverables:

- Add an immutable UI-neutral table model generated directly from each core service projection rather
  than parsing rendered text or CSV.
- Add one JavaFX table renderer with company date/money formatting, full-text tooltips, workbook colors,
  wrapping text columns, and sortable/resizable/reorderable columns.
- Put the parameter and preview regions behind a horizontal draggable divider and persist both Report
  Library divider positions for the active company.
- Add focused model, execution, renderer/source, documentation, and layout coverage.
- Run focused source validation and `mvn clean verify` where tooling is available; retain a desktop
  visual check for laptop-width color, wrapping, scrolling, and divider behavior.

Implementation and validation status:

- Implementation commit `b0462aa` adds an immutable `ReportTableModel` and maps Trial Balance, General
  Ledger Detail, Balance Sheet, and Income Statement directly from their authoritative core projections.
  TEXT/CSV/PDF/XLSX generation and the immutable request/export reuse boundary are unchanged.
- `FormattedReportFxRenderer` supplies a real JavaFX `TableView`, complete named columns, company money
  and date formatting, wrapping cells, full-value tooltips, independently scrollable content, blue
  headers/sections, emphasized totals, and textual green/amber status rows.
- General Ledger Detail exposes all ten projected fields. Statement reports retain ordered section and
  total rows, while Trial Balance displays total debits/credits plus `Balanced — PASS` or an actionable
  review status.
- Dynamic preview tables are explicitly registered with `CompanyTableStateBinder`; a new vertical
  `SplitPane` orientation produces the horizontal draggable divider between parameters and preview.
  Both Report Library dividers and every core report's column order/width/sort state are company-owned.
- Focused builder, execution-integration, JavaFX renderer, source-contract, documentation, and layout
  regressions are present. All 721 production/test Java sources parse under the Java 17 compiler module,
  the UI-neutral model/builder and JavaFX renderer type-compile in focused local checks, the four-builder
  smoke path passes, and `git diff --check` passes.
- Maven, the `javac` launcher, and a Linux JavaFX runtime are unavailable in this container. The focused
  JUnit/JavaFX suite and full `mvn clean verify` therefore require Maven PR Tests after publication.

Owner desktop checklist:

1. Open Trial Balance, General Ledger Detail, Balance Sheet, and Income Statement and confirm none uses
   a monospaced plain-text preview.
2. Confirm blue headers/section rows, emphasized totals, textual balance status, preferred company date
   and money formats, and readable wrapped long names/memos.
3. Resize/reorder/sort columns, move both Report Library dividers, reopen the company, and confirm the
   layout is restored for that company.
4. At laptop width, confirm both table scroll bars are available as needed and the horizontal divider can
   enlarge the preview without hiding access to report parameters.
5. Export representative TEXT, CSV, PDF, and XLSX files. Confirm PDF visually matches the JavaFX table
   hierarchy and company formatting, while CSV/XLSX retain stable machine-readable values.

Merge evidence:

- PR #282 merged to `main` at `9c359d88ef2371d1e8acc13229cb336b74d3d6f5`.

#### P11-C2 — Metadata-driven comparative financial statements

Status: DONE through merged PRs #283 and #284 plus owner desktop acceptance.

Branch: `codex/P11-C2-dynamic-financial-statements`
Base head: `9c359d88ef2371d1e8acc13229cb336b74d3d6f5`
Initial pull request: #283, merged at `1d6ee5ff2ea993c7a8d8c87d045283cd3e519ead`
Corrective pull request: #284, merged at `8cc3a8882df5dee346deb26ec77960f7d3122beb`

Purpose:

- Format Balance Sheet and Income Statement previews with the comparative SCA exchequer-report visual
  hierarchy while deriving all organization/company headings from active-company metadata.
- Derive statement categories and expense allocation columns from the active chart hierarchy; do not
  hard-code one organization's chart labels or the Society for Creative Anachronism name.
- Preserve the authoritative report projections, immutable request, and existing export adapters.
- Render structured core-report PDFs from the same typed model as the JavaFX table, remove obsolete
  `BalanceStmt`/`IncomeStmt` entries, and add All Accounts/specific-account parameters to General Ledger
  Detail and Transactions List.

Implemented deliverables:

- Add metadata header rows to the structured report model and JavaFX renderer.
- Make Balance Sheet a comparative period report with beginning, ending, and difference columns plus
  calculated net-worth change/net-income reconciliation.
- Pivot Income Statement expense rows from chart parent/child relationships into dynamic allocation
  columns and retain dynamic income/expense categories, totals, and reconciliation.
- Add company scoping, zero-activity chart rows, focused tests, governing documentation, and owner visual
  acceptance steps.
- Preserve typed table headings, wrapping, borders, colors, row emphasis, and company date/money formats
  in PDF exports; retain stable-ID account selection across preview/export.

Implementation and validation status:

- Implementation commit `0717aae67435956e1116f623dc7bfaa3aa565001` is published in draft PR #283.
- Corrective commit `6b04245a1eddf5337b063414d50e824ba5c60534` preserves the legacy null-company
  report-service constructor without weakening the production active-company/active-chart predicate.
- The structured table model and JavaFX renderer now accept two-column metadata header lines. The active
  company's parent organization, legal entity, display name, currency, fiscal-year start, and resulting
  quarter label are supplied through `ReportPresentationMetadata`; no sample organization or branch name
  is embedded in production report code.
- Balance Sheet now uses a selected comparative range, includes active-chart cash/bank, asset, and
  liability accounts even at zero balance, and calculates beginning, ending, difference, Net Worth,
  period change, Net Income, and reconciliation difference rows.
- Income Statement includes all active-chart income and expense posting accounts, derives category rows
  and allocation columns from parent/grandparent relationships, and retains dynamic gross/cost/net,
  expense, Net Income, Net Worth change, and difference totals.
- Production report queries are active-company scoped and obtain statement categories only from that
  company's active chart; no second ledger or reporting store is introduced. Existing export adapters
  continue to receive the same core projections.
- All 724 production/test Java sources parse with the Java 17 compiler module. The UI-neutral report
  model/builder type compilation and focused metadata, dynamic-category, and reconciliation behavior
  checks pass, as do `git diff --check` and the hard-coded sample-name source guardrails.
- Maven, the `javac` launcher, and a Linux JavaFX runtime are unavailable in this container. The focused
  JUnit/JavaFX suite and full `mvn clean verify` therefore required Maven PR Tests after publication.
- Exact corrected head `6b04245a1eddf5337b063414d50e824ba5c60534` passed all three Maven PR Tests
  gates in run `32214212704`: clean Maven verification, the deliberately repeated test suite, and
  production JavaFX route compliance.
- Plan-inclusive head `4683901fcc6da242259010654195971ebe18bd73` passed all three Maven PR Tests
  gates in run `32214599933` before the additional owner-requested PDF/catalog/account-filter work.
- The current unpublished continuation renders core and semantic reports through typed formatted Jasper
  PDF, removes the obsolete `BalanceStmt`/`IncomeStmt` catalog entries, templates, and dead renderer,
  and applies optional persisted-account-ID predicates to General Ledger Detail and Transactions List.
- All 725 production/test Java sources parse under the Java 17 compiler module. Focused type checks pass
  for the 17-source Jasper adapter seam, the 8-source request/catalog filter seam, and the 5-source
  semantic-table conversion seam. Generated structured JRXML is well formed and escapes dynamic text;
  every remaining semantic JSON template parses, obsolete production-name guardrails pass, and
  `git diff --check` is clean.
- Maven, its wrapper, the `javac` launcher, and a Linux JavaFX runtime are unavailable locally. Full
  Jasper compilation/PDF export, H2 predicates, JUnit, and JavaFX route checks require Maven PR Tests
  after publication.
- The continuation was published at head `2bd36f71dbc6f6db1c193fb6a7f9805ad5a09c04` in draft PR #284.
  Maven PR Tests job `96312987338` compiled successfully and ran 696 tests, with one failure and one
  error: structured PDF fill passed an immutable parameter map that JasperReports mutates internally,
  and the account-filter integration test asserted the nonexistent table key `accountCode` instead of
  the established General Ledger table key `account`. The reviewed local correction supplies a mutable
  `LinkedHashMap` to Jasper and fixes only that test key; `git diff --check` passes. Maven is unavailable
  locally, so the corrected focused tests and full suite require the PR workflow after publication.
- Corrective commit `d6cc636d9d5ce9f93e100da8e70d36f015d15462` was published to draft PR #284.
  Maven PR Tests run `32431913827` passed all three gates on that exact head: clean headless Maven
  verification, the deliberately repeated test suite, and production JavaFX route compliance.

Completion evidence:

- PR #283 merged the metadata-driven comparative statement implementation at `1d6ee5ff2ea993c7a8d8c87d045283cd3e519ead`; tested head `4683901fcc6da242259010654195971ebe18bd73` passed Maven PR Tests.
- PR #284 carried the structured PDF parity, obsolete-report removal, and account-filter continuation. Corrective head `d6cc636d9d5ce9f93e100da8e70d36f015d15462` passed Maven PR Tests run `32431913827`; final plan-inclusive head `606b0f1192b725e19f30ce11b5f2431af8f803b2` passed run `32432314663`.
- PR #284 merged to `main` at `8cc3a8882df5dee346deb26ec77960f7d3122beb`. The owner completed and accepted the desktop statement/PDF/account-filter checklist on 2026-08-20.

Next exact action:

- None; P11-C2 and P11 are DONE.

## 7. P12 — Administration, company lifecycle, preferences, and Funds edit

Status: DONE through P12-S1, P12-S2, P12-S3, P12-C1, P12-C2, and P12-C3 with owner desktop acceptance.

### P12-S1 — Stable-ID Funds editing and lifecycle rules

Status: DONE through merged PR #159, corrective PR #171, and owner desktop acceptance.

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
- The owner created a Fund, changed its code, and verified that one stable row remained, the old code was unavailable, no duplicate was created, and all persisted values reloaded after reopening Funds.
- The owner verified parent selection, effective dates and company formatting, restriction text, active/inactive display, table sorting, resizing, reordering and scrolling, and company-owned divider and table-state restoration.
- The owner verified that referenced Fund deletion was blocked with reference details and deactivation guidance, that deactivation retained the referenced row, and that unused Fund deletion required explicit confirmation and honored cancellation.

Next exact action:

- None; P12-S1 is DONE.

### P12-C3 — Funds horizontal divider correction

Status: DONE through merged PR #171 and owner desktop acceptance.

Branch: `codex/P12-C3-funds-horizontal-split`
Pull request: #171, merged into `main` at `b199f3e66d736d8f4e743fe61199b0f1683eacf9`
Base head: `34a28f513d2116878d03a43d3b0fffbf8e42fda7`
Implementation head: `e307633189c94a4f15428044965b76af83341ed8`

Purpose: correct the Funds center workspace discovered during laptop-width desktop validation so the fund table and editor are stacked top/bottom and separated by a horizontal draggable divider.

Planned deliverables:

- Set the existing Funds `SplitPane` to vertical item orientation, producing a horizontal draggable divider.
- Preserve the stable-ID table/editor workflow, independent editor scrolling, and company-owned divider persistence.
- Add a focused source/layout guardrail and update the Fund lifecycle documentation.
- Run the full Maven PR Tests workflow and leave the correction VERIFYING until desktop confirmation and merge.

Completed deliverables:

- Set `fundsWorkspaceSplit` to vertical item orientation so the divider runs horizontally and the fund table is above the editor.
- Allowed both split items to shrink while preserving table scrolling, the independent editor `ScrollPane`, and existing company-owned divider persistence.
- Added a focused Funds panel source guardrail and documented the top/bottom workspace behavior.
- Local Maven validation was unavailable because the container has neither Maven nor a Maven wrapper; GitHub Maven PR Tests is authoritative for this correction.
- Maven PR Tests run `29469588821` passed on handoff head `e14f778594c76f277230f37ac49f36638099b3b1`.
- Final Maven PR Tests run `29469691118` passed on PR head `ec2b949793a66f3322a598dfbdb4fbbdedd58496`.
- The owner verified on current `main` that the table is above the editor, the horizontal divider is draggable and restored, and both regions remain usable at laptop width.

Next exact action:

- None; P12-C3 is DONE.

### P12-S2 — Administration workspace hub

Status: DONE through merged PR #160 and owner desktop acceptance.

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
- The owner verified at laptop width that Preferences, Company Admin, and User Admin loaded through the single Administration destination, that global Save/New delegation reached the selected tab where supported, and that no separate Company Admin or User Admin shell destination exists.

Next exact action:

- None; P12-S2 is DONE.

### P12-C1 — Reconcile overlapping P12 slice records

Status: DONE through merged PR #162.

Branch: `codex/P12-C1-reconcile-plan-ledger`  
Pull request: #162, merged into `main` at `fa6b94a67c9f6aa2d6d79e1de77b257240c49a42`

Purpose: repair the execution ledger after PRs #159, #160, and #161 reused `P12-S1` and overwrote one another’s plan records.

Deliverables:

- Preserve PR #159 as P12-S1 and PR #160 as P12-S2.
- Record PR #161 as a merged plan-only activation; it did not implement company lifecycle.
- Restore the phase-contract tail removed by PR #161.
- Assign unimplemented company lifecycle work to P12-S3.
- Make no product-code changes.

### P12-S3 — Company lifecycle and active-company authority

Status: DONE through merged PR #163, corrective PR #164, and owner desktop acceptance.

Branch: `codex/P12-S3-company-lifecycle`
Pull request: #163, merged into `main` at `b08a0a29755d0d6a5e19fb152798c4d5e6eb4784`
Tested implementation head: `f1cf7333b981aa766498fd725853ecc5ccf187d1`

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

Completed deliverables:

- Added stable-ID H2 company create/edit/deactivate transactions with complete supported profile persistence and active-company invariants.
- Reconciled persisted recent-company convenience state against active H2 rows, so fictional or inactive codes cannot become the workspace company.
- Added the production toolbar selector and refreshed open database-bound panels after guarded active-company changes.
- Preserved the `SETTINGS` Administration hub while reducing Preferences to preferences and replacing Company Admin placeholders with one real split table/editor workflow.
- Added service integration coverage, restart/sidecar authority coverage, JavaFX source guardrails, and `doc/administration/company-lifecycle.md`.
- Maven PR Tests runs `29305022115`, `29305144314`, and `29305406957` passed; the last run is on the tested implementation head.
- The owner created a company with every supported profile field and verified that the same authoritative H2 profile reloaded after application restart.
- The owner renamed the current company code and verified that the toolbar, Administration workspace, Inspector and open-workspace context, and restart selection followed the same stable company record; the old code was no longer selectable and no duplicate company row was created.
- The owner verified that cancelling an active-company switch preserved the current company and unsaved workspace edits without refreshing panels, while approving the switch changed the active company and refreshed open workspaces without retaining stale company data.
- The owner deactivated a non-current company and verified that its authoritative row remained inactive while disappearing from active-company selectors and recent-company selection; attempts to deactivate the current or last active company were blocked.
- The owner verified Company Admin table sorting, resizing, reordering, horizontal and vertical scrolling, company-owned divider restoration, full-text tooltip behavior, and dirty-state prompts for every tested discard path.

Next exact action:

- None; P12-S3 is DONE.

### P12-C2 — Company Admin horizontal divider correction

Status: DONE through merged PR #164 and owner desktop verification.

Branch: `codex/P12-C2-company-admin-horizontal-split`
Pull request: #164, merged into `main` at `c59c11c25bc7c77841da0a8b77822404a358646a`
Tested implementation head: `1dafac8b6cfa0755eed1c8a2590da49cb60163ad`

Purpose: correct the Company Admin center workspace discovered during laptop-width desktop validation so the company table and profile editor are stacked top/bottom and separated by a horizontal divider.

Planned deliverables:

- Set the existing Company Admin `SplitPane` to vertical item orientation, producing a horizontal draggable divider.
- Preserve the existing table/editor workflow, scrolling, company-owned divider state, and Administration hub.
- Add a focused source/layout guardrail and update the lifecycle documentation.
- Run the full Maven PR Tests workflow and leave the correction VERIFYING until desktop confirmation and merge.

Completed deliverables:

- Set the existing `companyAdminWorkspaceSplit` to vertical item orientation so its divider runs horizontally and the table is above the editor.
- Preserved independent editor scrolling, table growth, company-owned divider persistence, and the existing Administration destination.
- Added a focused orientation guardrail and documented the top/bottom laptop-width layout.
- Maven PR Tests runs `29372548918` and `29372653080` passed; the last run is on final handoff head `ef6fb259a69fd03ce4ae39a2b612f07dddb07e08`.
- The owner verified the corrected laptop-width top/bottom layout and horizontal draggable divider after merge.

Next exact action:

- None; P12-C2 is DONE.

## 8. Active and recent phase contracts

# P13 — Data exchange and diagnostics without Import/Export Jobs

**Selector:** `PHASE=P13`
**Status:** DONE through P13-S1 / PR #177 and P13-S2 / PR #179
**Depends on:** P02, P05, P12

Purpose: preserve useful data exchange, import review, and diagnostics while eliminating the prohibited generic Import/Export Jobs function and generic job-tracking concept. Domain-specific banking, reconciliation, import-review, diagnostic, and audit facts remain governed by their authoritative services and tables.

Required reading:

- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/ui_design_rules.md`

Required inspection:

- `AppPanelId`, `NavigationPane`, `MainWindow`, `PanelFactory`, and command-palette tests.
- `ImportExportJobsPanel`, `UiWorkspaceDataStore`, `ImportPreviewPanel`, `BankTransactionsPanel`, and `DiagnosticsPanel`.
- Current import/export, banking-import, diagnostics, navigation, and UI-store tests.

### P13-S1 — Remove generic Import/Export Jobs function and session job tracking

Status: DONE through merged PR #177 and owner desktop verification.

Branch: `codex/P13-S1-remove-import-export-jobs`
Pull request: #177, merged
Base head: `58eab6563cbe245d39beb7ef6cb814946df6cf9d`
Implementation head: `495bf4ab296cb66ee2b4b7c8aebee803ca808ae2`
Merged head: `69ef903ab00e209f0855d9b374c0fc4fc3d39377`

Planned deliverables:

- Remove `IMPORT_EXPORT_JOBS` as an `AppPanelId`, navigation item, menu/command-palette destination, panel-factory route, and production panel.
- Remove the generic `ImportExportJob` session list and append/clear APIs rather than replacing them with durable generic job tracking.
- Preserve actual import/export commands and domain-specific H2 banking, reconciliation, import-issue, diagnostic, and audit facts.
- Preserve the temporary bank-transaction staging surface only to the extent still required by its owning banking work; do not broaden P13-S1 into unrelated P05 remediation.
- Update governing inventories, focused navigation/source tests, and obsolete-name consistency checks.
- Run the full Maven PR Tests workflow and leave the slice VERIFYING until desktop navigation confirmation and merge.

Out of scope:

- Diagnostics command redesign, accepted-import canonical transaction writes, and unrelated P14 hardening.

Completed deliverables:

- Removed `IMPORT_EXPORT_JOBS` from `AppPanelId`, navigation, the Tools menu, command-palette labels/capabilities, and `PanelFactory`.
- Deleted `ImportExportJobsPanel` and removed the generic `ImportExportJob` session list plus append, query, and clear APIs.
- Removed generic job-log writes from CoA and bank import/export actions while preserving the actual operations, user-facing outcome messages, and temporary bank-transaction staging.
- Updated the interface matrix and persistence inventory, simplified the staging-store tests, and added a focused elimination/source guardrail.
- Local Maven validation remains unavailable because the container has neither Maven nor a Maven wrapper; Maven PR Tests runs `29670629196` and `29670678408` passed, with the latter on the final PR head.
- PR #177 merged at `69ef903ab00e209f0855d9b374c0fc4fc3d39377`.
- The owner verified that Import / Export Jobs is absent from navigation, menus, and the command palette while CoA and OFX/QFX import/export operations and their user-facing outcome messages remain available.

Next exact action:

- None; P13-S1 is DONE.

### P13-S2 — Typed diagnostics and recovery command ownership

Status: DONE through merged PR #179 and owner desktop verification.

Branch: `codex/P13-S2-typed-diagnostics-recovery`
Pull request: #179, merged
Base head: `6809601310fda171b6259af042133ef105b75c81`
Implementation head: `cb6881d9810e249d7503e5944fab58254ca2f63e`
Merged head: `7ef06129551eb967f6caa1f9d30141835a7f6ddc`

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`

Required inspection:

- `DiagnosticsPanel`, `DatabaseRecoveryPanel`, `ProductionWorkspaceWindow`, `MainWindow`, and `PanelFactory`.
- `DatabaseLocationService`, `DatabaseMigrationService`, `FlywaySchemaRecoveryService`, `DatabaseSessionController`, `WorkspaceContext`, `UiSessionState`, and `UiServiceRegistry`.
- Current diagnostics, migration-recovery, database-session, navigation, tooltip, and JavaFX source/behavior tests.

Planned deliverables:

- Introduce explicit typed diagnostics queries/results for runtime, active-company, active-database, datasource, account, fund, and duplicate-code health rather than assembling diagnostic facts directly in the JavaFX panel.
- Give database retry/repair, select-existing, and create-new recovery actions explicit typed command ownership while preserving the existing Dashboard recovery surface and database-session composition.
- Keep diagnostics factual and non-destructive: do not add automatic data repair, generic job tracking, or enabled placeholder actions.
- Preserve drill-through to the existing Chart of Accounts and Funds destinations with explicit context when duplicate codes exist.
- Apply current scrolling, tooltip, formatting, error-message, and company-owned UI-state rules; add focused service tests and JavaFX source/behavior guardrails.
- Update the interface matrix and persistence inventory, run the full Maven PR Tests workflow, and leave the slice VERIFYING until desktop validation and merge.

Out of scope:

- Canonical acceptance from Import Preview, banking/reconciliation remediation, schema redesign, and unrelated P14 end-to-end hardening.

Completed deliverables:

- Added `DiagnosticsQueryService` with an immutable typed report for runtime, Java, active-company, active-database, datasource, active/total account and fund counts, duplicate codes, quality state, and safe failure details.
- Removed datasource, session, account, and fund fact gathering from `DiagnosticsPanel`; lifecycle-owned `WorkspaceServices` and `PanelFactory` now supply the diagnostics query service.
- Added typed `DatabaseRecoveryCommand` values and one command handler; File-menu and Dashboard recovery controls route retry-current, select-existing, and create-new through the production workspace shell.
- Preserved the existing Diagnostics destination, duplicate-code drill-through, Dashboard recovery surface, successful-connection-only database selection, and non-destructive failure behavior.
- Updated the interface matrix and persistence inventory and added focused service, JavaFX behavior, and source-boundary tests.
- Local Maven validation remains unavailable because the container has neither Maven nor a Maven wrapper; Maven PR Tests run `29672098613` passed on implementation head `cb6881d9810e249d7503e5944fab58254ca2f63e`.
- Final Maven PR Tests run `29672152187` passed on final PR head `094d9e4a33997431564c71c15c38669394a42e4c`.
- PR #179 merged at `7ef06129551eb967f6caa1f9d30141835a7f6ddc`.
- The owner verified factual Diagnostics refresh and warnings, duplicate account/fund drill-through behavior, all three Dashboard database-recovery actions, and protection of the selected database after a failed connection.

Next exact action:

- None; P13-S2 and P13 are DONE.

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** DONE through P14-S1, P14-S2, P14-S3, P14-S4, and documentation-only P14-C1
**Depends on:** P03 through P13 except eliminated P07

Purpose: harden the one production JavaFX/H2 application through cross-workspace lifecycle and regression coverage. P14 repairs defects exposed by end-to-end use; it does not absorb unfinished feature expansion owned by earlier domain phases.

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/workflow/development-workflow.md`

Original P14 audit findings and closure ownership:

- P14-S1 closed universal table interaction/state authority and production-route smoke gaps.
- P14-S2 closed core editor layout, scrolling, divider-state, and dirty-state gaps.
- P14-S3 closed scoped company money/date formatting and financial-view split/table-layout gaps.
- P14-S4 closes stale Help content/links, canonical production destination-menu names, and exact all-destination/alias smoke assertions.

### P14-S1 — Production UI compliance foundation and route smoke

Status: DONE through merged PR #181 and owner desktop verification.

Branch: `codex/P14-S1-production-ui-compliance-foundation`
Pull request: #181, merged into `main` at `9bbdda0ab7113df75bc0fd16705df7e8bd5c9c14`.
Base head: `62d0a8408dd482296d5a80736c7a31cba4cae7b1`
Implementation head: `6c5e2f5bbe02098679deba90769824c40fc264bc`
Validation: Maven PR Tests run `29716315784` passed, including the complete established headless suite and focused Xvfb production-route/table-state compliance tests.
Final documentation-head Maven PR Tests run `29716454453` passed.
The owner verified representative table sorting, resizing, reordering, restart restoration, and company-specific state, then confirmed the slice for merge.

Required inspection:

- `MainApp`, `ProductionWorkspaceWindow`, `NavigationPane`, `PanelFactory`, `PanelHost`, `WorkspaceServices`, `WorkspaceServicesFactory`, and `UiServiceRegistry`.
- `DatabaseBootstrap`, `DatabaseSessionController`, `CompanySessionController`, `WorkspaceContext`, `UiSessionState`, and test H2/bootstrap helpers.
- `AppPanelId`, `AppPanelConsistencyTest`, `WorkspaceCompositionTest`, `ProductionWorkspaceJavaFxBehaviorTest`, `ProductionDesignRulesTestFxTest`, and current panel source/behavior tests.

Planned deliverables:

- Build a repeatable JavaFX smoke fixture backed by a disposable migrated H2 database with an authoritative active company.
- Enumerate and open every canonical production destination through the production workspace composition, while confirming compatibility aliases reuse their canonical destination and eliminated destinations remain absent.
- Add one reusable H2-backed company-owned table-state helper for column order, widths, sort direction, and sort priority; do not add another preferences sidecar.
- Add production-root compliance guardrails that can enumerate every table and detect constrained policies, non-sortable/non-resizable/non-reorderable columns, and missing registered company-state ownership.
- Apply the shared helper at `PanelFactory` so every production table receives the common contract, while preserving the richer H2 owners already used by Journal, Funds, and Company Admin.
- Remove Banking and Inventory Java Preferences table stores so production table layout has only the H2 company-state authority.
- Update governing inventories, run the full Maven PR Tests workflow, and leave the slice VERIFYING until desktop validation and merge.

Out of scope:

- New Import Preview acceptance, bank-statement review, reconciliation resolution, inventory-to-ledger automation, report expansion, schema redesign, or other unfinished domain features merely listed in earlier-phase backlogs.

Next exact action:

- None; P14-S1 is DONE.

### P14-S2 — Core editor form layout and dirty-state compliance

Status: DONE through merged PR #182 and owner desktop verification.

Branch: `codex/P14-S2-core-editor-form-compliance`
Pull request: #182, merged into `main` at `96993c9a5c143dbd617ba6ac7ec5c1c0026ad980`.
Base head: `9bbdda0ab7113df75bc0fd16705df7e8bd5c9c14`
Activation head: `707078169c39f74e4f1169afa55e31dd8437aaaf`
Implementation head: `927c0d2024d7aed126c8dd5704e9ce2fd3a244f7`
Validation: Maven PR Tests run `29756498806` passed, including the complete established suite plus focused Xvfb production-route and seven-editor dirty-state coverage.
Final documentation-head Maven PR Tests run `29756777489` passed on `0bd9afa56bd70f380f2910e01ca8f94c8fbdc1cc`.
The owner verified the horizontal dividers, independent scrolling, company-owned divider restoration, dirty-state prompts, and hidden Administration-tab dirty aggregation, then confirmed the slice for merge.

Required inspection:

- `AssetsRegisterPanel`, `BankingPanel`, `BudgetEditorPanel`, `ChartOfAccountsPanel`, `SettingsPanel`, `UserAdminPanel`, and `AdministrationPanel`.
- `PanelFactory`, `PanelHost`, `ProductionWorkspaceWindow`, `CompanyTableStateBinder`, `CompanyUiPreferencesService`, and active-company workspace refresh behavior.
- Current panel source/behavior tests, dirty-state tests, production route compliance tests, and relevant service integration tests.
- `doc/ui_design_rules.md`, `doc/ui/editor-guidelines.md`, `doc/interface-operation-matrix.md`, and `doc/persistence-authority-inventory.md`.

Planned scope:

- Repair Assets, Banking, Budget Editor, Chart of Accounts, Preferences, User Admin, and the Administration dirty-state aggregator.
- Add required table/form split regions, editor scrolling, company-owned state, and loss-prevention prompts without changing domain services.
- Add focused JavaFX/source guardrails and run the complete Maven PR Tests workflow; leave the slice VERIFYING until desktop acceptance and merge.

Completed deliverables:

- Added reusable immutable form-snapshot dirty tracking and H2 company-owned split-divider persistence without introducing a new data authority.
- Rebuilt Asset Register and Chart of Accounts as table-over-scrollable-editor workspaces.
- Rebuilt both Banking sections and the User Admin user/assignment tabs with dedicated table/editor splits and independently scrollable forms.
- Moved the Budget amount editor out of the header into its own scrollable split region and protected unsaved target amounts from row, refresh, and activation loss.
- Added dirty-state reporting and discard protection to Assets, Banking, Budget Editor, Chart of Accounts, Preferences, and User Admin.
- Changed Administration dirty aggregation to inspect Preferences, Company Admin, and User Admin regardless of which tab is selected.
- Added focused source guardrails and Xvfb behavior coverage for all seven targets; no domain services, H2 models, or ledger behavior changed.

Next exact action:

- None; P14-S2 is DONE.

### P14-S3 — Financial view formatting and table-layout compliance

Status: DONE through merged PR #183 and owner desktop verification.

Branch: `codex/P14-S3-financial-view-formatting-compliance`
Pull request: #183, merged into `main` at `9e5edf02e7d85ae14f317435c0527788d2ec82f1`.
Base head: `96993c9a5c143dbd617ba6ac7ec5c1c0026ad980`
Activation head: `df737cc9d498e1fa0222b4989ae1d0fcadca4715`
Implementation head: `6e4ddc45f64a50d5f48beeb16aa257c49b8cbfab`
Validation: Maven PR Tests run `29789423476` passed, including the complete established suite, production-route Xvfb coverage, focused financial-view source guardrails, and company formatter behavior tests.
Final Maven PR Tests run `29789568217` passed on documentation head `56060b06e86c3e782a91a6fdde4e7326bfb24c87`.
The owner verified company-specific formatting, horizontal divider behavior/restoration, independent scrolling, and the visible Audit History title, then confirmed the slice for merge.

Planned scope:

- Repair Dashboard, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, Import Preview, Audit History, and Bank Transactions.
- Apply company money/date formatting, unconstrained tables, company-owned state, and required split regions without expanding their domain workflows.

Completed implementation:

- Routed Dashboard, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, Audit History, and Bank Transactions money/date presentation and editing through active-company `CompanyUiFormat`.
- Extended the shared formatter to company-aware date/time display without changing persisted timestamp values.
- Removed scoped constrained table policies and retained the universal H2 company-owned table-state boundary.
- Added company-owned split-divider state to Dashboard table cards, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, and Import Preview.
- Stacked Depreciation and Import Preview table regions top/bottom with horizontal draggable dividers; preserved independent table scrolling and current domain operations.
- Renamed the visible legacy Approval Audit surface to factual Audit History while retaining its stable compatibility identifier and repository.
- Added focused source and formatter behavior guardrails. No services, entities, migrations, canonical ledger behavior, or domain workflows changed.

Next exact action:

- None; P14-S3 is DONE.

### P14-S4 — Help, production desktop sweep, and final compliance closure

Status: DONE through merged PR #184 and owner desktop verification.

Branch: `codex/P14-S4-help-desktop-compliance-closure`
Pull request: #184, merged into `main` at `cf1c99788505b1cbb90bd92da1dcbc902af9a765`.
Base head: `9e5edf02e7d85ae14f317435c0527788d2ec82f1`
Activation head: `bffbe75ecb6e883dfcc6e909ec0104fe46c4d72b`
Implementation head: `3a6f5e4bcd2ecc1e8e9076d8ce661137e3fd7a1a`
Validation: Maven PR Tests run `29791007379` passed, including the complete established suite, exact all-destination production H2/Xvfb route smoke, alias reuse checks, and focused Help/menu/path guardrails.
Final Maven PR Tests run `29791138579` passed on documentation head `c70b5c981c4ace9db9913e672f19375a8227d86e`.
The owner completed and confirmed the final laptop-width production desktop sweep after merge, including Help, canonical destination names, all navigation routes, sidebars, scrolling, dividers, tooltips, formatting, dirty prompts, and company switching.

Planned scope:

- Correct Help navigation/document links, run the all-destination production smoke path, perform laptop-width desktop validation, and close only verified residual design-rule findings.

Completed implementation:

- Rebuilt Help as a laptop-safe scrollable surface with current database, active-company, active-period, Journal, Banking, import, and Administration guidance.
- Replaced nonexistent/wrong-owner Help links with the authoritative repository, plan, UI rules, and development-workflow paths; browser-unavailable failures now show the copyable URL instead of failing silently.
- Replaced retired Ledger Register and Transaction Editor entries in the production Destinations menu with canonical Journal, Administration, and Help entries.
- Strengthened the production H2/Xvfb route smoke to require the exact canonical destination set, reject the eliminated Schedules route, and prove both retired Journal aliases reuse one existing Journal root/tab.
- Added focused Help/menu source and documentation-path guardrails. No domain service, persistence, schema, ledger, or accounting behavior changed.

Next exact action:

- None; P14-S4 is DONE.

### P14-C1 — Final plan-ledger reconciliation

Status: DONE through documentation-only PR #185.

Branch: `codex/P14-C1-finalize-plan-ledger`
Pull request: documentation-only PR #185.
Base head: `cf1c99788505b1cbb90bd92da1dcbc902af9a765`
Activation head: `b41544371165bbb2fdb45c1a7a764c320334c37e`

Purpose:

- Record the owner-confirmed P14-S4 desktop sweep after product PR #184 merged with the slice still marked VERIFYING.
- Mark P14-S4 and P14 DONE only through a fresh documentation-only PR from current main.

Completed deliverables:

- Verified PR #184 merged at `cf1c99788505b1cbb90bd92da1dcbc902af9a765` after both Maven PR Tests runs passed.
- Recorded the owner's final desktop acceptance of Help, navigation, sidebars, scrolling, dividers, tooltips, company formatting, dirty prompts, and company switching.
- Reconciled the phase index, active front matter, P14 status, P14-S4 record, and next action without changing product code.

Next exact action:

- None after PR #185 merges; P14 is DONE and no later phase is authorized without an explicit plan amendment.


# P15 — Versioned data interchange and database transfer

**Selector:** `PHASE=P15`  
**Status:** DONE through P15-S8, merged PR #249, and documentation-only P15-C1 / PR #250
**Depends on:** P02, P05, P06, P12, P13, P14

Purpose: provide safe, previewable, versioned transfer of active-company business data, reusable Charts of Accounts, complete database copies, and bank-statement records without creating a second ledger, a parallel persistence model, or the eliminated generic Import/Export Jobs framework.

Established boundaries:

- SCLX is active-company business-data interchange reconstructed from current canonical H2 authority; it is not a raw H2 backup.
- Chart of Accounts JSON is an independently versioned chart-transfer format; it is not SCLX and does not contain transaction history.
- Database backup/import is a separate whole-database administration workflow.
- OFX 2.x, QFX, and bank CSV are single-account bank-statement interchange formats. They do not represent double-entry accounting and must not be advertised as complete ledger export.
- Imported bank records become durable review facts in `bank_import_batch`, `bank_statement_line`, and `import_issue` before any explicit canonical transaction acceptance or reconciliation action.
- SCLX bank sections preserve supported source statement metadata and match/reconciliation facts; OFX/QFX/CSV remain dedicated bank-statement entry and exit formats.
- Every import has a non-mutating preview and validation result before commit. Blocking errors prevent all writes.
- Every commit is atomic at its documented boundary and reports created, updated, skipped, warning, and error counts.
- No workflow in P15 writes compatibility journal/open-item tables, serializes JPA entities directly, retains passwords, or revives generic durable job tracking.

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/data-exchange/shared-operation-contract.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/audit/audit-history.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/administration/company-lifecycle.md`
- `doc/workflow/development-workflow.md`
- donor `benbaron/NonprofitAccounting` SCLX, COA, bank import/export, database administration, tests, and actual emitted fixtures as reference only

Required inspection:

- `Company`, `ChartOfAccounts`, `Account`, `Txn`, `TxnSplit`, `Fund`, `BudgetCategory`, `Activity`, `Counterparty`, `Merchant`, bank, asset, inventory, reconciliation, and audit entities and their company-ownership paths.
- `ImportExportOrchestrationService`, `ImportPreviewService`, `CoaCsvMapper`, `OfxQfxTransactionExtractor`, `BankDataEnvelopeRecognizer`, `BankImportNormalizationService`, `BankImportReviewService`, `ReconciliationComparisonService`, canonical transaction services, and current export adapters.
- `ImportPreviewPanel`, `BankTransactionsPanel`, `BankingPanel`, `ChartOfAccountsPanel`, production File menu, workspace lifecycle, dirty-state, progress, and company-owned UI-state composition.
- Flyway migrations and tests for company ownership, accounts, canonical transactions, banking imports, reconciliation, fixed assets, inventory, period close, audit history, and database bootstrap/recovery.
- Donor SCLX versions 1.0/1.2/1.3, COA JSON output, import modes/options/results, round-trip tests, and database-transfer behavior. Port behavior deliberately; do not copy donor sidecar repositories or alternate shell architecture.

## P15-S0 — Interchange contract and donor fixtures

Status: DONE through merged PR #187, passing final-head CI, and owner confirmation.

Branch: `codex/P15-S0-interchange-contracts`  
Pull request: #187, merged into `main` at `b9f61d8805e93772ee42ccf07081b5b0bcb92d5c`  
Base commit: `9f3e67e53cf7e96dd41d09abaafb1985535f9fce`  
Tested implementation commit: `96482a783d90fd81e2f6ac3df34c04d001389a17`  
Final PR head: `11ec3f3f43139ec30710f0d76057e7e8826227e4`  
Merge commit: `b9f61d8805e93772ee42ccf07081b5b0bcb92d5c`  
Donor reference commit: `c697630ec1f784ebe8338d7300da6c9ac801b180`

PR #186 merged the P15 authorization into `main` but delivered only `doc/PLAN.md`. Its former branch is not reused and does not satisfy this slice.

Purpose: freeze the governing contracts, compatibility fixtures, authority boundaries, security limits, and slice dependencies before product implementation.

Completed deliverables in this branch:

- Added `doc/data-exchange/sclx.md`, `doc/data-exchange/chart-of-accounts-json.md`, `doc/data-exchange/database-transfer.md`, and `doc/data-exchange/bank-statement-interchange.md`.
- Governed SCLX content identification, read compatibility for 1.0/1.2/1.3, deterministic 1.3 output, active-company scope, exclusions, `AS_IS`/`MAPPED`, generated balancing lines, identities/conflicts/duplicates, extensions, atomic writes, limits, results, and SHA-256.
- Generated the donor COA JSON compatibility fixture from the donor service at the recorded commit and froze a separate deterministic `SCA-COA` 1.0 shape.
- Governed `CREATE_NEW_CHART`, `MERGE_BY_CODE`, and `MAP_CODES`, including hierarchy, cycle, type, balance, posting, history, opening-balance, idempotency, and no-delete rules.
- Governed supported H2 backup/restore to a new target, exclusive access, active-database overwrite prohibition, migration/validation-before-switch, corrupt-input behavior, and existing database-session/recovery composition.
- Governed OFX 2.x XML, XML-body QFX, OFX 1.x SGML-body QFX, mapped CSV, and normalized round-trip CSV, including content-first recognition, account identity, dates, signs, FITID, corrections, balances, duplicates, durable review authority, deterministic export, and XML security.
- Added fictional valid and invalid fixtures for all required formats and compact executable boundary descriptors instead of committing enormous generated files.
- Added `DataExchangeFixtureContractTest` for JSON/XML/QFX/CSV syntax and shape, XML entity rejection, identifiers/references/cycles, duplicate cases, normalized round trips, format-separation guardrails, fictional-data checks, database-transfer hostile archives, and SHA-256 fixture governance.
- Audited direct and indirect company ownership in `doc/persistence-authority-inventory.md` and recorded the nondestructive P15-S1 migration sequence.
- Updated `doc/interface-operation-matrix.md` with four distinct exchange authorities and no generic Import/Export Jobs destination.
- Confirmed the intended diff contains no production entity, migration, service, repository, or JavaFX behavior change.

Discovered prerequisite:

- Active-company SCLX export is not safe on the current schema because charts, canonical transactions, funds, budgets, activities, counterparties, merchants, compatibility periods, and generic audit events lack unambiguous company ownership; several global business-key constraints also need company scope. P15-S1 now owns the exact nondestructive migration/backfill/isolation work. P15-S0 does not implement it.

Validation status:

- Focused fixture validation: PASS under Temurin 17 — `DataExchangeFixtureContractTest`, 18 tests, 0 failures, 0 errors, 0 skipped.
- Full local `mvn clean verify`: PASS under Temurin 17 with repository Maven settings and an offline resolved cache — 355 tests, 0 failures, 0 errors, 21 intentionally skipped display-dependent tests; application JAR built.
- Local virtual-display compliance subset matching `.github/workflows/maven-pr-tests.yml`: PASS — 4 tests, 0 failures, 0 errors, 0 skipped.
- Donor fixture: generated by `ChartOfAccountsIOService` at donor commit `c697630ec1f784ebe8338d7300da6c9ac801b180`; frozen fixture SHA-256 `a379c6d55a4c7cba17cf3ffb2e6c1255d4050ba03f48b6a4fb248e7369bce0c1`.
- GitHub Maven PR Tests run `29885296049` passed on final PR head `11ec3f3f43139ec30710f0d76057e7e8826227e4`, including the main Maven test step and the production JavaFX route-compliance step under Xvfb.
- PR #187 merged into `main` at `b9f61d8805e93772ee42ccf07081b5b0bcb92d5c`; the owner confirmed acceptance.

Acceptance:

- Each format has one documented authority, version policy, import/export scope, security boundary, and error policy.
- Golden fixtures prove donor compatibility claims and the intended OFX/QFX/CSV boundary.
- The plan identifies every prerequisite that otherwise makes active-company export ambiguous.
- The generic Import/Export Jobs destination and generic durable job log remain eliminated.

Next exact action:

- None; P15-S0 is DONE. P15-S1 is authorized and executing in draft PR #189.

## P15-S1 — Shared operation contract, company ownership, and external identity

Status: DONE through the owner-applied implementation now present on current `main` at `539f0f388eae89363e6ca1dcab136dc6cbd7f2ed`.

Former branch: `codex/P15-S1-company-ownership-reimplementation`  
Superseded pull request: #193, closed/merged before the owner manually applied the complete archive  
Authoritative current-main head: `539f0f388eae89363e6ca1dcab136dc6cbd7f2ed`

Purpose: make selected-company interchange structurally unambiguous before any SCLX company export/import implementation.

Completed deliverables in this branch:

- Add immutable shared preview, validation-message, confirmation, progress, operation-count, and result types used by SCLX, COA JSON, database transfer, and bank-statement exchange without conflating their DTOs or authorities.
- Preserve preview only, validate only, commit to active company, and create/import into a new database/company only where each governed format permits them.
- Add nullable ownership columns, indexes, diagnostics, and deterministic backfills for `ChartOfAccounts`, `Txn`, `Fund`, `BudgetCategory`, `BudgetPlan`, `Activity`, `Counterparty`, `Merchant`, retained `AccountingPeriod`, and business `AuditEvent` records.
- Add `company_id` ownership for `period_close_range`/`period_close_event`, replacing mutable code-only authority while preserving current company-code rename compatibility during migration.
- Diagnose and block zero-owner, multi-owner, and cross-company historical rows. Never assign the current UI company as a silent default.
- Backfill ownership through existing direct-company records where deterministic: configured bank accounts/imports/reconciliation, fixed assets/depreciation, inventory/movements, and transaction dimensions.
- Change global business-key uniqueness to company-scoped uniqueness where the audit requires it: fund code, budget category code, budget-plan fiscal-year/version, activity code, merchant business key, and retained accounting-period year/number. Keep `company.code`, usernames, and role codes intentionally global and account codes chart-scoped.
- Enforce same-company service boundaries for chart/accounts, transaction/splits/dimensions, corrections, fund transfers, bank/config/import/statement/matches, asset/depreciation, inventory/movements, close events, and audit facts.
- Add durable interchange identity keyed by company, format, source system, entity type, and external ID. This is deduplication/traceability evidence, not a job queue.
- Add migration-upgrade, collision-diagnostic, ambiguous-row, rollback, and multi-company-isolation tests.
- Keep compatibility reconciliation/period-run records excluded from selected-company SCLX unless they receive explicit company ownership and remain factual authority.

Acceptance:

- Every record eligible for active-company SCLX export has one provable authoritative owner.
- Every included reference is constrained or service-validated to the same company.
- Reimport distinguishes identical, new, and conflicting records without local numeric primary keys.
- Migration leaves ambiguous data untouched and reported rather than guessed.
- Shared contracts remain independent of JavaFX and JPA entities, and no generic job framework returns.

Validation status:

- Focused P15-S1 migration, ownership, identity, rollback, and contract tests: PASS.
- Complete established test catalog plus P15-S1 additions, executed in bounded local Maven batches: 368 tests, 0 failures, 0 errors, 21 documented display-dependent skips.
- Local virtual-display compliance subset matching `.github/workflows/maven-pr-tests.yml`: PASS — 4 tests, 0 failures, 0 errors, 0 skipped.
- The owner manually applied the complete implementation archive and merged the resulting source to `main`.
- Current `main` contains V61, the shared interchange contracts, ownership entities/services, updated persistence mappings, and focused tests; temporary `.p15-publish` payload files are absent.

Next exact action:

- None; P15-S1 is DONE. P15-S2 is authorized and begins from current main.

## P15-S2 — Whole-database backup, import-copy, validation, and recovery

Status: DONE through merged PR #195 for the transfer service and merged PR #196 for production UI/session integration, followed by owner verification.

Planned deliverables:

- Implement consistent H2 backup through supported H2 backup/script facilities rather than copying an open `.mv.db` file.
- Restore/import only into a new explicit target path by default, migrate and validate it, then offer a guarded database switch.
- Require exclusive-access and backup confirmation for any repair/recovery action that could modify a database.
- Display exact source, destination, backup, and validation-result paths.
- Reuse current database-session, Dashboard recovery, diagnostics, and migration composition; do not create a second database controller.
- Add failure-injection, source-equals-target, active-database overwrite, corrupt input, version migration, and round-trip tests.

Acceptance:

- A backup restored to a new database preserves every H2-backed company and record, including compatibility structures intentionally retained by current schema.
- Failed validation never replaces or changes the active database.
- Database transfer remains visibly separate from SCLX and COA JSON.

Validation status:

- The whole-database backup/restore-copy service, validation, guarded switching, File menu routes, and Administration surface are merged on current `main` through PRs #195 and #196.
- CI passed on the merged implementation heads, and the owner confirmed the database transfer workflow before authorizing P15-S3.

Next exact action:

- None; P15-S2 is DONE.

## P15-S3 — Chart of Accounts JSON import and export

Status: DONE through merged PR #197 after owner desktop acceptance.

Final implementation head: `16235ba0b8bc02caa525a90465a4027c95dae5a0`

Planned deliverables:

- Add DTO-based, deterministic, pretty-printed UTF-8 JSON; never serialize Hibernate entities.
- Import the actual donor compatibility shape and export a documented independently versioned shape.
- Preserve every donor field with a valid current-model equivalent, including account number/code, name, type, normal/increase side, parent, currency, opening balance, funds, and supplemental-detail kinds; warn on unsupported fields.
- Support `CREATE_NEW_CHART`, `MERGE_BY_CODE`, and explicit `MAP_CODES`; missing input accounts never delete or deactivate local accounts.
- Preview and validate duplicate codes, parent references, cycles, type/subtype/normal-balance compatibility, posting state, dates, and history restrictions before any write.
- Treat opening-balance or history-sensitive structural changes as financial changes requiring explicit supported policy and confirmation.
- Persist parent-before-child in one transaction and make repeated identical import idempotent.
- Add Import JSON and Export JSON actions to the existing Chart of Accounts workspace with dirty-state and desktop design-rule compliance.

Acceptance:

- Export/import into a clean company produces a semantically equivalent chart.
- Blocking errors or injected late failure produce no partial chart.
- Merge does not delete absent accounts and identical reimport makes no changes.

Completed deliverables:

- Added strict DTO-based donor-compatible and `SCA-COA` 1.0 parsing with bounded UTF-8, duplicate-key, size, depth, string, number, account-count, date, code, name, and decimal validation.
- Added deterministic parent-before-child JSON export through temporary-file plus atomic move, with byte count and SHA-256.
- Added non-mutating `CREATE_NEW_CHART`, `MERGE_BY_CODE`, and `MAP_CODES` previews covering hierarchy, cycles, mappings, types, subtypes, normal balances, currency, dates, opening balances, unsupported fields, and history-sensitive conflicts.
- Added one caller-owned JPA transaction for parent-before-child account writes, same-transaction durable interchange identities, no automatic activation, no deletion/deactivation of absent local accounts, idempotent reimport, and complete late-failure rollback.
- Added the production `ChartOfAccountsInterchangePanel` wrapper with explicit Import JSON and Export JSON actions while retaining `ChartOfAccountsPanel` as the sole account editor.
- Added donor conversion, deterministic export, malformed input, hierarchy, mapping, create-new, merge-idempotency, absent-account retention, and rollback tests.
- Added proxy-free GitHub Actions Maven settings while retaining the existing developer/Codex proxy settings.

Validation status:

- Maven PR Tests run `30141677017` passed on tested implementation head `ae8a30de51f166c8081b6974921b7aa771de1579` after the fixture and IDENTITY-persist corrections.
- Final Maven PR Tests run `30141967625` passed on clean-verification head `555206c60009410fe0a51afe9a1fff6957572cd6`.
- `mvn clean verify`, the repeated normal Maven test suite, and the focused JavaFX route-compliance suite all passed through proxy-free GitHub Actions settings.
- The earlier eight fixture errors were corrected by using non-reserved test-company codes; the later three rollback failures were corrected by populating new IDENTITY accounts before `persist`.

Owner verification:

- The owner completed the documented Chart of Accounts JSON desktop acceptance checks and authorized merge.
- PR #197 merged to `main` at `80fc7367d5b66f77a0d8d4414b1fc20a224b93e7`.

Next exact action:

- None; P15-S3 is DONE.

## P15-S4 — SCLX model, parser, and deterministic active-company export

Status: DONE through merged PR #226 and owner desktop acceptance.

Final exact tested head: `6ed522251193ae3aa16942f84dd8ff4a91556ebb`; merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`.

Incremental completed deliverables:

- PR #198: SCLX version/account-mode scaffold and governed source artifact publication.
- PR #199: strict bounded UTF-8 parser for SCLX 1.0, 1.2, and 1.3.
- PR #200: structural/reference validation and entity limits.
- PR #201: immutable SCLX 1.3 export DTO and governed included/excluded section catalog.
- PR #202: export snapshot identity/reference validation and exact debit/credit balance checks.
- PR #203: deterministic H2-independent portable identity derivation.
- PR #204: selected-company core DTO assembly for company, active-chart accounts, and funds.
- PR #205: bounded JPA loading of selected-company active-chart accounts and company-owned funds, with inactive-row retention and multi-company isolation coverage.
- PR #206: durable UUID identity for canonical `Txn` rows, with V62 backfill/default/uniqueness enforcement so transaction export does not use local numeric IDs.
- PR #207: selected-company budget plan/line and canonical `Txn`/`TxnSplit` snapshot mapping, including period-scoped budget identities, exact debit/credit conversion, correction links, deterministic business ordering, and cross-company rejection.
- PR #208: deterministic Jackson tree serialization, guarded same-directory atomic file replacement, overwrite/path/active-database protections, SHA-256, entity counts, deferred-section warnings, and explicit exclusions.
- PR #209: production File-menu selected-company SCLX export with frozen company/database scope, asynchronous execution, explicit overwrite confirmation, exact counts/hash/warnings/exclusions presentation, route tests, and desktop acceptance instructions.
- PR #210: selected-company Activity export under `extensions.scaJakartaH2.activities`, stable company/code identities, transaction-line activity references, strict extension/reference validation, deterministic ordering, counts, and inactive-row retention.
- PR #211: durable UUID portable identities for `Counterparty` and `Merchant`, with V63 backfill/default/non-null/uniqueness enforcement, entity initialization, migration tests, and identity-contract documentation.
- PR #212: selected-company counterparty and merchant export, standard transaction-line payee references, deterministic line-level merchant links, strict ownership/reference validation, counts, inactive-row retention, and desktop acceptance updates.
- PR #213: selected-company supplemental transaction-detail export, deterministic transaction-local identities, complete persisted field preservation, strict semantic/reference validation, counts, and desktop acceptance updates.
- PR #214: durable UUID portable identities for banks, configured company bank accounts, import batches, statement lines, import issues, reconciliation sessions, and reconciliation matches, with V64 backfill/default/non-null/uniqueness enforcement and focused migration/entity coverage.
- PR #215: selected-company bank configuration, reviewed import/statement provenance, transaction-line clearance facts, reconciliation sessions/matches, strict ownership/reference validation, deterministic ordering, counts, and desktop acceptance updates.
- PR #216: intrinsic UUID portable identities for fixed assets and completed depreciation runs, with V65 backfill/default/non-null/uniqueness enforcement, focused entity/migration/recovery coverage, identity-contract documentation, and no fixed-asset SCLX mapping.
- PR #218: selected-company fixed assets and completed depreciation runs under `extensions.scaJakartaH2.fixedAssets`, preserving all authoritative asset fields, account/fund references, canonical transaction provenance, deterministic intrinsic identities, exact counts, strict ownership/reference validation, and nullable notes.
- PR #219: corrective repair of the merged fixed-asset export assembly after overlapping implementations caused production compilation drift.
- PR #220: fixed-asset test-contract reconciliation, restoring governed accessors, identities, builder ordering, and assembler calls.
- PR #221: intrinsic UUID portable identities for inventory items and movements, with V66 recovery-safe backfill/default/non-null/uniqueness enforcement.
- PR #222: governed `extensions.scaJakartaH2.inventory` contract documentation only; no production implementation was included.
- PR #223: corrective selected-company inventory item and movement export implementation, including deterministic identities/order, strict ownership/reference validation, exact counts, completion-summary integration, and focused tests.
- PR #224: governed period-close extension foundation, range/event snapshot mapping, and portable identity contract; merged before production query/count/summary integration was complete.
- PR #225: corrective period-close production integration, strict validation, exact range/event counts, completion-summary integration, focused tests, and successful Maven PR Tests; merged at `6959f57daf840b9f93edb0bd9ed9a8d188685170`.
- P15-S4-C6: add durable AuditEvent portable identity and selected-company `extensions.scaJakartaH2.auditHistory` export, validation, counts, tests, and governing-document reconciliation.

Validation status:

- PRs #198 through #216 are merged.
- Maven PR Tests run `30221018029` passed on PR #204 head `2663adf380b47a94776234ac62247480f38712da`.
- Maven PR Tests run `30221508687` passed on PR #205 implementation head `413e0712004e5c2b8035a88d34c61eaa1463832b`.
- Final Maven PR Tests run `30221662744` passed on PR #205 plan-inclusive head `f5b114fd1922934fb63cd826a6ad7a91789f8faa`.
- Initial PR #206 run `30228781451` exposed a complete-schema recovery conflict because Hibernate could already create `txn.portable_id`; V62 was corrected to use recovery-safe `IF NOT EXISTS` operations.
- Maven PR Tests run `30229244201` passed on clean PR #206 review head `db1bae8fad0b0bdea50e1b80fadbedea225eeb48`, including `mvn clean verify`, the repeated test suite, and JavaFX production-route compliance.
- Final PR #206 run `30229515838` passed on plan-inclusive head `0d3546bbac752c77f255bd066798022914c37052`; PR #206 merged at `ba27c7218fd450a4e74fdb37ca788efd3e1ec1c5`.
- Initial PR #207 run `30231726888` exposed a test-fixture lifecycle violation because an `ACTIVE` budget plan lacked required `activatedAt`; the fixture was corrected without changing production behavior.
- PR #207 run `30231944656` passed on clean implementation head `3914cdaf42f0c1e3939cca4ca3cb7add4fe4291a`, including `mvn clean verify`, the repeated test suite, and JavaFX production-route compliance.
- Final PR #207 run `30232140766` passed on plan-inclusive head `cf14dfe6bcbae194e44c140060f82c0dcf8e8b68`; PR #207 merged at `d35281a558083b28117dc6668324d117aa6e7cf9`.
- Initial PR #208 run `30235977494` exposed one extra closing parenthesis in the new serializer test fixture; production sources compiled and the fixture was corrected.
- Focused PR #208 verification passed 7 tests with 0 failures and 0 errors after the correction.
- Maven PR Tests run `30236206639` passed on clean implementation head `43b6ada84f7531d7e235aac4d0ac6b7f9ae60ffb`, including `mvn clean verify`, the repeated test suite, and JavaFX production-route compliance.
- Final PR #208 run `30236397436` passed on plan-inclusive head `dfa7a25db3f89eee99b3a478137ac02ca1ad2520`; PR #208 merged at `96004c909192d663cdc168de35b871ca241bca66`.
- Initial clean PR #209 run `30238993416` exposed an invalid indexed JavaFX `ObservableList.addAll` overload in the fallback File-menu insertion; the implementation was corrected to the established indexed `List.of(...)` form.
- Focused PR #209 run `30239143008` passed 8 tests with 0 failures and 0 errors after the correction; 4 display-dependent cases were skipped because that focused diagnostic did not install Xvfb.
- Maven PR Tests run `30239221873` passed on clean implementation head `18aac1b988a6604521e7d3f1e59bc310dd34a7fd`, including `mvn clean verify`, the repeated test suite, and JavaFX production-route compliance under Xvfb.
- Final PR #209 run `30239456025` passed on plan-inclusive head `4717f19e533112255cc4fccf24742d9d8a3ac211`; PR #209 merged at `97d7b67fd601fb83b1e32d177690512e93e89592`.
- Initial clean PR #210 run `30319123316` passed compilation and 429 of 430 tests but exposed a stale serializer assertion that expected custom extension key `alpha` before the newly governed, correctly sorted `activities` key.
- Corrected PR #210 diagnostic run `30319427594` passed `mvn clean verify` with 430 tests, 0 failures, and 0 errors.
- Clean PR #210 implementation run `30319554988` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on head `a1699d91d3db13fc9f94869641654f1a29d9594e`.
- Final PR #210 run `30319837886` passed on artifact-free, plan-inclusive head `910de5a4c8e3e7a906c39e8588242e1d9ce869b4`; PR #210 merged at `64a3056392c5d723043f154f9ff26a151b3652a4`.
- PR #211 Java sources and focused tests passed Java 17 syntax compilation with bounded local stubs.
- Maven PR Tests run `30323574869` passed on clean, plan-inclusive PR #211 head `41280d0f26fe58afaf8f25fb71dd76035d2eebd8`, including `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance.
- Final PR #211 run `30323799799` passed on artifact-free review head `881dfed2d06f9104e166c82ecf9dbb81436a16ed`; PR #211 merged at `3b4a39dc8287b5ecd7dca57c213558619e7c90ac`.
- Initial clean PR #212 run `30327910606` compiled production code and passed 437 of 438 tests but exposed a shifted `SclxExportCounts` test fixture after counterparties and merchants were added.
- The fixture was corrected to preserve its intended budgets, transactions, transaction-lines, and total counts without changing production behavior.
- Clean PR #212 implementation run `30328268651` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on head `8364a1bd0abd8bc48f4673aa9e18bc28ebea5afc`.
- Final PR #212 run `30328459742` passed on artifact-free, plan-inclusive head `6bca1c6dfae8ad155af03458107b4645e7e6d68d`; PR #212 merged at `ab78c7faee8efe061c18375eef8518800fbec669`.
- PR #213 contract, validator, assembler, query, and focused test sources passed bounded Java 17 syntax compilation before publication.
- Clean PR #213 implementation run `30331172014` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on artifact-free head `264942af84ea74c8882a374cec573d94fef617b5`.
- Final PR #213 run `30331442228` passed on artifact-free, plan-inclusive head `6ee989d4a6030ea674c5ebe572eccc9dd0ed25fa`; PR #213 merged at `5716d5472e8af07e04fc0c261af805ba15915f5b`.
- PR #214 entity mappings and focused tests passed bounded Java 17 syntax compilation before publication.
- Clean PR #214 implementation run `30402166342` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on artifact-free head `db5c4a8c3c4271dbb60a526e95a8ce77be6edd41`.
- Final PR #214 run `30402481760` passed on artifact-free, plan-inclusive head `dfdae4509016ece2ed77146699651cb4af1fbd16`; PR #214 merged at `906deaee9b37e889012faf7c3c5cc44ec3e8cd6b`.
- PR #215 production and focused test sources passed bounded Java 17 syntax compilation before publication; an assembler/validator runtime smoke test also passed.
- Initial clean PR #215 run `30416169502` passed 446 of 447 tests but exposed that H2 returns native-query UUID columns as 16-byte arrays rather than `UUID` objects.
- The banking query conversion was corrected to accept exact 16-byte UUID values while retaining native `UUID` and text handling.
- Clean corrected PR #215 run `30416441389` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on head `59f80e1d6e474a47b3292a64b34c6813034dc74c`.
- Final PR #215 run `30416682585` passed on artifact-free implementation head `33a576ce923eea886dce8d23ca048b704546f7a0`; PR #215 merged into `main` at `f952e6cdfabc256488b6934556525baab6a8f9e7`.
- PR #216 entity mappings and focused tests passed bounded Java 17 syntax compilation before publication. That review caught and corrected an illegal Java escape in the migration-test JDBC URL before any repository commit.
- Temporary publication run `30424653524` applied the reviewed SHA-256-verified patch and removed all payload chunks and its workflow from the branch head. The resulting bot-pushed recursive Maven run `30424668038` was marked `action_required` without starting jobs, so a normal repository commit retriggered validation.
- Clean PR #216 implementation run `30424725757` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on artifact-free head `ef081a93975a3ba68c094d890322b876bd1df57e`.
- Final PR #216 run `30425048793` passed on exact head `7bdaa1858c5b3f659a0ec24c8aef72ab691d403b`; PR #216 merged into `main` at `c09be2557c7e3a63ae65985a210fc35723586be9`.
- Initial PR #218 run `30475855000` compiled production sources but exposed an existing coordinator fixture using the pre-fixed-assets 21-field export-count constructor; backward-compatible delegation was restored.
- Corrected PR #218 run `30476023044` passed compilation and 452 tests before a focused nullable-notes fixture exposed `Map.copyOf` rejecting absent optional notes; the production extension builder was corrected to omit null optional values without substituting empty text.
- Clean PR #218 implementation run `30476305641` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on exact head `049617344f778000be5e571c40b3c8b8055758e6`.
- PR #218 subsequently merged, and corrective PRs #219 and #220 restored production/test contract consistency; PR #220 merged at `88ab6a816f01d8b39245ee16c0d8c09fd66e87a4`.
- PR #221 passed Maven PR Tests run `30505370010` on head `58c59d8f366b7111b804ba4df75245eefc40f4f0` and merged at `99262cdd7e579943c62ec21eea7927d6f8f123a0`.
- PR #222 merged at `3e8c7c8220a8cfe20ee697d835e28d4cc69e4092` but changed only `doc/data-exchange/inventory-sclx.md`; production inventory export remained open.
- PR #223 publication run `30511264525` reconstructed a SHA-256-verified source archive and removed all temporary payload/workflow files in the resulting implementation commit.
- Initial normal PR #223 run `30511334865` compiled production sources but exposed two stale fixed-asset test calls using the pre-inventory assembler signature; the fixtures were corrected to pass empty inventory lists.
- Corrected PR #223 run `30511454947` compiled production and test sources and ran 462 tests; 461 passed and one new inventory test exposed that the shared extension reader incorrectly required optional fields to be present.
- The shared extension reader now rejects unknown fields while permitting governed optional fields to be omitted; required fields remain enforced by typed readers. The resulting bot-authored head `97c8856a9ab589e776497b70f5e9bec29e71e4d1` requires a normal plan-inclusive commit to trigger authoritative Maven validation.
- Maven PR Tests run `30512885063` passed on exact head `8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.
- PR #224 merged the governed period-close extension foundation before production integration was complete; corrective PR #225 completed selected-company query/assembly, strict validation, exact counts, and completion-summary integration and merged at `6959f57daf840b9f93edb0bd9ed9a8d188685170` after successful Maven PR Tests run `30582139713`.
- Initial authoritative PR #226 run `30595756059` compiled production and ran the new migration/extension tests successfully but exposed one stale export-result fixture that still expected a deferred-section warning after all governed P15-S4 sections became included.
- Corrected PR #226 run `30596426183` passed on exact implementation head `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.
- The owner checklist was reconciled to verify inventory, period-close, and audit-history counts/content and the completed no-deferred-section state. Final Maven PR Tests run `30596836488` passed on clean plan-inclusive head `230f247245073c39747f3573ccb1682e41eaf42f`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.

Planned deliverables:

- Adapt donor SCLX document/parser/options/result concepts while keeping DTOs independent of JPA entities.
- Read SCLX 1.0, 1.2, and 1.3; write deterministic SCLX 1.3.
- Export the active company, chart/accounts, funds, budgets, supported counterparties/activities, canonical transactions/splits, supplemental details, bank configuration and reviewed statement facts, reconciliation facts, fixed assets/depreciation, inventory/movements, period-close facts, and factual audit history where the governed format supports them.
- Put supported application-specific facts not expressible in standard SCLX under a documented `extensions.scaJakartaH2` namespace or list them explicitly as excluded.
- Preserve supported OFX provenance such as FITID, transaction type, transaction/posted dates, check/reference numbers, payee/name, memo, payee ID, correction references/actions, and statement/account metadata.
- Exclude authentication material, UI state, Flyway/H2 internals, filesystem paths, raw attachments, compatibility journal/open-item authority, eliminated Schedules, and generic job history.
- Validate references and balanced canonical transactions before writing through a temporary file and atomic move; report counts, warnings, exclusions, and SHA-256 content hash.

Acceptance:

- Export is reconstructed from current canonical H2 data, so later application edits appear.
- Deterministic exports from unchanged data compare equal except explicitly documented envelope metadata.
- No local numeric primary key is used as a portable identity.

Owner verification:

- The owner completed and passed `doc/P15-S4-sclx-export-ui-user-testing.md`.
- PR #226 merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`.

Next exact action:

- None; P15-S4 is DONE.

### P15-S4-C6 — Selected-company factual audit-history export

Status: DONE through merged PR #226 and owner desktop acceptance.

Scope:

- Add a recovery-safe V67 UUID portable identity for `AuditEvent` without serializing or deriving identity from local numeric IDs or polymorphic `entityId` text.
- Export every factual `AuditEvent` owned by the selected company under governed `extensions.scaJakartaH2.auditHistory` version 1.
- Preserve actor, action/entity types, optional subject identifier, summary, before/after values, reason, and timestamp with deterministic ordering.
- Strictly validate shape and duplicate identity, include exact audit-event counts and completion-summary output, and remove only the audit-history deferred warning.
- Keep application-global/unresolved audit rows, legacy `ApprovalAuditRecord`, users/authentication, UI state, and other-company records excluded.
- Add migration recovery/default/uniqueness tests and focused extension/ownership/count tests.

Current validation status:

- V67 recovery/backfill/default/uniqueness coverage and focused selected-company extension/ownership/count tests pass.
- Initial PR #226 run `30595756059` exposed only the stale warning-message expectation after audit history became included.
- Corrected implementation run `30596426183` passed all Maven PR Tests gates on head `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`.
- Final plan-inclusive Maven PR Tests run `30596836488` passed on clean head `230f247245073c39747f3573ccb1682e41eaf42f`; owner desktop acceptance remains open.

Owner verification:

- The owner completed and passed the P15-S4 desktop checklist.
- PR #226 merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`.

Next exact action:

- None; P15-S4-C6 is DONE.

## P15-S5 — SCLX preview, mapping, and transactional import

Status: DONE through merged PR #237 and owner desktop acceptance.

Startup scope:

- Begin with a coherent non-mutating import-preview slice: parsed-document projection, target-company scope, exact entity/reference counts, unsupported-section reporting, identity classification, account/fund mapping requirements, transaction-balance diagnostics, closed-period/reconciliation conflict diagnostics, and no H2 writes.
- Reuse the existing bounded SCLX parser, validators, shared interchange contracts, durable interchange identity, canonical transaction services, and Import Preview workspace; do not introduce a donor parallel repository or generic job framework.
- Defer transactional commit until the preview contract and mappings are governed and tested.

Current validation status:

- P15-S4 export final exact-head Maven PR Tests run `30597102760` passed all gates before merge.
- The read-only service, target query, projections, deterministic identity/mapping/transaction diagnostics, and focused tests are implemented on PR #228.
- Service correction head `922289b06089fdbd5786634f56da744efdae37d6` passed Maven PR Tests run `30654509351`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Initial completed-route run `30655874712` exposed one compile-time overload ambiguity in the no-argument `ImportPreviewPanel` constructor; no tests executed on that failed head.
- Correction head `b0034047dcb7676eecffb2fb491a06abe9467494` passed Maven PR Tests run `30655981097`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- The production Import Preview route, JavaFX behavior/source tests, governing SCLX/operation-matrix documentation, and owner checklist were published in PR #228.
- The owner completed the P15-S5-C1 desktop checklist and merged PR #228 to `main` at `7b032b71e1e201c530d25b421fe78cb4e957f711`.
- P15-S5-C2 adds the first caller-owned one-transaction core import boundary and focused atomicity/idempotency coverage.
- The initial C2 run `30659561592` exposed one stale unsupported-extension count expectation after production compilation and the remainder of the suite succeeded through that test point.
- Correction head `24f484cec10f4c5d3a87418a404c99393a828a2a` passed Maven PR Tests run `30659855007`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- Final plan-inclusive head `8f371f4ab821286776ad7ddf3753d2f5fbb6c9a4` passed Maven PR Tests run `30660125545`, and PR #229 merged to `main` at `0b7d5faaf3f32018a7f07b2307b000243c9d4208`.
- P15-S5-C3 implements activities, counterparties, merchants, line-merchant relationships, and supplemental transaction details inside the same caller-owned transaction.
- Plan-handoff head `bbf03bae2ce2cf71d895c543a8f260dfca4e904c` passed Maven PR Tests run `30662272533`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- Final C3 head `c5086ae0fff22d046f4ca2b32b4e0bc40d6afaa6` passed Maven PR Tests run `30662503100`, and PR #230 merged to `main` at `d4eed71fe8943ec0e56c3ad57f9892c5ddc49579`.
- P15-S5-C4 imports governed budget plans and lines through caller-owned canonical budget services while retaining the same atomic rollback and idempotent identity boundary.
- Final C4 head `e36ec398a216cfd38836d9213c2860a261fafab6` passed Maven PR Tests run `30665458733`, and PR #231 merged to `main` at `12e7195b528108a246d039d7f7cc41b25f08c5f7`.
- P15-S5-C5 imports fixed assets and completed depreciation runs through caller-owned canonical services after their account, fund, and canonical transaction dependencies exist.
- Final C5 head `9cc26891f381b8ee5bf4273510daf3d5180b10eb` passed Maven PR Tests run `30669920942`, and PR #232 merged to `main` at `9ba6dd0f369566fae78d752c35e4202e57f74428`.
- P15-S5-C6 imports governed inventory items and movement history through caller-owned canonical services after their account, fund, item, and optional canonical transaction dependencies exist.
- Initial C6 runs `30671855732` and `30671989995` compiled production and executed the full suite but exposed only an indentation-sensitive negative-test fixture mutation; production import behavior was not implicated.
- Corrected implementation head `85082fecfdcf509627f85928a0f0908890f531a2` passed Maven PR Tests run `30672084818`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- Final C6 head `2fa863c514875bc9481483e28d61dda71f21065d` passed Maven PR Tests run `30672245197`, and PR #233 merged to `main` at `454e88a68f0e7b96cf7054aefc9f8e2f1157f999`.
- P15-S5-C7 imports governed bank configuration, reviewed statement facts, transaction-line clearance, and reconciliation sessions/matches after their chart, transaction, and statement dependencies exist.
- Initial C7 run `30680763088` compiled production and executed the suite but exposed three whitespace-sensitive banking-fixture insertions and one stale operation-audit action expectation.
- Fixture-correction run `30680861206` passed 482 of 483 tests and exposed the protected identical-reimport edge: the imported transaction's finalized reconciliation blocked the no-op before identity classification could complete.
- Corrected implementation head `4cf2ff68a8d9f50823e52be14874c81f763de77c` passed Maven PR Tests run `30680958690`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- Final C7 head `474003596c8d8cd139d36eda6b513dd5367997dd` passed Maven PR Tests run `30681078096`, and PR #234 merged to `main` at `11e8b74106c0203f64a4e7efadf5e25d2a097174`.
- P15-S5-C8 restores authoritative period-close ranges and factual close/reopen events through a caller-owned `PeriodCloseRangeService` seam after the imported ledger graph exists.
- C8 strict parsing, service writes, populated-target correction, idempotency, pre-mutation mismatch rejection, and late-rollback coverage pass Java 17 grammar validation locally; Maven remains unavailable in the container.
- Implementation/plan head `fa7f19b57bfa2df2a8fde7c451575ebd1b840f32` passed Maven PR Tests run `30681932839`: `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance all succeeded.
- Final C8 head `7a8014f255dbf2026bcc14512bca74e025a599a2` passed Maven PR Tests run `30682072520`, and PR #235 merged to `main` at `f0c1ab20a8f828b24ab2738acb498015e7708b09`.
- P15-S5-C9 restores governed company-owned factual audit history through a caller-owned `AuditHistoryService` seam after the imported business graph exists.
- Final C9 head `758934db92118a94546cd413d022c811315c080d` passed Maven PR Tests run `30721745588`, and PR #236 merged to `main` at `f2aa151c1fa7d61f997dc85938d5596a7c79b31d`.
- P15-S5-C10 restores canonical reversal/replacement relationships, exposes the complete atomic import from Import Preview, verifies semantic re-export, and supplies the final owner desktop checklist.
- Final C10 head `d5351b2d437dbacdf1f852e9cd513f7b06061baf` passed Maven PR Tests run `30766011352`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- PR #237 merged to `main` at `a3b802342cfdcbe3e286320215848842d27bdeea`; the owner confirmed the complete desktop checklist on 2026-08-02.

Next exact action:

- None; P15-S5 is DONE.

### P15-S5-C1 — Non-mutating SCLX import preview

Status: DONE through merged PR #228 and successful owner desktop acceptance.

Implemented scope:

- Parse and structurally validate bounded SCLX 1.0, 1.2, and 1.3 input without beginning a transaction or changing H2.
- Read the explicit target company once for accounts, funds, durable external identities, closed ranges, and finalized-reconciliation protections.
- Show exact section/entity/reference/relationship/unsupported counts; `NEW`, `IDENTICAL`, and `CONFLICT` identity dispositions; account/fund mapping requirements; and transaction balance/protection diagnostics.
- Reject accidental populated-company merge and disclose skipped zero-value/no-posting facts.
- Route **Preview SCLX…** through the shell-owned composition root, capture target scope before background execution, display path-coded messages and exact diagnostics, and keep every SCLX commit action absent.
- Add focused service, production-route, and JavaFX rendering tests plus the owner desktop checklist.

Owner verification:

- The owner completed and passed `doc/P15-S5-sclx-import-preview-ui-user-testing.md`.
- PR #228 merged to `main` at `7b032b71e1e201c530d25b421fe78cb4e957f711`.

Remaining P15-S5 scope:

- Transactional mapping resolution and complete-section commit remain later P15-S5 slices.

### P15-S5-C2 — Core transactional SCLX import service

Status: DONE through merged PR #229.

Scope:

- Re-read and re-preview the approved source immediately before commit; reject changed content or target scope.
- Import the source organization profile, active chart/accounts, funds, and balanced `ENTERED` canonical transactions into an empty explicit target company under `AS_IS` rules.
- Write accounts and funds parent-before-child and route transaction creation through a caller-owned `TransactionEntryService` transaction overload.
- Record source identities for every governed core entity, including deliberately skipped zero-value lines, and make an identical second import a no-op.
- Write one factual operation audit event after the business graph and identities are complete.
- Roll back profile, masters, transactions, transaction audit facts, identities, and operation audit together after any late failure.
- Reject budgets, relationships, non-empty unsupported sections, and every application-extension entity until their canonical writers are implemented.
- Keep the production SCLX commit action absent; P15-S5-C2 is a tested service foundation, not a partial UI workflow.

Validation status:

- Focused H2 integration coverage passes for core graph commit, portable transaction identity, exact identity counts, identical reimport, and injected late-failure rollback.
- Local Maven is unavailable in this container; authoritative Maven PR Tests run `30659855007` passed all gates on correction head `24f484cec10f4c5d3a87418a404c99393a828a2a`.
- Final plan-inclusive Maven PR Tests run `30660125545` passed all gates on head `8f371f4ab821286776ad7ddf3753d2f5fbb6c9a4`.
- PR #229 merged to `main` at `0b7d5faaf3f32018a7f07b2307b000243c9d4208`.

Next exact action:

- None; P15-S5-C2 is DONE.

### P15-S5-C3 — Transaction-linked master and supplemental-detail import

Status: DONE through merged PR #230.

Scope:

- Strictly validate the governed `activities`, `counterparties`, and `supplementalDetails` extension shapes and their transaction-line relationships before opening the caller-owned transaction.
- Create company-owned activities, counterparties, and merchants before canonical transactions, preserving intrinsic party/merchant UUID identities and inactive historical masters.
- Resolve the repeated transaction-line counterparty representation to one canonical transaction-header payee and reject a source transaction that names more than one counterparty.
- Route activity and merchant references plus supplemental transaction details through `TransactionEntryService` inside the existing import transaction.
- Preserve persisted supplemental `lineOrder`, record durable source identities for every new master and supplemental row, and retain identical reimport as a no-op.
- Roll back the company profile, chart/accounts, funds, transaction-linked masters, canonical transactions/splits, supplemental rows, identities, and audit history after any late failure.
- Continue to reject budgets, banking, reconciliation, fixed assets, inventory, period close, imported audit history, corrections, and populated unknown sections.
- Keep the production SCLX commit action absent.

Validation status:

- Java 17 grammar validation passes for every changed Java source.
- Plan-handoff head `bbf03bae2ce2cf71d895c543a8f260dfca4e904c` passed Maven PR Tests run `30662272533`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `c5086ae0fff22d046f4ca2b32b4e0bc40d6afaa6` passed Maven PR Tests run `30662503100`, and PR #230 merged to `main` at `d4eed71fe8943ec0e56c3ad57f9892c5ddc49579`.

Next exact action:

- None; P15-S5-C3 is DONE.

### P15-S5-C4 — Budget plan and line import

Status: DONE through merged PR #231.

Scope:

- Strictly validate governed budget plan and line fields, references, active-version uniqueness, category/fund/period scopes, and exact decimal precision before mutation.
- Create source-referenced budget categories from their portable category codes because SCLX carries no separate category-name master record; do not infer names from accounts or activities.
- Create normalized budget plans and lines through caller-owned canonical budget service overloads inside the existing SCLX transaction.
- Preserve plan name, fiscal year, version, active state, category code, optional fund, optional period month, amount, plan identity, and line identity.
- Reject non-null budget-line account references because the normalized budget model has no account relation and current export deliberately emits that field absent.
- Record durable identities for every imported budget plan and line, retain identical reimport as a no-op, and roll budget categories/plans/lines back with the complete imported graph after a late failure.
- Continue to reject banking, reconciliation, fixed assets, inventory, period close, imported audit history, corrections, and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Java 17 grammar validation passes for every changed Java source.
- Plan-inclusive head `541da393bd0bc2c3ec64e8f649e2abc3244c5f5f` passed Maven PR Tests run `30665277721`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `e36ec398a216cfd38836d9213c2860a261fafab6` passed Maven PR Tests run `30665458733`, and PR #231 merged to `main` at `12e7195b528108a246d039d7f7cc41b25f08c5f7`.

Next exact action:

- None; P15-S5-C4 is DONE.

### P15-S5-C5 — Fixed-asset and completed-depreciation-run import

Status: DONE through merged PR #232.

Scope:

- Strictly validate `extensions.scaJakartaH2.fixedAssets` version 1, including asset and completed-run shapes, identities, values, timestamps, references, and one run per asset/date.
- Create fixed assets through a caller-owned `FixedAssetService` boundary after accounts and funds exist, preserving intrinsic UUIDs, timestamps, accounting references, acquisition facts, method, opening depreciation, status, and notes.
- Record completed depreciation runs after canonical transactions exist, preserving each run's intrinsic UUID, source amount, transaction provenance, notes, and creation time without recalculating depreciation or creating another ledger transaction.
- Record durable identities for every asset and completed run, retain identical reimport as a no-op, and roll both families back with the complete imported graph after a late failure.
- Continue to reject banking, reconciliation, inventory, period close, imported audit history, corrections, and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Local Maven is unavailable; Java 17 source validation and GitHub Maven PR Tests are required.
- Initial implementation head `09c0e38a4531e8f1a4a457ba49cbf9f9564fc89f` is published on draft PR #232.
- Plan-inclusive head `f3da2daa6da8d8b9f98cdbe38c894cc37e68190a` passed Maven PR Tests run `30669727984`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `9cc26891f381b8ee5bf4273510daf3d5180b10eb` passed Maven PR Tests run `30669920942`, and PR #232 merged to `main` at `9ba6dd0f369566fae78d752c35e4202e57f74428`.

Next exact action:

- None; P15-S5-C5 is DONE.

### P15-S5-C6 — Inventory item and movement import

Status: DONE through merged PR #233.

Scope:

- Strictly validate `extensions.scaJakartaH2.inventory` version 1, including item and movement shapes, identities, enum values, exact decimal values, timestamps, and references before mutation.
- Create inventory items through a caller-owned `InventoryService` boundary after accounts and funds exist, preserving intrinsic UUIDs, timestamps, quantity/value facts, lifecycle fields, storage/custodian facts, and notes.
- Record source movement history through the same service after items and canonical transactions exist, preserving intrinsic UUIDs, signed quantity change, resulting quantity, unit value, optional transaction provenance, notes, and creation time without synthesizing another receipt or ledger transaction.
- Record durable identities for every item and movement, retain identical reimport as a no-op, reject populated inventory targets, and roll the complete imported graph back after a late failure.
- Continue to reject banking, reconciliation, period close, imported audit history, corrections, and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Local Java 17 is available but Maven is unavailable; focused source validation and GitHub Maven PR Tests are required.
- Java 17 grammar parsing passes for all eight changed Java sources.
- Initial Maven PR Tests runs `30671855732` and `30671989995` exposed only the new negative test's whitespace-sensitive source mutation; both compiled production and ran the remainder of the suite through that assertion.
- Corrected head `85082fecfdcf509627f85928a0f0908890f531a2` passed Maven PR Tests run `30672084818`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `2fa863c514875bc9481483e28d61dda71f21065d` passed Maven PR Tests run `30672245197`, and PR #233 merged to `main` at `454e88a68f0e7b96cf7054aefc9f8e2f1157f999`.

Next exact action:

- None; P15-S5-C6 is DONE.

### P15-S5-C7 — Banking and reconciliation import

Status: DONE through merged PR #234.

Scope:

- Strictly validate the governed `bankConfiguration`, `bankStatementFacts`, and `reconciliation` extension shapes, portable identities, enum/date/decimal values, exact batch counts, and all chart/transaction/banking/reconciliation references before mutation.
- Recreate banks and configured bank accounts through caller-owned `BankConfigurationService` seams after chart accounts exist, preserving intrinsic UUIDs and all governed configuration facts.
- Recreate reviewed import batches, statement lines, and issues through `BankImportReviewService` after bank accounts and canonical transactions exist, preserving review dispositions and timestamps without replaying normalization or creating another ledger transaction.
- Restore transaction-line clearance through `BankClearedStateService`, then recreate native reconciliation sessions and matches through `BankReconciliationWorkspaceService` after every referenced statement line and canonical split exists.
- Preserve source UUIDs, statuses, balances, timestamps, hashes, and transaction provenance while deliberately excluding source-machine paths and source user names.
- Record durable identities for every imported banking/reconciliation entity, retain identical reimport as a no-op, reject populated banking/reconciliation targets, and roll the complete imported graph back after a late failure.
- Continue to reject period-close facts, imported audit history, corrections, and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Local Java 17 is available but Maven is unavailable; Java grammar validation and GitHub Maven PR Tests are required.
- Java 17 grammar parsing passes for the drafted C7 source and focused integration tests.
- Initial Maven PR Tests run `30680763088` exposed only three whitespace-sensitive fixture insertions and one stale audit-action expectation after production compilation.
- Fixture-correction run `30680861206` passed 482 of 483 tests and exposed the finalized-reconciliation protection on an otherwise identical reimport.
- Preview now keeps authoritative closed/finalized protection facts visible but blocks only a non-identical incoming transaction; an identical imported identity remains the governed no-op.
- Corrected implementation head `4cf2ff68a8d9f50823e52be14874c81f763de77c` passed Maven PR Tests run `30680958690`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `474003596c8d8cd139d36eda6b513dd5367997dd` passed Maven PR Tests run `30681078096`, and PR #234 merged to `main` at `11e8b74106c0203f64a4e7efadf5e25d2a097174`.

Next exact action:

- None; P15-S5-C7 is DONE.

### P15-S5-C8 — Period-close range and factual-event import

Status: DONE through merged PR #235.

Scope:

- Strictly validate `extensions.scaJakartaH2.periodClose` version 1, including exact fields, identities, ISO dates/timestamps, range kind/status, chronology, actor/reason lengths, event references, and non-overlapping active closed ranges before mutation.
- Require exactly one factual `CLOSED` event matching every range's close actor/reason/time and exactly one matching `REOPENED` event for every reopened range.
- Restore authoritative ranges and factual events through a caller-owned `PeriodCloseRangeService` seam after the imported ledger graph exists, preserving intrinsic UUIDs and source facts without replaying interactive close/reopen policy or manufacturing duplicate audit events.
- Record durable identities for every imported range and event, retain identical reimport as a no-op, treat existing period-close-only targets as populated, and roll the complete imported graph back after a failure injected after period-close persistence.
- Continue to reject imported audit history, correction relationships, and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Local Maven is unavailable; Java 17 grammar validation and GitHub Maven PR Tests are required.
- Java 17 grammar parsing passes for the strict projection, caller-owned service seam, commit integration, populated-target reader correction, and focused H2 tests.
- Draft PR #235 contains exactly the ten intended C8 implementation, test, and governing-document files from current merged `main`.
- Implementation/plan head `fa7f19b57bfa2df2a8fde7c451575ebd1b840f32` passed Maven PR Tests run `30681932839`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final plan-inclusive head `7a8014f255dbf2026bcc14512bca74e025a599a2` passed Maven PR Tests run `30682072520`, and PR #235 merged to `main` at `f0c1ab20a8f828b24ab2738acb498015e7708b09`.

Next exact action:

- None; P15-S5-C8 is DONE.

### P15-S5-C9 — Factual audit-history import

Status: DONE through merged PR #236.

Scope:

- Strictly validate `extensions.scaJakartaH2.auditHistory` version 1, including exact fields, durable identities, bounded ISO timestamps, and bounded required and optional factual text before mutation.
- Restore already-authoritative company-owned `AuditEvent` facts through a caller-owned `AuditHistoryService` seam after the imported business graph exists, preserving intrinsic UUIDs, source timestamps, actors, action/entity labels, summaries, values, and reasons without replaying historical commands.
- Treat source-local `entityId` as factual text rather than a portable foreign key, and keep restored source facts distinct from the one new local operation audit recording the import.
- Record durable interchange identities for every imported event, retain identical reimport as a no-op, treat existing audit-history-only targets as populated, and roll the complete imported graph back after a failure injected after audit-history persistence.
- Continue to reject correction relationships and populated unknown extensions.
- Keep the production SCLX commit action absent.

Validation status:

- Local Maven is unavailable; Java 17 grammar validation and GitHub Maven PR Tests are required.
- The focused integration scenarios cover full factual-field preservation, idempotent reimport, strict pre-mutation rejection, audit-only populated-target detection, and late rollback.
- All six changed Java files pass a Java 17 grammar parse.
- Draft PR #236 contains exactly the eleven intended C9 implementation, test, and governing-document files from merged `main`.
- Initial Maven PR Tests run `30721476025` compiled production and executed the suite but exposed two stale C8 operation-audit expectations in existing core and period-close fixtures; no production behavior or C9 test failed.
- Corrected implementation head `738f4c7972c86945e6c9aac1b11033c406a90eb4` passed Maven PR Tests run `30721599317`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final head `758934db92118a94546cd413d022c811315c080d` passed Maven PR Tests run `30721745588`; PR #236 merged at `f2aa151c1fa7d61f997dc85938d5596a7c79b31d`.

Next exact action:

- None; P15-S5-C9 is DONE.

### P15-S5-C10 — Correction relationships and complete production import

Status: DONE through merged PR #237 and owner desktop acceptance.

Scope:

- Strictly project every transaction correction relationship before mutation, requiring resolved same-file references, canonical `REVERSAL`/`REPLACEMENT` pairing and source statuses, one relationship of each kind per original, and an acyclic graph.
- Restore correction links through a caller-owned `TransactionCorrectionService` seam after all canonical transactions exist, without replaying interactive correction commands or synthesizing historical audit rows.
- Preserve the complete C2-C10 graph in the existing one-transaction boundary with portable identities, identical no-op reimport, operational-history protection, and late rollback after correction persistence.
- Expose **Import Previewed SCLX…** only for the exact successful, nonblocking empty-target, approved chart/fund mapping-merge, or wholly identical preview; require a nonblank audit actor and explicit source/hash/target/count confirmation; re-read the exact source and effective mappings inside the commit service.
- Report committed or rolled-back results in Import Preview, invalidate approval after success or rollback, and keep all file/service work off the JavaFX application thread.
- Prove semantic import/export preservation for representative core, budget, transaction-detail, fixed-asset, inventory, banking, reconciliation, period-close, audit-history, and correction facts.
- Add and complete `doc/P15-S5-sclx-import-commit-ui-user-testing.md` before merge.

Validation status:

- All twelve changed Java sources pass a Java 17 grammar parse locally; Maven is unavailable in this container.
- Focused tests cover correction validation, atomic write/idempotency, late rollback, semantic re-export, UI rendering/source guards, and the fixed-scope production route.
- Draft PR #237 contains exactly the seventeen intended C10 implementation, test, governing-document, and desktop-checklist files from merged `main`.
- Initial Maven PR Tests run `30765779256` compiled production and executed 497 tests but exposed one whitespace-sensitive malformed-correction fixture marker; no production behavior failed.
- Corrected implementation head `305beec8c1b0c47b04403d06316dc83d8c25259d` uses a structural JSON-node mutation and passed Maven PR Tests run `30765868528`, including `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance.
- Final plan-inclusive head `d5351b2d437dbacdf1f852e9cd513f7b06061baf` passed Maven PR Tests run `30766011352`; PR #237 merged at `a3b802342cfdcbe3e286320215848842d27bdeea`.
- The owner confirmed every item in `doc/P15-S5-sclx-import-commit-ui-user-testing.md` on 2026-08-02.

Next exact action:

- None; P15-S5-C10 is DONE.

Planned deliverables:

- Preview format/version, entity counts, references, external IDs, account/fund mappings, duplicates, unsupported sections, transaction balance, closed-period conflicts, reconciliation protections, and target-company conflicts without changing H2.
- Support donor-established `AS_IS` and explicit `MAPPED` account reference modes.
- For single-sided or unbalanced source transactions, require an explicitly selected active posting cash account and display every generated balancing line before commit.
- Skip zero-value lines and transactions with no posting lines with explicit warnings and counts.
- Import masters before dependent history, then route financial records through transaction-aware canonical services inside one caller-owned transaction.
- Default to a new or empty target company; P16-C6 supersedes the former blanket populated-target rejection only for an explicitly mapped, nondestructive chart/fund merge before operational history exists.
- Preserve external identity for idempotent reimport; skip identical records and require explicit resolution for conflicts.
- Write one factual audit event with source name/hash, version, mappings, target, counts, warnings, and user after successful commit.
- Roll back the entire documented commit boundary on any failure.

Acceptance:

- Export/import/export is semantically equivalent for every supported section.
- Identical second import creates no duplicates.
- Closed-period and completed-reconciliation protections remain authoritative.
- No compatibility journal table or donor parallel SCLX repository is written.

## P15-S6 — OFX 2.x/QFX and bank CSV import to durable review

Status: DONE through merged PR #241, passing final-head CI and owner desktop acceptance.

Purpose: replace temporary/session bank-transaction staging with a complete, company-scoped, configured-bank-account import and review path.

Planned deliverables:

- Parse OFX 2.x XML and governed real-world QFX envelopes, including XML and explicitly supported SGML/header variants established by P15-S0 fixtures.
- Reject malformed, encrypted, unsupported-message-set, multi-account-ambiguous, oversized, or entity-expansion input safely and explain the blocking reason.
- Add mapped CSV import with previewable column profiles for common amount or debit/credit layouts, delimiter/quote handling, header mapping, date format, sign convention, currency, and account selection.
- Persist reusable CSV mapping profiles in H2 per company and configured bank account only after explicit save; do not use Java Preferences or a sidecar file.
- Normalize source identifiers, transaction and posted dates, amount, type, payee/name, memo, check/reference data, currency, bank/account IDs, and correction metadata before durable review.
- Require an explicit active configured bank account; validate OFX/QFX bank/account identity against it and require a visible override decision for a non-blocking mismatch.
- Reuse and extend `BankImportNormalizationService` and `BankImportReviewService` so one import creates one atomic `bank_import_batch`, its `bank_statement_line` rows, and `import_issue` facts.
- Detect exact duplicates by stable source ID/FITID and probable duplicates by deterministic normalized fingerprint; show both in preview and never silently discard a conflict.
- Wire Import Preview, Banking, and Bank Transactions to the durable review authority and remove `UiWorkspaceDataStore.bankTransactions` when no production consumer remains.
- Do not create canonical `Txn`/`TxnSplit` rows merely because a bank statement was imported; acceptance/matching remains an explicit banking workflow.
- Add parser golden files, malformed/security cases, multi-company isolation, duplicate/correction, CSV profile, rollback, JavaFX behavior, and desktop tests.

Acceptance:

- OFX 2.x, QFX, and CSV imports produce equivalent normalized statement facts for equivalent inputs.
- Reimport is idempotent by source identity/fingerprint and reports skipped/conflicting rows.
- A late failure leaves no partial batch, statement line, issue, or saved CSV profile.
- Restart and company switching preserve only authoritative company-owned review state.

### P15-S6-C1 — Strict OFX/QFX parser and non-mutating preview

Status: DONE through merged PR #238 and owner desktop acceptance.

Scope:

- Replace the production Import Preview path's regex extraction and filename-sufficient recognition with a bounded, content-first parser for governed OFX 2.x XML, QFX 2.x XML envelopes, and QFX 1.x SGML envelopes.
- Enforce secure XML processing, prohibited `DOCTYPE`/external entities, governed header/version/security/compression/encoding rules, one supported statement account, singleton fields, record/depth/attribute/text limits, nonzero `DECIMAL(19,4)` amounts, governed dates/offsets, unique FITIDs, and complete correction pairs.
- Normalize and preview account identity, statement dates/balances, currency, transaction/posted dates, signed amounts, type, payee, memo, check/reference, and correction metadata without writing H2.
- Make decoded content authoritative over filename and show a warning when they disagree.
- Add and complete `doc/P15-S6-C1-ofx-qfx-preview-ui-user-testing.md` before merge.
- Keep durable review commit, configured-account matching/override, CSV profiles, and removal of temporary bank-transaction staging for later P15-S6 slices.
- Retain the existing canonical `bank_import_batch`/`bank_statement_line`/`import_issue` authority and do not create `Txn`/`TxnSplit` rows.

Validation status:

- All changed Java sources pass a Java 17 grammar parse.
- The strict parser compiles through the JDK compiler module and a focused runtime harness passes the three governed valid fixture families plus unsafe XML, duplicate FITID, multi-account, and encrypted-QFX rejection.
- Draft PR #238 contains exactly the twelve intended implementation, test, governing-document, and owner-checklist files from merged `main`.
- Initial Maven PR Tests run `30766866466` compiled production and passed 505 of 506 executed tests; the sole failure was the new UTF-16 rejection assertion because the generic NUL diagnostic ran before the more specific BOM diagnostic.
- Corrected head `9ed4787759896bf2491d26c5c2d0c37a96f4d54d` checks the UTF-16/UTF-32 BOM before the generic NUL scan and passed `mvn clean verify`, the repeated Maven suite, and JavaFX production-route compliance in run `30767054385`.
- Final governance-inclusive head `fbd360dae4e855b72bd1617b3e269d7a088e64ee` passed all three gates in run `30767225469`; the owner confirmed the desktop checklist on 2026-08-02, and PR #238 merged at `e64016eafa565b876199b35685e0df9eaace2dff`.

Next exact action:

- None; P15-S6-C1 is DONE.

### P15-S6-C2 — Configured-account matching and durable OFX/QFX review

Status: DONE through merged PR #239.

Scope:

- Add nondestructive durable fields for statement envelope/account/balance metadata, account-match facts, row currency, and correction facts, and preserve them through SCLX.
- Bind preview and commit to the exact source hash, active company, and selected active configured bank account; re-read and revalidate every scope value before mutation.
- Block cross-company, inactive, missing-posting-account, bank/account/type, and currency conflicts; require explicit confirmation for a suffix-only account match.
- Persist one atomic review batch, all normalized statement rows, durable issues, and one operation audit without creating `Txn` or `TxnSplit` rows.
- Scope stable-ID/fingerprint duplicate detection to company and configured account, retain row conflicts for review, make identical file/format/target reimport a no-op, and prove late rollback.

Validation status:

- All changed Java sources pass the Java 17 grammar parser locally; Maven is unavailable in this container.
- Focused H2 tests cover exact configured-account commit, complete metadata preservation, suffix confirmation, blocking mismatch, identical no-op, no ledger writes, operation audit, migration columns, and late rollback.
- Draft PR #239 contains exactly the nineteen intended implementation, migration, test, SCLX portability, and governing-document files from merged `main`.
- The first authoritative workflow on plan-inclusive head `80916aaee9840aed2a22abb256e99bb52f7c749a` compiled production and passed 508 of 510 executed tests. The two failures exposed compatibility drift in the legacy uppercase FITID projection and persisted duplicate matching.
- Corrected head `9c8d085b5fb056a66c62195e10e63aaf0159e076` restores canonical uppercase source and correction identities and canonicalizes legacy duplicate-context values before comparison.
- Maven PR Tests run `30769684204` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance on that corrected implementation head.
- Final governance-inclusive head `cf4b0b987be534316068953b6b534ab74b2052b3` passed the same three gates in run `30769879021`, and PR #239 merged at `066fad4163a8596b72bd4b155b966c5bdf31c66d`.

Next exact action:

- None; P15-S6-C2 is DONE.

### P15-S6-C3 — Mapped bank CSV profiles and durable review

Status: DONE through merged PR #240.

Scope:

- Persist explicitly saved, versioned mapping profiles in H2 under one company and configured bank account, with a 1,000-profile company cap and no financial rows, credentials, Java Preferences, or sidecars.
- Strictly validate profile format, delimiter, encoding, dates/locale, decimal/grouping policy, signed or debit/credit amount convention, currency/account identity, and field mappings before persistence.
- Parse bounded RFC 4180-style logical records, retain original rows beside normalized preview values, and reject malformed encoding/quoting, duplicate normalized headers, missing mappings, mixed identities, invalid dates/amounts, and resource-limit violations before mutation.
- Bind preview and commit to the exact source hash, company, configured account, profile portable identity, and canonical profile hash; re-read and reparse all scope before commit.
- Reuse the C2 atomic review transaction, duplicate policy, idempotent no-op, factual operation audit, rollback behavior, and no-ledger-write rule for mapped CSV.
- Treat mapping profiles as operational import preferences excluded from SCLX; whole-database transfer remains their portable backup authority.

Validation status:

- All changed Java sources pass the Java 17 grammar parser locally; Maven is unavailable in this container.
- Focused H2 tests cover signed and debit/credit profiles, original-row preview, durable review metadata, configured-account ownership, idempotency, malformed CSV rejection, stale/inactive profile rejection, no ledger writes, and late rollback.
- Draft PR #240 contains exactly the sixteen intended implementation, migration, test, and governing-document files from merged C2.
- The first plan-inclusive workflow on head `80c2336faa04ff54e2bc1f960737e0f4709d17a4` compiled production and passed 513 ordinary tests; only the two empty-Flyway-history recovery cases failed because V69 unconditionally created a table Hibernate had already materialized.
- Corrected head `ef21e6faef155cd21a1c3f0fe5cdb84971f247be` uses recovery-safe table/index creation and independently idempotent constraints.
- Maven PR Tests run `30770627629` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance on the corrected implementation head.
- Final governance-inclusive head `f5960836fe463ad0c979d0a1df9ff30f3a09f776` passed the same three gates in Maven PR Tests run `30770764755`, and PR #240 merged at `a9cff7b1a3f426a9a4cf56cbc69c48a20eb79a62`.

Next exact action:

- None; P15-S6-C3 is DONE.

### P15-S6-C4 — Production durable bank-review UI

Status: DONE through merged PR #241 and owner desktop acceptance.

Scope:

- Make Import Preview the sole production OFX/QFX and mapped bank CSV preview/commit route, using the exact C2/C3 service scopes rather than a second parser or writer.
- List active configured accounts and durable CSV profiles for the active company, show normalized rows and original CSV logical rows, require an actor and exact-scope confirmation, and expose suffix-only account confirmation explicitly.
- Reject company, account, file, profile, or profile-state drift after preview and require a new preview after any failed or rolled-back commit.
- Add a company-scoped read-only query authority for durable bank review rows and counts; wire Banking and Bank Transactions to it with correct company switching and restart behavior.
- Allow ledger drill-through only for statement rows explicitly matched to canonical transactions and retain the no-ledger-write rule for statement import.
- Remove `UiWorkspaceDataStore.bankTransactions` and the File-menu direct parser/session-staging route after every production consumer uses durable review.
- Add focused query isolation, production-route, composition, elimination, and owner desktop coverage in `doc/P15-S6-C4-bank-review-ui-user-testing.md`.

Validation status:

- All changed Java sources pass a Java 17 grammar parse locally; Maven is unavailable in this container.
- Focused query tests cover durable company isolation, persisted review summary counts, and matched-transaction projection.
- Source and composition tests cover canonical Import Preview service routing, exact-scope commit controls, durable Banking/Bank Transactions projections, and removal of the temporary store and direct File-menu import route.
- Draft PR #241 contains exactly the twenty intended production, test, governing-document, checklist, and obsolete-store-removal paths from merged C3, with no unrelated base drift.
- The first authoritative workflow on plan-inclusive head `dbf5679e7b711f5c10bc4723206283e452650b11` stopped at compilation because `ImportPreviewPanel` omitted the `java.util.List` import used by its new bank-row projection.
- The next workflow exposed one remote-publication omission: `WorkspaceServices.java` had not changed on the branch, leaving its constructor and accessors inconsistent with the already-published factory wiring. The exact locally reviewed composition file was then published without changing the C4 design.
- Corrected implementation head `a5a94e1ce98d4a9ffb486bb853ca524964ee775f` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30771668533`.
- Final governance-inclusive head `a2ecd3c2742ca11b64c379f1c3584c839d87176e` passed the same three gates in Maven PR Tests run `30771818131`.
- PR #241 merged at `c2efbe072edc2e20a5e88c08db6e09b049369897`; the owner confirmed every item in `doc/P15-S6-C4-bank-review-ui-user-testing.md` on 2026-08-02, recorded in PR comment `5161442812`.

Next exact action:

- None; P15-S6-C4 and P15-S6 are DONE.

## P15-S7 — OFX 2.x/QFX and normalized bank CSV export

Status: DONE through merged and owner-verified PR #245.

Purpose: export portable single-account bank-statement records without presenting OFX/QFX as a double-entry ledger export.

Planned deliverables:

- Export one explicitly selected configured bank account and date range from authoritative durable reviewed statement lines.
- Write standards-conformant OFX 2.x XML and a governed QFX profile with deterministic ordering, account/currency/statement metadata, transaction identifiers, dates, amounts, types, payee/name, memo, check/reference fields, supported correction metadata, and opening/closing balances when authoritative.
- Preserve imported FITID/source identity where valid. When a required export identifier is absent, derive a deterministic namespaced export ID and report that derivation without changing accounting authority.
- Export normalized UTF-8 CSV with a frozen round-trip schema and RFC 4180 quoting. Include source ID/FITID, transaction date, posted date, amount, debit/credit indicator, type, payee/name, memo, check/reference, currency, configured bank-account identity, batch/source provenance, duplicate/review state, and supported match identifiers.
- Make normalized CSV export directly re-importable through P15-S6 without losing governed statement facts.
- Keep canonical double-entry ledger export in SCLX/report exports; do not flatten the whole ledger into OFX/QFX.
- Write through a temporary file and atomic move, with counts, warnings, source scope, and SHA-256 hash.
- Add OFX/QFX schema/profile validation, CSV round-trip, deterministic export, correction, balance-availability, empty-range, and cross-company-isolation tests.
- Add explicit Export OFX 2.x, Export QFX, and Export Bank CSV actions to the existing Banking/Bank Transactions workflow; do not add an Import/Export Jobs destination.

Acceptance:

- Export/import round trips preserve every governed normalized bank-statement field.
- Export never includes another company or bank account.
- OFX/QFX output represents bank-statement activity only and is labeled accordingly.
- Missing optional source metadata produces disclosed warnings rather than fabricated values.

### P15-S7-C1 — Deterministic normalized bank CSV export

Status: DONE through merged PR #242.

Branch: `codex/P15-S7-C1-normalized-bank-csv-export`

Scope:

- Reconstruct only durable `bank_import_batch` and `bank_statement_line` facts for one selected active company-owned configured bank account and inclusive date range.
- Emit the frozen normalized CSV 1.0 header, portable batch/line/matched-transaction identities, deterministic ordering, RFC 4180 quoting, LF endings, ISO dates, and exact plain decimals without reading or flattening the canonical ledger.
- Preserve source, account, correction, review, exact/probable duplicate, and match facts; leave unavailable optional fields empty with explicit aggregate warnings rather than fabricating values.
- Reject empty ranges, cross-company or inactive account scope, an unconfirmed overwrite, a database-file target, directory/symlink targets, and invalid date ranges before replacing any output.
- Write UTF-8 without BOM through a forced same-directory temporary file and atomic move, then return row/byte counts, target scope, warnings, and SHA-256.
- Consolidate the existing SCLX temporary-file commit behavior behind one format-neutral atomic interchange writer so bank export does not introduce a parallel file-authority path.
- Add focused H2 company/account/date isolation, deterministic bytes, portable identity, quoting/newline, duplicate-state, empty-range, overwrite, and no-ledger-source coverage.
- Keep normalized CSV re-import, OFX/QFX serialization, and JavaFX export controls for later P15-S7 slices.

Validation status:

- Local Maven is unavailable in this source snapshot; authoritative compilation and H2 execution will run through the PR workflow after publication.
- The donor OFX writers were inspected for field coverage only. Their direct alternate-ledger source, generated/random identifiers, fabricated balances, and direct non-atomic file replacement are not ported.
- Draft PR #242 contains exactly the ten intended implementation, shared atomic-writer consolidation, test, and governing-document paths from merged S6, with no base drift.
- Published implementation/plan head `908ff761005382bd03e4f51d6f46a80b7f60bfea` passed the local Java 17 grammar review; GitHub Actions is authoritative for compilation and H2 execution.
- Plan-inclusive head `bf1cf48a110f1a7bd1518cc94aea9f166d4a4902` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30778043165`.
- Final governance-inclusive head `eeace4d8afd09474d61037f8f2b71762b2a69659` passed the same three gates in Maven PR Tests run `30778220375`; PR #242 merged at `5729705b6bf99d267f0995f1685527f5891b182c`.

Next exact action:

- None; P15-S7-C1 is DONE.

### P15-S7-C2 — Normalized bank CSV direct re-import and semantic round trip

Status: DONE through merged PR #243.

Branch: `codex/P15-S7-C2-normalized-bank-csv-round-trip`

Scope:

- Recognize only the exact frozen 29-column normalized bank CSV 1.0 header and strictly parse bounded UTF-8/RFC 4180 records without a user mapping profile.
- Validate version, source batch and line identities, single configured-account scope, currencies, dates, nonzero exact amounts, correction pairs, review/duplicate consistency, batch metadata consistency, and matched canonical transaction references before mutation.
- Extend durable review nondestructively to retain exact source batch/line external IDs and source PAYEEID, then make C1 export prefer those retained values so legacy and generated portable identities round-trip.
- Preserve multiple original source batches, statement metadata, row facts, review state, exact/probable duplicate state, and existing same-company matched-transaction links in one transaction; never create `Txn` or `TxnSplit` rows.
- Bind preview and commit to the exact file hash, active company, selected configured account, account-identity confirmation, and collision-free external identities; make identical file/target reimport a no-op.
- Add late-failure rollback, frozen-fixture compatibility, multi-batch semantic export/import/export, PAYEEID, match, duplicate, idempotency, and no-ledger-write coverage.
- Keep OFX/QFX serialization and JavaFX export controls for later P15-S7 slices.

Validation status:

- All twelve changed Java files pass an independent Java grammar parse.
- Local Maven/JDK are unavailable in this source snapshot; authoritative compilation, Flyway recovery, H2 integration, repeated-suite, and JavaFX checks will run through the PR workflow.
- Draft PR #243 contains exactly the seventeen intended implementation, recovery-safe migration, test, and governing-document paths, with no deletions or base drift.
- Published implementation/plan head `6e2a969e60383d8284e655eb0a4c74a70e9732a0` is seventeen commits ahead of exact merge base `5729705b6bf99d267f0995f1685527f5891b182c`.
- Plan-inclusive head `db3f9cfb1115e70130983d8b14414d471a2fae4f` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30781760154`.
- Final governance-inclusive head `0bce6fad68002349e0f0be2983970795111ee26a` passed the same three gates in Maven PR Tests run `30781932036`; PR #243 merged at `77584946bcb12005151db50be80d8809aae86c7d`.

Next exact action:

- None; P15-S7-C2 is DONE.

### P15-S7-C3 — Deterministic OFX 2.x and governed QFX statement export

Status: DONE through merged PR #244.

Branch: `codex/P15-S7-C3-ofx-qfx-statement-export`

Scope:

- Reuse the C1 company/account/date-range snapshot and shared atomic writer; never query or flatten canonical ledger transactions as statement activity.
- Emit deterministic UTF-8 OFX 2.x XML and governed QFX 2.x header/XML envelopes with one bank statement, selected-scope dates, account/currency metadata, ordered transactions, exact amounts, names/memos, check/reference fields, and supported correction metadata.
- Preserve valid source FITIDs; derive a deterministic collision-free FITID from the statement-line portable identity only when a required FITID is missing or duplicated, and report every derivation without mutating durable review facts.
- Include the latest unambiguous imported ledger/available balance and its authoritative statement date; omit unavailable or conflicting balances with explicit warnings rather than guessing.
- Retain C1 company/account/date isolation, empty-range rejection, database/symlink/overwrite protection, counts, warnings, bytes, SHA-256, and atomic replacement.
- Prove both output profiles parse through the strict production `BankStatementParser`, are deterministic on overwrite, preserve source FITIDs and balances, disclose derived FITIDs, and never emit CSV-only warnings.
- Keep JavaFX export controls for the final P15-S7 slice.

Validation status:

- All six changed Java files pass an independent Java grammar parse.
- Local Maven/JDK are unavailable in this source snapshot; authoritative compilation, H2 integration, repeated-suite, and JavaFX checks will run through the PR workflow.
- Draft PR #244 contains exactly the nine intended implementation, parser-round-trip test, and governing-document paths, with no deletions or base drift.
- Published implementation/plan head `e273767ce384d198525221af5a114046dca03eaa` is nine commits ahead of exact merge base `77584946bcb12005151db50be80d8809aae86c7d`.
- The first plan-inclusive workflow in run `30782384967` stopped at compilation because the newly package-visible `Snapshot` record retained a private compact constructor.
- Corrected head `47a34cfc7fa88b276d584289537c1c7b39183539` aligns the constructor to package scope and passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30782431431`.
- Final governance-inclusive head `796208dbb0ed1e7efc17a8fabc3220c8f152c835` passed the same three gates in Maven PR Tests run `30782601811`; PR #244 merged at `121d790a7139bcc8db4f9955f5c19b7df7801b27`.

Next exact action:

- None; P15-S7-C3 is DONE.

### P15-S7-C4 — Production bank-statement export controls

Status: DONE through merged PR #245 and owner desktop acceptance.

Branch: `codex/P15-S7-C4-bank-statement-export-ui`

Scope:

- Replace the Bank Transactions selected-row compatibility exporter with the governed C1/C3 durable selected-company/configured-account/date-range services; do not retain or add a second serializer.
- List only active configured accounts for the active company, default the inclusive range from the active accounting month, and expose separately labeled **Export Bank CSV…**, **Export OFX 2.x…**, and **Export QFX…** actions.
- Capture the exact active company, configured-account ID, dates, format, destination, and overwrite decision before background work; revalidate company/account ownership inside the canonical service.
- Choose a format-specific extension and filename, require explicit confirmation before replacing an existing file, run serialization/atomic commit away from JavaFX, and report destination, rows, bytes, warnings, SHA-256, and path-coded messages.
- Keep Banking as the configuration/import hub with an explicit route to **Review / Export Statements…**; keep Bank Transactions as the sole production statement-export surface and add no File-menu or Import/Export Jobs route.
- Preserve the no-ledger-query/no-ledger-write boundary and add production-composition/source coverage plus `doc/P15-S7-C4-bank-statement-export-ui-user-testing.md`.

Validation status:

- All eight changed Java files pass an independent Java 17 grammar parse.
- Source coverage requires the three explicit formats, configured-account/date scope, background `Task`, overwrite confirmation, exact result facts, workspace service composition, and removal of the old `ImportExportOrchestrationService`/`BankTransactionRecord` selected-row route.
- Local Maven/JDK are unavailable in this source snapshot; authoritative compilation, full H2 suite, repeated suite, and JavaFX production-route compliance will run through the PR workflow.
- Draft PR #245 contains exactly the twelve intended production, test, governing-document, and owner-checklist paths, with no deletions or base drift from exact merge base `121d790a7139bcc8db4f9955f5c19b7df7801b27`.
- Published implementation/documentation head `d4dd35c18db768929ad3dfe6cc4443bc2cacc4be` is twelve fast-forward commits ahead of that base.
- Plan-inclusive head `cb44d61dad697f0e02cab951b9a24517a9802209` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30783262811`.
- Final governance-inclusive head `0dfaa09a45d33835d33dada7ea7f1d74ec28772a` passed the same three gates in Maven PR Tests run `30783469434`.
- PR #245 merged at `83999de5afa80decf63068862fa897bbee3d8ce1`; the owner confirmed the complete desktop checklist on 2026-08-03.

Next exact action:

- None; P15-S7-C4 and P15-S7 are DONE.

## P15-S8 — Integrated JavaFX workflow and end-to-end verification

Status: DONE through merged PR #249 and owner acceptance.

Planned deliverables:

- Keep SCLX preview/validation/mapping/commit in Import Preview and expose Export Active Company to SCLX from the File menu.
- Keep COA JSON actions in Chart of Accounts.
- Keep OFX/QFX/CSV import, review, and export in Banking, Bank Transactions, and Import Preview as defined by the operation matrix.
- Keep whole-database backup/import/validate/recovery under database administration and existing recovery composition.
- Run long operations asynchronously with bounded progress and cancellation before commit; write factual audit history only for completed durable operations.
- Apply current scrolling, split-pane, tooltip, formatting, dirty-state, company-owned state, and guarded company/database switching rules.
- Add all-format golden-file, unsupported-version, malformed/oversized, conflict, rollback, semantic round-trip, idempotency, multi-company, closed-period, reconciliation-protection, production-route, and laptop-width desktop coverage.
- Run full `mvn clean verify` through the PR workflow and complete owner desktop acceptance.

Acceptance:

- Each operation is labeled by exact scope and target; “database,” “active company,” “Chart of Accounts,” and “bank statement” are never used interchangeably.
- No generic Import/Export Jobs destination or generic durable job log exists.
- Every enabled action has a real service-backed operation and a preview/confirmation path appropriate to its risk.

### P15-S8-C1 — Production normalized bank CSV import route

Status: DONE through merged PR #246 and owner desktop acceptance.

Branch: `codex/P15-S8-C1-normalized-bank-csv-import-ui`

Scope:

- Compose the existing strict `NormalizedBankCsvReviewService` through `UiServiceRegistry`, `WorkspaceServices`, and `PanelFactory`; do not add another parser or writer.
- Add an explicit **Preview Normalized Bank CSV…** action to Import Preview that requires the active company and one active configured bank account but no mapped-CSV profile.
- Capture one service instance plus the exact file, company, and configured-account scope before background preview; display normalized rows, source-batch/row counts, account-match state, and path-coded messages without changing H2.
- Retain the exact preview and service for explicit confirmation and background atomic commit. Revalidate source hash, company/account ownership, and suffix-only account confirmation through the canonical service.
- Restore durable source batches, statement rows, issues, review/duplicate state, portable identities, PAYEEID, and valid same-company transaction matches without creating `Txn` or `TxnSplit` rows.
- Add production-composition/source coverage, focused preview-row coverage, governing-document updates, and `doc/P15-S8-C1-normalized-bank-csv-import-ui-user-testing.md`.
- Keep all-format stress, unsupported-version, malformed/oversized, cancellation-before-commit, multi-company, round-trip, and final laptop-width verification for later P15-S8 slices.

Validation status:

- All nine changed Java files pass an independent Java 17 grammar parse; Maven is unavailable in this source snapshot.
- Draft PR #246 contains exactly the fifteen intended production, test, governing-document, and owner-checklist paths, with no deletions or base drift from exact merge base `83999de5afa80decf63068862fa897bbee3d8ce1`.
- Plan-inclusive head `0796a84ed80c26f81c12c456f6599bac4a778d3c` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30829022583`.
- Final governance-inclusive head `5d5355e86e42e784c4d849aed9660867614642b7` passed the same three gates in Maven PR Tests run `30829383420`.
- The owner confirmed the complete desktop checklist on 2026-08-03, and PR #246 merged at `30a71adf9545e31b2265c069379d9708cd92949b`.

Next exact action:

- None; P15-S8-C1 is DONE.

### P15-S8-C2 — All-format bank-import contract matrix

Status: DONE through merged PR #247.

Branch: `codex/P15-S8-C2-bank-import-contract-matrix`

Scope:

- Drive OFX 2.x XML, QFX 2.x header/XML, QFX 1.x SGML, signed mapped CSV, debit/credit mapped CSV, and normalized CSV through their canonical preview/commit services against one configured company-owned account.
- Prove every supported profile commits durable review facts, creates its factual operation audit, performs no canonical ledger write, and makes an identical second import a no-op.
- Exercise malformed, unsupported-version/message-set, multi-account, XML external-entity/expansion, encrypted/compressed QFX, malformed/ambiguous mapped CSV, and invalid normalized-header rejection through the production service boundary.
- Assert that every rejected input leaves batches, statement lines, issues, audit history, and canonical transactions unchanged while explicitly saved mapping profiles remain configuration facts.
- Prove a configured account owned by another company cannot be selected through the active-company service scope.
- Reuse governed fictional fixtures and existing parsers, mapping profiles, review services, and H2 authorities; add no parser, staging store, durable job log, UI route, migration, or ledger behavior.
- Keep cancellation/progress controls, closed-period/reconciliation noninterference, all-family export/import semantic checks, and the final laptop-width desktop sweep for later P15-S8 slices.

Validation status:

- Local Java 17 is available but Maven is unavailable in this source snapshot; authoritative compilation, Flyway/H2 execution, repeated-suite, and JavaFX route compliance will run through the PR workflow.
- The new integration source passes an independent Java 17 grammar parse.
- Draft PR #247 contains exactly the five intended test and governing-document paths on head `29d691e8dffeca9d382256f102db1bc6c3956142`, with no deletions or base drift.
- Plan-bound head `bd1b04de5dc79e2fccaf71775abe1a89051cba17` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30831011571`.
- Final governance-inclusive head `148c2a54abe539a09e99fa43e44f4caa65f4b1e0` passed the same three gates in Maven PR Tests run `30831368955`; PR #247 merged at `dd57ce5013c48f858c2d3d1444268f696413ed8b`.

Next exact action:

- None; P15-S8-C2 is DONE.

### P15-S8-C3 — Bank-statement round trip and ledger-protection noninterference

Status: DONE through merged PR #248.

Branch: `codex/P15-S8-C3-bank-import-protection-round-trip`

Scope:

- Import a governed OFX statement into durable review while the company already contains a closed statement period and a completed-reconciliation-protected balanced canonical bank transaction.
- Export that durable review as deterministic OFX 2.x and QFX 2.x, prove both strict parser projections are semantically equivalent, and import each into a second company-owned configured account with the same external bank identity.
- Prove the first target import remains reviewable, the equivalent second-format import is durably classified as duplicate review evidence, and both create no canonical ledger activity in the target company.
- Assert that the source canonical transaction, its two balanced splits, completed-reconciliation protection, closed range, and close event remain unchanged throughout raw import/export/re-import.
- Add no production source, schema, parser, UI, ledger, close, or reconciliation behavior; reuse the canonical services and the C2 matrix fixture.
- Keep cancellation/progress controls and the final laptop-width desktop sweep for later P15-S8 slices.

Validation status:

- Local Java 17 is available but Maven is unavailable; Java grammar review and the GitHub Maven/Flyway/H2 workflow are required.
- The changed integration source passes an independent Java 17 grammar parse.
- Draft PR #248 contains exactly the five intended test/governance paths on head `78ddee19273bd919224c8479f4beeb1b2ae8297d`, with no deletions or base drift.
- Plan-bound head `05726dc16f19f271979b7fdb2334a2b52cb62577` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30831952795`.
- Final governance-inclusive head `e608cfbc48b3ab8e885a8207e05f9cde96b16148` passed the same three gates in Maven PR Tests run `30832272897`; PR #248 merged at `f8b9ae842d1636dedd1df2c7386c228489d2c0ec`.

Next exact action:

- None; P15-S8-C3 is DONE.

### P15-S8-C4 — Interchange progress, pre-commit cancellation, and laptop-width closure

Status: DONE through merged PR #249 and owner acceptance.

Branch: `codex/P15-S8-C4-interchange-progress-laptop-closure`

Scope:

- Give Import Preview one transient JavaFX task owner with a visible bounded `InterchangeProgress` projection for every COA CSV, SCLX, OFX/QFX, mapped-bank-CSV, and normalized-bank-CSV preview.
- Allow **Cancel Preview** only while a non-mutating preview is running; suppress a cancelled task's result and retain the guarantee that no H2 fact or audit event was written.
- Display durable COA/profile/SCLX/bank commit progress through the same control while setting `commitStarted=true`, which disables cancellation for the complete canonical transaction boundary.
- Prevent simultaneous import-preview operations and freeze company, account, profile, identity-confirmation, and actor controls while one exact-scope task is active.
- Put the dense Import Preview and Bank Transactions export control rows in independently scrollable viewports, and show Bank Transactions export busy state without widening either workspace past laptop bounds.
- Add focused controller behavior and source/layout guardrails plus `doc/P15-S8-C4-interchange-progress-ui-user-testing.md`.
- Add no parser, schema, migration, persistence table, ledger write, sidecar state, generic durable job log, or second interchange authority.

Validation status:

- Local Maven/JDK compilation is unavailable in this source snapshot; all seven changed Java sources pass an independent Java 17 grammar parse.
- Focused tests cover preview cancellation before execution, suppressed success delivery, commit-time cancellation lockout, bounded completion, production wiring for all five preview families, and laptop-safe import/export control viewports.
- Draft PR #249 initially contained exactly the eleven intended production, test, contract, plan, and checklist paths on head `d6548d74344e6b373ce18587899c58fe5450ea67`, with no deletions or base drift from merge `f8b9ae842d1636dedd1df2c7386c228489d2c0ec`.
- Maven PR Tests run `30842770143` compiled the slice and executed H2 but found two stale legacy source guards that still required the removed `UiAsync.run(...)` spelling; the two guards now require the governed preview/commit wrappers for the same OFX/QFX and SCLX routes.
- Corrected implementation head `5ce4b8a3d43f01279e3d4971a38c7a965c72a243` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30843032117`.
- Final governance-inclusive head `ce987459c740c45399eb3debe6f2abd61f6e38d5` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30843385164`.
- PR #249 merged to `main` at `76bb90650efa2ac4a0a39bfcd4854642659ad171` on 2026-08-03, and the owner authorized continuation after the desktop gate.

Next exact action:

- None; P15-S8-C4 and P15-S8 are DONE.

### P15-C1 — Final plan-ledger reconciliation

Status: DONE through documentation-only PR #250.

Branch: `codex/P15-C1-finalize-plan-ledger`

Purpose:

- Record the owner-accepted P15-S8-C4 desktop closure after product PR #249 merged with the plan still marked `VERIFYING`.
- Mark P15-S8 product delivery complete, reconcile the phase index and active front matter, and close P15 without changing product code.
- Leave later feature work unauthorized until a new phase is deliberately added to this plan.

Validation status:

- PR #249 final head `ce987459c740c45399eb3debe6f2abd61f6e38d5` passed all three repository gates in Maven PR Tests run `30843385164` before merging at `76bb90650efa2ac4a0a39bfcd4854642659ad171`.
- Draft PR #250 was created from exact merge base `76bb90650efa2ac4a0a39bfcd4854642659ad171`; activation head `f42432fd87d4f1ec39e68cd0cdb3ae5503d7e211` changes only `doc/PLAN.md`, with no base drift.
- Plan-bound review head `41cedb6f79932a57bcef8c213a8a46bca900235f` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30846540866`.
- Governance-inclusive head `8c0f4a10cd5b1be131c6b0250276688915aec53c` passed the same three gates in Maven PR Tests run `30846894412`.
- Final post-merge-state head `db8eb62790950e2b294be6f392af3979e13fb305` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30847200012`.
- PR #250 merged to `main` at `1740de45b043553cca4e7bd3ba364019ec8fb99f` on 2026-08-03.

Next exact action:

- None; P15-C1 and P15 are DONE.


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

# P16 — Interface-to-authority completion and integrity corrections

**Selector:** `PHASE=P16`  
**Status:** DONE through P16-S17, merged PR #269, owner acceptance, and documentation-only P16-C1 / PR #270; corrective P16-C3 IN_PROGRESS after P16-C2 / PR #271
**Depends on:** P03 through P15 except eliminated P07

## Purpose

Correct production controls whose visible promise is stronger than their service, transaction, persistence, or query behavior. P16 begins with the highest-risk partial-write and authority defects, then completes the workflows implied by the panels, and ends with truthful commands, preferences, and reports.

P16 must extend the existing application. It must not create a second ledger, a second bank-import path, another database-session authority, a generic posting queue, or generic durable job tracking.

## Audit basis and slice ownership

The interface-to-persistence review was performed on `main` at `1740de45b043553cca4e7bd3ba364019ec8fb99f`, following `MainApp` through production navigation, panel composition, application services, repositories, migrations, and tests.

| Priority | Finding | Corrective owner |
|---|---|---|
| 1 | Monthly depreciation can commit a ledger transaction before the depreciation run fails | P16-S1 |
| 2 | COA CSV accepted-row commit permits partial success | P16-S2 |
| 3 | Finalized reconciliations remain mutable and some match operations are incompletely scoped | P16-S3 |
| 4 | Reconciliation exposes a second weak CSV/OFX/QIF import authority | P16-S4 |
| 5 | Preferences can change the remembered database path without changing the connected database | P16-S5 |
| 6 | Saved budget drafts can become hidden or orphaned and budget periods assume calendar years | P16-S6 |
| 7 | Audit History reads the obsolete approval audit table instead of current factual audit events | P16-S7 |
| 8 | Reviewed statement rows have no explicit acceptance path into the canonical ledger | P16-S8 |
| 9 | Financially relevant inventory movements can diverge from the ledger | P16-S9 |
| 10 | Journal displays every bank transaction as Uncleared | P16-S10 |
| 11 | Several saved preferences have no production consumer | P16-S11 |
| 12 | Global commands and Help advertise operations that panels do not implement | P16-S12 |
| 13 | Some report names overstate first-pass query semantics | P16-S13 |
| Deferred completion | Asset disposal accounting and domain-specific asset/inventory reports are absent | P16-S14 and P16-S15 |
| Deferred completion | User Admin cannot revoke assignments or maintain roles | P16-S16 |

## Established boundaries

- A bank-statement import creates durable review facts only. P16-S8 adds an explicit user-confirmed acceptance action; it must not auto-post imported rows.
- An inventory movement creates a ledger transaction only under a documented financially relevant movement policy and explicit user confirmation. SCLX restoration of historical movement facts must not synthesize new ledger activity.
- A reconciliation explanation is not an accounting correction. The UI must distinguish a factual explanation from a canonical adjusting transaction.
- A finalized reconciliation is immutable. Correction requires a separately governed reopen or successor-session action with factual audit history.
- Database selection remains owned by `DatabaseSessionController`; a preference value may not switch database authority.
- Current `AuditEvent`/`audit_event` remains the factual accounting-operation history. Legacy approval records are compatibility data, not current audit authority.
- Fixed-asset interchange remains governed by P15. Asset disposal, impairment, and specialized reports are product workflows, not interchange repairs.
- Authentication remains outside P16 unless separately authorized. P16-S16 maintains durable users, roles, and assignments without pretending to enforce login identity.

## Required reading

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/audit/audit-history.md`
- `doc/accounting/budget-model.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/data-exchange/bank-statement-interchange.md`
- `doc/data-exchange/chart-of-accounts-json.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/reporting/report-library.md`
- `doc/administration/company-lifecycle.md`
- `doc/administration/user-role-maintenance.md`
- `doc/workflow/development-workflow.md`

## Required inspection

- Production composition: `MainApp`, `ProductionWorkspaceWindow`, `PanelFactory`, `WorkspaceServices`, `UiServiceRegistry`, `AppPanel`, `AppPanelId`, `GlobalShortcuts`, and `HelpPanel`.
- Integrity paths: `FixedAssetService`, `TransactionEntryService`, `ImportPreviewService`, `AccountAdminService`, `BankReconciliationWorkspaceService`, `DatabaseSessionController`, `UiSessionState`, and their repositories/migrations.
- Incomplete panels: `ReconciliationRunsPanel`, `SettingsPanel`, `BudgetEditorPanel`, `BudgetVsActualPanel`, `ApprovalAuditPanel`, `ImportPreviewPanel`, `InventoryPanel`, `JournalWorkspacePanel`, `PeriodCloseRunsPanel`, `UserAdminPanel`, and report-library panels.
- Query/report projections: `TransactionView`, `AuditHistoryService`, `BudgetPlanService`, `BankReviewQueryService`, and `WorkbookSemanticReportService`.
- Existing H2 integration, migration, production-route, JavaFX behavior/layout, rollback, company-isolation, closed-period, and reconciliation-protection tests.

## Execution order

P16-S0 is documentation-only. After it merges, execute P16-S1 through P16-S17 in order unless a slice documents that it is safely independent and the plan is deliberately amended. Do not combine unrelated domain repairs merely because they were discovered in the same audit.

Every implementation slice must:

1. Reproduce the defect with a focused service or production-route test before changing behavior.
2. Put validation and writes inside one authoritative service boundary.
3. Preserve company ownership, closed-period protection, reconciliation protection, and factual audit history.
4. Add late-failure rollback and restart/isolation coverage where durable state changes.
5. Update the owning governing document, interface operation matrix, persistence inventory, and this ledger.
6. Run focused tests, `mvn clean verify`, the deliberately repeated suite, and JavaFX production-route compliance.
7. Add an owner desktop checklist for visible behavior and leave the slice `VERIFYING` until that checklist and merge are complete.

## P16-S0 — Govern the post-P15 completion program

Status: DONE through merged PR #251.

Branch: `codex/P16-S0-interface-completion-plan`

Pull request: #251  
Activation head: `84de548a68c818f6e94469b5b7100947a2a31fc0`

Scope:

- Record every material audit finding and the code authority implicated by it.
- Establish one mergeable vertical slice per coherent correction.
- Order partial-write, finalization, company/database isolation, and misleading-authority defects before feature-completion work.
- Preserve deliberate P15 boundaries and distinguish deferred functionality from regressions.
- Change only `doc/PLAN.md`; do not alter production behavior in this slice.

Acceptance:

- Every audit finding maps to one implementation slice and explicit completion criteria.
- P16-S1 is the only first unblocked product slice after P16-S0 merges.
- The plan does not mark any audited behavior complete before code, CI, desktop validation, and merge prove it.

Validation status:

- The branch was created from exact current `main` merge `1740de45b043553cca4e7bd3ba364019ec8fb99f`.
- Activation head `84de548a68c818f6e94469b5b7100947a2a31fc0` changes only `doc/PLAN.md`: 473 additions, 11 deletions, no base drift, and no product source, migration, test, or fixture changes.
- Plan-bound head `43040253e7a43eff51c899ca96b6dddb5d1c7e71` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30975002920`.
- Final governance-inclusive head `2a3b026f495413d5aab850add6e52fe55e509c5f` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `30975228164`.
- PR #251 merged to `main` at `88bae9870255630c53e057d2bb201e9b41389479` on 2026-08-05.
- Post-merge `main` run `31065210112` passed the same repository gates.

Next exact action:

- None; P16-S0 is DONE and P16-S1 is active.

## P16-S1 — Atomic monthly depreciation

Status: DONE through merged PR #252 and owner desktop acceptance.

Branch: `codex/P16-S1-atomic-monthly-depreciation`

Pull request: #252  
Starting base: `88bae9870255630c53e057d2bb201e9b41389479`  
Validated implementation head: `96175d54fd5c739ca29cf8bb35e49ebf89938d70`

Purpose: prevent a failed depreciation-run write from leaving an orphan canonical transaction.

Implementation steps:

1. Add a regression fixture that reproduces the late failure after transaction creation, including the same-asset/same-date unique constraint.
2. Introduce or reuse a caller-owned transaction seam so canonical transaction creation and `DepreciationRun` persistence use one JPA transaction and one `EntityManager`.
3. Validate company, asset status, account/fund ownership, amount, run date, closed period, completed-reconciliation protection, and prior run before the first mutation; retain the database unique constraint as the final concurrency guard.
4. Persist the balanced depreciation transaction, completed run, portable identity, and factual audit event inside the same transaction.
5. Return one result containing both durable identities only after commit; on failure return neither and refresh the panel without inventing success state.
6. Remove any two-step service composition that permits the transaction to commit independently.

Acceptance and tests:

- Duplicate run, injected late failure, audit failure, and constraint race leave transaction, splits, run, identity, and audit counts unchanged.
- Successful execution creates exactly one balanced transaction and one linked run; identical retry is rejected or reported as an idempotent no-op according to the governed policy.
- Multi-company, closed-period, and finalized-reconciliation protections cannot be bypassed.

Validation status:

- The branch remains based on exact P16-S0 merge `88bae9870255630c53e057d2bb201e9b41389479` with no base drift.
- Draft PR #252 implements one caller-owned JPA transaction for the canonical transaction, balanced splits, linked depreciation run, portable identities, and factual audit event, with authoritative refresh after commit or rollback.
- Deterministic protection fixtures use governed values: `CALCULATED` for the period-close range and `OFX` for the legacy reconciliation-run compatibility row. Applied migration V7 remains unchanged.
- Exact implementation head `96175d54fd5c739ca29cf8bb35e49ebf89938d70` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `31147294296`.
- The branch comparison contains only the 13 intended P16-S1 source, test, and governing-document files. No temporary workflow or migration change remains.
- Automated acceptance completed on implementation head `96175d54fd5c739ca29cf8bb35e49ebf89938d70` in Maven PR Tests run `31147294296`.
- Final plan-inclusive head `501374df0318e4008291c53c2a00e2d7e8c857a3` passed the complete Maven PR Tests gate in run `31147729410`.
- The owner completed and accepted `doc/P16-S1-atomic-monthly-depreciation-user-testing.md` and explicitly authorized merge.
- PR #252 merged to `main` at `a88becddf7ede7fcf3d986e7d8861351ce5438d5` on 2026-08-07.

Next exact action:

- None; P16-S1 is DONE and P16-S2 is active.

## P16-S2 — Atomic COA CSV accepted-row commit

Status: DONE through PR #253.

Branch: `codex/P16-S2-atomic-coa-csv-commit`

Pull request: #253  
Starting base: `a88becddf7ede7fcf3d986e7d8861351ce5438d5`  
Validated implementation head: `e495865b91a4979c1571f6ab61922ea43fedd839`

Purpose: make **Commit Accepted COA Rows** all-or-nothing while leaving P15 Chart-of-Accounts JSON interchange unchanged.

Implementation steps:

1. Freeze the preview by source hash, active company, target chart, accepted rows, mappings, and confirmation state.
2. Add a batch/caller-owned transaction boundary in `AccountAdminService` or a focused COA CSV commit service; do not call independently committing `upsert()` once per row.
3. Revalidate every row before mutation, including duplicate codes, chart/company ownership, parent references/order, cycles, type/normal-balance rules, history-sensitive changes, and inactive parents.
4. Order parent-before-child writes, then write all accounts, identities, and one factual operation audit in a single transaction.
5. Replace catch-and-continue behavior with one rollback result containing the failing row/path and zero committed rows.
6. Update UI counts so created/updated/skipped totals describe a committed batch, never attempted partial work.

Acceptance and tests:

- A failure on any row leaves all account fields, hierarchy, identities, and audit history unchanged.
- Identical re-preview/recommit is idempotent; source or target drift requires a new preview.
- Restart and company-isolation tests prove no partial chart is visible.

Validation status:

- Draft PR #253 is based on exact P16-S1 merge `a88becddf7ede7fcf3d986e7d8861351ce5438d5`.
- `CoaCsvImportService` owns the frozen CSV preview and one caller-owned JPA commit boundary; the production JavaFX route no longer loops accepted rows through independently committing `AccountAdminService.upsert()` calls.
- The batch freezes source SHA-256, company, target chart, target fingerprint, accepted/rejected rows, validation messages, and confirmation state; commit rejects source, company, chart, or target-state drift and requires a new preview.
- All account writes, `COA_CSV` external identities, and one factual operation `AuditEvent` commit together or roll back together. Late failure reports zero committed created/updated/skipped counts.
- Identical re-preview/recommit is idempotent and does not duplicate identities or operation audit history. P15 Chart-of-Accounts JSON import/export remains unchanged.
- Public single-account `AccountAdminService.upsert(...)` now validates code/name/type/normal-balance before any persistence access; caller-owned batch validation remains independently enforced.
- Exact implementation head `e495865b91a4979c1571f6ab61922ea43fedd839` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `31192123755`.
- Automated acceptance completed on implementation head `e495865b91a4979c1571f6ab61922ea43fedd839` in Maven PR Tests run `31192123755`.
- The owner completed and accepted `doc/P16-S2-atomic-coa-csv-user-testing.md`, and PR #253 merged to `main` at `100644b84c6bcb799446374aa928e953085255dd` on 2026-08-07.

Next exact action:

- None; P16-S2 is DONE and P16-S3 is active.

## P16-S3 — Reconciliation finalization and mutation integrity

Status: DONE through merged PR #254 and owner desktop acceptance.

Branch: `codex/P16-S3-reconciliation-mutation-integrity`

Pull request: #254, merged to `main` at `8f94ca2ef3581e37b6918b593c0f30176c01776f` on 2026-08-07
Starting base: `100644b84c6bcb799446374aa928e953085255dd`
Validated implementation head: `2a7df0c7aa085c602928e759a1b4ff01d41a0851`

Purpose: make finalized reconciliation sessions immutable and make all match/clear/resolution operations exact-scope and symmetric.

Implementation steps:

1. Centralize a session mutation guard in `BankReconciliationWorkspaceService`; every import, match, unmatch, clear, resolve, save, and finalize entry point must call it.
2. Reject mutation of `FINALIZED` sessions and prevent Save from downgrading a finalized status. Define an audited reopen or successor-session command before any later edit is allowed.
3. Validate that session, configured account, statement rows, transactions, and splits all belong to the same active company and bank account before mutation.
4. Make unmatch clear both sides of the statement/split relationship in one transaction and reject already-broken pairs instead of silently worsening them.
5. Separate **Record Difference Explanation** from **Create Adjustment Transaction**. The first stores factual explanation only; the second, if enabled, must preview and create a balanced canonical transaction through `TransactionEntryService` and then match it explicitly.
6. Render finalized sessions read-only and disable mutation controls with an explanatory tooltip.

Acceptance and tests:

- Every mutation API rejects finalized sessions and cross-company/account identifiers without changing data.
- Match/unmatch/clear operations are atomic and relationship-symmetric.
- Finalize is idempotent, Save cannot reopen/downgrade, and reopening or successor creation is factual and audited.
- UI tests prove finalized controls are disabled and explanations are not presented as accounting corrections.

Validation status:

- PR #254 was based on exact P16-S2 merge `100644b84c6bcb799446374aa928e953085255dd` and merged to `main` at `8f94ca2ef3581e37b6918b593c0f30176c01776f` on 2026-08-07.
- `BankReconciliationWorkspaceService` guards all live mutation paths, enforces exact company/account/session ownership, maintains symmetric match/unmatch relationships, keeps finalized sessions read-only, treats repeated finalization as idempotent, and creates an audited successor rather than editing finalized history.
- `ReconciliationRunsPanel` renders finalized sessions read-only, exposes explicit successor creation, and refreshes from the authoritative persisted snapshot after service results.
- Reconciliation statement import normalizes a caller-supplied temporary physical pathname to its logical leaf name at the reconciliation import boundary before `BankImportBatch` persistence. `BankImportBatch` itself remains a faithful persistence model so historical SCLX restoration is not silently rewritten. The `source_name` schema constraint is unchanged.
- `BankReconciliationMutationIntegrityTest` covers finalized-session immutability, symmetric unmatch, company/account scope rejection, factual difference explanations, successor audit/history, and the long-temporary-path provenance regression through real H2 persistence.
- Governing reconciliation, interface-operation, persistence-authority, and owner desktop acceptance documentation is updated in this slice.
- Exact implementation head `2a7df0c7aa085c602928e759a1b4ff01d41a0851` passed Maven PR Tests run `31225841857`, including clean headless verification, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner completed and accepted `doc/P16-S3-reconciliation-mutation-integrity-user-testing.md` on 2026-08-07.
- PR #254 merged to `main` at `8f94ca2ef3581e37b6918b593c0f30176c01776f`.

Next exact action:

- None; P16-S3 is DONE and P16-S4 is READY.

## P16-S4 — One governed bank-import authority

Status: DONE through merged PR #256 and owner acceptance.

Branch: `codex/P16-S4-governed-bank-import-authority`
Starting base: `ba3a6ccc4bb75fd61baf899afd5edde120c5fc5e`
Pull request: #256, merged to `main` at `4eef190adb8380d90356a94f24ef8d7f29e46f3a` on 2026-08-07
Validated implementation head: `2edcb313efa09a4032c5483a22ac4c991347924f`

Purpose: remove the weaker direct CSV/OFX/QIF import path from Reconciliation and retain Import Preview as the sole production statement-import authority.

Implementation steps:

1. Remove panel-local CSV splitting, OFX regular-expression extraction, QIF record parsing, and direct statement-row creation from `ReconciliationRunsPanel` and `BankReconciliationWorkspaceService`.
2. Route supported OFX 2.x, QFX, mapped CSV, and normalized CSV actions to the canonical Import Preview services with exact active-company/configured-account scope.
3. Remove QIF as an enabled production action unless a later governed QIF contract, strict parser, fixtures, limits, preview, and atomic durable-review service are separately authorized.
4. Keep file reading, parsing, and preview off the JavaFX thread using the existing transient interchange operation controller.
5. After successful durable import, refresh the selected reconciliation session from the canonical review query rather than copying transient rows.
6. Add source guards that prohibit parser logic and direct import persistence in Reconciliation.

Acceptance and tests:

- Exactly one production service path can create imported bank-review facts.
- Malformed/security/account-mismatch/duplicate behavior is identical whether import starts from Banking, Import Preview, or a Reconciliation navigation link.
- No QIF action implies support that the governed parser does not provide.

Execution state:

- Reconciliation file-import controls now route through one exact-account navigation command into Import Preview; manual statement entry remains a distinct reconciliation fact operation.
- `BankReconciliationWorkspaceService` no longer contains CSV/OFX/QIF parsers or imported-file persistence. Manual entry is delegated to a focused caller-owned transaction service so external file import has one production authority.
- Import Preview consumes the reconciliation context, locks the exact configured account, continues to use the existing off-FX-thread transient operation controller, and returns only after canonical durable-review commit so Reconciliation reloads H2 facts rather than transient rows.
- QIF remains outside the governed production formats.
- Source guards, governing documents, and `doc/P16-S4-governed-bank-import-authority-user-testing.md` are part of this slice.
- Exact final PR head `2edcb313efa09a4032c5483a22ac4c991347924f` passed Maven PR Tests run `31238920536`, including clean headless verification, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner accepted `doc/P16-S4-governed-bank-import-authority-user-testing.md`, and PR #256 merged to `main` at `4eef190adb8380d90356a94f24ef8d7f29e46f3a` on 2026-08-07.

Next exact action:

- None; P16-S4 is DONE and P16-S5 is active.

## P16-S5 — Connected-database session authority

Status: DONE through merged PR #257 and owner desktop acceptance.

Starting base: `4eef190adb8380d90356a94f24ef8d7f29e46f3a`
Pull request: #257
Validated implementation head: `6b226c8955e5a462e44f6938e59c9bd3a9d0cdeb`
Final tested PR head: `53d80e30c1338534f09935227eb3d9bf4f931e14`
Merge commit: `97fc2e224854c221cc15657e8b9ad7532a9fee23`

Purpose: prevent Preferences from displaying one database path while services remain connected to another.

Implementation steps:

1. Make the Preferences database field read-only factual state or replace it with **Select Existing Database…** and **Create New Database…** commands owned by `DatabaseSessionController`.
2. Capture dirty-workspace confirmation before a switch; connect, migrate, validate, and load company state before changing the selected path.
3. Update `UiSessionState`, datasource/JPA composition, active company, open panels, diagnostics, and recent-database state only after successful connection.
4. On failure, retain the prior database, company, services, and displayed path exactly; report source/target paths and validation facts.
5. Remove every direct preference write capable of changing active database authority.

Acceptance and tests:

- The displayed active path, JDBC/JPA datasource, Diagnostics path, and records always identify the same database.
- Failed migration/validation and cancelled dirty-state confirmation preserve the prior session.
- Successful switch rebuilds services and panels once, with no stale company data or split-brain reads.

Execution state:

- Settings displays the connected database path as read-only factual state and no longer writes `UiSessionState.databaseSelection` from Preferences.
- `DatabaseSessionController` prepares the target database, migrated JPA/service bundle, and authoritative active-company selection before publishing any new session authority.
- `UiServiceRegistry` keeps the prior service bundle active until the prepared target is validated; failed preparation closes only the target resources.
- Dirty-workspace cancellation prevents target preparation. Failed target migration/validation preserves the prior database, company, services, displayed path, Diagnostics path, and healthy workspace state.
- A successful switch publishes database/company shell state together, swaps the prepared service bundle, and refreshes open panels once without duplicate active-company refresh.
- `FileAppStateStore` persists database selection and active-company shell convenience state in one properties-file write; the retired `MainWindow` database command also routes through the same session controller.
- Focused controller/source/file-state tests plus real H2/JPA and JavaFX workspace regression coverage are included, along with `doc/P16-S5-connected-database-session-authority-user-testing.md`.
- Exact implementation head `6b226c8955e5a462e44f6938e59c9bd3a9d0cdeb` passed Maven PR Tests run `31240324104`. Final tested PR head `53d80e30c1338534f09935227eb3d9bf4f931e14` passed the complete Maven PR Tests gate in run `31240620992`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner completed and accepted `doc/P16-S5-connected-database-session-authority-user-testing.md` on 2026-08-08, and PR #257 merged to `main` at `97fc2e224854c221cc15657e8b9ad7532a9fee23`.

Next exact action:

- None; P16-S5 is DONE and P16-S6 is active.

## P16-S6 — Budget draft lifecycle and fiscal-period authority

Status: DONE through merged PR #258 and owner desktop acceptance.

Starting base: `97fc2e224854c221cc15657e8b9ad7532a9fee23`
Pull request: #258
Validated implementation head: `bb19fec9ea1fa864a92d8a65df1f5021062c0f9e`
Final tested PR head: `2e80f9ad3d433099f2f6ba600ae91d99536318e5`
Merge commit: `4edb463a5e7ca80d91381e8f941413703b6237be`

Purpose: preserve the exact saved draft and calculate budgets from company fiscal settings and the active accounting period.

Implementation steps:

1. Add company-scoped queries for active plans and editable drafts, with stable IDs and deterministic ordering.
2. Make Save return/select the same durable draft; reload must never replace it with a new blank draft.
3. Make **Activate Version** act only on the explicitly selected draft and enforce one active version per company/fiscal year in one transaction.
4. When creating a revision from an active plan, copy its governed lines and metadata into a new draft while preserving source plan history.
5. Replace `LocalDate.now()` and calendar-year assumptions with a shared fiscal-period range derived from company fiscal-year start and the shell-selected accounting period.
6. Align Budget Editor, Budget vs Actual, reports, and exports to the same immutable fiscal request.

Acceptance and tests:

- Save/reload/restart returns the same draft and lines; no blank or orphan draft is created implicitly.
- Activation targets the chosen draft, copies/revisions are exact, and concurrent activation rolls back safely.
- Non-January fiscal years and historical/future selected periods produce correct actual and budget ranges.

Execution state:

- S5 owner acceptance and merge are recorded; S6 began from exact merged `main` commit `97fc2e224854c221cc15657e8b9ad7532a9fee23` on a fresh branch.
- The current implementation replaces implicit draft creation with explicit stable-ID draft/version selection, adds exact active-to-draft revision copying, serializes activation by company, and derives budget/report date authority from `FiscalPeriodRange` using company fiscal settings plus the shell-selected accounting period.
- Focused service, non-January fiscal-range, stable-draft, revision, concurrent-activation, variance, report-default, and source-regression tests are included together with `doc/P16-S6-budget-draft-fiscal-period-authority-user-testing.md`.
- Exact implementation head `bb19fec9ea1fa864a92d8a65df1f5021062c0f9e` passed Maven PR Tests run `31280152673`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- Exact final PR head `2e80f9ad3d433099f2f6ba600ae91d99536318e5` passed Maven PR Tests run `31280381274`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner completed and accepted `doc/P16-S6-budget-draft-fiscal-period-authority-user-testing.md` on 2026-08-08, and PR #258 merged to `main` at `4edb463a5e7ca80d91381e8f941413703b6237be`.

Next exact action:

- None; P16-S6 is DONE and P16-S7 is active.

## P16-S7 — Factual Audit History authority

Status: DONE through merged PR #259 and owner desktop acceptance.

Starting base: `4edb463a5e7ca80d91381e8f941413703b6237be`
Pull request: #259
Validated implementation head: `e140aae3c1d07ceeabbc93d179489d03faa15896`
Final tested PR head: `92fa35a6d434f2ab06fb38e09629da3dcc419e2a`
Merge commit: `01b37364d68ea9388c8b704dadd442cb0122b3db`

Purpose: show current `audit_event` facts in Audit History instead of presenting the obsolete approval table as current history.

Implementation steps:

1. Replace the production query used by `ApprovalAuditPanel` with company-scoped `AuditHistoryService` projections over `AuditEvent`.
2. Display timestamp, actor, action, entity type/identifier, summary, before/after values, and reason with active-company formatting and bounded detail views.
3. Preserve immutable read-only behavior and company isolation; add filtering by date, action, entity, and actor without SQL in the panel.
4. If legacy `approval_audit_record` rows must remain visible, place them in an explicitly labeled compatibility view and never merge them into current factual history silently.
5. Rename compatibility classes/panel labels where practical without breaking saved destination identifiers.

Acceptance and tests:

- Transaction, correction, period-close, SCLX, bank-import, and other current operation events appear after refresh and restart.
- Other-company and unresolved global events do not leak into the active-company view.
- The panel performs no writes and never implies approval/rejection workflow.

Execution state:

- S6 owner acceptance and merge are recorded; S7 began from exact merged `main` commit `4edb463a5e7ca80d91381e8f941413703b6237be` on a fresh branch.
- Production Audit History now queries company-scoped `AuditEvent` projections through `AuditHistoryService`; other-company and unresolved/global rows are excluded by the service query.
- Action, entity, actor, and inclusive date filtering are service-owned. The JavaFX panel is SQL-free and exposes only immutable factual table/detail views with company-formatted timestamps and company-owned layout state.
- Legacy `approval_audit_record` persistence and compatibility classes remain stored compatibility structures only; the production panel and `UiServiceRegistry` no longer query or expose the legacy approval service, and legacy rows are not blended into factual history.
- Focused H2/JPA restart, company-isolation, global-event exclusion, filter, and production-source guard tests are included with `doc/audit/audit-history.md` and `doc/P16-S7-factual-audit-history-authority-user-testing.md`.
- Exact implementation head `e140aae3c1d07ceeabbc93d179489d03faa15896` passed Maven PR Tests run `31282119224`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- Exact final PR head `92fa35a6d434f2ab06fb38e09629da3dcc419e2a` passed Maven PR Tests run `31282307364`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner completed and accepted `doc/P16-S7-factual-audit-history-authority-user-testing.md` on 2026-08-08, and PR #259 merged to `main` at `01b37364d68ea9388c8b704dadd442cb0122b3db`.

Next exact action:

- None; P16-S7 is DONE and P16-S8 is active.

## P16-S8 — Explicit reviewed-statement acceptance into the ledger

Status: DONE through PR #260.

Starting base: `01b37364d68ea9388c8b704dadd442cb0122b3db`
Pull request: #260
Validated implementation head: `73ce3a2a966f690490de705058916519a741b635`
Final PR head: `f701aa4d495f9ab1c291578dd70de3f5221a42c7`
Merged `main` commit: `2edc47d862643b5e131a7825dbf7b6e5b662febe`

Purpose: let a user turn one reviewed bank statement row into a canonical transaction without automatic posting or re-keying.

Implementation steps:

1. Add **Create Transaction from Reviewed Row…** only for an unmatched, eligible statement row in the active company/configured account.
2. Freeze the statement-row identity, source amount/date/payee/memo/reference/currency, duplicate/review state, and target bank account before opening a transaction preview.
3. Prefill the bank split and require the user to select or edit counter-account splits, funds, activity, counterparty/merchant, date, and description until the canonical transaction balances.
4. Revalidate row eligibility, company/account ownership, source identity, closed period, duplicate state, and completed-reconciliation protection immediately before commit.
5. In one transaction, create the canonical transaction through `TransactionEntryService`, link the statement row/match, update review state, and write factual audit history.
6. Make retries idempotent and route the result to Journal/Reconciliation drill-through.

Acceptance and tests:

- Import alone still creates zero `Txn`/`TxnSplit` rows.
- Explicit acceptance creates exactly one balanced transaction and one durable link; cancellation or late failure creates neither.
- Exact/probable duplicates, already matched rows, closed periods, finalized reconciliations, and cross-company IDs are blocked or require the governed explicit resolution.
- Batch auto-posting and rule-driven unattended posting remain out of scope.

Execution state:

- S7 owner acceptance and merge are recorded; S8 began from exact merged `main` commit `01b37364d68ea9388c8b704dadd442cb0122b3db` on a fresh branch.
- The existing `bank_statement_line.accepted_txn_id` relationship is the durable acceptance link; S8 adds no schema, queue, or parallel ledger/import store.
- `ReviewedStatementAcceptanceService` freezes source identity in a non-mutating preview, revalidates company/account/source/duplicate/close/finalized-reconciliation state under a row lock, calls canonical `TransactionEntryService` inside one caller-owned transaction, then links the row, sets `ACCEPTED`, updates batch disposition, and writes a factual acceptance audit event atomically.
- Probable duplicates require explicit confirmation; exact duplicates, already matched rows, finalized reconciliation ranges, foreign-company IDs, unsupported currency conversion, and canonical closed-period/reference/balance failures remain blocked. Successful retries reuse the existing accepted transaction.
- Bank Transactions exposes the explicit acceptance dialog with a frozen source summary, prefilled bank split, editable balanced counter splits/reference data, and Journal drill-through after successful commit. Import remains non-posting.
- The initial PR head `e15ed7de60bf56880963e0813736d450d77d3ca7` failed the production source guard because the panel spelled the injected calls as `acceptanceService.get().preview(...)` / `.accept(...)`; the correction binds the supplier result locally and preserves the same service-owned preview/accept authority.
- Exact corrected implementation head `73ce3a2a966f690490de705058916519a741b635` passed Maven PR Tests run `31291318707`, including clean headless `mvn clean verify`, the deliberately repeated Maven test suite, and production JavaFX route compliance.
- Exact final PR head `f701aa4d495f9ab1c291578dd70de3f5221a42c7` passed Maven PR Tests run `31291581055`.
- The owner completed and accepted `doc/P16-S8-reviewed-statement-ledger-acceptance-user-testing.md` on 2026-08-09, and PR #260 merged to `main` at `2edc47d862643b5e131a7825dbf7b6e5b662febe`.

Next exact action:

- None; P16-S8 is DONE and P16-S9 is active.

## P16-S9 — Financially relevant inventory movements

Status: DONE through merged PR #261 and owner desktop acceptance.

Starting base: `2edc47d862643b5e131a7825dbf7b6e5b662febe`
Branch: `codex/P16-S9-inventory-movement-accounting`
Pull request: #261
Validated implementation head: `3c2c6663b0f0c5b28c9d7bb877cfe9197b225412`
Final tested PR head: `75fb30ed1fe2c7e118e20774608524db211c8ce4`
Merged `main` commit: `05ff95be58c650072c308670840b389d72f7c0c1`

Purpose: keep physical quantity and general-ledger inventory value synchronized when a movement has an accounting effect.

Implementation steps:

1. Amend `doc/inventory/inventory-and-assets.md` with explicit RECEIVE, ISSUE, and ADJUST valuation policies, required debit/credit accounts, fund behavior, rounding, zero-value handling, and correction rules.
2. Add a non-mutating movement preview showing quantity before/after, unit and extended value, accounts/funds, and the proposed balanced canonical transaction.
3. Require explicit confirmation for financially relevant movements; permit a clearly labeled nonfinancial movement only where the governing policy allows it.
4. Persist the movement, resulting quantity, canonical transaction, portable identities, and factual audit event in one caller-owned transaction.
5. Protect closed periods and completed reconciliations and implement reversal/correction through the canonical transaction correction policy rather than editing historical value silently.
6. Preserve P15 SCLX import behavior: restoring a historical movement with existing transaction provenance must not synthesize a second transaction.

Acceptance and tests:

- Successful financial movement changes quantity and ledger value together; failure changes neither.
- Restart, multi-company, rounding, negative-quantity, closed-period, duplicate, and late-failure scenarios are covered.
- The movement table's `Txn` column is populated only by a real canonical link.

Execution state:

- P16-S8 owner acceptance and merge are recorded; S9 began from exact merged `main` commit `2edc47d862643b5e131a7825dbf7b6e5b662febe` on a fresh branch.
- The working implementation adds a frozen non-mutating movement preview, fixed-unit valuation at four-decimal half-up ledger precision, explicit zero-value nonfinancial confirmation, active-company/account/fund/date validation, pessimistic revalidation, canonical caller-owned transaction entry, real movement transaction links, and factual inventory audit in one atomic operation.
- Financial correction is append-only: `InventoryService` uses a new caller-owned `TransactionCorrectionService` reversal seam so the canonical reversal and inverse adjustment movement commit together. Both legacy completed and native finalized reconciliation protections are enforced, and canonical balance validation now interprets stored split signs using account normal balance.
- Valued items must begin at zero quantity; account/fund/unit-value edits are blocked while quantity is on hand. P15 SCLX `createForImport(...)` and `recordMovementForImport(...)` remain non-synthesizing historical restore seams.
- Focused tests cover non-mutation, atomic success, rounding, idempotent retry, stale preview, late rollback, multi-company, negative quantity, close/finalized reconciliation, explicit nonfinancial behavior, canonical reversal, duplicate reversal, and restart persistence. The owner checklist is `doc/P16-S9-inventory-movement-accounting-user-testing.md`.
- Local Java syntax parsing and `git diff --check` pass. Java 17 is present, but the container has no Maven executable or wrapper, so the GitHub Maven PR Tests workflow is the authoritative compile/test environment. Publication uses local Git plus the connected GitHub service; no `gh` CLI is required.
- Initial PR head `73c261635917372c8e34452d8e65f6b24b93378b` exposed two obsolete transaction-correction fixtures that stored a credit-normal income credit with the pre-canonical negative sign. The fixtures and reversal expectation now use the same normal-balance-relative storage convention as `TransactionEntryService`; the production canonical balance validation remains intact.
- Exact corrected implementation head `3c2c6663b0f0c5b28c9d7bb877cfe9197b225412` passed Maven PR Tests run `31337956279`: clean headless `mvn clean verify` passed with 593 tests, 0 failures/errors, and 31 skips; the deliberately repeated 593-test suite passed; and the 9-test production JavaFX route/source compliance suite passed.
- Exact final PR head `75fb30ed1fe2c7e118e20774608524db211c8ce4` passed Maven PR Tests run `31338180450`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance.
- The owner completed and accepted `doc/P16-S9-inventory-movement-accounting-user-testing.md` on 2026-08-09, and PR #261 merged to `main` at `05ff95be58c650072c308670840b389d72f7c0c1`.

Next exact action:

- None; P16-S9 and P16-S10 are DONE.

## P16-S10 — Authoritative Journal cleared-state projection

Status: DONE through merged PR #262 and owner desktop acceptance.

Starting base: `05ff95be58c650072c308670840b389d72f7c0c1`
Branch: `codex/P16-S10-journal-cleared-state`
Pull request: #262
Validated implementation head: `e17773f5d44ef89b33176a77cad8f9c23125c9e8`
Final tested PR head: `f8e755374e65ceba2709837804ab6aaa07072753`
Merged `main` commit: `556a8216125bc4bcb7916451049da25648850350`

Purpose: replace the hard-coded `Uncleared` value with facts from canonical transaction splits.

Implementation steps:

1. Extend `TransactionView.Line` with `bankCleared` and `bankClearedOn` from `TxnSplit` and preserve line identity/account context.
2. Define transaction-level display states: `Not bank`, `Uncleared`, `Cleared`, and `Mixed`; do not collapse mixed split state into a false boolean.
3. Calculate the summary in the query/service projection, not in a JavaFX cell factory.
4. Show cleared date at line detail and provide reconciliation drill-through where a durable match/session exists.
5. Refresh Journal after reconciliation clear/unmatch/finalize operations without mutating ledger state from the view.

Acceptance and tests:

- Transactions with zero, one, or multiple bank splits render exact states and dates after restart.
- Reconciliation changes are reflected on refresh and remain company/account isolated.
- No bank transaction is labeled Uncleared merely because the projection omitted data.

Execution state:

- P16-S9 owner acceptance and merge are recorded; S10 began from exact merged `main` commit `05ff95be58c650072c308670840b389d72f7c0c1` on a fresh branch.
- `TransactionView.Line` now carries service-projected BANK classification, `TxnSplit.bankCleared`, `bankClearedOn`, and an optional durable reconciliation-session ID selected only through company/account-consistent native reconciliation facts.
- `TransactionView.clearedState()` summarizes all BANK lines as `Not bank`, `Uncleared`, `Cleared`, or `Mixed`; Journal no longer infers state from the transaction header or hard-codes `Uncleared`.
- The integrated entry-line table shows read-only bank state and company-formatted cleared date. **Open Selected Line Reconciliation** is enabled only for a projected durable session and routes the exact session into the existing reconciliation workspace; Journal gains no cleared-state write path.
- Focused H2 coverage exercises the four summary states, line dates, exact reconciliation-session projection, and restart persistence. Source guards prohibit the former hard-coded expression and require service-owned facts/navigation. The owner checklist is `doc/P16-S10-journal-cleared-state-user-testing.md`.
- Local `git diff --check` and independent Java grammar parsing pass. This container has a Java 17 runtime but no Maven executable, wrapper, or Java compiler, so GitHub Maven PR Tests is the authoritative compile/test environment.
- Draft PR #262 was opened from implementation head `e17773f5d44ef89b33176a77cad8f9c23125c9e8`. Maven PR Tests run `31341377340` passed on that exact head: clean verification and the deliberate repeated suite each ran 595 tests with 0 failures, 0 errors, and 31 profile skips; JavaFX production-route compliance ran 9 tests with 0 failures, 0 errors, and 0 skips.
- Exact final PR head `f8e755374e65ceba2709837804ab6aaa07072753` passed Maven PR Tests run `31341722534`, including clean verification, the deliberately repeated suite, and JavaFX production-route compliance.
- The owner completed and accepted `doc/P16-S10-journal-cleared-state-user-testing.md` on 2026-08-10, and PR #262 merged to `main` at `556a8216125bc4bcb7916451049da25648850350`.

Next exact action:

- None; P16-S10 is DONE and P16-S11 is active.

## P16-S11 — Production preference consumers

Status: DONE through merged PR #263 and owner desktop acceptance.

Starting base: `556a8216125bc4bcb7916451049da25648850350`

Purpose: ensure every saved preference affects production behavior or is removed/relabeled as deferred.

Implementation steps:

1. Inventory every Settings field and its production read path; add an executable preference-to-consumer matrix.
2. Apply theme and native-decoration choices at the supported lifecycle point, clearly marking restart-required settings.
3. Implement window-size/state restoration only when **Remember window state** is enabled and keep it company/user scoped according to the governing preference model.
4. Route closed-period defaults into `PeriodCloseRunsPanel`; remove hard-coded `WARN_AND_REOPEN`, reason flags, and actor text where preferences/session facts exist.
5. Do not expose privilege as effective authorization until authentication is governed; remove it, label it informational/deferred, or give it a real non-security consumer.
6. Remove retired `MainWindow` as the only consumer of any production-visible preference.

Acceptance and tests:

- Every enabled persisted setting has one observable production effect and one tested consumer.
- Restart-required settings say so and apply after restart.
- No preference changes accounting policy silently or pretends to enforce authentication.

Execution state:

- P16-S10 owner acceptance and merge are recorded; S11 began from exact merged `main` commit `556a8216125bc4bcb7916451049da25648850350` on a fresh branch.
- Startup inspection confirms production `MainApp` does not load or apply the persisted theme/native/window-state preferences, `SettingsPanel` does not save shell preferences through the production `AppStateStore`, `PeriodCloseRunsPanel` hard-codes reopening policy/reason and actor defaults, the entered-transaction delete-confirmation setting has no Journal consumer, and default privilege is presented only through retired/informational UI despite authentication remaining outside P16.
- `PreferenceConsumerMatrix` now enumerates every enabled or compatibility-only value. Settings disables and explains `defaultPrivilege` and `defaultReopenScope`; retired/reference shells no longer treat the former as authorization or an effective session role.
- Production startup loads user-machine preferences through the workspace-owned `AppStateStore`; theme classes update immediately, native/unified decoration is selected before stage creation, and top-level geometry/maximized state plus shell dividers restore and persist only while **Remember window state** is enabled. Disabling the preference clears those saved shell values.
- Period Close now defaults its reopening policy and require-reason control from the active desktop session whenever shown and uses the factual local operating-system user as its editable actor default. Journal now consumes the direct-delete confirmation preference while keeping reversal explicit and using the same factual actor identity for correction audit commands.
- Governing scope, operation, persistence, accounting, and Journal documentation now distinguish user-machine shell preferences from company-owned H2 display state. The owner checklist is `doc/P16-S11-production-preference-consumers-user-testing.md`.
- Focused tests cover the executable matrix, shell persistence round-trip/clearing, remembered-geometry clamping, decoration mapping, factual actor resolution, privilege non-gating, and JavaFX production-consumer source routes. All 681 Java sources/tests pass independent Java grammar parsing; dependency-free new policy classes compile with the JDK compiler module; CSS braces and `git diff --check` pass.
- Local Maven verification is unavailable because this container has no Maven executable or wrapper and no resolved project dependency repository. The JDK compiler module validates the dependency-free new policies, while GitHub Maven PR Tests remains the authoritative full compile/test environment after publication.
- The complete reviewed local tree was published through the connected GitHub service after explicit owner authorization because this checkout has no HTTPS push credentials. Draft PR #263 opened at exact remote head `c4b477289ddbea22261286dc3e8e539d5e299892`; that remote tree matches the reviewed local tree byte-for-byte.
- Initial documentation-inclusive run `31407639101` reached the full 607-test suite and exposed one incorrect source-guard ownership assertion: it searched `ProductionWorkspaceWindow` for the remember-window-state lifecycle check even though `MainApp` owns the JavaFX `Stage` and the production check. The guard now inspects `MainApp`; production preference behavior was not weakened or relocated.
- Exact corrected head `a7df25d5b6ac5c2353ab923d4ef82d5d62c352f5` passed Maven PR Tests run `31407997190`: clean `mvn clean verify` ran 607 tests with 0 failures/errors and 31 skips; the deliberate repeated 607-test suite passed; and all 9 production JavaFX route/source compliance tests passed.
- Exact final PR head `7fd2e1f05bee991083a0e84d66c5749965fefd1f` passed Maven PR Tests run `31408499739`.
- The owner completed and accepted `doc/P16-S11-production-preference-consumers-user-testing.md` on 2026-08-10, and PR #263 merged to `main` at `f7cce74e5d0cf93661df7673dcfa80a02e5761f9`.

Next exact action:

- None; P16-S11 is DONE and P16-S12 is active.

## P16-S12 — Truthful global command and shortcut capabilities

Status: DONE through merged PR #264 and owner desktop acceptance.

Starting base: `f7cce74e5d0cf93661df7673dcfa80a02e5761f9`

Purpose: stop the shell from claiming that empty panel defaults handled New, Save, Copy, Paste, Find, or command-palette actions.

Implementation steps:

1. Replace empty `AppPanel` command defaults with explicit capability/query methods and handled/not-handled results.
2. Enable toolbar/menu commands only when the active panel supports them; otherwise disable them with a concise explanation.
3. Return `handled=true` only after a real operation starts or completes; propagate validation/failure messages accurately.
4. Let standard JavaFX text controls handle Copy/Paste where appropriate and add panel delegates only for genuine table/editor semantics.
5. Implement Ctrl+F and Ctrl+K/Ctrl+G in the production shell or remove them from Help; keep menu labels, accelerators, and Help generated from one command registry where practical.
6. Add an all-destination capability matrix test covering navigation, toolbar, menu, shortcuts, dirty state, and no-op rejection.

Acceptance and tests:

- No enabled global command is a no-op.
- Help lists only installed shortcuts and exact destination names.
- Commands follow the selected Administration tab and canonical Journal workspace correctly.

Implementation progress:

- `AppPanel` now exposes explicit command capabilities and handled/not-handled execution; empty New/Save defaults no longer report success, and clipboard hooks are removed.
- `GlobalCommandRegistry` owns production New, Save, Close All Tabs, and Close Inspector labels/accelerators/Help text. Production no longer intercepts Copy/Paste or advertises uninstalled Find/command-palette shortcuts.
- The production menu and toolbar follow the active panel capability set. Administration delegates to the selected Preferences, Database Transfer, Company Admin, or User Admin tab and notifies the shell when selection changes.
- Focused tests cover default no-op rejection, thrown failures, every production destination and Journal alias, Administration tab changes, exact registry shortcuts, menu/toolbar enablement, and dirty-tab close protection.
- Governing operation, composition, UI-rule, and persistence-authority documents are updated. Owner verification is recorded in `doc/P16-S12-truthful-global-command-capabilities-user-testing.md`.
- All Java sources pass independent Java 17 parsing; the command core passes an isolated JavaFX compiler check; focused source guards and `git diff --check` pass. Maven is unavailable in the local container, so GitHub Maven PR Tests is the authoritative full compile/test gate.
- The complete 33-file implementation tree was published through the connected GitHub service after explicit owner authorization. Draft PR #264 opened at exact remote head `6accaf3ff1c86f0bce09fa732a40d68005a49d4a`; its tree SHA `7fd4396a77fab930aace1b93c6395ffe25015c50` exactly matches the reviewed local implementation tree.
- Initial plan-inclusive Maven PR Tests run `31427773070` compiled the production sources until `ReferenceWorkspaceWindow` and exposed two stale Edit-menu method references to the removed global `copySelection()` and `paste()` hooks. The compatibility shell now leaves native text-control Copy/Paste un-intercepted, and the focused source guard covers that boundary.
- Exact corrected head `8fdd8b636ad23a9545f8bb79300346f2f75e2734` passed Maven PR Tests run `31427958302`: clean `mvn clean verify` ran 616 tests with 0 failures/errors and 33 skips; the deliberate repeated 616-test suite passed; and all 9 production JavaFX route/source compliance tests passed.
- Exact documentation-inclusive head `dcb65f300d93832ee6d7f00cd5f76d2e2e68e1a5` passed Maven PR Tests run `31428418987`, including clean `mvn clean verify`, the deliberately repeated full suite, and production JavaFX route/source compliance.
- Exact final PR head `fcd063685c7dd741d39b1714af3e221989509825` passed Maven PR Tests run `31428868161`.
- The owner completed and accepted `doc/P16-S12-truthful-global-command-capabilities-user-testing.md` on 2026-08-10, and PR #264 merged to `main` at `6ff2c23649cede30e68fcd335d662eaf7d99b978`.

Next exact action:

- None; P16-S12 is DONE and P16-S13 is active.

## P16-S13 — Truthful report semantics

Status: DONE through merged PR #265 and owner desktop acceptance.

Starting base: `6ff2c23649cede30e68fcd335d662eaf7d99b978`

Purpose: make report names, filters, and exported content match authoritative accounting semantics.

Implementation steps:

1. Define exact row-selection contracts in `doc/reporting/report-library.md` for **All Checks/Transfers** and **Fund Transfers** before changing queries.
2. Identify the authoritative transaction/reference/split facts that distinguish checks, bank transfers, and inter-fund transfers; add nondestructive classification data only if current facts cannot prove the distinction.
3. Replace the unfiltered general-ledger implementation of **All Checks/Transfers** with the governed predicate and totals.
4. Replace fund-activity grouping with true transfer-pair selection and balanced per-transaction/per-fund totals.
5. If an existing schema cannot support a truthful report in the same slice, disable or accurately rename that catalog entry until its classification migration is delivered.
6. Add preview/CSV/PDF/XLSX semantic equivalence, empty-range, company/fund/date isolation, correction, and drill-through tests.

Acceptance and tests:

- Report title, parameters, selected rows, totals, exports, and Journal drill-through describe the same immutable request.
- Ordinary fund activity does not appear as a fund transfer.
- No first-pass approximation remains under a completed-sounding report name.

Implementation progress:

- The legacy stable ID and workbook template ID `all-checks-transfers` / `AllChecksTfrs` remain for traceability, but the visible report is now **Bank Account Activity** because current schema facts can prove bank-function account splits, not a general check/transfer classification.
- `SemanticAccountingReportQueryService` owns company-scoped predicates. Bank activity selects only canonical `TxnSplit` rows whose account has `AccountFunction.BANK`, with inclusive request dates and optional fund scope. Displayed totals are calculated only from the returned bank-function rows.
- Fund Transfers selects only explicit `POSTED` `FundTransfer` records linked to a canonical transaction owned by the active company. Each selected record expands into a negative source leg and equal positive destination leg, followed by per-fund totals and a zero all-funds net. Draft, void, unlinked, ordinary multi-fund, out-of-range, and other-company facts are excluded.
- Report Library injects the company-scoped query service into the existing `ReportExecutionService`; preview, export, and drill-through continue to share one immutable `ReportRequest`. No second ledger or report store was introduced.
- The governed predicates and row-limit semantics are recorded in `doc/reporting/report-library.md`. The owner checklist is `doc/P16-S13-truthful-report-semantics-user-testing.md`.
- Focused H2 integration tests cover bank-function-only selection, fund/date/company isolation, reversals, explicit posted transfers, balanced pair expansion, exclusions, totals, empty ranges, and preview/CSV semantic parity.
- Local Maven execution is unavailable in this environment. Draft PR #265 opened at implementation head `0f32bd755ac6a924e72bcea0098b9fe3e1fda36f`.
- Exact plan-inclusive head `2c16bc6c0777ee170ea8d3ee11868a2098aac2d4` passed Maven PR Tests run `31439706723`: clean `mvn clean verify` and the deliberate repeated suite each ran 621 tests with 0 failures/errors and 33 skips; all 9 production JavaFX route/source compliance tests passed.
- Exact documentation-inclusive head `d7425a5451121a277b527b546e1f316c1b27d136` passed Maven PR Tests run `31440051765` with the same clean verification, repeated 621-test suite, and 9-test JavaFX compliance result.
- Exact final PR head `e34311a43325c769a2512a5dd4ce3875cd6ee738` passed Maven PR Tests run `31440376358` with 621 tests passing twice, 33 skips, and all 9 production JavaFX compliance tests passing.
- The owner completed and accepted `doc/P16-S13-truthful-report-semantics-user-testing.md`, and PR #265 merged to `main` at `8c0717b2fc3b7de5f247044b30ff9951d5df7cfc` on 2026-08-10.

Next exact action:

- None; P16-S13 is DONE and P16-S14 is active.

## P16-S14 — Fixed-asset disposal accounting

Status: DONE through merged PR #266 and owner desktop acceptance.

Purpose: complete the visible `DISPOSED` lifecycle with disposal proceeds, accumulated depreciation, gain/loss, and canonical accounting.

Implementation steps:

1. Govern disposal, retirement, and impairment separately, including dates, proceeds, expense/gain/loss accounts, fund treatment, and closed-period rules.
2. Add a disposal preview that calculates remaining book value and proposed balanced canonical entries without mutation.
3. Persist disposal facts, status transition, canonical transaction link, portable identity, and factual audit history atomically.
4. Prevent direct status editing to `DISPOSED` without the governed workflow; provide reversal/correction through canonical correction policy.
5. Extend SCLX only after the disposal facts have a stable documented schema and round-trip tests.

Acceptance and tests:

- Disposal cannot leave asset status and ledger out of sync.
- Full/partial proceeds, zero proceeds, fully depreciated assets, impairment, closed periods, retry, and late rollback are covered.

Implementation progress:

- V71 and `FixedAssetLifecycleEvent` establish immutable Sale, Retirement, and Impairment facts with intrinsic UUID identity, carrying-value snapshots, account provenance, original/reversal transaction links, and database constraints.
- `FixedAssetService` owns frozen previews, pessimistic revalidation, exact balanced entries, asset status changes, canonical transaction/audit writes, idempotent retry, and domain reversal in one transaction. Direct `DISPOSED` edits and generic Journal mutation of linked transactions are rejected.
- Asset Register exposes responsive preview-confirm actions, persistent lifecycle history, governed reversal, and Ledger drill-through. Disposed assets remain visible but their status cannot be edited directly.
- Focused H2 tests cover gain and loss sales, zero-proceeds fully depreciated retirement, impairment and reversal, chronological ordering, closed periods, finalized proceeds-account reconciliation, retry, stale preview, company isolation, generic-correction rejection, late rollback across restart, and V71 schema constraints.
- The lifecycle schema and semantics are documented here and in `doc/inventory/inventory-and-assets.md`. SCLX fixed-assets extension v1 remains unchanged in this product-workflow slice; lifecycle facts are not inferred from `DISPOSED`. Any extension must be separately versioned and pass semantic round-trip tests before it can claim coverage.
- Owner desktop verification is governed by `doc/P16-S14-fixed-asset-disposal-accounting-user-testing.md`.
- Local implementation commit `c8bd75bdd0b32684dd0566de8ba7d97f0e7a13cf` passes full Java-source syntax parsing and `git diff --check`. Maven execution is unavailable because this environment has Java 17 but no `mvn` executable; focused and full verification must therefore run through the repository PR workflow after publishing authorization.
- Draft PR #266 opened from `codex/P16-S14-fixed-asset-disposal-accounting` at exact remote handoff head `a279fa16181670340a290ea1e0313f4c84fdc49b`; GitHub Maven PR Tests and owner desktop acceptance remain pending.
- Initial Maven PR Tests run `31448898022` exposed one real chronology gap and two test-helper errors: backdated depreciation did not reject a later impairment, while native H2 UUID values were cast directly instead of read through typed entity projections.
- Exact corrected head `bbab5c7530b045975c22a8f2ce1b226333597278` passed Maven PR Tests run `31449177523`: clean `mvn clean verify` and the deliberate repeated suite each ran 633 tests with 0 failures/errors and 33 skips; all 9 production JavaFX route/source compliance tests passed.
- Exact final PR head `b9021448f0ff8a068edb3600e5cde7e4d061b6a4` passed Maven PR Tests run `31449443954` with 633 tests passing twice, 33 skips, and all 9 production JavaFX compliance tests passing.
- The owner completed and accepted `doc/P16-S14-fixed-asset-disposal-accounting-user-testing.md`, and PR #266 merged to `main` at `4504fdbd17ae50d5b03c36a6f5128bed6ee81c28` on 2026-08-11.

Next exact action:

- None; P16-S14 is DONE and P16-S15 is active.

## P16-S15 — Fixed-asset and inventory reports

Status: DONE through merged PR #267 and owner desktop acceptance.

Purpose: add domain reports only after P16-S9 and P16-S14 establish authoritative movement and disposal facts.

Implementation steps:

1. Add typed immutable requests for asset register, depreciation history/schedule, inventory on hand/valuation, and inventory movement history.
2. Query canonical domain facts and linked ledger identities with company, date, account, fund, status, and item/asset filters.
3. Reuse Report Library formatting/export/drill-through behavior and active-company preferences.
4. Reconcile domain totals to canonical ledger control accounts and disclose timing or nonfinancial-movement differences explicitly.
5. Add preview/export semantic equivalence and reconciliation tests.

Acceptance and tests:

- Domain and ledger totals reconcile under the governed policy or show an exact explainable difference.
- Reports remain company isolated and do not infer missing transactions.

Implementation progress:

- `ReportDefinition` now exposes four real semantic reports and a domain-filter mode. `ReportRequest` retains typed immutable fixed-asset or inventory selections by persisted asset/item and control-account ID plus domain status; the same request continues through preview, export, and Journal drill-through.
- Company-scoped `AssetInventoryReportQueryService` queries fixed assets, completed depreciation runs, lifecycle events/reversals, inventory items/movements, and canonical control-account splits. Register and valuation are as-of projections; depreciation and movement history use inclusive ranges.
- Fixed-asset register values reconstruct lifecycle status as of the request date, include opening/completed depreciation and unreversed impairment, and derecognize final dispositions. Depreciation history retains original/reversal transaction identities and adds projection-only straight-line schedule summaries without fabricating future transactions.
- Inventory valuation reconstructs quantity from persisted movement history, including the exact state before a later first movement. Movement history exposes real canonical transaction IDs or an explicit nonfinancial/unlinked state.
- Every report emits domain, canonical ledger, and exact difference rows. Fund-unallocated account opening balances, unlinked movement net, shared control accounts, filtering, and row-limit effects remain visibly disclosed rather than inferred away.
- Four semantic templates reuse the Report Library preview and TEXT/CSV/PDF/XLSX adapters. The owner checklist is `doc/P16-S15-fixed-asset-inventory-reports-user-testing.md`.
- Focused database-backed tests cover company isolation, lifecycle impairment reversal, depreciation schedule identity, historical inventory position, linked/unlinked movements, filters, reconciliation differences, and semantic preview/CSV parity. Local Maven execution remains unavailable because this environment has no `mvn` or `javac`; Java syntax, JSON, whitespace, and repository source contracts are verified locally before PR handoff.
- Exact local implementation commit `8723c09dcd9c6055a6eb1667fdb89dc5b050aea8` passes full Java-source syntax parsing for all 696 main/test sources, JSON parsing for every semantic template, and `git diff --check`. Maven remains unavailable because the container has no `mvn` executable; authoritative focused/full execution is pending publication and GitHub CI.
- The approved two-commit, 24-file tree was reconstructed through the connected GitHub service without `gh`; remote implementation tree `63364c32e76d73952e9f2bd70ffc8b16421efa44` and handoff tree `8f7e0fe7a50c8d4c6d47d22961ade3706d6fc4e2` exactly match their local Git trees.
- Draft PR #267 opened at exact remote handoff head `e9f0e0224253f003c6e686d0c6d8d49e5b529a1d`; authoritative Maven PR Tests and owner desktop acceptance remain pending.
- Initial plan-inclusive Maven PR Tests run `31456857434` compiled production and ran 638 tests before one new integration assertion exposed truncated formatted money in semantic text output; the repeated suite and JavaFX compliance correctly did not run on that failed head.
- `SemanticReportRenderer` now preserves complete currency, date, and numeric facts in text output while retaining bounded truncation for descriptive text. Exact corrected head `1751d706a7cb733f48c3e381449f67f9ce1826e2` passed Maven PR Tests run `31457080329`: clean `mvn clean verify` and the deliberate repeated suite each ran 638 tests with 0 failures/errors and 33 skips; all 9 production JavaFX route/source compliance tests passed.
- Exact documentation-inclusive handoff head `c536a8ff6c348b14b13f8185d1bd8d0e32b8ae62` passed Maven PR Tests run `31457388822` with the same clean verification, repeated 638-test suite, 33 skips, and 9-test JavaFX compliance result.
- Exact final PR head `cd9b3ab7984b55ad9615837b8be4580f0c4a0cba` passed Maven PR Tests run `31457659645` with 638 tests passing twice, 33 skips, and all 9 production JavaFX compliance tests passing.
- The owner completed and accepted `doc/P16-S15-fixed-asset-inventory-reports-user-testing.md`, and PR #267 merged to `main` at `ca2505fc4388211cd2dbf166ee29556d0f51f8df` on 2026-08-11.

Next exact action:

- None; P16-S15 is DONE and P16-S16 is active.

## P16-S16 — User role and assignment maintenance

Status: DONE through merged PR #268 and owner desktop acceptance.

Purpose: complete the durable administration implied by User Admin without claiming authentication enforcement.

Implementation steps:

1. Add stable-ID role list/create/edit/deactivate behavior with uniqueness and referenced-role protections.
2. Add explicit assignment revoke/end-date behavior and retain factual assignment history rather than deleting referenced records.
3. Validate user/role/company ownership, overlapping active assignments, last-required-admin policy if adopted, and company switching.
4. Add real Delete/Deactivate explanations consistent with the production UI rules.
5. Record factual audit events for role and assignment changes.
6. Label authentication and runtime authorization as deferred until a separate security phase is authorized.

Acceptance and tests:

- Roles and assignments can be created, changed, revoked/deactivated, reloaded, and audited without losing history.
- UI wording never implies that login or permission enforcement exists when it does not.

Implementation decisions and progress:

- `doc/administration/user-role-maintenance.md` governs global stable-ID users/roles and active-company assignment history. Authentication, login identity, and runtime permission enforcement remain explicitly deferred.
- V72 adds role lifecycle timestamps/status plus assignment start/end/revocation facts, replaces the one-row-per-user/company/role constraint with dated history identity, and enforces coherent active/end state without deleting existing rows.
- `UserAdminService` owns stable-ID user/role writes, active-reference deactivation guards, active-company assignment queries/mutations, interval-overlap rejection, immutable ended/revoked history, pessimistic revalidation, and same-transaction factual audit events.
- No last-administrator authorization rule is adopted because the application has no authenticated operator or effective permission consumer. Active users and roles still cannot be deactivated while any active assignments reference them.
- User Admin now provides real Users, Roles, and Company Assignments table/editor regions with dynamic global New/Save capabilities, ID-backed selectors, company-formatted dates, explicit End/Revoke operations, and a factual Authentication deferral tab.
- Owner desktop verification is governed by `doc/P16-S16-user-role-assignment-maintenance-user-testing.md`.
- Focused migration, service, rollback, restart, isolation, history, command-capability, and source-contract tests are being added. Local Maven remains unavailable in this container.
- Static local verification passes Java 17 grammar parsing for all 704 main/test sources, isolated JDK compilation for the five immutable command/usage records, and `git diff --check`. Full compilation and focused/full test execution remain pending GitHub Maven CI because this container has no `mvn` executable or resolved Java dependency cache.
- Exact local implementation commit `5860bf7afad2e867169787ce7a28cfb7d0dd07e0` contains the focused 22-file vertical slice. The worktree is clean at that implementation boundary; publication, draft PR creation, and authoritative CI require explicit owner authorization.
- The owner authorized publication. The two reviewed local trees were reconstructed through the connected GitHub service without `gh`: remote implementation commit `6083f40888bc611642111c66583ff61f95d1facd` has exact tree `284b5a0a0e028f20f197f320261adc39728058f4`, and remote handoff commit `22a547621a2ef1c16ebb4d0d500a65bc2f980097` has exact tree `49a316c26b976a839ea6a207df1ea02cd23fabba`; both trees match their local Git counterparts byte-for-byte.
- Draft PR #268 is open from `codex/P16-S16-user-role-assignment-maintenance` to `main` at exact remote head `22a547621a2ef1c16ebb4d0d500a65bc2f980097`. Authoritative Maven PR Tests and owner desktop acceptance remain pending.
- Initial Maven PR Tests run `31556972371` compiled production and ran 645 tests before exposing one V72 recovery defect and one metadata assertion defect: complete-schema Flyway-history recovery encountered existing V72 constraints, and the migration test assumed uppercase H2 metadata despite `DATABASE_TO_LOWER=TRUE`. The repeated suite and JavaFX compliance correctly did not run on that failed head.
- V72 now uses `ADD CONSTRAINT IF NOT EXISTS` for all three lifecycle constraints, preserving the repository's nondestructive complete-schema recovery contract, and the migration test compares H2 metadata case-insensitively. Exact corrected head `539aae6b0358ca2ec900cc1ac34ec0c46814bd9d` passed Maven PR Tests run `31557199558`: clean `mvn clean verify` and the deliberate repeated suite each ran 645 tests with 0 failures/errors and 34 skips; all 9 production JavaFX route/source compliance tests passed.
- Exact documentation-inclusive handoff head `75baea3b22003fa3b4cb4ed18bf7c8931690c0b0` passed Maven PR Tests run `31557505169` with the same clean verification, repeated 645-test suite, 34 skips, and all 9 production JavaFX compliance tests passing. Only owner desktop acceptance remains before merge.
- Exact final PR head `6988fe12225ee87e972d85cd46ee8b044297ac41` passed Maven PR Tests run `31557804893` with 645 tests passing twice, 34 skips, and all 9 production JavaFX compliance tests passing.
- The owner completed and accepted `doc/P16-S16-user-role-assignment-maintenance-user-testing.md`, and PR #268 merged to `main` at `3776bcd3df29550c4acf615fe6376375de3e29fc` on 2026-08-12.

Next exact action:

- None; P16-S16 is DONE and P16-S17 is active.

## P16-S17 — End-to-end closure

Status: DONE through merged PR #269 and owner desktop acceptance.

Purpose: prove the corrected interface-to-authority chain and close P16 only after production desktop acceptance.

Implementation steps:

1. Run a migrated-file H2 scenario covering depreciation, COA CSV rollback, reconciliation finalization, database switching, budgets, audit history, bank acceptance, inventory accounting, Journal cleared state, preferences, commands, and reports.
2. Repeat the scenario across restart and two companies, including injected late failures and closed-period/reconciliation protections.
3. Run every canonical navigation destination at laptop width and verify command enablement, scrolling, split state, tooltips, dirty-state guards, and factual status text.
4. Run full `mvn clean verify`, the deliberate repeated suite, JavaFX production-route compliance, and the owner desktop checklist on the exact final head.
5. Reconcile the phase index, all slice statuses, governing documents, and next action in a documentation-only closure commit if needed.

Acceptance:

- Every finding in the P16 audit table is either DONE with merged evidence or explicitly deferred by a new owner-approved plan amendment.
- No panel promises an operation that its authoritative service does not perform.
- P16 is not marked DONE until the final merged head and owner desktop checklist pass.

Implementation progress:

- `P16EndToEndClosureTest` now exercises one migrated file across two companies and restart, combining injected late COA rollback with depreciation, inventory accounting, stable budget activation, canonical bank/Journaling facts, finalized-reconciliation mutation protection, closed-period rejection, company-switched asset/inventory report catalogs, and factual audit durability.
- `ProductionPanelRouteComplianceTest` now composes every canonical route at 1280 × 800 and verifies each panel's declared command capabilities, factual unsupported-command response, company-owned table state, unconstrained resize policy, and sortable/resizable/reorderable columns.
- `doc/P16-S17-end-to-end-closure-user-testing.md` is the exact-head desktop acceptance checklist and maps every S17 contract to its authoritative focused test evidence. The owner completed and accepted that checklist.
- The closure audit reconciles stale governance text: the nonexistent `architecture/dashboard-composition.md` requirement is removed, authority inventories are current through active S17, and the COA JSON contract records the already-accepted/merged PR #197 state.
- Local implementation commit `a2e5fb03e0116e40bd4d3abeece8db1391c90d6e` contains the focused seven-file closure boundary. Both modified Java sources pass Java 17 grammar parsing, every test named by the closure evidence manifest resolves to an existing source, and `git diff --check` passes.
- The approved local implementation and plan-handoff trees were published without rewriting their content as remote commits `122a916350575c6f87265e5d63342bb27fadb403` and `1e0e075967445939a63488dc87c50a79f1c58728`; draft PR #269 targets `main`, and both remote tree SHAs exactly match the reviewed local trees.
- Exact plan-inclusive head `9fb7156c2422a372cb79436e63430e9f2e2b840d` passed Maven PR Tests run `31567353726`: clean `mvn clean verify` and the deliberate repeated suite each ran 646 tests with 0 failures/errors and 34 skips; all 9 production JavaFX route/source compliance tests passed. Owner desktop acceptance remained pending at that intermediate head.
- Exact final PR head `105afc559ac662b70e5d250753e5edef17be1f90` passed Maven PR Tests run `31567672108`: clean `mvn clean verify` and the deliberate repeated suite each ran 646 tests with 0 failures/errors and 34 skips; all 9 production JavaFX route/source compliance tests passed.
- The owner completed and accepted the P16-S17 desktop checklist, and PR #269 merged to `main` at `929ff7c80e191f5dd616197918dfa7daef3c75bc` on 2026-08-12.

Next exact action:

- None; P16-S17 and P16-C1 are DONE, P16-C2 is merged, and corrective P16-C3 is active.

## P16-C1 — Final plan-ledger reconciliation

Status: DONE through documentation-only PR #270.

Branch: `codex/P16-C1-finalize-plan-ledger`
Pull request: #270
Base head: `929ff7c80e191f5dd616197918dfa7daef3c75bc`

Purpose:

- Record the owner-confirmed P16-S17 desktop closure after product PR #269 merged with the slice still marked `VERIFYING`.
- Mark P16-S17 and P16 DONE only through a fresh documentation-only PR from current `main`.
- Reconcile the phase index, active front matter, P16 status, final CI evidence, merge evidence, and next action without changing product code.
- Leave P17 and all other later feature work unauthorized until an explicit owner-approved plan amendment defines it.

Validation status:

- PR #269 final head `105afc559ac662b70e5d250753e5edef17be1f90` passed all three repository gates in Maven PR Tests run `31567672108` before merging at `929ff7c80e191f5dd616197918dfa7daef3c75bc`.
- The owner confirmed the complete P16-S17 desktop checklist after that exact-head validation and merge.
- Activation commit `58c12cb6af4cc437b6c52827ad717f324d8ce1e8` is based on that exact merge and changes only `doc/PLAN.md`; `git diff --check` passes.
- The two approved local trees were published through the connected GitHub service as remote commits `ffc5dde86dc84fb41fbe2542aebd545134d0ebac` and `acad647994ff087d579ff2e66b23fc76dbf35d80`; their tree and `PLAN.md` blob SHAs exactly match local commits `58c12cb6af4cc437b6c52827ad717f324d8ce1e8` and `9979d0d6881f0a09dba3aa2a6bb5539c66ed0007`.
- Draft PR #270 targets `main` from `codex/P16-C1-finalize-plan-ledger` and contains only `doc/PLAN.md`.
- Exact plan-inclusive PR head `e7277ae987d4a508cecff60de584b1083b9d16a8` passed Maven PR Tests run `31611849457`: clean `mvn clean verify`, the deliberately repeated full suite, and production JavaFX route compliance all succeeded.
- Exact CI-evidence head `172422fcb9ea44f5f6f0c40294d6b48272413c21` passed Maven PR Tests run `31612424174`: clean `mvn clean verify`, the deliberately repeated full suite, and production JavaFX route compliance all succeeded.
- This closure slice changes only `doc/PLAN.md`; the exact completion-state head must pass authoritative PR validation before PR #270 merges these DONE records to `main`.

Next exact action:

- Merge PR #270 only after its exact completion-state head passes Maven PR Tests. No P17 or other later phase is authorized by the current plan.

## P16-C2 — Import Preview Eclipse constructor disambiguation

Status: DONE through PR #271.

Branch: `codex/P16-C2-import-preview-constructor-ambiguity`
Pull request: #271
Base head: `e659a9140c12cdf4c88449f58503b25b1bd57a47`

Purpose:

- Correct the Eclipse Java compiler ambiguity between the package-visible and private nine-argument `ImportPreviewPanel` constructors.
- Preserve the existing shell-owned SCLX preview and commit service composition without changing import behavior or data authority.
- Keep P16 closed except for this narrowly scoped source-compatibility correction; no P17 or later feature phase is authorized.

Implementation and validation status:

- Replace the two untyped forwarding lambdas with a named adapter returning the exact private `SclxPreviewOperationFactory` type, so overload resolution no longer depends on compiler-specific lambda target inference.
- Add a focused source guard that requires the typed adapter and rejects the ambiguous inline lambda form.
- PR #271 contains only the production constructor repair, focused regression guard, and plan handoff.
- Exact PR head `87c30c662877a7f55d9987bd9c7f173d38bec330` passed Maven PR Tests run `31620785858`, including clean `mvn clean verify`, the deliberately repeated full suite, and production JavaFX route compliance.
- PR #271 merged to `main` at `9286e99a751c832c9af09b90840005a36946890e` on 2026-08-12.

Next exact action:

- None; P16-C2 is merged and P16-C3 is active.

## P16-C3 — Bounded donor SCLX 1.3 compatibility

Status: DONE through merged PR #273; owner desktop SCLX acceptance continues under the later corrective slices.

Branch: `codex/P16-C3-donor-sclx-compatibility`
Pull request: #272
Base head: `9286e99a751c832c9af09b90840005a36946890e`

Purpose:

- Make the production SCLX preview and atomic import accept the bounded donor SCLX 1.3 dialect represented by the owner-supplied `company.sclx.json` without weakening strict JSON, version, size, identity, balance, or transaction-boundary checks.
- Normalize donor numeric epoch timestamps, account/fund aliases, date arrays, people/counterparty links, transaction references, statuses, and missing fund assignments into existing canonical authority.
- Preserve target currency and fiscal-year settings when absent from the donor document.
- Skip only zero-value noncanonical donor budget shells and workbook-only budget annotations, with visible warnings; non-zero donor budget data remains blocking.
- Require preview and the existing final confirmation to name every donor compatibility decision, including assignment of 32 fundless lines to the unique `General Fund` in the supplied document.

Implementation and validation status:

- A bounded parser-side compatibility normalizer keeps source byte count and SHA-256 exact while supplying canonical fields to the existing structure validator, preview, identity comparison, and atomic commit path.
- Donor people become canonical counterparties and their transaction links are preserved; transaction references are retained in canonical memos rather than discarded.
- The supplied document's 72 accounts, four funds, 58 people, 14 balanced transactions, 32 transaction lines, 13 nonblank references, and ten zero-value/noncanonical budget shells define the corrective acceptance boundary.
- Focused parser, preview, commit, and JavaFX confirmation regressions use a compact fictional donor fixture; the owner-supplied file is diagnostic evidence and is not committed as a repository fixture.
- Local implementation commit `53ae336fd4ce3058a2383d9965fc83bd541dee88` contains the governed compatibility boundary, contract update, compact fixture, and focused regressions.
- The JDK compiler module parsed all 707 production and test Java sources without syntax errors, the compact fixture passes strict JSON parsing, and `git diff --check` passes.
- Local Maven and standalone `javac` executables remain unavailable; authoritative type-checking and Maven PR Tests require publication.
- The owner explicitly authorized publication. GitHub remote commits `bbe883cd3b0ae56e5c68f4d73b048b2b6cb105ab` and `5e75ee604ca0fc781582b16145d132b4b52ab1b0` exactly match the implementation and plan trees of approved local commits `53ae336fd4ce3058a2383d9965fc83bd541dee88` and `30f233d535e08c74e75a05d661243dfbcd3626ba`.
- Draft PR #272 targets `main` from `codex/P16-C3-donor-sclx-compatibility` and is mergeable.
- Maven PR Tests run `31639828090` compiled the implementation and ran 653 tests, then found two fixture-contract failures: the donor compatibility fixture was incorrectly placed under the governed canonical `data-exchange/sclx/valid` directory, so it appeared both absent from the frozen manifest and invalid before compatibility normalization.
- Corrective local commit `ca165ff788a97d317d498ccfb018c4dabb460658` moves the fictional fixture to `src/test/resources/compatibility/sclx`, updates its focused consumers, passes strict JSON parsing and `git diff --check`, and preserves all 707 Java sources under Java 17 grammar parsing.
- Remote corrective commits `0c0ada8767ed75330bd6e209601dd3c460f9efd3` and `c350e984e55bd1893d2b8387487e7188144f7b69` exactly match local corrective trees `1542dd63a6c64ef7e4e9f0ead255b7daf7c66306` and `213b2b431abba89a36c275179838bc2dcb595f3f`.
- Exact corrected PR head `c350e984e55bd1893d2b8387487e7188144f7b69` passed Maven PR Tests run `31640303069`: clean `mvn clean verify` and the deliberately repeated suite each ran 653 tests with 0 failures/errors and 34 skips; all 9 production JavaFX route/source compliance tests passed.
- Exact final plan-inclusive PR head `a8165a3a42f695c15424b4e78bb0fb14fcc020e4` passed all three Maven PR Tests gates in run `31640809823`, and PR #272 merged at `8d2c18c3ae7cf58c8495911e75320216df87830e` on 2026-08-12.
- Owner desktop acceptance did not complete because later live-main merge `cc6679052132df03f16e054675063665959f097d` retained `ImportPreviewPanelSclxTest.confirmationNamesDonorCompatibilityAssignments()` while dropping both the production `sclxCompatibilityConfirmationText(...)` helper and its confirmation-dialog call. P16-C4 owns that narrow post-merge repair.

Next exact action:

- Complete and merge P16-C4, then resume owner desktop verification with the supplied SCLX before marking P16-C3 DONE.

## P16-C4 — Restore donor SCLX confirmation after merge

Status: DONE through merged PR #275.

Branch: `codex/P16-C4-restore-sclx-confirmation`
Pull request: #273
Base head: `cc6679052132df03f16e054675063665959f097d`

Purpose:

- Repair the exact source/test inconsistency on current `main` that produces Eclipse error `The method sclxCompatibilityConfirmationText(SclxImportPreview) is undefined for the type ImportPreviewPanel`.
- Restore the P16-C3 final-confirmation disclosure for donor compatibility decisions without reverting the owner's intervening `ImportPreviewPanel` reformat.
- Preserve the existing P16-C3 parser, preview, identity, and atomic-commit behavior; no new interchange scope is authorized.

Implementation and validation status:

- PR #272's tested merge commit contained the helper and its call, but later merge `cc6679052132df03f16e054675063665959f097d` selected the independently reformatted pre-P16-C3 panel body while retaining the new focused test.
- The repair reintroduces only the helper and confirmation-dialog concatenation in the current formatting style.
- The existing `ImportPreviewPanelSclxTest.confirmationNamesDonorCompatibilityAssignments()` remains the focused regression guard.
- Local implementation commit `3f743548a65a0b018325ebcf9687453d83cb7c76` contains the one-file production correction. All 707 production and test Java sources parse under the Java 17 compiler module, and `git diff --check` passes.
- Maven remains unavailable in this container; authoritative type-checking, focused execution, and the full suite require publication and Maven PR Tests.
- The two approved local trees were published through the connected GitHub service as remote commits `74d771128d9c6dffe116544e3180e33d41e629eb` and `592ac02ff91d03c8e8565bbf86427d94e15ca8c7`; their full tree SHAs exactly match local commits `3f743548a65a0b018325ebcf9687453d83cb7c76` and `1d05958c87bfc37c3560f7d973b1b1acf4e3e4a8`.
- Draft PR #273 targets `main` from `codex/P16-C4-restore-sclx-confirmation` at published handoff head `592ac02ff91d03c8e8565bbf86427d94e15ca8c7`.
- Plan-inclusive remote head `a2e107576d19ae10ecb0818840ba73760b92bd3f` compiled the restored method and ran 653 tests in Maven PR Tests run `31659531247`, then failed five pre-existing source-text guardrails because the intervening whole-file reformat changed indentation and line wrapping. The repeated suite and JavaFX compliance correctly did not run on that failed head.
- Owner-approved corrective commit `d23a2de90ff61aafeef31c101e2831c25aab4023` makes only those five code-shape assertions whitespace-insensitive while retaining exact visible-text assertions and forbidden-route checks. The compacted assertions resolve against current production source, all 707 Java sources parse under the Java 17 compiler module, and `git diff --check` passes.
- Remote corrective commits `22d190730c3c39beca083a313cb498bae331619a` and `636b9e641e6987c0c87c787098aa2be98825acf3` exactly match local corrective trees `874104029fdd9e8eec5f4fe6bd966bb25b4f6717` and `6368f09dc3ea8eb9055380ff4afd088c142f26f6`.
- Exact corrected PR head `636b9e641e6987c0c87c787098aa2be98825acf3` passed Maven PR Tests run `31659982583`: clean `mvn clean verify` and the deliberately repeated suite each ran 653 tests with 0 failures/errors and 34 skips; all 9 production JavaFX route/source compliance tests passed.

Next exact action:

- None; PR #273 merged to `main` at `913a056781a656e68cfc548c74b62ca3099c1906`.

## P16-C6 — Nondestructive SCLX import into an existing company

Status: DONE through merged PR #274; owner desktop SCLX acceptance continues under P16-C8.

Branch: `codex/P16-C6-sclx-existing-company-merge`
Pull request: #274
Base head: `913a056781a656e68cfc548c74b62ca3099c1906`

Purpose:

- Add the owner-requested option to import SCLX into an existing active company without replacing its identity, preferences, chart metadata, or existing account/fund records.
- Permit only the bounded safe target state represented by an existing company with chart/accounts, funds, and ordinary factual audit history but no competing operational history.
- Preserve the one-transaction SCLX authority and every existing closed-period, reconciliation, identity, and rollback protection.

Planned deliverables:

- Split target occupancy into chart/fund/audit facts versus transactions, budgets/categories, transaction-linked masters, banking/reconciliation, assets, inventory, and period-close history.
- Project each account/fund as `CREATE`, direct identical reuse, explicit compatible `MAPPED` reuse, or blocking conflict/unresolved state.
- Offer compatible target choices for a same-code collision, apply the choices through a fresh non-mutating preview, and require both explicit approval of the final displayed mappings and separate consent to import into the existing company while preserving its settings.
- Re-read the exact source and mapping set at commit, preserve target organization/chart settings, reuse approved accounts/funds, create missing masters, record durable identities, and add the remaining graph atomically.
- Continue to block destructive replacement and any merge into a target with competing operational history.
- Add focused preview, commit, JavaFX, governing-document, and owner desktop verification coverage.

Validation status:

- Implementation commit `113b843c0274e19e79e973215b59fc6244c75f1a` adds the bounded target-state projection, create/reuse mapping choices, compatible target selection, two explicit approvals, atomic mapped-master reuse, target-setting preservation, durable identity/audit details, and focused preview/commit/JavaFX/source coverage.
- All 708 production and test Java sources parse under the Java 17 compiler module; the three new/changed dependency-free mapping projection types type-compile; the updated production-route source assertions match the five-argument guarded commit; and `git diff --check` passes.
- Local Maven and a JDK executable are unavailable in this container. Authoritative production/test type-checking, focused H2/JavaFX execution, and `mvn clean verify` therefore run in Maven PR Tests.
- The approved implementation and handoff trees were published through the connected GitHub service as remote commits `3752201480113a7a246a1f85f84a914d949c4b9a` and `89b8a924abe55712433225d31a01435c820deec2`; their full tree SHAs exactly match local commits `113b843c0274e19e79e973215b59fc6244c75f1a` and `b6a445470e9135c993b1c474cf5ab46ca2891141`.
- Draft PR #274 initially failed Maven PR Tests run `31767108927` during production compilation because the test-only `ImportPreviewPanel` constructor's nested lambda was ambiguous between the supplier and operation-factory overloads.
- Owner-approved local correction `aa4f7dace29c26afb76ec29a1963a0e2d022ec57` explicitly types that one constructor argument as `SclxPreviewOperationFactory`. Remote correction `33381e8a460c8be0300fb6ed1567f0dba8e27d37` has the exact same full tree SHA.
- Exact corrected PR head `33381e8a460c8be0300fb6ed1567f0dba8e27d37` passed all three Maven PR Tests gates in run `31767335496`: clean `mvn clean verify` and the deliberately repeated suite each ran 657 tests with 0 failures/errors and 35 skips; all 9 production JavaFX route/source compliance tests passed.
- Owner desktop verification is documented in `doc/P16-C6-sclx-existing-company-import-user-testing.md` and remains pending.
- PR #274 merged to `main` at `ec64a0ddd50759686542a6739d127df45b1a7974` after its exact corrected head passed all Maven PR Tests gates.

Next exact action:

- None; the owner-reported global ownership preview/commit mismatch is isolated to P16-C7.

## P16-C7 — Actionable company-ownership blockers and audited repair

Status: DONE.

Branch: `codex/P16-C7-ownership-diagnostics-repair`
Pull request: #275
Base head: `ec64a0ddd50759686542a6739d127df45b1a7974`

Purpose:

- Correct the owner-reported preview/commit mismatch where a newly created target displayed only donor warnings, then import failed on 14 global ownership diagnostics beginning with `ACTIVITY 1`.
- Surface every open ownership diagnostic as an actionable blocking SCLX preview message, with the selected message's resolution kept visible without requiring horizontal scrolling or truncated hover text.
- Add an explicit audited Administration repair for direct ownerless legacy records without bulk guessing, destructive replacement, or cross-company reference rewrites.

Implementation and validation status:

- Corrective implementation commit `1f91b7a` adds the missing preview gate, selects the first blocker into a persistent resolution subpane, and adds **Administration -> Company Ownership Diagnostics** with transactional per-record assignment, active-company validation, actor/reason evidence, confirmation, rollback, and audit coverage. Cross-company conflicts remain read-only and actionable rather than being silently dismissed.
- Focused service, preview, JavaFX, and source-level regressions cover actionable blocker projection, direct `ACTIVITY` repair plus audit, cross-company repair rejection, Administration reachability, and selected-message resolution visibility.
- The JDK compiler module parses all 710 production and test Java sources under Java 17 grammar with no syntax errors, and `git diff --check` passes. The ordinary `javac` launcher, Maven, and resolved Maven dependency cache remain unavailable, so semantic compilation and runtime tests require Maven PR Tests.
- Handoff commit `9b960a5` records the revised owner desktop verification step, and governance commit `1daa059` records that authenticated connected-GitHub publication is valid without the optional `gh` CLI.
- The owner explicitly authorized publication. This branch was created from the live `main` head after PR #274 merged, and the three reviewed commits were cleanly replayed without changing their patches.

Next exact action:

- None; PR #275 merged to `main` at `2f0bf9bf4a5520059e43b8e404c3807ccb312dc8`, and the owner-selected target-company correction continues under P16-C8.

## P16-C8 — SCLX import-target authority for direct ownerless records

Status: DONE through merged PR #276.

Branch: `codex/P16-C8-sclx-target-company-authority`
Pull request: #276
Base head: `2f0bf9bf4a5520059e43b8e404c3807ccb312dc8`

Purpose:

- Treat the active company selected for SCLX import as the operator's authoritative assignment for a direct ownerless record; do not ask the operator to infer a distinct historical company.
- Keep structural cross-company reference conflicts blocking because assigning a row cannot safely rewrite incompatible accounting links.
- Permit an Activity assigned to the target company to coexist with existing-company import, reuse an identical code/name/active record, and attach the incoming durable SCLX identity instead of creating a duplicate.
- Retain the exact previewed SCLX path and provide **Re-preview Same SCLX** after ownership repair.

Implementation and validation status:

- Company Ownership Diagnostics now defaults to the active company receiving the import and describes the required reason as an audit note, without historical-owner language.
- Target occupancy excludes Activity master rows from competing operational history. Preview reports compatible Activity reuse as `SCLX_TARGET_ACTIVITY_REUSED`; a same-code record with different name or active state remains a blocking `SCLX_ACTIVITY_CODE_CONFLICT`.
- Atomic commit revalidates the compatible Activity under the same target company, reuses it, records its SCLX identity, and continues to reject transactions, budgets, banking, assets, inventory, reconciliation, close history, and other existing operational families.
- Import Preview exposes **Re-preview Same SCLX** and reuses the normalized exact source path plus any applied account/fund selections.
- Focused preview, commit, ownership-service, Administration-source, and Import Preview production-route regressions cover the correction. All 710 production/test Java sources pass Java 17 grammar parsing, the changed dependency-free SCLX snapshot/preview records type-compile through the JDK compiler module, source guards and `git diff --check` pass. Local semantic Maven execution remains pending because Maven and the standalone `javac` launcher are unavailable in the current workspace.
- Local implementation commit `8d12385af178972193f9f413e89ec760b799d44f` contains the reviewed production, regression, contract, checklist, and plan activation boundary on current `main` merge `2f0bf9bf4a5520059e43b8e404c3807ccb312dc8`.

Next exact action:

- None; PR #276 merged to `main` at `ca0a50ea9670a91e46ef5093d4ae2bd70bd0369e` after exact head `3fed37069e2b5c6670363a6fa3f4f4a21c8ef1cf` passed Maven PR Tests run `31861442762`.

## P16-C9 — Identity-aware SCLX operational merge

Status: DONE.

Branch: `codex/P16-C9-target-chart-correction`
Pull request: #279
Base head: `11530b5314df4e5e8359f849a78343a801704760`

Purpose:

- Permit an SCLX import into a target that already contains unrelated operational history.
- Classify each durable identity as `IDENTICAL`, `NEW`, or `CONFLICT`: skip identical records, import new records, and require an explicit per-record target-versus-SCLX choice for each resolvable conflict.
- Preserve the target company's existing records and settings and retain company ownership, closed-period, finalized-reconciliation, business-key, and rollback protections.

Planned deliverables:

- Remove the coarse target-occupancy preview and commit guards.
- Permit mixed identical/new identity graphs, reuse identical records during the same atomic commit, and project a record-by-record conflict selector whose final choices are revalidated at commit.
- Keep structurally unsafe choices unavailable when they would rewrite cross-company ownership, a closed period, or finalized reconciliation history, with an actionable reason shown in preview.
- Add focused preview, commit, rollback, documentation, and owner-checklist coverage.

Implementation and validation status:

- Local implementation commit `c1dd824` removes the coarse operational-occupancy preview and commit gates, reuses identical imported records across the supported graph, adds new records beside unrelated target history, and extends banking, reconciliation, period-close, and audit service seams with existing-record maps for incremental import.
- `CONFLICT` rows now require an explicit record-level **A — Keep target** or **B — Take SCLX** choice. Source-winner mutation is enabled for Activity, counterparty, and merchant masters; protected accounting-history records keep the target choice available and explain why source replacement is unsafe.
- Commit re-previews the exact source with the approved mapping and conflict-choice sets, compares the full entity projection, revalidates local ownership/references, advances a selected source identity hash only with the corresponding local record, and retains one-transaction rollback.
- Focused regressions cover removal of the operational-presence blocker, import beside unrelated target audit/budget/period-close history, mixed identical/new graph reuse, Activity target/source winner projection, Activity source-winner commit, and production-route conflict controls.
- All 712 production and test Java sources parse under the Java 17 compiler module; the four dependency-free conflict/preview projection sources type-compile; focused source assertions and `git diff --check` pass. Maven and the standalone `javac` launcher are unavailable locally, so semantic compilation and runtime/H2/JavaFX tests require Maven PR Tests.
- The reviewed identity-aware merge implementation and handoff trees were published through the connected GitHub service as two commits on PR #277. Maven PR Tests run `31901329323` passed on that published head, and PR #277 subsequently merged to `main` at `771a152081f77a61656fe1d9c41a5dc299f1c4f0`.
- Owner desktop acceptance then confirmed that SCLX commit reported imported records but an already-open Journal tab retained its pre-import rows. The canonical commit path still persisted a `Txn`, and `TransactionEntryService.search(...)` could query it; the defect was that direct reusable-tab selection updated only `PanelHost.activeId` and did not invoke the selected panel's `onPanelShown()` refresh hook.
- Local correction commit `74f7719` invokes `onPanelShown()` whenever an existing tab is selected, avoids duplicate refresh when `PanelHost.show(...)` itself changes selection, updates the import success guidance, and adds both a JavaFX tab-lifecycle regression and an SCLX-to-`TransactionEntryService.search(...)` integration assertion. The correction was rebased onto merged PR #277 as remote commit `22605f44f5dd57bf96a2bd806593bc969f1bedaf` on fresh draft PR #278. All 712 Java sources parse under the Java 17 compiler module and `git diff --check` passes; Maven execution remains unavailable locally.
- Draft PR #278 head `e16224dc3c220f4ef4e2b5cf8b15259e619be67d` passed Maven PR Tests run `31909872616`, and PR #278 subsequently merged to `main` at `11530b5314df4e5e8359f849a78343a801704760` before the next owner desktop import attempt.
- That attempt rolled back with `ChartOfAccounts.name` null when the populated target had no active chart. Existing-target preservation caused `targetChart(...)` to create a chart but skip the metadata initialization intended to protect a genuinely existing chart.
- Local correction commit `11ec206` distinguishes a newly created target chart from an existing chart, assigns the new chart's required governed name/version/owner/status before account writes, and retains target-setting preservation only for a chart that already exists. A focused H2 regression covers a populated audit-history target with no active chart through successful canonical transaction import. The correction was rebased onto merged PR #278 as remote commit `6957496a1abd68f8af853eb0c3b04fff4034d10b` on fresh draft PR #279. All 712 Java sources parse under the Java 17 compiler module and `git diff --check` passes; Maven execution remains unavailable locally.

Next exact action:

- None; PR #279 merged to `main` at `2386d6e37c6f5226f2dbb7813036ce291e08a8a7`.

## P16-C10 — SCLX donor-export reconciliation and preview resolutions

Status: DONE through merged PR #280.

Branch: `codex/P16-C10-sclx-preview-resolutions`
Pull request: #280
Base head: `2386d6e37c6f5226f2dbb7813036ce291e08a8a7`

Purpose:

- Produce a clean canonical import from the owner-supplied workbook exporter without representing the
  assumed Cash/Asset counter-line as another transaction.
- Make preview diagnostics reviewable in a two-column message/disposition table and carry applied
  choices through fresh preview and exact-source commit revalidation.
- Make every safely selectable SCLX account/fund target an explicit combo-box choice and keep complete
  mapping detail visible through wrapping plus normal table scrolling.
- Retain strict canonical accounting, ownership, closed-period, finalized-reconciliation, and atomic
  rollback rules; a preview disposition cannot bypass a canonical invariant that has no safe correction.

Planned deliverables:

- Accept the donor `REVENUE` account alias as canonical `INCOME`, skip workbook annotation rows that
  contain no nonzero posting lines, and expose unsupported donor records individually so they may be
  deliberately dropped without silently discarding a whole section.
- Add immutable SCLX message-disposition selections for **No change**, **Ignore**, **Make suggested
  correction**, and **Drop record**. Re-preview applies only policy-supported choices, records the
  effective choices in the preview, and commit reuses the same choices against the unchanged source.
- Replace the Import Preview message list with a message/disposition table, retain full-text selection
  detail, and use combo-box mapping cells for alternate compatible target choices including create-mode
  defaults.
- Publish a revised standalone workbook `.bas` exporter that attaches one generated Cash line to the
  owning transaction, emits `INCOME`, omits empty budgets and unsupported template-only sections,
  bounds transactions to the reporting period, and emits transaction-linked supplemental details only
  where the workbook provides a resolvable ledger reference.
- Add focused preview, commit, donor-normalizer, JavaFX behavior/source, exporter fixture, governing
  contract, and owner desktop-check coverage.

Validation status:

- Current `main` through merged PR #279 is the base. The prior P16-C9 task branch was not reused.
- Local implementation commit `5101646` adds bounded preview-message dispositions, exact-source commit
  reapplication, donor normalization/record paths, alternate compatible mapping targets, the two-column
  message table, and wrapped mapping details with focused parser, preview, commit, JavaFX, and source
  regressions.
- The revised v14 workbook bridge contains 111 paired VBA procedures with no duplicate procedure names;
  its one generated Cash/Asset balancing line is appended only to the owning transaction's line array.
  The clean reference product contains 61 balanced in-period transactions, 13 accounts, 2 funds, 16
  counterparties, and 2 linked supplemental details, with no unresolved references, `REVENUE` types, or
  populated unsupported donor sections.
- All 715 production and test Java sources parse under the Java 17 compiler module, the two new
  dependency-free disposition types compile, the exporter/export-product invariant checks pass, and
  `git diff --check` passes. Maven and the standalone `javac` launcher are unavailable in this workspace,
  so semantic compilation and the H2/JavaFX/full suite require Maven PR Tests after owner-authorized
  publication.
- The revised `.bas` and clean `.sclx.json` artifacts are preserved outside the Git-backed worktree.
- Draft PR #280 published the exact approved branch, and head
  `69c792bd81d53a2a3179080c3abad743b912556f` passed Maven PR Tests run `32084588634`.
- PR #280 merged to `main` at `8838bc7a0f8d6092cb33e1801558129bf5eac2be`.

Next exact action:

- None; the later owner-reported native counterparty portable-identity collision is isolated to P16-C11.

## P16-C11 — Native SCLX portable-identity reconciliation

Status: DONE through merged PR #281.

Branch: `codex/P16-C11-native-portable-identity-reconciliation`
Pull request: #281
Base head: `8838bc7a0f8d6092cb33e1801558129bf5eac2be`

Purpose:

- Correct the owner-reported atomic rollback where the target already owns counterparty portable UUID
  `b6b3cbc4-c5bf-3178-8330-e492d5b0f5b6`, but the source-scoped `interchange_identity` row is absent.
- Reconcile native portable UUID authority during preview instead of classifying the incoming record as
  new and discovering the collision through `uq_counterparty_portable_id` during commit.
- Preserve record-level target/SCLX conflict choice, company isolation, exact-source re-preview, and the
  one-transaction rollback boundary.

Implementation and validation status:

- Current `main` through merged PR #280 is the base; its merged branch was not reused.
- Implementation commit `80ffd6b` adds bounded native UUID lookup for every SCLX entity family with an
  intrinsic portable identity, while querying only the UUIDs present in the current normalized source.
- An identical same-company counterparty or merchant is reused and receives the missing source identity;
  differing master content becomes the existing record-level **Keep target** / **Take SCLX** conflict.
  Protected accounting-history types retain only the safe target winner, and a UUID owned by another
  company remains blocking.
- Commit rechecks the native UUID, local record ID, and company owner inside the import transaction before
  any business write. A native master source winner records a new source identity rather than calling the
  update-only source-conflict path that requires an existing identity row.
- Focused preview regressions cover identical reuse, differing master conflict, and cross-company
  ownership. H2 commit regressions cover exact reuse plus missing-identity creation and the native
  **Take SCLX** master update without a duplicate insert.
- All 716 production and test Java sources parse under the Java 17 compiler module; the changed
  dependency-free snapshot/preview records type-compile, and `git diff --check` passes. Maven and the
  standalone `javac` launcher remain unavailable locally, so semantic compilation and focused/full H2
  execution require Maven PR Tests after publication.
- The owner authorized publication after confirming PR #280 merge state. Connected-GitHub remote
  implementation commit `add3002e16c8247535eca729d4d6481cd168c57c` and handoff commit
  `adf59267b7ab7b6faf1c8bb326f93af1f798d8c3` exactly match local trees `09a71654` and `a8c47aca`.
- Draft PR #281 targets `main` from the fresh P16-C11 branch.
- Maven PR Tests run `32089615271` compiled the implementation and ran 681 tests, then exposed nine H2
  integration errors because this Hibernate/native-query path returns UUID columns as 16-byte arrays
  rather than `java.util.UUID` values. The repeated suite and JavaFX gate correctly skipped after failure.
- Corrective local commit `9aa4a33` decodes both native `UUID` objects and H2 16-byte UUID values without
  changing portable-identity derivation. Connected-GitHub commit
  `8ae4d07a004189e159613fa8e6395b3cb9cb6d91` exactly matches local corrected tree `b1f0c3ac`.
- Exact corrected plan-inclusive head `7039f06f14b44fdd73db1d7781803612c0da58de` passed all three
  Maven PR Tests gates in run `32089850888`: clean `mvn clean verify`, the deliberately repeated test
  suite, and production JavaFX route compliance.
- Final PR head `bb0b53dbd4407c7a7f6493d77a40e95609967613` passed Maven PR Tests and PR #281
  merged to `main` at `3e95d42f56800b729cc01c6b7d3741c3b0345fb6`.

Next exact action:

- None; P16-C11 and P16 are DONE. The explicit owner-requested Report Library correction proceeds under
  P11-C1.
