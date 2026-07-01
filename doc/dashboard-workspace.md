# Dashboard Workspace

## Purpose

The production dashboard is a read-only operational overview for an SCA nonprofit bookkeeping database. Its visual specification is the approved dashboard reference drawing. Numbered callouts and explanatory annotations in that drawing are not application controls and are intentionally omitted.

## Architecture

The dashboard is separated into three layers:

- `DashboardWorkspacePanel` builds JavaFX controls and binds a completed projection.
- `DashboardQueryService` defines the read-only query boundary.
- `JpaDashboardQueryService` obtains authoritative values from H2 inside one explicit read transaction.

The JavaFX panel contains no SQL. Accounting derivation remains in the service layer rather than in cell factories or click handlers.

`DashboardExperiment` remains only as a compatibility entry point and delegates to `DashboardWorkspacePanel`. The earlier experiment-derived production layout and its separate layout policy were removed.

## Visual structure

The workspace contains:

1. Cash Balances.
2. Year-to-date surplus or deficit with a monthly sparkline.
3. Budget Performance donut chart.
4. Open Items with counts and monetary totals.
5. Recent Transactions.
6. Bank Reconciliation Status.
7. Budget vs Actual.
8. Quick Links.

The left navigation uses vector icons and a blue selected state. The right inspector uses Organization, Period Information, Balances, and Notes cards. Green, amber, red, blue, and neutral gray cues communicate status without relying on color alone; text labels and icons remain present.

All icons are dependency-free JavaFX `SVGPath` graphics created by `UiIcons`. No external font or image resource is required.

## Responsive behavior

`DashboardWorkspaceLayoutPolicy` selects one of three layouts based on the center viewport width:

- Wide: four KPI columns, a full-width transaction table, and three lower sections.
- Medium: two KPI columns with lower sections arranged in pairs.
- Narrow: one vertical card stack.

The dashboard sits in a fit-to-width `ScrollPane`. The transaction table keeps horizontal scrolling available because the reference requires ten operational columns. The navigation and inspector remain resizable through visible `SplitPane` dividers and do not overlay center content.

The headless geometry assessment models the center viewport after sidebar and divider widths are applied. It also evaluates KPI-card minimum widths, transaction-table minimum and preferred widths, and whether horizontal or vertical scrolling is required.

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

The schema currently stores budget categories and actual activity but does not provide an authoritative budget-target amount for this projection. The dashboard therefore leaves unavailable Budget, Variance, and Budget Performance results blank or neutral rather than inventing values.

## Data and safety rules

- All money uses `BigDecimal`.
- Posted balances use stable database IDs and posted transaction status.
- The dashboard never inserts, updates, deletes, posts, reverses, or migrates accounting records.
- Database startup and recovery behavior remains in the production workspace shell.
- A failed database selection still leads to the recovery dashboard, where a database can be retried, selected, or created.

## Validation

Material changes require:

- repository tests for dashboard projections and accounting derivations;
- responsive layout and full geometry-policy tests;
- `mvn clean verify` through GitHub Actions;
- a desktop JavaFX visual check at wide, medium, and narrow sizes, including 100%, 125%, and 150% display scaling.
