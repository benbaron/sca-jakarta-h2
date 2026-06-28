# Transaction, period, import, and reconciliation lifecycle

## Entered transactions

A saved transaction is immediately authoritative. The default lifecycle is:

```text
Entered -> Reversed
```

There is no separate posting or approval state. Direct editing is the default correction policy. A user preference may instead require reversal and replacement. A reversal uses a user-selected date that defaults to the active accounting period and remains linked to the original and optional replacement.

Entered transactions may be deleted when the user chooses that correction method. Deletion must first check reconciliation and period state and must create an audit snapshot inside the same database transaction.

## Unsaved work

When a user leaves an unsaved editor, the application asks whether to save a draft. Drafts are editor work, not authoritative ledger transactions.

## Closed periods

The default closed-period policy is a warning that offers to reopen the period. Any user who may enter transactions may reopen it. The user chooses the reopening scope. Reopening reasons are configurable.

A stricter user or organization preference may require a reason or a formal adjustment workflow. Closure and reopening events are always audited.

## Imports

Import staging is held in memory for the current session. Valid and invalid rows remain together with row-level errors. Exact duplicates use a stable source identifier when available and otherwise use a deterministic fingerprint. Probable duplicates compare date range, amount, payee, account, and reference.

When an imported row matches an entered transaction, the user may discard it, save it as a copy, or cancel for manual review.

## Reconciliation

The reconciliation lifecycle is:

```text
Draft -> In Progress -> Completed
```

A completed reconciliation may be reopened after warning and confirmation. Transactions included in a completed reconciliation cannot be edited until the reconciliation is reopened.

Completion expects a zero difference by default. A user may record a documented nonzero override; current role information does not enforce that action.

## Notes and audit

Any auditable business record may have one editable notes field. Material changes, imports, corrections, deletions, reversals, period actions, reconciliation actions, and database switches are written to audit history.
