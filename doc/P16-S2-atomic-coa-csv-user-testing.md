# P16-S2 Atomic COA CSV Accepted-Row Commit — Owner Desktop Checklist

Use a disposable migrated database with an active company and active Chart of Accounts. Keep a copy of the database before testing.

## Successful atomic commit

1. Open **Import Preview** and choose a COA CSV containing at least one parent account and one child account. Put the child before the parent in the CSV to exercise dependency ordering.
2. Confirm the preview names the active company and target chart, reports the source SHA-256, and clearly separates accepted, rejected, warning, and blocking counts.
3. Confirm **Commit Accepted COA Rows** is unavailable when the preview has a blocking duplicate-code, missing/inactive-parent, cycle, invalid type/balance, or other semantic error.
4. With a valid preview, enter an import actor and choose **Commit Accepted COA Rows**.
5. Confirm the dialog names the exact company/chart, accepted-row count, and SHA-256 and states that the batch is atomic.
6. Commit and confirm the completion message reports only committed `created`, `updated`, and `skipped` counts.
7. Open **Chart of Accounts** and confirm all accepted rows are present with the expected parent hierarchy. Confirm rejected rows were not written.
8. Restart the application/database and confirm the same chart state remains.

## Idempotent recommit

1. Preview the same unchanged CSV again against the now-updated chart.
2. Commit it again.
3. Confirm the result reports the identical rows as skipped rather than creating duplicates or another partial hierarchy.
4. Confirm Audit History does not gain a duplicate completion fact for the identical source/chart batch.

## Drift and rollback behavior

1. Preview a valid COA CSV, then modify that source file before committing. Confirm commit refuses the stale preview and directs you to preview again; no accepted row is written.
2. Preview again, then make an independent Chart of Accounts change before committing. Confirm commit refuses target drift and directs you to preview again; the independent change remains, but none of the stale accepted batch is written.
3. Preview under one company, switch the active company, and confirm the stale preview cannot commit into the new company.
4. For any surfaced commit-time failure, confirm the UI reports rollback/no committed batch rather than a partial row count. Restart and confirm no partial accepted-row hierarchy is visible.

Automated migrated-H2 tests inject a failure after the first account write to prove accounts, external identities, and the operation audit fact roll back together.

## Automated validation already completed

- Implementation head `e495865b91a4979c1571f6ab61922ea43fedd839` passed `mvn clean verify`, the deliberately repeated Maven test suite, and production JavaFX route compliance in Maven PR Tests run `31192123755`.
- The final documentation-inclusive PR head must pass the same complete gate before owner acceptance authorizes merge.
