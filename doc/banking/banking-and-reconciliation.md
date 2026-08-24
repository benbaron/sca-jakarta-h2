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

The account classification has three independent meanings:

- accounting type = `ASSET`;
- operational function = `BANK`;
- financial/schedule subtype = `CASH` when the balance is a cash or cash-equivalent balance; and
- normal balance = `DEBIT`.

The usual checking, savings, and similar deposit account is therefore `ASSET :: BANK :: CASH`.
`ASSET :: BANK :: !CASH` is also valid: it remains eligible for bank-statement, cleared-state, and
reconciliation operations, but it is excluded from cash-only presentation such as Book Cash and the
Balance Sheet cash subtotal. An `ASSET :: CASH` account without the `BANK` function is cash for financial
reporting (for example petty cash) but is not a bank-statement/reconciliation account.

The Banking panel must give both options:

1. create the corresponding Chart of Accounts account automatically as `ASSET :: BANK :: CASH`; or
2. select an existing `ASSET` / `BANK`-function / `DEBIT` account. `CASH` is not required for this second
   option because cash classification and banking behavior are deliberately independent.

## Configured bank accounts

For reconciliation and statement import, "configured bank accounts" means only Chart of Accounts accounts with the `BANK` function that are linked to a Bank record through the Banking panel. A standalone account with the `BANK` function is not sufficient.

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

The linked chart account must be a posting asset bank ledger account with `AccountType.ASSET`, `AccountFunction.BANK`, and `NormalBalance.DEBIT`. `AccountSubtype.CASH` remains the normal classification for ordinary deposit accounts, but is not a prerequisite for banking operations. Configured bank accounts store masked account number, nickname, opening date, opening balance, preferred statement import format, OFX bank/account identifiers, notes, and active flag.

This model is configuration metadata only; it does not create accepted accounting transactions and does not act as a second ledger. P05-S2 owns the JavaFX Banking panel under Accounting, P05-S3 owns import normalization and review wiring, and P05-S4 owns cleared-state mapping from reviewed bank statement facts to canonical ledger bank lines.

## P05-S2 implementation note — Banking panel

P05-S2 adds a first-class Banking panel under Accounting. The panel loads bank and configured bank-account rows from H2 through `BankConfigurationService`, saves Bank create/edit operations through that service, and creates configured bank accounts either from a selected qualifying Chart of Accounts account or by first creating an `ASSET :: BANK :: CASH` / `DEBIT` account through `AccountAdminService`.

The panel exposes a disabled Delete explanation instead of a destructive delete operation in this slice: bank configuration records can be deactivated to preserve statement import and reconciliation history until a later audited delete/deactivate policy is specified. Its tables use sortable, resizable, reorderable columns with active-company-keyed column width, order, and sort persistence, and its date and money entry fields accept common input forms and normalize display on focus loss.

## P05-S4 implementation note — cleared state on ledger bank lines

P05-S4 adds cleared-state columns to canonical `txn_split` rows. A matched imported statement line may mark only the split whose account equals the configured bank account's linked Chart of Accounts account. The imported statement row records the matched transaction for traceability, but the cleared flag and cleared date live on the ledger split.

P16-S10 makes those existing split facts visible in the canonical Journal projection. Transactions with no `BANK`-function lines display `Not bank`; bank transactions display `Uncleared`, `Cleared`, or `Mixed` according to all `BANK`-function lines. The entry-line detail shows each bank line's cleared date, and an exact company/account-consistent reconciliation match may drill to its durable session. Refresh or reopening Journal re-queries H2 after reconciliation changes; Journal receives no cleared-state mutation authority.

## P06-S1 reconciliation workflow note

P06 starts by removing approval/rejection semantics from the Reconciliation Runs workspace. Reconciliation remains a comparison workflow over configured bank accounts, imported/manual statement facts, and canonical ledger bank lines; it does not create an approve/reject queue or write approval decisions. The current run list may record started/completed/failed run facts while later P06 slices add configured-account comparison, mismatch resolution, and saved unresolved reconciliation reports.

## P06-S2 configured-account comparison note

P06-S2 adds `ReconciliationComparisonService`, which validates that reconciliation uses an active configured bank account linked to both a Bank record and an `ASSET` / `BANK`-function / `DEBIT` chart account. The service reads canonical `TxnSplit` bank lines and reviewed `BankStatementLine` facts, calculates beginning balance, activity, ending book balance, and cleared-only balance, and produces comparison lines for exact matches, unmatched ledger lines, unmatched statement lines, amount mismatches, date mismatches, and cleared-state mismatches.

The Bank Reconciliation panel now exposes a configured-account selector, from date, statement ending date, and a comparison table. When requested, unresolved comparison summaries are saved as factual reconciliation run records. Saving an unresolved report is not an approval or rejection workflow; it records the comparison result so a later workflow can reopen or resolve it.

## P05-C6 account-classification correction

P05-C6 removes `BANK` as a top-level accounting type. `AccountType` now describes only the accounting
class (`ASSET`, `LIABILITY`, `EQUITY`, `INCOME`, `EXPENSE`), while nullable `AccountFunction.BANK`
describes operational banking behavior. `AccountSubtype.CASH` remains the financial/schedule
classification used for cash presentation. Flyway V73 nondestructively converts every legacy
`account_type = 'BANK'` row to `account_type = 'ASSET'` plus `account_function = 'BANK'` and preserves
the existing subtype unchanged.

