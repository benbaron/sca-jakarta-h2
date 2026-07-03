---
plan_version: 3
active_phase: P03
active_slice: P03-S1
active_status: VERIFYING
active_branch: codex/p03-s1-shared-line-editor
active_pull_request: none
active_head: 062e503 (implementation commit; see latest branch head for plan-only update)
next_action: "P03-S1 implementation is complete; rerun mvn clean verify when Maven Central is reachable, then validate PR checks."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`.

Codex must not treat this as a general backlog. On container startup it selects one phase and one slice using `AGENTS.md`, executes only that scope, and updates this file with actual state.

The phase section is the seed prompt. A separate prompt document is not required.

## 2. How to invoke Codex

Preferred explicit invocation:

```text
Execute PHASE=P02, SLICE=P02-S1 from doc/PLAN.md.
Follow AGENTS.md.
Use current main.
Proceed through implementation, tests, documentation, and PR validation.
```

To resume recorded work:

```text
Continue the active phase and slice from doc/PLAN.md.
Follow AGENTS.md and the recorded branch/PR handoff.
```

To request a phase without a slice:

```text
Execute PHASE=P05 from doc/PLAN.md.
Select the first incomplete unblocked slice in that phase.
```

Codex must verify prerequisites even when the task names a later phase.

## 3. Status and state rules

Valid status values:

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`

Only merged and verified behavior is `DONE`.

When a branch begins, update front matter and the phase section. When a PR is open, record it. When validation remains, use `VERIFYING`. After merge is confirmed on current `main`, mark `DONE` and advance the active phase.

## 4. Phase index

| Phase | Name | Depends on | Initial status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE |
| P01 | Production shell and workspace composition | P00 | DONE |
| P02 | Canonical ledger and transaction operations | P00 | READY |
| P03 | Ledger Register and Transaction Editor | P01, P02 | BLOCKED |
| P04 | Persistent budgeting | P02 | BLOCKED |
| P05 | Bank import and statement-line persistence | P02 | BLOCKED |
| P06 | Bank reconciliation | P05 | BLOCKED |
| P07 | Schedules and open items | P02 | BLOCKED |
| P08 | Fixed assets and depreciation | P02 | BLOCKED |
| P09 | Inventory and supplies | P02 | BLOCKED |
| P10 | Period close, reopening, notes, and audit | P02, P06 | BLOCKED |
| P11 | Report Library | P02, P04, P06, P07, P08, P09 | BLOCKED |
| P12 | Administration, company lifecycle, and preferences | P01, P02 | BLOCKED |
| P13 | Imports, exports, jobs, and diagnostics | P02, P05, P12 | BLOCKED |
| P14 | End-to-end hardening | P03–P13 | BLOCKED |

A phase may begin early only when its required slice has no dependency on an unfinished prerequisite and the plan is updated to explain the deliberate exception.

## 5. Governing documents

### Always read

- `AGENTS.md`
- `doc/PLAN.md`

### Existing focused documents

