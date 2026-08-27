---
plan_version: 228
active_phase: P17
active_slice: P17-C8
active_status: VERIFYING
active_branch: codex/P17-C8-dashboard-compliance
active_pull_request: 300
active_head: c60d9ff3581e0885e4cab5f85e2358d89106837a
next_action: "Validate the final documentation-successor head of draft PR #300 with standard Maven PR Tests, execute doc/P17-C8-dashboard-compliance-user-testing.md, and stop before merge for owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved at `doc/archive/PLAN-pre-P17-C2.md`. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. P17-C3 merged through PR #295 at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`. P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`. P17-C5 merged through PR #297 at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`. P17-C6 merged through PR #298 at `d067877d699f4aa05c635b52abcc0aa65d55fbc3`. P17-C7 merged through PR #299 at `e053a7430f47824529b1c55d080d596f4b5e84a5` after owner acceptance and green final-head Maven PR Tests.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for their original phase contracts; later depreciation completion is P18 |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction is P17-C9 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for their original phase contracts; deferred Company Admin extensions are P19 |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, and durable-record corrections | C1-C7 DONE; C8 VERIFYING; C9-C12 queued |
| P18 | Depreciation-run workflow completion | BLOCKED pending P17 completion and batch-semantics review |
| P19 | Deferred Company Administration extensions | BLOCKED pending explicit product requirements for each slice |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 4. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, budget stores, import stores, record stores, or prototype maintenance paths.
- Every enabled production command performs a genuine operation or navigation.
- Disabled placeholder Delete buttons are not part of the UI contract. A Delete action is shown only when it performs a real governed operation.
- Durable records that must preserve history use deactivation, archive, disposition, correction, reversal, or another governed lifecycle rather than invented generic hard deletion.
- Company-specific money/date/table/divider state remains H2-backed.
- Retired compatibility identifiers may remain only when an active compatibility path requires them; obsolete customer-facing terminology and duplicate production panels must not remain merely because old tests reference them.

## 5. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE.

Completion evidence:

- PRs #290, #291, and #292 merged the authorized shared UI policy corrections.
- Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`.

### P17-C2 — Durable account record lifecycle

Status: DONE.

Completion evidence:

- PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`.
- Interactive Chart of Accounts uses stable account ID and governed Active/deactivation lifecycle without placeholder Delete.

### P17-C3 — Budget version lifecycle completion

Status: DONE.

Completion evidence:

- PR #295 merged at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc` after owner confirmation.
- `DRAFT`, `ACTIVE`, and retained `ARCHIVED` versions use stable `BudgetPlan.id`; replacement activation is the only way an active version becomes archived.

### P17-C4 — Banking durable-record lifecycle

Status: DONE.

Completion evidence:

- PR #296 merged at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4` after owner acceptance.
- Bank/configured-account parent-child active-state invariants are enforced across interactive and interchange seams without hard deletion.

### P17-C5 — Inventory item lifecycle completion

Status: DONE.

Completion evidence:

- PR #297 merged at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53` after owner acceptance.
- Inventory lifecycle status is service-owned; zero quantity is required before deactivation/disposal and disposed history remains retained.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: DONE.

Completion evidence:

- PR #298 merged at `d067877d699f4aa05c635b52abcc0aa65d55fbc3` after owner acceptance.
- Exact merged-main Maven PR Tests run `33033595424` passed all repository gates.
- Active Fund ancestry is enforced across interactive administration, active lookup, and SCLX import/export.

### P17-C7 — Fixed-asset status lifecycle completion

Status: DONE.

Completion evidence:

- PR #299 merged to `main` at `e053a7430f47824529b1c55d080d596f4b5e84a5` after owner acceptance.
- Exact product/test head `716748540ac4d77dbb32ec6afc99391614fbd258` passed Maven PR Tests run `33093565617`.
- Final documentation successor head `75e34ace61d2d799e6b0d7d6178bf985e5151556` passed Maven PR Tests run `33094228011` before merge.
- ACTIVE/INACTIVE is service-owned and audited; ordinary edits preserve status; DISPOSED remains Sale/Retirement-owned and is cleared only by governed domain reversal; SCLX historical restore retains its dedicated caller-owned seam.

