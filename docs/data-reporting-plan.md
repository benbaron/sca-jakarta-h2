# Data Reporting Implementation Plan (Full-Featured Accounting Application)

## 1. Objectives and Outcomes

This plan defines a production-ready reporting capability for a full-featured accounting application with nonprofit-ready fund accounting support. The reporting platform must:

1. Produce core financial statements (Balance Sheet, Income Statement/P&L, Cash Flow, Trial Balance).
2. Support multi-dimensional slicing (fund, program, department, grant, project, location).
3. Provide drill-down from report lines to journal entries and source transactions.
4. Offer reproducible exports (PDF, Excel, CSV) and parameterized report runs.
5. Ensure auditable, deterministic numbers with period close protections.
6. Handle both interactive UI reporting and scheduled/batch distribution.

## 2. Reporting Scope and Prioritization

### 2.1 Phase 1 (MVP Reports)

- Trial Balance (by period; optional fund/program filters)
- Balance Sheet (comparative periods)
- Income Statement / Statement of Activities
- General Ledger detail export
- Accounts Receivable aging
- Accounts Payable aging

### 2.2 Phase 2 (Operational + Management)

- Budget vs Actual (monthly and YTD)
- Cash Flow (direct/indirect)
- Fund balance roll-forward
- Department/program profitability
- Grant spend-to-date and remaining balance
- Open items and exception reports (unreconciled bank items, stale receivables)

### 2.3 Phase 3 (Governance + Compliance)

- Audit package report set (supporting schedules)
- Donor/grant restriction compliance reports
- Consolidation/elimination report pack
- Custom report builder templates for finance admins

## 3. Canonical Reporting Data Model

Use a star-schema-like reporting model in a dedicated `reporting` schema (or materialized view layer):

- **Fact tables**
  - `fact_gl_posting`: posting-level debits/credits in base + source currency
  - `fact_open_item`: receivables/payables state snapshots and aging buckets
  - `fact_budget`: periodic budget allocations and revisions
- **Dimensions**
  - `dim_account` (account hierarchy, report section mapping)
  - `dim_fund` (restricted/unrestricted and fund metadata)
  - `dim_org_unit` (department/program/location)
  - `dim_counterparty` (customer/vendor/donor)
  - `dim_calendar` (fiscal/period calendars and close status)

Design principles:

1. Preserve immutable posting records; corrections are reversal/rebook entries.
2. Keep derived aggregations reproducible from atomic posting facts.
3. Record as-of timestamp and run id for every generated report snapshot.
4. Normalize sign conventions by account category before presentation.

## 4. Report Generation Package Strategy

Adopt a layered approach:

1. **Primary engine: JasperReports**
   - Strengths: mature templating, subreports, pagination, PDF fidelity, chart support, broad Java ecosystem usage.
   - Fit: statutory statements, board packs, printable audit reports.
2. **Developer-friendly DSL wrapper: DynamicReports (optional)**
   - Strengths: Java DSL over Jasper for easier programmatic report construction.
   - Fit: generated/tabular management reports where maintaining many `.jrxml` files is costly.
3. **Spreadsheet exports: Apache POI**
   - Strengths: precise Excel formatting, formulas, multi-sheet workbooks.
   - Fit: analyst-facing outputs and pivot-friendly exports.

Recommendation:

- Standardize on JasperReports for canonical statement rendering.
- Use POI for high-utility Excel workbooks where native Jasper XLS output is insufficient.
- Introduce DynamicReports only if template maintenance overhead becomes material.

## 5. Reference Architecture

### 5.1 Layers

1. **Report Definition Layer**
   - report catalog metadata (name, owner, parameters, access policy, output formats)
   - semantic version for each report definition
2. **Query/Projection Layer**
   - parameter validation and query planning
   - SQL views/materialized views for heavy aggregations
3. **Rendering Layer**
   - Jasper/POI renderer adapters behind `ReportRenderer` interface
4. **Delivery Layer**
   - in-app preview, async export job, email/S3/secure-file delivery
5. **Audit Layer**
   - run metadata, execution duration, row counts, parameter hash, output checksum

### 5.2 Service Contracts

- `ReportCatalogService`
- `ReportExecutionService`
- `ReportParameterService`
- `ReportExportService`
- `ReportScheduleService`

Each execution should produce a `report_run` record with:

- `report_code`, `report_version`, `requested_by`
- `parameter_payload` (JSON)
- `as_of_date`, `period_id`
- `data_snapshot_id` / `ledger_cutoff_txn_id`
- `status`, `duration_ms`, `error_summary`

