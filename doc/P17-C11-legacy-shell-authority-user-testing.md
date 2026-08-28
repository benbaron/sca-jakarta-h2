# P17-C11 — Legacy shell authority user testing

## User-visible changes

P17-C11 does not add a new workflow. It removes the unreachable legacy `MainWindow` shell implementation and its obsolete Find/command-palette/date-range UI while preserving `ProductionWorkspaceWindow` as the only production shell. Company/database session behavior, current global commands, active-period selection, reporting, reconciliation, and period-close behavior must remain unchanged.

## Manual verification

1. Launch the application normally. Confirm the standard production workspace opens with menu, toolbar, navigation, center tabs, inspector, status bar, and active-period control.
2. Open Dashboard, Journal, Banking, Reconciliation Runs, Period Close Runs, Report Library, and Administration. Confirm each opens normally and no alternate/legacy shell window appears.
3. Switch the active company using the production company selector. Confirm the workspace refreshes to that company and no stale company data remains visible.
4. If a second database is available, exercise the normal database-selection/switch workflow and confirm the active database and company update atomically as before.
5. In Report Library, set an explicit custom date range and run a report. Confirm the report uses the chosen dates; changing the shell accounting period afterward must not overwrite the explicit report range.
6. Confirm the production shell does not expose the retired legacy Search menu, `Ctrl+F` Find command, `Ctrl+K` command palette, `Ctrl+G` Go To command, or the old generic Date Range selector from `MainWindow`.
7. Confirm current production commands such as New/Save where supported, Close All Tabs, Journal navigation/drill-through, and the production search field continue to work.
8. Open Reconciliation Runs and Period Close Runs and confirm historical run data remains readable. No historical reconciliation or period-close records should disappear as a result of this slice.

## Acceptance

Accept P17-C11 when the production shell and session-switching behavior are unchanged, the legacy shell controls are absent, explicit Report Library ranges still work, and existing reconciliation/period-close history remains available.
