# NonprofitBookkeeping Compatibility Matrix (Phase 1)

Legend:
- **KEEP**: imported API/class kept as-is (or preserved at constructor/method signature boundary).
- **WRAP**: local adapter/bridge required.
- **DEFER**: intentionally postponed to avoid destabilizing runtime in this slice.

| Imported package/class | Target integration point | Decision | Reasoning | Current status |
|---|---|---|---|---|
| `nonprofitbookkeeping.ui.panels.DashboardPanelFX` | `org.nonprofitbookkeeping.ui.DashboardPanel` | KEEP + WRAP | Dashboard vertical slice prioritized by roadmap; mounted as a subpanel inside the shell-consistent dashboard container. Wrapped via local bridge for data loading and app service interop. | Implemented |
| `nonprofitbookkeeping.ui.panels` (dashboard namespace) | JavaFX shell / `PanelHost` | KEEP | Namespace isolated from `org.nonprofitbookkeeping.*` to avoid model/service name collisions. | Implemented |
| `nonprofitbookkeeping.service.*` (full package) | Existing `org.nonprofitbookkeeping.service.*` | DEFER | Significant overlap/collision with current services and persistence assumptions; import as whole would require broad runtime and DI changes. | Deferred |
| `nonprofitbookkeeping.model.*` (full package) | Existing `org.nonprofitbookkeeping.model.*` | DEFER | Direct class name collisions (`Account`, `Fund`, `ChartOfAccounts`) with potentially different semantics. Requires explicit mapping plan. | Deferred |
| `nonprofitbookkeeping.ui.panels.skeletons.SkeletonDashboardPanel` | Alternate dashboard implementation | DEFER | Depends on `CurrentCompany` lifecycle and legacy summary/static model assumptions not aligned with current shell state model. | Deferred |
| `nonprofitbookkeeping.ui.MainApplicationView` / `NonprofitBookkeepingFX` | Current `MainWindow` / JavaFX launcher | DEFER | Would replace shell and navigation model; out of scope for API-preserving incremental migration. | Deferred |
| `nonprofitbookkeeping.reports.*` | Report panels (`ReportLibraryPanel`) | DEFER | Heavy jasper/report runtime coupling; roadmap places these after foundational panel/service bridges. | Deferred |
| `nonprofitbookkeeping.ui.actions.*` | Menu/toolbar action model | DEFER | Action framework is coupled to imported app context and file workflows; requires action adapter layer first. | Deferred |
| `org.nonprofitbookkeeping.bridge.dashboard.DashboardDataBridge` (local) | Imported dashboard data wiring | WRAP | Provides API-preserving adapter boundary so imported panel uses existing app services. | Implemented |

## Collision handling
- **Namespace isolation applied**:
  - Imported code remains under `nonprofitbookkeeping.*`.
  - Existing app remains under `org.nonprofitbookkeeping.*`.
- **Mapping adapter applied**:
  - `DashboardDataBridge` maps local service outputs into imported panel presentation rows without changing public service APIs.

## KEEP / WRAP / DEFER rationale summary
- **KEEP** where Dashboard UI integration can proceed without forcing core model replacement.
- **WRAP** where runtime dependencies differ (service locator/persistence and async behavior).
- **DEFER** where package-wide import would break shell/navigation or produce unresolved API/runtime conflicts.


## Base-codebase decision
- **Decision**: Keep `sca-jakarta-h2` as the host/base codebase.
- **Reason**: project goals prioritize preserving this app's data model and shell/navigation consistency; importing upstream features via adapters is lower risk than re-basing onto upstream app lifecycle.
- **Consequence**: continue vertical slice integration (subpanel mounting + bridge adapters), rather than wholesale upstream app adoption.
