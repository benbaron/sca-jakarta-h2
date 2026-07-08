---
plan_version: 7
active_phase: P08
active_slice: P08-S1
active_status: VERIFYING
active_branch: codex/P08-S1-h2-fixed-assets
active_pull_request: pending
active_head: pending
next_action: "Open P08-S1 PR, run mvn -Dtest=FixedAssetServiceTest test, then mvn clean verify, fix failures, and update this plan with validation."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records merged P07 completion and starts P08-S1 for H2-backed fixed assets and depreciation.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Phase index after requirements clarification

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update matrices as touched |
| P01 | Production shell and workspace composition | P00 | DONE; retrofit as touched |
| P02 | Canonical ledger and transaction operations | P00 | DONE; retain |
| P03 | Transaction Editor, Ledger Register, and Journal Pane | P01, P02 | READY for corrective/new slices |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | DONE through PR #137 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138 |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | VERIFYING P08-S1 |
| P09 | Inventory and supplies | P02 | BLOCKED |
| P10 | Period close, reopening, and factual audit history | P02, P06 | BLOCKED |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | BLOCKED |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | BLOCKED |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | BLOCKED |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | BLOCKED |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Focused documents for current UI/accounting work:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/accounting/transaction-editor-and-journal.md`
- `doc/workflow/development-workflow.md`

`doc/architecture/production-workspace.md` was removed. Do not re-add it or list it as required reading.

## 5. Established product decisions after clarification

- One production JavaFX application.
- H2 is authoritative for accepted operational/accounting data.
- Existing JPA/Hibernate model and Flyway migrations are the schema foundation.
- No parallel ledgers, budget stores, import stores, or panel frameworks.
- Write services own validation and transactions.
- Query services return projections for panels and reports.
- Constructor injection is preferred.
- Left Navigation under Accounting must include Banking and Inspect Journal.
- Left Navigation must not include Schedules.
- Left Navigation must not include Import/Export Jobs as a separate function.
- No approval queue or formal approval/rejection workflow.
- Reconciliation approve/reject semantics are replaced by saved comparison/reconciliation state.
- Notes and factual audit history are in scope.
- Reports belong in `REPORT_LIBRARY`.
- The former Schedules function is eliminated.
- Import/Export Jobs is eliminated as both a panel and generic durable job-tracking function.
- Fixed assets are distinct from inventory and require H2-backed asset/depreciation records.

## 6. Completed phases and slices

### P00 — Documentation and implementation inventory

Status: DONE, with clarification updates as touched.

### P01 — Production shell and workspace composition

Status: DONE, retrofit as touched.

### P02 — Canonical ledger and transaction operations

Status: DONE, retain.

### P03 — Transaction Editor, Ledger Register, and Journal Pane

Status: READY for corrective/new slices.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.

### P04 — Persistent budgeting

Status: DONE, retrofit as touched.

### P05 — Banking configuration and statement import

Status: DONE through PR #137.

Completed slices:

- P05-S1 Bank and bank-account model: DONE.
- P05-S2 Banking panel under Accounting: DONE.
- P05-S3 Statement import normalization and matching: DONE.
- P05-S4 Cleared-state mapping to ledger bank lines: DONE.

Validation recorded in PR #137: focused banking/import/reconciliation tests and full local `mvn clean verify` passed with 266 tests run and 9 skipped. No GitHub workflow runs were available for the merge commit.

## 7. Active and recent phase contracts

# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`
**Status:** DONE through PR #138
**Depends on:** P05

Completed deliverables: removed approval semantics; added configured-account reconciliation comparison; added unresolved report summaries backed by H2 run records.
Known follow-up: per-line cleared-state resolution choices and edit-existing reconciliation workflow remain later P06/P10/P11 work.

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`
**Status:** DONE through PR #139

Completed deliverables: removed `SchedulesPanel`, production factory route, navigation item, schedule runbook sidecar methods, schedule runbook formatting tests, and related operation-matrix/persistence-inventory references. `AppPanelId.SCHEDULES` remains only as a retired compatibility enum value for legacy switch/preset paths; it has no product route.

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`
**Status:** VERIFYING P08-S1
**Depends on:** P02

Required behavior: implement Asset Register add/edit and depreciation behavior through H2-backed records and canonical accounting transactions. Assets are separate from Inventory items. Depreciation schedules define calculation only; running depreciation creates actual accounting transactions through the canonical transaction service.

### P08-S1 — H2 fixed asset register and depreciation runs

Status: VERIFYING.

Branch: `codex/P08-S1-h2-fixed-assets`
Pull request: pending
Completed deliverables in branch: V49 fixed asset/depreciation-run migration; `FixedAsset` and `FixedAssetDepreciationRun` JPA entities; `FixedAssetService` create/update/list/depreciation-run behavior; depreciation runs create canonical `Txn`/`TxnSplit` rows; Asset Register and Depreciation Runs panels read/write through `FixedAssetService`; asset/depreciation runbook sidecars removed; docs and focused tests added/updated.
Remaining deliverables: open PR, run focused `FixedAssetServiceTest`, run `mvn clean verify`, fix failures, perform desktop visual review, update PR and this plan.
Next exact action: open P08-S1 PR.

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`
**Status:** BLOCKED
**Depends on:** P02

Required behavior: implement genuine Inventory item add and movement history; remove runbook subpane; use canonical transactions when financially relevant.

# P10 — Period close, reopening, and factual audit history

**Selector:** `PHASE=P10`
**Status:** BLOCKED
**Depends on:** P02, P06

Required behavior: implement calculated period close/reopen while keeping factual Audit History and no approval/rejection workflow.

# P11 — Report Library

**Selector:** `PHASE=P11`
**Status:** BLOCKED
**Depends on:** P02, P04, P06, P08, P09, P10

# P12 — Administration, company lifecycle, preferences, and Funds edit

**Selector:** `PHASE=P12`
**Status:** BLOCKED
**Depends on:** P01, P02

# P13 — Data exchange and diagnostics without Import/Export Jobs

**Selector:** `PHASE=P13`
**Status:** BLOCKED
**Depends on:** P02, P05, P12

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** BLOCKED
**Depends on:** P03-P13 except eliminated P07

## 8. Cross-cutting validation matrix

Every phase adds applicable tests:

- accounting invariants;
- validation;
- state transitions;
- service transaction rollback;
- in-memory H2 repositories;
- migrations;
- JavaFX layout and command routing;
- TestFX workflow checks where UI behavior is central;
- report/export smoke tests.

## 9. Pull-request completion checklist

Before a PR is ready:

- selected phase/slice is recorded;
- branch started from then-current `main`;
- scope is one coherent slice;
- relevant `doc/` files and this plan are updated;
- final diff inspected;
- no unintended/generated/user-data files changed;
- no placeholders or swallowed exceptions;
- no SQL in JavaFX panels;
- no accounting policy in repositories;
- no JavaFX controls in models;
- no sidecar/static store used as authoritative persistence;
- new migrations are nondestructive;
- applicable unit/service/H2/migration/regression/layout tests exist;
- `mvn clean verify` passes;
- GitHub confirms required checks when workflows exist;
- PR description records actual validation;
- required desktop visual check is complete;
- branch/PR/head/test/next-action handoff is recorded here;
- code is reviewed to ensure compliance with design documents.

## 10. Current next action

Execute validation for:

```text
PHASE=P08
SLICE=P08-S1
```

Open the PR, run `mvn -Dtest=FixedAssetServiceTest test`, fix any failures, then run `mvn clean verify`.
