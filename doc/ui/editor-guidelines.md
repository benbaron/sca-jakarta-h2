# Transaction editor guidelines

P03 introduces one shared spreadsheet-like line editor for transaction entry surfaces. P03-C4 reorganizes the Transaction Editor into task-sized pages and updates Journal Pane toward grouped transaction review.

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

## P03-C4 page and journal contract

- Transaction Editor is organized into task-sized pages: Header, Entry Lines, Additional Details, Donation Subschedule, and Supplemental Details.
- Header contains the transaction title, date, memo, debit total, credit total, difference, balanced/needs-attention status, and validation message.
- Entry Lines contains Add Line, Duplicate, Remove, the entry-line table, one-sided Debit/Credit behavior, blank-row behavior, and active-company table-state persistence.
- Additional Details groups Party / Document, Bank / Reconciliation, and Budget / Fund fields. Only fields supported by `TransactionCommand` are authoritative on save until their owning services exist.
- Donation and Supplemental Details are transaction-local detail panels. They must not reintroduce the eliminated generic Schedules panel, schedule runbook sidecar, or Schedules navigation item.
- Supplemental detail panels cover Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability. Unsupported Add/Remove actions must be absent or disabled with clear explanation.
- Journal Pane reviews whole transactions as grouped rows when the current projection supports it. A grouped row should expose Date, Account Title and Description, Fund, Cleared, Debit, Credit, Transaction ID, Supplemental, and Memo/Details regions.
- Journal New, Edit, Delete, and Refresh actions are valid only when they route to the current Transaction Editor or correction services.

## Design-reference findings for native transaction entry

The `benbaron/NonprofitAccounting` UI package can inform native P03 panels without becoming a parallel model:

- `JournalEntryWorkspaceFX` contributes UX patterns for add/duplicate/remove entry lines, one-sided debit/credit editing, immediate total recalculation, explicit validation messages, additional detail cards, donation details, and supplemental tabs. Native adoption must keep `TransactionLineEditorModel` and `TransactionCommand`.
- `JournalPanelFX` contributes a grouped transaction-block journal display with transaction-level selection/navigation and supplemental detail viewing. Native adoption must keep `TransactionEntryService.search(...)` and `journalView(...)` as projection sources.
- Focus-commit table-cell behavior is appropriate for spreadsheet-style cells, but native code should keep or extract a local utility rather than porting legacy application services.

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
