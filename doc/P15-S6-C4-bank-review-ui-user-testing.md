# P15-S6-C4 durable bank-review UI owner checklist

Use a disposable migrated H2 database containing at least two companies, active configured bank
accounts, the governed OFX/QFX fixtures, and both signed-amount and debit/credit CSV profiles.

## Company and account scope

- [ ] Import Preview lists only active configured bank accounts owned by the active company.
- [ ] Switching companies refreshes account/profile choices and does not retain an approved preview.
- [ ] The Import Preview layout remains usable at 1366 x 768 without hiding the commit controls.

## OFX/QFX preview and commit

- [ ] Preview shows detected variant/version, configured account, normalized rows, duplicate state, and warnings without changing H2.
- [ ] A suffix-only account match cannot commit until the separate identity-confirmation checkbox is selected.
- [ ] Changing the active company after preview rejects commit and requires a new preview.
- [ ] A nonblank actor and the exact-scope confirmation dialog are required before commit.
- [ ] Commit creates one durable review batch/rows/issues and no ledger transaction.

## Mapped CSV profile and preview

- [ ] Saving an explicit JSON profile persists it under the selected company and configured account.
- [ ] Signed-amount CSV displays both original logical rows and equivalent normalized rows.
- [ ] Debit/credit CSV applies the declared sign convention; malformed or ambiguous rows are blocked.
- [ ] Changing, replacing, deactivating, or retargeting the profile after preview rejects commit and requires a new preview.

## Durable review workspaces

- [ ] Banking shows durable batch, row, reviewable, duplicate, error, and issue counts for the active company.
- [ ] Banking routes Import Bank Statement to Import Preview and Review Imported Rows to Bank Transactions.
- [ ] Bank Transactions still shows the committed rows after application restart and isolates them when the company changes.
- [ ] An unmatched statement row refuses ledger drill-through; a matched row opens its canonical transaction.
- [ ] Identical reimport creates no additional rows or operation audit event.
- [ ] File-menu bank import/export entries open the governed preview/review workspaces rather than a session staging path.

## Owner acceptance

- [ ] I completed every check above and found no blocking issue.
