# Model and persistence authority inventory

Status: reconciled to current production authority through P17-C11. Historical migration/audit details remain in completed phase documents and archive material; this file identifies the authorities that current production must preserve and the compatibility stores that must not become a second business model.

## Current persistence map

| Area | Current authority | H2 authoritative? | Compatibility / duplicate risk | Rule |
|---|---|---:|---|---|
| Canonical ledger / Journal | `Txn`, `TxnSplit`, `txn_supplemental_line`, `TransactionEntryService`, `TransactionCorrectionService` | yes | `JournalTransaction` / `PostingLine` JDBC compatibility model remains separate | Never add independent production writes to the compatibility model. |
| Journal bank/cleared projection | `TxnSplit.bankCleared`, `bankClearedOn`, and exact reconciliation-session projection | yes | UI could become a second cleared-state writer | Journal renders `Not bank` / `Uncleared` / `Cleared` / `Mixed` read-only; reconciliation owns matching/cleared mutation. |
| Fund master data | `Fund`, `FundAdminService`, `FundLookupService` | yes | code-keyed compatibility seams may remain for older callers | Stable ID is identity; referenced funds deactivate, unused funds may delete only after authoritative usage checks. |
| Company master data / selection | `Company`, `CompanyAdminService`, `CompanySessionController` | yes | recent/session selection is convenience, not company authority | Active selection requires an existing active H2 company. |
| User/role/company assignment admin | `AppUser`, `AppRole`, `UserCompanyRole`, `UserAdminService` | yes for administration facts | no authentication/runtime authorization authority yet | Stable IDs and dated end/revoke history; no inferred authentication semantics. |
| Budget | `BudgetPlan`, `BudgetLine`, `BudgetPlanService` | yes | retired sidecar target stores | Stable plan identity; draft/active/archived version lifecycle remains service-owned. |
| Chart of Accounts | `ChartOfAccounts`, `Account`, `AccountAdminService` | yes | code-keyed compatibility import seams | Account ID is identity; code is editable business data. |
| COA CSV preview | frozen transient preview + `CoaCsvImportService` | preview no; accepted facts yes | generic staging/job-store reintroduction | Accepted rows, external identities, and audit commit atomically; drift requires re-preview. |
| COA JSON | chart DTO import/export services | accepted chart/account facts yes | accidental whole-database semantics | Chart structure only; no transaction-history transfer. |
| Bank statement import/review | `bank_import_batch`, `bank_statement_line`, `import_issue`, mapping profiles, format-specific import services, `BankReviewQueryService` | yes after commit | raw preview is intentionally transient | OFX/QFX/mapped CSV/normalized CSV commit durable review evidence but do not auto-post ledger transactions. |
| Reviewed statement acceptance | `ReviewedStatementAcceptanceService` + canonical `TransactionEntryService` | yes | second bank-transaction ledger | Explicit acceptance creates/links canonical `Txn`/`TxnSplit`; no second ledger table. |
| Reconciliation | current reconciliation workspace/query/finalization authority plus `bank_reconciliation_session` / match facts | yes | older `ReconciliationRunRepository`/service family remains compatibility/history and some comparison/SCLX consumption | Current workspace authority owns matching/finalization/cleared state. Retain legacy run APIs only while a live current consumer exists. |
| Period close | `period_close_range`, `period_close_event`, `AuditEvent`, `PeriodCloseRangeService` | yes | `AccountingPeriod`, `PeriodCloseService`, and run repository remain compatibility/history | Range service is canonical close/reopen authority; compatibility wrappers do not define production close state. |
| Fixed assets / depreciation / lifecycle | `FixedAsset`, `FixedAssetDepreciationRun`, `FixedAssetLifecycleEvent`, `FixedAssetService` | yes | old text/runbook sidecars removed | Lifecycle/depreciation facts and linked canonical transactions stay synchronized through domain services. P18 may add batching, not a second engine. |
| Inventory / supplies | `InventoryItem`, `InventoryMovement`, `InventoryService` | yes | old text runbook removed | Financial movements link atomically to canonical transactions; nonfinancial movement is explicit. Reporting already reads these authorities. |
| Audit history | company-owned `AuditEvent`, `AuditHistoryService` | yes | `approval_audit_record` compatibility data | Production Audit History and SCLX factual audit use `AuditEvent`; legacy approval records do not create an approval workflow. |
| Company UI preferences/state | company preference/state tables, `CompanyUiPreferencesService`, table/split binders | yes for company-owned UI state | machine/session preferences are separate by design | UI state never becomes accounting authority; company switching changes owner context. |
| Desktop session | `ApplicationSessionContext` / `UiSessionState` | no accounting persistence | deprecated `MainWindow` facade | Session facts are runtime context only. `MainWindow` owns no production shell or commands. |
| Whole-database transfer | supported H2 backup/restore + prepared database/session activation | yes for transferred database | treating interchange previews as database authority | Preserve all database records; activate only after migration/service/company validation. |
| Generic Import/Export Jobs | none | no | historical references could reintroduce generic job tracking | Panel, route, enum destination, session job list, and `UiWorkspaceDataStore` generic authority remain removed. |
| Former Schedules UI | none | no active top-level UI authority | historical schedule/open-item schema and `ScheduleEligibilityService` compatibility query | Do not restore a Schedules workspace. Retain compatibility data/query only while a current consumer requires it. |

