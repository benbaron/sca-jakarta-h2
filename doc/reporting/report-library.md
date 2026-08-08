# Report Library

## Purpose

The Report Library is the single production surface for financial and operational reports. Reports read authoritative H2-backed query services and semantic templates; they do not create a second ledger or reporting data store.

## Typed catalog

`ReportDefinition` is the selectable report catalog. Every entry identifies:

- a stable report ID and display name;
- core-service or semantic-template execution;
- as-of-date or date-range behavior;
- whether a fund filter applies;
- whether a maximum-row parameter applies.

A report must not appear in the catalog unless a real core service projection or semantic template exists. The UI must not expose selectable “Report not implemented” entries.

## Request and execution boundary

P16-S6 aligns the default Report Library date request with fiscal authority. When no explicit `DateRangeContext` range is supplied, the default start is the active company fiscal-year start and the default end/as-of is the end of the shell-selected accounting period, both from the same immutable `FiscalPeriodRange` used by budget comparison. An explicit report range remains user-controlled. Preview and every export format continue to consume the exact same immutable `ReportRequest`; export does not recalculate dates from the wall clock.

`ReportRequest` is the immutable validated parameter set shared by preview, export, and Journal drill-through. It contains:

- the typed report definition;
- normalized start/end or as-of dates;
- an All Funds selection or a persisted fund ID/code/name;
- a bounded row limit where applicable.

`ReportExecutionService` executes that request through `FinancialReportService` or `WorkbookSemanticReportService` and returns one `ReportResult`. Export reuses the current result when its request still matches the controls; otherwise it executes the newly validated request once.

## Parameters

- Trial Balance and Balance Sheet use an as-of date.
- General Ledger Detail, Income Statement, Workbook Summary, Transactions List, All Checks/Transfers, and Fund Transfers use a date range.
- Fund filtering is available where the underlying report has a meaningful single-fund projection.
- General Ledger and semantic ledger-list reports expose a maximum row count from 1 through 5000.
- Fund Transfers intentionally uses all funds because comparison between funds is the subject of the report.

## Formatting and exports

Visible core report previews use active-company date and money preferences through `CompanyUiFormat` and `FinancialReportDisplayFormat`.

CSV remains machine-readable and stable:

- dates remain ISO-8601;
- numeric values remain unadorned decimal strings;
- company currency symbols and grouping are not written into CSV numeric fields.

TEXT, CSV, PDF, and XLSX exports remain available. PDF/XLSX adapters consume the same text and CSV generated from the validated request.

## UI state

The Report Library split-pane divider is stored in company-owned UI state under the `reportLibrary.` prefix. The panel uses the global date range as an initial default, but report-specific date controls form the actual request.

## Validation

Required automated coverage includes:

- catalog completeness and no placeholder entries;
- date/fund/row-limit request validation;
- selected-fund filtering against authoritative ledger data;
- company-formatted visible text with stable raw CSV;
- source guardrails for typed catalog/request use and company-owned divider state.

Desktop validation must confirm report selection, parameter visibility, fund loading, preview, all export formats, Journal drill-through context, split-pane resizing, and laptop-width behavior.
