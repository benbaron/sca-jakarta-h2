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

Inventory items are genuine records with required fields:

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

Adding certain inventory items creates accounting transactions. The transaction also receives a supplemental inventory detail record that describes the inventory-specific facts.

Inventory supports quantity movements after creation. Quantity movements create transactions when financially relevant and show inventory changes.

Historical movements are tracked and displayed in a table. The old runbook subpane is not retained.

## Asset Register

Asset items are separate from Inventory items.

The Asset Register supports adding and editing assets.

Required asset fields:

- asset name;
- asset account;
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

The Asset Register shows accumulated depreciation for each item.

Depreciation schedules support straight-line depreciation over:

- 3 years;
- 5 years;
- 7 years.

Adding a depreciation schedule defines how depreciation runs calculate entries. It does not automatically create future scheduled entries.

When depreciation entries are run, they create actual accounting transactions through the canonical transaction service.