### P17-C8 — Dashboard production-compliance correction

Status: VERIFYING.

Branch: `codex/P17-C8-dashboard-compliance`
Starting base: `e053a7430f47824529b1c55d080d596f4b5e84a5`
Draft pull request: #300
Implementation/publication head before this PLAN successor: `c60d9ff3581e0885e4cab5f85e2358d89106837a`

Audit findings that define this slice:

- `DashboardHomePanel` displayed **View Ledger Register** although `LEDGER_REGISTER` is a retired compatibility alias and the canonical production destination is **Journal**.
- The Dashboard **All Quick Links** footer targeted `AppPanelId.DASHBOARD`, so an enabled control merely reselected the panel already open rather than performing a genuine navigation or operation.
- The Dashboard label **Import SCLX Workbook** carried obsolete workbook-centric terminology although the governed production workflow is SCLX file preview/import through Import Preview.

Required reading completed:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/architecture/dashboard-workspace.md`
- `doc/data-exchange/sclx.md`

Implemented deliverables:

- Recent Transactions now shows **View Journal →** and routes directly to canonical `AppPanelId.JOURNAL_PANE`.
- The New action's diagnostic text now describes the Journal entry workflow; the existing `TXN_EDITOR` compatibility identifier remains where it carries new-entry intent and still normalizes to the one Journal workspace.
- **All Quick Links →** and its Dashboard self-target are removed rather than replaced with a new artificial destination.
- The import quick action now shows **Import SCLX File** / **Preview and import an SCLX file** and continues to navigate only to governed Import Preview; no parsing or commit authority changed.
- `DashboardNavigationComplianceSourceTest` rejects the retired Dashboard wording, self-targeting Dashboard link, and workbook-only SCLX copy while requiring the canonical Journal and governed import/reconciliation destinations.
- `doc/architecture/dashboard-workspace.md` now states the Dashboard navigation contract.
- `doc/P17-C8-dashboard-compliance-user-testing.md` records the owner desktop acceptance steps.

Scope confirmation:

- no accounting, dashboard-query, Journal-service, import-commit, reconciliation, persistence, or schema behavior changed;
- the current `doc/interface-operation-matrix.md` already states canonical Journal/compatibility-alias authority and did not contain the three stale Dashboard labels, so no C8 matrix rewrite is required; the broader matrix/header reconciliation remains assigned to P17-C12;
- two transient connector helper files were accidentally created during publication and immediately deleted. The PR's final changed-file set contains neither helper and is checked from `main` rather than inferred from commit history.

Validation status:

- GitHub transport compare from starting `main` shows only the intended C8 production panel, focused test, Dashboard governing doc, C8 user-test doc, and PLAN controller as final changed files.
- Local Maven results are not claimed because this environment cannot reach GitHub/Maven through a local clone.
- Standard Maven PR Tests on the final PR head are pending and must be inspected before owner handoff.

Owner acceptance:

- Execute `doc/P17-C8-dashboard-compliance-user-testing.md` only after final-head Maven PR Tests are green.
- Do not merge until final-head GitHub validation passes and the owner accepts the checklist.

Next exact action:

- Validate the final documentation-successor head of draft PR #300 with standard Maven PR Tests, inspect any failing logs, then perform the owner checklist and stop before merge.

### P17-C9 — Report Library active-period synchronization

Status: BLOCKED by P17-C8 completion/merge.

Finding:

- `ReportLibraryPanel` derives its initial default dates from `ActivePeriodContext`, but unlike Dashboard/Budget views it does not currently react to a later shell active-period change. Existing tests prove initial fiscal defaults but not ongoing synchronization.

Purpose:

- Make the Report Library's default fiscal/as-of range follow a changed shell accounting period without overriding a report range the operator has explicitly edited.

Required behavior to prove before implementation is complete:

- default-following report controls update when the active accounting period changes;
- once the operator explicitly changes report-specific dates, those dates remain the actual immutable `ReportRequest` until the operator resets or changes them;
- preview and every export continue to consume the same request;
- no wall-clock/calendar-year substitution is introduced;
- Balance Sheet and Income Statement period-dependent headings/values update consistently when following the shell period.

Expected files include `ReportLibraryPanel`, active-period/fiscal authority tests, report request tests, and `doc/reporting/report-library.md`.

### P17-C10 — Retire duplicate legacy UI panels and stale customer-panel catalog

Status: BLOCKED by P17-C9 completion/merge.

Audit scope:

- classify and, where safe, remove obsolete alternate/reference Dashboard surfaces such as `DashboardWorkspacePanel`, `DashboardExperiment`, `DashboardPanelFX`, and `ReferenceWorkspaceWindow`;
- classify and, where safe, remove old separate Journal/Ledger/Transaction panels such as `JournalPane`, `LedgerRegisterPanel`, and `TransactionEditorPanel` now that production aliases normalize to one `JournalWorkspacePanel`;
- retire or rewrite the stale `CustomerUiPanelCatalog` / `CustomerPanelId` architecture model that still names eliminated customer-facing workspaces such as Open Item Schedules, Event Lifecycle, generic Import/Export, and Approval & Audit;
- remove tests that preserve dead architecture only after proving no production, compatibility, migration, or donor-reference contract still requires the class.

Guardrails:

- do not remove `AppPanelId.LEDGER_REGISTER` or `TXN_EDITOR` merely because the visible panels are retired; those identifiers remain compatibility aliases until a deliberate compatibility-breaking decision;
- no production behavior change other than removal of unreachable duplicate surfaces and stale architecture claims.

### P17-C11 — Retire legacy shell/date-range and classify residual compatibility services

Status: BLOCKED by P17-C10 completion/merge.

Audit scope:

- reduce or retire the obsolete `MainWindow` shell after moving any still-required shared session state to a dedicated current authority;
- remove old Find/command-palette/date-range behavior that is not installed by `ProductionWorkspaceWindow`;
- classify and remove `DateRangeUtil`, `DateRangeSelector`, or related calendar/fiscal-year TODO paths when they are proven unreachable from production;
- classify residual services such as `ScheduleEligibilityService`, legacy reconciliation-run repositories, and legacy period-close-run repositories as either required compatibility APIs or removable dead authority;
- remove only after source/test/search evidence proves no current production or migration contract depends on them.

Guardrails:

- do not delete historical H2 tables merely because their former panels are gone;
- no migration cleanup without a separate nondestructive migration decision and explicit evidence that retained historical data is no longer required.

### P17-C12 — Documentation authority reconciliation

Status: BLOCKED by P17-C11 completion/merge.

Purpose:

- perform the repository-wide documentation sweep exposed by the panel audit after the preceding code/architecture corrections are settled.

Required corrections include:

- update `doc/interface-operation-matrix.md` status/header and remove obsolete immediate-backlog claims for already-completed work;
- update `doc/persistence-authority-inventory.md` statements that still describe reconciliation editing as incomplete, fixed-asset specialized reporting as future work, or inventory reporting as deferred when current production code has already delivered those authorities;
- update `doc/testing/production-workspace-test-plan.md` import scenarios that still describe the superseded generic staging workflow rather than the current format-specific preview/review/atomic-commit contracts;
- search current governing documentation for retired production terminology such as separate Ledger Register/Transaction Editor destinations, eliminated Schedules, generic Import/Export Jobs, Approval workflow wording, and stale phase ownership;
- retain historical/archive documents as historical evidence rather than rewriting them to look current.

This is a documentation-authority slice, not a product-behavior redesign. Any newly discovered live defect becomes a separately numbered corrective slice rather than being silently changed here.

## 6. P18 — Depreciation-run workflow completion

Status: BLOCKED pending P17 completion and explicit batch-semantics review.

### P18-S1 — Period batching and Report Library integration

Finding:

- the current interface matrix explicitly records remaining Depreciation Runs work as **richer period batching and report integration**. The current panel has a genuine governed monthly depreciation operation, but the broader period/batch workflow is not complete.

Purpose:

- complete a user-usable accounting-period depreciation workflow by building on `FixedAssetService.runMonthlyDepreciation(...)` rather than creating a second depreciation engine.

Before implementation, the slice must inspect and document:

- eligible-asset selection for one accounting period;
- preview of per-asset amounts and exclusions;
- idempotency when a monthly run already exists;
- closed-period and lifecycle protection;
- whether a multi-asset action is one all-or-nothing transaction or an orchestrated set of independently atomic governed asset runs;
- failure reporting and retry semantics;
- exact navigation/integration with the existing Fixed Asset Depreciation History & Schedule report.

No batch implementation may weaken the existing per-asset canonical transaction, audit, portable-identity, or reversal/lifecycle authority.

## 7. P19 — Deferred Company Administration extensions

Status: BLOCKED pending explicit product requirements and persistence inspection for each slice.

The current Company Admin panel explicitly tells operators that the following editors are deferred. They are now assigned stable plan numbers rather than being left as an orphaned note.

### P19-S1 — Company chart assignment administration

Define and implement the durable relationship between a company and its selected/available chart authority, including safe reassignment rules and interaction with existing accounts, reports, imports, and company switching. Do not infer a migration or overwrite existing chart ownership until the current model is inspected and the owner approves the lifecycle semantics.

### P19-S2 — Company reporting-default administration

Define which report defaults are true persisted company policy versus transient UI convenience, then expose only defaults with real production consumers. Reuse `CompanyUiPreferencesService` or another existing authority where appropriate; do not create a second preference store.

### P19-S3 — Tax-filing metadata administration

Define the actual tax/fiscal metadata required by the product before adding controls or schema. This slice is blocked until the owner specifies the required filing identities/periods/fields and their reporting/export consumers.

## 8. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

The current User Admin **Authentication** tab is intentionally explanatory only. Administrative user/role/assignment records remain factual history and must not be mistaken for login identity or effective permissions until this phase is explicitly authorized.

### P20-S1 — Authentication and authorization requirements boundary

Define supported identity source(s), credential ownership, account recovery, session lifecycle, audit requirements, threat model, role-to-permission semantics, bootstrap/last-administrator rules, and compatibility/migration requirements. No credential storage or permission enforcement is implemented in this slice unless separately authorized after the requirements are approved.

### P20-S2 — Authentication implementation

Implement the approved login/identity/session boundary from P20-S1 with secure credential or identity-provider handling and tests. This slice remains BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

Map approved permissions onto production commands/services and enforce them at the authoritative service boundary, with JavaFX controls reflecting rather than defining permission. This slice remains BLOCKED until P20-S2 is DONE.

## 9. Audit-to-plan mapping

The August 27, 2026 repository-wide panel/documentation audit is assigned as follows:

| Audit finding | Planned owner |
|---|---|
| Dashboard retired **Ledger Register** wording | P17-C8 |
| Dashboard self-targeting **All Quick Links** | P17-C8 |
| Dashboard **Import SCLX Workbook** terminology | P17-C8 |
| Report Library does not follow later active-period changes | P17-C9 |
| Duplicate/reachable-looking old Dashboard/Journal/reference panels | P17-C10 |
| Stale `CustomerUiPanelCatalog` / `CustomerPanelId` architecture | P17-C10 |
| Obsolete `MainWindow` shell behavior and hard-coded legacy date-range TODO | P17-C11 |
| Residual Schedules/reconciliation-run/period-close-run compatibility services | P17-C11 |
| Stale interface matrix / persistence inventory / workspace test-plan claims | P17-C12 |
| Depreciation Runs richer batching/report integration | P18-S1 |
| Company chart assignment editor deferral | P19-S1 |
| Company reporting-default editor deferral | P19-S2 |
| Company tax-filing editor deferral | P19-S3 |
| Visible Authentication/runtime-permission deferral | P20-S1 through P20-S3 |

## 10. Advancement rule

Execute only the active slice. Do not start P17-C9 while P17-C8 is unmerged. When a corrective slice merges and its required validation/owner acceptance is complete, mark it `DONE`, activate the next unblocked slice, and update this controller from current `main`.

The PLAN update that records PR #300 is documentation-only. `active_head` records the implementation/publication head immediately before this PLAN successor rather than attempting to self-reference the commit that contains this line. The final documentation-successor head must itself pass the standard GitHub validation before owner handoff.