## Canonical ledger and Journal authority

- `Txn`/`TxnSplit` are the canonical accounting transaction model used by entry, correction, reports, banking acceptance, reconciliation, assets, inventory, and interchange.
- `txn_supplemental_line` is authoritative for transaction-attached Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability details.
- `JournalWorkspacePanel` queries `TransactionEntryService.search(...)` / `load(...)` and writes through `TransactionEntryService.enter(...)` / `update(...)`.
- Delete/reverse routes through `TransactionCorrectionService` with closed-period and completed-reconciliation protection.
- `LEDGER_REGISTER` and `TXN_EDITOR` are compatibility destination aliases only; they are not persistence models or separate panels.
- `JournalTransaction` / `PostingLine` and older JDBC journal/open-item repositories remain compatibility structures. No production feature may create a parallel writable ledger through them.

## Banking and reconciliation authority

Statement import and reconciliation are distinct authorities:

1. OFX/QFX/mapped CSV/normalized CSV preview validates source and configured-account scope.
2. Import commit writes durable statement evidence to `bank_import_batch`, `bank_statement_line`, and `import_issue`.
3. Statement import itself is non-posting.
4. Explicit reviewed-row acceptance may create/link one canonical transaction through `ReviewedStatementAcceptanceService` and `TransactionEntryService`.
5. Reconciliation owns statement-to-ledger matching, completion/finalization, and authoritative cleared facts.
6. Journal and reports may project reconciliation/cleared facts but do not mutate them.

Legacy reconciliation-run repositories/services remain only as classified P17-C11 compatibility/history APIs where current SCLX, comparison, migration, or historical reads still consume them. Their existence does not mean current reconciliation is incomplete or that a second reconciliation workflow should be built.

## Period-close authority

- `period_close_range` is the authoritative company-scoped closed-date-range state.
- `period_close_event` and company-owned `AuditEvent` retain factual close/reopen/adjustment history.
- `PeriodCloseRangeService` owns close, overlap validation, list, reopen policy, and open-period enforcement.
- Canonical transaction services call `requireOpen(...)` before protected ledger mutations.
- Reconciliation protection remains an independent prerequisite.
- `AccountingPeriod`, `PeriodCloseService`, and legacy period-close-run repositories/services are compatibility/history structures only where still consumed.
- Calculated active periods use the configured period-start day; wall-clock/calendar-year shortcuts are not production authority.

## Fixed-asset authority

- `FixedAsset` is authoritative durable asset identity and current lifecycle state.
- `FixedAssetDepreciationRun` and `FixedAssetLifecycleEvent` retain completed depreciation/lifecycle history.
- Confirmed sale, retirement, impairment, depreciation, and governed reversals link to canonical `Txn` records through domain services.
- Generic Journal mutation is blocked where it would cause asset/ledger divergence.
- Specialized fixed-asset reports already read these H2 facts. P18 concerns richer multiasset period batching/report integration, not basic asset reporting or a replacement depreciation engine.

## Inventory authority

