# P17-C4 — Banking durable-record lifecycle user testing

## User-visible behavior

- Bank and configured bank-account records remain durable H2 history; Banking uses Active/inactive lifecycle rather than physical deletion.
- A Bank cannot be made inactive while any active configured bank account still references it.
- A configured bank account cannot be created or reactivated as active while its Bank is inactive.
- Inactive configured accounts remain available as historical configuration and existing ledger/history views remain intact.
- Banking continues to explain that records should be deactivated to preserve history; it does not expose a placeholder or generic Delete operation.

## Owner acceptance checklist

Use a disposable/test database or a copy of production data.

- [ ] Open **Banking** and select an active Bank that has at least one active configured bank account.
- [ ] Clear **Bank active** and save. Confirm the save is rejected with an explanation that configured bank accounts must be deactivated first, and confirm the Bank remains active after Refresh.
- [ ] Select the linked configured bank account, clear **Account active**, and save. Refresh and confirm the same configured-account record remains present and inactive.
- [ ] Return to the Bank, clear **Bank active**, and save. Refresh and confirm the same Bank record remains present and inactive.
- [ ] Select the inactive configured bank account, set **Account active**, and save while the Bank is still inactive. Confirm the save is rejected and the account remains inactive.
- [ ] Reactivate the Bank and save, then reactivate the configured bank account and save. Confirm both original durable records are reused rather than duplicated.
- [ ] Open **Bank Transactions → Ledger Activity** and any existing reconciliation/history views for this account. Confirm historical records remain visible after the deactivate/reactivate cycle.
- [ ] Confirm Banking exposes no generic **Delete** or placeholder Delete button and its visible lifecycle guidance directs the operator to deactivate records to preserve history.
- [ ] At laptop width, confirm both Banking tables/forms, split dividers, and scrolling remain usable.

## Acceptance record

Record any failed step with the Bank ID/name, configured-account ID/name, active states before and after the operation, visible message, and whether Refresh changes the observed result. Do not merge P17-C4 until GitHub Actions and this checklist are accepted.
