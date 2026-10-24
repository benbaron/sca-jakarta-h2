# P26-S6B — Merchant settlement assistant

Status: ADOPTED FOR IMPLEMENTATION. [PLAN](PLAN.md) owns execution and merge status.

## Purpose and boundary

P26-S6B gives an operator a guided Preview and Save path for an already-entered merchant receivable. It reuses the canonical Journal transaction, RECEIVABLE item, optional OTHER_LIABILITY NMR item, Merchant, Event, Fund, reporting, correction, SCLX and reconciliation authorities delivered by P25. It creates no provider feed, settlement table, mutable balance, automatic matching rule or parallel ledger.

The operator selects one open merchant receivable and enters the actual settlement date, bank payout, processor fee, and optional evidenced NMR discharge. The assistant never derives a fee, withholding, NMR amount, rate, exemption or account. Optional unconfirmed withholding remains the existing dated observation and does not reduce either receivable or liability.

Chargebacks and refunds identify and correct their original records through the established Journal correction workflow. The assistant must explain and route that boundary; it must not invent a negative receipt, fee, income account or NMR recovery.

## Preview and Save contract

The selected receivable must be an authoritative open GROSS_RECEIPT item with one company-owned Merchant, debit-normal RECEIVABLE account, Fund and Event. A payout requires an active posting BANK asset account. A fee requires an active posting EXPENSE account and explicit AR, OA or FR function. An NMR discharge requires an authoritative open NMR OTHER_LIABILITY item for the same Fund and Event, a positive amount within availability, and a nonblank external evidence reference. The discharge line carries the receivable's Merchant while retaining the NMR item's recipient.

Preview builds one balanced canonical transaction:

- debit the selected bank account for the entered payout;
- debit the selected expense account for the entered fee;
- optionally debit the selected NMR liability for the evidenced discharge; and
- credit the selected merchant receivable for their exact total.

The receivable DECREASE is designated SETTLEMENT and links the actual payout, fee and NMR component lines. The optional liability DECREASE is designated MERCHANT_DISCHARGE with its existing recipient and evidence. Zero components are omitted. At least one monetary component must be positive, and the total cannot exceed the receivable or NMR item availability.

Save requires `BOOKKEEPING_WRITE`, an unchanged preview fingerprint and one request UUID. The canonical transaction service locks and revalidates the company facts before writing. Exact retry/restart returns the retained transaction; changed facts require a new preview; late failure rolls back every line, allocation, component and audit event. Bank clearing remains owned by reconciliation. SCLX already carries the transaction, item allocations and settlement metadata.

## Production workspace

Add **Merchant Settlement Assistant** under Accounting with open-receivable and optional matching-NMR tables, explicit amount/account/function/evidence fields, Preview, Refresh, **Save Settlement**, and a visible Journal correction route for chargebacks/refunds. Local Save and shared File/toolbar Save must expose the same operation with live permission gating. The workspace uses a draggable split, independent scrolling and company money/date formatting.

## Required evidence

Behavioral tests must cover the $1,000 gross receivable with $970 settlement made of $950 payout and $20 fee, retained $30 withholding observation, later evidenced $30 NMR discharge, combined payout/fee/NMR settlement, exact retry/restart, no duplicate income, stale preview, excessive/zero/negative/overprecision amounts, wrong account/dimension/item ownership, missing evidence/function, closed period, VIEWER denial, rollback, correction/reversal effects, SCLX retention and reconciliation protection. Actual JavaFX tests must cover Preview, local/shared Save, live permission changes, retained transaction/result state, tables, correction guidance, scrolling and divider geometry. Update the P25/P26 coverage ledger and production JavaFX CI selector.

Manual review uses a disposable company: enter the synthetic $1,000 gross merchant receivable, preview $950 bank plus $20 fee while observing $30 withheld, save and confirm the receivable remains $30. Then preview an evidenced $30 NMR discharge and confirm both receivable and liability close without a second income entry. Retry Save, reopen, inspect Merchant Receivable Movements and NMR Period Movements, and verify the bank line remains uncleared until reconciliation.
