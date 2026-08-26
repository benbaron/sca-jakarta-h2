---
plan_version: 215
active_phase: P17
active_slice: P17-C2
active_status: IN_PROGRESS
active_branch: codex/P17-C2-durable-record-lifecycle
active_pull_request: pending
active_head: pending-publication
next_action: "Publish P17-C2, run Maven PR Tests, complete the owner durable-account lifecycle checklist, and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic pre-P17-C2 ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current phase selection, branch/PR control, validation state, and next action are authoritative here.

This revision reconciles repository state that the prior controller had not caught up with:

- P05-C6 is DONE through merged PR #285 and corrective merged PR #286; its old `IN_PROGRESS` controller declaration is removed.
- P11-C2 is DONE through merged PRs #283 and #284 plus owner acceptance.
- P16 is DONE through corrective P16-C11 / merged PR #281.
- P17-C1 is DONE through merged PR #291 and CI-correction PR #292; current `main` is merge commit `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- P17-C2 is the active owner-authorized corrective slice on fresh branch `codex/P17-C2-durable-record-lifecycle`.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Current phase index

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update matrices as touched |
| P01 | Production shell and workspace composition | P00 | DONE |
| P02 | Canonical ledger and transaction operations | P00 | DONE |
| P03 | Journal workspace and canonical transaction operations | P01, P02 | DONE |
| P04 | Persistent budgeting | P02 | DONE |
| P05 | Banking configuration and statement import | P02, P03 | DONE through P05-C6 / PR #285 plus corrective PR #286 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE |
| P07 | Eliminated former Schedules phase | n/a | ELIMINATED/DONE |
| P08 | Asset Register and depreciation | P02 | DONE |
| P09 | Inventory and supplies | P02 | DONE |
| P10 | Period close, reopening, and factual audit history | P02, P06 | DONE |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | DONE through P11-C2 / PR #284 |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | DONE |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | DONE |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | DONE |
| P15 | Versioned data interchange and database transfer | P02, P05, P06, P12, P13, P14 | DONE |
| P16 | Interface-to-authority completion and integrity corrections | P03-P15 except eliminated P07 | DONE through P16-C11 / PR #281 |
| P17 | UI design-rule and durable-record lifecycle corrections | P03-P16 except eliminated P07 | P17-C1 DONE; P17-C2 IN_PROGRESS |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Required for P17-C2:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/administration/fund-lifecycle.md`
- `doc/administration/company-lifecycle.md`
- `doc/administration/user-role-maintenance.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/workflow/development-workflow.md`

Historical implementation detail may be read from `doc/archive/PLAN-pre-P17-C2.md` only as needed. Current repository code, migrations, tests, merged PRs, and this controller remain authoritative over stale historical statements.

## 5. Established product decisions retained by P17-C2

- One production JavaFX application and one H2 accounting authority.
- Existing JPA/Hibernate model and Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, import stores, record stores, or prototype maintenance paths.
- Disabled placeholder Delete buttons are not part of the UI contract. Delete is shown only for a real governed operation.
- Durable accounting/operational records that must survive history use deactivation, disposition, correction, or reversal rather than an invented generic hard-delete operation.
- Funds are maintained by stable database ID; referenced funds are deactivated and only demonstrably unused funds may use their existing protected delete operation.
- Companies use stable database ID and inactive lifecycle when referenced.
- Journal transactions retain the governed correction/reversal lifecycle.
- Fixed assets retain governed lifecycle events/disposition/reversal; Inventory retains governed movement/reversal semantics.
- User/role administration retains stable IDs and dated assignment/end/revoke semantics.
- Banking retains its stable bank/configuration record IDs and existing protected account-classification rules.
- Chart-of-Accounts JSON, COA CSV, SCLX, and Banking auto-create operations retain their governed batch/compatibility seams; P17-C2 changes the interactive single-account editor identity rule, not interchange contracts.

## 6. P17 execution ledger

### P17-C1 — UI design-rules implementation correction

Status: DONE.

Merged evidence:

- PR #291, `Codex/p17 c1 UI design rules implementation correction`, merged at `0e61ba578ec9a478424ab4206b140334c63ada9a`.
- PR #292, `P17-C1: complete CI corrections after UI audit`, merged at current-main commit `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- PR #292 completed the CI-driven migration-number, bank-account timestamp-identity, and active-period guard corrections after the P17-C1 UI audit.

