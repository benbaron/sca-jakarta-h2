# Model and persistence authority inventory

Status: P00 inventory of current main, updated through P12-S3 company lifecycle work. This document identifies duplicate authority risks, non-H2 stores, and migration hazards before later phases choose canonical models.

## Current persistence map

| Area | Current model/storage | H2 authoritative today? | Duplicate/sidecar risk | Later decision |
|---|---|---|---|---|
| Canonical ledger and Journal workspace | `Txn` and `TxnSplit` JPA entities, `txn_supplemental_line`, `TransactionEntryService`, unified `JournalWorkspacePanel` | yes for accepted transaction headers, lines, and supplemental details | the older `JournalTransaction`/`PostingLine` path remains a parallel-model hazard but is not used by the Journal workspace | preserve P02 authority and retire/remap overlapping journal/open-item tables deliberately |
| Journal/open-item compatibility core | `JournalTransaction`, `PostingLine`, JDBC journal/open-item repositories, V4/V5 migrations | partially; repository exists but is separate from `Txn`/`TxnSplit` | second transaction model can become a parallel ledger | do not add independent writes; later compatibility/migration treatment |
| Corrections/period close | `period_close_range`, `period_close_event`, `audit_event`, `PeriodCloseRangeService`, canonical transaction services | yes for active company close state and factual history | legacy `AccountingPeriod` rows and period-close run records remain compatibility data but are not the P10 business authority | preserve range authority; retire or remap legacy period/run surfaces deliberately |
| Fund master data | `Fund` JPA entity, stable-ID `FundCommand`, `FundAdminService`, `FundLookupService` | yes for fund identity and lifecycle fields | code-keyed compatibility `upsert` remains for older callers but the production editor uses stable IDs | preserve referenced funds through deactivation; delete only zero-reference funds |
| Company master data and active selection | `Company` JPA entity, stable-ID `CompanyCommand`, `CompanyAdminService`, `CompanySessionController` | yes for existence, profile, and active/inactive lifecycle | `MultiCompanyState` remains sidecar recent-selection convenience only | require an existing active H2 row for selection; deactivate rather than hard-delete; protect current and last active companies |
| Budget categories | `BudgetCategory` JPA plus V45 | yes for categories | categories are not budget targets | P04 |
| Budget targets | `BudgetPlan`/`BudgetLine` JPA entities and `budget_plan`/`budget_line` tables | yes | version activation must remain through `BudgetPlanService`; no sidecar target store remains | P04 persistent budget model |
| Import preview | `ImportPreviewService` in-memory accepted/rejected rows | no by design until acceptance | acceptable staging, but accepted writes must use canonical services | P05/P13 |
| Bank transactions | P05-S1 `bank_import_batch`, `bank_statement_line`, and `import_issue`; current `UiWorkspaceDataStore.bankTransactions` static/session list | H2 schema exists for reviewed import facts; current panel remains unwired | parser normalization, duplicate detection, and review acceptance are still pending | P05 |
| Reconciliation runs | JDBC `ReconciliationRunRepository`, V6/V7 style workflow tables | yes for run records and P06-S2 unresolved report summaries | remaining mismatch-resolution/edit workflow is incomplete | P06/P10 |
| Former Schedules panel | top-level panel, route, navigation item, and schedule runbook sidecar removed in P07 | no active top-level persistence remains | historical V2 schedule/open-item tables remain until a later migration decision | future domain-specific supplemental transaction records, not a Schedules function |
| Fixed assets/depreciation | `FixedAsset` and `FixedAssetDepreciationRun` JPA entities with V55 tables; depreciation runs create canonical `Txn` rows | yes for P08-S1 asset records and completed depreciation runs | old asset/depreciation text sidecars removed from production paths | later hardening: richer disposal/impairment workflows, visual polish, and reports |
| Inventory/supplies | `InventoryItem` and `InventoryMovement` JPA entities with V56 tables; movement records reserve a nullable canonical `Txn` link | yes for P09-S1 item records and movement history | old inventory text runbook removed from production paths | later hardening: financially relevant movement-to-ledger automation and reports |
| Audit/approval | `AuditEvent` is factual JPA audit history; `ApprovalAuditRecord` remains a legacy approval-oriented repository/panel | yes for both stored record types | legacy approval terminology conflicts with product decision outside Period Close | P12 should rename/scope the remaining approval audit surface |
| Preferences/app state | `FileAppStateStore`, `UserAppStateStore`, session state, company UI preference/state tables | mixed; company display state is H2, shell state remains sidecar/user file | shell preferences are not fully company-scoped | P12 |
| Import/export jobs | `UiWorkspaceDataStore.jobs` static list | no | no durable job diagnostics | P13 |

## Duplicate transaction and journal models

- `Txn`/`TxnSplit` are the canonical JPA ledger model used by financial reports, transaction entry, correction operations, and the unified Journal workspace.
- `txn_supplemental_line` is authoritative for transaction-attached Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability details.
- `JournalTransaction`/`PostingLine` and JDBC journal repositories remain a separate compatibility path introduced for open-item and schedule work.
- P03-C6 does not use or write the compatibility path. No second writable ledger is introduced.

## Unified Journal authority

- `JournalWorkspacePanel` queries `TransactionEntryService.search(...)` and `load(...)`.
- New and edited entries write through `TransactionEntryService.enter(...)` and `update(...)`.
- Delete and reverse operations write through `TransactionCorrectionService` and retain period-range/reconciliation protection.
- The panel stores only UI preferences such as divider positions and table state outside ledger tables; unsaved editor rows are dirty UI state, not accepted accounting data.
- `LEDGER_REGISTER` and `TXN_EDITOR` are compatibility destination aliases only. They do not identify separate data stores or panels.

