# SCA Bookkeeping Program — Master Implementation Plan

## 1. Purpose

This is the starting document for all substantial work in `benbaron/sca-jakarta-h2`.

Read this file before changing code. Then read the focused documents linked below for the requested implementation slice.

This plan coordinates the build-out of the production JavaFX interface and the models, services, repositories, migrations, tests, and documentation required to make every visible workflow operate on genuine data.

## 2. How agents use this plan

For each task:

1. Read `AGENTS.md`.
2. Read this file completely.
3. Read the focused documents named by the selected workstream.
4. Fetch current `main`.
5. Inspect the existing implementation, migrations, tests, and recent merged pull requests.
6. Choose the first incomplete prerequisite slice relevant to the request.
7. Create one focused branch and one focused pull request.
8. Update this plan in that pull request:
   - note work started;
   - add discovered prerequisites or conflicts;
   - mark only work actually merged and verified;
   - link any new governing document.
9. Run `mvn clean verify` through GitHub Actions.
10. Record actual validation and the next action.

A checked item means the behavior is merged into current `main`, documented, and verified. Existing-looking code is not enough.

## 3. Repository and product direction

### Primary repository

```text
https://github.com/benbaron/sca-jakarta-h2
```

The package namespace remains:

```text
org.nonprofitbookkeeping
```

### Donor and experimental sources

`benbaron/npbk-javafx-h2` and `experiments/` may be used for:

- visual references;
- mature panel behavior;
- report definitions;
- workbook-oriented domain insights;
- test cases.

They are not schema or application authorities.

### Product goal

Produce one production-quality nonprofit and fund-accounting desktop application using:

- Java 17 or later;
- JavaFX;
- H2;
- JPA/Hibernate;
- Flyway;
- Maven;
- JUnit 5.

The application must provide genuine double-entry accounting, nonprofit fund accounting, banking, budgeting, schedules and open items, assets, inventory, reports, imports and exports, period controls, audit history, and safe database/company handling.

## 4. Governing document map

### Current governing documents

Read these when relevant:

- [`architecture/production-workspace.md`](architecture/production-workspace.md)
  - production shell, tabs, navigation, inspector, organization/database context;
- [`dashboard-workspace.md`](dashboard-workspace.md)
  - dashboard architecture, projections, responsive behavior, and derived values;
- [`accounting/transaction-lifecycle.md`](accounting/transaction-lifecycle.md)
  - transaction, import, reconciliation, notes, and audit lifecycle;
- [`accounting/period-and-correction-policy.md`](accounting/period-and-correction-policy.md)
  - active period, closed-period behavior, correction, and deletion policy;
- [`import/import-review-workflow.md`](import/import-review-workflow.md)
  - in-memory review staging, duplicate handling, and dispositions;
- [`testing/production-workspace-test-plan.md`](testing/production-workspace-test-plan.md)
  - accounting, repository, dashboard, import, JavaFX, geometry, and final validation.

### Legacy document to consolidate

`docs/union-application-migration-plan.md` contains still-relevant union-application decisions, including:

- this repository is primary;
- the JPA/Hibernate model is schema authority;
- donor features must be adapted;
- reports belong in `REPORT_LIBRARY`;
- Supplies belongs within Inventory;
- `BudgetCategory` is distinct from `Activity`.

Create a focused document under `doc/architecture/` containing the still-current decisions, link it here, and then treat the legacy `docs/` file as historical reference.

### Documents to create as work advances

Create these only when their slice begins, and link them here:

- `architecture/application-composition.md`
- `architecture/command-and-query-boundaries.md`
- `accounting/ledger-authority.md`
- `accounting/budget-model.md`
- `accounting/open-items-and-schedules.md`
- `banking/import-and-reconciliation.md`
- `database/schema-and-migration-policy.md`
- `database/database-lifecycle.md`
- `reporting/report-architecture.md`
- `ui/editor-guidelines.md`
- `testing/end-to-end-scenarios.md`
- `workflow/development-workflow.md`

Do not duplicate a decision in several documents. Link to the owner document instead.

## 5. Established product decisions

These decisions govern implementation unless deliberately superseded in documentation.

### Interface

