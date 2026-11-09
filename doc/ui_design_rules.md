# UI design rules

This document records cross-cutting UI display and interaction rules for the production SCA bookkeeping application. It applies to every production pane unless a more specific governing document narrows the behavior for a particular domain.

## Work panes and overflow access

P29-S2 replaces the three-column shell with left navigation and the work area. The right Inspector and its controls/shortcut are removed. Every work pane shows at most two major sections, including nested subdivisions. Extra functions use named work panes sharing the same controller and draft. Labels and local command/tool bars are chrome, not additional independent sections. Resizable action/form headers remain chrome; adding a divider above the existing business body does not add a third business/data section.

Every destination and logical tab has horizontal and vertical overflow access when its content exceeds the available size. Forms retain scrolling and tables retain independent horizontal/vertical scrolling; reducing the window must not make controls unreachable. Do not disable a needed scrollbar axis. The earlier obligation to add nested resize bars around every Journal subsection is superseded by the two-section limit. See [P29-S2](P29-S2-workspace-simplification.md) for the complete route review and user checks.

## Full text hover tooltips

Every production JavaFX widget with visible non-blank display text should expose that full text in a hover tooltip so clipped, abbreviated, or partially hidden labels remain readable. This applies to labels, buttons, check boxes, radio buttons, menu buttons, table/list/tree cells, combo boxes, choice boxes, date pickers, spinners, and similar non-text-entry controls.

Text boxes and other `TextInputControl` instances are excluded so user-entered or potentially sensitive typed text is not automatically repeated in a tooltip. Controls that already define a custom tooltip may keep it when the tooltip carries more specific help text.

Tooltip popups set their foreground and background together: black text on a
light neutral bubble in light themes, white text on a dark neutral bubble in
dark themes. This includes the skin's painted text, automatic full-text and
custom help tooltips, and existing tooltips reopened after a theme change.
Ordinary workspace foreground rules must not override that pairing.

## Theme text contrast

Production exposes the Java-17-compatible AtlantaFX 2.0.1 theme set: Primer, Nord and Cupertino in light/dark variants plus Dracula, alongside JavaFX System Default. Application shell CSS uses AtlantaFX palette variables for surfaces and controls. Ordinary workspace text uses exact black or white according to the effective painted background, choosing the greater WCAG contrast. White/pastel reports retain black text even inside dark themes; dark surfaces use white text. Transparent backgrounds inherit the composed ancestor surface. Enforcement runs after CSS/layout, covers dynamically created controls and never modifies a skin during its construction. The native Windows title bar remains operating-system controlled. Existing persisted Light/Dark settings map to Primer Light/Primer Dark. Theme changes apply immediately and must preserve readable menus, toolbar controls, forms, tabs, navigation, work panes, tables and lists.

## Windows rendering compatibility

The desktop launcher selects JavaFX software rendering (`prism.order=sw`) on Windows before starting the toolkit. Owner testing confirmed that software rendering restores controls when the accelerated pipeline produces a blank client area without an exception. This is a process startup default, not a saved theme or accounting preference. An explicit `-Dprism.order` is preserved; Linux and macOS keep their platform defaults. Software rendering can use more CPU. For deliberate hardware testing in Git Bash use `JAVA_TOOL_OPTIONS='-Dprism.order=d3d,sw' mvn -Pui clean compile javafx:run`.

## Preference storage scope

Preferences have two explicit scopes:

- user-machine shell preferences cover theme, native/unified window decoration, and whether window geometry and shell dividers are restored; `UserAppStateStore` owns these values outside H2 because they describe one desktop installation rather than company accounting facts;
- per-company preferences cover money/date display and company-specific table/divider state; H2 `company_ui_preference` and `company_ui_state` rows own these values and restore them only for the active company.

User-interface state that changes how company data is displayed or edited must not be stored only as a global user preference. Conversely, a company switch must not change user-machine theme or top-level window geometry. Settings must label restart-time shell choices, and **Remember window state** must be the sole gate for reading or writing top-level geometry and shell divider state.

Compatibility values such as `defaultPrivilege` and `defaultReopenScope` are not enabled Settings controls until a governed production consumer exists. In particular, a stored privilege label is not authentication or effective authorization.

## Global commands and shortcuts

Every editable pane or subpane exposes its real save operation through a visible **Save** button or **File -> Save** entry. Shared transaction details use the owning editor's Save; the control or help text states that scope. Composite panels route Save to the selected subpane and refresh command availability when selection, readiness or busy state changes. Save must retain the same validation, permissions, confirmation and authoritative service as the existing operation.

Read-only results and history have no independent draft to save. Their Save entry remains disabled with an explanation; supported report/file output uses Export or Backup. Deliberate accounting operations such as closing a period, running depreciation, reversing or matching retain their specific command and confirmation: those commands persist immediately and require no later Save.

An enabled global command must identify a real operation in the active production panel. Panels publish current New, Save, and typed validation support through `AppPanel.commandCapabilities()`; the shell disables unsupported menu and toolbar controls and provides a concise explanation. Composite panels must recalculate capabilities when their selected mode changes.

Default command labels, accelerators, and Help shortcut text come from `GlobalCommandRegistry`; `AppPanel.commandLabel` may supply the selected subpane's specific save scope. Help must not advertise an uninstalled shortcut. The workspace must not intercept standard Copy/Paste accelerators merely to route them to empty panel hooks; focused JavaFX text controls own their native editing behavior. A handled result is returned only after a declared operation has been invoked successfully; undeclared commands and thrown failures return a factual not-handled result.

