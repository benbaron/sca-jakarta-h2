# Bank import and reconciliation

## P05 import persistence boundary

Import review staging remains in memory until the user accepts, rejects, matches, or completes a reviewed row. The application must keep valid and invalid staged rows together during review so the user can correct individual rows without losing the rest of the source file.

P05-S1 adds durable H2 tables for reviewed import facts, not a second ledger. `bank_import_batch` records the source format, source identity, status, counts, and reviewer metadata for a batch. `bank_statement_line` records each durable reviewed statement line with source row identity, stable source transaction ID when available, deterministic fingerprint fallback, transaction date, optional posted date, amount, descriptive fields, and final disposition. `import_issue` records durable validation or review issues at batch or row level.

Accepted accounting transactions still enter the canonical `txn`/`txn_split` ledger only through `TransactionEntryService` or later documented command services. Bank statement lines may link to accepted or matched canonical transactions after review, but they do not calculate balances, replace ledger splits, or store raw source documents as accounting truth.

P05-S2 owns the Banking configuration panel. P05-S3 owns parser normalization, duplicate detection, deterministic fingerprint generation, row-level warnings/errors, and the first durable review service boundary that converts an in-memory staged statement into H2 `bank_import_batch`, `bank_statement_line`, and `import_issue` facts without creating ledger transactions. Later P05 acceptance UI work owns converting reviewed rows into canonical accepted transactions. P05-S4 owns cleared-state mapping and SCLX idempotency/non-posting annotation handling.


## P05-S2 normalization and duplicate detection

`BankImportNormalizationService` converts extracted bank rows into immutable normalized statement-line projections before any durable write. It normalizes stable source IDs, parses OFX/QFX-style posted dates into `LocalDate`, keeps transaction and posted dates separate in the projection, and calculates a deterministic SHA-256 fingerprint from date, amount, transaction type, name, and memo when a stable source transaction ID is absent.

Exact duplicates are row-level errors. They are detected by normalized source transaction ID when present, or by deterministic fingerprint fallback when the source ID is blank, and are compared against both the current in-memory review batch and known persisted identities supplied by the caller. Probable duplicates remain row-level warnings based on caller-supplied date/amount/payee/memo candidates so later review workflow can keep the row visible for user disposition. Invalid dates and zero or missing amounts are row-level errors and do not discard neighboring rows.


## P05-S3 review persistence

`BankImportReviewService` persists one normalized bank statement review batch in a single resource-local transaction. It builds duplicate context from existing durable statement-line source IDs and deterministic fingerprints for the active company, normalizes the incoming rows, creates one `bank_import_batch`, creates one `bank_statement_line` per staged row, and creates one `import_issue` per row warning/error.

Invalid and duplicate rows remain durable review facts rather than being discarded: rows with validation errors are stored with status `ERROR`, exact duplicates are stored with status `DUPLICATE`, and warning-only rows remain `IMPORTED` with durable warning issues. The service does not write `txn` or `txn_split`; accepted accounting activity remains owned by canonical transaction services after an explicit later user acceptance action.


## P05-S4 cleared-state mapping

`BankClearedStateService` maps a reviewed `bank_statement_line` to the canonical `txn_split` line for the configured bank account. The service verifies that the statement line references a configured bank account and that the target split uses that configured account's chart-of-accounts bank ledger account.

A confirmed match stores cleared state on `txn_split` (`bank_cleared`, `bank_cleared_on`, and the matched statement-line reference) and marks the imported statement line as `MATCHED` with its matched canonical transaction. This keeps the ledger split as the authoritative cleared-state location while preserving the reviewed statement fact as match evidence.

## P15-S6-C1 strict external-statement preview

`BankStatementParser` is the production non-mutating OFX/QFX preview authority. It uses decoded content
and governed envelope structure rather than trusting a filename, supports the frozen OFX 2.x XML,
QFX 2.x XML, and QFX 1.x SGML fixture families, and preserves statement account, currency, date,
balance, transaction, check/reference, and correction facts in immutable DTOs.

The parser runs before `BankImportNormalizationService` or `BankImportReviewService`. Blocking parser
failure therefore leaves H2 untouched. P15-S6-C1 does not change the durable review transaction or
create canonical ledger activity; later S6 slices will map the richer parser projection into the
existing normalized-review boundary and retire temporary session staging only after every production
consumer is rewired.

## P15-S6-C2 configured account and durable OFX/QFX review

`BankStatementReviewService` is the strict OFX/QFX preview-to-review authority. A preview is bound to
the absolute file, SHA-256, active company, selected active configured bank account, and parsed content.
Commit repeats every one of those reads and rejects stale files or retargeted company/account state.
Full source bank/account/type/currency conflicts remain blocking. A suffix-only account match requires
an explicit confirmation and is recorded on the durable batch.

The write retains statement-level variant, version, encoding, account identity, dates, and balances,
plus row-level currency, check/reference, and correction facts. Duplicate identities are compared only
within the selected company and configured account. An identical file/format/target is a no-op returning
the existing batch. A new batch, its lines and issues, and one factual operation audit share one
resource-local transaction. The service never creates a canonical ledger transaction.

## P15-S6-C3 mapped CSV profiles and durable review

`BankCsvMappingProfileService` owns reusable mapping profiles in H2. Every profile belongs to exactly
one company and configured bank account, is limited by the 1,000-profile company cap, and stores only
validated versioned mapping JSON plus searchable format metadata. Saving, replacing, activating, and
deactivating a profile are explicit configuration operations; raw financial rows and credentials are
never stored in a profile.

