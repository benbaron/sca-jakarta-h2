# P20-S3 import-commit authorization

## Scope

This tranche applies the established P20-S3 runtime policy to the two outer atomic import commit boundaries that directly create or mutate authoritative bookkeeping data:

- `SclxImportCommitService.commit(...)`; and
- `CoaCsvImportService.commit(...)`.

Both require `ApplicationPermission.BOOKKEEPING_WRITE` before source, preview, confirmation, mapping, conflict, drift, or other interactive validation and before entering a mutation transaction. Preview and other non-mutating review operations remain available without this write decision.

## Outer-boundary ownership

These services remain the authorization and transaction owners for their respective accepted imports. Their nested helpers intentionally remain caller-owned transaction seams. SCLX continues to invoke unguarded import helpers for accounts, budgets, transactions, fixed assets, inventory, banking/reconciliation, period close, and audit-history restoration inside one governed SCLX transaction. COA CSV continues to use the caller-owned `AccountAdminService.upsert(EntityManager, ...)` seam inside one governed CSV transaction.

No nested helper receives a second authorization decision. This preserves atomicity and prevents partial import caused by repeated authorization checks inside a caller-owned transaction.

The selected target company supplied to the outer commit service is part of the authorization decision. An absent session or a session bound to another company fails closed and records an `AUTHORIZATION_DENIED` security fact.

## Preserved integrity rules

Authorization does not replace import validation. After authorization succeeds, all existing protections remain authoritative, including exact source hashing, fresh preview/re-preview requirements, mapping and conflict approval, populated-target confirmation, company/chart ownership, target fingerprint drift, idempotent identities, closed-period and finalized-reconciliation protections, and all-or-nothing rollback.

Legacy constructors remain source-compatible for existing tests and already-governed caller-owned seams. Production `UiServiceRegistry` now supplies the current-bundle guard. Free-form import actor parameters remain compatibility inputs only: guarded CoA CSV and SCLX commits derive the authoritative current-operation actor from the authenticated session. SCLX still preserves actor values inside imported historical period-close and audit-history facts. Governing detail: `doc/P20-S3-authenticated-audit-actor.md`.

## Required regression coverage

Direct H2 coverage proves:

- VIEWER denial occurs before interactive commit validation and leaves imported business facts unchanged;
- ACCOUNTANT, MANAGER, ADMIN, and a non-ADMIN union containing ACCOUNTANT receive bookkeeping-write authority;
- an absent session and a session bound to the wrong company fail closed;
- authorization reads the current session for every commit attempt rather than caching permission state;
- denial produces durable `AUTHORIZATION_DENIED` facts; and
- successful guarded commits retain existing idempotency, target/source drift, conflict, and rollback behavior already covered by the import suites.

Bank-statement review/import and reviewed-row ledger acceptance are separate service-owned mutation boundaries and are deliberately deferred to the next P20-S3 banking-import authorization tranche.
