# User, role, and assignment maintenance

## Authority and security boundary

`app_user`, `app_role`, and `user_company_role` are the durable administration authority. They record application-user identities, global role definitions, and company-specific assignment history.

P20-S1 defines the approved authentication/authorization contract in `doc/P20-S1-authentication-authorization-boundary.md`. P20-S2 implemented local H2 authentication, credentials, reserved accounts/roles, company-scoped effective roles, and authenticated session identity. P20-S3 applies the fixed runtime permission policy from `doc/P20-S3-runtime-authorization.md` to authoritative mutation boundaries.

`UserAdminService` mutation entry points require `SECURITY_ADMIN` for the active company. Under the fixed P20 policy only effective singleton ADMIN authority grants that permission; MANAGER, ACCOUNTANT, VIEWER, and any union of non-ADMIN reserved roles do not. User/role/assignment queries remain non-mutating and do not require `SECURITY_ADMIN` merely to inspect current facts.

Roles never own passwords. Credentials belong only to `AppUser` accounts. The reserved ADMIN, MANAGER, ACCOUNTANT, and VIEWER accounts begin passwordless unless an ADMIN has deliberately configured a credential. Password set/replace/clear and inactivity-timeout changes remain owned by `SecurityAdminService`, not `UserAdminService`. Those `SecurityAdminService` mutation entry points now require `SECURITY_ADMIN` in the requested company context before password validation or service-owned transaction work begins. The existing persistence-backed singleton-ADMIN/effective-ADMIN check remains in force after authorization succeeds, so injecting or fabricating a nominal ADMIN permission cannot replace the reserved ADMIN identity invariant. `passwordConfigured(...)` and `settings()` remain non-mutating reads.

Authenticated session identity is now available, but the existing User Admin command DTO actor strings remain a temporary compatibility input until the P20-S3 authenticated-audit-actor tranche replaces them as authoritative audit identity. They must not survive as a parallel actor authority when P20-S3 is complete.

## Users and roles

Users and roles are edited by stable database ID. Changing a username or role code updates the selected row and does not create a replacement record. Usernames and role codes remain global, case-insensitively unique business labels.

Users and roles are retained rather than hard-deleted. An active user or role may be deactivated only after every active assignment referencing it has been ended or revoked. Historical assignments continue to reference inactive users and roles. No Delete control is exposed.

Roles are global definitions. A role change is audited in the company context from which User Admin performed the operation, while assignment changes are audited against the assignment's authoritative company.

P20 reserves the built-in usernames and role codes `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER` for runtime security. These reserved identities/roles cannot be renamed or deactivated. Additional custom roles may remain for administrative/history purposes but confer no runtime permissions unless a later explicitly approved policy changes that rule.

The effective ADMIN model is deliberately simple: there is one singleton database-global ADMIN account, and only that account may obtain effective ADMIN authority. Each company receives an ADMIN assignment for that singleton account. New company bootstrap also establishes default MANAGER, ACCOUNTANT, and VIEWER assignments using the reserved default accounts. Additional non-ADMIN user accounts may be assigned MANAGER, ACCOUNTANT, or VIEWER roles.

## Assignment lifecycle

Company assignments are queried and changed only for the active company. Switching the production company recreates the panel and changes that scope. The assignment form does not offer an arbitrary company selector.

Each assignment period is a separate stable history row with:

- user, company, and role IDs;
- inclusive start date;
- optional end date;
- active state;
- optional revocation timestamp and reason; and
- created/updated timestamps.

Creating an assignment requires an active user and role. Another interval for the same user, company, and role may not overlap it. Ending or revoking an assignment retains the row and requires an end date on or before the current date and not before the start date. Revocation also requires a factual reason. A later reassignment creates a new row whose start follows the prior end; an ended row is never reactivated.

Different non-ADMIN roles may overlap for the same user and company. Effective permissions are the union of active reserved non-ADMIN roles, but that union never includes `SECURITY_ADMIN`. The P20 singleton ADMIN invariant forbids removing effective ADMIN access from the sole ADMIN account, and the required ADMIN assignment for each company cannot be ended or revoked.

## Atomicity and audit history

Every create, update, deactivate, assignment, end, and revoke operation first passes the runtime `SECURITY_ADMIN` guard and then runs in one service-owned JPA transaction. The service revalidates selected rows under a pessimistic lock, writes the domain fact and company-owned `AuditEvent`, flushes, and commits once. Any authorization denial, validation, audit, constraint, or injected late failure leaves the requested User Admin mutation unapplied.

Credential and inactivity-timeout mutations follow the same fail-closed ordering: the runtime `SECURITY_ADMIN` guard runs before credential validation or the security-setting transaction, then the existing singleton ADMIN check revalidates the durable account/assignment facts before the change and its factual `security_event` are committed. A denied call cannot replace/clear credential state or alter the database-global inactivity timeout.

Audit action types include:

- `APP_USER_CREATED`, `APP_USER_UPDATED`, `APP_USER_DEACTIVATED`;
- `APP_ROLE_CREATED`, `APP_ROLE_UPDATED`, `APP_ROLE_DEACTIVATED`;
- `USER_ROLE_ASSIGNED`, `USER_ROLE_ENDED`, `USER_ROLE_REVOKED`.

Runtime authorization denial is recorded separately as factual H2 `AUTHORIZATION_DENIED` security history. Login/logout/credential/recovery events remain owned by the authentication/security services. The remaining P20-S3 actor-conversion work must make authenticated `AppUser.username` authoritative for protected `AuditEvent` writes without storing submitted passwords or creating a second audit identity.

## User interface

The Administration destination retains one User Admin tab. Its inner Users, Roles, and Company Assignments tabs expose real New/Save/lifecycle behavior, and P20-S2 replaced the former Authentication placeholder with real login/session and ADMIN credential-management behavior.

Tables remain sortable, resizable, reorderable, independently scrollable, and separated from their editors by company-owned horizontal dividers. Assignment dates use active-company display preferences and selectors retain stable entity IDs.

P20-S3 service authorization is authoritative even before JavaFX command gating is wired. The later consolidated UI pass must inject the current-session guard through `UiServiceRegistry` and disable/explain User Admin and security-admin mutation commands for accounts without `SECURITY_ADMIN`; it must not create duplicate panels or a second user/role repository.

## Donor-reference decision

The donor repository has SCLX office-assignment records but no application authentication/password/login implementation suitable for this workflow. No donor authentication, sidecar repository, or alternate role model is imported. Current `AppUser`, `AppRole`, and `UserCompanyRole` remain the authority extended by P20.
