# Phase 2 - Domain Model Detail (Initial Implementation)

This phase introduces executable domain contracts beyond enums by implementing immutable journal entities and explicit state transition policies.

## Implemented entities and value objects

- `JournalTransaction`
  - Immutable source-of-truth transaction with group scope, posting date, memo, timing, lines, and optional reversal linkage.
  - Enforces balancing invariant: total debits must equal total credits.
- `PostingLine`
  - Immutable line with `accountCode`, `fundCode`, `EntrySide`, and positive amount.
- `EntrySide`
  - `DEBIT` / `CREDIT`.
- Existing value object: `TransactionTiming`.

## Implemented transition policies

`OpenItemStatePolicies` now defines concrete allowed transitions for:

- `OutstandingBankItemState`
- `ReceivableItemState`
- `PrepaidExpenseItemState`
- `DeferredRevenueItemState`
- `PayableItemState`
- `AssetItemState`
- `EventState`

The `StateTransitionPolicy` interface centralizes:

- `canTransition(from, to)`
- `assertTransitionAllowed(from, to)`

## Customer UI panel design contracts

Added typed panel descriptors with role/workflow metadata:

- `CustomerPanelDescriptor`
- `UserRole`
- `CustomerPanelDesignService`

These classes define minimum-role access and expected workflows per panel before JavaFX view/controller wiring.

## Next phase candidates

- Add concrete open-item entities (`ReceivableItem`, `DeferredRevenueItem`, etc.) with balances and transition methods.
- Add journal posting service that derives/updates open-item projections deterministically from journal transactions.
- Add persistence schema for journal headers/lines and open-item state snapshots + transition history.
