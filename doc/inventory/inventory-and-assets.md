# Funds, Inventory, and Asset Register requirements

## Funds Panel

The Funds Panel must support editing:

- fund name;
- fund code;
- fund type/classification;
- active/inactive status;
- notes.

Fund code mutability:

- if the fund is referenced by any current ledger entries, the fund code is immutable;
- "already used" is determined by auditing the present ledger state;
- if entries containing the fund are deleted under an allowed correction policy and no references remain, the fund may be deleted or its code may become editable according to the final service policy.

Deletion:

- deleting a fund is allowed only when unused;
- if the fund is used, the user must deactivate it instead.

## Inventory Panel

The Inventory Runbook subpane is eliminated.

Inventory items are genuine H2-backed records with required fields:

- item name;
- item type;
- quantity;
- unit;
- value;
- acquisition date;
- custodian;
- storage location;
- condition;
- notes;
- active/disposed status.

Valued inventory is created at zero quantity. The first receipt is recorded through the governed movement action so quantity and general-ledger value begin together. A positive opening quantity is allowed only for a zero-value item and creates a nonfinancial initial movement. SCLX restore remains a separate caller-owned historical seam.

Inventory supports quantity movements after creation. Interactive movements use a non-mutating frozen preview and explicit confirmation before any write:

- **RECEIPT** adds the entered quantity. At fixed item unit value, debit the item's inventory account and credit the selected offset account.
- **ISSUE** subtracts the entered quantity. At fixed item unit value, debit the selected offset account and credit the item's inventory account.
- **ADJUSTMENT** treats the entered quantity as the desired ending count. An upward adjustment uses the RECEIPT direction; a downward adjustment uses the ISSUE direction. An adjustment that leaves quantity unchanged is rejected.

The movement value is `absolute quantity change × item unit value`, rounded once to the ledger precision of four decimal places using half-up rounding. A positive raw value that rounds to zero is rejected. The item fund is used on both canonical transaction lines; the movement never creates an implicit inter-fund transfer. The inventory and offset accounts must be distinct, active posting accounts owned by the active company and effective on the movement date. The item fund must likewise be active and effective.

A positive unit value makes every nonzero quantity movement financially relevant. The user must select an offset account and explicitly confirm a preview that shows quantity before/change/after, unit and extended value, inventory and offset accounts, fund, and the balanced debit/credit proposal. A zero-unit-value item may create a movement only after the user selects the clearly labeled nonfinancial confirmation; it has no offset account and no `Txn` link. A valued movement can never be downgraded to nonfinancial.

The service locks and revalidates the item after confirmation. It commits the item quantity, `InventoryMovement`, canonical `Txn` and two `TxnSplit` rows, portable identities, transaction audit, and inventory audit in one caller-owned JPA transaction. A stale preview, duplicate preview retry, company switch, negative result, closed date, finalized bank-reconciliation range, invalid dimension, identity collision, or late failure cannot leave a partial quantity or ledger change. A successful retry of the same preview returns its existing movement.

Historical movements are immutable. A financial movement is corrected with **Reverse Selected Movement**, which calls the canonical transaction-correction policy and atomically creates the linked reversal transaction plus an inverse adjustment movement. Completed or finalized reconciliations and closed reversal dates block correction. The item account, fund, or unit value cannot be changed while quantity is on hand because that would silently reclassify or revalue stored inventory.

Historical movements are tracked and displayed in a table. The old runbook subpane is not retained.

P09-S1 implementation notes:

