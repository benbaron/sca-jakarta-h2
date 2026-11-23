# P27-C18 — Import lookup maintenance visibility and caller return

Status: IN_PROGRESS. Owner reports No matching Budget Categories on the list and
an incorrect Return to Journal action when arriving from Import.

## Owner extension — all Import Add destinations

Extension final native gate passes **15/0/0/0 (01:57)** after the Chart header
correction, including all five real Import journeys, actual Return/Show All
viewport bounds, chart selection and overflow. Expanded gate passes
**72/0/0/0 (05:47)**; initial gate passes **17/0/0/0 (02:28)**. All five native
maintenance screenshots were inspected, including the corrected Chart header.
251 local links, whitespace and final Word preservation checks pass. No further
source/test/Word edits are pending. Publish this extension and require a new
full local clean verify and exact-final-head CI before marking #444 ready.


The owner extends C18 to Account, Fund, Event, Merchant and Payee maintenance,
as well as Budget. Continue the open #444 branch; do not merge or stack a new
architecture. The Budget-only final receipt below is historical for this
extension. Baseline compile passes at eb2628dc.

Required inspection: ChartOfAccountsPanel and its interchange wrapper, FundsPanel,
ActivitiesPanel, CounterpartiesPanel, MerchantsPanel, their company-scoped lookup/
admin services and existing native editor tests. Applicable donor adaptation is
the same native maintenance/tab infrastructure already used by C17/C18.
No schema, new persistence model or widened ownership query is needed.

Every destination gets a named caller-aware Return (default Journal), using the
existing activation hook; the Chart wrapper must forward it. Searchable panes
clear filters on activation/Show All and after Save. Unfiltered Fund/Account lists
reload all records in their existing company/chart scope. Retain selected chart
while browsing; Show All means accounts within that chart, never a chart switch.
List refresh never accepts/discards editor changes; asynchronous callbacks must
not overwrite edits made during a request. Counts and empty/filter/load-error
feedback distinguish missing master data from hidden/failed lists. Maintenance
includes inactive rows; Import choices retain existing active/eligible filtering.

Validate every real Add/Save/Return route, source and target draft retention,
filter reset, inactive visibility, saved-row selection, ordinary callers,
permissions, chart browsing and local overflow. Update Word directly, preserving
review/parts/media/links; run focused/native/full and exact-head GitHub checks.

## Extension implementation and validation

All five additional maintenance destinations use small UI-only helpers for
caller Return and list feedback. The Chart interchange wrapper forwards the
existing activation hook. Searchable lists reset on activation/Show All/Save;
Refresh retains the filter. Funds and Chart lists already have no search, so
Show All simply reloads all records in the existing company/viewed-chart scope.
Selection callbacks are suppressed during list replacement/reselection. Dirty
forms are retained; Fund/Chart callbacks also compare the requested form revision/
snapshot and ignore superseded loads. Existing Activity guards remain.
The anonymous Chart table-state key is deliberately unchanged. No maintenance
query, schema, Import eligibility or Save service authority changes.

Baseline and candidate compile pass. Initial native gate passes **17/0/0/0
(02:28)**, including five actual Import Add/Save/Return journeys plus existing
Counterparty, Merchant, Event and Chart tests. Expanded native gate adds empty/error/recovery journeys for all five
destinations and existing Budget, host, Save, route, table-state, guide and
overflow checks. All 22 Import cases pass. Visual inspection found the new
Chart actions below its default header viewport; move that action row directly
below the title and assert Return/Show All fit the actual local viewport in
every normal-size maintenance route. Expanded native gate passes **72/0/0/0 (05:47)**. Final header geometry
gate, full clean verification and new exact-head CI remain pending.

Word extension changes only paragraph 301/document.xml; all other parts,
paragraphs, annotations, tables, drawings, bookmarks and links remain identical.
Render remains 31 pages; changed pages 15/16 were visually inspected.

Extension user checks: for every Import Add lookup, create a record through its
existing maintenance pane, use Show All after a search miss, refresh while a
draft is unfinished, Save and verify the row becomes visible/selected. Use
Return to Import; unfinished source fields/line selection and Updated markers
remain and new eligible choices reload. Inactive maintenance rows remain
unavailable for new import selection. Open each pane from Journal and verify
Return to Journal; ordinary same-tab refresh retains the caller. In Chart of
Accounts, Show All/Refresh retains the viewed chart and its read-only rules.

## Final validation and handoff

Final local receipt: accepted remote head
**e97bb2464a8788b3e1f7c6b0e216aba373b110db**, tree
**41a9b9d2c7a4f372a2933ff969d18765756572ba**; native fetch and PR read-back
match. Full local `clean verify` passes **1,339 tests, zero failures/errors,
173 expected headless display skips (12:18)**, exit 0. Broad native gate passes
47 (04:28); final category/Import/host gate passes 22 (02:40), both zero failures/
errors/skips. Final source/test/Word bytes are unchanged after verification.
The real Return to Import screenshot and Word pages 15/25 were inspected; Word
changes only paragraphs 301/488 in document.xml, preserving all other parts,
paragraphs, tables, media, links and annotations. Render remains 31 pages.
252 local document links and whitespace pass.

