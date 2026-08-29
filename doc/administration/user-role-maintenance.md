# User, role, and assignment maintenance

## Authority and security boundary

`app_user`, `app_role`, and `user_company_role` are the durable administration authority. They record application-user identities, global role definitions, and company-specific assignment history.

Current production through P19 does not yet authenticate a person or enforce permissions. P20-S1 now defines the approved security contract in `doc/P20-S1-authentication-authorization-boundary.md`; login/session/credential behavior remains unimplemented until P20-S2 and runtime permission enforcement remains unimplemented until P20-S3.

Roles never own passwords. Credentials belong only to `AppUser` accounts. The reserved ADMIN, MANAGER, ACCOUNTANT, and VIEWER accounts are intended to begin passwordless, with credential management owned by the singleton ADMIN account once P20-S2 is implemented.

Until authenticated sessions are live, the factual audit actor remains explicit user input defaulted from the local operating-system username. It is an audit label, not proof of identity. P20-S3 must replace that free-form actor as the authoritative audit identity rather than retain two competing actor authorities.

## Users and roles

Users and roles are edited by stable database ID. Changing a username or role code updates the selected row and does not create a replacement record. Usernames and role codes remain global, case-insensitively unique business labels.

Users and roles are retained rather than hard-deleted. An active user or role may be deactivated only after every active assignment referencing it has been ended or revoked. Historical assignments continue to reference inactive users and roles. No Delete control is exposed.

Roles are global definitions. A role change is audited in the company context from which User Admin performed the operation, while assignment changes are audited against the assignment's authoritative company.

P20 reserves the built-in usernames and role codes `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER` for runtime security. After P20 enforcement is enabled, these role codes cannot be renamed/deactivated. Additional custom roles may remain for administrative/history purposes but do not automatically gain runtime permissions.

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

Different non-ADMIN roles may overlap for the same user and company. Under P20, effective permissions are the union of active reserved non-ADMIN roles. The former statement that there was no last-administrator rule applied only before runtime authorization existed; the P20 singleton ADMIN invariant replaces it and forbids removing effective ADMIN access from the sole ADMIN account.

## Atomicity and audit history

Every create, update, deactivate, assignment, end, and revoke operation runs in one service-owned JPA transaction. The service revalidates selected rows under a pessimistic lock, writes the domain fact and company-owned `AuditEvent`, flushes, and commits once. Any validation, audit, constraint, or injected late failure rolls back all writes.

Audit action types currently include:

- `APP_USER_CREATED`, `APP_USER_UPDATED`, `APP_USER_DEACTIVATED`;
- `APP_ROLE_CREATED`, `APP_ROLE_UPDATED`, `APP_ROLE_DEACTIVATED`;
- `USER_ROLE_ASSIGNED`, `USER_ROLE_ENDED`, `USER_ROLE_REVOKED`.

P20-S2/P20-S3 must add factual login/logout/credential/recovery/authorization-denial events without storing passwords or submitted credential text.

## User interface

The existing Administration destination retains one User Admin tab. Its inner Users, Roles, and Company Assignments tabs each expose real New and Save behavior. The current Authentication tab remains informational until P20-S2 is implemented.

Tables remain sortable, resizable, reorderable, independently scrollable, and separated from their editors by company-owned horizontal dividers. Assignment dates use active-company display preferences and selectors retain stable entity IDs.

P20-S2 will replace the Authentication deferral with the approved login/session and ADMIN credential-management behavior. P20-S3 will add permission-aware command/action availability without creating duplicate panels or a second user/role repository.

## Donor-reference decision

The donor repository has SCLX office-assignment records but no application authentication/password/login implementation suitable for this workflow. No donor authentication, sidecar repository, or alternate role model is imported. Current `AppUser`, `AppRole`, and `UserCompanyRole` remain the authority extended by P20.