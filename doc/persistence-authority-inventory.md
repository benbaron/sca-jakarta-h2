# Model and persistence authority inventory

Status: P00 inventory of current main, updated through P16-S4 governed bank-import authority, P16-S2 atomic COA CSV commit, P15-S8-C4 interchange progress, pre-commit cancellation, and laptop-width closure. This document identifies duplicate authority risks, non-H2 stores, and migration hazards before later phases choose canonical models.

## Current persistence map

| Area | Current model/storage | H2 authoritative today? | Duplicate/sidecar risk | Later decision |
|---|---|---|---|---|
| Canonical ledger and Journal workspace | `Txn` and `TxnSplit` JPA entities, `txn_supplemental_line`, `TransactionEntryService`, unified `JournalWorkspacePanel` | yes for accepted transaction headers, lines, supplemental details, and `TxnSplit` bank-cleared facts | P16-S10 projects `bank_cleared`, `bank_cleared_on`, and an exact company/account-consistent native reconciliation session into immutable `TransactionView.Line` values; Journal renders `Not bank`/`Uncleared`/`Cleared`/`Mixed` and never writes cleared state; the older `JournalTransaction`/`PostingLine` path remains a parallel-model hazard but is not used by the Journal workspace | preserve P02/P06 authority and retire/remap overlapping journal/open-item tables deliberately |
| Journal/open-item compatibility core | `JournalTransaction`, `PostingLine`, JDBC journal/open-item repositories, V4/V5 migrations | partially; repository exists but is separate from `Txn`/`TxnSplit` | second transaction model can become a parallel ledger | do not add independent writes; later compatibility/migration treatment |
| Corrections/period close | `period_close_range`, `period_close_event`, `audit_event`, `PeriodCloseRangeService`, canonical transaction services | yes for active company close state and factual history | legacy `AccountingPeriod` rows and period-close run records remain compatibility data but are not the P10 business authority | preserve range authority; retire or remap legacy period/run surfaces deliberately |
| Fund master data | `Fund` JPA entity, stable-ID `FundCommand`, `FundAdminService`, `FundLookupService` | yes for fund identity and lifecycle fields | code-keyed compatibility `upsert` remains for older callers but the production editor uses stable IDs | preserve referenced funds through deactivation; delete only zero-reference funds |
| Company master data and active selection | `Company` JPA entity, stable-ID `CompanyCommand`, `CompanyAdminService`, `CompanySessionController` | yes for existence, profile, and active/inactive lifecycle | `MultiCompanyState` remains sidecar recent-selection convenience only | require an existing active H2 row for selection; deactivate rather than hard-delete; protect current and last active companies |
| Budget categories | `BudgetCategory` JPA plus V45 | yes for categories | categories are not budget targets | P04 |
| Budget targets | `BudgetPlan`/`BudgetLine` JPA entities and `budget_plan`/`budget_line` tables | yes | P16-S6 keeps draft creation/revision/save/activation in `BudgetPlanService`, selects versions by stable ID, and derives fiscal comparison ranges from company fiscal settings plus the shell-selected accounting period; no sidecar target store remains | P04/P16-S6 persistent budget model |
| Import preview | `ImportPreviewService` remains transient staging for legacy preview families; P16-S2 `CoaCsvImportService` owns the frozen COA CSV preview/commit scope | no by design until acceptance; yes for the resulting account/identity/audit facts after commit | preview data is intentionally in-memory, but accepted COA CSV writes now use one caller-owned transaction instead of independently committing rows | preserve transient preview; require atomic accepted-row commit, idempotent identical recommit, and new preview on source/company/chart/target drift |
| Bank statement review and explicit ledger acceptance | `bank_import_batch`, `bank_statement_line`, `import_issue`, `bank_csv_mapping_profile`; strict statement preview/commit services; `BankReviewQueryService`; P16-S8 `ReviewedStatementAcceptanceService` using existing `bank_statement_line.accepted_txn_id` plus canonical `TransactionEntryService` | yes for committed review facts, retained normalized external identities/PAYEEID/profiles, and explicit accepted-transaction linkage | raw preview remains in-memory and import remains non-posting; normalized CSV restores governed review facts without a mapping profile; explicit one-row acceptance freezes/revalidates source identity and commits canonical `Txn`/`TxnSplit`, accepted link/status, batch disposition, and factual audit atomically; matching/cleared state remains reconciliation-owned | P05/P15-S6/P15-S7/P15-S8/P16-S8 |
| Reconciliation runs | JDBC `ReconciliationRunRepository`, V6/V7 style workflow tables | yes for run records and P06-S2 unresolved report summaries | remaining mismatch-resolution/edit workflow is incomplete | P06/P10 |
| Former Schedules panel | top-level panel, route, navigation item, and schedule runbook sidecar removed in P07 | no active top-level persistence remains | historical V2 schedule/open-item tables remain until a later migration decision | future domain-specific supplemental transaction records, not a Schedules function |
| Fixed assets/depreciation | `FixedAsset` and `FixedAssetDepreciationRun` JPA entities with V55 tables; depreciation runs create canonical `Txn` rows | yes for P08-S1 asset records and completed depreciation runs | old asset/depreciation text sidecars removed from production paths | later hardening: richer disposal/impairment workflows, visual polish, and reports |
| Inventory/supplies | `InventoryItem` and `InventoryMovement` JPA entities with V56 tables; financially relevant interactive movements link to the canonical `Txn` ledger atomically | yes for item records, movement history, and P16-S9 governed quantity/value posting | old inventory text runbook removed from production paths | later inventory reporting only |
| Audit history | `AuditEvent` is company-owned factual JPA audit history with an intrinsic portable UUID; production Audit History queries it through `AuditHistoryService` | yes for factual `audit_event`; legacy `approval_audit_record` remains stored compatibility data only | legacy approval records are a distinct family and are not queried or blended into the production factual history surface | export/query only company-owned `AuditEvent`; keep legacy approval repository/classes compatibility-only unless a future migration deliberately retires them |
| Preferences/app state | `FileAppStateStore`, `UserAppStateStore`, session state, company UI preference/state tables, production `CompanyTableStateBinder` | mixed; all production table order/width/sort state and company display state are H2, while shell convenience state remains a user file | connected database path is factual runtime authority and is read-only in Preferences; `FileAppStateStore.saveDatabaseSession` writes target database/company convenience together only after target preparation succeeds | database switching is owned by `DatabaseSessionController`; no preference editor may change active database authority |
| Former Import/Export Jobs function | panel, route, navigation destination, enum identifier, and `UiWorkspaceDataStore` generic job list removed in P13-S1 | no active generic job store remains | none; domain-specific import, banking, reconciliation, diagnostic, and audit facts remain in their owning models | do not reintroduce generic job tracking |
| Diagnostics and database recovery | `DiagnosticsQueryService.Report`, `DatabaseSessionController`, prepared `UiServiceRegistry` service bundles, and typed `DatabaseRecoveryCommand` dispatch | H2 remains authoritative for datasource/account/fund facts; runtime/session values are factual context only | migration, JPA/service construction, and target active-company resolution happen before activation; a cancelled or failed target retains the prior datasource, company, session path, Diagnostics path, and records | keep recovery explicit and non-destructive; publish database/company selection only with the successfully prepared service bundle |

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

