# P20-S3 period-close authorization

## Scope

This tranche applies the established P20-S3 runtime authorization policy to the authoritative period-close write service. It does not add a second close-state, ledger, import, session, or authorization authority.

`PeriodCloseRangeService` remains the canonical production authority for company-scoped closed date ranges and factual close/reopen history. `ApplicationPermission.BOOKKEEPING_WRITE` is required at that service boundary before interactive validation or mutation for:

- `closeRange(...)`; and
- `reopenRange(...)`.

`reopenRange(...)` resolves the immutable company owner of the selected durable range before the authorization decision so a session bound to another company cannot reopen it. Existing overlap, company ownership, reopen-policy, reason, and factual-history rules remain in force after authorization succeeds.

Read-only range/history queries and `requireOpen(...)` remain outside the bookkeeping-write authorization decision at this service boundary.

## Caller-owned interchange seam

`importForInterchange(...)` remains a caller-owned transaction seam. It requires an already-active JPA transaction and restores already-authoritative factual period-close history without replaying interactive Close/Reopen policy. It is not independently guarded here. P20-S3 must authorize the outer SCLX/import commit boundary before this helper is reached.

## Compatibility and deferred work

The existing one-argument constructor remains source-compatible for tests and already-governed internal seams. A guarded constructor accepts the current `AuthorizationGuard`; production `UiServiceRegistry`/session injection remains a later P20-S3 tranche.

This tranche deliberately does not convert the existing close/reopen actor input to authenticated identity. P20-S3 authenticated audit-actor conversion remains a separate cross-cutting task so runtime authorization and audit provenance are not conflated.

Legacy `PeriodCloseService`/`PeriodCloseRunRepository` artifacts remain compatibility/history support and are not promoted back to canonical close-state authority. There are no schema or migration changes.

## Required regression coverage

Direct H2 integration coverage must prove:

- VIEWER is denied on both service-owned Close/Reopen routes before mutable work or interactive argument validation;
- denial leaves durable close ranges, factual close/reopen events, and domain `AuditEvent` history unchanged while adding durable `AUTHORIZATION_DENIED` security facts;
- ACCOUNTANT, MANAGER, ADMIN, and a union containing ACCOUNTANT can exercise `BOOKKEEPING_WRITE`, and current-session changes take effect immediately;
- an absent session and a session bound to the wrong company fail closed;
- range/history queries remain usable without a bookkeeping-write decision; and
- `importForInterchange(...)` remains usable inside an explicitly authorized caller-owned transaction even when the `PeriodCloseRangeService` instance has no authenticated session.

Existing period-close tests remain authoritative for overlap rejection, factual close/reopen history, closed-period protection, and reopen policy semantics.
