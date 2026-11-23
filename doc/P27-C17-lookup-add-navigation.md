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