## Factual audit-history authority

- `AuditEvent` is the selected-company authority for material-change audit facts. V61 supplies explicit nullable company ownership for recoverable historical data, and V67 supplies a non-null intrinsic UUID portable identity without rewriting local IDs or polymorphic subject text.
- New JPA and SQL-created business audit events receive a UUID through entity initialization or the H2 default. Existing events are backfilled nondestructively, and duplicate portable identities are rejected.
- SCLX exports only events whose `company_id` is the selected company. Application-global or unresolved historical rows remain outside active-company export rather than being guessed.
- `ApprovalAuditRecord` is a separate legacy workflow-oriented compatibility record and is not substituted for `AuditEvent` in SCLX. P16-S7 removes it from the production Audit History query path; legacy rows are not silently merged with current facts.
- Production Audit History uses `AuditHistoryService` with an inner company join, so other-company and unresolved/null-company events cannot leak into the selected-company view. Filtering by action/entity/actor/date is service-owned, while JavaFX remains SQL-free and read-only.

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

## Production table-state authority

- `PanelFactory` applies `CompanyTableStateBinder` to every canonical production panel root.
- Column order, width, sort direction, and sort priority are stored by active company and stable panel/table keys in `company_ui_state`.
- Journal, Funds, and Company Admin keep their existing richer H2 layout binders and mark their tables as already owned so the shared boundary does not attach a second writer.
- Banking and Inventory no longer use `java.util.prefs.Preferences` for table layout. Recent user-machine state is not migrated because H2 company rows are the authoritative boundary.
- P14-S2 editor split positions use `CompanySplitPaneStateBinder` and `company_ui_state`; unsaved form snapshots remain in-memory dirty UI state and never become a second persistence path.
- P14-S3 financial-view split positions use the same binder and H2 state boundary. Active-company money/date preferences affect presentation and parsing only; they do not alter canonical `BigDecimal`, date/time, ledger, budget, asset, inventory, reconciliation, period-close, import, or audit storage.

