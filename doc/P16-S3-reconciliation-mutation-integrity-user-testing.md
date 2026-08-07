# P16-S3 Owner Desktop Acceptance — Reconciliation Mutation Integrity

Use this checklist only after the exact PR #254 head has passed the complete Maven PR Tests gate. These steps verify observable desktop behavior; persistence-only invariants are covered by automated tests and are not duplicated as manual database exercises.

## Preconditions

1. Build/run the PR #254 branch with a disposable test database or company data that can be safely changed.
2. Open **Accounting → Bank Reconciliation** for a company with one configured BANK/DEBIT/CASH account and at least one bank transaction/statement line suitable for matching.
3. If needed, start a reconciliation session whose statement period contains the test transaction.

## A. Mutable-session workflow

1. Load an in-progress reconciliation session.
2. Confirm the normal mutation controls are enabled: statement entry/import, Auto Match, Match, Unmatch, Mark Cleared, Record Difference Explanation, Save Unresolved, and Finalize.
3. Match a statement row to the correct ledger bank line, then Unmatch it. Confirm the UI refresh shows both sides as unmatched; rematch the same pair for finalization.
4. Enter a factual difference explanation on an unresolved statement item. Confirm the text is represented as an explanation/resolution and is not presented as a newly created accounting transaction.
5. Use **Save Unresolved** once and confirm the session reloads as still editable rather than appearing finalized.

## B. Finalize and read-only behavior

1. Resolve the session to the state required by the UI, then press **Finalize**.
2. Confirm the status reports the reconciliation as finalized and the displayed tables/balances refresh.
3. Confirm manual statement entry, pasted/file import, Auto Match, Match, Unmatch, Mark Cleared, Record Difference Explanation, Save Unresolved, and Finalize are disabled.
4. Confirm the statement source controls and difference-explanation editor are also disabled.
5. Select/reload the same finalized session from **Saved Reconciliations**. Confirm it remains read-only after reload; there is no ordinary Save path that makes it editable again.

## C. Explicit successor

1. With the finalized session selected, confirm the successor controls are enabled and ordinary mutation controls remain disabled.
2. Enter the successor statement ending date/balance, actor, and a factual reason, then choose **Start Successor**.
3. Confirm a new in-progress reconciliation session opens for the next statement period.
4. Reload the predecessor from **Saved Reconciliations** and confirm it is still finalized and read-only.
5. Reload the successor and confirm it remains independently editable.

## D. Error/refresh behavior

1. Deliberately make one validation error that the UI permits, such as omitting required successor actor/reason on a finalized session.
2. Confirm the UI displays an actionable error and does not claim the operation was saved/finalized.
3. Reload the session and confirm the displayed state agrees with persisted authority after the failed action.

## Automated-only acceptance

The owner does **not** need to reproduce these by database manipulation. The P16-S3 automated suite proves finalized immutability, company/account/session scope rejection, exact atomic symmetric match/unmatch, idempotent finalization, factual-only difference explanations, audited successor history, and logical import-source provenance when parsing transport uses an overlong temporary filesystem pathname.

## Acceptance record

- [ ] A. Mutable-session workflow passed
- [ ] B. Finalize/read-only behavior passed
- [ ] C. Explicit successor passed
- [ ] D. Error/refresh behavior passed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S3 DONE or begin P16-S4 until this checklist is accepted and the repository workflow's merge/completion boundary has been satisfied.
