# Whole-database transfer contract

## 1. Purpose and authority

Whole-database transfer preserves an entire SCA Bookkeeping H2 database: every company, canonical and compatibility table intentionally retained by the current schema, administration records, migration history, preferences, bank review facts, assets, inventory, period-close facts, and audit history.

It is not a selected-company SCLX export, a Chart of Accounts JSON file, or bank-statement interchange.

The active database remains authoritative until a restored copy has been migrated, validated, and explicitly selected through the existing database-session boundary.

## 2. Supported backup mechanism

A consistent backup MUST use an H2-supported database facility through a live connection, principally `BACKUP TO` for a binary H2 backup compatible with the governed H2 version. Copying an open `.mv.db` file is prohibited.

A future text recovery mode may use H2 `SCRIPT TO` and `RUNSCRIPT FROM`, but it is a distinct explicitly labeled recovery option and MUST satisfy the same validation and path protections. Generic ZIP creation is not a substitute for H2 backup semantics.

The operation records:

- source database path in normalized redacted form;
- H2 and application versions;
- Flyway schema version;
- operation start/end instants;
- byte count and SHA-256 of the final backup;
- database/table/company counts used for validation; and
- warnings and exclusions.

It MUST NOT record a database password.

## 3. Consistency and exclusive access

Backup coordinates with the existing `DatabaseSessionController` and persistence composition. It MUST obtain exclusive application-level backup authority so no application writer can commit during the consistency boundary. The operation may use H2's supported online backup behavior, but the application still blocks company/session switching and conflicting recovery operations until completion.

Restore, repair, or overwrite operations require exclusive access with all EntityManagers, pools, and other database connections to the target closed. The operation MUST fail visibly when exclusive access cannot be proven.

## 4. Restore target and overwrite policy

Restore/import defaults to a new, explicit target path chosen by the user. The target path MUST be different from:

- the active database path;
- the source backup path;
- any open database path;
- the application installation or resource directory; and
- any path reached through a symlink or traversal outside the confirmed target parent.

An existing target file or H2 file family is never overwritten by default. Overwrite, when later supported for a non-active closed target, requires:

1. explicit path display and confirmation;
2. an independently completed backup of the target;
3. verified SHA-256 and readable backup metadata;
4. exclusive access; and
5. rollback/restore instructions.

The active database MUST NOT be overwritten in place.

## 5. Restore, migration, validation, and switch

Restore into a new path follows this order:

1. validate backup container, size, checksum, and supported H2 metadata;
2. create a temporary target in the confirmed target directory;
3. restore using H2-supported facilities;
4. open only the restored copy;
5. run `DatabaseMigrationService` and existing Flyway recovery behavior;
6. validate Hibernate schema and application composition;
7. run diagnostics and consistency checks;
8. compare expected table, company, transaction, and key authority counts;
9. close the restored database cleanly;
10. atomically place the validated database family at the final new target; and
11. offer, but do not silently perform, a guarded switch through `DatabaseSessionController`.

The selected database path changes only after successful service composition. A failed connection or validation MUST NOT replace it.

## 6. Validation requirements

At minimum, validation MUST confirm:

- the H2 backup can be opened with the supported H2 version;
- Flyway history is internally consistent or can be nondestructively recovered by current services;
- all current migrations complete;
- Hibernate validates the migrated schema;
- required canonical tables and constraints exist;
- company rows and active-company invariants are valid;
- no obvious foreign-key or referential-integrity violation exists;
- canonical transaction split totals and critical migration invariants pass focused diagnostics; and
- the restored path can be reopened after clean shutdown.

Warnings do not permit switching when they affect schema, identity, canonical ledger integrity, or company selection.

## 7. Failure behavior

Corrupt, truncated, encrypted-with-unsupported-means, wrong-format, or unsupported-version input is rejected before target placement when possible. Any failure after temporary target creation MUST:

- close all target connections;
- leave the active database and selected path unchanged;
- remove or quarantine the incomplete temporary target;
- preserve an existing final target unchanged;
- report the exact failed stage and safe paths; and
- avoid logging secrets or raw financial content.

The application MUST NOT attempt automatic repair of the active database as part of import. Recovery remains an explicit operation using `FlywaySchemaRecoveryService`, `DatabaseMigrationService`, diagnostics, and the typed database recovery commands.

## 8. Atomic file behavior

The final backup is written to a temporary file in the destination directory, flushed, hashed, and moved into place atomically where supported. Existing output requires explicit overwrite confirmation. Partial backup files use a recognizable temporary suffix and are removed after failure.

A restored H2 database is a file family, not a single arbitrary stream. Placement MUST account for all H2-created files and MUST occur only after the database is closed.

## 9. Security and resource limits

Default limits and protections are:

| Limit | Value |
|---|---:|
| Backup input/output size | 8 GiB |
| Archive entry count | 100,000 |
| Archive expansion ratio | 100:1 |
| Required free space | at least 2x input size plus 1 GiB |
| Normalized path length | platform limit, with application maximum 4,096 characters |

The 8 GiB limit is configurable only by an administrator before the operation; increasing it does not weaken free-space, timeout, or confirmation requirements.

Archive extraction MUST reject absolute paths, `..` traversal, drive changes, symlinks, duplicate entries, case-collision entries, and entries outside the temporary target. Input type is recognized from H2 backup structure and metadata, not filename alone.

Checksums are SHA-256. Date/time metadata uses UTC RFC 3339. Any textual manifest is strict UTF-8. The database's internal encoding is governed by H2 and is not re-encoded by transfer.

## 10. Relationship to current services

Post-login **Back Up Database**, **Restore Database Copy**, and **Switch to Validated Copy** require P20 `DATABASE_ADMIN` at the service layer. `DatabaseAdministrationService` owns that authorization decision and delegates the file/database mechanics to `DatabaseTransferService`; the persistence implementation itself does not become a permission-policy authority. The service resolves the current production authorization guard for each operation so a workspace surviving a database switch cannot retain stale authority. Database select/create/retry at the outer login gate remains the documented pre-login exception.

Implementation MUST compose with:

- `DatabaseSessionController` for selected-path and safe-switch authority;
- `DatabaseMigrationService` for current Flyway migration;
- `FlywaySchemaRecoveryService` for explicit nondestructive baseline recovery;
- current persistence/JPA composition for schema validation; and
- `DiagnosticsQueryService` and focused integrity checks for restored-copy validation.

It MUST NOT create a second database selector, a static active-company authority, a sidecar database registry, or generic Import/Export Jobs framework.

## 11. Separation from other exchange types

- A whole-database backup contains all companies and application records and can include authentication and UI preference tables because it is the database itself.
- SCLX deliberately excludes authentication, UI state, schema internals, and other companies.
- Chart of Accounts JSON contains only chart definitions.
- OFX/QFX/CSV contain bank statement activity and never an H2 schema or canonical ledger.

The UI MUST use the terms **Back Up Database**, **Restore Database Copy**, and **Switch Database**. It MUST NOT label these actions as SCLX, COA import, or bank import.
