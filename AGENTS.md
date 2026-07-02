# AGENTS.md

## Purpose

This file governs work in the `benbaron/sca-jakarta-h2` repository.

The repository is the production implementation of the SCA Bookkeeping Program. Work must advance a single coherent JavaFX/H2 application rather than create prototypes, parallel persistence systems, or disconnected panels.

These instructions apply to the entire repository unless a more specific `AGENTS.md` exists in a subdirectory. A nested file may refine local practices, but it must not contradict this file or the governing documents under `doc/`.

## Mandatory first step

Before designing, editing, or reviewing code:

1. Open and read [`doc/PLAN.md`](doc/PLAN.md).
2. Follow the document map in `doc/PLAN.md` and read every document relevant to the requested slice.
3. Inspect current `main`, including the implementation, migrations, tests, and recently merged pull requests that affect the slice.
4. Compare the requested change with the current plan and documentation.
5. If the request conflicts with current implementation or documentation, identify the conflict and resolve it deliberately. Do not silently introduce another architecture.

Do not begin implementation from memory, an obsolete branch, an old pull request, or a donor repository.

## Sources of truth

Use the following order when resolving ambiguity:

1. The user's current explicit request and project instructions.
2. This `AGENTS.md`.
3. `doc/PLAN.md`.
4. Focused governing documents under `doc/`.
5. Current `main` as the source of truth for what is actually implemented.
6. Current migrations and tests as evidence of persisted behavior and enforced contracts.
7. Historical pull requests, old branches, experiments, and donor repositories as reference only.

When two authoritative sources conflict:

- stop the affected design decision;
- describe the conflict in the pull request or planning document;
- select one direction explicitly;
- update the governing documentation in the same slice;
- do not preserve both behaviors by creating parallel code paths.

## Repository roles

The authoritative production repository is:

```text
https://github.com/benbaron/sca-jakarta-h2
```

The package namespace remains:

```text
org.nonprofitbookkeeping
```

`benbaron/npbk-javafx-h2` and any `experiments/` modules are donor or visual-reference sources only. They may supply useful behavior, layouts, report definitions, or test cases, but they do not replace this repository's architecture or schema authority.

Adapt useful donor behavior into the established model, services, repositories, panel contracts, and workspace shell. Never copy a donor database bootstrap or create a second application beside the production application.

## Documentation policy

The `doc/` directory contains the evolving production specification.

### Required behavior

- Start every substantial task with `doc/PLAN.md`.
- Update `doc/PLAN.md` when a slice begins, changes scope, reveals a prerequisite, or completes.
- Put detailed architecture, accounting, schema, UI, migration, workflow, and testing decisions in focused Markdown files under `doc/`.
- Update governing documentation in the same pull request as the implementation it describes.
- Link new documents from `doc/PLAN.md`.
- Record unresolved decisions explicitly; do not hide them in code comments.
- Prefer concise Javadoc for public APIs and durable design rationale in `doc/`.

### Directory convention

Use these areas where applicable:

```text
doc/
  PLAN.md
  accounting/
  architecture/
  banking/
  database/
  import/
  reporting/
  testing/
  ui/
  workflow/
```

Do not create new planning documents under `docs/`. Existing files under `docs/` are legacy references until their still-valid decisions are incorporated into `doc/`.

## Development workflow

For every implementation slice:

1. Fetch current `main`.
2. Confirm there is no active branch or pull request that already owns the same slice.
3. Create a focused branch from current `main`.
4. Implement one coherent, mergeable slice.
5. Commit logically grouped changes.
6. Open one focused pull request.
7. Continue updating that pull request while the slice remains active.
8. Do not continue committing to a branch after its pull request is merged.
9. If a merged change needs repair, start a new corrective branch from the new current `main`.
10. Inspect the final diff and verify that no unintended files changed.
11. Run or inspect `mvn clean verify` through GitHub Actions.
12. Read all failing logs and correct the implementation.
13. Update the pull request description with the actual validation results.
14. Mark the pull request ready only after checks pass and any required desktop visual validation is complete.

Never claim that code was pushed, tested, fixed, or ready to merge unless GitHub confirms it.

## Technology and project structure

Use:

- Java 17 or later;
- JavaFX;
- H2;
- Maven;
- JUnit 5;
- the existing JPA/Hibernate persistence model;
- Flyway migrations;
- an Eclipse-compatible Maven project structure.

Use Allman brace style.

Prefer descriptive names, small focused types, constructor injection, immutable command/query DTOs where practical, and Javadoc for important public APIs.

Do not add another dependency-injection framework or UI framework without an explicit documented decision.

## Architectural boundaries

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
- Domain entities and DTOs contain no JavaFX controls or JavaFX properties.
- Cell factories and event handlers do not calculate authoritative accounting values.
- Query services return projection DTOs intended for a screen or report.
- Command services perform validation and write operations inside explicit database transactions.
- Repositories use prepared statements or JPA parameters.
- Failures roll back completely.
- Exceptions are not swallowed.
- Placeholder implementations and unexplained TODOs are not acceptable.
- Static or sidecar stores are not authoritative accounting persistence.

Extend the established architecture. Do not create parallel service registries, ledgers, budget stores, import stores, or panel frameworks.

## Accounting rules

The H2 database is authoritative for accounting data.

### Double entry

Every authoritative accounting transaction must:

- contain a header and at least two meaningful lines;
- have total debits equal total credits;
- use `BigDecimal` for all monetary values;
- contain either a debit or a credit on a line, never both;
- reference accounts, funds, budget categories, and other dimensions by stable database ID;
- reject zero-value accounting lines;
- preserve meaningful history.

