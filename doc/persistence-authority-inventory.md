# Model and persistence authority inventory

Status: P00 inventory of current main, updated through P03-C6 unified Journal workspace. This document identifies duplicate authority risks, non-H2 stores, and migration hazards before later phases choose canonical models.

## Current persistence map

| Area | Current model/storage | H2 authoritative today? | Duplicate/sidecar risk | Later decision |
|---|---|---|---|---|
| Canonical ledger and Journal workspace | `Txn` and `TxnSplit` JPA entities, `txn_supplemental_line`, `TransactionEntryService`, unified `JournalWorkspacePanel` | yes for accepted transaction headers, lines, and supplemental details | the older `JournalTransaction`/`PostingLine` path remains a parallel-model hazard but is not used by the Journal workspace | preserve P02 authority and retire/remap overlapping journal/open-item tables deliberately |
| Journal/open-item compatibility core | `JournalTransaction`, `PostingLine`, JDBC journal/open-item repositories, V4/V5 migrations | partially; repository exists but is separate from `Txn`/`TxnSplit` | second transaction model can become a parallel ledger | do not add independent writes; later compatibility/migration treatment |
| Corrections/periods | accounting-period/audit/correction entities and V47/V48 | yes where wired | must not bypass canonical ledger or reconciliation protection | P02/P10 |
| Budget categories | `BudgetCategory` JPA plus V45 | yes for categories | categories are not budget targets | P04 |
| Budget targets | `BudgetPlan`/`BudgetLine` JPA entities and `budget_plan`/`budget_line` tables | yes | version activation must remain through `BudgetPlanService`; no sidecar target store remains | P04 persistent budget model |
| Import preview | `ImportPreviewService` in-memory accepted/rejected rows | no by design until acceptance | acceptable staging, but accepted writes must use canonical services | P05/P13 |
| Bank transactions | P05-S1 `bank_import_batch`, `bank_statement_line`, and `import_issue`; current `UiWorkspaceDataStore.bankTransactions` static/session list | H2 schema exists for reviewed import facts; current panel remains unwired | parser normalization, duplicate detection, and review acceptance are still pending | P05 |
| Reconciliation runs | JDBC `ReconciliationRunRepository`, V6/V7 style workflow tables | yes for run records and P06-S2 unresolved report summaries | remaining mismatch-resolution/edit workflow is incomplete | P06/P10 |
| Former Schedules panel | top-level panel, route, navigation item, and schedule runbook sidecar removed in P07 | no active top-level persistence remains | historical V2 schedule/open-item tables remain until a later migration decision | future domain-specific supplemental transaction records, not a Schedules function |
| Fixed assets/depreciation | `FixedAsset` and `FixedAssetDepreciationRun` JPA entities with V55 tables; depreciation runs create canonical `Txn` rows | yes for P08-S1 asset records and completed depreciation runs | old asset/depreciation text sidecars removed from production paths | later hardening: richer disposal/impairment workflows, visual polish, and reports |
| Inventory/supplies | `InventoryItem` and `InventoryMovement` JPA entities with V56 tables; movement records reserve a nullable canonical `Txn` link | yes for P09-S1 item records and movement history | old inventory text runbook removed from production paths | later hardening: financially relevant movement-to-ledger automation and reports |
| Audit/approval | `ApprovalAuditRecord`/repository and approval UI | H2 records exist | approval/rejection semantics conflict with product decision | P10/P12 factual audit history |
| Preferences/app state | `FileAppStateStore`, `UserAppStateStore`, session state | sidecar/user file | not company-scoped H2 preferences | P12 |
| Import/export jobs | `UiWorkspaceDataStore.jobs` static list | no | no durable job diagnostics | P13 |

## Duplicate transaction and journal models

- `Txn`/`TxnSplit` are the canonical JPA ledger model used by financial reports, transaction entry, correction operations, and the unified Journal workspace.
- `txn_supplemental_line` is authoritative for transaction-attached Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability details.
- `JournalTransaction`/`PostingLine` and JDBC journal repositories remain a separate compatibility path introduced for open-item and schedule work.
- P03-C6 does not use or write the compatibility path. No second writable ledger is introduced.

## Unified Journal authority

- `JournalWorkspacePanel` queries `TransactionEntryService.search(...)` and `load(...)`.
- New and edited entries write through `TransactionEntryService.enter(...)` and `update(...)`.
- Delete and reverse operations write through `TransactionCorrectionService` and retain period/reconciliation protection.
- The panel stores only UI preferences such as divider positions and table state outside H2; unsaved editor rows are dirty UI state, not accepted accounting data.
- `LEDGER_REGISTER` and `TXN_EDITOR` are compatibility destination aliases only. They do not identify separate data stores or panels.

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
5. V49 through V58 are occupied by reconciliation, budget, bank import, fixed asset, inventory, and transaction-supplemental migrations. These migrations must remain nondestructive.
6. Any later schema change needs a new nondestructive migration and in-memory upgrade test.
7. Hibernate generation must not be treated as a substitute for Flyway review.

## Sidecar/static stores to eliminate or confine

- `UiWorkspaceDataStore`: bank transactions and import/export jobs remain sidecar/static session lists.
- Unified Journal draft state: acceptable only as unsaved UI state; accepted headers, lines, and supplemental details must be written through `TransactionEntryService` to H2.
