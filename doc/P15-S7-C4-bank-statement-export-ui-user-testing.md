# P15-S7-C4 bank-statement export UI owner checklist

Use a disposable migrated H2 database with at least two companies, at least two configured bank
accounts in one company, durable reviewed statement rows, source and derived FITIDs, optional and
missing balance metadata, a matched row, and a correction pair.

## Scope and navigation

- [ ] Banking labels the route **Review / Export Statements…** and opens Bank Transactions.
- [ ] Bank Transactions lists only active configured accounts owned by the active company.
- [ ] The default date range begins on the first day of the active accounting month and ends on the active date.
- [ ] Switching companies and reopening Bank Transactions refreshes account choices and durable rows; an account from the prior company cannot export.
- [ ] The review table and all export controls remain usable at 1366 x 768 without clipping the action buttons.

## Explicit formats

- [ ] **Export Bank CSV…** writes the frozen normalized CSV 1.0 file and it previews through direct normalized-CSV import without a mapping profile.
- [ ] **Export OFX 2.x…** writes bare governed OFX 2.x XML that previews through the strict production parser.
- [ ] **Export QFX…** writes the governed QFX 2.x header/XML profile that previews through the strict production parser.
- [ ] Each chooser proposes the correct `.csv`, `.ofx`, or `.qfx` extension and the filename identifies company and date range.
- [ ] Each output contains only the selected company, configured account, and inclusive date range, independent of table-row selection.

## Overwrite, background work, and results

- [ ] Cancelling the file chooser writes nothing and reports cancellation.
- [ ] Selecting an existing file requires explicit replacement confirmation; cancelling leaves its bytes unchanged.
- [ ] Export runs without freezing navigation or the JavaFX window, and all three export buttons remain disabled while it is active.
- [ ] Success details show format, company, portable configured-account identity, date range, rows, bytes, SHA-256, destination, and every warning/path-coded message.
- [ ] An empty range or invalid/inactive/cross-company account fails clearly and commits no output file.
- [ ] Missing optional metadata is disclosed; no balance, FITID, or other source fact is silently fabricated.

## Authority and round trip

- [ ] Export creates or changes no canonical ledger transaction, split, durable review row, match, or audit fact.
- [ ] Normalized CSV export/import/export preserves governed facts byte-for-byte in the established round-trip scenario.
- [ ] OFX/QFX output remains labeled and structured as bank-statement activity, not double-entry ledger export.
- [ ] No **Export Selected**, direct selected-row serializer, File-menu bank export, or Import/Export Jobs destination is visible.

## Owner acceptance

- [ ] I completed every check above and found no blocking issue.
