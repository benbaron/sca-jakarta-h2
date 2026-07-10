# Interface operation matrix

Status: P00 inventory of current main, updated through P03-C5 persisted Transaction Editor supplemental details. This document records visible operations and data authority so later phases can replace placeholders without rescanning the whole UI.

## Scope and evidence

- `AppPanelId` defines 21 stable workspace panel identifiers.
- `PanelHost` supports every `AppPanelId` except `DASHBOARD` through a static factory map and uses `DashboardHomePanel` for the permanent dashboard.
- `NavigationPane` exposes the same panel set in grouped left navigation.
- Global commands route from `MainWindow`/toolbar/menu through `PanelHost` to `AppPanel` hooks (`onNew`, `onSave`, `onCopy`, `onPaste`, `onRunCommand`).
- `UiServiceRegistry` creates JPA-backed lookup/admin/report services, plus JDBC repositories for reconciliation, period-close, and approval audit run panels.
- Search evidence: `UiWorkspaceDataStore` and sidecar/static stores remain active for unfinished import/export and bank-transaction surfaces; the former top-level Schedules panel, asset/depreciation runbook sidecars, and inventory runbook sidecar were removed in P07/P08-S1/P09-S1.

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

Do not add disabled placeholder Delete buttons. A durable-record panel may expose a real Delete action only when the authoritative service supports the action and protection rules. Otherwise, explain non-deletable records through status/help text, inactive/disposed workflow copy, or documentation rather than as a disabled button.

## Completed-phase UI design-rule updates

`doc/ui_design_rules.md` applies to every panel listed below, including panels delivered by completed phases. For future inventory passes, record whether each table-bearing panel has sortable/resizable/reorderable columns, per-company saved table state, vertical and horizontal scroll bars, a split-pane boundary from surrounding data, company-preference money/date formatting, and whether Delete is a real supported operation. Any noncompliance found in a completed phase becomes a focused corrective slice rather than a wholesale phase reopening.

## Panel matrix