## Budget model authority

- `BudgetCategory` is a real JPA master-data concept and remains distinct from accounts and activities.
- P04 added `BudgetPlan` and `BudgetLine` as the normalized H2 authority for versioned budget targets.
- Budget editing and activation use `BudgetPlanService`; P16-S6 adds deterministic company/year draft+active queries, explicit revision copying, serialized activation, and fiscal-range variance through that same authority. Budget vs Actual uses the shared fiscal range. Dashboard retains its established `DashboardQueryService` projection and remains a separate read projection. The former text sidecar keyed by fund code is no longer authoritative.

## Import staging versus accepted data

- Import preview can remain in-memory while users review rows; preview state is not accepted business data.
- P16-S2 COA CSV preview freezes the source SHA-256, active company, target chart, target fingerprint, accepted/rejected rows, validation messages, and confirmation state. **Commit Accepted COA Rows** revalidates the frozen scope and writes every accepted account, `COA_CSV` external identity, and one factual operation audit through `CoaCsvImportService` plus the caller-owned `AccountAdminService` seam in one JPA transaction. Any row, identity, audit, or constraint failure rolls back the whole batch and reports zero committed counts; identical re-preview/recommit is idempotent, while source/company/chart/target drift requires a new preview. P15 Chart-of-Accounts JSON import/export remains a separate unchanged authority.
- Accepted bank statements write durable review facts through the exact-scope OFX/QFX, mapped-CSV, or normalized-CSV service and never through static UI state.
- Bank-statement import does not create canonical accounting effects. Any later promotion of one reviewed statement row into `Txn`/`TxnSplit` remains a separate explicit P02-owned workflow.
- P15-S5-C2 through C10 SCLX import writes one empty target company graph in one caller-owned JPA transaction. Accounts and funds are created before normalized budgets and dependent canonical transactions; activities, counterparties, and merchants are created before transaction relationships; corrections follow transaction creation; fixed assets and completed runs follow their account, fund, and transaction references; inventory history follows its dependencies; banking/reconciliation follows chart, transaction, and statement creation; authoritative period-close and factual audit history are restored after the ledger graph exists.
- SCLX budget categories are created from portable category codes through `BudgetCategoryAdminService`, and plans/lines are created through the caller-owned `BudgetPlanService` boundary. Budget lines preserve optional fund, period month, and exact amount; account-bearing budget lines are rejected because the normalized authority has no account relation.
- Supplemental source `lineOrder` is preserved through the canonical supplemental command. Every imported SCLX entity, transaction, posting line, budget plan/line, supplemental row, bank/review fact, and reconciliation record receives a same-transaction `interchange_identity`; supporting category master rows remain canonical local data. A late failure rolls all business rows, transaction audit facts, identities, and the operation audit event back together.
- C7 recreates banks and configured accounts through `BankConfigurationService`, reviewed batch/line/issue facts through `BankImportReviewService`, imported split clearance through `BankClearedStateService`, and native reconciliation sessions/matches through `BankReconciliationWorkspaceService`. Intrinsic UUIDs and governed timestamps are preserved, while source-machine paths and source user names remain deliberately excluded.
- C8 recreates `period_close_range` and `period_close_event` facts through a caller-owned `PeriodCloseRangeService` seam. Intrinsic UUIDs, range state, actors, reasons, and timestamps are preserved without replaying interactive close/reopen policy or synthesizing duplicate audit events. Every range must have one matching CLOSED event and a reopened range must also have one matching REOPENED event. Existing period-close-only targets now block preview as populated targets.
- C9 recreates company-owned `audit_event` facts through a caller-owned `AuditHistoryService` seam. Intrinsic UUIDs, source timestamps, actors, actions, entity labels, values, and reasons are preserved without replaying historical commands. The imported facts and their `interchange_identity` rows remain distinct from the single new local operation audit that records completion of the import. Existing audit-history-only targets block preview as populated targets.
- Fixed assets and completed depreciation runs are recreated through caller-owned `FixedAssetService` methods in that same transaction. Their intrinsic UUID identities, source timestamps, accounting references, and completed-run transaction provenance are preserved; the importer does not recalculate depreciation or create duplicate canonical transactions.
- C10 restores `Txn.reversalOf` and `Txn.replacementFor` through a caller-owned `TransactionCorrectionService` seam without replaying interactive commands. The complete service is reachable from production JavaFX only from the retained exact, nonblocking, empty-target or wholly identical preview; unsupported populated sections are rejected rather than discarded.

