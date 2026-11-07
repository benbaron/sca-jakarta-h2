# Panel and function guide

This is the Markdown source for panel-level operating instructions. Each function
has its own heading so it can be gathered into a manual. Screens show controls,
current results and validation; hover over a control for a short local hint.

## Getting started

Choose **File → Database → Select Database File…** or **Create New Database…**.
Choose an active company in the toolbar, select the accounting month, and choose
**Set Active Period**. Create and maintain companies in **Administration → Company
Admin**. Sign in with an account assigned to the selected company. Leave Password
blank for an account without a password; ADMIN maintains account passwords.

Open destinations through the left navigation or Destinations menu. Journal is
the transaction entry and review workspace. Banking, Bank Reconciliation and Bank
Transactions handle bank work. Preview supported imports before accepting them.
File/toolbar Save applies to the active function; explicit action buttons identify
operations with a different scope. A disabled command may require a selection,
a valid preview, or a permission available to another role.

## Dashboard

Review the selected company's cash, open items, budget comparison and recent
transactions. Use the named views and links to open the owning workspace. Empty
or unavailable figures describe the current records; they are not sample values.

## Journal: register and entry

Search by dates, memo, payee, check/reference, bank, Fund or Event. When Fund and
Event filters are both supplied, the same entry line must match both. Each result
is a complete transaction; its totals include all lines, not just matching lines.
Double-click a transaction or choose **Edit Selected** to open **Entry**.

New/Edit uses one draft across Entry, Additional Details and Supplemental Details.
**Save Entry**, File Save and toolbar Save save the complete transaction together.
Enter at least two meaningful lines with balanced debits and credits. Add,
duplicate or remove draft lines as needed. Current validation and totals remain
visible beside the entry.

## Journal: Fund and Event assignments

Select one or more entry lines, search for a Fund or Event by name/code, and use
the corresponding Apply action. Only selected lines change. Fund and Event are
independent; Clear Event preserves Fund. Maintenance and refresh actions retain
the current draft. Fund, Event/Activity and Budget are separate classifications.

## Journal: payment and additional details

Additional Details holds transaction identity, status, Payee and header bank
context. Enter payment method, Check / Reference and issue/delivery dates on each
individual bank line. Check numbers must be unique within the issuing account,
including retained reversed checks. Cleared state is maintained by reconciliation.

## Journal: supplemental details

Check Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset or Other
Liability to enable that type's editor. **Add Open Item Detail** creates a draft
row. **Apply Existing Item** links an existing open item. **Remove Detail** removes
the selected draft detail; the owning transaction's Save/correction operation
applies the change. Every supplemental tab saves with the full entry.

Each detail has a stable Item ID, an Increase/Decrease effect, and a **1-based
Ledger Line**: 1 means the first entry line. Existing item links survive SCLX
transfer. An empty table is ready for a new detail when its type is checked.

## Journal: merchant receivables

Merchant role is optional. Receipt or settlement requires Merchant and Event on
the receivable's linked entry line. Enter comma-separated, 1-based payout, fee and
evidenced NMR line numbers. Linked lines must share the Merchant, Fund and Event;
each whole line can be claimed once.

Observed withholding is a dated, nonfinancial fact and requires evidence. Blank
means unknown; zero is an explicit observation. Withholding alone never settles
the receivable or an NMR liability. Save Entry saves these facts with the entry.

## Journal: NMR item designation

In Other Liabilities, choose **NMR recipient** to designate an NMR item explicitly.
The linked line needs an Event and an eligible credit-normal liability account.
Keep recipient, account, Fund and Event consistent across the item's allocations;
use separate items for different dimensions. Blank recipient means unclassified.
An entry line's NMR checkbox does not designate an open item.

Choose the movement explicitly. Merchant discharge requires Merchant on the
linked line and NMR evidence; observed withholding alone is not a discharge.

## Payees / Counterparties and Merchants

Create or edit company-owned names and contact/merchant details. Journal refreshes
choices without discarding its draft when you return. These records are retained
for transaction, import and audit history. Clear Active and Save to retire one;
existing references remain valid while new choices omit inactive records.

## Merchant Settlement Assistant

Select one existing merchant receivable. Enter the actual payout, fee and optional
evidenced NMR discharge, then Preview. Save Settlement accepts the unchanged,
ready preview as one Journal transaction. Bank clearing stays in reconciliation;
chargebacks and refunds use Journal correction.

## Event Accounting

Select the Event/Activity and date range. Review the explicitly event-tagged
Journal splits and double-click a row to open its transaction. This is a read-only
view. Related bank rows come from the same transactions; they are context, not
an assertion that the bank line itself was allocated to the Event. Bank inflow
and outflow use configured bank-account lines.

## Banking and configured bank accounts

