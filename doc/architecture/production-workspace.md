# Production workspace and dashboard

This document records the approved production direction for the SCA bookkeeping desktop application.

## Production shell

The dashboard experiment becomes the production main window. The shell provides a menu bar, toolbar, collapsible navigation, tabbed center workspace, collapsible inspector, draggable dividers, status bar, external CSS, and responsive scrolling.

The application opens on the dashboard. The dashboard tab remains available throughout the session.

## Workspace behavior

The center workspace uses one reusable tab per panel type. Opening an existing destination activates its tab. Dirty work is checked before a tab or database context is closed.

The inspector follows the selection in the active tab and may show details, contextual actions, editable notes, related records, and audit history.

Pane content that can hide default-size text or tabular values must not depend on clipping, wrapping-only behavior, or oversized minimum widths. The affected pane portion must be placed behind a visible `SplitPane` divider when it shares space with another pane portion, and the content region must expose both vertical and horizontal scrolling so users can reach hidden rows and columns at supported laptop sizes and display scaling.

Every production workspace function that maintains durable records exposes Delete or a visible explanation of why Delete is not allowed. Delete commands are owned by the same application service boundary as Save/Update and must not remove rows only from the UI. Transaction Delete follows Settings -> Correction method: `DIRECT_EDIT` permits audited hard deletion after period/reconciliation checks; non-direct correction methods ask whether to auto-fill and perform a reversing entry instead.

Detailed production rules for table column state, table scroll/split behavior, money formatting, date formatting, and period wording are governed by `doc/ui_design_rules.md`.

## Navigation and editors

Navigation uses workflow-oriented top-level groups with accounting subjects beneath them. The Ledger Register and ordinary Transaction Editor become one workspace with the register above and editor below. Journal Entry remains separate and reuses the common line editor.

## Organization and period

Each organization uses a separate H2 database file. Switching organization switches the active database and rebuilds database-bound services. Organization identity comes from the selected database.

The global period control represents the active accounting period. The top chrome selects a period, not an arbitrary day. The period start date is calculated from the selected year/month and the configured period start day in Settings. Changing it requires an explicit Set Active Period action. Report date ranges remain local to report panels.

## Dashboard data

All values come from the active database. The dashboard includes book cash, reconciled cash, unreconciled difference, significant bank accounts, year-to-date surplus or deficit, fund-classification breakdowns, budget exceptions, pending work, recent entered transactions, reconciliation status, and quick actions.

## Out of scope

Transaction approvals, a separate posting workflow, formal oversight roles, attachments, persistent import staging, and a void workflow are out of scope.
