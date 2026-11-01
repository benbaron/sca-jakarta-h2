# P27-S4D3R — Recovery integrity prerequisite

[PLAN](PLAN.md) owns status. The S4D3 combined gate exposed an independent storage-engine prerequisite; S4D3 is blocked and its unmerged fidelity fixes/fixtures are preserved on `codex/P27-S4D3-integrated-portability`. Work here starts from current main after #398, without replaying those production fixes.

## Reproduced failure

The populated synthetic source has two companies, 33 canonical transactions and 73 splits. With H2 2.2.224, native BACKUP produces an archive containing those facts, but repeated same-process restoration/validation can publish an earlier image with one company, one transaction and two splits. Complete-row comparison detects loss of security/preferences, operations, audit and close history too. Readiness snapshot restoration can also cause a factual review to appear stale without intervening source edits.

The failure reproduces outside JUnit after the same real review snapshots. Opening the restored image initially reports all 33 transactions; holding a JDBC connection across validation retains them until close, and reopening can show the earlier state. A diagnostic explicit compact shutdown raised an H2 MVStore `getKeyType()` null-map/page-read exception. A source/target checkpoint did not fix the failure; none of these unproven workarounds is retained in production.

A classpath-only diagnostic using H2 2.5.252 retained both companies, all 33 transactions and all 73 splits through the same native restore. This is candidate evidence, not full upgrade acceptance. H2's 2.3.230 notes include #2752 (backup-open double-mark fix), 2.4.240 includes #4247 (compaction missing chunks/data loss), and 2.5.252 includes #4387 (reopen after shutdown corruption). Do not claim one upstream issue is the definitive cause without a minimized reproduction.

## Selected runtime pair and baseline

Use H2 **2.5.252** and Flyway **13.12.0**. Upstream Flyway 13.12's H2Database explicitly tests through 2.5.252 and its parent compiler release is Java 17. Sources: [H2 release](https://github.com/h2database/h2database/releases/tag/version-2.5.252), [Flyway H2 compatibility](https://github.com/flyway/flyway/blob/flyway-13.12.0/flyway-core/src/main/java/org/flywaydb/core/internal/database/h2/H2Database.java), [Flyway Java release](https://github.com/flyway/flyway/blob/flyway-13.12.0/pom.xml). No applied migration is edited and no source checkpoint or anchor-only workaround is retained. Isolated-copy finalization is explicitly verified below.

Fresh-main clean compile and six existing recovery/close tests passed. The new unchanged native regression was then run on H2 2.2.224/Flyway 10.17: **two tests, one failure, zero errors**; hot populated recovery returned 1 / 1 / 2 instead of 2 / 33 / 73. The genuine old-engine fixture already passes its full original-field oracle on the old stack. This establishes the red baseline before changing the two dependency properties.

## Coherent scope

- Upgrade H2 to a maintained version that passes the reproduced native recovery gate. Use a Flyway version that explicitly tests that H2 version while retaining Java 17 compatibility; inspect published metadata before choosing.
- Keep H2, canonical JPA, nondestructive Flyway migrations and native restore-to-new-copy semantics. No SQL/SCLX substitute for whole-database backup, silent repair, dropped guards, disabled tests or active-database overwrite.
- Retain a complete synthetic populated recovery regression with real review/close/reopen snapshots, original/reversed transfers, banking, inventory/assets, supplemental/merchant facts, security/preferences/aliases and a second company. Compare every persisted row/column and trigger, native retries, period protection and subsequent identity allocation across restart.
- Verify an actual old-engine/old-Flyway synthetic backup upgrades without losing any original public-table fields, source identities, audit payloads, credentials or guards. Explain any nondestructive internal metadata extension explicitly. Retain a reproducible fixture-generation record and hashes; use no user data.
- Inspect existing migration/recovery tests and any dependency/API compatibility failures. Run baseline compile/affected tests before edits, focused regression, full clean verification, exact production JavaFX and exact-head GitHub CI.

No production UI is added. Consult the UI matrix/design/editor rules and retain existing destination, role and switch protections. The user should verify backup/restore on a disposable copy, restart, inspect current records/history/security, and confirm the active file is not switched automatically. No human result is inferred.

## Finalization and executable coverage

H2 2.5.252/Flyway 13.12.0 preserves the repeated current native snapshots. The old hot fixture additionally exposed a secondary-page error after schema/primary-row validation. Finish each isolated restored copy with H2 `SHUTDOWN COMPACT`, then reopen and confirm its counts did not change before publishing the target. This is engine-native physical finalization, not reconstruction of accounting or replacement of source facts. No source file/backup is compacted or rewritten. Exceptions retain the original backup/active database and prevent publication of an invalid target.

`NativeDatabaseRecoveryTest` verifies three evolving online generations (33, 34 and 35 transactions), every public-table row/column and trigger through close/restart, write-free native transfer/close retries, closed-period rejection, subsequent generated IDs and source isolation. It separately verifies healthy offline and affected hot **actual old-engine** images: all 66 original public-table field digests/counts/columns, complete guard inventory, repeat opens, indexed account lookups, old-period protection, new writes and unchanged original archives. The existing three DatabaseTransferService and three ClosePortabilityAudit tests remain required. The nine-test focused gate passed, zero failures/errors/skips.

The [fixture provenance/generators](../src/test/resources/fixtures/recovery/README.md) explain old-engine generation, synthetic security data, both archive hashes and full-field oracles. Do not generate the legacy image with the new engine. The three new tests supplement P25/P26 coverage without declaring all-feature SCLX accepted.

Before publishing, run `mvn clean verify` and the exact production JavaFX selector, inspect intentional scope/links and frozen hashes, then verify actual published head/tree and exact-head CI. Record results in PLAN/PR. On owner review, retain the old application/database backup and test upgraded recovery on a disposable copy; inspect history/security and verify restart before choosing a validated copy. Native human acceptance remains separate.

## Handoff after merge

After this prerequisite is accepted and merged, resume S4D3 on a fresh main-based branch. Reapply only its intentional fidelity/report/test changes; rewrite PLAN against then-current main rather than replaying stale plan records. Run its full combined gate with the accepted storage stack. P27/S4/P28 remain incomplete/gated meanwhile. P27-C1 WARN_ONLY matching remains a separate recorded follow-up.

Design reference: the donor `org.nonprofitbookkeeping.ui.admin.BackupResult` is administrative backup guidance/output metadata, not a native H2 recovery protocol. Retain this repository’s DatabaseTransferService, caller boundaries and restore-to-new-copy architecture; import no donor persistence or models. UI operation/design/editor rules were consulted; no UI command or layout changes.

The final local `mvn clean verify` passed **1,102 tests, zero failures/errors, 77 expected headless display skips** on the 11 frozen source/test/fixture/build files. The exact production JavaFX selector also passed 69 tests, zero failures/errors/skips. GitHub final-head validation is separately recorded in PLAN. Compaction adds restore I/O for large databases; retain the original old-app backup and exercise upgrade on a disposable copy rather than relying on downgrade of an upgraded file.