- The approved dashboard/reference drawing defines the visual direction.
- Numbered annotations in the drawing are not application controls.
- The production shell has menu, toolbar, left navigation, center tabs, right inspector, draggable dividers, and status bar.
- The dashboard opens first and remains available.
- The startup window fits a laptop screen.
- Center content never renders beneath a sidebar.
- CSS is external.
- Icons and green/amber/red/gray/blue cues remain, with text or symbols so meaning does not depend on color alone.
- No fictional values appear in production.
- Unavailable values remain blank or neutral.

### Architecture

- H2 is authoritative for operational and accounting data.
- JavaFX panels contain no SQL.
- Repositories contain no accounting policy.
- Entities contain no JavaFX controls.
- Constructor injection is preferred.
- Write services own validation and transactions.
- Query services own screen/report projections.
- Existing architecture is extended rather than paralleled.

### Accounting

- Transactions use genuine double entry.
- Money uses `BigDecimal`.
- Relationships use stable IDs.
- Funds include unrestricted, restricted, and designated classifications.
- Budget categories are distinct from accounts and activities.
- History and corrections follow one documented lifecycle.
- Closed periods are never bypassed silently.
- Reopening and material changes are audited.
- A separate user-facing posting or approval workflow is not presumed.

### Scope

- Approvals and approval queues are out of scope.
- Formal oversight roles are out of scope.
- Attachments and document storage are out of scope.
- Notes and audit history are in scope.
- Supplies are part of Inventory.
- Reports appear in `REPORT_LIBRARY`.

### Imports

- Review staging is in memory until accepted.
- Valid and invalid rows remain visible together.
- Exact duplicate detection uses a stable source ID or deterministic fingerprint.
- Probable duplicates are warnings.
- A match may be discarded, saved as a copy, or left for manual review.
- Accepted accounting data is persisted through the canonical transaction service.

## 6. Immediate baseline assessment

Current `main` already contains substantial shell, dashboard, model, service, and panel code. It also contains simulated or sidecar-backed behavior that must be inventoried before replacement.

The first implementation slice must identify:

- controls that only update labels;
- session-only saves;
- static collections used as operational stores;
- sidecar-file persistence used instead of H2;
- duplicate models for the same accounting fact;
- approval-oriented UI that is now out of scope;
- panels using names instead of IDs;
- panels using `LocalDate.now()` instead of the active period;
- missing repository and migration coverage;
- empty or fictional dashboard values;
- buttons whose operation is not genuine.

