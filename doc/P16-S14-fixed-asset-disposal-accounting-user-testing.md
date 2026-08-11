# P16-S14 fixed-asset lifecycle accounting — owner desktop checklist

Use a disposable or backed-up database with an active company, a fixed-asset account, accumulated-depreciation account, BANK/ASSET proceeds account, INCOME gain account, EXPENSE loss account, and an active fund. Complete this checklist on the exact pull-request head after Maven PR Tests pass.

## Preview and commit

1. Open **Asset Register**, select an active asset with known cost and accumulated depreciation, and choose **Record Lifecycle Event...**. Confirm Sale, Retirement, and Impairment are separate choices and that irrelevant amount/account controls disable appropriately.
2. Preview a Sale whose proceeds exceed carrying amount. Confirm cost, accumulated depreciation, prior impairment, carrying amount, proceeds, gain, exact accounts, fund, date, and `ACTIVE → DISPOSED` transition before accepting.
3. Accept the Sale. Confirm one lifecycle-history row appears, the asset becomes `DISPOSED`, and Ledger drill-through opens the linked balanced canonical transaction. Confirm ordinary asset editing cannot create or clear `DISPOSED`.
4. Repeat with a different active asset whose proceeds are below carrying amount. Confirm the preview and posting recognize the exact loss. Repeat with zero proceeds and a fully depreciated asset; confirm no zero-value lines are displayed or stored.
5. Record an Impairment. Confirm the asset remains `ACTIVE`, accumulated impairment increases, current book value and next depreciation decrease, and the transaction debits the selected loss expense and credits accumulated depreciation.

## Protection and correction

6. Try an event in a closed date range. Confirm preview/commit is blocked and no status, lifecycle, ledger, or audit fact changes.
7. Try Sale proceeds into a BANK account/date covered by a finalized reconciliation. Confirm the action is blocked without partial accounting.
8. Select an unreversed lifecycle row and choose **Reverse Selected Lifecycle Event**. Confirm the reversal date/reason and original transaction are shown before accepting. After commit, confirm both transaction IDs remain visible and Sale/Retirement restores the former asset status.
9. In Journal, try to edit, delete, or reverse either lifecycle-linked transaction. Confirm the application directs correction back to Asset Register.
10. Cancel each preview and confirmation once. Confirm the status text says no accounting changed and no history row appears. While an operation is running, confirm its controls are disabled and the UI remains responsive.

## Persistence and isolation

11. Switch to another company. Confirm no asset or lifecycle history leaks across companies and accounts from the other company cannot be used.
12. Restart the application. Confirm asset status, impairment/book value, lifecycle history, original/reversal links, and Ledger drill-through reload from H2 unchanged.
13. At laptop width, confirm the action strip wraps, both tables and the editor remain reachable, horizontal/vertical scrolling works, and company-owned divider positions restore after restart.

Record the exact tested commit, operating system, Java version, and pass/fail notes in the PR before merge.
