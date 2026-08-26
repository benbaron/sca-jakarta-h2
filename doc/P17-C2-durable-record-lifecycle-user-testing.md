# P17-C2 — Durable record lifecycle user testing

## User-visible changes

- The Chart of Accounts editor now edits an existing account by its stable database ID rather than treating the account code as row identity.
- Account code remains editable business data. Renumbering an account updates the same durable account and preserves references to it.
- **Active** remains the lifecycle control for retiring an account. Clearing **Active** and saving deactivates the account without removing historical references.
- The Chart of Accounts workspace explains that accounts are retained rather than physically deleted; no placeholder Delete button is added.
- **New** clears the current account identity so saving creates a genuinely new account instead of overwriting the previously selected row.

## Owner acceptance checklist

Use a disposable/test database or a copy of production data for this checklist.

- [ ] Open **Chart of Accounts**, select an existing account, and note its code and any known Journal, Banking, report, or other durable references.
- [ ] Change that account's code and name, then choose **Save**.
- [ ] Confirm there is still exactly one account row: the edited row shows the new code and the old code did not remain as a duplicate.
- [ ] Reopen any known Journal, Banking, report, or other reference and confirm it still resolves to the same account under the new code/name.
- [ ] Refresh or leave and reopen **Chart of Accounts** and confirm the edited account remains selected/reloadable as the same durable record.
- [ ] Create or identify a second account, edit it, change its code to a code already used by another account, and choose **Save**. Confirm the duplicate code is rejected and the second account retains its original saved values.
- [ ] Select an account, clear **Active**, and save. Confirm the account remains visible as inactive and historical transactions/references remain intact.
- [ ] Confirm there is no disabled or placeholder Delete button. The workspace should explain that deactivation preserves history.
- [ ] Select an existing account, choose **+ Add** / global **New**, enter a distinct code and valid details, and save. Confirm a new account row is created and the previously selected account is unchanged.
- [ ] For an account configured in **Banking**, confirm changing its code while retaining `ASSET / BANK / DEBIT` succeeds and the Banking configuration still references it.
- [ ] For a Banking-configured account, attempt to remove the required BANK classification. Confirm the existing Banking protection still rejects that change.

## Acceptance record

Record any failed step with the account code/ID involved, the exact visible message, and whether a refresh/restart changes the result. Do not merge P17-C2 until GitHub Actions and this owner checklist are accepted.
