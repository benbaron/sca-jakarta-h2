# AGENTS.md

## 1. Purpose and scope

This file is the repository-wide operating contract for Codex and other coding agents working in:

```text
https://github.com/benbaron/sca-jakarta-h2
```

The repository is the production implementation of the SCA Bookkeeping Program. Work must advance one coherent JavaFX/H2 application. Do not create a second application, a parallel persistence model, a second ledger, or disconnected prototype panels.

These instructions apply to the entire repository unless a more specific `AGENTS.md` exists below the working directory. A nested file may narrow local practices, but it may not contradict this file or the governing documents under `doc/`.

## 2. Codex container bootstrap protocol

Codex must perform this protocol immediately after the task container starts and before editing any file.

### 2.1 Establish repository state

1. Confirm that the working directory is the `sca-jakarta-h2` repository.
2. Inspect all uncommitted and untracked files.
3. Never discard existing work with `git reset --hard`, `git clean -fd`, checkout-overwrite, or equivalent destructive commands.
4. If the worktree contains unexplained changes, stop and report them before proceeding.
5. Treat current `origin/main` as the implementation source of truth.
6. If Codex starts on an existing task branch, determine whether that branch owns the selected phase and whether it is based on current `origin/main`.
7. If Codex starts on `main` and the selected phase is ready, create a focused branch named:

```text
codex/<phase-id>-<slice-id>-<short-description>
```

Do not reuse a branch whose pull request has already merged.

### 2.2 Read the control documents

Read, in this order:

1. `AGENTS.md`
2. `doc/PLAN.md`
3. every document listed by the selected phase under **Required reading**
4. every current implementation, migration, and test file listed by the selected phase under **Required inspection**

Do not begin from chat memory, an old pull request, an obsolete branch, a donor repository, or an experiment module.

## 3. Phase selection contract

Codex must execute one selected plan phase and one selected slice at a time.

### 3.1 Selection precedence

Determine `SELECTED_PHASE` and `SELECTED_SLICE` using this order:

1. An explicit phase and slice in the user/developer task, for example:

   ```text
   PHASE=P03
   SLICE=P03-S2
   ```

2. An explicit phase in the task. When only a phase is given, select the first incomplete, unblocked slice in that phase.
3. A task that clearly names work owned by exactly one phase. State the mapping before implementation.
4. When the task says only “continue,” “proceed,” or equivalent, use the `active_phase` and `active_slice` in the front matter of `doc/PLAN.md`.
5. If no unambiguous phase can be selected, stop and ask which phase to execute.

The explicit task phase may override `active_phase`, but Codex must still verify its prerequisites.

### 3.2 Phase status rules

Valid statuses are:

```text
BLOCKED
READY
IN_PROGRESS
VERIFYING
DONE
```

Interpret them as follows:

- `BLOCKED`: do not implement. Report the unmet prerequisite.
- `READY`: start the slice from current `main`.
- `IN_PROGRESS`: resume the branch and pull request recorded in `doc/PLAN.md`.
- `VERIFYING`: finish tests, diff review, documentation, and pull-request validation for the recorded branch.
- `DONE`: do not reimplement. A new request affecting this phase is a corrective slice from current `main`.

A phase is `DONE` only when the behavior is merged into current `main`, the governing documentation is current, and required validation passed. Local code or an open pull request is not `DONE`.

### 3.3 Scope fence

After selecting the phase:

1. Read only enough adjacent-phase material to understand dependencies.
2. Do not implement later-phase features.
3. Do not opportunistically rewrite unrelated panels.
4. Do not add “temporary” parallel models or services for a later phase.
5. A small prerequisite defect may be repaired in the same pull request only when:
   - it directly blocks the selected slice;
   - the repair is narrowly scoped;
   - the plan and PR explain it;
   - tests cover it.
6. If the prerequisite is a substantial independent slice, mark the selected phase blocked and stop.

## 4. Plan advancement protocol

`doc/PLAN.md` is an execution ledger, not merely a wish list.

### At task start

Codex must:

1. verify the plan front matter against current `main`;
2. verify any recorded branch and pull request;
3. update the selected phase to `IN_PROGRESS` when beginning a new slice;
4. record the branch and pull request when they exist;
5. record newly discovered prerequisites or conflicts.

### Before ending an implementation run

Codex must update `doc/PLAN.md` with:

- actual phase and slice status;
- branch name;
- pull-request number or URL;
- current head commit;
- completed deliverables;
- remaining deliverables;
- test status;
- known failures;
- next exact action.

### Advancing to another phase

Do not advance `active_phase` merely because local tests pass.

Advance only after confirming that the previous phase or required slice is merged into current `main`. Then:

1. mark the completed slice and phase as `DONE`;
2. clear obsolete branch/PR fields;
3. activate the first unblocked dependent slice;
4. set its status to `READY`;
5. update `next_action`.

## 5. Execution loop for the selected phase

### Step 1 — Inspect before design

Inspect:

