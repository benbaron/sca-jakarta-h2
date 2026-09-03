# P20-S3 inventory authorization

## Scope

This tranche applies the established P20-S3 runtime authorization policy to the authoritative inventory write service. It does not add a second inventory, ledger, import, session, or authorization authority.

`InventoryService` remains the single application service for interactive inventory metadata, retained status lifecycle, quantity movements, movement accounting, and governed movement reversal. `ApplicationPermission.BOOKKEEPING_WRITE` is required at that service boundary before validation or mutation for:

- `create(...)`;
- `update(...)`;
- `changeStatus(...)`;
- confirmed `recordMovement(MovementPreview, actor)`;
- compatibility `recordMovement(itemId, command)` before its preview/commit sequence; and
- `reverseMovement(...)`.

Read-only item/movement loads and lists, `previewMovement(...)`, and `previewMovementReversal(...)` remain readable without a bookkeeping-write authorization decision at this service boundary.

## Caller-owned import seams

`createForImport(...)` and `recordMovementForImport(...)` remain caller-owned transaction seams. They require an already-active JPA transaction and preserve the existing SCLX/import authority model; they are not independently guarded here. P20-S3 must authorize the outer import/SCLX commit boundary before these helpers are reached.

The canonical transaction-entry and transaction-correction helpers invoked from confirmed inventory movements likewise remain caller-owned inner transaction seams. The outer `InventoryService` mutation is the authorization decision for the complete atomic inventory/accounting operation.

## Compatibility and production composition

Existing unguarded constructors remain source-compatible for tests and already-governed internal seams. Production `UiServiceRegistry` now constructs the service with the current-bundle `AuthorizationGuard`.

Guarded inventory status, movement, and reversal operations override caller actor text with the current authenticated username and pass that actor into nested canonical transaction/correction audits. Caller-owned import seams retain their established source/caller semantics. Governing detail: `doc/P20-S3-authenticated-audit-actor.md`.

There are no schema or migration changes.

## Required regression coverage

Direct H2 integration coverage must prove:

- VIEWER is denied on every service-owned inventory mutation route before argument validation or durable mutation;
- denial leaves inventory items, movement history, canonical transactions, and domain audit facts unchanged while adding durable `AUTHORIZATION_DENIED` security facts;
- ACCOUNTANT, MANAGER, ADMIN, and a union containing ACCOUNTANT can exercise `BOOKKEEPING_WRITE` and current-session changes take effect immediately;
- an absent session and a session bound to the wrong company fail closed;
- item/movement reads and non-mutating previews remain usable without a bookkeeping-write decision; and
- the two documented caller-owned import seams continue to operate inside an explicitly caller-owned transaction even when this service instance has no authenticated session.

Existing inventory lifecycle/accounting tests remain authoritative for quantity/value validation, atomicity, period-close/reconciliation protection, portable identity, retained lifecycle history, and correction semantics.
