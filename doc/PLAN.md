---
plan_version: 335
active_phase: P25
active_slice: P25-S1
active_status: VERIFYING
active_branch: codex/P25-S1-internal-fund-transfers
active_pull_request: https://github.com/benbaron/sca-jakarta-h2/pull/362
active_head: 81fde3393c3e7315cd7e7b718736bff9e9def8ca
next_action: "Inspect actual final published head of draft PR #362 with Maven/headless/Xvfb CI, repair any failure, then complete owner desktop acceptance and separately authorized merge. D03 continues to gate legal-entity mappings."
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
| P21 | Activity and Event Accounting | DONE through P21-S2 / PR #339; completion record PR #340 |
| P22 | Post-P21 correctness and authority corrections | DONE through P22-S6 / PR #348 |
| P23 | Accounting correctness and open-item integrity | DONE through P23-S5 / PR #355 |
| P24 | Named events and usable entry workflows | DONE through P24-S4C / PR #361 |
| P25 | Transfers, payment references, reporting classification | VERIFYING — P25-S1 local implementation; publication/CI/desktop/merge pending |
| P26 | Property, planning, recognition, conversion | BLOCKED — P23–P25 and relevant decisions |
| P27 | Submission, evidence, complete portability | BLOCKED — required P23–P26 contracts and template approval |
| P28 | End-to-end acceptance and release | BLOCKED — preceding required slices |

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

Status: DONE.

Foundation PR #312 exact final head `db3a30289aa17b967948a79a048f9ebdf9c5042e` passed Maven PR Tests run `33293417227`, job `99208891671`, and merged to `main` at `1b11df7cdc98775c618e8489ca7608bde36ea547`.

Fund-service PR #313 final head `9cb928554546be938841cd391e6e13995ad77918` passed Maven PR Tests run `33337392090`, job `99326671667`: clean headless verification, full tests, and production JavaFX route compliance all succeeded. The owner merged PR #313 to `main` at `19f85937b154cb8a6ad6517a4564425440ae0aa1`.

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
- Company Ownership Diagnostics was outside the actor tranche because its mutations are classified `DATABASE_ADMIN`; the following database-administration tranche owns that guard and actor conversion. The former legacy `AccountingPeriodService` had no production route, remained non-authoritative, and is retired by P22-S3;
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

Completed P20-S3 verification and owner acceptance:

- PR #334 exact final head `47903ad0db0556c19ba98f11a52022d56b73fea9` passed Maven PR Tests run `33921783760`, job `101181576090`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #334 to `main` at `7f611dd94b5d69d75e5dbed96730bea6a5941410`;
- post-merge `main` Maven PR Tests run `33944333008`, job `101247580392` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- repository/governing-document inspection found no additional missing production service or JavaFX authorization boundary requiring another P20-S3 implementation tranche;
- the previously stale source-route assertion remains correctly based on stable `UiPermissionGate` behavior rather than a local variable name;
- on 2026-09-06 the owner explicitly accepted the completed desktop role/permission behavior, including VIEWER, ACCOUNTANT, MANAGER, ADMIN, company/session switching, and the Chart of Accounts JSON preview/import boundary;
- all P20-S3 completion gates are satisfied, so P20-S3 and P20 are complete.

Completion-record publication:

- branch `codex/P20-S3-completion` starts from exact merged `main` `7f611dd94b5d69d75e5dbed96730bea6a5941410`;
- completion behavior/documentation head `e96d08eeb5b52982df89f304997d0ee3355fea50` records owner desktop acceptance and closes P20-S3/P20;
- draft PR #335 changed only `doc/PLAN.md` and merged to `main` at `6010a5563e70e58fc69a91197cbf1ec819bd346c`; post-merge Maven PR Tests run `34074737387`, job `101598529975` passed clean headless verification, Maven tests, and production JavaFX route compliance.

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

Completion-record publication:

- PR #340 exact completion head `3046eb014b355df67718c2ac7266737f56b40df5` passed Maven PR Tests run `34784883704`, job `103798424392`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- PR #340 merged to `main` at `b7e4f45daa4aa5befda89a164e3bd6ced876c07e`;
- post-merge `main` Maven PR Tests run `34793559393`, job `103822237567` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- PR #340's P21 completion state is retained. This corrective publication restores detailed P20 execution-ledger history that PR #340 compacted and makes no production-behavior change.

## 8. P22 — Post-P21 correctness and authority corrections

Purpose: correct semantic and authority defects discovered by the post-P21 repository audit without reopening completed feature phases or creating parallel persistence/workflows. Execute one corrective slice at a time from current `main`.

### P22-S1 — Dashboard authority and company/fiscal correctness

Status: DONE.

PR #342 final head `a20aebabaadaabc406cb352202e05baa258ae1ef` merged to `main` at `83da79b062d029cbb22b8e2ed448bd7f7f4a1b17` after owner acceptance.

Scope:

- scope every Dashboard ledger, cash, fund, budget, recent-transaction, running-balance, reconciliation, and monthly-result projection to the active persisted `Company`;
- interpret the top-chrome active-period value as the selected period start and project through the calculated period end using company fiscal-year configuration;
- derive Period Information from company fiscal authority plus `period_close_range`, not legacy `AccountingPeriod`;
- derive reconciliation status from `bank_reconciliation_session`, not legacy `reconciliation_run`;
- derive reconciled cash/unreconciled difference from canonical reconciliation-owned `TxnSplit.bankCleared` facts;
- stop presenting `open_item_snapshot` compatibility data as current Dashboard authority; until P22-S5 establishes canonical supplemental settlement/open-balance reporting, show Open Items as unavailable rather than fictional/current;
- add regression coverage proving another company and contradictory legacy compatibility rows cannot contaminate the active-company Dashboard;
- no schema migration, mutation-service change, alternate ledger, or reconciliation write path in this slice.

Required reading:

- root `AGENTS.md`;
- `doc/PLAN.md`;
- `doc/architecture/dashboard-workspace.md`;
- `doc/persistence-authority-inventory.md`;
- `doc/testing/production-workspace-test-plan.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`;
- `doc/accounting/period-and-correction-policy.md`;
- `doc/banking/banking-and-reconciliation.md`.

Required inspection:

- `DashboardHomePanel`, `DashboardQueryService`, `JpaDashboardQueryService`, `DashboardSnapshot`, `InspectorPane`;
- `Company`, `Txn`, `TxnSplit`, `CompanyBankAccount`, `BankReconciliationSession`, `PeriodCloseRange`, `BudgetPlan`, `BudgetLine`;
- `FiscalPeriodRange`, active-period workspace composition, Dashboard tests, reconciliation tests, and current migrations defining company ownership/cleared/close-range authority.

User-visible changes / manual owner testing:

1. With two companies containing different transactions, switch companies and confirm Dashboard cash, YTD, bank balances, recent transactions, budget rows, and reconciliation rows contain only the active company.
2. For a company whose fiscal year does not start January 1, select a period and confirm YTD/monthly values begin at the configured fiscal-year start and extend through the selected period end.
3. Reconcile/clear some but not all bank-cash activity and confirm Dashboard/Inspector Book Cash, Reconciled Cash, and Unreconciled Difference agree with Journal/reconciliation facts.
4. Confirm Bank Reconciliation Status names the configured bank account and current session status/difference rather than showing legacy import-run fields.
5. Confirm Open Items explicitly displays `Not available` until the canonical supplemental settlement slice is implemented; legacy snapshot rows must not appear.

Validation state:

- exact behavior head `406a41a8b5a3bcbd1ff1ab69c55f63a18981bf54` passed Maven PR Tests run `34866284854`, job `104050825643`;
- exact final head `a20aebabaadaabc406cb352202e05baa258ae1ef` passed Maven PR Tests run `34923265219`, job `104235794126`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S2 — Bank-statement export permission/busy binding correction

Status: DONE.

PR #343 exact head `6c58b05b2267cec7ba492cd4139ae6dbc7da2dc8` merged to `main` at `5bb8e28174e5133a2b115eda490da8dc30eb21be` after owner acceptance.

Scope delivered:

- removed `UiPermissionGate.gate(...)` from the three bank-statement export buttons because those controls also require a busy-state binding;
- bound each export button disabled state once as export busy OR denied `EXPORT` permission;
- retained permission-denied explanatory tooltips while keeping `tooltipProperty()` unbound so the production full-text tooltip installer can still operate;
- added stable JavaFX IDs and focused permission/busy/full-text-tooltip regression coverage;
- did not change export serialization, destination/overwrite behavior, services, persistence, interchange formats, or authorization policy.

Validation state:

- exact PR head `6c58b05b2267cec7ba492cd4139ae6dbc7da2dc8` passed Maven PR Tests run `35264848366`, job `105349328123`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35265278336`, job `105350785585`, completed successfully at merge commit `5bb8e28174e5133a2b115eda490da8dc30eb21be`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S3 — Retire obsolete alternate writable services

Status: DONE.

PR #344 exact behavior head `e7991d5771639cb19e7cad567bb3c2a2f7e62840` merged to `main` at `d68e8f52ac06022254a9fa221779bcb6ba6e2157` after owner acceptance.

Scope delivered:

- retired `PostingService`, whose direct `Txn`/`TxnSplit` write path had no current production or compatibility caller and bypassed the current company/authorization/period/correction command boundary;
- retired legacy `AccountingPeriodService`, whose writable `AccountingPeriod` close/reopen path had no current production or compatibility caller and was superseded by `PeriodCloseRangeService`;
- retired `CoaFundIo`, whose direct Fund/Chart/Account CSV/JSON writes had no current caller and were superseded by guarded administration/interchange services;
- removed tests that existed only to exercise those retired writer implementations;
- retained historical entities/tables, applied migrations, and live compatibility/history services where current consumers still exist;
- added source-level regression coverage preventing the three retired writer types from returning while preserving canonical replacements;
- reconciled ledger, persistence-authority, application-composition, and P20 actor documentation to the current authority map.

Validation state:

- exact PR head `e7991d5771639cb19e7cad567bb3c2a2f7e62840` passed Maven PR Tests run `35269724312`, job `105365693725`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35476426775`, job `105986345643`, passed at merge commit `d68e8f52ac06022254a9fa221779bcb6ba6e2157`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S4 — Journal company UI-state single authority

Status: DONE.

PR #346 final head `a2fbf19928be15b785da0e90730212d6dab76c38` merged to `main` at `471e3b0c269d079d730a170446c6a6db01e523d8` after owner acceptance.

Completed scope:

- removed the Journal delegate's duplicate Java `Preferences` persistence for table state and divider positions;
- retained `JournalWorkspaceCompliancePanel` / `CompanyUiPreferencesService` as the single company-owned H2 UI-state authority;
- preserved Journal behavior and visible layout while strengthening source guards.

Validation state:

