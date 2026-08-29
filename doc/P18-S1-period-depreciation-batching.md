# P18-S1 — Accounting-period depreciation batching

## Purpose

P18-S1 completes a user-usable accounting-period depreciation workflow without introducing a second depreciation engine, ledger writer, or batch persistence model.

The authoritative write remains `FixedAssetService.runMonthlyDepreciation(...)`. That operation already owns the fixed-asset lock, account/fund/lifecycle validation, closed-period protection, canonical transaction, depreciation-run row, portable identities, factual audit, and rollback boundary.

## Accounting-period authority

The Depreciation Runs workspace uses the shell-selected accounting period and the configured period start day. For selected period `P`:

- `periodStart = ActivePeriodContext.periodStartFor(P, configuredStartDay)`;
- `periodEnd = periodStart.plusMonths(1).minusDays(1)`;
- every new run in the batch uses `periodEnd` as its deterministic depreciation posting date.

The workspace no longer asks the operator for an arbitrary depreciation date.

## Preview classifications

`DepreciationPeriodBatchService` is an orchestration/query layer over `FixedAssetService.listAssets(...)`, `listDepreciationRuns(...)`, and `runMonthlyDepreciation(...)`. It does not use JPA or create transactions itself.

The preview freezes the current company, period, posting date, asset set, and proposed amounts. Each asset is classified as one of:

- `ELIGIBLE` — active, acquired by period end, has a positive next amount, has no completed run in the period, and has no later depreciation run;
- `ALREADY_RUN` — a durable completed depreciation run already exists anywhere inside the selected accounting period;
- `LATER_RUN_EXISTS` — later depreciation exists; the earlier period is not backfilled because the authoritative per-asset calculation includes completed run history. Later activity must be corrected/reversed before chronological backfill;
- `INACTIVE` — lifecycle state is not `ACTIVE`;
- `NOT_ACQUIRED` — acquisition is after period end;
- `NO_REMAINING_BASIS` — no positive next depreciation amount remains.

Preview exclusions are explanatory, not a replacement for the authoritative service. Every attempted commit is revalidated by `FixedAssetService` at write time.

## Commit and retry semantics

The batch deliberately uses **independently atomic governed asset runs**, not one multi-asset database transaction.

On confirmation:

1. The orchestrator re-previews the same frozen company/period.
2. A previously eligible asset is skipped if its current eligibility or proposed amount changed.
3. Each still-eligible asset is passed separately to `FixedAssetService.runMonthlyDepreciation(assetId, periodEnd, notes)`.
4. One asset failure is reported and does not roll back earlier successful assets.
5. The orchestrator continues through the frozen eligible set and returns exact `COMMITTED`, `SKIPPED`, and `FAILED` outcomes.

Retry is naturally idempotent at the period-workflow level: successfully committed assets are seen as `ALREADY_RUN` on the next preview, so only still-eligible assets are attempted. The existing database uniqueness on `(fixed_asset_id, run_date)` remains the final exact-date concurrency guard.

This choice preserves one canonical transaction and one durable depreciation-run fact per asset. There is no synthetic batch transaction and no second audit model.

## Report Library integration

The production Report Library remains the single reporting surface. **Open Depreciation Report** hands the selected accounting period to `DateRangeContext` and opens the canonical `REPORT_LIBRARY` destination through `DrillThroughCoordinator`.

The target report is **Fixed Asset Depreciation History & Schedule**. It remains read-only: completed runs in the period use durable transaction IDs, while the schedule summary is a projection and creates no future accounting facts. If Report Library is already open on another report, the operator selects Fixed Asset Depreciation History & Schedule there; no parallel report-selection state or second report panel is introduced.

## UI and design-rule contract

- The accounting-period preview and completed-run history remain separate resizable `SplitPane` table regions.
- Both tables use sortable, resizable, reorderable columns and company-owned table/divider state through the production composition boundary.
- Money/date display uses `CompanyUiFormat`.
- The commit action requires explicit confirmation showing period, posting date, eligible count, total proposed amount, and the independent-atomic failure semantics.
- Full service failures remain visible in status text; failures are not represented as successful batch completion.

## Validation

Required automated coverage:

- period classification, including any run inside the period and later-run chronology fencing;
- proposed total from eligible assets only;
- preview creates no accounting write;
- frozen-preview amount/state revalidation before execution;
- one asset failure does not erase prior success;
- retry skips prior successes and can commit a previously failed asset;
- production UI source guard for active-period calculation, confirmation, asynchronous preview/run operations, and Report Library handoff;
- repository Maven PR Tests: clean verification, repeat tests, and production JavaFX route compliance.

Manual acceptance is recorded in `doc/P18-S1-period-depreciation-batching-user-testing.md`.
