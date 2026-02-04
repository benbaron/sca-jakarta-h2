# SCA Ledger (H2 + Jakarta) — Design

## Architecture overview
- **Model layer**: JPA entities representing the database tables.
- **Persistence layer**: `Jpa` helper creates `EntityManager` instances for RESOURCE_LOCAL usage.
- **Service layer**:
  - `PostingService`: validates balancing and persists Txn + TxnSplit
  - `FundBalanceService`: computes “as of” balances by fund (refinable)
  - `CoaFundIo`: import/export (CSV + JSON baseline)
- **CLI layer**: Picocli commands for utilities (seed, import/export, etc.).
- **UI layer** (future): JavaFX grid views mimicking spreadsheet tabs.

## Signed split convention
Store signed amounts in `txn_split.amount_signed`.
For display in DR/CR:
- If account normal is DEBIT:
  - debit = max(amount, 0)
  - credit = max(-amount, 0)
- If account normal is CREDIT:
  - credit = max(amount, 0)
  - debit = max(-amount, 0)

Balancing:
- Sum(debits) == Sum(credits) after applying the rule above.

## Funds as a required dimension
- `txn_split.fund_id` is **NOT NULL**.
- A transaction may contain splits for multiple funds.
- Fund transfers are represented as:
  - an explicit `fund_transfer` record (metadata)
  - a posted transaction that moves value between funds via a clearing account
  (implementation template to be finalized)

## Data model summary
See `DATA_MODEL.md` for tables and relationships.

## Import/export strategy
- CSV is the user-facing interchange format (editable in Excel).
- JSON is for versioning / “git the chart” snapshots.
- Import is 2-pass when hierarchy exists (create then link parents).

## Planned UI mapping
Spreadsheet-like “tabs” become navigation sections:
- Ledger
- Outstanding / Schedules
- Asset Details
- Liability Details
- Budget
- Inventory
- Fixed Assets
- Depreciation
- Reports
- Chart of Accounts
- Funds
Each section has a “Show Journal” button or drill-down.