## Panel instructions and help

Keep operating instructions in the [panel and function guide](ui/panel-guide.md),
organized under Markdown function headings for later manual assembly. Remove
redundant introductions, implementation details and development-phase notes from
panels. Put short, control-specific hints in hover tooltips, installed before
permission gating so permission explanations remain authoritative.

Do not reserve a pane, splitter section or empty region for explanatory prose.
Keep current errors, validation, results, confirmation details and status visible.
Where a durable record has no Delete action, retain a brief visible explanation
of its supported correction/deactivation action. Tooltips supplement meaningful
labels and do not hide information required to understand the current result.

## Tables

Every production table must support the following column behavior:

1. Columns are sortable.
2. Columns are resizable.
3. Columns are reorderable.
4. Column sort order, column widths, and column order are remembered in the saved state for the active company.

Table layout requirements:

- Every table has both vertical and horizontal scroll bars when content exceeds the visible area.
- Every table has a bounded, independently scrolling region. A paired major section uses a draggable `SplitPane`; local labels and tools remain with their table so they do not create extra subdivisions.
- Tables must not rely on clipping, oversized minimum widths, or wrapping-only behavior to hide unavailable content.

### Unified Journal implementation

The production Journal is created through `JournalWorkspaceCompliancePanel`, which decorates the service-backed `JournalWorkspacePanel` without replacing its accounting services. The compliance layer must preserve the following behavior:

- Journal, Entry, Additional Details and Supplemental Details are separate work panes with their own overflow viewports;
- each selected work pane retains at most two major sections and the appropriate draggable divider;
- every Journal, entry-line, and supplemental table uses unconstrained resizing, sortable/resizable/reorderable columns, independent table scrolling, and a bounded table region;
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

Entry dates and reporting cutoffs have different defaults: new accounting drafts
propose period start; recognition/check-review cutoffs and whole-period export
ends use the company fiscal period end. Preserve existing drafts and factual
dates. Bank export dates follow until explicitly edited, including raw text or
clearing a field; normal DatePicker skin/format updates are not manual edits.
See the [workflow defaults matrix](accounting/period-and-correction-policy.md#workflow-date-defaults-p10-c2).

Period selection, Set Active Period and the active-range label belong to Period Close, not the main toolbar. Keep the close-date draft independent: applying a default period must not replace its dates or an unsaved review.

Period Close and Depreciation use the same `FiscalPeriodRange` end as reports
and Dashboard, including the fiscal-year cap. Their calculated ranges use the
already selected start; changing Settings does not silently recalculate it.
Startup applies loaded period-start preferences before opening workspace panels.
See [period consistency](accounting/period-and-correction-policy.md#p10-c1-calculated-period-consistency).

Accounting periods must be stated in days, quarters, or years as appropriate for the screen, report, or workflow. The start of each fiscal year or period is calculated from the active company's configured start preference. The Period Close active-period selector chooses an accounting period, not an arbitrary day, and the active period start date is derived from the selected period plus the configured period start day.

## Completed-phase retrofit obligations

These rules apply retroactively to UI surfaces delivered by completed phases. A completed phase is not reopened wholesale, but any corrective slice that touches an existing surface must bring that surface into conformance with this document or record a visible follow-up in `doc/PLAN.md`.

- P00 documentation inventories must identify panels whose table state, money/date formatting, period display, Delete behavior, split-pane/scroll behavior, or full-text hover tooltip behavior is not yet compliant.
- P01 shell and workspace surfaces must store qualifying preferences per company and must not keep company-specific display behavior only in global user state.
- P02 services remain the authority for accounting data and internal precision; UI money/date format correction must never alter service command precision, entity precision, or persisted date types.
- P03 Journal workspace uses the table, money, date, Delete, split-pane, period, and tooltip rules through its production compliance layer.
- P04 budget surfaces and later table-heavy panels must implement sortable/resizable/reorderable table columns with per-company saved state before those panels are considered design-rule complete.

### P10-C4 close-state and affected-range interaction

Period Close and the Dashboard Cash card use the shared inclusive closed-range
coverage projection: Open, Partially closed, Closed. Adjacent closed ranges can
cover the whole selected period; reopened history does not count. Show textual
state with company-formatted dates. The Dashboard link and Journal closed-date
error link navigate to actual range/history rows without applying period defaults,
discarding drafts, reopening or retrying accounting commands. Keep these controls
in their existing sections and preserve horizontal/vertical overflow.

Close/reopen commands capture their service, scope and inputs on the FX thread.
Disable conflicting commands while busy, retain rejected inputs, ignore stale
loads and refresh current range/history/coverage after success. Invalidate old
readiness/review observations; never silently save a review or clear another
company/database draft. Dashboard reloads when shown again.

## P29-C7 independently resizable control headers

Production control headers above a business workspace expose a real horizontal
drag handle (a vertical SplitPane), including nested Administration views. Fixed
scrollable VBox headers and actionable BorderPane headers use the shared viewport
preparation rule. Preserve the existing body splits and draft-owning controller;
retain Journal's existing header split without duplication. Pure title/status
strips do not require a new divider. Both axes scroll when needed. Header scroll
areas must not retain maximum-height caps that defeat expansion. The divider is
saved by company/database and stable route/tab identity, using the existing UI
state guard. See the [audit and checks](P29-C7-resizable-panel-headers.md).
