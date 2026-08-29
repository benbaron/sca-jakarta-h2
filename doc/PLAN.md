---
plan_version: 235
active_phase: P17
active_slice: P17-C12
active_status: VERIFYING
active_branch: codex/P17-C12-documentation-authority-reconciliation
active_pull_request: 305
active_head: d9c20734f03cd9280e9036bb8d826e4c38a9309d
next_action: "Validate the successor head after this ledger update in Maven PR Tests; require clean verify, repeat tests, and production JavaFX route compliance green, then update PR evidence and stop before merge for owner acceptance."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and source of truth

This document is the current phase controller and execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and one selected slice at a time under root `AGENTS.md`.

The former monolithic execution ledger is preserved at `doc/archive/PLAN-pre-P17-C2.md`. Current `main`, merged pull requests, migrations, tests, governing documents, and this controller are authoritative over historical notes.

A slice is `DONE` only when its behavior/documentation is merged, required validation is green, governing documentation is current, and required owner acceptance is complete.

## 2. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for original contracts; richer depreciation batching is P18 |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction completed in P17-C9 |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for original contracts; deferred Company Admin extensions are P19 |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, durable-record, and documentation corrections | C1-C11 DONE; C12 VERIFYING |
| P18 | Depreciation-run workflow completion | BLOCKED pending P17 completion and batch-semantics review |
| P19 | Deferred Company Administration extensions | BLOCKED pending explicit product requirements for each slice |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query services own read projections.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, or period-close authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics rather than invented hard deletion.
- Company-specific money/date/table/divider state remains H2-backed.
- Compatibility identifiers/APIs may remain only when a current compatibility path requires them; retired customer-facing terminology and duplicate production panels do not remain merely because historical tests or documents reference them.
- Historical H2 tables are not deleted merely because a UI or compatibility API is retired; schema cleanup requires an explicit nondestructive migration decision.
- Historical/archive documents remain historical evidence. Current governing documents must describe current production authority.

## 4. P17 execution ledger

### P17-C1 — Cross-cutting UI design-rule compliance

Status: DONE.

PRs #290, #291, and #292 merged the shared UI policy corrections. Corrective evidence/regression PR #304 merged to `main` at `c2e9d27087d40cc61114b98dbcc2b3ed55a395fd` after its stale import/export source guard was aligned with the P17-C11 shell-retirement architecture. No retired shell method was restored.

### P17-C2 — Durable account record lifecycle

Status: DONE. PR #294 merged at `30920323fb7f2d8fd786bad7e0225ca4aa484198`.

### P17-C3 — Budget version lifecycle completion

Status: DONE. PR #295 merged at `beeb8121be7bfe53fa7444bbc6187d1d7ee534fc`.

### P17-C4 — Banking durable-record lifecycle

Status: DONE. PR #296 merged at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`.

### P17-C5 — Inventory item lifecycle completion

Status: DONE. PR #297 merged at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: DONE. PR #298 merged at `d067877d699f4aa05c635b52abcc0aa65d55fbc3`.

### P17-C7 — Fixed-asset status lifecycle completion

Status: DONE. PR #299 merged at `e053a7430f47824529b1c55d080d596f4b5e84a5`.

### P17-C8 — Dashboard production-compliance correction

Status: DONE. PR #300 merged at `8f66bcfce86a411b6c1d5af6209bb062803af742`.

### P17-C9 — Report Library active-period synchronization

Status: DONE. PR #301 merged at `2cac5dc3275f08a5f175332b030c3f336e94c5d0`.

### P17-C10 — Retire duplicate legacy UI panels and stale customer-panel architecture

Status: DONE.

PR #302 merged at `c7df4252681454f9f37584b14092b41155f8be51`.

Completed outcomes:

