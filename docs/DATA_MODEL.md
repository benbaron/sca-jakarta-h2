# Data Model (current)

## Core accounting
### chart_of_accounts
Chart metadata; accounts belong to a chart (supports versioning).

### account
- code, name, type (asset/liability/equity/income/expense)
- normal_balance (DEBIT/CREDIT)
- parent_id for hierarchy
- is_posting/is_active + effective dates

### txn (transaction header)
- txn_date, payee, memo
- bank_account_id (optional reference to Account)

### txn_split (transaction line/split)
- txn_id, account_id, fund_id (required)
- amount_signed (DECIMAL(19,4))
- optional activity, merchant, NMR flag, notes

## Funds
### fund
- code, name, type, restrictions
- parent_id hierarchy, active/effective dates

### fund_transfer
Explicit fund transfer metadata linked to a posted transaction (posted_txn_id).

## Classification / reporting
### report_section + account_report_section
Optional mapping for custom report layouts.

### schedule_kind + account_schedule_requirement
Defines whether an account requires schedule lines (receivables, payables, etc.).

## Future schedules (V2 migration tables)
### schedule_item
Operational schedule rows (receivable/payable/prepaid/etc.)

### schedule_link
Links schedule items to origin/clearing splits for forward tracing.
