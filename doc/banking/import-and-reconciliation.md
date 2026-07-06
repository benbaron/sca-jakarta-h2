# Bank import and reconciliation

## P05 import persistence boundary

Import review staging remains in memory until the user accepts, rejects, matches, or completes a reviewed row. The application must keep valid and invalid staged rows together during review so the user can correct individual rows without losing the rest of the source file.

P05-S1 adds durable H2 tables for reviewed import facts, not a second ledger. `bank_import_batch` records the source format, source identity, status, counts, and reviewer metadata for a batch. `bank_statement_line` records each durable reviewed statement line with source row identity, stable source transaction ID when available, deterministic fingerprint fallback, transaction date, optional posted date, amount, descriptive fields, and final disposition. `import_issue` records durable validation or review issues at batch or row level.

Accepted accounting transactions still enter the canonical `txn`/`txn_split` ledger only through `TransactionEntryService` or later documented command services. Bank statement lines may link to accepted or matched canonical transactions after review, but they do not calculate balances, replace ledger splits, or store raw source documents as accounting truth.

P05-S2 owns parser normalization, duplicate detection, deterministic fingerprint generation, and row-level warnings/errors. P05-S3 owns the review UI/service workflow that converts in-memory staged rows into durable batch/line/issue facts and canonical accepted transactions. P05-S4 owns SCLX idempotency and non-posting annotation handling.
