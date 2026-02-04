# Import/Export Specification (baseline)

## Funds CSV
Header:
code,name,fund_type,parent_code,is_active,effective_from,effective_to,restriction_text

- fund_type: UNRESTRICTED, TEMP_RESTRICTED, PERM_RESTRICTED, DESIGNATED, EVENT, OTHER
- parent_code: optional
- dates: ISO-8601 (YYYY-MM-DD)

## Accounts CSV
Header:
chart_id,code,name,account_type,normal_balance,parent_code,is_posting,is_active,effective_from,effective_to,description

- account_type: ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
- normal_balance: DEBIT, CREDIT
- parent_code: optional
- chart_id is written on export; on import, a new chart is created and chart_id is ignored.

## CoA JSON export
Current JSON includes:
- charts[] (entities)
- accounts[] (entities)

Planned extensions:
- aliases
- report section mappings
- schedule requirements