Bank configuration, statement activity, Journal bank-line state, cleared-state protection, and
reconciliation use the `BANK` function. Balance Sheet asset membership uses `ASSET`; Balance Sheet
cash breakout and Dashboard Book Cash use `ASSET + CASH`. Existing SCA-COA/SCLX interchange versions
retain the literal `BANK` portable type token for compatibility; readers map it to `ASSET + BANK` and
writers map an internal bank-function account back to that portable token.

## P16-S3 finalized-session mutation integrity

The production reconciliation workspace is authoritative through `BankReconciliationWorkspaceService` and native H2 reconciliation sessions/matches. A finalized reconciliation session is an immutable historical accounting fact for normal interactive commands: statement-line import/manual entry, match, unmatch, cleared-state changes, factual difference explanations, unresolved Save, and other live mutation paths reject a finalized session before mutation. Repeating Finalize on the already-finalized session is idempotent and does not downgrade or rewrite it.

Match and unmatch commands validate the exact reconciliation session, company, configured bank account, statement line, canonical transaction, and bank-account split before mutation. A match is persisted symmetrically across the reconciliation relationship and the linked statement/split facts; unmatch requires that exact symmetric pair and removes the relationship atomically rather than clearing an unrelated identifier. Cleared-state changes apply only to the canonical split for the session's configured bank account. A difference explanation is factual reconciliation evidence only; it does not reserve an arbitrary transaction/split relationship or synthesize an adjustment transaction.

Correction after finalization uses the explicit successor command. The finalized predecessor remains unchanged, the successor starts as a new mutable session for the next statement period, and a factual audit event links the successor action to its predecessor, actor, and reason. The JavaFX panel mirrors this authority by disabling live mutation controls for finalized sessions, enabling successor controls only for finalized sessions, and applying the service-returned persisted snapshot after successful commands. Errors are reported without claiming a rolled-back mutation succeeded.

P16-S3 also prevented temporary parsing paths from leaking into logical import provenance while the reconciliation-local file-import path still existed. P16-S4 removes that duplicate file-import path entirely; logical file provenance is now owned only by the governed Import Preview review services. `BankImportBatch` remains a faithful persistence model so historical SCLX restoration is not silently rewritten, and no schema widening or truncation is introduced.

## P16-S4 one governed bank-import authority

The Bank Reconciliation workspace no longer owns CSV splitting, OFX regular-expression parsing, QIF parsing, file reading, or direct persistence of imported `BankImportBatch`/`BankStatementLine` facts. Its file-import command navigates to Import Preview with the exact reconciliation session and configured bank-account identity. Import Preview resolves that account only inside the active company, locks the selector for the reconciliation-origin operation, and exposes only the governed OFX/QFX, mapped CSV, and normalized CSV preview paths.

All file reading, secure parsing, source hashing, identity checks, duplicate handling, preview, and durable review commit continue through the existing Import Preview services and transient `InterchangeTaskController`; Reconciliation does not copy staged rows. After a successful canonical commit, navigation returns to the originating reconciliation session and reloads its authoritative H2 snapshot, which sees the newly durable review facts through the normal statement query. Failed or cancelled previews do not return a false success state.

Manual statement entry remains available because it is not an external file import. A focused `BankStatementManualEntryService` persists that single explicit statement fact inside the reconciliation service's caller-owned transaction while preserving the existing durable representation. QIF remains disabled unless a later slice separately governs a strict QIF contract, parser, fixtures, limits, preview, and atomic review service.
### Explicit reviewed-row ledger acceptance (P16-S8)

Import remains durable review only. In **Bank Transactions**, one unmatched eligible `IMPORTED` row may be explicitly converted into a canonical transaction through **Create Transaction from Reviewed Row…**. The dialog freezes the source identity and configured bank account, pre-fills the bank split, and requires a balanced canonical transaction before commit. `ReviewedStatementAcceptanceService` revalidates the row and protections immediately before one atomic caller-owned transaction creates the ledger transaction, sets the existing `accepted_txn_id` relationship and `ACCEPTED` state, updates batch disposition, and writes factual audit history. Probable duplicates require explicit confirmation; exact duplicates, matched rows, closed periods, finalized reconciliation ranges, stale company/account scope, and late failures do not partially post. Acceptance alone does not clear or reconcile the transaction.

## P05-C7 configured bank-account edit/update correction

P05-C7 closes the gap between the documented Banking maintenance workflow and the production editor. A selected `CompanyBankAccount` is loaded into edit mode by stable database ID. Saving an existing configured account calls `BankConfigurationService.updateBankAccount(...)` rather than attempting another insert. The update preserves the configured-account database ID, immutable portable identity, company ownership, and creation timestamp while allowing the Bank link, qualifying Chart account link, masked account, nickname/display name, opening date/balance, statement format, OFX IDs, notes, and active state to change through the authoritative service transaction.

The service revalidates the selected Bank and Chart account against the active company and the `ASSET` / `BANK`-function / `DEBIT` eligibility rule. The existing `UNIQUE (company_id, account_id)` constraint remains the race-safety backstop, while ordinary duplicate attempts are rejected before persistence with a controlled explanation instead of exposing raw H2 constraint SQL. The Banking editor also uses `CompanyUiFormat` for opening-date and opening-balance parsing, focus-loss normalization, and company-formatted zero initialization.

P05-C7 deliberately does not redefine **Bank Transactions**. That workspace still represents durable imported/review statement facts. P05-C8 is the immediately queued corrective slice that will expose canonical ledger activity for a configured bank account as a distinct view from Statement Review.