- `InventoryItem` is the authoritative inventory-register record.
- `InventoryMovement` is authoritative receipt/issue/adjustment history.
- Interactive financial movements commit quantity/value effects, movement history, canonical transaction links, portable identities, and factual audit under the inventory service boundary.
- Explicitly confirmed zero-value/nonfinancial movements may remain unlinked to a transaction.
- Financial correction is append-only through inverse movement plus canonical reversal; historical rows are not silently rewritten.
- Inventory valuation and movement reports already read `InventoryItem` / `InventoryMovement` plus canonical inventory-account splits. Inventory reporting is therefore not “later work” in this authority inventory.

## Audit-history and approval compatibility

- `AuditEvent` is the current company-owned factual audit authority.
- Production Audit History queries `AuditEvent` through `AuditHistoryService` and does not blend legacy approval records into current facts.
- `ApprovalAuditRecord` / `approval_audit_record` are retained compatibility data only where historical/migration code still needs them.
- The repository deliberately has no user-facing approval queue or approval workflow. Historical migration names or compatibility tables do not authorize one.

## Import staging versus accepted data

Transient preview is permitted because review state is not accepted business data. Each format has its own real commit boundary:

- **COA CSV:** frozen source/company/chart/target scope; accepted accounts/external identities/audit commit atomically.
- **COA JSON:** chart structure import/export only.
- **OFX/QFX/mapped CSV/normalized CSV:** durable bank-review facts after format-specific commit; no auto-posting.
- **SCLX:** complete target-company preview, ownership checks, `NEW`/`IDENTICAL`/`CONFLICT` classification, explicit conflict disposition, revalidation, then one caller-owned atomic target-company graph commit.
- **Whole database:** H2 backup/restore and prepared-session activation, not a preview-family import.

No accepted data depends on `UiWorkspaceDataStore`, a generic Import/Export Jobs history, a donor sidecar repository, static company authority, or a second ledger.

## SCLX selected-company authority

SCLX is selected-company business data reconstructed from canonical H2 facts after ownership validation. Current ownership rules include:

- stable `Company.id` root identity;
- company-owned charts/accounts/funds/budgets/activities/parties/merchants according to the P15 ownership migrations and service checks;
- company-owned canonical `Txn` and dimensions;
- same-company bank import/reconciliation relationships;
- fixed asset and inventory dimensions constrained to the selected company;
- company-owned factual audit identity;
- compatibility families exported only when the format contract explicitly supports them and ownership is provable.

Ambiguous ownership fails closed; the application must not silently assign historical data to the currently selected UI company.

## Session, preferences, and UI-state authority

- `ApplicationSessionContext` / `UiSessionState` hold runtime database/company/session selection facts.
- `MainWindow` is a deprecated compatibility facade only; it is not production JavaFX shell authority.
- Company money/date formatting and company table/split state are H2-backed through current preference/state services.
- Theme, top-level window geometry, native decoration choice, and similar machine/session preferences remain non-accounting state.
- No preference control may mutate active database authority or present a compatibility value as authentication/authorization policy.

## Migration and compatibility rules

1. Applied Flyway migrations are immutable.
2. Schema is never dropped/recreated as a recovery shortcut.
3. Historical tables are not deleted merely because their production UI/service wrapper is retired.
4. Compatibility APIs may remain only when a current production/interchange/migration/historical read actually consumes them.
5. New schema cleanup requires its own nondestructive migration decision and upgrade/regression tests.
6. V2 schedule tables and other historical compatibility structures may remain physically present without restoring their retired product surface.
7. V6/V7/V8 workflow/approval-era records must not be surfaced as a new approval workflow absent an explicit plan change.
8. Hibernate generation is not a substitute for Flyway review.

## Current duplicate-authority risks to keep fenced

- `JournalTransaction` / `PostingLine`: compatibility model, never a second production ledger.
- legacy reconciliation-run family: compatibility/history only where a current consumer remains.
- legacy period-close-run family: compatibility/history only; `PeriodCloseRangeService` is production authority.
- `ScheduleEligibilityService` and historical schedule schema: compatibility query/data only; no Schedules destination.
- `ApprovalAuditRecord`: compatibility history only; `AuditEvent` is factual production audit authority.
- deprecated `MainWindow`: session facade only; production shell is `ProductionWorkspaceWindow`.

When a future slice proves the last current consumer of one of these compatibility families has disappeared, removal may be planned. Persistence cleanup must remain nondestructive unless an explicit migration says otherwise.
