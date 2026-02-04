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
- Fund editor screen + transfer wizard

## Mid term (Schedules)
- Schedule screens aligned with workbook:
  - Outstanding
  - Asset details (prepaids, deposits)
  - Liability details (deferred revenue, payables)
- Schedule linkage to ledger splits for traceability

## Longer term
- Inventory module (operational + optional postings)
- Fixed assets + depreciation runs (posting automation)
