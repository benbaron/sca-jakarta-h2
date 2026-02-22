# TODO / Next Steps

## Immediate
- Add CLI commands:
  - export-funds, import-funds
  - export-accounts, import-accounts
  - post-demo (post a sample balanced txn once seeded)
- Add explicit Posting templates for fund transfers, receivables, payables, etc.
- Add DB seed migrations for dev environments (optional alternative to CLI seed)

## Near term (JavaFX)
- Ledger grid UI (SpreadsheetView):
  - Txn header fields + split editor
  - Show Journal drawer
- CoA editor screen + import/export buttons
  - [x] Read-only account list now wired to `AccountLookupService`
  - [ ] Add account create/edit forms + import/export
- Fund editor screen + transfer wizard
  - [x] Read-only fund list now wired to `FundLookupService`
  - [ ] Add fund create/edit + transfer wizard
- Dashboard data wiring
  - [x] Dashboard now calls `FundBalanceService` + lookup services for summary counts
  - [x] Data loads moved off JavaFX UI thread for dashboard/CoA/funds/schedules read-only views
  - [ ] Add chart widgets and drill-down links

## Mid term (Schedules)
- Schedule screens aligned with workbook:
  - Outstanding
  - Asset details (prepaids, deposits)
  - Liability details (deferred revenue, payables)
- Schedule linkage to ledger splits for traceability

## Longer term
- Inventory module (operational + optional postings)
- Fixed assets + depreciation runs (posting automation)