- `inventory_item` stores the inventory register, inventory account/fund links, item facts, quantity, value, condition, status, and notes.
- `inventory_movement` stores receipt, issue, and adjustment history for each inventory item. It has a nullable canonical `txn` link reserved for financially relevant movement transactions.
- `InventoryService` validates inventory account type/subtype, required item fields, nonnegative item quantity/value, positive movement quantities, active item movement eligibility, and no-negative-result movement rules.
- Creating a zero-value item records an initial nonfinancial receipt movement when initial quantity is greater than zero. Valued items must begin at zero and receive quantity through the governed P16-S9 movement action.
- Receiving quantity adds to the item count, issuing subtracts from it, and adjustment treats the entered quantity as the corrected count.
- P16-S9 fills `inventory_movement.transaction_id` only with the real canonical transaction created or reversed atomically with the movement. The table never displays a synthetic identifier.
- `InventoryPanel` reads/writes through `InventoryService`; it retains the frozen preview through confirmation, exposes a clearly labeled zero-value nonfinancial choice, supports canonical reversal and Ledger drill-through, and has no SQL or parallel posting path.
- `InventoryService.createForImport(...)` and `recordMovementForImport(...)` remain caller-owned SCLX restore seams. They preserve source quantity, unit value, portable identity, timestamps, and existing transaction provenance without synthesizing an initial movement or a second transaction.

## Asset Register

Asset items are separate from Inventory items.

The Asset Register supports adding and editing assets through H2-backed fixed asset records, not text runbook entries.

Required asset fields:

- asset name;
- asset account;
- accumulated depreciation account;
- depreciation expense account;
- fund;
- acquisition date;
- acquisition cost;
- salvage value;
- useful life;
- depreciation method;
- accumulated depreciation;
- current book value;
- status;
- notes.

Accumulated depreciation may be entered by the user as an opening accumulated depreciation amount.

The Asset Register shows accumulated depreciation for each item as opening accumulated depreciation plus completed depreciation runs.

Depreciation schedules support straight-line depreciation over:

- 3 years;
- 5 years;
- 7 years.

Adding a depreciation schedule defines how depreciation runs calculate entries. It does not automatically create future scheduled entries.

When depreciation entries are run, they create actual accounting transactions through the canonical transaction service. Each completed run stores a durable H2 depreciation-run record linked to the fixed asset and to the created transaction. The transaction header, balanced splits, completed run, portable identities, and factual audit event commit atomically in one JPA transaction. Company ownership, asset/account/fund eligibility, duplicate-run state, closed periods, and completed/finalized reconciliation protection are checked before the first mutation; database uniqueness remains the final concurrency guard. A late failure rolls back every write and the panel refreshes from authoritative persisted state rather than showing partial success.

P08-S1 implementation notes:

- `fixed_asset` stores the asset register, account/fund links, straight-line life, status, notes, and opening accumulated depreciation.
- `fixed_asset_depreciation_run` stores completed depreciation runs and links each run to the canonical `txn` row created by the run.
- `FixedAssetService` validates fixed asset account type/subtype, depreciation expense account type, useful-life limits, nonnegative cost/salvage/opening-depreciation values, and remaining depreciable basis.
- `AssetsRegisterPanel` and `DepreciationRunsPanel` read/write through `FixedAssetService`; their old asset/depreciation runbook sidecars are removed.

## Fixed-asset lifecycle accounting

P16-S14 distinguishes three financially authoritative lifecycle operations:

- **Sale** removes the asset from service and records any proceeds plus the resulting gain or loss.
- **Retirement** removes the asset from service with no proceeds and recognizes the remaining carrying amount as a loss.
- **Impairment** keeps the asset active while reducing its carrying amount by an explicitly entered impairment loss.

Fixed-asset status is service-owned lifecycle state rather than ordinary editable metadata. Interactive creation starts `ACTIVE`; ordinary asset editing preserves the current status. Explicit **Deactivate Asset** and **Reactivate Asset** actions govern `ACTIVE <-> INACTIVE` transitions with a factual actor, nonblank reason, and `AuditEvent`. `DISPOSED` remains a service-owned result of a confirmed Sale or Retirement; it cannot be selected, produced by the ACTIVE/INACTIVE lifecycle action, or cleared through ordinary asset editing. A disposed asset returns to its former lifecycle state only when its linked canonical transaction is reversed through the asset lifecycle workflow.

