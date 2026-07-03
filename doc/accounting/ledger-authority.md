# Ledger authority

## Decision

The canonical writable ledger for accepted accounting activity is the JPA/Hibernate `Txn` header and `TxnSplit` line model backed by the `txn` and `txn_split` H2 tables.

All P02 and later accepted accounting writes must enter H2 through the canonical transaction command service built on this model. Existing `PostingService` behavior is therefore treated as the seed implementation path to be replaced or wrapped by the P02 command service in later slices, not as a second ledger.

## Rationale

`Txn` and `TxnSplit` are already the model used by current ledger queries, financial reports, fund balances, corrections, period policy, and audit history. They also reference stable database identifiers for accounts, funds, counterparties, activities, merchants, budget categories, and bank accounts. Keeping this model canonical avoids a disruptive migration before the transaction editor, report library, import acceptance, reconciliation, open-item, and correction phases are wired together.

The alternate `JournalTransaction` and `PostingLine` tables are append-only UUID records keyed by account and fund codes. They are useful for deterministic open-item and schedule projections, but they do not carry the stable JPA master-data relationships required for the authoritative ledger and currently overlap transaction semantics. They must not become independently writable accounting truth.

## Scope of authority

Authoritative accepted accounting transactions are:

- `txn` rows for transaction headers;
- `txn_split` rows for accounting lines;
- related correction, period, reconciliation-protection, and audit rows that reference the canonical transaction identifier.

A valid canonical transaction must have:

- one header;
- at least two meaningful nonzero lines;
- `BigDecimal` money;
- one-sided debit/credit input at the command boundary;
- balanced debit and credit totals after account normal-balance conversion;
- stable database IDs for accounts and funds;
- a complete rollback on any validation or persistence failure.

## Treatment of retained journal tables

The `journal_transaction` and `journal_posting_line` tables created by V4 are retained as compatibility/projection tables for existing open-item and schedule tests. They are not the source of truth for accepted ledger entries.

Until a later slice deliberately replaces them, journal records may be used only as derived or compatibility inputs for open-item projections. New UI workflows, imports, reports, corrections, and reconciliation features must not write accepted accounting activity directly to `journal_transaction` or `journal_posting_line`.

When a future phase needs open-item or schedule state from canonical transactions, it must project from `txn` and `txn_split` or write a documented bridge inside the same transaction as the canonical ledger write. It must not ask users to enter the same accounting event twice.

## Compatibility and migration policy

No destructive data migration is required for P02-S1. Existing databases keep both schemas. Later migrations must be nondestructive and may add bridge columns, projection tables, or backfill metadata only after tests prove upgrade safety.

For existing data:

1. `txn` and `txn_split` remain authoritative for reports and transaction corrections.
2. `journal_transaction` and `journal_posting_line` remain historical compatibility/projection rows.
3. If both models contain similar business events, the `txn`/`txn_split` record wins for accounting balances and user-facing ledger history.
4. Any conversion from journal rows into canonical transactions must be explicit, idempotent, audited, and covered by migration or service tests.

## Required service boundary

P02-S2/P02-S3 must create or complete one command service for canonical transaction writes. That service owns validation, persistence, period checks, reconciliation protection, correction policy, and audit hooks.

The following are forbidden after this decision:

- adding a new writable ledger model;
- adding accepted accounting writes to sidecar files or static UI collections;
- adding UI save actions that write directly to `journal_transaction` or `journal_posting_line` as accounting truth;
- implementing reports or balances that combine both transaction models as if both were authoritative.

## Open follow-up for later P02 slices

- Define command DTOs and line DTOs with explicit debit/credit input.
- Replace signed amount entry at the public command boundary with one-sided debit/credit validation while preserving signed storage in `TxnSplit`.
- Add query projections that read only canonical ledger tables for transaction editor, ledger register, and journal views.
- Fold period, correction, reconciliation-protection, and audit behavior into the canonical command service.
