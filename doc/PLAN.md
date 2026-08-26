---
plan_version: 216
active_phase: P17
active_slice: P17-C2
active_status: VERIFYING
active_branch: codex/P17-C2-durable-record-lifecycle
active_pull_request: 294
active_head: 32916efa63122d3ce7d0fd8b00b8c1e57711bbf9
next_action: "Verify GitHub Maven PR Tests on the final documentation-record head, then complete the owner durable-account lifecycle checklist and stop before merge until owner acceptance."
plan_version: 215
active_phase: P17
active_slice: P17-C1-C1
active_status: VERIFYING
active_branch: codex/P17-C1-C1-plan-reconciliation
active_pull_request: 293
active_head: published_pr_293
next_action: "Keep draft PR #293 unchanged after final green CI and stop before merge. P17-C1 remains VERIFYING until the owner explicitly accepts the desktop checklist."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic pre-P17-C2 ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

This revision reconciles repository state that the prior controller had not caught up with:

- P05-C6 is DONE through merged PR #285 and corrective merged PR #286; its obsolete `IN_PROGRESS` controller declaration is removed.
- P11-C2 is DONE through merged PRs #283 and #284 plus owner acceptance.
- P16 is DONE through corrective P16-C11 / merged PR #281.
- P17-C1 is DONE through merged PR #291 and CI-correction PR #292; the P17-C2 starting `main` is `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- P17-C2 is the active owner-authorized corrective slice on fresh branch `codex/P17-C2-durable-record-lifecycle`, published as draft PR #294.

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
| P05 | Banking configuration and statement import | DONE through P05-C6 / PR #285 plus corrective PR #286 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE |
| P11 | Report Library | DONE through P11-C2 / PR #284 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | UI design-rule and durable-record lifecycle corrections | P17-C1 DONE; P17-C2 VERIFYING |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Required for P17-C2:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/administration/fund-lifecycle.md`
- `doc/administration/company-lifecycle.md`
- `doc/administration/user-role-maintenance.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/banking/banking-and-reconciliation.md`
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

Status: DONE through corrective P05-C8 / merged PR #289. PR #290 records the owner's explicit direction that P05-C8 is DONE.

#### P05-C6 — ASSET / BANK / CASH account classification

Status: DONE through merged PR #285 and corrective PR #286.

Completion evidence:

- PR #285 introduced the accounting correction that models deposit accounts as `ASSET / BANK / CASH / DEBIT`, including the nondestructive V73 migration and portable BANK compatibility.
- PR #286 corrected post-merge SCLX/SCA-COA fixtures, the explicit Function=None editor behavior, and configured-bank declassification guards.
- PR #285 merged at `84d2969ce687e13d7460ed320188c36c582dd4f8`; corrective PR #286 merged at `18299ead1014a568b7fc6dd27bcc514a38a3d5b1`.
- Owner verification remains documented in `doc/P05-C6-asset-bank-cash-classification-user-testing.md`.

Next exact action:

- None; P05-C6 is DONE.

#### P05-C7 — Configured bank-account edit/update

Status: DONE through merged PR #287.

- Existing `CompanyBankAccount` rows are updated by stable identity rather than duplicate insert, with controlled duplicate-link validation, explicit new/edit modes, active-company opening-date/money formatting, and safe deactivation behavior.
- PR #287 merged at `5f17a29e7bcaffbbd09919bc009b5f83569c2cd0`.
- Owner verification remains documented in `doc/P05-C7-configured-bank-account-edit-user-testing.md`.

Next exact action:

- None; P05-C7 is DONE.

#### P05 follow-up — Administration bank-account summary

Status: DONE through merged PR #288.

- Administration exposes a read-only **Bank Accounts** tab backed by the existing `BankConfigurationService`; creation/editing remains owned by Banking and no parallel bank-account model was introduced.
- PR #288 merged at `d2449d884023d8301eb2bd3c5a2741b00c807cd7`.

#### P05-C8 — Canonical bank ledger activity versus statement review

Status: DONE through merged PR #289 by explicit owner direction recorded in PR #290.

