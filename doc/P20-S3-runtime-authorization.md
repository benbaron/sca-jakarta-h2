# P20-S3 — Runtime authorization enforcement

## Purpose

P20-S3 applies the owner-approved P20 reserved-role policy to the production JavaFX shell and authoritative mutation services. Authentication and effective role calculation remain owned by the P20-S2 `AppUser` / `UserCompanyRole` / `AuthenticatedUserSession` path. This slice adds no second identity store, role store, permission table, or approval workflow.

Runtime authorization is fail-closed: a production mutation requires an authenticated session and the fixed permission assigned to that session's effective reserved roles for the active company. UI disabling is explanatory only; the authoritative write service must reject a denied mutation even when called directly.

## Fixed permissions

P20-S3 uses a small application-owned permission vocabulary rather than editable permission rows:

| Permission | Meaning |
|---|---|
| `BOOKKEEPING_WRITE` | Create or mutate accounting/operational bookkeeping facts. |
| `COMPANY_ADMIN` | Maintain non-security company configuration and durable company master data. |
| `SECURITY_ADMIN` | Maintain users, roles, assignments, credentials, and security configuration. |
| `DATABASE_ADMIN` | Perform post-login database administration, diagnostic ownership repair, and sample-data administration. |
| `EXPORT` | Produce non-mutating reports or exports from data the account may read. |
| `UI_PREFERENCE_WRITE` | Persist presentation/layout preferences that do not mutate business/accounting facts. |

Custom/non-reserved roles confer no runtime permission in P20. There is no editable permission-assignment table.

## Reserved-role matrix

Permissions are the union of the authenticated account's active reserved roles for the active company.

| Role | Bookkeeping write | Company admin | Security admin | Database admin | Export | UI preference write |
|---|---:|---:|---:|---:|---:|---:|
| ADMIN | yes | yes | yes | yes | yes | yes |
| MANAGER | yes | yes | no | no | yes | yes |
| ACCOUNTANT | yes | no | no | no | yes | yes |
| VIEWER | no | no | no | no | yes | yes |

The singleton ADMIN rule from P20-S1/P20-S2 remains authoritative. No role hierarchy is inferred from mutable role labels; the table above is fixed application policy.

## Production operation classification

### BOOKKEEPING_WRITE

The following authoritative mutations require `BOOKKEEPING_WRITE`:

- Journal entry create/update and transaction correction/delete/reversal;
- Chart of Accounts, Funds, budget categories, budget plans/versions;
- bank statement import/review/mapping and reviewed-row ledger acceptance;
- reconciliation matching, cleared-state changes, explanations, successor creation, and finalization;
- fixed-asset/depreciation and inventory lifecycle/accounting operations;
- period close/reopen/adjustment operations;
- accepted Chart CSV/JSON and SCLX imports and other import commits that mutate authoritative bookkeeping data.

Preview, search, drill-through, validation, and other non-mutating review operations do not require this write permission.

### COMPANY_ADMIN

The following require `COMPANY_ADMIN`:

- company profile/master-data maintenance;
- active Chart of Accounts selection and other non-security company defaults;
- durable bank/configured-bank-account setup.

Operational statement import/review/reconciliation remains bookkeeping work and therefore uses `BOOKKEEPING_WRITE` rather than `COMPANY_ADMIN`.

Company reporting defaults stored in `company_ui_state` under the `reportingDefaults.` prefix are company-level configuration and therefore require `COMPANY_ADMIN`, even though they share the existing UI-state store. Generic state writes must not provide a lower-privilege bypass for that prefix. Presentation-only company UI preferences and layout/workspace state outside `reportingDefaults.` remain `UI_PREFERENCE_WRITE`, so VIEWER retains the ability to persist table, divider, date/money-display, and similar presentation state.

### SECURITY_ADMIN

The following require `SECURITY_ADMIN` and therefore effective singleton ADMIN authority:

- user maintenance;
- role maintenance;
- company user-role assignment maintenance;
- password set/replace/clear;
- inactivity-timeout/security setting changes;
- post-login reserved-account/bootstrap conflict adoption.

The persistence-level singleton ADMIN and required ADMIN assignment protections remain in force in addition to this runtime permission.

### DATABASE_ADMIN

The following post-login operations require `DATABASE_ADMIN`:

- whole-database backup/restore/validated-copy activation;
- company ownership diagnostic repair;
- explicit sample-company create/refresh administration.

Database selection/create/retry on the outer login gate is a deliberate exception. An operator must be able to choose or create the local H2 database before any database-owned account can authenticate. That pre-login local-file bootstrap boundary does not grant access to protected bookkeeping data.

### EXPORT

Reports and non-mutating exports require `EXPORT`. VIEWER therefore retains report/export access. An export path must not mutate accounting/business state as a side effect.

### UI_PREFERENCE_WRITE

All authenticated roles may persist presentation-only UI preferences such as table order/width/sort and company display/divider state. These values affect presentation rather than durable business/accounting authority, so allowing VIEWER to persist them does not violate the VIEWER read-only business-data contract.

## Enforcement architecture

`AuthorizationPolicy` is the single fixed mapping from effective `ReservedSecurityRole` values to `ApplicationPermission` values.

`AuthorizationGuard` is the single runtime enforcement helper. It receives:

- the current H2 `Jpa` authority; and
- a supplier of the current `AuthenticatedUserSession` from the existing `UiSessionState`.

It stores no independent session, role, or permission state. Production service wiring must inject/use this guard for mutation entry points. A denied operation records `AUTHORIZATION_DENIED` in the existing H2 `security_event` authority and then fails without changing the requested business state.

