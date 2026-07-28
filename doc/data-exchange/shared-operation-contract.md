# Shared Data-Exchange Operation Contract

Status: governing P15-S1 contract for operation lifecycle, company ownership, diagnostics, and external identity.

## 1. Purpose

P15 exchange families share a small framework-independent operation vocabulary without sharing format DTOs or persistence authority. SCLX, Chart of Accounts JSON, whole-database transfer, and bank-statement interchange remain separate operations with separate parsers, previews, confirmations, commit services, results, and file-selection language.

The shared types live under `org.nonprofitbookkeeping.interchange`. They contain no JavaFX controls, JPA entities, or durable generic job state.

## 2. Operation modes

`InterchangeOperationMode` distinguishes the supported lifecycle intentions:

- preview only;
- validate only;
- commit to the active company;
- create or import into a new company or database where the governing format permits it; and
- export.

A format-specific service must reject a mode that its governing contract does not permit. The shared enum does not authorize every mode for every format.

## 3. Immutable shared results

The shared records provide:

- `InterchangeValidationMessage`: severity, stable code, location, message, and blocking state;
- `InterchangeOperationCounts`: created, updated, skipped, warning, and error counts;
- `InterchangeConfirmation`: explicit confirmation text and satisfaction state;
- `InterchangeProgress`: phase, completed/total units, commit-started state, and cancellation state;
- `InterchangePreview<T>`: format, mode, source/target labels, source SHA-256, immutable preview items, messages, confirmations, and counts; and
- `InterchangeResult<T>`: format, mode, committed/rolled-back state, output label, source SHA-256, immutable result items, messages, and counts.

Constructors defensively copy collections. An `ERROR` validation message is always blocking. Once commit has begun, progress is not cancelable. A result cannot be both committed and rolled back.

These records report one synchronous operation result. They are not an Import/Export Jobs queue, scheduler, retry log, approval workflow, or second persistence model.

## 4. Preview, validation, confirmation, and commit boundary

Every mutating format-specific operation must:

1. identify and hash the source before mutation;
2. parse into format-specific DTOs with bounded resource use;
3. produce a non-mutating preview and validation messages;
4. require every blocking error to be cleared;
5. require every format-specific confirmation to be explicitly satisfied;
6. start one documented caller-owned transaction for the commit boundary;
7. write only through the canonical company, ledger, banking, chart, or administration services; and
8. commit operation data, audit facts, and external identities atomically.

Cancellation is allowed only before commit begins. A failure after commit begins rolls back the caller-owned transaction. Atomic file export writes to a temporary target, validates the completed bytes, and replaces the destination only after success.

## 5. Authoritative company ownership

Flyway migration `V61__company_ownership_and_interchange_identity.sql` adds nullable `company_id` ownership and foreign keys to:

- `chart_of_accounts`;
- `txn`;
- `fund`;
- `budget_category`;
- `budget_plan`;
- `activity`;
- `counterparty`;
- `merchant`;
- retained compatibility `accounting_period`;
- business `audit_event`;
- `period_close_range`; and
- `period_close_event`.

Accounts inherit company ownership through their chart. Transaction splits and supplemental lines inherit through their transaction. Aliases, budget lines, depreciation runs, inventory movements, reconciliation matches, and similar children inherit through their authoritative parent.

New writes use the selected company supplied by `UiServiceRegistry` or an explicit format-specific company context. Services reject references owned by another company. A null legacy owner may be adopted during a write only when the database contains exactly one company and that company is the expected owner. This is deterministic database evidence, not a default derived from the current screen selection.

## 6. Nondestructive backfill and diagnostics

V61 performs only deterministic backfills:

- a sole database company may own otherwise unambiguous legacy master data;
- a chart is owned when exactly one company selects it as active;
- a transaction is owned only when its existing company-owned references identify one company without conflict;
- close ranges and events are backfilled from an exact company code; and
- other records are backfilled only from existing authoritative relationships that identify one company.

Rows with zero, multiple, or conflicting candidate owners remain unchanged. V61 records them in `company_ownership_issue` with entity type, entity ID, issue code, candidate count, details, and detection time. Cross-company references are also recorded. The migration never assigns the active UI company as a silent fallback.

`CompanyOwnershipService.requireNoOpenOwnershipIssues()` is the fail-closed gate for selected-company interchange. A later repair operation may resolve diagnostics deliberately; P15-S1 does not invent or silently repair ambiguous history. Nullable ownership remains necessary for such retained rows. Non-null enforcement is deferred until supported databases have no unresolved ownership diagnostics.

## 7. Company-scoped business keys

V61 replaces global uniqueness with company-scoped uniqueness for:

- fund code;
- budget-category code;
- budget-plan fiscal year and version;
- activity code;
- merchant name/business key; and
- retained accounting-period fiscal year and period number.

Company code, usernames, and role codes remain intentionally global. Account code remains chart-scoped and becomes company-safe because each eligible chart has one owner.

## 8. Same-company service boundary

`CompanyOwnershipService` centralizes company lookup and ownership validation. P15-S1 applies it to the current canonical write and lookup paths for:

- charts and accounts;
- funds;
- budget categories and plans;
- transactions, splits, optional dimensions, corrections, and replacement/reversal relationships;
- configured bank accounts and reconciliation workspaces;
- fixed assets and depreciation transactions;
- inventory items and linked transactions;
- accounting periods and close ranges/events; and
- sample-company creation and active-company UI service composition.

A write that mixes companies fails before commit and rolls back all partial ledger or operational rows. Optional transaction dimensions remain optional; when supplied, each must belong to the transaction company.

## 9. Durable external identity

`interchange_identity` records idempotency and traceability evidence. Its external key is unique by:

- company;
- interchange format;
- source system;
- entity type; and
- external ID.

Each identity stores the normalized content SHA-256 and an optional local entity identifier. Classification is:

- `NEW`: no identity exists;
- `IDENTICAL`: the external key and normalized content hash match; or
- `CONFLICT`: the external key exists with different normalized content.

Recording identical content is idempotent. A conflicting hash or a different already-linked local identity is rejected. Format-specific commit services use the caller-owned `EntityManager` overload so identity creation rolls back with the business write. This table is not a generic job log and does not authorize automatic canonical transaction creation.

Durable local portable identities are separate from source-specific `interchange_identity` rows. `Txn`, `Counterparty`, and `Merchant` carry UUID portable identities that survive mutable business labels and never expose local numeric primary keys. The shared identity table continues to record import-source idempotency and traceability; it is not used as a substitute for an intrinsic local entity identity.

## 10. Validation and operational visibility

P15-S1 validation includes:

- migration from V60 with deterministic single-company backfill;
- multi-company unresolved-owner retention and diagnostics;
- company-scoped uniqueness and collision rejection;
- presence of every required ownership column, foreign key, diagnostic table, and identity table;
- same-company service rejection with atomic rollback;
- external identity idempotency, company isolation, conflicts, and rollback; and
- framework independence and immutability of shared operation records.

P15-S1 introduces no visible import/export command or panel. Later P15 slices consume this contract and must preserve the four distinct operation authorities in `doc/interface-operation-matrix.md`.
