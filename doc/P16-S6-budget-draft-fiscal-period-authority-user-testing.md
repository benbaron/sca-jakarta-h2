# P16-S6 Owner Desktop Acceptance — Budget Draft and Fiscal-Period Authority

Use this checklist after the exact P16-S6 PR head passes the complete Maven PR Tests gate. Use disposable budget data. A company whose fiscal year does not start in January is preferred for sections C and D.

## A. Stable draft lifecycle

1. Open **Accounting → Budget Editor** for a fiscal year with no budget version.
2. Confirm loading/refreshing does **not** create a durable budget automatically; the UI invites an explicit **New Draft**.
3. Create a draft, edit one category amount, and choose **Save Draft Amount**.
4. Confirm the same version remains selected after save and refresh.
5. Leave and reopen Budget Editor and confirm the same draft ID/version and saved line are present rather than a new blank draft.

## B. Explicit revision and activation

1. Activate a draft, then select that active version.
2. Confirm direct amount editing is disabled for the active version.
3. Choose **Create Revision** and confirm a new DRAFT version is selected with the active plan's existing amounts copied exactly.
4. Change and save one amount in the revision; confirm the prior active version remains unchanged.
5. Choose **Activate Version** while the revision is selected. Confirm that exact draft becomes ACTIVE and the previous active version becomes historical/archived.

## C. Non-January fiscal-year authority

1. Configure or use a company with a non-January fiscal start, for example July 1.
2. Select an accounting period in the following calendar year, for example February 2027.
3. Open Budget Editor and confirm the fiscal label/range identifies the fiscal year beginning in 2026 and ending in 2027, rather than calendar 2027.
4. Confirm a newly created draft uses that fiscal start/end range.

## D. Budget vs Actual and active-period authority

1. With the same non-January company, enter/identify budget-category actuals both before and after the fiscal start.
2. Select an accounting period and open **Budget vs Actual**.
3. Confirm actuals start at the company fiscal-year start and stop at the end of the selected accounting period; transactions before the fiscal start or after the selected period are excluded.
4. Change the shell active accounting period and confirm Budget vs Actual refreshes to the new period rather than today's date.

## E. Report defaults and export request

1. Clear any explicit global date-range filter and open **Report Library**.
2. Confirm the default start date is the current fiscal-year start and the default end/as-of date is the selected accounting-period end.
3. Run a report, export it, and confirm export uses the same visible request dates as preview.
4. Enter an explicit custom report range and confirm it remains user-controlled rather than being replaced by fiscal defaults.

## Acceptance record

- [ ] A. Stable draft save/reload identity passed
- [ ] B. Explicit revision and selected-draft activation passed
- [ ] C. Non-January fiscal-year range passed
- [ ] D. Budget vs Actual selected-period range passed
- [ ] E. Report default/export request alignment passed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S6 DONE or begin P16-S7 until this checklist is accepted and the P16-S6 PR has merged.
