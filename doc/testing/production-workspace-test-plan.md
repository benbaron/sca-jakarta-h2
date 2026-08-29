# Production workspace test plan

## Purpose

The production workspace must preserve accounting correctness, database safety, and every current reachable workflow. Tests target the canonical production shell and domain-specific services; retired prototype panels, generic job tracking, and eliminated Schedules UI are not acceptance targets.

## Accounting and service tests

Cover:

- minimum two meaningful transaction lines;
- debit and credit equality;
- one-sided debit or credit values;
- direct editing with factual audit history;
- reversal and optional replacement links;
- deletion with an audit snapshot and rollback on failure;
- protection of transactions in completed reconciliations;
- closed-period enforcement, reopening, and adjustment policies;
- active accounting-period selection independent of explicit report date ranges;
- nonzero reconciliation override recording.

## Repository and migration tests

Use in-memory H2 to verify:

- every new migration upgrades an existing schema without destructive recreation;
- foreign keys, indexes, constraints, and delete behavior;
- generated identifiers;
- period closure and reopening history;
- factual audit records for material actions;
- dashboard projections on empty and populated databases;
- rollback after repository or service failure;
- compatibility tables remain readable when current production no longer routes through their legacy UI/service wrapper.

## Dashboard tests

Verify database-backed values for:

- book, reconciled, and unreconciled cash;
- multiple bank accounts;
- year-to-date result by unrestricted, restricted, and designated classification;
- budget exceptions and thresholds;
- pending import and reconciliation work;
- recent entered transactions;
- empty-state behavior without fictional values.

## Import and interchange tests

Test each format through its real preview/review/commit boundary rather than a generic staged-import session.

### Chart of Accounts CSV

Cover:

- preview of valid and invalid rows;
- frozen source/company/chart/target identity;
- accepted/rejected disposition;
- atomic **Commit Accepted COA Rows**;
- rollback on any accepted-row failure;
- idempotent identical recommit;
- required re-preview after source, company, chart, or target drift.

### Chart of Accounts JSON

Cover:

- explicit chart-only import/export scope;
- stable chart/account semantics without transaction-history transfer;
- validation before authoritative writes.

### OFX/QFX and bank CSV

Cover:

- exact configured company/bank-account scope;
- OFX/QFX identity and duplicate handling;
- mapped CSV and normalized CSV parsing/validation;
- durable `bank_import_batch`, `bank_statement_line`, and issue persistence only after commit;
- no automatic canonical ledger posting during statement import;
- statement-review status and retained source identity;
- explicit reviewed-row acceptance through the canonical transaction service;
- reconciliation-owned matching and cleared-state changes.

### SCLX

Cover:

- complete preview before commit;
- target-company selection and ownership gating;
- per-record `NEW`, `IDENTICAL`, and `CONFLICT` classification;
- explicit conflict resolutions and revalidation;
- warnings/errors with actionable dispositions;
- one caller-owned atomic target-company graph commit;
- rollback without partial accepted business data;
- semantic round-trip for supported sections and preserved portable identities.

For every import family, abandoning an unresolved preview may discard transient review state, but committed H2 facts must never depend on a generic Import/Export Jobs history or `UiWorkspaceDataStore`.

## JavaFX behavior tests

Verify:

- Dashboard opens first and remains available;
- one reusable tab per canonical `AppPanelId` destination;
- `LEDGER_REGISTER` and `TXN_EDITOR` compatibility requests normalize to the one Journal workspace and do not create separate panels;
- selecting an open destination activates its tab and triggers the panel refresh contract;
- dirty-tab save, discard, and cancel behavior;
- company switching checks all dirty tabs and recreates company-bound workspaces as required;
- inspector follows the active tab and selected record;
- quick actions open the correct current workflow state;
- the Journal grouped review region appears above the integrated common entry editor;
- transaction cleared-state display is service-projected and read-only in Journal;
- no Schedules destination, generic Import/Export Jobs destination, legacy shell Find/command palette, standalone Ledger Register, or standalone Transaction Editor is installed.

## JavaFX test strategy

