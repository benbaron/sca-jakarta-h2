---
plan_version: 284
active_phase: P20
active_slice: P20-S3
active_status: VERIFYING
active_branch: codex/P20-S3-post-merge-verification
active_pull_request: 334
active_head: 53b555722a78f58294f029c11fb27a91d1570bda
next_action: "Complete owner desktop acceptance across VIEWER, ACCOUNTANT, MANAGER, and ADMIN permission behavior plus company/session switching. P20-S3 implementation and post-merge CI verification are complete; do not mark P20-S3 or P20 DONE until owner desktop acceptance is explicitly recorded."
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
| P20 | Authentication and runtime authorization | P20-S3 VERIFYING |

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

Status: VERIFYING.

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

Completed bank CSV authorization tranche:

- PR #328 final head `449ab6b5947ad8d5e6148eced11e3dd60c61b857` passed Maven PR Tests run `33778610783` and merged to `main` at `d91262dbe22983a017e567aba6f7de5e723ecdb3` after owner verification;
- direct `NormalizedBankCsvReviewService.commit(...)` requires `BOOKKEEPING_WRITE` before ordinary preview/actor/commit validation while `preview(...)` remains non-mutating;
- `BankCsvMappingProfileService.create(...)`, `replace(...)`, and `setActive(...)` require `BOOKKEEPING_WRITE` before profile parsing or transaction work while `list(...)` remains read-only;
- direct H2 coverage exercises VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and non-ADMIN role-union success, immediate current-session switching, absent/wrong-company fail-closed behavior, durable `AUTHORIZATION_DENIED` facts, and continued preview/list read access;
- no schema, migration, JavaFX layout, or authenticated-audit-actor change was included in that tranche.

Governing bank-CSV authorization design: `doc/P20-S3-bank-csv-authorization.md`, `doc/banking/import-and-reconciliation.md`, and `doc/interface-operation-matrix.md`.

Completed production current-session authorization wiring tranche:

- PR #329 final head `b7748eb32a86bac302e0a1130da4549f64732339` passed Maven PR Tests run `33787354446`, job `100755384759`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded;
- the owner accepted and merged PR #329 to `main` at `7f190a68fe37284440225d6b90edfb2afde669c3`;
- `UiServiceRegistry` now creates one `AuthorizationGuard` per production `ServiceBundle`, bound to that bundle's `Jpa` and `ApplicationSessionContext.sharedSessionState()::authenticatedUser`, so authorization consumes the live current session without another cache or session authority;
- bundle-owned and on-demand protected services select guarded constructors, including Account, Fund, Budget, Bank Configuration, Fixed Asset, Inventory, Company/User/Security Administration, Journal, Reconciliation, Period Close, CoA/SCLX commit, bank review/CSV/profile/normalized review, reviewed-statement acceptance, and company preference/state writes;
- mapped CSV preserves one authorization owner through its guarded `BankStatementReviewService` delegate; database preparation creates a fresh target-`Jpa` guard and database-switch activation clears the old authenticated session;
- source-compatible unguarded constructors and documented caller-owned transaction/import seams remain intact.

Current authenticated audit actor tranche:

- branch `codex/P20-S3-authenticated-audit-actor` starts from exact merged `main` `7f190a68fe37284440225d6b90edfb2afde669c3`;
- guarded production audit-producing mutations derive `AuthenticatedUserSession.username` from the same current-session `AuthorizationGuard` that authorizes the write, rather than trusting caller actor text;
- `ServiceAuthorization.actor(...)` is the shared compatibility adapter inside the service package, while interchange services use public `AuthorizationGuard.requireActor(...)`; unguarded tests and explicitly caller-owned seams retain their established fallback actor behavior;
- Journal, fixed asset/depreciation/lifecycle, inventory, period close/reopen, reconciliation successor, reviewed-statement acceptance, CoA CSV, SCLX, strict/normalized bank review, and User Admin current-operation audit writes are covered;
- SCLX source period-close and audit-history actor values remain historical source facts and are not rewritten; only new local import/canonical-transaction audit facts use the authenticated current actor;
- `DesktopActorIdentity` resolves authenticated session identity first, protected JavaFX actor displays are read-only, and literal/workstation actors no longer act as authority on already-guarded production routes;
- Company Ownership Diagnostics was outside the actor tranche because its mutations are classified `DATABASE_ADMIN`; the following database-administration tranche owns that guard and actor conversion. Legacy `AccountingPeriodService` has no production route and remains non-authoritative;
- direct H2 regression coverage proves spoofed Journal/User Admin actor inputs are replaced by authenticated username, while source-route coverage requires authenticated actor derivation across all current guarded audit-producing production boundaries and read-only actor displays;
- there is no schema or migration change;
- PR #330 behavior/documentation head `d3667270f34bc971a87d887ae96141db5af0d900` passed Maven PR Tests run `33807059790`, job `100819950561`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- final exact PR head `abdef30d53655ee19d753ca80af2104e0efbff4a` passed Maven PR Tests run `33807732460`, job `100822104225`, was owner-accepted, and merged to `main` at `56c792c3787ac0a0d9cef980e8a07bee07b26b1c`.