- current entities and DTOs;
- repositories and query services;
- application/domain services;
- JavaFX panels and workspace wiring;
- Flyway migrations;
- existing tests;
- recent merged PRs affecting the selected area;
- donor/experiment code only when the phase explicitly lists it as reference.

Identify:

- current behavior;
- missing behavior;
- duplicate models;
- sidecar or static persistence;
- placeholders;
- architecture conflicts;
- migration risks;
- test gaps.
Record any material conflict in the governing `doc/` file before implementing an alternative.

### Step 2 — Establish baseline

For code phases, run the least expensive useful baseline before editing, normally:

```bash
mvn -DskipTests compile
```

For migration, service, or repository work, also run the most relevant existing tests.

If baseline fails for a defect unrelated to the selected phase, report it. Repair it only under the scope-fence rule.

### Step 3 — Implement one coherent slice

A slice must be mergeable and vertically coherent. Where applicable it includes:

- model/entity changes;
- a new nondestructive migration;
- repository changes;
- domain/application services;
- query projections;
- JavaFX wiring;
- tests;
- documentation.

Do not deliver a visible enabled button before its real operation exists.

Design methodology and Donor Code:

https://github.com/benbaron/NonprofitAccounting.git is available as reference or a donor. 
Examine the donor codebase and consider imports or design choices from it when working in a new area of code.
Record any design choices made and add a slice to PLAN.md when a feature is discovered that is a candidate for a
mergeable and vertically coherent slice.

### Step 4 — Validate locally

Create focused tests to increase test coverage for the new slice's behaviors.

Run focused tests during development. Before completion run:

```bash
mvn clean verify
```

If a desktop UI cannot be launched in the container, run headless policy/layout tests and clearly record the required manual visual check. Do not claim visual validation that did not occur.

### Step 5 — Inspect the final result



### Step 6 — GitHub validation

Use the repository workflow to run or inspect `mvn clean verify`.

Read failing logs. Correct every failure. Update the pull-request description with actual results. Never claim that GitHub confirmed a result when it did not.

### Step 7 - User testing

Add user testing notes stating:
- User visible changes.
- Manual testing for the user to perform.

## 6. Sources of truth

Resolve ambiguity in this order:

1. the current explicit user/developer request;
2. this `AGENTS.md`;
3. `doc/PLAN.md`, including the selected phase contract;
4. focused governing documents under `doc/`;
5. current `main` for actual implementation state;
6. migrations and tests as evidence of persisted/enforced behavior;
7. merged PR history;
8. donor repositories, experiments, and old branches as reference only.

When authoritative sources conflict:

- stop the affected design decision;
- describe the conflict;
- select one direction deliberately;
- update the governing documentation in the same slice;
- do not preserve both directions with parallel code.

## 7. Repository and documentation rules

The package namespace remains:

```text
org.nonprofitbookkeeping
```
The `doc/` directory is the production specification. Detailed architecture, accounting, database, UI, workflow, reporting, migration, and testing decisions belong there.

Required documentation behavior:

- start with `doc/PLAN.md`;
- update the plan in every substantial slice;
- link every new governing document from the plan;
- do not hide unresolved product decisions in comments;
- use concise Javadoc for important public APIs;
- do not create new planning documents under legacy `docs/`.

## 8. Non-negotiable technical rules

Use:

- Java 17 or later;
- JavaFX;
- H2;
- Maven;
- JUnit 5;
- the established JPA/Hibernate persistence model;
- Flyway;
- an Eclipse-compatible Maven layout;
- Allman brace style.

Prefer constructor injection, focused package boundaries, immutable command/query DTOs where practical, and descriptive names.

Do not introduce another UI framework or dependency-injection framework without an explicit documented decision.

## 9. Architecture boundaries

The intended dependency direction is:

```text
JavaFX view
    -> application service or query service
        -> domain rules
            -> repository interface
                -> JPA/JDBC implementation
                    -> H2
```

Required boundaries:

- JavaFX panels contain no SQL.
- Repositories contain no accounting policy.
- Entities and domain DTOs contain no JavaFX controls/properties.
- Cell factories do not calculate authoritative accounting values.
- Query services return screen/report projections.
- Command services validate and write inside explicit database transactions.
- Repositories use JPA parameters or prepared statements.
- Failures roll back completely.
- Exceptions are not swallowed.
- No unexplained TODOs or placeholder implementations.
- Static collections and sidecar files are not authoritative accounting persistence.
- Existing architecture is extended, not paralleled.

## 10. Accounting rules

H2 is authoritative for accounting and operational data accepted into the application.

Every authoritative accounting transaction must:

- have a header and at least two meaningful lines;
- balance total debits and credits;
- use `BigDecimal`;
- contain either debit or credit on an input line, never both;
- reference master data by stable database IDs;
- reject zero-value accounting lines;
- preserve meaningful history.

The canonical transaction and correction behavior is governed by the selected phase documents. Do not invent a second writable ledger.

