---
plan_version: 217
active_phase: P17
active_slice: P17-C3
active_status: IN_PROGRESS
active_branch: codex/P17-C3-budget-version-lifecycle
active_pull_request: pending
active_head: working
next_action: "Publish P17-C3, run Maven PR Tests, complete the owner Budget version lifecycle checklist, and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md` (3,288 lines). Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198` after exact head `ce85bad0810ca33de778e4354b8fa4a7ff74f0c5` passed Maven PR Tests run `32923532902`. The owner explicitly confirmed and merged C2, then directed work to continue to C3. The PR #294 merge combined two independently edited PLAN controllers and left duplicate YAML keys plus duplicated P17 history; P17-C3 removes that merge artifact while preserving the archived historical ledger.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE |
| P11 | Report Library | DONE through P11-C2 / PR #284 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 IN_PROGRESS |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Required for P17-C3:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/accounting/budget-model.md`
- `doc/workflow/development-workflow.md`

Required implementation/test inspection for P17-C3:

- `src/main/java/org/nonprofitbookkeeping/model/BudgetPlan.java`
- `src/main/java/org/nonprofitbookkeeping/service/BudgetPlanService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/BudgetEditorPanel.java`
- `src/test/java/org/nonprofitbookkeeping/service/BudgetPlanServiceTest.java`
- `src/test/java/org/nonprofitbookkeeping/ui/BudgetEditorPanelTest.java`
- `src/test/java/org/nonprofitbookkeeping/ui/BudgetFiscalAuthoritySourceTest.java`
- current production-route/core-editor compliance tests touching Budget Editor

`doc/architecture/production-workspace.md` was removed. Do not re-add it or list it as required reading.

## 5. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, budget stores, import stores, record stores, or prototype maintenance paths.
- Disabled placeholder Delete buttons are not part of the UI contract. A Delete action is shown only when it performs a real governed operation.
- Durable records that must preserve history use deactivation, archive, disposition, correction, reversal, or another governed lifecycle rather than invented generic hard deletion.
- Budget plans are versioned durable records with `DRAFT`, `ACTIVE`, and retained `ARCHIVED` states. Activation, not approval, selects the authoritative comparison version.
- Budget vs Actual reads only the active plan for the selected fiscal range. Archived history never becomes variance authority.
- Company-specific money/date/table/divider state remains H2-backed and production tables retain the P17-C1 UI-design-rule contract.

## 6. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE.

Completion evidence:

- PRs #290, #291, and #292 merged the authorized shared UI policy corrections.
- Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`.
- Current main includes those corrections; do not reopen C1 wholesale.

### P17-C2 — Durable account record lifecycle

Status: DONE.

Completion evidence:

- PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`.
- Exact final C2 head `ce85bad0810ca33de778e4354b8fa4a7ff74f0c5` passed Maven PR Tests run `32923532902`.
- Interactive Chart of Accounts now saves by stable account ID, permits code changes as business data, preserves references, enforces ownership/uniqueness/Banking guards, and retires through Active/deactivation without placeholder Delete.
- The owner explicitly confirmed and merged C2 before directing C3.

### P17-C3 — Budget version lifecycle completion

Status: IN_PROGRESS.

Branch: `codex/P17-C3-budget-version-lifecycle`  
Starting base: `30920323fb7f2d8fd786bad7e0225ca4aa484198`  
Pull request: pending

Purpose:

- Close the next durable-record lifecycle gap after C2 in Budget Editor.
- Reuse the authoritative `BudgetPlanService` archive lifecycle rather than porting the donor repository's non-persistent **Delete Selected** placeholder.
- Keep retained version history visible by stable plan ID.
- Permit explicit retirement of abandoned drafts while preventing manual archival of the active budget.
- Preserve the existing rule that activating a replacement draft archives the prior active version atomically.
- Leave Budget vs Actual, fiscal-period derivation, normalized H2 budget persistence, and interchange authority unchanged.

Planned deliverables:

- Add a retained-version query that returns `DRAFT`, `ACTIVE`, and `ARCHIVED` plans for one active-company fiscal year.
- Restrict explicit `BudgetPlanService.archive(...)` to drafts so the active budget cannot be silently removed from authority.
- Add service-backed **Archive Draft** with confirmation and visible retained-history guidance to Budget Editor.
- Keep archived versions selectable but read-only; preserve New Draft, Create Revision, Save Draft Amount, and Activate Version semantics.
- Add service and UI regression coverage proving stable ID/line retention, archived visibility, active-plan protection, and absence of placeholder Delete.
- Update `doc/accounting/budget-model.md`, `doc/interface-operation-matrix.md`, and add `doc/P17-C3-budget-version-lifecycle-user-testing.md`.
- Reconcile the malformed post-C2 PLAN controller created by the #293/#294 merge without changing the archived historical ledger.

Validation status:

- Exact starting main is `30920323fb7f2d8fd786bad7e0225ca4aa484198`.
- Exact merged-C2 head `ce85bad0810ca33de778e4354b8fa4a7ff74f0c5` passed Maven PR Tests run `32923532902`; this is the C3 baseline evidence.
- The task container cannot resolve `github.com` and has no Maven executable, so no local Maven baseline/result is claimed. The exact repository source snapshot from the successful C2 workflow was used for local source inspection and diff construction.
- GitHub Maven PR Tests are required after publication and are authoritative for compile, JUnit/H2, and JavaFX route validation.

Known failures:

- None at task start.

Owner acceptance:

- Follow `doc/P17-C3-budget-version-lifecycle-user-testing.md` after final-head CI is green.
- Do not merge until the owner accepts the checklist.

Next exact action:

- Publish the implementation on the fresh C3 branch, open a draft PR, run Maven PR Tests, fix any CI findings, update this record to `VERIFYING`, and stop before merge for owner desktop acceptance.
