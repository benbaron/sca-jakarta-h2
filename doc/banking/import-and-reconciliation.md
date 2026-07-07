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
