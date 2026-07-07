# Transaction Editor, Ledger Register, and Journal Pane design

## Purpose

This document records the clarified requirements for transaction entry, ledger-register navigation, and the new Journal Pane.

## Transaction Editor modes

The Transaction Editor has two modes: **New** and **Edit**.

### New mode

New mode creates a new transaction.

The opener may prefill fields from any relevant context, including:

- a selected ledger row;
- a selected bank-import or bank-statement row;
- selected account, fund, or budget filters;
- active period;
- selected journal context;
- selected inventory, asset, open-item, or reconciliation context.

Prefill is only an editor convenience. The transaction is not authoritative until saved through the canonical transaction service.

### Edit mode

Edit mode updates an existing transaction.

The opener must provide the transaction ID. Edit mode is not an upsert and must not match likely duplicates by date, payee, reference, or amount.

When policy allows direct editing, Edit mode modifies the existing `Txn`/`TxnSplit` records through the canonical transaction service. When policy or state protection prevents direct editing, the UI must surface the documented correction path.

## Ledger Register

The Ledger Register is read-only. Editing is done through Transaction Editor or by opening the Journal Pane.

The Ledger Register has exactly these primary transaction actions:

- **New**
- **Open Selected**

Behavior:

- **New** opens Transaction Editor in New mode.
- **Open Selected** opens Transaction Editor in Edit mode for the selected transaction ID.
- **Open Selected** is disabled unless exactly one transaction is selected.

The Ledger Register also provides **Inspect Journal**, which opens the separate Journal Pane.

## Journal Pane

The Journal Pane is a separate workspace tab with its own `AppPanelId`.

The Journal Pane presents transactions in traditional accounting general-journal form:

- transaction date;
- transaction number or ID;
- transaction memo/reference;
- account lines;
- debit;
- credit;
- line memo/details where applicable.

The Journal Pane opens unfiltered by default but may be centered at a supplied transaction ID. It includes date and text filters that can be applied on the fly against canonical transaction projections.

Sources:

- Ledger Register → Inspect Journal opens the entire journal, centered on the selected transaction.
- Left Navigation → Accounting → Inspect Journal opens the Journal Pane in the same general form, unfiltered by default.

## Editing from the Journal Pane

Users do not edit directly in the journal grid. The grid is read-only and each displayed line is a flattened view of the canonical transaction journal projection, not an independent ledger model.

When the user chooses to edit an existing journal transaction, the Journal Pane opens Transaction Editor in Edit mode with the transaction ID.

When the user chooses to create a new transaction from the Journal Pane, it opens Transaction Editor in New mode, optionally prefilled from the current journal filter/context.

All validation and saving remains through the canonical transaction service.

## Supplemental transaction records

The Journal Pane and Transaction Editor must make provision for supplemental transaction records attached to a journal entry. These records are not the eliminated Schedules panel. They are domain-specific details connected to the transaction, such as:

- inventory movement detail;
- open-item or deferral detail;
- asset acquisition or depreciation detail;
- bank clearing/reconciliation detail.

Supplemental records must be persisted through the owning domain service and linked to the canonical transaction or transaction line by stable IDs.


## P03-C2 implementation note

P03-C2 adds `JOURNAL_PANE` as a first-class workspace panel. Left Navigation under Accounting exposes **Inspect Journal**, and Ledger Register **Inspect Journal** opens that same pane, centered at the selected transaction when one is selected. The Journal Pane queries `TransactionEntryService.search(...)` and `TransactionEntryService.journalView(...)`, flattens canonical journal projections for display, and routes New/Edit actions back to Transaction Editor mode contexts. The journal table uses sortable, resizable, reorderable columns; persists column widths, order, and sort state under the active company key; keeps the table in a visible `SplitPane`; and formats debit/credit displays with a currency symbol and two decimals while leaving canonical `BigDecimal` values unchanged. Supplemental transaction record display remains a provisioned design area for the owning future Inventory, Asset, Open Item, Deferral, Banking, or Reconciliation slice; P03-C2 does not create a parallel supplemental persistence model.

## P03-C3 implementation note

P03-C3 adds a Transaction Editor **Delete** affordance for durable transactions loaded in Edit mode or just saved through the canonical service. The button is disabled in New mode because no authoritative record exists yet. Direct-edit correction policy confirms and delegates to `TransactionCorrectionService.delete`, which performs period/reconciliation checks and audit snapshot work inside the authoritative service boundary. Non-direct correction policy confirms a reversing entry instead, using the active accounting period date as the default reversal date through `TransactionCorrectionService.reverse`.
