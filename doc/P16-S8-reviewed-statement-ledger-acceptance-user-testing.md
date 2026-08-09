# P16-S8 Owner Desktop Acceptance — Reviewed Statement Ledger Acceptance

Use a disposable database with one configured bank account and imported durable review rows. Include at least one ordinary row and, if practical, a probable-duplicate row. Import itself must remain non-posting.

## A. Import remains review-only

1. Import an OFX/QFX/mapped CSV/normalized CSV statement through **Import Preview**.
2. Open **Bank Transactions** and confirm the imported row appears as a durable reviewed row.
3. Confirm the import alone created no new Journal transaction.
4. Confirm **Create Transaction from Reviewed Row…** is enabled only for one unmatched `IMPORTED` row and is disabled for duplicate, matched, rejected/error, or already accepted rows.

## B. Frozen preview and balanced transaction

1. Select one eligible row and choose **Create Transaction from Reviewed Row…**.
2. Confirm the dialog shows the frozen source row, configured account, source date, amount, payee/name, memo/reference/source ID, currency, eligibility, and import issues.
3. Confirm the canonical bank account is fixed and the bank split amount/direction is prefilled from the source amount.
4. Choose the bank fund/activity as applicable; add one or more counter-account splits with accounts, funds, activity/merchant, debit/credit, notes, and optional payee/counterparty.
5. Confirm an unbalanced transaction cannot be created. Balance it and create the transaction.

## C. Atomic durable acceptance and drill-through

1. Confirm exactly one Journal transaction is created and is balanced.
2. Refresh **Bank Transactions** and confirm the source row is `ACCEPTED` and remains linked to that transaction.
3. Use **Drill to Ledger** and confirm it routes to the accepted canonical transaction context.
4. Reopen/restart and confirm the accepted row and transaction link persist.
5. Confirm no reconciliation match or cleared state was created merely by accepting the row.

## D. Duplicate and protection behavior

1. For a probable-duplicate warning, confirm creation is blocked until the explicit duplicate-confirmation checkbox is selected.
2. Confirm an exact duplicate cannot be accepted.
3. Confirm a row already matched in reconciliation cannot be accepted.
4. Confirm a source row or edited transaction date inside a finalized reconciliation cannot be accepted.
5. Confirm a transaction date inside a closed period cannot be accepted.
6. Switch companies after opening a preview, or otherwise test stale company scope, and confirm commit is rejected without partial ledger/link changes.

## E. Cancellation and failure

1. Open the acceptance dialog and cancel it; confirm no transaction/link/status change is created.
2. Cause a validation failure (for example missing fund or unbalanced counter splits) and confirm nothing is committed.
3. After any failed attempt, refresh both Bank Transactions and Journal and confirm there is no orphan transaction and the source row remains reviewable unless it had a pre-existing blocking state.

## Automated validation record

- Initial implementation head `e15ed7de60bf56880963e0813736d450d77d3ca7` reached the Maven verification suite but failed `ReviewedStatementAcceptanceSourceTest.bankTransactionsOffersOnlyExplicitReviewedRowAcceptance` because the production panel delegated through `acceptanceService.get().preview(...)` / `.accept(...)` while the source guard required explicit local-service delegation.
- Corrected implementation head `73ce3a2a966f690490de705058916519a741b635` binds the injected supplier result to a local `ReviewedStatementAcceptanceService` and calls the existing `acceptanceService.preview(...)` / `acceptanceService.accept(...)` operations without changing the service contract or persistence authority.
- Maven PR Tests run `31291318707` passed on exact head `73ce3a2a966f690490de705058916519a741b635`, including clean headless `mvn clean verify`, the deliberately repeated Maven test suite, and production JavaFX route compliance.
- Final PR head `f701aa4d495f9ab1c291578dd70de3f5221a42c7` passed Maven PR Tests run `31291581055`, and PR #260 merged to `main` at `2edc47d862643b5e131a7825dbf7b6e5b662febe`.

## Acceptance record

- [x] A. Import remains review-only and enablement passed
- [x] B. Frozen preview and balanced transaction passed
- [x] C. Atomic accepted link/restart/drill-through passed
- [x] D. Duplicate, close, reconciliation, and company guards passed
- [x] E. Cancellation/failure non-mutation passed
- [x] Exact tested PR head recorded
- [x] Owner acceptance recorded on 2026-08-09

P16-S8 is accepted and merged. P16-S9 began from the exact merged `main` commit recorded above.
