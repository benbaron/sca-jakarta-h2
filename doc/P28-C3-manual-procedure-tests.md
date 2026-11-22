# P28-C3 — Executable user-manual procedures

## Scope

The owner asks for tests following the procedures in the authoritative
[Word manual](user-manual.docx). Read that document directly; do not regenerate
or edit it as part of test work. P28-C2/#439 remains independently open and
VERIFYING. Start this procedure-test slice from merged main ef1006f7 on
codex/P28-C3-manual-procedure-tests, preserving the C2 branch and its local receipt.

Map every manual chapter to behavioral tests. Reuse established coverage, and
add named scenarios that follow the manual's steps through real command/query
boundaries: company/chart/Fund/bank setup, balanced Journal examples and invalid
inputs, manual statements and independent accounts, reports, reviewed CSV import
and backup/restore handover. Assert saved identities, amounts, account/company
scope, read-only previews and rollback/protection, not prose or control names.

Use pure unit tests for input rules and integration tests where a procedure saves
or reloads records. Existing Jpa construction accepts file paths only; persistence
and restart/recovery scenarios use disposable @TempDir H2 databases through that
supported seam. No user database, private CSV or frozen archive changes, no
production persistence refactor, new dependency or accounting-policy change.

## Required reading and inspection

AGENTS, PLAN, Word manual including its tables, C1/C2 manual contracts, existing
company/chart/Journal/import/banking/report/recovery/close tests and their public
services. Existing donor decisions remain applicable; these tests exercise this
application, so no donor implementation or separate ledger is needed.

Baseline compile passes (24.5s); focused existing Journal/banking checks pass
10 tests, zero failures/errors/skips (01:13). Add an explicit chapter/procedure-to-test map with the distinction
between unit, persistence/integration and desktop tests. Navigation, readability,
external policy/evidence custody and the owner's actual handover remain manual
acceptance; automated tests must not claim to establish those facts.

## Validation and review

Run the new named scenario suite and adjacent existing tests, then required
clean verification and exact-head GitHub CI. Record actual results in PLAN/PR.
No new JavaFX layout is proposed. Tests must run without a display where their
procedure does not involve actual UI interaction. No retries or weakened oracles.

User-visible effect: stronger regression coverage plus the narrow chart-import
identity correction below. The reviewer can compare scenario names/data with the Word manual
and run the documented Maven selector. Keep VERIFYING until confirmed owner merge;
do not merge automatically.

## Narrow prerequisite correction found by the new tests

The first procedure run reproduced a real second-chart import failure: the
COA_JSON ACCOUNT receipt used a company-wide source-code key, so a second
CREATE_NEW_CHART with account 1000 rolled back with an identity collision.
Account codes are chart-local. Scope new account receipts with the target
chart's stable portable UUID in the receipt namespace, keeping the source code
as external ID for all modes and retaining every legacy alias unchanged. The chart receipt, file format,
authorization, idempotency and transactional rollback boundaries stay intact.
No migration or historical remapping. This small production correction
directly unblocks the documented chart procedure and is covered by fresh and
legacy-receipt cases plus adjacent JSON import tests. Governing contract:
[data-exchange/chart-of-accounts-json.md](data-exchange/chart-of-accounts-json.md).

The first run also had one incorrect test assumption that the banking instrument
reference would fall back to the informational transaction-header reference.
The manual distinguishes these fields. The corrected scenario checks that header
information is saved while instrument reference/cleared state stay independent.

[Procedure coverage map](user-manual-procedure-tests.md) links all 18 chapters
to relevant new/existing behavioral suites, identifying pure unit, persistence
and display coverage and the remaining human acceptance. The first corrected
focused run passes 27 tests, zero failures/errors/skips (01:15); the expanded run
passes 33 (01:19), including the explicit legacy-receipt case and all JSON
coverage. The final namespace form keeps Unicode account-code capacity intact;
the same 33-test gate is running on these final bytes. Full clean verification
and exact-head CI remain pending. Review this test map whenever a Word procedure
changes; Word remains authoritative and is unchanged by this slice.

Final focused gate passes **33 tests, zero failures/errors/skips (01:24)** on
the accepted namespace form: 23 new manual-derived cases (13 pure unit and ten
service/file integration) plus ten adjacent chart JSON cases. All 113 named
suites in the chapter map exist; local links and whitespace pass. The Word
document is byte-identical to current main. Full clean verification and exact-head
CI remain pending. Evidence: /workspace/p28-c3-evidence/.

Published [draft PR #440](https://github.com/benbaron/sca-jakarta-h2/pull/440),
implementation 15f87277c3a3f052f9a36d7327238487ee1ebf59, tree
9178453aced64f7f96a99e45c00df2fcb95a7b4b, by normal new-branch push after
confirming current main/absent remote. PR head matches. This publication receipt
changes documentation only; accept CI on its resulting head. Final clean
verification is running; exact-head CI remains pending.

## Final verification receipt

[PR #440](https://github.com/benbaron/sca-jakarta-h2/pull/440) is ready for review,
open/unmerged, accepted remote head cf5e19a82859b5c473cbcdc955090cb991ec4191,
tree 3f6c0b508ba61e5e36f25bb7ca7b5bb2ce4a596f. Native ref and PR metadata
match. This final local-only receipt preserves the green remote head; the PR
contains actual final results. Earlier pending-check progress notes are historical.

Final local clean verify passes **1,317 tests, zero failures/errors and 160
expected headless display skips (12:52)**. All 23 new cases ran without skips:
13 pure unit and ten service/file integration. Final focused gate passes 33
without failures/errors/skips (01:24).

[Exact-head CI 44982489768](https://github.com/benbaron/sca-jakarta-h2/actions/runs/44982489768)
passes both jobs. Linux 137085851305 passes rendering, clean/repeat, Period Close,
overflow, imports, Journal/SCLX feedback and production routes. Windows
137085851573 passes all 23 display tests across seven required suites with
zero failures/errors/skips; actual log totals were inspected. Final run/job/step
metadata and logs are retained under /workspace/p28-c3-evidence/, including the
four new full-run XML reports. No final executed gate failed.

No Word or user-data changes; no application/test changes after validation.
Remaining: owner review the mapped scenarios and merge #440. C2/#439 remains
independently open. Keep VERIFYING until confirmed merge; do not merge
or regenerate the authoritative Word manual automatically.
