# Accounting periods and correction policy

## Active period

The global toolbar period is the active accounting period for transaction entry. The toolbar selects a period, not an individual day. The active period start date is calculated from the selected year/month and the period start day configured in Settings. Browsing reports or historical activity does not change it. The active period changes only through an explicit Set Active Period action.

## Closed-period behavior

The default policy is warning-based reopening:

1. A user attempts to enter or modify activity in a closed period.
2. The application warns that the period is closed.
3. The user may cancel or reopen the period.
4. Reopening is recorded in audit history.

Any user who may enter transactions may reopen a period under the default policy. The user chooses the reopening scope. A reason is optional by default.

Supported policy levels are:

- warn and allow reopening;
- require a reopening reason;
- require a formal adjustment workflow.

The default is warn and allow reopening.

## Correction methods

The default correction method is direct editing. A user preference may require reversal and replacement instead.

Direct editing preserves the transaction identifier and writes an audit record of material changes.

Reversal creates an opposite transaction linked to the original. The reversal date is selected by the user and defaults to the active accounting period. The user may optionally create a linked replacement transaction.

## Deletion

Every durable maintenance function should expose Delete or explain why deletion is not available. Transaction Delete is governed by the active correction method:

- `DIRECT_EDIT`: Delete may remove the entered transaction after the checks below and must write the audit snapshot in the same transaction.
- Any non-direct correction method: Delete must not hard-delete the entered transaction. The UI must ask whether the user wants to auto-fill a reversing entry, default the reversal date from the active accounting period, and perform the reversal if the user confirms. If the user declines, no ledger change is made.

Entered transactions may be deleted only when the active correction policy permits hard deletion. Deletion must:

- verify that the transaction is not protected by a completed reconciliation;
- handle any closed-period warning or reopening first;
- record an audit snapshot before removal;
- delete dependent rows in one database transaction;
- roll back completely on failure.

A transaction included in a completed reconciliation cannot be edited or deleted until that reconciliation is reopened.