JavaFX controls use the same fixed policy to disable mutation commands and provide a concise explanation. `UiPermissionGate` is presentation-only and reads the current shared authenticated session; UI state is never the security boundary. Tests still call guarded services directly under lower-privilege sessions and prove the mutation is rejected. Governing UI detail: `doc/P20-S3-javafx-permission-gating.md`.

### Production current-session composition

`UiServiceRegistry` is the one production composition point for P20-S3 guarded services. Each active `ServiceBundle` owns one `AuthorizationGuard` constructed from:

- that bundle's current `Jpa`; and
- `ApplicationSessionContext.sharedSessionState()::authenticatedUser`.

The bundle guard is supplied to the guarded constructors for Account, Fund, Budget Category, Budget Plan, Bank Configuration, Fixed Asset, Inventory, Company Administration, User Administration, Journal transaction entry/correction, Reconciliation, and Period Close. On-demand production constructors for CoA CSV commit, Chart of Accounts JSON commit, SCLX commit, strict bank-statement review, mapped CSV review, bank CSV mapping profiles, normalized CSV review, reviewed-statement acceptance, Security Administration, and company UI preference/state writes reuse the same current bundle guard.

Mapped CSV retains one authorization owner: its public guarded composition constructs the delegated `BankStatementReviewService` with the current guard, and `BankCsvReviewService.commit(...)` continues to delegate the durable review write instead of adding another independent authorization check.

Source-compatible unguarded constructors remain available for tests and documented caller-owned transaction/import seams. Their existence is not a production bypass because production composition deliberately chooses the guarded constructors. Nested SCLX/CoA helpers continue to rely on the already-guarded outer atomic commit boundary rather than performing repeated inner authorization checks.

Database switching prepares a new `ServiceBundle` around the target `Jpa` before activation. Activating the prepared bundle swaps the entire service/guard authority, and the existing database-session controller clears the authenticated session on a database change. A guard therefore cannot retain the old database's `Jpa` or a cached permission snapshot. Company switching continues to use `AuthenticationService.rebind(...)`; because the guard calls the shared session supplier on each decision, the newly rebound company/roles take effect immediately.

Post-login whole-database transfer uses `DatabaseAdministrationService` as a service-layer authorization facade over the existing persistence `DatabaseTransferService`. Because transfer actions survive database switches, the facade resolves the current `UiServiceRegistry` bundle guard on every operation rather than retaining the guard from workspace construction. `CompanyOwnershipService.assignOwner(...)` and production `SampleCompanyService.createOrRefresh()` likewise enforce `DATABASE_ADMIN`; their read/query or compatibility seams remain unchanged.

## Company and role switching

Changing company preserves the authenticated `AppUser` and uses the P20-S2 `AuthenticationService.rebind(...)` path to recompute effective roles. Authorization decisions must consume the current session each time rather than cache permissions independently. Therefore the same user may have write authority in one company and VIEWER-only authority in another, and the change takes effect immediately after the session rebind.

## Authoritative audit actor

The authenticated `AppUser.username` is the authoritative actor for protected writes and security-denial events. Guarded production services derive that username from the same current-session `AuthorizationGuard` used for permission enforcement on every mutation.

Existing command DTO/method actor strings remain only as source-compatible inputs for unguarded tests and explicitly caller-owned seams. A guarded production service overrides those values with `AuthorizationGuard.requireActor(...)`; JavaFX actor fields for protected operations display the authenticated username and are non-editable.

Literal actors such as `"ui"`, `"ui-operator"`, or workstation usernames are not alternate authority for protected writes. `DesktopActorIdentity` prefers the authenticated session and its workstation fallback is compatibility/pre-login display only. Imported historical SCLX actor facts remain source history and are not rewritten as current-operation identity. Governing detail: `doc/P20-S3-authenticated-audit-actor.md`.
Company Ownership Diagnostics follows the same rule once its `DATABASE_ADMIN` mutation is guarded: the repair audit actor is the authenticated ADMIN username and the displayed actor field is read-only.

## UI command behavior

The production shell now enforces this presentation contract through `AppPanel.requiredPermission(...)`, `PanelHost.activeRequiredPermission(...)`, and `UiPermissionGate`. Global New/Save/Validate availability is the conjunction of:

1. the active panel declaring a genuine command capability; and
2. the current authenticated session holding the permission required by that command.

Panel-local mutation controls follow the same rule. Unbound controls use `UiPermissionGate.gate(...)`; controls already bound to busy/selection/lifecycle state compose their existing disable binding with `UiPermissionGate.deniedProperty(...)`. A denied control is disabled or unavailable with concise explanatory text; it must not appear enabled and then silently do nothing.

Read/navigation/preview controls remain available to any authenticated account with effective access to the active company. Export actions require `EXPORT`; company presentation preferences require `UI_PREFERENCE_WRITE`. Session/company-role changes refresh the permission presentation immediately without a second role cache.

## Test requirements

P20-S3 is not complete without tests covering:

- every reserved-role row in the permission matrix;
- union behavior for multiple non-ADMIN roles;
- absent-session and wrong-company rejection;
- role-state changes and ACCOUNTANT/VIEWER permission switching without stale permission caches;
- direct service calls proving VIEWER cannot write even if UI gating is bypassed;
- MANAGER/ACCOUNTANT/ADMIN positive and negative boundaries;
- singleton ADMIN/security-admin restrictions;
- authenticated audit actor replacing spoofable free-form actor text;
- `AUTHORIZATION_DENIED` security events;
- production service wiring using the guard for every protected mutation route;
- shell and local control availability following the same permission policy;
- non-mutating VIEWER report/export access and presentation preference persistence.

## Non-goals

P20-S3 does not introduce:

- approval queues or posting approval;
- formal oversight workflow;
- custom editable permission definitions;
- a second user/role repository;
- remote authentication, SSO, MFA, or network security;
- a second accounting/persistence path.
