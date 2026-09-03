---
plan_version: 276
active_phase: P20
active_slice: P20-S3
active_status: VERIFYING
active_branch: codex/P20-S3-bank-csv-authorization
active_pull_request: 328
active_head: d5fe34f12be75e88dd4fdb32f9215f8d469745d7
next_action: "PR #328 behavior/documentation head d5fe34f12be75e88dd4fdb32f9215f8d469745d7 passed Maven PR Tests run 33777774277, job 100723658952. Publish this PLAN-only verification successor, validate Maven PR Tests on that exact final PR head, then stop before merge for owner acceptance."
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
| P20 | Authentication and runtime authorization | P20-S3 IN_PROGRESS |

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

### P20-S1 — Authentication and authorization requirements boundary

Status: DONE.

PR #310 exact final head `17077c2c2ba68a7c152554bccde04f3bb2aaa6ce` passed Maven PR Tests run `33279457435`, job `99172041566`, and merged to `main` at `3d4f0d775e454e506ca4e20d7101eff613f47d0a` after owner acceptance.

Governing requirements: `doc/P20-S1-authentication-authorization-boundary.md`.

### P20-S2 — Authentication implementation

Status: DONE.

PR #311 exact final head `630d022584449298ad900ee00126f41eafe96917` passed Maven PR Tests run `33292407265`, job `99206247747`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. Owner confirmed the tests and merged PR #311 to `main` at `40a4a37aaeed7fa94d847009d55a177f94b1d407`.

Completed behavior includes:

- H2-owned optional `AppUser` credentials; roles never own passwords;
- passwordless reserved ADMIN/MANAGER/ACCOUNTANT/VIEWER accounts and per-company assignments;
- singleton effective ADMIN and required ADMIN assignment protection;
- explicit login/logout and authenticated in-memory session identity;
- effective reserved roles derived from current company-scoped H2 assignments;
- company-switch role recomputation and no-access rejection;
- default inactivity timeout disabled, with ADMIN-controlled nonzero configuration;
- ADMIN password set/replace/clear including self;
- explicit offline ADMIN credential recovery;
- factual security events and real User Admin authentication controls.

### P20-S3 — Runtime authorization enforcement

Status: IN_PROGRESS.

Foundation PR #312 exact final head `db3a30289aa17b967948a79a048f9ebdf9c5042e` passed Maven PR Tests run `33293417227`, job `99208891671`, and merged to `main` at `1b11df7cdc98775c618e8489ca7608bde36ea547`.

Fund-service PR #313 exact final head `9cb928554546be938841cd391e6e13995ad77918` passed Maven PR Tests run `33337392090`, job `99326671667`: clean headless verification, full tests, and production JavaFX route compliance all succeeded. The owner merged PR #313 to `main` at `19f85937b154cb8a6ad6517a4564425440ae0aa1`.

Budget Category PR #314 exact final head `17fe284c2c6986efdfd076d47e68caa3c44f3167` passed Maven PR Tests run `33337813403`, job `99327841889`: clean headless verification, full tests, and production JavaFX route compliance all succeeded. The owner merged PR #314 to `main` at `79eb9e52f4bf4a834587f9e66d34a60c1749f71d`.

Account PR #315 exact final head `17f0d4e5675007f5e136e0c948e413fc97f0a3a4` passed Maven PR Tests run `33347854910`, job `99355352917`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #315 to `main` at `e96b33fb6b7a8016ff4568737bab4cd1bc5ec6f2`.

Budget Plan PR #316 exact final head `bcae13e291738abb5003ad6899ced7ac9496db08` passed Maven PR Tests run `33350574686`, job `99363005574`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #316 to `main` at `bf7a373208c7f207cb4764140768cb6d793209c0`.

Bank Configuration PR #317 exact final head `62e882d807ea4ffeeb9c66ffefac075635f86703` passed Maven PR Tests run `33351860101`, job `99366659502`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #317 to `main` at `fee2728eca53c8d0da7a9d0bcbddc75a7daa4965`.

Company Administration PR #318 exact final head `89e7948fbfda3b776cdc5f829c88aea1683cf5e8` passed Maven PR Tests run `33354408246`, job `99373686535`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #318 to `main` at `4991916c114c8e1ecc96367201bc3f841d3c3dc9`.

User Administration PR #319 exact final behavior/documentation head `dcec480d5702662567e29fc37a14f90cafec0531` passed Maven PR Tests run `33431783480`, job `99618708073`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #319 to `main` at `b7397b72395033d0cbb57df418b11e3b24807bc1`.

