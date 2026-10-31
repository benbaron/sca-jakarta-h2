# P27-S4D3R — Recovery integrity prerequisite

[PLAN](PLAN.md) owns status. The S4D3 combined gate exposed an independent storage-engine prerequisite; S4D3 is blocked and its unmerged fidelity fixes/fixtures are preserved on `codex/P27-S4D3-integrated-portability`. Work here starts from current main after #398, without replaying those production fixes.

## Reproduced failure

The populated synthetic source has two companies, 33 canonical transactions and 73 splits. With H2 2.2.224, native BACKUP produces an archive containing those facts, but repeated same-process restoration/validation can publish an earlier image with one company, one transaction and two splits. Complete-row comparison detects loss of security/preferences, operations, audit and close history too. Readiness snapshot restoration can also cause a factual review to appear stale without intervening source edits.

The failure reproduces outside JUnit after the same real review snapshots. Opening the restored image initially reports all 33 transactions; holding a JDBC connection across validation retains them until close, and reopening can show the earlier state. A diagnostic explicit compact shutdown raised an H2 MVStore `getKeyType()` null-map/page-read exception. A source/target checkpoint did not fix the failure; none of these unproven workarounds is retained in production.

A classpath-only diagnostic using H2 2.5.252 retained both companies, all 33 transactions and all 73 splits through the same native restore. This is candidate evidence, not full upgrade acceptance. H2's 2.3.230 notes include #2752 (backup-open double-mark fix), 2.4.240 includes #4247 (compaction missing chunks/data loss), and 2.5.252 includes #4387 (reopen after shutdown corruption). Do not claim one upstream issue is the definitive cause without a minimized reproduction.

## Coherent scope

- Upgrade H2 to a maintained version that passes the reproduced native recovery gate. Use a Flyway version that explicitly tests that H2 version while retaining Java 17 compatibility; inspect published metadata before choosing.
- Keep H2, canonical JPA, nondestructive Flyway migrations and native restore-to-new-copy semantics. No SQL/SCLX substitute for whole-database backup, silent repair, dropped guards, disabled tests or active-database overwrite.
- Retain a complete synthetic populated recovery regression with real review/close/reopen snapshots, original/reversed transfers, banking, inventory/assets, supplemental/merchant facts, security/preferences/aliases and a second company. Compare every persisted row/column and trigger, native retries, period protection and subsequent identity allocation across restart.
- Verify an actual old-engine/old-Flyway synthetic backup upgrades without losing any original public-table fields, source identities, audit payloads, credentials or guards. Explain any nondestructive internal metadata extension explicitly. Retain a reproducible fixture-generation record and hashes; use no user data.
- Inspect existing migration/recovery tests and any dependency/API compatibility failures. Run baseline compile/affected tests before edits, focused regression, full clean verification, exact production JavaFX and exact-head GitHub CI.

No production UI is added. Consult the UI matrix/design/editor rules and retain existing destination, role and switch protections. The user should verify backup/restore on a disposable copy, restart, inspect current records/history/security, and confirm the active file is not switched automatically. No human result is inferred.

## Handoff after merge

After this prerequisite is accepted and merged, resume S4D3 on a fresh main-based branch. Reapply only its intentional fidelity/report/test changes; rewrite PLAN against then-current main rather than replaying stale plan records. Run its full combined gate with the accepted storage stack. P27/S4/P28 remain incomplete/gated meanwhile. P27-C1 WARN_ONLY matching remains a separate recorded follow-up.
