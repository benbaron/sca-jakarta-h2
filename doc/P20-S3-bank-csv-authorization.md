# P20-S3 — Bank CSV authorization

## Scope

This tranche closes the two bank-CSV service-owned write boundaries deliberately left after PR #327:

- `NormalizedBankCsvReviewService.commit(...)`; and
- `BankCsvMappingProfileService.create(...)`, `replace(...)`, and `setActive(...)`.

Each mutation requires `ApplicationPermission.BOOKKEEPING_WRITE` before ordinary argument, profile, preview, or transaction validation. The fixed P20 policy therefore permits ADMIN, MANAGER, ACCOUNTANT, and a non-ADMIN role union containing ACCOUNTANT; VIEWER, an absent session, and a session bound to another company fail closed and record factual `AUTHORIZATION_DENIED` security history.

There is no schema, migration, JavaFX layout, or new persistence authority in this tranche.

## Normalized CSV review authority

`NormalizedBankCsvReviewService` retains its existing source-compatible constructor and adds a guarded constructor accepting the current `AuthorizationGuard`. `preview(...)` remains non-mutating and does not require bookkeeping-write authority.

`commit(...)` checks `BOOKKEEPING_WRITE` before ordinary preview or actor validation. When a preview is present, the authorization decision is bound to the preview company code. A null preview still reaches the authorization boundary first so a lower-privilege direct caller cannot probe protected commit validation. After authorization succeeds, all existing source-hash, configured-account identity, portable/external identity, matched-transaction, idempotency, rollback, and audit behavior remains unchanged.

The existing free-form actor parameter remains a source-compatible input. In guarded production composition, normalized CSV commit derives the authoritative audit actor from the current authenticated session rather than trusting that input. Governing detail: `doc/P20-S3-authenticated-audit-actor.md`.

## Mapping-profile authority

`BankCsvMappingProfileService` likewise retains its source-compatible unguarded constructor and adds a guarded constructor. Service-owned `create(...)`, `replace(...)`, and `setActive(...)` require `BOOKKEEPING_WRITE` for the named company before profile parsing or transaction work.

`list(...)` remains a read-only query. Authorization does not replace the existing company/bank-account ownership, profile-count limit, duplicate name/version, validated profile-definition, or transaction rollback rules.

Mapped statement review continues to delegate durable review commit through `BankStatementReviewService`; there is no second review persistence path.

## Production wiring boundary

This tranche establishes service enforcement but does not perform the consolidated `UiServiceRegistry`/JavaFX current-session wiring. Production `UiServiceRegistry` now constructs:

- `BankCsvMappingProfileService` with the current-session `AuthorizationGuard`;
- `NormalizedBankCsvReviewService` with the same guard; and
- mapped CSV's delegated `BankStatementReviewService` with that guard.

UI availability remains explanatory only; direct guarded service calls are the authoritative security boundary.

## Regression coverage

Direct H2 integration coverage proves:

- VIEWER denial occurs before ordinary mutation validation and leaves requested bank-review/profile state unchanged;
- ACCOUNTANT, MANAGER, ADMIN, and a VIEWER+ACCOUNTANT union receive bookkeeping-write authority;
- changing the supplied current session immediately changes authorization without a stale permission cache;
- wrong-company and absent sessions fail closed;
- denial creates durable `AUTHORIZATION_DENIED` security facts;
- normalized CSV preview and mapping-profile list remain readable without bookkeeping-write authority; and
- successful calls retain the established normalized-import and mapping-profile lifecycle behavior.

## User testing

No new JavaFX control or layout is introduced. Desktop acceptance for this tranche is regression-oriented: confirm the existing mapped-CSV profile workflow and normalized-CSV preview/import workflow still open and behave normally under the current ADMIN/operator path. Role-based JavaFX disabling is deliberately deferred to the consolidated P20-S3 UI wiring tranche; lower-privilege enforcement is covered directly at the H2 service boundary here.
