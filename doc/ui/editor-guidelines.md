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
- Save, reverse, replace, and load/edit policy belong to later P03 slices and must continue through `TransactionEntryService` and correction services.
