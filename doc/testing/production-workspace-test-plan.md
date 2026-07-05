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

## Import tests

Cover:

- valid and invalid rows in one staged session;
- exact duplicate rejection by source ID and fingerprint;
- probable duplicate warnings;
- edit and accept;
- reject;
- match to an existing transaction;
- discard, save as copy, and cancel dispositions;
- warning before losing unresolved in-memory staging.

## JavaFX behavior tests

Verify:

- dashboard opens first and remains available;
- one reusable tab per panel type;
- selecting an open destination activates its tab;
- dirty-tab save, discard, and cancel behavior;
- organization switching checks all dirty tabs;
- inspector follows the active tab and selected record;
- quick actions open the correct workflow state;
- ledger register appears above its editor;
- Journal Entry reuses the common line editor.

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