[Exact-head CI 45112712948](https://github.com/benbaron/sca-jakarta-h2/actions/runs/45112712948)
completed **success**. Windows job 137488523204 passes **24 displayed tests
across seven suites, zero failures/errors/skips**; actual log summary retained
in /workspace/p27-c18-evidence/windows-test-summary.log. Linux job 137488523627
passes rendered theme, clean/repeated verification, Period Close, overflow,
Import, Journal/export feedback and production JavaFX routes. Final run/job/step
metadata is retained in /workspace/p27-c18-evidence/github-final-checks.json.
No final executed gate failed. PR #444 is ready for review, open and unmerged
at the accepted head. This final receipt changes only PLAN/C18 and stays local
to preserve the checked remote head. Earlier pending notes below are historical.
Keep VERIFYING until confirmed merge; no automatic merge. Owner review/merge
is the only remaining delivery action.

## Inspected authority and intended behavior

Current main 54c4cdef includes merged C17/#443; its final CI passed. The existing
BudgetCategoryLookupService.listForMaintenance includes all active/inactive
categories owned by the selected company. UiServiceRegistry supplies the current
company. Preserve that ownership boundary; do not add sample rows or expose other
companies' writable records to make an empty list look populated.

The controller retains search on tab re-entry and after new-record Save. It also
blocks all list refresh while editing a draft, so saved/reference categories can
stay invisible. Default activation should show all categories; Show All clears
search and reloads. Refresh and filter changes preserve unsaved editor fields.
Save reveals/selects the saved category. Empty-company, filtered-no-match and
load-failure states must be distinct, with a visible list count.

Budget Categories Return uses the actual previous panel/tab title, including
Return to Import or Return to Journal. Use PanelHost's existing tab activation
boundary and a default-compatible AppPanel hook; do not maintain a parallel
navigation history. Same-tab refresh preserves the last return destination.
Returning is navigation, not Save or discard. Import's existing return reload
retains pending/applied fields and update markers. Other destinations keep their
existing activation behavior.

## Required inspection and validation

Read [C17](P27-C17-lookup-add-navigation.md), [UI rules](ui_design_rules.md),
[editor rules](ui/editor-guidelines.md), [operations](interface-operation-matrix.md).
Inspect BudgetCategoriesPanel, BudgetCategoryLookupService/AdminService,
UiServiceRegistry, AppPanel/PanelHost/DrillThroughCoordinator and actual native
Budget Categories/import/host behavior tests. Existing master maintenance and
shared native tabs are the applicable prior donor adaptation; no donor model,
new dependency, persistence or schema is needed.

Baseline compile passes. Add regressions before the fix for retained searches,
dirty-list refresh, new Save visibility and the actual Import return button.
Test active/inactive and foreign ownership, same-tab/Journal/other callers,
source/target draft retention, ordinary host lifecycle/command routes, permissions
and overflow. Edit the Word procedure directly with ZIP/paragraph/annotation/
media preservation and render checks. Finish native/full/CI validation and record
actual results. No user database or private CSV is used; no automatic merge.

## Implementation and evidence

Maintenance/service ownership is unchanged. Activation/Show All clears search;
Refresh keeps its filter. Reload suppresses selection callbacks and preserves the
dirty editor. Successful Save selects the saved row after clearing search. Counts
and separate empty-company/search-miss/load-error messages explain visibility.
AppPanel's default-compatible activation hook carries the previous native tab's
ID/title. Budget Categories Return follows that caller, retaining the destination
on same-tab reselection; Import's actual return button resumes its existing draft.

Before-fix native assertions reproduced the original empty-state wording and real
Import Return-to-Journal defect. The retained-search regression independently
failed against the original controller (expected empty search, actual no match).
Initial new-test fixture failures (unlaid-out SplitPane lookup and a wrong Company
setter) were corrected separately, not counted as product defects. Baseline
compile passes; broad native gate **47/0/0/0, 04:28** covers Budget Categories,
Import, host lifecycle/commands, production routes, Save, overflow, guide and
company/permission boundaries. Final native gate passes **22/0/0/0, 02:40** on final source/test bytes, including
the fifth Budget Categories load-failure/recovery test and the real Import
Return button with inactive-row visibility. The native screenshot was inspected.
All 252 changed-document local links and whitespace checks pass. Clean verify
and exact-head CI remain pending.

Word edits directly preserve the package: only paragraphs 301/488 differ in
word/document.xml; all other paragraphs/parts, annotations, tables, drawings,
bookmarks and links remain. Render stays 31 pages; changed pages 15/25 inspected.

## User testing

1. In Import's Edit Entry, use Budget Add. Budget Categories lists all saved
   categories for the selected company, including inactive rows with Active No.
2. Search for a miss, then Show All. Start an unsaved category draft, leave and
   return; the list reloads and draft fields remain. An empty company reports
   no categories created; it does not claim an unmatched search.
3. Save a new category while a search would hide it. Search clears and its row
   is selected. Use Return to Import; the same proposed entry and raw edits
   remain, and eligible saved choices reload. An inactive category remains
   maintenance-visible but is not a new import choice.
4. Open from Journal; Return to Journal resumes Journal. Refreshing the same
   category tab preserves the caller. No return action saves or discards edits.

No actual owner database was available: this fixes verified controller paths,
not missing records in an uninspected desktop database. No automatic merge.

## Publication

Draft [PR #444](https://github.com/benbaron/sca-jakarta-h2/pull/444) on
`codex/P27-C18-category-list-return` contains implementation
bee2e1c64c61326d897826e3ce645308739d3cad, tree
2c4d1f1dea329ceb153d7bdf7e4cbf8ca111a904. New remote branch was absent,
normal push succeeded and PR read-back matches. This receipt successor changes
only PLAN/C18. Full clean verify and exact-final-head CI pending; no automatic merge.