Deactivation is nonfinancial retained history: it stops depreciation and Sale/Retirement/Impairment eligibility without derecognizing the asset or changing carrying value. Reactivation resumes those governed operations. A financial disposal still requires Sale or Retirement. Fixed assets are never physically deleted from this maintenance surface.

### Preview and accounting policy

Every lifecycle operation begins with a frozen, non-mutating preview. The preview identifies the active company, asset and portable identity, operation type/date, asset account, accumulated-depreciation account, proceeds account when applicable, gain/loss account when applicable, fund, acquisition cost, accumulated depreciation, prior unreversed impairment, carrying amount, proceeds, impairment, gain or loss, exact debit/credit proposal, notes, and new portable identities.

All monetary values use ledger precision of four decimal places with half-up rounding. The service uses the asset's one existing fund on every line and never creates an implicit inter-fund transfer.

Sale and Retirement remove the complete asset record from service; partial physical disposals are not inferred. Their accounting is:

- debit the proceeds account for positive proceeds;
- debit accumulated depreciation for completed/opening depreciation plus prior unreversed impairment;
- debit the selected loss expense account when proceeds are below carrying amount;
- credit the fixed-asset account for acquisition cost; and
- credit the selected gain income account when proceeds exceed carrying amount.

A zero-proceeds Sale or Retirement has no proceeds-account line. A fully depreciated asset therefore posts only the accumulated-depreciation debit and fixed-asset credit. A zero-cost asset cannot enter this financial disposal workflow because canonical accounting forbids zero-value transaction lines.

Impairment must be positive and cannot exceed the asset's current carrying amount. It debits the selected loss expense account and credits the asset's accumulated-depreciation/contra-asset account. Impairment does not change the asset status, acquisition cost, historical depreciation, or salvage estimate. Current book value is acquisition cost less accumulated depreciation and unreversed impairment, never below zero.

The proceeds account must be an active posting `BANK` or `ASSET` account and must differ from both fixed-asset contra accounts. The fixed-asset and accumulated-depreciation accounts must also differ. A gain account must be an active posting `INCOME` account, and a loss/impairment account must be an active posting `EXPENSE` account. Every account and the asset fund must belong to the active company and be effective on the event date. Positive proceeds require a proceeds account and a nonzero gain/loss requires its matching account. An unused selection creates no line and is not retained as event provenance; the frozen preview identifies only the accounts actually used by its exact proposal.

### Atomic persistence and correction

After confirmation, `FixedAssetService` locks and revalidates the asset and recomputes the complete preview. The canonical `Txn`/`TxnSplit` rows, durable fixed-asset lifecycle event, asset status transition, portable identities, transaction audit, and asset-lifecycle audit commit in one caller-owned JPA transaction. Lifecycle dates cannot precede already-recorded later depreciation, lifecycle, or lifecycle-reversal activity; depreciation dates likewise cannot precede a later lifecycle fact. A stale preview, company switch, duplicate final disposition, identity collision, closed date, finalized bank-account reconciliation range, invalid account/fund, chronology conflict, retry conflict, or injected late failure leaves no partial status, event, transaction, split, or audit change. An identical retry of the committed preview returns the existing event.

Lifecycle events are immutable financial facts. **Reverse Selected Lifecycle Event** creates the canonical opposite transaction through `TransactionCorrectionService`, links it to the lifecycle event, and restores the asset's prior status in the same transaction. Reversal requires an open date and is blocked by completed/finalized reconciliation protection. An impairment cannot be reversed while a later unreversed Sale or Retirement remains in effect. Generic Journal edit/delete/reversal is blocked for both original and reversal transactions linked to a fixed-asset lifecycle event; corrections must use the domain workflow so asset state and ledger state cannot diverge.

The lifecycle history remains visible after reversal, including both canonical transaction links. SCLX disposal interchange is not inferred from the legacy asset status field; it may be extended only through a separately versioned fixed-assets extension that preserves these stable lifecycle facts and passes semantic round-trip tests.

## Report Library projections

