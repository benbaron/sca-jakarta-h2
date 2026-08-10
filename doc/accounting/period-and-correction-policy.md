# Accounting periods and correction policy

## Active period

The global toolbar period is the active accounting period for transaction entry. The toolbar selects a calculated month, not an authoritative `accounting_period` row and not an individual day. The active period start date is calculated from the selected year/month and the period start day configured in Settings. Browsing reports or historical activity does not change it. The active period changes only through an explicit Set Active Period action.

## Period-close authority

`period_close_range` is the authoritative company-scoped close state. A close range may be:

- `CALCULATED`: normally the start and end dates calculated for the active fiscal month;
- `CUSTOM`: a one-time explicit date range selected by the operator.

Active closed ranges for the same company may not overlap. `AccountingPeriod` rows remain compatibility data and are not the P10 business authority for close enforcement.

Closing a range writes all of the following atomically:

1. the `period_close_range` row;
2. a `period_close_event` row with event type `CLOSED`;
3. an `AuditEvent` with action type `PERIOD_RANGE_CLOSED`.

Reopening updates the close range and writes a `REOPENED` event plus `PERIOD_RANGE_REOPENED` audit history in the same database transaction.

## Closed-period behavior

The default policy is warning-based reopening:

1. A user attempts to enter or modify activity in an authoritative closed range.
2. The application reports that the transaction date belongs to that close range.
3. The user may cancel or reopen the range through the Period Close workspace.
4. After reopening, the original accounting operation may be retried.

Authentication and effective authorization are not governed by the current desktop preference model. The saved closed-period policy is an interaction default for the Period Close workspace, not a privilege grant. A reason is optional under the default policy.

Supported policy levels are:

- `WARN_AND_REOPEN`: allow direct reopening and permit an optional reason;
- `REQUIRE_REASON`: allow reopening only when a reason is supplied;
- `REQUIRE_FORMAL_ADJUSTMENT`: do not reopen directly; require the later formal-adjustment workflow.

The default is `WARN_AND_REOPEN`.

The production Period Close workspace initializes its policy and require-reason controls from the active desktop-session preferences whenever the panel is shown. The visible actor defaults to the factual local operating-system user when available and remains editable; it must not use a fictional privilege or operator identity.

`TransactionEntryService` checks both the original and proposed dates when updating a transaction. `TransactionCorrectionService` checks the relevant original and destination dates for direct edit, delete, and reversal. These checks run inside the same transaction as the ledger change.

A prior-period transaction may be reversed into an open range without reopening its original range. The reversal transaction itself must use an open date.

## Correction methods

The default correction method is direct editing. A user preference may require reversal and replacement instead.

Direct editing preserves the transaction identifier and writes an audit record of material changes.

Reversal creates an opposite transaction linked to the original. The reversal date is selected by the user and defaults to the active accounting period. The user may optionally create a linked replacement transaction.

## Deletion

Every durable maintenance function should expose Delete or explain why deletion is not available. Transaction Delete is governed by the active correction method:

- `DIRECT_EDIT`: Delete may remove the entered transaction after the checks below and must write the audit snapshot in the same transaction.
- Any non-direct correction method: Delete must not hard-delete the entered transaction. The UI must ask whether the user wants to auto-fill a reversing entry, default the reversal date from the active accounting period, and perform the reversal if the user confirms. If the user declines, no ledger change is made.

For `DIRECT_EDIT`, the **Confirm before deleting an entered transaction** preference controls only the extra JavaFX confirmation prompt. It never bypasses closed-period, reconciliation, audit, or transactional service checks. Reversal remains an explicit confirmed correction even when direct-delete confirmation is disabled.

Entered transactions may be deleted only when the active correction policy permits hard deletion. Deletion must:

- verify that the transaction is not protected by a completed reconciliation;
- reject an authoritative closed-range date until the range is reopened;
- record an audit snapshot before removal;
- delete dependent rows in one database transaction;
- roll back completely on failure.

A transaction included in a completed reconciliation cannot be edited or deleted until that reconciliation is reopened. Reopening a period-close range does not bypass reconciliation protection.
