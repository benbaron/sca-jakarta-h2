# Transaction editor guidelines

P03 introduces one shared spreadsheet-like line editor for transaction entry surfaces.

## Shared line editor contract

- Transaction lines use stable database IDs for account, fund, budget category, activity, merchant, and counterparty selections.
- Debit and credit are separate one-sided money inputs. A row cannot carry both a debit and a credit.
- Blank rows are editor affordances only and are not converted to `TransactionLineCommand` instances.
- Live totals show debit total, credit total, and debit-minus-credit difference before any save attempt.
- Row validation rejects missing account/fund IDs, negative values, zero-value accounting rows, both-sided rows, fewer than two meaningful rows, and unbalanced totals.
- Dirty state is owned by the editor model and is cleared only after the containing panel deliberately saves or discards work.

## Scope boundaries

- The editor model maps UI rows to the canonical P02 `TransactionCommand`/`TransactionLineCommand` boundary.
- Controls do not calculate authoritative accounting balances; they only present immediate row totals and validation feedback.
- Save, load/edit, reverse, and replace policy must continue through `TransactionEntryService` and correction services; JavaFX panels only route commands and present validation or protection results.
- Editor and register regions that can hide default-size text must remain user-resizable and scrollable: use a visible `SplitPane` when a region shares space with another pane, and provide both vertical and horizontal scrolling for hidden rows, columns, or long values instead of increasing minimum widths or relying only on wrapping.
- Editors for durable records must not show disabled placeholder Delete buttons. Transaction Editor Delete may hard-delete only when Settings -> Correction method is `DIRECT_EDIT`; otherwise it must ask whether to auto-fill and perform a reversing entry, defaulting the reversal date from the active period, and route the confirmed reversal through the correction service.

## Donor inspection findings for native transaction entry

The `benbaron/NonprofitAccounting` archive was inspected from a shallow clone at `/tmp/NonprofitAccounting` on 2026-07-03. The following donor areas can inform native P03 transaction panels and services without becoming a parallel application model:

- `src/main/java/nonprofitbookkeeping/ui/panels/JournalEntryWorkspaceFX.java` contributes UX patterns for transaction entry: a single add/duplicate/remove line workspace, account choices backed by a chart lookup, one-sided debit/credit edit commits, immediate total recalculation, blank-line skipping, and explicit validation messages before save. Native adoption should keep the P03 `TransactionLineEditorModel` and map selected IDs to `TransactionCommand`; do not import the donor's `CurrentCompany`, legacy `AccountingTransaction`, or direct JDBC metadata behavior.
- `src/main/java/nonprofitbookkeeping/ui/helpers/FocusCommitTextFieldTableCell.java` confirms the focus-loss commit behavior needed by spreadsheet-style cells. Native adoption should keep the local focus-commit cell or extract a small JavaFX utility after tests prove Enter, tab, click-away, and invalid-number behavior; avoid copying donor logging-to-stderr behavior.
- `src/main/java/org/nonprofitbookkeeping/service/PostingService.java` and `JournalLine.java` reinforce the canonical `Txn`/`TxnSplit` ledger, normal-balance debit/credit projection, and journal-view DTO shape already implemented by `TransactionEntryService`, `TransactionView`, and `AccountingJournalProjection`. Native P03-S2 should use `TransactionEntryService` rather than reintroducing donor `PostingService`, but can adapt donor-style post-save journal refresh and clear validation messages.
- Donor supplemental/donation schedule tabs show a possible later pattern for related sub-schedules, but those are outside P03-S1/P03-S2 unless the plan deliberately opens schedules or donations work. They should not be imported into the transaction editor slice.

Recommended focused adaptations for upcoming native work:

1. Replace free-text account/fund cells with ID-backed combo cells whose displayed labels come from `TransactionLineEditorModel.ReferenceData`, following the donor's account-choice pattern but resolving to stable database IDs.
2. Add duplicate-line and row-level missing-reference styling only after the base save workflow is wired to `TransactionEntryService`.
3. Keep native validation at the command boundary; use donor validation wording only as UI copy, not as a second accounting policy.
4. Add service-backed journal preview after save/load by calling `TransactionEntryService.journalView`, using donor `JournalLine` only as confirmation of the projection shape.

## P03-S2 transaction workflow integration notes

- Transaction Editor relationship cells now use ID-backed option selections for account, fund, budget category, activity, merchant, and counterparty values. The displayed label is only presentation; save mapping uses the selected stable IDs.
- Runtime reference choices are loaded through a query service and the save action calls `TransactionEntryService.enter(...)`; the editor no longer reports a session-only draft save as success.
- Journal preview is intentionally available after a successful service save and reads the persisted projection from `TransactionEntryService.journalView(...)`.


## P03-S3 ledger register integration notes

- Ledger Register refreshes through the canonical `TransactionEntryService.search(...)` query boundary so filters and rows use the same `TransactionView` projection as Transaction Editor loads.
- The register exposes date and memo/payee filters, keeps bounded results to avoid unbounded UI loads, and shows persisted `ENTERED` status rather than a UI-only posted label.
- The register table and transaction journal details are separated by a vertical `SplitPane`, giving users a horizontal divider to resize the middle pane. Table/detail content must preserve vertical and horizontal scroll access whenever default-size values do not fit.
- Double-clicking or choosing **Open Selected in Editor** passes the stable transaction ID to Transaction Editor, which loads the transaction through `TransactionEntryService.load(...)` and saves subsequent edits through `TransactionEntryService.update(...)`.
- A register-level Delete for a selected transaction must delegate to Transaction Editor/correction services rather than deleting table rows. Under non-direct correction settings, the selected transaction's Delete flow asks to auto-fill and perform a reversing entry instead of hard deletion.
- Register-to-editor navigation is an editor context handoff only; it does not create a second ledger cache or calculate accounting values in table cells.

## P03-S00 sample-company system testing

P03 Transaction Editor and Ledger Register system testing uses an explicit sample-company lifecycle action, not automatic production seed data. Testers create or select a disposable database, then choose **File -> Create / Refresh Sample Company Data** to run `SampleCompanyService` against the active database. The service is idempotent: it creates or refreshes the clearly labeled `SCA Sample Chart`, a compact nonprofit chart of accounts, unrestricted/restricted/designated funds, and the budget category, activity, merchant, and counterparty reference records needed by ID-backed Transaction Editor choices.

Do not add fictional sample records to Flyway migrations or production startup. The sample action is intentionally user-triggered so production databases remain free of fictional chart and reference data unless a tester explicitly chooses to create a sample database.

Manual P03 system-test setup:

1. Create a new disposable database from the File menu.
2. Run **File -> Create / Refresh Sample Company Data**.
3. Open Transaction Editor and confirm account, fund, budget category, activity, merchant, and counterparty choices are populated.
4. Enter a balanced debit/credit transaction with the sample choices, save it, and refresh Ledger Register to confirm the persisted transaction appears.