| `AppPanelId` | Panel/class | Visible controls | Query source | Write source | Survives restart | H2 authoritative | Dependencies | Simulated/placeholder/sidecar behavior | Missing work | Owning phase |
|---|---|---|---|---|---|---|---|---|---|---|
| `DASHBOARD` | `DashboardHomePanel` | KPI cards, report/action buttons, tables | `UiServiceRegistry.dashboardQuery`, reports, fund balances | navigation only | yes for queried data | yes for queried data | dashboard query/report/fund services | date defaults use `LocalDate.now()` | lifecycle-owned workspace context and neutral states | P01 |
| `LEDGER_REGISTER` | `LedgerRegisterPanel` | table, refresh/search/open editor actions | `LedgerQueryService` | navigation to editor | yes | reads current ledger query model | `Txn`/`TxnSplit`, ledger query service | no independent write | canonical ledger authority not yet documented | P02/P03 |
| `TXN_EDITOR` | `TransactionEditorPanel` | page/tabbed journal-entry workspace: Header, Entry Lines, Additional Details, Donation Subschedule, Supplemental Details; validate/save/journal/open-in-ledger/delete/reverse controls | lookup services, transaction reference data, canonical transaction load/update routing | `TransactionEntryService` for New/Edit save paths including `txn_supplemental_line`; `TransactionCorrectionService` for Delete/Reverse policy | yes after save | yes for saved transaction header, lines, and supplemental detail rows | account/fund/budget/activity/merchant/counterparty lookups, canonical transaction service, `txn_supplemental_line` | donation/donor detail fields remain editor-local until donor/donation H2 services exist; supplemental rows are no longer sidecar/session-only | full donor/donation service remains future work; richer domain-owned supplemental services may later replace generic transaction-attached details | P02/P03 |
| `JOURNAL_PANE` | `JournalPane` | filter fields, grouped transaction journal table, transaction supplemental-detail viewer, New/Edit/Delete/Refresh actions | `TransactionEntryService.search` and `journalView` projections | New/Edit navigates to Transaction Editor; Delete routes through `TransactionCorrectionService` delete/reversal policy | yes for queried H2 data; table state persists by active company key | yes; reads canonical `txn`/`txn_split` projections | canonical transaction service and correction service | supplemental indicator/viewer is transaction-local, not the eliminated generic Schedules module | richer persisted supplemental-domain display remains later domain work | P03 |
| `BANKING` | `BankingPanel` | bank table/form, configured-account table/form, existing-account selector, auto-create chart account option | `BankConfigurationService`, `AccountLookupService` | `BankConfigurationService`, `AccountAdminService` for auto-created BANK/DEBIT/CASH accounts | yes | yes | `Bank`, `CompanyBankAccount`, chart `Account` | no sidecar ledger; deactivate preserves statement/reconciliation history | desktop JavaFX visual validation | P05 |
| `BUDGET_EDITOR` | `BudgetEditorPanel` | category budget table, amount editor, save draft, activate version | `BudgetPlanService`, active budget categories | `BudgetPlanService` draft line replacement and activation | yes, H2 | yes for normalized budget plans/lines | `budget_plan`/`budget_line`, budget category lookup | no sidecar budget target authority after P04 | table-state/preference hardening remains a design-rule follow-up | P04 |
| `BUDGET_VS_ACTUAL` | `BudgetVsActualPanel` | active budget variance table, run/refresh | `BudgetPlanService.activeVariance` | none | yes for queried H2 data | yes for active budget and actual ledger data | active `budget_plan`/`budget_line`, canonical ledger actuals | neutral state when no active budget version exists | report-library expansion remains P11 | P04/P11 |
| `ASSETS_REGISTER` | `AssetsRegisterPanel` | H2 asset table, asset form, account/fund selectors, save/new | `FixedAssetService`, account/fund lookup services | `FixedAssetService.create/update` | yes | yes | `fixed_asset`, chart accounts, funds | old lifecycle runbook removed | disposal/impairment specialization and table-state polish | P08 |
| `DEPRECIATION_RUNS` | `DepreciationRunsPanel` | depreciation basis table, run date/notes, run monthly depreciation, completed run table | `FixedAssetService` | `FixedAssetService.runMonthlyDepreciation` creates canonical `Txn` and `fixed_asset_depreciation_run` | yes | yes | `fixed_asset`, `fixed_asset_depreciation_run`, canonical `Txn`/`TxnSplit` | old depreciation runbook removed | richer period batching and report integration | P08 |
| `INVENTORY` | `InventoryPanel` | inventory item table, movement-history table, item form subpanel opened by New Item/Edit Selected, account/fund selectors, receipt/issue/adjustment buttons | `InventoryService`, account/fund lookup services | `InventoryService.create/update/recordMovement` | yes | yes | `inventory_item`, `inventory_movement`, chart accounts, funds | old inventory runbook removed; P09-C1 adds item-editor subpanel navigation, global New/Save, dirty state, table-state persistence, validation highlighting, and common money/date parsing/formatting | financially relevant movement-to-ledger automation and reports | P09 |
| `RECONCILIATION_RUNS` | `ReconciliationRunsPanel` | configured-account selector, date range, comparison run/save controls, comparison table, saved run table, record started/completed/failed buttons, comparison-workflow note | `BankConfigurationService`, `ReconciliationComparisonService`, reconciliation run repository | `ReconciliationComparisonService` saves unresolved reports through reconciliation run records; run buttons write reconciliation run records | yes | partially; run/report summaries are H2 and comparison reads canonical H2 ledger/import facts | configured `Bank`/`CompanyBankAccount`, canonical `Txn`/`TxnSplit`, `BankStatementLine`, reconciliation run repository | comparison uses real H2 records; mismatch resolution choices and report edit/reopen remain later work | per-line cleared-state resolution choices and edit existing reconciliation workflow | P06 |
| `PERIOD_CLOSE_RUNS` | `PeriodCloseRunsPanel` | run table, close/reopen/approve/reject buttons | `PeriodCloseService`/JDBC repository | period close run repository | yes | partially; run records are H2 | period-close repository, approval audit | approve/reject controls conflict with policy | close/reopen and audit policy alignment | P10 |
| `IMPORT_PREVIEW` | `ImportPreviewPanel` | preview buttons, accepted/rejected tables | `ImportPreviewService` | preview only except external import actions | no for staged rows | no until accepted elsewhere | CSV parser/import preview service | staging is in-memory by design | accepted import through canonical transaction service | P05 |
| `APPROVAL_AUDIT` | `ApprovalAuditPanel` | search/table/filter | `ApprovalAuditService` | none from panel | yes | H2 audit table | JDBC approval audit repository | approval terminology conflicts with future audit-history direction | rename/scope as factual audit history | P10/P12 |
| `IMPORT_EXPORT_JOBS` | `ImportExportJobsPanel` | job table, clear jobs | `UiWorkspaceDataStore.jobs` | `UiWorkspaceDataStore.clearJobsForTests` | no/general static session | no | UI static store | job history not durable/import-authoritative | durable job diagnostics | P13 |
| `BANK_TRANSACTIONS` | `BankTransactionsPanel` | bank transaction table, mark/export actions | `UiWorkspaceDataStore.bankTransactions` | static job/bank cache | no | P05-S1 adds `bank_import_batch`/`bank_statement_line`/`import_issue`, but this panel is not wired to them yet | UI static store, import/export jobs, future import fact entities | bank transactions are session/static until review workflow wiring | P05 |
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
2. P10/P12 must remove approval/rejection terminology from user-facing production workflows unless the plan is amended.
3. P09 must still add financially relevant movement-to-ledger automation and inventory reporting.
4. P06 must finish per-line cleared-state resolution choices and edit existing reconciliation workflow.
