# P15-S2 implementation notes

P15-S2 is delivered in two reviewable pull-request slices:

- PR #195 introduced the whole-database transfer service and focused service tests.
- PR #196 integrates that service into the production File menu and Administration workspace, corrects the H2 archive-name restore defect found during end-to-end validation, and adds JavaFX route tests.

## Service authority

`DatabaseTransferService` is the single authority for whole-database transfer:

- backup uses H2 `BACKUP TO` through a live JDBC connection instead of copying an open `.mv.db` file;
- restore is permitted only into a new explicit target;
- the active database and an existing target are never overwritten;
- restored data is isolated in a temporary workspace, migrated with `DatabaseMigrationService`, opened through `Jpa`, and counted before final placement;
- backup and restore results include exact paths, SHA-256, byte/count evidence, and timestamps;
- switching is a separate explicit action available only for a validated restore result;
- a process-wide lock rejects overlapping database-transfer operations.

H2 restores an archive under the database filename stored inside the archive, not under the requested destination basename. The validated implementation therefore restores into an isolated directory, requires exactly one `.mv.db` payload, renames that payload to the requested temporary target, and only then migrates and validates it. This behavior is covered by the real H2 round-trip test.

## Production UI composition

The production UI exposes one shared `DatabaseTransferActions` instance through:

- **File > Backup Database…**;
- **File > Restore Database Copy…**;
- **File > Switch to Validated Copy**; and
- **Administration > Database Transfer**.

`DatabaseTransferCoordinator` owns file selection, path confirmation, progress state, result dialogs, and the guarded switch offer. Backup and restore execute on a daemon worker thread so the JavaFX application thread remains responsive. Both surfaces bind to the same busy state and last validated result; no generic job queue or second database controller is introduced.

The switch path delegates to the production workspace's existing database connection flow. That flow uses `DatabaseSessionController`, restores authoritative company selection, refreshes open panels, updates the active-database display, and returns to Dashboard or recovery as appropriate.

## Safety behavior

- Every confirmation displays the exact active, backup, and target paths relevant to the operation.
- Restore never changes the active database automatically.
- A failed restore or validation removes its temporary workspace and leaves the active database unchanged.
- The Switch command remains disabled until a restore has completed validation.
- Backup and restore destinations must not already exist.
- Database transfer remains visibly and structurally separate from SCLX, Chart of Accounts JSON, and bank-statement exchange.

## Automated validation

Focused validation covers:

- real H2 backup/restore/migrate/validate/switch round trip;
- active-target, source-equals-target, existing-target, and corrupt-input guards;
- Administration path/result display and action routing;
- File-menu route installation, idempotence, busy-state disablement, and guarded-switch enablement;
- production panel route and core editor compliance under Xvfb.

Owner desktop acceptance steps are maintained in `doc/P15-S2-user-testing.md`.
