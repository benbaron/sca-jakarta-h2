# P17-C3 — Interface operation matrix addendum

This focused addendum records the P17-C3 Budget Editor lifecycle correction without rewriting the large historical `doc/interface-operation-matrix.md` inventory. Where the Budget Editor row in that inventory differs from this addendum, this addendum is authoritative for P17-C3 and later work until the matrix is next consolidated.

## BUDGET_EDITOR lifecycle authority

| Workspace | Production panel | Visible operations | Read authority | Write authority | Durable identity/lifecycle | Delete semantics | Acceptance |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `BUDGET_EDITOR` | `BudgetEditorPanel` | retained-version selector; Refresh Budget; New Draft; Create Revision; Save Draft Amount; Activate Version; Archive Draft; category table/editor | `BudgetPlanService.versionsForFiscalYear(...)`, active budget categories, selected fiscal-period range | `BudgetPlanService.createDraft(...)`, `createRevision(...)`, `replaceDraftLines(...)`, `activate(...)`, `archive(...)` | `BudgetPlan.id` is stable identity. `DRAFT` is editable; `ACTIVE` is authoritative for comparison; `ARCHIVED` is retained read-only history. Explicit archive accepts only `DRAFT`. Activating a replacement draft archives the prior active version atomically. | No physical or placeholder Delete. **Archive Draft** retires an abandoned draft while retaining the plan and its lines. Active versions are archived only by governed replacement activation. | `doc/P17-C3-budget-version-lifecycle-user-testing.md` |

## Boundary retained from earlier phases

- Budget vs Actual continues to query only the active plan for the selected fiscal range.
- No approval/rejection workflow is introduced.
- No sidecar or parallel budget store returns.
- Fiscal-year and selected-period derivation remain governed by `FiscalPeriodRange` and the active company.
- The donor repository's non-persistent **Delete Selected** budget-row action is not a production lifecycle operation and is not ported.
