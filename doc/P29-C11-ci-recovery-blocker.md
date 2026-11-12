# C11 CI blocker: H2 online backup integrity

Historical investigation below: #423 is now merged at
`ec4d358f4568dbf6ee689fe3356f0ad0a7817400`. Its final accepted head
`4257f255f1470a280434915bd246c93e38b7a20d` subsequently passed workflow
`43160850963`. That intermittent pass does not repair the standalone reproduction.
[P27-C2](P27-C2-online-backup-integrity.md) now owns the independent correction.
No production code changed during this original investigation. [PLAN](PLAN.md)
records the current selected work.

## Actual CI failure

[Run 43132731784](https://github.com/benbaron/sca-jakarta-h2/actions/runs/43132731784)
passed clean headless verification. Its subsequent plain `mvn test` failed:
1,197 tests, zero assertion failures, one error, 141 skips. Windows presentation
passed; subsequent Linux display steps, including Journal Save, were skipped.
The failed-test-reports artifact is `13906018684`.

`AccountingReadinessTest.canonicalSnapshotFindingsMatchPacketAndRevisionsTrackAssessedContentWithoutWrites:73`
fails through CoreAccountingPacketService.capture ->
DatabaseTransferService.restoreCopy:138 -> DatabaseMigrationService.migrate ->
FlywaySchemaRecoveryService.prepareBaselineForUntrackedSchema:61.
The last location is DriverManager.getConnection, before history inspection.
Its SQLException is H2 error 50000-252, caused by:

```text
org.h2.mvstore.MVStoreException: Chunk 8 not found [2.5.252/9]
FileStore.getChunk -> readPage -> MVStore.openMap ->
TransactionStore.openMap -> LobStorageMap.openLobMap -> Database.<init>
```

This is the first opening of an extracted private native backup, before the
restore's migration/JPA validation or SHUTDOWN COMPACT. It is not evidence of
bad Flyway history or a readiness assertion mismatch. Changing recovery baseline
rules would not repair the missing physical chunk.

## Local investigation

Unchanged baseline compile passed (12.9 seconds). Existing AccountingReadinessTest,
DatabaseTransferServiceTest and NativeDatabaseRecoveryTest passed: ten tests,
zero failures/errors/skips (54.5 seconds). Thirty additional executions of the exact failing readiness method (210 native
snapshot captures) passed in one JVM. Passing intermittent repetitions is not
proof of repair. The baseline logs are `/tmp/c11-recovery-baseline-compile.log`
and `/tmp/c11-recovery-baseline-tests.log`; repetition is recorded in
`/tmp/c11-readiness-repeat.log`. No full-suite success is inferred from these
focused runs; the failed GitHub gate remains authoritative.

The standalone [H2OnlineBackupProbe.java](../src/test/resources/fixtures/recovery/H2OnlineBackupProbe.java)
uses only H2 JDBC, one five-row CLOB table, native BACKUP TO and Restore.execute
to a fresh directory. It accelerates chunk reclamation with RETENTION_TIME=0
and WRITE_DELAY=1; those are diagnostic options, not application settings.
It reproduces the same open-time missing-chunk/LobStorageMap exception without
application classes, schema migrations, JPA or JavaFX. This isolates an engine
backup problem with the same signature; it does not establish which upstream
code defect caused the CI image, which was deleted by normal snapshot cleanup.

The retained probe adds source and restored content assertions. Its final run
failed at generation 4: source had five committed rows / total CLOB length
25,010, whereas the copy reopened with four rows / length 20,006. The log is
`/tmp/c11-retained-probe-final.log`. Checking only whether a backup opens would
therefore miss another observed integrity failure.

The first experiment failed after generation 500 with Chunk 1006 not found.
The source still reopened with five rows and total CLOB length 27,945, whereas
the restored archive failed. A separate CHECKPOINT SYNC-before-each-backup
experiment failed after generation 1800 with Chunk 3677 not found. Do not adopt
checkpointing as a proven fix. An experiment nesting BACKUP under MVStore's
internal file-operation lock blocked and was terminated; no internal-lock
workaround is retained. Logs and failed synthetic databases are local `/tmp`
diagnostics, not frozen user or legacy recovery fixtures.

Run the retained probe in a disposable directory with the project's H2 jar:

```sh
java -cp /path/to/h2-2.5.252.jar src/test/resources/fixtures/recovery/H2OnlineBackupProbe.java
java -cp /path/to/h2-2.5.252.jar src/test/resources/fixtures/recovery/H2OnlineBackupProbe.java checkpoint
```

It runs up to 2,000 evolving snapshots, exits on the first error or content
mismatch and retains that synthetic source/archive/copy. Timing is variable;
a successful invocation is not an exhaustive storage integrity guarantee.

## Required recovery correction

AGENTS §3.3.6 requires a substantial independent prerequisite to block the
selected slice and stop. This investigation therefore blocks C11 rather than
folding a speculative storage-engine repair into a Journal feedback change.
Start the independent correction from current main; read
[the accepted recovery integrity contract](P27-S4D3R-recovery-integrity.md), the
native transfer/recovery tests, frozen legacy fixture provenance and H2 backup
source. Preserve the Journal branch and its UI regression tests.

Establish a reproducible native backup failure before selecting a supported
engine correction. Retain whole-database snapshot semantics, all-row/column/
trigger comparison, old-engine upgrade/index tests, reopen checks and source
isolation. Do not substitute SQL/SCLX export, silently retry a corrupt snapshot,
drop migration guards, remove repeated CI checks, regenerate frozen fixtures
or disable readiness assertions. No production dependency or database setting
has been changed here.

Acceptance requires repeated native integrity and readiness tests, migration
upgrade tests, clean verify followed by the repeated test gate, and exact-head
GitHub results. Then incorporate the accepted prerequisite into C11 and rerun
its display tests. Owner Journal checks remain pending; no merge is authorized.
