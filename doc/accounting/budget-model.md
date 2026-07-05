# Persistent budget model

P04 replaces sidecar budget targets with normalized H2 persistence. Budget values shown in the application must come from `budget_plan` and `budget_line` rows, authoritative accounting actuals, or a neutral no-budget state.

## Concepts

- `BudgetCategory` remains a master-data classification separate from account and activity.
- `BudgetPlan` is a versioned fiscal-year header. A plan starts as `DRAFT`, one version can be selected for comparison by moving it to `ACTIVE`, and superseded versions are retained as `ARCHIVED` history.
- `BudgetLine` stores one planned amount for a budget category, optional fund, and optional `YYYY-MM` fiscal period. A null fund means the line is organization-wide for the category. A null period means the amount applies to the whole plan period.

## Persistence rules

- Schema changes are nondestructive and start with migration `V50__budget_plan_and_line.sql`.
- Budget amounts use `NUMERIC(19,4)`/`BigDecimal`.
- Plan fiscal year and version code are unique so history can preserve multiple versions without overwriting prior plans.
- Line scope is unique per plan, budget category, optional fund, and optional period.
- Activation selects the comparison version; it is not an approval workflow.
- Sidecar/static budget persistence is deprecated and must not remain authoritative after P04-S3.

## Service and UI follow-up

P04-S1 established the model and migration. P04-S2 adds `BudgetPlanService` as the application boundary for draft creation, draft line replacement, validation, activation, archive, active-version selection, and actual/variance queries. Activation makes exactly one budget version active for a fiscal year and archives the prior active version; it is not an approval workflow. P04-S3 converts Budget Editor, Budget vs Actual, Dashboard Budget Performance, and YTD comparisons to those services and removes sidecar budget storage.
