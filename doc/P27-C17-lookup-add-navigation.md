# P27-C17 — Add missing lookup records through maintenance navigation

Status: VERIFYING. Owner requests Add alongside Clear/dropdown selection.
After an optional scope question and ample opportunity to reply, proceed with
the six CSV review record selectors in the current discussion. Application-wide
selectors are not silently included. Fixed enums are not maintained records.

## Existing authorities and route contract

Use the existing native workspace tabs and DrillThroughCoordinator. No popup
record editor, fabricated combo item, alternate master-data service or implicit
Save is introduced. Existing source/destination dirty drafts survive navigation.

| Lookup | Existing maintenance destination |
|---|---|
| Account | Chart of Accounts |
| Fund | Funds |
| Budget category | Budget Categories |
| Event | Events / Activities |
| Merchant | Merchants |
| Payee | Payees / Counterparties |

Expense Function is a fixed governed enum with no maintenance panel; keep its
real dropdown rather than offering an unimplemented Add. Required Account/Fund
mappings remain required. Optional Clear keeps its existing meaning.

Add navigates to the existing panel. Use its New/Save controls; an already-open
dirty target is preserved. Return by selecting the Import tab. Reload eligible
references in the original company/database scope, preserve stable chosen IDs,
explicit clearing, pending header/line edits, Apply/Cancel baselines and Updated
markers. Unique exact untouched source labels may resolve as in C15. No Apply
or Journal commit occurs. Explicit Refresh Review retains its existing marker-
clearing behavior. Read-only/accepted/busy/wrong-scope Add actions are unavailable
with appropriate existing permission/context explanations.

## Inspection and validation

Read AGENTS/PLAN, C15/C16, [UI rules](ui_design_rules.md),
[editor rules](ui/editor-guidelines.md) and [operations](interface-operation-matrix.md).
Inspect actual combo/review/navigation controllers, six destination panels,
permission gates/reference queries and real FX tests. Existing Journal maintenance
buttons and native reusable PanelHost routes are the applicable prior donor
adaptation; no new donor component or parallel record authority is needed.

Baseline Java-17 compile passes. Add native route checks for every field and an
actual save/return/refresh workflow. Check permissions, cancelled/dirty target,
company/database changes, pending and applied import drafts, selected line and
both-axis overflow. Update the authoritative Word procedure directly, preserving
all other package parts, paragraphs, review annotations and media. Run focused
native/full verification and exact-head CI; record real results and manual steps.
No user financial database or private source is used. Do not merge automatically.

## Implementation and initial validation

All six import record combos now include Add navigation. Use the existing target
New/Save action; Add never resets an existing target draft. Returning once to the
original Import scope reloads references while retaining current selection, raw
header/line fields, explicit clearing, Apply/Cancel snapshots and Updated markers.
Wrong company/database, busy, accepted and read-only Add commands are unavailable.
Explicit Refresh Review still clears Updated markers. Fixed Expense Function
choices remain a real enum dropdown. Bounded preferred combo width keeps adjacent
Add/Clear controls usable; local both-axis overflow remains.

The first 33-case gate found one genuine return-state defect: a prior wrong-scope
visit left Refresh disabled before the automatic return reload ran. Reorder
availability refresh before that reload, retaining the pending flag while away.
The regression also checks database scope. The real Budget Category tab/save/
return case preserves both source and target drafts, makes a durable category
available, retains invalid raw amount/header edits, and writes no Journal entry.
All 12 revised import cases now pass; broader final checks are running.

The Word procedure was edited directly in existing paragraph 301 only. Every
other paragraph and ZIP part is byte-identical; review annotation counts, tables,
images, bookmarks and links are preserved. LibreOffice render remains 31 pages;
updated pages 15/16 were inspected. No Word regeneration or user data was used.

Broad production gate passes **49 tests, zero failures/errors/skips (03:12)**,
including all 12 import cases, real maintenance/save/return, permissions and both
scope changes, ordinary/grey contrast, Save routing, overflow and guide checks.
A final 18-case native gate follows the preferred field-size adjustment. Full
clean verification and exact-head CI remain. Keep VERIFYING until merged.

Final native gate on the final sized fields passes **18 tests, zero failures/
errors/skips (02:06)**, covering all 12 actual import cases and six overflow
cases. Earlier broader gate passes 49 (03:12). The updated native Add/return
screenshot and 31-page Word render were inspected. All 249 changed-document
local links and whitespace pass. Full clean verification, publication and
exact-head CI remain pending.

Final action-geometry gate passes **18 tests, zero failures/errors/skips (02:00)**
on the final preferred sizing. Direct bounds checks confirm the visible Account,
Fund and Payee Add actions fit the normal-width viewport; reduced windows retain
local scrolling. The real Add/return screenshot was inspected after this final
change. Earlier sizing-gate notes are historical. No further runtime changes
are pending; publication, clean verification and exact-head CI remain.

## Publication

Published [draft PR #443](https://github.com/benbaron/sca-jakarta-h2/pull/443),
implementation 5ca6ec66586184726955f20bb756ac24ac49cf79 and final native sized
head a00965b783a4b3ce13cd357592d8bd020b4116f0, tree
fc19682189b0c4b23251d7b09637b922a9d81bb6. Normal new-branch push and PR
read-back match. This publication successor changes documentation only.
Full clean verification and exact-final-head Linux/Windows CI remain pending.
Keep VERIFYING until confirmed merge; no automatic merge. Evidence and native/
Word render artifacts are under /workspace/p27-c17-evidence/.

## Final local verification receipt

Accepted remote head 31802a92a6e3400bddad170e9655fcc0a88532f3, tree
f4a7a14cf047b11f0dfbabbeb4a811040bb36eaa, matches native branch and PR read-back.
Local clean verification passes **1,335 tests, zero failures/errors and 169
expected headless display skips (10:59)**. Native 49/18 gates pass without skips,
with final screenshot and direct action bounds inspected. Word ZIP proof retains
all parts/paragraphs except the intended paragraph 301; nine tables, two drawings,
80 bookmarks, 18 hyperlinks and review counts are unchanged. Render stays 31 pages.
No product/test/Word bytes changed after final validation; this final receipt
stays local to preserve the checked remote head.

Exact-head [CI 45104932605](https://github.com/benbaron/sca-jakarta-h2/actions/runs/45104932605)
passes Windows job 137463968961: 24 display tests across seven suites without
failures/errors/skips; actual totals inspected. Linux job 137463969282 passed rendered theme checks, clean/repeated verification,
Period Close, overflow, imports, Journal/SCLX feedback and production JavaFX routes.
The final run and both jobs completed success. Actual Windows totals and final
run/job/individual-step metadata were inspected. No final executed gate failed.
[PR #443](https://github.com/benbaron/sca-jakarta-h2/pull/443) is ready, open and
unmerged at the accepted head. Earlier pending/sizing/initial failure notes are
historical. Keep VERIFYING until confirmed merge; no automatic merge. Evidence
and final metadata are under /workspace/p27-c17-evidence/.
