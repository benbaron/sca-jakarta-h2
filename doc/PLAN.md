---
plan_version: 233
active_phase: P17
active_slice: P17-C11
active_status: IN_PROGRESS
active_branch: codex/P17-C11-legacy-shell-authority
active_pull_request: null
active_head: c7df4252681454f9f37584b14092b41155f8be51
next_action: "Inspect the current C11 shell/date-range and residual compatibility-service authorities from merged C10 main, classify each as current authority, compatibility, or dead code, implement only proven-safe retirements/extractions, add focused regressions and user-testing notes, then publish a draft PR for GitHub validation."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and source of truth

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and one selected slice at a time under root `AGENTS.md`.

The former monolithic execution ledger is preserved at `doc/archive/PLAN-pre-P17-C2.md`. Current `main`, merged pull requests, migrations, tests, governing documents, and this controller are authoritative over historical notes.

## 2. Status values

Valid slice states are `BLOCKED`, `READY`, `IN_PROGRESS`, `VERIFYING`, and `DONE`. `ELIMINATED` is retained only for intentionally removed product scope.

A slice is `DONE` only when its behavior is merged, required validation is green, governing documentation is current, and required owner acceptance is complete.

## 3. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for original contracts; later depreciation completion is P18 |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction completed in P17-C9 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for original contracts; deferred Company Admin extensions are P19 |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, and durable-record corrections | C1-C10 DONE; C11 IN_PROGRESS; C12 queued |
| P18 | Depreciation-run workflow completion | BLOCKED pending P17 completion and batch-semantics review |
| P19 | Deferred Company Administration extensions | BLOCKED pending explicit product requirements for each slice |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 4. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledger, budget, import, record, preference, or prototype authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics rather than invented hard deletion.
- Company-specific money/date/table/divider state remains H2-backed.
- Compatibility identifiers may remain only when an active compatibility path requires them; obsolete customer-facing terminology and duplicate production panels do not remain merely because historical tests referenced them.
- Historical H2 tables are not deleted merely because a UI or compatibility API is retired; schema cleanup requires an explicit nondestructive migration decision.

## 5. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE for merged production behavior; corrective evidence/coverage PR #304 is VERIFYING and must not be merged without green final-head Actions.

PRs #290, #291, and #292 merged the shared UI policy corrections. Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`.

### P17-C2 — Durable account record lifecycle

Status: DONE.

PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. Chart of Accounts uses stable account identity and governed Active/deactivation lifecycle.

### P17-C3 — Budget version lifecycle completion

Status: DONE.

PR #295 merged at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`. `DRAFT`, `ACTIVE`, and retained `ARCHIVED` versions use stable `BudgetPlan.id` and governed replacement activation.

### P17-C4 — Banking durable-record lifecycle

Status: DONE.

PR #296 merged at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`. Bank/configured-account active-state invariants are enforced without hard deletion.

### P17-C5 — Inventory item lifecycle completion

Status: DONE.

PR #297 merged at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`. Inventory lifecycle is service-owned; zero quantity is required before retirement states and durable history is retained.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: DONE.

PR #298 merged at `d067877d699f4aa05c635b52abcc0aa65d55fbc3`. Active Fund ancestry is enforced across administration, lookup, and SCLX seams. Merged-main Maven PR Tests run `33033595424` passed.

### P17-C7 — Fixed-asset status lifecycle completion

Status: DONE.

PR #299 merged at `e053a7430f47824529b1c55d080d596f4b5e84a5`. Product/test head `716748540ac4d77dbb32ec6afc99391614fbd258` passed run `33093565617`; final documentation successor `75e34ace61d2d799e6b0d7d6178bf985e5151556` passed run `33094228011`.

### P17-C8 — Dashboard production-compliance correction

Status: DONE.

PR #300 merged at `8f66bcfce86a411b6c1d5af6209bb062803af742`. Final head `a4e46d59012bd84b41ca82dcfb4609bafaf3c8e6` passed Maven PR Tests run `33113991215`. Dashboard uses canonical Journal wording/navigation, removes the self-targeting Quick Links footer, and uses current SCLX file/preview terminology.

### P17-C9 — Report Library active-period synchronization

Status: DONE.

PR #301 merged at `2cac5dc3275f08a5f175332b030c3f336e94c5d0`. Implementation head `dd6b11c043c6d2b4c027451a37e43f985d1d7d08` passed run `33117645158`; final documentation successor `0dde55a22f298797a946925107b8aa79d7926eb6` passed run `33118135046`. Untouched report defaults follow shell accounting-period changes while explicit report dates remain detached.

