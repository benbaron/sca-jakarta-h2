# P27-C16 — Import grey review and production correction

Status: owner-authorized corrective implementation **VERIFYING**.
The preceding review evidence remains historical and reproducible.

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

## Authorized implementation

The owner now instructs “Apply the changes.” Fresh branch
`codex/P27-C16-import-grey-contrast` begins at fetched main 17fa56ae with the
previous review and merge receipts preserved. Baseline Java-17 compile passes.
No new donor design, persistence or accounting behavior is required.

The production contrast traversal now propagates the two existing import grey
roles into skin/painted Text descendants. It retains a readable dark/light grey
against each actual composed surface; selection colors are handled by contrast
rather than erasing the semantic distinction. Quantized RGB adjustment keeps
body-text contrast at least 4.5:1 even after CSS painting. Removing a role or
reusing a resolved cell returns to the ordinary black/white rule; bound skin
styles remain bound and unrelated styles/fonts are retained.

The existing CSV review tests now install the production theme and contrast
routine. A new real CSV case supplies all five unresolved lookup dimensions,
checks all eight supported themes, selected/unselected proposal and line cells,
painted foregrounds, real selections, explicit Clear and Cancel with reused
controls. A general contrast case covers bound skins/dynamic roles across the
same themes and runs in the existing Windows contrast suite. A pure color gate
checks 256 neutral surfaces and representative selection colors. Both selected
before-fix native regressions fail as expected (two wrapped foreground assertion
errors, zero skips, 01:01), while candidate validation is running.

The existing Word instructions already describe the intended grey behavior;
there is no procedure change, so the authoritative Word package is unchanged.
No schema, service, source CSV or user data changes. No extra panes or dialogs.

User checks on a disposable import: unresolved source labels appear grey in
Account/Fund/Budget/Merchant/Payee value fields and line cells; invalid proposals
remain grey when selected and when another row is selected. Choose saved records
or Clear optional fields, Apply the whole entry and see a bold Updated row.
Refresh returns valid updated rows to normal; Cancel restores unresolved grey.
Change the theme and resize/scroll; status text, IDs and commit protection remain.
Only final Commit writes Journal. Publication, full verification and exact-head
CI remain pending; no automatic merge.

First focused/native candidate gate passes 38 tests, zero failures/errors/skips
(02:48): nine CSV review, four theme contrast, seven command routing, eight Save,
two pure contrast and eight suggestion-state cases. All eight themes, all five
lookup fields, selected/unselected grey, painted skin text, bound styles and
resolution/reuse are exercised. Grey editor/list and Updated/Ready screenshots
were inspected. Final import/contrast/overflow check is running after adding
fixture theme cleanup; full clean verification and exact-head CI remain.
