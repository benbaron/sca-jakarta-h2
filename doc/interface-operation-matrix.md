# Interface operation matrix

Status: P00 inventory updated through active P11-S1 typed Report Library work. This document records visible operations and data authority so later phases can replace placeholders without rescanning the whole UI.

## Scope and evidence

- `AppPanelId` retains stable identifiers, but `LEDGER_REGISTER` and `TXN_EDITOR` are retired compatibility aliases normalized to canonical `JOURNAL_PANE`.
- `PanelHost` creates one reusable workspace tab per canonical destination. Requests for either retired P03 alias select the same Journal tab.
- `NavigationPane` exposes one **Journal** destination under Accounting rather than separate Ledger Register, Transaction Editor, and Inspect Journal items.
- `PanelFactory` routes the Journal destination to `JournalWorkspaceCompliancePanel`, which delegates accounting behavior to `JournalWorkspacePanel` and applies company UI formatting/state/layout.
- Global commands route from `MainWindow`/toolbar/menu through `PanelHost` to `AppPanel` hooks.
- `UiServiceRegistry` creates JPA-backed lookup, admin, report, reconciliation-workspace, and period-close-range services, plus compatibility JDBC repositories for legacy records.
- `ReportLibraryPanel` uses a typed catalog and one immutable request for preview, export, and Journal drill-through.
- `UiWorkspaceDataStore` and sidecar/static stores remain active only for unfinished import/export and bank-transaction surfaces; former Schedules, asset/depreciation runbook, and inventory runbook sidecars were removed.

## Global commands

| Command/source | Visible control | Query source | Write source | Survives restart | H2 authoritative | Placeholder/sidecar risk | Owning phase |
|---|---|---|---|---|---|---|---|
| New | menu/toolbar button | active `AppPanel` | active panel `onNew` | panel-dependent | panel-dependent | generic dispatch hides whether command is genuine | P01/P03 |
| Save | menu/toolbar button | active `AppPanel` | active panel `onSave` | panel-dependent | panel-dependent | unified Journal delegates to `TransactionEntryService`; other panels remain panel-dependent | P01/P02/P03 |
| Copy/Paste | menu/toolbar button | active selection | active panel hooks | no accounting persistence | no | mostly UI clipboard/status behavior | P01 |
| Find/Journal/command runner | toolbar/global shortcuts | active panel selection and services | active panel `onRunCommand` | panel-dependent | panel-dependent | text-command dispatch should become typed commands | P01 |
| Import/Export | main-window file actions | CSV/OFX/QIF services and session state | `UiWorkspaceDataStore` job/bank caches; some COA import writes H2 | partially | mixed | job log and bank transactions are session/static, not canonical import persistence | P05/P13 |
| Database wizard/switch | main-window database actions | `DatabaseLocationService`, `DatabaseMigrationService`, `UiServiceRegistry` | H2 file/JPA resources | yes | yes after successful switch | switching and recovery need atomic composition review | P01/P12 |

## Delete operation rule

Do not add disabled placeholder Delete buttons. A durable-record panel may expose a real Delete action only when the authoritative service supports the action and protection rules. Otherwise, explain non-deletable records through status/help text or documentation rather than as a disabled button.

## Completed-phase UI design-rule updates

`doc/ui_design_rules.md` applies to every panel below. Record whether each table-bearing panel has sortable/resizable/reorderable columns, per-company table state, both scroll directions, split-pane separation, company-preference formatting, and a real supported Delete operation where applicable. A defect discovered in a completed phase becomes a focused corrective slice.

## Panel matrix

