# P16-S5 Owner Desktop Acceptance — Connected-Database Session Authority

Use this checklist only after the exact P16-S5 PR head passes the complete Maven PR Tests gate. Use disposable databases or copies that can be switched safely.

## Preconditions

1. Have database A open with a recognizable active company and records.
2. Have a second valid database B with either the same active company code or a different active company so authoritative company resolution can be observed.
3. Keep one deliberately invalid/unusable target available when practical (for example, a file that cannot be opened as an H2 database).

## A. Preferences shows factual connected state

1. Open **Administration → Preferences**.
2. Confirm **Connected database file** displays the current database A path and cannot be edited.
3. Confirm the help text directs database selection/creation to the File menu rather than implying that Apply/Save changes the connection.
4. Change and save an ordinary preference. Confirm the connected database path does not change.

## B. Dirty-workspace cancellation preserves database A

1. Open an editor and make an unsaved change.
2. Choose **File → Select Database File…** and select database B.
3. Cancel the discard-unsaved-edits confirmation.
4. Confirm database A remains displayed in the status/Preferences/Diagnostics surfaces, the same active company and records remain visible, and the unsaved editor remains intact.

## C. Successful prepared switch is coherent

1. Save or discard the dirty edit, then select database B again and accept the switch.
2. Confirm the status bar and Preferences show database B.
3. Open **Diagnostics** and confirm its active database path is database B and its active company matches the company actually loaded from B.
4. Refresh/reopen several already-open workspaces. Confirm they show B data only and no stale A company/account/transaction rows remain.
5. Switch back to database A and confirm the same coherence in the opposite direction.

## D. Failed target keeps the healthy source session

1. With database A healthy and active, attempt to select an invalid/unusable target.
2. Confirm the UI reports the source and failed target paths and says the target was not activated.
3. Confirm database A remains active in status, Preferences, and Diagnostics.
4. Confirm the prior active company and records remain available and the application does not replace the healthy session with a database-recovery state for the failed target.

## E. Create/restart and recent-selection behavior

1. Choose **File → Create New Database…** and create a new disposable database.
2. Confirm the new database becomes active only after migration/validation completes and an authoritative active company is available.
3. Exit and restart the application. Confirm the last successfully connected database and resolved company are restored together.
4. Confirm a previously failed or cancelled target was not recorded as the active database.

## Acceptance record

- [ ] A. Preferences factual/read-only database state passed
- [ ] B. Dirty-workspace cancellation preservation passed
- [ ] C. Successful coherent database/company/service switch passed
- [ ] D. Failed-target source-session preservation passed
- [ ] E. Create/restart/recent-selection behavior passed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S5 DONE or begin P16-S6 until this checklist is accepted and the P16-S5 PR has merged.
