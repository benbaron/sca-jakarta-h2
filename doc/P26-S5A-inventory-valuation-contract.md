# P26-S5A inventory valuation contract proposal

## Decision requested

Adopt **perpetual moving weighted average** as the one supported valuation method for capitalized inventory. Do not claim FIFO, periodic weighted average, standard cost, or specific identification.

This proposal resolves D06 only after owner acceptance. Until then P26-S5 implementation remains blocked.

## Why this method fits the current authority

`InventoryItem`, `InventoryMovement`, `InventoryService`, and their canonical transaction links already form one perpetual quantity and accounting authority. Today every movement silently reuses one fixed item unit value. Moving weighted average corrects varying-cost receipts within that authority without inventing unavailable lot history or adding a second inventory ledger.

The donor repository was inspected. Its inventory and Sales/COG paths accept a per-unit cost but provide no FIFO layers, weighted-average engine, durable return linkage, or compatible accounting authority to import.

## Proposed rules

1. A receipt or upward count adjustment requires an explicit nonnegative unit cost for capitalized inventory. Its exact extended value is quantity times entered cost, rounded once to four decimals, half up.
2. After an increase, carrying value is prior carrying value plus the receipt value. Displayed average unit cost is carrying value divided by quantity, rounded to four decimals for display and proposal purposes.
3. An issue or downward count adjustment uses the current moving-average cost. Its value is quantity times the unrounded carrying-value ratio, rounded once to four decimals, half up. A movement that removes all remaining quantity consumes the exact remaining carrying value so no residual remains.
4. Quantity and carrying value can never become negative. Zero quantity requires zero carrying value.
5. Returned stock is entered as a receipt with its supported cost basis and factual note/reference. Reversing a prior issue remains the exact canonical way to undo that whole issue. This slice does not invent partial reversal or lot tracing.
6. A canonical movement reversal restores the exact signed quantity and carrying-value effect of the selected movement. It does not recalculate or rewrite prior movement history. Reversal is rejected when that exact inverse would make current quantity or carrying value negative, including a receipt already consumed by later issues.
7. Interactive financial movements must be dated on or after the latest accepted movement for the item. Same-date operations use durable acceptance order. This prevents a backdated receipt from silently changing the cost of already-posted issues. Historical SCLX restore preserves source order and exact source facts through its caller-owned import transaction.
8. Standalone revaluation is unavailable. Correct a wrong receipt cost through governed reversal and replacement. Upward count corrections require an explicit cost; downward corrections use current average.
9. Expensed supplies remain zero-value/nonfinancial under the P26-S2 treatment. They do not acquire inventory carrying value.
10. Fund, Event, Budget Category, functional-expense, authorization, closed-period, reconciliation, retry, concurrency, and correction rules remain those of the existing canonical movement path.

## Persistence and migration boundary

P26-S5 implementation should add exact signed carrying-value facts rather than derive authoritative value repeatedly from a rounded display average:

- item current carrying value, constrained to four decimals and nonnegative;
- movement signed value change, constrained to four decimals;
- movement entered/applied unit cost retained for explanation and interchange;
- one explicit valuation-method value, `MOVING_WEIGHTED_AVERAGE`, without a configurable method selector.

For existing fixed-value inventory, migration sets current carrying value to current quantity times the existing item unit value, rounded to four decimals. Existing movement value changes are backfilled from signed quantity change times their persisted unit value. This identifies the available legacy fixed-unit basis and does not fabricate FIFO lots or receipt prices that were never recorded. Migration tests must cover zero quantity, zero-value supplies, nonzero stock, restart, and rollback-safe upgrade.

SCLX must version the inventory extension if exact carrying-value or method facts are added. Import must preserve source movement values and method, reject unsupported methods or inconsistent quantity/value sequences before mutation, and remain retry-safe. P27-S4 still owns the final integrated portability audit.

## Worked acceptance fixture

All values below are exact at four-decimal ledger precision.

| Step | Quantity effect | Value effect | Quantity on hand | Carrying value | Moving average |
|---|---:|---:|---:|---:|---:|
| Receipt 10 at $2 | +10 | +$20 | 10 | $20 | $2.0000 |
| Receipt 10 at $3 | +10 | +$30 | 20 | $50 | $2.5000 |
| Issue 8 | -8 | -$20 | 12 | $30 | $2.5000 |
| Return/receipt 2 at $2.50 | +2 | +$5 | 14 | $35 | $2.5000 |
| Reverse that return | -2 | -$5 | 12 | $30 | $2.5000 |

The inventory control-account net debit is $30 and equals the register carrying value. Repeating an accepted preview creates no duplicate movement or transaction.

## Required implementation coverage

P26-S5 is not complete without behavioral unit/integration coverage for:

- the worked varying-cost fixture and exact register-to-GL tie-out;
- fractional quantities, repeating decimal averages, final-issue residual consumption, and zero-value rejection;
- receipt, issue, upward/downward adjustment, returned-stock receipt, whole-movement reversal, consumed-receipt reversal rejection, and reversal retry;
- stale preview, concurrent movement, cross-company ownership, inactive/ineffective dimensions, closed periods, finalized reconciliation, and late rollback;
- negative-stock and backdated interactive-movement rejection;
- legacy fixed-value migration, restart, and SCLX versioned round-trip/retry/rejection;
- historical/as-of valuation and movement reports using persisted signed value changes;
- JavaFX entry of receipt cost, previewed old/new carrying value and average, explicit Save, Viewer restriction, scrolling, and shared command behavior;
- the existing P25/P26 coverage ledger updated with all new test classes and scenarios.

## Owner decision

Acceptance of this proposal means:

- moving weighted average is the only implemented capitalized-inventory cost method;
- legacy on-hand value begins from quantity times the previously stored fixed unit value;
- negative stock and backdated interactive movements remain prohibited;
- no standalone revaluation, FIFO reconstruction, or partial movement reversal is added;
- returned stock uses an explicit receipt cost unless the whole original issue is reversed.

After acceptance, P26-S5 implementation begins on a fresh implementation branch from the accepted contract merge. Official workbook/form mapping remains P27 work.
