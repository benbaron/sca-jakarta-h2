# CLI

The CLI is intended for development utilities and migrations. Later, some commands may be exposed as power tools for treasurers/admins.

## Run examples
Using Maven exec:

- Show help:
  mvn -q exec:java -Dexec.args="--help"

- Seed a minimal dataset:
  mvn -q exec:java -Dexec.args="seed"

- Seed with custom chart version and fund:
  mvn -q exec:java -Dexec.args="seed --chart-version 2026.1 --fund-code GENERAL --fund-name \"General Fund\""


## Seed command
`seed` inserts or updates:
- Chart of Accounts (by chart-name + chart-version)
- Accounts:
  - 1000 Cash / Bank (ASSET, DEBIT)
  - 3000 Fund Transfer Clearing (EQUITY, CREDIT)
  - 4000 Income (General) (INCOME, CREDIT)
  - 5000 Expense (General) (EXPENSE, DEBIT)
- Default Fund (GENERAL by default)
- Optional schedule kinds

Idempotent behavior: rerunning seed will not create duplicates; it updates names/types if needed.
