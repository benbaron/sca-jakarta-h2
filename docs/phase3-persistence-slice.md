# Phase 3 - Persistence-First Slice

This slice introduces concrete persistence infrastructure for append-only journaling and open-item projections.

## Added database migration

- `V4__journal_open_item_core.sql`
  - `journal_transaction`
  - `journal_posting_line`
  - `open_item_snapshot`
  - `open_item_transition`

Design intent:

- Journal remains immutable and append-only.
- Open-item state is stored as current snapshot + transition history.
- Group scoping is explicit for multi-branch data.

## Added repositories

- `JournalTransactionRepository` + `JdbcJournalTransactionRepository`
  - append transaction
  - find by id
  - query by group/date range
- `OpenItemSnapshotRepository` + `JdbcOpenItemSnapshotRepository`
  - create snapshot
  - apply state transition (history + snapshot update in one transaction)
  - find by id
  - query by group/kind

## Added test scaffolding and repository tests

- `RepositoryIntegrationSupport`
- `TestDataSource`
- `JdbcJournalTransactionRepositoryTest`
- `JdbcOpenItemSnapshotRepositoryTest`

The tests are deterministic and use isolated H2 in-memory databases with Flyway migrations per test.
