# Production workspace test plan

## Purpose

The production workspace replacement must preserve accounting correctness, database safety, and existing reachable workflows while replacing the prototype shell.

## Accounting and service tests

Cover:

- minimum two meaningful transaction lines;
- debit and credit equality;
- one-sided debit or credit values;
- direct editing with audit history;
- reversal and optional replacement links;
- deletion with an audit snapshot and rollback on failure;
- protection of transactions in completed reconciliations;
- closed-period warning and reopening policies;
- active-period selection independent of report date ranges;
- nonzero reconciliation override recording.

## Repository and migration tests

Use in-memory H2 to verify:

- every new migration upgrades an existing schema without destructive recreation;
- foreign keys, indexes, constraints, and delete behavior;
- generated identifiers;
- period closure and reopening history;
- audit records for material actions;
- dashboard projections on empty and populated databases;
- rollback after any repository or service failure.

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

Cover each governed format through its own preview/review/commit authority rather than a generic staged-import session:

- COA CSV freezes source SHA-256, company, target chart/fingerprint, accepted/rejected rows, and validation state; **Commit Accepted COA Rows** revalidates that scope and commits all accepted accounts, identities, and one factual operation audit atomically;
- Chart of Accounts JSON uses strict bounded recognition, non-mutating preview, stable target ownership, and one caller-owned import transaction; export remains deterministic and chart-only;
- SCLX preview freezes source hash, target company, mappings, protections, dispositions, and conflict choices; any source/company/target drift requires re-preview, and commit imports the retained exact nonblocking preview in one caller-owned transaction or rolls back completely;
- OFX 2.x, QFX, mapped CSV, and normalized CSV bank imports are locked to one configured company-owned bank account, use their governed preview services, and commit only durable review facts (`bank_import_batch`, `bank_statement_line`, `import_issue`, and profile/identity facts where applicable);
- bank-file import remains non-posting: it does not create canonical `Txn`/`TxnSplit` records or clear/reconcile a ledger line merely because a statement row was imported;
- **Create Transaction from Reviewed Row…** is a separate explicit acceptance operation that revalidates the reviewed row and atomically creates the canonical transaction plus accepted linkage/audit facts; matching and cleared state remain reconciliation-owned;
- reconciliation-origin bank import preserves and locks the exact reconciliation/configured-account scope, returns to that session only after successful durable review commit, and reloads H2 facts;
- exact/probable duplicate handling, idempotent retry rules, blocking ownership/close/reconciliation protections, and source identity are format-authority tests rather than generic import dispositions;
- cancelling or failing a preview/commit must not publish false success or partial durable state.

## JavaFX behavior tests

Verify:

- dashboard opens first and remains available;
- one reusable tab per canonical panel type;
- selecting an open destination activates its tab;
- dirty-tab save, discard, and cancel behavior;
- organization switching checks all dirty tabs;
- inspector follows the active tab and selected record;
- quick actions open the correct workflow state;
- Accounting exposes one **Journal** destination whose grouped journal and integrated New/Edit editor share the same workspace;
- requests using retired `LEDGER_REGISTER` or `TXN_EDITOR` compatibility identifiers normalize to the existing `JOURNAL_PANE` tab and never create separate Ledger Register or Transaction Editor workspaces;
- Journal New/Edit operations reuse the common line editor and canonical transaction services.


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

Table tests must cover the rules in `doc/ui_design_rules.md`: sortable, resizable, and reorderable columns; per-company persistence of sort/width/order state; vertical and horizontal scroll-bar access; and separation of each table in its own split-pane region from surrounding data. Formatter tests must cover company preference-based money symbols/print formats, two displayed decimal numerals, permissive money entry correction, preference-based date display, day/month/year ordering preference, permissive date entry correction, and period wording in days, quarters, or years as appropriate.

When a completed-phase panel is touched by corrective work, add or update tests for the applicable `doc/ui_design_rules.md` requirements rather than limiting coverage to the new behavior. If a rule cannot be implemented in the same corrective slice, document the skipped rule and the follow-up slice in `doc/PLAN.md`.

## Final validation

Before the PR is ready:

1. Inspect the final diff.
2. Verify that no unintended files changed.
3. Run the complete Maven test suite.
4. Verify `mvn clean verify` through GitHub Actions.
5. Read and correct all failing logs.
6. Update the PR description with actual validation results.
