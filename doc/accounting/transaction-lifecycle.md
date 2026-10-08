# Transaction, period, import, and reconciliation lifecycle

## Entered transactions

A saved transaction is immediately authoritative. The default lifecycle is:

```text
Entered -> Reversed
```

There is no separate posting or approval state. Direct editing is the default correction policy. A user preference may instead require reversal and replacement. A reversal uses a user-selected date that defaults to the active accounting period and remains linked to the original and optional replacement.

Entered transactions may be deleted only when Settings -> Correction method is `DIRECT_EDIT`. Deletion must first check reconciliation and period state and must create an audit snapshot inside the same database transaction. If the active correction method is not `DIRECT_EDIT`, the Delete affordance becomes a correction prompt: ask whether to auto-fill and perform a reversing entry using the active period as the default reversal date. Confirming performs the reversal through the correction service; declining leaves the original transaction unchanged.

## Unsaved work

When a user leaves an unsaved editor, the application asks whether to save a draft. Drafts are editor work, not authoritative ledger transactions.

## Closed periods

The default closed-period policy is a warning that offers to reopen the period. Any user who may enter transactions may reopen it. The user chooses the reopening scope. Reopening reasons are configurable.

A stricter user or organization preference may require a reason or a formal adjustment workflow. Closure and reopening events are always audited.

## Imports

Raw import preview is held in memory for the current session. Valid and invalid rows remain together with row-level errors. After explicit exact-scope confirmation, bank-statement imports atomically create durable review facts in `bank_import_batch`, `bank_statement_line`, and `import_issue`; they do not create canonical ledger transactions. Exact duplicates use a stable source identifier when available and otherwise use a deterministic fingerprint. Probable duplicates compare date range, amount, payee, account, and reference.

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


### Internal fund-transfer links (P25-S1)

A canonical transaction linked to an existing FundTransfer is retained: both generic correction direct-edit/delete and entry-service update reject it. Reverse-and-replace rejects it; enter a corrected transfer through Fund Transfers after reversal. A generic Journal reversal or transfer-specific reversal creates the inverse-direction FundTransfer in the same transaction as its canonical opposite lines and audit history. A reason is required for this linked correction. Original facts remain reportable for original dates, reversal facts appear on the correction date, and reversing a reversal restores direction. Current fund retirement/restriction does not prohibit historical correction; correction does not rerun new-transfer availability policy. Existing reconciliation and open-reversal-period protections still apply. See [Internal fund transfers](../funds/internal-fund-transfers.md).