- **Ledger Activity** is the primary Bank Transactions accounting view over configured-bank `TxnSplit` facts, while imported durable rows remain separately under **Statement Review**.
- PR #289 merged at `b099b763c34ebdbe3b5d0bbea15459c688d28836`. Its published Maven run was not green; this ledger preserves that historical fact. PR #290 records the owner's subsequent direction to mark C8 DONE, and later exact-current-code Maven PR Tests passed after P17-C1 corrections.
- Owner verification remains documented in `doc/P05-C8-canonical-bank-ledger-activity-user-testing.md`.

Next exact action:

- None; P05-C8 and P05 are DONE.

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

## 5. Established product decisions retained by P17-C2

- One production JavaFX application and one H2 accounting authority.
- Existing JPA/Hibernate model and Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, import stores, record stores, or prototype maintenance paths.
- Disabled placeholder Delete buttons are not part of the UI contract. Delete is shown only for a real governed operation.
- Durable accounting/operational records that must survive history use deactivation, disposition, correction, or reversal rather than an invented generic hard-delete operation.
- Funds are maintained by stable database ID; referenced funds are deactivated and only demonstrably unused funds may use their existing protected delete operation.
- Companies use stable database ID and inactive lifecycle when referenced.
- Journal transactions retain the governed correction/reversal lifecycle.
- Fixed assets retain governed lifecycle events/disposition/reversal; Inventory retains governed movement/reversal semantics.
- User/role administration retains stable IDs and dated assignment/end/revoke semantics.
- Banking retains its stable bank/configuration record IDs and existing protected account-classification rules.
- Chart-of-Accounts JSON, COA CSV, SCLX, and Banking auto-create operations retain their governed batch/compatibility seams; P17-C2 changes the interactive single-account editor identity rule, not interchange contracts.

## 6. P17 execution ledger

### P17-C1 — UI design-rules implementation correction

Status: DONE.

Merged evidence:

- PR #291 merged at `0e61ba578ec9a478424ab4206b140334c63ada9a`.
- PR #292 merged at `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1` after the CI-driven migration-number, bank-account timestamp-identity, and active-period corrections.

Next exact action: none; P17-C1 is merged and must not be reopened for P17-C2 work.

### P17-C2 — Durable record lifecycle completion

Status: VERIFYING.

Branch: `codex/P17-C2-durable-record-lifecycle`  
Starting base: `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`  
Pull request: #294 (draft)  
Validated implementation head: `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9`

Purpose:

- Correct interactive Chart of Accounts maintenance so an account's durable database ID, not its editable account code, selects the row being updated.
- Ensure an account code change updates one existing row and preserves ledger, Banking, report, import-identity, and other durable references to that account.
- Enforce active-company/active-chart ownership and chart-scoped code uniqueness on stable-ID saves.
- Preserve existing Banking classification protections and parent-account validation.
- Retain **Active** as the account retirement lifecycle. Do not invent a generic Delete operation for accounts that participate in accounting history.
- Audit the remaining durable editors for lifecycle consistency without changing already-governed Journal, Funds, Company, Asset, Inventory, Banking, or User semantics.

Completed deliverables:

- Added immutable `AccountCommand` with nullable durable ID.
- Added `AccountAdminService.save(AccountCommand)` so create uses null ID and update requires the exact account ID; code-addressed `upsert` remains only as a compatibility/batch/auto-create boundary.
- Added active-company/active-chart ownership validation, duplicate account-code rejection excluding the edited ID, parent membership/self-parent protection, BANK classification validation, and the existing configured-Banking protection on stable-ID saves.
- Updated `ChartOfAccountsPanel` to retain `editingAccountId`, clear it for New, construct `AccountCommand`, save through the stable-ID service path, and reselect by ID rather than code.
- Added visible lifecycle guidance: clearing Active and saving retires an account while preserving history; no Delete placeholder is added.
- Added unit/H2 integration/UI-source regression coverage for stable ID, durable Banking-reference preservation, duplicate-code rollback, self-parent prevention, form-state identity, New identity clearing, ID-based reload, and absence of a Delete placeholder.
- Updated the existing core-editor compliance fixture for the new FormState identity field found during the pre-publication source audit.
- Updated `doc/interface-operation-matrix.md` with stable-ID account authority and the retained lifecycle audit for Journal, Funds, Company/User, Banking, Asset, and Inventory editors.
- Added `doc/P17-C2-durable-record-lifecycle-user-testing.md` for owner acceptance.
- Preserved the prior 3,288-line execution ledger byte-for-byte as `doc/archive/PLAN-pre-P17-C2.md` rather than discarding it while replacing its stale active controller.

