# P21-S2 Event Accounting — owner desktop acceptance

## User-visible changes

- **Accounting -> Event Accounting** opens a read-only workspace for the active company's Activities/events/projects/occasions.
- Active and inactive Activities are selectable by the same stable H2 identity used by Journal history.
- The default scope is the active company's fiscal-year start through the end of the shell-selected accounting period. Editing either date makes that Event Accounting tab use its explicit range until **Use Active Period Scope** is selected.
- **All funds** or one active/inactive historical fund may be selected.
- Income, Expenses, and Net come only from canonical splits explicitly tagged with the selected Activity. Corrections and reversals remain signed in the totals.
- The first detail table shows those exact Activity-tagged Journal splits.
- **Related configured-bank movement** is separate contextual evidence. It shows configured bank-account splits from transactions that also contain the selected Activity; the bank row is not claimed to be an Activity allocation merely because it shares the transaction.
- Related bank presentation uses **Inflow / Outflow** and the persisted cleared state. It does not invent a universal deposit/refund type.
- Double-clicking a detail row or choosing **Open Selected in Journal** opens the existing Journal transaction.
- The closeout section is factual/read-only. It creates no approval, reviewed, reconciliation, period-close, or event-close record.

## Manual acceptance

1. Open a database containing at least one Activity with Journal transactions. Sign in as VIEWER and open **Accounting -> Event Accounting**. Confirm the destination is available and contains no create/save/delete/close/reconcile action.
2. Select an active Activity, then an inactive Activity. Confirm the displayed stable ID/code/name/status matches Administration -> Activities and historical Activity links remain reviewable.
3. Select an Activity with income and expense lines. Reconcile the Income and Expenses values against the Activity-tagged lines shown in the first table; Net must equal Income minus Expenses.
4. If the Activity has a correction or reversal with a negative natural amount, confirm it reduces the corresponding total rather than disappearing from the result.
5. Choose a specific fund and confirm every Activity-tagged row and related configured-bank row is within that persisted fund. Return to **All funds** and confirm the wider scope returns.
6. Confirm the initial dates run from fiscal-year start through the end of the selected accounting period. Change the shell period and confirm the untouched Event Accounting defaults follow it.
7. Manually edit either Event Accounting date, then change the shell period. Confirm the explicit Event Accounting dates remain unchanged. Choose **Use Active Period Scope** and confirm the fiscal/period defaults resume.
8. For a transaction whose Activity is on an income/expense split and whose offset is a configured bank account, confirm the bank row appears under **Related configured-bank movement** even though the bank split itself is not Activity-tagged.
9. Confirm a bank-function account that is not configured through `CompanyBankAccount` does not appear in related bank movement.
10. Confirm the bank section says Inflow/Outflow and visibly explains that the rows are contextual evidence rather than an Activity allocation. Check persisted Cleared/Uncleared and Cleared On facts against Bank Transactions/Reconciliation.
11. Select a row and choose **Open Selected in Journal**. Also double-click a row. Confirm both navigate to the existing Journal and load the exact transaction ID.
12. Resize/reorder/sort both tables, move both split dividers, close/reopen the company workspace, and confirm company-owned layout state is restored. Verify both tables scroll horizontally and vertically at laptop width.
13. Switch companies and confirm Activity/fund choices and all result rows change to the new company without leaking the prior company's data.
14. Repeat the read-only workflow as ACCOUNTANT, MANAGER, and ADMIN. Confirm S2 itself never requires `BOOKKEEPING_WRITE`; existing Journal mutations reached by drill-through still obey their normal role permissions.

Acceptance requires the automated exact-head Maven PR Tests and production JavaFX route compliance to be green in addition to these desktop checks.
