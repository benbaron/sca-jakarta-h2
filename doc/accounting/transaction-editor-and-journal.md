# Transaction Editor, Ledger Register, and Journal

## Transaction Editor modes

The Transaction Editor has two explicit opener modes.

- **New mode** creates a new canonical transaction through `TransactionEntryService.enter`.
- **Edit mode** loads an existing transaction by the caller-provided transaction ID and saves through `TransactionEntryService.update` when the correction policy allows the update.

Edit mode is not an upsert. It must not identify a transaction by probable duplicate matching such as date, payee, reference, or amount. The opener must provide the stable transaction ID.

New mode may be prefilled by explicit opener context, including a selected ledger row, selected bank import row, account, fund, budget, active period, journal context, inventory or asset context, open-item context, or reconciliation context. Prefill context does not change New mode into an update.

## Ledger Register

The Ledger Register is a read-only transaction list. It exposes two primary transaction-entry buttons:

- **New** opens the Transaction Editor in New mode.
- **Open Selected** opens the Transaction Editor in Edit mode for the selected transaction ID.

Open Selected is disabled unless exactly one transaction row is selected. Double-clicking a row follows the same Edit-mode routing as Open Selected.

The register must not perform authoritative accounting calculations in cell factories. It obtains row projections from the service layer and uses the transaction ID from that projection for editor routing.

## Journal direction

The Journal Pane is implemented in a later P03-C2 slice. It will be a read-only general-journal inspection surface. Journal edit and new actions must route back through the Transaction Editor modes described here rather than editing directly in the journal grid.
