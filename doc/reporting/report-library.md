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
- whether the report accepts stable-ID fixed-asset or inventory filters.

A report must not appear in the catalog unless a real core service projection or semantic template exists. The UI must not expose selectable “Report not implemented” entries.

## Request and execution boundary

P16-S6 aligns the default Report Library date request with fiscal authority. When no explicit `DateRangeContext` range is supplied, the default start is the active company fiscal-year start and the default end/as-of is the end of the shell-selected accounting period, both from the same immutable `FiscalPeriodRange` used by budget comparison. An explicit report range remains user-controlled. Preview and every export format continue to consume the exact same immutable `ReportRequest`; export does not recalculate dates from the wall clock.

`ReportRequest` is the immutable validated parameter set shared by preview, export, and Journal drill-through. It contains:

- the typed report definition;
- normalized start/end or as-of dates;
- an All Funds selection or a persisted fund ID/code/name;
- a bounded row limit where applicable.
- a typed `None`, `FixedAssetSelection`, or `InventorySelection` domain filter. Asset/item and control-account choices retain persisted IDs; status remains the domain enum.

`ReportExecutionService` executes that request through `FinancialReportService` or `WorkbookSemanticReportService` and returns one `ReportResult`. Export reuses the current result when its request still matches the controls; otherwise it executes the newly validated request once.

## Parameters

- Trial Balance, Fixed Asset Register, and Inventory On Hand & Valuation use an as-of date.
- Balance Sheet uses a comparative range: the beginning column is the day before the selected start and
  the ending column is the selected end date. Income Statement uses the same selected range.
- General Ledger Detail, Income Statement, Workbook Summary, Transactions List, Bank Account Activity, Fund Transfers, Fixed Asset Depreciation History & Schedule, and Inventory Movement History use a date range.
- Fund filtering is available where the underlying report has a meaningful single-fund projection.
- General Ledger and semantic ledger-list reports expose a maximum row count from 1 through 5000.
- Fund Transfers intentionally uses all funds because comparison between funds is the subject of the report.
- The four asset/inventory reports expose optional control-account, asset/item, and current/as-of status filters. The UI choices are loaded from the active company only.

## Fixed-asset and inventory predicates

P16-S15 adds four specialized projections without introducing a reporting store. Their immutable request, semantic value table, preview, TEXT/CSV/PDF/XLSX export, and Journal drill-through context are shared.

### Fixed Asset Register

An asset row qualifies when its `FixedAsset.company.code` is the active company, acquisition date is on or before the as-of date, and optional fund, fixed-asset account, asset ID, and status-as-of filters match. Status as of the report date is reconstructed from immutable Sale/Retirement lifecycle events and their dated canonical reversals. Recognized cost is acquisition cost while the asset is in service and zero after an unreversed final disposition. Accumulated depreciation is opening accumulated depreciation plus completed `FixedAssetDepreciationRun` amounts through the as-of date. Unreversed impairment events through that date are shown separately and included in recognized contra value. Book value is `max(recognized cost - accumulated depreciation - unreversed impairment, 0)`.

Displayed domain gross, contra, and net totals reconcile to natural-balance amounts on the qualifying source assets' fixed-asset and accumulated-depreciation control accounts through the same date. Reversal transactions net through their own canonical splits. Status filters and row limits may display fewer assets than those shared control accounts contain, so the exact displayed-domain-minus-ledger difference is retained. A fund-scoped control total excludes account opening balance because `Account.openingBalance` has no fund dimension; the excluded amount is labeled separately.

### Fixed Asset Depreciation History & Schedule

Completed depreciation rows select persisted runs whose run date is in the inclusive request range and retain their canonical transaction IDs. Impairment rows select immutable lifecycle events; a canonical reversal in the range appears as its own negative row even when the original impairment predates the range. One schedule-summary row per qualifying asset is calculated at the end date using the persisted straight-line basis, accumulated completed runs, unreversed impairment, and remaining depreciable amount. It states the next monthly amount and estimated remaining periods but creates no future transaction and claims no posted future date.

