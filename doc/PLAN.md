---
plan_version: 243
active_phase: P19
active_slice: P19-S3
active_status: IN_PROGRESS
active_branch: codex/P19-S3-company-ein-metadata
active_pull_request: null
active_head: 341852ce83173bd3e67f3cad948b60e72c37fc17
next_action: "Publish the P19-S3 governing documentation successor, open a draft PR to main, run Maven PR Tests on the exact PR head, correct any concrete failure, then stop before merge for owner acceptance."
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
| P19 | Deferred Company Administration extensions | P19-S1 DONE; P19-S2 DONE; P19-S3 IN_PROGRESS |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, report, company, or Chart of Accounts authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider and other UI/workflow defaults remain H2-backed through the established company preference/state authority.
- EIN is optional informational company metadata. It is not tax-filing configuration and does not imply tax-return, jurisdiction, period, reporting, or export workflow.
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

Status: DONE.

PR #308 exact final head `e1f45ee0b0f96870425f9f96d10e960e3c86d3c0` passed Maven PR Tests run `33264570758`, job `99132280957`: `Run clean headless verification`, `Run tests`, and `Run production JavaFX route compliance` all succeeded. Owner acceptance was confirmed and PR #308 merged to `main` at `b4ef30643978eb926e97773a13a6102b0389244a`.

Completed behavior:

- current Report Library has exactly two safe company-level opening defaults with real consumers: initial report selection and initial export format;
- the values reuse existing H2 `company_ui_state` via `CompanyUiPreferencesService` under `reportingDefaults.`;
- missing or stale saved values fall back to Trial Balance/Text;
- a new Report Library reads the defaults once; changing Company Admin defaults never replaces an already-open operator selection;
- Report Library interaction does not automatically rewrite company defaults;
- report dates, fund, row limit, account, fixed-asset, inventory, and status filters remain transient/current-request parameters;
- no migration, report query/execution, or export-adapter behavior was introduced.

Governing design: `doc/P19-S2-company-reporting-defaults.md`.

### P19-S3 — Company EIN informational metadata

Status: IN_PROGRESS.

Branch: `codex/P19-S3-company-ein-metadata`
Starting base: merged `main` `b4ef30643978eb926e97773a13a6102b0389244a`
Implementation commit: `341852ce83173bd3e67f3cad948b60e72c37fc17`
Pull request: not opened yet.

Owner requirement resolving the former block:

- there is no tax-filing requirement;
- EIN is informational metadata that belongs to the company profile;
- do not invent filing identities, filing periods, tax jurisdictions, returns, filing addresses, filing status, or report/export semantics.

Governing design: `doc/P19-S3-company-ein-metadata.md`.
Owner verification: `doc/P19-S3-company-ein-metadata-user-testing.md`.
Company lifecycle authority: `doc/administration/company-lifecycle.md`.

Implementation decision:

- add nullable `company.ein VARCHAR(40)` as the sole live production EIN authority;
- V75 nondestructively backfills a nonblank legacy `company_tax_profile.ein` to the stable company row;
- retain the legacy `company_tax_profile` table/data physically, but retire `CompanyTaxProfile` from production JPA and remove the unused `CompanyAdminService.taxProfile(...)` query so there is no second writable/read authority;
- expose EIN through `Company`, `CompanyCommand`, `CompanyView`, `CompanyAdminService`, and the existing Company Admin profile form;
- trim values, store blank as null, limit to 40 characters, and deliberately avoid IRS-specific format validation;
- preserve backward-compatible command/view constructors for unrelated callers;
- do not change reports, report presentation metadata, exports, SCLX, Chart of Accounts interchange, banking, accounting, or P20 authorization.

Validation required:

- focused service round-trip/validation coverage;
- V74-to-V75 in-memory Flyway upgrade proving legacy EIN backfill and preservation of the legacy table/data;
- Company Admin source guard proving the real field/write path and absence of the old tax-filing deferral/live tax-profile dependency;
- repository Maven PR Tests on the exact final head: clean headless verification, repeat tests, and production JavaFX route compliance;
- owner desktop checks from the P19-S3 user-testing document.

No local Maven result is claimed because the execution container cannot resolve GitHub for a repository checkout.

Next exact action:

Publish the P19-S3 documentation successor, open a draft PR to `main`, validate the exact PR head in GitHub Actions, correct any concrete failure without widening into tax-filing/reporting behavior, and stop before merge for owner acceptance.

## 6. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements before implementation.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE.

## 7. Advancement rule

Execute only the active slice. Do not advance beyond P19-S3 until its exact final head is green, owner acceptance is complete, and its PR is merged. After merge, rescan current `main` before selecting another slice.