## Reconciliation, open item, and former schedule authority

- Reconciliation run records are durable comparison facts after P06-S2, not an approval queue.
- Native `bank_reconciliation_session` and `bank_reconciliation_match` records are the production reconciliation authority. P16-S3 makes a `FINALIZED` native session immutable through ordinary live mutation APIs; repeated finalization is idempotent, while later correction proceeds through an explicit audited successor that preserves the finalized predecessor.
- P16-S3 validates session/company/configured-account/statement/split scope before mutation and requires exact symmetric relationship state for unmatch. A factual difference explanation does not create or reserve an arbitrary statement/split relationship.
- `bank_import_batch.source_name` is logical/original import provenance, not a temporary parser filesystem pathname. P16-S4 removes external file-import persistence from Reconciliation; logical source provenance is owned by the governed Import Preview review services, while historical SCLX restoration continues to preserve governed source facts and the existing `VARCHAR(260)` constraint remains unchanged.
- `BankReconciliationWorkspaceService` no longer contains CSV/OFX/QIF parsers or constructs imported `BankImportBatch`/`BankStatementLine` rows. Manual statement entry is a separate explicit fact persisted through `BankStatementManualEntryService` inside the reconciliation transaction; imported bank-review facts have one production write authority through Import Preview's canonical services.
- The former Schedules top-level function and schedule runbook sidecar are removed.
- Open-item and deferral concepts return only as domain-specific supplemental transaction records linked to canonical transaction/split IDs.
- Open-item snapshot repositories and domain state enums must not become a second ledger.

## Fixed asset and depreciation authority

- `FixedAsset` records are the H2 authority for asset-register facts.
- `FixedAssetDepreciationRun` records are the H2 authority for completed depreciation runs.
- Interactive monthly depreciation is owned by `FixedAssetService.runMonthlyDepreciation`. It reuses the caller-owned `TransactionEntryService.enter(...)` seam so the canonical transaction header/splits, linked completed run, both portable identities, and factual transaction audit event share one `EntityManager`, one JPA transaction, and one commit-or-rollback decision.
- Duplicate prechecks provide actionable validation, while `uq_fixed_asset_dep_run_period` and portable-identity uniqueness remain database concurrency guards. Any late run, identity, audit, or constraint failure rolls back the complete operation; no canonical transaction is allowed to survive without its depreciation run.
- The old asset lifecycle and depreciation text runbooks are no longer referenced by production code after P08-S1.
- `FlywayMigrationVersionUniquenessTest` guards against duplicate `V#__*.sql` migration versions before service tests cascade into Flyway startup failures.

## Inventory authority

- `InventoryItem` records are the H2 authority for inventory-register facts.
- `InventoryService.createForImport(...)` and `recordMovementForImport(...)` are caller-owned canonical SCLX seams. They preserve source UUID/timestamp metadata and factual movement values without creating an automatic receipt or another canonical transaction.
- `InventoryMovement` records are the H2 authority for receipt, issue, and adjustment history.
- P16-S9 interactive financial movements freeze a non-mutating preview, then commit item quantity, movement, canonical transaction/splits, portable identities, and factual audits in one transaction after lock-time revalidation. Zero-value movements require explicit nonfinancial confirmation and retain a null transaction link.
- Financial correction is append-only: the canonical reversal transaction and inverse adjustment movement commit together. Historical item quantity/value and movement rows are never silently edited.
- The old inventory text runbook and `RunbookPersistence` path are no longer referenced by production code after P09-S1.
- `InventoryMovement.transaction_id` is nullable only because explicitly confirmed zero-value movements may be nonfinancial and historical SCLX provenance may be absent. A populated value is always a real canonical `Txn` relationship; there is no sidecar inventory ledger.