Presentation may derive debit and credit from the canonical signed amount only where the governing accounting model explicitly defines that conversion.

### Transaction lifecycle

The application does not need a separate user-facing posting or approval workflow merely to make a transaction authoritative.

The governing transaction and correction rules are defined in:

- `doc/accounting/transaction-lifecycle.md`;
- `doc/accounting/period-and-correction-policy.md`;
- any later document linked from `doc/PLAN.md` that deliberately supersedes them.

Retained accounting history must not be silently rewritten. Reversal, replacement, audit history, reconciliation protection, and any narrowly permitted edit or deletion behavior must be implemented through one documented policy and one transaction service.

Do not invent `POSTED`, `APPROVED`, or `REJECTED` states without an explicit change to the governing documents.

### Closed periods

Closed-period behavior must follow the documented policy. The default user experience is warning-based reopening, with stricter reason or adjustment workflows available only where configured.

A closed period is never bypassed silently. Reopening and adjustment actions are audited.

### Nonprofit and fund accounting

Support:

- unrestricted, restricted, and designated funds;
- budget categories separate from general-ledger accounts and activities;
- fund transfers;
- bank accounts and reconciliation;
- receivables;
- prepaid expenses;
- payables;
- deferred revenue;
- fixed assets and depreciation;
- inventory and supplies;
- assets, liabilities, income, expenses, and net assets.

Financial policies and committee review are organizational controls and reporting context. Do not invent a formal in-application approval role unless the governing plan explicitly adds one.

### Scope exclusions

Unless `doc/PLAN.md` is deliberately amended:

- transaction approvals are out of scope;
- formal approval/rejection queues are out of scope;
- attachments and document storage are out of scope;
- a separate posting workflow is out of scope;
- fictional production data is forbidden.

Notes and audit history are in scope.

## Database and migration rules

Use a normalized H2 schema with:

- primary and foreign keys;
- deliberate nullability;
- deliberate delete behavior;
- check and unique constraints;
- indexes for joins, searches, sorting, and reporting;
- generated identifiers;
- explicit transactions;
- rollback on failure.

Use file-mode H2 for the application and in-memory H2 for tests.

Every schema change must use a new, versioned, nondestructive Flyway migration.

Never:

- edit a migration that may already have run in a user database;
- delete or recreate a user database to resolve a migration problem;
- drop user tables as a recovery shortcut;
- overwrite user data to make a test pass;
- infer schema state only from Hibernate entities without inspecting migrations.

Migration and recovery logic must preserve existing data and must have regression tests for every reproduced failure.

## JavaFX and workspace rules

The production interface follows the approved compact white-and-blue reference design.

The main workspace includes:

- menu bar;
- toolbar;
- collapsible and resizable left navigation;
- tabbed center workspace;
- collapsible and resizable right inspector;
- visible draggable dividers;
- bottom status bar;
- horizontal and vertical scrolling where required;
- external CSS;
- icons and visual status cues that do not rely on color alone.

The application opens at a laptop-friendly size. The center content must never render beneath either sidebar.

Avoid hard-coded panel minimum widths that force content outside the viewport. Use:

- responsive `GridPane` constraints;
- wrapping labels;
- suitable `TableView` resize policies;
- zero or low center minimum widths;
- scroll panes only where the child genuinely needs scrolling;
- explicit empty, loading, success, warning, and error states.

Spreadsheet-like editors must provide:

- keyboard navigation;
- ID-backed combo-box cells;
- visible validation;
- dirty-state indication;
- add and remove row operations;
- immediate totals and recalculation;
- prevention of invalid authoritative writes.

Every visible command must either perform a genuine operation, navigate to a genuine workflow, or be disabled with an explanation. A label-only success message is not an implementation.

## Service and model creation

Before adding a model or service:

1. Search current entities, repositories, services, migrations, and tests for an existing concept.
2. Check `doc/PLAN.md` and focused documents for the intended ownership and lifecycle.
3. Reuse and extend the established concept where possible.
4. If overlapping models exist, select one authority and document the migration path.
5. Do not make two independently writable representations of the same accounting fact.

Use separate write and read models when beneficial:

- command DTOs for validated changes;
- domain services for accounting and workflow rules;
- repository interfaces for persistence;
- projection DTOs for dashboards, tables, inspectors, and reports.

## Testing requirements

Every material change includes appropriate tests.

Use:

- unit tests for accounting and validation rules;
- service tests for transactions and workflow behavior;
- in-memory H2 repository tests;
- migration upgrade and recovery tests;
- regression tests for reproduced defects;
- JavaFX policy and headless layout tests;
- report and export smoke tests where applicable.

A geometry test must consider:

- child minimum and preferred sizes;
- viewport dimensions;
- scrolling behavior;
- divider positions and movement;
- collapsed and expanded sidebars;
- content constraints;
- display scaling assumptions.

Do not test only the outer rectangles of sibling panels.

## Definition of done

A slice is complete only when:

- the requested behavior is genuine and database-backed where applicable;
- no parallel architecture was added;
- governing documentation is current;
- accounting and migration invariants are tested;
- no unintended files changed;
- GitHub Actions confirms `mvn clean verify`;
- failures and warnings have been reviewed;
- the pull request description records actual validation;
- required desktop visual checks are complete;
- `doc/PLAN.md` records the resulting status and next dependency.

## Agent handoff

Before ending an unfinished task, update or provide a precise handoff containing:

- branch and pull request;
- current head commit;
- completed work;
- remaining work;
- test status;
- known failures;
- governing documents changed or still requiring changes;
- the next exact action from `doc/PLAN.md`.

Do not leave the next agent to reconstruct the state from chat history.