- `doc/architecture/production-workspace.md`
- `doc/architecture/dashboard-workspace.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/accounting/ledger-authority.md`
- `doc/import/import-review-workflow.md`
- `doc/testing/production-workspace-test-plan.md`
- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/architecture/union-application-direction.md`
- `doc/workflow/development-workflow.md`

### Legacy reference consolidated during P00

- `doc/architecture/union-application-direction.md` records the still-current union application direction.
- The legacy path `docs/union-application-migration-plan.md` was not present in this worktree during P00 inventory.

### Documents created by later phases

Create only when their owning phase begins:

- `doc/architecture/application-composition.md` — P01 (created in P01-S1)
- `doc/architecture/command-and-query-boundaries.md` — P01
- `doc/accounting/budget-model.md` — P04
- `doc/banking/import-and-reconciliation.md` — P05/P06
- `doc/accounting/open-items-and-schedules.md` — P07
- `doc/database/schema-and-migration-policy.md` — first schema-changing phase
- `doc/database/database-lifecycle.md` — P12
- `doc/reporting/report-architecture.md` — P11
- `doc/ui/editor-guidelines.md` — P03 (created in P03-S1)
- `doc/testing/end-to-end-scenarios.md` — P14
- `doc/workflow/development-workflow.md` — P00 (created)

Link every created document from this section.

## 6. Established product decisions

These apply to every phase unless a focused document deliberately supersedes them.

### Product and architecture

- One production JavaFX application.
- H2 is authoritative for accepted operational/accounting data.
- Existing JPA/Hibernate model and Flyway migrations are the schema foundation.
- No parallel ledgers, budget stores, import stores, or panel frameworks.
- Write services own validation and transactions.
- Query services return projections for panels and reports.
- Constructor injection is preferred.

### Interface

- Follow the approved compact white-and-blue reference.
- Omit numbered drawing annotations.
- Preserve icons and green/amber/red/gray/blue cues with textual/symbolic meaning.
- Use menu, toolbar, left navigation, center tabs, right inspector, visible dividers, and status bar.
- Fit a laptop display.
- Never render center content beneath a sidebar.
- Use external CSS.
- Show blank/neutral state rather than fictional data.

### Donor Code
https://github.com/benbaron/NonprofitAccounting.git is available as reference or a donor. Examine the codebase and suggest imports from it when working in a new area of code.

### Accounting

- Genuine double entry.
- `BigDecimal` money.
- Stable IDs.
- At least two meaningful lines.
- Debit equals credit.
- One-sided debit/credit input.
- Zero-value accounting lines rejected.
- Funds include unrestricted, restricted, and designated.
- Budget categories are separate from accounts and activities.
- Closed periods are never silently bypassed.
- Material actions are audited.

### Scope

- No approval queue or formal approval/rejection workflow.
- No formal in-application oversight role.
- No attachment/document-storage feature.
- Notes and factual audit history are in scope.
- Supplies belongs in Inventory.
- Reports belong in `REPORT_LIBRARY`.

### Import behavior

- Review staging remains in memory until accepted.
- Valid and invalid rows remain together.
- Exact duplicate detection uses source ID or deterministic fingerprint.
- Probable duplicates are warnings.
- Matched imports may be discarded, saved as a copy, or left for review.
- Accepted accounting activity uses the canonical transaction service.
- Stable SCLX IDs must be idempotent.
- Zero-value/non-posting SCLX annotations are not accounting transactions.
- Raw SCLX source documents are not retained as accounting truth.

## 7. Phase contracts

---

# P00 — Documentation and implementation inventory

**Selector:** `PHASE=P00`
**Status:** DONE
**Depends on:** none
**Branch:** merged
**Pull request:** P00 documentation inventory was validated and merged by user confirmation.
**Head:** cleared after merge confirmation.

## Objective

Establish an authoritative inventory of current `main` before further broad implementation.

## Required reading

- all documents in section 5;
- root `README.md`;
- repository-level `AGENTS.md`.

## Required inspection

- `AppPanelId`;
- `PanelHost`;
- `NavigationPane`;
- `InspectorPane`;
- workspace/window classes;
- `UiServiceRegistry`;
- all production panel classes;
- entities;
- repositories;
- services;
- migrations;
- tests;
- recent merged PRs affecting the workspace and models.

Search for:

```text
UiWorkspaceDataStore
RunbookPersistence
BudgetTargetPersistence
TODO
placeholder
sample
demo
mock
saved in session
not implemented
Approve
Reject
Attachment
LocalDate.now()
```

## Slices

### P00-S1 — Interface-operation matrix

Status: DONE. Created `doc/interface-operation-matrix.md`.

For every `AppPanelId` and global command record:

- panel/class;
- visible controls;
- query source;
- write source;
- whether data survives restart;
- whether H2 is authoritative;
- model/service/repository dependencies;
- simulated or placeholder behavior;
- sidecar/static persistence;
- missing work;
- owning future phase.

### P00-S2 — Model and persistence authority inventory

Status: DONE. Created `doc/persistence-authority-inventory.md` documenting:

- duplicate transaction/journal models;
- duplicate budget models;
- import staging versus accepted data;
- reconciliation models;
- open-item models;
- audit/approval overlap;
- sidecar file stores;
- migration risks.

### P00-S3 — Documentation consolidation

Status: DONE.

- created `doc/architecture/union-application-direction.md`;
- consolidated still-current legacy `docs/` decisions; the legacy file was absent in this worktree;
- created `doc/workflow/development-workflow.md`;
- updated this plan with a dependency-ordered PR backlog and handoff.

## Forbidden in P00

- production behavior changes;
- new entities or migrations;
- broad UI rewrites;
- deleting legacy models before authority is decided.

## Validation

- Markdown links resolve;
- inventory covers every `AppPanelId`;
- searches and evidence are cited by path/class;
- final diff is documentation only.

## Exit gate

P00 is done when the inventory is merged and later phases can identify exact prerequisites without rescanning the entire repository.


## P00 handoff and findings (2026-07-02)

- Selected phase/slice: P00 documentation and implementation inventory, covering P00-S1 through P00-S3 because the phase seed prompt requested the full inventory set.
- Completed deliverables: interface-operation matrix, model/persistence authority inventory, union application direction, development workflow, and this plan update.
- Required inspection completed: `AppPanelId`, `PanelHost`, `NavigationPane`, `InspectorPane`, workspace/window classes, `UiServiceRegistry`, production panels, entities, repositories, services, migrations, tests, and targeted placeholder/sidecar searches.
- Repository limitations: no `origin` remote is configured, so `git fetch origin --prune`, `git log origin/main`, GitHub workflow inspection, and remote PR creation could not be completed from this container.
- Missing reference files: root `README.md`, `docs/`, and `docs/union-application-migration-plan.md` were not present in this worktree.
- Validation status: P00 was documentation-only and user-confirmed validated/merged. Container Maven checks still cannot resolve plugins from Maven Central (`Network is unreachable`), so local `mvn clean verify` remains environment-blocked here.
- Known findings: static/sidecar UI stores exist for budget targets, bank transactions, import/export jobs, schedules, assets, depreciation, and inventory; transaction entry is session-only; report library contains not-implemented report fallback; approval/rejection controls are visible in reconciliation and period-close run panels.
- Completion update: user confirmed P00 is documentation-only, validated, and merged. P01/P02 prerequisites from P00 are now unblocked; active work advances to P01-S1.

## Dependency-ordered PR backlog after P00

1. P01-S1 Shell authority: one production workspace shell, remove duplicate/reference shell behavior from production routes, remove approval-oriented global commands, and replace button-text command discovery with typed commands.
2. P01-S2 Workspace composition: introduce lifecycle-owned workspace context/services/factory/panel factory so panels no longer use static service lookup as the composition root.
3. P01-S3 Database switching/recovery: make database migration/service construction atomic and refresh database-bound panels after successful swap.
4. P01-S4 Geometry/preferences: harden responsive sidebars, divider persistence, and laptop-size layout tests.
5. P02-S1 Ledger authority: create `doc/accounting/ledger-authority.md` and choose one canonical writable ledger before adding transaction writes.
6. P02-S2/P02-S4: implement transaction command/query/correction policy on the canonical ledger.
7. P03: wire ledger register and transaction editor to canonical services; remove session-only transaction save.
8. P04: replace `BudgetTargetPersistence` with H2 budget plans/lines.
9. P05/P13: persist accepted bank statement/import-job facts and keep only review staging in memory.
10. P06/P10: reconcile bank activity and replace approval/rejection UI with factual audit/close/reopen history.
11. P07/P08/P09: replace runbook sidecars with H2-backed schedules/open items, fixed assets/depreciation, and inventory/supplies.
12. P11/P12/P14: complete report architecture, company/preferences lifecycle, diagnostics, and end-to-end hardening.

## Codex seed prompt

```text
Execute PHASE=P00 from doc/PLAN.md.
Perform only the documentation and implementation inventory.
Inspect current main comprehensively.
Do not change production behavior.
Create the operation matrix, persistence-authority inventory, union-direction document, and dependency-ordered backlog.
Update doc/PLAN.md with actual findings and the next unblocked slice.
```

---

# P01 — Production shell and workspace composition

**Selector:** `PHASE=P01`
**Status:** DONE
**Depends on:** P00
**Branch:** merged by user confirmation
**Pull request:** verified and merged per user instruction
**Head:** cleared after merge confirmation

## Objective

Make the approved interface the single production shell and establish lifecycle-owned workspace composition.

## Required reading

- `doc/architecture/production-workspace.md`;
- `doc/architecture/dashboard-workspace.md`;
- P00 operation matrix;
- `doc/testing/production-workspace-test-plan.md`.

## Required inspection

- all window/workspace classes;
- `MainApp`;
- `PanelHost`;
- `NavigationPane`;
- `InspectorPane`;
- command/shortcut classes;
- `UiServiceRegistry`;
- database recovery and selection logic;
- geometry policies and tests;
- CSS.

## Slices

### P01-S1 — Shell authority

Status: DONE. Branch: merged by user confirmation. PR: verified and merged per user instruction. Head: cleared after merge confirmation.

Completed in this slice:

- selected `ProductionWorkspaceWindow` as the production application launch shell;
- added typed `AppCommand` routing for global shell commands and panel run commands;
- added Workspace → Close All Tabs with Ctrl+Shift+W, permanent Dashboard preservation, and an unsaved-edit discard prompt;
- renamed global approval-audit navigation to factual audit-history terminology;
- created `doc/architecture/application-composition.md` documenting the shell authority decision.

Test status:

- `mvn -DskipTests compile` and `mvn clean verify` were attempted locally before and after the Close All Tabs follow-up, but Maven plugin resolution is environment-blocked by `Network is unreachable` to Maven Central.

Remaining before merge: none; P01-S1 verified and merged by user confirmation.

Original scope:

- select one production workspace class;
- remove decorator/duplicate shell behavior;
- preserve menu, toolbar, navigation, tabs, inspector, dividers, and status bar;
- remove approval-oriented global commands;
- use typed application commands rather than button-text discovery.

### P01-S2 — Workspace composition

Status: DONE. Branch: merged by user confirmation. PR: verified and merged per user instruction. Head: cleared after merge confirmation.

Completed in this slice:

- introduced `WorkspaceContext`, `WorkspaceServices`, `WorkspaceServicesFactory`, and `PanelFactory`;
- made active database, company, period, and database failure state observable through `WorkspaceContext`;
- made `ProductionWorkspaceWindow` own workspace services and construct `PanelHost` from the lifecycle-owned `PanelFactory`;
- added composition tests for context synchronization, database connection context refresh, and panel factory ownership.

Test status:

- Verified and merged by user confirmation.

Remaining before merge:

- none; slice verified and merged by user confirmation.

Original scope:

Create or complete:

- `WorkspaceContext`;
- `WorkspaceServices`;
- `WorkspaceServicesFactory`;
- `PanelFactory`.

Make database/company/period/connection state observable and explicit.

### P01-S3 — Atomic database switching and recovery

Status: DONE. Branch: merged by user confirmation. PR: verified and merged per user instruction. Head: cleared after merge confirmation.

Completed in this slice:

- preserved candidate database validation/migration and service construction before session selection persistence;
- retained the old database selection on failed connection attempts;
- made replacement service-bundle construction close failed candidate JPA resources and close old JPA resources only after a successful swap;
- refreshed open workspace panels through the lifecycle-owned panel factory after a successful database swap;
- documented the atomic switching/recovery behavior in `doc/architecture/application-composition.md`;
- added a panel-host regression test proving database-switch refresh preserves open destinations and the active tab while recreating stale panels.

Test status:

- Verified and merged by user confirmation.

Remaining before merge:

- none; slice verified and merged by user confirmation.

Original scope:

- validate/migrate candidate database;
- construct candidate services;
- swap only after success;
- retain old selection on failure;
- close old JPA resources after success;
- refresh open database-bound panels;
- keep recovery dashboard constructible without accounting services.

### P01-S4 — Geometry and preferences

Status: DONE. Branch: merged by user confirmation. PR: verified and merged per user instruction. Head: cleared after merge confirmation.

Completed in this slice:

- added persisted workspace divider state to the existing app-state store;
- restored only safe remembered divider positions and fell back to responsive defaults when a stored position would clip a sidebar or squeeze the center;
- preserved laptop-friendly startup sizing policy and expanded layout-policy tests for responsive safe-divider behavior.

Test status:

- Verified and merged by user confirmation.

Remaining before merge:

- none; slice verified and merged by user confirmation.

Original scope:

- responsive sidebars;
- remembered safe divider positions;
- laptop startup size;
- no clipping or sidebar overlap;
- scaling policies.

## Forbidden in P01

- new accounting rules;
- transaction-model replacement;
- fake service implementations;
- visual rewrite without preserving reachable workflows.

## Validation

- command-routing tests;
- permanent Dashboard tab;
- close-all-tabs;
- dirty-tab handling;
- atomic database-switch tests;
- service-resource closure;
- geometry tests at supported sizes/scaling;
- `mvn clean verify`;
- desktop screenshots when required.

## Exit gate

One shell and one composition root own all production panels and database-bound service lifecycles.

## Codex seed prompt

```text
Execute PHASE=P01 from doc/PLAN.md.
Use the P00 inventory.
Consolidate the production shell and implement explicit workspace composition without changing accounting semantics.
Complete the first incomplete P01 slice only, including tests and documentation.
```

---

# P02 — Canonical ledger and transaction operations

**Selector:** `PHASE=P02`
**Status:** DONE
**Depends on:** P00
**Branch:** merged by user confirmation
**Pull request:** verified and merged per user instruction
**Head:** cleared after merge confirmation

## Objective

Select one authoritative writable ledger and implement genuine transaction entry, query, and correction services.

## Required reading

- `doc/accounting/transaction-lifecycle.md`;
- `doc/accounting/period-and-correction-policy.md`;
- P00 persistence-authority inventory;
- `doc/testing/production-workspace-test-plan.md`.

## Required inspection

- `Txn`;
- `TxnSplit`;
- journal transaction/line models and migrations;
- transaction services;
- correction services;
- ledger query services;
- accounting periods;
- reconciliation protection;
- audit models;
- all relevant tests and migrations.

## Slices

### P02-S1 — Ledger authority decision

Status: DONE.

Created `doc/accounting/ledger-authority.md`.

Selected `Txn`/`TxnSplit` backed by `txn`/`txn_split` as the canonical writable ledger. Retained `journal_transaction`/`journal_posting_line` as compatibility/projection tables only, not independently writable accounting truth.

Completed deliverables:

- Documented the canonical ledger decision, compatibility treatment, migration policy, service boundary, and follow-up work for P02-S2/P02-S4.
- Linked the decision from the governing document list.

Remaining deliverables:

- None for P02-S1 after user-confirmed P02 verification and merge.

Known failures:

- None recorded after user-confirmed verification and merge.

Next exact action: P02-S1 is complete as part of merged P02.

### P02-S2 — Command and validation model

Status: DONE.

Branch: merged by user confirmation.
Pull request: verified and merged per user instruction.
Head: cleared after merge confirmation.

Completed deliverables:

- Added immutable transaction command and line command DTOs with explicit debit/credit input.
- Added reusable transaction validation result and validator for date, two-line, one-sided, non-zero, required ID, and balance rules.
- Added transaction view and accounting journal projection records for later services and UI panels.
- Documented the P02-S2 command/projection boundary in `doc/accounting/ledger-authority.md`.

Remaining deliverables:

- None for P02-S2 after user-confirmed P02 verification and merge.

Known failures:

- None recorded after user-confirmed verification and merge.

User-visible changes:

- No visible JavaFX workflow changes in P02-S2; this slice adds the shared command and projection model used by later transaction entry/editor work.

Manual testing for user:

- After Maven dependencies are reachable, run `mvn clean verify`. In P02-S3/P03, verify that transaction editor validation messages match the command validation rules before saves are enabled.

Next exact action: P02-S2 is complete as part of merged P02.

### P02-S3 — Transaction entry and query services

Status: DONE.

Branch: merged by user confirmation.
Pull request: verified and merged per user instruction.
Head: cleared after merge confirmation.

Completed deliverables:

- Added `TransactionEntryService` for canonical `Txn`/`TxnSplit` entry, load, bounded search, journal projection, and narrow `ENTERED` update operations.
- Converted debit/credit command input to signed split storage by account normal balance while preserving immutable query projections.
- Resolved payee, bank account, account, fund, budget category, activity, and merchant by stable database ID inside a single JPA write transaction.
- Added rollback coverage for missing referenced master data so failed writes leave no orphan transaction header or lines.
- Documented the P02-S3 service boundary and update policy in `doc/accounting/ledger-authority.md`.

Remaining deliverables:

- None for P02-S3 after user-confirmed P02 verification and merge.

Known failures:

- None recorded after user-confirmed verification and merge.

User-visible changes:

- No visible JavaFX workflow changes in P02-S3; this slice adds the transaction service that P03 will wire into the transaction editor and ledger register.

Manual testing for user:

- After Maven dependencies are reachable, run `mvn clean verify`. In P03, enter, save, reload, search, edit an `ENTERED` transaction, and open journal view from the UI to verify service behavior.

Next exact action: P02-S3 is complete as part of merged P02.

### P02-S4 — Correction, period, reconciliation, and audit behavior

Status: DONE.

Branch: merged by user confirmation.
Pull request: verified and merged per user instruction.
Head: cleared after merge confirmation.

Completed deliverables:

- Added canonical reconciliation protection storage linking `txn` rows to completed `reconciliation_run` records.
- Made the V49 reconciliation-protection migration idempotent for Flyway recovery after partial failed migration attempts.
- Extended transaction entry/update and correction paths with closed-period guards, completed-reconciliation protection, factual audit history, and rollback behavior.
- Preserved direct edit, reversal, optional replacement, and narrow deletion while rejecting protected or closed-period writes before material ledger changes.
- Added service regression coverage for completed-reconciliation protection across entry-service update, direct edit, deletion, and reversal.
- Documented P02-S4 correction, period, reconciliation, and audit policy in `doc/accounting/ledger-authority.md`.

Remaining deliverables:

- None for P02-S4 after user-confirmed verification and merge.

Known failures:

- None recorded after user-confirmed verification and merge.

User-visible changes:

- No visible JavaFX workflow changes in P02-S4; this slice adds the ledger safety rules that P03/P06/P10 UI flows will surface for protected transactions, closed periods, and corrections.

Manual testing for user:

- After Maven dependencies are reachable, run `mvn clean verify`. In later UI phases, save a transaction, close/reopen its period, reconcile it, and verify edit/delete/reverse actions show the documented warnings or protection messages.

Next exact action: return to P01 verification/merge work; P03 remains blocked until P01 and P02 are both merged.

Implemented the documented combination of:

- direct edit;
- reversal;
- optional replacement;
- narrowly permitted deletion;
- audit snapshot/history;
- closed-period warning/reopen;
- completed-reconciliation protection.

## Required rules

- at least two meaningful lines;
- debit equals credit;
- one-sided input;
- zero-value line rejection;
- stable IDs;
- generated identifiers;
- rollback without orphan headers/lines.

## Validation

Unit, service, repository, migration, concurrency, correction, closed-period, reconciliation-protection, and rollback tests.

## Exit gate

All authoritative accounting writes flow through one documented transaction service and one canonical persistence model.

## Codex seed prompt

```text
Execute PHASE=P02 from doc/PLAN.md.
Start with the first incomplete P02 slice.
Resolve ledger authority before adding more transaction code.
Do not introduce posting or approval states.
Implement and test only one canonical writable transaction path.
```

---

# P03 — Ledger Register and Transaction Editor

**Selector:** `PHASE=P03`
**Status:** VERIFYING
**Depends on:** P01, P02
**Branch:** codex/p03-s1-shared-line-editor
**Pull request:** pending local make_pr record
**Head:** 062e503 (implementation commit; see latest branch head for plan-only update)

## Objective

Replace validation-only/session-only UI with a genuine spreadsheet-like accounting workspace.

## Required reading

- `doc/architecture/production-workspace.md`;
- `doc/accounting/ledger-authority.md`;
- transaction lifecycle and period policy;
- `doc/testing/production-workspace-test-plan.md`.

## Slices

### P03-S1 — Shared line editor

Status: VERIFYING. Implemented a reusable `TransactionLineEditorModel`, updated the transaction editor to show separate debit/credit, budget category, and counterparty columns, added live debit/credit totals, documented the shared editor contract in `doc/ui/editor-guidelines.md`, and added focused model tests. Maven validation is blocked in this container because Maven plugin artifacts are not present locally and the network cannot reach Maven Central.

- ID-backed account, fund, budget category, activity, merchant, and counterparty controls;
- debit/credit columns;
- keyboard navigation;
- add/remove row;
- live totals;
- row/field validation;
- dirty state.

Handoff: rerun `mvn clean verify` after dependency resolution is available, then complete PR validation. User testing: open Transaction Editor, confirm Debit/Credit/Budget/Counterparty columns and totals are visible, add a row and verify the Account cell enters edit focus, edit line cells and then click away to confirm values persist, remove a selected row, enter one debit and one credit, and verify validation feedback blocks both-sided or unbalanced input.

### P03-S2 — Transaction workflow

- enter/save through transaction service;
- load/edit under documented policy;
- reverse;
- reverse and replace;
- notes;
- reference/check number;
- journal preview;
- unsaved save/discard/cancel.

### P03-S3 — Ledger Register

- bounded/paged database query;
- filters;
- running balances where meaningful;
- open/correct selected transaction;
- export filtered view;
- refresh on writes/context changes.

## Forbidden in P03

- free-text relationship identity;
- label-only save success;
- “Post / Validate” as an authoritative workflow;
- accounting calculations in controls.

## Validation

Cell-policy tests, editor-to-command mapping, totals, dirty state, invalid-write prevention, refresh, geometry, and service integration.

## Exit gate

The main accounting workspace performs genuine transaction operations and remains usable at supported laptop sizes.

## Codex seed prompt

```text
Execute PHASE=P03 from doc/PLAN.md.
Use the canonical services from P02 and composition from P01.
Complete the first incomplete P03 slice.
Do not reimplement accounting rules in JavaFX.
```

---

# P04 — Persistent budgeting

**Selector:** `PHASE=P04`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Replace sidecar budget targets with normalized H2-backed budget plans and lines.

## Required inspection

- `BudgetCategory`;
- current budget panels;
- dashboard budget projection;
- `UiWorkspaceDataStore`;
- `BudgetTargetPersistence`;
- migrations/tests.

## Slices

### P04-S1 — Budget model and migration

Add only missing concepts, such as:

- `BudgetPlan`;
- `BudgetLine`;
- version/status metadata.

Define fiscal year, category, optional fund, optional period, amount, notes, uniqueness, and history.

### P04-S2 — Budget services

Implement create, edit draft, validate, activate, archive, select active version, and calculate actual/variance.

Activation selects the comparison version; it is not an approval workflow.

### P04-S3 — Budget UI and dashboard

Convert Budget Editor, Budget vs Actual, Dashboard Budget Performance, and YTD comparisons to genuine services.

Remove sidecar/static budget persistence.

## Validation

Migration, uniqueness, `BigDecimal`, atomic save, historical version, actual calculation, neutral no-budget state, and dashboard tests.

## Exit gate

Every displayed/stored budget value comes from normalized H2 data or authoritative accounting projections.

## Codex seed prompt

```text
Execute PHASE=P04 from doc/PLAN.md.
Replace the first incomplete sidecar-backed budget slice with normalized H2 models and services.
Preserve BudgetCategory as distinct from Account and Activity.
```

---

# P05 — Bank import and statement-line persistence

**Selector:** `PHASE=P05`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Persist accepted bank statement lines and import-job facts safely and idempotently.

## Required reading

- `doc/import/import-review-workflow.md`;
- SCLX/import documentation produced by P00;
- ledger authority.

## Slices

### P05-S1 — Import batch/line model

Add or complete:

- `BankImportBatch`;
- `BankStatementLine`;
- `ImportIssue`;
- accepted import-job metadata.

### P05-S2 — Normalization and duplicate detection

- stable external IDs;
- deterministic fingerprint fallback;
- separate transaction/posting dates;
- exact duplicate prevention;
- probable duplicate warning;
- row-level errors.

### P05-S3 — Review and acceptance workflow

- in-memory staging;
- valid and invalid rows together;
- edit/accept/reject/match;
- discard/save-copy/cancel;
- explicit acceptance through authoritative services;
- persistence survives restart.

### P05-S4 — SCLX hardening

- stable SCLX transaction IDs reuse existing records;
- safe generated IDs;
- zero-value/non-posting annotations skipped;
- raw SCLX documents not retained;
- compact summaries allowed;
- repeated import does not duplicate accounting records.

## Forbidden in P05

- automatic accounting transactions without user acceptance;
- raw source documents as accounting truth;
- static bank-transaction store as persistence.

## Validation

Idempotency, duplicates, malformed rows, rollback, accepted/rejected outcomes, restart persistence, and SCLX regression tests.

## Exit gate

Accepted imported statement/activity data is durable, idempotent, and connected to the canonical transaction architecture.

## Codex seed prompt

```text
Execute PHASE=P05 from doc/PLAN.md.
Follow the import-review workflow.
Complete the first incomplete P05 slice.
Preserve in-memory review staging but persist accepted records and durable job facts in H2.
```

---

# P06 — Bank reconciliation

**Selector:** `PHASE=P06`
**Status:** BLOCKED
**Depends on:** P05

## Objective

Implement genuine reconciliation between bank statement lines and authoritative accounting transactions.

## Slices

### P06-S1 — Reconciliation model and services

Add or complete sessions, matches, states, notes, and history without duplicating existing models.

### P06-S2 — Matching workspace

- account/statement selection;
- statement lines;
- ledger candidates;
- match/unmatch;
- partial/split matching only where documented;
- outstanding checks;
- deposits in transit.

### P06-S3 — Completion and reopening

- adjusted balances;
- difference;
- zero-difference default;
- documented override;
- complete;
- reopen after warning;
- protect included transactions.

### P06-S4 — Dashboard/report projections

Update reconciliation status, pending work, cash differences, inspector, and reports.

## Forbidden in P06

- Approve/Reject actions;
- modifying transaction amounts through matching;
- deleting completed history.

## Validation

Matching, unmatched items, outstanding/deposit timing, difference rules, override audit, completion, reopening, and transaction protection.

## Exit gate

Reconciliation is genuine, auditable, and reflected consistently across dashboard, inspector, and reports.

## Codex seed prompt

```text
Execute PHASE=P06 from doc/PLAN.md.
Use persisted bank lines from P05 and canonical transactions from P02.
Implement the first incomplete reconciliation slice without approval semantics.
```

---

# P07 — Schedules and open items

**Selector:** `PHASE=P07`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Use one normalized architecture for receivables, prepaid expenses, payables, deferred revenue, other assets, and other liabilities.

## Slices

### P07-S1 — Authority and model

Reconcile existing open-item snapshot/transition tables and schedule concepts. Avoid parallel state machines.

### P07-S2 — Open-item services

- source transaction-line link;
- original/open amounts;
- reference;
- dates;
- counterparty;
- notes;
- state transitions.

### P07-S3 — Settlement and reversal

- partial settlement;
- full settlement;
- reversal effects;
- retained history;
- reconciliation/period interactions.

### P07-S4 — UI, aging, dashboard

- schedule editor/query;
- aging;
- drill-through;
- Dashboard Open Items counts/amounts.

## Validation

Source eligibility, amount consistency, partial/full settlement, reversal, aging, history, and dashboard tests.

## Exit gate

All schedule/open-item panels use one H2-backed state model and genuine services.

## Codex seed prompt

```text
Execute PHASE=P07 from doc/PLAN.md.
First determine open-item authority.
Complete one normalized slice and do not create separate state machines for each schedule type.
```

---

# P08 — Fixed assets and depreciation

**Selector:** `PHASE=P08`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Replace runbook persistence with genuine fixed-asset records and accounting-generating depreciation.

## Slices

### P08-S1 — Fixed-asset model

Asset, acquisition link, cost, dates, salvage, useful life, method, status, notes.

### P08-S2 — Depreciation model and calculation

Straight-line first; run, entry, preview, duplicate-period prevention.

### P08-S3 — Completion and accounting

Completed run creates a balanced authoritative transaction through P02 services.

### P08-S4 — Asset UI and disposal

Register, inspect, run, retire, dispose, retain history, update reports.

## Validation

Calculation, salvage floor, first/last period policy, duplicate prevention, generated transaction balance, disposal, and migration tests.

## Exit gate

Assets and depreciation survive restart and all financial effects are authoritative transactions.

## Codex seed prompt

```text
Execute PHASE=P08 from doc/PLAN.md.
Replace the first incomplete asset/depreciation runbook slice with normalized H2 data and canonical accounting operations.
```

---

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Make Inventory the single H2-backed home for equipment, regalia, supplies, consumables, and other items.

## Slices

### P09-S1 — Item and movement model

Item type, name, quantity, unit, value, custodian, dates, status, notes; movement and count records.

### P09-S2 — Movement services

Acquisition, transfer, adjustment, count, loss, consumption, disposal, and financial links.

### P09-S3 — Inventory UI

All items and filtered Supplies/durable/regalia/custodian views.

### P09-S4 — Cleanup and reports

Remove runbook persistence; add inventory reports and audit history.

## Validation

Quantity rules, movement history, SUPPLY as subtype/filter, stable links, restart persistence, and migration tests.

## Exit gate

No inventory or supplies operation depends on runbook or sidecar persistence.

## Codex seed prompt

```text
Execute PHASE=P09 from doc/PLAN.md.
Implement the first incomplete Inventory slice.
Supplies is a subtype/filter within Inventory, never a separate persistence subsystem.
```

---

# P10 — Period close, reopening, notes, and audit history

**Selector:** `PHASE=P10`
**Status:** BLOCKED
**Depends on:** P02, P06

## Objective

Replace approval-oriented period controls with documented close/reopen policy, notes, and factual audit history.

## Slices

### P10-S1 — Pre-close checks and service

Validate transaction, period, reconciliation, open-item, depreciation, and configured conditions.

### P10-S2 — Close and reopen

Default warning/reopen; optional reason; optional adjustment workflow; documented reopening scope.

### P10-S3 — Notes and audit

Persist period notes and factual material-change events.

### P10-S4 — UI cleanup

Remove Approve/Reject, approval counts, and formal oversight assumptions. Present Audit History/Change History.

## Validation

Close/reclose, reopen policies, audit, transaction service enforcement, reconciliation protection, and notes.

## Exit gate

Period state is genuine, audited, and consistently enforced without approval semantics.

## Codex seed prompt

```text
Execute PHASE=P10 from doc/PLAN.md.
Follow the governing period and correction documents.
Complete the first incomplete close/reopen/audit slice and remove approval semantics only within this phase scope.
```

---

# P11 — Report Library

**Selector:** `PHASE=P11`
**Status:** BLOCKED
**Depends on:** P02, P04, P06, P07, P08, P09

## Objective

Provide genuine read-only reports through one Report Library.

## Required reports

- Trial Balance;
- General Ledger Detail;
- Balance Statement;
- Income Statement;
- Workbook Summary;
- Transactions List;
- All Checks/Transfers;
- Fund Transfers;
- Budget vs Actual;
- Reconciliation;
- Open-item aging;
- Fixed assets/depreciation;
- Inventory where required.

## Slices

### P11-S1 — Report architecture and shared projections

Semantic embedded definitions, value providers, filters, preview contract.

### P11-S2 — Core financial reports

Trial Balance, Balance Statement, Income Statement, Ledger Detail.

### P11-S3 — Operational and workbook-derived reports

Budget, reconciliation, open items, checks/transfers, fund transfers, assets, inventory, workbook summary.

### P11-S4 — Exports

XLSX, PDF, text/CSV as appropriate; export smoke tests and job integration.

## Forbidden in P11

- SQL in panels;
- accounting formulas in JavaFX;
- runtime dependence on an external workbook;
- raw worksheet-cell identity as accounting truth.

## Validation

Balance, date ranges, fund/company filters, closed-period cutoff, export content, and no fictional fallbacks.

## Exit gate

Every report is database-backed, read-only, reproducible, and available through `REPORT_LIBRARY`.

## Codex seed prompt

```text
Execute PHASE=P11 from doc/PLAN.md.
Use authoritative services from prerequisite phases.
Complete the first incomplete report slice without duplicating accounting calculations in panels or templates.
```

---

# P12 — Administration, company lifecycle, and preferences

**Selector:** `PHASE=P12`
**Status:** BLOCKED
**Depends on:** P01, P02

## Objective

Complete master-data administration, company/database operations, and persisted preferences.

## Slices

### P12-S1 — Master data

Chart of Accounts, Funds, Budget Categories, Counterparties, Merchants, Activities.

Rules: stable IDs, duplicate-code prevention, deactivation instead of casual deletion, aliases where needed.

### P12-S2 — Company identity and defaults

Company configuration, required defaults, organization identity from selected database.

### P12-S3 — Database lifecycle

Create, select, switch, explicit sample database, backup/export, restore/import, repair/recover, and deliberate destroy with strong confirmation.

Never delete/recreate a user database as migration repair.

### P12-S4 — Preferences

Closed-period policy, correction policy, default company, active-period behavior, display/divider preferences, duplicate thresholds where appropriate.

## Validation

CRUD/deactivation, reference protection, aliases, preference persistence, atomic switch, recovery, backup/restore, and no user-data loss.

## Exit gate

Administration and database/company lifecycle operations are genuine and safe.

## Codex seed prompt

```text
Execute PHASE=P12 from doc/PLAN.md.
Complete the first incomplete administration/company/preferences slice.
Use stable IDs and nondestructive database lifecycle rules.
```

---

# P13 — Imports, exports, jobs, and diagnostics

**Selector:** `PHASE=P13`
**Status:** BLOCKED
**Depends on:** P02, P05, P12

## Objective

Persist durable job facts, complete supported imports/exports, and provide safe diagnostics.

## Slices

### P13-S1 — Job and issue persistence

Import batch/job metadata, export job metadata, row-level issues where appropriate, retry rules.

### P13-S2 — Accounting/master-data exchange

SCLX, Chart of Accounts, Funds, Budget Categories, and other approved formats.

### P13-S3 — Database and report exchange

Backup/export, restore/import, report export history.

### P13-S4 — Diagnostics

Migration status, database health, backup status, safe diagnostic export without secrets or unnecessary paths.

## Scope rule

Review staging may remain in memory; durable accepted results and job facts belong in H2.

## Validation

Repeated import idempotency, errors, retry safety, export scope, backup/restore, diagnostics privacy, and migration health.

## Exit gate

All supported import/export/diagnostic commands have genuine behavior and durable outcomes where required.

## Codex seed prompt

```text
Execute PHASE=P13 from doc/PLAN.md.
Complete the first incomplete job/import/export/diagnostic slice.
Keep review staging distinct from durable accepted data.
```

---

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** BLOCKED
**Depends on:** P03–P13

## Objective

Remove remaining simulated behavior and prove the complete application across persistence, accounting, UI, reports, and database lifecycle.

## Slices

### P14-S1 — Simulation cleanup

Remove operational use of:

- `UiWorkspaceDataStore`;
- `RunbookPersistence`;
- `BudgetTargetPersistence`;
- sample/demo/mock production data;
- label-only success actions;
- obsolete approval UI;
- duplicate services/frameworks.

### P14-S2 — End-to-end automated scenarios

Cover:

1. create company database;
2. populate defaults;
3. create master data;
4. enter balanced transaction;
5. correct/reverse/replace;
6. create active budget;
7. import bank lines;
8. reconcile;
9. create/settle open item;
10. register asset and depreciate;
11. move inventory/supplies;
12. close/reopen period;
13. generate/export reports;
14. backup/restore;
15. restart and verify persistence.

### P14-S3 — Visual/accessibility closure

Validate:

- 800×700;
- 1000×760;
- 1180×760;
- 1400×860;
- 100%, 125%, 150% scaling;
- no clipping/overlap;
- usable dividers and scrolling;
- readable tables/wrapping;
- visible validation;
- icons plus non-color meaning;
- laptop startup.

### P14-S4 — Documentation and release closure

Update README, all governing docs, operation matrix, migration notes, user workflows, and final plan state.

## Validation

Full `mvn clean verify`, GitHub checks, final diff, migration upgrade matrix, desktop screenshots, and manual scenario review.

## Exit gate

Every visible production operation is genuine or deliberately disabled, all authoritative data survives restart, and no parallel or simulated architecture remains.

## Codex seed prompt

```text
Execute PHASE=P14 from doc/PLAN.md.
Use the operation matrix as the closure checklist.
Complete the first incomplete hardening slice.
Do not mark the program complete until automated, migration, GitHub, and desktop visual gates all pass.
```

## 8. Cross-cutting validation matrix

Every phase adds applicable tests.

### Unit

- accounting invariants;
- validation;
- state transitions;
- calculations;
- duplicate detection;
- pure layout/command policies.

### Service

- explicit transactions;
- rollback;
- correction;
- closed periods;
- reconciliation protection;
- audit history.

### Repository

- in-memory H2;
- generated IDs;
- foreign keys;
- indexes;
- constraints;
- delete behavior;
- sorting and paging.

### Migration

- upgrade existing schemas;
- preserve data;
- recover known Flyway-history defects safely;
- never recreate a user database;
- regression for each reproduced failure.

### JavaFX

- minimum/preferred child sizes;
- viewport and scrolling;
- divider behavior;
- sidebar collapse;
- dirty prompts;
- command routing;
- ID-backed cells;
- context refresh.

### Reports/exports

- balanced trial balance;
- balance-sheet equation;
- income range;
- fund filters;
- budget/reconciliation/open-item results;
- XLSX/PDF/text smoke tests;
- no fictional fallback.

## 9. Pull-request completion checklist

Before a PR is ready:

- [ ] Selected phase/slice is recorded.
- [ ] Branch started from the then-current `main`.
- [ ] Scope is one coherent slice.
- [ ] Relevant `doc/` files and this plan are updated.
- [ ] Final diff inspected.
- [ ] No unintended/generated/user-data files changed.
- [ ] No placeholders or swallowed exceptions.
- [ ] No SQL in JavaFX panels.
- [ ] No accounting policy in repositories.
- [ ] No JavaFX controls in models.
- [ ] No sidecar/static store used as authoritative persistence.
- [ ] New migrations are nondestructive.
- [ ] Applicable unit/service/H2/migration/regression/layout tests exist.
- [ ] `mvn clean verify` passes.
- [ ] GitHub confirms required checks.
- [ ] PR description records actual validation.
- [ ] Required desktop visual check is complete.
- [ ] Branch/PR/head/test/next-action handoff is recorded here.

## 10. Current next action

Execute:

```text
PHASE=P02
SLICE=P02-S1
```

Create `doc/accounting/ledger-authority.md`, choose one canonical writable ledger, and prevent two independently writable ledgers before adding transaction writes.