Primary search targets include:

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
```

## 7. Delivery roadmap

Each numbered phase may contain several focused pull requests. Do not combine unrelated phases merely to reduce PR count.

---

## Phase 0 — Documentation and implementation inventory

### Objective

Establish a reliable current-main inventory and turn this plan into a maintained execution ledger.

### Deliverables

- [ ] Create `doc/interface-operation-matrix.md`.
- [ ] Map every `AppPanelId` to its panel, data source, write path, persistence, services, and missing work.
- [ ] Inventory sidecar/static stores.
- [ ] Inventory duplicate ledger, journal, budget, import, reconciliation, and audit models.
- [ ] Inventory approval and attachment UI that conflicts with scope.
- [ ] Inventory all donor-only features not yet represented in production.
- [ ] Consolidate still-valid union-application decisions into `doc/architecture/`.
- [ ] Add a dependency-ordered PR backlog to this plan.

### Acceptance

- No production behavior change.
- Every visible panel and command has a documented status.
- Every later phase has a known prerequisite.

---

## Phase 1 — Production shell and workspace composition

### Objective

Make the approved interface the single production shell and establish explicit application composition.

### Workstreams

#### 1A. Shell consolidation

- [ ] Remove duplicate/decorator workspace shells where they create two sources of UI behavior.
- [ ] Bind menu, toolbar, quick links, navigation, and shortcuts to typed application commands.
- [ ] Preserve categorized navigation, center tabs, inspector, status bar, icons, and responsive layout.
- [ ] Remove approval commands from the shell.
- [ ] Persist safe UI preferences such as divider positions where appropriate.

#### 1B. Workspace context and services

- [ ] Introduce or complete a `WorkspaceContext`.
- [ ] Represent active database, company, period, user preference, and connection state.
- [ ] Introduce a lifecycle-owned `WorkspaceServices` bundle.
- [ ] Introduce a `PanelFactory` using constructor injection.
- [ ] Permit the recovery dashboard to open without accounting services.
- [ ] Make database switching atomic.
- [ ] Close old JPA resources after a successful switch.
- [ ] Refresh open database-bound panels.

### Required tests

- command routing;
- permanent Dashboard tab;
- close-all-tabs;
- dirty-tab handling;
- database-switch failure and rollback;
- service-resource closure;
- sidebar collapse and restore;
- divider movement;
- supported laptop geometry;
- 100%, 125%, and 150% scaling policies.

---

## Phase 2 — Canonical ledger and transaction operations

### Objective

Select one authoritative writable ledger and make transaction entry, correction, and query operations genuine.

### Required decision

- [ ] Create `doc/accounting/ledger-authority.md`.
- [ ] Reconcile `Txn`/`TxnSplit` with any `journal_transaction` and `journal_posting_line` structures.
- [ ] Prevent two independently writable ledgers.
- [ ] Define migration/read compatibility for any retained legacy tables.

### Models and services

Create or complete, without duplicating existing types:

- [ ] transaction command DTO;
- [ ] line command DTO;
- [ ] validation result;
- [ ] transaction view projection;
- [ ] transaction entry service;
- [ ] transaction query service;
- [ ] transaction correction service;
- [ ] journal projection service;
- [ ] audit recording for material changes.

### Required behavior

- [ ] At least two meaningful lines.
- [ ] Debit equals credit.
- [ ] One-sided debit/credit line input.
- [ ] Zero-value accounting lines rejected.
- [ ] Account, fund, budget category, activity, merchant, and counterparty references use IDs.
- [ ] Atomic transaction creation.
- [ ] Direct edit, reversal, replacement, deletion, or other corrections follow one documented policy.
- [ ] Completed reconciliation protection.
- [ ] Closed-period warning/reopen or configured stricter workflow.
- [ ] Full rollback on failure.

### Required tests

- balanced two-line and multi-line entry;
- unbalanced rejection;
- invalid and duplicate references;
- reversal and replacement links;
- audit history;
- closed-period behavior;
- reconciliation protection;
- generated identifiers;
- concurrent writes;
- rollback without orphan headers or lines.

---

## Phase 3 — Ledger Register and Transaction Editor

### Objective

Replace validation-only and session-only behavior with a genuine spreadsheet-like accounting editor.

### Transaction Editor

- [ ] ID-backed account, fund, budget category, activity, merchant, and counterparty selectors.
- [ ] Debit and credit columns.
- [ ] Keyboard navigation.
- [ ] Add and remove lines.
- [ ] Live totals and balance indication.
- [ ] Validation messages attached to fields/rows.
- [ ] Dirty-state indication.
- [ ] Save/enter through the transaction service.
- [ ] Reverse and reverse/replace.
- [ ] Unsaved-change save/discard/cancel handling.
- [ ] Notes and reference/check number.
- [ ] Journal preview.

### Ledger Register

- [ ] Database-backed paging or bounded loading.
- [ ] Date, account, fund, budget category, status, and text filters.
- [ ] Stable running-balance behavior where meaningful.
- [ ] Open selected transaction.
- [ ] Correct selected transaction.
- [ ] Export the filtered view.
- [ ] Refresh after writes and database/period changes.

### Acceptance

No command reports success unless the corresponding H2 transaction committed.

---

## Phase 4 — Persistent budgeting

### Objective

Replace sidecar budget targets with normalized budget plans and lines.

### Model direction

Evaluate current main first. Add only missing concepts such as:

```text
BudgetPlan
BudgetLine
BudgetRevision or version metadata
```

### Required behavior

- [ ] Fiscal-year budget plan.
- [ ] Draft and active versions without approval semantics.
- [ ] Budget line by category, optional fund, and optional period.
- [ ] Duplicate line-key constraint.
- [ ] Atomic edit/save.
- [ ] Active-version selection.
- [ ] Historical version retention.
- [ ] Actuals derived from authoritative transactions.
- [ ] Monthly and YTD variance.
- [ ] Dashboard Budget Performance uses genuine values.
- [ ] Neutral dashboard state when no active budget exists.

### Cleanup

- [ ] Remove operational budget use of `UiWorkspaceDataStore`.
- [ ] Remove `BudgetTargetPersistence` as accounting truth.

---

## Phase 5 — Bank import and statement-line persistence

### Objective

Persist imported banking activity safely and idempotently.

### Models

Evaluate and add only what is missing:

```text
BankImportBatch
BankStatementLine
ImportIssue
ImportJob
```

### Required behavior

- [ ] Bank account identified by ID.
- [ ] Source format and file fingerprint.
- [ ] Stable external row ID where available.
- [ ] Deterministic fingerprint fallback.
- [ ] Transaction date and posting date retained separately.
- [ ] Preview and row-level errors.
- [ ] Exact duplicate prevention.
- [ ] Probable duplicate warning.
- [ ] Accepted statement rows survive restart.
- [ ] No automatic accounting transaction without explicit acceptance.
- [ ] Import jobs and outcomes persist.
- [ ] No raw source document dump as accounting truth.

### SCLX requirements

- [ ] Reimport is idempotent.
- [ ] Stable SCLX IDs reuse the existing accounting transaction.
- [ ] Zero-value/non-posting annotations are not inserted as transactions.
- [ ] Compact summaries may be retained.
- [ ] Raw SCLX payload documents are not retained.

---

## Phase 6 — Bank reconciliation

### Objective

Implement a genuine reconciliation workflow over imported statement lines and accounting transactions.

### Required behavior

- [ ] Select bank account and statement period.
- [ ] Start reconciliation.
- [ ] Display statement lines and ledger candidates.
- [ ] Match and unmatch.
- [ ] Support partial or split matching only where documented.
- [ ] Identify outstanding checks.
- [ ] Identify deposits in transit.
- [ ] Calculate adjusted book and statement balances.
- [ ] Calculate difference.
- [ ] Complete only under documented zero-difference or override policy.
- [ ] Reopen after warning.
- [ ] Retain reconciliation history.
- [ ] Protect included transactions from incompatible edit/delete.
- [ ] Update dashboard reconciliation status.

### Scope

No Approve or Reject actions.

---

## Phase 7 — Schedules and open items

### Objective

Use one normalized open-item and schedule architecture for:

- receivables;
- prepaid expenses;
- payables;
- deferred revenue;
- other assets;
- other liabilities.

### Required work

- [ ] Reuse or migrate existing open-item snapshot and transition concepts.
- [ ] Link a schedule item to its source transaction line.
- [ ] Store reference, original amount, open amount, due date, recognition date, counterparty, status, and notes.
- [ ] Support partial settlement.
- [ ] Support full settlement.
- [ ] Preserve transition history.
- [ ] Define reversal effects.
- [ ] Provide aging projections.
- [ ] Drill through to the source transaction.
- [ ] Feed genuine Dashboard Open Items counts and totals.

Do not create separate unrelated state machines for each schedule panel.

---

## Phase 8 — Fixed assets and depreciation

### Objective

Replace runbook entries with H2-backed fixed assets and generated accounting activity.

### Required behavior

- [ ] Register asset and link acquisition transaction line.
- [ ] Store cost, acquisition date, in-service date, salvage value, life, method, status, and notes.
- [ ] Implement straight-line depreciation first.
- [ ] Preview a depreciation run.
- [ ] Prevent duplicate asset/period depreciation.
- [ ] Complete a run by creating a balanced authoritative transaction.
- [ ] Retain run and entry history.
- [ ] Support retirement and disposal.
- [ ] Feed reports and dashboard projections as appropriate.

### Cleanup

- [ ] Remove asset and depreciation runbook-file persistence.

---

## Phase 9 — Inventory, supplies, and counts

### Objective

Make Inventory the single home for durable items, regalia, equipment, supplies, and consumables.

### Required behavior

- [ ] Persist inventory items.
- [ ] Persist inventory movements.
- [ ] Support item subtype, including `SUPPLY`.
- [ ] Support acquisition, transfer, adjustment, count, loss, consumption, and disposal.
- [ ] Link financially relevant movements to transaction lines.
- [ ] Track quantity, unit, value, custodian, status, and notes.
- [ ] Provide filtered Supplies and durable-equipment views without separate storage.
- [ ] Preserve movement history.
- [ ] Prevent invalid negative quantity unless a documented policy permits it.

### Cleanup

- [ ] Remove inventory runbook-file persistence.

---

## Phase 10 — Period close, reopening, notes, and audit history

### Objective

Replace approval-oriented period controls with documented close/reopen behavior and factual audit history.

### Required behavior

- [ ] Pre-close checks.
- [ ] Close period.
- [ ] Warn and offer reopen by default.
- [ ] Require reason where configured.
- [ ] Invoke adjustment workflow where configured.
- [ ] Choose documented reopening scope.
- [ ] Persist period notes.
- [ ] Audit close, reopen, correction, import, reconciliation, and database-switch events.
- [ ] Present Audit History or Change History.

### Remove

- [ ] Approve Selected.
- [ ] Reject Selected.
- [ ] Approval/rejection counts.
- [ ] Formal oversight role assumptions.
- [ ] Approval queue semantics.

---

## Phase 11 — Report Library

### Objective

Deliver genuine read-only reports through one Report Library.

### Required reports

- [ ] Trial Balance.
- [ ] General Ledger Detail.
- [ ] Balance Statement.
- [ ] Income Statement.
- [ ] Workbook Summary.
- [ ] Transactions List.
- [ ] All Checks/Transfers.
- [ ] Fund Transfers.
- [ ] Budget vs Actual.
- [ ] Reconciliation report.
- [ ] Open-item aging.
- [ ] Fixed-asset/depreciation report.
- [ ] Inventory report where required.

### Architecture

- [ ] Semantic embedded report definitions.
- [ ] No required external workbook at runtime.
- [ ] Values calculated by Java/H2 services.
- [ ] Report panels contain no SQL or accounting formulas.
- [ ] Screen preview.
- [ ] XLSX export where appropriate.
- [ ] PDF export where appropriate.
- [ ] Text/CSV export where appropriate.
- [ ] Company, fund, and period filters.

---

## Phase 12 — Administration, company, and preferences

### Objective

Complete genuine administration and database/company lifecycle operations.

### Master data

- [ ] Chart of Accounts.
- [ ] Funds.
- [ ] Budget Categories.
- [ ] Counterparties.
- [ ] Merchants.
- [ ] Activities.
- [ ] Company identity and configuration.

Rules:

- referenced records are deactivated rather than casually deleted;
- duplicate codes are rejected;
- display names may change without breaking references;
- aliases are used where import normalization requires them.

### Company/database handling

- [ ] Create a new company database.
- [ ] Select/switch company database.
- [ ] Populate required defaults.
- [ ] Create an explicit sample company/database without mixing sample data into production.
- [ ] Export/backup a database.
- [ ] Import/restore a database safely.
- [ ] Repair or recover a database without deleting user data.
- [ ] Destroy a company database only through explicit confirmation and documented safety checks.
- [ ] Preserve the recovery dashboard when the remembered/default database is invalid.

### Preferences

- [ ] Closed-period policy.
- [ ] Correction policy.
- [ ] Default company.
- [ ] Active-period behavior.
- [ ] Display and divider preferences.
- [ ] Import duplicate thresholds where appropriate.

---

## Phase 13 — Import/export jobs and diagnostics

### Objective

Replace session-only job history with genuine persistent operational records while keeping review staging in memory.

### Required behavior

- [ ] Persistent import batch/job metadata.
- [ ] Persistent export job metadata.
- [ ] Row-level issue records where appropriate.
- [ ] Retry only where safe.
- [ ] SCLX import/export.
- [ ] Bank import.
- [ ] Chart of Accounts import/export.
- [ ] Funds and Budget Categories import/export.
- [ ] Database export/backup and import/restore.
- [ ] Report export history.
- [ ] Migration status.
- [ ] Database health checks.
- [ ] Backup status.
- [ ] Safe diagnostic export without secrets or unnecessary personal paths.

---

## Phase 14 — End-to-end hardening and removal of simulated behavior

### Objective

Close every remaining gap between the visible production interface and authoritative operations.

### Cleanup checklist

- [ ] Remove operational use of `UiWorkspaceDataStore`.
- [ ] Remove operational use of `RunbookPersistence`.
- [ ] Remove operational use of `BudgetTargetPersistence`.
- [ ] Remove sample/demo/mock production data.
- [ ] Remove label-only success actions.
- [ ] Remove obsolete approval UI and models that have no retained audit purpose.
- [ ] Remove duplicate services and panel frameworks.
- [ ] Remove dead compatibility paths after data migration is proven safe.
- [ ] Update README run instructions and architecture summary.
- [ ] Ensure every public operation has meaningful error handling.

### End-to-end scenarios

Add tests and manual validation for:

1. create a company database;
2. populate required defaults;
3. create accounts, funds, and budget categories;
4. enter a balanced transaction;
5. edit or correct it under the configured policy;
6. reverse and replace where required;
7. create and activate a budget;
8. import bank statement lines;
9. reconcile a bank account;
10. create and settle an open item;
11. register an asset and run depreciation;
12. record inventory and supply movements;
13. close and reopen a period;
14. generate and export reports;
15. back up and restore the database;
16. restart and verify all authoritative records.

### Visual validation

Verify at minimum:

- 800 × 700;
- 1000 × 760;
- 1180 × 760;
- 1400 × 860;
- 100%, 125%, and 150% display scaling.

Check:

- no clipping;
- no center content beneath sidebars;
- usable dividers;
- correct scrolling;
- readable table columns;
- wrapping labels;
- visible validation;
- icons and non-color status text;
- laptop-friendly startup.

## 8. Cross-cutting test matrix

Every phase adds the applicable tests below.

### Unit tests

- accounting invariants;
- validation;
- state transitions;
- calculations;
- duplicate detection;
- formatting-independent policies.

### Service tests

- explicit transaction boundaries;
- rollback;
- correction;
- closed periods;
- reconciliation protection;
- audit events.

### Repository tests

- in-memory H2;
- generated IDs;
- foreign keys;
- indexes;
- unique and check constraints;
- deliberate delete behavior;
- sorting and paging.

### Migration tests

- upgrade from existing schemas;
- preserve user data;
- recover known malformed Flyway history safely;
- never delete/recreate a user database;
- regression test every reproduced migration failure.

### JavaFX policy tests

- responsive child minimum/preferred sizes;
- viewport and scrolling;
- divider positions;
- sidebar collapse;
- dirty-state prompts;
- command routing;
- ID-backed cells;
- active-period and database refresh.

### Report/export tests

- balanced trial balance;
- balance-sheet equation;
- income statement range;
- fund filtering;
- budget comparison;
- XLSX/PDF/text smoke tests;
- no fictional fallback values.

## 9. Pull-request completion checklist

Before marking any implementation PR ready:

- [ ] Started from the then-current `main`.
- [ ] Scope matches one coherent plan slice.
- [ ] Relevant `doc/` files are updated.
- [ ] Final diff inspected.
- [ ] No unintended files changed.
- [ ] No placeholders or unexplained TODOs.
- [ ] No swallowed exceptions.
- [ ] No SQL in JavaFX panels.
- [ ] No accounting policy in repositories.
- [ ] No JavaFX controls in models.
- [ ] No sidecar/static store used as accounting truth.
- [ ] New migration is nondestructive.
- [ ] In-memory H2 tests added where applicable.
- [ ] Regression tests added for defects.
- [ ] Geometry tests cover child and viewport behavior where applicable.
- [ ] GitHub Actions ran `mvn clean verify`.
- [ ] All failures were read and corrected.
- [ ] PR description states actual validation.
- [ ] Required desktop visual check completed.
- [ ] This plan records completion and the next dependency.

## 10. Next action

Begin with **Phase 0 — Documentation and implementation inventory**.

The first focused pull request should:

1. create `doc/interface-operation-matrix.md`;
2. inventory every `AppPanelId`, panel, command, data source, and write path;
3. identify every simulated or sidecar-backed operation;
4. identify duplicate domain/persistence models;
5. consolidate still-current union-application decisions under `doc/architecture/`;
6. update this plan with the resulting dependency-ordered implementation backlog.

Do not begin another broad interface rewrite before this inventory is complete.
