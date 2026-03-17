# Data Reporting Implementation Plan of Execution

## Purpose

This document translates `docs/data-reporting-plan.md` into an actionable implementation program with concrete workstreams, engineering deliverables, owners, acceptance criteria, and validation checkpoints.

## 1) Delivery Strategy

We will execute reporting in four vertical increments:

1. **Foundation + Trial Balance vertical slice**
2. **Core Statements + Drilldown vertical slice**
3. **Subledger/Budget Reporting + Async Exports**
4. **Scheduling, Security Hardening, and Audit/Compliance Pack**

Each increment must ship end-to-end: data model, query/projection logic, renderer integration, UI/API access, tests, and operational telemetry.

## 2) Workstreams and Ownership Model

Use a matrix ownership model so work advances in parallel while maintaining a single release train.

- **WS-A: Reporting Data Platform** (schema, views/materializations, performance)
- **WS-B: Report Execution Engine** (catalog, parameters, orchestration, run metadata)
- **WS-C: Renderers and Export Delivery** (JasperReports, CSV/XLSX/PDF)
- **WS-D: Product UX and Workflow** (library, filters, preview, run history)
- **WS-E: Security and Auditability** (RBAC/RLS, event logging, retention)
- **WS-F: Quality Engineering** (integration/golden/performance/security tests)

## 3) Execution Backlog by Milestone

## Milestone M0 — Foundation Readiness (Week 1)

### Deliverables

- Confirm report catalog schema and naming convention (`report_code`, semantic versioning).
- Create `report_run` table and run-event audit trail structure.
- Define parameter payload contract and validation rules (date range, fiscal period, dimensional filters).
- Introduce feature flags for incremental rollout (`reporting.enabled`, per-report flags).

### Acceptance criteria

- Catalog entries can be loaded at startup and validated.
- `report_run` row created for each execution attempt with status transitions.
- Parameter validation rejects malformed/unsafe payloads with clear user-visible messages.

## Milestone M1 — First Production Slice: Trial Balance + GL Detail (Weeks 2–3)

### Deliverables

- Implement Trial Balance projection query with period/fund filters.
- Implement General Ledger detail extraction with pagination and export support.
- Wire JasperReports template(s) for Trial Balance PDF rendering.
- Add CSV export path for both reports.
- Add UI/API endpoint for report preview and download.

### Acceptance criteria

- Trial Balance enforces debit=credit tie-out on every run.
- GL detail drill path includes account + transaction identifiers.
- Deterministic integration tests validate totals against seeded ledger fixtures.

## Milestone M2 — Financial Statements (Weeks 4–6)

### Deliverables

- Implement Balance Sheet projection (as-of date, comparative period option).
- Implement Income Statement/Statement of Activities projection (period + YTD modes).
- Add account-to-report-section mapping validation diagnostics.
- Implement drill-through from statement lines to ledger details.
- Add reconciliation badges (`PASS`, `WARN`, `FAIL`) in run metadata and UI.

### Acceptance criteria

- Statement totals reconcile to control checks.
- Comparative periods are deterministic and timezone-safe.
- Drill-through retains all applied report filters.

## Milestone M3 — Operational Reporting (Weeks 7–8)

### Deliverables

- Implement AR aging and AP aging with configurable bucket boundaries.
- Implement Budget vs Actual (monthly + YTD, approved budget versioning).
- Add async export job queue and worker processing.
- Add XLSX exports for management reports (Apache POI path for advanced formatting).

### Acceptance criteria

- Aging totals tie to receivable/payable control accounts.
- Budget report uses effective budget revision for selected period.
- Async jobs support retry + idempotent completion semantics.

## Milestone M4 — Distribution + Governance (Weeks 9–10)

### Deliverables

- Implement scheduling (cron-like recurrence + timezone) and delivery channels.
- Add secure file lifecycle policy (encryption at rest + retention expiry).
- Add role- and scope-aware data filters for report access.
- Capture access/download audit events with actor + parameter hash.

### Acceptance criteria

- Scheduled runs generate auditable `report_run` entries.
- Access policy tests prove row-level restrictions across funds/programs.
- Expired artifacts are purged according to retention policy.

## Milestone M5 — Performance + Launch Readiness (Weeks 11–12)

### Deliverables

- Add indexes/materialized views for high-cost report paths.
- Introduce result caching for repeat parameter sets.
- Finalize operational dashboards (latency, success rate, failure taxonomy).
- Complete finance UAT + runbook sign-off.

### Acceptance criteria

