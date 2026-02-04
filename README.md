# SCA Ledger (H2 + Jakarta) starter project

## What this is
A minimal Maven/Jakarta/Hibernate/Flyway/H2 skeleton that supports:
- Explicit Chart of Accounts (accounts, aliases, report-section mapping, schedule requirements)
- Funds as a required dimension on transaction splits
- Signed split amounts (derive DR/CR for journal view via Account.normalBalance)
- Explicit FundTransfer object linked to posted ledger transaction

## Run
From this directory:

    mvn -q test
    mvn -q exec:java

This will:
1) Create / migrate the H2 file DB at ./data/sca-ledger.* (via Flyway)
2) Boot a CDI container (Weld SE)

## Next steps you’ll likely add
- A PostingService that validates balancing rules and creates Txn + TxnSplit rows
- Import/export for Chart of Accounts and Funds (CSV/JSON)
- JavaFX UI (Ledger grid + split editor + “Show Journal” view)

## Added in v2
- PostingService: validates balancing and posts Txn + TxnSplit (signed amounts)
- JournalLine DTO + PostingService.journalForTxn(txnId): derives DR/CR for display
- CoaFundIo: CSV import/export for Funds, CSV import/export for Accounts, JSON export for CoA (minimal)
- FundBalanceService: “as of” fund balances (simple sum of signed splits; refine later)

## Notes
- You must seed Accounts/Funds before PostingService example will work.
- A later iteration can add a seeding command or Flyway seed migration.

## CLI usage
Help:
    mvn -q exec:java -Dexec.args="--help"

Seed minimal dataset:
    mvn -q exec:java -Dexec.args="seed"

## UI prototype (JavaFX)
Run:
    mvn -q -Pui javafx:run

UI: Ledger Register + Transaction Editor, tabbed Schedules, Budget Editor + Budget vs Actual, Asset Register + Depreciation Runs, Reports Library.
# sca-jakarta-h2
