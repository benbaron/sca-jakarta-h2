# Phase remap after requirements clarification

## Purpose

This document maps the owner's clarification answers onto the Codex phase plan.

The remap should be incorporated into `doc/PLAN.md`.

## Summary of remap

### Keep completed foundations

P00 through P04 remain completed foundations, but completed UI phases receive corrective slices when touched:

- P00 inventory remains useful but must be updated for new Banking, Journal Pane, and the removal of Schedules and Import/Export Jobs.
- P01 shell remains the composition foundation.
- P02 ledger authority remains the canonical transaction foundation.
- P03 requires corrective expansion for Transaction Editor modes, Ledger Register buttons, and Journal Pane.
- P04 budget remains complete unless later budget UI changes trigger design-rule retrofit.

### Add first-class Journal Pane

Add a P03 corrective slice:

- `P03-C1 — Transaction Editor modes and Ledger buttons`
- `P03-C2 — Journal Pane and Inspect Journal navigation`

The Journal Pane gets its own `AppPanelId` and navigation entry under Accounting.

### Eliminate Schedules phase

Remove P07 as a phase.

Remove:

- `SCHEDULES` `AppPanelId`;
- Schedules left-navigation entry;
- Schedules panel route;
- Schedules phase contract;
- Schedules documentation references.

Retain underlying receivable, payable, prepaid, deferred, inventory-detail, and other supplemental transaction concepts as domain-specific supplemental transaction records. Do not call them the Schedules function.

### Add Banking to Accounting

P05 is remapped from "Bank import and statement-line persistence" to:

> P05 — Banking configuration and statement import

Suggested slices:

- P05-S1 Bank and bank-account model.
- P05-S2 Banking panel under Accounting.
- P05-S3 Statement import normalization and duplicate matching.
- P05-S4 Cleared-state mapping to ledger bank lines.
- P05-S5 SCLX/CSV/OFX/QIF import compatibility where applicable.

### Reconciliation remains P06

P06 is remapped to:

> P06 — Bank reconciliation and cleared-state comparison

It depends on P05 Banking configuration and P02 canonical ledger.

It replaces the approve/reject run model entirely.

### Remove Import/Export Jobs as a phase target

Remove `IMPORT_EXPORT_JOBS` as an `AppPanelId` and left-navigation function.

P13 should be renamed from "Imports, exports, jobs, and diagnostics" to:

> P13 — Data exchange and diagnostics

P13 must not implement a generic persistent Import/Export Jobs panel or durable generic job tracking. It may retain domain data required for banking, import review, reconciliation, diagnostics, and audit where another governing document requires it.

### Keep Audit History

P10 retains factual Audit History. Remove only approval/rejection semantics.

P10 should be renamed to:

> P10 — Period close, reopening, and factual audit history

### Period Close simplification

P10 must implement calculated period close without an accounting-period table authority, with custom one-time date ranges and reopen support.

### Funds, Inventory, Assets

P12 owns Funds edit and delete/deactivate rules.

P09 owns Inventory item add/edit, financial transaction creation, supplemental inventory detail records, quantity movement history, and removal of the runbook subpane.

P08 owns Asset add/edit, accumulated depreciation display, opening accumulated depreciation, straight-line 3/5/7 year schedules, and depreciation-run accounting transactions.

## Suggested revised phase index

| Phase | Revised name | Status intent |
|---|---|---|
| P00 | Documentation and implementation inventory | DONE, update matrices |
| P01 | Production shell and workspace composition | DONE, retrofit as touched |
| P02 | Canonical ledger and transaction operations | DONE, retain |
| P03 | Transaction Editor, Ledger Register, and Journal Pane | corrective/new slices |
| P04 | Persistent budgeting | DONE, retrofit as touched |
| P05 | Banking configuration and statement import | active/remap |
| P06 | Bank reconciliation and cleared-state comparison | blocked by remapped P05 |
| P07 | eliminated | remove from plan |
| P08 | Asset Register and depreciation | future |
| P09 | Inventory and supplies | future |
| P10 | Period close, reopening, and factual audit history | future |
| P11 | Report Library | future |
| P12 | Administration, company lifecycle, preferences, and Funds edit | future |
| P13 | Data exchange and diagnostics, without Import/Export Jobs | future |
| P14 | End-to-end hardening | future |

## Plan consistency actions

After merging this remap:

1. Update front matter active phase/slice if P05 remains active.
2. Rename P05 and P06.
3. Delete P07 phase contract or mark `ELIMINATED`.
4. Add P03 corrective slices.
5. Update P10 to preserve Audit History.
6. Update P13 to remove generic job tracking.
7. Update all matrices for removed/added `AppPanelId` values.
8. Remove references to `doc/architecture/production-workspace.md`.
