---
plan_version: 242
active_phase: P19
active_slice: P19-S2
active_status: VERIFYING
active_branch: codex/P19-S2-company-reporting-defaults
active_pull_request: 308
active_head: 913089672ec094575e8698c26034dc29a4942353
next_action: "Validate the documentation successor to corrective commit 913089672ec094575e8698c26034dc29a4942353 in Maven PR Tests; require clean verification, repeat tests, and production JavaFX route compliance green, then update PR #308 metadata only and stop before merge for owner acceptance."
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
| P19 | Deferred Company Administration extensions | P19-S1 DONE; P19-S2 VERIFYING |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, report, company, or Chart of Accounts authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider and other UI/workflow defaults remain H2-backed through the established company preference/state authority.
- Compatibility identifiers/APIs remain only where a current compatibility path requires them.
- Historical/archive documents remain historical evidence; current governing documents describe current production authority.

## 4. Completed recent phases

P17 is DONE through P17-C12 / PR #305. P18-S1 is DONE: PR #306 final head `d802cfdbb739978f09fa516ad09fde32a8fe92ff` passed Maven PR Tests run `33229276083`, job `99039021126`, and merged to `main` at `3dec9516f1bb785dfefb2b277372be7fed656871` after owner acceptance.

## 5. P19 — Deferred Company Administration extensions

### P19-S1 — Company Chart of Accounts assignment administration

Status: DONE.

PR #307 exact final head `75e2ab1a47e2fad95be460426ea64b038109842c` passed Maven PR Tests run `33231705169`, job `99045589148`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. The owner accepted the desktop checks and merged PR #307 to `main` at `9e9ea01f180f1e35ed49916b064705a0fdff87db`.

Completed behavior:

- `chart_of_accounts.company_id` remains chart ownership authority;
- `company.active_chart_of_accounts_id` remains the current-chart pointer;
- Company Admin lists only company-owned charts and deliberately selects the current chart;
- DRAFT selection promotes to ACTIVE, while RETIRED/cross-company selection is rejected;
- prior ACTIVE charts and all existing account/history relationships remain intact;
- no migration or second chart/assignment authority was introduced.

### P19-S2 — Company reporting-default administration

Status: VERIFYING.

Branch: `codex/P19-S2-company-reporting-defaults`
Starting base: merged `main` `9e9ea01f180f1e35ed49916b064705a0fdff87db`
Implementation commit: `e9ced0ed3fcba67063f8d9b3e7ab545d28ba8230`
Documentation commit: `49e2319c0bf44785f65eb7aaa06cbd75931f8948`
Corrective source-guard commit: `913089672ec094575e8698c26034dc29a4942353`
Pull request: #308

Governing design: `doc/P19-S2-company-reporting-defaults.md`
Owner verification: `doc/P19-S2-company-reporting-defaults-user-testing.md`
Company lifecycle authority: `doc/administration/company-lifecycle.md`

Persisted-consumer inspection decision:

- current Report Library has exactly two safe company-level opening defaults with real consumers: initial report selection and initial export format;
- current Report Library otherwise derives dates from active-period/fiscal authority and treats fund, row limit, account, fixed-asset, inventory, and status filters as deliberate `ReportRequest` parameters;
- therefore P19-S2 persists only **Default opening report** and **Default export format**;
- those two values reuse existing H2 `company_ui_state` via `CompanyUiPreferencesService` under `reportingDefaults.`;
- no migration and no second preference repository/table are required;
- missing or stale saved values fall back to Trial Balance/Text;
- a new Report Library reads the defaults once; changing Company Admin defaults never replaces an already-open operator selection;
- Report Library interaction does not automatically rewrite company defaults.

Implemented deliverables:

- typed `CompanyReportingDefaults` projection;
- `CompanyUiPreferencesService.loadReportingDefaults(...)` / `saveReportingDefaults(...)` with stable report-ID and export-enum persistence;
- Company Admin **Reporting defaults** controls with immediate company-owned persistence and scalar-dirty guard;
- Report Library startup uses company defaults instead of hard-coded Trial Balance/Text;
- focused H2 round-trip/stale-value coverage and production source guard;
- governing design, company lifecycle, owner-testing, and PLAN documentation;
- no report query/execution/export-adapter behavior and no schema/migration changed.

Validation:

- no local Maven result is claimed because the execution container cannot resolve GitHub for a repository checkout;
- exact verification head `a685dfb1ca83327479b1143312728175a99bae4c` failed Maven PR Tests run `33234747858` (#1647), job `99053661368`, in `Run clean headless verification`; production and test compilation succeeded, while the repeat-test and production-route steps were skipped after the clean gate failed;
- the failure was `CompanyReportingDefaultsSourceTest.companyAdminAndReportLibraryShareCompanyOwnedOpeningDefaults`: its source-text guard required the compiler-concatenated strings `reportingDefaults.defaultReportId` and `reportingDefaults.defaultExportFormat` to occur literally, while production correctly declares `REPORTING_DEFAULTS_PREFIX = "reportingDefaults."` and composes `DEFAULT_REPORT_KEY` / `DEFAULT_EXPORT_FORMAT_KEY` from that prefix;
- corrective commit `913089672ec094575e8698c26034dc29a4942353` changes only that source guard to verify the declared prefix and key-composition expressions; no production behavior, persistence, Flyway/H2 setup, schema, or migration changed;
- the documentation successor to that corrective commit must pass repository Maven PR Tests: `Run clean headless verification`, `Run tests`, and `Run production JavaFX route compliance`;
- any further failure must be corrected without introducing a migration, second preference authority, persisted report-request filters, or report-side accounting state.

Next exact action:

Validate the documentation successor to corrective commit `913089672ec094575e8698c26034dc29a4942353` in GitHub Actions, update PR #308 with exact final-head run/job evidence without another repository commit, and stop before merge for owner acceptance.

### P19-S3 — Company tax-filing metadata administration

Status: BLOCKED until the owner specifies required filing identities, periods, fields, and reporting/export consumers. Do not invent tax identifiers or filing workflows from the legacy architecture placeholder.

## 6. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements before implementation.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE.

## 7. Advancement rule

Execute only the active slice. Do not advance beyond P19-S2 until its exact final head is green, owner acceptance is complete, and its PR is merged. After merge, rescan current `main` before selecting another slice.
