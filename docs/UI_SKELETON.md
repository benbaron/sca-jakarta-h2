# UI Skeleton (JavaFX) — Notes

## Run
    mvn -q -Pui javafx:run

## Your panel-splitting decisions (applied)
1. Ledger: **two panels**
   - Ledger Register
   - Transaction Editor
2. Schedules: **one tabbed panel**
   - Schedule tabs are enabled/disabled based on selected Account (like Excel)
3. Budget: **two panels**
   - Budget Editor (input)
   - Budget vs Actual (output)
4. Assets: **two panels**
   - Asset Register (input)
   - Depreciation Runs (wizard/output)
5. Reports: **library panel**
   - report list + parameter area + preview placeholder
6. Inspector: **details first**
   - journal is a drill-down (button/menu), not default inspector contents

## What’s implemented (skeleton level)
- Office-style MenuBar + ToolBar
- Left navigation with grouped sections
- Center workspace host
- Right inspector pane (details-first placeholder)
- Transaction Editor split lines: start minimal (2) and add more via + Add Line
- Schedules gating: placeholder mapping in `SchedulesPanel.applyGating()`

## Next wiring steps
- Add a small “Selection Bus” so right-click/double-click in panels updates the shared InspectorPane (instead of local alerts).
- Drive schedule gating from DB: `account_schedule_requirement` records (or equivalent).
- Create real TableView models for Txn + Split using your signed-amount conventions.


Schedule gating will be driven by CoA subtype (plus per-account overrides).


Standard CoA subtypes: Receivable, Payable, Prepaid, DeferredRevenue, Inventory, FixedAsset, OtherAsset, OtherLiability, Cash.


SchedulesPanel now loads accounts from DB via AccountLookupService (active posting accounts) and gates tabs using ScheduleEligibilityService.allowedScheduleKindCodes(account).


Global toolbar now includes a Date Range selector with presets: all, quarter, year, fiscal year, and custom date pickers.
