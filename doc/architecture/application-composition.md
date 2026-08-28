# Application composition

P01-S1 establishes `ProductionWorkspaceWindow` as the production shell owner. The launched JavaFX application constructs one workspace shell containing menu, toolbar, navigation, reusable center tabs, inspector, dividers, and status bar.

P17-C10 retires the obsolete `ReferenceWorkspaceWindow` compatibility subclass after source and test inspection confirmed that production startup and composition already use `ProductionWorkspaceWindow`. Current shell behavior and shell-focused tests target `ProductionWorkspaceWindow` directly; no alternate reference-chrome window remains as a competing implementation.

P17-C11 removes the remaining legacy `MainWindow` shell implementation. `ApplicationSessionContext` now owns the application-wide `UiSessionState`; `MainWindow` is reduced to a deprecated, non-JavaFX compatibility facade that exposes only that session state for older panel helpers. It no longer owns window chrome, panel routing, Find/command-palette behavior, date-range controls, authentication UI, or view presets. New production code must use `ApplicationSessionContext` or the workspace-owned context/services rather than adding behavior back to the facade.

The obsolete `DateRangeSelector` and `DateRangeUtil` belonged only to the removed shell and are deleted. `DateRange` and `DateRangeContext` remain because Report Library deliberately accepts an explicit initial report range; they are not a replacement for the shell-selected accounting-period authority.

Global shell actions use typed `AppCommand` values. `GlobalCommandRegistry` is the production source for installed labels, accelerators, and Help shortcut text. The shell and `PanelHost` route commands by enum identity instead of discovering behavior from button text.

P16-S12 makes support explicit: each `AppPanel` publishes its current `commandCapabilities()` and returns a handled/not-handled result from `executeCommand`. The production File menu and toolbar enable New and Save only when the active panel declares them. Composite Administration delegates both the query and execution to its selected inner tab and notifies the shell when that selection changes. Undeclared commands cannot fall through to empty New/Save/Copy/Paste hooks. Copy and Paste remain native focused-text-control behavior; production does not capture `Ctrl+C` or `Ctrl+V`. Unimplemented production Find and command-palette shortcuts are neither installed nor listed in Help.

User-facing global navigation uses factual audit-history terminology. Approval/rejection workflows are not introduced by the production shell.

The production Workspace menu exposes a user-facing `Close All Tabs` command with `Ctrl+Shift+W`. It closes every non-Dashboard tab, keeps the permanent Dashboard tab open, and prompts before discarding any tab-reported unsaved edits.

## Workspace composition root

P01-S2 introduces explicit shell-owned composition objects:

- `WorkspaceContext` is the observable runtime context for active database path, company code, active period date, and database availability/failure state.
- `WorkspaceServices` owns the context, database session controller, dashboard query boundary, and panel factory for one workspace lifecycle.
- `WorkspaceServicesFactory` constructs those objects from the current `UiSessionState`, state store, and database connector, and keeps context state synchronized with session/database/period changes.
- `PanelFactory` is the one panel construction boundary used by `PanelHost`; production `PanelHost` instances no longer own a static panel factory map.

Existing panels may still call the deprecated `MainWindow.sharedSessionState()` compatibility facade internally until their owning feature phases replace those static lookups with constructor-injected command/query services. The facade delegates to `ApplicationSessionContext` and contains no UI behavior. New production shell code should receive panels through `PanelFactory` rather than constructing panels directly.

## Residual compatibility services

P17-C11 classifies, but does not destructively migrate, the remaining pre-workspace run APIs:

- `ReconciliationService` plus `ReconciliationRunRepository` / `JdbcReconciliationRunRepository` remain required compatibility authorities. Current reconciliation comparison and SCLX import paths still consume them, while the routed reconciliation workspace uses `BankReconciliationWorkspaceService`.
- `PeriodCloseService` plus `PeriodCloseRunRepository` / `JdbcPeriodCloseRunRepository` remain required compatibility authorities. SCLX snapshot/history compatibility still consumes that service, while the routed Period Close workspace uses `PeriodCloseRangeService` for current range policy.
- `ScheduleEligibilityService` has no routed Schedules workspace consumer after P07 elimination. It remains a registry-exposed compatibility query over retained schedule metadata; it is not a production navigation or accounting authority. Removing that public/schema-facing compatibility surface is not required for shell retirement and would require a separate deliberate compatibility/persistence decision.

Accordingly, no historical H2 run tables are removed and no applied migration is edited or dropped in P17-C11.

## Atomic database switching

P01-S3 makes database selection a candidate-swap operation. The selected
database path is not persisted to session state until the candidate database
has migrated and the replacement service bundle has been constructed
successfully. If candidate construction fails, the prior selection remains the
active database and the recovery dashboard is shown with the failure details.

`UiServiceRegistry.reconnectToDatabase` builds the replacement JPA-backed
service bundle before assigning it as the active bundle. The previous JPA
resources stay open until the replacement is ready, then they are closed after
the swap. If candidate service construction fails after opening a candidate JPA
resource, the candidate resource is closed before the failure is reported.

After a successful swap, `PanelHost.refreshOpenPanels` recreates every open
workspace panel through the lifecycle-owned `PanelFactory` while preserving the
open destinations and active tab. This prevents database-bound panels from
retaining stale service references after an organization/database switch. The
recovery dashboard remains a replacement dashboard panel and is constructed
without requiring accounting query services from the failed candidate database.
