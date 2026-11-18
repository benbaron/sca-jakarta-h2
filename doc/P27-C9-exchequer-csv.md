# P27-C9 Exchequer ledger-sheet CSV importer

This slice delivers a reusable ExchequerReportCsvImporter and typed immutable in-memory report/row/split facts, exposed through ImportPreviewService. It reads the 2026-03 SCA Exchequer Ledger worksheet layout, not arbitrary spreadsheets or bank statements. The source contains seven header rows, an opening balance/date row, up to four accounting split blocks (AG:AM, BG:BM, CG:CM, DG:DM), and calculated/report helpers. Raw source facts and provenance remain available; report helpers are not independent accounting authority.

**Use every entry’s literal transaction date. Never validate/filter by report quarter, year, reporting range or filename.** Heading text is retained as informational source data. Opening balances are facts, not fabricated opening transactions. Blank/default template rows and pure computed/helper rows do not become journal entries. Populated zero-value/cancelled/note-only records remain non-posting records for review, not inferred void/reversal commands. Expense-function defaults alone do not create split lines. Negative values retain their sign; categories are source labels, not guessed native account classifications.

The reader supports quoted commas, escaped quotes, multiline fields, BOM, strict UTF-8 by default and explicitly chosen encodings. It bounds bytes, cells, records, columns and field/record lengths, verifies the input layout, retains malformed business rows with blocking diagnostics, checks dates/money/split completeness and source net-versus-split consistency. Unknown timing or purpose text is never silently reclassified. The bank CSV reader reuses the shared lexer with its original narrow limits.

The importer is a format/preview boundary, like existing format parsers. It performs no database writes and introduces no parallel ledger. Posting requires explicit native account/Fund/Budget/party mappings plus canonical transaction/lifecycle/idempotency/period validation. A single worksheet does not supply missing asset/liability schedules, native item identities, or other workbook tabs. No automatic account creation, reconciliation, reversal, opening entry or SCLX synthesis is inferred. A later UI/commit slice must use existing canonical services and preserve these facts.

Donor inspection: SCFX-SCA-Data-Exchange-Format SCLX_Ledger_IO_v14_canonical_import.bas confirms the four split groups and exclusion of function-only defaults. Its absolute-value conversion and report-period filtering are deliberately not adopted: preserve signs and follow the owner’s literal-date direction. No macro, formula or instruction embedded in CSV content is executed.

Synthetic automated fixtures must cover the actual wide shape without publishing the owner’s financial data. Private-file validation reports counts/diagnostics only and leaves the input bytes unchanged. Owner usage: call new ExchequerReportCsvImporter().read(path), or ImportPreviewService.previewExchequerReportCsv(path); inspect rows/splits/messages before any explicit mapping/acceptance work. No desktop command is added by this class-only request.

Known free-text statement statuses (including `not cashed`) remain available verbatim through Row.statementDateText(), with no invented statement date or cleared flag. Invalid numeric calendar dates still produce blocking diagnostics. Opening balance and computed running balance are distinct source facts; an unavailable computed balance does not invalidate the entry’s monetary inputs.

Initial verification: baseline compile (21.9s) and 16 existing CSV/preview/review tests pass. Final focused reader/import/review gate passes **28 tests, zero failures/errors/skips (52.1s)**. The private owner file reads **1,009 records, 600 worksheet slots, 98 retained source records, 88 monetary candidates, 10 non-posting records, 502 blank/default rows and 401 helper rows**, with no blocking errors. All stated entry dates, January 3 through October 22, remain literal; source bytes/digest are unchanged. No real financial rows are copied into source control or published. Final clean verification passes **1,245 tests, zero failures/errors, 147 expected headless display skips (10:23)**. Packaged-JAR private-input validation passes. Exact published head 1404a40e9170278d155688a896fc647f60c5429b passes Linux and Windows [CI run 44218011323](https://github.com/benbaron/sca-jakarta-h2/actions/runs/44218011323), including full clean/repeat and all display workflows. Draft [PR #432](https://github.com/benbaron/sca-jakarta-h2/pull/432) awaits owner review/merge; no implementation bytes remain unpublished. Final verification receipts are local-only documentation so the accepted CI head stays unchanged.

## Calling the importer

```java
var report = new ExchequerReportCsvImporter().read(Path.of("ledger.csv"));
// Or: new ImportPreviewService().previewExchequerReportCsv(path)
if (report.hasBlockingErrors())
{
    // Present report.messages() and original row facts for correction/review.
}
for (var row : report.rows())
{
    // row.kind(), row.transactionDate(), row.splits(), row.originalFields()
    // are source facts, not accepted native transaction commands.
}
```

For explicitly encoded legacy files use read(path, Charset.forName("windows-1252")). Reading never changes the source or a database. Verify with the synthetic ExchequerReportCsvImporterTest/CsvRecordReaderTest and existing BankCsvReviewServiceTest/NormalizedBankCsvReviewServiceTest/ImportPreviewServiceTest. No UI layout changed; desktop routing remains unchanged.