## Period close authority

- V60 adds `period_close_range` as the authoritative company-scoped closed-date-range state and `period_close_event` as factual close/reopen history.
- `PeriodCloseRangeService` owns close, overlap validation, list, reopen-policy enforcement, and factual event/audit writes.
- `TransactionEntryService` and `TransactionCorrectionService` call `PeriodCloseRangeService.requireOpen(...)` inside the same transaction before changing canonical ledger data.
- Reconciliation protection remains an independent prerequisite check; period close does not weaken completed-reconciliation protection.
- `AccountingPeriod` and `AccountingPeriodService` remain compatibility structures but are not the P10 business authority for calculated/custom range close state.
- `PeriodCloseService` and `PeriodCloseRunRepository` remain compatibility run-artifact APIs and are not used by the production Period Close workspace.

## Fund master-data authority

- `Fund.id` is record identity. Fund codes are editable unique business labels, not update keys.
- The production Funds workspace writes through `FundAdminService.save(FundCommand)` and may change a code without creating a second row.
- Parent, effective dates, restriction text, type, and active state remain fields of the same H2 `Fund` record.
- `FundAdminService.usage(...)` counts references from canonical transaction splits, budget lines, fixed assets, inventory items, aliases, transfers, and child funds.
- `deleteUnused(...)` repeats the usage assessment in its transaction and removes only zero-reference funds.
- Referenced funds remain authoritative historical master data and are deactivated rather than deleted.

## Company master-data and selection authority

- `Company.id` is stable record identity. Company code is an editable unique business label; code-keyed UI state and period-close history are renamed transactionally with it.
- `CompanyAdminService.save(...)` persists identity fields, active state, fiscal-year start, and default currency in one transaction.
- The current company cannot be deactivated, and no operation may leave the database without an active company.
- `CompanySessionController` validates selection through `CompanyAdminService.requireActiveCompany(...)` before changing session or workspace context.
- `MultiCompanyState` contains only active codes confirmed in the current H2 database. Missing and inactive recent codes are discarded rather than materialized as companies.
- Open production workspaces are recreated after an active-company change so cached formatting, layout ownership, and company context do not remain bound to the prior company.
- No hard-delete company operation is exposed. Existing foreign keys to company-owned banking, reconciliation, asset, inventory, tax, and role records remain intact.

## Budget model authority

- `BudgetCategory` is a real JPA master-data concept and remains distinct from accounts and activities.
- P04 added `BudgetPlan` and `BudgetLine` as the normalized H2 authority for versioned budget targets.
- Budget editing, activation, dashboard comparison, and Budget vs Actual views must use `BudgetPlanService`; the former text sidecar keyed by fund code is no longer authoritative.

## Import staging versus accepted data

- Import preview can remain in-memory while users review rows.
- Accepted COA imports may write through admin services today; accepted bank/accounting activity must not write static `UiWorkspaceDataStore` rows as accounting truth.
- P05 must persist statement lines and route accepted accounting effects through the P02 canonical transaction service.

## Reconciliation, open item, and former schedule authority

- Reconciliation run records are durable comparison facts after P06-S2, not an approval queue.
- The former Schedules top-level function and schedule runbook sidecar are removed.
- Open-item and deferral concepts return only as domain-specific supplemental transaction records linked to canonical transaction/split IDs.
- Open-item snapshot repositories and domain state enums must not become a second ledger.

## Fixed asset and depreciation authority

- `FixedAsset` records are the H2 authority for asset-register facts.
- `FixedAssetDepreciationRun` records are the H2 authority for completed depreciation runs.
- Depreciation runs use `TransactionEntryService` to create the canonical accounting transaction and then store the run-to-transaction link.
- The old asset lifecycle and depreciation text runbooks are no longer referenced by production code after P08-S1.
- `FlywayMigrationVersionUniquenessTest` guards against duplicate `V#__*.sql` migration versions before service tests cascade into Flyway startup failures.

## Inventory authority

- `InventoryItem` records are the H2 authority for inventory-register facts.
- `InventoryMovement` records are the H2 authority for receipt, issue, and adjustment history.
- The old inventory text runbook and `RunbookPersistence` path are no longer referenced by production code after P09-S1.
- `InventoryMovement.transaction_id` is nullable in P09-S1 so later financially relevant movement automation can link movements to canonical `Txn` rows without introducing a sidecar ledger.

## Migration risks

1. V1 plus V45/V47/V48 establish JPA accounting tables for companies, funds, accounts, transactions, periods, audit, and corrections.
2. V4/V5 add journal/open-item tables that overlap transaction semantics.
3. V6/V7/V8 add workflow/approval records that conflict with the plan’s no-approval-queue decision if surfaced as approval workflow.
4. V2 schedule tables remain as historical schema until a deliberate nondestructive migration retires or remaps them.
5. V49 through V60 are occupied by reconciliation, budget, bank import, fixed asset, inventory, transaction-supplemental, company UI preference/state, and period-close-range migrations. These migrations must remain nondestructive.
6. Any later schema change needs a new nondestructive migration and in-memory upgrade test.
7. Hibernate generation must not be treated as a substitute for Flyway review.

## Sidecar/static stores to eliminate or confine

- `UiWorkspaceDataStore`: bank transactions and import/export jobs remain sidecar/static session lists.
- Unified Journal draft state: acceptable only as unsaved UI state; accepted headers, lines, and supplemental details must be written through `TransactionEntryService` to H2.
- Legacy period-close run artifacts: compatibility-only; production close state and factual history belong to `period_close_range`/`period_close_event` and `AuditEvent`.
