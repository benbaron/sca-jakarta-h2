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

## Service and UI boundary

P04-S1 established the model and migration. P04-S2 adds `BudgetPlanService` as the application boundary for draft creation, draft line replacement, validation, activation, archive, active-version selection, and actual/variance queries. Activation makes exactly one budget version active for a fiscal year and archives the prior active version; it is not an approval workflow.

P04-S3 converted the Budget Editor and Budget vs Actual panels to `BudgetPlanService`. P16-S6 corrects the remaining lifecycle and fiscal-period assumptions: loading a budget workspace is now read-only with respect to durable plan creation, editable drafts and the active version are listed by stable database ID, Save updates and reselects the exact selected draft, and a revision is created only by the explicit **Create Revision** command from the selected active plan. A revision copies the active plan header period and governed lines before editing. **Activate Version** targets only the explicitly selected draft; activation serializes company/year activation so one active version remains authoritative.

The fiscal-year label is the calendar year in which the company's configured fiscal year starts. `FiscalPeriodRange` derives the fiscal-year start/end from the authoritative company fiscal month/day and derives the selected accounting-period start/end from the shell-selected period start. The period end is the day before the next monthly accounting period, capped at fiscal year end. Budget actuals start at the fiscal-year start and end at the selected accounting-period end; they never substitute the wall-clock year or `LocalDate.now()`. Annual budget lines remain applicable for the whole plan, while monthly lines are included through the selected accounting period.

Budget Editor and Budget vs Actual consume this same fiscal request. When Report Library has no explicit `DateRangeContext` range, its default request starts at the same fiscal-year start and ends at the same selected accounting-period end; preview and export continue to reuse the resulting immutable `ReportRequest`. Explicit report date parameters remain user-controlled. Dashboard budget cards continue to consume `DashboardQueryService`; changing their broader dashboard projection semantics is outside P16-S6. The legacy sidecar `BudgetTargetPersistence` file store remains removed and `UiWorkspaceDataStore` exposes no budget target maps.

## P17-C3 retained version lifecycle

P17-C3 closes the Budget Editor durable-record lifecycle gap without adding physical deletion. The editor lists retained `DRAFT`, `ACTIVE`, and `ARCHIVED` versions for the selected fiscal year by stable database ID. An abandoned draft may be retired explicitly with **Archive Draft**; the service requires that the selected version still be `DRAFT`, preserves the plan and every line, records `archived_at`, and returns the same durable plan ID.

An `ACTIVE` version is not manually archived from the editor. It remains authoritative until another explicitly selected draft is activated, at which point the existing activation transaction archives the prior active version and activates the replacement atomically. Explicit draft archival takes the same company write lock as activation, so an archive/activate race cannot silently retire a version after it becomes active. Archived versions remain selectable as read-only history; their stored annual organization-wide category lines are rendered from the retained line projection even if a category is no longer active. The editor provides visible lifecycle guidance instead of a Delete button. The donor repository's non-persistent **Delete Selected** budget-row placeholder is not ported.