P16-S15 adds four read-only reports to the existing Report Library: Fixed Asset Register, Fixed Asset Depreciation History & Schedule, Inventory On Hand & Valuation, and Inventory Movement History. They use typed immutable date/fund/control-account/status/asset-or-item filters and the active-company boundary. Preview, every export format, and Journal drill-through retain the same request.

Asset as-of values combine acquisition cost, opening accumulated depreciation, completed runs through the date, and unreversed impairment. An unreversed Sale or Retirement derecognizes gross and contra value; a dated lifecycle reversal restores the prior status for later as-of reports. The schedule summary derives only the next straight-line amount, remaining depreciable amount, and estimated remaining periods. It never creates or claims future transactions.

Inventory as-of quantity comes from the latest persisted movement through the date. When the first movement is later than the selected date, the prior quantity is reconstructed from that movement's resulting quantity and change rather than using today's item quantity. Movement value is the persisted signed quantity change times persisted unit value. A null movement transaction remains visibly nonfinancial/unlinked.

Each domain report shows canonical control-account totals beside its domain totals. Shared control accounts, fund-unallocated opening balances, nonfinancial movements, unrelated account activity, filters, and row limits can produce a difference; the exact difference and reason are shown and never normalized away. The complete predicate and calculation contract is in `doc/reporting/report-library.md`.

## P17-C5 inventory item lifecycle

Inventory item status is a durable lifecycle fact, not ordinary editable metadata. `InventoryItem.id` remains the stable identity for the complete item history; the application does not physically delete inventory items or their movement/ledger history.

Interactive item creation starts `ACTIVE`. Ordinary item editing preserves the existing status and cannot select `INACTIVE` or `DISPOSED`. Lifecycle changes use the explicit `InventoryService.changeStatus(...)` service boundary with the active company, stable item ID, factual actor, and nonblank reason.

- `ACTIVE -> INACTIVE` is allowed only when on-hand quantity is exactly zero.
- `INACTIVE -> ACTIVE` reactivates the retained item.
- `ACTIVE` or `INACTIVE -> DISPOSED` is allowed only at zero quantity and is terminal for interactive lifecycle operations.
- A `DISPOSED` item is retained as history and cannot be reactivated or deactivated.
- Nonzero inventory must be reduced through the governed movement workflow before retirement; lifecycle status never substitutes for an ISSUE or ADJUSTMENT and never changes ledger value.

Status transitions acquire the same pessimistic item lock used by confirmed quantity movements, so a movement cannot race a retirement into an invalid stranded balance. The status change and its factual `AuditEvent` commit atomically. Movement history, canonical transaction links, portable identity, and item metadata remain attached to the same durable item.

`createForImport(...)` remains a caller-owned historical restore seam. It may restore an already inactive or disposed source item with its source status and timestamps without fabricating a new local lifecycle event; normal interactive writes cannot use that seam to bypass lifecycle rules.


## P17-C7 fixed-asset status lifecycle authority

`FixedAsset.id` remains the durable identity for metadata, depreciation runs, lifecycle events, canonical transactions, and reports. Interactive ACTIVE/INACTIVE transitions use `FixedAssetService.changeStatus(...)`; ordinary metadata updates cannot change status, and interactive creation cannot begin INACTIVE or DISPOSED.

Metadata updates, explicit ACTIVE/INACTIVE changes, monthly depreciation, financial lifecycle commit, and lifecycle reversal all serialize through a pessimistic lock on the same `FixedAsset` row before revalidation and mutation. This prevents stale metadata or status writes from racing a depreciation run or Sale/Retirement/Impairment operation into inconsistent asset state.

`DISPOSED` remains financially authoritative terminal state while its Sale/Retirement is unreversed. It can be undone only by the governed lifecycle reversal, which restores the event's prior status and reverses canonical accounting. The ACTIVE/INACTIVE action never creates or clears DISPOSED.

SCLX `createForImport(...)` remains a caller-owned historical restore seam. It may restore the source status and timestamps without fabricating a local ACTIVE/INACTIVE audit transition; interactive callers cannot use that seam.