Do not introduce user-facing `POSTED`, `APPROVED`, or `REJECTED` workflows unless the plan is deliberately amended.

Closed periods are never bypassed silently. Reopening, adjustment, correction, import acceptance, reconciliation, and material changes are audited as required by governing documents.

Support nonprofit and fund accounting, including:

- unrestricted, restricted, and designated funds;
- budget categories separate from accounts and activities;
- fund transfers;
- banking and reconciliation;
- receivables and payables;
- prepaid expenses and deferred revenue;
- fixed assets and depreciation;
- inventory and supplies;
- assets, liabilities, income, expenses, and net assets.

Unless the plan is deliberately amended:

- approval queues are out of scope;
- formal in-application oversight roles are out of scope;
- attachments are out of scope;
- a separate posting workflow is out of scope;
- fictional production data is forbidden.

Notes and factual audit history are in scope.

## 11. Database and migration rules

Use normalized H2 tables with deliberate:

- primary and foreign keys;
- nullability;
- delete behavior;
- check and unique constraints;
- indexes;
- generated identifiers.

Use file-mode H2 in production and in-memory H2 in tests.

Every schema change uses a new nondestructive Flyway migration.

Never:

- edit an applied migration;
- delete or recreate a user database to fix migration history;
- drop user tables as a recovery shortcut;
- overwrite user data to satisfy tests;
- treat Hibernate entity generation as a substitute for migration review.

Migration and recovery changes require in-memory upgrade tests and a regression test for the reproduced failure.

## 12. JavaFX rules

Always: 
All UI designs must consult these UI design rules:
doc/interface-operation-matrix.md
doc/ui_design_rules.md
doc/ui/editor-guidelines.md
architecture/dashboard-composition.md

The approved compact white-and-blue reference is the visual direction.

The production workspace includes:

- menu;
- toolbar;
- collapsible/resizable left navigation;
- tabbed center;
- collapsible/resizable right inspector;
- visible draggable dividers;
- status bar;
- external CSS;
- appropriate scrolling;
- icons and textual/non-color status meaning.

The default window fits a laptop display. Center content never renders beneath a sidebar.

The top chrome active-period control selects an accounting period, not an arbitrary calendar day. The period start date is derived from the selected period and the Settings-defined period start day.

Avoid hard-coded panel minimum widths that force clipping. When default-size text or tabular content can be hidden by the available pane size, that pane portion must be independently resizable with a `SplitPane` divider and must expose both vertical and horizontal scrolling rather than relying on clipping, wrapping-only behavior, or an enlarged minimum width. Geometry tests must consider child minimum/preferred sizes, viewport behavior, both vertical and horizontal scrolling, divider movement, collapsed sidebars, and scaling.

Detailed table, money, date, and accounting-period display/editing rules are governed by `doc/ui_design_rules.md`. Preferences described there are per-company and saved with the active company.

Spreadsheet-like editors provide:

- keyboard navigation;
- ID-backed combo cells;
- visible validation;
- dirty state;
- add/remove rows;
- immediate totals;
- commit/preserve data on focus lost in a cell.
- tool tip on hover
- double click to select
- right mouse click to raise context-appropriate select
- prevention of invalid writes.

Every enabled command performs a genuine operation or navigation. Otherwise it is disabled with an explanation.

Every production function that creates or maintains a durable business record must expose a Delete action or an explicit, visible explanation for why the record cannot be deleted. Delete actions are real operations, not placeholders: they must route through the authoritative service boundary, confirm destructive effects when configured, respect reconciliation and closed-period protection, write required audit history, and roll back completely on failure. Transaction deletion follows the configured Settings -> Correction method: when the method is `DIRECT_EDIT`, Delete may remove the entered transaction through the transaction correction service after required checks; when the method is not `DIRECT_EDIT`, Delete must not hard-delete the transaction and must instead ask whether to auto-fill and perform a reversing entry using the active period as the default reversal date.

## 13. Testing requirements

Every material slice adds applicable:

- accounting/validation unit tests;
- service tests;
- in-memory H2 repository tests;
- migration tests;
- regression tests;
- JavaFX policy/layout tests;
- report/export smoke tests.

`mvn clean verify` is the final local and CI gate.

A test that checks only outer sibling rectangles is not a sufficient geometry test.

## 14. Definition of done

A selected slice is complete only when:

- its genuine behavior is implemented;
- no parallel architecture was introduced;
- governing documentation and plan status are current;
- accounting and migration invariants are tested;
- the final diff is intentional;
- `mvn clean verify` passes;
- GitHub confirms required checks;
- the PR description contains actual validation;
- required desktop visual validation is complete;
- `doc/PLAN.md` identifies the next action.

## 15. Required handoff

When a run ends before merge, leave a precise handoff in `doc/PLAN.md` and the PR description:

- selected phase and slice;
- branch;
- PR;
- head commit;
- completed work;
- remaining work;
- test status;
- known failures;
- documents changed;
- next exact command or code action.

Do not require the next Codex container to reconstruct state from chat history.
