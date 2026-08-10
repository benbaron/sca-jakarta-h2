# UI design rules

This document records cross-cutting UI display and interaction rules for the production SCA bookkeeping application. It applies to every production pane unless a more specific governing document narrows the behavior for a particular domain.

## Full text hover tooltips

Every production JavaFX widget with visible non-blank display text should expose that full text in a hover tooltip so clipped, abbreviated, or partially hidden labels remain readable. This applies to labels, buttons, check boxes, radio buttons, menu buttons, table/list/tree cells, combo boxes, choice boxes, date pickers, spinners, and similar non-text-entry controls.

Text boxes and other `TextInputControl` instances are excluded so user-entered or potentially sensitive typed text is not automatically repeated in a tooltip. Controls that already define a custom tooltip may keep it when the tooltip carries more specific help text.

## Preference storage scope

Preferences have two explicit scopes:

- user-machine shell preferences cover theme, native/unified window decoration, and whether window geometry and shell dividers are restored; `UserAppStateStore` owns these values outside H2 because they describe one desktop installation rather than company accounting facts;
- per-company preferences cover money/date display and company-specific table/divider state; H2 `company_ui_preference` and `company_ui_state` rows own these values and restore them only for the active company.

User-interface state that changes how company data is displayed or edited must not be stored only as a global user preference. Conversely, a company switch must not change user-machine theme or top-level window geometry. Settings must label restart-time shell choices, and **Remember window state** must be the sole gate for reading or writing top-level geometry and shell divider state.

Compatibility values such as `defaultPrivilege` and `defaultReopenScope` are not enabled Settings controls until a governed production consumer exists. In particular, a stored privilege label is not authentication or effective authorization.

## Global commands and shortcuts

An enabled global command must identify a real operation in the active production panel. Panels publish current New, Save, and typed validation support through `AppPanel.commandCapabilities()`; the shell disables unsupported menu and toolbar controls and provides a concise explanation. Composite panels must recalculate capabilities when their selected mode changes.

Production labels, accelerators, and Help shortcut text come from `GlobalCommandRegistry`. Help must not advertise an uninstalled shortcut. The workspace must not intercept standard Copy/Paste accelerators merely to route them to empty panel hooks; focused JavaFX text controls own their native editing behavior. A handled result is returned only after a declared operation has been invoked successfully; undeclared commands and thrown failures return a factual not-handled result.

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

### Unified Journal implementation

The production Journal is created through `JournalWorkspaceCompliancePanel`, which decorates the service-backed `JournalWorkspacePanel` without replacing its accounting services. The compliance layer must preserve the following behavior:

- the complete editor region is wrapped in one `journalWorkspaceEditorScroll` vertical `ScrollPane`;
- the Journal/editor, editor subsections, and additional/supplemental detail regions retain draggable `SplitPane` dividers;
- every Journal, entry-line, and supplemental table uses unconstrained resizing, sortable/resizable/reorderable columns, independent table scrolling, and a dedicated table `SplitPane` region;
- column width, order, sort direction, sort priority, and divider positions are stored through company-owned UI preference state rather than Java global user preferences;
- money and date display/edit behavior uses the active company's `CompanyUiPreferences` through `CompanyUiFormat`.

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

## Delete controls

Do not add disabled placeholder Delete buttons. A Delete button should be present only when it performs a real supported delete or correction operation through the authoritative service. Non-deletable records should be explained through status/help text or the applicable inactive, disposed, reversal, or correction workflow.

## Accounting period display

Accounting periods must be stated in days, quarters, or years as appropriate for the screen, report, or workflow. The start of each fiscal year or period is calculated from the active company's configured start preference. The top chrome active-period selector chooses an accounting period, not an arbitrary day, and the active period start date is derived from the selected period plus the configured period start day.

## Completed-phase retrofit obligations

These rules apply retroactively to UI surfaces delivered by completed phases. A completed phase is not reopened wholesale, but any corrective slice that touches an existing surface must bring that surface into conformance with this document or record a visible follow-up in `doc/PLAN.md`.

- P00 documentation inventories must identify panels whose table state, money/date formatting, period display, Delete behavior, split-pane/scroll behavior, or full-text hover tooltip behavior is not yet compliant.
- P01 shell and workspace surfaces must store qualifying preferences per company and must not keep company-specific display behavior only in global user state.
- P02 services remain the authority for accounting data and internal precision; UI money/date format correction must never alter service command precision, entity precision, or persisted date types.
- P03 Journal workspace uses the table, money, date, Delete, split-pane, period, and tooltip rules through its production compliance layer.
- P04 budget surfaces and later table-heavy panels must implement sortable/resizable/reorderable table columns with per-company saved state before those panels are considered design-rule complete.
