# P19-S1 — Company Chart of Accounts assignment

## Purpose

P19-S1 completes the previously deferred Company Admin workflow for selecting a company's current Chart of Accounts. It uses the durable model already present in H2 and does not introduce a second chart registry, move accounting history, or require a migration.

## Durable authority

Two existing relationships have distinct meanings:

- `chart_of_accounts.company_id` owns a chart and everything attached to that chart;
- `company.active_chart_of_accounts_id` selects the chart for routine journal choices and chart-targeted compatibility interchange. P27-C21 native maintenance can explicitly target another owned non-retired chart without changing that pointer.

Ownership is not reassignment. P19-S1 never changes `chart_of_accounts.company_id` and never moves `Account.chart` references.

## Selection rules

`CompanyAdminService.assignActiveChart(companyId, chartId)` owns the transaction and revalidates both stable IDs under pessimistic write locks.

- The company must exist and be active.
- The chart must exist and already belong to that company.
- Ownerless legacy charts are corrected through Company Ownership Diagnostics, not adopted by this operation.
- `RETIRED` charts are not selectable.
- Selecting a `DRAFT` chart promotes it to `ACTIVE`.
- Selecting an `ACTIVE` chart changes the company pointer only.
- A previous `ACTIVE` chart is not auto-retired. Chart lifecycle and current selection are separate facts.
- No account, transaction, bank configuration, reconciliation fact, report history, SCLX/COA identity, or other durable record is rewritten because the pointer changes.

This permits multiple owned charts to remain `ACTIVE` while one explicit company pointer identifies the current chart. Existing compatibility fallback for a missing pointer may resolve exactly one ACTIVE chart; a missing pointer with multiple ACTIVE charts remains ambiguous until Company Admin selects one.

## UI behavior

P19-C1 owner direction supersedes the original placement below: named chart viewing and activation now belong to **Chart of Accounts**, not Company Admin. See [P19-C1](P19-C1-chart-selection-location.md). The service rules above remain authoritative.

### Original placement (historical)

The existing **Administration → Company Admin** editor gains a **Chart of Accounts assignment** section. There is no new shell destination.

- Selecting a persisted company loads only that company's charts.
- The current chart is visibly marked.
- The action is disabled for a new/unsaved company, dirty company-profile edits, the current chart, and a `RETIRED` chart.
- A confirmation explains that the change affects future chart-targeted operations but does not move or delete existing accounting history.
- The existing vertically scrollable Company Admin editor and company-owned table/divider state remain intact.

## Interactions

- Legacy `AccountAdminService` callers resolve `Company.activeChartOfAccounts`; P27-C21 native maintenance supplies the viewed chart ID explicitly and validates ownership/lifecycle.
- Chart of Accounts JSON `CREATE_NEW_CHART` remains non-activating and produces a company-owned `DRAFT` chart. P19-S1 is the explicit activation step.
- Chart of Accounts JSON `MERGE_BY_CODE` continues to target the company's active chart.
- P27-C8 supersedes the SCLX single-chart rule: all-chart files retain membership across company-owned charts and preserve an existing target selection; see [all-chart interchange](P27-C8-sclx-all-charts.md). Legacy SCLX existing-company import continues to preserve/use the target company's current chart; a new/unpopulated target may establish its first chart through the governed SCLX path.
- Reports and historical transactions retain their existing stable account/chart references; changing the current pointer is not a historical data migration.
- P27-C3 new interactive Journal drafts use the selected chart, with atomic stale-selection validation. Historical correction/import/generated entry ownership rules remain unchanged; see [Journal/SCLX consistency](P27-C3-sclx-chart-consistency.md).
- Company switching continues to rebuild company-bound workspaces. Re-selecting an already-open chart-sensitive workspace invokes its normal `onPanelShown()` refresh and re-queries current H2 authority.

## Schema decision

No Flyway migration is required. The necessary foreign keys already exist. Adding a second assignment table or duplicating the pointer would create parallel authority and is prohibited.

## Donor decision

The donor repository was checked as required by root `AGENTS.md`. Its earlier company-management ideas remain useful for explicit selection semantics, but it does not provide a compatible H2 company-owned active-chart pointer implementation. No donor static/current-company or serialized persistence mechanism is imported.

## Automated validation

Required coverage includes:

- company chart listing is ownership-filtered;
- DRAFT selection promotes to ACTIVE;
- prior ACTIVE chart remains durable and is not auto-retired;
- existing account remains attached to its original chart;
- cross-company and RETIRED chart selection is rejected;
- production Company Admin exposes the real assignment operation and no longer advertises chart assignment as deferred;
- full repository Maven PR Tests: clean verification, repeat tests, and production JavaFX route compliance.

Owner verification is recorded in `doc/P19-S1-company-chart-assignment-user-testing.md`.
