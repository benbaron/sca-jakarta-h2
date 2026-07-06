# Interface operation matrix

Status: P00 inventory of current main, updated through P04 budget persistence. This document records visible operations and data authority so later phases can replace placeholders without rescanning the whole UI.

## Scope and evidence

- `AppPanelId` defines 22 stable workspace panel identifiers.
- `PanelHost` supports every `AppPanelId` except `DASHBOARD` through a static factory map and uses `DashboardHomePanel` for the permanent dashboard.
- `NavigationPane` exposes the same panel set in grouped left navigation.
- Global commands route from `MainWindow`/toolbar/menu through `PanelHost` to `AppPanel` hooks (`onNew`, `onSave`, `onCopy`, `onPaste`, `onRunCommand`).
- `UiServiceRegistry` creates JPA-backed lookup/admin/report services, plus JDBC repositories for reconciliation, period-close, and approval audit run panels.
- Search evidence: `UiWorkspaceDataStore` and `RunbookPersistence` remain active sidecar/static stores; the former `BudgetTargetPersistence` budget sidecar was removed in P04. Searches for “saved in session”, “not implemented”, “Approve”, “Reject”, and `LocalDate.now()` identify remaining non-authoritative UI paths.

## Global commands

| Command/source | Visible control | Query source | Write source | Survives restart | H2 authoritative | Placeholder/sidecar risk | Owning phase |
|---|---|---|---|---|---|---|---|
| New | menu/toolbar button | active `AppPanel` | active panel `onNew` | panel-dependent | panel-dependent | generic dispatch hides whether command is genuine | P01/P03 |
| Save | menu/toolbar button | active `AppPanel` | active panel `onSave` | panel-dependent | panel-dependent | transaction editor reports “Draft saved in session” | P01/P02/P03 |
| Copy/Paste | menu/toolbar button | active selection | active panel hooks | no accounting persistence | no | mostly UI clipboard/status behavior | P01 |
| Find/Journal/command runner | toolbar/global shortcuts | active panel selection and services | active panel `onRunCommand` | panel-dependent | panel-dependent | text-command dispatch should become typed commands | P01 |
| Import/Export | main-window file actions | CSV/OFX/QIF services and session state | `UiWorkspaceDataStore` job/bank caches; some COA import writes H2 | partially | mixed | job log and bank transactions are session/static, not canonical import persistence | P05/P13 |
| Database wizard/switch | main-window database actions | `DatabaseLocationService`, `DatabaseMigrationService`, `UiServiceRegistry` | H2 file/JPA resources | yes | yes after successful switch | switching and recovery need atomic composition review | P01/P12 |

## Delete operation rule

Every panel that creates or maintains durable records must expose Delete or a visible explanation for why Delete is unavailable. Delete must delegate to the authoritative service for that record type and must not be implemented as a table-only row removal. For transactions, Delete follows Settings -> Correction method: `DIRECT_EDIT` delegates to audited transaction deletion after period and reconciliation checks; non-direct correction methods ask whether to auto-fill and perform a reversing entry using the active accounting period as the default reversal date.

## Completed-phase UI design-rule updates

`doc/ui_design_rules.md` applies to every panel listed below, including panels delivered by completed phases. For future inventory passes, record whether each table-bearing panel has sortable/resizable/reorderable columns, per-company saved table state, vertical and horizontal scroll bars, a split-pane boundary from surrounding data, company-preference money/date formatting, and a Delete affordance or visible unavailable reason. Any noncompliance found in a completed phase becomes a focused corrective slice rather than a wholesale phase reopening.

## Panel matrix

