# P27-C18 — Budget Categories visibility and caller return

Status: VERIFYING. Owner reports No matching Budget Categories on the list and
an incorrect Return to Journal action when arriving from Import.

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
