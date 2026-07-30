# Inventory and movement SCLX extension

## Scope

Selected-company SCLX 1.3 export writes authoritative H2-backed inventory items and movement history under:

```text
extensions.scaJakartaH2.inventory
```

The extension is versioned independently with `version: 1` and contains ordered `items` and `movements` arrays. It does not create a second inventory authority and does not export local numeric database identifiers.

## Portable identities

- Inventory items use `inventory-item:<company-code>:<intrinsic-item-uuid>`.
- Inventory movements use `inventory-movement:<company-code>:<intrinsic-movement-uuid>`.
- Account, fund, and canonical transaction references use their existing governed portable identities.

The UUID values come from the durable `portable_id` columns introduced by Flyway V66. Export must reject a missing portable identity rather than substitute a local primary key or derive an identity from editable display fields.

## Item fields

Each item preserves:

- `itemId`
- `name`
- `itemType`
- `quantity`
- `unit`
- `unitValue`
- `acquisitionDate`
- optional `custodian`
- optional `storageLocation`
- `condition`
- `status`
- optional `notes`
- `inventoryAccountId`
- `fundId`
- `createdAt`
- `updatedAt`

Active, inactive, and disposed items are retained. Quantity and unit value use exact decimal values and must not be negative.

## Movement fields

Each movement preserves:

- `movementId`
- `itemId`
- `movementDate`
- `movementType`
- signed `quantityChange`
- `resultingQuantity`
- `unitValue`
- optional `transactionId`
- optional `notes`
- `createdAt`

A movement may omit `transactionId` when no canonical accounting transaction was associated with it. When present, the referenced transaction must be part of the same selected-company export snapshot.

## Selection and ordering

Export includes every inventory item owned by the selected company and every movement belonging to those items. Items and movements are ordered by intrinsic portable UUID so unchanged data serializes deterministically.

Records from other companies are excluded. An item account must belong to the selected company's active chart, and the item fund must belong to the selected company.

## Validation

Before file replacement, validation requires:

- unique item and movement identities;
- every movement item reference resolves within the extension;
- every item account and fund reference resolves in the exported core snapshot;
- every optional movement transaction reference resolves in the exported transaction snapshot;
- item quantity and unit value are nonnegative;
- movement resulting quantity and unit value are nonnegative;
- no cross-company item, movement, account, fund, or transaction reference is accepted.

## Counts and user-visible result

`SclxExportCounts` includes exact `inventoryItems` and `inventoryMovements` counts, and both contribute to `totalEntities`. Once this extension is active, Inventory is no longer reported as a deferred governed section.

## Exclusions

This extension does not export JavaFX state, filesystem paths, compatibility stores, generic job history, authentication material, or records owned by another company.