## Migration risks

1. V1 plus V45/V47/V48 establish JPA accounting tables for companies, funds, accounts, transactions, periods, audit, and corrections.
2. V4/V5 add journal/open-item tables that overlap transaction semantics.
3. V6/V7/V8 add workflow/approval records that conflict with the plan’s no-approval-queue decision if surfaced as approval workflow.
4. V2 schedule tables remain as historical schema until a deliberate nondestructive migration retires or remaps them.
5. V49 through V60 are occupied by reconciliation, budget, bank import, fixed asset, inventory, transaction-supplemental, company UI preference/state, and period-close-range migrations. These migrations must remain nondestructive.
6. Any later schema change needs a new nondestructive migration and in-memory upgrade test.
7. Hibernate generation must not be treated as a substitute for Flyway review.

## Sidecar/static stores to eliminate or confine

- Former `UiWorkspaceDataStore`: removed in P15-S6-C4 after Import Preview, Banking, Bank Transactions, and the File-menu route moved to durable H2 review authority.
- Unified Journal draft state: acceptable only as unsaved UI state; accepted headers, lines, and supplemental details must be written through `TransactionEntryService` to H2.
- Legacy period-close run artifacts: compatibility-only; production close state and factual history belong to `period_close_range`/`period_close_event` and `AuditEvent`.

## P15-S0 active-company interchange ownership audit

This audit is the authority gate for selected-company SCLX export and import. It was performed against `main` at `9f3e67e53cf7e96dd41d09abaafb1985535f9fce`. A direct company owner means a non-null `company_id` foreign key to `company`. An indirect owner is acceptable only when every link is mandatory and cross-company references are structurally impossible or service-validated. A code string is not equivalent to a foreign-key owner.

