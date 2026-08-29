---
plan_version: 238
active_phase: P19
active_slice: P19-S1
active_status: IN_PROGRESS
active_branch: codex/P19-S1-company-chart-assignment
active_pull_request: null
active_head: c05980fc2a55ce6bbec604abc95582e689cfddc8
next_action: "Publish this PLAN successor on the P19-S1 branch, open a draft PR to main, and validate the exact final head with clean verification, repeat tests, and production JavaFX route compliance. Correct any real implementation/test/documentation mismatch, then stop before merge for owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and source of truth

This document is the execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and slice at a time under root `AGENTS.md`. Current `main`, merged PRs, migrations, tests, governing documents, and this controller are authoritative over archived plans.

A slice is `DONE` only when its behavior/documentation is merged, required validation is green, governing documentation is current, and required owner acceptance is complete.

## 2. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for original contracts |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction P17-C9 DONE |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for original contracts |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, durable-record, documentation corrections | DONE through P17-C12 / PR #305 |
| P18 | Depreciation-run workflow completion | DONE through P18-S1 / PR #306 |
| P19 | Deferred Company Administration extensions | P19-S1 IN_PROGRESS |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, report, company, or Chart of Accounts authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider state remains H2-backed.
- Compatibility identifiers/APIs remain only where a current compatibility path requires them.
- Historical/archive documents remain historical evidence; current governing documents describe current production authority.

## 4. P17 completion ledger

P17 is DONE.

- P17-C1: shared UI compliance; PRs #290/#291/#292 plus corrective PR #304 merged.
- P17-C2: durable account lifecycle; PR #294 merged.
- P17-C3: budget-version lifecycle; PR #295 merged.
- P17-C4: banking durable-record lifecycle; PR #296 merged.
- P17-C5: inventory lifecycle; PR #297 merged.
- P17-C6: fund hierarchy lifecycle; PR #298 merged.
- P17-C7: fixed-asset lifecycle; PR #299 merged.
- P17-C8: Dashboard compliance; PR #300 merged.
- P17-C9: Report Library active-period synchronization; PR #301 merged.
- P17-C10: legacy UI retirement; PR #302 merged at `c7df4252681454f9f37584b14092b41155f8be51`.
- P17-C11: legacy shell/session/date-range cleanup; PR #303 merged at `7427da72e37f29d3016afb67c1aa35931c01a897`.
- P17-C12: documentation authority reconciliation; PR #305 final head `0b059e10a2907ad25ee971475317c3196a245193` passed Maven PR Tests run `33226042279`, owner acceptance was confirmed, and PR #305 merged to `main` at `3e87b56b26b189ca27008284734321c54a2ea0ec`.

## 5. P18 — Depreciation-run workflow completion

### P18-S1 — Accounting-period batching and Report Library integration

Status: DONE.

PR #306 final head `d802cfdbb739978f09fa516ad09fde32a8fe92ff` passed Maven PR Tests run `33229276083`, job `99039021126`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. The owner subsequently merged PR #306 to `main` at `3dec9516f1bb785dfefb2b277372be7fed656871`.

Governing design: `doc/P18-S1-period-depreciation-batching.md`
Owner verification: `doc/P18-S1-period-depreciation-batching-user-testing.md`

Completed behavior:

- accounting-period preview is derived from active period plus configured start day;
- eligible/excluded assets and deterministic period-end posting date are previewed;
- each asset remains an independently atomic governed `FixedAssetService.runMonthlyDepreciation(...)` operation;
- prior successes remain durable if a later asset fails, and retry naturally skips completed runs;
- chronological backfill is fenced when later depreciation exists;
- Report Library receives the selected period for the existing Fixed Asset Depreciation History & Schedule report;
- no second depreciation engine, batch table, synthetic multi-asset transaction, migration, or report-side writer was introduced.

## 6. P19 — Deferred Company Administration extensions

### P19-S1 — Company Chart of Accounts assignment administration

Status: IN_PROGRESS.

Branch: `codex/P19-S1-company-chart-assignment`
Starting base: merged `main` `3dec9516f1bb785dfefb2b277372be7fed656871`
Implementation head before this PLAN successor: `c05980fc2a55ce6bbec604abc95582e689cfddc8`
Pull request: not yet opened

Governing design: `doc/P19-S1-company-chart-assignment.md`
Company lifecycle authority: `doc/administration/company-lifecycle.md`
Owner verification: `doc/P19-S1-company-chart-assignment-user-testing.md`

Persistence/architecture decision:

- no migration is required;
- `chart_of_accounts.company_id` remains immutable chart ownership authority for this workflow;
- `company.active_chart_of_accounts_id` remains the current-chart selection authority;
- chart assignment never moves accounts, transactions, interchange identities, bank/reconciliation facts, report history, or any other durable record between charts/companies;
- ownerless legacy charts remain Company Ownership Diagnostics work and are not silently adopted by Company Admin.

Selection semantics:

- selected company must exist and be active;
- target chart must exist and already belong to the exact company;
- RETIRED charts are rejected;
- selecting DRAFT promotes that chart to ACTIVE;
- selecting an already ACTIVE chart changes only the company pointer;
- prior ACTIVE charts are retained and are not auto-retired;
- multiple company-owned ACTIVE charts may therefore exist, but the explicit company pointer determines the current chart;
- existing missing-pointer fallback may resolve one unambiguous ACTIVE chart; multiple ACTIVE charts with no pointer remain an error requiring deliberate Company Admin selection.

Implemented deliverables on `c05980fc2a55ce6bbec604abc95582e689cfddc8`:

- `CompanyAdminService.listCompanyCharts(...)` returns ownership-filtered chart projections;
- `CompanyAdminService.assignActiveChart(...)` performs locked transactional selection and lifecycle/ownership validation;
- `CompanyChartView` provides a detached UI projection;
- `CompanySessionController` exposes the same authoritative service operations without another persistence path;
- Company Admin now includes a real Chart of Accounts selector, current-state display, guarded **Make Active Chart** operation, explicit confirmation, and removal of the prior deferred-chart placeholder copy;
- scalar dirty-state blocks chart reassignment until profile edits are saved/discarded;
- `CompanyChartAssignmentServiceTest` proves DRAFT promotion, prior ACTIVE retention, old-account chart retention, and cross-company/RETIRED rejection;
- `CompanyChartAssignmentSourceTest` guards the reachable UI/service wiring;
- governing design, company lifecycle, and owner-testing documentation are updated.

Validation:

- no local Maven result is claimed because the current execution container cannot resolve GitHub for a repository checkout;
- exact final branch head must pass repository Maven PR Tests, including `Run clean headless verification`, `Run tests`, and `Run production JavaFX route compliance`;
- any failure must be diagnosed as implementation, test, or documentation drift; do not weaken ownership/history rules merely to satisfy stale source text.

Next exact action:

Publish this PLAN successor, open a draft PR to `main`, inspect the exact diff, and validate the exact final head in GitHub Actions. Stop before merge for owner acceptance after the final head is green.

### P19-S2 — Company reporting-default administration

Status: BLOCKED pending completion of P19-S1 and explicit persisted-consumer inspection.

Define persisted policy versus transient UI convenience and expose only defaults with real production consumers. Reuse existing preference authority; do not create a second preference store.

### P19-S3 — Company tax-filing metadata administration

Status: BLOCKED until the owner specifies required filing identities, periods, fields, and reporting/export consumers.

## 7. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements before implementation.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE.

## 8. Advancement rule

Execute only the active slice. Do not advance beyond P19-S1 until its exact final head is green, owner acceptance is complete, and its PR is merged. After merge, rescan current `main` before selecting another slice.
