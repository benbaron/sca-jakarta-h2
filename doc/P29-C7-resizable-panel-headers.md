# P29-C7 resizable panel control headers

The owner reports that Merchants has a cramped top pane and no horizontal resize
handle, and requests the same fix wherever this occurs. Treat action/filter/form
headers as adjustable control chrome above the existing business workspace.

Production PanelFactory/PanelViewport preparation installs a vertical SplitPane
between an actionable BorderPane header and its body, or between the existing
VBox control header (including one already in a ScrollPane) and its workspace. The handle spans the panel horizontally.
Keep the same controls, selections, handlers, drafts and original body dividers.
Journal already has this divider and is left intact. Title/status-only strips
remain ordinary chrome. Nested Administration tabs receive the same treatment.

Each header remains independently scrollable in both directions. Remove fixed
maximum heights on header scroll areas and allow scroll content to retain its
computed preferred minimum height. Expanding the header must expose its contents,
not just enlarge an empty wrapper. Body tables/forms retain their own scrolling.
Persist header size through the existing company-owned H2 UI preference authority,
with route/tab-specific keys and stale-scope guards. Existing saved body divider
keys and values remain independent.

The control header is chrome rather than a third business/data section. The body
still has at most two visible major sections. Tests must prove an actual visible
horizontal divider, meaningful header-height change on movement, body resize,
scrollbar visibility and final-control reachability at narrow/large-font sizes,
state restoration, and unchanged draft/Save/permission/route behavior.

Owner checks: open Merchants and expand its top pane by dragging the new horizontal
handle. Repeat on other maintenance/form panels and Administration sub-tabs.
Check a small window and enlarged text; scroll to the last action/field. Resize
the lower list/editor independently, switch away/back and restart to verify saved
sizes. Draft edits and financial commands must retain their original behavior.

## Production audit

23 of the 29 canonical destinations receive the shared treatment:
Banking, Budget Editor, Budget vs Actual, Budget Categories, Recognition
Assistant, Counterparties, Merchants, Merchant Settlement Assistant, Assets,
Depreciation, Property Custody, Reconciliation (Checks / Exceptions), Period
Close, Import Preview, Opening Conversion, Audit History, Bank Transactions,
Report Library, Chart of Accounts, Funds, Fund Transfers, Activities and
Administration. Chart of Accounts has two distinct control headers;
Administration includes Company Admin, Bank Accounts, User Admin, Company
Ownership Diagnostics and NMR Policies.

Unchanged cases: Journal already has a header/table divider; Inventory already
has a controls/workspace divider; Dashboard and Event Accounting have title/status
strips rather than an adjustable control header; Diagnostics and Help are flat
scrollable pages rather than a cramped header above a business workspace. The
route test traverses inactive tabs as well, records each header path, and verifies
that header chrome contains no additional business table.
