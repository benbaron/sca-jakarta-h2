# P22-S5 Supplemental/Open-Item Reporting — Owner Testing

## User-visible changes

- Journal supplemental tabs now maintain canonical open-item lifecycle linkage with **Item ID**, **Effect**, and **Ledger Line**.
- **Add Open Item Detail** creates a new stable Item ID with `INCREASE`.
- **Apply Existing Item** selects a currently open item from the canonical projection and creates a `DECREASE` row for the same Item ID.
- Report Library adds Accounts Receivable, Accounts Payable, Prepaid Expenses, Deferred Revenue, Other Assets, and Other Liabilities as as-of reports.
- Dashboard Open Items now uses the same canonical projection and no longer reads the historical `open_item_snapshot` compatibility data.
- The former `SCHEDULES` panel identifier and unused `ScheduleEligibilityService` runtime surface are retired. No top-level Schedules destination exists.
- Current SCLX remains unchanged and does not preserve Item ID / ledger-split / effect lifecycle linkage. Journal states this limitation explicitly.

## Manual test

1. Open **Journal** for a test company with a Receivable account subtype.
2. Enter a balanced invoice transaction that increases the Receivable account.
3. Select Receivable supplemental detail and choose **Add Open Item Detail**.
4. Confirm Item ID is populated, Effect is `INCREASE`, and enter the 1-based Ledger Line corresponding to the Receivable split. Add reference/counterparty/description/amount and save.
5. Open **Report Library -> Accounts Receivable** as of the invoice date. Confirm the item appears with the full increase and matching open balance.
6. Enter a later partial-payment transaction. In Receivable supplemental detail choose **Apply Existing Item**, select the invoice, set the Ledger Line to the reducing Receivable split, reduce the prefilled amount to a partial payment, and save.
7. Run Accounts Receivable as of a date before the payment and then after it. Confirm the earlier report shows the original balance and the later report shows `increase - reduction`.
8. Enter a final payment using **Apply Existing Item**. Confirm the report shows `CLOSED` and zero open balance.
9. Reverse the partial or final settlement using the configured Journal correction workflow. Confirm the corresponding reduction disappears from the as-of projection and the open balance returns.
10. Exercise reverse-and-replacement on a transaction carrying supplemental lifecycle data. Confirm the replacement retains the same logical Item ID and the report balance is unchanged by the correction itself.
11. Repeat creation/partial settlement for Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability accounts. Confirm each domain-specific report uses the matching account subtype and balance direction.
12. Open **Dashboard** for the same accounting period. Confirm Receivables/Payables and total open items agree with Report Library open rows as of the period end.
13. Switch to another company. Confirm its Dashboard/report open-item data is isolated and the first company's items do not appear.
14. Confirm a historical supplemental row without Item ID/Effect/Ledger Line remains visible when loaded and is reported as `UNMATCHED_LEGACY`, not guessed into an open balance.
15. Confirm there is no Schedules destination/workspace in navigation or routing.
16. Smoke-test normal Journal New/Edit/Save/Delete-or-Reverse behavior, table/divider persistence, Report Library preview, and PDF/XLSX export.
17. Review the Journal lifecycle help text and confirm the current SCLX limitation is clear: SCLX preserves legacy supplemental detail but does not round-trip P22-S5 lifecycle linkage.

## Expected failure behavior

Saving must fail with an explanatory message when lifecycle fields are only partially supplied, the ledger-line number is invalid, the linked account subtype does not match the supplemental kind, the split direction conflicts with INCREASE/DECREASE, allocations exceed the split amount, a DECREASE references no existing item increase, or one Item ID is reused for a different supplemental kind.