Security Administration PR #320 exact final head `4514737d7c695a3a0a9c358575dff71aa4313dd8` passed Maven PR Tests run `33447487143`, job `99669737473`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #320 to `main` at `67fdcc819f2716263ea952ffff60e3ad87c7fea4`.

Journal PR #321 exact final head `b7310405390c342e02a378606f766d8a1173b3de` passed Maven PR Tests run `33468647019`, job `99733662262`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #321 to `main` at `77be356ed3351936b623b208898f61a0acec23ee`.

Fixed Asset PR #322 exact final head `b8144b9ea609a2150c63912b3ad7e83aab87ff46` passed Maven PR Tests run `33552680761`, job `100005736808`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #322 to `main` at `2f22b2cc3f2a40e77151d6c2892ad62772cdcc05`.

Inventory PR #323 exact final head `b8e861c6107aa7de0ecd4c1aa024b60effd8aa68` passed Maven PR Tests run `33565845969`, job `100048785715`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #323 to `main` at `4d1a741b6c7bc70c52c4387cabea7d7fb21ee1b7`.

Previous reconciliation authorization tranche: PR #324 final head `1a420aae36fb01c019a5b72f487591bcfcaaf54a` merged to `main` at `5d591e4d767264490611870d80fe271303b79017`. Its behavior/documentation head `ca124f846178f5b1abcf34e7c9cafab1a079bbdb` passed Maven PR Tests run `33578682023`, job `100088178045`; the final PLAN successor was merged with the PR and the post-merge `main` workflow run `33582591691` also passed.

Previous period-close authorization tranche: PR #325 final head `bc62b4c192d7ecb2098ee86bd787e7a3db163b31` passed Maven PR Tests run `33590915729`, job `100124557154`, and merged to `main` at `128660a4793e2232920ff0ec32ee8d8c7736d18f` after owner acceptance. Post-merge `main` workflow run `33653699552` also passed.

Completed P20-S3 behavior to date:

