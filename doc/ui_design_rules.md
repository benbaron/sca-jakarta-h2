# UI design rules

This document records cross-cutting UI display and interaction rules for the production SCA bookkeeping application. It applies to every production pane unless a more specific governing document narrows the behavior for a particular domain.

## Preference storage scope

Any preference mentioned in this document is a per-company preference. It must be saved with that company and restored when that company is active. User-interface state that changes how company data is displayed or edited must not be stored only as a global user preference when the behavior is company-specific.

## Tables

Every production table must support the following column behavior:

1. Columns are sortable.
2. Columns are resizable.
3. Columns are reorderable.
4. Column sort order, column widths, and column order are remembered in the saved state for the active company.

Table layout requirements:

- Every table has both vertical and horizontal scroll bars when content exceeds the visible area.
- Every table is separated in its own `SplitPane` region from any surrounding data in the table's major pane.
- Tables must not rely on clipping, oversized minimum widths, or wrapping-only behavior to hide unavailable content.

## Money display and editing

All money amounts in all data views and editors must follow the active company's money display preferences:

1. Displayed amounts include the money symbol configured in preferences.
2. Displayed amounts use the print format configured in preferences.
3. Editable money fields accept entries with or without the currency symbol and with optional decimals. On commit or focus loss, the UI corrects the displayed value to the configured money format rather than refusing otherwise valid numeric input.
4. Displayed money values always show two numerals after the decimal point.
5. These rules affect edit and display formatting only. They do not change the authoritative internal storage format or accounting precision.

## Date display and editing

All date fields in all data views and editors must follow the active company's date display preferences:

1. Displayed dates use the date format selected in Settings.
2. Editable date fields accept commonly accepted date formats.
3. The day/month/year ordering rule is not guessed; it comes from preferences.
4. On commit or focus loss, the UI corrects the displayed date to the configured default date format rather than refusing otherwise valid date input.
5. These rules affect edit and display formatting only. They do not change the authoritative internal storage format.

## Accounting period display

Accounting periods must be stated in days, quarters, or years as appropriate for the screen, report, or workflow. The start of each fiscal year or period is calculated from the active company's configured start preference. The top chrome active-period selector chooses an accounting period, not an arbitrary day, and the active period start date is derived from the selected period plus the configured period start day.

## Completed-phase retrofit obligations

These rules apply retroactively to UI surfaces delivered by completed phases. A completed phase is not reopened wholesale, but any corrective slice that touches an existing surface must bring that surface into conformance with this document or record a visible follow-up in `doc/PLAN.md`.

- P00 documentation inventories must identify panels whose table state, money/date formatting, period display, Delete behavior, or split-pane/scroll behavior is not yet compliant.
- P01 shell and workspace surfaces must store qualifying preferences per company and must not keep company-specific display behavior only in global user state.
- P02 services remain the authority for accounting data and internal precision; UI money/date format correction must never alter service command precision, entity precision, or persisted date types.
- P03 Ledger Register and Transaction Editor must use the table, money, date, Delete, split-pane, and period rules when their controls are next changed.
- P04 budget surfaces and later table-heavy panels must implement sortable/resizable/reorderable table columns with per-company saved state before those panels are considered design-rule complete.