Validation status:

- Repository state was verified against exact current `main`; no pre-existing P17-C2 branch or open PR existed before this run.
- Draft PR #294 was opened against exact base `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- Maven PR Tests run `32921328550` on validated implementation head `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9` PASSED on 2026-08-25/26: clean headless verification, repeated test suite, and production JavaFX route compliance all succeeded.
- Local Maven execution is unavailable in the connected GitHub-only runtime; no local Maven result is claimed.
- This PLAN record is a documentation-only head change. GitHub Maven PR Tests must also pass on that final head before owner acceptance; the final run/head may be recorded in the PR without creating another recursive documentation-only CI head.

Known failures:

- None on validated implementation head `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9`.

Owner acceptance:

- Follow `doc/P17-C2-durable-record-lifecycle-user-testing.md` after final-head CI is green.
- Do not merge until the owner accepts the desktop checklist.

Next exact action:

- None; P16-C11 and P16 are DONE. The explicit owner-requested Report Library correction proceeds under
  P11-C1.

# P17 — Cross-cutting production UI design-rule compliance

**Selector:** `PHASE=P17`
**Status:** P17-C1 VERIFYING after merged PRs #290, #291, and #292
**Depends on:** P01 through P16

Purpose: apply the remaining shared production JavaFX formatting, dialog, period-boundary, and regression-policy corrections discovered after P05-C8 without reopening completed accounting authorities or introducing parallel UI/service paths.

## P17-C1 — UI design-rules compliance

Status: VERIFYING. The implementation is merged and the exact final code head passed CI; owner desktop acceptance is not yet recorded in this ledger.

Delivery and validation:

- PR #290 opened the authorized cross-cutting UI compliance slice and merged at `272a9b4ee530712189ac8a127a9e61273065a670`.
- PR #291 merged the shared company-aware money normalization, dialog compliance helper, Fixed Asset lifecycle formatting corrections, and configured active-period calculation at `0e61ba578ec9a478424ab4206b140334c63ada9a`.
- PR #292 resolved the duplicate Flyway version, normalized `CompanyBankAccount` audit timestamps to database microsecond precision, strengthened stable-identity coverage, and aligned the Period Close source guard. It merged at `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`: clean headless verification, the deliberately repeated full test suite, and production JavaFX route compliance all succeeded.
- The merge commit has no file differences from that tested PR head.
- Owner verification remains defined by `doc/P17-C1-ui-design-rules-compliance-user-testing.md`; merge plus automated CI is not treated as owner desktop acceptance.

Next exact action:

- Complete and explicitly accept the P17-C1 owner desktop checklist. Do not begin the later durable-record lifecycle work merely because P17-C1 implementation is merged.

## P17-C1-C1 — Plan-ledger reconciliation after merged P17-C1 corrections

Status: VERIFYING.

Branch: `codex/P17-C1-C1-plan-reconciliation`
Base head: `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`
Pull request: #293 (draft)

Purpose:

- Repair plan version 214, whose front matter still described P05-C6 as unpublished and stated that P17 was unauthorized after PRs #285-#292 had already merged.
- Reconcile the phase index and P05/P17 records to current repository history without changing product code.
- Preserve the distinction between automated CI completion and the still-unrecorded P17-C1 owner desktop acceptance.

Validation status:

- This reconciliation changes only `doc/PLAN.md` from exact current `main`.
- Draft PR #293 is open from exact current `main`, and its current comparison changes only `doc/PLAN.md`.
- Maven PR Tests are green for draft PR #293, including clean headless verification, the repeated full test suite, and production JavaFX route compliance; the final PR head must remain unchanged after confirmation.
- No local Maven result is claimed because the execution container cannot resolve GitHub and has no usable local repository checkout.

Next exact action:

- Keep draft PR #293 unchanged after final green CI and stop before merge; owner desktop acceptance for P17-C1 remains separate.
