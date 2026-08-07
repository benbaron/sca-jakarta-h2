# P16-S1 Atomic Monthly Depreciation — Owner Desktop Checklist

Use a migrated test company with one active straight-line fixed asset, valid depreciation and accumulated-depreciation accounts, an active fund, and an open run date.

## Normal execution

1. Open **Assets & Inventory → Depreciation Runs** and select an eligible asset.
2. Enter an open run date and optional notes, then choose **Run Monthly Depreciation**.
3. Confirm the run and refresh controls remain disabled while the operation is active.
4. Confirm the completion message names a committed canonical transaction and the refreshed completed-runs table contains exactly one new run linked to that transaction.
5. Close and reopen the application/database and confirm the same run and transaction remain present.

## Duplicate handling

1. Select the same asset and same run date and run depreciation again.
2. Confirm the UI reports that the completed run already exists.
3. Confirm the panel refreshes and still shows only the original run, with no extra transaction or misleading success message.

## Failure and refresh behavior

1. Attempt a run in a closed period or against an ineligible asset.
2. Confirm the message is actionable and does not claim a transaction or run was created.
3. Confirm the panel refreshes after the failure and displays only persisted assets and completed runs.
4. Restart the application and confirm no partial depreciation activity appears.

Failure injection for late run, portable-identity, audit-event, and uniqueness failures is covered by automated migrated-H2 rollback tests rather than desktop testing.