- unreachable alternate Dashboard/reference workspaces and standalone Journal/Ledger Register/Transaction Editor source panels were removed;
- disconnected `CustomerUiPanelCatalog` / `CustomerPanelId` prototype architecture was removed;
- `AppPanelId.LEDGER_REGISTER` and `TXN_EDITOR` remain compatibility aliases to canonical Journal only;
- no accounting, persistence, migration, banking, reconciliation, report-calculation, or period-close authority changed.

### P17-C11 — Retire legacy shell/date-range and classify residual compatibility services

Status: DONE.

PR #303 merged at `7427da72e37f29d3016afb67c1aa35931c01a897`.

Completed outcomes:

- application-wide session ownership moved from obsolete JavaFX `MainWindow` to dedicated `ApplicationSessionContext` / `UiSessionState` authority;
- `MainWindow` is a deprecated non-JavaFX compatibility facade only;
- dead `DateRangeSelector` and `DateRangeUtil` were removed while `DateRange` / `DateRangeContext` remain for intentional Report Library explicit-range behavior;
- obsolete legacy shell Find/command-palette/window behavior remains retired;
- `ScheduleEligibilityService` remains only as an unrouted compatibility/domain query where current metadata consumers require it; no Schedules workspace is restored;
- reconciliation-run and period-close-run compatibility repositories/services remain only where current SCLX/comparison/history paths still consume them;
- no historical H2 tables were deleted.

### P17-C12 — Documentation authority reconciliation

Status: VERIFYING.

Branch: `codex/P17-C12-documentation-authority-reconciliation`
Starting base: merged `main` `c2e9d27087d40cc61114b98dbcc2b3ed55a395fd`
Pull request: #305
Corrective head before this ledger update: `d9c20734f03cd9280e9036bb8d826e4c38a9309d`

Purpose:

Reconcile live governing documentation with the architecture actually merged through P17-C11. This is a documentation-authority slice, not a product redesign.

Audit findings corrected in this branch:

1. `doc/interface-operation-matrix.md` had a P17-C5-era status and still described import/export as legacy main-window actions. It is now current through P17-C11 and identifies `ProductionWorkspaceWindow`, current command capabilities, format-specific import/export authority, current durable-record lifecycle behavior, and retained compatibility classifications.
2. `doc/persistence-authority-inventory.md` had a P16-S17-era status, called reconciliation mismatch/edit workflow incomplete, described fixed-asset reporting as future work, and described inventory reporting as later work. It now records current reconciliation, period-close, fixed-asset, inventory, audit, shell/session, and interchange authority while fencing retained compatibility stores from production authority.
3. `doc/testing/production-workspace-test-plan.md` still described a generic staged-import session and separately visible Ledger Register/editor geometry. It now tests current COA CSV/JSON, OFX/QFX/CSV, SCLX, and whole-database boundaries and the one canonical Journal workspace.
4. `doc/accounting/transaction-editor-and-journal.md` still claimed line-level cleared-state projection was unavailable. It now records P16-S10 service-projected read-only `Not bank` / `Uncleared` / `Cleared` / `Mixed` behavior and reconciliation-owned mutation.
5. Added `doc/P17-C12-documentation-authority-reconciliation-user-testing.md` so owner acceptance checks documentation against current reachable production without inventing new product behavior.
6. Historical/archive documents are intentionally left unchanged as historical evidence.

Verification correction:

- Initial PR head `211a75d666ef8c592bfb2cc1ad301cf20e163247` failed Maven PR Tests run `33225474337`, job `99028235949`, in `SchedulesEliminationSourceTest.interfaceOperationMatrixDoesNotListSchedulesPanel` because the reconciled interface matrix said “former top-level Schedules destination” but the source guard deliberately requires the documentation to retain the explicit phrase “former top-level Schedules panel”.
- The failure did not indicate a missing production route or class. `SchedulesPanel.java` remains absent and no Schedules factory/navigation route is restored.
- Corrective commit `d9c20734f03cd9280e9036bb8d826e4c38a9309d` changes the governing sentence to “former top-level Schedules panel/destination”, preserving the architectural fact and the elimination guard without reintroducing product behavior.
- A successor exact-head Maven PR Tests run is required after this ledger update.