Use a three-level JavaFX testing approach for production UI work:

1. Keep business logic, validation, persistence, calculations, and durable state transitions in ordinary services or view models that can be tested with standard JUnit tests without starting JavaFX.
2. Component-test controllers, panels, and view models on the JavaFX Application Thread when scene-graph behavior, bindings, sizing, or control state must be verified. Use deterministic JavaFX synchronization helpers rather than arbitrary sleeps.
3. Add a small number of TestFX workflow tests for important user interactions such as navigation, editing, validation feedback, save/reload, table selection, dialogs, keyboard traversal, and persistence-visible behavior.

Production JavaFX tests must prefer stable control IDs and CSS lookup selectors such as `#transactionEditorSaveButton` over visible text lookups, because labels can change for wording, accessibility, or localization. Any new or materially changed workflow surface should assign IDs to important fields, buttons, tables, menus, status labels, and dialog controls that need automation coverage.

TestFX interaction tests use `org.testfx:testfx-junit5` with JUnit 5. They should initialize the displayed stage through the TestFX JUnit extension, drive the UI with `FxRobot`, and assert observable state with TestFX/JUnit assertions. Reserve full robot workflows for high-value paths; continue putting most coverage in service, repository, model, view-model, and focused component tests.

All JavaFX scene-graph mutations in tests must respect the JavaFX Application Thread. Use JavaFX/TestFX synchronization, for example `WaitForAsyncUtils.asyncFx(...).get()`, `WaitForAsyncUtils.waitForFxEvents()`, or condition-based waits. Do not use `Thread.sleep(...)` as a synchronization mechanism for UI assertions.

UI tests must not run concurrently with each other. JavaFX tests share the JavaFX runtime, windows, focus, mouse, and keyboard. Mark TestFX classes for same-thread execution when JUnit parallelism is enabled, and close any dialog or secondary stage opened by a test.

Headless CI should run JavaFX/TestFX tests under a virtual display such as `xvfb-run -a mvn test` unless a later documented Monocle configuration is proven stable for the repository's Java and JavaFX versions. In local non-display containers, component tests may use assumptions to skip when no display is available, but final PR validation must record whether JavaFX workflow tests were run under a display or skipped for an environment limitation.

## Geometry tests

Tests must consider child minimum and preferred sizes, viewport behavior, scrolling, divider behavior, sidebar collapse, and center-content constraints. They must verify that center content never renders beneath either sidebar at supported window sizes and display scaling.

Any pane section whose default-size text or tabular values can be hidden must have geometry coverage for a visible `SplitPane` divider plus both vertical and horizontal scroll-bar access. Tests should fail layouts that solve clipping by increasing minimum widths, hiding overflow, or wrapping text without a horizontal path to the full value.

Table tests must cover the rules in `doc/ui_design_rules.md`: sortable, resizable, and reorderable columns; per-company persistence of sort/width/order state; vertical and horizontal scroll-bar access; and separation of each table in its own split-pane region from surrounding data. Formatter tests must cover company preference-based money symbols/print formats, two displayed decimal numerals, permissive money entry correction, preference-based date display, day/month/year ordering preference, permissive date entry correction, and accounting-period wording as appropriate.

When a completed-phase panel is touched by corrective work, add or update tests for the applicable `doc/ui_design_rules.md` requirements rather than limiting coverage to the new behavior. If a rule cannot be implemented in the same corrective slice, document the skipped rule and the follow-up slice in `doc/PLAN.md`.

## Final validation

Before a PR is ready for owner acceptance:

1. Inspect the final diff and verify that no unintended files changed.
2. Run focused tests during implementation when the local environment supports them.
3. Run the complete Maven test suite locally only when dependencies/runtime permit, and record the actual result rather than assuming it.
4. Verify `mvn clean verify` through the repository GitHub Actions workflow.
5. Verify the workflow's repeat-test and production JavaFX route-compliance gates.
6. Read and correct every failing log.
7. Update the PR description and `doc/PLAN.md` with exact final-head validation evidence.
8. Keep desktop visual/manual acceptance distinct from automated CI evidence.
