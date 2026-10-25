# P27-S1 — Generic accounting packet contract

Owner direction, 2026-10-25: “no, simply use generic accounting rules.” This supersedes the official-workbook/template approval prerequisite. [PLAN](PLAN.md) owns execution status. P27-S1 defines the packet; P27-S2 implements export after this slice merges.

## Scope and authority

Build a generic accounting workbook and supporting reports from existing company-owned ledger, operational records and query services. Use double-entry accounting, exact decimal amounts, dated net activity, explicit classifications and retained correction history. This packet makes no claim of compliance with a particular organization's official form, filing deadline, certification or signature procedure.

The historical March 2026 workbook and runbook remain reference evidence only. Approval of their revision, cell/formula reproduction, legal-entity mapping, signatures, deadlines, duplicated sections and missing §22 do not gate this generic packet. No NMR operating-policy engine or attachment/approval system is introduced.

## Required sections and sources

| Section | Required content | Canonical source |
|---|---|---|
| Manifest | Company identity, period start/end and cutoff, Fund/Event scope, packet/application version, creation time, snapshot identity and exceptions | Company metadata, explicit export request and captured snapshot |
| Trial Balance | Account code/name, debits, credits, totals and difference | FinancialReportService.trialBalance |
| Balance Sheet | Assets, liabilities, net assets and balancing difference at cutoff | FinancialReportService.balanceSheet |
| Income Statement | Income, expenses and net result for selected dates | FinancialReportService.incomeStatement |
| General Ledger | Complete dated transaction/split rows, accounts, Fund/Event, debit/credit, memo, references and correction links | Txn/TxnSplit and FinancialReportService.generalLedgerDetail |
| Functional Expenses | AR = Events, OA = Administration, FR = Fundraising and visible Unclassified, tied to net GL expenses | FinancialReportService.functionalExpenses |
| Bank Activity and Reconciliation | Bank identity, dated splits, instrument references, opening/ending amounts, uncleared items and unresolved differences | Bank configuration, canonical bank splits, payment facts and reconciliation authority |
| Supplemental Schedules | Receivable, prepaid, other asset, payable, deferred and other liability; item identity, opening, movements, closing and control differences | SupplementalOpenItemQueryService |
| NMR and Merchant Receivables | Recipient/Merchant, collections, settlements, fees, evidenced discharges, remaining balances and separate withholding observations | Existing dated movement queries and canonical allocations |
| Fixed Assets | Acquisition links, cost, accumulated depreciation, carrying amount and lifecycle status through cutoff | AssetInventoryReportQueryService and fixed-asset authority |
| Inventory and Supplies | Quantity, moving weighted average carrying value, movements and valuation exceptions | Inventory authority and AssetInventoryReportQueryService |
| Property Custody | Recorded custody/transfer history and factual evidence references | PropertyCustodyService and property custody report query |
| Budget versus Actual | Explicit selected plan/version and Fund/Event scope, budget, actual and variance | BudgetPlanService and canonical actual reporting |
| Fund Transfers | Recorded internal Fund movements with canonical transaction links | FundTransfer authority and SemanticAccountingReportQueryService |
| Exceptions and Notes | Missing tags/classifications, unmatched legacy schedules, control differences, unavailable sources and operator explanations | Existing diagnostics; operator notes explicitly labelled |

These are section/field requirements rather than official-template coordinates. P27-S2 must inventory query properties and supply missing projections explicitly; existing semantic reports are not presumed to contain every required field. Supporting reports use the same captured data and scope.

## Accounting and snapshot rules

Capture one company and consistent snapshot for all sections. Activity uses an inclusive period; balances use an explicit cutoff. Include opening balances and earlier outstanding items where relevant. Report corrections by dated effects and preserve original/reversal/replacement links. Never count settlement as new income or infer clearing from cancellation or age.

Keep BigDecimal precision through aggregation. State consistent presentation rounding in the manifest, retain underlying four-decimal amounts and disclose rounding differences. UI row limits must not truncate export: enumerate all rows or fail visibly on capacity overflow. Split worksheets with repeated headers where necessary and list their parts in the manifest.

Fund, Event and Budget Category remain independent. Missing Event/function stays visible rather than inferred from memo. Use existing account types/functions and expose unmapped cases. Distinguish internal Fund transfers from income/expense. Do not infer an external counterparty's legal relationship from ownership or name.

Supplemental opening plus movements must equal closing and controls must tie to GL. Preserve stable item identity across installment, recognition and carryover presentation. NMR and fees use explicit saved facts; observations do not reduce receivable or liability. Asset depreciation and inventory values follow merged contracts. Exporting physical count/custody facts creates no accounting entries.

Stale-check age alone changes no accounting. Retain cancellation/reissue and closed-period/reconciliation protections. Jurisdiction-specific stale treatment remains outside generic export; unresolved policy does not block factual balances and exceptions.

## Output and completeness

P27-S2 generates a new generic XLSX workbook, supporting report files and manifest. Include all required sections, headers, date/money formats, totals, scope and exceptions. Historical workbook formulas/extensions are not copied. Output must open without repair. Export must not mutate H2, clear bank lines, close a period, erase history or overwrite a prior packet without explicit destination handling.

Optional notes may be blank. Missing financial facts must appear unavailable or as blocking exceptions, never fabricated zeroes. Export with exceptions remains available and clearly marked incomplete; no fully-ready claim is allowed while required sources or control differences remain unresolved. Evidence references/attestations do not certify independent document review. P27-S3 owns durable readiness/evidence and period-close integration; P27-S4 owns integrated SCLX coverage.

## Acceptance and unit coverage

Use synthetic quarter/year-end fixtures with openings, earlier outstanding items, partial settlements, corrections across cutoffs, NMR discharge/withholding, explicit/missing functions, assets, inventory and transfers. Assert totals against canonical queries, complete enumeration above UI limits, deterministic financial contents for one snapshot, company isolation, precision/rounding, missing-source diagnostics, file capacity, safe destinations and zero database writes. Check workbook structure and supporting report readability. Human spreadsheet opening is separately recorded, never inferred from automation.

Owner review: inspect section/scope choices, compare synthetic totals to reports, inspect an incomplete packet and confirm no official filing/signature claim. This documentation slice changes no production Save path and requires no new UI tests.
