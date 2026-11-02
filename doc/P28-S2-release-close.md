# P28-S2 — Release and correction-program closure

Status: DONE through merged [PR #403](https://github.com/benbaron/sca-jakarta-h2/pull/403) at `da54b6c293163ca332fbf473106a9f8ca5775727`. Accepted head `99c5752cb38a98357713921a257f2ec80a89973b` passed exact-head workflow `41354367929`, retry job `124993311642`, including clean, repeat and production JavaFX stages.

## Scope

This final adopted slice publishes the actual-screen [operating guide](operating-guide.md), [release and upgrade notes](release-notes-P23-P28.md), exact tested authority and G1–G14 closure evidence. It removes obsolete workaround instructions from current guidance while retaining historical documents as factual records. It changes no accounting, schema, authorization, persistence, UI route or dependency.

P28-S1 merged through PR #402 at main `c1ba17743133d91b747c36ab74f40a6755ee0a45`. Its accepted head `ca66658b342802b6f56c8e3dcceb30484cf7b236` passed workflow `41274251144`, job `124753135048`, after local 218-test combined acceptance, 71-test actual-display and 1,109-test clean gates. P28-S2 must receive its own final local and exact-head gates before release closure.

## Required deliverables

- Actual navigation, button and menu labels for the P23–P27 workflows.
- Replacement of SQL, memo-only reference, clearing-account, spreadsheet-master and template-only workarounds with current application steps.
- Upgrade guidance through current Flyway V92 without editing migration history or replacing user databases.
- Whole-database backup/restore guidance distinct from selected-company SCLX.
- Supported SCLX read/write versions and generic packet boundary.
- Exact tested commit, CI and owner acceptance evidence.
- G1–G14 closure status with remaining factual limitations.

## Retained limitations

- External documents, signatures, custody and approval remain external procedures with stored references and factual attestations. The application has no attachment repository or independent approval workflow.
- Generic accounting packet output does not claim an official external form, filing deadline or signature policy.
- SCLX excludes credentials, application UI preferences, schema internals, raw attachments and other companies. Whole-database backup retains the complete database instead.
- Optional saved NMR policies are references only. Users enter accounting amounts and classifications explicitly; no external rate/default engine is implied.
- Older SCLX versions have the documented reduced contract. Unsupported future versions block before commit.

## Acceptance

All new document links and actual UI labels resolve against current source. `mvn clean verify` and the production JavaFX selector pass on the final bytes. The draft description records exact results and final published SHA/tree. After owner review and merge, verify the merged commit and CI, mark P28/P23–P28 DONE, and retain these limitations in the final PLAN ledger.

## Local validation

On 2026-11-01 America/Denver, `ReleaseOperatingGuideTest` passed two focused source/document checks. Final `mvn clean verify` passed 1,111 tests with zero failures/errors and 77 expected headless display skips in 05:51. The exact production JavaFX selector passed 69 tests with zero failures/errors/skips on a verified X display in 02:16. The first display launcher attempt lacked Xvfb and skipped 55 tests; it is not acceptance evidence. Changed-document links and whitespace checks pass. Exact-head GitHub clean, repeat and production JavaFX stages subsequently passed, and the owner merged the slice.
