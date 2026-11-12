# P23–P28 correction-program release and upgrade notes

## Release authority

P28-S1 acceptance is merged in main `c1ba17743133d91b747c36ab74f40a6755ee0a45`, containing tested head `ca66658b342802b6f56c8e3dcceb30484cf7b236`. Exact-head workflow `41274251144`, job `124753135048`, passed clean verification, repeated tests and production JavaFX compliance. The final P28-S2 published and merged commit will supersede this candidate as release authority and must be recorded in PLAN after merge.

## Delivered corrections

| Finding | Closure |
|---|---|
| G1 reversal actuals | Dated reversal/replacement budget and financial-report results agree across cutoffs. |
| G2 historical open items | Six schedule kinds preserve as-of balances through settlement, reversal and replacement. |
| G3 incomplete schedules | New allocations require completeness, excess is rejected, and acknowledged legacy gaps remain visible until repaired. |
| G4 lifecycle portability | SCLX retains supported identities, corrections, operational history and reports; native recovery retains the complete database. |
| G5 submission/readiness | Generic packet, readiness preview, evidence review and protected close expose omissions and differences. |
| G6 inventory event costs | Inventory cost-out retains Fund/Event/Budget attribution and reverses quantity/accounting together. |
| G7 fund transfers | Internal allocations use one atomic balanced ledger/operational fact with reversal and retry protection. |
| G8 lookup maintenance | Categories, counterparties and merchants have production maintenance routes with stable history. |
| G9 check/reference lifecycle | Structured references, delivery/cancel/reissue and reconciliation protection replace memo-only tracking. |
| G10 NMR/functions | Explicit NMR item/recipient/settlement/merchant facts and AR/OA/FR classifications report and round-trip without default rates. |
| G11 acquisition/custody | Asset acquisition and custody history link to canonical accounting without recapitalizing expensed supplies. |
| G12 planning/automation | Fund/Event budgets, recognition assistance and moving-weighted-average inventory have governed previews, saves and reports. |
| G13 evidence/approvals | External evidence references, attestations and readiness are retained. Attachments and independent approval remain external by adopted design. |
| G14 event usability | Events can be created/searched by name/code, tagged independently from Funds, reopened and reported. |

## Upgrade and migration

- Back up the active database before upgrading.
- Launch the new application against a copy first. Flyway upgrades the schema nondestructively through V92; never edit applied migrations or Flyway history.
- Current storage selects H2 2.4.240 with Flyway 13.12.0 to correct the reproduced 2.5.252 online-backup integrity defect. [P27-C2](P27-C2-online-backup-integrity.md) requires genuine 2.2.224 and 2.5.252 image compatibility, every-field recovery comparisons, evolving CLOB snapshots and restart/write guards. Retain the original backup and verify a disposable copy before switching; existing damaged archives are not reconstructed.
- File connections use MAX_COMPACT_TIME=0 to avoid partial compaction during normal close; validated restores still receive explicit full compaction. Files can remain larger between full compactions.
- After migration, verify company/chart selection, counts, Journal history, reconciliation, Period Close, assets, inventory and one report before switching production use.
- Keep the pre-upgrade backup until the migrated copy and an application-created post-upgrade backup both validate.

Follow [database backup and recovery](operating-guide.md#database-backup-and-recovery) and the detailed [database transfer contract](data-exchange/database-transfer.md). Restore to a new path; do not overwrite the active database.

## Interchange compatibility

- SCLX readers: 1.0, 1.2 and 1.3.
- SCLX writer: deterministic 1.3.
- Selected-company SCLX is not a complete backup. Preserve whole-database backups for credentials, preferences, every company and schema state.
- Generic accounting packet output follows generic accounting rules. The historical external workbook is reference evidence only; no official-template revision, signature method or filing deadline is encoded.
- Chart of Accounts JSON and OFX/QFX/CSV remain separate formats and never carry the complete ledger/database.

## Validation and known limits

P28-S1 locally passed a 218-test combined A01–A14/P25/P26 matrix, 71 actual-display JavaFX tests and 1,109 clean-verification tests with 77 expected headless display skips. Exact-head CI passed all required stages. On the final P28-S2 local bytes, the two-test release-guide contract, 1,111-test clean verification and 69-test production JavaFX selector passed with zero failures/errors; the headless suite had 77 expected display skips. Exact-head P28-S2 CI and merge remain before final release authority is recorded.

Known limits are deliberate and visible: no internal attachment store, no independent approval queue, no automatic NMR rates/defaults, no official external form/deadline/signature policy, and reduced facts for older SCLX contracts where documented. Unsupported or conflicting interchange facts block or require explicit disposition; they are never silently guessed.
