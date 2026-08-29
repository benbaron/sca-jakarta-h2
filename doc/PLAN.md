---
plan_version: 236
active_phase: P18
active_slice: P18-S1
active_status: IN_PROGRESS
active_branch: codex/P18-S1-period-depreciation-batching
active_pull_request: null
active_head: d2a945e8b2ed5f761009aa5e1ea24ad1ea6d4617
next_action: "Review the P18-S1 batch service/UI/tests/docs from current merged main, correct compile/test issues, publish a draft PR, require exact-final-head Maven PR Tests green, record owner testing notes, and stop before merge."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose and source of truth

This document is the execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and slice at a time under root `AGENTS.md`. Current `main`, merged PRs, migrations, tests, governing documents, and this controller are authoritative over archived plans.

A slice is `DONE` only when its behavior/documentation is merged, required validation is green, governing documentation is current, and required owner acceptance is complete.

## 2. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for original contracts |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction P17-C9 DONE |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for original contracts |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, durable-record, documentation corrections | DONE through P17-C12 / PR #305 |
| P18 | Depreciation-run workflow completion | P18-S1 IN_PROGRESS |
| P19 | Deferred Company Administration extensions | BLOCKED pending explicit product requirements |
| P20 | Authentication and runtime authorization | BLOCKED pending explicit security requirements and authorization |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, or report authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider state remains H2-backed.
- Compatibility identifiers/APIs remain only where a current compatibility path requires them.
- Historical/archive documents remain historical evidence; current governing documents describe current production authority.

## 4. P17 completion ledger

P17 is DONE.

- P17-C1: shared UI compliance; PRs #290/#291/#292 plus corrective PR #304 merged.
- P17-C2: durable account lifecycle; PR #294 merged.
- P17-C3: budget-version lifecycle; PR #295 merged.
- P17-C4: banking durable-record lifecycle; PR #296 merged.
- P17-C5: inventory lifecycle; PR #297 merged.
- P17-C6: fund hierarchy lifecycle; PR #298 merged.
- P17-C7: fixed-asset lifecycle; PR #299 merged.
- P17-C8: Dashboard compliance; PR #300 merged.
- P17-C9: Report Library active-period synchronization; PR #301 merged.
- P17-C10: legacy UI retirement; PR #302 merged at `c7df4252681454f9f37584b14092b41155f8be51`.
- P17-C11: legacy shell/session/date-range cleanup; PR #303 merged at `7427da72e37f29d3016afb67c1aa35931c01a897`.
- P17-C12: documentation authority reconciliation; PR #305 final head `0b059e10a2907ad25ee971475317c3196a245193` passed Maven PR Tests run `33226042279`, owner acceptance was confirmed, and PR #305 merged to `main` at `3e87b56b26b189ca27008284734321c54a2ea0ec`.

## 5. P18 — Depreciation-run workflow completion

### P18-S1 — Accounting-period batching and Report Library integration

Status: IN_PROGRESS.

Branch: `codex/P18-S1-period-depreciation-batching`
Starting base: merged `main` `3e87b56b26b189ca27008284734321c54a2ea0ec`
Pull request: none yet.

Governing design: `doc/P18-S1-period-depreciation-batching.md`
Owner verification: `doc/P18-S1-period-depreciation-batching-user-testing.md`

Purpose:

Complete a user-usable accounting-period depreciation workflow by orchestrating the existing `FixedAssetService.runMonthlyDepreciation(...)` authority. Do not create a second depreciation engine, ledger writer, or batch persistence model.

Batch-semantics decision:

- use **independently atomic governed asset runs**, not one multi-asset transaction;
- calculate the period from `ActivePeriodContext` plus the configured period start day;
- use period end as the deterministic run/posting date;
- freeze a preview of company, period, asset set, proposed amounts, and exclusions before confirmation;
- classify any durable run anywhere inside the selected accounting period as `ALREADY_RUN`;
- reject chronological backfill as `LATER_RUN_EXISTS` when a later depreciation run exists, because the authoritative per-asset calculation uses completed run history and later accounting must be corrected first;
- re-preview before each execution pass and skip a previously eligible asset whose state or proposed amount changed;
- call `FixedAssetService.runMonthlyDepreciation(...)` separately for every still-eligible asset so its lock, service validations, closed-period protection, canonical transaction, durable run, portable identity, audit, and rollback remain authoritative;
- continue after an isolated asset failure and report exact committed/skipped/failed outcomes;
- retry by previewing again; previous successes become `ALREADY_RUN`, so only remaining eligible assets are attempted;
- preserve the existing database `(fixed_asset_id, run_date)` uniqueness as the final exact-date concurrency guard;
- hand the selected period to the existing Report Library via `DateRangeContext` / canonical `REPORT_LIBRARY`; the Fixed Asset Depreciation History & Schedule report remains read-only and creates no future transactions.

Implementation in progress:

- new `DepreciationPeriodBatchService` orchestration layer with no JPA/SQL/transaction construction;
- Depreciation Runs UI converted from arbitrary-date/single-asset action to active-period preview, explicit batch confirmation, independent execution summary, and completed-run history;
- explicit Report Library period handoff;
- focused batch service tests for classification, frozen-preview revalidation, partial failure, and retry;
- source guard updated to require the current period workflow and reject the former arbitrary `LocalDate.now()` run-date control;
- governing design and owner-testing notes added.

Guardrails:

- no schema/migration change in this slice unless testing proves a new persistence invariant is required;
- no change to the canonical per-asset transaction/audit/portable-identity writer;
- no synthetic multi-asset transaction or batch table;
- no automatic future schedule posting;
- no report-side accounting writes;
- no hidden retry or silent partial-success claim;
- UI changes must retain company money/date formatting, company-owned table/divider state, scrolling, tooltips, and reachable controls.

Validation:

- no local Maven result is claimed unless it is actually run;
- final exact branch head must pass repository Maven PR Tests including clean verification, repeat tests, and production JavaFX route compliance;
- owner desktop acceptance follows `doc/P18-S1-period-depreciation-batching-user-testing.md`;
- stop before merge.

Next exact action:

Review the implementation for compile/source-guard/UI-policy issues, inspect the final diff, open a draft PR, validate the exact final head in GitHub Actions, correct every failure, and stop before merge for owner acceptance.

## 6. P19 — Deferred Company Administration extensions

Status: BLOCKED pending explicit product requirements and persistence inspection.

### P19-S1 — Company chart assignment administration

Define the durable company↔chart relationship, safe reassignment rules, and interactions with existing accounts, reports, imports, and company switching before implementation.

### P19-S2 — Company reporting-default administration

Define persisted policy versus transient UI convenience and expose only defaults with real production consumers. Reuse existing preference authority.

### P19-S3 — Company tax-filing metadata administration

BLOCKED until the owner specifies required filing identities, periods, fields, and reporting/export consumers.

## 7. P20 — Authentication and runtime authorization

Status: BLOCKED pending explicit security requirements and authorization.

### P20-S1 — Authentication and authorization requirements boundary

Define identity sources, credential ownership, recovery, session lifecycle, audit, threat model, role-permission semantics, bootstrap/last-administrator rules, and migration requirements before implementation.

### P20-S2 — Authentication implementation

BLOCKED until P20-S1 is DONE.

### P20-S3 — Runtime authorization enforcement

BLOCKED until P20-S2 is DONE.

## 8. Advancement rule

Execute only the active slice. Do not advance beyond P18-S1 until its exact final head is green, owner acceptance is complete, and its PR is merged. After merge, rescan current `main` before selecting another slice.
