# P26-S4 — Governed prepaid and deferred recognition assistance

## Purpose

P26-S4 adds an explicit recognition assistant for canonical `PREPAID_EXPENSE` and
`DEFERRED_REVENUE` items. It proposes straight-line monthly recognition and saves
one selected proposal through the existing transaction and supplemental-item
authorities. It does not create an automatic scheduler, recurring background job,
second balance, or second ledger.

## Existing authorities

- `Txn` and `TxnSplit` remain the only accounting authority.
- `TxnSupplementalLine` lifecycle facts remain the only item identity and movement
  authority.
- `SupplementalOpenItemQueryService` remains the derived balance authority.
- `TransactionEntryService` owns posting validation, company locking, periods,
  authorization, item availability, request replay, audit, and commit/rollback.
- `TransactionCorrectionService` owns reversal and replacement.
- Existing SCLX transaction and supplemental lifecycle sections preserve accepted
  recognition entries and their corrections.

The donor application has grant deferral fields and report-oriented schedule
configuration, but no compatible canonical prepaid/deferred recognition authority.
No donor persistence or schedule model is imported.

## Recognition request

The operator selects:

- Prepaid Expense or Deferred Revenue;
- one authoritative open item;
- service start and end dates;
- a run-through date;
- an active company-owned destination EXPENSE account for prepaid recognition or
  INCOME account for deferred recognition; and
- an explicit AR/OA/FR expense function for prepaid recognition.

The assistant never guesses a destination account or expense function. The item's
opening allocation supplies its Fund and optional Event and Budget Category. Those
dimensions are copied to both generated lines. Deferred revenue has no expense
function because its destination is income.

## Straight-line rule and rounding

One installment exists for each calendar month touched by the inclusive service
range. Its recognition date is that month's final day, clipped to the service end
date. A partial first or final month remains one equal monthly installment; the
assistant does not invent daily proration.

The authoritative item increases are divided equally at four-decimal precision
using `HALF_UP`. The final installment receives the exact residual, so all
installments total the item increases exactly. Existing recognized reductions are
applied to the earliest installments first. A partially recognized installment
produces only its remaining amount.

Only positive installments dated on or before the run-through date are proposed.
An empty preview is a factual result, not a posting.

## Canonical entries

For prepaid recognition:

- debit the selected EXPENSE account;
- credit the opening PREPAID asset account; and
- attach a `PREPAID_EXPENSE` `DECREASE` supplemental allocation to the prepaid
  control line.

For deferred revenue recognition:

- debit the opening DEFERRED_REVENUE liability account;
- credit the selected INCOME account; and
- attach a `DEFERRED_REVENUE` `DECREASE` supplemental allocation to the deferred
  control line.

The accepted transaction retains the opening Fund, Event, and Budget Category on
both lines. Prepaid expense retains the explicit functional classification on its
expense line. Its memo, supplemental reference, and notes identify the item and
recognition date without creating a separate durable recognition record.

Each preview proposal carries one request identity retained across save retries.
The transaction service makes an exact retry idempotent. A fresh concurrent preview
has a different request identity but is rejected atomically if another acceptance
consumes the available item balance first.

## Cancellation and correction

- Cancelling or discarding a preview writes nothing.
- A saved recognition is corrected or cancelled with the existing canonical
  reversal/replacement workflow.
- Reversal restores the item balance on the reversal date. A later fresh preview
  can propose the reopened amount.
- Repeating a preview after completed recognition returns no duplicate proposal
  because the derived reductions already consume the installments.
- Deleted create receipts are not resurrected by retry.

## Rejection and visibility rules

Preview rejects or clearly reports:

- unsupported or non-authoritative items;
- a closed, over-applied, or cross-company item;
- missing/invalid dates, destination account, or prepaid expense function;
- a destination account of the wrong type or an inactive/non-posting account;
- an opening allocation whose account/subtype or Fund authority is inconsistent;
  and
- recognition already exceeding the straight-line total.

Save revalidates through canonical entry. Closed/finalized periods, stale balance,
concurrent recognition, ownership changes, invalid classifications, and late
posting failures roll back without a partial accounting entry.

## UI and Save contract

`Recognition Assistant` is a Planning destination. It provides independently
scrollable item/proposal regions, explicit Preview and Refresh commands, and a
`Save Selected Recognition` button. The shared File > Save command invokes the
same selected-proposal save path. VIEWER remains read-only. An accepted preview
is treated as unsaved work for workspace navigation protection.

The panel explains that rules are transient and that corrections use Journal. It
does not revive the retired Schedules destination or imply unattended posting.

## Portability

No new durable recognition configuration exists. Accepted entries use existing
portable transaction identities, transaction budget/function extensions, and
supplemental lifecycle item links. Existing SCLX export/import therefore preserves
recognition accounting, item effects, dimensions, corrections, and exact retry
facts without a new extension. A preview that was never saved has no portable fact.

## Acceptance and automated coverage

- A 1,200.0000 prepaid from January through December produces twelve 100.0000
  proposals and closes at exactly zero after all are accepted.
- Repeating a completed run produces no proposals or duplicate transactions.
- Partial-month, non-even division, prior partial recognition, reversal, and exact
  retry behavior preserve exact totals.
- Deferred recognition uses the opposite control/destination direction.
- Missing function/account classification, wrong-company/wrong-type accounts,
  closed periods, stale/concurrent over-recognition, and VIEWER saves fail without
  residue.
- Fund/Event/Budget/expense-function tags and supplemental item links survive
  reload, correction, restart, and SCLX round trip.
- Actual JavaFX coverage exercises Preview, selected Save/shared Save, role gates,
  and reachable scroll/split layout.

The cross-slice behavioral inventory is maintained in
[P25/P26 unit coverage](P25-P26-unit-coverage.md).

## Owner checks

Use a disposable company. Open one 1,200 prepaid item covering twelve months and
one deferred-revenue item. Preview through the final month, save selected entries,
and verify the Journal and open-item reports. Repeat preview after completion,
reverse one recognition in Journal, preview again after the reversal date, and
export/import SCLX. Confirm exact balances, tags, no duplicate recognition, and
clear closed-period and missing-classification messages.
