# P21-S1 Activity administration — owner desktop verification

## User-visible changes

- **Administration -> Activities** opens production maintenance for the same company-owned Activities already used by Journal and SCLX.
- New and Save use stable database identity. Editing an Activity code or name updates the selected durable row rather than creating a replacement.
- **Active** controls lifecycle. Inactive Activities remain visible in Activity maintenance and historical Journal rows but are omitted from the Activity choices offered for new/refreshed Journal entry.
- **Delete Unused** is a real protected operation. It is available only when the Activity has neither Journal `TxnSplit` references nor a durable Activity interchange identity. Otherwise the panel explains the references and directs the operator to deactivate the Activity instead.
- VIEWER can inspect Activity data but cannot edit, save, lifecycle-change, or delete it. ACCOUNTANT, MANAGER, and ADMIN use the established `BOOKKEEPING_WRITE` authority.
- Activity mutations write factual audit history using the authenticated username.

## Manual verification

Use a disposable database with sample company data. Do not perform lifecycle testing against records you need to retain.

1. Authenticate as **ACCOUNTANT**, open **Activities**, and confirm the table shows existing active and inactive Activities for the active company only.
2. Choose **New**, create `P21-TEST` / `P21 Test Activity`, and Save. Refresh and confirm exactly one row exists.
3. Select that row, change the code to `P21-RENAMED` and the name to `Renamed P21 Test Activity`, then Save. Confirm the displayed Activity ID is unchanged and no second Activity row appears.
4. Open **Journal**, start or refresh a new entry, and confirm `P21-RENAMED` is available in the Activity selector.
5. Save a balanced disposable Journal transaction with one line linked to `P21-RENAMED`. Return to **Activities** and refresh/select it. Confirm **Delete Unused** is unavailable and the panel reports a Journal reference.
6. Clear **Active** and Save. Refresh Journal reference data and confirm the Activity is no longer offered for a new line. Load the previously saved transaction and confirm its Activity relationship still displays the renamed Activity rather than becoming blank or changing identity.
7. Return to **Activities**, re-enable **Active**, Save, then refresh Journal and confirm the Activity is selectable again.
8. Create a second Activity that is never referenced. Select it and confirm the panel states that it may be permanently deleted. Choose **Delete Unused**, confirm the warning, and verify the row disappears.
9. If the disposable database contains an Activity imported through SCLX, select it and confirm its durable interchange identity prevents physical deletion even when it has no Journal transaction. Confirm deactivation remains available.
10. Export SCLX after the rename and inspect/re-preview it. Confirm the Activity extension uses `activity:<company-code>:P21-RENAMED` and any exported line linked to that Activity uses the same current portable address.
11. Open **Audit History** and confirm create/update/deactivate/reactivate/delete Activity facts show the authenticated username that performed each action.
12. Sign in as **VIEWER**. Open **Activities** and confirm the list remains readable while New, Save, Delete Unused, code/name editing, and Active lifecycle editing are unavailable with permission explanations.
13. Switch back through **ACCOUNTANT**, **MANAGER**, and **ADMIN** sessions and confirm each can perform an authorized Activity save. Switch company/session context and confirm permissions and the visible Activity list follow the current company immediately.
14. At laptop-size window width, resize/reorder/sort Activity table columns and move the horizontal workspace divider. Reopen the company and confirm the company-owned table/divider state restores without clipping the editor.

## Acceptance

P21-S1 is not DONE until the owner confirms this desktop verification, the implementation is merged, and the exact-head Maven PR Tests plus production JavaFX route compliance are green.
