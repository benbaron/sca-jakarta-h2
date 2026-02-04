# Subtype → Schedule Mapping (Defaults)

Standard CoA Subtypes (canonical):
- Receivable
- Payable
- Prepaid
- DeferredRevenue
- Inventory
- FixedAsset
- OtherAsset
- OtherLiability
- Cash

## Where the mapping lives
- DB table: `account_subtype_schedule_default` (added in migration V3)
- Seed CLI populates default rows when `--include-schedule-kinds` is true.
- Per-account overrides: `account_schedule_requirement`

## Default mapping (seeded)
- RECEIVABLE        → RECEIVABLE
- PAYABLE           → PAYABLE
- PREPAID           → PREPAID
- DEFERRED_REVENUE  → DEFERRED_REVENUE
- INVENTORY         → INVENTORY
- FIXED_ASSET       → FIXED_ASSET
- OTHER_ASSET       → OTHER_ASSET
- OTHER_LIABILITY   → OTHER_LIABILITY
- CASH              → (none)

## Used by
- ScheduleEligibilityService
- UI SchedulesPanel tab enable/disable (Excel-like gating)
