# Unified Journal workspace design

## Purpose

The Journal workspace is the single production surface for reviewing canonical transactions and entering or editing journal entries. P03-C6 replaces the separate Ledger Register, Transaction Editor, and Inspect Journal surfaces with one resizable Journal workspace.

The user-visible interaction model is based on the donor repository's `JournalPanelFX`, `JournalEntryWorkspaceFX`, `GeneralJournalEntryPanelFX`, and `JournalShellNavigation`. The production implementation continues to use the current H2 schema and service boundaries; donor repositories, static persistence, and alternate ledger models are not imported.

## One Journal destination

Left Navigation under Accounting exposes one **Journal** item. The canonical panel identifier is `JOURNAL_PANE`.

`LEDGER_REGISTER` and `TXN_EDITOR` remain retired compatibility aliases. Existing callers, saved destination references, dashboard actions, and drill-through paths normalize to `JOURNAL_PANE` and select the same existing Journal tab. They do not create duplicate tabs or independent transaction caches.

## Journal review region

The upper Journal region is read-only and displays one grouped row per canonical transaction. Each row presents:

- transaction date;
- account titles and descriptions in journal-line order;
- funds;
- debit and credit lines;
- transaction ID;
- supplemental-detail count;
- memo, payee, bank, and line-detail text where available.

Date and text filters query `TransactionEntryService.search(...)`. A row may be selected and opened for editing by **Edit Selected** or double-click. The selection is a transaction-level selection, never an independently editable ledger line.

The current aggregate transaction projection does not yet expose every `TxnSplit.bankCleared` value. Until a line-level cleared-state projection is added, the Journal must not pretend to distinguish mixed cleared and uncleared lines authoritatively.

## Integrated New and Edit modes

The lower editor uses the same surface for New and Edit modes.

### New mode

New mode creates a transaction only when **Save Entry** successfully calls `TransactionEntryService.enter(...)`. Prefill from active period or drill-through context is only an editor convenience and is not authoritative.

### Edit mode

Edit mode loads one transaction by stable ID through `TransactionEntryService.load(...)` and saves through `TransactionEntryService.update(...)`. It is not an upsert and must not infer identity from date, memo, payee, reference, or amount.

The integrated editor contains:

- transaction date and memo;
- live debit, credit, and difference totals;
- balanced/needs-attention and validation messages;
- ID-backed payee and bank-account selectors;
- editable accounting lines;
- persisted supplemental-detail tabs.

## Accounting lines

Accounting line editing follows the canonical P02 contract:

- stable database IDs for account, fund, budget category, activity, and merchant;
- separate Debit and Credit fields;
- no line may contain both debit and credit;
- no negative or zero-value accounting lines;
- at least two meaningful lines;
- total debits must equal total credits;
- blank editor rows are not persisted;
- `BigDecimal` remains authoritative internally.

The JavaFX table provides Add Line, Duplicate Line, and Remove Line. Immediate totals and field validation are presentation behavior only; `TransactionCommandValidator` and `TransactionEntryService` enforce authoritative rules.

## Additional details

The Additional Details region shows only fields supported by current authoritative services. Unsupported donor fields such as legacy check/reference, clearing-bank text, or budget-tracking text must not appear as enabled fake-save fields. New fields require a deliberate H2/service slice before becoming editable production data.

## Supplemental transaction records

Receivable, Payable, Prepaid Expense, Deferred Revenue, Other Asset, and Other Liability rows are persisted in `txn_supplemental_line` and linked to canonical `txn` records by stable ID.

The canonical transaction command/view types carry supplemental line DTOs. `TransactionEntryService.enter(...)` persists them atomically with a new transaction, `update(...)` replaces them atomically with the edited transaction, and `load(...)` returns them for editor repopulation.

The service rejects:

- unsupported kinds;
- missing descriptions;
- negative amounts;
- unpaired start/end dates;
- start dates after end dates.

These rows are transaction-attached details, not a reintroduction of the eliminated generic Schedules module or a sidecar ledger.

## Correction operations

The Journal exposes a real correction action only for a selected or loaded durable transaction.

- Under `DIRECT_EDIT`, the action is labeled **Delete**, requires confirmation, and calls `TransactionCorrectionService.delete(...)`.
- Under other correction policies, the action is labeled **Reverse**, requires confirmation, and calls `TransactionCorrectionService.reverse(...)` using the active period date.
- Period and reconciliation protections remain enforced by the service.

There is no disabled placeholder Delete control for an unsaved record; action availability follows whether a durable transaction is selected or loaded.

## Resizable layout

The Journal workspace uses nested visible `SplitPane` dividers between:

1. the grouped Journal and the integrated editor;
2. editor header, entry-line table, and detail region;
3. Additional Details and Supplemental Details.

Divider positions are remembered for the active company. Journal, accounting-line, and supplemental tables use unconstrained column resizing, sortable/resizable/reorderable columns, per-company table state, and both horizontal and vertical scrolling when content exceeds the viewport.

## Global command behavior

When Journal is active:

- global **New** starts a new integrated journal entry;
- global **Save** saves the current New/Edit entry;
- global **Post / Validate** performs validation without introducing a separate posting workflow;
- Journal toolbar actions and global actions call the same methods.

## Persistence authority

The dependency direction remains:

```text
JournalWorkspacePanel
    -> TransactionEntryService / TransactionCorrectionService / reference-data service
        -> JPA/domain validation
            -> H2
```

The Journal panel contains no SQL, no static authoritative transaction collection, and no alternate transaction model.
