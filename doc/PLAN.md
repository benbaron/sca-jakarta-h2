---
plan_version: 4
active_phase: P03
active_slice: P03-C1
active_status: VERIFYING
active_branch: codex/P03-P03-C1-transaction-editor-register-modes
active_pull_request: pending
active_head: HEAD
next_action: "Finish P03-C1 verification and PR, then execute P03-C2 Journal Pane."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`.

Codex must not treat this as a general backlog. On container startup it selects one phase and one slice using `AGENTS.md`, executes only that scope, and updates this file with actual state.

This revision incorporates the owner's clarification answers from `answers.docx` and remaps the remaining phases accordingly.

## 2. How to invoke Codex

Preferred explicit invocation:

```text
Execute PHASE=P03, SLICE=P03-C1 from doc/PLAN.md.
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
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

When a branch begins, update front matter and the phase section. When a PR is open, record it. When validation remains, use `VERIFYING`. After merge is confirmed on current `main`, mark `DONE` and advance the active phase.

## 4. Phase index after requirements clarification

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update matrices as touched |
| P01 | Production shell and workspace composition | P00 | DONE; retrofit as touched |
| P02 | Canonical ledger and transaction operations | P00 | DONE; retain |
| P03 | Transaction Editor, Ledger Register, and Journal Pane | P01, P02 | READY for corrective/new slices |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | READY after P03-C1 unless slice is strictly model-only |
| P06 | Bank reconciliation and cleared-state comparison | P05 | BLOCKED |
| P07 | Eliminated former Schedules phase | n/a | ELIMINATED |
| P08 | Asset Register and depreciation | P02 | BLOCKED |
| P09 | Inventory and supplies | P02 | BLOCKED |
| P10 | Period close, reopening, and factual audit history | P02, P06 | BLOCKED |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | BLOCKED |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | BLOCKED |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | BLOCKED |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | BLOCKED |

A phase may begin early only when its required slice has no dependency on an unfinished prerequisite and the plan is updated to explain the deliberate exception.

## 4.1 Completed-phase design-rule retrofit

The design rules in `doc/ui_design_rules.md` apply retroactively to completed UI phases. Completed phases are not reopened wholesale, but corrective slices that touch completed surfaces must either bring that surface into compliance or record a focused follow-up slice here.

Completed-phase updates to apply:

- **P00 documentation inventory:** keep `doc/interface-operation-matrix.md` and `doc/persistence-authority-inventory.md` current when a completed panel lacks sortable/resizable/reorderable table state, per-company preference storage, money/date formatting correction, record removal behavior, or split-pane/scroll behavior.
- **P01 production shell:** shell preferences that affect company data display must move from global user state to per-company saved state; workspace geometry tests must include the table/split-pane/scroll requirements.
- **P02 canonical ledger services:** continue preserving internal money precision and date types; UI money/date correction remains outside authoritative service storage.
- **P03 transaction surfaces:** retrofit Transaction Editor, Ledger Register, and Journal Pane to the clarified New/Edit, read-only register, and journal navigation requirements.
- **P04 budget surfaces:** table-heavy budget panels must adopt the table-state and formatting rules before those panels are considered design-rule complete.

## 5. Governing documents

### Always read

- `AGENTS.md`
- `doc/PLAN.md`

### Current focused documents

- `doc/architecture/dashboard-workspace.md`
- `doc/architecture/application-composition.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/accounting/ledger-authority.md`
- `doc/import/import-review-workflow.md`
- `doc/testing/production-workspace-test-plan.md`
- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/architecture/union-application-direction.md`
- `doc/workflow/development-workflow.md`
- `doc/ui/editor-guidelines.md`
- `doc/ui_design_rules.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/accounting/budget-model.md`

`doc/architecture/production-workspace.md` was removed. Do not re-add it or list it as required reading.

### Clarification documents to create or update

The next documentation-conformance slice must create or update:

- `doc/requirements/requirements-clarification-overlay.md`
- `doc/accounting/transaction-editor-and-journal.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/accounting/period-close-design.md`
- `doc/inventory/inventory-and-assets.md`

After those focused documents exist, link them from this section.

### Donor/reference repositories

- `https://github.com/benbaron/NonprofitAccounting.git` is available as reference or donor code. When starting work in a new area, examine this donor codebase if accessible and suggest focused imports or adaptations that fit the current JavaFX/H2/JPA architecture. Donor code remains reference only until deliberately imported through the selected phase scope, tests, and documentation.