Use Banks or Bank accounts to maintain the selected record type. New/Save follows
that selected view and retains drafts in the other view. The Administration bank
account summary is read-only; create or edit its records in Banking. Import Bank
Statement opens Import Preview. Review / Export Statements opens Bank Transactions.

## Bank Transactions

Ledger Activity is the accounting record. Statement Review contains imported bank
evidence for matching or explicit acceptance. Import alone does not create a
second bank ledger. Review selected rows before accepting them or opening their
transaction for correction.

## Bank Reconciliation: setup and statement

Work through Setup, Statement, Match and Review. Enter a manual statement line or
open Import Preview for OFX/QFX, mapped CSV or normalized bank CSV. QIF is not a
supported production import format. Select the intended configured bank account
and statement dates before matching.

## Bank Reconciliation: matching and continuation

Match statement rows to ledger lines and review differences. A difference
explanation records a reconciliation fact without creating or changing a Journal
entry. **Warn only** preserves ledger cleared state; use **Mark Cleared** before
matching an uncleared ledger line when required.

Finalized sessions are read-only. Start a successor to continue reconciliation
while preserving the finalized predecessor.

## Bank Reconciliation: check review, Void and Reissue

Choose an issued-on/before cutoff to find older checks. Age alone never cancels
a check, removes an obligation or changes accounting. Save Review changes only
delivery/evidence facts. References identify external evidence; they are not
attachments and are not independently verified.

Void requires a cancellation reason and reverses the whole selected transaction.
Reissue reverses the check transaction and creates a linked replacement with the
same allocation, avoiding a second expense. Retained correction history cannot
be edited or deleted. Cleared-state changes belong to Match; resolve other bank
errors or differences through the owning bank or Journal workflow.

## Budget Editor and Budget Categories

Choose an explicit draft version, Fund/Event scope and category, edit the amount,
then Save. Create Revision copies the selected active version for editing;
activation affects only the selected draft. A replacement activation archives
the previous active version. Archive Draft retires an abandoned draft. Versions
are retained and archived versions are read-only.

Budget Categories are company-owned classifications used by Budget, Journal,
Inventory and Event cost reporting. Clear Active and Save to retire a category;
its budget, transaction and import references remain intact.

## Budget vs Actual

Compare actual activity through the active accounting period with the active
budget version. Choose Fund and Event filters and Run. General / no event shows
untagged activity separately; scoped results avoid double counting.

## Recognition Assistant

Choose a prepaid or deferred open item and Preview its straight-line installments.
The preview does not create an entry. **Save Selected Recognition** accepts the
selected ready proposal as a Journal transaction. File/toolbar Save has the same
scope. Correct saved recognition through Journal.

## Asset Register and Depreciation Runs

Choose an acquisition route. Save Asset/File Save saves the register record and
acquisition together. For payable invoices, checks or more complex allocations,
enter Journal first and Link existing entry. Opening / register only creates no
accounting entry; existing links are not guessed.

Assets are retained rather than deleted. Deactivate temporarily stops
depreciation and financial lifecycle actions; Reactivate resumes them. Use Sale
or Retirement for disposal. A disposed asset remains in history until that
lifecycle event is reversed. Depreciation Runs previews and accepts eligible
depreciation through the existing asset workflow.

## Inventory

Maintain item identity and review Movement History. Inventory items are retained;
use the appropriate movements to reach zero quantity before deactivation or
disposal. Disposed items remain terminal history.

For financial movements, Event and Budget classify the offset line; both lines
retain the item's Fund. Select an Event or explicitly confirm Non-event. Budget
is independent and optional. Zero-value nonfinancial movements cannot carry
accounting tags. Review the preview before committing or reversing a movement.

## Property Custody

Record guardianship, confirmations, loans, returns, transfers and removals.
Custody events do not create accounting entries. A removal must reference the
existing asset lifecycle event or reducing inventory movement that recorded the
accounting treatment. Saved history is retained; Correct Selected records a
replacement rather than rewriting the original event.

## Import Preview and Chart of Accounts interchange

Choose SCLX, COA CSV, OFX/QFX, mapped CSV or normalized bank CSV, inspect the
preview, resolve errors and confirm the intended target before accepting it.
Current validation messages, selected-message details, counts and confirmations
remain visible. Previewing itself does not save imported records.

Chart of Accounts JSON is separate from SCLX and whole-database backup. For
MAP_CODES, supply one explicit `sourceCode=targetCode` line for every source code.
Review the target chart and accepted/rejected rows before saving the import.

## Opening Conversion

Review account openings, configured bank baselines, outstanding bank items,
assets, inventory and supplemental schedules. Acceptance records an immutable
review/audit fact. It does not create or balance accounting entries. Preview again
when the scope or underlying records change.

## Period Close: readiness and review evidence

