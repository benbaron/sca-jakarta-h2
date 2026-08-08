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

Reconciliation accepts two deliberately separate statement-source paths:

- **Manual Entry** records one user-entered statement fact for the selected mutable reconciliation session.
- **Import Bank Statement…** opens the governed Import Preview workspace, locked to the reconciliation's exact configured bank account. Supported production file families are OFX 2.x, governed QFX, mapped CSV, and normalized CSV.

Reconciliation does not parse or persist imported CSV/OFX/QFX files itself. QIF is not an enabled production format because the governed Import Preview parser contract does not define QIF.

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

## P05-S1 implementation note — bank configuration model

P05-S1 implements the model-level portion of this design. A configured bank is a company-owned `Bank` record for the financial institution and stores the institution name, routing number, address, website, contact name, contact phone, contact email, notes, active flag, and timestamps.

A new configured bank account write links one `CompanyBankAccount` to exactly one `Bank` and exactly one chart-of-accounts `Account`. Existing legacy `CompanyBankAccount` rows may remain without those links until migrated or edited, but new P05 service writes require stable bank and account IDs.

The linked chart account must be a posting cash-bank ledger account with `AccountType.BANK`, `NormalBalance.DEBIT`, and `AccountSubtype.CASH`. Configured bank accounts store masked account number, nickname, opening date, opening balance, preferred statement import format, OFX bank/account identifiers, notes, and active flag.

This model is configuration metadata only; it does not create accepted accounting transactions and does not act as a second ledger. P05-S2 owns the JavaFX Banking panel under Accounting, P05-S3 owns import normalization and review wiring, and P05-S4 owns cleared-state mapping from reviewed bank statement facts to canonical ledger bank lines.

## P05-S2 implementation note — Banking panel

P05-S2 adds a first-class Banking panel under Accounting. The panel loads bank and configured bank-account rows from H2 through `BankConfigurationService`, saves Bank create/edit operations through that service, and creates configured bank accounts either from a selected qualifying Chart of Accounts account or by first creating a BANK/DEBIT/CASH account through `AccountAdminService`.

The panel exposes a disabled Delete explanation instead of a destructive delete operation in this slice: bank configuration records can be deactivated to preserve statement import and reconciliation history until a later audited delete/deactivate policy is specified. Its tables use sortable, resizable, reorderable columns with active-company-keyed column width, order, and sort persistence, and its date and money entry fields accept common input forms and normalize display on focus loss.

## P05-S4 implementation note — cleared state on ledger bank lines

P05-S4 adds cleared-state columns to canonical `txn_split` rows. A matched imported statement line may mark only the split whose account equals the configured bank account's linked Chart of Accounts account. The imported statement row records the matched transaction for traceability, but the cleared flag and cleared date live on the ledger split.

## P06-S1 reconciliation workflow note

P06 starts by removing approval/rejection semantics from the Reconciliation Runs workspace. Reconciliation remains a comparison workflow over configured bank accounts, imported/manual statement facts, and canonical ledger bank lines; it does not create an approve/reject queue or write approval decisions. The current run list may record started/completed/failed run facts while later P06 slices add configured-account comparison, mismatch resolution, and saved unresolved reconciliation reports.

## P06-S2 configured-account comparison note

P06-S2 adds `ReconciliationComparisonService`, which validates that reconciliation uses an active configured bank account linked to both a Bank record and a BANK/DEBIT/CASH chart account. The service reads canonical `TxnSplit` bank lines and reviewed `BankStatementLine` facts, calculates beginning balance, activity, ending book balance, and cleared-only balance, and produces comparison lines for exact matches, unmatched ledger lines, unmatched statement lines, amount mismatches, date mismatches, and cleared-state mismatches.

The Bank Reconciliation panel now exposes a configured-account selector, from date, statement ending date, and a comparison table. When requested, unresolved comparison summaries are saved as factual reconciliation run records. Saving an unresolved report is not an approval or rejection workflow; it records the comparison result so a later workflow can reopen or resolve it.

## P16-S3 finalized-session mutation integrity

The production reconciliation workspace is authoritative through `BankReconciliationWorkspaceService` and native H2 reconciliation sessions/matches. A finalized reconciliation session is an immutable historical accounting fact for normal interactive commands: statement-line import/manual entry, match, unmatch, cleared-state changes, factual difference explanations, unresolved Save, and other live mutation paths reject a finalized session before mutation. Repeating Finalize on the already-finalized session is idempotent and does not downgrade or rewrite it.

Match and unmatch commands validate the exact reconciliation session, company, configured bank account, statement line, canonical transaction, and bank-account split before mutation. A match is persisted symmetrically across the reconciliation relationship and the linked statement/split facts; unmatch requires that exact symmetric pair and removes the relationship atomically rather than clearing an unrelated identifier. Cleared-state changes apply only to the canonical split for the session's configured bank account. A difference explanation is factual reconciliation evidence only; it does not reserve an arbitrary transaction/split relationship or synthesize an adjustment transaction.

Correction after finalization uses the explicit successor command. The finalized predecessor remains unchanged, the successor starts as a new mutable session for the next statement period, and a factual audit event links the successor action to its predecessor, actor, and reason. The JavaFX panel mirrors this authority by disabling live mutation controls for finalized sessions, enabling successor controls only for finalized sessions, and applying the service-returned persisted snapshot after successful commands. Errors are reported without claiming a rolled-back mutation succeeded.

P16-S3 also prevented temporary parsing paths from leaking into logical import provenance while the reconciliation-local file-import path still existed. P16-S4 removes that duplicate file-import path entirely; logical file provenance is now owned only by the governed Import Preview review services. `BankImportBatch` remains a faithful persistence model so historical SCLX restoration is not silently rewritten, and no schema widening or truncation is introduced.

## P16-S4 one governed bank-import authority

The Bank Reconciliation workspace no longer owns CSV splitting, OFX regular-expression parsing, QIF parsing, file reading, or direct persistence of imported `BankImportBatch`/`BankStatementLine` facts. Its file-import command navigates to Import Preview with the exact reconciliation session and configured bank-account identity. Import Preview resolves that account only inside the active company, locks the selector for the reconciliation-origin operation, and exposes only the governed OFX/QFX, mapped CSV, and normalized CSV preview paths.

All file reading, secure parsing, source hashing, identity checks, duplicate handling, preview, and durable review commit continue through the existing Import Preview services and transient `InterchangeTaskController`; Reconciliation does not copy staged rows. After a successful canonical commit, navigation returns to the originating reconciliation session and reloads its authoritative H2 snapshot, which sees the newly durable review facts through the normal statement query. Failed or cancelled previews do not return a false success state.

Manual statement entry remains available because it is not an external file import. A focused `BankStatementManualEntryService` persists that single explicit statement fact inside the reconciliation service's caller-owned transaction while preserving the existing durable representation. QIF remains disabled unless a later slice separately governs a strict QIF contract, parser, fixtures, limits, preview, and atomic review service.