- fixed `ApplicationPermission` policy and multi-role union are established;
- `AuthorizationGuard` reads the current authenticated session on every decision and writes durable `AUTHORIZATION_DENIED` facts;
- `ServiceAuthorization` provides a nullable adapter so legacy/test constructors remain source-compatible while guarded constructors fail closed;
- Fund service create/update/upsert/delete requires `BOOKKEEPING_WRITE`; Fund queries remain readable;
- direct H2 Fund tests prove VIEWER denial, immediate ACCOUNTANT enablement, immediate switch back to VIEWER denial, and wrong-company rejection without stale authorization state;
- Budget Category service-owned `upsert(...)` requires `BOOKKEEPING_WRITE`, while caller-owned import transaction seams remain governed by the outer import commit;
- direct H2 Budget Category tests prove the same VIEWER/ACCOUNTANT/company-switch behavior;
- Account stable-ID `save(...)` and service-owned code-addressed `upsert(...)` require `BOOKKEEPING_WRITE`, while caller-owned account import helpers remain governed by the outer import commit;
- direct H2 Account tests prove VIEWER denial/no write, immediate role/company switching, MANAGER/ADMIN/non-ADMIN union success, and preserved BANK/company/chart validation;
- Budget Plan service-owned draft/revision/save/activate/archive mutations require `BOOKKEEPING_WRITE`, while caller-owned budget import helpers remain governed by the outer import commit;
- direct H2 Budget Plan tests prove VIEWER denial/no write, immediate role/company switching, ACCOUNTANT/MANAGER/ADMIN/non-ADMIN union success, and preserved duplicate-scope and draft/version lifecycle protections;
- Bank Configuration service-owned Bank create/update and configured-bank-account create/update mutations require `COMPANY_ADMIN`, while list methods remain read-only and caller-owned import helpers remain governed by the outer import commit;
- direct H2 Bank Configuration tests prove VIEWER/ACCOUNTANT denial/no durable change, immediate role/company switching, MANAGER/ADMIN/non-ADMIN union success, preserved bank-ledger-account classification and lifecycle protections, and continued caller-owned import-helper use inside an explicitly authorized outer transaction;
- Company Administration stable-ID company create/update/deactivate and active Chart of Accounts assignment require `COMPANY_ADMIN`; `reportingDefaults.*` state writes also require `COMPANY_ADMIN`, while presentation-only company UI state retains `UI_PREFERENCE_WRITE`;
- direct H2 Company Administration tests prove VIEWER/ACCOUNTANT denial, MANAGER/ADMIN/non-ADMIN union success, immediate role/company switching, wrong-company rejection, preserved company/chart lifecycle protections, reporting-default bypass prevention, and continued VIEWER presentation preference persistence;
- User Administration stable-ID user/role/assignment mutations require `SECURITY_ADMIN` in the active-company context while read/usage queries remain non-mutating;
- direct H2 User Administration tests prove VIEWER/ACCOUNTANT/MANAGER denial/no durable mutation, ADMIN success, non-ADMIN union denial, immediate role/company switching, wrong-company and absent-session fail-closed behavior, durable authorization-denial facts, and preserved reserved/lifecycle protections;
- Security Administration password set/replace/clear and inactivity-timeout changes require `SECURITY_ADMIN`, while credential/configuration reads remain non-mutating;
- direct H2 Security Administration tests prove VIEWER/ACCOUNTANT/MANAGER denial/no credential or timeout mutation, ADMIN success, non-ADMIN union denial, immediate session/company switching, wrong-company and absent-session fail-closed behavior, durable authorization-denial facts, and preserved singleton-ADMIN/inactive-target protections;
- Journal service-owned entry/update/direct-edit/delete/reversal mutations require `BOOKKEEPING_WRITE`; Journal reads remain non-mutating and caller-owned transaction/import seams remain outer-governed;
- direct H2 Journal tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and non-ADMIN role-union success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned seam use;
- Fixed Asset service-owned create/update/status, depreciation, lifecycle commit, and lifecycle reversal mutations require `BOOKKEEPING_WRITE`; reads/previews and caller-owned import seams remain outside the service-owned write guard;
- direct H2 Fixed Asset tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned import seam use;
- Inventory service-owned create/update/status, confirmed movement commit, compatibility movement commit, and governed movement reversal mutations require `BOOKKEEPING_WRITE`; reads/previews and caller-owned import seams remain outside the service-owned write guard;
- direct H2 Inventory tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned import seam use;
- Reconciliation workspace session start/successor, manual statement entry, matching/unmatching, cleared-state, factual explanation, save/finalization, and direct reviewed-row cleared-state mutations require `BOOKKEEPING_WRITE`; configured-account/session/snapshot reads and caller-owned interchange seams remain outside the service-owned write guard;
- direct H2 Reconciliation tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned interchange seam use.
- Period Close service-owned close/reopen mutations require `BOOKKEEPING_WRITE`; range/history reads, `requireOpen(...)`, and caller-owned interchange restore remain outside the service-owned write guard;
- direct H2 Period Close tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, read access, and continued caller-owned interchange use.

Previous import-commit authorization tranche:

- PR #326 behavior/documentation head `effb5f3dcc7423a8946fbf0cdd3e1fb1027505ce` passed Maven PR Tests run `33705042519`, job `100492254318`;
- final PLAN-only head `8696b59a9d49d7d88a1ae994e9ba5be81a055098` was owner-accepted and merged to `main` at `09c209097fbd0bba71299c88db5745cc83943002`;
- post-merge `main` workflow run `33708842774`, job `100503792968` passed clean headless verification, full tests, and production JavaFX route compliance;
- `SclxImportCommitService.commit(...)` and `CoaCsvImportService.commit(...)` require `BOOKKEEPING_WRITE` at their outer atomic commit boundaries while nested caller-owned import seams remain outer-governed.

Governing import-commit design: `doc/P20-S3-import-commit-authorization.md`, `doc/data-exchange/sclx.md`, and `doc/interface-operation-matrix.md`.

Previous bank import review/acceptance authorization tranche:

- PR #327 final head `750b3493d1e90329ce62f2099c933a7b5189bf4c` passed Maven PR Tests run `33715100237`, job `100522508886`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded;
- the owner accepted and merged PR #327 to `main` at `0e7d71a1322446a8dfe4f5d98245a94c54b93922`;
- `BankStatementReviewService.commit(...)`, `BankImportReviewService.createReviewBatch(...)`, and `ReviewedStatementAcceptanceService.accept(...)` require `BOOKKEEPING_WRITE` before ordinary commit validation or durable mutation;
- strict statement preview and reviewed-row acceptance preview remain non-mutating and outside the write guard;
- `BankImportReviewService.importForInterchange(...)` remains a caller-owned SCLX seam and is deliberately not independently guarded;
- existing source hash, configured-account identity, duplicate/idempotency, reviewed-row accounting, closed-period/finalized-reconciliation, and rollback protections remain authoritative after authorization succeeds.