## 6. Established product decisions after clarification

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
- Left Navigation under Accounting must include Banking and Inspect Journal.
- Left Navigation must not include Schedules.
- Left Navigation must not include Import/Export Jobs as a separate function.

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
- Audit History remains in scope as factual audit history.

### Clarified scope

- No approval queue or formal approval/rejection workflow.
- Reconciliation approve/reject semantics are replaced by saved comparison/reconciliation state.
- No formal in-application oversight role.
- No attachment/document-storage feature.
- Notes and factual audit history are in scope.
- Supplies belongs in Inventory.
- Reports belong in `REPORT_LIBRARY`.
- The former Schedules function is eliminated.
- Import/Export Jobs is eliminated as both a panel and generic durable job-tracking function.

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
**Status:** DONE, with clarification updates as touched
**Depends on:** none

## Objective

Maintain an authoritative inventory of current `main` so later phases can replace placeholders without rescanning the whole UI.

## Clarification updates required when touched

- Add Journal Pane and Banking to `doc/interface-operation-matrix.md`.
- Remove Schedules and Import/Export Jobs as product functions.
- Preserve Audit History as factual audit history.
- Update `doc/persistence-authority-inventory.md` so former schedule concepts become domain-specific supplemental transaction records rather than a Schedules phase.

## Exit gate

The inventory reflects the clarified navigation and phase ownership.

## Codex seed prompt

```text
Execute PHASE=P00 as a documentation-conformance slice.
Update the operation matrix and persistence authority inventory for the clarified requirements.
Do not change production behavior.
```

---

# P01 — Production shell and workspace composition

**Selector:** `PHASE=P01`
**Status:** DONE, retrofit as touched
**Depends on:** P00

## Objective

Maintain the single production workspace shell and lifecycle-owned composition root.

## Clarification updates required when touched

- Do not reference `doc/architecture/production-workspace.md`.
- The production shell must expose Accounting navigation for Banking and Inspect Journal.
- The production shell must remove Schedules and Import/Export Jobs navigation entries when their implementation slices run.
- The shell must route Journal Pane through a first-class `AppPanelId` and typed command, not a text-command shortcut.

## Exit gate

One shell and one composition root own all production panels and database-bound service lifecycles.

---

# P02 — Canonical ledger and transaction operations

**Selector:** `PHASE=P02`
**Status:** DONE, retain
**Depends on:** P00

## Objective

Maintain one authoritative writable ledger and canonical transaction service.

## Clarification updates required when touched

- Transaction Editor Edit mode updates an existing transaction identified by caller-provided ID when policy allows.
- Transaction Editor New mode creates a new transaction and may prefill from any relevant context.
- Edit is not upsert and must not match likely duplicates by date/payee/reference/amount.
- Supplemental transaction records may be linked to transaction or split IDs for inventory, assets, bank clearing, and open-item/deferral detail.

## Exit gate

All authoritative accounting writes flow through one documented transaction service and one canonical persistence model.

---

# P03 — Transaction Editor, Ledger Register, and Journal Pane

**Selector:** `PHASE=P03`
**Status:** READY for corrective/new slices
**Depends on:** P01, P02

## Objective

Apply the clarified transaction-entry and journal requirements.

## Required reading

- `doc/ui/editor-guidelines.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/ui_design_rules.md`
- `doc/accounting/transaction-editor-and-journal.md`

## Slices

### P03-C1 — Transaction Editor modes and Ledger Register buttons

Status: VERIFYING.

Branch: `codex/P03-P03-C1-transaction-editor-register-modes`
Pull request: pending
Head commit: `HEAD`
Completed deliverables: Transaction Editor New/Edit routing, Ledger Register New/Open Selected buttons, read-only selected-ID edit routing, and governing documentation.
Remaining deliverables: create PR, record PR details, and perform any available remote PR validation.
Test status: Baseline `mvn -DskipTests compile` passed; focused `mvn -Dtest=LedgerRegisterPanelTest test` passed but Surefire reported 0 tests executed for that class in this environment; full `mvn clean verify` passed with 255 tests run and 9 skipped.
Known failures: none.
Next exact action: create the pull request for P03-C1 and then proceed to P03-C2 after PR validation/merge.

Implement and document:

Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items:

If this requires major redesign then remark this in documentation.

