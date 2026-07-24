# P15-S2 owner desktop test procedure

Use disposable backup and restore paths. Do not select an existing production database as the restore target.

## Preparation

1. Start the application through the normal Maven/Eclipse JavaFX launch path.
2. Open a known database containing at least one company and several posted transactions.
3. Record the active database path, active company, company count, and a few identifiable transaction facts.
4. Open **Administration > Database Transfer**.
5. Confirm that **Active database** shows the exact database currently selected in the workspace.
6. Confirm that **Switch to Validated Copy** is disabled before any restore succeeds.

## Backup

1. Choose **Backup Database…** from the Administration tab.
2. Select a new `.zip` path that does not already exist.
3. Confirm that the dialog displays the correct active database and backup destination.
4. Approve the operation.
5. Confirm that the UI remains responsive while the progress indicator is visible.
6. Confirm that the completion dialog and Administration tab show:
   - the exact backup path;
   - a nonzero byte count;
   - a 64-character SHA-256 value;
   - plausible company, transaction, and split counts.
7. Confirm that the active database path has not changed.
8. Repeat the path-selection step using the existing backup filename and confirm that overwrite is rejected.
9. Repeat the backup through **File > Backup Database…** and confirm that it uses the same workflow and status state.

## Restore and validation

1. Choose **Restore Database Copy…**.
2. Select the backup ZIP created above.
3. Select a new target such as `sca-ledger-restored.mv.db` that does not already exist.
4. Confirm that the pre-restore dialog displays:
   - the active database that will not be overwritten;
   - the selected backup archive;
   - the exact new target database.
5. Approve the operation.
6. Confirm that the UI remains responsive while restore, migration, and validation run.
7. When the validation dialog appears, choose **Later** rather than switching immediately.
8. Confirm that:
   - the original database remains active;
   - the restored `.mv.db` exists at the selected path;
   - the Administration tab shows the target path, SHA-256, and record counts;
   - **Switch to Validated Copy** is now enabled.
9. Attempt to restore again to the same target and confirm that the existing target is rejected.
10. Attempt to restore to the active database path and confirm that active-database overwrite is rejected.
11. Select a non-H2/corrupt ZIP or text file renamed to `.zip` and confirm that validation fails without changing the active database or leaving a target database behind.

## Guarded switch

1. Choose **Switch to Validated Copy** from either the Administration tab or File menu.
2. Confirm that the switch dialog shows the current active database and the validated restored database.
3. Approve the switch.
4. Confirm that the workspace now reports the restored database as active.
5. Confirm that the previously active company is restored when it exists in the copy.
6. Open Dashboard, Journal, Funds, Chart of Accounts, and at least one report panel.
7. Confirm that the recorded company and transaction facts match the source database.
8. Confirm that open panels refresh rather than continuing to display stale source-database data.

## Return and recovery behavior

1. Use **File > Select Database File…** to return to the original database.
2. Confirm that company selection and visible accounting data return to the original database state.
3. Reopen the validated copy from the recent/normal database-selection path and confirm it opens cleanly without another restore.
4. Close and restart the application; confirm that the last successfully selected database is restored according to existing session-state rules.

## Acceptance record

Record:

- source database path;
- backup ZIP path and SHA-256;
- restored database path;
- displayed company/transaction/split counts;
- whether restore left the original database active until explicit switch;
- whether all overwrite/corrupt-input guards behaved correctly;
- whether the File-menu and Administration routes produced the same workflow;
- any visual clipping, inaccessible controls, misleading text, stale data, or error dialog encountered.
