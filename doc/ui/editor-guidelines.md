# Journal workspace editor guidelines

P03 provides one spreadsheet-like transaction editor inside the unified Journal workspace. P03-C6 replaces the separate Ledger Register, Transaction Editor, and Inspect Journal destinations with one Journal surface derived from the interaction model of the donor repository's `Journal*` classes. P03-C7 adds the production `JournalWorkspaceCompliancePanel`, which applies the cross-cutting scrolling, table-state, money/date-formatting, and company-owned UI-state contract without duplicating accounting behavior.

## Shared line editor contract

- Transaction lines use stable database IDs for account, fund, budget category, activity, merchant, and counterparty selections.
- Debit and credit are separate one-sided money inputs. A row cannot carry both a debit and a credit.
- Blank rows are editor affordances only and are not converted to `TransactionLineCommand` instances.
- Live totals show debit total, credit total, and debit-minus-credit difference before any save attempt.
- Row validation rejects missing account/fund IDs, negative values, zero-value accounting rows, both-sided rows, fewer than two meaningful rows, and unbalanced totals.
- Dirty state belongs to the unified Journal editor and is cleared only after a deliberate save or discard.

## Service and authority boundaries

- The editor maps UI rows to canonical `TransactionCommand`, `TransactionLineCommand`, and `TransactionSupplementalLineCommand` values.
- Controls calculate only immediate presentation totals and validation feedback; authoritative accounting behavior remains in services.
- New, load/edit, save, delete, and reverse operations route through `TransactionEntryService` and `TransactionCorrectionService`.
- The JavaFX panel contains no SQL and does not import donor persistence, static company state, or an alternate ledger.
- Transaction supplemental details remain H2-backed rows linked to the canonical transaction.
- Fields that current services cannot persist are omitted or clearly described; they must not appear as enabled fake-save fields.
- `JournalWorkspaceCompliancePanel` may adjust the JavaFX composition and formatting of the delegate workspace, but it must not add a second transaction model, ledger cache, or save path.

## Unified Journal layout contract

The production Accounting navigation exposes one **Journal** destination. `LEDGER_REGISTER` and `TXN_EDITOR` remain stable compatibility identifiers but normalize to the canonical `JOURNAL_PANE` destination.

The Journal workspace contains:

1. A grouped transaction journal with date and text filters. One row represents one complete canonical transaction and displays account lines, funds, debit and credit lines, transaction ID, supplemental count, and memo/details.
2. An integrated New/Edit header with date, memo, transaction identity/status, live debit/credit/difference totals, and validation text.
3. An editable entry-line table with Add Line, Duplicate Line, Remove Line, stable-ID reference selectors, one-sided Debit/Credit behavior, NMR, notes, and per-company table state.
4. An Additional Details region containing only transaction fields currently backed by H2 services.
5. Persisted supplemental-detail regions for Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability.

The major regions are separated by visible draggable `SplitPane` dividers:

- journal register versus transaction editor;
- editor header versus entry lines versus details;
- additional details versus supplemental details;
- every table versus the surrounding controls/help in that table's major region.

The complete middle/editor region is wrapped in a single vertical `ScrollPane` identified as `journalWorkspaceEditorScroll`. The nested resize bars remain inside that scrollable editor. Journal, entry-line, and supplemental `TableView` controls keep their own horizontal and vertical scrolling and must not expand to their complete row count.

Divider positions and table state are remembered for the active company through `CompanyUiPreferencesService`. Table state includes column width, column order, sort direction, and multi-column sort priority. Every table uses unconstrained resizing and every column remains sortable, resizable, and reorderable.

Money and date controls use `CompanyUiFormat` and the active company's `CompanyUiPreferences`. Formatting changes presentation and accepted input only; it does not change canonical service values or H2 precision/types.

## Operations

- **New Entry** clears the integrated editor after any required dirty-state confirmation.
- **Edit Selected** or journal-row double-click loads the selected transaction by stable ID through `TransactionEntryService.load(...)`.
- **Save Entry** calls `enter(...)` in New mode and `update(...)` in Edit mode.
- **Delete** is offered only as a real action under direct-edit correction policy. Other correction policies label the action **Reverse** and route to `TransactionCorrectionService.reverse(...)`.
- **Refresh** and filter operations query `TransactionEntryService.search(...)`.
- Global New, Save, and Post/Validate commands delegate to the same integrated editor operations.

## Donor-reference decisions

The following concepts are deliberately adapted from `benbaron/NonprofitAccounting`:

- `JournalPanelFX`: grouped one-row-per-transaction journal presentation and transaction-level selection.
- `JournalEntryWorkspaceFX`: integrated New/Edit workflow, add/duplicate/remove lines, immediate totals, validation, additional details, and supplemental tabs.
- `JournalShellNavigation`: one Journal destination for new, edit, and review work.
- `GeneralJournalEntryPanelFX`: traditional account/debit/credit visual ordering.

The donor's `CurrentCompany`, repositories, JDBC/static persistence, and alternate transaction models are not ported.

## Historical compatibility

Older P03 callers may still request Ledger Register or Transaction Editor. `AppPanelId.canonical(...)`, `PanelHost`, and `DrillThroughCoordinator` normalize those requests to the existing Journal tab and preserve one-time New/Edit transaction context. They must not create duplicate panels or a second ledger cache.

## Sample-company system testing

P03 system testing uses an explicit sample-company lifecycle action, not automatic production seed data. Testers create or select a disposable database, then choose **File -> Create / Refresh Sample Company Data** to run `SampleCompanyService` against the active database.

Manual validation:

1. Create or select a disposable database.
2. Run **File -> Create / Refresh Sample Company Data**.
3. Open **Journal** and confirm account, fund, budget category, activity, merchant, and counterparty choices are populated.
4. At laptop width, confirm the complete editor has one overall vertical scrollbar and move every nested divider.
5. Confirm Journal, entry-line, and every supplemental table scroll independently in both directions when content exceeds the viewport.
6. Reorder and resize columns, apply single- and multi-column sorts, reopen the company, and confirm the state is restored.
7. Enter and save a balanced transaction with supplemental details.
8. Refresh the Journal, select the transaction, choose Edit Selected, and confirm all accounting and supplemental rows reload.
