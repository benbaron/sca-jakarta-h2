# NonprofitBookkeeping Archive Inventory (Phase 0)

## Inputs surveyed
- `nonprofitbookkeepingsource.zip` (source archive present in repo root; used as code source input for this pass).
- `nonprofitbookkeeping-javadoc.zip` (API/Javadoc archive).

## Source archive high-level inventory
- Total Java source files under `src/main/java`: **329**.
- Dominant package groups:
  - `nonprofitbookkeeping.reports.jasper.beans` (42)
  - `nonprofitbookkeeping.reports.jasper.generator` (37)
  - `nonprofitbookkeeping.model` (31)
  - `nonprofitbookkeeping.ui.panels` (30)
  - `nonprofitbookkeeping.service` (25)
  - `nonprofitbookkeeping.ui.actions` (22)
  - `nonprofitbookkeeping.model.ofx` (20)
  - `nonprofitbookkeeping.ui.actions.scaledger` (20)

## Javadoc archive high-level inventory
- HTML files detected: **1490**.
- Dashboard artifacts are present in Javadocs (`DashboardPanelFX` docs detected), confirming UI API surface is documented.

## Vertical slice candidate mapping (Dashboard first)
Imported dashboard-related classes identified:
- `nonprofitbookkeeping.ui.panels.DashboardPanelFX`
- `nonprofitbookkeeping.ui.panels.skeletons.SkeletonDashboardPanel`
- Dashboard dependencies observed in source:
  - model: `AccountingTransaction`, `CurrentCompany`, `Company`, `Ledger`, `AccountingEntry`
  - utility: `FormatUtils`

## Phase 0 import strategy used in this pass
To preserve API while minimizing runtime dependency spread:
1. Imported dashboard package namespace was introduced in-project as `nonprofitbookkeeping.ui.panels`.
2. `DashboardPanelFX` API was preserved as a JavaFX `BorderPane` with constructor entrypoint.
3. A local bridge (`org.nonprofitbookkeeping.bridge.dashboard`) was added to source dashboard data from existing app services without modifying local app core service signatures.

## Dependency graph summary (Dashboard slice)
- `nonprofitbookkeeping.ui.panels.DashboardPanelFX`
  -> `org.nonprofitbookkeeping.bridge.dashboard.DashboardDataBridge`
  -> `org.nonprofitbookkeeping.ui.UiServiceRegistry`
  -> `FundBalanceService`, `FundLookupService`, `AccountLookupService`
  -> JPA persistence stack (`Jpa`)

## Notes
- The roadmap references `nonprofitbookkeeping.zip`; repository root currently contains `nonprofitbookkeepingsource.zip` instead, which was used as the source-equivalent archive for this implementation pass.
