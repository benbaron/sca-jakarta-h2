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