| Record/family | Current ownership path | Ambiguity or cross-company risk | P15-S1 prerequisite |
|---|---|---|---|
| `Company` | root record; globally unique `company.code` | none as root, but code is mutable and is used by some non-FK tables | retain stable `Company.id`; migrate code-owned records to `company_id` or enforce transactional rename/backfill |
| `ChartOfAccounts` | no company column; `Company.activeChartOfAccounts -> ChartOfAccounts` is a one-way optional pointer | a chart can be shared by multiple companies, remain orphaned, or differ from the company whose accounts reference it | add nullable then backfilled `chart_of_accounts.company_id`; reject shared/orphaned active charts; add company-scoped chart identity/uniqueness |
| `Account` and aliases/report/schedule mappings | `Account -> ChartOfAccounts`; no direct company | ownership is only as reliable as chart ownership; `CompanyBankAccount`, assets, inventory, transactions, and splits can reference an account outside the active company's chart | inherit company through owned chart; add same-company service checks and targeted constraints where H2 can enforce them; preserve `(chart_id, code)` uniqueness |
| `Txn` | no company column; references global payee and account | bank account may imply a chart, but it is nullable; non-bank transactions have no owner; reversal/replacement links can cross companies | add/backfill non-null `txn.company_id`; derive only when every split/account/fund reference agrees; quarantine ambiguous rows; add company/date and external-identity indexes |
| `TxnSplit` | `TxnSplit -> Txn` | split account, fund, budget category, activity, merchant, matched statement line, and cleared facts can disagree with the future transaction company | inherit owner through `Txn`; validate all dimensions and matched statement lines belong to that company; reject cross-company writes |
| `TxnSupplementalLine` | `TxnSupplementalLine -> Txn` | safe only after `Txn` is company-owned | inherit through `Txn`; no separate company column required unless query isolation needs it |
| transaction correction links | `Txn.reversalOf` and `replacementFor` | current FKs do not require same-company pairs | add service and migration checks that correction links remain within one company |
| `Fund`, `FundAlias`, `FundTransfer` | no company column; global unique `fund.code`; transfers reference funds and optional `Txn` | funds and hierarchy are global; a transfer can join funds or a transaction from different companies | add/backfill `fund.company_id`; change code uniqueness to `(company_id, code)`; enforce same-company parent/transfer/posted-transaction references |
| `BudgetCategory` and aliases | no company column; global unique code | category code namespace is shared across all companies | add/backfill `budget_category.company_id`; change uniqueness to `(company_id, code)`; scope aliases through category |
| `BudgetPlan` | no company column; uniqueness `(fiscal_year, version_code)` | every company currently shares one budget version namespace | add/backfill `budget_plan.company_id`; change uniqueness to `(company_id, fiscal_year, version_code)`; scope activation queries |
| `BudgetLine` | `BudgetLine -> BudgetPlan`, plus global category/fund | line dimensions can span companies | inherit through owned plan and enforce category/fund same-company references |
| `Activity` | no company column; global unique code | activity namespace and history are global | add/backfill `activity.company_id`; change uniqueness to `(company_id, code)` |
| `Counterparty` | no company column | a person/organization can be reused accidentally across companies; privacy-sensitive export scope is indeterminate | add company ownership or a deliberate shared-party model. P15-S1 defaults to `counterparty.company_id` and company-scoped external identity; do not infer from display name |
| `Merchant` | no company column; global unique name | merchant namespace is global and name matching can merge unrelated parties | add/backfill `merchant.company_id`; change uniqueness to `(company_id, normalized_name)` or a governed company-scoped business key |
| `Bank` | direct `Bank.company` | `CompanyBankAccount.bank` FK does not prove that bank and configured account company match | retain direct owner; add migration validation and service checks for same-company configured-account links |
| `CompanyBankAccount` | direct `company_id`; references `Bank` and `Account` | DB permits its bank to belong to another company and its ledger account to belong to another company's chart | require bank owner equality and account chart owner equality; retain unique `(company_id, account_id)` |
| `BankImportBatch` | direct `company_id`; optional configured account | DB does not enforce batch company equals configured-account company | validate/backfill mismatches; add service checks and, where practical, composite ownership constraints |
| `BankStatementLine` | direct `company_id`; also batch and configured account | direct owner can disagree with batch/account; accepted/matched `Txn` has no current company owner | require line, batch, bank account, accepted/matched transaction to share one company; preserve batch-row/fingerprint uniqueness and add account-scoped source identity |
| `ImportIssue` | `ImportIssue -> BankImportBatch` and optional statement line | issue line could reference a line from another batch/company | inherit through batch; enforce optional line belongs to the same batch/company |
| `bank_reconciliation_session` | direct `company_id`; configured account | DB does not enforce selected account company equality | retain direct owner and validate same-company account |
| `bank_reconciliation_match` | through reconciliation session; references statement line and `TxnSplit` | match can currently cross session company, statement company, and global transaction dimensions | enforce session/statement/split company equality after `Txn` ownership migration |
| compatibility `reconciliation_run` | `group_code` text only | code is mutable and not a foreign key; compatibility record can be misattributed | either add `company_id` and backfill from current company code or explicitly exclude compatibility runs from SCLX; keep production reconciliation session/facts authoritative |
| `txn_reconciliation_protection` | through global `Txn`; UUID run link has no FK | cannot prove company until both transaction and run are owned | inherit from `Txn`; validate linked run/company and preserve completed-reconciliation protection |
| `FixedAsset` | direct `company_id`; references three accounts and a fund | referenced accounts/fund may belong elsewhere | retain direct owner; enforce all referenced dimensions share company |
| `FixedAssetDepreciationRun` | through `FixedAsset`; references `Txn` | run transaction may belong to another company | inherit through asset and enforce transaction company equality |
| `InventoryItem` | direct `company_id`; references account and fund | referenced account/fund may belong elsewhere | retain direct owner; enforce dimension ownership equality |
| `InventoryMovement` | through `InventoryItem`; optional `Txn` | linked transaction may belong to another company | inherit through item and enforce transaction company equality |
| `period_close_range` / `period_close_event` | mutable `company_code` text | no FK; rename service currently preserves code, but stale/manual rows cannot be proven by schema | add `company_id`, backfill by exact active/inactive company code, retain code only as denormalized display if useful; enforce event/range company equality |
| `AccountingPeriod` / `PeriodReopenEvent` | no company column; global uniqueness `(fiscal_year, period_number)` | compatibility period history is global and conflicts across companies | add company ownership and company-scoped uniqueness only if retained for interchange; otherwise explicitly exclude compatibility period structures |
| `AuditEvent` | no company column; generic entity type/ID strings | factual audit events cannot be reliably selected by company and entity IDs can collide by type | add nullable then backfilled `company_id`; require company for new business audit events; retain truly application-global events as explicitly global and exclude them from company SCLX |
| `CompanyTaxProfile`, `UserCompanyRole` | direct company ownership | authentication/authorization material is outside SCLX | no SCLX migration required; whole-database transfer preserves them |
| `company_ui_preference` / `company_ui_state` | mutable `company_code` text | no FK, but these are UI state and excluded from SCLX | no P15-S1 export migration required; whole-database transfer preserves them |

