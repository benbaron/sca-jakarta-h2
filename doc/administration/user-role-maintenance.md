# User, role, and assignment maintenance

## Authority and security boundary

`app_user`, `app_role`, and `user_company_role` are the durable administration authority. They record application-user labels, global role definitions, and company-specific assignment history. They do not authenticate a person, establish the current operator's identity, or enforce permissions in the production shell. Passwords, identity providers, login policy, session identity, and role-aware action gating require a separately authorized security phase.

The factual audit actor is explicit user input defaulted from the local operating-system username. It is an audit label, not proof of identity.

## Users and roles

Users and roles are edited by stable database ID. Changing a username or role code updates the selected row and does not create a replacement record. Usernames and role codes remain global, case-insensitively unique business labels.

Users and roles are retained rather than hard-deleted. An active user or role may be deactivated only after every active assignment referencing it has been ended or revoked. Historical assignments continue to reference inactive users and roles. No Delete control is exposed.

Roles are global definitions. A role change is audited in the company context from which User Admin performed the operation, while assignment changes are audited against the assignment's authoritative company.

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

Different roles may overlap for the same user and company. No last-administrator rule is adopted in this phase because no authenticated user or effective authorization policy exists.

## Atomicity and audit history

Every create, update, deactivate, assignment, end, and revoke operation runs in one service-owned JPA transaction. The service revalidates selected rows under a pessimistic lock, writes the domain fact and company-owned `AuditEvent`, flushes, and commits once. Any validation, audit, constraint, or injected late failure rolls back all writes.

Audit action types are:

- `APP_USER_CREATED`, `APP_USER_UPDATED`, `APP_USER_DEACTIVATED`;
- `APP_ROLE_CREATED`, `APP_ROLE_UPDATED`, `APP_ROLE_DEACTIVATED`;
- `USER_ROLE_ASSIGNED`, `USER_ROLE_ENDED`, `USER_ROLE_REVOKED`.

## User interface

The existing Administration destination retains one User Admin tab. Its inner Users, Roles, and Company Assignments tabs each expose real New and Save behavior; the Authentication tab exposes neither. Tables remain sortable, resizable, reorderable, independently scrollable, and separated from their editors by company-owned horizontal dividers. Assignment dates use active-company display preferences and selectors retain stable entity IDs.

## Donor-reference decision

The donor repository has SCLX office-assignment records but no application user/role maintenance authority suitable for this workflow. Its office-assignment DTOs remain interchange-domain reference only; no donor authentication, sidecar repository, or alternate role model is imported.