- P95 latency meets agreed SLO thresholds.
- Failure classes are actionable and observable.
- Launch checklist signed by Engineering + Finance owner.

## 4) Technical Implementation Design

## 4.1 Report catalog + contracts

Define report identity and runtime contract:

- `report_code` (stable machine id)
- `report_version` (semantic; increment on breaking layout/logic changes)
- `parameter_schema` (JSON schema-like validator)
- `supported_formats` (`PDF`, `XLSX`, `CSV`)
- `access_policy` (role/scope constraints)

## 4.2 Execution pipeline (single run)

1. Validate request + authorize user scope.
2. Resolve report definition + parameter defaults.
3. Build data snapshot cursor/cutoff id.
4. Execute projection query/materialized path.
5. Run reconciliation checks and attach status.
6. Render output via selected adapter (Jasper/CSV/POI).
7. Persist output metadata + checksums + run events.
8. Return preview/download response (or async job reference).

## 4.3 Data and projection approach

- Keep atomic posting facts immutable.
- Prefer deterministic SQL projections for auditable totals.
- Introduce materialized summaries only for known heavy paths.
- Tag every run with `as_of` and ledger cutoff markers.

## 4.4 Renderer strategy

Concrete rendering outputs for implemented M1/M2 reports:

- **Trial Balance**
  - Text preview: fixed-width tabular view (account, name, debit, credit, totals, balance check).
  - CSV export: `account_code,account_name,debit,credit`.
- **General Ledger Detail**
  - Text preview: line-level journal projection including transaction, account, fund, debit/credit.
  - CSV export: `txn_date,txn_id,memo,payee,account_code,account_name,fund_code,fund_name,debit,credit`.
- **Balance Sheet**
  - Text preview: sectioned output (assets/liabilities/equity) plus balance check.
  - CSV export: `section,account_code,account_name,amount`.
- **Income Statement**
  - Text preview: sectioned income/expense output with net income.
  - CSV export: `section,account_code,account_name,amount`.

Planned extensions after M2:

- **JasperReports** for production PDF statements.
- **Apache POI** for structured XLSX board/management workbooks.

## 5) Quality Plan (Definition of Quality Gates)

For each milestone, ship only when all gates pass:

1. **Functional gate**: required scenarios and parameter combinations validated.
2. **Reconciliation gate**: tie-outs and control totals pass.
3. **Security gate**: access and scope rules verified.
4. **Performance gate**: latency and resource baseline within threshold.
5. **Observability gate**: run logs/metrics/errors are queryable and actionable.

## 6) Testing Plan by Layer

- **Unit tests**: parameter validators, bucket logic, sign conventions, section mapping.
- **Integration tests**: seeded ledger snapshots for each report and tie-out assertions.
- **Golden output tests**: stable snapshots for CSV and structural assertions for PDF/XLSX.
- **Security tests**: role/scope and export-authorization coverage.
- **Performance tests**: representative tenant volumes, warm/cold cache runs.

## 7) Risk Register with Triggered Mitigations

1. **Template drift across versions**  
   Mitigation: strict report versioning + backward-compatible catalog entries.
2. **Large-period query degradation**  
   Mitigation: targeted indexes, pre-aggregation, async execution fallback.
3. **Inconsistent dimensional mappings**  
   Mitigation: startup diagnostics + fail-fast checks for invalid report section mappings.
4. **Insufficient audit traceability**  
   Mitigation: mandatory run-event lifecycle logging and immutable metadata.

## 8) Operational Readiness Checklist

- [ ] Report run dashboard in place (success/fail/retry, P95 latency).
- [ ] Error catalog and incident playbook documented.
- [ ] Backup/restore path validated for reporting metadata.
- [ ] Retention/encryption controls verified in staging.
- [ ] UAT sign-off completed for core statement pack.

## 9) Handoff Plan After Implementation

At the end of each milestone:

1. Publish implementation notes and known constraints.
2. Demo with Finance stakeholders using seeded and real-like datasets.
3. Capture prioritized feedback into next milestone backlog.
4. Freeze report versions for released artifacts and retain run evidence.

## 10) Immediate Next Implementation Tasks (first sprint)

1. Add report catalog seed + runtime loading.
2. Add `report_run` persistence model and state transitions.
3. Implement Trial Balance projection query with tie-out checker.
4. Add first Jasper template and CSV export endpoint.
5. Add deterministic integration test fixture + reconciliation assertions.

Completion of these tasks marks the first implementable reporting slice ready for broader statement expansion.
