# P20-S3 — Database administration authorization

## Scope

This tranche applies the fixed P20 `DATABASE_ADMIN` permission to the remaining post-login database-administration mutations. `DATABASE_ADMIN` is granted only by effective reserved `ADMIN` authority.

Protected operations are:

- whole-database backup;
- restore to a new validated database copy;
- activation/switch to a validated restored copy;
- explicit Company Ownership Diagnostics owner assignment; and
- explicit sample-company create/refresh.

Database selection, database creation, and retry/repair at the outer login gate remain the deliberate pre-login exception from `doc/P20-S3-runtime-authorization.md`. They are required to reach a database-owned login identity and do not grant protected bookkeeping access.

There is no schema or migration change.

## Whole-database transfer authority

`DatabaseTransferService` remains the persistence/file implementation for H2 `BACKUP TO`, restore-to-new-copy validation, and delegated database switching. Authorization policy is not moved into the persistence package.

`DatabaseAdministrationService` is the authoritative post-login service boundary. Before delegating it requires `DATABASE_ADMIN` for:

- `backUpDatabase(...)`;
- `restoreDatabaseCopy(...)`; and
- `switchToValidatedCopy(...)`.

Production workspace composition supplies this service with the current `UiServiceRegistry` bundle guard through a supplier rather than capturing one guard forever. That distinction is required because database-transfer actions outlive a database switch: each operation must authorize against the currently active H2 `Jpa` and current authenticated session. A successful validated-copy activation continues through the existing `DatabaseSessionController`, which records/clears the old authenticated session at the database-change boundary.

The persistence-level `DatabaseTransferService` remains directly testable as an unguarded implementation detail. Production JavaFX routes do not call it directly for protected post-login transfer mutations.

## Company ownership repair authority

`CompanyOwnershipService.assignOwner(...)` is the only service-owned diagnostic repair mutation in this scope. It requires `DATABASE_ADMIN` before ordinary actor/reason validation or transaction work. Listing diagnostics and ownership validation/query helpers remain non-mutating and are not guarded by this permission.

For a guarded repair, the factual audit actor is the authenticated username obtained from the same `AuthorizationGuard`; caller actor text remains only a source-compatible fallback for unguarded legacy/test construction. The JavaFX diagnostics actor display is therefore read-only and cannot spoof audit provenance.

Existing repair protections remain unchanged after authorization succeeds: stale/resolved diagnostics, unsupported repair types, inactive companies, related-company incompatibility, already-owned rows, constraint failures, and all transaction rollback rules still apply.

## Sample-company administration authority

`SampleCompanyService.createOrRefresh()` requires `DATABASE_ADMIN` before seeding/updating the explicit disposable sample records. Existing direct unguarded construction remains source-compatible for established tests; production `UiServiceRegistry` constructs the bundle-owned service with the current guard.

Sample data remains an explicit tester/admin action. This tranche does not make sample data automatic, does not seed fictional data into ordinary production databases without an explicit command, and does not change the existing idempotent sample definitions.

## Required regression coverage

Direct integration coverage must prove:

- VIEWER, ACCOUNTANT, and MANAGER cannot back up, restore, activate a validated copy, repair ownership, or create/refresh sample data;
- an absent authenticated session fails closed;
- denied operations leave the requested file/business mutation undone and add durable `AUTHORIZATION_DENIED` security facts;
- ADMIN can perform the existing whole-database transfer lifecycle;
- successful guarded ownership repair records the authenticated ADMIN username even when a spoofed actor argument is supplied;
- changing the current authenticated role state immediately changes the decision without a stale permission cache;
- sample-company denial leaves sample rows absent and ADMIN success retains the established idempotent behavior; and
- production source routing uses the guarded database-administration façade, guarded ownership/sample constructors, and a dynamic current-bundle guard supplier.

## User testing

1. Log in as ADMIN and confirm **Backup Database…**, **Restore Database Copy…**, and **Switch to Validated Copy** still complete through the existing validated-transfer flow.
2. As VIEWER, ACCOUNTANT, or MANAGER, invoke the same post-login transfer actions and confirm the operation is rejected without creating/restoring/switching the requested database. Role-based control disabling is completed in the later JavaFX-gating tranche.
3. As ADMIN, open **Administration -> Company Ownership Diagnostics**, perform one valid direct owner assignment, and confirm the displayed actor is the logged-in username and cannot be edited.
4. As a non-ADMIN account, confirm the same ownership repair is rejected and the diagnostic remains unresolved.
5. As ADMIN, run **File -> Create / Refresh Sample Company Data** and confirm the established idempotent sample data is present. As a non-ADMIN account, confirm the mutation is rejected.
6. From the outer login/database gate, confirm selecting/creating/retrying a database remains available before authentication as required for local database bootstrap.
