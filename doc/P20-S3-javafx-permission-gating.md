# P20-S3 — JavaFX permission gating

## Purpose

This tranche applies the fixed P20-S3 `ApplicationPermission` policy to production JavaFX command availability. It is presentation behavior only: guarded application services remain the authoritative security boundary and continue to reject direct unauthorized calls.

No permission rows, second session cache, schema change, migration, or approval workflow is introduced.

## Current-session authority

`UiPermissionGate` reads `ApplicationSessionContext.sharedSessionState()` and evaluates the established `AuthorizationPolicy`. It stores only JavaFX observable denied-state wrappers so controls can react to authentication/company-role changes; it does not persist or independently derive roles.

The shared session's authentication-change notification refreshes every permission state. A company rebind or login/logout therefore changes JavaFX availability immediately without restarting the application.

## Global commands

`AppPanel.requiredPermission(AppCommand)` is the panel contract for a supported global command. `PanelHost` exposes the selected panel's requirement, and `ProductionWorkspaceWindow` enables a global command only when both are true:

1. the active panel genuinely supports the command; and
2. the current session has the declared permission.

A direct shell command invocation is checked again before panel dispatch and returns a concise permission explanation when denied.

Current global mutation mappings are:

- Journal New/Save — `BOOKKEEPING_WRITE`;
- Banking configuration New/Save — `COMPANY_ADMIN`;
- Budget, Fixed Asset, Inventory, Funds, and Chart of Accounts mutation commands — `BOOKKEEPING_WRITE`;
- Company Administration New/Save — `COMPANY_ADMIN`;
- User Administration New/Save — `SECURITY_ADMIN`;
- presentation Settings Save — `UI_PREFERENCE_WRITE`.

Non-mutating validation/navigation commands do not acquire a write permission merely because they are global commands.

## Panel-local actions

Durable local actions use the same fixed policy as their guarded service owners. Representative mappings include:

- Journal, Budget, Funds, Chart of Accounts, fixed-asset/depreciation, Inventory, Reconciliation, Period Close, accepted imports (including Chart of Accounts JSON), and reviewed-bank-row transaction creation — `BOOKKEEPING_WRITE`;
- Bank Configuration and Company Administration durable maintenance — `COMPANY_ADMIN`;
- User and Security Administration mutations — `SECURITY_ADMIN`;
- database backup/restore/validated-copy activation, ownership repair, and sample-company administration — `DATABASE_ADMIN`;
- bank transaction exports — `EXPORT`;
- presentation preference Apply/Save — `UI_PREFERENCE_WRITE`.

Read/search/drill-through/preview controls remain available when their underlying operation is non-mutating. The outer pre-login database selection/create/retry path remains outside `DATABASE_ADMIN`, matching the governing database-administration design.

For an unbound local control, `UiPermissionGate.gate(...)` overlays permission denial while preserving the panel's own local disabled state. For a control whose disabled property is already bound to busy/selection/lifecycle state, the existing binding is composed with `UiPermissionGate.deniedProperty(...)`. Permission gating must never erase an independent local reason that a control is unavailable.

Denied unbound controls receive a concise tooltip naming the required permission. Global menu/toolbar commands receive the same explanation through the shell command-state path.

## Service-boundary prerequisite correction

The panel audit found that `ChartOfAccountsJsonImportService.commit(...)` was a durable production write path not yet guarded by P20-S3. This tranche corrects that narrow prerequisite defect by adding a source-compatible guarded constructor, requiring `BOOKKEEPING_WRITE` before ordinary commit validation, and routing production construction through the current `UiServiceRegistry` guard. Existing unguarded constructors remain compatibility/test seams. Chart JSON export is non-mutating.

## Security boundary

Disabling a JavaFX control is not authorization. Every durable mutation remains guarded at the service boundary under the P20-S3 service tranches. Tests therefore cover both presentation gating and direct lower-privilege service denial.

## Regression requirements

Coverage must prove:

- VIEWER keeps `EXPORT` and `UI_PREFERENCE_WRITE` but does not gain bookkeeping, company, security, or database administration writes;
- MANAGER gains `BOOKKEEPING_WRITE` and `COMPANY_ADMIN` but not `SECURITY_ADMIN` or `DATABASE_ADMIN`;
- role/session changes refresh controls without stale permission state;
- a panel's independent local disabled state survives permission changes;
- global New/Save uses panel-declared permission requirements;
- representative local durable actions are wired to their governing permission;
- existing ADMIN-oriented JavaFX tests establish an authenticated ADMIN session rather than relying on an absent-session bypass.
