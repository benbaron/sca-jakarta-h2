# Chart of Accounts: Category + Subtype

Each Account includes:
- `code` (account number / canonical code)
- `name` (short display name)
- `description` (long description)
- **category** (`account_type` column): ASSET / LIABILITY / EQUITY / INCOME / EXPENSE / BANK
- **subtype** (`subtype` column): coarse functional classification used to decide which schedule tabs apply
- `opening_balance`: initial value when adopting the chart (optional; defaults to 0)

## Why both?
- Category drives:
  - normal balance (debit/credit)
  - reporting placement (Balance Sheet vs Income Statement)
- Subtype drives:
  - what subsidiary schedule UI is applicable (Receivables, Payables, Prepaids, etc.)
  - validation rules (schedule required/optional)

## Overrides
Subtype should not be the only mechanism.
Use `account_schedule_requirement` rows for per-account overrides (required/optional, multiple schedules, or exceptions).
