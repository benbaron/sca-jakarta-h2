# P27-C9 Exchequer CSV review and journal import

Import -> Import Exchequer CSV opens an alternate page containing Proposed Entries, Edit Entry, and Original Source and Diagnostics work pages. Back to Import and Resume Exchequer Review preserve the same draft. Choose Another CSV confirms discard of an unresolved draft. Closing the workspace/application uses the existing unsaved-work protection.

Every entry starts with its literal transaction date. Never validate/filter by report quarter, year, reporting range, filename or calculated report-inclusion flag. Users may deliberately edit the proposed date and memo/payee, map accounts/Funds and optional Budget/Event/Merchant/expense function, edit debit/credit/notes, add/remove accounting lines and delete proposed entries before commit. Removal affects only the transient proposal. Original CSV bytes and existing Journal records are unchanged.

## Reader and source facts

ExchequerReportCsvImporter reads the 2026-03 SCA Exchequer Ledger worksheet layout: seven header rows, opening balance/date and four split blocks (AG:AM, BG:BM, CG:CM, DG:DM). It returns immutable source facts through ImportPreviewService and performs no database writes. It supports quoted commas, escaped quotes, multiline fields, BOM, strict UTF-8 or an explicitly chosen encoding, with bounds on bytes/records/fields/columns/cells. Bank CSV retains its independent 128-column cap when reusing CsvRecordReader.

Default-only/template/helper slots do not become entries. Zero-value/note-only records remain source evidence and never become zero-value journal entries. Invalid business rows remain available for correction or removal. Known statement-status text such as not cashed remains verbatim without an invented clearing date/state. Signed BigDecimal values retain negative adjustments. Source labels are not native account classifications. Opening balance, running balance, timing flags, reversal links, item numbers, quantities and raw text remain evidence, not fabricated openings, operational schedules, cleared facts or lifecycle commands.

Donor inspection: SCFX-SCA-Data-Exchange-Format SCLX_Ledger_IO_v14_canonical_import.bas confirms four split groups and excludes purpose-only defaults. Its absolute-value conversion and period filtering are not adopted. No embedded macro, formula or instruction is executed. Journal design already adapted from NonprofitAccounting JournalEntryWorkspaceFX supplies the stable-ID, one-sided-line, totals and focus-preserved editing approach; donor persistence/static company state is not imported.

## Proposed entries and acceptance

ExchequerImportService.preview constructs a signed bank-side line plus opposite category lines, without guessed mappings. All proposed master references start unmapped. For a zero-net monetary transaction, only meaningful category lines are proposed. A malformed proposal may be edited into a valid balanced command or removed. Apply Mappings to Same Source Labels copies the selected account to exact matching bank/category labels and the selected Fund to exact matching source Fund labels. This is explicit user action, never cross-chart code matching. Refresh Choices retains edits but invalidates review acknowledgement.

The review uses active selected-chart account and active company reference choices, live totals/validation, company money/date formatting, typed date/amount sorting, independently scrolling tables/forms, remembered table/divider state and hover text. Raw numeric input retains four-decimal precision despite two-decimal display normalization. Edits invalidate the batch review acknowledgement. Every remaining proposal must have a valid date, at least two meaningful one-sided lines, account/Fund IDs and balanced totals. Original diagnostics stay available; corrected canonical inputs determine acceptance readiness.

Commit Reviewed Entries and File/toolbar Save share the same explicit confirmation and non-cancellable asynchronous commit. ExchequerImportService owns BOOKKEEPING_WRITE/current-company authorization before validation, unchanged-source verification, a company lock and one outer H2 transaction. It refreshes/rechecks the active selected chart and active master references before calling TransactionEntryService's caller-owned enter boundary. Canonical ownership, period, precision, supplemental and accounting rules remain authoritative. A failure identifies its source line/worksheet row, rolls back the entire batch and retains the draft. Success names saved Journal transaction IDs; corrections/deletions thereafter use Journal.

Each accepted source row retains an immutable AuditEvent receipt, original source record, source SHA-256 and edited command alongside the canonical transaction audit. Deterministic company/source-field receipt identities prevent repeat acceptance after restart, file renaming, correction or transaction deletion. Exact repeats cannot be overridden. A similar existing date/total requires explicit acknowledgement after reviewing Journal; it is never silently discarded or merged. No schema change, sidecar persistence or second ledger is introduced.

## Reader API

```java
var report = new ExchequerReportCsvImporter().read(Path.of("ledger.csv"));
// report.rows(), report.messages(), report.hasBlockingErrors()
// Explicit legacy encoding: read(path, Charset.forName("windows-1252"))
```

The reader API remains non-mutating. The production review and commit composition supersedes the initial class-only boundary. Only synthetic financial fixtures are committed; uploaded source data is never published.

## User checks

Use a disposable company/database with an active chart and open dates. Open Import -> Import Exchequer CSV, inspect Original Source and Diagnostics, and confirm that entries outside the report heading's quarter/year retain their dates. Map a bank/category and Fund, apply matching-label mappings if appropriate, then edit a date/memo/amount, add/remove a line and delete a proposal. Switch pages and use Back/Resume; edits must remain. Resize the window and drag the header/editor dividers; access all columns/fields in both directions.

An unmapped, invalid or unbalanced proposal must prevent commit. After explicit review and confirmation, verify the remaining entries/amounts/dates in Journal. Repeat the source import: accepted source rows must be identified and blocked. Confirm that a closed date, stale chart/master or changed source fails without partial writes. After successful acceptance use the normal Journal correction workflow rather than deleting source history.

## Verification

Parser-only revision 1404a40e passed local clean verification and Linux/Windows CI. Those results do not validate this review/commit extension. Extension baseline compile passed (22.6s); existing reader/preview/transaction baseline passed. Initial service gate passed six tests, expanded service gate seven tests. Initial UI attempts exposed display-socket access and test skin timing; these are not accepted display results. Final focused/display/clean and updated-head CI results are recorded in PLAN before handoff.

## Additional regression coverage (P27-C10)

PR #432 is merged at c34db345 in current main; the original parser, nine service and two JavaFX review tests remain. ExchequerImportValidationTest adds parameterized coverage for nine invalid edited-command cases (unbalanced, zero, both-sided, negative, missing Fund/date, one line, overlong memo/notes), seven stale mapping cases (Fund, Payee, Budget, Event, Merchant, non-posting account, inactive header account), correction/deletion of malformed-date source proposals and unknown/empty selections. Every rejected batch verifies that earlier valid entries and all audit receipts roll back, then retries the same review successfully after correction. Accepted optional mappings and unchanged source bytes are verified. All input is synthetic.

This is a test-only extension; no accounting, UI or schema behavior changes. Existing owner checks above still apply. Focused command: mvn -Dtest=ExchequerImportValidationTest,ExchequerImportServiceTest,ExchequerReportCsvImporterTest,CsvRecordReaderTest test. Full clean and new-head CI results are recorded in PLAN before handoff.