## 6. Security and Access Control

1. Enforce role-based and attribute-based controls (finance admin, accountant, grant manager, read-only auditor).
2. Apply row-level security filters for fund/program/entity scope.
3. Mask sensitive counterparties where policy requires.
4. Log report access and export events for audit trail.
5. Encrypt generated files at rest and expire temporary artifacts.

## 7. Data Quality and Reconciliation Controls

Introduce automated report guardrails:

1. Trial Balance check: sum(debits) == sum(credits).
2. Statement tie-outs:
   - Net income ties to equity retained earnings movement.
   - Cash ending balance ties to bank/cash ledger balances.
3. Aging totals tie to subledger control accounts.
4. Budget vs actual uses approved budget version and effective dates.
5. All report runs surface reconciliation status (`PASS`, `WARN`, `FAIL`).

## 8. Performance and Scalability Plan

1. Partition high-volume facts by fiscal period.
2. Add covering indexes for common filter combinations (period, fund, account, org unit).
3. Use materialized aggregates for board-level dashboards and large comparative reports.
4. Support async report jobs with queue + worker model.
5. Add result caching for identical parameter/as-of combinations with cache invalidation on posting changes.

SLO targets:

- Interactive preview (simple reports): < 3 seconds P95
- Heavy statements (comparative multi-period): < 15 seconds P95
- Bulk distribution pack: < 5 minutes for standard monthly close set

## 9. UX and Workflow Design

1. Report Library page with categories (Financial, Operational, Compliance, Custom).
2. Parameter panel with saved presets and validations.
3. Preview pane with drill-through actions to ledger detail.
4. Export controls for PDF/XLSX/CSV + schedule options.
5. Run history tab showing who ran what, when, and with which parameters.

## 10. Delivery Roadmap (12 Weeks)

### Sprint 1-2: Foundations

- finalize report catalog and canonical parameter schema
- create `report_run` and audit tables
- implement trial balance and GL detail reports (Jasper + CSV)

### Sprint 3-4: Core Statements

- implement balance sheet and income statement with comparative periods
- add drill-down links to journal lines
- add reconciliation checks and error surfacing

### Sprint 5-6: Subledger + Budgets

- add AR/AP aging
- implement budget-vs-actual model and report
- start async export queue

### Sprint 7-8: Distribution + Security

- add scheduling and email delivery
- enforce row-level policy filters
- add signed export artifacts and retention rules

### Sprint 9-10: Performance Hardening

- optimize indexes/materialized views
- add report result cache and load tests
- tune high-volume tenant scenarios

### Sprint 11-12: Compliance + Launch

- audit pack templates
- run UAT with finance users and auditors
- finalize runbooks, alerting, and production readiness checklist

## 11. Testing Strategy

1. **Unit tests**
   - parameter validation, sign normalization, subtotal logic
2. **Integration tests**
   - deterministic seeded ledger snapshots for each report
   - tie-out assertions across report/subledger/GL
3. **Golden-file rendering tests**
   - stable snapshot checks for PDF/CSV/XLSX structure and totals
4. **Performance tests**
   - baseline latency with realistic data volumes
5. **Security tests**
   - row-level access checks and export authorization

## 12. Risks and Mitigations

1. **Template sprawl and maintenance burden**
   - Mitigation: report definition standards + shared components + semantic versioning.
2. **Slow reporting on large ledgers**
   - Mitigation: pre-aggregations, async jobs, and indexing strategy.
3. **Data trust issues during close**
   - Mitigation: cutoff snapshots, reconciliation badges, and immutable run metadata.
4. **Complex permission matrix**
   - Mitigation: centralized authorization policy service and automated policy tests.

## 13. Implementation Checklist

- [ ] Approve report catalog and owners
- [ ] Select JasperReports baseline version and POI compatibility matrix
- [ ] Create reporting schema + run audit tables
- [ ] Build ReportExecutionService + renderer abstraction
- [ ] Deliver Phase 1 report set with tie-out checks
- [ ] Add scheduling + export delivery pipeline
- [ ] Complete security and audit controls
- [ ] Conduct finance UAT and performance certification

## 14. Definition of Done for Reporting Platform

A report capability release is complete when:

1. Required reports produce reconciled totals with deterministic outputs.
2. Access controls and audit logs are verified.
3. Exports and schedules are reliable and observable.
4. Finance stakeholders sign off on usability and trustworthiness.
5. Runbooks and support alerts are in place for production operations.
