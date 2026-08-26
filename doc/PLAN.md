---
plan_version: 218
active_phase: P17
active_slice: P17-C4
active_status: IN_PROGRESS
active_branch: codex/P17-C4-banking-lifecycle
active_pull_request: pending
active_head: 93fc1f06bdc458fcbd98abfe2da95731a47a5e5b
next_action: "Open the P17-C4 draft PR, run Maven PR Tests, correct any findings, complete the owner Banking lifecycle checklist, and stop before merge until owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. P17-C3 merged through PR #295 at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc` after the owner confirmed the Budget version lifecycle checklist and directed work to continue to C4.

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
| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 IN_PROGRESS |

## 4. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledgers, budget stores, import stores, record stores, or prototype maintenance paths.
- Disabled placeholder Delete buttons are not part of the UI contract. A Delete action is shown only when it performs a real governed operation.
- Durable records that must preserve history use deactivation, archive, disposition, correction, reversal, or another governed lifecycle rather than invented generic hard deletion.
- Company-specific money/date/table/divider state remains H2-backed.

## 5. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE.

Completion evidence:

- PRs #290, #291, and #292 merged the authorized shared UI policy corrections.
- Exact final code head `aca71b43ba7e78670986e9f79698cc60239079f3` passed Maven PR Tests run `32917267993`.

### P17-C2 — Durable account record lifecycle

Status: DONE.

Completion evidence:

- PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`.
- Exact final C2 head `ce85bad0810ca33de778e4354b8fa4a7ff74f0c5` passed Maven PR Tests run `32923532902`.
- Interactive Chart of Accounts uses stable account ID and governed Active/deactivation lifecycle without placeholder Delete.

### P17-C3 — Budget version lifecycle completion

Status: DONE.

Completion evidence:

- PR #295 merged to `main` at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc` after owner confirmation.
- Budget Editor retains `DRAFT`, `ACTIVE`, and `ARCHIVED` versions by stable `BudgetPlan.id`.
- Explicit archival is draft-only; active versions retire only through governed replacement activation.
- Archived line history remains read-only and visible even when a category later becomes inactive.
- Initial C3 implementation head `47975931f3249667e6c8c0c3e89f0e516b2e7558` passed all Maven PR Tests gates in run `32926472161`; the final audit correction was merged only after owner acceptance.

### P17-C4 — Banking durable-record lifecycle

Status: IN_PROGRESS.

Branch: `codex/P17-C4-banking-lifecycle`  
Starting base: `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`  
Pull request: pending

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/banking/banking-and-reconciliation.md`
- `doc/banking/banking-lifecycle.md`

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/Bank.java`
- `src/main/java/org/nonprofitbookkeeping/model/CompanyBankAccount.java`
- `src/main/java/org/nonprofitbookkeeping/service/BankConfigurationService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/BankingPanel.java`
- `src/test/java/org/nonprofitbookkeeping/service/BankConfigurationServiceTest.java`
- production-route/core-editor compliance tests touching Banking

Purpose:

- Complete the next durable-record lifecycle gap after Budget versions.
- Preserve Bank and configured bank-account history through Active/inactive state rather than physical deletion.
- Prevent an invalid active configured bank account from existing beneath an inactive Bank.
- Keep bank-statement import, canonical ledger, reconciliation, and Chart-account authority unchanged.

Deliverables:

- Serialize Bank and configured-account lifecycle writes on the owning Company.
- Reject Bank deactivation while any active configured account references it.
- Reject creation/reactivation of an active configured account under an inactive Bank.
- Retain both record families and their stable IDs after deactivation; do not add generic Delete.
- Add focused H2 lifecycle regressions.
- Document the authority in `doc/banking/banking-lifecycle.md` and add `doc/P17-C4-banking-lifecycle-user-testing.md`.
- Reconcile the stale post-C3 PLAN controller against merged PR #295.

Validation status:

- Exact starting `main` is merge commit `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`.
- Service implementation and focused H2 regression sources are published on the fresh C4 branch.
- No local Maven result is claimed; GitHub Maven PR Tests are required after the draft PR opens.

Known failures:

- None currently known.

Owner acceptance:

- Follow `doc/P17-C4-banking-lifecycle-user-testing.md` after final-head CI is green.
- Do not merge until the owner accepts the checklist.

Next exact action:

- Open the draft PR, run Maven PR Tests, inspect all required gates, correct any failure, update this record to `VERIFYING`, and stop before merge for owner desktop acceptance.
