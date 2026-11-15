# P27-C2 native online backup integrity

Selected independently after owner-merged #423, from main
`ec4d358f4568dbf6ee689fe3356f0ad0a7817400`. [PLAN](PLAN.md) owns current status.
This corrects the [C11 storage blocker](P29-C11-ci-recovery-blocker.md), not the
already merged Journal feedback implementation.

## Evidence and selected correction

H2 2.5.252 native online archives can reopen with an older committed image or
fail with a missing chunk in LobStorageMap. The retained probe also fails using
default engine settings: generation 467 lost a committed update. Accelerated
RETENTION_TIME=0 makes the new production-backup regression fail at generation 4
(one assertion failure, zero errors) on unchanged 2.5.252. The regression compares
every complete CLOB value through two opens and checks the source remains intact.

WRITE_DELAY=0, CHECKPOINT SYNC and copying under the public native storage lock
do not repair the failure. Inspection of a failed archive finds a newer chunk
holding five rows, while reopen selects an earlier four-row image. This narrows
the issue to native storage/reopen behavior; it does not identify one definitive
upstream issue. No experimental lock, checkpoint, source setting or physical-file
rewrite is retained. Maven Central still lists 2.5.252 as the latest release.

Select the published stable **H2 2.4.240**, retaining **Flyway 13.12.0** and Java
17 compatibility. The unchanged standalone probe verified 2,000 evolving copies
on 2.4.240, and all 14 existing readiness, native transfer/recovery and migration
tests passed with the candidate dependency override. Acceptance requires the
new regressions and full gates below, not just these initial passes.

The first 2.4.240 content gate passed its assertions, but a retained trace exposed
a suppressed normal-close partial-compaction MVStoreException/FreeSpaceBitSet
assertion. It is not accepted as a clean integrity gate. A stronger regression
fails before configuration with “H2 must not conceal storage failures in its
trace.” Use H2's supported **MAX_COMPACT_TIME=0** database setting in both shared
file URLs and the default persistence-unit URL. This disables partial compaction
on ordinary last-connection close; it does not disable engine assertions,
background operation, durability or the explicit full SHUTDOWN COMPACT used to
finalize restored copies. It can leave a larger file between full compactions.
The assertion-enabled 2,000-copy diagnostic with this setting and explicit full
compaction of each restored copy passes without a storage trace.

The bounded production-backup regression now verifies content across ordinary
opens and explicit full compact/restart, checks trace files for concealed
MVStoreException failures, and retains all source/content assertions. Populated
native recovery tests also reject concealed storage failures in retained traces.
No trace-file deletion is used to accept an engine error; checks precede cleanup.

This deliberately changes the engine dependency to a prior stable release.
Do not assume arbitrary backward file compatibility: a separately frozen image
created by actual H2 2.5.252/Flyway 13.12.0 at schema V92 is required, in addition
to the existing actual 2.2.224 fixtures. No schema migration or Flyway version
changes. Native BACKUP TO, isolated restore, migration validation and SHUTDOWN
COMPACT finalization remain authoritative. No SQL/SCLX substitute, retries,
disabled tests, source data reconstruction or alternate persistence is added.

## Compatibility and acceptance

The first full candidate verification failed: 1,199 tests, two URL-contract
assertion failures, 29 errors, 141 display skips (06:16). The errors expose
2.4.240's in-memory constant-IN check expressions retaining a closed DDL session;
the file-mode startup/recovery tests pass. Other published candidates were also
tested: 2.5.250 loses a row at generation 4 and 2.3.232 loses an update at 59.
Neither replaces the selected runtime.

In-memory fixtures cannot close/reopen disk metadata as the production file
bootstrap does. `MigrationTestConnections` therefore owns real pooled migration
sessions for each test database's lifetime, then closes the pools and shuts down
the isolated in-memory databases at teardown. Affected migration tests and the
shared repository fixture use that lifecycle. The two full-schema constraint
fixtures use the same Flyway migration locations through the scoped datasource;
DatabaseMigrationRecoveryTest retains its real application-service guard tests.
All original SQL, assertions, schema versions, backfill/default/uniqueness and
rollback checks remain. No test is skipped or weakened. The exact file URL
normalization oracle now includes the intentional close-compaction setting.
The expanded migration/repository/service/native/readiness gate passes **88
tests, zero failures/errors/skips** (01:18).

