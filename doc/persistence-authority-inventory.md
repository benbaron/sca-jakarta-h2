# Model and persistence authority inventory

Status: P00 inventory of current main, updated through P04 budget persistence. This document identifies duplicate authority risks, non-H2 stores, and migration hazards before later phases choose canonical models.

## Current persistence map

| Area | Current model/storage | H2 authoritative today? | Duplicate/sidecar risk | Later decision |
|---|---|---|---|---|
| Legacy ledger | `Txn` and `TxnSplit` JPA entities, ledger query service | yes for existing ledger queries | competes with journal transaction tables | P02 selects canonical writable ledger |
| Journal/open-item core | `JournalTransaction`, `PostingLine`, JDBC journal/open-item repositories, V4/V5 migrations | partially; repository exists but is separate from `Txn`/`TxnSplit` | second transaction model can become a parallel ledger | P02 compatibility/migration treatment |
| Corrections/periods | accounting-period/audit/correction entities and V47/V48 | yes where wired | must not bypass canonical ledger or reconciliation protection | P02/P10 |
| Budget categories | `BudgetCategory` JPA plus V45 | yes for categories | categories are not budget targets | P04 |
| Budget targets | `BudgetPlan`/`BudgetLine` JPA entities and `budget_plan`/`budget_line` tables | yes | version activation must remain through `BudgetPlanService`; no sidecar target store remains | P04 persistent budget model |
| Import preview | `ImportPreviewService` in-memory accepted/rejected rows | no by design until acceptance | acceptable staging, but accepted writes must use canonical services | P05/P13 |
| Bank transactions | P05-S1 `bank_import_batch`, `bank_statement_line`, and `import_issue`; current `UiWorkspaceDataStore.bankTransactions` static/session list | H2 schema exists for reviewed import facts; current panel remains unwired | parser normalization, duplicate detection, and review acceptance are still pending | P05 |
| Reconciliation runs | JDBC `ReconciliationRunRepository`, V6/V7 style workflow tables | yes for run records | approve/reject workflow language conflicts with no approval queue | P06/P10 |
| Open items/schedules | schedule entities/defaults and JDBC open-item snapshots | partially | multiple item state enums/repositories need one authority | P07 |
| Fixed assets/depreciation | UI sidecar lifecycle/depreciation text lists | no | no stable H2 asset/depreciation authority | P08 |
| Inventory/supplies | UI sidecar movement text list | no | no stable H2 inventory/supplies authority | P09 |
| Audit/approval | `ApprovalAuditRecord`/repository and approval UI | H2 records exist | approval/rejection semantics conflict with product decision | P10/P12 factual audit history |
| Preferences/app state | `FileAppStateStore`, `UserAppStateStore`, session state | sidecar/user file | not company-scoped H2 preferences | P12 |
| Import/export jobs | `UiWorkspaceDataStore.jobs` static list | no | no durable job diagnostics | P13 |

## Duplicate transaction and journal models

- `Txn`/`TxnSplit` are the JPA ledger model used by current financial reports and ledger queries.
- `JournalTransaction`/`PostingLine` and JDBC journal repositories provide a separate domain/repository path introduced for open-item and schedule work.
- P02 must prevent two independently writable ledgers. The safest near-term rule is: do not add writes to the journal path until `doc/accounting/ledger-authority.md` selects canonical authority and compatibility handling.

## Budget model authority

- `BudgetCategory` is a real JPA master-data concept and remains distinct from accounts and activities.
- P04 added `BudgetPlan` and `BudgetLine` as the normalized H2 authority for versioned budget targets.
- Budget editing, activation, dashboard comparison, and Budget vs Actual views must use `BudgetPlanService`; the former text sidecar keyed by fund code is no longer authoritative.

## Import staging versus accepted data

- Import preview can remain in-memory while users review rows.
- Accepted COA imports may write through admin services today; accepted bank/accounting activity must not write static `UiWorkspaceDataStore` rows as accounting truth.
- P05 must persist statement lines and route accepted accounting effects through the P02 canonical transaction service.

## Reconciliation, open item, and schedule authority

- Reconciliation run records are durable, but current UI includes approve/reject controls that should be removed or reworded.
- Open-item snapshot repositories and domain state enums exist before the canonical transaction authority is settled.
- P07 should attach open-item state to the canonical ledger rather than a separate posting model.

## Migration risks

1. V1 plus V45/V47/V48 establish JPA accounting tables for companies, funds, accounts, transactions, periods, audit, and corrections.
2. V4/V5 add journal/open-item tables that overlap transaction semantics.
3. V6/V7/V8 add workflow/approval records that conflict with the plan’s no-approval-queue decision if surfaced as approval workflow.
4. Any later schema change needs a new nondestructive migration and in-memory upgrade test.
5. Hibernate generation must not be treated as a substitute for Flyway review.

## Sidecar/static stores to eliminate or confine

- `UiWorkspaceDataStore`: bank transactions, jobs, and runbook lists.
- `RunbookPersistence`: schedule, asset, depreciation, and inventory text files.
- Session draft in `TransactionEditorPanel`: useful as UI dirty state only, not accepted accounting persistence.
