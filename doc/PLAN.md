---
plan_version: 224
active_phase: P17
active_slice: P17-C7
active_status: IN_PROGRESS
active_branch: codex/P17-C7-fixed-asset-lifecycle
active_pull_request: pending
active_head: d067877d699f4aa05c635b52abcc0aa65d55fbc3
next_action: "Complete fixed-asset status lifecycle authority: make ACTIVE/INACTIVE explicit audited actions, preserve DISPOSED as Sale/Retirement-owned, serialize metadata/depreciation/lifecycle writes on the asset lock, update UI/docs/tests, publish a draft PR, run Maven PR Tests, and stop before merge for owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and historical ledger

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

The former monolithic execution ledger is preserved byte-for-byte at `doc/archive/PLAN-pre-P17-C2.md`. Read that archive when detailed execution history for P00-P16 or older corrective slices is required. Current repository code, migrations, tests, merged pull requests, governing documents, and this controller are authoritative over stale historical statements.

P17-C2 merged through PR #294 at `30920323fb7f2d8fd786bad7e0225ca4aa484198`. P17-C3 merged through PR #295 at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`. P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`. P17-C5 merged through PR #297 at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`; the owner explicitly accepted C5 and Maven PR Tests push run `33028403587` passed on that exact merged `main` head before C6 began.

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
| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 DONE; C6 DONE; C7 IN_PROGRESS |

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

Status: DONE.

Completion evidence:

- PR #296 merged to `main` at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4` after owner acceptance.
- Bank/configured-account lifecycle changes serialize on company authority; a Bank cannot be inactive while an active configured account points to it, and an active configured account cannot be created/reactivated under an inactive Bank.
- The same active-parent invariant applies to the SCLX/interchange creation seam; inactive records remain durable history and no hard-delete path was introduced.
- Maven PR Tests push run `33020990140` passed on exact merged `main` head `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`.

### P17-C5 — Inventory item lifecycle completion

Status: DONE.

Completion evidence:

- PR #297 merged to `main` at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53` after owner acceptance.
- Inventory lifecycle status is service-owned; ordinary edits cannot silently alter it, zero quantity is required before deactivation/disposal, and disposed items remain terminal retained history.
- Metadata edits, lifecycle transitions, and governed quantity movements serialize on the same durable `InventoryItem` lock; lifecycle transitions write factual audit history.
- Exact merged-main Maven PR Tests run `33028403587` passed on `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: DONE.

Completion evidence:

- PR #298 merged to `main` at `d067877d699f4aa05c635b52abcc0aa65d55fbc3` after owner acceptance.
- Exact final C6 PR head `2c9ebaf804d07b744554b35f03f5a70bc82e764d` passed Maven PR Tests run `33029795711`.
- Exact merged-main push run `33033595424` passed clean verify, repeat tests, and production JavaFX route compliance.
- Active Fund ancestry is enforced across interactive administration, active lookup, and SCLX import/export; lifecycle writes serialize on company authority while inactive hierarchy history remains retained.

### P17-C7 — Fixed-asset status lifecycle completion

Status: IN_PROGRESS.

Branch: `codex/P17-C7-fixed-asset-lifecycle`
Starting base: `d067877d699f4aa05c635b52abcc0aa65d55fbc3`
Pull request: pending

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/data-exchange/sclx.md` for the historical restore boundary

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/FixedAsset.java`
- `src/main/java/org/nonprofitbookkeeping/service/FixedAssetCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java`
- fixed-asset service/lifecycle/SCLX tests and current production-route/core-editor compliance tests

Purpose:

- Complete one fixed-asset lifecycle authority without changing Sale, Retirement, Impairment, depreciation calculations, or canonical ledger behavior.
- Preserve stable `FixedAsset.id` history while removing ACTIVE/INACTIVE status mutation from ordinary metadata editing.
- Keep `DISPOSED` exclusively owned by confirmed Sale/Retirement and restored only by domain lifecycle reversal.

Audit finding and selected direction:

- The financial lifecycle is already strong: Sale/Retirement/Impairment use frozen previews, canonical transactions, lifecycle events, audit facts, and pessimistic revalidation; `DISPOSED` is not directly editable.
- `AssetsRegisterPanel`, however, still exposes ACTIVE/INACTIVE in the ordinary asset form, and `FixedAssetService.update(...)` copies that requested status while loading the asset without a pessimistic lock.
- `runMonthlyDepreciation(...)` likewise reads the asset without the lock used by lifecycle commit, so metadata/status/depreciation operations can race the financial lifecycle boundary.
- Company Admin and User Admin already expose governed retained-history lifecycle behavior, while fixed assets remain the next concrete P17 durable-record gap.

Planned deliverables:

- Interactive asset creation starts ACTIVE; ordinary metadata updates preserve the persisted status and cannot change lifecycle state.
- Add explicit audited ACTIVE <-> INACTIVE service actions with factual actor/reason. `DISPOSED` remains unavailable to that action and requires Sale/Retirement; disposed assets require lifecycle reversal before any reactivation/edit.
- Serialize ordinary asset updates, explicit status changes, monthly depreciation, lifecycle commit, and lifecycle reversal through the same pessimistic `FixedAsset` lock before validating/mutating the asset.
- Keep SCLX `createForImport(...)` as the caller-owned historical source-status restore seam without fabricating local lifecycle audit facts.
- Replace the editable status combo with read-only status plus explicit Deactivate/Reactivate controls, retained-history/no-delete guidance, and the existing financial lifecycle controls.
- Add focused H2 lifecycle/audit regressions, concurrency/source/UI guardrails, governing documentation/matrix updates, and an owner desktop checklist.

Validation status:

- Exact starting `main` is `d067877d699f4aa05c635b52abcc0aa65d55fbc3`.
- Merged-main Maven PR Tests push run `33033595424` passed all three repository gates and is the C7 baseline.
- No local Maven result is claimed; GitHub Maven PR Tests will be authoritative after publication.

Known failures:

- None at task start.

Owner acceptance:

- A P17-C7 owner checklist will be added before handoff.
- Do not merge until final-head GitHub validation passes and the owner accepts the checklist.

Next exact action:

- Implement the fixed-asset status/lifecycle lock corrections and UI lifecycle actions, add focused regressions and governing documentation, publish a draft PR, run Maven PR Tests, correct any failure, then stop before merge for owner desktop acceptance.
