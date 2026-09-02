# Interface operation matrix

Status: reconciled to current production architecture through P17-C11. Historical phase notes remain in archived plans and completed slice documents; this file describes current reachable production behavior.

## Scope and authority

- `ProductionWorkspaceWindow` is the production shell. The retired `MainWindow` is only a deprecated session-compatibility facade and owns no production chrome, routing, Find/command-palette, authentication UI, date-range selector, or import/export commands.
- `ApplicationSessionContext` / `UiSessionState` own shared desktop session facts used by current production.
- `AppPanelId.JOURNAL_PANE` is the one canonical Journal destination. `LEDGER_REGISTER` and `TXN_EDITOR` remain compatibility aliases only and normalize to the same Journal tab.
- The former top-level Schedules panel/destination and the generic Import/Export Jobs destination are eliminated. Their historical schema/compatibility data is not itself a production destination.
- `PanelHost` owns reusable canonical tabs and calls `onPanelShown()` when an existing destination is reselected so current H2 data is refreshed.
- `PanelFactory` applies shared production UI compliance, including company-owned table state, while panels with richer existing binders retain their single authoritative state owner.
- `UiServiceRegistry` supplies current service authorities. Compatibility repositories/services may remain only where a current production or interchange path still consumes them.

## Global commands

| Command/source | Current behavior | Authority / boundary |
|---|---|---|
| New | Enabled only when the active panel declares a real New capability. | Active `AppPanel.commandCapabilities()` and panel `onNew`. |
| Save | Enabled only when the active panel declares a real Save capability. | Active panel `onSave`; no empty success path. |
| Validate | Journal-only validation command. | Canonical Journal editor/service validation; not a posting or approval workflow. |
| Copy/Paste | Native JavaFX text-control behavior. | Focused standard text control; production shell does not intercept `Ctrl+C`/`Ctrl+V`. |
| Close All Tabs | Closes closable workspace tabs with dirty-state protection. | `ProductionWorkspaceWindow` / `PanelHost`. |
| Close Inspector | Hides the production inspector. | Production shell state. |
| Find / command palette | Not installed or advertised. | Retired with legacy shell authority in P17-C11. |
| Database create/open/switch/recovery | Prepares and validates the target database/service bundle before activation. | `DatabaseSessionController`, migration/location services, `UiServiceRegistry`, company-session validation. |
| Import / export | Format-specific production actions; there is no generic Jobs workflow. | `ImportExportOrchestrationService` plus format-specific preview/review/commit services. |

## Production command capability matrix

`Close All Tabs` and `Close Inspector` are shell-owned. Panel-owned capabilities are exact and must never be enabled as placeholders.

| Destination | New | Save | Validate | Notes |
|---|---:|---:|---:|---|
| Dashboard | yes | no | no | New opens the canonical Journal entry workflow. |
| Journal | yes | yes | yes | Retired Ledger Register/Transaction Editor aliases normalize here. |
| Banking | yes | yes | no | Durable bank/configuration editing plus panel-local statement import/review actions. |
| Asset Register | yes | yes | no | Stable asset identity and governed lifecycle operations. |
| Inventory | yes | yes | no | Stable item identity and governed movement/lifecycle operations. |
| Chart of Accounts | yes | yes | no | Stable account ID editing; code is mutable business data. |
| Funds | yes | yes | no | Stable fund ID editing with protected delete/deactivation rules. |
| Budget Editor | no | yes | no | Version creation/activation/archive remain explicit panel actions. |
| Administration — Preferences | no | yes | no | Capabilities follow the selected inner tab. |
| Administration — Company/User maintenance | yes | yes | no | Stable durable-record maintenance. |
| Report Library, Budget vs Actual, Depreciation Runs, Reconciliation, Period Close, Import Preview, Audit History, Bank Transactions, Diagnostics, Help | no | no | no | Run/review/navigation commands remain panel-local. |

## Canonical panel matrix

