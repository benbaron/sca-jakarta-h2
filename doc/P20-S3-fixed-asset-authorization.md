# P20-S3 fixed-asset authorization

## Scope

This tranche applies the established P20-S3 runtime authorization policy to the authoritative fixed-asset write service. It does not add a second asset, ledger, import, session, or authorization authority.

`FixedAssetService` remains the single application service for interactive fixed-asset metadata, retained status lifecycle, depreciation runs, and Sale/Retirement/Impairment lifecycle accounting. `ApplicationPermission.BOOKKEEPING_WRITE` is required at that service boundary before validation or mutation for:

- `create(...)`;
- `update(...)`;
- `changeStatus(...)`;
- `runMonthlyDepreciation(...)`;
- `recordLifecycleEvent(...)`; and
- `reverseLifecycleEvent(...)`.

Read-only loads/lists and the non-mutating lifecycle preview methods remain readable without a bookkeeping-write authorization decision at this service boundary.

## Caller-owned import seams

`createForImport(...)` and `recordCompletedRunForImport(...)` remain caller-owned transaction seams. They require an already-active JPA transaction and preserve the existing import authority model; they are not independently guarded here. P20-S3 must authorize the outer SCLX/import commit boundary before these helpers are reached.

The canonical transaction helpers invoked from fixed-asset depreciation/lifecycle commits likewise remain caller-owned inner transaction seams. The outer `FixedAssetService` mutation is the authorization decision for the complete atomic asset/accounting operation.

## Compatibility and deferred work

Existing unguarded constructors remain source-compatible for tests and already-governed internal seams. A guarded constructor accepts the current `AuthorizationGuard`; production registry/session injection remains a later P20-S3 tranche.

This tranche deliberately does not convert caller-supplied fixed-asset audit actor strings to authenticated identity. P20-S3 authenticated audit-actor conversion remains a separate cross-cutting task so authorization enforcement and audit provenance are not conflated.

There are no schema or migration changes.

## Required regression coverage

Direct H2 integration coverage must prove:

- VIEWER is denied on every service-owned fixed-asset mutation route before argument validation or durable mutation;
- denial leaves fixed assets, depreciation runs, lifecycle events, canonical transactions, and domain audit facts unchanged while adding durable `AUTHORIZATION_DENIED` security facts;
- ACCOUNTANT, MANAGER, ADMIN, and a union containing ACCOUNTANT can exercise `BOOKKEEPING_WRITE` and current-session changes take effect immediately;
- an absent session and a session bound to the wrong company fail closed; and
- the two documented caller-owned import seams continue to operate inside an explicitly caller-owned transaction even when this service instance has no authenticated session.

Existing fixed-asset accounting/lifecycle tests remain authoritative for domain validation, atomicity, period-close/reconciliation protection, portable identity, and correction semantics.