Governing actor design: `doc/P20-S3-authenticated-audit-actor.md`.

Completed database administration authorization tranche:

- PR #331 exact head `ec544039444d014c3e50deceea9a653372b119a5` passed Maven PR Tests run `33815258946`, job `100845874647`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner verified and merged PR #331 to `main` at `841f17d91bf85f1337f3f71b4fcd719c26f15404`;
- post-login whole-database backup, restore-to-validated-copy, and validated-copy activation route through service-layer `DatabaseAdministrationService` requiring `DATABASE_ADMIN`, while persistence `DatabaseTransferService` remains policy-free;
- the transfer facade resolves the current `UiServiceRegistry` bundle guard on each operation so a long-lived workspace cannot retain the old database's authorization guard after switching;
- `CompanyOwnershipService.assignOwner(...)` and production `SampleCompanyService.createOrRefresh()` require `DATABASE_ADMIN`; ownership repair derives the factual audit actor from the authenticated ADMIN session;
- database selection/create/retry at the outer login gate remains deliberately pre-authentication;
- direct integration and source-route coverage are added for non-ADMIN denial/no mutation, ADMIN success, durable denial facts, authenticated repair actor, current-session changes, and guarded production composition;
- there is no schema or migration change.

Governing database-admin design: `doc/P20-S3-database-administration-authorization.md`.

Completed JavaFX permission-gating tranche:

- PR #332 final exact head `43405195a9598baf371a50d19f9ac7e5bd5d6185` passed Maven PR Tests run `33832728168`, job `100898934346`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #332 to `main` at `d2dba2c270c8c23594f1073f757c40d15d3d9186`; post-merge `main` workflow run `33833200591`, job `100900327559` also passed;
- global mutation commands declare their required fixed permission through `AppPanel.requiredPermission(...)`;
- `ProductionWorkspaceWindow` combines active-panel capability with current-session permission and returns an explanatory denial before dispatch if invoked directly;
- panel-local durable actions use the same fixed permission policy without replacing independent busy/selection/lifecycle disable reasons;
- the tranche corrected the directly blocking Chart of Accounts JSON import authorization gap by requiring `BOOKKEEPING_WRITE` at that commit boundary and using guarded production composition rather than relying on UI disabling;
- read/navigation/preview controls remain available; export and presentation-preference actions retain `EXPORT` and `UI_PREFERENCE_WRITE` respectively;
- focused JavaFX/session and source-route regression coverage is included;
- the final source-route assertion verifies the stable permission-check and denial-explanation behavior rather than depending on a local variable name;
- no schema or migration change.

Completed final P20-S3 reconciliation tranche:

- branch `codex/P20-S3-final-reconciliation` started from exact merged `main` `d2dba2c270c8c23594f1073f757c40d15d3d9186`;
- the tranche reconciled stale User Admin documentation to the implemented JavaFX `SECURITY_ADMIN` gating and recorded the completed #332/CI/merge evidence in this execution ledger;
- no Java production/test code, schema, migration, or interface-operation-matrix change was included;
- PR #333 behavior/documentation head `966c4dff0d214a3c8e29dc7895d529acfea32ac2` passed Maven PR Tests run `33833984753`, job `100902609878`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- PR #333 final exact head `5efced5e170db390292cdbf77a7a3b016538d718` passed Maven PR Tests run `33834473777`, job `100904026212`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #333 to `main` at `7c1f5ae69b7631de801fdb8169230967767316ac`;
- post-merge `main` Maven PR Tests run `33839082567`, job `100917493733` passed clean headless verification, repeated full Maven tests, and production JavaFX route compliance.

Current post-merge verification:

- current `main` is exact merge commit `7c1f5ae69b7631de801fdb8169230967767316ac` for PR #333;
- repository/governing-document inspection found no additional missing production service or JavaFX authorization boundary requiring another P20-S3 implementation tranche;
- the previously stale source-route assertion remains correctly based on stable `UiPermissionGate` behavior rather than a local variable name;
- P20-S3 remains in VERIFYING solely because owner desktop acceptance has not yet been recorded.

Still required before P20-S3 completion:

- owner desktop acceptance of the completed reserved-role permission behavior, including company/session switching, before P20-S3 is marked DONE:
  - VIEWER: protected mutation controls are disabled/explained while read/report/export behavior remains available according to policy;
  - ACCOUNTANT: bookkeeping mutation is available while Company Admin, Security Admin, and Database Admin are unavailable;
  - MANAGER: bookkeeping plus non-security Company Admin are available while Security Admin and Database Admin are unavailable;
  - ADMIN: all protected operations are available;
  - company switch: permissions update immediately from current effective roles without application restart;
  - Chart of Accounts JSON preview remains readable while accepted import requires `BOOKKEEPING_WRITE`.

Required reading:

- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-authenticated-audit-actor.md`;
- `doc/P20-S3-database-administration-authorization.md`;
- `doc/P20-S3-javafx-permission-gating.md`;
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