### P17-C10 — Retire duplicate legacy UI panels and stale customer-panel architecture

Status: DONE.

Completion evidence:

- PR #302 merged to `main` at `c7df4252681454f9f37584b14092b41155f8be51` after owner verification.
- Exact final PR head `6b9efc06ac8b3a37e0390274ae00f536605a0ce7` passed Maven PR Tests run `33125550120`.
- Unreachable alternate Dashboard/reference workspaces and standalone Journal/Ledger Register/Transaction Editor source panels were removed.
- The disconnected `CustomerUiPanelCatalog` / `CustomerPanelId` prototype architecture was removed.
- `AppPanelId.LEDGER_REGISTER` and `TXN_EDITOR` remain only as compatibility aliases to the canonical Journal workspace.
- Current production shell/routing guardrails and application-composition documentation were updated.
- No accounting, persistence, migration, import, report-calculation, banking, reconciliation, or period-close authority changed.

### P17-C11 — Retire legacy shell/date-range and classify residual compatibility services

Status: IN_PROGRESS.

Branch: `codex/P17-C11-legacy-shell-authority`
Starting base: merged C10 `main` `c7df4252681454f9f37584b14092b41155f8be51`
Pull request: none yet.

Purpose:

- remove or reduce obsolete shell/date-range architecture only after proving which pieces are still consumed by current production;
- move any still-required shared session state out of obsolete `MainWindow` into a dedicated current authority before retiring the old shell;
- classify residual legacy services as current compatibility requirements or dead code, then remove only those proven unreachable.

Required reading:

- root `AGENTS.md`
- `doc/interface-operation-matrix.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/architecture/application-composition.md`
- current persistence/period-close/reconciliation documentation relevant to any residual service under review.

Required inspection before design:

- `MainWindow` and every current consumer of `MainWindow.sharedSessionState()`;
- `ProductionWorkspaceWindow`, `WorkspaceContext`, `WorkspaceServicesFactory`, `PanelFactory`, and current shell/session state classes;
- `DateRangeContext`, `DateRangeSelector`, `DateRangeUtil`, and all production/test consumers;
- old Find/command-palette helpers or shell actions not installed by `ProductionWorkspaceWindow`;
- `ScheduleEligibilityService` and every production/test consumer;
- legacy reconciliation-run repositories/services and current reconciliation authorities;
- legacy period-close-run repositories/services and current period-close authorities;
- relevant migrations and H2 tables before classifying persistence-backed compatibility code as removable.

Required decisions and deliverables:

1. **Session authority** — identify the current production facts still obtained through `MainWindow.sharedSessionState()`. Extract only those required facts into a dedicated current authority; update consumers and tests; then retire or reduce `MainWindow` only as far as source evidence permits.
2. **Date-range helpers** — classify `DateRangeContext`, `DateRangeSelector`, `DateRangeUtil`, and the known legacy fiscal/calendar TODO path. Retain any helper still serving current Report Library/drill-through behavior; remove only unreachable shell/editor paths. Do not replace accounting-period authority with wall-clock/calendar-year logic.
3. **Find/command palette** — remove obsolete shell-only command/find behavior only when it is not installed or consumed by the production workspace.
4. **Schedules compatibility** — classify `ScheduleEligibilityService` after the top-level Schedules product phase was eliminated. Retain it if a current production feature still depends on its domain rule; otherwise remove it and its dead-only tests/wiring.
5. **Reconciliation compatibility** — distinguish current authoritative reconciliation services from superseded run/history wrappers. Do not remove any API or table needed for cleared-state comparison, finalization, audit, migration compatibility, or historical reads.
6. **Period-close compatibility** — distinguish current authoritative period-close/audit services from superseded run wrappers. Preserve closed-period enforcement, reopen/adjustment/audit history, and historical reads.
7. Add focused source/service/regression tests proving each removed class is unreachable and each retained authority still has a live consumer.
8. Update directly affected governing architecture documentation and add `doc/P17-C11-legacy-shell-authority-user-testing.md` with user-visible/manual checks.

Guardrails:

- no database table deletion merely because a Java class is retired;
- no applied migration edits;
- no new shell, second session store, second date authority, second reconciliation authority, or second period-close authority;
- no C12 repository-wide documentation sweep in this slice;
- any substantial newly discovered behavior defect becomes a separately planned corrective slice rather than an opportunistic rewrite.

Validation:

