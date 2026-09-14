---
plan_version: 289
active_phase: P21
active_slice: P21-S2
active_status: DONE
active_branch: null
active_pull_request: null
active_head: 942cf2c9af69bbb3973184bc4da1d94d4e1d1a70
next_action: "P21 is complete through P21-S2. Do not invent a successor phase; begin new work only from a deliberate PLAN amendment or an explicit owner-selected phase/slice."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and source of truth

This document is the execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and slice at a time under root `AGENTS.md`. Current `main`, merged PRs, migrations, tests, governing documents, and this controller are authoritative over archived plans.

A slice is `DONE` only when the behavior is merged into current `main`, the governing documentation is current, and required validation passed. Local code or an open pull request is not `DONE`.

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
| P19 | Deferred Company Administration extensions | DONE through P19-S3 / PR #309 |
| P20 | Authentication and runtime authorization | DONE through P20-S3 |
| P21 | Activity and Event Accounting | DONE through P21-S2 / PR #339 |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, report, company, Chart of Accounts, identity, credential, or authorization authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider and other UI/workflow defaults remain H2-backed through the established company preference/state authority.
- EIN is optional informational company metadata. It is not tax-filing configuration and does not imply tax-return, jurisdiction, period, reporting, or export workflow.
- Compatibility identifiers/APIs remain only where a current compatibility path requires them.
- Historical/archive documents remain historical evidence; current governing documents describe current production authority.

### P20 adopted security decisions

- `AppUser` is the account/login identity; `AppRole` never stores a password.
- Authentication is local to the H2 database; no external IdP/SSO/MFA requirement is adopted.
- Reserved default accounts/roles are `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER`.
- Reserved accounts start with no password. A passwordless account can explicitly log in with no password challenge.
- Only ADMIN may set, replace, or clear account passwords, including its own.
- Each company starts with one default assignment for ADMIN, MANAGER, ACCOUNTANT, and VIEWER.
- There is exactly one effective ADMIN account in the database; no second account may obtain effective ADMIN authority.
- Default inactivity timeout is disabled. A session ends on explicit logout, application exit, or database switch unless an ADMIN later configures a nonzero timeout.
- ADMIN has full security/application authority; MANAGER has bookkeeping plus non-security company administration; ACCOUNTANT has normal bookkeeping write authority; VIEWER is read-only with report/export access.
- Authenticated user identity becomes the authoritative audit actor once P20 enforcement is implemented.
- Forgotten singleton-ADMIN credentials require an explicit offline local recovery that clears only the ADMIN credential to the passwordless state; no default/backdoor password is permitted.
- The local workstation/H2 file permissions are the outer trust boundary; application passwords do not claim protection against unrestricted OS/database-file access.

Governing requirements: `doc/P20-S1-authentication-authorization-boundary.md`.

## 4. Completed recent phases

P17 is DONE through P17-C12 / PR #305.

P18-S1 is DONE: PR #306 final head `d802cfdbb739978f09fa516ad09fde32a8fe92ff` passed Maven PR Tests run `33229276083`, job `99039021126`, and merged to `main` at `3dec9516f1bb785dfefb2b277372be7fed656871` after owner acceptance.

## 5. P19 — Deferred Company Administration extensions

### P19-S1 — Company Chart of Accounts assignment administration

Status: DONE.

PR #307 exact final head `75e2ab1a47e2fad95be460426ea64b038109842c` passed Maven PR Tests run `33231705169`, job `99045589148`, and merged after owner acceptance.

Completed behavior:

- `chart_of_accounts.company_id` remains chart ownership authority;
- `company.active_chart_of_accounts_id` remains the current-chart pointer;
- Company Admin lists only company-owned charts and deliberately selects the current chart;
- DRAFT selection promotes to ACTIVE, while RETIRED/cross-company selection is rejected;
- prior ACTIVE charts and all existing account/history relationships remain intact;
- no migration or second chart/assignment authority was introduced.

### P19-S2 — Company reporting-default administration

Status: DONE.

PR #308 exact final head `e1f45ee0b0f96870425f9f96d10e960e3c86d3c0` passed Maven PR Tests run `33264570758`, job `99132280957`, and merged to `main` at `b4ef30643978eb926e97773a13a6102b0389244a` after owner acceptance.

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

Status: DONE.

PR #309 exact final head `6849fd70d6c47a886f4eef9967c4b69a38e380b6` passed Maven PR Tests run `33273006267`, job `99154805470`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. Owner acceptance was confirmed and PR #309 merged to `main` at `00d498705544e1a76d99b159f4b8fc23f80012a1`.

Completed behavior:

- nullable `company.ein VARCHAR(40)` is the sole live production EIN authority;
- V75 nondestructively backfills nonblank legacy `company_tax_profile.ein` while retaining legacy table/data;
- obsolete live `CompanyTaxProfile` JPA/query authority is retired;
- EIN is editable through the stable company profile lifecycle;
- no tax-filing, jurisdiction, filing-period, report, export, banking, or accounting workflow was introduced.

Governing design: `doc/P19-S3-company-ein-metadata.md`.

## 6. P20 — Authentication and runtime authorization

P20 is DONE. P20-S1 requirements merged in PR #310; P20-S2 authentication merged in PR #311; P20-S3 runtime authorization was delivered through PRs #312-#334 and final owner desktop acceptance. The completed authority is local H2 authentication with reserved ADMIN/MANAGER/ACCOUNTANT/VIEWER roles, current-session permission enforcement, fail-closed service guards and durable denial facts, authenticated audit actors, protected production mutation/import/database-administration boundaries, database-switch-safe service composition, and matching JavaFX permission gating.

