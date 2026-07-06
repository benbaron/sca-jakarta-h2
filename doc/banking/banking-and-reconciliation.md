# Banking and reconciliation design

## Purpose

This document records the clarified requirements for the Banking panel, bank account configuration, statement import, cleared state, and bank reconciliation.

## Banking panel

Banking is a first-class Accounting function and appears in the Left Navigation Pane under the Accounting subsection.

The Banking panel lets the user create and maintain Bank records and linked bank accounts.

## Bank record

A Bank record represents the financial institution, not the chart-of-accounts account.

Fields:

- bank name;
- routing number;
- institution address;
- website;
- contact name;
- contact phone;
- contact email;
- notes;
- active/inactive status.

## Bank account record

Each actual bank account is linked to:

- one Bank record;
- one Chart of Accounts account.

Fields:

- bank ID;
- chart-of-accounts account ID;
- masked account number;
- account nickname;
- opening date;
- opening balance;
- statement import format preference;
- OFX bank ID where applicable;
- OFX account ID where applicable;
- notes;
- active/inactive status.

## Chart of Accounts integration

Bank-account entries are distinguished by their separate Chart of Accounts code.

The linked chart-of-accounts account must have:

- account type = `BANK`;
- normal balance = `DEBIT`;
- financial statement class = `CASH`.

The Banking panel must give both options:

1. create the corresponding Chart of Accounts account automatically; or
2. select an existing Chart of Accounts account that satisfies the Bank/Debit/Cash rules.

## Configured bank accounts

For reconciliation and statement import, "configured bank accounts" means only Chart of Accounts bank accounts linked to a Bank record through the Banking panel. A standalone account with type `BANK` is not sufficient.

## Bank Reconciliation panel

The Bank Reconciliation panel lets the user select any configured bank account.

For the selected bank account and statement/as-of date, it reads all ledger transactions involving that bank account.

## Balance calculations

For the selected bank account and date, reconciliation calculates from the most recent period close before the statement date through the selected date:

1. beginning balance from the most recent closed period ending before the statement date;
2. activity after that close date through the statement date;
3. ending book balance at the statement date.

It produces two balances:

- all relevant transactions;
- only transactions whose bank ledger line is marked cleared.

Balances are recalculated rather than read from immutable period-balance snapshots.

## Cleared state

Cleared state is stored on the ledger transaction line involving the bank account.

Imported bank statement records do not replace the ledger. Their matching rules map automated import records to internal ledger lines and propose cleared-state changes.

If a transaction contains multiple bank-account lines, each bank-account ledger line is reconciled according to its own bank account and cleared state.

## Statement sources

Bank statements may be provided by:

- manual entry;
- CSV;
- OFX;
- QIF.

## Cleared-state mismatch handling

The reconciliation workflow must offer four options:

1. warn only;
2. overwrite ledger cleared state;
3. never overwrite and require manual resolution;
4. choose per imported line.

## Comparison report

The reconciliation comparison report shows:

- unmatched ledger transactions;
- unmatched statement entries;
- amount mismatches;
- date mismatches;
- duplicate possible matches;
- cleared-state mismatches;
- beginning balance differences;
- ending balance differences;
- record-detail differences.

## Saving and completion

Reconciliation completion does not require exact agreement.

The user may save an unresolved reconciliation report.

A new reconciliation can either:

- start new; or
- edit an existing reconciliation at the user's option.

The current approve/reject run model is replaced entirely. Reconciliation has no approval/rejection workflow.
