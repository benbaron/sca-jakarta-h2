# P20-S3 bank import review and acceptance authorization

## Scope

This tranche applies the established P20-S3 runtime authorization policy to three service-owned bank-review and reviewed-row acceptance mutation boundaries:

- `BankStatementReviewService.commit(...)`;
- `BankImportReviewService.createReviewBatch(...)`; and
- `ReviewedStatementAcceptanceService.accept(...)`.

Each requires `ApplicationPermission.BOOKKEEPING_WRITE` before ordinary commit validation or durable mutation. Non-mutating statement preview and reviewed-row acceptance preview remain outside the bookkeeping-write decision.

The fixed P20 reserved-role policy therefore allows ADMIN, MANAGER, ACCOUNTANT, and a non-ADMIN role union containing ACCOUNTANT to perform these mutations. VIEWER, an absent session, and a session bound to another company fail closed and record `AUTHORIZATION_DENIED` security history.

## Boundary ownership

`BankStatementReviewService` remains the authoritative strict OFX/QFX durable-review commit. Its guarded constructor accepts the current `AuthorizationGuard`; the existing one-argument and package-private constructors remain source-compatible for tests and already-governed internal composition.

`BankCsvReviewService` continues to delegate mapped-CSV commit to `BankStatementReviewService`. It does not receive a second independent authorization decision in this tranche. The later production `UiServiceRegistry` wiring tranche must construct its underlying `BankStatementReviewService` with the current-session guard.

`BankImportReviewService.createReviewBatch(...)` remains a service-owned generic normalized-review transaction and now requires bookkeeping-write authority. Its `importForInterchange(...)` method remains deliberately unguarded because it is a caller-owned transaction seam used from the already-guarded outer SCLX commit.

`ReviewedStatementAcceptanceService.accept(...)` remains the single service-owned boundary that converts one eligible durable reviewed row into a canonical `Txn`, links the accepted transaction, updates statement/batch disposition, and writes factual audit history in one transaction. Its nested `TransactionEntryService.enter(EntityManager, ...)` call remains caller-owned; the outer acceptance service owns authorization exactly once.

## Preserved integrity rules

Authorization does not replace bank-import or acceptance validation. After permission succeeds, all existing protections remain authoritative, including:

- exact source hashing and preview freshness;
- configured-company/bank-account ownership and statement-account matching;
- suffix-only account-identity confirmation;
- duplicate classification and idempotent statement re-import;
- mapped-CSV profile freshness through the existing delegated path;
- reviewed-row source freezing, company/account/currency validation, balanced canonical transaction validation, duplicate confirmation, closed-period protection, and finalized-reconciliation protection; and
- complete rollback on late failure.

Free-form actor parameters remain source-compatible inputs, but guarded production review/acceptance commits override them with the current authenticated username. Reviewed-row acceptance passes that same authenticated actor into the nested canonical transaction and its acceptance audit fact. Governing detail: `doc/P20-S3-authenticated-audit-actor.md`.

## Explicitly remaining bank authorization work

Current-main inspection identified two additional independent service-owned bank mutations that are not part of the owner-authorized file scope for this tranche and therefore remain required before P20-S3 completion:

- `NormalizedBankCsvReviewService.commit(...)`, which persists normalized-bank-CSV review facts directly rather than delegating to `BankStatementReviewService`; and
- `BankCsvMappingProfileService.create(...)`, `replace(...)`, and `setActive(...)`, because bank statement mapping maintenance is classified as `BOOKKEEPING_WRITE` by `doc/P20-S3-runtime-authorization.md`.

These routes must receive their own guarded service-boundary tranche before consolidated production registry injection. They must not be treated as protected merely because this tranche guards OFX/QFX, mapped-CSV delegated review, generic review-batch creation, and reviewed-row ledger acceptance.

## Required regression coverage

Direct H2 integration coverage must prove for each guarded service-owned boundary that:

- VIEWER denial occurs before ordinary argument/commit validation and leaves requested business state unchanged;
- ACCOUNTANT, MANAGER, ADMIN, and a non-ADMIN union containing ACCOUNTANT receive bookkeeping-write authority;
- authorization consumes the current session on every call rather than caching permission state;
- an absent session and a session bound to another company fail closed;
- denial produces durable `AUTHORIZATION_DENIED` security facts;
- preview/read operations remain usable without a bookkeeping-write decision where the service exposes a preview; and
- successful guarded calls retain the existing idempotency, acceptance, ownership, duplicate, accounting, and rollback semantics covered by the established banking suites.

There are no schema, migration, or JavaFX layout/control changes in this tranche.
