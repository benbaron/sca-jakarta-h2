# P16-S10 Owner Desktop Acceptance — Journal Cleared State

Use a disposable database with one active company, two configured BANK accounts, a general fund, and one non-bank expense or income account. Create representative entered transactions, then use Bank Reconciliation to clear and match their bank lines.

## A. Four-state transaction summary

1. Open **Journal** and confirm a transaction containing no BANK account line displays **Not bank** in the Cleared column.
2. Confirm a transaction with one uncleared BANK line displays **Uncleared**.
3. Clear that BANK line through Bank Reconciliation, return to Journal or choose **Refresh**, and confirm it displays **Cleared**.
4. Create a balanced transaction containing two BANK lines and a counter-line. Clear only one BANK line and confirm the Journal row displays **Mixed**.
5. Clear the second BANK line and confirm the same row displays **Cleared** after refresh.

## B. Persisted line detail and reconciliation drill-through

1. Load a cleared transaction into the integrated Journal editor.
2. Confirm each entry line shows read-only **Bank state** and **Cleared on** columns; the cleared date follows the active-company date format.
3. Select a bank line with a durable reconciliation match and choose **Open Selected Line Reconciliation**.
4. Confirm the exact saved reconciliation session opens, including a finalized session in read-only form.
5. Select a non-bank or unmatched bank line and confirm the reconciliation button is unavailable.

## C. Authority, refresh, restart, and company isolation

1. Unmatch or change cleared state through Bank Reconciliation, return to Journal, and confirm Refresh shows the authoritative new state without editing the transaction.
2. Restart the application and confirm the transaction summary, line state/date, and reconciliation drill-through remain the same.
3. Switch to another company and confirm no transaction, cleared fact, date, or reconciliation session from the first company appears.
4. Confirm Journal exposes no control that directly marks a line cleared or uncleared.

## D. Laptop-width layout and existing Journal behavior

1. At laptop width, confirm the Journal and entry-line tables remain independently horizontally and vertically scrollable with the new columns.
2. Reorder/resize/sort the Cleared, Bank state, and Cleared on columns; reopen Journal and confirm company-owned table state restores.
3. Create, edit, save, reverse/delete under the configured correction policy, and reload an ordinary balanced transaction; confirm existing Journal behavior remains intact.

## Automated validation record

- Local `git diff --check` passes. This container has a Java 17 runtime but no Maven executable, Maven wrapper, or Java compiler; GitHub Maven PR Tests is the authoritative compile/test environment.
- Focused H2 coverage exercises `Not bank`, `Uncleared`, `Cleared`, `Mixed`, cleared dates, exact reconciliation-session projection, and restart persistence.
- Source guard coverage requires service-owned split facts and exact-session navigation and prohibits the former hard-coded Journal `Uncleared` expression.

## Acceptance record

- [ ] A. Four-state transaction summary passed
- [ ] B. Persisted line detail and exact reconciliation drill-through passed
- [ ] C. Authority, refresh, restart, and company isolation passed
- [ ] D. Laptop-width layout and existing Journal regression passed
- [ ] Exact tested PR head and Maven PR Tests run recorded
- [ ] Owner acceptance recorded

Do not mark P16-S10 DONE or begin P16-S11 until this checklist is accepted and the P16-S10 PR has merged.
