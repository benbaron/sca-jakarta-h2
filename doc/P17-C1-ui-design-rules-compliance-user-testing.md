# P17-C1 — UI design-rules compliance owner verification

P17-C1 is the dedicated cross-cutting correction for production JavaFX compliance with `doc/ui_design_rules.md`. It deliberately does not implement the later durable-record Delete/deactivate/reverse/retire lifecycle work.

## User-visible scope

Verify the completed slice with at least two companies whose money/date preferences differ.

1. Money fields accept ordinary decorated/unadorned input and normalize to the active company's money format when committed or focus leaves the field.
2. Date controls accept supported input and normalize to the active company's date format.
3. New/cleared money fields show company-formatted zero rather than hard-coded `$0.00` or `0.00`.
4. Banking, Budget Editor, Reconciliation, Assets Register, Inventory, Dashboard, Bank Transactions export controls, Reviewed Statement Acceptance, Chart-of-Accounts JSON Preview, Import Preview, and other corrected production surfaces retain their existing accounting behavior while using the shared formatting/layout rules.
5. Period Close's active-period helper honors the configured period-start day instead of forcing calendar day 1.
6. Modal dialogs expose full-text tooltips and company-owned table state where applicable.
7. Major tables retain sortable/resizable/reorderable columns, unconstrained horizontal scrolling, company-specific column state, and independently resizable table/detail regions.
8. Switching companies restores that company's display and table-layout preferences rather than leaking state from the previous company.

## Desktop geometry

At the application's practical 1440x900 target and at a narrower laptop-sized window:

- controls must remain reachable without clipping;
- table/detail and table/editor dividers must remain draggable;
- horizontal and vertical scrolling must remain available where content exceeds the viewport;
- modal content must remain usable without relying on an oversized minimum window.

## Explicit boundary

Durable-record lifecycle completion is a later P17 corrective slice. P17-C1 must not add generic Delete buttons or change authoritative accounting correction semantics merely to satisfy UI compliance.
