# Production workspace and dashboard

## Status

This document records the approved production direction for the SCA bookkeeping desktop application. It supersedes prototype assumptions that conflict with the decisions below.

## Production shell

The dashboard experiment becomes the production main window rather than remaining a visual demonstration.

The application shell shall provide:

- a menu bar and action toolbar;
- a resizable and collapsible left navigation pane;
- a responsive tabbed center workspace;
- a resizable and collapsible right inspector pane;
- visible draggable dividers;
- a status bar;
- external CSS styling;
- appropriate scrolling without allowing center content to render beneath either sidebar.

The application shall open on the dashboard. The dashboard tab shall remain available throughout the session.

## Workspace tabs

The center workspace uses one reusable tab per panel type.

Opening a destination that is already open activates its existing tab. Closing a dirty tab prompts the user to save, discard, or cancel. Database switching performs the same dirty-state check across all open tabs.

The inspector follows the selection in the active workspace tab. Its production scope includes selected-record details, contextual actions, editable notes, related records, and audit history. Approvals and attachments are out of scope.

## Navigation

Navigation uses a hybrid structure: workflow-oriented top-level groups with accounting subjects beneath them.

The Ledger Register and ordinary Transaction Editor become one workspace with the register above and the editor below. Journal Entry remains a separate destination but reuses the common transaction-line editor.

## Organization and database selection

Each organization uses a separate H2 database file. Switching organization therefore means switching the active database file and rebuilding all database-bound services.

The application shall not offer an operation that adds another accounting organization inside the active database. Organization identity is read from the selected database. Recent database paths may be retained for convenience.

## Global accounting period

The global period control represents the active accounting period. It is not a generic report date range.

Browsing another period does not silently change the transaction-entry period. Changing the posting or entry context requires an explicit **Set Active Period** action. Report date ranges remain local to report panels.

## Dashboard contents

Every displayed amount or count must come from the active database. Production code shall not display fictional fallback values.

The dashboard includes:

- book cash balance as the dominant cash figure;
- reconciled or cleared balance;
- unreconciled difference;
- combined cash total plus significant individual bank accounts;
- year-to-date surplus or deficit across all funds;
- unrestricted, restricted, and designated breakdowns;
- chart, totals, and drill-down for fund classifications;
- combined budget performance with configurable exceptions;
- one pending-work summary with counts by workflow and a combined action list;
- recent entered transactions;
- reconciliation status;
- quick links that immediately begin the named action.

Budget exceptions include current overruns, projected overruns, and configurable thresholds.

## Roles

Roles and per-user overrides may be displayed and persisted for future use, but they are informational in the current production scope. There is no formal financial-committee or approval role in this application slice.

## Explicitly excluded features

The following are outside the production workspace scope:

- transaction approval workflows;
- a separate posting workflow or posting queue;
- formal oversight roles;
- document attachments;
- persistent import staging;
- a void workflow for saved transactions.