| Destination | Production panel / role | Query authority | Write authority | Persistence notes |
|---|---|---|---|---|
| Dashboard | `DashboardHomePanel` | Dashboard/report/fund query services | navigation only | No fictional values or second data path. |
| Journal | `JournalWorkspaceCompliancePanel` -> `JournalWorkspacePanel` | `TransactionEntryService.search/load`, reference-data services, reconciliation projection | `TransactionEntryService`, `TransactionCorrectionService` | `Txn`/`TxnSplit` are canonical. Cleared facts are read-only service projections in Journal. |
| Banking | `BankingPanel` | `BankConfigurationService`, account lookup, `BankReviewQueryService` | bank/configuration services; explicit navigation to import/review | Bank statement import is non-posting until explicit reviewed-row acceptance. |
| Budget Editor | `BudgetEditorPanel` | `BudgetPlanService` | draft/revision/save/activate/archive via same service | Stable `BudgetPlan.id`; retained archived history. |
| Budget vs Actual | `BudgetVsActualPanel` | active budget plus canonical ledger actuals in company fiscal/accounting-period context | none | No calendar-year substitution. |
| Asset Register | `AssetsRegisterPanel` | `FixedAssetService` | fixed-asset lifecycle/depreciation/correction services | Asset-linked transactions are domain-governed. |
| Inventory | `InventoryPanel` | `InventoryService` | inventory movement/lifecycle services | Financial movements link atomically to canonical transactions. |
| Chart of Accounts | `ChartOfAccountsPanel` | account/chart services | `AccountAdminService` | Stable account ID; deactivate instead of invented hard delete for referenced history. |
| Funds | Funds administration panel | fund lookup/admin services | `FundAdminService` | Stable IDs; unused delete only after usage checks, otherwise deactivate. |
| Reconciliation | reconciliation workspace | current reconciliation query/workspace services | current reconciliation finalization/matching services | Matching and cleared-state mutation remain reconciliation-owned. |
| Period Close | `PeriodCloseRunsPanel` | `PeriodCloseRangeService` and factual history | `PeriodCloseRangeService` guarded close/reopen operations | `BOOKKEEPING_WRITE` is authoritative at the service boundary; calculated period honors configured period start day. |
| Bank Transactions | current bank-transaction workspace | canonical configured-bank split projection plus statement-review facts | explicit reviewed-row acceptance/correction routes only | No second bank ledger. |
| Import Preview | `ImportPreviewPanel` and format-specific review surfaces | transient frozen preview state plus current target facts | format-specific atomic commit service | Preview is intentionally transient; accepted facts become H2 authority. |
| Report Library | report library panels/services | report query/execution services | export only where supported | Explicit report dates may detach from shell-selected accounting period. |
| Administration | `AdministrationPanel` | preferences/company/user/database-transfer services | owning service of selected tab | No second shell identifier or preference store. |
| Diagnostics | diagnostics/recovery query surfaces | `DiagnosticsQueryService` and current session/database services | explicit typed recovery/database actions | Failed/cancelled recovery retains prior active bundle. |

## Import and export operation matrix

| Format / operation | Preview/review boundary | Commit/write authority | Important invariant |
|---|---|---|---|
| Chart of Accounts CSV | frozen COA preview with source/company/chart/target identity | `CoaCsvImportService` + caller-owned `AccountAdminService` transaction | accepted rows commit atomically; drift requires new preview. |
| Chart of Accounts JSON | chart-only import/export validation | chart/account services | no transaction-history transfer. |
| OFX/QFX | strict statement preview and configured-account validation | bank statement import service | persists statement evidence only; does not auto-post ledger transactions. |
| Mapped CSV | mapping/parse preview | bank CSV import service | same durable bank-review authority as other statement formats. |
| Normalized CSV | normalized identity/validation preview | normalized bank CSV service | preserves external IDs/PAYEEID and governed review facts. |
| Reviewed statement acceptance | selected durable statement row | `ReviewedStatementAcceptanceService` + canonical `TransactionEntryService` | one explicit canonical transaction acceptance; reconciliation retains cleared-state ownership. |
| SCLX | complete target-company preview, ownership gate, per-record classification/resolution | SCLX caller-owned atomic target-company graph commit | `NEW`/`IDENTICAL`/`CONFLICT` decisions are revalidated; no partial commit. |
| Whole database transfer | explicit backup/restore preparation | supported H2 transfer/session activation path | preserves full database including compatibility data; activation only after validation. |

There is no production `UiWorkspaceDataStore` generic job list and no generic Import/Export Jobs route.

## Durable-record lifecycle rule

Every production durable-record maintenance surface exposes either a real governed lifecycle/correction operation or visible explanatory copy describing why physical deletion is unavailable. A disabled placeholder Delete is not acceptable.

Current examples:

- accounts retain stable IDs and use Active/deactivation for referenced history;
- budget versions retain archived history;
- bank/configuration, inventory, and fixed-asset records preserve history through domain lifecycle operations;
- funds delete only when unused, otherwise deactivate;
- Journal transaction correction follows the configured direct-delete versus reversal policy and service protections.

## Company UI state and formatting

- Company money/date preferences are H2-backed and consumed through `CompanyUiFormat`.
- Company table order/width/sort and production split-pane state are H2-backed through the established binders.
- Machine/session preferences such as theme or top-level window geometry remain outside accounting persistence.
- Company switching rebuilds company-bound workspace state so formatting and layout ownership do not remain attached to the prior company.

## Compatibility classifications retained after P17-C11

- `MainWindow`: deprecated non-JavaFX session compatibility facade only.
- `DateRange` / `DateRangeContext`: retained for intentional Report Library explicit-range behavior.
- `DateRangeSelector` / `DateRangeUtil`: retired dead legacy shell/editor helpers.
- `ScheduleEligibilityService`: retained unrouted compatibility/domain query where existing metadata consumers still require it; it does not restore a Schedules workspace.
- legacy reconciliation-run repositories/services: retained only where current SCLX/comparison/history compatibility paths consume them; they are not the canonical reconciliation workspace authority.
- legacy period-close-run repositories/services: retained for compatibility/history where consumed; `PeriodCloseRangeService` remains canonical production close-state authority.

## Verification rule

When current production behavior and an older completed-phase statement disagree, current `main`, migrations/tests, and the owning current service boundary win. Correct the governing document rather than restoring retired UI or duplicate authority merely to satisfy stale documentation.
