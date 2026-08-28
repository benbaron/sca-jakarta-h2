# Requirements clarification overlay — 2026-07

> Historical clarification record. The decisions in this overlay have been incorporated into the current plan and focused governing documents. Retain the file as requirements evidence, but do not treat its one-time update checklist or older terminology as the current execution backlog. Current `doc/PLAN.md` and current focused authority documents supersede it for phase status and production routing.

## Purpose

This document records the owner's clarification answers for the next design-document update pass. It is an overlay on the current `AGENTS.md`, `doc/PLAN.md`, and focused `doc/*.md` files in `benbaron/sca-jakarta-h2`.

Codex must use this overlay to update the governing documents before implementing the remapped slices. Where this overlay conflicts with the current plan, this overlay supersedes the older phase text after it is merged into `doc/PLAN.md`.

## Source

The source is the owner's `answers.docx` response to the clarification interview. The resulting decisions are recorded here as requirements.

## Global decisions

1. Ignore any remaining reference to `doc/architecture/production-workspace.md`; that document was removed.
2. Preserve Audit History. The earlier proposal to eliminate Audit History is reversed.
3. Eliminate the Schedules function entirely as a top-level panel, navigation item, `AppPanelId`, `PanelHost` route, documentation concept, and phase-plan item.
4. Eliminate Import/Export Jobs both as a panel and as persistent/generic job tracking.
5. Banking becomes a first-class Accounting function, not Administration.
6. Journal Pane becomes a first-class Accounting function and separate workspace panel.
7. The plan should be fully remapped and should also include this overlay so a later Codex pass can recheck conformance against the clarified requirements.

## Terminology corrections

- Transaction Editor **Edit** mode updates an existing transaction identified by a caller-provided ID. Do not call this upsert.
- Transaction Editor **New** mode creates a new transaction. It may be prefilled from any relevant context.
- The word "schedule" must no longer mean a top-level Schedules function. Where a transaction requires supplemental detail records, those records should be called **supplemental transaction records** or a domain-specific name such as inventory detail, open-item detail, depreciation detail, or bank-clearing detail.

## Required doc updates

Update at least:

- `doc/PLAN.md`
- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui/editor-guidelines.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/ledger-authority.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/ui_design_rules.md`

Create or update focused docs for:

- `doc/accounting/transaction-editor-and-journal.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/accounting/period-close-design.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/requirements/requirements-clarification-overlay.md`

## Requirements checklist for Codex

Before implementing any phase after this clarification, Codex must:

1. Read this overlay.
2. Update `doc/PLAN.md` to the remapped phase structure.
3. Remove Schedules from the plan and UI matrices.
4. Add Banking and Journal Pane as first-class Accounting functions.
5. Remove Import/Export Jobs as a function and durable generic job-tracking concept.
6. Keep Audit History as factual audit history.
7. Reassign any schedule/open-item concepts to domain-specific supplemental transaction records and reports.
8. Run documentation consistency checks for obsolete names:
   - `SchedulesPanel`
   - `SCHEDULES`
   - `ImportExportJobsPanel`
   - `IMPORT_EXPORT_JOBS`
   - `Approve`
   - `Reject`
   - `production-workspace.md`