- Transaction Editor **New** mode creates a new transaction.
- Transaction Editor **Edit** mode updates an existing transaction by caller-provided ID when policy allows.
- Rename any remaining upsert language to update/edit.
- New mode may prefill from selected ledger row, selected bank import row, account/fund/budget filters, active period, journal context, inventory/asset/open-item/reconciliation context, or other explicit opener context.
- Ledger Register is read-only.
- Ledger Register has primary buttons **New** and **Open Selected**.
- **New** opens Transaction Editor in New mode.
- **Open Selected** opens Transaction Editor in Edit mode for the selected transaction ID.
- **Open Selected** is disabled unless exactly one transaction row is selected.

### P03-C2 — Journal Pane and Inspect Journal navigation

Status: READY after P03-C1.

Implement and document:

Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items:

If this requires major redesign beyond this slice, propose a solution then remark this in documentation.

- Add first-class Journal Pane with its own `AppPanelId`.
- Left Navigation under Accounting has **Inspect Journal**.
- Ledger Register has **Inspect Journal**.
- Journal Pane shows the traditional accounting general journal: date, transaction number/ID, memo/reference, account lines, debit, credit, and line details.
- Journal Pane opens unfiltered by default and may be centered at a selected transaction.
- Journal Pane provides filters that can be applied on the fly.
- Users do not edit directly in the journal grid; edit/new actions open Transaction Editor in Edit/New mode.
- Journal Pane makes provision for supplemental transaction records attached to a journal entry.

## Forbidden in P03

- Free-text relationship identity.
- Label-only save success.
- Direct editing in the journal grid.
- Upsert or likely-duplicate matching for Transaction Editor Edit mode.
- Accounting calculations in JavaFX controls.

## Validation

Add tests for mode routing, disabled Open Selected state, prefill context mapping, Journal Pane navigation, selected-transaction centering, and journal-to-editor handoff.
Review code to ensure compliance with all design documents.

## Codex seed prompt

```text
Execute PHASE=P03, SLICE=P03-C1 from doc/PLAN.md.
Apply Transaction Editor New/Edit modes and Ledger Register New/Open Selected behavior.
Do not implement Banking or Reconciliation in this slice.
```

---

# P04 — Persistent budgeting

**Selector:** `PHASE=P04`
**Status:** DONE, retrofit as touched
**Depends on:** P02

## Objective

Maintain normalized H2-backed budget plans and lines.

## Clarification updates required when touched

- Preserve `BudgetCategory` as distinct from Account and Activity.
- Apply `doc/ui_design_rules.md` table, money, date, and per-company preference rules to budget panels.

Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.
---

# P05 — Banking configuration and statement import

**Selector:** `PHASE=P05`
**Status:** READY after P03-C1 unless the selected slice is strictly model-only
**Depends on:** P02 and clarified P03 navigation/mode rules

## Objective

Create Banking as an Accounting function and connect statement import to configured bank accounts and canonical ledger lines.

## Required reading

- `doc/banking/import-and-reconciliation.md`
- `doc/banking/banking-and-reconciliation.md` once created
- `doc/import/import-review-workflow.md`
- `doc/accounting/ledger-authority.md`

## Slices

### P05-S1 — Bank and bank-account model

A Bank record represents the financial institution and stores:

- bank name;
- routing number;
- institution address;
- website;
- contact name;
- contact phone/email;
- notes;
- active/inactive status.

Each Bank Account record links one Bank to one Chart of Accounts account and stores:

- bank ID;
- chart-of-accounts account ID;
- masked account number;
- account nickname;
- opening date;
- opening balance;
- statement import format preference;
- OFX bank ID/account ID where applicable;
- notes;
- active/inactive status.

The linked chart-of-accounts account must have account type `BANK`, normal balance `DEBIT`, and financial statement class `CASH`.
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

### P05-S2 — Banking panel under Accounting

The Banking panel appears under Accounting in the Left Navigation Pane and lets the user:

- create a Bank record;
- edit Bank records;
- create the linked Chart of Accounts bank account automatically; or
- select an existing qualifying Chart of Accounts account.
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

### P05-S3 — Statement import normalization and matching

Support manual entry, CSV, OFX, and QIF statement sources. Preserve current SCLX import idempotency rules where applicable.
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

### P05-S4 — Cleared-state mapping to ledger bank lines

Imported bank statement records propose matches to internal ledger lines. Cleared state is stored on the ledger transaction line involving the bank account, not as authoritative accounting state in the imported statement row.
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