| `AppPanelId` | Panel/class | Visible controls | Query source | Write source | Survives restart | H2 authoritative | Dependencies | Simulated/placeholder/sidecar behavior | Missing work | Owning phase |
|---|---|---|---|---|---|---|---|---|---|---|
| `DASHBOARD` | `DashboardHomePanel` | KPI cards, report/action buttons, tables | `UiServiceRegistry.dashboardQuery`, reports, fund balances | navigation only | yes for queried data | yes for queried data | dashboard query/report/fund services | date defaults use `LocalDate.now()` | lifecycle-owned workspace context and neutral states | P01 |
| `LEDGER_REGISTER` | `LedgerRegisterPanel` | table, refresh/search/open editor actions | `LedgerQueryService` | navigation to editor | yes | reads current ledger query model | `Txn`/`TxnSplit`, ledger query service | no independent write | canonical ledger authority not yet documented | P02/P03 |
| `TXN_EDITOR` | `TransactionEditorPanel` | header fields, split table, validate/save/run commands | lookup services | UI draft only/current save path | no for draft | no | account/fund/budget lookups | “Draft saved in session”; no canonical persisted transaction entry | real transaction command service and editor wiring | P02/P03 |
| `SCHEDULES` | `SchedulesPanel` | kind selector, eligibility list, runbook list, add action | `ScheduleEligibilityService` plus sidecar runbook | `UiWorkspaceDataStore.appendScheduleRunbookEntry` / `RunbookPersistence` | yes, sidecar file | no | account schedule metadata, sidecar runbook | sidecar runbook is not H2 accounting truth | schedules/open-item model | P07 |
| `BUDGET_EDITOR` | `BudgetEditorPanel` | category budget table, amount editor, save draft, activate version | `BudgetPlanService`, active budget categories | `BudgetPlanService` draft line replacement and activation | yes, H2 | yes for normalized budget plans/lines | `budget_plan`/`budget_line`, budget category lookup | no sidecar budget target authority after P04 | table-state/preference hardening remains a design-rule follow-up | P04 |
| `BUDGET_VS_ACTUAL` | `BudgetVsActualPanel` | active budget variance table, run/refresh | `BudgetPlanService.activeVariance` | none | yes for queried H2 data | yes for active budget and actual ledger data | active `budget_plan`/`budget_line`, canonical ledger actuals | neutral state when no active budget version exists | report-library expansion remains P11 | P04/P11 |
| `ASSETS_REGISTER` | `AssetsRegisterPanel` | asset table, lifecycle log, add lifecycle action | schedule eligibility + sidecar lifecycle | `UiWorkspaceDataStore.appendAssetLifecycleEntry` | yes, sidecar file | no | schedule/account services, sidecar runbook | lifecycle log is sidecar text | fixed-asset register/depreciation authority | P08 |
| `DEPRECIATION_RUNS` | `DepreciationRunsPanel` | run table/log, record run action | schedule/fixed-asset candidates + sidecar | `UiWorkspaceDataStore.appendDepreciationRunEntry` | yes, sidecar file | no | schedule services, sidecar runbook | depreciation run is sidecar text | depreciation service and ledger integration | P08 |
| `INVENTORY` | `InventoryPanel` | item/movement inputs, movement log | schedule eligibility + sidecar movements | `UiWorkspaceDataStore.appendInventoryMovementEntry` | yes, sidecar file | no | schedule services, sidecar runbook | inventory movements are sidecar text | inventory/supplies model and accounting | P09 |
| `RECONCILIATION_RUNS` | `ReconciliationRunsPanel` | run table, record/approve/reject buttons | `ReconciliationService`/JDBC repository | reconciliation run repository | yes | partially; run records are H2 | JDBC reconciliation repository, approval audit | approve/reject controls conflict with no approval workflow | reconciliation workflow without approval semantics | P06/P10 |
| `PERIOD_CLOSE_RUNS` | `PeriodCloseRunsPanel` | run table, close/reopen/approve/reject buttons | `PeriodCloseService`/JDBC repository | period close run repository | yes | partially; run records are H2 | period-close repository, approval audit | approve/reject controls conflict with policy | close/reopen and audit policy alignment | P10 |
| `IMPORT_PREVIEW` | `ImportPreviewPanel` | preview buttons, accepted/rejected tables | `ImportPreviewService` | preview only except external import actions | no for staged rows | no until accepted elsewhere | CSV parser/import preview service | staging is in-memory by design | accepted import through canonical transaction service | P05 |
| `APPROVAL_AUDIT` | `ApprovalAuditPanel` | search/table/filter | `ApprovalAuditService` | none from panel | yes | H2 audit table | JDBC approval audit repository | approval terminology conflicts with future audit-history direction | rename/scope as factual audit history | P10/P12 |
| `IMPORT_EXPORT_JOBS` | `ImportExportJobsPanel` | job table, clear jobs | `UiWorkspaceDataStore.jobs` | `UiWorkspaceDataStore.clearJobsForTests` | no/general static session | no | UI static store | job history not durable/import-authoritative | durable job diagnostics | P13 |
| `BANK_TRANSACTIONS` | `BankTransactionsPanel` | bank transaction table, mark/export actions | `UiWorkspaceDataStore.bankTransactions` | static job/bank cache | no | no | UI static store, import/export jobs | bank transactions are session/static not statement-line persistence | bank statement-line persistence | P05 |
| `REPORT_LIBRARY` | `ReportLibraryPanel` | report list, date range, format/export buttons | `FinancialReportService`, semantic templates | file export | exported files yes | reports query H2 where implemented | report service/templates | some reports return “Report not implemented” | report architecture and full report coverage | P11 |
| `CHART_OF_ACCOUNTS` | `ChartOfAccountsPanel` | table, fields, type/status selectors, save/new | account lookup/admin services | `AccountAdminService` | yes | yes | JPA account model | real admin write path exists | composition/validation hardening | P12 |
| `FUNDS` | `FundsPanel` | table, fields, fund type/status selectors, save/new | fund lookup/admin services | `FundAdminService` | yes | yes | JPA fund model | real admin write path exists | composition/validation hardening | P12 |
| `SETTINGS` | `SettingsPanel` | theme/range/database preferences controls | `UiSessionState`/preferences state | app state store/session state | yes, app-state sidecar | no | `FileAppStateStore`, `UserAppStateStore` | preferences are not company/database-scoped H2 | admin/preferences lifecycle | P12 |
| `DIAGNOSTICS` | `DiagnosticsPanel` | buttons for diagnostics/recovery | database/session services | recovery/navigation actions | mixed | mixed | migration/location/recovery services | diagnostic actions need typed command ownership | diagnostics/jobs architecture | P13 |
| `HELP` | `HelpPanel` | static guidance text | static content | none | n/a | n/a | none | no write behavior | keep aligned with current workflows | P12/P14 |

## Panels without `AppPanelId`

- `CompanyAdminPanel` and `UserAdminPanel` are production admin panels with H2-backed services but are not currently reachable through `AppPanelId`/`NavigationPane`.
- `DashboardWorkspacePanel`, `DashboardPanelFX`, `ReferenceWorkspaceWindow`, and `DashboardExperiment` are alternate/reference dashboard/workspace components that P01 must classify or retire from the production shell.
- `DatabaseRecoveryPanel` is a recovery surface outside normal panel navigation.

## Immediate backlog implications

1. P01 must replace static panel factories and global text dispatch with lifecycle-owned workspace services and typed commands.
2. P02/P03 must provide a canonical transaction command service before enabling transaction-editor persistence.
3. P04 removed the budget sidecar from authoritative budget operations; P05/P07/P08/P09 must still remove sidecar/static stores from bank, schedule, asset, depreciation, and inventory operations.
4. P10/P12 must remove approval/rejection terminology from user-facing production workflows unless the plan is amended.