Preview Readiness to inspect automated findings and open their correction screens.
These findings do not independently verify external evidence or authorize closing.
In Review Evidence, preview the selected dates, record external references and
factual self-attestation, and Save Review. Saved reviews cannot be edited or
deleted; start a new review to correct them. Blockers remain blockers.

Save Closed Range requires a current matching review, zero blockers and reasons
for every warning. The close operation rechecks the accounting content before
saving. A reopened range needs a new review before closing again.

## Audit History

Review factual change history and the selected event's details. Retained history
is read-only. Use the owning business function for corrections.

## Report Library

Select a report and set Parameters. Run Report opens Preview. Explicit report
dates may differ from the toolbar period. Export uses the chosen format and
requires export permission. Accounting packet export uses its own start/end dates
and includes all funds/events, with exceptions reported in its manifest.

## Chart of Accounts and Funds

Accounts and funds retain stable identity when their code or name changes.
Deactivate referenced records instead of deleting their history; only unused
funds can be physically deleted. Before deactivating a parent Fund, deactivate
or reparent its active children. Reactivate parents before their children.

## Fund Transfers

Allocate between unrestricted or designated funds in the same company. This
changes fund allocations, not bank balances. History shows the latest 500
transfers. Saved transfers cannot be edited or deleted: Reverse Selected, then
enter a corrected transfer. SCLX preserves transfer history and ledger links;
keep a whole-database backup for complete recovery.

## Events / Activities

Choose New Event, enter a distinct code and name, and Save. Search by name or code.
Annual events may share a name but require different codes. Select a row and Edit
to rename it without changing existing Journal links. Clear Active and Save to
deactivate; select Active and Save to reactivate. Inactive events remain in history
and are omitted from new choices. Delete Unused requires no Journal or durable
interchange references; otherwise deactivate the event.

## Administration: company profiles, charts and reporting defaults

Create/edit company profiles and informational EINs. Clear Active and Save to
deactivate a non-current company; the current company and last active company
are protected. Companies are not hard-deleted. Select Active Company explicitly
to change the workspace company. Bank accounts are maintained in Banking.

The active Chart of Accounts controls new account maintenance and chart-targeted
imports. Selecting a draft chart promotes it to active. Existing charts, accounts,
transactions and references remain with their original chart.

Reporting defaults apply when a new Report Library opens. Dates, Fund, row limit,
account, asset and inventory filters remain controlled by the active period or
operator. Choice changes save immediately; Save Reporting Defaults saves the
displayed choices. File Save saves the company profile.

## Administration: Preferences and database transfer

Apply/Save presentation preferences for the active company. Change the connected
database using File → Database so the selected target is validated before it
becomes active.

Legacy stored privilege and default-reopen-scope values are compatibility data,
not preference controls. Authorization comes from the signed-in account's company
role assignments. Reopening uses the selected close range and its actual policy.

Backup Database creates a supported live backup. Restore Database Copy creates
and validates a new copy; it does not overwrite or automatically replace the
active database. Use Switch to Validated Copy when ready.

## Administration: company ownership diagnostics

Review missing or conflicting company ownership before assigning a record.
The active company receiving an import is the target for a directly ownerless
record. No accounting reference changes automatically. Supply an audit note and
assign only the selected record. Stale, already-owned or conflicting records are
rejected without a partial assignment.

## Administration: users, roles and authentication

Users, roles and assignments retain history. End or revoke active non-reserved
assignments before deactivating a user. Reserved accounts retain their fixed
username and active state. ADMIN, MANAGER, ACCOUNTANT and VIEWER are fixed security
roles and cannot be renamed or deactivated.

Assignments apply to the active company and determine the user's effective roles.
ADMIN's assignment is permanent. Other assignment changes retain dated history;
ending/revocation dates cannot be in the future. Account passwords belong to users,
not roles. Only ADMIN sets, replaces or clears them. Save Password updates the
selected account; File → Save Timeout saves only the inactivity timeout.

## Administration: NMR Policies

Record dated source-policy terms and explicit rates/currencies. Saving does not
verify external approval, calculate NMR or create accounting entries. Blank rate
never means zero. Retained versions cannot be edited or deleted. End a version
with a reason, then add a non-overlapping successor. Rule descriptions are recorded
evidence rather than executable calculations. Whole-database backup preserves
these records; SCLX carries saved versions, with company-administration permission
required on import.

## Diagnostics and database recovery

Review the current diagnostic or startup failure. Retry/Repair Current Database,
Select Existing Database or Create New Database as appropriate. A failed or
cancelled recovery leaves the prior active workspace available. The displayed
database path and actual error remain visible for diagnosis.

## Help and keyboard shortcuts

Help displays the currently installed shortcuts and links to this guide and
project documentation. Copy/Paste use the standard shortcuts of the focused
text control. Hover hints supplement field labels; validation and current status
stay visible without hovering.
