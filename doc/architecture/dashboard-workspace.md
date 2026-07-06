# Dashboard Workspace

## Purpose

The production dashboard is a read-only operational overview for an SCA nonprofit bookkeeping database. Its visual specification is the approved dashboard reference drawing. Numbered callouts and explanatory annotations in that drawing are not application controls and are intentionally omitted.

## Architecture

The dashboard is separated into three layers:

- `DashboardHomePanel` builds the active JavaFX dashboard and binds a completed projection.
- `DashboardQueryService` defines the read-only query boundary.
- `JpaDashboardQueryService` obtains authoritative values from H2 inside one explicit read transaction.

The JavaFX panel contains no SQL. Accounting derivation remains in the service layer rather than in cell factories or click handlers.

`DashboardExperiment` remains only as a compatibility entry point. The standalone module under `experiments/dashboard-ui` is a visual and sizing reference, not the production data path.

## Visual structure

The workspace contains:

1. Cash Balances.
2. Year-to-date surplus or deficit with a monthly trend.
3. Budget Performance.
4. Open Items with counts and monetary totals.
5. Recent Transactions.
6. Bank Reconciliation Status.
7. Budget vs Actual.
8. Quick Links.

The left navigation uses vector icons and a blue selected state. The right inspector uses Organization, Period Information, Balances, and Notes cards. Green, amber, red, blue, and neutral gray cues communicate status without relying on color alone; text labels and icons remain present.

All production icons are dependency-free JavaFX `SVGPath` graphics created by `UiIcons`. No external font or image resource is required.

## Workspace chrome

The application chrome follows the compact white-and-blue reference design:

- Segoe UI is requested on Windows, with the JavaFX platform fallback used elsewhere.
- Menus and toolbars use white surfaces, subtle gray separators, and flat controls.
- The navigation pane, center tab workspace, and inspector are independent `SplitPane` children.
- The center workspace has a zero minimum width so a panel cannot force the inspector outside the window.
- Initial dividers use compact pixel-oriented sidebar targets rather than fixed 20/80 percentages.
- Dividers remain visible and draggable after startup. Safe user-adjusted positions are remembered in the existing app-state file; unsafe remembered positions that would clip sidebars or crowd the center fall back to responsive defaults.
- The native operating-system title bar retains the user's Windows accent color.

`WorkspaceWindowSizingPolicy` limits startup to a laptop-friendly size inside the primary screen's visual bounds. A normal desktop opens at no more than 1180 by 760 logical pixels; smaller screens use 90 percent of their usable width and height. The window is centered and its minimum dimensions are also capped to the available screen.

## Responsive behavior

`DashboardWorkspaceLayoutPolicy` selects one of three layouts based on the center viewport width:

- Wide: four KPI columns, a full-width transaction table, and three lower sections.
- Medium: two KPI columns with lower sections arranged in pairs.
- Narrow: one vertical card stack.

The dashboard sits in a fit-to-width `ScrollPane`. The transaction table keeps horizontal scrolling available when its columns need more width. The navigation and inspector remain resizable through visible `SplitPane` dividers and do not overlay center content.

The headless geometry assessments model the center viewport after sidebar and divider widths are applied. They also evaluate startup window bounds, sidebar allocation, KPI-card minimum widths, transaction-table minimum and preferred widths, and whether horizontal or vertical scrolling is required.

## Derived transaction columns

### Balance

For the displayed transaction window, the service derives an aggregate running balance across posted bank-type accounts. Ordering is stable by transaction date and transaction database ID. The service first calculates the posted bank balance immediately before the oldest displayed transaction, then applies each displayed transaction's posted bank delta in chronological order.

When no bank account exists, the balance cannot be established reliably, or the displayed transaction is not posted, the cell is blank.

### Affects Bank

The indicator is shown only when the transaction is posted and at least one line references an account whose type is `BANK`. Otherwise the cell is blank.

### Affects Budget

The indicator is shown only when the transaction is posted and at least one income or expense line references a budget category. Otherwise the cell is blank.

Reversed and other non-posted history remains visible but does not claim current balance, bank, or budget effects.

## Fund-class balances

The inspector's unrestricted, restricted, and designated values represent net assets by fund classification. The query includes posted equity, income, and expense lines and converts their debit-positive storage signs into net-asset effects. It does not sum every line in a balanced transaction, which would incorrectly collapse ordinary same-fund activity to zero.

## Budget values

P04 supplies authoritative budget-target amounts through active `budget_plan` and `budget_line` rows. Dashboard Budget Performance and YTD Budget vs Actual projections read the selected active normalized budget version and canonical ledger actuals through `DashboardQueryService`; when no active budget version exists, the dashboard shows the documented neutral no-budget state rather than inventing values.

## Data and safety rules

- All money uses `BigDecimal`.
- Posted balances use stable database IDs and posted transaction status.
- The dashboard never inserts, updates, deletes, posts, reverses, or migrates accounting records.
- Database startup and recovery behavior remains in the production workspace shell.
- A failed database selection still leads to the recovery dashboard, where a database can be retried, selected, or created.

## Validation

Material changes require:

- repository tests for dashboard projections and accounting derivations;
- responsive layout, shell allocation, and startup-window geometry tests;
- `mvn clean verify` through GitHub Actions;
- a desktop JavaFX visual check at wide, medium, and narrow sizes, including 100%, 125%, and 150% display scaling.
