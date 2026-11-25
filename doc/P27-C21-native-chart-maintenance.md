# P27-C21 — Native Chart of Accounts maintenance

Status: VERIFYING. Owner requests native chart creation, editing and cloning;
JSON is compatibility interchange, not the primary maintenance path. C20/#446
merged at f7453b324f54421ff33ccc12edbc073121f9ef70; this branch starts from that
main. Baseline compile passes. Branch: `codex/P27-C21-native-chart-maintenance`.

## Required reading and inspection

Read [chart assignment](P19-S1-company-chart-assignment.md),
[view/activation placement](P19-C1-chart-selection-location.md),
[JSON compatibility](data-exchange/chart-of-accounts-json.md),
[UI rules](ui_design_rules.md), [editor](ui/editor-guidelines.md) and
[operations](interface-operation-matrix.md). Inspect Chart/Account models,
CompanyAdminService, AccountAdminService/AccountLookupService, native Chart panel
and wrapper, their actual native/service tests and current migrations. Existing
production account/metadata editors are the applicable donor adaptation; P19's
donor assessment is retained. No donor persistence/alternate chart store is needed.

## Native workflow

Prominent New Chart, Edit Chart and Clone Chart actions live on Chart of Accounts.
They open an inline chart form with explicit Save/Cancel and retained dirty inputs.
The existing account list/editor remains the primary way to maintain definitions.
Expose posting/group state, effective dates, opening-balance field and description
alongside code/name/classification/parent/active. Draft and other owned non-retired
charts can be maintained without activation. Viewing or editing never changes the
current company pointer. Make Active Chart remains the separate explicit operation.
JSON import/review/export moves to a secondary Compatibility menu, retaining C20.

Chart and account editing require BOOKKEEPING_WRITE, matching the existing JSON
import authority. Make Active Chart retains COMPANY_ADMIN. New/clone charts are DRAFT. Metadata editing preserves stable
chart identity/ownership; retirement cannot retire the current chart. Charts and
accounts are retained, with visible retirement/deactivation explanations. No new
hard-delete operation. Existing history/bank/acquisition protections remain.

Use the existing H2 Chart/Account graph and an authoritative native chart service,
with atomic create/edit/clone and factual audit in the same transaction. Clone
creates new chart/account IDs and chart portable UUID, remaps parents and copies
account-definition adjuncts (aliases/report mappings/schedule requirements).
Journal entries, banking links, reconciliation, external import identities and
financial history remain on the source. Source is not activated or modified.
Duplicate new chart name/version is rejected per target company. Source/target
drift and wrong company/database cannot silently redirect a pending command.
All failures roll back and preserve editor input.

Owner answered: include other companies' owned charts as clone sources, and reset
all opening balances to zero. This is a target BOOKKEEPING_WRITE operation over
read-only source definitions, with read-only chart-template metadata access across the database. Sources identify company/name/version/lifecycle/ID.
No source-company mutation or financial history is copied. There is no currency
conversion or option to carry opening balances in this slice.

## Implementation constraints and validation

Supersedes P19-C1's restriction that noncurrent charts are always read-only;
ownership/history protections and explicit activation remain. Extend the existing
AccountAdmin command with an optional selected-chart target/full-definition fields,
keeping old callers' active-chart behavior and preserving omitted metadata.
Share existing classification predicates with JSON rather than adding divergent
policy. Include non-posting/inactive accounts in the maintenance list only; Journal
choices remain governed by their existing current-chart/posting/active filters.
No schema change or JSON round-trip is required for native operations.

Respect two visible sections, local two-axis scrolling, company formatting/state,
dirty/cancel/navigation, global New/Save scope, busy/permission explanations and
Import Add's active-chart/name-only handoff. Capture services/scope before async
commands; reject stale responses. Preserve both account and compatibility drafts.

Add service tests for empty-company creation, metadata/retirement, cloning the
complete definition graph with fresh identities, scope/permissions, source drift,
duplicate names, precision/history and rollback. Add real native create/edit/clone/
draft-account/activation/compatibility/dirty/cancel/busy/overflow/global Save tests.
Update the primary Word file directly, preserving review parts; render and inspect.
Finish focused, native, full clean verify and exact-head Linux/Windows CI. Publish
an honest PR handoff; no automatic merge.

## Verification and user testing

Baseline compile and 15 existing service cases pass. Broad focused gate passes
107 tests, zero failures/errors/skips, including native services, chart selection,
CSV/JSON review and production command/save routes. Final native and full gates
and publication/CI receipts are recorded in PLAN; no automatic merge.

Native clone form visually inspected at 1050×790 and 550×590 with enlarged text:
primary controls wrap and the two sections expose independent local scrolling
and a draggable divider. Word was edited directly, retaining all other ZIP parts
and existing review markup; affected 33-page render inspected on 8–10, 15 and 18.

Owner checks on the PR build:
1. Open Chart of Accounts → New Chart; save a name/version and confirm DRAFT.
2. Use New Account for a non-posting group, then a posting child with that parent;
   save classification, dates, description and a two-decimal opening amount.
3. Choose Edit Chart, change name/version and save; existing IDs remain stable.
4. Choose Clone Chart, select a chart belonging to another company, name/save it;
   check account hierarchy and zero opening balances. Source remains unchanged.
5. Choose Make Active Chart only when ready (MANAGER/ADMIN). Existing journal,
   bank and reconciliation history stays on its original records.
6. Check Back/Cancel draft behavior, retired-chart explanation and the secondary
   Compatibility → Import/Export Chart JSON routes.
