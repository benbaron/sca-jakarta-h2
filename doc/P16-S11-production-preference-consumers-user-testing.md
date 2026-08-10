# P16-S11 production preference consumers — owner desktop checklist

Use a disposable or backed-up database. Complete this checklist on the exact pull-request head after Maven PR Tests pass.

## Shell preferences

1. Open **Administration → Preferences**, select **Dark** theme, and choose **Apply**. Confirm the production workspace changes immediately, including the navigation, tabs, forms, and tables.
2. Choose **Light** and **Save**. Restart the application and confirm the light theme is restored.
3. Toggle **Use unified native window decorations (restart required)**, save, and confirm the current window does not reconstruct itself. Restart and confirm the selected decoration mode is used.
4. Enable **Restore window size, position, and maximized state on startup**, save, move/resize or maximize the window, and exit normally. Restart and confirm the geometry is restored within the visible screen.
5. Disable remembered window state and save. Restart and confirm the standard laptop-safe geometry and default shell divider positions are used rather than the prior saved state.

## Accounting interaction defaults

6. Select **Reversal and replacement** as the correction method, save, open **Journal**, select an eligible entered transaction, and choose Delete/Reverse. Confirm Journal offers an explicit reversal and does not hard-delete.
7. Select **Direct edit** and enable delete confirmation. Confirm an eligible direct delete asks before mutation and Cancel leaves the transaction unchanged.
8. Disable delete confirmation, save, and repeat with another disposable eligible transaction. Confirm the extra prompt is skipped but closed-period/reconciliation protections still reject protected deletes.
9. Set the closed-period policy to **Require reason**, enable **Require a reason when reopening a closed period**, save, and open **Period Close**. Confirm both controls initialize from those values and the actor defaults to the local desktop user rather than `ui-operator`.
10. Change the period start day, save, select a period in the toolbar, and confirm the active-period start follows the saved day.

## Company display and truthful deferred state

11. Change currency symbol, money print format, and date format for one company. Save, switch away and back, and confirm production money/date views restore the company-specific formats without changing another company.
12. Confirm Settings does not offer an editable **Default privilege** or **Default reopening scope**. It must explain that authentication/authorization is not implemented and that calculated/custom close ranges have no session reopen-scope mode.
13. Confirm Audit History, Diagnostics, and Preferences are not enabled/disabled based on a stored default-privilege label; the application must not imply that this preference authenticates the operator.

Record the exact tested commit, operating system, Java version, and pass/fail notes in the PR before merge.
