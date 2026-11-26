# P27-C22 — Draft account deletion and visible JSON

Status: VERIFYING. Owner wants JSON import/export alongside the native editor,
and physical account deletion in DRAFT charts. Branch
`codex/P27-C22-draft-account-deletion`, based on merged C21/#447 b0d1c613.

## Reading and inspection

Read C21, P19 chart assignment/location, Chart JSON and UI/editor/operation rules.
Inspect ChartAdminService/AccountAdminService/Chart panel/wrapper, Account and all
account-reference entities/migrations, interchange identities and actual native/
service tests. Adapt existing production protected Activity deletion and atomic
chart service patterns; retain the documented P19 donor decision. No alternate
model, donor persistence or schema change is needed.

## Behavior and authority

Direct Import Chart JSON / Export Chart JSON buttons coexist with New/Edit/Clone
Chart. Import keeps C20 inline review, modes and atomic commit. Export uses the
viewed owned stable chart ID, including DRAFT/RETIRED, with captured company/DB
scope, existing deterministic SCA-COA 1.0 and safe file rules. Existing active-chart
export API remains available to callers. No implicit activation or history move.

Delete Account is real for a selected, saved, unchanged account in an owned,
non-current DRAFT chart. BOOKKEEPING_WRITE applies at the authoritative service.
Preview explains eligibility; confirmation names chart/account and removed
metadata. Revalidate ownership/lifecycle/revision/references under company/chart
locks inside one transaction. Reject children (delete/reparent them first),
Journal splits/header bank references, configured banking, fixed-asset/lifecycle,
inventory and foreign-company account mapping receipts. Never cascade children
or operational/history records. ACTIVE/RETIRED/current charts retain accounts.

An unused imported draft can be cleaned up: aliases, report-section mappings,
schedule requirements and same-company ACCOUNT interchange identity receipts
are definition metadata and are removed with that account. Preserve shared
report/schedule dictionaries, chart receipts, other accounts' receipts and all
audit/history. Record factual deletion with stable account/chart identity and
removed definition/receipt facts. Touch chart revision. Late failures roll back
all rows and audit. A later deliberate import may recreate the omitted account.

Routine results/errors remain inline; native file chooser and destructive
confirmation are permitted. Cancel/failure preserves selected row and form.
Save/Cancel unapplied account edits before Delete. During async inspection/delete,
block competing commands/navigation and ignore stale scope responses. Keep two
business sections, local both-axis scrolling, wrapping toolbar and company state.
Native screenshot exposed an account-header overflow at enlarged text after adding
visible actions. Bound that chrome with its own persisted divider/scroll viewport,
leaving the two existing business sections (account table/editor) usable; verify
actual body/viewport heights, header overlap, controls and divider movement.

## Verification and owner checks

Add service tests for draft/group-leaf deletion, metadata/receipt cleanup,
permissions/ownership/lifecycle/current restrictions, all reference categories,
stale previews and late rollback. Add native successful/blocked/cancelled/dirty/
busy/scope deletion and actual visible JSON import/export of a viewed draft.
Preserve existing review/editor flows and test narrow/enlarged text. Update Word
in place preserving review parts; render/inspect. Complete full clean verify and
exact-head Linux/Windows CI, publish review handoff; no automatic merge.

Owner: open Chart of Accounts, confirm Import/Export JSON beside native controls;
export a viewed draft without activation and review a JSON file. In a disposable
draft, delete an unused child then group; confirm table/editor refresh. Cancel a
delete and confirm unchanged inputs. Try used/bank-linked/non-draft accounts and
read the inline reason. Journal/bank/history and other charts stay unchanged.

Baseline compile and 12 existing service cases pass. Five new deletion service
cases pass, including JSON/SCLX export smoke after deletion. Initial native test
findings corrected loading/layout timing, and actual screenshot found/fixed
account-header overflow with bounded resizable chrome. Final broad/native gate
is running; full clean verify and exact-head CI remain before readiness. Word
edited directly (five paragraph changes/one insertion), all other parts/review
markup preserved; 33-page render inspected on 9, 10, 15 and 18. 268 links and
whitespace pass. No automatic merge.

Publication: [draft PR #448](https://github.com/benbaron/sca-jakarta-h2/pull/448),
implementation d6dcc9d58042648bd29cacd8febcf90881a602dc, tree
bed64f7a10722d36aab3716076665058052ab4cc. Normal new-branch push and PR
head read-back match. This receipt successor changes PLAN/C22 only; final focused,
full and exact-final-head CI remain. No automatic merge.

The initial broad gate passed 95/96 cases; its sole error was production chrome
classification (also reproduced in CI 45741546880). The chart now uses the existing
ResizablePanelHeader service/policy instead of a separate unclassified split;
the two business sections remain table/editor. Long redundant default chrome
copy is removed so narrow views retain actual account rows. Final native/route
revalidation and full/final-head CI are required on this successor. Earlier run
is superseded, not an unrelated accepted failure.

Shared-header production route passes on the correction. All functional native
cases pass; the complete-row geometry check now uses the actual rendered scrollbar
height and both resize dividers and passes independently. Final complete native
and clean verify gates, then exact successor-head CI, remain. Source/Word are
frozen; no unrelated failures are accepted.
