# P27-C16 — Review of import suggestions after #441

Status: review complete; corrective implementation **READY**, not implemented.

## Scope and source

The owner reports missing grey styling and asks whether #441 contains the agreed
changes. This is a review, not an instruction to change the runtime. Review
current fetched main 17fa56aef67b9983b35827b2453b9f384f40bc4e, the confirmed merge
of [#441](https://github.com/benbaron/sca-jakarta-h2/pull/441). Earlier local
validation and merge receipts are preserved on `codex/P27-C16-import-review`.
Read [C15](P27-C15-import-suggestions.md), [UI rules](ui_design_rules.md),
[editor rules](ui/editor-guidelines.md) and [operations](interface-operation-matrix.md).
No new donor design is involved; inspect the actual production integration.

## Confirmed finding (P2): production contrast overrides semantic grey

The production shell installs `WorkspaceTextContrastEnforcer` in both
`AuthenticatedWorkspaceRoot` and `ProductionWorkspaceWindow`. After layout it
writes an inline black/white `-fx-text-fill` into every Labeled control and an
inline `-fx-fill` into every Text descendant. Those declarations take precedence
over C15's external grey CSS, including correctly assigned
`import-source-suggestion` and `import-entry-invalid` classes. CSS specificity
alone cannot repair this. The forced color changes no reference ID or validation;
unresolved choices still block writes, but their promised visual distinction is
lost. This affects combo value cells, line lookup cells and invalid proposal rows.

C15's display tests construct a standalone Import pane with styles.css; they do
not install the shell's contrast enforcer. Thus an isolated grey assertion and
screenshot cannot establish that grey survives in the application. The review
uses a temporary copy of those tests with the production Primer Light theme,
workspace style classes and installed contrast pass, plus focused unselected
invalid-row and unresolved-line checks. Ordinary selections and focus cannot
explain missing grey on these unselected controls.

## Requirement audit

| Agreed behavior | Inspection result |
|---|---|
| CSV hint in the same Account/Fund/Budget/Merchant/Payee combo as the final value | Implemented; the grey foreground is overridden in the production shell |
| Unique eligible exact matches selected automatically; ambiguous/missing values unresolved | Implemented by ImportReferenceSuggestion; IDs stay separate from source text |
| Optional supplied lookup needs a selection or explicit Clear | Implemented and included in commit validation |
| Apply Entry Changes accepts the whole record immediately into the batch | Implemented; contextual Save delegates to Apply on Edit Entry; no Journal write |
| Cancel restores the last applied whole record | Implemented, including information, amounts, line count and lookup/clearing state |
| Correct/invalid/updated proposals visually distinct | Ready/Needs attention/Updated text and bold font implemented; grey foreground fails under the production contrast pass |
| Refresh reloads choices, retains applied/pending values and clears bold update markers | Implemented; does not accept unfinished edits; stale IDs become unresolved |
| Multi-delete through native multi-selection or checkboxes | Implemented through Ctrl/Cmd/Shift native multi-selection and Delete Selected Entries (N) |
| Only final explicit Commit writes Journal | Implemented through the existing guarded atomic import service |

The feature specifies grey text, not a grey background fill. A uniquely matched
valid source is intended to use normal foreground. Source Event identity is not
supplied by this CSV; Event remains an explicit ID-backed choice. Expense function
uses its governed enum editor rather than guessing from source prose.

## Validation and evidence

Baseline Java-17 compile passes. The 16 existing focused tests pass: eight pure
suggestion/state tests and eight native review tests. A copy of the eight native
tests with the production theme/enforcer plus two new foreground checks runs ten
cases: seven pass, three expose the missing grey. Combined Maven result is
**26 tests / zero reported failures / three errors / zero skips (02:07)**.
The three errors are CompletionException-wrapped AssertionFailedError instances
from FX-thread assertions, all showing black `0x000000ff` where grey is required;
there is no database/migration error. Explicit inline `-fx-text-fill: #000000;`
is logged on the unresolved combo, unresolved unselected bank-line cell and
unselected Needs attention row. Both native screenshots were visually inspected.

A first temporary-harness compilation attempt used the copied class's old private
method reference; that diagnostic-only reference was corrected before the run
above. It was not a baseline or application compile defect. The grey assertions
remain unchanged and failing. A separate production Apply/Cancel/refresh/font
check passes **one test / zero failures/errors/skips (00:44)**. It exercises the
entire existing whole-record workflow with only its already-confirmed foreground
assertion separated out: Apply accepts, Cancel restores, Updated is bold, Refresh
returns to normal Ready and applied values survive. Thus the visual grey defect
is confirmed while that acceptance workflow also runs under the production pass.

Evidence, reports, screenshots and the temporary diagnostic harness are under
`/workspace/p27-c16-review-evidence/`. The harness is removed from repository test
sources after review, with its reproducer retained as an artifact. No private
CSV, user database, Word package, schema or application source is modified.
No full clean verification or CI is claimed for this review-only documentation.

## Correction scope

Teach the production contrast path to preserve the import's semantic foreground
while retaining readable contrast on the actual composed surface. Cover both
Labeled controls and their painted Text descendants, dynamic/virtualized row
reuse, resolved/unresolved transitions and selected/unselected states. Do not
turn off ordinary-text contrast enforcement for the entire workspace.

Add regression coverage with the production contrast routine installed, exercising
combo hints, unselected invalid rows and line cells, all applicable lookups,
light/dark themes and resolution/refresh transitions. Keep the existing state,
Apply/Cancel, multi-delete, atomic commit, overflow and general contrast tests.
Update the governing contrast exception deliberately, then run focused/native,
full clean verification and exact-head CI for the corrective implementation.
The current review creates no patch or PR and does not claim this correction is
implemented. [PLAN](PLAN.md) owns the exact next action.
