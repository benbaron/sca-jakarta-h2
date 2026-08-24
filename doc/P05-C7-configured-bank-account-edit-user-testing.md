# P05-C7 configured bank-account edit/update — owner desktop verification

## Purpose

Verify that Banking maintains an existing configured bank account by stable identity instead of attempting a second insert, and that the touched Banking money/date controls obey active-company display preferences.

This checklist does not validate P05-C8 canonical bank-ledger activity. Until P05-C8 is implemented, **Bank Transactions** continues to show durable imported/review statement rows rather than every ledger transaction involving the linked bank account.

## Before testing

1. Stop every running copy of the application.
2. Because the defect report included SQL column names that do not exist in the current `CompanyBankAccount` mapping, remove stale compiled output before retesting: run **Project -> Clean** and **Maven -> Update Project** in Eclipse (or otherwise clean `target/`) and rebuild.
3. Open the same migrated database used to reproduce the issue and select the intended company.
4. Confirm the chart account to be linked is a qualifying `ASSET` / `BANK`-function / `DEBIT` account. `CASH` is expected for an ordinary checking/savings account but is not required for banking eligibility.

## Existing configured bank-account edit

1. Open **Banking**.
2. In **Configured bank accounts**, select an existing row such as `mybank -> Cash`.
3. Confirm the lower editor enters edit mode and loads the same Bank, Chart account, masked account, nickname, opening date/balance, import format, OFX IDs, notes, and active state.
4. Change at least the nickname or masked account and one additional field.
5. Choose **Update Bank Account**.
6. Confirm the operation succeeds without a unique-index/primary-key SQL error.
7. Confirm the configured-bank-account table still contains exactly one row for that linked chart account, rather than a newly inserted duplicate.
8. Reselect the row and confirm the edited values reload.
9. Restart the application, reopen Banking, and confirm the same edited row and values remain.
10. Select an existing configured bank-account row so its editor enters edit mode, then select a different row in **Financial institutions**. Confirm the configured-account editor leaves the previous edit context, selects the newly chosen institution, and presents a clean new-account form. Saving from that state must not mutate the previously selected configured bank account.

Expected: the existing `CompanyBankAccount` database ID and portable identity are preserved while mutable configuration fields are updated, and switching financial-institution context cannot accidentally reuse a stale configured-account ID.

## Controlled duplicate protection

1. Choose **New Bank Account**.
2. Select a Bank and intentionally select a Chart account that is already linked to another configured bank account for this company.
3. Save.

Expected: the application gives a controlled explanation that the Chart account is already linked. It must not expose the raw H2 unique-constraint SQL as the normal validation path, and it must not create a partial or duplicate row.

## Company money/date formatting

1. In Settings choose a non-default date presentation, then return to Banking.
2. Select an existing configured bank account with an opening date and confirm the editor displays that date in the company format.
3. Enter the date using another accepted form, move focus away, and confirm it normalizes to the company format.
4. Enter an opening balance using grouping and/or the configured currency symbol, move focus away, and confirm it normalizes to the company money format with two decimal places.
5. Choose **New Bank Account** and confirm the opening balance starts as company-formatted zero rather than a hard-coded `$0.00` or `0.00`.

## Lifecycle behavior

1. Edit an existing configured bank account and clear **Account active**.
2. Update it, refresh/restart, and confirm the row remains present but inactive.

Expected: P05-C7 does not add destructive deletion of banking history. Deactivation remains the safe lifecycle action until the separately governed lifecycle work defines any legal physical-delete case.

## Explicit P05-C8 boundary

For an existing linked bank account such as `mybank -> Cash`, it is still expected in P05-C7 that **Bank Transactions** can show zero durable review rows when no statement has been imported. P05-C8 is the queued correction that will add canonical ledger-bank activity as a separate view from imported Statement Review.