Completion PR #335 merged to `main` at `6010a5563e70e58fc69a91197cbf1ec819bd346c`; post-merge Maven PR Tests run `34074737387`, job `101598529975` passed clean headless verification, Maven tests, and production JavaFX route compliance. Governing detail remains in `doc/P20-S1-authentication-authorization-boundary.md`, `doc/P20-S3-runtime-authorization.md`, `doc/P20-S3-authenticated-audit-actor.md`, `doc/P20-S3-database-administration-authorization.md`, `doc/P20-S3-javafx-permission-gating.md`, and the focused P20-S3 authorization documents.

## 7. P21 — Activity and Event Accounting

Purpose: make the existing `Activity` accounting dimension operator-manageable and then provide a read-only event/project/occasion accounting workspace over canonical Activity-linked Journal transactions. P21 extends current H2/JPA authority and does not create an event ledger, posting workflow, schedule subsystem, approval workflow, or parallel persistence.

Governing design: `doc/P21-activity-event-accounting.md`.

### P21-S1 — Activity administration

Status: DONE.

Completion evidence:

- PR #337 exact implementation head `adea4f127141a26a6ce11a601c93591e2c145e3c` passed Maven PR Tests run `34551426152`, job `103114964058`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- owner desktop verification was explicitly accepted;
- PR #337 merged to `main` at `f3509ff55e4da404f47e3f837ea525ad8dbeeb94`;
- post-merge `main` Maven PR Tests run `34667078105`, job `103481051301` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- the merged Activity authority remains the stable-ID H2/JPA `Activity` model with governed deactivate/reactivate and safe unused delete semantics; Journal and SCLX consume the same authority.

Required reading:

- root `AGENTS.md`;
- `doc/PLAN.md`;
- `doc/P21-activity-event-accounting.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`;
- `doc/accounting/transaction-lifecycle.md`;
- `doc/data-exchange/sclx.md`;
- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-javafx-permission-gating.md`.

Required inspection:

- `Activity`, `TxnSplit`, `Txn`, `BudgetCategory`, and all current activity-related Flyway constraints;
- `TransactionEntryService`, `TransactionReferenceDataService`, `CompanyOwnershipService`, and current stable-ID durable admin service patterns;
- SCLX activity snapshot, preview, portable-identity, and commit paths;
- `WorkspaceServices`, `UiServiceRegistry`, `PanelFactory`, `AppPanelId`, `NavigationPane`, `ProductionWorkspaceWindow`, and `UiPermissionGate`;
- current table-state, production route-compliance, authorization, and durable-record lifecycle tests;
- donor `benbaron/NonprofitAccounting` Activity/Event Accounting code only after current-repository inspection.

Implemented company-scoped stable-ID Activity create/update and governed lifecycle maintenance using current H2 authority. Activity code/name remain mutable business data; code remains company-unique; Journal and SCLX continue to consume the same Activity authority. Service-owned mutations require `BOOKKEEPING_WRITE`; VIEWER remains read-only; no parallel Activity cache or event entity was introduced. Physical-delete eligibility is determined from authoritative Journal and interchange usage, while used history is preserved through deactivate/reactivate.

Completion gate satisfied:

- stable-ID Activity create/update/lifecycle behavior is atomic and company-scoped;
- invalid, duplicate, cross-company, and unauthorized writes fail without partial mutation;
- used Activity history is preserved under the adopted lifecycle rule;
- P20 reserved-role/session switching behavior applies immediately at direct service and JavaFX boundaries;
- production UI follows current New/Save/dirty-state/table/layout/lifecycle rules and truthfully explains unavailable deletion;
- active Journal Activity choices refresh through existing H2 reference-data authority;
- existing SCLX Activity import/export compatibility remains intact;
- focused JUnit/H2/JavaFX tests, exact-head Maven PR Tests, and production JavaFX route compliance pass;
- owner desktop acceptance is recorded.

### P21-S2 — Event Accounting workspace

Status: DONE.

Completed behavior is the read-only **Accounting -> Event Accounting** workspace over canonical company-owned `Activity`, `Txn`, and `TxnSplit` authority. Income/expense/net use signed natural-balance Activity-tagged splits, including negative corrections/reversals. Related bank movement remains a separate contextual projection and qualifies only through configured `CompanyBankAccount` evidence plus `ASSET + AccountFunction.BANK + DEBIT normal balance`; it is not presented as an Activity allocation merely because the bank split shares the transaction.

The workspace supports active/inactive Activity selection, fiscal-year-to-selected-period default dates with explicit-date detachment/reset, all-funds or stable-ID fund filtering, canonical split detail, related bank inflow/outflow and cleared facts, factual closeout review, and drill-through to the existing Journal by transaction ID. It is available to VIEWER and all higher reserved roles and introduces no durable mutation, `Event` entity, schema migration, alternate ledger/bank model, or SCLX write authority.

Completion evidence:

- PR #339 exact implementation head `a2397749dda98fe098920c7fb97a2417e9a3fdfd` passed Maven PR Tests run `34675164328`, job `103503607827`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- PR #339 merged to `main` at `942cf2c9af69bbb3973184bc4da1d94d4e1d1a70`;
- post-merge `main` Maven PR Tests run `34721261596`, job `103627445343` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- owner desktop verification was explicitly accepted on 2026-09-12;
- P21-S2 and P21 are complete.

## 8. Advancement rule

P21-S1 and P21-S2 are DONE, so P21 is complete. No later P21 slice is active. Candidate donor workflows such as donor/receipt management and monthly-close assistance remain uncommitted future candidates and require a deliberate PLAN amendment before implementation.
