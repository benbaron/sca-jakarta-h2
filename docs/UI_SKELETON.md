# UI Workspace Design Status (Current)

## Run
```bash
mvn -q -Pui javafx:run
```

## Current panel structure

1. Ledger: **two panels**
   - Ledger Register
   - Transaction Editor
2. Schedules: **one gated runbook panel**
   - Schedule tabs enabled/disabled by account eligibility
   - lifecycle actions: open / settle / write-off
3. Budget: **two panels**
   - Budget Editor (target editing + validation)
   - Budget vs Actual (variance rendering)
4. Assets: **two operational panels**
   - Asset Register (lifecycle actions + runbook)
   - Depreciation Runs (run state lifecycle + history)
5. Inventory: **operational runbook panel**
   - movement actions: receipt / issue / adjust
6. Reports: **library panel**
   - report list + parameter area + preview + file export workflow
7. Inspector: **shared presentation model**
   - panel and navigation context rendered via shared inspector composition

## What is implemented (non-placeholder)

- MenuBar + ToolBar + navigation + right inspector shell.
- Explicit active-panel run command contract (`POST_VALIDATE`).
- Query/filter search with panel jump.
- Journal inspector preferring active panel selection context.
- Import/export and workflow panels:
  - Import Preview
  - Import/Export Jobs
  - Bank Transactions
  - Approval Audit
- Session workspace store with cross-panel projections:
  - bank transactions
  - import/export jobs
  - budget targets
  - operational runbook entries (schedules/assets/depreciation/inventory)
- Durable persistence:
  - budget targets
  - operational runbook logs
- Privilege gating:
  - panel open-time checks
  - restricted menu/toolbar disablement by privilege

## Next wiring priorities

1. Persist operational runbook events into domain/repository records (not only UI projection files).
2. Add deterministic interaction tests for each gated control and high-traffic panel action.
3. Unify runbook event schema and inspector rendering for timeline/history views.
4. Add role-aware visibility (not only enabled/disabled state) for advanced workflows.
5. Continue replacing static help text with context-aware guidance sourced from active panel capabilities.
