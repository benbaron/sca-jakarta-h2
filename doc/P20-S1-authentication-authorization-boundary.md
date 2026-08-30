# P20-S1 — Authentication and authorization requirements boundary

## Purpose

P20-S1 defines the security contract that later P20 implementation slices must follow. It does **not** add login, passwords, session enforcement, or permission gating by itself.

The production model already contains global `AppUser` records, global `AppRole` definitions, and company-scoped `UserCompanyRole` assignment history. P20 must extend that authority rather than introduce a second identity or role store.

## Owner decisions

The owner has adopted the following security model:

- authentication is local to the H2 bookkeeping database; there is no external identity provider requirement;
- roles themselves never own passwords or credentials;
- application user accounts may optionally have a password;
- the built-in `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER` accounts start with **no password**;
- `ADMIN` may set, replace, or clear a password on any account, including `ADMIN` itself;
- a company starts with one default `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER` assignment;
- there is exactly **one effective ADMIN account** in a database for simplicity;
- inactivity timeout is disabled by default;
- explicit login/logout remains the session boundary;
- authenticated session identity replaces free-form operator text as the authoritative actor for security-sensitive and accounting audit events once P20 is implemented.

## Terminology: account versus role

`AppUser` is the login/account identity. `AppRole` is an authorization label. `UserCompanyRole` assigns a role to an account within one company.

A role never has a password. When the UI refers to the default ADMIN, MANAGER, ACCOUNTANT, or VIEWER "account", it means a reserved `AppUser` associated with the corresponding reserved role through company-scoped assignments.

## Reserved built-in accounts and roles

P20 reserves the usernames and role codes:

- `ADMIN`
- `MANAGER`
- `ACCOUNTANT`
- `VIEWER`

The four reserved accounts are database-global `AppUser` rows because usernames are already global identities. Each company receives one active default assignment from each reserved account to the matching reserved role.

The singleton ADMIN rule is stronger:

- exactly one reserved `ADMIN` `AppUser` is the effective administrator account for the database;
- no second account may acquire effective ADMIN authority;
- the reserved ADMIN account cannot be renamed, deactivated, or deleted while P20 authorization is active;
- each company must have an active ADMIN assignment for that singleton account;
- new company creation must establish the default four assignments atomically with the company bootstrap/security initialization path.

The reserved MANAGER, ACCOUNTANT, and VIEWER accounts provide a simple default identity for each role. Additional non-reserved user accounts may later be assigned MANAGER, ACCOUNTANT, or VIEWER roles. The singleton restriction applies only to ADMIN.

The reserved role codes are runtime security identifiers and therefore may not be renamed or deactivated once P20 enforcement is enabled. Existing custom role records may remain as administrative/history data, but they confer no runtime permissions unless a later explicitly authorized policy defines them.

## Password and credential semantics

Credentials belong to `AppUser`, never `AppRole`.

A newly bootstrapped reserved account has no password. A passwordless account may start a session only through an explicit login/account-selection action; an empty password is accepted because no password challenge is configured for that account. This is an intentional low-friction local-desktop mode, not a claim of strong identity proof.

Only ADMIN may manage credentials. ADMIN may:

- set a password on any active account;
- replace an existing password;
- clear a password, returning that account to passwordless login; and
- perform the same actions on the ADMIN account itself.

Non-ADMIN accounts do not manage credentials in P20 unless a later requirement explicitly adds self-service password change.

When a password exists:

- plaintext or reversibly encrypted passwords are forbidden;
- H2 stores only salted, adaptive one-way credential material and the parameters/version needed to verify and upgrade it;
- passwords are never written to logs, audit payloads, exceptions, UI state, exports, backups other than as already-hashed database content, or diagnostic text;
- P20-S2 must select a maintained password-hashing implementation suitable for Java 17+ and document its upgrade strategy;
- no composition rule is required beyond a nonblank value and a defensive maximum length unless the owner later adopts a stricter policy.

## Login and session lifecycle

P20 requires an explicit account-selection/login surface before protected application work begins.

Session facts are:

- current authenticated `AppUser` ID;
- username/display label for presentation and audit;
- active company;
- effective company-scoped roles/permissions;
- login time and security-event correlation information as needed for audit.

Logout clears the authenticated identity and returns to the login surface. Application exit and database switch also terminate the authenticated session.

Changing company does not change the authenticated account. The application recomputes that account's effective assignments for the target company. A company switch is rejected if the account has no effective access to the target company. The default reserved assignments ensure a newly created company is initially reachable by the four built-in accounts.

### Inactivity timeout

The default is **no inactivity timeout**. A zero/absent timeout means the session remains active until explicit logout, application exit, or database switch.

P20 may expose an ADMIN-controlled database security setting for a nonzero inactivity timeout, but timeout enforcement must remain disabled unless explicitly configured. If enabled later, expiration logs out the session rather than silently changing identity.

## Recovery model

There is no email, security-question, cloud, or secondary-administrator recovery requirement.

ADMIN may set or clear its own password while logged in. Because there is only one ADMIN account, forgotten ADMIN credentials require an explicit **offline local recovery** path rather than another privileged account. The recovery boundary is direct control of the local H2 database file:

