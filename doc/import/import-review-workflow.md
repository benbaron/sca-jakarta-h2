# Import review workflow

## Scope

Raw parse and mapping preview remains in memory for the current application session. Once the user
explicitly commits a valid OFX/QFX or mapped bank CSV preview, the complete review batch, statement
rows, and issues are persisted to H2 atomically; this durable review commit is not ledger acceptance.

## Row model

Each staged row records:

- source file and source row;
- stable source identifier when available;
- deterministic fingerprint otherwise;
- parsed values;
- validation errors and warnings;
- exact-duplicate and probable-duplicate status;
- user edits made during review;
- final disposition.

Valid and invalid rows remain together so the user can correct or reject individual rows without losing the rest of the import.

## Duplicate detection

Exact duplicates are blocked. Identity uses a stable source-system transaction identifier when available; otherwise it uses a deterministic content fingerprint.

Probable duplicates compare:

- date within a configured range;
- amount;
- payee;
- account;
- reference.

A probable duplicate is a warning, not an automatic rejection.

## Review actions

For each staged row, the user may:

- edit and accept it as a new entered transaction;
- reject it;
- match it to an existing transaction;
- cancel and leave it unresolved for the current session.

When a row matches an existing transaction, the application asks whether to:

- discard the imported row;
- save it as a distinct copy;
- cancel for manual review.

Saving as a copy records that the user intentionally accepted the duplicate-like item and prevents the duplicate detector from rejecting the same reviewed row again.

## Session end

Closing the import review or application with unresolved staged rows prompts the user before discarding the in-memory session.

Durably committed bank review rows survive restart and company switching. They remain scoped to their
owning company and configured bank account and require a later explicit accept or match action before
any canonical `Txn`/`TxnSplit` effect.
