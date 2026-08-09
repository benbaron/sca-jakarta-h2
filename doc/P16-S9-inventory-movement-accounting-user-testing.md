# P16-S9 Owner Desktop Acceptance — Inventory Movement Accounting

Use a disposable database with one active company, an active inventory account, an active fund, an expense or clearing offset account, and at least one valued inventory item created at zero quantity. Also create one zero-value item for the explicitly nonfinancial case.

## A. Frozen preview and financial receipt

1. Select the valued item, enter a receipt quantity/date/note, choose an offset account and actor, then choose **Receive Quantity**.
2. Confirm the preview shows the exact item, quantity before/change/after, unit and extended value, item fund, inventory account, offset account, and financial classification.
3. Cancel once and confirm neither quantity nor Journal changes.
4. Repeat, confirm, and verify the item quantity changes and the movement table shows a real `Txn` ID.
5. Drill the selected movement to Ledger and confirm a balanced transaction debits inventory and credits the offset account in the item's fund.

## B. Issue and count adjustment

1. Issue less than the on-hand quantity and confirm the preview/transaction debit the offset account and credit inventory.
2. Use **Adjust Count To Quantity** once upward and once downward; confirm each uses the correct receipt/issue accounting direction.
3. Try to issue more than on hand and try an adjustment equal to the current count; confirm both are rejected without any durable change.

## C. Explicit nonfinancial zero-value movement

1. Select the zero-value item and try a movement without the nonfinancial checkbox; confirm it is blocked.
2. Select **Zero-value nonfinancial movement (no ledger transaction)**, preview, and confirm.
3. Verify quantity and movement history persist, the movement `Txn` cell is blank, and no Journal transaction was created.
4. Confirm a valued item cannot be recorded as nonfinancial.

## D. Atomicity, protection, and stale scope

1. Open a movement preview, change the item quantity through another confirmed movement, then try the stale preview; confirm it is rejected without a partial transaction.
2. Confirm a movement date in a closed range is blocked.
3. For a configured bank offset account, confirm a date inside a finalized reconciliation range is blocked.
4. Switch active company after opening a preview and confirm commit is rejected.
5. Restart and confirm quantities, movement history, transaction links, and Journal splits remain consistent.

## E. Immutable correction

1. Select a financial movement, supply a reversal date, actor, and reason, then choose **Reverse Selected Movement**.
2. Confirm the preview shows the original movement/transaction, inverse quantity change, resulting quantity, and reversal value.
3. Confirm and verify the original transaction is marked reversed, a canonical reversal transaction exists, and an inverse adjustment movement links to it.
4. Confirm a second reversal attempt is blocked.
5. Confirm a completed/finalized reconciliation or closed reversal date blocks the correction without changing quantity or ledger state.

## F. Item and SCLX integrity

1. Confirm a valued item cannot be created with positive opening quantity; create it at zero and receive through the governed movement action.
2. With quantity on hand, try changing the inventory account, fund, or unit value; confirm the silent reclassification/revaluation is blocked.
3. Export and restore an SCLX inventory graph containing movements with and without transaction provenance.
4. Confirm restore preserves the source movement count and existing transaction links without synthesizing an additional receipt, transaction, or movement audit.

## Automated validation record

- Local Java syntax parsing passed for every changed Java source and test file.
- The work container has Java 17 but no Maven executable or Maven wrapper, so `mvn clean verify` must run in the pull-request workflow.
- Exact tested PR head and Maven PR Tests run: pending.

## Acceptance record

- [ ] A. Frozen financial preview/receipt/cancel/drill-through passed
- [ ] B. Issue and adjustment direction/negative/no-op guards passed
- [ ] C. Explicit zero-value nonfinancial behavior passed
- [ ] D. Atomicity, close, reconciliation, company, and restart guards passed
- [ ] E. Canonical immutable correction passed
- [ ] F. Item edit and SCLX no-duplicate integrity passed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S9 DONE or begin P16-S10 until this checklist is accepted and the P16-S9 PR has merged.
