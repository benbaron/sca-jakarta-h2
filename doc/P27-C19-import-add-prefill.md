# P27-C19 — Literal Import Add suggestion prefill

Status: VERIFYING. Current main e7c69354 includes merged C18/#444.
Owner requests that Add from Import autofill the corresponding maintenance
new-record form. Their final clarification is literal Name only; Code stays
blank. Do not generate codes or infer classification from a name.

## Contract and inspection

All six record lookups apply: Account, Fund, Budget Category, Event, Merchant
and Payee. Copy the displayed unresolved suggestion literally; no normalization
or identity assignment. A resolved/cleared/blank lookup has no unresolved hint
to transfer. Add itself never saves master data, applies an Import proposal or
writes Journal. Existing service validation still requires explicit codes and
classifications before Save.

Extend the existing one-time DrillThroughCoordinator/PanelHost handoff with a
transient database/company-scoped value; do not add a persistence service or
parallel navigation history. Default-compatible AppPanel dispatch reaches the
real panel, including the Chart interchange wrapper. Clean targets start a new
record; dirty targets retain all fields and expose a queued literal suggestion
for the existing New action once the user resolves that draft. No automatic
discard. Ignore stale/wrong-scope or unauthorized requests. Reloads cannot
overwrite the new draft or reapply an already consumed suggestion. Chart Add
uses the company's already-active chart for the new account without activating
a different chart. Required Account/Fund type/normal-balance fields stay explicit.

Read [C18](P27-C18-category-list-return.md), [UI rules](ui_design_rules.md),
[editor rules](ui/editor-guidelines.md), [operations](interface-operation-matrix.md).
Inspect ImportReferenceSuggestion, SuggestedImportComboBox, ExchequerImportReviewPane,
DrillThroughCoordinator, AppPanel/PanelHost, the Chart wrapper and all six master
editors plus existing native tests. Existing C17/C18 native maintenance/tab
implementation is the applicable donor adaptation; no external donor model,
schema or accounting authority changes are needed.

## Validation and handoff

Publication: [draft PR #445](https://github.com/benbaron/sca-jakarta-h2/pull/445)
contains implementation **b1080040c366f3da872ed1d87267dd2d4f92b94e**, tree
**026f97b6644a386be73057224acdd7df1d1c4365**. The new branch was absent, normal
push succeeded and PR read-back matches. This publication receipt successor
changes only PLAN/C19. Require full local clean verify and exact-final-head
Linux/Windows CI on the published successor; no source/test/Word edits remain.
No automatic merge.


Final native gate passes **18/0/0/0 (02:04)** on final source/test bytes: six
panel routes, the actual CSV Budget suggestion, scoped permission protection
and active-chart browsing without activation or inferred classification. Broad
gate passes **96/0/0/0 (06:39)**. Initial new-test layout error is resolved.
All six prefill screenshots and final Budget/Account status were inspected.
259 local links, whitespace and Word preservation pass; Word still renders to
31 pages, changed pages 15/16 inspected. No further source/test/Word changes
are pending. Publish the new branch, require full clean verify and exact-head
Linux/Windows CI, and record the actual publication/validation receipt.


Baseline compile passes. Add pure handoff/source-suggestion tests and real
native Add routes for all six destinations. Check literal punctuation/case,
blank codes, new IDs, no writes, pre-existing dirty drafts and queued New,
clean selected records, repeated/replaced handoffs, scope/permission changes,
asynchronous loads, return/reloaded choices and overflow. Update the Word
procedure directly, preserving all other parts/review/media/links, and inspect
its render. Finish focused/native/full and exact-head GitHub checks.
No user database/private CSV is used; no automatic merge.

## Implementation and current evidence

A literal-name-only transient handoff is stored/consumed in the existing
DrillThroughCoordinator/PanelHost route. AppPanel's default hook and the Chart
interchange wrapper dispatch it to each real maintenance controller. A shared
UI helper protects existing dirty drafts, exposes a queued-name header notice
and consumes it on the existing New action. Deferred Chart loading may apply
only a clean initial offer; Save/Refresh cannot auto-accept a queued dirty offer.
No codes or classifications are inferred, and no ID is assigned before Save.
Fund reload reselection also checks the current editing ID so an older list
request cannot select the prior record over a newly prefilled draft.

Baseline/candidate compile pass. Initial focused native run executes 28 cases: all
12 six-panel clean/dirty journeys and 15 pure handoff/suggestion cases pass; one
additional actual-CSV Budget case hits a new-test layout lookup error. The test
now waits for the real target field to be laid out before asserting; no product
assertion was weakened. Expanded native regression gate is running, including
the CSV case, permission/scope protection, old Import/master/Save/navigation/
overflow tests and final screenshots. A final new Chart test also checks that
Add views the already-active chart without activating a different chart or
inferring an account type/normal balance. Full clean verify and CI remain.

Word edited directly: only paragraph 301/document.xml differs; all other parts,
paragraphs, review annotations, tables, drawings, bookmarks and links remain.
Render remains 31 pages; changed pages 15/16 inspected. Local links and whitespace
pass. No user financial database or uploaded CSV is used.

## User checks

1. In Import Edit Entry, click Add beside an unresolved grey lookup. Its literal
   text should appear in the maintenance Name field for a new record; Code stays
   blank. Fill required codes/classifications yourself and use Save explicitly.
2. Return to Import: existing raw entry edits remain, and saved eligible choices
   reload. Add itself does not save a master or accept/commit a Journal entry.
3. If maintenance already has an unsaved draft, Add preserves its fields and
   shows the queued name. Resolve that draft, then use the existing New action
   to populate the queued Name. Later refresh must not restore an already-used
   suggestion over your edits.
4. Account Add uses the company's already-active chart for the new draft; no
   other chart is activated. Non-account names retain the same literal behavior.

The expanded run passes all 35 Import cases, including the corrected actual CSV
Budget path. Permission/scope checks and six-panel clean/dirty journeys pass.
Native images of all six destinations confirm literal Name and blank Code.
A stale status that still named the prior selected master is corrected to state
New record; a final targeted native gate checks that final presentation and
the added active-chart/no-classification-inference case on the final bytes.

Expanded native regression gate passes **96 tests, zero failures/errors/skips
(06:39)**. The earlier actual-CSV layout fixture error is resolved; all 35 Import
cases pass. Final targeted presentation/active-chart native gate, full local
clean verify, publication and exact-head CI remain.