The reconciliation is the same end-date domain contra value and canonical accumulated-depreciation control-account balance used by the register. It is not the sum of the displayed range rows, because that would mix period activity with an as-of balance.

### Inventory On Hand & Valuation

An item qualifies when its `InventoryItem.company.code` is the active company, acquisition date is on or before the as-of date, and optional fund, inventory account, item ID, and current status filters match. Quantity and unit value come from the latest persisted `InventoryMovement` on or before the as-of date. If the first movement is later, the prior quantity is reconstructed exactly as `first resulting quantity - first quantity change`; an item with no movement uses its persisted item facts. Valuation is `quantity × unit value` at four decimal places.

Displayed domain value reconciles to natural-balance canonical activity plus account opening balance on the qualifying inventory control accounts through the as-of date. The report separately totals selected movements without a canonical transaction. Fund-scoped opening balance and all remaining differences stay explicit.

### Inventory Movement History

A movement row qualifies through its active-company-owned item, inclusive movement date range, and optional fund, inventory account, item ID, and current item-status filters. Signed domain value is `quantity change × persisted movement unit value`; receipt/upward adjustment is positive and issue/downward adjustment is negative. A populated transaction link displays the real canonical transaction ID. A null link is labeled **Nonfinancial / no canonical transaction** and is never replaced with a synthetic identifier.

Displayed domain movement net reconciles to canonical natural-balance split activity on the qualifying inventory control accounts within the same inclusive range. The report also shows the exact unlinked-movement net. Account activity without a matching displayed movement, nonfinancial history, filters, and row limits therefore remain visible as differences rather than being inferred away.

## Governed bank and fund-transfer predicates

P16-S13 preserves the legacy stable IDs and workbook template IDs so saved selections and donor traceability remain intact, but the visible names and results must state only what current authoritative facts can prove.

### Bank Account Activity (legacy ID `all-checks-transfers`)

The schema does not contain a durable check-number/type classification that can distinguish checks from every other BANK-account movement. The former **All Checks/Transfers** title is therefore retired. **Bank Account Activity** selects exactly:

- a persisted `TxnSplit` whose account has `AccountType.BANK`;
- a canonical `Txn` owned by the active company;
- a transaction date within the immutable request range, inclusive;
- the selected fund when a fund filter is present.

It returns the BANK split itself, not every split in the transaction. Corrections and reversals appear only when their own canonical BANK splits satisfy the same predicate. The displayed debit and credit totals are calculated only from the returned BANK rows; a row limit therefore limits both the detail and its explicitly labeled displayed total. No memo, payee, reference string, or amount pattern is treated as proof that a movement was a check or transfer.

### Fund Transfers (legacy ID `fund-transfers`)

A row qualifies only when an explicit `FundTransfer` is `POSTED`, has a non-null canonical `postedTxn`, the transaction and both funds belong to the active company, and the transfer date is within the immutable request range, inclusive. Draft, void, ordinary multi-fund journal activity, and unlinked records are excluded.

Each selected transfer expands into two report legs: a negative source-fund effect and an equal positive destination-fund effect. Per-fund totals are the sum of those explicit legs and the all-funds net must be zero. The request row limit selects complete transfer records; both legs and their totals are then emitted so a transfer pair is never truncated into an unbalanced report.

Preview, TEXT, CSV, PDF, XLSX, and Journal drill-through retain the same `ReportRequest`. Export never reclassifies rows or widens company/date/fund scope.

## Formatting and exports

Visible core and semantic report previews use active-company date and money preferences through `CompanyUiFormat` and `FinancialReportDisplayFormat`.

