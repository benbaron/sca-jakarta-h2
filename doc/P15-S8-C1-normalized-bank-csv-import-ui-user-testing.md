# P15-S8-C1 normalized bank CSV import UI owner checklist

Use a disposable migrated H2 database with at least two active companies, one active configured bank
account in each company, and a normalized bank CSV 1.0 file exported from durable reviewed statement
rows. The file should contain multiple source batches, a probable or exact duplicate, retained PAYEEID,
review state, and a valid matched-transaction portable identity where available.

## Route and exact scope

- [ ] Banking → **Import Bank Statement…** opens Import Preview.
- [ ] Import Preview shows **Preview Normalized Bank CSV…** separately from OFX/QFX and mapped CSV.
- [ ] The normalized action requires an active configured bank account but does not require or change a mapped-CSV profile.
- [ ] The file chooser identifies normalized bank CSV 1.0 and proposes `*.csv` files.
- [ ] Preview shows the exact active company and configured account, source-batch count, row count, account-match state, normalized rows, duplicate flags, and path-coded messages.
- [ ] Preview explicitly states that no data changed.

## Approval and background commit

- [ ] A suffix-only account match keeps commit disabled until **Confirm suffix-only account identity** is selected.
- [ ] Commit requires a nonblank audit actor and names the source file, SHA-256, company, account, batch count, and row count.
- [ ] Confirmation states that only durable review facts are restored, no ledger transaction is created, and every batch/row rolls back on failure.
- [ ] Cancelling confirmation changes no H2 row and retains a clear cancellation result.
- [ ] Preview and commit run without freezing the JavaFX window; their action buttons are disabled while work is active.
- [ ] Success reports created or identical status, batch IDs/count, total rows, reviewable rows, matched rows, duplicates, issues, and the no-ledger result.

## Durable behavior and invalidation

- [ ] Commit restores all source batches, statement rows, review/duplicate state, PAYEEID, correction facts, issues, and valid same-company matches visible in the normalized file.
- [ ] Reopening Banking and Bank Transactions after restart shows the committed durable review facts.
- [ ] Reimporting the unchanged file into the same company/account is an idempotent no-op.
- [ ] Switching company, changing the selected account, or changing the file after preview prevents the retained approval from committing.
- [ ] A cross-company matched-transaction identity or blocking account mismatch commits nothing.
- [ ] No `Txn` or `TxnSplit` is created, and no mapped-CSV profile is created or mutated.

## Layout and regression

- [ ] At 1366 x 768, SCLX/COA, OFX/QFX, mapped CSV, normalized CSV, selectors, actor, confirmation, and commit controls remain reachable without clipped action buttons.
- [ ] Existing OFX/QFX and mapped-CSV preview/commit routes still work and retain their own exact scopes.
- [ ] Original mapped-CSV rows remain visible for mapped CSV; normalized CSV does not fabricate an original-row projection.
- [ ] No File-menu direct parser/staging route or Import/Export Jobs destination appears.

## Owner acceptance

- [ ] I completed every check above and found no blocking issue.
