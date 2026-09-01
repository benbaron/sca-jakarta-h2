# Unified Journal workspace design

## Purpose

The Journal workspace is the single production surface for reviewing canonical transactions and entering or editing journal entries. P03-C6 replaced the separate Ledger Register, Transaction Editor, and Inspect Journal surfaces with one resizable Journal workspace.

The user-visible interaction model was informed by the donor repository's `JournalPanelFX`, `JournalEntryWorkspaceFX`, `GeneralJournalEntryPanelFX`, and `JournalShellNavigation`. The production implementation uses the current H2 schema and service boundaries; donor repositories, static persistence, and alternate ledger models are not production authority.

## One Journal destination

Left Navigation under Accounting exposes one **Journal** item. The canonical panel identifier is `JOURNAL_PANE`.

`LEDGER_REGISTER` and `TXN_EDITOR` remain retired compatibility aliases. Existing callers, saved destination references, dashboard actions, and drill-through paths normalize to `JOURNAL_PANE` and select the same existing Journal tab. They do not create duplicate tabs, separate panels, or independent transaction caches.

## Journal review region

The upper Journal region is read-only and displays one grouped row per canonical transaction. Each row presents the current transaction projection, including:

- transaction date;
- account titles and descriptions in journal-line order;
- funds;
- debit and credit lines;
- transaction ID;
- supplemental-detail count;
- memo, payee, bank, and line-detail text where available;
- authoritative bank-state summary derived from line-level bank/reconciliation facts.

Date and text filters query `TransactionEntryService.search(...)`. A row may be selected and opened for editing by **Edit Selected** or double-click. The selection is a transaction-level selection, never an independently editable ledger line.

P16-S10 projects each line's bank/cleared facts, including `bankCleared`, `bankClearedOn`, and the exact native reconciliation session when applicable. Journal renders the service-owned transaction summary as `Not bank`, `Uncleared`, `Cleared`, or `Mixed`. These values are read-only in Journal; matching and cleared-state mutation remain reconciliation-owned.

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

- Under `DIRECT_EDIT`, the action is labeled **Delete**, requires confirmation when configured, and calls `TransactionCorrectionService.delete(...)`.
- Under other correction policies, the action is labeled **Reverse**, requires confirmation, and calls `TransactionCorrectionService.reverse(...)` using the active period as the default reversal context.
- Closed-period and completed-reconciliation protections remain enforced by authoritative services.

There is no disabled placeholder Delete control for an unsaved record; action availability follows whether a durable transaction is selected or loaded.

## Runtime authorization

P20-S3 makes the canonical service-owned Journal mutation boundary authoritative for bookkeeping authorization.

- `TransactionEntryService.enter(TransactionCommand)` and `update(...)` require `BOOKKEEPING_WRITE` in the active company context before validation or transaction work begins.
- `TransactionCorrectionService.directEdit(...)`, `delete(...)`, and the service-owned `reverse(...)` require the same `BOOKKEEPING_WRITE` permission before correction validation or transaction work begins.
- `load(...)`, `search(...)`, and `journalView(...)` remain non-mutating reads and are not blocked by the write permission.
- caller-owned `EntityManager` entry/reversal/import relationship overloads are transactional seams for an outer import or domain workflow. They intentionally do not perform a second authorization check; the outer service that owns the atomic commit must authorize the whole operation once.
- existing period-close, reconciliation, fixed-asset lifecycle, balanced-entry, company-ownership, and other accounting protections remain in force after authorization succeeds.
- this service tranche does not yet replace legacy/free-form transaction audit actors with authenticated `AppUser.username`, and it does not yet constitute JavaFX command gating or production `UiServiceRegistry` guard wiring. Those remain separate P20-S3 completion work.

A lower-privilege direct call to the guarded service-owned mutation methods fails closed and records the central durable `AUTHORIZATION_DENIED` security fact. UI disabling is explanatory only and is not the enforcement boundary.

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
- global **Validate** performs validation without introducing a separate posting/approval workflow;
- Journal-local toolbar actions and global actions route to the same authoritative editor/service behavior.

## Persistence authority

The dependency direction remains:

```text
JournalWorkspacePanel
    -> TransactionEntryService / TransactionCorrectionService / reference-data service
        -> JPA/domain validation
            -> H2
```

The Journal panel contains no SQL, no static authoritative transaction collection, and no alternate transaction model. Cleared-state facts are projected from authoritative transaction/reconciliation data and are never recomputed or written by the Journal UI.
