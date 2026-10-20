# P26-S3 — Event/fund budget planning and open-item filters

Status IN_PROGRESS. [PLAN](PLAN.md) selects this slice after owner-verified/merged P26-S2. It completes the scoped-planning and reporting-filter portion of adopted G12/A12. Prepaid/deferred recognition remains P26-S4 and inventory valuation remains P26-S5.

## Budget scope and accounting boundary

`BudgetPlan` remains the version/lifecycle authority and `BudgetLine` remains the planned-amount authority. A line may now identify an existing company-owned Event in addition to its existing optional Fund and month. No Event, Fund, Budget Category, transaction, or ledger authority is duplicated.

Event scope is mutually exclusive. A line with an Event compares only with canonical income/expense splits carrying that exact Event. A line with no Event compares only with untagged canonical splits; it is not added to every event-specific plan. Within one category/Event/period scope, an All Funds line and fund-specific lines cannot coexist. Annual and monthly targets retain their existing additive behavior. When an applicable All Funds target exists, actuals aggregate there; otherwise each Fund scope compares independently. Every actual is therefore counted once in the displayed comparison.

Budget Editor exposes explicit Fund and Event scopes and preserves every other scope when one amount is saved. Revision, activation, archive, authorization, company ownership, fiscal-period handling, reversal actuals, and shared Save remain governed by the existing service. Budget vs Actual exposes Fund/Event filters over the same immutable projection and labels general/untagged activity explicitly.

## Supplemental report allocation basis

Accounts Receivable, Accounts Payable, Prepaid Expenses, Deferred Revenue, Other Assets, and Other Liabilities accept stable-ID Fund and Event filters. An item is selected by the Fund/Event on its authoritative opening/increase allocation. Every later reduction, settlement, recognition, correction, and reversal for that item remains in its balance even when the later line uses a different Fund/Event. This opening-allocation basis is shown in TEXT, CSV, and table output.

Control-account reconciliation remains company-wide because filtering one item by its opening dimension while excluding a cross-dimension settlement would create a false partial-ledger tie-out. Filtered output states this boundary. Filters do not rewrite lifecycle facts, fabricate allocations, or create accounting.

## Persistence and portability

Migration V88 adds nullable `activity_id` to `budget_line`, its foreign key/index, and event-aware null-safe scope uniqueness. Existing rows remain general/untagged. SCLX budget lines add an optional `activityId` that references the existing Activity extension identity. Absence stays compatible; strict validation, import ownership, deterministic identity, retry, and rollback cover the event scope.

## Required verification and owner checks

Unit and integration coverage must include two Events and two Funds, general and event-specific actuals, All Funds aggregation, overlap rejection/rollback, revision preservation, ownership/authorization, reversal handling, opening-allocation report filters with a cross-scope settlement, request validation, migration uniqueness, and SCLX sparse compatibility/event identity. Actual JavaFX coverage must confirm both Budget panes expose reachable Fund/Event controls, the existing Save route, role gating, scrolling, and persisted dividers.

In a disposable company, create two Events, two Funds, and a budget draft containing general, event-specific, All Funds, and fund-specific examples without overlapping the same category/Event/month. Save, revise, activate, and filter Budget vs Actual; verify totals count each canonical split once. Create an open item in one Fund/Event and settle part of it from another; filter the applicable supplemental report by the opening Fund/Event and verify the complete item balance remains while the report explains its company-wide reconciliation. Export/import SCLX and confirm all budget scopes return unchanged. No per-check human result is inferred from automated tests.
