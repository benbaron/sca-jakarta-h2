# P27-C9 Exchequer CSV review and journal import

Import -> Import Exchequer CSV opens an alternate page containing Proposed Entries, Edit Entry, and Original Source and Diagnostics work pages. Back to Import and Resume Exchequer Review preserve the same draft. Choose Another CSV confirms discard of an unresolved draft. Closing the workspace/application uses the existing unsaved-work protection.

Every entry starts with its literal transaction date. Never validate/filter by report quarter, year, reporting range, filename or calculated report-inclusion flag. Users may deliberately edit the proposed date and memo/payee, map accounts/Funds and optional Budget/Event/Merchant/expense function, edit debit/credit/notes, add/remove accounting lines and delete proposed entries before commit. Removal affects only the transient proposal. Original CSV bytes and existing Journal records are unchanged.

## Reader and source facts

ExchequerReportCsvImporter reads the 2026-03 SCA Exchequer Ledger worksheet layout: seven header rows, opening balance/date and four split blocks (AG:AM, BG:BM, CG:CM, DG:DM). It returns immutable source facts through ImportPreviewService and performs no database writes. It supports quoted commas, escaped quotes, multiline fields, BOM, strict UTF-8 or an explicitly chosen encoding, with bounds on bytes/records/fields/columns/cells. Bank CSV retains its independent 128-column cap when reusing CsvRecordReader.

Default-only/template/helper slots do not become entries. Zero-value/note-only records remain source evidence and never become zero-value journal entries. Invalid business rows remain available for correction or removal. Known statement-status text such as not cashed remains verbatim without an invented clearing date/state. Signed BigDecimal values retain negative adjustments. Source labels are not native account classifications. Opening balance, running balance, timing flags, reversal links, item numbers, quantities and raw text remain evidence, not fabricated openings, operational schedules, cleared facts or lifecycle commands.

Donor inspection: SCFX-SCA-Data-Exchange-Format SCLX_Ledger_IO_v14_canonical_import.bas confirms four split groups and excludes purpose-only defaults. Its absolute-value conversion and period filtering are not adopted. No embedded macro, formula or instruction is executed. Journal design already adapted from NonprofitAccounting JournalEntryWorkspaceFX supplies the stable-ID, one-sided-line, totals and focus-preserved editing approach; donor persistence/static company state is not imported.

## Proposed entries and acceptance

ExchequerImportService.preview constructs a signed bank-side line plus opposite category lines without guessed mappings. The C15 review resolves only unique exact code/name/label matches from eligible active references. Unmatched or ambiguous Account, Fund, Budget, Merchant and Payee source labels appear grey in the same fields as final choices; grey text is not a saved record ID. Supplied optional values require a choice or explicit Clear. For a zero-net monetary transaction, only meaningful category lines are proposed. Apply Mappings to Same Source Labels reuses resolved or explicitly cleared choices for the same nonblank source labels, never across charts or unrelated blank labels. Original source evidence remains unchanged. See [C15](P27-C15-import-suggestions.md).

Apply Entry Changes validates and immediately accepts the whole edited proposal into the batch; Cancel Entry Changes restores its last applied state. In Edit Entry, contextual File/toolbar Save performs Apply. Unapplied edits survive Refresh Review without being accepted and block batch commit until applied or cancelled. Proposed Entries shows Ready in normal foreground, unresolved/unapplied rows in grey and valid Updated rows in bold. Refresh Review reloads references and clears the visual marker without discarding accepted changes or acting as a second acceptance. Ctrl/Cmd/Shift multi-select and Delete Selected Entries (N) remove only selected proposals. Refresh and edits invalidate batch review acknowledgement.

The review uses active selected-chart account and active company reference choices, live totals/validation, company money/date formatting, typed date/amount sorting, independently scrolling tables/forms, remembered table/divider state and hover text. Raw numeric input is preserved for correction rather than silently rounded. P27-C11 supersedes initial four-decimal acceptance: journal posting amounts must be whole cents (at most two meaningful decimal places), and fractional-cent inputs block commit. Quantity precision is independent. Edits invalidate the batch review acknowledgement. Every remaining proposal must have a valid date, at least two meaningful one-sided lines, account/Fund IDs and balanced totals. Original diagnostics stay available; corrected canonical inputs determine acceptance readiness.

Commit Reviewed Entries and File/toolbar Save on Proposed Entries share the same explicit confirmation and non-cancellable asynchronous commit. ExchequerImportService owns BOOKKEEPING_WRITE/current-company authorization before validation, unchanged-source verification, a company lock and one outer H2 transaction. It refreshes/rechecks the active selected chart and active master references before calling TransactionEntryService's caller-owned enter boundary. Canonical ownership, period, precision, supplemental and accounting rules remain authoritative. A failure identifies its source line/worksheet row, rolls back the entire batch and retains the draft. Success names saved Journal transaction IDs; corrections/deletions thereafter use Journal.

Each accepted source row retains an immutable AuditEvent receipt, original source record, source SHA-256 and edited command alongside the canonical transaction audit. Deterministic company/source-field receipt identities prevent repeat acceptance after restart, file renaming, correction or transaction deletion. Exact repeats cannot be overridden. A similar existing date/total requires explicit acknowledgement after reviewing Journal; it is never silently discarded or merged. No schema change, sidecar persistence or second ledger is introduced.

## Reader API

```java
var report = new ExchequerReportCsvImporter().read(Path.of("ledger.csv"));
// report.rows(), report.messages(), report.hasBlockingErrors()
// Explicit legacy encoding: read(path, Charset.forName("windows-1252"))
```

The reader API remains non-mutating. The production review and commit composition supersedes the initial class-only boundary of this still-open slice. Only synthetic financial fixtures are committed; uploaded source data is never published.

## User checks

Use a disposable company/database with an active chart and open dates. Open Import -> Import Exchequer CSV, inspect Original Source and Diagnostics, and confirm that entries outside the report heading's quarter/year retain their dates. Map a bank/category and Fund, apply matching-label mappings if appropriate, then edit a date/memo/amount, add/remove a line and delete a proposal. Switch pages and use Back/Resume; edits must remain. Resize the window and drag the header/editor dividers; access all columns/fields in both directions.

An unmapped, invalid or unbalanced proposal must prevent commit. After explicit review and confirmation, verify the remaining entries/amounts/dates in Journal. Repeat the source import: accepted source rows must be identified and blocked. Confirm that a closed date, stale chart/master or changed source fails without partial writes. After successful acceptance use the normal Journal correction workflow rather than deleting source history.

## Verification

Parser-only revision 1404a40e passed local clean verification and Linux/Windows CI. Those results do not validate this review/commit extension. Extension baseline compile passed (22.6s); existing reader/preview/transaction baseline passed. Initial service gate passed six tests, expanded service gate seven tests. Initial UI attempts exposed display-socket access and test skin timing; these are not accepted display results. Final focused/display/clean and updated-head CI results are recorded in PLAN before handoff.
