# Inventory and movement SCLX extension

## Scope

Selected-company SCLX 1.3 export writes authoritative H2-backed inventory items and movement history under:

```text
extensions.scaJakartaH2.inventory
```

The extension is versioned independently. Current export writes `version: 2`; import also accepts legacy version 1. Both contain ordered `items` and `movements` arrays. It does not create a second inventory authority and does not export local numeric database identifiers.

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
- `carryingValue` (version 2)
- `valuationMethod` (`MOVING_WEIGHTED_AVERAGE`, version 2)
- `financialTreatment` (`CAPITALIZED_INVENTORY` or `EXPENSED_SUPPLY`)
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
- signed `valueChange` (version 2)
- `resultingCarryingValue` (version 2)
- optional `transactionId`
- optional `notes`
- `createdAt`

A movement may omit `transactionId` when no canonical accounting transaction was associated with it. When present, the referenced transaction must be part of the same selected-company export snapshot.

## Selection and ordering

Export includes every inventory item owned by the selected company and every movement belonging to those items. Serialized arrays use deterministic portable identities; historical import validates and restores the dated source acceptance sequence before accepting the final quantity/carrying value. It does not replay interactive weighted-average calculations.

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

## Transactional import

Import strictly validates the complete supported extension before mutation. Version 2 preserves exact signed value changes and resulting carrying values; unsupported valuation methods/versions and inconsistent histories reject. Version 1 retains the governed fixed-unit compatibility basis without inventing unavailable receipt prices. Items are recreated through a caller-owned `InventoryService` transaction boundary after their company-owned account and fund references exist. Movement history is then recorded through the same service after optional canonical transaction provenance resolves.

Import preserves the intrinsic item and movement UUIDs, item facts, movement facts, source timestamps, status, condition, notes, and exact decimal values. It does not synthesize an initial receipt, recalculate source quantities, or create another ledger transaction. Every item and movement receives a durable SCLX interchange identity, identical reimport is a no-op, and any late failure rolls the complete imported company graph back.

P16-S9 does not route restore through the interactive movement preview/posting command. `createForImport(...)` and `recordMovementForImport(...)` remain the only SCLX inventory seams: an imported `transactionId` reattaches the already restored canonical transaction, while an absent reference stays null. Neither case synthesizes a second transaction or movement audit fact.

The original C6 empty-target restriction is superseded by the current existing-company Preview/Save and identity/conflict contract in [SCLX](sclx.md). Exact retained identities are reused, changed facts require a supported explicit resolution, and unsupported populated extensions still block before commit. Current production also supports banking, reconciliation, period-close, factual audit and canonical correction graphs through their governed sections; C6's historical exclusions are not current restrictions.

The [P27-S4 audit](../P27-S4-portability-audit.md) tracks the final integrated recovery/compatibility gate; individual inventory tests do not establish all-feature acceptance.

## P24-S3 Event and Budget attribution

Inventory movement attribution lives on the linked canonical transaction splits. Event and Fund continue through the existing core line references. Budget split attribution uses optional `extensions.scaJakartaH2.transactionBudgets`, an object with integer `version: 1` and `lines: [{lineId, categoryCode}]`. Only attributed posting lines appear. Export sorts relationships by portable line ID and checks company ownership through the canonical snapshot. Inventory extension version 1 is unchanged.

Import strictly validates the extension version, fields, nonblank category code (maximum 64 characters), unique line relationships, and references to nonzero posting lines. It resolves category codes within the selected company through the existing category import service, even when there is no Budget plan. Without complete master metadata, a newly created category uses its code as its initial name; transactionBudgets carries attribution only. Optional [S4D2 category masters](../P27-S4D2-category-portability.md) preserve names/descriptions/effective state/times and unused records independently. Transaction and line conflict hashes include the Budget code, so changed tags cannot be silently treated as an identical import. Identical reimport is a no-op. Tags, categories, transactions and movement links commit or roll back together.

Old files without this extension continue to import without inferred Budget tags. Older application versions do not support committing a populated `transactionBudgets` extension; use the updated reader to preserve attribution. The existing SCLX merge, correction-history, inactive-reference, and authorization policies remain in force. No new transaction is generated from inventory history during import.

P27-S4D3 integrated acceptance retains current inventory snapshot fields and their source creation/update timestamps independently from custody history. Restoring custody events does not replay them over the register or touch an unchanged item on retry. Custody quantities accept the serializer’s decimal strings as well as JSON numbers, must fit nonnegative DECIMAL(19,4), and reject missing/null/invalid/coerced values instead of becoming zero. See the [combined gate](../P27-S4D3-integrated-portability.md).