- the database must not be active in another application session;
- recovery may clear only the ADMIN credential, returning ADMIN to the default passwordless state;
- it must not create a second ADMIN account or default/backdoor password;
- it must preserve users, roles, assignments, and accounting data;
- the next successful open/login must record a factual security-recovery event.

This is consistent with the local-desktop threat model: an operator with unrestricted operating-system access to the database files is already outside the protection offered by an application password.

## Authorization semantics

Runtime authorization uses the authenticated account's active `UserCompanyRole` assignments for the active company. Reserved role permissions are fixed application policy rather than editable arbitrary permission rows.

### ADMIN

ADMIN has full application authority, including:

- all MANAGER and ACCOUNTANT operations;
- company/database administration exposed by the production application;
- user, role, assignment, credential, and security-setting administration;
- the ability to set/replace/clear any account password, including its own.

Only the singleton ADMIN account receives effective ADMIN authority.

### MANAGER

MANAGER has normal bookkeeping authority plus non-security company administration. It may perform operational/accounting work and maintain company-level configuration that is not identity/security administration.

MANAGER does not manage passwords, authentication settings, reserved role definitions, or user-role security assignments unless a later owner decision explicitly expands that authority.

### ACCOUNTANT

ACCOUNTANT has normal bookkeeping write access: accounting entry/correction workflows and the operational banking, reconciliation, budget, asset/depreciation, inventory, import, period-close, and reporting/export workflows that already belong to bookkeeping operations.

ACCOUNTANT does not receive security administration or company-security authority.

### VIEWER

VIEWER is read-only for durable business/accounting state. It may navigate, search, review, run reports, and perform non-mutating exports, but it may not create, edit, delete, correct, reconcile/finalize, close/reopen periods, accept imports, or otherwise mutate authoritative data.

### Multiple non-ADMIN roles

A non-ADMIN account may hold more than one active reserved role in a company. Effective permissions are the union of those active role permissions. Custom/non-reserved roles do not expand runtime permissions in P20.

No approval queue, posting-approval state, or formal oversight workflow is introduced by these roles.

## Audit requirements

Once P20-S2/P20-S3 are active, security-relevant actions must produce factual audit/security events sufficient to reconstruct:

- successful login;
- failed login attempts without recording the submitted password;
- logout;
- password set/replaced/cleared;
- offline ADMIN credential recovery;
- authorization denial for a requested protected action;
- inactivity timeout if a timeout is later configured;
- creation of reserved security bootstrap facts and resolution of any bootstrap conflict.

Authenticated `AppUser` identity becomes the authoritative actor for application audit writes. Existing free-form actor entry is not identity proof and must not remain a parallel authoritative actor once authenticated sessions are enforced.

## Upgrade and bootstrap requirements

P20 implementation must be nondestructive.

For an existing database:

- preserve all existing `app_user`, `app_role`, `user_company_role`, and accounting/history rows;
- ensure the four reserved role definitions exist;
- ensure the four reserved default accounts exist with no password unless a credential has already been deliberately established by P20;
- establish the default company assignments needed by the security model;
- never invent a password during migration;
- never silently delete or overwrite a conflicting existing reserved identity;
- if an existing row makes reserved-account bootstrap ambiguous, enter an explicit security-bootstrap resolution path rather than guessing or discarding data.

For a newly created company, the company/security bootstrap path must establish one default assignment for each reserved account to the corresponding role. The singleton ADMIN account is reused rather than recreated.

Pre-P20 role assignments were administrative facts and did not confer runtime permissions. P20 must preserve their history while deriving effective runtime authority only from the adopted reserved-role policy.

## Threat model

This is a local JavaFX/H2 bookkeeping application, not a network authentication service.

The intended security boundary is:

- normal application users should not acquire write/admin privileges merely by choosing a lower-privilege account;
- an optional password can prevent casual impersonation through the application UI;
- passwords protect application login, not the underlying workstation against an attacker with unrestricted OS/process/database-file access;
- operating-system account/file permissions, backups, disk encryption, and physical workstation security remain the outer trust boundary;
- no SSO, OAuth/OIDC, LDAP/Active Directory, remote credential service, MFA, or internet account recovery is required by P20.

## Implementation sequencing

### P20-S1 — Requirements boundary

This document and the governing PLAN update only. There is no user-visible runtime behavior change.

### P20-S2 — Authentication implementation

Implement credential persistence, reserved-account bootstrap, login/logout/session identity, optional timeout setting with disabled default, ADMIN credential management, and offline ADMIN recovery. Do not yet rely on scattered panel-specific permission checks as the security boundary.

### P20-S3 — Runtime authorization enforcement

Define and apply the concrete command/panel/service permission matrix, replace free-form audit actor authority with authenticated identity, enforce singleton ADMIN/reserved-role protections across User Admin, and verify that denied mutations cannot bypass service boundaries.

## P20-S1 acceptance

P20-S1 is accepted when the owner confirms this document accurately captures the intended model and the requirements PR is merged. Because P20-S1 is documentation-only, it has no desktop behavior to test; repository CI is still required to prove the documentation/PLAN change did not disturb the build.