| `AppPanelId` | Panel/class | Visible controls | Query source | Write source | Survives restart | H2 authoritative | Dependencies | Simulated/placeholder/sidecar behavior | Missing work | Owning phase |
|---|---|---|---|---|---|---|---|---|---|---|
| `DASHBOARD` | `DashboardHomePanel` | KPI cards, report/action buttons, tables | dashboard/report/fund services | navigation only | yes for queried data | yes | current reporting projections | date defaults use current date | lifecycle-owned context and neutral states | P01 |
| `JOURNAL_PANE` | `JournalWorkspaceCompliancePanel` / `JournalWorkspacePanel` | grouped journal; filters; New/Edit/Save/Delete-or-Reverse/Refresh; entry lines; additional and supplemental details; overall editor scrolling and resize bars | canonical transaction/reference services | canonical transaction/correction services and company UI state | yes | yes | `Txn`, `TxnSplit`, supplemental lines, master data, close ranges | donor code is design reference only | laptop-width validation; explicit line-level cleared projection later | P02/P03/P10 |
| `LEDGER_REGISTER`, `TXN_EDITOR` | retired aliases | no separate destination | normalized to Journal | normalized to Journal | same | same | compatibility routing | aliases only | remove only in a compatibility-breaking release | P03 |
| `BANKING` | `BankingPanel` | bank/configured-account tables and forms | bank/account services | bank/account services | yes | yes | Bank, CompanyBankAccount, Account | no sidecar ledger | desktop visual validation | P05 |
| `BUDGET_EDITOR` | `BudgetEditorPanel` | category budget table, amount editor, save draft, activate version | `BudgetPlanService` | `BudgetPlanService` | yes | yes | budget plans/lines/categories | no sidecar target store | table-state/preference hardening | P04 |
| `BUDGET_VS_ACTUAL` | `BudgetVsActualPanel` | variance table, run/refresh | `BudgetPlanService.activeVariance` | none | yes | yes | active budget and canonical ledger | neutral state when no active budget | broader report catalog integration | P04/P11 |
| `ASSETS_REGISTER` | `AssetsRegisterPanel` | asset table/form and account/fund selectors | fixed-asset/account/fund services | `FixedAssetService` | yes | yes | fixed assets and master data | old runbook removed | disposal/impairment specialization | P08 |
| `DEPRECIATION_RUNS` | `DepreciationRunsPanel` | basis table, run controls, completed-run table | `FixedAssetService` | depreciation creates canonical transaction/run | yes | yes | fixed assets, runs, canonical ledger | old runbook removed | richer batching/report integration | P08/P11 |
| `INVENTORY` | `InventoryPanel` | item and movement tables, editor, movement actions | inventory/account/fund services | `InventoryService` | yes | yes | inventory items/movements | old runbook removed | financial movement automation and reports | P09/P11 |
| `RECONCILIATION_RUNS` | `ReconciliationRunsPanel` | setup, statement, match, review/save workflows | reconciliation services/repositories | reconciliation services/repositories | yes | partially | configured bank accounts, ledger/import facts | real H2 comparison; detailed edit workflow incomplete | line-level cleared resolution/edit workflow | P06/P11 |
| `PERIOD_CLOSE_RUNS` | `PeriodCloseRunsPanel` | calculated/custom close, reopen, refresh, range and event tables | `PeriodCloseRangeService` | `PeriodCloseRangeService` | yes | yes | active company/period/policy/canonical transaction services | legacy run records are compatibility-only | policy settings and later specialized formal adjustment | P10 |
| `IMPORT_PREVIEW` | `ImportPreviewPanel` | preview buttons and accepted/rejected tables | import preview service | preview only | no for staging | no until accepted | import parsing | staging is in-memory by design | canonical acceptance workflow | P05 |
| `APPROVAL_AUDIT` | `ApprovalAuditPanel` | search/table/filter | approval-audit service | none | yes | yes | legacy audit repository | legacy terminology remains | rename/scope as factual audit history | P12 |
| `IMPORT_EXPORT_JOBS` | `ImportExportJobsPanel` | legacy job table | static workspace data | static workspace data | no | no | UI static store | eliminated product function still has legacy code | remove in P13 | P13 |
| `BANK_TRANSACTIONS` | `BankTransactionsPanel` | transaction table/actions | static workspace data | static workspace data | no | no | future bank-import facts | not wired to H2 import schema | P05 follow-up | P05 |
| `REPORT_LIBRARY` | `ReportLibraryPanel` | typed report list; report-specific as-of/range dates; All Funds/fund selector; conditional row limit; Run, Export, Drill to Journal; split preview | `ReportExecutionService`, `FinancialReportService`, semantic templates, fund lookup, company UI preferences | file export and company-owned divider state only | exported files and divider state yes | report data comes from authoritative H2 projections | typed `ReportDefinition`, `ReportRequest`, core report services/templates, export adapters | no selectable placeholder reports; CSV stays machine-readable while visible core text uses company formatting | laptop-width visual validation and later addition of specialized reports | P11 |
| `CHART_OF_ACCOUNTS` | `ChartOfAccountsPanel` | table/form/type/status/save/new | account services | `AccountAdminService` | yes | yes | Account | real admin path | composition/validation hardening | P12 |
| `FUNDS` | `FundsPanel` | table/form/type/status/save/new | fund services | `FundAdminService` | yes | yes | Fund | real admin path | composition/validation hardening | P12 |
| `SETTINGS` | `SettingsPanel` | theme/range/database and company display settings | session/preferences services | app state and company UI preference tables | yes | yes for company display state | file app state plus company preference tables | mixed shell/company concerns remain | broader lifecycle | P12 |
| `DIAGNOSTICS` | `DiagnosticsPanel` | diagnostics/recovery actions | database/session services | recovery/navigation | mixed | mixed | migration/location/recovery | typed ownership incomplete | diagnostics architecture | P13 |
| `HELP` | `HelpPanel` | static guidance | static content | none | n/a | n/a | none | none | keep aligned | P12/P14 |

## Panels without `AppPanelId`

- `CompanyAdminPanel` and `UserAdminPanel` are H2-backed but not currently reachable through normal navigation.
- Alternate/reference dashboard/workspace classes remain for later classification or retirement.
- `DatabaseRecoveryPanel` is outside normal panel navigation.

## Immediate backlog implications

1. P11 must complete visual validation and document specialized-report follow-up candidates.
2. P12 must remove remaining approval/rejection terminology from administration/audit surfaces unless requirements change.
3. P09 may later add financially relevant movement automation and inventory reports.
4. P06 may later add line-level cleared-state resolution and edit-existing reconciliation workflow.
5. P03 may later add explicit line-level cleared-state projection.
