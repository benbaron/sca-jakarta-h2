# Fund lifecycle and administration

## Purpose

Funds are H2-backed accounting master data. They are referenced by canonical transaction splits and may also be referenced by budgets, fixed assets, inventory, aliases, transfers, and child funds. Fund administration must preserve those relationships and must not turn a display code into record identity.

## Stable identity

`FundCommand.id` is the edit identity.

- A null ID creates a new fund.
- A non-null ID updates that exact fund.
- Changing a fund code does not create a second fund and does not retarget another row.
- Code uniqueness is case-insensitive and is checked inside the service transaction.

The compatibility `FundAdminService.upsert(...)` method remains for older code-addressed callers, but the production Funds workspace uses `FundCommand` and stable IDs.

## Editable lifecycle fields

The production editor exposes the fields already owned by the `Fund` entity:

- code;
- name;
- fund type;
- active state;
- optional parent fund;
- optional effective-from and effective-through dates;
- restriction or purpose text.

The service validates required values, field lengths, date ordering, parent existence, self-parenting, and circular parent relationships.

## Deactivation and deletion

Deactivation and physical deletion serve different purposes.

### Deactivation

Clearing **Active** and saving retains the fund and every historical relationship. Deactivated funds remain visible in the Funds administration list but are excluded from active-fund selectors used for new accounting work.

Referenced funds must be deactivated rather than deleted.

### Delete Unused

`FundAdminService.deleteUnused(...)` physically removes a fund only when authoritative reference counts are all zero. The usage assessment covers:

- canonical `TxnSplit` rows;
- `BudgetLine` rows;
- `FixedAsset` rows;
- `InventoryItem` rows;
- `FundAlias` rows;
- `FundTransfer` source and destination references;
- child funds that name the fund as parent.

The JavaFX workspace first displays the reference summary. It asks for explicit confirmation only when the service reports that deletion is allowed. The service repeats the assessment in the deletion transaction, so the UI check is not the authority.

## Workspace behavior

The Funds workspace stacks the fund table above the editor in a `SplitPane`. The pane uses vertical item orientation, producing a horizontal draggable divider. Both regions can shrink with the window: the table retains its own scrolling and the editor retains its independent `ScrollPane`.

- **New** clears the editor for a new stable record.
- Selecting a row loads all lifecycle fields and records its database ID.
- **Save** creates or updates through `FundAdminService.save(FundCommand)`.
- **Delete Unused** performs a real protected delete; it is not a disabled placeholder.
- **Refresh** reloads active and inactive funds.
- Unsaved editor state is reported through the `AppPanel` dirty-state contract.

Date fields use the active company’s date preference through `CompanyUiFormat`. Table column width, order, sort state, and the horizontal workspace divider are stored in company-owned UI state under the `funds.` prefix.

## Validation

Automated coverage must verify:

- stable-ID code changes retain one row;
- all lifecycle fields persist;
- duplicate codes, invalid date ranges, self-parenting, and cycles are rejected;
- usage queries cover every authoritative reference type;
- unused funds can be deleted;
- referenced funds cannot be deleted but can be deactivated without losing references;
- the production panel uses the stable command, protected-delete service, company date formatting, split regions, dirty state, and company-owned layout state.

Desktop validation must confirm create, edit, code change, parent selection, date formatting, deactivation, blocked referenced deletion, confirmed unused deletion, table scrolling/sorting/reordering/resizing, divider movement/restoration, and laptop-width usability.
