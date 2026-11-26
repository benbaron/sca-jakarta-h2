# P27-C23 — Complete chart workspace

Status: VERIFYING. Owner cannot find whole-chart deletion, multiple-chart
creation, JSON new-chart import, existing-chart merge or export in the actual
production screen. Branch `codex/P27-C23-complete-chart-workspace` starts from
merged #448, b922b2a408f2c97179c6f63353c5ce3dc7530ece.

## Required reading and inspection

Read C21/C22, P19 chart assignment/location, JSON interchange, UI/editor rules and
operation matrix. Inspect ChartAdminService/AccountAdminService and Chart/Account
references/identities, PanelFactory/PanelViewport/ResizablePanelHeader, native
wrapper/account/chart/JSON panes, review/commit DTO/services and real tests.
Existing canonical protected deletion and layout helpers are the donor adaptation;
retain P19's donor decision, no donor persistence or parallel application.

## Findings and authoritative behavior

PanelFactory prepares the wrapper root via PanelViewport/ResizablePanelHeader.
That moves root.top into a split held by root.center; later root.setCenter calls
for New/Edit/Clone/Import/Back/Save discard the toolbar. Earlier injected native
tests used PanelHost.showReplacement without factory preparation and missed it.
Keep chart controls permanently attached using the existing compact-header policy,
bound the toolbar with local two-axis scrolling to preserve usable account space
at narrow/enlarged-text sizes, then test actual production preparation and a factory-created journey. Do not
weaken the two-business-section audit or merely assert initial button existence.

New/Edit/Clone Chart, Delete Draft Chart, Import Chart JSON and Export Chart JSON
remain visible throughout view transitions. Multiple owned charts remain independently
selectable by name/version/lifecycle/ID. New/clone/imported charts remain DRAFT;
activation stays explicit and requires COMPANY_ADMIN. Other maintenance uses
BOOKKEEPING_WRITE; export retains EXPORT. Preserve all three independent drafts.

Delete Draft Chart is distinct from Delete Account. It can remove an owned,
non-current DRAFT and all its unused definitions, including hierarchy, adjuncts
and same-company ACCOUNT/CHART import mapping receipts, atomically with factual
audit. Empty drafts qualify. Require saved/cancelled account edits and confirm the
named chart/ID/count/effects. Revalidate scope, revision, lifecycle and every account
operational reference under company/chart locks. Protect current pointers from
any company, external child references, journal/header bank, banking, fixed assets/
lifecycle, inventory and foreign-company mapping receipts. Never delete shared
report/schedule dictionaries, other charts, audit or operational/history records.
Late failure rolls back everything. Internal parent links may be removed within
the same deletion transaction. New service previews do not write.

Import Setup uses plain labels for create-new, merge, and explicit code mapping.
Existing-chart modes select an owned non-retired target by stable ID, defaulting
to the viewed chart, including a draft. No need to activate a draft just to merge.
Add optional target chart ID to the existing immutable request while preserving
old constructor/caller behavior (legacy null target resolves active). Retain that
ID through Apply/Refresh/commit. Scope fingerprints, source hash, ownership,
lifecycle and existing history rules are rechecked before and under the commit
lock. Missing/foreign/retired/drifted targets cannot silently fall back to active.
An unfinished review must ignore target choices loaded for a different company or
database; its original selector value is retained until the original scope returns.
Scope changes still invalidate acknowledgement and require review refresh.
New explicit-target CHART receipts use the target portable UUID rather than a
name/label, preserving legacy receipt keys and idempotency across renames; target
fingerprints include that UUID. CREATE_NEW_CHART remains separate and cannot carry an existing target. No JSON
format or schema change. Export continues to use the viewed owned chart.

Routine inputs/results/errors stay inline; only native file selection and
explicit destructive confirmations are dialogs. Busy controls, retained errors/
cancelled drafts, wrong-scope callbacks, global commands, company state and two-axis
scrolling remain compliant. Chart deletion requires the account list context.

## Validation and owner checks

Reproduce the production toolbar loss with prepared/factory routes before fixing;
assert actions survive native create/save, repeated chart creation, edit/cancel,
clone/back, JSON create/merge/commit/back, return navigation and export. Add actual
empty/partial chart deletion, cancel/dirty/permission/scope/blocker/rollback and
stale preview tests; retain all account deletion guards. Exercise merge into a
non-active draft while another chart remains current, reviewed edits/target drift,
wrong-owner/retired targets, legacy APIs and deterministic JSON/SCLX export.
Inspect real rendered normal/narrow views; update primary Word in place, preserving
review parts and render. Run required native, full clean verify and exact-head
Linux/Windows CI; publish honest handoff with no automatic merge.

Owner: in Chart of Accounts, use New Chart twice and inspect both in View chart.
Delete an unused draft with Delete Draft Chart; cancel first to inspect its effects.
Use Import Chart JSON, choose Create a new chart and review/import. Repeat with
Merge into an existing chart and the intended named draft. Verify the current
chart/history does not change. Export the viewed chart. Actions must remain visible
throughout. Used/current/retired charts and referenced accounts must be retained.

## Publication and verification receipt

Draft [PR #449](https://github.com/benbaron/sca-jakarta-h2/pull/449), implementation
6e6047fbb4ca53d6c4a7c28827462f5ae3f658e4, tree
636cf19d2904d917077d359e94c2883e008e8ba2, normal push/read-back verified.
Baseline compile plus 17 prior service cases pass. Initial service gate 33 cases
passes; final focused gate 52 tests, zero failures/errors/skips, including 19
native chart and 8 JSON review cases. Actual factory loss reproduced before fix;
complete-row, divider movement and local scroll reachability pass at enlarged
text/narrow size. Word edited in place; only document.xml changed, all other ZIP
parts, 18 links and 2 drawings preserved. 33-page render inspected on pages 9/10.
272 local documentation file links and whitespace pass. Full local clean verify
and exact-final-head Linux/Windows CI remain pending; keep draft until green.
No automatic merge.

A final retained-target regression reproduced original selection -> null when
foreign-scope choices were configured. Ignore those choices while the review's
original scope is absent; acknowledgement remains invalidated. Pre-correction
clean verify passes 1,423 tests with zero failures/errors and 225 display skips.
Repeat native/full and exact-successor-head CI for this one-line guard plus test.

Published correction 20318de4c0297e8f81f6c8363c6433e4acb939b8, tree
c8186ea754e0f6d07a0f76f197cda8eeccf83a55. Code is frozen. Corrected native
chart/JSON/route tests, full clean verify and exact final receipt-head CI remain
required before readiness. The pre-correction full run elapsed 9:32.