Next exact action: none; P17-C1 is merged and must not be reopened for P17-C2 work.

### P17-C2 — Durable record lifecycle completion

Status: IN_PROGRESS.

Branch: `codex/P17-C2-durable-record-lifecycle`  
Starting base: `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`  
Pull request: pending publication  
Current implementation head: pending publication

Purpose:

- Correct interactive Chart of Accounts maintenance so an account's durable database ID, not its editable account code, selects the row being updated.
- Ensure an account code change updates one existing row and preserves ledger, Banking, report, import-identity, and other durable references to that account.
- Enforce active-company/active-chart ownership and chart-scoped code uniqueness on stable-ID saves.
- Preserve existing Banking classification protections and parent-account validation.
- Retain **Active** as the account retirement lifecycle. Do not invent a generic Delete operation for accounts that participate in accounting history.
- Audit the remaining durable editors for lifecycle consistency without changing already-governed Journal, Funds, Company, Asset, Inventory, Banking, or User semantics.

Required inspection:

- `src/main/java/org/nonprofitbookkeeping/service/AccountAdminService.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/CompanyOwnershipService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/ChartOfAccountsPanel.java`
- `src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java`
- `src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java`
- `src/test/java/org/nonprofitbookkeeping/service/AccountAdminServiceTest.java`
- `src/test/java/org/nonprofitbookkeeping/service/AccountAdminServiceIntegrationTest.java`
- `src/test/java/org/nonprofitbookkeeping/ui/ChartOfAccountsPanelFormStateTest.java`
- `src/test/java/org/nonprofitbookkeeping/ui/FundsPanelLifecycleSourceTest.java`

Implemented/published deliverables for this run:

- Add immutable `AccountCommand` with nullable durable ID.
- Add `AccountAdminService.save(AccountCommand)` so create uses null ID and update requires the exact account ID; code-addressed `upsert` remains only as a compatibility/batch/auto-create boundary.
- Validate active-company/active-chart ownership, duplicate account code excluding the edited ID, parent membership/self-parenting, BANK classification, and configured-Banking protections before commit.
- Update `ChartOfAccountsPanel` to retain `editingAccountId`, clear it for New, construct `AccountCommand`, save through the stable-ID service path, and reselect by ID rather than code.
- Add visible lifecycle guidance: clearing Active and saving retires an account while preserving history; no Delete placeholder is added.
- Add unit/integration/UI-source regression coverage for stable ID, reference preservation, duplicate-code rollback, self-parent prevention, form-state identity, New identity clearing, ID-based reload, and absence of a Delete placeholder.
- Update `doc/interface-operation-matrix.md` with the stable-ID account authority and retained lifecycle audit for Journal, Funds, Company/User, Banking, Asset, and Inventory editors.
- Add `doc/P17-C2-durable-record-lifecycle-user-testing.md` for owner acceptance.

Validation status:

- Repository/PR state verified against current `main`; no pre-existing P17-C2 branch or open PR existed before this run.
- Local Maven execution is unavailable in the connected GitHub-only runtime; no local Maven result is claimed.
- GitHub Maven PR Tests are required after publication. Record the exact workflow run/head here before moving to `VERIFYING` or `DONE`.

Known failures:

- None recorded before publication. Any GitHub Actions failure must be recovered from the exact failing job/log and corrected on this branch before owner acceptance.

Owner acceptance:

- Follow `doc/P17-C2-durable-record-lifecycle-user-testing.md` after CI is green.
- Do not merge until the owner accepts the desktop checklist.

Next exact action:

- Publish the implementation on the recorded fresh branch, open a draft PR to `main`, run/inspect Maven PR Tests, correct any exact CI failures, then stop before merge for owner desktop acceptance.
