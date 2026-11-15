# P27-C5 native backup integrity correction

The owner requests repair of the reproduced native-backup failure blocking C4. CI run 43373336055 restored only rows 0–2 after row 3 had committed, at generation 3 / first opening. Preserve this evidence and a locally failing native source/archive before cleanup. Initial passing runs are not a repair.

Inspect the existing DatabaseTransferService, OnlineBackupIntegrityTest and retained H2OnlineBackupProbe; C2 engine/configuration and legacy fixture provenance; DatabaseMigrationService/Jpa and complete native transfer/recovery/readiness tests; and exact H2 2.4.240 BackupCommand/MVStore/FileStore sources. Existing production architecture is authoritative; this is an engine-boundary correction, not a donor import or second persistence model.

Keep a native complete-database archive, atomic destination publication, restore into an isolated new copy, canonical data, every complete-value oracle, repeated opening, compact/restart, source-preservation, trace and migration assertion. Do not patch an archive's data or headers, reconstruct rows, substitute SQL/SCLX, close live user transactions, weaken tests, or retry until green. If native SQL BACKUP cannot provide a consistent boundary, document and prove a deliberately selected native API alternative before retaining it. No internal-lock callback may call SQL BACKUP recursively: its flush can wait on workers that the callback itself prevents from running.

Acceptance requires a reproduced pre-fix failure, retained diagnostic artifacts, focused tests including concurrent committed writes, the unchanged 64-generation regression, longer evolving native snapshot checks, old-image and full recovery compatibility, full clean/repeat verification and exact-head GitHub checks. No engine dependency change is assumed. Record actual results and user testing in PLAN.

## Selected boundary for verification

Temurin 17.0.17 reproduces the original test at generation 3 / opening 0 on its first run. The unmodified source/archive and failure are retained under `/workspace/p27-c5-reproduction/exact-15630757620071350963`; hashes are recorded in `/workspace/p27-c5-diagnostics/reproduced-native-files.json`. The archive reopens with three rows on both Java 17 and 21; the closed source contains four. Its latest native chunk contains a three-row root, so this is not merely a display or CLOB comparison error. Fifty original-test runs on Java 21 passed, demonstrating why CI's runtime must be exercised too.

Verify an embedded-H2 adapter that commits pending store changes, then uses public `MVStore.executeFilestoreOperation` to drain serialization and buffer-save work and retain the storage lock while H2's own `FileStore.backup` writes the ZIP. Unlike recursive SQL BACKUP, the callback performs no SQL or new store flush. It uses exactly the engine's native archive writer, with no data/header rewriting or engine dependency change. JDBC transactions stay open; physical writes may wait while the native snapshot is copied. Capture archive-output IOExceptions inside the callback and rethrow afterward, because letting application output failures escape the callback would invoke MVStore's panic handler against the live database. Connection ownership and existing atomic publication/restore validation remain unchanged.

The adapter explicitly retains SQL BACKUP's H2 administrator-rights check before archive creation. Application authorization remains in its existing facade. A non-admin regression requires denial before any output bytes, while preserving that connection and the source data. Final review added this guard before acceptance; the first candidate's passing full run is superseded by the guarded implementation and new regression.

Earlier broad scratch probes were interrupted by an unbounded synthetic stress writer exhausting the temporary volume. They are not acceptance evidence. Only that oversized synthetic database was removed after preserving logs/header; subsequent workloads are bounded.

## User-visible behavior and owner check

Backup still creates the same native ZIP and Restore still validates an isolated new database copy before optional switching. H2/Flyway versions, schema, permissions, source data and existing archives are unchanged. Existing stale archives are not reconstructed by this correction. Physical persistence can wait during the native copy; current transactions are not closed or rolled back.

On a disposable database copy, create and save entries, back up while the application remains open, restore to a different path, and check the newest entries plus historical data after restart. Keep the original database and prior application available. This focused recovery check does not reopen the separately deferred native acceptance list. Actual focused/full/repeated/CI results are recorded in PLAN.