- exact final PR head `a2fbf19928be15b785da0e90730212d6dab76c38` passed Maven PR Tests run `35484432468`, job `106008076783`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35548359913`, job `106178350043`, passed at merge commit `471e3b0c269d079d730a170446c6a6db01e523d8` with the same three gates successful;
- local Maven was unavailable in the execution environment, so no local Maven result is claimed;
- owner acceptance and merge are complete.

### P22-S5 — Supplemental/open-item reporting and eliminated-Schedules cleanup

Status: DONE.

PR #347 final head `86b88e09c733d51fbd5841af61777d6d9c3de1a2` merged to `main` at `39da667a7e2499dfb6dcd8122341cef4932f1dd3` after owner acceptance.

Base: merged `main` `471e3b0c269d079d730a170446c6a6db01e523d8`.

Scope / adopted design:

- retain `Txn` / `TxnSplit` as the sole canonical ledger;
- extend `txn_supplemental_line` nondestructively with optional stable `item_id`, exact `txn_split_id`, and `INCREASE` / `DECREASE` lifecycle effect;
- keep supplemental amount non-negative and derive direction from the lifecycle effect plus canonical split sign;
- preserve legacy supplemental rows with no lifecycle triple and report them as unmatched rather than guessing identity from `entryRef`;
- derive company-scoped as-of open balances in one `SupplementalOpenItemQueryService`, excluding `REVERSED` and future transactions;
- surface over-applied, unmatched, and inconsistent rows explicitly;
- make Journal the lifecycle input surface with new-item creation and apply-existing-item behavior; do not add a separate Open Items editor;
- add Accounts Receivable, Accounts Payable, Prepaid Expenses, Deferred Revenue, Other Assets, and Other Liabilities Report Library entries backed by the shared projection;
- restore Dashboard Open Items from that same projection and never read `open_item_snapshot` as current authority;
- retire unconsumed `AppPanelId.SCHEDULES` and `ScheduleEligibilityService` executable compatibility seams while retaining historical schema/entities nondestructively;
- repair reverse-and-replacement so replacement canonical splits retain the original supplemental lifecycle allocations while reversal rows do not duplicate supplemental effects;
- current SCLX is explicitly out of scope by owner direction: no SCLX format/import/export files are changed. Current SCLX does not round-trip P22-S5 lifecycle linkage, and production UI/docs must state that limitation rather than claiming portability.

Required reading / governing updates:

- root `AGENTS.md` and `doc/PLAN.md`;
- `doc/accounting/transaction-editor-and-journal.md`;
- `doc/architecture/application-composition.md`;
- `doc/architecture/dashboard-workspace.md`;
- `doc/interface-operation-matrix.md`;
- `doc/persistence-authority-inventory.md`;
- `doc/reporting/report-library.md`;
- `doc/ui_design_rules.md` and `doc/ui/editor-guidelines.md`;
- `doc/P22-S5-supplemental-open-items-user-testing.md`.

Validation state:

- baseline `main` merge `471e3b0c269d079d730a170446c6a6db01e523d8` passed post-merge Maven PR Tests run `35548359913`, job `106178350043`;
- exact behavior head `8ed1a340b8d748406f61f0da17a609c39278c15a` passed Maven PR Tests run `36082153566`, job `107906242503`: clean headless verification, full tests, and production JavaFX route compliance all succeeded;
- exact final PR head `86b88e09c733d51fbd5841af61777d6d9c3de1a2` passed Maven PR Tests run `36090114072`, job `107930549770`;
- post-merge `main` run `36091432198`, job `107934507846`, passed at merge commit `39da667a7e2499dfb6dcd8122341cef4932f1dd3`: clean headless verification, full tests, and production JavaFX route compliance all succeeded;
- local Maven is unavailable in the current execution environment, so no local Maven result is claimed;
- owner acceptance and merge are complete.

### P22-S6 — Stale production copy and compatibility wording cleanup

Status: DONE.

PR #348 head `d61fd8b0cacf7b89cb083fa9a24d77bbb715e3c2` merged to `main` at `a1e739254b4d63b2bc3df6b0d71aa6ef4658d2a4` after owner verification and acceptance.

Base: merged `main` `39da667a7e2499dfb6dcd8122341cef4932f1dd3`.

Scope:

- correct the production Settings explanation for legacy `defaultPrivilege` so it reflects completed P20 authentication and company-scoped authorization;
- keep `defaultPrivilege` disabled because the stored compatibility value is not authentication or effective authorization;
- reconcile stale historical owner-testing wording that otherwise instructs testers to expect the false pre-P20 claim;
- scan production JavaFX/help copy for other obsolete "not implemented", future, or compatibility statements and change only claims that are now factually false;
- add regression coverage for the corrected Settings message;
- no persistence, migration, accounting, authentication, authorization-policy, routing, or preference-consumer behavior change.

Validation state:

- owner verified and accepted P22-S6 before merge;
- PR #348 is merged into current `main` at `a1e739254b4d63b2bc3df6b0d71aa6ef4658d2a4`;
- no post-merge GitHub Actions run was returned for that merge commit when closure was recorded, so no post-merge CI result is claimed;
- P22-S6 and P22 are complete.

## 9. Advancement rule

P21, P22, P23 and P24-S1 are complete. Owner accepted P24-S1 and GitHub verified PR #356 merged on 2026-09-30. P24-S2 is accepted and merged in PR #357. P24-S3 is accepted and merged in PR #358; P24-S4A Budget Category maintenance is merged; P24-S4B and P24-S4C are DONE; P24 is complete. P25-S1 is READY for internal fund transfers; unresolved D03 remains a gate before entity mappings. Conditional P27-S5 remains unadopted.

## 10. Archived proposal and adoption

- [2026 deficiency correction action plan](archive/SCA_2026_Deficiency_Correction_Action_Plan.md) remains the immutable proposal archived by PR #350, merged at `849dc4e8f652d47aff05c124790f78d6dc7f93dc`. The archive PR's exact-head Maven PR Tests run `36289042355` passed.
- The owner subsequently adopted P23–P28 and selected P23-S1 on 2026-09-26. The [live governing program](P23-P28-runbook-correction-program.md) supersedes the proposal for execution scope; explicit pending policy decisions remain pending.

## 11. P23–P28 — Adopted correction execution ledger

Required reading for P23-S1:

- root `AGENTS.md` and this plan;
- [adopted program](P23-P28-runbook-correction-program.md);
- [baseline, evidence and acceptance contract](P23-S1-baseline-and-acceptance.md);
- [owner review and evidence collection](P23-S1-user-testing.md);
- `doc/P21-activity-event-accounting.md`;
- `doc/P22-S5-supplemental-open-items-user-testing.md`;
- `doc/interface-operation-matrix.md`, `doc/ui_design_rules.md`, `doc/ui/editor-guidelines.md`;
- `doc/accounting/transaction-lifecycle.md` and `doc/accounting/period-and-correction-policy.md`.

Required inspection for S1 is the finding ledger in the baseline contract: refresh source evidence and test inventory for G1–G14, inspect V77's existing lifecycle protections, establish workbook/runbook identity, and record installed-build evidence gaps. Future slice owners must perform the additional scoped implementation/migration/test inspection named there before editing code.

### Slice statuses and prerequisites

The adopted program owns each slice's deliverables and acceptance criteria. The table below owns execution status. Sequential prerequisites prevent multiple selected slices; accounting-policy and external-evidence dependencies are recorded as D01–D10 in the baseline contract. Input collection may occur in S1 without starting later implementation.

| Slice | Deliverable | Status | Prerequisite / gate |
|---|---|---|---|
| P23-S1 | Adopt scope and establish reproducible acceptance cases | DONE | Documentation validation, owner review, publication and merge |
| P23-S2 | Correct Budget vs Actual reversal treatment (G1) | DONE | P23-S1 merged; owning contract and D01–D10 gates as applicable |
| P23-S3 | Preserve historical open-item balances (G2) | DONE | P23-S2 merged; owning contract and D01–D10 gates as applicable |
| P23-S4 | Enforce and explain schedule-to-ledger completeness (G3) | DONE | P23-S3 merged; owning contract and D01–D10 gates as applicable |
| P23-S5 | Preserve open-item lifecycle in SCLX (G4) | DONE | P23-S4 merged; owning contract and D01–D10 gates as applicable |
| P24-S1 | Make event creation and discovery explicit (G14) | DONE | P23-S5 merged; owning contract and D01–D10 gates as applicable |
| P24-S2 | Make Fund/Event tagging obvious and reliable (G14) | DONE | P24-S1 merged; owning contract and D01–D10 gates as applicable |
| P24-S3 | Attribute generated inventory costs (G6) | DONE | P24-S2 merged; owning contract and D01–D10 gates as applicable |
| P24-S4 | Complete lookup maintenance (G8) | DONE | P24-S3 merged; owning contract and D01–D10 gates as applicable |
| P24-S4A | Budget Category maintenance | DONE | P24-S3 merged; A08 category workflow |
| P24-S4B | Payee/Counterparty maintenance | DONE | P24-S4A merged; A08 party workflow |
| P24-S4C | Merchant maintenance | DONE | P24-S4B merged; A08 merchant workflow |
| P25-S1 | Implement one internal fund-transfer operation (G7) | VERIFYING | P24-S4 merged; internal-only scope; D03 required before legal-entity mappings; other applicable acceptance gates retained |
| P25-S2 | Add structured payment/check references (G9) | BLOCKED | P25-S1 merged; owning contract and D01–D10 gates as applicable |
| P25-S3 | Complete check exception and reconciliation workflows (G9) | BLOCKED | P25-S2 merged; owning contract and D01–D10 gates as applicable |
| P25-S4 | Implement the adopted NMR accounting contract (G10) | BLOCKED | P25-S3 merged; owning contract and D01–D10 gates as applicable |
| P25-S5 | Add independent functional-expense classification (G10) | BLOCKED | P25-S4 merged; owning contract and D01–D10 gates as applicable |
| P26-S1 | Link fixed-asset acquisition and opening records (G11) | BLOCKED | P25-S5 merged; owning contract and D01–D10 gates as applicable |
| P26-S2 | Complete custody, supplies, and property-transfer records (G11) | BLOCKED | P26-S1 merged; owning contract and D01–D10 gates as applicable |
| P26-S3 | Complete event/fund budget planning and reporting filters (G12) | BLOCKED | P26-S2 merged; owning contract and D01–D10 gates as applicable |
| P26-S4 | Add governed prepaid/deferred recognition assistance (G12) | BLOCKED | P26-S3 merged; owning contract and D01–D10 gates as applicable |
| P26-S5 | Resolve inventory valuation limitations (G12) | BLOCKED | P26-S4 merged; owning contract and D01–D10 gates as applicable |
| P26-S6 | Provide controlled conversion and merchant-settlement assistance | BLOCKED | P26-S5 merged; owning contract and D01–D10 gates as applicable |
| P27-S1 | Finalize the form contract and resolve runbook conflicts (G5/G10/G13) | BLOCKED | P26-S6 merged; owning contract and D01–D10 gates as applicable |
| P27-S2 | Export the complete required workbook and supporting packet (G5) | BLOCKED | P27-S1 merged; owning contract and D01–D10 gates as applicable |
| P27-S3 | Add factual close readiness and evidence references (G3/G5/G13) | BLOCKED | P27-S2 merged; owning contract and D01–D10 gates as applicable |
| P27-S4 | Complete interchange coverage for all new fields (G4 and cross-cutting) | BLOCKED | P27-S3 merged; owning contract and D01–D10 gates as applicable |
| P28-S1 | Run the complete runbook acceptance matrix | BLOCKED | P27-S4 merged; owning contract and D01–D10 gates as applicable |
| P28-S2 | Release and close the correction program | BLOCKED | P28-S1 merged; owning contract and D01–D10 gates as applicable |
| P27-S5 (conditional) | Internal attachments and approvals | BLOCKED | Not adopted; explicit scope amendment and separate specifications required; not a prerequisite for baseline P28 |

P27-S1 consolidates earlier template/policy collection. Its position does not defer D02–D07: relevant facts must be settled before the earlier affected code slices, without marking P27 implementation active.

### P23-S1 handoff

Status: VERIFYING (local documentation implemented; not DONE).

- Base: `849dc4e8f652d47aff05c124790f78d6dc7f93dc`.
- Branch: `codex/P23-S1-adopt-correction-plan`.
- Pull request: https://github.com/benbaron/sca-jakarta-h2/pull/351 (draft). Owner authorized publication on 2026-09-26.
- Publication mapping: local `1993128` -> remote `739c31ab23f34794085d73fc54ce33777d32bda4`, tree `78f74144e79797f4b2fb8220f8d2a9b596f7800a`; local `215ba5b` -> remote `f01ae14a85c61d6bdd56f7988d6c2d41df1b37a8`, tree `b611bf60fece88bc90d8c3f9e826108925328020`. Both trees match; connected-service commit identities differ from local identities. This publication-handoff commit follows that reviewed content head.
- Completed: live adopted P23–P28 contract; all slice statuses/dependencies; G1–G14 source/test/owner/disposition ledger; A01–A14 reproducible specifications; runbook coverage matrix; retrieved named workbook identity/structure and instruction anchors; D01–D10 pending-input decisions; owner review instructions.
- No production code, migrations, workbook data or archived proposal changes.
- Validation: source/route/test inventory inspection and read-only workbook structure inspection completed. Local validation passed: four changed documents, 26 adopted slices plus conditional P27-S5, G1–G14/A01–A14 coverage, local Markdown links, unchanged archive, active selection, and `git diff --check`. An initial validator assumed 27 baseline slices; checking the actual adopted headings corrected that count to 26 plus one conditional slice, and the complete check passed. `mvn clean verify` attempted: exit 127, Maven unavailable; no Maven wrapper. No application tests, desktop acceptance or exact-head CI pass claimed.
- Remaining: owner supplies/verifies D01 installed-build evidence and confirms D02 submission authority; policy inputs gate only their affected later slices. S1 owner documentation acceptance, final-head CI and merge remain outstanding.
- CI: Maven PR Tests run `36292168364` started for published head `f01ae14`; it was in progress when this handoff was prepared. This handoff changes the head, so require the successor final-head run; no CI pass is claimed in this record.
- Next exact action: inspect final-head Maven PR Tests for PR #351, resolve any failure, and review `doc/P23-S1-user-testing.md` with the owner. Do not implement S2 until S1 merge is confirmed.

## 12. P23-S1 closure and P23-S2 selection

P23-S1 is DONE: owner merged PR #351 at `654825ba8f2855204cc52ca3302f878f8746e500`; exact final head `08ace942e745282a2d02aa053f7b3fa9837e8725` passed Maven PR Tests run `36292208118`. The owner explicitly selected P23-S2 on 2026-09-27. The preceding S1 handoff is historical.

P23-S2 is VERIFYING on `codex/P23-S2-budget-reversal-actuals`, based on that merged main. Scope is G1/A01 only; no schema, lifecycle-write, event UI, SCLX or open-item query changes. Required reading: AGENTS.md, this plan, adopted P23-S2 program contract, P23-S1 A01 baseline, budget model, transaction lifecycle/correction policy and report-library contract. Required inspection: BudgetPlanService, TransactionEntryService, TransactionCorrectionService, FinancialReportService, Txn/TxnSplit, FiscalPeriodRange, V48 status constraints, BudgetPlanServiceTest and correction/report tests.


### P23-S2 implementation and validation

- Completed: actuals explicitly include ENTERED and REVERSED canonical transactions by their own dates; signed expense/income rules retained; missing categories appear as separate unclassified income/expense groups per fund. No lifecycle writes, schema changes or invented persisted categories.
- Regression: `BudgetReversalActualsTest` reproduces A01 through real entry/reversal/replacement commands, checks same/later-period cutoffs, income signs, refunds, category/fund separation, July fiscal boundaries, and unclassified amounts. GL, Income Statement and event/fund projections are compared on equivalent scopes. Budget comparison itself still aggregates all events; event budget filtering remains P26-S3.
- Reproduction evidence: on the unchanged production query, all seven original regression cases failed while eight existing BudgetPlanServiceTest cases passed. Before-cutoff originals became 0 instead of ±100; replacement expense was −20 instead of 80 (income 20 instead of −80); prior fiscal-year expense became 0 instead of 100. The initial fixture omitted active-chart selection; that fixture was corrected before recording these seven accounting failures.
- Baseline compile passed. Focused gate passed: 19 tests, zero failures/errors/skips, covering new budget regression tests and existing budget/correction tests. Full verification and final handoff follow below.
- Governing specification: [budget actuals](accounting/budget-model.md#p23-s2-correction-actuals). [User-visible changes and manual acceptance](P23-S2-budget-reversal-user-testing.md).
- Remaining: publication authorization, PR/exact-head CI, owner desktop acceptance and merge. No PR or GitHub S2 result is claimed. P23-S2 is not DONE; P23-S3 stays blocked.


### P23-S2 final local handoff

- Status: VERIFYING; branch `codex/P23-S2-budget-reversal-actuals`; PR: none (not published).
- Reviewed implementation head: `d8fae86a2cb6fcfa47856db56de187b4538c19c9`. This documentation-only handoff commit follows that head; resolve the branch tip with `git rev-parse HEAD` before publication. The active-head field identifies the verified implementation commit.
- Final local gate: `mvn --settings /tmp/p23-maven-settings.xml clean verify` passed on 2026-09-27 using Maven 3.9.9: **801 tests, 0 failures, 0 errors, 31 skipped**, BUILD SUCCESS. New regression class: 8 passing cases, including event/fund reconciliation. Headless skips are not desktop acceptance. `git diff --check` and changed-document local link validation passed.
- Environment recovery: installed Maven in `/tmp` and used runtime proxy settings outside the repository. Initial dependency resolution failed with the stale proxy; an offline clean attempt lacked a cached plugin. Refreshing the private settings and rerunning online completed successfully. No project build configuration changed; no known failing test remains. The build-generated removal of the tracked manifest was restored.
- Changed documents: this plan, P23-S1 baseline execution cross-reference, accounting/budget-model.md, and P23-S2-budget-reversal-user-testing.md. Archive unchanged.
- Next exact action: obtain owner authorization for S2 publication under AGENTS.md section 5, then publish the reviewed commit sequence to the named branch without force, create the S2 PR with actual validation evidence, verify matching tree/head and exact-head CI, and record those results here. Owner desktop checks and merge remain required before DONE or advancement.


### P23-S2 publication

Owner authorized publication on 2026-09-27. Draft PR: https://github.com/benbaron/sca-jakarta-h2/pull/352. Branch: `codex/P23-S2-budget-reversal-actuals`; verified published content head: `0703e2a17abca00f3a9aa81069e65ceb2c91a21e`. The preceding local handoff is historical.

- Local `d8fae86a2cb6fcfa47856db56de187b4538c19c9` → remote `e7bc3e9e4104c9211d5c1bc363a78ed4ee49d1a7`, matching tree `eca2bd215884256216c4176ff09b25f15a67328f`.
- Local `5158780268318819523ca46ceead112264c17352` → remote `0703e2a17abca00f3a9aa81069e65ceb2c91a21e`, matching tree `f902e32d1fde4177199e5dc87578be0028b2adac`.
- Connected-service publication preserved commit messages, order and file trees. The branch was absent and created at the reviewed head; no force update occurred. Remote main remains the verified S1 merge base.
- This publication-record commit follows that content head. Require the successor exact-head Maven PR Tests run, not a result for an earlier head. At preparation, GitHub validation is pending; final result is recorded in the PR description after the run completes.
- Remaining: exact-head CI, owner execution of P23-S2-budget-reversal-user-testing.md, and merge. S2 stays VERIFYING and S3 stays BLOCKED. Next action: inspect PR #352's final-head checks and resolve any failure before owner review/merge.


## 13. P23-S2 closure

On 2026-09-27 the owner confirmed acceptance and merge of PR #352. GitHub verifies merge commit `321a3fd3a702931775ac47073229a3e389e4cb45` in current main. Final PR head `5d238989ed06ce1c5ad8774746080d6e239d7ec6` passed Maven PR Tests run `36337954378`, including clean headless verification, the additional test pass and production JavaFX route compliance. Local verification was 801 tests, zero failures/errors, 31 skips. No post-merge CI result is claimed.

P23-S2 is DONE. All preceding S2 pending-publication, acceptance and merge statements are historical. P23-S3 is READY, with no implementation branch or PR yet. It owns G2/A02 historical open-item balances; read the adopted S3 contract and required lifecycle/report sources before coding. No later slice has been implemented.

This documentation-only closeout is recorded on fresh branch `codex/P23-S2-closeout`, based on the verified merge above; local only, no closeout PR. Validation: merge and exact-head CI verified; `git diff --check` passed. When beginning S3, preserve this closeout record in the S3 plan update if it has not separately reached main. Next action: await owner selection of S3, then refresh main and create a fresh S3 branch.


## 14. P23-S3 execution

Owner selected S3 on 2026-09-27. Branch `codex/P23-S3-historical-open-items` starts from current main `321a3fd3a702931775ac47073229a3e389e4cb45` and preserves the S2 closeout record. PR: none. Scope: G2/A02 effective-date supplemental projection only; S4 completeness and S5 interchange remain separate.

Required reading/inspection completed: root AGENTS, PLAN, adopted program S3 and baseline A02; transaction/editor, lifecycle, correction-policy and report-library contracts; SupplementalOpenItemQueryService, Txn/TxnSupplementalLine, entry/correction services, V77 migration, six-report builder, Dashboard and Apply Existing Item consumers, existing query/report/migration tests.

Design finding: reversal transactions have inverse ledger splits and a unique reversalOf link but no supplemental allocations; replacements copy the original allocations with the same item identity. The projection must retain original allocations until their dated inverse facts apply, including reversal chains and permitted backdating. Derive corrections from those existing facts; do not add persisted allocations or a schema migration. Update the earlier current-status-only specification in this slice.


### P23-S3 implementation and validation

- Delivered: effective-date original/inverse projection for all six kinds; reversal chains and independent replacements; explicit explanation when an inverse precedes its source date; shared save-time increase validation using the same projection and current EntityManager. No migration, persisted duplicate reversal allocations, new report model, or UI layout changes.
- Baseline compile passed. Red reproduction against unchanged production query: 10 tests run, seven assertion failures and one missing-row error in the eight new cases; two existing cases passed. All six kind fixtures changed February from 75 to 100 after a March reversal; opening replacement erased prior balances and a backdated inverse had no row.
- Focused verification passed: 18 tests, no failures/errors/skips, covering query, six report definitions, legacy migration, Dashboard/report/control-ledger agreement, opening/settlement corrections and company isolation. Final full verification follows in the handoff.
- Specifications updated: [transaction supplemental lifecycle](accounting/transaction-editor-and-journal.md#supplemental-transaction-records-and-open-item-lifecycle), [reports](reporting/report-library.md#supplemental-open-item-reports), and [owner testing](P23-S3-historical-open-items-user-testing.md).
- Scope boundary: earlier direct-edit/delete policy remains in force; S3 does not reconstruct deleted versions. S4 completeness/over-application protections and S5 SCLX lifecycle portability remain separate. Owner desktop acceptance, publication/exact-head CI and merge remain outstanding; S3 is not DONE.


### P23-S3 final local handoff

- Status: VERIFYING; branch `codex/P23-S3-historical-open-items`; PR: none, not published. Verified implementation head: `04801e5265ae1c199af7367a8de3021f54d66c00`; this documentation-only handoff follows it. `git rev-parse HEAD` identifies the local tip for publication. The branch also carries the preserved S2 closeout commit.
- Final gate: Maven 3.9.9 `mvn --offline --settings .mvn/settings-github.xml clean verify` passed on 2026-09-27: **815 tests, 0 failures, 0 errors, 31 skips**, BUILD SUCCESS. All 16 supplemental query cases passed, including the final changed replacement amount. No test failure remains. Headless skips are not visual acceptance.
- Baseline compile and 18 focused checks passed. Final diff whitespace and changed-document local links passed. Maven was restored to `/tmp` because it was no longer present; all dependencies resolved from the existing local cache. No build configuration changed. Build-generated removal of the tracked manifest was restored.
- Completed: A02 reproduction/correction, shared read/save projection, regression/consumer checks, governing specifications, user testing notes and S2 closure carry-forward. No schema or SCLX changes.
- Remaining: owner publication authorization under AGENTS.md section 5, draft PR, exact-head CI, manual acceptance and merge. No GitHub S3 result is claimed. Next exact action after authorization: publish the reviewed local commit sequence without force, verify each tree and PR head, run/inspect CI, and record results. P23-S4 remains BLOCKED.


### P23-S3 publication

Owner authorized publication on 2026-09-27. Draft PR: https://github.com/benbaron/sca-jakarta-h2/pull/353. Branch: `codex/P23-S3-historical-open-items`. Verified published content head: `c1e98e59dc87656616358a50c0f1587a2577dc44`. Preceding local-only and authorization-pending handoffs are historical.

- Local `2f4f88cb2771299ee93fca9e01eb9e9f1b8d6995` → remote `b4c7d8dbc7ec1adcc3230c17a12af7bbcadb8bc9`, matching tree `81ee7e8696a83414889c2dbc5826ec28226e7d14`.
- Local `04801e5265ae1c199af7367a8de3021f54d66c00` → remote `54d09c8b6599c9f50db3e4699bf8247ae00954cf`, matching tree `4989a9a55cb6b18a36498cd485f551fe41515c9b`.
- Local `dfb50428d860b37e975076e3df1528d5eedd6472` → remote `c1e98e59dc87656616358a50c0f1587a2577dc44`, matching tree `f9ac4ae346a1fb5031c14ee98b818551d75c9f76`.
- All messages, ordering and file trees preserved through connected-service publication. A documentation transfer mismatch was caught before branch creation and corrected with exact blob bytes; only matching trees were published. No force update occurred.
- This publication-record commit follows the reviewed content head. CI is pending at preparation; inspect the successor final-head Maven PR Tests run. The final result and head are recorded in the PR description after completion.
- Remaining: final-head CI, owner desktop checks in P23-S3-historical-open-items-user-testing.md, and merge. S3 remains VERIFYING; S4 remains BLOCKED. Next action: resolve any final-head check failure, then owner acceptance/merge.


## 15. P23-S3 closure and P23-S4 policy gate

Owner accepted and merged S3 on 2026-09-27 (America/Denver), then instructed proceeding to S4. GitHub confirms PR #353 merged into current main at `114cc045761fb64c5e22b03f1f8ab2a3944024e0`. Exact final head `231fa1a643d557c3175d5606e4109e4d9432d328` passed Maven PR Tests run `36356514191`. S3 is DONE; preceding outstanding S3 acceptance/merge statements are historical. No post-merge CI result is claimed.

S4 is selected on fresh branch `codex/P23-S4-supplemental-completeness`, based on that main. PR: none. Status: BLOCKED on the explicit D08 prerequisite in P23-S1-baseline-and-acceptance.md. Inspection confirms that full allocation enforcement affects historical SCLX imports, which currently carry no lifecycle linkage. The adopted reject-excess default is already settled; the historical import exception and remediation contract still need resolution.

Completed: merge/CI verification; S4/A03/D08 contract review; entry/correction/import boundary inspection; [concrete D08 policy proposal](P23-S4-completeness-policy-proposal.md). The proposal preserves historical data through an acknowledged exception while rejecting incomplete new entries and excess applications, and keeps S5 portability separate. No production changes or tests were made while the policy gate is unresolved. Documentation-only validation: local links and diff whitespace. No application validation or S4 completion is claimed.

Remaining: owner decision on the proposed legacy exception contract; implementation, regression/concurrency/consumer testing, full verification, publication authorization, exact-head CI and manual acceptance. Next action: adopt or revise the proposal, update D08 to the actual decision, mark S4 IN_PROGRESS and perform remaining required UI/source inspection before implementing. Local documentation handoff only; not published.


## 16. P23-S4 policy adoption and implementation

Owner adopted D08 on 2026-09-27 after clarification of SCLX behavior. The [policy](P23-S4-completeness-policy-proposal.md) is now governing. Prior BLOCKED statements are historical. S4 is IN_PROGRESS on `codex/P23-S4-supplemental-completeness`; no PR yet. New entries require complete explicit allocations; excess applications are rejected; legacy imports require explicit acknowledgment and remain visibly incomplete until repaired. No guessed links or new overpayment workflow.


### S4 implementation and validation handoff

- Implemented strict complete control-split allocations for normal and generated entries; company-lock serialization and effective-date availability checks for writes, edits, deletes and corrections; excess application rollback.
- Implemented explicit legacy SCLX acknowledgment, source/hash audit evidence, precise per-line preview warnings (including textual decimal amounts), and truthful preserved-data incompleteness. No lifecycle portability, guessed allocations, schema migration or overpayment workflow is introduced.
- Added all-six-kind control reconciliation with ledger/explained/gross unmatched/difference, stable transaction/split diagnostics, supplemental readiness, text/CSV/table output and Journal repair navigation. Offsetting missing allocations cannot cancel gross deficiencies. P27 general close readiness remains separate.
- Governing documents updated: [adopted D08 policy](P23-S4-completeness-policy-proposal.md), [decision baseline](P23-S1-baseline-and-acceptance.md), [Journal](accounting/transaction-editor-and-journal.md), [Report Library](reporting/report-library.md), [SCLX](data-exchange/sclx.md) and [interface matrix](interface-operation-matrix.md). [Desktop/user acceptance instructions](P23-S4-supplemental-completeness-user-testing.md) cover the visible behavior and remaining visual check.
- Evidence: baseline compile passed after replacing two corrupt local Maven cache artifacts (ECJ and POI; no project dependency/configuration change). New missing/partial and competing-application regression tests first failed on prior behavior, then passed. Focused integration run: 52 tests, zero failures/errors/skips. An intermediate full run exposed old assertions that expected permitted excess applications or omitted control rows; those assertions were updated to the adopted contract. The first full verification attempt also caught a test-call signature typo, corrected before the successful run. Full verification before the final textual-decimal edge-case fix passed: 820 tests, zero failures/errors, 31 headless skips. Final-source `mvn --offline --settings .mvn/settings-github.xml clean verify` passed on 2026-09-27 (America/Denver): **820 tests, 0 failures, 0 errors, 31 skips**, BUILD SUCCESS. This includes the textual-decimal regression. Local documentation links and `git diff --check` also passed. No known test failure remains; headless skips do not establish desktop visual acceptance.
- Status: VERIFYING, branch `codex/P23-S4-supplemental-completeness`, PR: none. Verified implementation commit: `b7ee73cddcb2a1b4511383f619d267ba331e0f87`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the local tip for publication. Worktree was reviewed; generated manifest restored. No published-head CI or desktop visual acceptance is claimed. S5 remains BLOCKED until S4 is merged.
- Next exact action: request owner publication authorization under AGENTS.md section 5; after authorization publish the exact reviewed commit sequence without force, verify trees/head and CI, and complete owner desktop acceptance before merge.


### S4 authorized publication — PR #354

Owner authorized publication on 2026-09-27 (America/Denver). Draft [PR #354](https://github.com/benbaron/sca-jakarta-h2/pull/354) targets unchanged main `114cc045761fb64c5e22b03f1f8ab2a3944024e0`. Connected-service publication preserved the reviewed three-commit sequence/messages; every tree matched the corresponding local tree. Remote head `524c635f3c56167770637ba2d92e08f731b95fcd` corresponds to local `88664fa9b4785f94d633c49e3c6425ba0314675c`. Commit IDs differ because publication creates server-authored commit objects; content is identical.

Initial Maven PR Tests run `36375036852` started on that head. Inspection of the existing CI workflow found that the new renderer geometry/repair test would remain headless-skipped even though other route tests run under Xvfb. This follow-up adds `FormattedReportFxRendererTest` to that existing Xvfb test list so CI actually exercises the new behavior. No production source changed after the locally successful 820-test gate. Final-head CI remains pending; desktop visual acceptance, owner acceptance and merge remain required. Previous publication-authorization requests are satisfied. S4 remains VERIFYING and S5 BLOCKED.


## 17. P23-S4 closure and P23-S5 execution

Owner confirmed completion and instructed continuing on 2026-09-27 (America/Denver). GitHub verifies PR #354 merged at current main `820ab4235350999a8123d2b195cb18eaeb6793c0`. Final head `cf19103e15c27bb8e56fde0e0ee3b007e6220ea2` passed Maven PR Tests run `36375133299`, job `108779350961`, including both report renderer tests under Xvfb without skips. S4 is DONE; earlier pending S4 records are historical.

S5 is IN_PROGRESS on fresh branch `codex/P23-S5-sclx-lifecycle` from that main. Scope is G4/A04 only. Required inspection covers SCLX specification, snapshot/serialization/preview/identity/commit/correction code, canonical entry/query services, V77 and existing supplemental/import tests. Baseline compile passed using restored Maven 3.9.9; no build configuration change.

Design: add optional version-1 `lifecycle` objects to existing supplemental detail records, carrying the intrinsic item UUID, effect and portable transaction-line reference. Missing objects remain legacy. The existing detail identity/hash owns conflict handling; no second item ledger or migration. Linked exports preserve canonical split order and validate links. Import stages complete transaction/correction history within its existing atomic boundary and validates allocations and effective balances after relationships exist, independent of file ordering. Ordinary/generated entry validation stays strict. Unsupported versions, malformed or foreign/dangling links block preview/commit. Legacy acknowledgment remains explicit and never suppresses invalid linked data.


### S5 implementation and verification

Implemented optional version-1 lifecycle payloads within existing supplemental records, export-side reference validation, preview rejection of invalid linkage, allocation-aware legacy warnings, and canonical command mapping. Existing detail hashes include lifecycle content for conflict review. Linked exports preserve source/inverse split correspondence and numeric portable ordinals; ordinary exports retain established ordering. No migration or new persistent authority.

The SCLX transaction batch uses the existing entry service with a scoped history writer. Commands, ownership, period and line rules remain enforced. Complete-source/inverse coverage and effective available balances are checked after correction relationships are restored; failure marks the caller transaction rollback-only. Normal/generated entry APIs remain immediate and strict. Legacy acknowledgment never waives malformed links, foreign item identity or excess allocation.

Existing baseline compile and 31 supplemental/import tests passed. Focused integration gate passed 38 tests after correcting a regression that initially applied linked inverse correspondence to unrelated legacy corrections. Eleven new round-trip cases passed, including all six kinds, partial/reversed/replacement history, reordered transaction arrays, repeat import/re-export, malformed links, changed identities, foreign-company conflict, 60+60 against 100 batch rollback, repeated control accounts with thirteen ledger lines, and explicit acknowledgment for an inverse of an existing legacy source. A detached-baseline regression run was initiated before a runtime replacement; its temporary log is unavailable, so no red-run result is claimed. Final `mvn clean verify` passed on 2026-09-28 (America/Denver): 831 tests, zero failures/errors, 31 headless skips. This includes all eleven new lifecycle cases after the final source-completeness review.

Compatibility inspection: external SCLX specification/workbook exporter at `109b99e360d2bf5eaf6bbf829c3e808c29e7c2e0` was read only. Its current v14 exporter emits legacy supplemental details without lifecycle objects; the optional extension fits the existing schema extension boundary. No external repository or workbook was changed, and no Excel round-trip fidelity is claimed. Governing [SCLX specification](data-exchange/sclx.md#p23-s5-versioned-supplemental-lifecycle), [Journal contract](accounting/transaction-editor-and-journal.md#p23-s5-sclx-history-validation) and [S5 user testing](P23-S5-sclx-lifecycle-user-testing.md) describe the actual behavior and reduced-information compatibility.

Status: VERIFYING on `codex/P23-S5-sclx-lifecycle`, based on main `820ab4235350999a8123d2b195cb18eaeb6793c0`; PR: none. Remaining: publication authorization, exact published-head CI, desktop acceptance and merge. No S5 GitHub or visual result is claimed. Verified implementation commit: `83bb730a64626cc8b5c3f0bf43fef614d162fc9c`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the local publication tip. Generated tracked manifest restored and diff whitespace checked. Next exact action: obtain S5 publication authorization, publish the reviewed commit sequence and inspect final-head CI.


### S5 authorized publication — 2026-09-28 (America/Denver)

Owner explicitly authorized S5 publication. Draft [PR #355](https://github.com/benbaron/sca-jakarta-h2/pull/355) targets main `820ab4235350999a8123d2b195cb18eaeb6793c0`. Local Git push lacked credentials; connected-service publication preserved both reviewed commit messages, sequence and exact trees:

- Local `83bb730a64626cc8b5c3f0bf43fef614d162fc9c` maps to remote `92936695411ac965539162bf0ce48da7e1d1655b`, tree `6b58916a16350cb0010347a21f6f63d4d8104289`.
- Local `5e4b50e6a933ca97535fec5abfd9c1a2ba4a0f88` maps to verified PR head `7f21285bb4568f6768e2501368339b454f9771aa`, tree `3aadafce6a6c858430a183fb147490f19541ea79`.

Earlier publication-authorization requests are satisfied. This documentation-only publication record follows that content head. GitHub validation is pending at preparation; inspect the successor final-head Maven PR Tests run and record its outcome in the PR description. Local full verification passed 831 tests with zero failures/errors and 31 headless skips. Desktop visual/user acceptance and merge remain outstanding. S5 remains VERIFYING; no later slice is activated.


## 18. P23 closure and P24 readiness — 2026-09-29 (America/Denver)

Owner confirmed acceptance and merge of S5. GitHub verifies [PR #355](https://github.com/benbaron/sca-jakarta-h2/pull/355) merged at current main `74efca9bd67d4e47c5d54b07822691cf78e49034`. Its final head `82487d07b5e14b2cecac3c3add938e7bf0acb26f` passed [Maven PR Tests run 36515069363](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36515069363), job `109235479177`: both full test passes reported 831 tests, zero failures/errors and 31 headless skips; the Xvfb JavaFX pass reported 11 tests with zero failures/errors/skips. Owner acceptance is recorded separately from automated visual checks. Earlier S5 pending acceptance/publication/merge records are historical.

P23-S5 and P23 are DONE. P24-S1 is READY, with no implementation branch or PR. Its scope is named event creation and discovery through the existing Activity authority; Fund/Event Journal tagging belongs to P24-S2. D01 installed executable, launch route, role/company, display/scaling and saved-layout evidence remains required for P24-S1 reproduction acceptance; no such evidence is invented by this closeout.

This documentation-only closeout is on fresh branch `codex/P23-closeout`, based on the verified merge above; the merged S5 branch is not reused. Validation: GitHub merge/final-head CI verification and `git diff --check`; no application changes or additional application test run. Next exact action: start P24-S1 scoped inspection from current main when execution resumes, preserving this closure record. No P24 implementation has begun.


## 19. P24-S1 execution — 2026-09-29 (America/Denver)

Owner selected the next slice. Fresh branch `codex/P24-S1-event-discovery` starts from main `74efca9bd67d4e47c5d54b07822691cf78e49034` and carries the documentation-only P23 closure commit `f709b38`. No PR yet. Baseline compile passed. Current Activity service already enforces stable identity, ownership, authorization and referenced-record protection. Source reproduction finds maintenance labelled only Activities, no name-search control, no Event Accounting maintenance route, and row selection that can discard a dirty new form. D01 owner-installed-build/layout evidence remains a desktop reproduction acceptance prerequisite; source findings do not claim that evidence.

Design: retain Activity authority and existing route/state keys; expose Events / Activities, New Event, Edit and existing Active/Save lifecycle controls, case-insensitive name/code search, and a maintenance navigation action from Event Accounting. Preserve unsaved forms through filtering and asynchronous refresh. No schema or accounting-service policy change. P24-S2 Journal tagging is outside this slice.


### P24-S1 implementation and validation

Implemented Events / Activities navigation/title/help, New Event and explicit Edit focus, case-insensitive name/code search including inactive records, and the Event Accounting maintenance route. Existing Activity service, stable IDs, ownership, permission gates, lifecycle/delete rules and `activities.*` layout keys remain authoritative. Filtering and asynchronous loads retain unsaved drafts; selecting a different row and Refresh preserve drafts when discard is cancelled. Sorting remains live through search. The header/editor expose scrolling at narrow widths.

Baseline compile and eight focused tests passed. The modified focused Maven run passed ten tests with two display-dependent skips. Both new `EventDiscoveryPanelTest` cases then passed without skips using a temporary JavaFX Monocle headless backend and JUnit console: repeated names/distinct codes, name/code search, dirty new/edit cancellation, save, VIEWER gating, customized column order/width/sort, 900/600-pixel widths with enlarged text, table horizontal/vertical scrolling, editor overflow, divider movement, maintenance routing and refreshed Event Accounting choices. The test confirmation callback replaces modal automation only in tests; production retains the normal discard dialog. Initial modal automation hung; an initial teardown emitted late preference-save authorization errors. Both were corrected, and the final two-test run passed without those errors. No native desktop visual acceptance is claimed. Temporary tools/logs were lost in the subsequent runtime replacement; the observed results above are retained here. CI now includes these tests in its existing Xvfb step.

Donor review: `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180` supplies a read-only event list concept, but no better canonical maintenance authority; no donor persistence/UI framework was copied. Governing [P21/P24 contract](P21-activity-event-accounting.md#p24-s1--event-discovery-and-maintenance-navigation), [operation matrix](interface-operation-matrix.md) and [user testing](P24-S1-event-discovery-user-testing.md) describe the implemented workflow. No migration or accounting policy change.

Status: VERIFYING on `codex/P24-S1-event-discovery`; PR: none. Final local `mvn clean verify` passed on 2026-09-29 (America/Denver): 833 tests, zero failures/errors, 33 headless skips. The two new JavaFX tests separately passed on Monocle as recorded above. Remaining: publication authorization, exact published-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. P24-S2 is not started.


Final-gate prerequisite repair: the first full run reported 833 tests, one failure, zero errors and 33 headless skips. Existing `ActivePeriodContextTest` expected notification when setting 2026-09-30, which equaled this replacement runtime's current date; JavaFX correctly emits no change for the same value. The test now establishes a distinct starting date and removes its listener after asserting the change. This narrowly scoped, test-only repair is required to complete the slice's full gate; no period behavior changed. The fresh full verification passed: 833 tests, zero failures/errors and 33 headless skips. No known failing test remains.


Verified implementation commit: `a448bdac93731eff1d3c5af60c159605368ccbea`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the publication tip. Diff whitespace and local documentation links passed; the generated tracked manifest was restored. Worktree changes were reviewed and committed. No P24-S1 PR, published-head CI or owner desktop acceptance is claimed. Next exact action: obtain explicit publication authorization under AGENTS.md, publish this branch's reviewed sequence (including the P23 closeout), open a draft PR and inspect its final-head workflow.


### P24-S1 authorized publication — 2026-09-29 (America/Denver)

Owner explicitly authorized publication. Draft [PR #356](https://github.com/benbaron/sca-jakarta-h2/pull/356) targets unchanged main `74efca9bd67d4e47c5d54b07822691cf78e49034`. Connected-service publication preserved the reviewed sequence/messages and verified all local trees:

- Local `f709b38d8c58223244d20c8ae9cd75508c9c211b` → remote `f9072d5b2e3852c79ab98509c93654f156d43aae`, tree `acfe8040744270bae61baa723d67bc72eb4c9482`.
- Local `a448bdac93731eff1d3c5af60c159605368ccbea` → remote `8937ff50ad15568cf12926f01d8efa83ff33db8d`, tree `8ddba76222e98f91b5c609373033888ad1fb26fb`.
- Local `7d4a7bdb75fd52abae2b7242da01b249882d8d8d` → verified PR head `69107cc696a2e9b66846b4d914745d5885fd3872`, tree `7b2d996e406e1fb1314ef2d59a6f77865a5c95ad`.

Earlier publication requests are satisfied. This documentation-only record follows that content head. Published-head CI is pending at preparation; inspect the successor final-head Maven PR Tests run and record the outcome in the PR description. Local validation passed 833 tests with zero failures/errors and 33 headless skips; both new JavaFX tests separately passed. D01 installed-build/layout evidence, owner desktop acceptance and merge remain outstanding. P24-S1 remains VERIFYING; P24-S2 remains blocked.


## 20. P24-S1 closure and P24-S2 readiness — 2026-09-30 (America/Denver)

Owner explicitly confirmed acceptance and merge. GitHub verifies [PR #356](https://github.com/benbaron/sca-jakarta-h2/pull/356) merged at current main `ab580628740228812e32649d19e723d50e81b152`. Final head `671deec0bb38223ec9fafb38d0db543677569510` passed [Maven PR Tests run 36670303713](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36670303713), job `109743720948`: both full passes reported 833 tests with zero failures/errors and 33 headless skips; Xvfb reported 13 tests with zero failures/errors/skips, including both event-discovery cases. Owner acceptance supersedes the earlier pending-acceptance statements; no specific screenshots, display settings or D01 measurements are invented by this record.

P24-S1 is DONE. P24 remains IN_PROGRESS; P24-S2 is READY with no implementation branch or PR. S2 owns visible/searchable Journal Fund/Event choices, independent line assignments, saved-entry review and event-name filtering, with refresh that preserves drafts. No S2 implementation has begun.

This documentation-only closeout uses fresh branch `codex/P24-S1-closeout` from the verified merge; the merged implementation branch is not reused. Validation: merge and final-head CI verification, plan status review and `git diff --check`. No application code changed or additional application test run was needed. Next exact action when execution resumes: create a fresh P24-S2 implementation branch from current main, preserve this closeout record, and inspect the owning contract and current Journal workflow.


## 21. P24-S2 execution — 2026-09-30 (America/Denver)

Owner selected S2. Fresh branch `codex/P24-S2-journal-fund-event` starts from main `ab580628740228812e32649d19e723d50e81b152` and carries the S1 documentation closeout `f993eb3`. PR: none. Baseline compile passed. Source inspection finds the Event column far right, non-searchable option cells, absent event names in saved Journal rows, ID-only inactive event rendering, and reference choices loaded only on construction.

Design: retain canonical per-line Fund/Activity assignments. Add a visible selected-line tagging area with name/code search, independent Apply Fund / Apply Event and explicit Clear Event actions; no inferred transaction-wide event and no automatic overwrite of mixed allocations. Keep existing table column/state IDs and expose Event next to Fund by default. Enrich transaction line read projections with current event code/name, including inactive history. Add service-backed Fund/Event name/code filters before row limiting; when both filters are supplied, require one split to match both. Refresh reference choices on return or explicit request without losing drafts or changing dirty state. No migration or second event authority; generated inventory tags remain P24-S3.


Implementation: visible selected-line tagging controls with independent active name/code searches, multi-selection Apply/Clear, current event labels in saved review and inactive loaded assignments, same-split Fund/Event filters before the result limit, and draft-preserving active-choice refresh. Existing IDs and per-company table preferences are retained; remembered dividers size filters and tagging regions. Corrected combo initialization/value commits and dirty tracking for applied dimensions. Journal drill-through New/Edit now respects the existing discard confirmation.

Donor: inspected `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180`, especially the associated-fund selector refresh in `JournalEntryWorkspaceFX`. Reused the refresh intent, not its name-based transaction-level assignment or persistence. No additional donor feature is needed in this slice.

Documentation: updated [Journal editor guidelines](ui/editor-guidelines.md), [interface operation matrix](interface-operation-matrix.md), and added [P24-S2 user testing](P24-S2-user-testing.md). Workflow includes the production JavaFX tagging test in the existing Xvfb gate. No SCLX or schema change.

Validation completed locally on 2026-10-01 (America/Denver): `/tmp/apache-maven-3.9.9/bin/mvn --offline --settings .mvn/settings-github.xml clean verify` passed: **837 tests, zero failures/errors, 35 headless skips**. The retained reports from the prior run had the same totals; the gate was repeated after the runtime restart because its temporary log was lost. Focused service/review tests passed, including same-split filtering before row limiting, mixed IDs, inactive rename/reopen, reversal preservation, company scope and untagged review. Both new production JavaFX tests separately passed with temporary Monocle headless rendering on 2026-09-30: two executed, zero skipped or failed. They cover selected-line tagging, draft-preserving refresh, permissions, restored wide/reordered columns, narrow/scaled geometry, divider movement and the reproduced combo initialization/value-commit defect. No native desktop acceptance is claimed.

Remaining: explicit publication authorization under AGENTS §5 Step 6, exact published-head CI, D01 installed-build/layout evidence and owner acceptance/merge. No known local test failures. P24-S3 remains blocked by S2 completion. The branch includes the S1 closeout, S2 implementation and a subsequent documentation-only handoff; current content head is recorded in that handoff.

## 22. P24-S2 local handoff — 2026-10-01 (America/Denver)

Status: VERIFYING. Branch: `codex/P24-S2-journal-fund-event`. PR: none. Verified implementation head: `4f87aeb212911354e9fb533a5e4ae95c9cb2dcc4`; this documentation-only commit follows it. The branch carries `f993eb3` (accepted S1 closeout) and the S2 implementation. `origin/main` was rechecked at `ab580628740228812e32649d19e723d50e81b152`. Final local clean verification: 837 tests, zero failures/errors, 35 headless skips; the two new JavaFX tests separately passed under Monocle. Diff review and whitespace checks passed. Generated tracked manifest restored after Maven clean; no generated build output is part of the implementation commit.

Completed: canonical selected-line tagging, searchable choices, event labels in saved/reopened entries, same-split Fund/Event filters, draft-preserving refresh, combo commits, resizable/scrollable controls, regression tests, CI selection and user testing notes. Remaining: owner publication authorization, draft PR and exact-head CI, installed desktop validation and acceptance/merge. Next exact action upon publication authorization: verify the clean local branch and remote state, publish the reviewed commit sequence without force-updating, create the P24-S2 draft PR with actual validation and desktop checks, then inspect its final-head Maven PR Tests result. Do not start P24-S3 before S2 is accepted and merged.

## 23. P24-S2 authorized publication — 2026-10-01 (America/Denver)

Owner explicitly authorized publication. Draft [PR #357](https://github.com/benbaron/sca-jakarta-h2/pull/357) targets unchanged main `ab580628740228812e32649d19e723d50e81b152`. Local Git lacked credentials, so connected GitHub publication preserved the reviewed sequence/messages and verified each tree:

- Local `f993eb345b8849675ce4303d95529e9f1560333f` → remote `9153340ae0981e85b9f12412b32ea35de620c3f9`; verified tree `d0a4834fed30fdddc8b0e8fbd279a26212a721a7`.
- Local `4f87aeb212911354e9fb533a5e4ae95c9cb2dcc4` → remote `bf0637cb424c602038f5a2f068b496eabd898c69`; verified tree `9b087601b7fc57269430507826ffb85f4baf5ad9`.
- Local `0b0fd6600c036dbbe0a768be680fb85bf1cdec41` → remote `8a5964065f72fa9feff0aedb62933be3eab673f4`; verified tree `30584fb26d35077ab02e2afd50c4472d40986d18`.

All earlier publication-authorization requests are satisfied. This documentation-only record follows the verified content head above. Final-head CI is pending at preparation; inspect its Maven PR Tests workflow and record the result in the PR description. Local clean verification passed 837 tests, zero failures/errors and 35 headless skips; both new JavaFX tests separately passed. Remaining: exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. S2 remains VERIFYING; S3 remains blocked.

Publication follow-up: remote documentation head `1fd7944715361551c763f18e66d2d82bc9e43524` was fetched and its tree `b1877e25f4af379bbc8096ac26a5025a7582946e` matched local `c5e438a2491479a7651f011b772465ae8b93f652`. [Run 36950856982](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36950856982), job `110663322715`, entered clean headless verification. This documentation-only follow-up synchronizes two stale current-status summaries; inspect the successor final-head run and record its outcome in PR #357.

## 24. P24-S2 closure and P24-S3 execution — 2026-10-02 (America/Denver)

Owner verified and merged S2. GitHub confirms PR #357 merged at current main `a7a7ca6964884cbadf27af8b8891d68520e939c4`. Final head `3ceef546ec38abcaf75256242e2779d5beebfa34` passed run `36951048419`: both full passes 837 tests, zero failures/errors, 35 skips; Xvfb 15 tests, zero failures/errors/skips. Prior pending S2 acceptance/merge records are historical. S2 is DONE.

S3 starts on fresh `codex/P24-S3-inventory-event-costs` from that main; no PR. Baseline compile and eight focused inventory/accounting/authorization/interchange tests passed. Required inspection: InventoryMovementCommand, InventoryService previews/writes/reversals/import seams, InventoryPanel, TxnSplit, Event Accounting/Budget actuals, inventory SCLX snapshot/import and existing tests. G6/A06 owns this slice; D06 cost-method policy remains a P26-S5 gate and does not change the existing fixed unit value here.

Design: Event and optional Budget Category apply only to the generated offset split, whose account determines accounting recognition; the inventory split remains untagged. Both lines retain the item's Fund. Financial commands require an Event or explicit Non-event confirmation; nonfinancial zero-value movements reject accounting tags. Active/company/date eligibility is checked at preview and again under the existing commit lock. History reads current labels from canonical linked splits. SCLX retains existing Event/Fund references and movement-to-transaction provenance; the round-trip regression exposed missing Budget split attribution, repaired with the bounded transactionBudgets version 1 extension documented in the inventory contract. No duplicate movement dimension columns or database migration is introduced. Reversal copies canonical dimensions. No inference from a sale, memo or item default. Existing value/correction/closed-period/authorization policies remain in force.


### P24-S3 implementation and validation

Completed locally: canonical command/preview/offset-split attribution, explicit Non-event choice, active/company/effective-date checks, current-label movement history, governed reversal, draft-preserving Event/Budget choice refresh, confirmation debit/credit and tag labels, scrollable/resizable controls, and CI JavaFX selection. See [inventory contract](inventory/inventory-and-assets.md), [interchange contract](data-exchange/inventory-sclx.md), [interface matrix](interface-operation-matrix.md), and [P24-S3 desktop checks](P24-S3-user-testing.md).

Direct prerequisite repair: the generated-cost round-trip test proved that SCLX omitted Budget split links. A bounded transactionBudgets version 1 extension now carries line ID/category code, restores company-owned categories with the existing code-as-initial-name policy, includes attribution in transaction/line conflict hashes, and rejects invalid relationships before writes. Old files remain valid; older readers cannot commit a populated unsupported extension. Inventory extension/schema are unchanged. No category master-data maintenance or costing-policy expansion is included.

Donor inspection: NonprofitAccounting `c697630ec1f784ebe8338d7300da6c9ac801b180`, InventoryService lines 30–92 uses static-map/JSON storage. That authority is unsuitable here; existing H2/JPA commands, canonical TxnSplit dimensions and transaction-correction services were extended instead. No donor import or independent future feature was needed.

Focused accounting/authorization/interchange validation: 19 tests passed with zero failures/errors/skips. The A06 case proves $500 income, $200 expense, $300 net, Budget actuals, quantity, reversal, restart, tag-preserving SCLX import and idempotence; foreign/inactive/out-of-date choices and injected late-write failures leave no partial writes. Invalid extension relationships and tag-change conflict identity have regression coverage. Final full verification and final JavaFX geometry result are recorded in the handoff below.


### P24-S3 local verification — 2026-10-02 (America/Denver)

Final `mvn --offline --settings .mvn/settings-github.xml -Duser.home=/tmp/p24s3-test-home clean verify` passed: 843 tests, zero failures/errors, 36 headless skips. The initial full run had three existing UI-test cleanup errors because the replacement container's `/root` home is read-only; rerunning with an isolated writable Java home resolved them without changing application code. Final new JavaFX test separately passed under temporary Monocle 17.0.10/JUnit Console 1.10.2 (one test, zero failures/skips), including Event/Non-event exclusivity, optional Budget, draft-preserving refresh, inactive-choice removal, viewer write restrictions, 900/600-width and larger-text controls, horizontal/vertical history scrolling and divider movement. Native desktop visual acceptance is not claimed.

Final diff review, whitespace check, and new documentation links passed. Generated tracked manifest restored after Maven clean. Current `origin/main` was rechecked at `a7a7ca6964884cbadf27af8b8891d68520e939c4`; the owner's renewed merge confirmation refers to the S2 merge present there. S3 remains local, VERIFYING, with no PR or published-head CI. Remaining: explicit publication authorization under AGENTS §5 Step 6, draft PR and exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. P24-S4 is not started.


### P24-S3 commit handoff

Branch: `codex/P24-S3-inventory-event-costs`. PR: none. Verified implementation head: `c210c742e49eb3a8683a8ede7c737a0169f5b8fa`; this documentation-only handoff follows it, and `git rev-parse HEAD` identifies the publication tip. All implementation, regression tests, CI test selection, governing documentation and desktop notes are committed. No known local test failure remains.

Next exact action after owner publication authorization: confirm the clean local branch and expected remote parent, publish both reviewed commits without force-updating (matching tree SHAs if using connected GitHub transport), open a P24-S3 draft PR with the SCLX compatibility detail and actual validation above, inspect its final-head Maven PR Tests workflow, and complete the desktop checks in `doc/P24-S3-user-testing.md`. Do not begin P24-S4 until S3 is accepted and merged.


### P24-S3 authorized publication — 2026-10-02 (America/Denver)

Owner explicitly authorized publication. Draft [PR #358](https://github.com/benbaron/sca-jakarta-h2/pull/358) is open from `codex/P24-S3-inventory-event-costs` to main. Connected GitHub publication reproduced both reviewed commits with matching trees:

- Local `c210c742e49eb3a8683a8ede7c737a0169f5b8fa` → remote `e9bb37e34b73ba129e31ef6ff02bfc89fcb46776`, tree `0b4e5f6c2f7e8137789c68bebfadd4a26ae26f3a`.
- Local `90a9a80bf14416a943a172eec4ce1813665d9918` → remote `e1631b1e4bb360ec3236e834ea41f2fc88209221`, tree `ca3500ba0aa6c25c313df56b6d203812ceeae8c3`.

Remote main remained `a7a7ca6964884cbadf27af8b8891d68520e939c4`; the new branch was created without force updates. Fetched published head/tree match the expected values. [Initial run 37080636631](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37080636631) entered Maven PR Tests. This documentation-only publication record follows the content head above. Inspect the successor final-head run and record its outcome in the PR description. The publication-authorization requirement is satisfied; earlier requests are historical. Remaining: exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. S3 remains VERIFYING; S4 is not started.


## 25. P24-S3 accepted merge and P24-S4 readiness — 2026-10-02 (America/Denver)

Owner accepted and merged PR #358. GitHub confirms merge `e5570f2d413df853a684f93db8a59616dad53321`, fetched as current main. Final PR head `1afd706d5b3600337bf828162c6a8d93c14f6ceb` has tree `b1a3f1c40baac755db8505b92181dd979c585044`, matching local publication-record commit `07574fef97dc7f7360c48e92796ffe2dd08db4a5`. [Maven PR Tests run 37080733410](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37080733410), job `111080791303`, completed successfully: both full passes 843 tests, zero failures/errors, 36 headless skips; Xvfb production JavaFX pass 16 tests, zero failures/errors/skips. Logs were inspected and PR #358 updated with the actual results. Initial superseded run 37080636631 was cancelled. Earlier pending publication, CI and acceptance records are historical.

P24-S3 is DONE. P24-S4 lookup maintenance is the first unblocked slice and is READY, not started. This documentation-only closeout is committed locally on fresh `codex/P24-S3-closeout` from the merge, without reusing the merged implementation branch. No closeout PR is open. On the next implementation run, carry this closeout ledger forward, read the P24-S4 contract and relevant authorities, and select one master-data family before implementation. No known local or GitHub test failure remains; owner acceptance is recorded without inventing additional desktop evidence.


## 26. P24-S4A Budget Category maintenance — 2026-10-02 (America/Denver)

Selected the category family under the adopted P24-S4 contract; Payee/Counterparty and Merchant are separate S4B/S4C slices. Fresh branch `codex/P24-S4A-budget-category-maintenance` starts at current main `e5570f2d413df853a684f93db8a59616dad53321` and carries S3 closeout `80bb126`. No PR. Baseline compile and three existing BudgetCategory service/authorization tests passed.

Inspected: BudgetCategory/alias model, V45/V50/V61 relationships, BudgetCategoryAdminService/LookupService, authorization wiring, canonical Budget and Journal consumers, Activity maintenance conventions, panel/navigation/command routing and UI rules. Existing code-keyed upsert cannot serve a stable-ID editor and there is no category maintenance route. Add audited stable-ID create/edit with company lock, case-insensitive duplicate detection, date validation and existing write authorization. Retain categories rather than hard-delete: UI explains deactivation preserves Journal, Budget, alias and interchange history. Existing import/upsert compatibility seams remain available; no schema or interchange-policy change. Journal and Inventory gain maintenance navigation and preserve drafts on choice refresh. Existing date fields are maintained, with optional bounds and required from <= to.

Donor `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180`: BudgetEditorPanel uses editable string account/fund rows and BudgetWorkspaceStore, with no suitable category master editor. Retain its explicit refresh intent but use existing H2 category IDs, production table/divider state and service authorization; no donor persistence import. See [Budget model](accounting/budget-model.md) for maintenance policy.

### P24-S4A implementation and local verification

Implemented on `codex/P24-S4A-budget-category-maintenance`:

- added immutable `BudgetCategoryCommand`/`BudgetCategoryView` projections and a stable-ID `BudgetCategoryAdminService.save(...)` path with company locking, ownership checks, case-insensitive duplicate-code validation, effective-date validation, and same-transaction create/update audit events;
- added the production `BudgetCategoriesPanel` with searchable active/inactive table, New/Edit/Save/Refresh behavior, company date formatting, draft-preserving refresh, deactivation guidance instead of physical Delete, write-permission gating, and Journal/Inventory return navigation;
- routed the panel through `AppPanelId`, `PanelFactory`, Planning navigation, Journal and Inventory maintenance actions, and the production command-capability contract; the existing compatibility `upsert(...)` and SCLX import seam remain unchanged;
- added service authorization/audit regression coverage, a JavaFX panel behavior/layout test, and the panel to the existing CI Xvfb selection; updated the budget model, interface matrix and manual acceptance checklist.

Validation completed locally: all production Java sources and all test sources compile with the cached Java 17 compiler (`--release 17`, no annotation processing); the focused `BudgetCategoryAuthorizationIntegrationTest` passed 2/2 tests; `git diff --check` passed. The focused JavaFX capability test had 3 successful tests and 3 display-dependent aborts; the new `BudgetCategoriesPanelTest` was display-dependent and aborted because this runtime has no `DISPLAY`/Xvfb. Maven is not installed in this runtime, so `mvn clean verify` and the full CI test gate remain pending; no native installed-build visual acceptance is claimed.

Current handoff: implementation is locally ready but not published. Implementation commit: `0458d48cef80cdc686087237cf932f4c5e906d78` (`feat(P24-S4A): add budget category maintenance`). Remaining gates are explicit owner publication authorization, draft PR/final-head GitHub checks, and owner desktop acceptance. Do not mark S4A or P24-S4 DONE until the change is merged into current `main` and those gates are satisfied.

### P24-S4A authorized publication — 2026-10-03 (America/Denver)

Owner explicitly authorized publication. Draft [PR #359](https://github.com/benbaron/sca-jakarta-h2/pull/359) is open from `codex/P24-S4A-budget-category-maintenance` to `main`. Connected GitHub publication reproduced the reviewed sequence with matching trees:

- Local `80bb1261726a55cc14304614377260ba90c2c5e2` → remote `9879d55683c9848a75f284f43cff9ee30f14ca19`, tree `f2ba36834c5a6fd045af4ae7a50143fc1321a0e7`.
- Local `0458d48cef80cdc686087237cf932f4c5e906d78` → remote `14af1c2e173a8c425b550e3683ff5e1602f23db4`, tree `1b28c900e5aa10d1682296720c8cc88cff30d4c5`.
- Local `abe69455bf4882a74542bf9c71d3664b38fe23b1` → remote `1b32e410aa83cd80f6f1527126782f98ac9dbaa2`, tree `9b252c80d4d20a1628c3964140bf80d6b431a044`.

Remote main remained `e5570f2d413df853a684f93db8a59616dad53321`; the branch was created without force updates. The draft PR remains open and unmerged. Inspect its successor final-head CI and complete the owner desktop acceptance in `doc/P24-S4A-user-testing.md`. Do not mark S4A or P24-S4 DONE until the change is merged into current `main` and those gates are satisfied.


## 27. P24-S4A merge reconciliation and P24-S4B selection — 2026-10-06 (America/Denver)

Owner selected S4B with “ok. proceed” after the repository rescan. PR #359 was merged by the owner on 2026-10-02 (America/Denver) at `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`, now current main. Final-head Maven PR Tests run [37092325277](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37092325277) completed successfully for `3d72b08e7e15e798d2d103055801c5becc44109a`. No PR remains open. The previous VERIFYING controller was stale. Owner merge and subsequent selection are the acceptance/advancement evidence; no additional desktop evidence is invented. S4A is DONE; S4B begins on fresh `codex/P24-S4B-counterparty-maintenance` from that main. S4C stays BLOCKED.

Inspected Counterparty mapping/kind/portable identity, company ownership, TransactionEntryService and reference-data service, Journal refresh, SCLX parties, Budget Category service/panel conventions, routing, authorization, and UI rules. Baseline production compilation passed with Java 17 using cached dependencies. Maven executable is unavailable; full Maven verification is not yet claimed.

Design: keep Counterparty as the sole Payee authority. Add company-scoped immutable maintenance projections and audited stable-ID create/edit, company locking and case-insensitive name duplicate checks, contact field bounds, active/inactive retention, production maintenance navigation and draft-preserving Journal choice refresh. Existing SCLX identities and import behavior stay authoritative. No schema or ledger change. Retain all parties instead of hard deletion, with a visible deactivation/history explanation. Merchant remains S4C. Governing maintenance contract: [Counterparty maintenance](accounting/counterparty-maintenance.md).

Donor review: `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180` uses `CounterpartySyncAdapter` to merge/delete parties by name/kind from legacy Person/Donor records. That creates a second authority and can delete history, so it is not imported. Production Counterparty stable/portable IDs and company ownership remain the sole authority. UI refresh intent follows the established production Journal mechanism.


### P24-S4B implementation and verification handoff

Implemented audited company-scoped CounterpartyAdminService, immutable command/view, name/contact/kind validation and serialized duplicate checks. Added Payees / Counterparties maintenance with search/sort, New/Edit Selected/Save/Refresh, contact/lifecycle fields, discard protection, visible history-retention explanation, resizable scrolling regions, company table/divider state, tooltips and permission gates. Wired Accounting navigation, PanelFactory/global commands, UiServiceRegistry and Journal maintenance/return refresh. No migration or interchange source changes. Added service, concurrent duplicate, identity/restart, authorization/ownership, GUI draft/geometry and Journal choice-refresh regressions; both new GUI cases are in the CI Xvfb gate.

Actual verification:

- Current main baseline production compilation passed with Java 17; final production and test-source compilation also passed using cached dependencies and the installed Java compiler module. Existing ReportLibrary varargs warnings remain unchanged.
- JUnit launcher focused verification: **35 started, 30 successful, zero failures, five display-dependent aborts**. Includes CounterpartyAdminServiceTest (four successful), TransactionEntryServiceTest, SclxLifecycleRoundTripTest, SclxPortableIdentityTest and command capability tests. Three capability GUI tests and both CounterpartiesPanelTest cases require a display and were not executed here. No desktop or Xvfb pass is claimed.
- First new ownership assertion expected IllegalArgumentException; the established ownership guard returns CompanyOwnershipException. Corrected the assertion to the existing contract; subsequent tests passed. No production ownership behavior was changed.
- `mvn clean verify` exited 127 because Maven is unavailable. Attempt to obtain Maven from Maven Central timed out under this runtime's network restrictions. Full Maven, exact published-head GitHub CI and native owner acceptance remain pending.
- Final diff review, documentation-link validation and `git diff --check` passed. Archived proposal unchanged.

Status: VERIFYING, local branch `codex/P24-S4B-counterparty-maintenance`, based on main `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`; no PR or GitHub S4B result. User testing: [P24-S4B](P24-S4B-user-testing.md). Next: after explicit publication authorization under AGENTS §5 Step 6, confirm local/remote state, publish the reviewed sequence without force, create a draft PR, inspect exact-head Maven/Xvfb checks and fix failures. Owner desktop acceptance and merge remain required. S4C remains BLOCKED.

Reviewed implementation commit: `5dc0b20a501292ad5542bc6a05045774c3de2c94`. This documentation-only successor records its head for publication; `git rev-parse HEAD` identifies the final local tip. Both commits must be published in order with matching trees when using connected-service transport.


### P24-S4B authorized publication — 2026-10-06 (America/Denver)

Owner explicitly authorized publication. Draft [PR #360](https://github.com/benbaron/sca-jakarta-h2/pull/360) is open from `codex/P24-S4B-counterparty-maintenance` to main. Current main remained `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`. Connected publication preserved the reviewed commit messages, order and exact trees:

- Local `5dc0b20a501292ad5542bc6a05045774c3de2c94` → remote `8a1aeb9d5887b1167ee04e5b50bb6ee8e3b1c8a5`; tree `22653cf3255637e4a9c8ba52d0c8e889c20e00f1`.
- Local `03fd833bbda8b6241855be53fe8e4b0b65372211` → remote `48ddd15910866304f700caa10181fe3786d556e5`; tree `20862bcb69519bc7e500b6d80aa3587b4062627c`.

The new branch was created at the verified publication head, without force updates. This documentation-only successor records PR/authorization; it requires final-head CI. At publication, CI and both display-dependent CounterpartiesPanelTest cases remain pending. Inspect Maven PR Tests for the final PR head, repair failures, and record the exact workflow evidence in the PR. Owner desktop acceptance using P24-S4B-user-testing.md remains required. S4B is VERIFYING, unmerged; S4C remains BLOCKED. No S4B GitHub pass or native desktop result is claimed by this snapshot.


### P24-S4B final-head GitHub verification

Final published head `8c4b7b6c2b98d0024b48afb426456fb4289d6852` passed [Maven PR Tests run 37560761479](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37560761479), job `112597449168`. Both full passes: 851 tests, zero failures/errors, 39 headless skips. Production Xvfb gate: 19 tests, zero failures/errors/skips, including both CounterpartiesPanelTest cases. Clean verify, repeat tests, production JavaFX route compliance and cleanup all succeeded. Source publication ledger `a04daad5982a3f209b6e09756cb913a23fbf7ec8` maps to remote final head with identical tree `1e5393072bc3823050a5f90bb20c642a0d8f5806`.

PR #360 records the final result and owner testing link. This post-CI documentation handoff is local only, deliberately not published to avoid replacing the verified PR head with an unverified successor. No implementation change followed the successful CI head. Status remains VERIFYING; owner native desktop acceptance and separate merge authorization remain required. S4C stays BLOCKED. Preserve this closeout evidence when continuing from the merged PR or publishing a later documentation successor.


### P24-S4B owner merge and P24-S4C readiness — 2026-10-06 (America/Denver)

While final CI evidence was being recorded, owner `benbaron` merged PR #360 at `e60c8211094548f36a5df1d4ee76db74c9c9d580` (2026-10-07 02:19:43 UTC / 2026-10-06 20:19:43 America/Denver). Current origin/main was fetched and confirmed at that merge. Final PR-head CI passed as recorded above. Owner merge is the acceptance/advancement evidence; no separate desktop evidence is invented. S4B is DONE. S4C Merchant maintenance is the first unblocked successor, READY and not started; P24-S4 remains IN_PROGRESS.

This documentation-only closeout is local on fresh `codex/P24-S4B-closeout` based on the confirmed merge, with the local final-CI evidence cherry-picked as `ac2accb`. The merged implementation branch is not reused. No closeout PR or S4C implementation exists. Next: when the owner selects S4C, start a fresh branch from current main, carry this closeout ledger forward, inspect Merchant authority and consumers, and implement that one family. Diff whitespace checks passed; no application changes or repeated application tests were required for closeout.


## 28. P24-S4C Merchant maintenance — 2026-10-06 (America/Denver)

Owner selected S4C with “proceed.” Current main is `e60c8211094548f36a5df1d4ee76db74c9c9d580`, the confirmed owner merge of PR #360 after final-head CI passed. Fresh branch `codex/P24-S4C-merchant-maintenance` carries the local S4B final-CI/closeout records as `850d0b5` and `11dd7e6`. No PR or Merchant implementation existed at selection.

Inspected Merchant, TxnSplit, V1/V61/V63 migrations, ownership/reference-data/transaction/correction consumers, SCLX parties, Counterparty maintenance service/UI/tests, factory/navigation/global commands, adopted G8/A08 contract and UI rules. Merchant name is company-unique in the existing schema, portable identity is durable, and no merchant maintenance service or route exists. Baseline production compilation passed using Java 17 and cached dependencies; Maven remains unavailable.

Design: add an audited stable-ID MerchantAdminService and immutable command/view using the existing Merchant master. Company lock serializes case-insensitive trimmed-name duplicate checks; authorization/ownership and the existing database unique constraint remain enforced. Name and notes plus active/inactive lifecycle are the entire mapped family; no unsupported contact or settlement fields. Retain records instead of physical deletion with a visible history explanation. New/Edit/Save/Refresh/search maintenance uses existing production layout/state conventions. Journal navigation refreshes active choices without discarding draft lines, including mixed merchants. No migration, alternate master, or SCLX change; settlement assistance stays P26-S6. Governing contract: [Merchant maintenance](accounting/merchant-maintenance.md).

Donor review at `NonprofitAccounting` `c697630ec1f784ebe8338d7300da6c9ac801b180`: legacy outstanding/undeposited records use free-text `from_to_card_merchant`; no suitable company-owned Merchant master editor was found in the inspected persistence/UI paths. Do not port name-based persistence. Reuse the established production stable-ID maintenance/refresh approach instead.

Narrow S4C prerequisite repair: Journal previously projected Merchant ID without its name when reopening a transaction, making inactive historical assignments blank. TransactionView now carries the current Merchant name; reference refresh carries company-owned retained Merchant labels separately from active choices. Resolve existing selections by ID against those labels, including a rename and deactivation in one save, without offering inactive merchants for new selection or changing mixed line assignments. Existing constructors remain compatible. Regression coverage belongs to this slice.


### P24-S4C local implementation and verification handoff

Implemented company-owned MerchantAdminService, immutable command/view, atomic authenticated creation/update audit, bounded trimmed-name validation, duplicate-name checks including inactive records, exact-ID ownership checks and company-serialized concurrent creation. The existing schema and portable identities are retained. MerchantsPanel provides New/Edit/Save/Refresh, name/notes search, active lifecycle, draft protection, permissions, table-state binding, remembered dividers, overflow scrolling and hover tooltips. Accounting navigation, production factory/service bundle, global commands and Journal maintenance/return refresh are wired. No later settlement work is included.

The directly blocking historical-label defect is repaired narrowly: TransactionView.Line carries Merchant name, and Journal reference refresh resolves current retained labels by stable ID separately from active new-choice lists. Mixed line assignments and draft dirty state remain intact. Existing projection constructors are compatible. Service regression covers restart, mixed canonical balanced lines, inactive names, reversal, and portable SCLX master/line-link round trip. No SCLX format/policy change or migration. Five Merchant service tests and two JavaFX tests are added; both JavaFX cases are included in the CI Xvfb gate.

Actual local validation: Java 17 compilation of all production and test sources passed using cached dependency jars (two pre-existing ReportLibraryPanel varargs warnings). Initial focused run: 38 tests found, 33 passed, five aborted for unavailable DISPLAY, zero failures. Broader ownership/correction/authorization/SCLX/source-policy regression run: 78 found, 75 passed, three display-dependent tests aborted, zero failures. These runs overlap and are not unique-test totals. The fifth Merchant service test deliberately rejects the audit insert after Merchant flush and proves the entire update rolls back. After final naming/comment and UI stress-assertion refinements, all sources compiled again. Native JavaFX assertions remain unexecuted here. `mvn clean verify` was attempted and exited 127 because Maven is not installed. This is not a Maven or desktop visual pass. No known application failure from executed checks; full Maven, Xvfb and owner desktop acceptance are outstanding. Final diff whitespace check passed.

Documentation: [Merchant maintenance](accounting/merchant-maintenance.md), [owner acceptance](P24-S4C-user-testing.md), interface-operation matrix and editor guidelines updated alongside this plan. Branch `codex/P24-S4C-merchant-maintenance`; no PR or remote publication yet. S4C remains VERIFYING; P25-S1 remains BLOCKED until P24-S4 is accepted and merged. Next exact action: after explicit owner publication authorization under AGENTS.md, reproduce the reviewed local commit sequence through authenticated Git/GitHub transport, confirm matching tree hashes and remote/PR head, create a draft PR, run/inspect final-head Maven PR Tests including MerchantsPanelTest under Xvfb, correct any failure, and update the PR/plan with actual CI evidence. Owner desktop acceptance and separate merge authorization follow.

Reviewed implementation/source head `8c5b5a36b163f5946def133b1b4a52cbe5eaee90`, tree `7015e61f39939f8429d8c857cc66f324e532290e`. The following documentation-only handoff commit records this source head; resolve `git rev-parse HEAD` for the publication tip. There have been no application changes after this verified source head.


### P24-S4C authorized draft PR publication — 2026-10-07

Owner explicitly authorized all three requested actions: publish branch, open draft PR and verify GitHub CI. Current remote main remains `e60c8211094548f36a5df1d4ee76db74c9c9d580`; the fresh S4C branch was absent and the local worktree was clean before publication. Published the reviewed sequence without force-updating, with every tree matching its corresponding local commit. Draft [PR #361](https://github.com/benbaron/sca-jakarta-h2/pull/361) targets main; verified initial branch and PR head `849bcf95a3af399e51d13126baf5332d3a18fa7f`.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `850d0b54985bf8e5074042975f282fd2ac2a760e` | `16082457f058a2e215f7dae86dd7ef079e15fff4` | `9f33a7ac2352e96cbe496d0711483244356f35f8` |
| `11dd7e6eb75df7061d4b92eae422c40e3a3ce4bf` | `855d1f6440631662d95c7a204c0e2835be8a12cc` | `4aa540d3bcd4b80b454dfcf31fd5941ea1ebe6d7` |
| `8c5b5a36b163f5946def133b1b4a52cbe5eaee90` | `b9737623430b7a084bcc86ad81573735d5e3dc9d` | `7015e61f39939f8429d8c857cc66f324e532290e` |
| `5c0f3497e18411fb8b14d08ead7a5e9e90561f3a` | `849bcf95a3af399e51d13126baf5332d3a18fa7f` | `994771c8cf9c725f41bfd467ca63eb954ed34c49` |

This documentation-only publication ledger follows the reviewed implementation and will be fast-forwarded under the same authorization; active_head records its known parent because a commit cannot embed its own hash. Verify the resulting branch/PR tip and run CI against that final published head. Full Maven, Xvfb and owner desktop results are still pending at this checkpoint; S4C is VERIFYING, not DONE. After final CI, record evidence locally and in the PR body without publishing another evidence-only successor that would replace the verified head. No application changes were introduced by this publication ledger.


### P24-S4C first final-head CI and narrow corrections

Published publication-ledger head `a4386001b443e61446959c19f414309f9da704fc` (local `b170f02b770738b41fc16436aa5458f88dd5f978`, matching tree `d6f8ec05a555a1ff60a62280fd234c2aea886eaf`) was confirmed on branch and draft PR #361. [Run 37628321930](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37628321930), job `112815990896`, passed both full Maven runs: 858 tests each, zero failures/errors, 41 headless skips. Xvfb ran 21 tests with two errors, both MerchantsPanelTest assertions. No final CI success is claimed.

The Merchant-only assignment regression exposed a missing Merchant property dirty listener in Journal; add that narrow listener beside the existing Fund/Event listeners so assignment changes are protected as unsaved drafts. The refresh callback already suppresses loading changes and restores the previous dirty state. The overflow stress test also used a fixed 850-pixel minimum immediately after requesting a narrower Stage, before the window resize/layout pulse had completed. Size the stress content relative to the actual viewport and await real width/height overflow plus both visible scrollbars before asserting; preserve all geometry requirements rather than weakening them. Explicitly assert Merchant-only dirty state before other draft changes. These two corrections are within the authorized S4C publication/verification scope. Local Maven and DISPLAY remain unavailable. The dependency cache was subsequently rediscovered (145 cached jars); all corrected production and test sources compiled locally under Java 17, with the same two pre-existing ReportLibraryPanel warnings. Native display validation runs in CI. Whitespace checks passed; final-head CI and owner desktop acceptance remain pending.


### P24-S4C successful final-head CI — 2026-10-07

Final published correction head `6ebaea8ec851ff3bed65dbbcd07851873d174bec` passed [Maven PR Tests run 37629410014](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37629410014), job `112819446148`. Both full runs: **858 tests, zero failures/errors, 41 headless skips**. Production JavaFX/Xvfb gate: **21 tests, zero failures/errors/skips**, including both MerchantsPanelTest cases. All five Merchant service cases passed in both full runs. Clean verify, repeat tests, display compliance and job cleanup succeeded. Run head, branch ref and PR #361 head were confirmed identical after CI; draft PR is open, mergeable and unmerged. No application failure remains from executed gates. Native owner desktop testing is still required; Xvfb is not owner acceptance.

Correction source commit `106e890c6876dae2ec4fc911429af2e9f6af7ba4` maps to final published head with matching tree `a327f9f8fa15bc31519f8aafc709ed0fc7a24941`. Together with the four original commits and publication ledger recorded above, all six published commits retain reviewed messages/order and matching trees, with only expected-head fast-forward ref updates. PR description records actual final-head results and the initial failure/correction rationale.

This post-CI evidence commit is **local only**, deliberately leaving the successful published head unchanged. No application change follows the verified source head. The worktree is clean after recording this handoff; `git rev-parse HEAD` identifies the documentation tip. Carry this evidence forward from confirmed main after owner merge. S4C remains VERIFYING, P24-S4 remains IN_PROGRESS, and P25-S1 stays BLOCKED. Next exact action: owner follows [acceptance notes](P24-S4C-user-testing.md), then separately authorizes/merges PR #361. On reported merge, fetch current main, verify PR merge and final CI head, close S4C/P24-S4, and select the first unblocked dependent slice. Do not merge or advance without that evidence.


## 29. P24 completion and P25-S1 readiness — 2026-10-07 (America/Denver)

Owner reported “tested and merged.” GitHub independently confirms owner `benbaron` merged PR #361 at `a9275ad1b9a970a084829d5659d792375ed196fb` on 2026-10-07 23:34:25 UTC / 17:34:25 America/Denver. Fetched origin/main and confirmed that merge as current main. PR final head `6ebaea8ec851ff3bed65dbbcd07851873d174bec` matches successful Maven PR Tests run `37629410014`: both full runs 858 tests, zero failures/errors, 41 headless skips; Xvfb 21 tests, zero failures/errors/skips. Owner testing statement supplies desktop acceptance; no additional device/scaling evidence is invented.

P24-S4C and the Budget Category/Payee/Merchant P24-S4 family are DONE. All P24 slices are accepted and merged, so P24 is DONE. Clear obsolete active branch/PR/head fields and select P25-S1 as READY, not started. Its adopted contract is [internal fund transfers](P23-P28-runbook-correction-program.md#p25-s1--implement-one-internal-fund-transfer-operation-g7): one atomic operation linking canonical balanced accounting and existing FundTransfer reporting, with restriction, closed-period, duplicate/failure and reversal protections. A07 distinguishes internal reallocation, bank movement and inter-entity payment. D03 remains unresolved before legal-entity mappings; readiness for internal transfers does not resolve that policy or authorize external mapping implementation. P25-S2 and later slices stay BLOCKED.

This documentation-only closeout is local on fresh `codex/P24-closeout`, based on the confirmed merge; the merged implementation branch is not reused. Preserved the local final-CI evidence commit `76f8c2d` by cherry-picking it as `165446e`. No closeout PR or P25 implementation branch exists. Validation: current-main ancestry, PR merge/final-head CI confirmation, scoped governing/acceptance dependency review and diff whitespace checks; no application changes or repeated application tests. Next exact action when the owner resumes execution: fetch current main, create a focused P25-S1 branch, carry this closeout forward, read the adopted G7/A07 and D03 contracts, inspect FundTransfer/FundAdminService/FundsPanel, canonical transaction/correction/ownership/authorization services, bank-transfer authority, semantic transfer report and relevant migrations/tests, then establish baseline before design.


## 30. P25-S1 internal fund transfers — selected 2026-10-07

Owner selected P25-S1 with “Ok, proceed.” Fetched and confirmed main `a9275ad1b9a970a084829d5659d792375ed196fb`. Fresh `codex/P25-S1-internal-fund-transfers` carries P24 evidence/closeout as `82ce8a4` and `8cc7a71`. Inspected the existing FundTransfer master and report, Fund maintenance/ownership, canonical entry/correction caller-owned transactions, period/reconciliation protections, SCLX coverage, V1 and current migration history, G7/A07/D03 and production UI rules. Existing FundBalanceService sums natural-signed amounts of all account types without company scope; it is not a sound net-assets availability authority and will not be used for transfer policy.

Design and governing contract: [Internal fund transfers](funds/internal-fund-transfers.md). One atomic service operation creates existing FundTransfer and canonical Txn facts, with audit and UUID request idempotency. Four lines reallocate a selected ordinary non-bank ASSET allocation account and EQUITY net-assets account between funds; bank, income and expense company totals remain unchanged and each fund's balance sheet stays balanced. Accounts are explicitly selected from current active chart; no automatic accounting data. Both funds and their parent hierarchy must be active/effective, unrestricted or designated and free of restriction text. Ledger-defined source net assets must cover the amount. D03 remains a gate on external/legal-entity mappings, which are excluded.

Generic Journal edits/deletes of linked transfers must fail with a transfer-workflow explanation; reversal preserves original operational facts and creates the opposite-direction dated transfer fact atomically, including generic Journal reversals. No replacement shortcut may produce an unlinked transfer. New migration adds nullable request identity/hash for legacy compatibility, unique new request identity, and indexes; existing FundTransfer status remains storage vocabulary, not a new visible posting workflow. SCLX already omits FundTransfer facts: this slice must verify portable canonical line/reversal effects and visibly disclose the existing operational-link portability limit; full operational-field coverage remains P27-S4.

Donor reviewed at `c697630ec1f784ebe8338d7300da6c9ac801b180`: FundTransferPostingService supplies four-line source/destination accounting but uses separate posting/lifecycle commits and alternate JDBC ledger; adapt four-line intent with one production JPA transaction, not donor persistence/status queues. Legacy FundsPanelFX uses name-based transaction metadata; retain stable IDs instead.


### P25-S1 local implementation and verification — 2026-10-08

Implemented company-scoped FundTransferService and immutable commands/views, UUID request/hash migration V78, four-line canonical accounting and atomic audit facts. Added source net-assets checks through the transfer date and every future recorded day, company-row serialization and exact-request restart/concurrency handling. Generic Journal entry update/direct edit/delete/reverse-and-replace reject linked transfers; both public and caller-owned Journal reversals create inverse operational history with a required reason, preserving dated facts and historical retirement corrections. New-transfer restrictions do not prevent historical correction.

Added the production FUND_TRANSFERS route, Funds action, service registry authorization wiring and navigation. Entry, latest-500 history, New/Save/Refresh, dated reasoned reversal and transaction-ID Journal navigation are real operations. Saved drafts lock; New intentionally discards unsaved changes, while refresh/history selection preserve drafts. Company date/money formatting, typed sortable columns, saved table/divider preferences, independent scrolling and live write-permission gates are integrated. Visible retention and SCLX portability explanations replace deletion. Export inventory warns about deferred operational transfer links while existing canonical ledger/reversal export/import is verified. D03 and later-slice gates remain unchanged.

Documents: [governing transfer contract](funds/internal-fund-transfers.md), [desktop acceptance notes](P25-S1-user-testing.md), interface operation matrix, editor guidelines, transaction lifecycle and SCLX specification. Tests cover accounting/report reconciliation, restart and concurrent retries/overdraw, future-date availability, restrictions/ancestors/ownership/accounts/authorization/periods, generic corrections, reversal chains and audit-failure atomic rollback. V77-to-V78 in-memory upgrade and untracked-history recovery preserve legacy facts/request uniqueness. The Xvfb CI list includes real FundTransfersPanel service/layout tests.

Local baseline/final Maven commands are unavailable (`mvn: command not found`, exit 127); baseline and final all-main/all-test Java 17 compilation succeeds with the same two existing ReportLibraryPanel varargs warnings. A custom JUnit Platform 1.10.3 launcher uses cached project dependencies, with older cached Platform 1.9.3 jars excluded. Final focused regression: 18 tests started/successful, zero failures/aborts. Broad regression initially exposed replay failures in untracked-schema recovery; unapplied V78 now follows existing replay-safe migration conventions, and the recovery regression passes. A subsequent broad run hit only stale checksums in the earlier test-created default database; the final broad run uses an isolated JVM user.home and leaves that database untouched. Final isolated broad regression completed: 904 tests discovered, 873 started, 830 successful, 43 aborted because the graphical toolkit/display is unavailable, zero failures; 12 headless containers aborted and their 31 tests did not start. This custom-launcher evidence is not a Maven or Xvfb success claim. No GitHub publication or CI is claimed; owner desktop testing is pending.


**Final local handoff:** P25-S1 VERIFYING on `codex/P25-S1-internal-fund-transfers`; implementation/verified code head `0103def5449d69b6a527375fbad9ab6188b81981`, based on merged main `a9275ad1b9a970a084829d5659d792375ed196fb`. Local sequence includes P24 evidence/closeout `82ce8a4`, `8cc7a71`, the implementation commit, then this documentation-only evidence commit (resolve final branch tip with `git rev-parse HEAD`; `active_head` identifies the verified implementation). PR is null/unpublished. The final tree/whitespace review is clean and intentional. Local application compilation and focused/broad JUnit runs pass; no unresolved functional failures from the completed local runs. `mvn clean verify` remains unavailable, graphical tests/owner desktop acceptance remain pending, and GitHub has not run this branch.

Remaining exact action after explicit owner publication authorization: confirm current remote main and expected branch state, publish the complete reviewed local sequence through authenticated Git or the connected GitHub service without force updates, compare every remote tree to its local tree, create a draft P25-S1 PR, confirm its actual head and inspect the repository Maven/headless/Xvfb workflow. Repair any failures in this slice and record actual CI evidence; use [P25-S1 desktop acceptance](P25-S1-user-testing.md) for owner testing. Do not mark DONE or activate P25-S2 before confirmed merge and required acceptance. D03 is still unresolved for legal-entity mappings; no later-phase work is authorized by this handoff.


### P25-S1 authorized draft PR publication — 2026-10-07 (America/Denver)

Owner explicitly authorized branch publication, draft PR creation and CI verification. Remote main remained `a9275ad1b9a970a084829d5659d792375ed196fb`, the task branch was absent, and the local worktree was clean before publication. Published the complete reviewed local sequence with original messages and identical per-commit trees, then created the new branch at the publication head without force updates. Draft [PR #362](https://github.com/benbaron/sca-jakarta-h2/pull/362) targets main; verified initial branch/PR head `8b507cf89b9dc172ceae138980f15c4b5d334a52`.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `82ce8a48146708f5559fc5969c1b003ad830b4f2` | `4be52df7e347983c9f99acba3ea2206f4669d24c` | `e2c0daff2d1f13fcde04aa71f7057336901dbb2c` |
| `8cc7a71fb48a9227be3f12fdfe9fc6aabc9a4211` | `3402a425707ff3b294377b01689123769b5b4453` | `09b62524f9b4c8607f08ac4fbe545a12fc04ce60` |
| `0103def5449d69b6a527375fbad9ab6188b81981` | `571fb6a1a413087071ad892f2eff4a962e376414` | `e7be9e049ab684043872c68502e14007ecd8ef45` |
| `af23712447c1ed66fb2f5b81f6d78bfb9a572317` | `8b507cf89b9dc172ceae138980f15c4b5d334a52` | `55083f0c28690f092292b1c34643b735d0de0ec9` |

This publication-ledger successor is documentation only and will be fast-forwarded under the same owner authorization. active_head records its known published parent because a commit cannot embed its own SHA. Verify the resulting branch and PR tip and inspect CI for that actual final head; no Maven/Xvfb or desktop pass is claimed at this checkpoint. P25-S1 remains VERIFYING and P25-S2 remains BLOCKED. After final CI, record evidence locally and in the PR body without replacing the verified head with another evidence-only publication. Owner desktop acceptance remains required via [testing notes](P25-S1-user-testing.md).


### P25-S1 first final-head CI and compile correction

Published local publication-ledger `5011f0bd33b4ec49712aaaae07698a9726618b0f` as `3c7623a092b453aa585c3a25ff4f3228ff549625`, with matching tree `5bed2504b9dff2ed5c9af0fb81e1bca035534b1f`; fetched and verified the final branch and PR head. [Maven PR Tests run 37713786924](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37713786924), job `113105502284`, failed during main compilation before tests. Maven source lookup interpreted the panel's wildcard layout import against the existing package-local `GridPane.java` compatibility file, which declares GridPaneAlias. The earlier explicit-all-files local javac invocation did not exercise Maven's implicit source lookup. Repeat tests and Xvfb were skipped; no CI pass is claimed.

Narrow correction: replace the new FundTransfersPanel wildcard imports with explicit JavaFX control/layout imports, including javafx.scene.layout.GridPane. Preserve the existing compatibility file and all accounting/UI behavior. Final local Maven remains unavailable and the earlier container's cached dependencies are absent in this resumed runtime; current validation is import/diff review, with fresh actual-head Maven/Xvfb CI required. Publish the correction under the owner's existing authorization, verify matching tree/head with an expected-parent non-force update, inspect the replacement workflow and resolve any remaining failure before owner desktop acceptance. No later-phase feature or merge is included.


### P25-S1 explicit import completion

The import correction local `74aa82ec3d7600dec99a54fdfecca511d5fc95c3` published as `81fde3393c3e7315cd7e7b718736bff9e9def8ca`, matching tree `1ecf3ee52422a8a78b25c5bfebb60a8f61f98158`, was verified on branch/PR. [Run 37714081173](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37714081173), job `113106439364`, confirmed the GridPane source-lookup error was removed but main compilation found the omitted javafx.scene.control.Control import for the gated input array. Add that explicit import; no behavior or tests change. Full actual-head CI remains required; tests/Xvfb were skipped in this run.