Trial Balance, General Ledger Detail, Balance Sheet, and Income Statement do not display their TEXT
export as the production preview. Each core projection is also mapped directly to an immutable,
UI-neutral table model and rendered as a JavaFX `TableView` with workbook-style colored headers,
section rows, totals, status rows, named columns, wrapping text, and full-text tooltips. General Ledger
Detail exposes every projected field: date, transaction ID, account code/name, fund code/name, payee,
memo, debit, and credit. The model retains typed dates and money until the JavaFX formatting boundary;
the preview never reparses TEXT or CSV.

Balance Sheet and Income Statement add a company-metadata report header above the table. Parent
organization, legal entity, local company/group, currency, and fiscal-quarter context come from the
active `Company`; no organization or branch name is embedded in report code. The Balance Sheet is a
comparative beginning/ending/difference statement with dynamic cash-account breakout, Assets,
Liabilities, calculated Net Worth, period change, Net Income, and a visible reconciliation difference.

Statement categories come from active posting accounts in the active company's active chart, including
zero-activity accounts. Balance Sheet rows use the chart's asset, bank/cash, and liability accounts.
Income Statement income and flat expense rows use their account names. When expense posting accounts
are grandchildren in the chart hierarchy, their parent accounts become category rows and the repeated
posting-account names become dynamic allocation columns. Thus a chart may define three allocation
columns, more, fewer, or none; labels such as Administration, Activity, and Fundraising are data, not
report constants. The Income Statement retains Gross, Cost/Refunds, and Net/Total presentation plus
total-expense, Net Income, change-in-Net-Worth, and difference rows.

Each core preview table remains independently scrollable. Its columns are sortable, resizable, and
reorderable, and their order, width, and sort state are stored for the active company. The report
parameter region and preview region are separated by a horizontal draggable divider so a large table
can use the available workspace without hiding the controls.

The donor `SemanticReportFxRenderer` remains a visual reference for the white/blue workbook hierarchy.
Its static `GridPane` and alternate report workspace are not ported. The production correction uses the
existing Report Library plus `TableView` so column interaction, scrolling, tooltips, and company-owned
state follow the application-wide table contract.

CSV remains machine-readable and stable:

- dates remain ISO-8601;
- numeric values remain unadorned decimal strings;
- company currency symbols and grouping are not written into CSV numeric fields.

TEXT, CSV, PDF, and XLSX exports remain available. PDF/XLSX adapters consume the same text and CSV generated from the validated request.

## UI state

The Report Library catalog divider and parameter/preview divider are stored in company-owned UI state
under the `reportLibrary.` prefix. Per-report core table layout is stored through the shared company
table-state authority. The panel uses the global date range as an initial default, but report-specific
date controls form the actual request.

## Validation

Required automated coverage includes:

- catalog completeness and no placeholder entries;
- date/fund/row-limit request validation;
- selected-fund filtering against authoritative ledger data;
- company-formatted visible text with stable raw CSV;
- source guardrails for typed catalog/request use and company-owned divider state.
- structured core-table coverage for every core report, complete General Ledger columns, workbook row
  styles, dynamic company table-state binding, and the horizontal parameter/preview divider.
- metadata-only statement headings, comparative Balance Sheet values, zero-activity chart categories,
  chart-hierarchy-derived expense allocation columns, and Net Income/change-in-Net-Worth reconciliation.
- fixed-asset lifecycle/as-of reconstruction, inventory historical quantity, company isolation, stable-ID filters, linked/unlinked identities, exact control-account differences, and semantic preview/CSV parity.

Desktop validation must confirm report selection, parameter visibility, fund loading, preview, all export formats, Journal drill-through context, split-pane resizing, and laptop-width behavior.

For Balance Sheet and Income Statement, desktop validation must also use a company whose metadata and
chart labels do not contain SCA sample names. Confirm the displayed parent organization, legal entity,
group, currency, fiscal quarter, statement rows, and expense allocation columns exactly follow that
company and chart. Change the selected period and confirm the Balance Sheet beginning/end/difference
columns and both statements' reconciliation rows update together.
