# P16-C10 SCLX preview resolutions — owner desktop checklist

Use a workbook copy and the revised `SCLX_Ledger_IO_v14_canonical_import.bas`. Keep the original v13
module and original export unchanged for comparison.

## Clean workbook export

1. Import the v14 module into the supplied Caer Galen workbook copy and run `ExportSCLX`.
2. Choose the intended Cash asset account once when prompted.
3. Confirm the completion summary reports skipped nonposting annotation rows, skipped transactions
   outside the reporting period, linked supplemental details, and excluded unsupported schedules.
4. Preview the new file in SCA Bookkeeping. Confirm there is no
   `SCLX_DONOR_ACCOUNT_TYPE_UNSUPPORTED`, `SCLX_DONOR_UNSUPPORTED_SECTION`, or
   `SCLX_DATE_REQUIRED` blocker.
5. In Transactions, select a formerly unbalanced workbook row and confirm it is one transaction with
   the original split line(s) plus one Cash balancing line. There must not be a second Cash transaction.
6. Import, open or reselect Journal, and confirm the 61 January-through-June posting transactions are
   visible. Confirm the five July rows are absent and the two linked supplemental details remain attached
   to their referenced transactions.

The supplied reconciled reference product should show 61 transactions, 13 accounts, 2 funds, 16
counterparties, 2 supplemental details, no `REVENUE` account types, no populated unsupported donor root
sections, and no unresolved account/fund/transaction references.

## Preview-message dispositions

1. Preview the original v13 export and locate the **Preview message** and **Disposition** columns.
2. Leave one blocker at **No change**, re-preview, and confirm it recurs.
3. Choose **Drop record** for one unsupported donor array record, click **Re-preview with SCLX Choices**,
   and confirm only that record's blocker disappears and an applied-disposition message is shown.
4. Choose **Ignore** on a warning and confirm re-preview removes that warning. Confirm **Ignore** is not
   offered for a blocking accounting, ownership, closed-period, or reconciliation error.
5. Where **Make suggested correction** is offered, apply it and confirm the fresh preview states the
   correction. An unsupported correction must remain blocked rather than being silently accepted.
6. Confirm the import confirmation lists effective non-default dispositions and that reopening the file
   starts again from the unchanged source bytes.

## Mapping choices and detail layout

1. In **SCLX Mappings**, locate a source account or fund with compatible existing targets.
2. Confirm **Target / Select** is a combo box whose initial value is the preview recommendation.
3. Select a different compatible target and re-preview. Confirm the mapping row and downstream
   references now use that target.
4. Confirm incompatible targets are absent.
5. Resize the window and mapping columns. Confirm Detail text wraps, its tooltip contains the full text,
   and the table's horizontal scrollbar can still reach all columns.

Do not approve import if a blocking preview message remains. A failed commit must report rollback and
leave no partial imported records.
