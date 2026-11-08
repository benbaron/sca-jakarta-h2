# Accounting periods and correction policy

## Active period

The Period Close workspace owns the shared active accounting period used for transaction-entry and reporting defaults. Its selector chooses a calculated month, not an authoritative `accounting_period` row and not an individual day. The active period start date is calculated from the selected year/month and the period start day configured in Settings. Browsing reports or historical activity does not change it. After startup initialization, the active period changes only through an explicit Set Active Period action.

## Workflow date defaults (P10-C2)

| Workflow/date | New default | Existing draft/history |
|---|---|---|
| Journal, fund transfer, merchant settlement, inventory movement, asset lifecycle/reversal accounting | Selected period start | Retain entered dates across period changes |
| New bank-funded asset purchase | Selected period start when choosing NEW_PURCHASE, only if acquisition date is untouched | Manual dates and loaded acquisitions are never reset; master/opening/existing-entry routes keep factual dates |
| Recognition run-through; check-review through | Selected company's capped period end | Retain edited or open draft cutoff; service/delivery/age/evidence dates remain explicit |
| Bank statement export | Selected period start through capped end | Follow new period selections/refresh of current company fiscal settings only until either date is edited/cleared; refresh never replaces custom dates |
| Historical acquisition, custody, conversion, imported statements | Factual or explicitly entered date (today where already proposed) | Never infer a historical fact from the accounting period |

These are editable proposals, not automatic accounting or evidence. Applying a
period does not rewrite an open financial draft, recognition schedule, custom
report/export range, asset master or stored history. Service guards continue to
reject closed accounting dates; defaults never reopen them or alter authorization.

## Period-close authority

### P10-C1 calculated-period consistency

`FiscalPeriodRange` is the shared calculation for reports, Dashboard, Period
Close and Depreciation. Preserve its existing fiscal-year cap: the inclusive end
is the earlier of the day before the next monthly start and the fiscal-year end.
For example, a June 15 start with a July 1 fiscal year ends June 30 in all four
workflows. This correction does not redesign the fiscal calendar or create
additional selectable stub periods.

Selecting a candidate month does not apply it. Set Active Period applies Settings' start day and shows the fiscal start/end locally, without changing existing close dates or entry/review/custom-report drafts. Use Active Period separately copies the selected range into close dates. Changing this shared default never opens, closes or reopens accounting dates. The main toolbar contains no period selector, action or period status.

The desktop initializes the selected month using the loaded period-start-day
preference before creating workspace consumers. Settings changes apply to the
next explicit period selection; an already selected start remains unchanged.
Company/database switches retain that selection and resolve fiscal bounds from
the new company's authority. Consumers must use the selected start, rather than
recalculate it from newer preferences. Browsing and custom date edits do not
change the active selection or reopen a closed range.

New interactive `CALCULATED` closes must have the shared calculated end for
their explicit start and company fiscal year. Edited dates can be closed as
`CUSTOM` after review of those exact dates. Historical imports and exact retries
retain their original dates and history, including legacy calculated ranges.
No persisted close range is rewritten by this correction.

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

Domain-linked transactions whose reversal changes authoritative operational state must be corrected through their owning workflow. In particular, a transaction linked as the original or reversal of a `fixed_asset_lifecycle_event` cannot be edited, deleted, or generically reversed in Journal. **Reverse Selected Lifecycle Event** in Asset Register invokes the canonical reversal service inside the same transaction that links the reversal fact and restores the governed asset status. Closed-period and completed/finalized reconciliation protections remain mandatory.

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

## P26-S1 acquisition protection

New bank purchases respect open dates and finalized reconciliation BANK ranges before atomic canonical entry. Exact accepted request acknowledgement performs no accounting mutation and remains available after closing/reconciliation. Historical existing-entry/register-only metadata creates no new opening ledger. Removing an accepted acquisition link requires open and unreconciled source accounting and no retained depreciation/lifecycle activity; linked basis and generic source transaction rewrites remain blocked. Use the existing lifecycle correction route for subsequent asset history. See [S1](../P26-S1-asset-acquisitions.md).

## Reconciliation coverage (P10-C3)

Accounting close and reopen events do not choose bank statement coverage. New
reconciliation sessions use the same configured bank's earlier finalized statement
end plus one day, otherwise its opening date (with earliest-evidence fallback
when absent). Older unsettled canonical bank lines remain reviewable. Stored
session dates and imported/finalized history stay factual. See the
[banking balance contract](../banking/banking-and-reconciliation.md#balance-calculations).
Reopening a close range still does not release finalized reconciliation protection.
