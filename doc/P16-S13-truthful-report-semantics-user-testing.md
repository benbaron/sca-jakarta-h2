# P16-S13 truthful report semantics — owner desktop checklist

Use a disposable or backed-up database containing at least one bank transaction, one ordinary multi-fund transaction, and one posted fund transfer. Complete this checklist on the exact pull-request head after Maven PR Tests pass.

## Bank Account Activity

1. Open **Report Library** and select **Bank Account Activity (SCA workbook)**. Confirm the former **All Checks/Transfers** title is not shown.
2. Choose a date range and All Funds. Confirm every detail row names a BANK account and no income, expense, liability, or equity split appears merely because it shares the transaction.
3. Choose one fund. Confirm only BANK splits assigned to that fund remain.
4. Confirm the displayed debit and credit totals equal the visible BANK detail rows. Reduce the row limit and confirm the explicitly labeled displayed total changes with the returned rows.
5. Include a reversed/corrected bank transaction in the range. Confirm the original and reversal appear as their own canonical BANK facts; no memo or payee heuristic reclassifies other entries.

## Fund Transfers

6. Select **Fund Transfers** for a range containing an explicit posted transfer. Confirm the transfer appears as exactly one negative source leg and one equal positive destination leg with the linked transaction ID.
7. Confirm per-fund totals equal the displayed transfer legs and **All funds net** is zero.
8. Confirm ordinary multi-fund journal activity is absent unless it has an explicit POSTED `FundTransfer` linked to its canonical transaction.
9. Confirm draft, void, unlinked, out-of-range, and other-company transfer records are absent.
10. Select an empty range. Confirm no invented detail appears and the report shows only its explicit zero summary.

## Preview, export, and drill-through

11. Without changing parameters, export each report as TEXT, CSV, PDF, and XLSX. Confirm each format contains the same selected detail and totals as preview.
12. Change a parameter after preview, then export. Confirm export uses the new immutable request rather than the stale preview.
13. Choose **Drill to Journal** and confirm the Journal opens with the exact report name, date range, fund selection, and row-limit context.
14. Restart the application, reopen the reports, and confirm the same persisted accounting facts produce the same results.

Record the exact tested commit, operating system, Java version, and pass/fail notes in the PR before merge.
