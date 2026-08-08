# P16-S4 Owner Desktop Acceptance — Governed Bank-Import Authority

Use this checklist only after the exact PR head passes the complete Maven PR Tests gate. Use disposable company data that can safely receive bank-review facts.

## Preconditions

1. Open a company with at least two configured active bank accounts so exact-account locking can be observed.
2. Start or load a mutable Bank Reconciliation session for account A and choose a statement date range that can contain the test statement rows.
3. Have one small valid OFX/QFX, mapped CSV, or normalized CSV statement for account A and, when practical, one malformed or account-mismatched file.

## A. Reconciliation import route and exact scope

1. Open **Accounting → Bank Reconciliation** and load the mutable session for account A.
2. Confirm the Statement step contains **Add Manual Line** and **Import Bank Statement…**; confirm there are no pasted CSV, OFX, or QIF import tabs/actions.
3. Choose **Import Bank Statement…**.
4. Confirm Import Preview opens with account A selected and the configured-account selector locked for this reconciliation-origin operation.
5. Confirm the available bank preview actions are OFX/QFX, Mapped Bank CSV, and Normalized Bank CSV. Confirm there is no QIF preview/import action.

## B. Canonical preview, failure, and cancellation behavior

1. Preview a malformed/security-invalid or account-mismatched source from the reconciliation-origin Import Preview route.
2. Confirm the same canonical blocking diagnostics appear as when Import Preview is opened directly and no durable review batch is claimed as committed.
3. Start another preview and use **Cancel Preview** before commit begins. Confirm no committed result is published and the reconciliation is not told that import succeeded.
4. Confirm that once a durable commit begins the UI states that commit cannot be cancelled.

## C. Successful commit and durable reconciliation refresh

1. Preview a valid account-A source and review its normalized rows, warnings, account identity, and duplicate state.
2. Supply the required actor/identity confirmation and commit the exact preview.
3. Confirm the UI returns to the same reconciliation session only after the canonical commit succeeds.
4. Confirm the reconciliation reports that it refreshed from durable bank-review facts and the committed statement rows appear in the Statement/Match data for the session's date range.
5. Leave and reopen Bank Reconciliation, reload the same session, and confirm the rows remain present from H2 rather than transient preview state.
6. Re-import the identical source and confirm canonical duplicate/idempotent behavior is the same as direct Import Preview use.

## D. Manual entry and finalized-session boundaries

1. In a mutable reconciliation, add one manual statement line and confirm it appears and survives reload.
2. Confirm manual entry does not open Import Preview and does not imply a file format.
3. Load a finalized reconciliation and confirm both **Add Manual Line** and **Import Bank Statement…** are disabled with the finalized/read-only explanation.

## E. Banking entry point remains governed

1. Open **Accounting → Banking** and choose its **Import Bank Statement…** action.
2. Confirm it opens the same Import Preview workspace without a reconciliation-session return requirement and still uses the governed preview/commit controls.

## Acceptance record

- [ ] A. Reconciliation route and exact configured-account lock passed
- [ ] B. Canonical failure/cancellation behavior passed
- [ ] C. Successful commit, return, durable refresh, and re-import behavior passed
- [ ] D. Manual-entry and finalized-session boundaries passed
- [ ] E. Banking entry point remains governed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S4 DONE or begin P16-S5 until this checklist is accepted and the PR has merged.