`OnlineBackupIntegrityTest` exercises the production DatabaseTransferService
backup with evolving large CLOBs, full content comparison, repeated opens and
source isolation. Its small JDBC-only storage schema is not a second ledger or
accounting-policy fixture. Existing populated recovery tests remain mandatory.

`NativeDatabaseRecoveryTest.previousRuntimeFileOpensInPlaceAndRestoresEveryOriginalField`
opens the actual frozen 2.5.252 image directly through Jpa (normal application
startup) and through validated restore, twice each. It verifies all 66 public
tables, every original column/row digest and the complete trigger inventory,
then indexed lookup, closed-period protection, generated IDs, a genuine new
balanced transaction, restart and original-source/archive isolation.

The fixture ZIP SHA-256 is
`985d8e86f7d51a943c6013c81e658009d46942c9faa15cbfa6d4785dbcf14470`.
[Provenance and generation](../src/test/resources/fixtures/recovery/README.md)
retain the exact old runtime and seed commit. Existing old-engine archives and
oracles must remain byte-for-byte unchanged.

Final gates: focused native/readiness/migration tests, complete `clean verify`,
repeated `test`, and Journal Save display cases on the selected dependency;
verify the published head/tree and exact-head CI. Consulted existing recovery,
UI operation/design/editor contracts; no UI command, label or layout changes.
The donor backup guidance supplies no native storage correction; retain the
existing production transfer service and canonical models.

Reproduce the extended diagnostic with assertions enabled:

```sh
java -ea -cp /path/to/h2-2.4.240.jar src/test/resources/fixtures/recovery/H2OnlineBackupProbe.java safe-close
```

## Owner verification

Retain the original application and database backup. On a disposable database
copy, start the corrected build, check recent transactions and history, make a
native backup and restore it to another new copy. Reopen and compare those facts
before explicitly switching to that copy. This cannot reconstruct rows already
missing from an earlier archive; no owner database was inspected. Recheck Journal
Save completion on Windows. Automated display tests do not establish native
Windows visual acceptance.

## Local verification record

- Unchanged merged baseline: compile passes (12.5 seconds); 14 existing focused
  tests pass (01:12).
- New complete-content test on unchanged 2.5.252: one assertion failure at
  generation 4, zero errors (`/tmp/p27-c2-red-regression.log`).
- Stronger trace assertion on unconfigured 2.4.240: one assertion failure, zero
  errors (`/tmp/p27-c2-close-red.log`); no assertion is disabled in the correction.
- Selected runtime/settings: **16 focused tests, zero failures/errors/skips**,
  01:20 (`/tmp/p27-c2-runtime-final-focused.log`).
- Retained assertion-enabled `safe-close` probe: **2,000 evolving snapshots**,
  explicit full compact/restart of every copy, no source/copy storage traces
  (`/tmp/p27-c2-final-probe.log`). The frozen old fixture bytes are unchanged;
  new archive hash and changed-document links validate.
- Final completed `clean verify`: **1,199 tests, zero failures/errors, 141
  expected display skips**, BUILD SUCCESS, exit 0, 06:11
  (`/tmp/p27-c2-final-clean-verify.log`). An earlier session ended before a
  Maven summary; that interrupted attempt is not counted as a pass. The final
  persistent runner records its actual exit status.
- Repeated full `test`: **1,199 tests, zero failures/errors, 141 expected display
  skips**, BUILD SUCCESS, exit 0, 06:06 (`/tmp/p27-c2-repeat-tests.log`).
- Final display gate: **145 tests across 44 classes, zero failures/errors/skips**,
  BUILD SUCCESS / exit 0, 08:47 (`/tmp/p27-c2-display.log`). It includes the exact
  production route selector plus Import, Journal feedback, close and overflow
  gates, each class in an isolated JVM on Xvfb. No native Windows visual result
  is inferred.
- Exact-head GitHub checks and owner review/merge remain pending.

Publication: [draft PR #424](https://github.com/benbaron/sca-jakarta-h2/pull/424),
implementation `1b2c5e489e338045f4a5fd381577daafc9c672b0`, tree
`3d7140ed4d15c832024ebd1eacd8fbbc6cb3909c`. Native branch and connected PR
match; final-head CI and owner verification remain required. PLAN owns handoff.
