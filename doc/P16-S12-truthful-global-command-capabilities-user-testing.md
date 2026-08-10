# P16-S12 truthful global commands — owner desktop checklist

Use a disposable or backed-up database. Complete this checklist on the exact pull-request head after Maven PR Tests pass.

## Destination capabilities

1. Open **Dashboard**. Confirm New is enabled in both the File menu and toolbar, Save is disabled, and hovering Save explains that it is unavailable in Dashboard.
2. Choose New or press **Ctrl+N**. Confirm the one canonical **Journal** workspace opens in new-entry mode; no separate Transaction Editor tab appears.
3. In Journal, confirm New and Save are enabled. Enter an intentionally incomplete transaction and press **Ctrl+S**; confirm Journal shows its real validation failure rather than a generic success claim. Complete and save a disposable balanced entry, and confirm the factual save result appears.
4. Open **Help**, **Diagnostics**, and **Report Library**. Confirm New and Save are disabled in each destination. Pressing their shortcuts must not mutate data or claim success.
5. Open **Banking**, **Budget Editor**, **Asset Register**, **Inventory**, **Chart of Accounts**, and **Funds**. Confirm their global New/Save states match the capability matrix in `doc/interface-operation-matrix.md`, and that every enabled command reaches a real editor action.

## Administration selection

6. Open **Administration → Preferences**. Confirm Save is enabled and New is disabled.
7. Select **Database Transfer**. Confirm both New and Save become disabled immediately.
8. Select **Company Admin**, then **User Admin**. Confirm New and Save become enabled immediately for each selected tab and act on that tab only.

## Shortcuts, native editing, and tab safety

9. Open Help and confirm it lists exactly **Ctrl+N**, **Ctrl+S**, **Ctrl+Shift+W**, and **Esc** as production command shortcuts. It must not claim Ctrl+F, Ctrl+K, or Ctrl+G.
10. Focus a standard editable text field, select text, and use **Ctrl+C** and **Ctrl+V**. Confirm the control performs normal JavaFX copy/paste and the workspace does not replace it with a panel status-only action.
11. Create unsaved edits in a closable editor tab and press **Ctrl+Shift+W**. Cancel the warning and confirm the tab and edits remain. Repeat and accept; confirm non-Dashboard tabs close and Dashboard remains open.

Record the exact tested commit, operating system, Java version, and pass/fail notes in the PR before merge.