`BankCsvParser` maps explicit signed-amount or credit-minus-debit asset-account layouts into
`BankStatementDocument`. It retains original logical rows for preview, requires one source account and
currency, applies only declared date/encoding/decimal/grouping rules, and blocks malformed quoting,
duplicate headers, missing mappings, mixed identities, and resource-limit violations before mutation.

`BankCsvReviewService` binds the selected profile revision to the exact source/company/account preview
and then delegates commit to `BankStatementReviewService`. CSV therefore shares configured-account
validation, account confirmation, scoped duplicate detection, idempotent reimport, complete rollback,
durable operation audit, and the prohibition on implicit canonical ledger creation.

## P15-S6-C4 production durable-review workspace

Import Preview is the production write route for OFX/QFX and mapped bank CSV. It captures the active
company, selected active configured account, source hash, parsed facts, and—when CSV is used—the selected
durable profile revision. The user sees normalized rows, original CSV logical rows, duplicate status,
account-match status, and every blocking/warning message before an audit actor and an explicit exact-scope
confirmation can authorize the atomic durable-review write. A suffix-only account match requires a
separate visible confirmation. Any scope or source drift is rejected and requires a new preview.

`BankReviewQueryService` is the read-only desktop authority for persisted review batches and statement
lines. Banking reports company-scoped durable counts and navigates to the import and review workspaces.
Bank Transactions shows persisted source/account/date/amount/currency/status and match facts, survives
restart, and isolates active companies. It opens the canonical ledger only when the row has an explicit
matched transaction. The former `UiWorkspaceDataStore.bankTransactions` session staging authority and
the File-menu direct-import path are removed. Import alone still creates no `Txn` or `TxnSplit`.


## P16-S4 single production bank-import authority

P16-S4 removes the remaining reconciliation-local CSV/OFX/QIF parsing and imported-file persistence path. `ReconciliationRunsPanel` exposes one **Import Bank Statement…** navigation command instead of pasted/file parsers, and `BankReconciliationWorkspaceService` no longer creates imported `BankImportBatch` or `BankStatementLine` records.

A reconciliation-origin import passes the exact reconciliation session and configured bank-account ID to Import Preview. Import Preview re-resolves that configured account from the active company, locks the selector for the operation, and then uses the same `BankStatementReviewService`, `BankCsvReviewService`, or `NormalizedBankCsvReviewService` preview/commit path used elsewhere. Parsing and preview remain off the JavaFX thread through `InterchangeTaskController`; commit remains atomic and non-cancelable once begun.

After a successful durable review commit, Import Preview returns to the originating reconciliation session. Reconciliation reloads its persisted snapshot and therefore observes the newly committed review facts from H2 rather than copying transient preview rows. Parser rejection, unsafe input, account mismatch, duplicate classification, source drift, and idempotent re-import semantics are consequently identical regardless of whether the user reached Import Preview from Banking or Reconciliation.

Manual reconciliation statement entry is intentionally separate from external import and is persisted through `BankStatementManualEntryService` inside the reconciliation transaction. QIF is not exposed as a production action because it is not part of the governed bank-statement interchange contract.
### Explicit reviewed-row ledger acceptance (P16-S8)

Import remains durable review only. In **Bank Transactions**, one unmatched eligible `IMPORTED` row may be explicitly converted into a canonical transaction through **Create Transaction from Reviewed Row…**. The dialog freezes the source identity and configured bank account, pre-fills the bank split, and requires a balanced canonical transaction before commit. `ReviewedStatementAcceptanceService` revalidates the row and protections immediately before one atomic caller-owned transaction creates the ledger transaction, sets the existing `accepted_txn_id` relationship and `ACCEPTED` state, updates batch disposition, and writes factual audit history. Probable duplicates require explicit confirmation; exact duplicates, matched rows, closed periods, finalized reconciliation ranges, stale company/account scope, and late failures do not partially post. Acceptance alone does not clear or reconcile the transaction.

## P20-S3 bank import review and acceptance authorization

`BankStatementReviewService.commit(...)`, `BankImportReviewService.createReviewBatch(...)`, and `ReviewedStatementAcceptanceService.accept(...)` now enforce `ApplicationPermission.BOOKKEEPING_WRITE` at their service-owned mutation boundaries before ordinary commit validation or durable write work. ADMIN, MANAGER, and ACCOUNTANT therefore retain these bookkeeping operations while VIEWER, absent sessions, and wrong-company sessions fail closed with factual `AUTHORIZATION_DENIED` security history.

Strict OFX/QFX preview and reviewed-row acceptance preview remain non-mutating. Mapped CSV continues to delegate its durable commit through `BankStatementReviewService`; the later production registry-wiring tranche must supply the current-session guard to that delegated service. `BankImportReviewService.importForInterchange(...)` remains a caller-owned SCLX restoration seam and is intentionally not independently guarded because the outer SCLX commit owns authorization and atomicity.

Authorization does not weaken the existing source-hash, configured-account identity, duplicate, idempotency, acceptance, closed-period, finalized-reconciliation, balanced-transaction, or rollback rules. Existing actor strings also remain compatibility inputs until the separate authenticated-audit-actor tranche.

Current-source inspection also confirms two bank write routes still requiring a later P20-S3 service-boundary correction: direct `NormalizedBankCsvReviewService.commit(...)`, and `BankCsvMappingProfileService` create/replace/active-state mutations. The runtime authorization plan must not treat those routes as protected until their own guards are implemented and tested.