## Forbidden in P05

- Treating every account with type `BANK` as configured for reconciliation unless it is linked to a Bank record.
- Creating accounting transactions without explicit acceptance.
- Reintroducing generic Import/Export Jobs tracking.

## Codex seed prompt

```text
Execute PHASE=P05 from doc/PLAN.md.
Start with the first incomplete Banking configuration slice.
Banking belongs under Accounting, not Administration.
```

---

# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`
**Status:** BLOCKED
**Depends on:** P05

## Objective

Implement reconciliation for configured bank accounts using ledger-line cleared state and statement comparison.

Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

## Required behavior

- User selects from configured bank accounts only.
- Reconciliation reads all ledger transactions involving the selected bank account.
- From the most recent period close before the statement date through the given date, calculate beginning balance, activity, and ending book balance.
- Show one balance using all relevant transactions and one using only transactions whose bank ledger line is marked cleared.
- Accept statement data by manual entry, CSV, OFX, or QIF.
- Compare imported/manual statement entries to ledger lines using matching rules.
- On cleared-state mismatch, offer four options: warn only, overwrite ledger cleared state, never overwrite and require manual resolution, or choose per imported line.
- Report unmatched ledger transactions, unmatched statement entries, amount mismatches, date mismatches, duplicate possible matches, cleared-state mismatches, beginning/ending balance differences, and record-detail differences.
- User may save an unresolved reconciliation report.
- A new reconciliation may start new or edit an existing reconciliation at user option.
- Replace the current approve/reject run model entirely.

## Codex seed prompt

```text
Execute PHASE=P06 from doc/PLAN.md.
Use configured Bank-linked accounts from P05 and canonical ledger bank lines from P02.
Do not implement approve/reject reconciliation semantics.
```

---

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`
**Status:** ELIMINATED

The Schedules function is eliminated entirely as:

- `AppPanelId`;
- left navigation item;
- `PanelHost` route;
- documentation concept;
- future phase plan.

Underlying receivable, prepaid, payable, deferred revenue, and other supplemental transaction-detail concepts may remain, but they are not a Schedules panel or phase. They must be assigned to their owning domain phases as supplemental transaction records.

---

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Implement Asset Register add/edit and depreciation behavior through H2-backed records and canonical accounting transactions.

## Required behavior
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

- Assets are separate from Inventory items.
- Asset Register supports add and edit.
- Required fields: asset name, asset account, acquisition date, acquisition cost, salvage value, useful life, depreciation method, accumulated depreciation, current book value, status, and notes.
- User may enter opening accumulated depreciation.
- Show accumulated depreciation for each asset.
- Depreciation schedules support straight-line 3-year, 5-year, and 7-year schedules.
- Adding a depreciation schedule defines calculation only; it does not create future entries immediately.
- Running depreciation creates actual accounting transactions through the canonical transaction service.

## Codex seed prompt

```text
Execute PHASE=P08 from doc/PLAN.md.
Implement Asset Register add/edit and straight-line 3/5/7 year depreciation scheduling.
Depreciation runs create canonical accounting transactions.
```

---

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`
**Status:** BLOCKED
**Depends on:** P02

## Objective

Implement Inventory items and quantity movements as genuine records, eliminating the Inventory Runbook subpane.

## Required behavior
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

- Inventory Runbook subpane is removed.
- User can add inventory items.
- Required fields: item name, item type, quantity, unit, value, acquisition date, custodian, storage location, condition, notes, and active/disposed status.
- Adding certain inventory items creates accounting transactions.
- The transaction receives a supplemental inventory detail record with inventory-specific facts.
- Quantity movements are supported after item creation.
- Quantity movements create transactions when financially relevant.
- Historical movements are shown in another table, not in the eliminated runbook subpane.

## Codex seed prompt

```text
Execute PHASE=P09 from doc/PLAN.md.
Implement genuine Inventory item add and movement history.
Remove the runbook subpane and use canonical transactions where financially relevant.
```

---

# P10 — Period close, reopening, and factual audit history

**Selector:** `PHASE=P10`
**Status:** BLOCKED
**Depends on:** P02, P06

## Objective

Implement simplified calculated period close while preserving factual Audit History.

## Required behavior
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

