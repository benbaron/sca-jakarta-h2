# P15-S8-C4 owner desktop acceptance

Use a disposable database and fictional test files. Run the application at the normal laptop window
size before expanding it.

## Import Preview progress and cancellation

1. Open **Import Preview** and confirm the complete top control area is reachable at laptop width. If
   the controls exceed the viewport, use its horizontal scrollbar; no button or selector may be clipped
   without a way to reach it.
2. Start each available non-mutating preview in turn: COA CSV, SCLX, OFX/QFX, mapped bank CSV, and
   normalized bank CSV. Confirm a bounded progress bar, an operation-stage label, and **Cancel Preview**
   appear while the operation runs.
3. During a sufficiently large preview, select **Cancel Preview**. Confirm the status states that the
   preview was cancelled before commit and that no newly previewed result becomes eligible for commit.
4. Refresh Banking or Bank Transactions and confirm a cancelled bank preview created no import batch,
   statement line, issue, ledger transaction, or factual import audit.
5. Start one preview, then confirm company, configured-account, mapped-profile, identity-confirmation,
   actor, and all other import actions remain unavailable until it finishes or is cancelled. Confirm a
   second operation cannot start concurrently.

## Commit boundary

6. Complete a valid bank or SCLX preview and approve its exact-scope confirmation. Confirm progress
   remains visible, the stage says commit cannot be cancelled, and **Cancel Preview** is disabled.
7. Confirm successful completion reports the existing exact counts and no-ledger boundary. For any
   rejected test input, confirm the existing rollback message appears and a new preview is required.

## Bank Transactions laptop-width export

8. Open **Bank Transactions** at laptop width. Confirm Account, From, Through, **Export Bank CSV…**,
   **Export OFX 2.x…**, and **Export QFX…** are all reachable through the export control scrollbar.
9. Run one statement export and confirm a visible busy indicator appears, all three export actions are
   disabled during the operation, and the existing destination/row/byte/warning/SHA-256 result remains
   intact.
10. Confirm the durable review table still scrolls horizontally and vertically, its columns remain
    sortable/resizable/reorderable, and no Import/Export Jobs destination appears.

Reply **confirmed** only after every applicable item passes. P15-S8-C4 and P15 remain VERIFYING until
this checklist is complete and the validated pull request is merged.
