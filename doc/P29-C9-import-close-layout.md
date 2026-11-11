# P29-C9 Import and Period Close usability

Rename only the visible Import destination/header/tab title. Keep class names,
IMPORT_PREVIEW IDs, source identities and accepted audit history stable.

Period Close owns one explicit header ScrollPane, reused by shared resizable
header preparation. Its grid retains preferred width for horizontal access,
while status and active-period text wrap. Review Evidence, Draft References and
Recorded Reviews are sibling tabs sharing the original evidence controller,
services, fields and split-state keys. Preview/Save/New/Refresh remain in Review
Evidence; draft fields and references stay together in Draft References. History
and retained details stay in Recorded Reviews. Shared Save works from all three
contexts, only when the existing draft/permission/busy guards allow it.

Opening Conversion is an initial setup/migration review of already-loaded opening
balances, bank baselines/outstanding items, assets, inventory and supplemental
schedules. Resolve findings and preview again before accepting. Acceptance is one
immutable company audit fact; it creates no entries and does not balance missing
openings. Keep the function available; close its workspace tab when unused. It
is not a recurring close step. No conversion behavior changes in C9.

## Import test coverage inventory

The Maven build does not configure JaCoCo or another line/branch coverage report.
C9 generated a temporary JaCoCo 0.8.13 report using 72 focused tests: the seven
classes below plus PaneSaveCoverageTest. All 72 passed (01:46), with independent
JVMs and a real Xvfb display. These are mixed unit, UI, source and integration
tests, not a unit-only percentage or a measurement of the entire repository
suite. Source-file counters include nested classes/lambdas.

| Source file | Lines covered | Branches covered |
|---|---:|---:|
| ImportPreviewPanel.java | 796/1,440 (55.3%) | 176/420 (41.9%) |
| ImportPreviewService.java | 173/184 (94.0%) | 73/102 (71.6%) |
| SclxImportPreviewService.java | 723/875 (82.6%) | 417/632 (66.0%) |
| SclxImportCommitService.java | 1,297/1,555 (83.4%) | 390/632 (61.7%) |

The following counts inventory declared test cases; overlapping service/UI
coverage is not additive coverage of code paths.

| Test class | Cases | Scope |
|---|---:|---|
| ImportPreviewPanelTokenNormalizationTest | 2 | Account-type aliases/case and debit/credit aliases; pure unit tests |
| ImportPreviewPanelCoaAtomicSourceTest | 1 | Source guard for frozen atomic CSV commit route |
| ImportPreviewPanelSclxTest | 5 | Rendered counts/mappings/identity/diagnostics, donor confirmation, explicit mapping approval, blocking resolution visibility, same-file re-preview; visible Import title |
| ImportPreviewServiceTest | 8 | CSV duplicates/rejections/multiline/malformed quotes, statement metadata, normalized persisted duplicate, accepted/failed commit counts |
| SclxImportPreviewServiceTest | 17 | Read-only preview counts/identity/mappings, conflicts, donor compatibility, ownership, record/message choices and unsupported content |
| SclxImportCommitServiceTest | 30 | H2 commit/idempotency/rollback/mapping/identity/accounting/reconciliation and conflict behavior |
| SclxImportCommitAuthorizationTest | 1 | Service authorization boundary |

Additional coverage includes SclxImportPreviewProductionRouteSourceTest and the
real `PaneSaveCoverageTest.importSaveFollowsSelectedResultAndCancellationLeavesH2Unchanged`
workflow (selected-result Save routing, confirmation cancellation and actual CSV
commit). Bank format/normalization and atomic CSV/JSON import suites cover their
respective service paths. These are separate service/integration/source/UI
categories, rather than all being unit tests.

The measured screen gaps include bank preview/commit handlers and return to
reconciliation, CSV profile saving, asynchronous SCLX preview/re-preview/choice
handlers, progress/cancellation/error callbacks and native file choosers.
Dedicated panel tests do not establish exhaustive native file-chooser coverage
or a complete SCLX choose-file-through-confirmed-UI-commit scenario. The 30 SCLX
commit tests exercise the service authority, not a claim that all UI branches are
covered. No project dependency or CI coverage threshold is introduced here. The temporary
agent and CLI are `org.jacoco:org.jacoco.agent:0.8.13:runtime` and
`org.jacoco:org.jacoco.cli:0.8.13:nodeps`. Run the eight named classes with
`-DreuseForks=false`, a fresh append-mode agent data file and the configured
test user.home; report the matching compiled classes with the source tree.
Local evidence: `/tmp/c9-coverage-tests.log`, `/tmp/c9-import-coverage.exec`,
`/tmp/c9-import-coverage.xml` and `/tmp/c9-import-coverage-report/index.html`.
The counters above retain the result independently of those temporary files.
A focused follow-up UI-test slice should prioritize the bank and asynchronous
SCLX paths; no new import feature is implied.

## Owner checks

Open Import from navigation and verify its header/workspace tab say Import while
preview and confirmation still precede any commit. Close its tab and reopen it.
Open Period Close at narrow width and larger font; scroll the top form to its
last fields/actions and read the wrapped status. Use the header divider.
Preview Review Evidence, enter references/reasons/attestation in Draft References,
switch among the five peer tabs and verify no draft loss. Save a review through
the existing guarded action; inspect it in Recorded Reviews. Check viewer access,
busy state and company/date changes still invalidate or disable appropriately.
Native owner visual acceptance is pending; automated results go in PLAN.

Validation: baseline compile passed; focused 85-case run found one stale operating
guide name (corrected), followed by 17 passing guide/real Save/period regressions
and the 72-case measured run above. All six overflow cases, four evidence cases,
six close-state cases and 29 route checks passed on a display. Final clean verify
passed 1,166 tests with no failures/errors and 110 expected display skips (06:02).
Native owner checks and exact-head CI remain pending before merge.
