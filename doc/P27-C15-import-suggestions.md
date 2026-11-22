# P27-C15 — CSV suggestions in import lookup fields

The owner requests grey proposed source values in the same combo fields used for
final choices. Clarifications adopt automatic unique exact matches, all applicable
lookup fields and mandatory selection or explicit clearing of unmatched optional
suggestions. Applies to Account, Fund, Budget Category, Merchant and Payee in the
Exchequer review. CSV supplies no Event identity; Event remains an explicit choice.
Expense function retains its existing governed enum editor, without inference
from arbitrary source purpose/category text.

## Behavior

- Auto-match only a unique eligible record by trimmed, case-insensitive exact
  code/name/display label. A code/name collision across different IDs is ambiguous.
  Account choices remain restricted to the company's active selected chart;
  other choices remain active and company-owned. No fuzzy/cross-chart matching.
- An unresolved source label appears grey in the combo's normal value area and
  corresponding line-table column. A real selection replaces it with normal
  styling. The dropdown lists real eligible records, never invented IDs.
- Account/Fund remain required. For supplied optional source values, choose a
  real record or use the explicit Clear action to omit that dimension. Clearing
  is retained across selection, Back/Resume and refresh. Original CSV evidence
  remains unchanged. Blank optional source fields require no extra acknowledgement.
- Bulk mapping can reuse selected/explicitly cleared choices for identical source
  labels. It does not invent a mapping for an unresolved source or copy blank
  labels across unrelated records.
- Refresh retains chosen identities and updates their names; removed/inactive or
  wrong-chart choices become unresolved. Never replace a prior deliberate choice
  with a different same-name ID automatically. Newly available unique matches may
  resolve untouched source suggestions; explicit clearing remains cleared.
- All edits and mapping/refresh changes invalidate batch review. Unresolved
  suggestions block Commit/File Save; resolved commands use the existing atomic
  service, permission, source/duplicate and accounting validation boundaries.

Use native JavaFX combo/list cells and external CSS with readable grey text,
tooltips and textual validation/accessibility cues. Preserve the existing two
pane editor, local overflow, remembered state, raw money/date input and source
diagnostics. No new windows, persistence, migrations, dependencies or ledger.

## Evidence and validation

Fresh branch codex/P27-C15-import-suggestions starts from main ff48a52f after
C3/#440 merged; prior receipt carried as 281b7202. Integrate the independently
authorized C2/#439 documentation head dadd715c by ordinary merge, preserving its
Word/manual authority changes. Only PLAN conflicts; retain C2/C3 history around
this selected C15 task. No automatic merge of either PR.

Read AGENTS, PLAN, C9/C11 and UI design/editor/operation contracts; inspect the
review pane, active reference queries, source DTO/parser, CSS and FX tests. Existing
donor decisions in C9/editor guidelines apply; no donor code or persistence is
needed. Baseline compile passes (15.8s). Run existing display baseline, new pure
matching/state tests, actual combo/popup/draft/refresh/clear/commit tests and local
overflow checks, inspect styled native screenshots, update the Word instructions
directly preserving package/review parts, then final clean verification and
exact-head Linux/Windows CI. Record actual results and user checks before handoff.

User checks: preview a source with exact, missing and ambiguous labels; inspect
grey hints in each applicable field, select a real record or Clear optional hints,
apply matching-label choices, switch entries and Back/Resume, refresh after a
reference change, verify readiness and explicitly commit. Check saved Journal
identities and source bytes. Keep VERIFYING until owner review/merge.

## Owner clarification: whole-entry acceptance and multi-delete

Apply Entry Changes validates the whole edited record and accepts changes
immediately into the proposed batch, returning to Proposed Entries. It does not
write Journal. Cancel Entry Changes restores the last applied draft, including
mappings, explicit clearing, raw amounts, information and line count. Unapplied
edits survive navigation/Back/Resume and Refresh Review, but block batch commit
until applied or cancelled. In Edit Entry contextual File/toolbar Save applies
the record; elsewhere it targets the explicit batch commit.

Proposed Entries has a textual Review status and whole-row styling: Ready uses
normal foreground (black in the light theme), Needs attention/Unapplied edits
uses readable grey, and valid Updated rows use bold normal foreground. The owner
selected immediate acceptance on Apply, so Refresh Review is not a second
acceptance: it reloads choices, revalidates and clears the recent-update marker.
Applied values remain; stale references become unresolved/grey. Selection
highlight retains readable theme foreground, with status text preserving meaning.

Ctrl/Cmd and Shift multi-selection plus Delete Selected Entries (N) removes only
chosen proposals, with no Journal/source-file effect. Edit acts on the lead row;
deletion acts on all selected rows. No new dialog, window or major pane. Existing
final batch confirmation stays. Baseline display passed three tests (51.2s).
Initial candidate gate passes 15 tests (eight pure, seven actual FX), zero failures,
errors or skips (01:11). Final styled render/geometry and broader gates follow.

## Final focused verification

Final source gate passes **37 tests, zero failures/errors/skips (02:11)**:
eight pure suggestion/state units, eight actual CSV review cases, seven production
command routes, eight Save-scope and six overflow checks. Earlier broad gate
passes 62 (03:39), including 23 Import workflows and task lifecycle. Final refresh
allows unfinished edits to survive a choices reload, without accepting them;
Cancel restores the prior applied baseline with refreshed reference identities.
Native row states, computed grey color and bold-to-regular font change, real popup,
complete Apply/Cancel, multi-delete, auto-matching and stale references are tested.
Synthetic native Updated/Ready/grey-editor screenshots visually inspected.

Word is edited directly in four procedure paragraphs only. Every other package
part and original paragraph remains unchanged; nine tables, two illustrations,
80 bookmarks, 18 links and annotation counts are preserved. Render remains 31
pages; updated import pages inspected. Specification links and whitespace pass.
Full clean verification and exact-head CI remain pending. Evidence:
/workspace/p27-c15-evidence/. No user database/private CSV or schema changes.
