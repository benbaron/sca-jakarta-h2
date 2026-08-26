---
plan_version: 216
active_phase: P17
active_slice: P17-C2
active_status: VERIFYING
active_branch: codex/P17-C2-durable-record-lifecycle
active_pull_request: 294
active_head: 32916efa63122d3ce7d0fd8b00b8c1e57711bbf9
next_action: "Verify GitHub Maven PR Tests on the final documentation-record head, then complete the owner durable-account lifecycle checklist and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic pre-P17-C2 ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

This revision reconciles repository state that the prior controller had not caught up with:

- P05-C6 is DONE through merged PR #285 and corrective merged PR #286; its obsolete `IN_PROGRESS` controller declaration is removed.
- P11-C2 is DONE through merged PRs #283 and #284 plus owner acceptance.
- P16 is DONE through corrective P16-C11 / merged PR #281.
- P17-C1 is DONE through merged PR #291 and CI-correction PR #292; the P17-C2 starting `main` is `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- P17-C2 is the active owner-authorized corrective slice on fresh branch `codex/P17-C2-durable-record-lifecycle`, published as draft PR #294.

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
| P05 | Banking configuration and statement import | DONE through P05-C6 / PR #285 plus corrective PR #286 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE |
| P11 | Report Library | DONE through P11-C2 / PR #284 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | UI design-rule and durable-record lifecycle corrections | P17-C1 DONE; P17-C2 VERIFYING |

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

Historical implementation detail may be read from `doc/archive/PLAN-pre-P17-C2.md` only as needed.

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

- PR #291 merged at `0e61ba578ec9a478424ab4206b140334c63ada9a`.
- PR #292 merged at `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1` after the CI-driven migration-number, bank-account timestamp-identity, and active-period corrections.

Next exact action: none; P17-C1 is merged and must not be reopened for P17-C2 work.

### P17-C2 — Durable record lifecycle completion

Status: VERIFYING.

Branch: `codex/P17-C2-durable-record-lifecycle`  
Starting base: `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`  
Pull request: #294 (draft)  
Validated implementation head: `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9`

Purpose:

- Correct interactive Chart of Accounts maintenance so an account's durable database ID, not its editable account code, selects the row being updated.
- Ensure an account code change updates one existing row and preserves ledger, Banking, report, import-identity, and other durable references to that account.
- Enforce active-company/active-chart ownership and chart-scoped code uniqueness on stable-ID saves.
- Preserve existing Banking classification protections and parent-account validation.
- Retain **Active** as the account retirement lifecycle. Do not invent a generic Delete operation for accounts that participate in accounting history.
- Audit the remaining durable editors for lifecycle consistency without changing already-governed Journal, Funds, Company, Asset, Inventory, Banking, or User semantics.

Completed deliverables:

- Added immutable `AccountCommand` with nullable durable ID.
- Added `AccountAdminService.save(AccountCommand)` so create uses null ID and update requires the exact account ID; code-addressed `upsert` remains only as a compatibility/batch/auto-create boundary.
- Added active-company/active-chart ownership validation, duplicate account-code rejection excluding the edited ID, parent membership/self-parent protection, BANK classification validation, and the existing configured-Banking protection on stable-ID saves.
- Updated `ChartOfAccountsPanel` to retain `editingAccountId`, clear it for New, construct `AccountCommand`, save through the stable-ID service path, and reselect by ID rather than code.
- Added visible lifecycle guidance: clearing Active and saving retires an account while preserving history; no Delete placeholder is added.
- Added unit/H2 integration/UI-source regression coverage for stable ID, durable Banking-reference preservation, duplicate-code rollback, self-parent prevention, form-state identity, New identity clearing, ID-based reload, and absence of a Delete placeholder.
- Updated the existing core-editor compliance fixture for the new FormState identity field found during the pre-publication source audit.
- Updated `doc/interface-operation-matrix.md` with stable-ID account authority and the retained lifecycle audit for Journal, Funds, Company/User, Banking, Asset, and Inventory editors.
- Added `doc/P17-C2-durable-record-lifecycle-user-testing.md` for owner acceptance.
- Preserved the prior 3,288-line execution ledger byte-for-byte as `doc/archive/PLAN-pre-P17-C2.md` rather than discarding it while replacing its stale active controller.

Validation status:

- Repository state was verified against exact current `main`; no pre-existing P17-C2 branch or open PR existed before this run.
- Draft PR #294 was opened against exact base `a96eb39db1a3ed5b9ece309dcca1d1d046e327e1`.
- Maven PR Tests run `32921328550` on validated implementation head `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9` PASSED on 2026-08-25/26: clean headless verification, repeated test suite, and production JavaFX route compliance all succeeded.
- Local Maven execution is unavailable in the connected GitHub-only runtime; no local Maven result is claimed.
- This PLAN record is a documentation-only head change. GitHub Maven PR Tests must also pass on that final head before owner acceptance; the final run/head may be recorded in the PR without creating another recursive documentation-only CI head.

Known failures:

- None on validated implementation head `32916efa63122d3ce7d0fd8b00b8c1e57711bbf9`.

Owner acceptance:

- Follow `doc/P17-C2-durable-record-lifecycle-user-testing.md` after final-head CI is green.
- Do not merge until the owner accepts the desktop checklist.

Next exact action:

- Verify Maven PR Tests on the final documentation-record head, then stop before merge for owner desktop acceptance.