### Global or ambiguous uniqueness requiring company scope

P15-S1 must nondestructively replace or supplement these global business-key constraints after backfill:

- `fund.code`;
- `budget_category.code`;
- `budget_plan(fiscal_year, version_code)`;
- `activity.code`;
- `merchant.name`;
- `accounting_period(fiscal_year, period_number)` if compatibility periods remain company business data; and
- any new external identity, which must be unique by company, format, source system, entity type, and external ID.

`company.code`, application usernames, and role codes remain intentionally global. `account(chart_id, code)` remains chart-scoped and becomes company-safe when each chart has one owner.

### Required nondestructive P15-S1 migration sequence

1. Add nullable ownership columns and supporting indexes without dropping current constraints.
2. Backfill direct derivations from existing company-owned records.
3. For charts, transactions, funds, budgets, activities, parties, merchants, periods, and audit events, run deterministic ownership analysis and write diagnostics for zero-owner, multi-owner, and cross-company rows.
4. Stop and require explicit repair for ambiguous rows; never choose the current UI company as a silent default.
5. Add service-boundary same-company validation before making new ownership columns mandatory.
6. Replace affected global uniqueness with company-scoped uniqueness only after collision diagnostics and explicit nondestructive conflict handling.
7. Add non-null and referential constraints after all supported rows are owned.
8. Add multi-company isolation, migration-upgrade, and cross-company rejection tests.

Until that sequence is merged and verified, active-company SCLX export MUST fail closed for ambiguous sections rather than emit a partial document that appears complete.

### P15-S1 implementation outcome

Migration `V61__company_ownership_and_interchange_identity.sql` implements the nondestructive ownership stage as follows:

- nullable `company_id` foreign keys and indexes now exist for charts, canonical transactions, funds, budget categories/plans, activities, counterparties, merchants, retained accounting periods, business audit events, and close ranges/events;
- deterministic single-owner evidence is backfilled, while zero-owner, multi-owner, and cross-company evidence is retained unchanged and recorded in `company_ownership_issue`;
- affected global business keys are company-scoped;
- `interchange_identity` provides company/format/source/entity/external-ID uniqueness and normalized-content SHA-256 evidence;
- canonical administration, transaction, bank, reconciliation, asset, inventory, period, and lookup services enforce the selected or explicitly supplied company; and
- selected-company interchange must call the unresolved-ownership gate before previewing a complete export.

The migration deliberately does not make every new ownership column non-null. Ambiguous historical rows must remain recoverable and diagnosed rather than guessed. Non-null enforcement is a later migration step after explicit repair has reduced open diagnostics to zero for supported records.

## P15 exchange authority boundaries

- SCLX is selected-company business data reconstructed from current canonical H2 authority after the ownership gate passes.
- Chart of Accounts JSON is chart structure only and uses DTOs; it neither serializes entities nor transfers transaction history.
- Whole-database transfer uses supported H2 backup/restore facilities and preserves every database record, including application administration and compatibility structures.
- OFX/QFX/CSV import persists external statement facts to `bank_import_batch`, `bank_statement_line`, and `import_issue`; it does not automatically create canonical ledger transactions.
- Normalized CSV direct re-import additionally retains exact source batch/line external IDs and PAYEEID in those same durable authorities; matched transaction UUIDs must resolve inside the selected company and are never synthesized.
- No exchange type writes `UiWorkspaceDataStore`, a donor sidecar repository, a parallel journal, static company authority, or generic Import/Export Jobs history.
