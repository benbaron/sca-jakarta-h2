# P20-S3 authenticated audit actor

## Scope

This tranche makes the current authenticated application account the authoritative actor for protected P20-S3 audit writes. It does not create another identity, session, role, audit, import, or persistence authority.

For every guarded production mutation that writes a current-operation `AuditEvent` or factual actor field, the actor is the current `AuthenticatedUserSession.username` returned by the same `AuthorizationGuard` that permits the mutation. Caller-supplied actor strings may remain in public commands and method signatures for source compatibility, but a guarded production service does not trust them.

The covered current-operation boundaries include:

- Journal transaction entry, update, direct edit, delete, and reversal;
- fixed-asset status, depreciation, lifecycle commit, and lifecycle reversal;
- inventory status, movement commit, and movement reversal;
- period close and reopen;
- reconciliation successor creation;
- reviewed-statement acceptance and its nested canonical transaction;
- Chart of Accounts CSV commit;
- SCLX commit and canonical transactions created by that commit;
- strict bank-statement review and normalized-bank-CSV review commits; and
- User Admin user, role, assignment, end, and revoke audit facts.

`ServiceAuthorization.actor(...)` is the shared adapter for service-package compatibility seams. When a service has no guard, it returns the existing fallback actor so established tests and explicitly caller-owned composition remain source-compatible. When a guard is present, it returns `AuthorizationGuard.requireActor(...)`, which both authorizes against the current session and returns that authenticated username. Interchange packages use the same public `AuthorizationGuard.requireActor(...)` authority directly.

## Caller-owned and historical seams

Authenticated current-operation provenance must not rewrite historical source facts.

Caller-owned transaction/import helpers remain deliberately unguarded at their inner boundary and continue to accept an actor from their already-governed outer operation. The guarded outer fixed-asset, inventory, reviewed-statement, and SCLX services pass the authenticated username into nested canonical transaction/correction helpers.

SCLX factual period-close and audit-history extensions are different: their actor values are historical source data. `SclxImportCommitService` therefore preserves source `actor` values when restoring those already-authoritative history records, while the newly created local SCLX operation audit fact and canonical transaction audit facts use the current authenticated username.

## Desktop behavior

`DesktopActorIdentity.current()` resolves the authenticated session username first. Its workstation-name fallback remains only for pre-authentication/unguarded compatibility use; it is not an alternate authority at a guarded service boundary.

Existing JavaFX actor controls for protected P20-S3 workflows may continue to display actor identity for operator clarity, but they are read-only and initialized from `DesktopActorIdentity.current()`. Literal protected-operation actors such as `ui` and `ui-operator` are removed from those routes.

`CompanyOwnershipDiagnosticsPanel` is deliberately excluded from this tranche because company-ownership repair is classified as `DATABASE_ADMIN`, whose service authorization is the next separate P20-S3 tranche. Its legacy actor input must be converted when that database-administration boundary becomes guarded; it is not evidence of an alternate actor authority for an already-guarded mutation. Legacy `AccountingPeriodService` likewise has no production route and is not promoted back to canonical period-close authority.

## Security and atomicity

Actor derivation occurs at the guarded mutation boundary before ordinary protected mutation validation. A lower-privilege caller therefore still fails closed before it can use malformed or spoofed actor input to probe a protected write path. Authorization consumes the live current session on every call; no actor or permission snapshot is cached independently.

This tranche changes no schema or migration. It does not change imported historical actor values, durable entity identities, transaction atomicity, period-close/reconciliation protections, import idempotency, or existing business validation after authorization succeeds.

## Regression coverage

Required coverage proves that:

- guarded Journal and User Admin mutations persist the authenticated username even when callers submit another actor string;
- source guardrails cover every current guarded audit-producing production boundary and reject regression to literal/workstation actor authority;
- JavaFX actor displays on protected workflows are authenticated and non-editable;
- SCLX continues to preserve imported historical actor values while using the authenticated actor for new local operation facts; and
- existing authorization suites continue to prove VIEWER denial, role/company/session changes, durable denial facts, caller-owned seams, and successful authorized behavior.