Guardrails:

- no production Java changes unless the audit uncovers a separate live defect that is first planned as its own corrective slice;
- no migrations/schema changes;
- no restoration of Ledger Register, Transaction Editor, Schedules, generic Import/Export Jobs, approval workflow, or legacy MainWindow authority;
- no rewrite of historical/archive documents to make them appear current;
- if owner/manual checks reveal a real product/document mismatch, create a separately numbered corrective slice rather than masking it in C12.

Validation:

- no local Maven result is claimed;
- final exact branch head must pass repository Maven PR Tests, including clean verify, repeat tests, and production JavaFX route compliance;
- owner consistency checks are recorded in `doc/P17-C12-documentation-authority-reconciliation-user-testing.md`;
- stop before merge for owner acceptance.

Next exact action:

- validate the successor exact branch head after this ledger update, record the final workflow/run result in PR #305, and stop before merge for owner acceptance.

## 5. P18 — Depreciation-run workflow completion

Status: BLOCKED pending P17 completion and explicit batch-semantics review.

### P18-S1 — Period batching and Report Library integration

Purpose: complete a user-usable accounting-period depreciation workflow by building on `FixedAssetService.runMonthlyDepreciation(...)`, not a second depreciation engine.

Before implementation, inspect and decide:

- eligible assets for one accounting period;
- per-asset preview amounts and exclusions;
- idempotency when a monthly run already exists;
- closed-period and lifecycle protection;
- all-or-nothing multiasset transaction versus orchestrated independently atomic governed asset runs;
- failure/retry semantics;
- exact integration with the existing Fixed Asset Depreciation History & Schedule report.

No batch implementation may weaken existing per-asset canonical transaction, audit, portable-identity, reversal, or lifecycle authority.

## 6. P19 — Deferred Company Administration extensions

Status: BLOCKED pending explicit product requirements and persistence inspection.

### P19-S1 — Company chart assignment administration

Define the durable company↔chart relationship, safe reassignment rules, and interactions with existing accounts, reports, imports, and company switching before any migration or overwrite behavior is introduced.

### P19-S2 — Company reporting-default administration

Define persisted policy versus transient UI convenience and expose only defaults with real production consumers. Reuse existing preference authority; do not create a second preference store.

### P19-S3 — Company tax-filing metadata administration

Blocked until the owner specifies the required filing identities, periods, fields, and their reporting/export consumers.

## 7. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements. No credentials or enforcement are implemented without separate authorization.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE. Enforce approved permissions at authoritative service boundaries; JavaFX reflects permissions rather than defining them.

## 8. Audit-to-plan mapping

| Audit finding | Planned owner |
|---|---|
| Dashboard retired Ledger Register wording / self-targeting Quick Links / old SCLX terminology | P17-C8 — DONE |
| Report Library active-period synchronization | P17-C9 — DONE |
| Duplicate old Dashboard/Journal/reference panels and stale customer-panel architecture | P17-C10 — DONE |
| Obsolete MainWindow shell/date-range behavior and residual compatibility classification | P17-C11 — DONE |
| Stale interface matrix / persistence inventory / workspace test-plan / Journal authority claims | P17-C12 — VERIFYING |
| Depreciation Runs richer batching/report integration | P18-S1 |
| Company chart assignment editor deferral | P19-S1 |
| Company reporting-default editor deferral | P19-S2 |
| Company tax-filing editor deferral | P19-S3 |
| Authentication/runtime-permission deferral | P20-S1 through P20-S3 |

## 9. Advancement rule

Execute only the active slice. Do not advance to P18 until P17-C12 is merged, its required Actions validation is green, and owner acceptance is complete. After that merge, rescan current `main` before selecting P18; do not rely on this controller if a newer corrective slice has been added meanwhile.
