# P17-C5 — Inventory item lifecycle user testing

## User-visible behavior

- Inventory item status is no longer a directly editable combo-box field.
- **Deactivate Item**, **Reactivate Item**, and **Dispose Item** are explicit lifecycle operations on the selected durable item.
- Deactivation and disposal require zero on-hand quantity; use normal Issue/Adjustment movement operations first.
- Disposed items remain retained terminal history and are not physically deleted.
- Every successful lifecycle transition requires a factual actor/reason and is written to Audit History.

## Owner acceptance checklist

Use a disposable/test database or a copy of production data.

- [ ] Open **Inventory**, select an `ACTIVE` item with positive quantity, and confirm **Deactivate Item** and **Dispose Item** are unavailable until quantity reaches zero; visible help explains why.
- [ ] Use **Issue Quantity** or **Adjust Count To Quantity** through its normal preview/confirmation to bring the item to zero. Confirm movement history and any canonical transaction remain visible.
- [ ] Enter a lifecycle actor/reason and choose **Deactivate Item**. Cancel once and confirm nothing changes; then confirm the action and verify the same stable item remains listed as `INACTIVE` with its movement history intact.
- [ ] Select the inactive item, enter a reason, choose **Reactivate Item**, and confirm the same item returns to `ACTIVE` without duplication.
- [ ] With quantity still zero, enter a reason and choose **Dispose Item**. Confirm the item remains listed as `DISPOSED`, its prior movements/transaction links remain visible, and the lifecycle UI offers no way to reactivate the disposed item.
- [ ] Open **Audit History** and confirm factual `INVENTORY_ITEM_STATUS_CHANGED` rows exist for the successful transitions with the actor and reason supplied.
- [ ] Open **Edit Selected** for active, inactive, and disposed examples. Confirm status is displayed but cannot be directly edited, while ordinary metadata saves preserve the current status.
- [ ] Confirm there is no generic or placeholder **Delete** action for inventory items.
- [ ] At laptop width, confirm lifecycle guidance/actions, item and movement tables, horizontal/vertical scrolling, and split divider remain usable.

## Acceptance record

Record any failure with item ID/name, quantity/status before and after, visible message, actor/reason, and whether Refresh changes the observed result. Do not merge P17-C5 until final-head GitHub Actions and this checklist are accepted.
