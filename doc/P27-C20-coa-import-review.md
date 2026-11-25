# P27-C20 — Reviewed Chart of Accounts JSON proposals

Status: VERIFYING. Owner requests an editor/reviewer like Journal CSV import
for Chart JSON validation issues, including incompatible subtype. Main contains
merged C19/#445 at 2934f37a. Baseline compile passes.

Read [JSON contract](data-exchange/chart-of-accounts-json.md), [shared operation
contract](data-exchange/shared-operation-contract.md), [UI rules](ui_design_rules.md),
[editor rules](ui/editor-guidelines.md), [operations](interface-operation-matrix.md).
Inspect current JSON parser/preview/import service, DTOs, Chart interchange wrapper,
Journal review behavior and their service/native tests. Existing native maintenance
and Journal review pages are the applicable donor adaptation; no donor persistence,
new ledger, schema or formal approval authority is introduced.

## Contract

Routine Setup/Preview/Edit are inline in the existing Chart workspace. Proposed
Accounts supports selection, field-level editing and multi-delete of proposals
only. Edit includes code/name/type/function/subtype/normal balance/parent, posting/
active, effective dates, opening balance and description/currency. Apply stages
parsable edits and revalidates the entire interdependent graph; invalid proposals
remain visibly blocked. Cancel restores the last applied account. Source metadata
is retained/read-only. Diagnostics show complete messages and original source.
Use explicit review acknowledgment and opening-balance confirmation, then Import
Reviewed Accounts. No warning/error override, saved-account deletion or source
file mutation. Codes/parents/history protections remain authoritative.

Extend existing request/preview/commit with immutable reviewed replacements and
exclusions keyed by original source ordinal, bound to original SHA-256 and a
company/chart target snapshot. Re-read the original source and revalidate reviewed
fields before commit; target drift requires fresh review. Unknown metadata cannot
be silently discarded. Existing CREATE_NEW_CHART/MERGE_BY_CODE/MAP_CODES and atomic
identity behavior stay; no extra audit mechanism is introduced. Review is transient;
Apply is not an H2 write.
Preserve raw editor changes across navigation/refresh, block commit until Apply/
Cancel, retain drafts on failure, and disable in-flight mutating controls.
Global Save routes to Apply while editing and reviewed commit on proposal pages.
Existing maintenance drafts and Import Add handoffs remain preserved.

Add pure/service regressions for the reported subtype repair, removals/parents,
source/scope/target drift, confirmation, permissions, atomic rollback and no-write
preview. Add real native review/edit/cancel/delete/commit/failure/overflow/global
Save tests. Update Word directly preserving other parts/review/media/links; inspect
render. Finish focused/native/full and exact-head CI. No owner financial file or
database is required; no automatic merge.

## Implementation and verification

Published [draft PR #446](https://github.com/benbaron/sca-jakarta-h2/pull/446),
implementation **2c87ad8fc0f9cbd7f8d3bc1af59de67a6739ceb7**, tree
**7d1e87db68db8545fec5a08a21ae54937c9fc7a0**. The new remote branch was absent,
normal push succeeded, and PR read-back matches. This publication receipt is a
PLAN/C20-only successor; validate that exact final head before readiness. Initial
CI 45549836933 is preliminary. No automatic merge.

Inline review is implemented in the existing Chart wrapper, over the existing
JSON parser and guarded atomic commit. The immutable reviewed request retains
source SHA/ordinals/metadata and a company/target-account fingerprint. Opening
confirmation, three modes, full graph/history validation and rollback remain.
The review has a persistent header/body divider and local scrolling; original
JSON and complete diagnostics use alternative tabs. Apply/Cancel and multi-delete
affect proposals only. Changes to setup/source or company/database cannot reuse
a stale acknowledgment. Diagnostic navigation follows source identity after sort.
The wrapper retains maintenance drafts and forwards Import Add/return hooks.

Existing service baseline: 10/0/0/0. New reviewed-service regressions: 8/0/0/0,
including subtype repair, graph removals, mapping/parents, source/target/company
drift, opening confirmation/precision, immutable source identity, history and
late atomic rollback. The first native review gate passed 5/0/0/0. A broad
79-case run passed 77, including all 35 CSV scenarios; it caught an unavailable
command explanation regression and a new-test status lookup on the maintenance
page. Both are corrected. Final expanded native gate is running; no claim of
full verification or CI success yet.

Word was edited directly: one JSON procedure paragraph updated and five added.
Only document.xml changed; 400 other existing paragraphs, all comments/anchors,
tracked changes, bookmarks, drawings, hyperlinks and every other ZIP part remain.
Rendered to 31 pages; changed page 9 and following page 10 inspected. Local
documentation links (260) and whitespace checks pass. Evidence is in
`/workspace/p27-c20-evidence`, outside the repository.

Remaining: finish expanded final native gate (including async prefill, sorting,
scope and actual painted grey lookup/row text) and inspect its image; run full
clean verify and require exact-final-head Linux/Windows CI before making it ready.
The unavailable-command route audit now passes. The new maintenance draft test
waits for its real asynchronous prefill before asserting; assertions are retained.
No automatic merge.

## Owner testing

1. Chart of Accounts → Import JSON… → choose the file → set the mode and Start
   New Review. Confirm target/source and proposed actions.
2. Edit the disputed account. Correct its classification (for an EQUITY account,
   clear an incompatible INVENTORY subtype or select a compatible value based
   on the account's meaning), then Apply Account Changes. Check status/diagnostics.
3. Check grey Needs attention, black Ready, bold Updated — ready and Refresh
   clearing the bold marker. Confirm raw unapplied edits survive refresh/back.
4. Ctrl/Shift-select proposals and Delete Selected Proposals; saved accounts and
   source JSON must remain unchanged. Resolve any missing-parent errors.
5. Resolve all errors, acknowledge review/opening fields when requested and Import
   Reviewed Accounts. Check the result and account reload. New charts remain DRAFT.
6. Change source/target during review and verify import blocks rather than using
   stale data. Check narrow/scaled scrolling, divider movement and full diagnostics.