- Period Close supports selecting a named/calculated open period and selecting a through-date or custom date range.
- There is no accounting-period table as the authority for open periods.
- Periods are calculated from Settings.
- Settings supports monthly, fiscal quarter, fiscal year, and custom date.
- Custom date supports a one-time custom open and close date.
- Closing a period closes only the selected period/date range and leaves all other dates alone.
- Provide a way to reopen a closed period, either in the Period Close panel or a related subpanel.
- New company setup has period state open, beginning date, and beginning balance setup.
- Beginning balances are entered through a wizard that creates balanced opening entries.
- Period close does not create immutable period-balance snapshots. Balances are recalculated from the ledger.
- Audit History is kept and remains factual.
- Approval/rejection semantics are removed from period close.

## Codex seed prompt

```text
Execute PHASE=P10 from doc/PLAN.md.
Implement calculated period close/reopen and keep factual Audit History.
Do not remove Audit History and do not implement approval/rejection workflow.
```

---

# P11 — Report Library

**Selector:** `PHASE=P11`
**Status:** BLOCKED
**Depends on:** P02, P04, P06, P08, P09, P10

## Objective

Deliver genuine read-only reports through one Report Library.

## Clarification updates required when touched

- Remove dependencies on eliminated Schedules phase.
- Include reports for Banking/Reconciliation, Inventory, Assets/Depreciation, Funds, Period Close, and factual Audit History as those domains become available.

Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

---

# P12 — Administration, company lifecycle, preferences, and Funds edit

**Selector:** `PHASE=P12`
**Status:** BLOCKED
**Depends on:** P01, P02

## Objective

Complete master-data administration, company/database operations, preferences, and clarified Funds editing behavior.

## Funds Panel requirements
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

Funds edit supports:

- fund name;
- fund code;
- fund type/classification;
- active/inactive status;
- notes.

Fund code is immutable while the fund is referenced by current ledger entries. Usage is determined by auditing the present ledger state. If ledger entries containing the fund are removed under an allowed correction policy and no references remain, deletion or code edit may be allowed under the final service policy.

Deleting a fund is allowed only when unused; otherwise the user deactivates it.

## Codex seed prompt

```text
Execute PHASE=P12 from doc/PLAN.md.
Implement Funds edit/delete/deactivate rules and company/preferences lifecycle without reintroducing Import/Export Jobs.
```

---

# P13 — Data exchange and diagnostics without Import/Export Jobs

**Selector:** `PHASE=P13`
**Status:** BLOCKED
**Depends on:** P02, P05, P12

## Objective

Implement necessary data exchange and diagnostics without a generic Import/Export Jobs panel or persistent generic job-tracking function.

## Required behavior
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

- Import/export feedback may be immediate UI feedback, produced files, diagnostics, or domain-specific durable records required by another feature.
- Do not reintroduce `IMPORT_EXPORT_JOBS` as an `AppPanelId` or navigation item.
- Do not create a generic durable job-tracking subsystem unless the plan is explicitly amended.
- Domain-specific import facts required by Banking/Reconciliation remain allowed.

## Codex seed prompt

```text
Execute PHASE=P13 from doc/PLAN.md.
Implement necessary data exchange and diagnostics without a generic Import/Export Jobs panel or durable generic job log.
```

---

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** BLOCKED
**Depends on:** P03-P13 except eliminated P07

## Objective

Remove remaining simulated behavior and prove the complete application across persistence, accounting, UI, reports, and database lifecycle.


## Clarified cleanup checklist
Always: read and follow 
- doc/interface-operation-matrix.md 
- doc/ui_design_rules.md
- doc/ui/editor-guidelines.md 
and apply them to the following work items. If this requires major redesign then remark this in documentation.

- Remove Schedules as a product function.
- Remove Import/Export Jobs as a product function.
- Preserve factual Audit History.
- Verify Journal Pane, Banking, Reconciliation, Period Close, Funds edit, Inventory, and Assets/depreciation workflows.
- Verify no obsolete `doc/architecture/production-workspace.md` references remain.

## Validation

Full `mvn clean verify`, GitHub checks, final diff, migration upgrade matrix, desktop screenshots, and manual scenario review.

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
- GitHub confirms required checks;
- PR description records actual validation;
- required desktop visual check is complete;
- branch/PR/head/test/next-action handoff is recorded here.
- code is reviewed to ensure compliance with design documents.

## 10. Current next action

Execute:

```text
PHASE=P03
SLICE=P03-C1
```

After P03-C1, execute P03-C2 Journal Pane, then return to the remapped P05 Banking configuration and statement import work.
