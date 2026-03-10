# Phase 1 - Stateful SCA Accounting Architecture

## 1) Problem Restatement in Domain Terms

The system must model **baronial finance over time** where journal postings are immutable source-of-truth events, and all operational views (open items, event status, reconciliation, close outputs) are derived projections.

Key characteristics:

- Two independent timing dimensions per transaction:
  - bank timing (`PREVIOUSLY`, `NOW`, `FUTURE`, `NONE`)
  - budget timing (`PREVIOUSLY`, `NOW`, `FUTURE`, `NONE`)
- Explicit branch identity (multiple SCA groups in one database).
- Distinction between true branch income and custodial/pass-through funds.
- Deterministic period close and repeatable reconciliation.
- Auditability with supervisory controls for reversals/deletions.

## 2) Proposed Architecture (DDD-lite, layered)

- `domain/`
  - Immutable value objects, enums, and entity models.
  - State machine rules for open-item lifecycle transitions.
- `service/`
  - Application services for posting, settlement, recognition, and transfer workflows.
- `workflow/`
  - Orchestrations for event settlement, close, and reconciliation sequences.
- `validation/`
  - Domain validators and cross-entity rule validators.
- `repository/`
  - JDBC/JPA repositories and query objects.
- `reconciliation/`
  - Reconciliation run logic and result records.
- `close/`
  - Period close engines and roll-forward processors.
- `reporting/`
  - Financial statements, schedule exports, audit reports.
- `app/`
  - Composition root, CLI/UI bootstrapping.
- `ui/`
  - JavaFX panels for journaling, schedules, close, and audit.

## 3) Core Aggregates and Boundaries

Minimum aggregate roots for first implementation wave:

1. `JournalTransaction` aggregate
   - Holds immutable posting lines and timing metadata.
   - Supports supervisory reversal/deletion metadata only via explicit audit action.
2. `OpenItem` aggregate families
   - Receivables, payables, deferred/prepaid, outstanding bank items.
   - Lifecycle transitions from journal events + settlement events.
3. `Event` aggregate
   - SCA event lifecycle from planning through settlement and close.
4. `PeriodClose` aggregate
   - Deterministic close run including inputs, outputs, and roll-forward signatures.
5. `ReconciliationRecord` aggregate
   - Reconciliation statement context and clear/unclear outcomes.

## 4) Core State Machines (initial)

- `OutstandingBankItemState`: `RECORDED -> UNCLEARED -> (CARRIED_FORWARD)* -> CLEARED | VOIDED | STALE_WRITTEN_OFF`
- `ReceivableItemState`: `OPEN -> PARTIALLY_APPLIED -> SETTLED_BY_CASH | SETTLED_BY_EXPENSE_APPLICATION | WRITTEN_OFF`
- `PrepaidExpenseItemState`: `OPEN -> PARTIALLY_RECOGNIZED -> FULLY_RECOGNIZED`
- `DeferredRevenueItemState`: `OPEN -> PARTIALLY_RECOGNIZED -> FULLY_RECOGNIZED | REFUNDED`
- `PayableItemState`: `OPEN -> PARTIALLY_PAID -> PAID | ADJUSTED | REVERSED`
- `AssetItemState`: `ACTIVE -> ON_LOAN | LOST | SOLD | DISPOSED`
- `EventState`: `PLANNING -> OPEN_FOR_PREREG -> ACTIVE -> SETTLING -> CLOSED`

## 5) Customer UI Panel Design (Phase 1)

Initial customer-facing panel map:

1. Group Selector
2. Journal Workbench
3. Open Item Schedules
4. Event Lifecycle
5. Bank Reconciliation
6. Period Close
7. Import / Export
8. Approval & Audit

These were added as architecture metadata classes to anchor implementation and tests before full JavaFX controller development.

## 6) Minimum Viable Java Classes to Implement First

Phase 1 implementation baseline (completed in this change):

- Timing model
  - `TimingPosition`
  - `TransactionTiming`
- Core lifecycle enums
  - `OutstandingBankItemState`
  - `ReceivableItemState`
  - `PrepaidExpenseItemState`
  - `DeferredRevenueItemState`
  - `PayableItemState`
  - `AssetItemState`
  - `EventState`
- Customer UI design descriptors
  - `CustomerPanelId`
  - `CustomerPanelBlueprint`
  - `CustomerUiPanelCatalog`

Next phase should add transition-rule methods and invariant validation services for each state machine.
