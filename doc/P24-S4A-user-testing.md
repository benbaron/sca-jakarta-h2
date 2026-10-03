# P24-S4A — Budget Category maintenance

Budget Categories is now a production maintenance destination. It uses the canonical company-owned `BudgetCategory` table and stable IDs. New/Edit saves code, name, active state, optional effective dates and description through the guarded service. Changes are audited. Categories are retained: clear Active and save to retire one; there is no physical Delete action because Journal, Budget, alias and import history may refer to it.

Manual acceptance:

1. Open a disposable company and authenticate as an accountant. Navigate to **Planning → Budget Categories**. Create `OPS / Operations`, add effective dates and a description, and save. Confirm it appears in the table and is offered by Journal and Inventory choice refresh.
2. Select the saved row, rename it, change the dates, save, restart, and confirm the same stable category is updated rather than duplicated. Search by code and name; clear search to see all active and inactive rows.
3. Clear Active and save. Confirm the row remains visible with historical details but is removed from new active choices. Reactivate it and refresh Journal/Inventory without losing an unfinished draft.
4. Try a blank code/name, duplicate code with different case, and an effective-from date after effective-to. Each must show a factual error and leave the prior row unchanged.
5. As a viewer, confirm Save, New and editor fields are disabled while search, table, Refresh and navigation remain available. A different company must not be able to edit or reuse the category ID.
6. Resize at approximately 900 and 600 pixels and larger text. Move the visible table/editor divider, widen/reorder/sort columns, and confirm both table/editor scrolling remain usable. Reopen the company and confirm table state is company-owned and retained.

Automated coverage should include stable-ID create/edit/deactivation, audit facts, duplicate/date validation, authorization, route capability, choice refresh and headless layout. Native installed-build visual evidence remains an owner acceptance step.