- local Maven results must not be claimed unless actually run;
- before owner handoff, final branch head must pass the repository Maven PR Tests, including clean verify, repeat tests, and production JavaFX route compliance;
- desktop/manual acceptance follows the C11 user-testing document;
- stop before merge for owner acceptance.

Next exact action:

- inspect and classify the current C11 authorities from this exact merged-C10 base, starting with `MainWindow.sharedSessionState()` consumers and the date-range helpers, then document the classification before performing removals or extraction.

### P17-C12 — Documentation authority reconciliation

Status: BLOCKED by P17-C11 completion/merge.

Purpose:

Perform the repository-wide governing-document reconciliation exposed by the August 27 panel audit after code/architecture cleanup is settled.

Required corrections include:

- update `doc/interface-operation-matrix.md` status/header and obsolete completed-backlog claims;
- update `doc/persistence-authority-inventory.md` stale reconciliation/fixed-asset/inventory statements;
- update `doc/testing/production-workspace-test-plan.md` import scenarios to current format-specific preview/review/atomic-commit workflows;
- search current governing docs for retired Ledger Register/Transaction Editor destinations, eliminated Schedules, generic Import/Export Jobs, Approval workflow wording, and stale phase ownership;
- preserve historical/archive documents as historical evidence.

This is a documentation-authority slice, not a product redesign. Newly found live defects receive separately numbered corrective slices.

## 6. P18 — Depreciation-run workflow completion

Status: BLOCKED pending P17 completion and explicit batch-semantics review.

### P18-S1 — Period batching and Report Library integration

Purpose: complete a user-usable accounting-period depreciation workflow by building on `FixedAssetService.runMonthlyDepreciation(...)`, not a second depreciation engine.

Before implementation, inspect and decide:

- eligible assets for one accounting period;
- per-asset preview amounts and exclusions;
- idempotency when a monthly run already exists;
- closed-period and lifecycle protection;
- all-or-nothing multiasset transaction versus orchestrated independently atomic governed asset runs;
- failure/retry semantics;
- exact integration with the existing Fixed Asset Depreciation History & Schedule report.

No batch implementation may weaken existing per-asset canonical transaction, audit, portable-identity, reversal, or lifecycle authority.

## 7. P19 — Deferred Company Administration extensions

Status: BLOCKED pending explicit product requirements and persistence inspection.

### P19-S1 — Company chart assignment administration

Define the durable company↔chart relationship, safe reassignment rules, and interactions with existing accounts, reports, imports, and company switching before any migration or overwrite behavior is introduced.

### P19-S2 — Company reporting-default administration

Define persisted policy versus transient UI convenience and expose only defaults with real production consumers. Reuse existing preference authority; do not create a second preference store.

### P19-S3 — Tax-filing metadata administration

Blocked until the owner specifies the required filing identities, periods, fields, and their reporting/export consumers.

## 8. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements. No credentials or enforcement are implemented without separate authorization.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE. Enforce approved permissions at authoritative service boundaries; JavaFX reflects permissions rather than defining them.

## 9. Audit-to-plan mapping

| Audit finding | Planned owner |
|---|---|
| Dashboard retired Ledger Register wording | P17-C8 — DONE |
| Dashboard self-targeting All Quick Links | P17-C8 — DONE |
| Dashboard Import SCLX Workbook terminology | P17-C8 — DONE |
| Report Library active-period synchronization | P17-C9 — DONE |
| Duplicate old Dashboard/Journal/reference panels | P17-C10 — DONE |
| Stale CustomerUiPanelCatalog / CustomerPanelId architecture | P17-C10 — DONE |
| Obsolete MainWindow shell behavior and legacy date-range TODO | P17-C11 |
| Residual Schedules/reconciliation-run/period-close-run compatibility services | P17-C11 |
| Stale interface matrix / persistence inventory / workspace test-plan claims | P17-C12 |
| Depreciation Runs richer batching/report integration | P18-S1 |
| Company chart assignment editor deferral | P19-S1 |
| Company reporting-default editor deferral | P19-S2 |
| Company tax-filing editor deferral | P19-S3 |
| Authentication/runtime-permission deferral | P20-S1 through P20-S3 |

## 10. Advancement rule

Execute only the active slice. P17-C12 remains blocked until P17-C11 is merged and verified. P18 remains blocked until P17 is complete and its batch-semantics review is explicitly entered.

The C11 branch begins from exact merged C10 `main` `c7df4252681454f9f37584b14092b41155f8be51`. `active_head` intentionally records that verified starting base until the first tested/published C11 product head exists.