Governing bank-import authorization design: `doc/P20-S3-bank-import-authorization.md`, `doc/banking/import-and-reconciliation.md`, and `doc/interface-operation-matrix.md`.

Current bank CSV authorization tranche:

- branch `codex/P20-S3-bank-csv-authorization` starts from merged `main` `0e7d71a1322446a8dfe4f5d98245a94c54b93922`;
- behavior head `f6f41c5a0550251ffef4af9f148415c51852b201` adds guarded constructors without removing source-compatible unguarded/test constructors;
- direct `NormalizedBankCsvReviewService.commit(...)` requires `BOOKKEEPING_WRITE` before ordinary preview/actor/commit validation while `preview(...)` remains non-mutating;
- `BankCsvMappingProfileService.create(...)`, `replace(...)`, and `setActive(...)` require `BOOKKEEPING_WRITE` before profile parsing or transaction work while `list(...)` remains read-only;
- direct H2 coverage exercises VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and non-ADMIN role-union success, immediate current-session switching, absent/wrong-company fail-closed behavior, durable `AUTHORIZATION_DENIED` facts, and continued preview/list read access;
- no schema, migration, JavaFX layout, or authenticated-audit-actor change is included in this tranche.
- behavior/documentation head `d5fe34f12be75e88dd4fdb32f9215f8d469745d7` passed Maven PR Tests run `33777774277`, job `100723658952`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded.

Governing bank-CSV authorization design: `doc/P20-S3-bank-csv-authorization.md`, `doc/banking/import-and-reconciliation.md`, and `doc/interface-operation-matrix.md`.

Still required before P20-S3 completion:

- production `UiServiceRegistry`/current-session guard wiring for all guarded services, including mapped-CSV delegation through guarded `BankStatementReviewService`, guarded mapping-profile service construction, and guarded normalized-CSV service construction;
- authenticated identity as the authoritative actor for protected audit writes;
- database administration authorization;
- JavaFX global and panel-local mutation gating using the same fixed policy;
- governing interface/user-role documentation updates;
- final Maven PR Tests and owner desktop acceptance.

Required reading:

- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-fixed-asset-authorization.md`;
- `doc/P20-S3-inventory-authorization.md`;
- `doc/P20-S3-period-close-authorization.md`;
- `doc/P20-S3-import-commit-authorization.md`;
- `doc/P20-S3-bank-import-authorization.md`;
- `doc/P20-S3-bank-csv-authorization.md`;
- `doc/data-exchange/sclx.md`;
- `doc/accounting/period-close-design.md`;
- `doc/banking/banking-and-reconciliation.md`;
- `doc/administration/user-role-maintenance.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`.

Required inspection:

- `AuthenticatedUserSession`, `ReservedSecurityRole`, `AuthenticationService`, `SecurityAdminService`, `SecurityRepository`;
- `ApplicationSessionContext`, `UiSessionState`, `ProductionWorkspaceWindow`, `PanelHost`, `AppPanel`, `UiServiceRegistry`;
- every production mutation service/factory listed by the interface operation matrix;
- current free-form actor fields and audit-producing service paths;
- current role/session/security tests and source-route guard tests.

Implement the fixed reserved-role permission model consistently at shell/panel and authoritative service mutation boundaries. UI disabling is explanatory only; direct lower-privilege service calls must fail closed and write factual authorization-denial security events. Replace free-form audit actor authority with authenticated identity without creating a parallel audit identity.

Governing enforcement design: `doc/P20-S3-runtime-authorization.md`.

Completion gate:

- fixed permission matrix is implemented from current effective reserved roles with multi-role union;
- all production protected mutation routes use the central authorization guard;
- VIEWER cannot mutate durable business/accounting state even through direct service calls;
- MANAGER, ACCOUNTANT, and ADMIN boundaries match the adopted P20 contract;
- company/role switching immediately changes permissions without a stale cache;
- authenticated identity is the authoritative actor for protected audit writes;
- denial events are durable H2 `security_event` facts;
- JavaFX commands/actions reflect the same permissions and explain unavailable operations;
- Maven PR Tests and production JavaFX route compliance are green on the exact final head;
- owner desktop acceptance is complete.

## 7. Advancement rule

Execute only the active slice. Do not begin P20-S2 implementation until P20-S1 requirements are merged and current `main` is rescanned. Do not begin P20-S3 until P20-S2 is merged and authenticated session identity is authoritative.
