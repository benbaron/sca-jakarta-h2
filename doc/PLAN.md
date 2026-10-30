---
plan_version: 422
active_phase: P27
active_slice: P27-S4D2
active_status: VERIFYING
active_branch: codex/P27-S4D2-category-portability
active_pull_request: https://github.com/benbaron/sca-jakarta-h2/pull/398
active_head: 71137b8c60923b5981fc41e0563374aca07c8f98
next_action: "Owner reviews and merges draft PR #398; all local and exact-head CI gates passed. After confirmed merge, carry final evidence to a fresh S4D3 integrated compatibility/recovery branch."
---

# SCA Bookkeeping Program — Codex Execution Plan

Final S4D2 local/CI gates and handoff — 2026-10-29 (America/Denver): final `mvn clean verify` passed at **21:11:16 with 1,099 tests, zero failures/errors and 77 expected display skips**. The exact production JavaFX selector passed under Xvfb at **21:16:27 with 69 tests, zero failures/errors/skips** (`/tmp/p27-s4d2-fx.log`). All 18 frozen source/test/schema/build hashes match both completed gates. Final published head `71137b8c60923b5981fc41e0563374aca07c8f98`, tree `8881b55187e37875ed8ecb6c5dc206d5ea8d3a1f`, matches local HEAD, native branch ref, archived PR #398 head and connected PR head. Local archive contains 398 PR heads. Exact-head [Maven PR Tests run 40996260724](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40996260724), job `123870215137`, completed successfully: clean headless verification, repeated tests and production JavaFX route compliance all passed. Independent PR/run/job inspection confirms the same published head and all three successful stages; the draft description records the result. Current open PRs are #398 and the older overlapping #396; current main remains the #397 merge. No known executed-check failure remains and no native human result is inferred. This final evidence stays local to preserve the remote CI head. S4D2 remains VERIFYING pending owner review/acceptance and merge; S4D3/P28 remain blocked. After the owner reports merge, verify current main, carry this final local-only record onto a fresh S4D3 branch and inspect the combined P24–P27 fixture/report/history/conflict/recovery acceptance matrix. Do not implement that slice on this branch.

S4D2 draft publication — 2026-10-29 (America/Denver): pushed implementation `42760301e8ea919c0d11c016d74a3e23a205fe03`, tree `38d8f431d469f8a58755514282e272de0cdc3995`, without force from confirmed main `f8206e45`. Native fetched ref and draft [PR #398](https://github.com/benbaron/sca-jakarta-h2/pull/398) confirm that exact head. The PR records complete master/legacy boundaries, V92 upgrade, all nine new scenarios, final 1,099-test clean / 16-test affected results and owner checks. Initial PR head #398 was fetched into the local archive, which now contains 398 PR heads. The exact production JavaFX selector is running; no GitHub success or native human result is inferred. This documentation-only successor changes none of the 18 frozen source/test/schema/build files; verify its resulting head/tree and exact-head CI before handoff. S4D2 remains VERIFYING; S4D3/P28 remain blocked.

### GitHub resync and S4D2 selection — 2026-10-29 (America/Denver)

[GitHub/archive rescan](github-state-sync.md) confirms S4D1 merged through **PR #397**, not #396. Current main `f8206e45a1f1f245956c6447db7c0c2625d8227c` contains accepted head `c852a10a5a913b6b548d8ea426d6ea0e19a2c681`; workflow `40809782195`, job `123237359004`, passed clean/repeat/production JavaFX. S4D1 is DONE. #396 remains an open overlapping draft at `fd3b6a99`; its old `5224626c` validation is not evidence for that new head. Do not merge/replay the obsolete implementation. All 397 PR heads are archived locally. Preserve the previous local branch/evidence separately rather than cherry-picking it over #397's newer records. No additional native human result is inferred.

Fresh `codex/P27-S4D2-category-portability` starts from verified main. Select [S4D2 category masters](P27-S4D2-category-portability.md) IN_PROGRESS. Inspection confirms code-only references currently lose names/descriptions/effective state/times and unused records; categories have no intrinsic portable UUID. Add nondestructive V92 UUIDs and a complete optional extension restored through BudgetCategoryAdminService inside the existing import transaction. Legacy absence keeps the code-as-name contract; no invented master facts or accounting. Production alias maintenance has no writer/reader; its legacy dictionary is a deliberate SCLX exclusion retained by whole-database recovery. Parent S4 remains incomplete; S4D3/P28 stay blocked. Baseline compile passed; existing category/budget/import tests run before production edits.

S4D2 implementation and focused gates — 2026-10-29 (America/Denver): V92 nondestructive category UUIDs, strict complete budgetCategories v1 snapshot/parser, native/source identity and code-conflict classification, exact caller-owned master restoration before Budget/Journal links, current-fact revalidation even on replay, and exact export/Preview counts are implemented. Historical inactive/effective-date assignments are restored without live Save replay. Legacy absence leaves financial identity hashes/code-as-name behavior unchanged; Keep target preserves master metadata/native links. No new UI command, workflow, dependency, ledger or accounting policy. Alias compatibility rows remain a documented selected-company exclusion and survive upgrade/recovery.

Baseline compile and 42 existing tests passed. New migration/file gate passed eight tests; broader category/budget/import/export gate passed **51 tests, zero failures/errors/skips**. Final review added constant-time target-ID lookup and source/native binding-drift rejection; the strengthened eight-test gate passed again, including target rename and identity tampering. Nine governing documents / 192 relative links and whitespace/scope checks pass. No known executed-check failure remains. Full clean verification, exact production JavaFX, draft publication/final-head CI and owner review/merge remain. S4D3/P28 stay blocked.

S4D2 final-source refinement: native category maintenance permits non-ASCII whitespace in a nonblank code, so the import DTO must not impose a stronger trimming rule. Added a service/export/parser/restoration regression proving exact Unicode-code preservation. The final nine-test migration/file gate passed, zero failures/errors/skips. The earlier full clean was deliberately stopped after this refinement; only `/tmp/p27-s4d2-clean-final.log` owns final full validation. No success is claimed for the stopped run.

S4D2 inventory-label regression correction: the first complete 1,099-test run had one failure, zero errors and 77 expected display skips. `InventoryMovementAccountingTest` still expected a new target to lose “Event Cost” and display “COST — COST”. It now asserts the preserved source label “COST — Event Cost” while retaining its exact accounting/function/reversal/link checks. The final affected inventory/category/migration gate passed **16 tests, zero failures/errors/skips**. Final frozen source covers 18 changed source/test/schema/build files. The prior failing log is retained as `/tmp/p27-s4d2-clean-before-label.log`; rerun final full clean verification before publication.

Final S4D2 clean gate — 2026-10-29 (America/Denver): final `mvn clean verify` passed at **21:11:16 with 1,099 tests, zero failures/errors and 77 expected display skips** (`/tmp/p27-s4d2-clean-final.log`). All 18 frozen source/test/schema/build hashes match the completed gate, including the Unicode-code and preserved inventory-label regressions. Final affected gate passed 16; the broader gate passed 51. Ten governing documents / 195 relative links and whitespace/scope review pass. The exact production JavaFX selector is running under Xvfb with the required environment permission. Main remains `f8206e45`; the fresh remote branch is absent. Draft publication/final-head CI and owner review/merge remain; S4D2 is VERIFYING and S4D3/P28 stay blocked. No source change follows this final clean gate.

### P27-S4D1 authorized draft publication — 2026-10-28 (America/Denver)

Owner explicitly authorized draft publication and CI with “proceed” in response to the publication request. Confirmed clean worktree, exact reviewed two-commit sequence, current main `6ed44f635aee5eefc63d861258cf29e96f668247` and absent remote branch before creating it at that base. Used the connected GitHub service; preserved commit messages/order, verified each changed blob and both tree IDs against local Git objects, and fast-forwarded with expected base and force disabled. No unrelated files were included.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `62b4bcf90ac1a84389f9f81dcb0f6f779236cb39` | `519612c3c3a1c7a5fa6714ce209049fc750f2dbf` | `ad84e9f2b9b60e33c2d496018edc6a2e60c8b7fe` |
| `99ee69045c9c76fe8ea390f0d4297cde5626a7ce` | `b8ec3ca7ff786d80c0abbf02d8210b5ddeefeb66` | `91f5f81a7e4b802804e06b9f2ce8c66d7a5b259c` |

Opened draft [PR #397](https://github.com/benbaron/sca-jakarta-h2/pull/397) at `b8ec3ca7ff786d80c0abbf02d8210b5ddeefeb66`. This publication-only successor records its known parent in active_head because a commit cannot embed its own SHA; publish it under the same authorization, verify final branch/PR head and matching tree, then inspect actual-head Maven clean/repeat/production JavaFX CI and correct failures. No S4D1 GitHub success is claimed yet. Final local source/test gates remain 1,090 clean tests (77 headless skips), 69 Xvfb tests (zero skips), zero failures/errors. S4D1 stays VERIFYING; owner acceptance and separately accepted merge remain. S4D2 category masters, S4D3 integrated/recovery acceptance and P28 remain blocked.

### P27-S4C merge and S4D1 selection — 2026-10-28 (America/Denver)

Current main is `6ed44f635aee5eefc63d861258cf29e96f668247`, confirmed by native fetch and GitHub PR #395. Accepted head `5856e0d917e8799c3467f4c3256f88de9f4d46c1` passed workflow `40617977330`, including clean/repeat/production JavaFX. S4C is DONE. This current plan supersedes this conversation's older S3 handoff; S3 is already closed and accepted in current main. Do not replay merged work. Fresh branch `codex/P27-S4D1-review-validation` starts at current main; no merged branch is reused or unpublished old evidence cherry-picked over newer records.

The [S4 audit](P27-S4-portability-audit.md) explicitly permits an independent evidence-hardening merge. Inspection found that preview does not invoke nested audit parsing, commit only bounds audit text, and history's Jackson record conversion accepts missing/coerced primitive fields and does not check reference/reason/fingerprint consistency. Category-master portability requires its own optional master graph/identity contract, distinct from recognized historical JSON validation. Select [S4D1 evidence validation](P27-S4D1-review-payload-validation.md) as one coherent slice; category-master completion and all-feature/recovery fixture remain S4D2/S4D3, not completed or bypassed. Parent S4 remains IN_PROGRESS and P28 BLOCKED.

Required reading/inspection: AGENTS/current PLAN, adopted S4 program and audit, S4B/C, SCLX/period-close/inventory contracts, generic packet and S3B1/B2; review service/payload/readiness types, audit snapshot/export validator, document parser/preview/commit, audit restoration seam and existing review/close/recovery/export tests. Donor MonthlyCloseChecklistService uses alternate gateways and unwired checklist state, with no compatible retained-review codec; retain canonical audit/service authority and import no donor model. No UI/schema/dependency/workflow change is planned. Recovered scratch Maven runtime/cache; baseline compile passed, focused existing review/audit/recovery tests are running. No later implementation or new native human acceptance is inferred.

S4D1 implementation checkpoint: shared strict bounded review reader, complete action/entity recognition, field/type/version/date/hash/reference/finding/reason and retained-command fingerprint checks now protect Preview/export/commit/restoration/history. Generic audit before/after/subject/reason text retains exact whitespace/empty/Unicode facts. Eight new tests cover dozens of malformed payload cases plus actual file/company/restart/retry/conflict/Keep target/no-write boundaries. Existing baseline compile/10 tests passed; broader focused review/audit/recovery/atomic-close gate passed 26; final affected import/reader/file gate passed 38 with zero failures/errors/skips. Initial full clean ran 1,090 tests with one obsolete test expecting a late actor-length error; the test now asserts the same diagnostic as a Preview blocker and still asserts commit denial/no writes. Final review bounded malformed-member diagnostics to existing Preview message capacity, with regressions. Superseded clean attempts were deliberately stopped; only the final clean/display records below own completion. No accounting/schema/UI/dependency/workflow change or unresolved executed functional defect is claimed from that obsolete assertion. Production JavaFX and final frozen-source clean verification remain pending at this checkpoint.

S4D1 frozen-source actual-display gate — 2026-10-28 (America/Denver): the exact production JavaFX selector passed under Xvfb with **69 tests, zero failures/errors/skips**, BUILD SUCCESS (`/tmp/p27-s4d-fx-final.log`). All ten changed source/test file hashes match the frozen snapshot. Final complete clean verification is now running on those same source bytes (`/tmp/p27-s4d-clean-final.log`); no full success or GitHub result is inferred yet.

### P27-S4D1 final local verification and publication handoff — 2026-10-28 (America/Denver)

Final frozen-source `mvn clean verify` passed **1,090 tests, zero failures/errors, 77 expected headless display skips**, BUILD SUCCESS (`/tmp/p27-s4d-clean-final.log`). The exact workflow production JavaFX selector passed **69 tests, zero failures/errors/skips** under Xvfb (`/tmp/p27-s4d-fx-final.log`). Final affected import/reader/file gate: **38 tests, zero failures/errors/skips**; broader review/audit/recovery/atomic-close gate: 26 passed. All ten changed source/test file hashes match both final completed gates. The earlier full run's sole failure was the obsolete late-diagnostic expectation; corrected Preview diagnostic/commit denial/no-write assertions pass in the final full gate. No executed functional failure remains. Generated tracked manifest is restored and excluded. Six governing documents / 146 relative links and whitespace/scope review pass.

Completed: confirmed #395 merge/final-head CI and current main; fresh S4D1 branch; shared bounded strict retained-review validation at Preview/export/commit/restoration/history; exact opaque audit text preservation; actual-file malformed/unsupported/changed/conflicting/Keep target/retry/restart/company/no-write regressions; bounded diagnostics; governing contract and owner checks. No UI command, migration, accounting policy, dependency or workflow change. No GitHub S4D1 or native human result is claimed.

Branch `codex/P27-S4D1-review-validation`, base confirmed main `6ed44f635aee5eefc63d861258cf29e96f668247`. Remote branch is absent and no PR is created. This implementation and its documentation handoff are committed locally; the following evidence-only successor records the implementation SHA/tree because a commit cannot embed its own identity. Resolve `git rev-parse HEAD` for the complete local tip. Publication requires explicit owner authorization under AGENTS.md. After authorization, confirm clean worktree/current main/expected remote branch, reproduce both reviewed commits in order with matching trees and no force update, open a draft PR, verify actual branch/PR head and final-head clean/repeat/production JavaFX CI, repair failures and record actual results. Owner tests [S4D1 notes](P27-S4D1-review-payload-validation.md) and separately accepts/merges. Parent S4 is IN_PROGRESS and S4D1 VERIFYING, not DONE. S4D2 category masters and S4D3 integrated fixture/recovery remain incomplete and sequentially blocked; P28 stays BLOCKED. Do not substitute this evidence hardening for full portability acceptance.

Verified S4D1 implementation commit `62b4bcf90ac1a84389f9f81dcb0f6f779236cb39`, tree `ad84e9f2b9b60e33c2d496018edc6a2e60c8b7fe`. This documentation-only successor records that verified head; resolve `git rev-parse HEAD` for the complete local tip. Publish both commits in reviewed order only after explicit owner publication authorization, comparing every tree and verifying the final PR head before CI. Worktree is clean after this successor. No application/test/schema/build change follows final clean/Xvfb verification and no remote S4D1 branch or PR has been created. Prepared draft title: “P27-S4D1: validate retained review payloads before interchange”; the contract and final verification paragraphs above provide its complete review/handoff basis. Category-master and integrated/recovery gates remain subsequent work.

S4C draft publication — 2026-10-27 (America/Denver): implementation `beed32d79ec022148da3f7b3e04240e1054c9f46`, tree `63a0c82e2a3b3f278266d51ab73353f3bc63876f`, was pushed without force to fresh `codex/P27-S4C-transfer-portability` on confirmed main `00aa3cb6`. Native fetched ref and draft [PR #395](https://github.com/benbaron/sca-jakarta-h2/pull/395) confirm the matching head. The PR records UUID upgrade/legacy preservation, full operational/canonical graph authority, source request provenance, all new scenarios, final 1,082-test clean result and owner checks. Production JavaFX is running; no GitHub success or native human result is inferred. This publication-only PLAN successor changes none of the tested source/test/schema bytes and requires its resulting head/tree and CI to be verified. S4C remains VERIFYING; S4D/P28 remain blocked.

### P27-S4B merge and S4C selection — 2026-10-27 (America/Denver)

Owner reported PR #394 merged. GitHub/native fetch confirm main `00aa3cb6` contains accepted head `3437de8b`, whose workflow `40604890977` passed clean/repeat/JavaFX. S4B is DONE. Fresh `codex/P27-S4C-transfer-portability` carries its final local evidence as `f9768e6f`; no additional native human result is inferred. Baseline compile and existing transfer/service/migration/interchange tests passed.

Select [S4C operational transfer portability](P27-S4C-transfer-portability.md) IN_PROGRESS. Inspection found no intrinsic transfer UUID and legacy unlinked rows, so nondestructive V91 retains all existing facts and supplies durable UUIDs. The versioned extension covers native saved/reversal facts and legacy DRAFT/VOID without manufacturing transactions. Existing canonical correction restoration is non-posting; metadata is attached afterward through FundTransferService. Request IDs/hashes remain source provenance; native-ID remapping never authorizes a changed replay. Preview checks global request collisions and incompatible kept transaction links; final service validation rechecks actual target ownership, shape and reversal graph, including retained identities. No new posting/approval UI, donor persistence or second ledger. Required reading/inspection, donor decision and owner checks are in the contract. S4D evidence/category-master/integrated coverage and P28 remain blocked.

S4C implementation/verification: V91 UUID migration, exact immutable transfer export/parser, intrinsic/source-specific identity integration, transaction metadata binding, request/kept-link preview checks and actual managed-graph restoration/validation. FundTransferService preserves historical facts after canonical correction restoration without Save/Reverse replay. Unlinked legacy facts mark a target populated; exact-retained retries validate native existence/ownership/accounting rather than acknowledging stale identity rows. Export/Preview counts and Fund Transfers retention copy reflect included links. Baseline compile / 13 existing tests passed. Final focused migration/transfer/export gate passed 11 tests, including seven new behavioral tests and the new V90-to-V91 upgrade case. A broader run's sole failure was its obsolete deferred-transfer warning expectation; that assertion is corrected and passed in the final focused gate. No current executed-check failure remains. Full clean verification, production JavaFX, publication/exact-head CI and owner checks remain pending. Generated target files, dependencies and workflow changes are excluded.

Final S4C review additionally rejects distinct external IDs carrying one intrinsic transfer UUID, preventing duplicate aliases from coalescing into one stored fact. The alias regression is in the final source. The initial clean run was intentionally stopped and restarted after this review fix; only `/tmp/p27-s4c-clean-final.log` owns full clean-gate validation. Existing canonical correction validation remains the status/inverse authority; transfer validation adds operational consistency without duplicating posting logic.

Final S4C clean verification — 2026-10-27 (America/Denver): `mvn clean verify` passed at **19:53:45 with 1,082 tests, zero failures/errors and 77 expected display skips**. This includes seven new transfer behavioral tests, the V90-to-V91 UUID/guard upgrade regression, alias rejection and updated section/count assertions. Eight governing documents / 178 relative links and whitespace/scope review passed. The exact production JavaFX selector is running; draft publication/exact-head CI and owner checks remain. S4C stays VERIFYING and P28 stays blocked. No generated target files or dependency/workflow changes are included.

Final S4B exact-head CI and handoff — 2026-10-27 (America/Denver): [Maven PR Tests run 40604890977](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40604890977), job `122542260050`, **passed clean verification, repeated tests and production JavaFX** on published head `3437de8b6278c281e55cb240340b1039fdc488fc`, tree `cdb9ee451a942025a8fd44d2f4525dfe8977552e`. Native fetched ref and draft PR #394 head match; final source/test/build bytes remain identical. The PR description records exact-head success, local 1,074-test clean / 69-test actual-display results, scope, regressions and owner checks. No known executed-check failure remains. This final local-only PLAN successor is intentionally not pushed solely to restart green CI; carry it after owner reports merge. S4B remains VERIFYING, not DONE, pending review/merge. No native human test result is invented. Next: verify PR #394 merge/current main, start a fresh S4C branch and inspect FundTransfer original/reversal/request relationships, canonical import/correction, existing identity/conflict/Keep target and snapshot/preview/commit boundaries before implementing its versioned extension. S4D structured-evidence/category-master/integrated fidelity work remains incomplete, and P28 stays blocked.

Final S4B local gates — 2026-10-27 (America/Denver): final clean passed at **17:49:42 with 1,074 tests, zero failures/errors and 77 expected display skips**; the exact production JavaFX selector passed under Xvfb at **17:54:20 with 69 tests, zero failures/errors/skips** (`/tmp/p27-s4b-fx.log`). Published head `3437de8b6278c281e55cb240340b1039fdc488fc`, tree `cdb9ee451a942025a8fd44d2f4525dfe8977552e`, matches native fetched ref and draft PR #394. Test/application/schema bytes equal the final tested source. Workflow `40604890977`, job `122542260050`, is running clean verification; no GitHub success or native human result is inferred. The workspace connection interruption did not stop the local FX process or the completed publication. This final local-only evidence preserves the remote CI head rather than restarting CI for documentation alone. S4B remains VERIFYING; S4C/S4D and P28 stay blocked.

S4B draft publication — 2026-10-27 (America/Denver): implementation `6f677607d7d5f655b7d4796338004dd00da489ee`, tree `c06dd76c26fa9fc5334b57a2232877609e0f6135`, was pushed without force to fresh `codex/P27-S4B-budget-metadata` from confirmed main `8500d99b`. Native fetched branch and draft [PR #394](https://github.com/benbaron/sca-jakarta-h2/pull/394) confirm the matching head. The PR records the six new scenarios, V50 insert ordering, exact inactive-identity regression, final 1,074-test clean result, compatibility limits and owner checks. Production JavaFX is running; no GitHub success is claimed yet. This publication-only PLAN successor requires its resulting exact head/tree and CI to be verified; it changes none of the tested application/test/schema bytes. S4B remains VERIFYING, parent S4 stays incomplete and P28 stays blocked.

### P27-S4A merge and S4B selection — 2026-10-27 (America/Denver)

Owner reported PR #393 merged. GitHub/native fetch confirm main `8500d99b` contains accepted head `a5263d26`, whose workflow `40442798593` passed clean/repeat/JavaFX. S4A is DONE. Fresh `codex/P27-S4B-budget-metadata` carries final local evidence as `79f19870`; no additional native human result is inferred.

Select [S4B complete budget metadata](P27-S4B-budget-metadata.md) IN_PROGRESS. Baseline compile and focused existing budget/import/lifecycle tests passed. Inspection confirms that existing canonical budget services already support non-calendar ranges and archived plans; the loss is at the core-only interchange representation. An optional complete version-1 plan/line metadata extension preserves those facts through the existing caller-owned service, identity/conflict and Preview/Save boundaries. No migration, second budget/ledger, new Save or policy gate. Preserve nullable missing legacy times as unknown facts. Source-only enriched lines beneath an explicitly kept target plan reject before mutation. Budget Category descriptive/effective metadata is a separate audit concern, not silently claimed as part of plan/line fidelity. S4C/S4D and P28 remain blocked.

S4B implementation/verification: strict complete budgetMetadata v1 export/parser and canonical identity projection; exact non-calendar dates/status/notes/times through BudgetPlanService's caller-owned creation/history boundary; cross-calendar month validation, preview counts, changed/stripped metadata conflicts and Keep target guards. Historical status/timestamps are set before the initial insert to satisfy existing V50 activation constraints. A reproduced partial-reimport defect now permits the exact retained AS_IS fund identity when source and target are both inactive, without enabling inactive remapping. Six new behavioral tests passed, then 66 cross-feature budget/import/close/auth regressions passed. Final review added explicit persisted microsecond/UTF-8 capacity rejection and immutable nested export values. The first full run was intentionally stopped and restarted after that final snapshot refinement; only the final run owns clean-gate validation. Full clean, production JavaFX, publication and exact-head CI remain pending. No known current test failure remains. No migration/workflow/dependency changes, private financial rows or generated target artifacts are included.

Final S4B clean verification — 2026-10-27 (America/Denver): final `mvn clean verify` passed at **17:49:42 with 1,074 tests, zero failures/errors and 77 expected display skips** (`/tmp/p27-s4b-clean-final.log`). This includes the final immutable snapshot, precision/UTF-8 capacity and all invalid lifecycle cases. Eight governing documents / 174 relative links and whitespace/scope review passed. The production JavaFX selector is running; draft publication and exact-head CI remain next. No human desktop result or GitHub success is claimed. S4B stays VERIFYING; S4C/S4D and P28 stay blocked.

Final S4A exact-head CI and handoff — 2026-10-26 (America/Denver): [Maven PR Tests run 40442798593](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40442798593), job `121987169044`, **passed clean verification, repeated tests and production JavaFX** on published head `a5263d26c2fef9e655c6f13282c90898c14e636d`, tree `e1de7842322381d9f2c8dd7830e0c5e75e10405f`. The PR description records exact-head success and local 1,068-test clean / 69-test actual-display results. No known executed-check failure remains. Native remote and PR head were verified; final source/test/build bytes remain identical. This final local-only PLAN evidence follows the green remote head and is intentionally not pushed solely to restart CI. S4A remains VERIFYING pending owner review/merge. No native human acceptance is invented. Next: verify PR #393 merge/current main, carry this evidence to a fresh S4B branch, inspect BudgetPlan/Line/Category and existing SCLX metadata/identity/import contracts, then preserve full dates/archive/notes/times with explicit compatibility/conflict/rollback tests. S4C transfer links and S4D evidence/integrated acceptance remain incomplete; P28 stays blocked.

Final S4A local gates — 2026-10-26 (America/Denver): final `mvn clean verify` passed at 21:10:06 with **1,068 tests, zero failures/errors, 77 expected display skips**; the exact production JavaFX selector passed under Xvfb at 21:14:37 with **69 tests, zero failures/errors/skips**. Published head `a5263d26c2fef9e655c6f13282c90898c14e636d`, tree `e1de7842322381d9f2c8dd7830e0c5e75e10405f`, matches native remote ref and PR #393 head. Final test/application/schema bytes match that published head. Run `40442798593`, job `121987169044`, is in progress; no CI success or native human check is inferred. The PR description records both local gates. This final evidence stays local to avoid restarting exact-head CI for documentation alone; S4A remains VERIFYING, S4B/C/D remain blocked until their preceding merges, and P28 remains blocked.

S4A draft publication — 2026-10-26 (America/Denver): implementation `248dd8e5c272b7187398c40b5fb062eddbe4c3a6`, tree `386cc4b3b4f79527df7b393d064f80f37a048b25`, was pushed without force to `codex/P27-S4A-portability-audit`. Native fetch and draft [PR #393](https://github.com/benbaron/sca-jakarta-h2/pull/393) confirm the exact matching head on base `76fe8450`. The PR records gaps, test scenarios and owner checks. Full local clean verification passed 1,068 / zero failures/errors / 77 expected display skips, the focused cross-feature suite passed 57, and the final warning-reason subset passed three. Production JavaFX is running. Initial-head CI run `40442749069` has started; no GitHub success is claimed. This publication-only PLAN successor requires verification of its resulting head/tree and CI; it does not change the tested application/test/schema bytes. Status remains VERIFYING until review/merge; parent S4 remains incomplete. Relative documentation links (156) and whitespace/scope review passed; generated target files are excluded.

### P27-S3B2 merge and S4A audit selection — 2026-10-26 (America/Denver)

Owner reported PR #392 merged. GitHub and native fetch confirm main `76fe8450` contains accepted head `e3091452`, whose clean/repeat/JavaFX workflow `40428989992` passed. S3B2 and parents S3B/S3 are DONE. Fresh `codex/P27-S4A-portability-audit` carries local final evidence as `84d5d916`. No additional human upgrade/desktop result is inferred from the merge message.

Select [S4A inventory and recovery gate](P27-S4-portability-audit.md) IN_PROGRESS. Baseline compile passed. Required reading/inspection and donor decision are recorded in that contract. The audit found missing operational transfer links, lost budget dates/archive/notes/times, and generic audit import's lack of recognized nested review-payload validation. These are pending S4B budget metadata, S4C transfer links and S4D evidence validation/integrated compatibility, not deliberate exclusions or completed coverage. Parent S4 remains IN_PROGRESS; P28 remains blocked. S4A changes tests/documentation only and corrects stale inventory v1/C6 documentation.

Three new real-boundary tests passed: actual SCLX review/close/reopen/history into a differently named company with restart/no-op retry; changed/blocked target close rejection with no partial writes; whole-database recovery comparing every stored row/column plus triggers for two companies, reviewed close and reversed transfers, followed by exact native retries and restored period protection. Focused cross-feature portability passed 57 tests; the final strengthened actual-warning-reason subset passed three. Full `mvn clean verify` passed at 21:10:06 with 1,068 tests, zero failures/errors and 77 expected display skips. Production JavaFX, draft publication and exact-head CI remain pending. Known initial compile typo in the new test's requireOpen call was corrected before these passes. No known executed test failure remains; no S4B/C/D implementation is included.

Final S3B2 publication/CI evidence — 2026-10-26 (America/Denver): corrected published head `e30914525ad7d6b4163d5b6587c06647a54a16d1` matches the fetched remote branch and open draft PR #392 head exactly, reviewed tree `c70f6aa781b15f3a68beb3cb77f09d050f2551f7`. [Maven PR Tests run 40428989992](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40428989992), job `121941777354`, completed successfully: clean verification, repeated tests and production JavaFX all passed. The corrected head also passed local `mvn clean verify` at 18:54:56 with 1,065 tests / zero failures/errors / 77 expected display skips, followed by the exact production Xvfb selector at 18:59:15 with 69 tests / zero failures/errors/skips. The PR description records the actual-head result and test-only layout synchronization correction. No known executed-check failure remains. This final PLAN evidence stays local to preserve the green remote head. S3B2 remains VERIFYING pending native owner upgrade/close/reopen checks and merge; no human result is inferred. After owner reports merge, verify current main, carry this evidence to a fresh branch, close parent S3/S3B and select P27-S4's integrated SCLX/backup compatibility audit without reusing the merged branch or asking for official-template/rate-policy inputs.

Published implementation `1b51e95dae57d13e6828fc4960cf4b640b475fd4` on fresh `codex/P27-S3B2-close-enforcement` and opened draft [PR #392](https://github.com/benbaron/sca-jakarta-h2/pull/392) against verified main `6b1d4a34`. The PR records the guarded close behavior, V90 deployment/serialization boundary, cascade correction, actual final gates, fixture rationale and native owner checks. This documentation-only successor records its known implementation parent; verify final remote head/tree and require CI for that exact commit. No S3B2 GitHub success or human upgrade/desktop result is claimed yet. S3B2 remains VERIFYING; parent S3 and P27-S4 do not advance before merge.

Initial final-head workflow `40426942871`, job `121935408371`, passed clean verification and repeated tests but failed the display stage at AssetAcquisitionPanelTest:220, checking the horizontal scrollbar immediately after asynchronous row loading and before the skin's first layout pulse. The downloaded failed-test artifact identified this as the sole display error. The geometry test now waits, using its existing bounded FX polling helper, for that exact visible horizontal-scroll condition before retaining all original assertions. Production geometry/accounting code is unchanged. This successor requires its own final-head CI; no initial-head success is inferred from the passing headless stages.

The corrected AssetAcquisitionPanelTest passed all three actual-display tests locally. Publish this reviewed synchronization-only test/PLAN successor to the same draft, verify its remote head/tree and run the full local clean/expanded-display gates while inspecting the successor CI. The active_head field records the known parent; resulting actual-head validation supersedes the initial workflow.

Final S3B2 local verification — 2026-10-26 (America/Denver): post-fix `mvn clean verify` passed at **18:25:06 with 1,065 tests, zero failures/errors, 77 expected display skips**. The exact expanded production JavaFX selector passed under Xvfb at **18:29:47 with 69 tests, zero failures/errors/skips**. Both include the final session-publication/timestamp and H2 cascade fixes. The original merchant migration regression plus new upgrade/cascade/rollback/concurrent-close subset passed seven tests before the full gate. Relative documentation links (147) and whitespace review passed. No known executed-check failure remains. Remaining: draft publication, exact-head GitHub CI and owner [S3B2 upgrade/close/reopen checks](P27-S3B2-close-enforcement.md). No native human result is inferred; S3B2 remains VERIFYING and P27-S4 remains blocked until merge.

### P27-S3B1 merge and S3B2 close enforcement — 2026-10-26 (America/Denver)

Owner reported PR #391 merged. GitHub/native fetch confirm accepted head `6291fcfc` in current main at `6b1d4a34fe7f56aa056dc9d6a9d717c406081cb0`; final-head workflow `40337557545` passed clean/repeat/JavaFX. S3B1 is DONE. Fresh `codex/P27-S3B2-close-enforcement` carries the final local CI evidence. The [S3B2 contract](P27-S3B2-close-enforcement.md) adopts a coarse database transaction guard at the H2 write boundary, shared with canonical close assessment and write-time period checks. This covers operational writers without relying on UI locks or a content hash as a mutex. Parent S3 is incomplete until enforcement is merged.

Implementation adds nondestructive V90 SQL installation of BEFORE statement guards across current application tables, excluding the mutex/Flyway/security-denial audit table. The canonical packet assembler is shared by guarded live close assessment without making a backup claim. PeriodCloseRangeService requires an exact-scope review, matching canonical revision, zero blockers and reasons for all warnings, holds the guard through range/event/audit commit, rechecks actor/permission and uses review identity for idempotent retry. Reopening/reclosing retains history and requires a fresh review. Interactive null-guard constructors follow the same gate; historical recovery/import continues through the governed interchange seam. The FX close action captures immutable request context, disables duplicate submission and reports service failures. No accounting entry, approval queue or policy default is introduced. Donor alternate/unwired checklist paths were inspected and not ported.

Baseline compile passed. Focused close/protection/auth regressions passed 14 tests; expanded accounting/review/concurrency gate passed 15. Final V90 SQL/mutex/concurrent-close subset passed six tests, including preserved V89 history, complete trigger inventory, missing-guard failure, both writer orderings, simultaneous close idempotency, post-persist rollback and permission revocation. Actual JavaFX subset passed ten checks including the new missing-review/busy/permission action. Existing closed-period fixtures now restore factual historical closes through the production interchange seam; actual gated close is covered by AtomicPeriodCloseTest and guarded-role tests. Full clean and expanded production display gates remain pending. No GitHub or native owner result is claimed.

The first complete final-source run exposed H2's auto-commit statement-trigger behavior during a merchant component FK cascade (MerchantSettlementMigrationTest); that was the sole error among 1,065 tests. Trigger-local H2 auto-commit suppression now surrounds only the lock query, restoring the mode without committing the caller. New migration checks cover the reproduced cascade, MERGE and rollback/connection-mode preservation, while retaining the original failing test. Earlier clean verification was intentionally restarted for session-publication/timestamp fixes; only the final post-fix gates below own validation.

Final S3B1 publication/CI evidence — 2026-10-26 (America/Denver): published head `6291fcfce8295565c4e33d91bc45517d61b40545` matches the fetched remote branch and open draft PR #391 head exactly, reviewed tree `a332dd370f6a56be7a52378e2385e974a4a5aec1`. [Maven PR Tests run 40337557545](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40337557545), job `121623782297`, completed successfully: clean verification, repeated tests and production JavaFX all passed. The PR description records exact-head CI and final local 1,058-test clean / 68-test actual JavaFX results. This final evidence remains a local-only PLAN successor to preserve the green remote head. S3B1 stays VERIFYING pending owner review/acceptance and merge; no native human result is inferred. S3B2 remains blocked until that merge. After owner reports merge, verify current main, carry this evidence to a fresh branch and inspect all readiness-source writers before designing shared serialization and atomic PeriodCloseRangeService revalidation. Reuse neither this task branch nor a content hash as a concurrency lock. Preserve factual evidence, reasons, native retry and close/reopen history; generic accounting rules remain authoritative.

Published S3B1 implementation `a4f36227d399f5db260ea7171752aaa12b86fd02` on fresh `codex/P27-S3B1-review-evidence` and opened draft [PR #391](https://github.com/benbaron/sca-jakarta-h2/pull/391) against verified main `ad21bfab`. The PR records factual-review scope, serialization/enforcement remaining in S3B2, final local gates, unit scenarios and owner checks. This documentation-only publication successor records its known implementation parent; verify its resulting remote head/tree and inspect CI for that exact commit. No S3B1 GitHub success or human native result is claimed yet. S3B1 stays VERIFYING and S3B2 stays gated on merge.

Final S3B1 local gates — 2026-10-26 (America/Denver): `mvn clean verify` passed at 08:05:58 with **1,058 tests, zero failures/errors, 76 expected display skips**. The expanded exact production JavaFX selector passed under Xvfb at 08:11:04 with **68 tests, zero failures/errors/skips**. These gates include all final source/test changes. Focused service gate passed 16 tests; focused display gate passed nine tests and the final layout subset passed three. Relative documentation links (144) and whitespace review passed. No known executed-check failure remains. Remaining: draft publication, exact-head GitHub CI and owner [S3B1 review steps](P27-S3B1-review-evidence.md). No human desktop/spreadsheet result is inferred; S3B1 remains VERIFYING and S3B2 remains blocked until merge.

### P27-S3A merge and S3B1 evidence selection — 2026-10-26 (America/Denver)

Owner confirmed PR #390 merged. GitHub/native fetch confirm accepted head `41b461bf` in current main at `ad21bfab2dd5c2add08f198e082dbe960e712798`; final-head workflow `40260820386` passed clean/repeat/JavaFX. S3A is DONE. Fresh branch carries its final local CI evidence. Inspection found PeriodCloseRangeService does not serialize all operational writers used by readiness; a content hash alone cannot make its separate snapshot atomic with closing. Split remaining S3B into [S3B1 durable snapshot review facts](P27-S3B1-review-evidence.md) and S3B2 shared writer serialization/close-commit revalidation. This is a scope decision, not a claim of enforcement: S3B1 leaves existing close/reopen behavior unchanged and cannot waive blockers or authorize close. Parent S3 remains incomplete until S3B2 merges.

S3B1 adds CloseReviewEvidenceService over immutable AuditEvent facts, with versioned one-MiB structured payloads, explicit external-reference self-attestation, optional warning reasons, exact native request retry/restart and authenticated actor/time. Save recaptures canonical accounting content and rejects a changed preview, unknown warning identities, blocker reasons, conflicting identities and denied permissions; it records a historical snapshot, not atomic close readiness. Period Close's Review Evidence tab exposes real Save Review/shared Save, reference/reason drafts, retained history, busy/access/dirty guards and scope invalidation. Accounting packet format accounting-4 adds exact-period review headers, references, recorded findings and reasons without including those facts in the accounting content revision. Existing generic audit SCLX and whole-database backup preserve the payload; no migration, ledger or close-state changes. Baseline compile, 16 focused service regressions and nine focused actual-JavaFX checks passed, followed by three passing layout checks including bounded editor scrolling, horizontal table scroll and divider movement. Final clean verification and expanded production JavaFX remain pending; no GitHub or human desktop result is claimed.

Final S3A publication/CI evidence — 2026-10-25 (America/Denver): published head `41b461bf16b043d0581c469ba6569ff9c6fb60d0` matches the fetched remote branch and open draft PR #390 head exactly; reviewed tree `b536743086164759dc09dda67f583ea004043a8d`. [Maven PR Tests run 40260820386](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40260820386), job `121372950976`, completed successfully: clean verification, repeated tests and production JavaFX all passed. The PR description records that exact-head result and final local 1,050-test clean / 65-test actual JavaFX gates. This final PLAN evidence remains a local-only successor to preserve the green remote head. S3A is VERIFYING pending owner review/acceptance and merge; no native human result is inferred. S3B remains blocked until this merge. Next: after owner reports merge, verify current main, carry this evidence to a fresh S3B branch and implement durable factual evidence, warning-exception reasons and commit-time readiness revalidation under the existing period-close authority. Do not reuse the merged branch or treat the content revision alone as a concurrency token.

Published implementation `2635bc7030c4f6d13447a9dd21aff1620f89993a` on fresh `codex/P27-S3A-readiness-preview` and opened draft [PR #390](https://github.com/benbaron/sca-jakarta-h2/pull/390) against verified main `fac8b071`. The PR records the bounded preview scope, final local gates, scenario coverage and concrete owner checks. This documentation-only publication successor records its known implementation parent; verify the final remote head/tree and require CI for that exact commit. No S3A GitHub success or native owner result is claimed yet. S3A remains VERIFYING, parent S3 incomplete and S3B gated on this merge.

Final local S3A gates — 2026-10-25 (America/Denver): `mvn clean verify` passed at 20:41:33 with **1,050 tests, zero failures/errors, 73 expected display-dependent skips**. The expanded exact production JavaFX workflow selector passed under Xvfb at 20:46:12 with **65 tests, zero failures/errors/skips**. All final source/test behavior is included in these gates; 142 relative documentation links and whitespace review passed. No known executed-check failure remains. Initial financial-opening test precision was corrected to the existing two-decimal account-opening column; no schema/accounting precision policy was changed. Remaining: draft publication, final-head GitHub CI, owner [S3A checks](P27-S3A-readiness-preview.md) and merge. No human desktop/spreadsheet result is inferred.

### P27-S2B merge and P27-S3A selection — 2026-10-25 (America/Denver)

Owner reported PR #389 merged. GitHub/native fetch confirm accepted head `62f0a051` in current main at `fac8b07142be413106fff36dc77366f459c3535d`; exact-head workflow `40241876620` passed clean/repeat/JavaFX. S2B and parent S2 are DONE. Fresh branch carries the final local CI evidence. S3 is split into a vertically usable [S3A readiness preview](P27-S3A-readiness-preview.md) and S3B durable evidence/exception reasons with close-time revalidation. S3 is not DONE until both merge; S3A does not certify readiness or change close authorization/enforcement.

S3A implementation reuses one factored packet snapshot capture for preview/export, adding immutable typed findings, stable finding identities, a content revision and explicit Trial Balance/Balance Sheet blockers plus outstanding/unsettled/transfer warnings. Packet format accounting-3 includes the matching readiness worksheet/CSV and manifest basis/counts/revision. Period Close's Readiness tab captures asynchronously, exposes existing correction destinations, invalidates on date/company/database/access changes, discards stale results and clears previous findings on errors. Existing close/reopen and ledger authorities are unchanged. Donor MonthlyCloseChecklistService was inspected; alternate gateways/unwired placeholders are not ported. No migration or SCLX shape changes. Baseline compile, 11 focused service tests and six focused actual-JavaFX tests passed. New cases exercise content/cutoff/company isolation, changed revisions, financial/classification blockers, unsettled warnings, packet parity, capacity/permission failure and post-capture revocation, no source writes, background/busy behavior, correction routing, scope-away-and-back discard, company/database change and error clearing. Full clean and expanded workflow-display gates remain in progress; no new GitHub or human desktop result is claimed.

Final S2B publication/CI evidence — 2026-10-25: published head `62f0a051ca043ac6efc8b591853acdc178a741c6` and fetched remote head/tree match exactly, tree `764ce55c66320fbc5f1d4030b5bd28f4982daa59`. [Maven PR Tests run 40241876620](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40241876620), job `121315249403`, passed clean verification, repeated tests and production JavaFX checks. The draft PR #389 description records final-head CI and the final local 1,043-test clean / 62-test actual JavaFX results. This evidence is a local-only PLAN successor to preserve the green published head. S2B stays VERIFYING pending owner spreadsheet review/acceptance and merge; no human spreadsheet result is inferred. After the owner reports merge, verify current main, carry this evidence forward to a fresh branch and select the next unblocked slice; do not reuse this branch or start S3 before S2B merges.

Published implementation `6a0313b780a6bd1702086e857ccf1211fc37435b` and opened draft [PR #389](https://github.com/benbaron/sca-jakarta-h2/pull/389) against verified main `54d939fc`. The PR records scope, unit scenarios, final clean/actual JavaFX gates and owner spreadsheet checks. This documentation-only publication successor records the known implementation parent; confirm its resulting remote head and tree and require CI for that exact commit. No S2B GitHub success or human spreadsheet result is claimed yet. S2B stays VERIFYING; owner review and merge remain.

Final S2B local verification — 2026-10-25: `mvn clean verify` passed at 22:42:04 UTC with **1,043 tests, zero failures/errors, 70 expected headless display skips**. The exact workflow production JavaFX selector passed under Xvfb at 22:46:32 UTC with **62 tests, zero failures/errors/skips**. Both gates include the final supporting reconciliation tables, NMR opening controls and populated workbook/CSV checks. Relative documentation links and `git diff --check` pass; no source/test changes follow these gates. Draft publication, exact-head CI and owner spreadsheet review/merge remain. P27-S2B is VERIFYING; S2 and S3 do not advance before merge.

### P27-S2A acceptance/merge and S2B operational completion — 2026-10-25

Owner confirmed PR #388 merged. GitHub and native fetch confirm accepted head `d63a8fbff7fc0f88b37d170d1d63ff1d0c3118f5` in current main at `54d939fc1d60b805a622d42632ccbd3aca12e3a5`; exact-head workflow `40236217512` passed. S2A is DONE. Fresh `codex/P27-S2B-operational-packet` carries its local final-CI evidence and implements the [S2B contract](P27-S2B-operational-packet.md). This supersedes historical S2A merge-pending statements below.

The existing private snapshot now supplies the enriched ledger, bank activity/outstanding items/configuration, read-only reconciliation sessions/supporting lines, checks, all six supplemental schedules and opening/closing controls, dated NMR/merchant movements, assets/depreciation/acquisition links, inventory/supplies/movements, historical custody, retained budgets/active fiscal-YTD comparisons, transfers and full Exceptions and Notes. The UI exports an accounting packet and reports its exception count. Missing data never becomes invented zeroes or close-readiness certification. No schema, SCLX, ledger or policy default is introduced. Donor placeholder projections are not imported; canonical production queries are reused.

Scenario coverage adds populated operational CSV/XLSX parity, earlier outstanding items with future clearing, NMR/merchant partial settlement and observations, six-schedule controls/period effects, asset/depreciation/inventory facts, custody cutoff/future correction, canonical budget identity/YTD, saved opposite-direction transfer reversals, 5,001-row capacity refusal and unchanged source session/ledger/audit facts. Existing company isolation, precision, year boundary, complete-ledger, permission, overwrite and actual JavaFX tests remain. Initial clean verification passed 1,043 tests with zero failures/errors and 70 headless display skips; final supporting-table checks and final gates follow. User checks are in the S2B contract. No human spreadsheet result or S2B GitHub success is claimed yet.

Final S2A publication/CI evidence — 2026-10-25: published PR #388 head `d63a8fbff7fc0f88b37d170d1d63ff1d0c3118f5` and its fetched remote ref match exactly, with tree `1ea610331dab77d5119475dde5ca4b4f7f180f67`. [Maven PR Tests run 40236217512](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40236217512), job `121298519862`, completed successfully: clean verification, repeated tests and production JavaFX checks all passed. The PR description records this result and the final local 1,040-test clean / 62-test actual JavaFX gates. This final evidence stays local to preserve the green published head. S2A remains VERIFYING for owner review/acceptance and merge; parent S2 is incomplete and S2B stays blocked until that merge. No human spreadsheet result is claimed.

Published implementation head `3348b05322a19ddbb49f2b2c71b26f0e5c772bb1` on `codex/P27-S2A-core-packet` and opened draft [PR #388](https://github.com/benbaron/sca-jakarta-h2/pull/388) against confirmed main `a5c6ef17`. This publication-only successor records the draft and completed local gates. Confirm its resulting remote head/tree and inspect CI for that actual commit; no GitHub success is claimed yet. Owner review/merge and separate human spreadsheet checks remain.

### P27-S1 closeout and P27-S2A implementation — 2026-10-25 (America/Denver)

Owner confirmed and merged PR #387. GitHub/native fetch confirm merge `a5c6ef171519948e9d141ab6df4494c5acea70ee`, containing accepted head `6ac81bcf`; workflow `40232697699` passed. S1 is DONE. Fresh `codex/P27-S2A-core-packet` starts from that main. S2 is split into S2A core export and S2B completion, retaining every adopted generic packet requirement and the existing sequential merge prerequisite.

[S2A](P27-S2A-core-packet.md) adds Report Library's **Export Core Packet (all funds/events)** with independent always-visible Packet start/end dates, live EXPORT gating and background execution. One consistent private H2 backup copy supplies canonical Trial Balance, Balance Sheet, Income Statement, complete GL and Functional Expenses. ZIP output contains a new XLSX, five CSV reports and manifest with snapshot SHA, company, dates, exact-decimal policy and explicit incomplete/omitted-section status. Source backup bytes and other-company data are never included. The service rejects existing destinations, stages output and checks permission again before publication; temporary snapshots are removed on success/failure. No ledger, schema, period-close or reconciliation mutation is introduced.

Source inspection covered FinancialReportService, report execution/table/CSV adapters, FunctionalExpenseQueryService/renderer, DatabaseTransferService and Report Library routing/preferences. Donor `c697630e` has individual statement/table exports rather than a compatible consistent packet, so production authorities/POI are reused. Functional expense export now has an untruncated rendering entry point while screen limits remain intact. Unit cases cover 502 GL rows, 501 expense lines, exact decimals, canonical totals, company/cutoff separation, year-end/reversal dates, deterministic report contents, capacity overflow, invalid/denied/overwrite paths and unchanged source transaction/audit counts. Actual JavaFX coverage exercises the action, background completion, permission/busy state and independent packet-date controls via production routing. CI's display selector includes the new action test. Final validation and publication evidence follow; no human spreadsheet result is inferred. Operational schedules/enriched ledger remain S2B and the packet never claims complete readiness.

Final local verification passed: `mvn clean verify` at 2026-10-25 15:34:20 America/Denver reported **1,040 tests, zero failures/errors, 70 expected headless display skips**. The final expanded production JavaFX selector passed **62 tests, zero failures/errors/skips** under Xvfb at 15:30:15. Focused service/functional regression testing passed 12 tests; final clean verification includes the capacity guard added afterward. The packet date controls were revised after the first clean run so they remain accessible independently of as-of report selection; both final gates include that revision. Documentation links and whitespace review passed; the build helper restored tracked generated output. Remaining: draft publication, exact published-head CI and owner review/merge using the S2A checks.

Current scope amendment — 2026-10-25: owner directs generic accounting rules for P27. The [generic packet contract](P27-S1-submission-contract.md) supersedes historical official-template, signature, deadline and runbook-resolution gates for generic export. P27-S2 remains sequenced after S1 merge; historical entries below remain evidence of earlier scope.

Published draft [PR #387](https://github.com/benbaron/sca-jakarta-h2/pull/387) on fresh `codex/P27-S1-form-contract`, based on merged P26 main. Contract head `92cd583931107c13ab9af1acd97a008817feb5c6` passed documentation link and whitespace validation. This publication-only successor records the draft; verify its fetched head/tree and inspect CI before owner merge. No P27 application code, human spreadsheet result or CI success is claimed yet.

P26 closeout and P27-S1 handoff — 2026-10-25: GitHub and native fetch confirm PR #386 merged at current main `db488cd259eef65ceb2cab1f8b2fdaa650690363`, containing accepted head `fb7f7813`; its clean/repeat/JavaFX run `40132078817` passed. P26 is DONE. Fresh `codex/P27-S1-form-contract` carries the local final-CI record as `7ccba770`. Source inspection covered canonical financial/semantic reports, workbook semantic adapters, property/inventory and period-close services. Reference workbook SHA matches the P23 inventory; populated source bytes remain unmodified and uncommitted. Owner then explicitly declined official-template use and selected generic accounting rules. S1 now defines required packet sections/sources, snapshot/cutoff/precision/completeness rules, safe output behavior, visible missing data and required synthetic unit scenarios. D02/D05 are resolved for generic output. No production, migration, Save or accounting behavior changed; no additional application unit tests are warranted for this documentation slice. Documentation consistency, links and whitespace checks precede draft publication; exact published-head CI and owner review/merge remain. No human spreadsheet validation is claimed.

## 1. Purpose and source of truth

This document is the execution ledger for `benbaron/sca-jakarta-h2`. Execute one selected phase and slice at a time under root `AGENTS.md`. Current `main`, merged PRs, migrations, tests, governing documents, and this controller are authoritative over archived plans.

A slice is `DONE` only when the behavior is merged into current `main`, the governing documentation is current, and required validation passed. Local code or an open pull request is not `DONE`.

## 2. Current phase index

| Phase | Name | Status |
|---|---|---|
| P00-P04 | Inventory, shell, canonical ledger/Journal, budgeting | DONE |
| P05 | Banking configuration and statement import | DONE through P05-C8 / PR #289 |
| P06 | Bank reconciliation and cleared-state comparison | DONE |
| P07 | Former Schedules phase | ELIMINATED/DONE |
| P08-P10 | Assets/depreciation, Inventory, period close/audit | DONE for original contracts |
| P11 | Report Library | DONE through P11-C2 / PR #284; period-context correction P17-C9 DONE |
| P12-P15 | Administration, diagnostics/exchange, hardening, versioned interchange | DONE for original contracts |
| P16 | Interface-to-authority completion and integrity corrections | DONE through P16-C11 / PR #281 |
| P17 | Cross-cutting UI, authority, cleanup, durable-record, documentation corrections | VERIFYING — P17-C13 merged in PR #367, final-head CI passed; explicit native acceptance evidence remains unrecorded; earlier slices DONE through P17-C12 / PR #305 |
| P18 | Depreciation-run workflow completion | DONE through P18-S1 / PR #306 |
| P19 | Deferred Company Administration extensions | DONE through P19-S3 / PR #309 |
| P20 | Authentication and runtime authorization | DONE through P20-S3 |
| P21 | Activity and Event Accounting | DONE through P21-S2 / PR #339; completion record PR #340 |
| P22 | Post-P21 correctness and authority corrections | DONE through P22-S6 / PR #348 |
| P23 | Accounting correctness and open-item integrity | DONE through P23-S5 / PR #355 |
| P24 | Named events and usable entry workflows | DONE through P24-S4C / PR #361 |
| P25 | Transfers, payment references, reporting classification | DONE through reviewed/merged MR #378 — S4D merged in MR #371; S4E DONE through MR #372; S4F DONE through MR #373; S4G DONE through MR #374; S4H DONE through MR #375; S4I DONE through approved/merged MR #376; S4 financial scope DONE; S5A DONE through MR #377; S5B DONE through MR #378; D07 adopted; S4C MR #370 merged/final-head CI passed, native evidence unrecorded; S1/S2/S3/S4A DONE; external operating-policy gate removed |
| P26 | Property, planning, recognition, conversion | DONE through owner-merged PR #386; final published head passed clean/repeat/JavaFX CI |
| P27 | Generic accounting packet, evidence, complete portability | IN_PROGRESS — S1/S2/S3 and S4A/B/C merged; S4D1 merged through #397; S4D2 category masters active; combined acceptance remains |
| P28 | End-to-end acceptance and release | BLOCKED — preceding required slices |

## 3. Established product decisions

- One production JavaFX application and one H2 accounting/operational authority.
- Existing JPA/Hibernate model and nondestructive Flyway migrations remain the schema foundation.
- Write services own validation and transactions; query/orchestration services do not create parallel persistence.
- No parallel ledger, budget, import, record, preference, shell, session, reconciliation, period-close, depreciation, report, company, Chart of Accounts, identity, credential, or authorization authority.
- Every enabled production command performs a genuine operation or navigation.
- Durable records preserve meaningful history through governed lifecycle/correction semantics.
- Company-specific money/date/table/divider and other UI/workflow defaults remain H2-backed through the established company preference/state authority.
- EIN is optional informational company metadata. It is not tax-filing configuration and does not imply tax-return, jurisdiction, period, reporting, or export workflow.
- Compatibility identifiers/APIs remain only where a current compatibility path requires them.
- Historical/archive documents remain historical evidence; current governing documents describe current production authority.

### P20 adopted security decisions

- `AppUser` is the account/login identity; `AppRole` never stores a password.
- Authentication is local to the H2 database; no external IdP/SSO/MFA requirement is adopted.
- Reserved default accounts/roles are `ADMIN`, `MANAGER`, `ACCOUNTANT`, and `VIEWER`.
- Reserved accounts start with no password. A passwordless account can explicitly log in with no password challenge.
- Only ADMIN may set, replace, or clear account passwords, including its own.
- Each company starts with one default assignment for ADMIN, MANAGER, ACCOUNTANT, and VIEWER.
- There is exactly one effective ADMIN account in the database; no second account may obtain effective ADMIN authority.
- Default inactivity timeout is disabled. A session ends on explicit logout, application exit, or database switch unless an ADMIN later configures a nonzero timeout.
- ADMIN has full security/application authority; MANAGER has bookkeeping plus non-security company administration; ACCOUNTANT has normal bookkeeping write authority; VIEWER is read-only with report/export access.
- Authenticated user identity becomes the authoritative audit actor once P20 enforcement is implemented.
- Forgotten singleton-ADMIN credentials require an explicit offline local recovery that clears only the ADMIN credential to the passwordless state; no default/backdoor password is permitted.
- The local workstation/H2 file permissions are the outer trust boundary; application passwords do not claim protection against unrestricted OS/database-file access.

Governing requirements: `doc/P20-S1-authentication-authorization-boundary.md`.

## 4. Completed recent phases

P17 is DONE through P17-C12 / PR #305.

P18-S1 is DONE: PR #306 final head `d802cfdbb739978f09fa516ad09fde32a8fe92ff` passed Maven PR Tests run `33229276083`, job `99039021126`, and merged to `main` at `3dec9516f1bb785dfefb2b277372be7fed656871` after owner acceptance.

## 5. P19 — Deferred Company Administration extensions

### P19-S1 — Company Chart of Accounts assignment administration

Status: DONE.

PR #307 exact final head `75e2ab1a47e2fad95be460426ea64b038109842c` passed Maven PR Tests run `33231705169`, job `99045589148`, and merged after owner acceptance.

Completed behavior:

- `chart_of_accounts.company_id` remains chart ownership authority;
- `company.active_chart_of_accounts_id` remains the current-chart pointer;
- Company Admin lists only company-owned charts and deliberately selects the current chart;
- DRAFT selection promotes to ACTIVE, while RETIRED/cross-company selection is rejected;
- prior ACTIVE charts and all existing account/history relationships remain intact;
- no migration or second chart/assignment authority was introduced.

### P19-S2 — Company reporting-default administration

Status: DONE.

PR #308 exact final head `e1f45ee0b0f96870425f9f96d10e960e3c86d3c0` passed Maven PR Tests run `33264570758`, job `99132280957`, and merged to `main` at `b4ef30643978eb926e97773a13a6102b0389244a` after owner acceptance.

Completed behavior:

- current Report Library has exactly two safe company-level opening defaults with real consumers: initial report selection and initial export format;
- the values reuse existing H2 `company_ui_state` via `CompanyUiPreferencesService` under `reportingDefaults.`;
- missing or stale saved values fall back to Trial Balance/Text;
- a new Report Library reads the defaults once; changing Company Admin defaults never replaces an already-open operator selection;
- Report Library interaction does not automatically rewrite company defaults;
- report dates, fund, row limit, account, fixed-asset, inventory, and status filters remain transient/current-request parameters;
- no migration, report query/execution, or export-adapter behavior was introduced.

Governing design: `doc/P19-S2-company-reporting-defaults.md`.

### P19-S3 — Company EIN informational metadata

Status: DONE.

PR #309 exact final head `6849fd70d6c47a886f4eef9967c4b69a38e380b6` passed Maven PR Tests run `33273006267`, job `99154805470`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. Owner acceptance was confirmed and PR #309 merged to `main` at `00d498705544e1a76d99b159f4b8fc23f80012a1`.

Completed behavior:

- nullable `company.ein VARCHAR(40)` is the sole live production EIN authority;
- V75 nondestructively backfills nonblank legacy `company_tax_profile.ein` while retaining legacy table/data;
- obsolete live `CompanyTaxProfile` JPA/query authority is retired;
- EIN is editable through the stable company profile lifecycle;
- no tax-filing, jurisdiction, filing-period, report, export, banking, or accounting workflow was introduced.

Governing design: `doc/P19-S3-company-ein-metadata.md`.

## 6. P20 — Authentication and runtime authorization

### P20-S1 — Authentication and authorization requirements boundary

Status: DONE.

PR #310 exact final head `17077c2c2ba68a7c152554bccde04f3bb2aaa6ce` passed Maven PR Tests run `33279457435`, job `99172041566`, and merged to `main` at `3d4f0d775e454e506ca4e20d7101eff613f47d0a` after owner acceptance.

Governing requirements: `doc/P20-S1-authentication-authorization-boundary.md`.

### P20-S2 — Authentication implementation

Status: DONE.

PR #311 exact final head `630d022584449298ad900ee00126f41eafe96917` passed Maven PR Tests run `33292407265`, job `99206247747`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. Owner confirmed the tests and merged PR #311 to `main` at `40a4a37aaeed7fa94d847009d55a177f94b1d407`.

Completed behavior includes:

- H2-owned optional `AppUser` credentials; roles never own passwords;
- passwordless reserved ADMIN/MANAGER/ACCOUNTANT/VIEWER accounts and per-company assignments;
- singleton effective ADMIN and required ADMIN assignment protection;
- explicit login/logout and authenticated in-memory session identity;
- effective reserved roles derived from current company-scoped H2 assignments;
- company-switch role recomputation and no-access rejection;
- default inactivity timeout disabled, with ADMIN-controlled nonzero configuration;
- ADMIN password set/replace/clear including self;
- explicit offline ADMIN credential recovery;
- factual security events and real User Admin authentication controls.

### P20-S3 — Runtime authorization enforcement

Status: DONE.

Foundation PR #312 exact final head `db3a30289aa17b967948a79a048f9ebdf9c5042e` passed Maven PR Tests run `33293417227`, job `99208891671`, and merged to `main` at `1b11df7cdc98775c618e8489ca7608bde36ea547`.

Fund-service PR #313 final head `9cb928554546be938841cd391e6e13995ad77918` passed Maven PR Tests run `33337392090`, job `99326671667`: clean headless verification, full tests, and production JavaFX route compliance all succeeded. The owner merged PR #313 to `main` at `19f85937b154cb8a6ad6517a4564425440ae0aa1`.

Budget Category PR #314 exact final head `17fe284c2c6986efdfd076d47e68caa3c44f3167` passed Maven PR Tests run `33337813403`, job `99327841889`: clean headless verification, full tests, and production JavaFX route compliance all succeeded. The owner merged PR #314 to `main` at `79eb9e52f4bf4a834587f9e66d34a60c1749f71d`.

Account PR #315 exact final head `17f0d4e5675007f5e136e0c948e413fc97f0a3a4` passed Maven PR Tests run `33347854910`, job `99355352917`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #315 to `main` at `e96b33fb6b7a8016ff4568737bab4cd1bc5ec6f2`.

Budget Plan PR #316 exact final head `bcae13e291738abb5003ad6899ced7ac9496db08` passed Maven PR Tests run `33350574686`, job `99363005574`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #316 to `main` at `bf7a373208c7f207cb4764140768cb6d793209c0`.

Bank Configuration PR #317 exact final head `62e882d807ea4ffeeb9c66ffefac075635f86703` passed Maven PR Tests run `33351860101`, job `99366659502`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #317 to `main` at `fee2728eca53c8d0da7a9d0bcbddc75a7daa4965`.

Company Administration PR #318 exact final head `89e7948fbfda3b776cdc5f829c88aea1683cf5e8` passed Maven PR Tests run `33354408246`, job `99373686535`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #318 to `main` at `4991916c114c8e1ecc96367201bc3f841d3c3dc9`.

User Administration PR #319 exact final behavior/documentation head `dcec480d5702662567e29fc37a14f90cafec0531` passed Maven PR Tests run `33431783480`, job `99618708073`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #319 to `main` at `b7397b72395033d0cbb57df418b11e3b24807bc1`.

Security Administration PR #320 exact final head `4514737d7c695a3a0a9c358575dff71aa4313dd8` passed Maven PR Tests run `33447487143`, job `99669737473`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #320 to `main` at `67fdcc819f2716263ea952ffff60e3ad87c7fea4`.

Journal PR #321 exact final head `b7310405390c342e02a378606f766d8a1173b3de` passed Maven PR Tests run `33468647019`, job `99733662262`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #321 to `main` at `77be356ed3351936b623b208898f61a0acec23ee`.

Fixed Asset PR #322 exact final head `b8144b9ea609a2150c63912b3ad7e83aab87ff46` passed Maven PR Tests run `33552680761`, job `100005736808`: clean headless verification, repeat tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #322 to `main` at `2f22b2cc3f2a40e77151d6c2892ad62772cdcc05`.

Inventory PR #323 exact final head `b8e861c6107aa7de0ecd4c1aa024b60effd8aa68` passed Maven PR Tests run `33565845969`, job `100048785715`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded. The owner accepted and merged PR #323 to `main` at `4d1a741b6c7bc70c52c4387cabea7d7fb21ee1b7`.

Previous reconciliation authorization tranche: PR #324 final head `1a420aae36fb01c019a5b72f487591bcfcaaf54a` merged to `main` at `5d591e4d767264490611870d80fe271303b79017`. Its behavior/documentation head `ca124f846178f5b1abcf34e7c9cafab1a079bbdb` passed Maven PR Tests run `33578682023`, job `100088178045`; the final PLAN successor was merged with the PR and the post-merge `main` workflow run `33582591691` also passed.

Previous period-close authorization tranche: PR #325 final head `bc62b4c192d7ecb2098ee86bd787e7a3db163b31` passed Maven PR Tests run `33590915729`, job `100124557154`, and merged to `main` at `128660a4793e2232920ff0ec32ee8d8c7736d18f` after owner acceptance. Post-merge `main` workflow run `33653699552` also passed.

Completed P20-S3 behavior to date:

- fixed `ApplicationPermission` policy and multi-role union are established;
- `AuthorizationGuard` reads the current authenticated session on every decision and writes durable `AUTHORIZATION_DENIED` facts;
- `ServiceAuthorization` provides a nullable adapter so legacy/test constructors remain source-compatible while guarded constructors fail closed;
- Fund service create/update/upsert/delete requires `BOOKKEEPING_WRITE`; Fund queries remain readable;
- direct H2 Fund tests prove VIEWER denial, immediate ACCOUNTANT enablement, immediate switch back to VIEWER denial, and wrong-company rejection without stale authorization state;
- Budget Category service-owned `upsert(...)` requires `BOOKKEEPING_WRITE`, while caller-owned import transaction seams remain governed by the outer import commit;
- direct H2 Budget Category tests prove the same VIEWER/ACCOUNTANT/company-switch behavior;
- Account stable-ID `save(...)` and service-owned code-addressed `upsert(...)` require `BOOKKEEPING_WRITE`, while caller-owned account import helpers remain governed by the outer import commit;
- direct H2 Account tests prove VIEWER denial/no write, immediate role/company switching, MANAGER/ADMIN/non-ADMIN union success, and preserved BANK/company/chart validation;
- Budget Plan service-owned draft/revision/save/activate/archive mutations require `BOOKKEEPING_WRITE`, while caller-owned budget import helpers remain governed by the outer import commit;
- direct H2 Budget Plan tests prove VIEWER denial/no write, immediate role/company switching, ACCOUNTANT/MANAGER/ADMIN/non-ADMIN union success, and preserved duplicate-scope and draft/version lifecycle protections;
- Bank Configuration service-owned Bank create/update and configured-bank-account create/update mutations require `COMPANY_ADMIN`, while list methods remain read-only and caller-owned import helpers remain governed by the outer import commit;
- direct H2 Bank Configuration tests prove VIEWER/ACCOUNTANT denial/no durable change, immediate role/company switching, MANAGER/ADMIN/non-ADMIN union success, preserved bank-ledger-account classification and lifecycle protections, and continued caller-owned import-helper use inside an explicitly authorized outer transaction;
- Company Administration stable-ID company create/update/deactivate and active Chart of Accounts assignment require `COMPANY_ADMIN`; `reportingDefaults.*` state writes also require `COMPANY_ADMIN`, while presentation-only company UI state retains `UI_PREFERENCE_WRITE`;
- direct H2 Company Administration tests prove VIEWER/ACCOUNTANT denial, MANAGER/ADMIN/non-ADMIN union success, immediate role/company switching, wrong-company rejection, preserved company/chart lifecycle protections, reporting-default bypass prevention, and continued VIEWER presentation preference persistence;
- User Administration stable-ID user/role/assignment mutations require `SECURITY_ADMIN` in the active-company context while read/usage queries remain non-mutating;
- direct H2 User Administration tests prove VIEWER/ACCOUNTANT/MANAGER denial/no durable mutation, ADMIN success, non-ADMIN union denial, immediate role/company switching, wrong-company and absent-session fail-closed behavior, durable authorization-denial facts, and preserved reserved/lifecycle protections;
- Security Administration password set/replace/clear and inactivity-timeout changes require `SECURITY_ADMIN`, while credential/configuration reads remain non-mutating;
- direct H2 Security Administration tests prove VIEWER/ACCOUNTANT/MANAGER denial/no credential or timeout mutation, ADMIN success, non-ADMIN union denial, immediate session/company switching, wrong-company and absent-session fail-closed behavior, durable authorization-denial facts, and preserved singleton-ADMIN/inactive-target protections;
- Journal service-owned entry/update/direct-edit/delete/reversal mutations require `BOOKKEEPING_WRITE`; Journal reads remain non-mutating and caller-owned transaction/import seams remain outer-governed;
- direct H2 Journal tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and non-ADMIN role-union success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned seam use;
- Fixed Asset service-owned create/update/status, depreciation, lifecycle commit, and lifecycle reversal mutations require `BOOKKEEPING_WRITE`; reads/previews and caller-owned import seams remain outside the service-owned write guard;
- direct H2 Fixed Asset tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned import seam use;
- Inventory service-owned create/update/status, confirmed movement commit, compatibility movement commit, and governed movement reversal mutations require `BOOKKEEPING_WRITE`; reads/previews and caller-owned import seams remain outside the service-owned write guard;
- direct H2 Inventory tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned import seam use;
- Reconciliation workspace session start/successor, manual statement entry, matching/unmatching, cleared-state, factual explanation, save/finalization, and direct reviewed-row cleared-state mutations require `BOOKKEEPING_WRITE`; configured-account/session/snapshot reads and caller-owned interchange seams remain outside the service-owned write guard;
- direct H2 Reconciliation tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, and continued caller-owned interchange seam use.
- Period Close service-owned close/reopen mutations require `BOOKKEEPING_WRITE`; range/history reads, `requireOpen(...)`, and caller-owned interchange restore remain outside the service-owned write guard;
- direct H2 Period Close tests prove VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and multi-role success, immediate session/company switching, absent-session and wrong-company fail-closed behavior, durable denial facts, read access, and continued caller-owned interchange use.

Previous import-commit authorization tranche:

- PR #326 behavior/documentation head `effb5f3dcc7423a8946fbf0cdd3e1fb1027505ce` passed Maven PR Tests run `33705042519`, job `100492254318`;
- final PLAN-only head `8696b59a9d49d7d88a1ae994e9ba5be81a055098` was owner-accepted and merged to `main` at `09c209097fbd0bba71299c88db5745cc83943002`;
- post-merge `main` workflow run `33708842774`, job `100503792968` passed clean headless verification, full tests, and production JavaFX route compliance;
- `SclxImportCommitService.commit(...)` and `CoaCsvImportService.commit(...)` require `BOOKKEEPING_WRITE` at their outer atomic commit boundaries while nested caller-owned import seams remain outer-governed.

Governing import-commit design: `doc/P20-S3-import-commit-authorization.md`, `doc/data-exchange/sclx.md`, and `doc/interface-operation-matrix.md`.

Previous bank import review/acceptance authorization tranche:

- PR #327 final head `750b3493d1e90329ce62f2099c933a7b5189bf4c` passed Maven PR Tests run `33715100237`, job `100522508886`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded;
- the owner accepted and merged PR #327 to `main` at `0e7d71a1322446a8dfe4f5d98245a94c54b93922`;
- `BankStatementReviewService.commit(...)`, `BankImportReviewService.createReviewBatch(...)`, and `ReviewedStatementAcceptanceService.accept(...)` require `BOOKKEEPING_WRITE` before ordinary commit validation or durable mutation;
- strict statement preview and reviewed-row acceptance preview remain non-mutating and outside the write guard;
- `BankImportReviewService.importForInterchange(...)` remains a caller-owned SCLX seam and is deliberately not independently guarded;
- existing source hash, configured-account identity, duplicate/idempotency, reviewed-row accounting, closed-period/finalized-reconciliation, and rollback protections remain authoritative after authorization succeeds.

Governing bank-import authorization design: `doc/P20-S3-bank-import-authorization.md`, `doc/banking/import-and-reconciliation.md`, and `doc/interface-operation-matrix.md`.

Completed bank CSV authorization tranche:

- PR #328 final head `449ab6b5947ad8d5e6148eced11e3dd60c61b857` passed Maven PR Tests run `33778610783` and merged to `main` at `d91262dbe22983a017e567aba6f7de5e723ecdb3` after owner verification;
- direct `NormalizedBankCsvReviewService.commit(...)` requires `BOOKKEEPING_WRITE` before ordinary preview/actor/commit validation while `preview(...)` remains non-mutating;
- `BankCsvMappingProfileService.create(...)`, `replace(...)`, and `setActive(...)` require `BOOKKEEPING_WRITE` before profile parsing or transaction work while `list(...)` remains read-only;
- direct H2 coverage exercises VIEWER denial/no durable mutation, ACCOUNTANT/MANAGER/ADMIN and non-ADMIN role-union success, immediate current-session switching, absent/wrong-company fail-closed behavior, durable `AUTHORIZATION_DENIED` facts, and continued preview/list read access;
- no schema, migration, JavaFX layout, or authenticated-audit-actor change was included in that tranche.

Governing bank-CSV authorization design: `doc/P20-S3-bank-csv-authorization.md`, `doc/banking/import-and-reconciliation.md`, and `doc/interface-operation-matrix.md`.

Completed production current-session authorization wiring tranche:

- PR #329 final head `b7748eb32a86bac302e0a1130da4549f64732339` passed Maven PR Tests run `33787354446`, job `100755384759`: clean headless verification, full Maven tests, and production JavaFX route compliance all succeeded;
- the owner accepted and merged PR #329 to `main` at `7f190a68fe37284440225d6b90edfb2afde669c3`;
- `UiServiceRegistry` now creates one `AuthorizationGuard` per production `ServiceBundle`, bound to that bundle's `Jpa` and `ApplicationSessionContext.sharedSessionState()::authenticatedUser`, so authorization consumes the live current session without another cache or session authority;
- bundle-owned and on-demand protected services select guarded constructors, including Account, Fund, Budget, Bank Configuration, Fixed Asset, Inventory, Company/User/Security Administration, Journal, Reconciliation, Period Close, CoA/SCLX commit, bank review/CSV/profile/normalized review, reviewed-statement acceptance, and company preference/state writes;
- mapped CSV preserves one authorization owner through its guarded `BankStatementReviewService` delegate; database preparation creates a fresh target-`Jpa` guard and database-switch activation clears the old authenticated session;
- source-compatible unguarded constructors and documented caller-owned transaction/import seams remain intact.

Current authenticated audit actor tranche:

- branch `codex/P20-S3-authenticated-audit-actor` starts from exact merged `main` `7f190a68fe37284440225d6b90edfb2afde669c3`;
- guarded production audit-producing mutations derive `AuthenticatedUserSession.username` from the same current-session `AuthorizationGuard` that authorizes the write, rather than trusting caller actor text;
- `ServiceAuthorization.actor(...)` is the shared compatibility adapter inside the service package, while interchange services use public `AuthorizationGuard.requireActor(...)`; unguarded tests and explicitly caller-owned seams retain their established fallback actor behavior;
- Journal, fixed asset/depreciation/lifecycle, inventory, period close/reopen, reconciliation successor, reviewed-statement acceptance, CoA CSV, SCLX, strict/normalized bank review, and User Admin current-operation audit writes are covered;
- SCLX source period-close and audit-history actor values remain historical source facts and are not rewritten; only new local import/canonical-transaction audit facts use the authenticated current actor;
- `DesktopActorIdentity` resolves authenticated session identity first, protected JavaFX actor displays are read-only, and literal/workstation actors no longer act as authority on already-guarded production routes;
- Company Ownership Diagnostics was outside the actor tranche because its mutations are classified `DATABASE_ADMIN`; the following database-administration tranche owns that guard and actor conversion. The former legacy `AccountingPeriodService` had no production route, remained non-authoritative, and is retired by P22-S3;
- direct H2 regression coverage proves spoofed Journal/User Admin actor inputs are replaced by authenticated username, while source-route coverage requires authenticated actor derivation across all current guarded audit-producing production boundaries and read-only actor displays;
- there is no schema or migration change;
- PR #330 behavior/documentation head `d3667270f34bc971a87d887ae96141db5af0d900` passed Maven PR Tests run `33807059790`, job `100819950561`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- final exact PR head `abdef30d53655ee19d753ca80af2104e0efbff4a` passed Maven PR Tests run `33807732460`, job `100822104225`, was owner-accepted, and merged to `main` at `56c792c3787ac0a0d9cef980e8a07bee07b26b1c`.

Governing actor design: `doc/P20-S3-authenticated-audit-actor.md`.

Completed database administration authorization tranche:

- PR #331 exact head `ec544039444d014c3e50deceea9a653372b119a5` passed Maven PR Tests run `33815258946`, job `100845874647`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner verified and merged PR #331 to `main` at `841f17d91bf85f1337f3f71b4fcd719c26f15404`;
- post-login whole-database backup, restore-to-validated-copy, and validated-copy activation route through service-layer `DatabaseAdministrationService` requiring `DATABASE_ADMIN`, while persistence `DatabaseTransferService` remains policy-free;
- the transfer facade resolves the current `UiServiceRegistry` bundle guard on each operation so a long-lived workspace cannot retain the old database's authorization guard after switching;
- `CompanyOwnershipService.assignOwner(...)` and production `SampleCompanyService.createOrRefresh()` require `DATABASE_ADMIN`; ownership repair derives the factual audit actor from the authenticated ADMIN session;
- database selection/create/retry at the outer login gate remains deliberately pre-authentication;
- direct integration and source-route coverage are added for non-ADMIN denial/no mutation, ADMIN success, durable denial facts, authenticated repair actor, current-session changes, and guarded production composition;
- there is no schema or migration change.

Governing database-admin design: `doc/P20-S3-database-administration-authorization.md`.

Completed JavaFX permission-gating tranche:

- PR #332 final exact head `43405195a9598baf371a50d19f9ac7e5bd5d6185` passed Maven PR Tests run `33832728168`, job `100898934346`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #332 to `main` at `d2dba2c270c8c23594f1073f757c40d15d3d9186`; post-merge `main` workflow run `33833200591`, job `100900327559` also passed;
- global mutation commands declare their required fixed permission through `AppPanel.requiredPermission(...)`;
- `ProductionWorkspaceWindow` combines active-panel capability with current-session permission and returns an explanatory denial before dispatch if invoked directly;
- panel-local durable actions use the same fixed permission policy without replacing independent busy/selection/lifecycle disable reasons;
- the tranche corrected the directly blocking Chart of Accounts JSON import authorization gap by requiring `BOOKKEEPING_WRITE` at that commit boundary and using guarded production composition rather than relying on UI disabling;
- read/navigation/preview controls remain available; export and presentation-preference actions retain `EXPORT` and `UI_PREFERENCE_WRITE` respectively;
- focused JavaFX/session and source-route regression coverage is included;
- the final source-route assertion verifies the stable permission-check and denial-explanation behavior rather than depending on a local variable name;
- no schema or migration change.

Completed final P20-S3 reconciliation tranche:

- branch `codex/P20-S3-final-reconciliation` started from exact merged `main` `d2dba2c270c8c23594f1073f757c40d15d3d9186`;
- the tranche reconciled stale User Admin documentation to the implemented JavaFX `SECURITY_ADMIN` gating and recorded the completed #332/CI/merge evidence in this execution ledger;
- no Java production/test code, schema, migration, or interface-operation-matrix change was included;
- PR #333 behavior/documentation head `966c4dff0d214a3c8e29dc7895d529acfea32ac2` passed Maven PR Tests run `33833984753`, job `100902609878`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- PR #333 final exact head `5efced5e170db390292cdbf77a7a3b016538d718` passed Maven PR Tests run `33834473777`, job `100904026212`: clean headless verification, repeated full Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #333 to `main` at `7c1f5ae69b7631de801fdb8169230967767316ac`;
- post-merge `main` Maven PR Tests run `33839082567`, job `100917493733` passed clean headless verification, repeated full Maven tests, and production JavaFX route compliance.

Completed P20-S3 verification and owner acceptance:

- PR #334 exact final head `47903ad0db0556c19ba98f11a52022d56b73fea9` passed Maven PR Tests run `33921783760`, job `101181576090`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- the owner merged PR #334 to `main` at `7f611dd94b5d69d75e5dbed96730bea6a5941410`;
- post-merge `main` Maven PR Tests run `33944333008`, job `101247580392` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- repository/governing-document inspection found no additional missing production service or JavaFX authorization boundary requiring another P20-S3 implementation tranche;
- the previously stale source-route assertion remains correctly based on stable `UiPermissionGate` behavior rather than a local variable name;
- on 2026-09-06 the owner explicitly accepted the completed desktop role/permission behavior, including VIEWER, ACCOUNTANT, MANAGER, ADMIN, company/session switching, and the Chart of Accounts JSON preview/import boundary;
- all P20-S3 completion gates are satisfied, so P20-S3 and P20 are complete.

Completion-record publication:

- branch `codex/P20-S3-completion` starts from exact merged `main` `7f611dd94b5d69d75e5dbed96730bea6a5941410`;
- completion behavior/documentation head `e96d08eeb5b52982df89f304997d0ee3355fea50` records owner desktop acceptance and closes P20-S3/P20;
- draft PR #335 changed only `doc/PLAN.md` and merged to `main` at `6010a5563e70e58fc69a91197cbf1ec819bd346c`; post-merge Maven PR Tests run `34074737387`, job `101598529975` passed clean headless verification, Maven tests, and production JavaFX route compliance.

Required reading:

- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-authenticated-audit-actor.md`;
- `doc/P20-S3-database-administration-authorization.md`;
- `doc/P20-S3-javafx-permission-gating.md`;
- `doc/P20-S3-fixed-asset-authorization.md`;
- `doc/P20-S3-inventory-authorization.md`;
- `doc/P20-S3-period-close-authorization.md`;
- `doc/P20-S3-import-commit-authorization.md`;
- `doc/P20-S3-bank-import-authorization.md`;
- `doc/P20-S3-bank-csv-authorization.md`;
- `doc/data-exchange/sclx.md`;
- `doc/accounting/period-close-design.md`;
- `doc/banking/banking-and-reconciliation.md`;
- `doc/administration/user-role-maintenance.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`.

Required inspection:

- `AuthenticatedUserSession`, `ReservedSecurityRole`, `AuthenticationService`, `SecurityAdminService`, `SecurityRepository`;
- `ApplicationSessionContext`, `UiSessionState`, `ProductionWorkspaceWindow`, `PanelHost`, `AppPanel`, `UiServiceRegistry`;
- every production mutation service/factory listed by the interface operation matrix;
- current free-form actor fields and audit-producing service paths;
- current role/session/security tests and source-route guard tests.

Implement the fixed reserved-role permission model consistently at shell/panel and authoritative service mutation boundaries. UI disabling is explanatory only; direct lower-privilege service calls must fail closed and write factual authorization-denial security events. Replace free-form audit actor authority with authenticated identity without creating a parallel audit identity.

Governing enforcement design: `doc/P20-S3-runtime-authorization.md`.

Completion gate:

- fixed permission matrix is implemented from current effective reserved roles with multi-role union;
- all production protected mutation routes use the central authorization guard;
- VIEWER cannot mutate durable business/accounting state even through direct service calls;
- MANAGER, ACCOUNTANT, and ADMIN boundaries match the adopted P20 contract;
- company/role switching immediately changes permissions without a stale cache;
- authenticated identity is the authoritative actor for protected audit writes;
- denial events are durable H2 `security_event` facts;
- JavaFX commands/actions reflect the same permissions and explain unavailable operations;
- Maven PR Tests and production JavaFX route compliance are green on the exact final head;
- owner desktop acceptance is complete.

## 7. P21 — Activity and Event Accounting

Purpose: make the existing `Activity` accounting dimension operator-manageable and then provide a read-only event/project/occasion accounting workspace over canonical Activity-linked Journal transactions. P21 extends current H2/JPA authority and does not create an event ledger, posting workflow, schedule subsystem, approval workflow, or parallel persistence.

Governing design: `doc/P21-activity-event-accounting.md`.

### P21-S1 — Activity administration

Status: DONE.

Completion evidence:

- PR #337 exact implementation head `adea4f127141a26a6ce11a601c93591e2c145e3c` passed Maven PR Tests run `34551426152`, job `103114964058`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- owner desktop verification was explicitly accepted;
- PR #337 merged to `main` at `f3509ff55e4da404f47e3f837ea525ad8dbeeb94`;
- post-merge `main` Maven PR Tests run `34667078105`, job `103481051301` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- the merged Activity authority remains the stable-ID H2/JPA `Activity` model with governed deactivate/reactivate and safe unused delete semantics; Journal and SCLX consume the same authority.

Required reading:

- root `AGENTS.md`;
- `doc/PLAN.md`;
- `doc/P21-activity-event-accounting.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`;
- `doc/accounting/transaction-lifecycle.md`;
- `doc/data-exchange/sclx.md`;
- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-javafx-permission-gating.md`.

Required inspection:

- `Activity`, `TxnSplit`, `Txn`, `BudgetCategory`, and all current activity-related Flyway constraints;
- `TransactionEntryService`, `TransactionReferenceDataService`, `CompanyOwnershipService`, and current stable-ID durable admin service patterns;
- SCLX activity snapshot, preview, portable-identity, and commit paths;
- `WorkspaceServices`, `UiServiceRegistry`, `PanelFactory`, `AppPanelId`, `NavigationPane`, `ProductionWorkspaceWindow`, and `UiPermissionGate`;
- current table-state, production route-compliance, authorization, and durable-record lifecycle tests;
- donor `benbaron/NonprofitAccounting` Activity/Event Accounting code only after current-repository inspection.

Implemented company-scoped stable-ID Activity create/update and governed lifecycle maintenance using current H2 authority. Activity code/name remain mutable business data; code remains company-unique; Journal and SCLX continue to consume the same Activity authority. Service-owned mutations require `BOOKKEEPING_WRITE`; VIEWER remains read-only; no parallel Activity cache or event entity was introduced. Physical-delete eligibility is determined from authoritative Journal and interchange usage, while used history is preserved through deactivate/reactivate.

Completion gate satisfied:

- stable-ID Activity create/update/lifecycle behavior is atomic and company-scoped;
- invalid, duplicate, cross-company, and unauthorized writes fail without partial mutation;
- used Activity history is preserved under the adopted lifecycle rule;
- P20 reserved-role/session switching behavior applies immediately at direct service and JavaFX boundaries;
- production UI follows current New/Save/dirty-state/table/layout/lifecycle rules and truthfully explains unavailable deletion;
- active Journal Activity choices refresh through existing H2 reference-data authority;
- existing SCLX Activity import/export compatibility remains intact;
- focused JUnit/H2/JavaFX tests, exact-head Maven PR Tests, and production JavaFX route compliance pass;
- owner desktop acceptance is recorded.

### P21-S2 — Event Accounting workspace

Status: DONE.

Completed behavior is the read-only **Accounting -> Event Accounting** workspace over canonical company-owned `Activity`, `Txn`, and `TxnSplit` authority. Income/expense/net use signed natural-balance Activity-tagged splits, including negative corrections/reversals. Related bank movement remains a separate contextual projection and qualifies only through configured `CompanyBankAccount` evidence plus `ASSET + AccountFunction.BANK + DEBIT normal balance`; it is not presented as an Activity allocation merely because the bank split shares the transaction.

The workspace supports active/inactive Activity selection, fiscal-year-to-selected-period default dates with explicit-date detachment/reset, all-funds or stable-ID fund filtering, canonical split detail, related bank inflow/outflow and cleared facts, factual closeout review, and drill-through to the existing Journal by transaction ID. It is available to VIEWER and all higher reserved roles and introduces no durable mutation, `Event` entity, schema migration, alternate ledger/bank model, or SCLX write authority.

Completion evidence:

- PR #339 exact implementation head `a2397749dda98fe098920c7fb97a2417e9a3fdfd` passed Maven PR Tests run `34675164328`, job `103503607827`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- PR #339 merged to `main` at `942cf2c9af69bbb3973184bc4da1d94d4e1d1a70`;
- post-merge `main` Maven PR Tests run `34721261596`, job `103627445343` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- owner desktop verification was explicitly accepted on 2026-09-12;
- P21-S2 and P21 are complete.

Completion-record publication:

- PR #340 exact completion head `3046eb014b355df67718c2ac7266737f56b40df5` passed Maven PR Tests run `34784883704`, job `103798424392`: clean headless verification, Maven tests, and production JavaFX route compliance all succeeded;
- PR #340 merged to `main` at `b7e4f45daa4aa5befda89a164e3bd6ced876c07e`;
- post-merge `main` Maven PR Tests run `34793559393`, job `103822237567` passed clean headless verification, Maven tests, and production JavaFX route compliance;
- PR #340's P21 completion state is retained. This corrective publication restores detailed P20 execution-ledger history that PR #340 compacted and makes no production-behavior change.

## 8. P22 — Post-P21 correctness and authority corrections

Purpose: correct semantic and authority defects discovered by the post-P21 repository audit without reopening completed feature phases or creating parallel persistence/workflows. Execute one corrective slice at a time from current `main`.

### P22-S1 — Dashboard authority and company/fiscal correctness

Status: DONE.

PR #342 final head `a20aebabaadaabc406cb352202e05baa258ae1ef` merged to `main` at `83da79b062d029cbb22b8e2ed448bd7f7f4a1b17` after owner acceptance.

Scope:

- scope every Dashboard ledger, cash, fund, budget, recent-transaction, running-balance, reconciliation, and monthly-result projection to the active persisted `Company`;
- interpret the top-chrome active-period value as the selected period start and project through the calculated period end using company fiscal-year configuration;
- derive Period Information from company fiscal authority plus `period_close_range`, not legacy `AccountingPeriod`;
- derive reconciliation status from `bank_reconciliation_session`, not legacy `reconciliation_run`;
- derive reconciled cash/unreconciled difference from canonical reconciliation-owned `TxnSplit.bankCleared` facts;
- stop presenting `open_item_snapshot` compatibility data as current Dashboard authority; until P22-S5 establishes canonical supplemental settlement/open-balance reporting, show Open Items as unavailable rather than fictional/current;
- add regression coverage proving another company and contradictory legacy compatibility rows cannot contaminate the active-company Dashboard;
- no schema migration, mutation-service change, alternate ledger, or reconciliation write path in this slice.

Required reading:

- root `AGENTS.md`;
- `doc/PLAN.md`;
- `doc/architecture/dashboard-workspace.md`;
- `doc/persistence-authority-inventory.md`;
- `doc/testing/production-workspace-test-plan.md`;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`;
- `doc/accounting/period-and-correction-policy.md`;
- `doc/banking/banking-and-reconciliation.md`.

Required inspection:

- `DashboardHomePanel`, `DashboardQueryService`, `JpaDashboardQueryService`, `DashboardSnapshot`, `InspectorPane`;
- `Company`, `Txn`, `TxnSplit`, `CompanyBankAccount`, `BankReconciliationSession`, `PeriodCloseRange`, `BudgetPlan`, `BudgetLine`;
- `FiscalPeriodRange`, active-period workspace composition, Dashboard tests, reconciliation tests, and current migrations defining company ownership/cleared/close-range authority.

User-visible changes / manual owner testing:

1. With two companies containing different transactions, switch companies and confirm Dashboard cash, YTD, bank balances, recent transactions, budget rows, and reconciliation rows contain only the active company.
2. For a company whose fiscal year does not start January 1, select a period and confirm YTD/monthly values begin at the configured fiscal-year start and extend through the selected period end.
3. Reconcile/clear some but not all bank-cash activity and confirm Dashboard/Inspector Book Cash, Reconciled Cash, and Unreconciled Difference agree with Journal/reconciliation facts.
4. Confirm Bank Reconciliation Status names the configured bank account and current session status/difference rather than showing legacy import-run fields.
5. Confirm Open Items explicitly displays `Not available` until the canonical supplemental settlement slice is implemented; legacy snapshot rows must not appear.

Validation state:

- exact behavior head `406a41a8b5a3bcbd1ff1ab69c55f63a18981bf54` passed Maven PR Tests run `34866284854`, job `104050825643`;
- exact final head `a20aebabaadaabc406cb352202e05baa258ae1ef` passed Maven PR Tests run `34923265219`, job `104235794126`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S2 — Bank-statement export permission/busy binding correction

Status: DONE.

PR #343 exact head `6c58b05b2267cec7ba492cd4139ae6dbc7da2dc8` merged to `main` at `5bb8e28174e5133a2b115eda490da8dc30eb21be` after owner acceptance.

Scope delivered:

- removed `UiPermissionGate.gate(...)` from the three bank-statement export buttons because those controls also require a busy-state binding;
- bound each export button disabled state once as export busy OR denied `EXPORT` permission;
- retained permission-denied explanatory tooltips while keeping `tooltipProperty()` unbound so the production full-text tooltip installer can still operate;
- added stable JavaFX IDs and focused permission/busy/full-text-tooltip regression coverage;
- did not change export serialization, destination/overwrite behavior, services, persistence, interchange formats, or authorization policy.

Validation state:

- exact PR head `6c58b05b2267cec7ba492cd4139ae6dbc7da2dc8` passed Maven PR Tests run `35264848366`, job `105349328123`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35265278336`, job `105350785585`, completed successfully at merge commit `5bb8e28174e5133a2b115eda490da8dc30eb21be`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S3 — Retire obsolete alternate writable services

Status: DONE.

PR #344 exact behavior head `e7991d5771639cb19e7cad567bb3c2a2f7e62840` merged to `main` at `d68e8f52ac06022254a9fa221779bcb6ba6e2157` after owner acceptance.

Scope delivered:

- retired `PostingService`, whose direct `Txn`/`TxnSplit` write path had no current production or compatibility caller and bypassed the current company/authorization/period/correction command boundary;
- retired legacy `AccountingPeriodService`, whose writable `AccountingPeriod` close/reopen path had no current production or compatibility caller and was superseded by `PeriodCloseRangeService`;
- retired `CoaFundIo`, whose direct Fund/Chart/Account CSV/JSON writes had no current caller and were superseded by guarded administration/interchange services;
- removed tests that existed only to exercise those retired writer implementations;
- retained historical entities/tables, applied migrations, and live compatibility/history services where current consumers still exist;
- added source-level regression coverage preventing the three retired writer types from returning while preserving canonical replacements;
- reconciled ledger, persistence-authority, application-composition, and P20 actor documentation to the current authority map.

Validation state:

- exact PR head `e7991d5771639cb19e7cad567bb3c2a2f7e62840` passed Maven PR Tests run `35269724312`, job `105365693725`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35476426775`, job `105986345643`, passed at merge commit `d68e8f52ac06022254a9fa221779bcb6ba6e2157`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- local Maven remained unavailable in the execution environment, so no local Maven result was claimed;
- owner acceptance and merge are recorded above.

### P22-S4 — Journal company UI-state single authority

Status: DONE.

PR #346 final head `a2fbf19928be15b785da0e90730212d6dab76c38` merged to `main` at `471e3b0c269d079d730a170446c6a6db01e523d8` after owner acceptance.

Completed scope:

- removed the Journal delegate's duplicate Java `Preferences` persistence for table state and divider positions;
- retained `JournalWorkspaceCompliancePanel` / `CompanyUiPreferencesService` as the single company-owned H2 UI-state authority;
- preserved Journal behavior and visible layout while strengthening source guards.

Validation state:

- exact final PR head `a2fbf19928be15b785da0e90730212d6dab76c38` passed Maven PR Tests run `35484432468`, job `106008076783`: clean headless verification, repeated Maven tests, and production JavaFX route compliance all succeeded;
- post-merge `main` run `35548359913`, job `106178350043`, passed at merge commit `471e3b0c269d079d730a170446c6a6db01e523d8` with the same three gates successful;
- local Maven was unavailable in the execution environment, so no local Maven result is claimed;
- owner acceptance and merge are complete.

### P22-S5 — Supplemental/open-item reporting and eliminated-Schedules cleanup

Status: DONE.

PR #347 final head `86b88e09c733d51fbd5841af61777d6d9c3de1a2` merged to `main` at `39da667a7e2499dfb6dcd8122341cef4932f1dd3` after owner acceptance.

Base: merged `main` `471e3b0c269d079d730a170446c6a6db01e523d8`.

Scope / adopted design:

- retain `Txn` / `TxnSplit` as the sole canonical ledger;
- extend `txn_supplemental_line` nondestructively with optional stable `item_id`, exact `txn_split_id`, and `INCREASE` / `DECREASE` lifecycle effect;
- keep supplemental amount non-negative and derive direction from the lifecycle effect plus canonical split sign;
- preserve legacy supplemental rows with no lifecycle triple and report them as unmatched rather than guessing identity from `entryRef`;
- derive company-scoped as-of open balances in one `SupplementalOpenItemQueryService`, excluding `REVERSED` and future transactions;
- surface over-applied, unmatched, and inconsistent rows explicitly;
- make Journal the lifecycle input surface with new-item creation and apply-existing-item behavior; do not add a separate Open Items editor;
- add Accounts Receivable, Accounts Payable, Prepaid Expenses, Deferred Revenue, Other Assets, and Other Liabilities Report Library entries backed by the shared projection;
- restore Dashboard Open Items from that same projection and never read `open_item_snapshot` as current authority;
- retire unconsumed `AppPanelId.SCHEDULES` and `ScheduleEligibilityService` executable compatibility seams while retaining historical schema/entities nondestructively;
- repair reverse-and-replacement so replacement canonical splits retain the original supplemental lifecycle allocations while reversal rows do not duplicate supplemental effects;
- current SCLX is explicitly out of scope by owner direction: no SCLX format/import/export files are changed. Current SCLX does not round-trip P22-S5 lifecycle linkage, and production UI/docs must state that limitation rather than claiming portability.

Required reading / governing updates:

- root `AGENTS.md` and `doc/PLAN.md`;
- `doc/accounting/transaction-editor-and-journal.md`;
- `doc/architecture/application-composition.md`;
- `doc/architecture/dashboard-workspace.md`;
- `doc/interface-operation-matrix.md`;
- `doc/persistence-authority-inventory.md`;
- `doc/reporting/report-library.md`;
- `doc/ui_design_rules.md` and `doc/ui/editor-guidelines.md`;
- `doc/P22-S5-supplemental-open-items-user-testing.md`.

Validation state:

- baseline `main` merge `471e3b0c269d079d730a170446c6a6db01e523d8` passed post-merge Maven PR Tests run `35548359913`, job `106178350043`;
- exact behavior head `8ed1a340b8d748406f61f0da17a609c39278c15a` passed Maven PR Tests run `36082153566`, job `107906242503`: clean headless verification, full tests, and production JavaFX route compliance all succeeded;
- exact final PR head `86b88e09c733d51fbd5841af61777d6d9c3de1a2` passed Maven PR Tests run `36090114072`, job `107930549770`;
- post-merge `main` run `36091432198`, job `107934507846`, passed at merge commit `39da667a7e2499dfb6dcd8122341cef4932f1dd3`: clean headless verification, full tests, and production JavaFX route compliance all succeeded;
- local Maven is unavailable in the current execution environment, so no local Maven result is claimed;
- owner acceptance and merge are complete.

### P22-S6 — Stale production copy and compatibility wording cleanup

Status: DONE.

PR #348 head `d61fd8b0cacf7b89cb083fa9a24d77bbb715e3c2` merged to `main` at `a1e739254b4d63b2bc3df6b0d71aa6ef4658d2a4` after owner verification and acceptance.

Base: merged `main` `39da667a7e2499dfb6dcd8122341cef4932f1dd3`.

Scope:

- correct the production Settings explanation for legacy `defaultPrivilege` so it reflects completed P20 authentication and company-scoped authorization;
- keep `defaultPrivilege` disabled because the stored compatibility value is not authentication or effective authorization;
- reconcile stale historical owner-testing wording that otherwise instructs testers to expect the false pre-P20 claim;
- scan production JavaFX/help copy for other obsolete "not implemented", future, or compatibility statements and change only claims that are now factually false;
- add regression coverage for the corrected Settings message;
- no persistence, migration, accounting, authentication, authorization-policy, routing, or preference-consumer behavior change.

Validation state:

- owner verified and accepted P22-S6 before merge;
- PR #348 is merged into current `main` at `a1e739254b4d63b2bc3df6b0d71aa6ef4658d2a4`;
- no post-merge GitHub Actions run was returned for that merge commit when closure was recorded, so no post-merge CI result is claimed;
- P22-S6 and P22 are complete.

## 9. Advancement rule

P21, P22, P23 and P24-S1 are complete. Owner accepted P24-S1 and GitHub verified PR #356 merged on 2026-09-30. P24-S2 is accepted and merged in PR #357. P24-S3 is accepted and merged in PR #358; P24-S4A Budget Category maintenance is merged; P24-S4B and P24-S4C are DONE; P24 is complete. P25-S1 is accepted and merged in PR #362. P25-S2 is accepted and merged in PR #363; P25-S3 check exceptions is DONE: MR #364 merged, final-head CI passed, and owner desktop acceptance passed on 2026-10-17. D03's former operating-policy-input gate is superseded by the owner's S4D scope correction. Conditional P27-S5 remains unadopted.

## 10. Archived proposal and adoption

- [2026 deficiency correction action plan](archive/SCA_2026_Deficiency_Correction_Action_Plan.md) remains the immutable proposal archived by PR #350, merged at `849dc4e8f652d47aff05c124790f78d6dc7f93dc`. The archive PR's exact-head Maven PR Tests run `36289042355` passed.
- The owner subsequently adopted P23–P28 and selected P23-S1 on 2026-09-26. The [live governing program](P23-P28-runbook-correction-program.md) supersedes the proposal for execution scope; explicit pending policy decisions remain pending.

## 11. P23–P28 — Adopted correction execution ledger

Required reading for P23-S1:

- root `AGENTS.md` and this plan;
- [adopted program](P23-P28-runbook-correction-program.md);
- [baseline, evidence and acceptance contract](P23-S1-baseline-and-acceptance.md);
- [owner review and evidence collection](P23-S1-user-testing.md);
- `doc/P21-activity-event-accounting.md`;
- `doc/P22-S5-supplemental-open-items-user-testing.md`;
- `doc/interface-operation-matrix.md`, `doc/ui_design_rules.md`, `doc/ui/editor-guidelines.md`;
- `doc/accounting/transaction-lifecycle.md` and `doc/accounting/period-and-correction-policy.md`.

Required inspection for S1 is the finding ledger in the baseline contract: refresh source evidence and test inventory for G1–G14, inspect V77's existing lifecycle protections, establish workbook/runbook identity, and record installed-build evidence gaps. Future slice owners must perform the additional scoped implementation/migration/test inspection named there before editing code.

### Slice statuses and prerequisites

The adopted program owns each slice's deliverables and acceptance criteria. The table below owns execution status. Sequential prerequisites prevent multiple selected slices; accounting-policy and external-evidence dependencies are recorded as D01–D10 in the baseline contract. Input collection may occur in S1 without starting later implementation.

| Slice | Deliverable | Status | Prerequisite / gate |
|---|---|---|---|
| P23-S1 | Adopt scope and establish reproducible acceptance cases | DONE | Documentation validation, owner review, publication and merge |
| P23-S2 | Correct Budget vs Actual reversal treatment (G1) | DONE | P23-S1 merged; owning contract and D01–D10 gates as applicable |
| P23-S3 | Preserve historical open-item balances (G2) | DONE | P23-S2 merged; owning contract and D01–D10 gates as applicable |
| P23-S4 | Enforce and explain schedule-to-ledger completeness (G3) | DONE | P23-S3 merged; owning contract and D01–D10 gates as applicable |
| P23-S5 | Preserve open-item lifecycle in SCLX (G4) | DONE | P23-S4 merged; owning contract and D01–D10 gates as applicable |
| P24-S1 | Make event creation and discovery explicit (G14) | DONE | P23-S5 merged; owning contract and D01–D10 gates as applicable |
| P24-S2 | Make Fund/Event tagging obvious and reliable (G14) | DONE | P24-S1 merged; owning contract and D01–D10 gates as applicable |
| P24-S3 | Attribute generated inventory costs (G6) | DONE | P24-S2 merged; owning contract and D01–D10 gates as applicable |
| P24-S4 | Complete lookup maintenance (G8) | DONE | P24-S3 merged; owning contract and D01–D10 gates as applicable |
| P24-S4A | Budget Category maintenance | DONE | P24-S3 merged; A08 category workflow |
| P24-S4B | Payee/Counterparty maintenance | DONE | P24-S4A merged; A08 party workflow |
| P24-S4C | Merchant maintenance | DONE | P24-S4B merged; A08 merchant workflow |
| P25-S1 | Implement one internal fund-transfer operation (G7) | DONE | P24-S4 merged; internal-only scope; form-specific legal-entity mappings remain in their owning reporting contract |
| P25-S2 | Add structured payment/check references (G9) | DONE | P25-S1 merged; owning contract and D01–D10 gates as applicable |
| P25-S3 | Complete check exception and reconciliation workflows (G9) | DONE | MR #364 merged; final-head CI passed; owner desktop acceptance “Passed — no issues” on 2026-10-17. D04 still gates stale-triggered accounting only |
| P25-S4 | User-entered NMR accounting and liability tie-out (G10) | DONE | Owner explicitly approved and merged MR #376 after A10 financial acceptance/retry repair; S4D–S4I merged/final-head CI passed. Optional S4B/C native evidence and P27 form mappings are separate |
| P25-S4A | Prepare D03 NMR accounting-contract proposal (documentation only) | DONE | MR #368 merged/final-head CI passed; owner accepted custodial framework. S4D supersedes mandatory external-policy inputs |
| P25-S4B | Company-owned dated NMR policy configuration | VERIFYING | MR #369 merged at 7943060e; final-head clean/repeat/Xvfb CI passed; explicit native acceptance evidence unrecorded; optional reference data only |
| P25-S4C | Optional policy-reference SCLX portability | VERIFYING | MR #370 merged at 6d3a7214; final head 9d49c102 clean/repeat/Xvfb CI passed; native evidence unrecorded; no generated accounting |
| P25-S4D | Correct NMR software scope (documentation only) | DONE | Owner accepted; MR #371 merged at 33c1e99e; final-head clean/repeat/Xvfb CI passed; S3 desktop acceptance recorded |
| P25-S4E | Preserve entered NMR line attribution through SCLX | DONE | MR #372 merged at 704a4a9e; final-head clean/repeat/Xvfb CI and full local verification passed |
| P25-S4F | Explicit NMR item/recipient/event detail and liability coverage | DONE | MR #373 merged at b2ff4aa6; corrected full local and final-head clean/repeat/Xvfb CI passed |
| P25-S4G | Explicit settlement attribution/evidence and period movements | DONE | MR #374 merged at fb37feba; full local and exact-head clean/repeat/JavaFX CI passed |
| P25-S4H | Merchant receivable breakdown and withholding observations | DONE | MR #375 merged at 030b7d3b; final full local and exact-head clean/repeat/JavaFX CI passed |
| P25-S4I | NMR financial acceptance and replay-safe Journal create | DONE | MR #376 explicitly approved/merged at 4c965490; final head de44946b passed full local and exact-head clean/repeat/JavaFX CI |
| P25-S5 | Add independent functional-expense classification (G10) | DONE | Owner reviewed/merged S5B MR #378; D07 adopted; full local and exact-head CI passed |
| P25-S5A | Adopt functional-expense contract (documentation only) | DONE | MR #377 merged at 7bc89aef; final-head clean/repeat/JavaFX CI passed; D07 adopted |
| P25-S5B | Independent functional expense entry, reporting and portability | DONE | MR #378 reviewed/merged at 70462b49; full local and exact-head CI passed;  [Implementation contract](P25-S5B-functional-expenses.md); nullable canonical fact, shared Save, compatible native retries and lossless SCLX |
| P26-S1 | Link fixed-asset acquisition and opening records (G11) | DONE | Owner accepted/merged MR #379 at `2de87dc3`; [S1 contract](P26-S1-asset-acquisitions.md); full local and exact-head CI passed |
| P26-S2 | Complete custody, supplies, and property-transfer records (G11) | DONE | Owner verified/merged PR #380 at `21f379dd`; final head `c83db0a5` and exact-head clean/repeat/JavaFX CI passed; internal branch form does not claim P27 official-template mapping |
| P26-S3 | Complete event/fund budget planning and reporting filters (G12) | DONE | Owner verified/merged PR #381 at `c7170328`; final head `ee36b641` and exact-head clean/repeat/JavaFX CI passed; D05 form mapping remains P27 |
| P26-S4 | Add governed prepaid/deferred recognition assistance (G12) | DONE | Owner accepted and merged PR #382 at `bf5bc979`; exact published head `70ca3b1b`; [S4 contract](P26-S4-prepaid-deferred-recognition.md); full local verification passed; no new schedule authority or policy/default engine |
| P26-S5A | Adopt inventory valuation contract (documentation only) | DONE | Owner reviewed/merged PR #383 at `e750f73c`; final head `4f965262` passed exact-head clean/repeat/JavaFX CI; [S5A contract](P26-S5A-inventory-valuation-contract.md) resolves D06 |
| P26-S5 | Resolve inventory valuation limitations (G12) | DONE | Owner merged PR #384 at main `7739698b`; implementation head `0c102dfd`; local headless and actual JavaFX gates passed; exact-head PR workflow was still in progress when the merge was reported and must not be described as passed without its result |
| P26-S6A | Review and accept opening/carryover conversion | DONE | Owner merged PR #385 at `e298d2e0`; accepted head `bba4f944` passed full local, actual JavaFX and exact-head GitHub CI |
| P26-S6B | Assist merchant settlement | DONE | Owner merged PR #386 at db488cd2; final published head fb7f7813 passed full CI |
| P27-S1 | Define generic accounting packet contract | DONE | Owner confirmed/merged PR #387 at a5c6ef17; final head 6ac81bcf passed CI run 40232697699 |
| P27-S2 | Export generic accounting workbook and supporting packet | DONE | S2A/S2B merged in PRs #388/#389; generic packet and operational sections complete |
| P27-S2A | Export an incomplete core financial packet | DONE | Owner merged PR #388 at 54d939fc; published head d63a8fbf passed clean/repeat/JavaFX CI run 40236217512 |
| P27-S2B | Complete operational sections and enriched ledger | DONE | Owner merged PR #389 at fac8b071; final-head clean/repeat/JavaFX workflow 40241876620 passed |
| P27-S3 | Add factual close readiness and evidence references (G3/G5/G13) | DONE | S3A/S3B1/S3B2 merged, most recently PR #392 at 76fe8450; accepted-head CI passed |
| P27-S3A | Shared-snapshot readiness preview and correction navigation | DONE | Owner merged PR #390 at ad21bfab; final-head workflow 40260820386 passed |
| P27-S3B | Durable factual evidence, exception reasons and close-time revalidation | DONE | S3B1/S3B2 merged with accepted-head CI passed |
| P27-S3B1 | Snapshot-bound external evidence references and review history | DONE | Owner merged PR #391 at 6b1d4a34; final-head clean/repeat/JavaFX passed |
| P27-S3B2 | Serialize readiness writers and enforce close-time revalidation | DONE | PR #392 merged at 76fe8450; accepted head e3091452 passed clean/repeat/JavaFX workflow 40428989992 |
| P27-S4 | Complete interchange coverage for all new fields (G4 and cross-cutting) | IN_PROGRESS | [Audit and remaining gates](P27-S4-portability-audit.md); incomplete until S4A–S4D merge |
| P27-S4A | Durable field inventory and recovery gate | DONE | PR #393 merged at 8500d99b; accepted head a5263d26 passed workflow 40442798593 |
| P27-S4B | Complete budget metadata portability | DONE | PR #394 merged at 00aa3cb6; accepted head 3437de8b passed workflow 40604890977 |
| P27-S4C | Preserve operational transfer links | DONE | PR #395 merged at 6ed44f63; accepted head 5856e0d9 passed workflow 40617977330 |
| P27-S4D | Evidence validation and integrated compatibility gate | IN_PROGRESS | S4C merged; bounded S4D1 selected; complete only after S4D1–S4D3 merge |
| P27-S4D1 | Retained review validation | DONE | PR #397 merged at f8206e45; accepted head c852a10a passed workflow 40809782195 |
| P27-S4D2 | Complete category-master portability | VERIFYING | [Contract](P27-S4D2-category-portability.md); UUIDs, exact master fields, unused records, conflict/legacy/rollback gates |
| P27-S4D3 | Integrated all-feature and recovery acceptance | BLOCKED | S4D2 merged; combined P24–P27 report/history/conflict/backup fixture |
| P28-S1 | Run the complete runbook acceptance matrix | BLOCKED | P27-S4 merged; owning contract and D01–D10 gates as applicable |
| P28-S2 | Release and close the correction program | BLOCKED | P28-S1 merged; owning contract and D01–D10 gates as applicable |
| P27-S5 (conditional) | Internal attachments and approvals | BLOCKED | Not adopted; explicit scope amendment and separate specifications required; not a prerequisite for baseline P28 |

P27-S1 consolidates earlier template/policy collection. Its position does not defer unresolved applicable D02–D07 decisions before their affected code slices. D03's former mandatory external-policy-input gate is resolved by the S4D scope correction; do not request those policies before NMR accounting development.

### P23-S1 handoff

Status: VERIFYING (local documentation implemented; not DONE).

- Base: `849dc4e8f652d47aff05c124790f78d6dc7f93dc`.
- Branch: `codex/P23-S1-adopt-correction-plan`.
- Pull request: https://github.com/benbaron/sca-jakarta-h2/pull/351 (draft). Owner authorized publication on 2026-09-26.
- Publication mapping: local `1993128` -> remote `739c31ab23f34794085d73fc54ce33777d32bda4`, tree `78f74144e79797f4b2fb8220f8d2a9b596f7800a`; local `215ba5b` -> remote `f01ae14a85c61d6bdd56f7988d6c2d41df1b37a8`, tree `b611bf60fece88bc90d8c3f9e826108925328020`. Both trees match; connected-service commit identities differ from local identities. This publication-handoff commit follows that reviewed content head.
- Completed: live adopted P23–P28 contract; all slice statuses/dependencies; G1–G14 source/test/owner/disposition ledger; A01–A14 reproducible specifications; runbook coverage matrix; retrieved named workbook identity/structure and instruction anchors; D01–D10 pending-input decisions; owner review instructions.
- No production code, migrations, workbook data or archived proposal changes.
- Validation: source/route/test inventory inspection and read-only workbook structure inspection completed. Local validation passed: four changed documents, 26 adopted slices plus conditional P27-S5, G1–G14/A01–A14 coverage, local Markdown links, unchanged archive, active selection, and `git diff --check`. An initial validator assumed 27 baseline slices; checking the actual adopted headings corrected that count to 26 plus one conditional slice, and the complete check passed. `mvn clean verify` attempted: exit 127, Maven unavailable; no Maven wrapper. No application tests, desktop acceptance or exact-head CI pass claimed.
- Remaining: owner supplies/verifies D01 installed-build evidence and confirms D02 submission authority; policy inputs gate only their affected later slices. S1 owner documentation acceptance, final-head CI and merge remain outstanding.
- CI: Maven PR Tests run `36292168364` started for published head `f01ae14`; it was in progress when this handoff was prepared. This handoff changes the head, so require the successor final-head run; no CI pass is claimed in this record.
- Next exact action: inspect final-head Maven PR Tests for PR #351, resolve any failure, and review `doc/P23-S1-user-testing.md` with the owner. Do not implement S2 until S1 merge is confirmed.

## 12. P23-S1 closure and P23-S2 selection

P23-S1 is DONE: owner merged PR #351 at `654825ba8f2855204cc52ca3302f878f8746e500`; exact final head `08ace942e745282a2d02aa053f7b3fa9837e8725` passed Maven PR Tests run `36292208118`. The owner explicitly selected P23-S2 on 2026-09-27. The preceding S1 handoff is historical.

P23-S2 is VERIFYING on `codex/P23-S2-budget-reversal-actuals`, based on that merged main. Scope is G1/A01 only; no schema, lifecycle-write, event UI, SCLX or open-item query changes. Required reading: AGENTS.md, this plan, adopted P23-S2 program contract, P23-S1 A01 baseline, budget model, transaction lifecycle/correction policy and report-library contract. Required inspection: BudgetPlanService, TransactionEntryService, TransactionCorrectionService, FinancialReportService, Txn/TxnSplit, FiscalPeriodRange, V48 status constraints, BudgetPlanServiceTest and correction/report tests.


### P23-S2 implementation and validation

- Completed: actuals explicitly include ENTERED and REVERSED canonical transactions by their own dates; signed expense/income rules retained; missing categories appear as separate unclassified income/expense groups per fund. No lifecycle writes, schema changes or invented persisted categories.
- Regression: `BudgetReversalActualsTest` reproduces A01 through real entry/reversal/replacement commands, checks same/later-period cutoffs, income signs, refunds, category/fund separation, July fiscal boundaries, and unclassified amounts. GL, Income Statement and event/fund projections are compared on equivalent scopes. Budget comparison itself still aggregates all events; event budget filtering remains P26-S3.
- Reproduction evidence: on the unchanged production query, all seven original regression cases failed while eight existing BudgetPlanServiceTest cases passed. Before-cutoff originals became 0 instead of ±100; replacement expense was −20 instead of 80 (income 20 instead of −80); prior fiscal-year expense became 0 instead of 100. The initial fixture omitted active-chart selection; that fixture was corrected before recording these seven accounting failures.
- Baseline compile passed. Focused gate passed: 19 tests, zero failures/errors/skips, covering new budget regression tests and existing budget/correction tests. Full verification and final handoff follow below.
- Governing specification: [budget actuals](accounting/budget-model.md#p23-s2-correction-actuals). [User-visible changes and manual acceptance](P23-S2-budget-reversal-user-testing.md).
- Remaining: publication authorization, PR/exact-head CI, owner desktop acceptance and merge. No PR or GitHub S2 result is claimed. P23-S2 is not DONE; P23-S3 stays blocked.


### P23-S2 final local handoff

- Status: VERIFYING; branch `codex/P23-S2-budget-reversal-actuals`; PR: none (not published).
- Reviewed implementation head: `d8fae86a2cb6fcfa47856db56de187b4538c19c9`. This documentation-only handoff commit follows that head; resolve the branch tip with `git rev-parse HEAD` before publication. The active-head field identifies the verified implementation commit.
- Final local gate: `mvn --settings /tmp/p23-maven-settings.xml clean verify` passed on 2026-09-27 using Maven 3.9.9: **801 tests, 0 failures, 0 errors, 31 skipped**, BUILD SUCCESS. New regression class: 8 passing cases, including event/fund reconciliation. Headless skips are not desktop acceptance. `git diff --check` and changed-document local link validation passed.
- Environment recovery: installed Maven in `/tmp` and used runtime proxy settings outside the repository. Initial dependency resolution failed with the stale proxy; an offline clean attempt lacked a cached plugin. Refreshing the private settings and rerunning online completed successfully. No project build configuration changed; no known failing test remains. The build-generated removal of the tracked manifest was restored.
- Changed documents: this plan, P23-S1 baseline execution cross-reference, accounting/budget-model.md, and P23-S2-budget-reversal-user-testing.md. Archive unchanged.
- Next exact action: obtain owner authorization for S2 publication under AGENTS.md section 5, then publish the reviewed commit sequence to the named branch without force, create the S2 PR with actual validation evidence, verify matching tree/head and exact-head CI, and record those results here. Owner desktop checks and merge remain required before DONE or advancement.


### P23-S2 publication

Owner authorized publication on 2026-09-27. Draft PR: https://github.com/benbaron/sca-jakarta-h2/pull/352. Branch: `codex/P23-S2-budget-reversal-actuals`; verified published content head: `0703e2a17abca00f3a9aa81069e65ceb2c91a21e`. The preceding local handoff is historical.

- Local `d8fae86a2cb6fcfa47856db56de187b4538c19c9` → remote `e7bc3e9e4104c9211d5c1bc363a78ed4ee49d1a7`, matching tree `eca2bd215884256216c4176ff09b25f15a67328f`.
- Local `5158780268318819523ca46ceead112264c17352` → remote `0703e2a17abca00f3a9aa81069e65ceb2c91a21e`, matching tree `f902e32d1fde4177199e5dc87578be0028b2adac`.
- Connected-service publication preserved commit messages, order and file trees. The branch was absent and created at the reviewed head; no force update occurred. Remote main remains the verified S1 merge base.
- This publication-record commit follows that content head. Require the successor exact-head Maven PR Tests run, not a result for an earlier head. At preparation, GitHub validation is pending; final result is recorded in the PR description after the run completes.
- Remaining: exact-head CI, owner execution of P23-S2-budget-reversal-user-testing.md, and merge. S2 stays VERIFYING and S3 stays BLOCKED. Next action: inspect PR #352's final-head checks and resolve any failure before owner review/merge.


## 13. P23-S2 closure

On 2026-09-27 the owner confirmed acceptance and merge of PR #352. GitHub verifies merge commit `321a3fd3a702931775ac47073229a3e389e4cb45` in current main. Final PR head `5d238989ed06ce1c5ad8774746080d6e239d7ec6` passed Maven PR Tests run `36337954378`, including clean headless verification, the additional test pass and production JavaFX route compliance. Local verification was 801 tests, zero failures/errors, 31 skips. No post-merge CI result is claimed.

P23-S2 is DONE. All preceding S2 pending-publication, acceptance and merge statements are historical. P23-S3 is READY, with no implementation branch or PR yet. It owns G2/A02 historical open-item balances; read the adopted S3 contract and required lifecycle/report sources before coding. No later slice has been implemented.

This documentation-only closeout is recorded on fresh branch `codex/P23-S2-closeout`, based on the verified merge above; local only, no closeout PR. Validation: merge and exact-head CI verified; `git diff --check` passed. When beginning S3, preserve this closeout record in the S3 plan update if it has not separately reached main. Next action: await owner selection of S3, then refresh main and create a fresh S3 branch.


## 14. P23-S3 execution

Owner selected S3 on 2026-09-27. Branch `codex/P23-S3-historical-open-items` starts from current main `321a3fd3a702931775ac47073229a3e389e4cb45` and preserves the S2 closeout record. PR: none. Scope: G2/A02 effective-date supplemental projection only; S4 completeness and S5 interchange remain separate.

Required reading/inspection completed: root AGENTS, PLAN, adopted program S3 and baseline A02; transaction/editor, lifecycle, correction-policy and report-library contracts; SupplementalOpenItemQueryService, Txn/TxnSupplementalLine, entry/correction services, V77 migration, six-report builder, Dashboard and Apply Existing Item consumers, existing query/report/migration tests.

Design finding: reversal transactions have inverse ledger splits and a unique reversalOf link but no supplemental allocations; replacements copy the original allocations with the same item identity. The projection must retain original allocations until their dated inverse facts apply, including reversal chains and permitted backdating. Derive corrections from those existing facts; do not add persisted allocations or a schema migration. Update the earlier current-status-only specification in this slice.


### P23-S3 implementation and validation

- Delivered: effective-date original/inverse projection for all six kinds; reversal chains and independent replacements; explicit explanation when an inverse precedes its source date; shared save-time increase validation using the same projection and current EntityManager. No migration, persisted duplicate reversal allocations, new report model, or UI layout changes.
- Baseline compile passed. Red reproduction against unchanged production query: 10 tests run, seven assertion failures and one missing-row error in the eight new cases; two existing cases passed. All six kind fixtures changed February from 75 to 100 after a March reversal; opening replacement erased prior balances and a backdated inverse had no row.
- Focused verification passed: 18 tests, no failures/errors/skips, covering query, six report definitions, legacy migration, Dashboard/report/control-ledger agreement, opening/settlement corrections and company isolation. Final full verification follows in the handoff.
- Specifications updated: [transaction supplemental lifecycle](accounting/transaction-editor-and-journal.md#supplemental-transaction-records-and-open-item-lifecycle), [reports](reporting/report-library.md#supplemental-open-item-reports), and [owner testing](P23-S3-historical-open-items-user-testing.md).
- Scope boundary: earlier direct-edit/delete policy remains in force; S3 does not reconstruct deleted versions. S4 completeness/over-application protections and S5 SCLX lifecycle portability remain separate. Owner desktop acceptance, publication/exact-head CI and merge remain outstanding; S3 is not DONE.


### P23-S3 final local handoff

- Status: VERIFYING; branch `codex/P23-S3-historical-open-items`; PR: none, not published. Verified implementation head: `04801e5265ae1c199af7367a8de3021f54d66c00`; this documentation-only handoff follows it. `git rev-parse HEAD` identifies the local tip for publication. The branch also carries the preserved S2 closeout commit.
- Final gate: Maven 3.9.9 `mvn --offline --settings .mvn/settings-github.xml clean verify` passed on 2026-09-27: **815 tests, 0 failures, 0 errors, 31 skips**, BUILD SUCCESS. All 16 supplemental query cases passed, including the final changed replacement amount. No test failure remains. Headless skips are not visual acceptance.
- Baseline compile and 18 focused checks passed. Final diff whitespace and changed-document local links passed. Maven was restored to `/tmp` because it was no longer present; all dependencies resolved from the existing local cache. No build configuration changed. Build-generated removal of the tracked manifest was restored.
- Completed: A02 reproduction/correction, shared read/save projection, regression/consumer checks, governing specifications, user testing notes and S2 closure carry-forward. No schema or SCLX changes.
- Remaining: owner publication authorization under AGENTS.md section 5, draft PR, exact-head CI, manual acceptance and merge. No GitHub S3 result is claimed. Next exact action after authorization: publish the reviewed local commit sequence without force, verify each tree and PR head, run/inspect CI, and record results. P23-S4 remains BLOCKED.


### P23-S3 publication

Owner authorized publication on 2026-09-27. Draft PR: https://github.com/benbaron/sca-jakarta-h2/pull/353. Branch: `codex/P23-S3-historical-open-items`. Verified published content head: `c1e98e59dc87656616358a50c0f1587a2577dc44`. Preceding local-only and authorization-pending handoffs are historical.

- Local `2f4f88cb2771299ee93fca9e01eb9e9f1b8d6995` → remote `b4c7d8dbc7ec1adcc3230c17a12af7bbcadb8bc9`, matching tree `81ee7e8696a83414889c2dbc5826ec28226e7d14`.
- Local `04801e5265ae1c199af7367a8de3021f54d66c00` → remote `54d09c8b6599c9f50db3e4699bf8247ae00954cf`, matching tree `4989a9a55cb6b18a36498cd485f551fe41515c9b`.
- Local `dfb50428d860b37e975076e3df1528d5eedd6472` → remote `c1e98e59dc87656616358a50c0f1587a2577dc44`, matching tree `f9ac4ae346a1fb5031c14ee98b818551d75c9f76`.
- All messages, ordering and file trees preserved through connected-service publication. A documentation transfer mismatch was caught before branch creation and corrected with exact blob bytes; only matching trees were published. No force update occurred.
- This publication-record commit follows the reviewed content head. CI is pending at preparation; inspect the successor final-head Maven PR Tests run. The final result and head are recorded in the PR description after completion.
- Remaining: final-head CI, owner desktop checks in P23-S3-historical-open-items-user-testing.md, and merge. S3 remains VERIFYING; S4 remains BLOCKED. Next action: resolve any final-head check failure, then owner acceptance/merge.


## 15. P23-S3 closure and P23-S4 policy gate

Owner accepted and merged S3 on 2026-09-27 (America/Denver), then instructed proceeding to S4. GitHub confirms PR #353 merged into current main at `114cc045761fb64c5e22b03f1f8ab2a3944024e0`. Exact final head `231fa1a643d557c3175d5606e4109e4d9432d328` passed Maven PR Tests run `36356514191`. S3 is DONE; preceding outstanding S3 acceptance/merge statements are historical. No post-merge CI result is claimed.

S4 is selected on fresh branch `codex/P23-S4-supplemental-completeness`, based on that main. PR: none. Status: BLOCKED on the explicit D08 prerequisite in P23-S1-baseline-and-acceptance.md. Inspection confirms that full allocation enforcement affects historical SCLX imports, which currently carry no lifecycle linkage. The adopted reject-excess default is already settled; the historical import exception and remediation contract still need resolution.

Completed: merge/CI verification; S4/A03/D08 contract review; entry/correction/import boundary inspection; [concrete D08 policy proposal](P23-S4-completeness-policy-proposal.md). The proposal preserves historical data through an acknowledged exception while rejecting incomplete new entries and excess applications, and keeps S5 portability separate. No production changes or tests were made while the policy gate is unresolved. Documentation-only validation: local links and diff whitespace. No application validation or S4 completion is claimed.

Remaining: owner decision on the proposed legacy exception contract; implementation, regression/concurrency/consumer testing, full verification, publication authorization, exact-head CI and manual acceptance. Next action: adopt or revise the proposal, update D08 to the actual decision, mark S4 IN_PROGRESS and perform remaining required UI/source inspection before implementing. Local documentation handoff only; not published.


## 16. P23-S4 policy adoption and implementation

Owner adopted D08 on 2026-09-27 after clarification of SCLX behavior. The [policy](P23-S4-completeness-policy-proposal.md) is now governing. Prior BLOCKED statements are historical. S4 is IN_PROGRESS on `codex/P23-S4-supplemental-completeness`; no PR yet. New entries require complete explicit allocations; excess applications are rejected; legacy imports require explicit acknowledgment and remain visibly incomplete until repaired. No guessed links or new overpayment workflow.


### S4 implementation and validation handoff

- Implemented strict complete control-split allocations for normal and generated entries; company-lock serialization and effective-date availability checks for writes, edits, deletes and corrections; excess application rollback.
- Implemented explicit legacy SCLX acknowledgment, source/hash audit evidence, precise per-line preview warnings (including textual decimal amounts), and truthful preserved-data incompleteness. No lifecycle portability, guessed allocations, schema migration or overpayment workflow is introduced.
- Added all-six-kind control reconciliation with ledger/explained/gross unmatched/difference, stable transaction/split diagnostics, supplemental readiness, text/CSV/table output and Journal repair navigation. Offsetting missing allocations cannot cancel gross deficiencies. P27 general close readiness remains separate.
- Governing documents updated: [adopted D08 policy](P23-S4-completeness-policy-proposal.md), [decision baseline](P23-S1-baseline-and-acceptance.md), [Journal](accounting/transaction-editor-and-journal.md), [Report Library](reporting/report-library.md), [SCLX](data-exchange/sclx.md) and [interface matrix](interface-operation-matrix.md). [Desktop/user acceptance instructions](P23-S4-supplemental-completeness-user-testing.md) cover the visible behavior and remaining visual check.
- Evidence: baseline compile passed after replacing two corrupt local Maven cache artifacts (ECJ and POI; no project dependency/configuration change). New missing/partial and competing-application regression tests first failed on prior behavior, then passed. Focused integration run: 52 tests, zero failures/errors/skips. An intermediate full run exposed old assertions that expected permitted excess applications or omitted control rows; those assertions were updated to the adopted contract. The first full verification attempt also caught a test-call signature typo, corrected before the successful run. Full verification before the final textual-decimal edge-case fix passed: 820 tests, zero failures/errors, 31 headless skips. Final-source `mvn --offline --settings .mvn/settings-github.xml clean verify` passed on 2026-09-27 (America/Denver): **820 tests, 0 failures, 0 errors, 31 skips**, BUILD SUCCESS. This includes the textual-decimal regression. Local documentation links and `git diff --check` also passed. No known test failure remains; headless skips do not establish desktop visual acceptance.
- Status: VERIFYING, branch `codex/P23-S4-supplemental-completeness`, PR: none. Verified implementation commit: `b7ee73cddcb2a1b4511383f619d267ba331e0f87`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the local tip for publication. Worktree was reviewed; generated manifest restored. No published-head CI or desktop visual acceptance is claimed. S5 remains BLOCKED until S4 is merged.
- Next exact action: request owner publication authorization under AGENTS.md section 5; after authorization publish the exact reviewed commit sequence without force, verify trees/head and CI, and complete owner desktop acceptance before merge.


### S4 authorized publication — PR #354

Owner authorized publication on 2026-09-27 (America/Denver). Draft [PR #354](https://github.com/benbaron/sca-jakarta-h2/pull/354) targets unchanged main `114cc045761fb64c5e22b03f1f8ab2a3944024e0`. Connected-service publication preserved the reviewed three-commit sequence/messages; every tree matched the corresponding local tree. Remote head `524c635f3c56167770637ba2d92e08f731b95fcd` corresponds to local `88664fa9b4785f94d633c49e3c6425ba0314675c`. Commit IDs differ because publication creates server-authored commit objects; content is identical.

Initial Maven PR Tests run `36375036852` started on that head. Inspection of the existing CI workflow found that the new renderer geometry/repair test would remain headless-skipped even though other route tests run under Xvfb. This follow-up adds `FormattedReportFxRendererTest` to that existing Xvfb test list so CI actually exercises the new behavior. No production source changed after the locally successful 820-test gate. Final-head CI remains pending; desktop visual acceptance, owner acceptance and merge remain required. Previous publication-authorization requests are satisfied. S4 remains VERIFYING and S5 BLOCKED.


## 17. P23-S4 closure and P23-S5 execution

Owner confirmed completion and instructed continuing on 2026-09-27 (America/Denver). GitHub verifies PR #354 merged at current main `820ab4235350999a8123d2b195cb18eaeb6793c0`. Final head `cf19103e15c27bb8e56fde0e0ee3b007e6220ea2` passed Maven PR Tests run `36375133299`, job `108779350961`, including both report renderer tests under Xvfb without skips. S4 is DONE; earlier pending S4 records are historical.

S5 is IN_PROGRESS on fresh branch `codex/P23-S5-sclx-lifecycle` from that main. Scope is G4/A04 only. Required inspection covers SCLX specification, snapshot/serialization/preview/identity/commit/correction code, canonical entry/query services, V77 and existing supplemental/import tests. Baseline compile passed using restored Maven 3.9.9; no build configuration change.

Design: add optional version-1 `lifecycle` objects to existing supplemental detail records, carrying the intrinsic item UUID, effect and portable transaction-line reference. Missing objects remain legacy. The existing detail identity/hash owns conflict handling; no second item ledger or migration. Linked exports preserve canonical split order and validate links. Import stages complete transaction/correction history within its existing atomic boundary and validates allocations and effective balances after relationships exist, independent of file ordering. Ordinary/generated entry validation stays strict. Unsupported versions, malformed or foreign/dangling links block preview/commit. Legacy acknowledgment remains explicit and never suppresses invalid linked data.


### S5 implementation and verification

Implemented optional version-1 lifecycle payloads within existing supplemental records, export-side reference validation, preview rejection of invalid linkage, allocation-aware legacy warnings, and canonical command mapping. Existing detail hashes include lifecycle content for conflict review. Linked exports preserve source/inverse split correspondence and numeric portable ordinals; ordinary exports retain established ordering. No migration or new persistent authority.

The SCLX transaction batch uses the existing entry service with a scoped history writer. Commands, ownership, period and line rules remain enforced. Complete-source/inverse coverage and effective available balances are checked after correction relationships are restored; failure marks the caller transaction rollback-only. Normal/generated entry APIs remain immediate and strict. Legacy acknowledgment never waives malformed links, foreign item identity or excess allocation.

Existing baseline compile and 31 supplemental/import tests passed. Focused integration gate passed 38 tests after correcting a regression that initially applied linked inverse correspondence to unrelated legacy corrections. Eleven new round-trip cases passed, including all six kinds, partial/reversed/replacement history, reordered transaction arrays, repeat import/re-export, malformed links, changed identities, foreign-company conflict, 60+60 against 100 batch rollback, repeated control accounts with thirteen ledger lines, and explicit acknowledgment for an inverse of an existing legacy source. A detached-baseline regression run was initiated before a runtime replacement; its temporary log is unavailable, so no red-run result is claimed. Final `mvn clean verify` passed on 2026-09-28 (America/Denver): 831 tests, zero failures/errors, 31 headless skips. This includes all eleven new lifecycle cases after the final source-completeness review.

Compatibility inspection: external SCLX specification/workbook exporter at `109b99e360d2bf5eaf6bbf829c3e808c29e7c2e0` was read only. Its current v14 exporter emits legacy supplemental details without lifecycle objects; the optional extension fits the existing schema extension boundary. No external repository or workbook was changed, and no Excel round-trip fidelity is claimed. Governing [SCLX specification](data-exchange/sclx.md#p23-s5-versioned-supplemental-lifecycle), [Journal contract](accounting/transaction-editor-and-journal.md#p23-s5-sclx-history-validation) and [S5 user testing](P23-S5-sclx-lifecycle-user-testing.md) describe the actual behavior and reduced-information compatibility.

Status: VERIFYING on `codex/P23-S5-sclx-lifecycle`, based on main `820ab4235350999a8123d2b195cb18eaeb6793c0`; PR: none. Remaining: publication authorization, exact published-head CI, desktop acceptance and merge. No S5 GitHub or visual result is claimed. Verified implementation commit: `83bb730a64626cc8b5c3f0bf43fef614d162fc9c`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the local publication tip. Generated tracked manifest restored and diff whitespace checked. Next exact action: obtain S5 publication authorization, publish the reviewed commit sequence and inspect final-head CI.


### S5 authorized publication — 2026-09-28 (America/Denver)

Owner explicitly authorized S5 publication. Draft [PR #355](https://github.com/benbaron/sca-jakarta-h2/pull/355) targets main `820ab4235350999a8123d2b195cb18eaeb6793c0`. Local Git push lacked credentials; connected-service publication preserved both reviewed commit messages, sequence and exact trees:

- Local `83bb730a64626cc8b5c3f0bf43fef614d162fc9c` maps to remote `92936695411ac965539162bf0ce48da7e1d1655b`, tree `6b58916a16350cb0010347a21f6f63d4d8104289`.
- Local `5e4b50e6a933ca97535fec5abfd9c1a2ba4a0f88` maps to verified PR head `7f21285bb4568f6768e2501368339b454f9771aa`, tree `3aadafce6a6c858430a183fb147490f19541ea79`.

Earlier publication-authorization requests are satisfied. This documentation-only publication record follows that content head. GitHub validation is pending at preparation; inspect the successor final-head Maven PR Tests run and record its outcome in the PR description. Local full verification passed 831 tests with zero failures/errors and 31 headless skips. Desktop visual/user acceptance and merge remain outstanding. S5 remains VERIFYING; no later slice is activated.


## 18. P23 closure and P24 readiness — 2026-09-29 (America/Denver)

Owner confirmed acceptance and merge of S5. GitHub verifies [PR #355](https://github.com/benbaron/sca-jakarta-h2/pull/355) merged at current main `74efca9bd67d4e47c5d54b07822691cf78e49034`. Its final head `82487d07b5e14b2cecac3c3add938e7bf0acb26f` passed [Maven PR Tests run 36515069363](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36515069363), job `109235479177`: both full test passes reported 831 tests, zero failures/errors and 31 headless skips; the Xvfb JavaFX pass reported 11 tests with zero failures/errors/skips. Owner acceptance is recorded separately from automated visual checks. Earlier S5 pending acceptance/publication/merge records are historical.

P23-S5 and P23 are DONE. P24-S1 is READY, with no implementation branch or PR. Its scope is named event creation and discovery through the existing Activity authority; Fund/Event Journal tagging belongs to P24-S2. D01 installed executable, launch route, role/company, display/scaling and saved-layout evidence remains required for P24-S1 reproduction acceptance; no such evidence is invented by this closeout.

This documentation-only closeout is on fresh branch `codex/P23-closeout`, based on the verified merge above; the merged S5 branch is not reused. Validation: GitHub merge/final-head CI verification and `git diff --check`; no application changes or additional application test run. Next exact action: start P24-S1 scoped inspection from current main when execution resumes, preserving this closure record. No P24 implementation has begun.


## 19. P24-S1 execution — 2026-09-29 (America/Denver)

Owner selected the next slice. Fresh branch `codex/P24-S1-event-discovery` starts from main `74efca9bd67d4e47c5d54b07822691cf78e49034` and carries the documentation-only P23 closure commit `f709b38`. No PR yet. Baseline compile passed. Current Activity service already enforces stable identity, ownership, authorization and referenced-record protection. Source reproduction finds maintenance labelled only Activities, no name-search control, no Event Accounting maintenance route, and row selection that can discard a dirty new form. D01 owner-installed-build/layout evidence remains a desktop reproduction acceptance prerequisite; source findings do not claim that evidence.

Design: retain Activity authority and existing route/state keys; expose Events / Activities, New Event, Edit and existing Active/Save lifecycle controls, case-insensitive name/code search, and a maintenance navigation action from Event Accounting. Preserve unsaved forms through filtering and asynchronous refresh. No schema or accounting-service policy change. P24-S2 Journal tagging is outside this slice.


### P24-S1 implementation and validation

Implemented Events / Activities navigation/title/help, New Event and explicit Edit focus, case-insensitive name/code search including inactive records, and the Event Accounting maintenance route. Existing Activity service, stable IDs, ownership, permission gates, lifecycle/delete rules and `activities.*` layout keys remain authoritative. Filtering and asynchronous loads retain unsaved drafts; selecting a different row and Refresh preserve drafts when discard is cancelled. Sorting remains live through search. The header/editor expose scrolling at narrow widths.

Baseline compile and eight focused tests passed. The modified focused Maven run passed ten tests with two display-dependent skips. Both new `EventDiscoveryPanelTest` cases then passed without skips using a temporary JavaFX Monocle headless backend and JUnit console: repeated names/distinct codes, name/code search, dirty new/edit cancellation, save, VIEWER gating, customized column order/width/sort, 900/600-pixel widths with enlarged text, table horizontal/vertical scrolling, editor overflow, divider movement, maintenance routing and refreshed Event Accounting choices. The test confirmation callback replaces modal automation only in tests; production retains the normal discard dialog. Initial modal automation hung; an initial teardown emitted late preference-save authorization errors. Both were corrected, and the final two-test run passed without those errors. No native desktop visual acceptance is claimed. Temporary tools/logs were lost in the subsequent runtime replacement; the observed results above are retained here. CI now includes these tests in its existing Xvfb step.

Donor review: `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180` supplies a read-only event list concept, but no better canonical maintenance authority; no donor persistence/UI framework was copied. Governing [P21/P24 contract](P21-activity-event-accounting.md#p24-s1--event-discovery-and-maintenance-navigation), [operation matrix](interface-operation-matrix.md) and [user testing](P24-S1-event-discovery-user-testing.md) describe the implemented workflow. No migration or accounting policy change.

Status: VERIFYING on `codex/P24-S1-event-discovery`; PR: none. Final local `mvn clean verify` passed on 2026-09-29 (America/Denver): 833 tests, zero failures/errors, 33 headless skips. The two new JavaFX tests separately passed on Monocle as recorded above. Remaining: publication authorization, exact published-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. P24-S2 is not started.


Final-gate prerequisite repair: the first full run reported 833 tests, one failure, zero errors and 33 headless skips. Existing `ActivePeriodContextTest` expected notification when setting 2026-09-30, which equaled this replacement runtime's current date; JavaFX correctly emits no change for the same value. The test now establishes a distinct starting date and removes its listener after asserting the change. This narrowly scoped, test-only repair is required to complete the slice's full gate; no period behavior changed. The fresh full verification passed: 833 tests, zero failures/errors and 33 headless skips. No known failing test remains.


Verified implementation commit: `a448bdac93731eff1d3c5af60c159605368ccbea`. This documentation-only handoff follows it; `git rev-parse HEAD` identifies the publication tip. Diff whitespace and local documentation links passed; the generated tracked manifest was restored. Worktree changes were reviewed and committed. No P24-S1 PR, published-head CI or owner desktop acceptance is claimed. Next exact action: obtain explicit publication authorization under AGENTS.md, publish this branch's reviewed sequence (including the P23 closeout), open a draft PR and inspect its final-head workflow.


### P24-S1 authorized publication — 2026-09-29 (America/Denver)

Owner explicitly authorized publication. Draft [PR #356](https://github.com/benbaron/sca-jakarta-h2/pull/356) targets unchanged main `74efca9bd67d4e47c5d54b07822691cf78e49034`. Connected-service publication preserved the reviewed sequence/messages and verified all local trees:

- Local `f709b38d8c58223244d20c8ae9cd75508c9c211b` → remote `f9072d5b2e3852c79ab98509c93654f156d43aae`, tree `acfe8040744270bae61baa723d67bc72eb4c9482`.
- Local `a448bdac93731eff1d3c5af60c159605368ccbea` → remote `8937ff50ad15568cf12926f01d8efa83ff33db8d`, tree `8ddba76222e98f91b5c609373033888ad1fb26fb`.
- Local `7d4a7bdb75fd52abae2b7242da01b249882d8d8d` → verified PR head `69107cc696a2e9b66846b4d914745d5885fd3872`, tree `7b2d996e406e1fb1314ef2d59a6f77865a5c95ad`.

Earlier publication requests are satisfied. This documentation-only record follows that content head. Published-head CI is pending at preparation; inspect the successor final-head Maven PR Tests run and record the outcome in the PR description. Local validation passed 833 tests with zero failures/errors and 33 headless skips; both new JavaFX tests separately passed. D01 installed-build/layout evidence, owner desktop acceptance and merge remain outstanding. P24-S1 remains VERIFYING; P24-S2 remains blocked.


## 20. P24-S1 closure and P24-S2 readiness — 2026-09-30 (America/Denver)

Owner explicitly confirmed acceptance and merge. GitHub verifies [PR #356](https://github.com/benbaron/sca-jakarta-h2/pull/356) merged at current main `ab580628740228812e32649d19e723d50e81b152`. Final head `671deec0bb38223ec9fafb38d0db543677569510` passed [Maven PR Tests run 36670303713](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36670303713), job `109743720948`: both full passes reported 833 tests with zero failures/errors and 33 headless skips; Xvfb reported 13 tests with zero failures/errors/skips, including both event-discovery cases. Owner acceptance supersedes the earlier pending-acceptance statements; no specific screenshots, display settings or D01 measurements are invented by this record.

P24-S1 is DONE. P24 remains IN_PROGRESS; P24-S2 is READY with no implementation branch or PR. S2 owns visible/searchable Journal Fund/Event choices, independent line assignments, saved-entry review and event-name filtering, with refresh that preserves drafts. No S2 implementation has begun.

This documentation-only closeout uses fresh branch `codex/P24-S1-closeout` from the verified merge; the merged implementation branch is not reused. Validation: merge and final-head CI verification, plan status review and `git diff --check`. No application code changed or additional application test run was needed. Next exact action when execution resumes: create a fresh P24-S2 implementation branch from current main, preserve this closeout record, and inspect the owning contract and current Journal workflow.


## 21. P24-S2 execution — 2026-09-30 (America/Denver)

Owner selected S2. Fresh branch `codex/P24-S2-journal-fund-event` starts from main `ab580628740228812e32649d19e723d50e81b152` and carries the S1 documentation closeout `f993eb3`. PR: none. Baseline compile passed. Source inspection finds the Event column far right, non-searchable option cells, absent event names in saved Journal rows, ID-only inactive event rendering, and reference choices loaded only on construction.

Design: retain canonical per-line Fund/Activity assignments. Add a visible selected-line tagging area with name/code search, independent Apply Fund / Apply Event and explicit Clear Event actions; no inferred transaction-wide event and no automatic overwrite of mixed allocations. Keep existing table column/state IDs and expose Event next to Fund by default. Enrich transaction line read projections with current event code/name, including inactive history. Add service-backed Fund/Event name/code filters before row limiting; when both filters are supplied, require one split to match both. Refresh reference choices on return or explicit request without losing drafts or changing dirty state. No migration or second event authority; generated inventory tags remain P24-S3.


Implementation: visible selected-line tagging controls with independent active name/code searches, multi-selection Apply/Clear, current event labels in saved review and inactive loaded assignments, same-split Fund/Event filters before the result limit, and draft-preserving active-choice refresh. Existing IDs and per-company table preferences are retained; remembered dividers size filters and tagging regions. Corrected combo initialization/value commits and dirty tracking for applied dimensions. Journal drill-through New/Edit now respects the existing discard confirmation.

Donor: inspected `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180`, especially the associated-fund selector refresh in `JournalEntryWorkspaceFX`. Reused the refresh intent, not its name-based transaction-level assignment or persistence. No additional donor feature is needed in this slice.

Documentation: updated [Journal editor guidelines](ui/editor-guidelines.md), [interface operation matrix](interface-operation-matrix.md), and added [P24-S2 user testing](P24-S2-user-testing.md). Workflow includes the production JavaFX tagging test in the existing Xvfb gate. No SCLX or schema change.

Validation completed locally on 2026-10-01 (America/Denver): `/tmp/apache-maven-3.9.9/bin/mvn --offline --settings .mvn/settings-github.xml clean verify` passed: **837 tests, zero failures/errors, 35 headless skips**. The retained reports from the prior run had the same totals; the gate was repeated after the runtime restart because its temporary log was lost. Focused service/review tests passed, including same-split filtering before row limiting, mixed IDs, inactive rename/reopen, reversal preservation, company scope and untagged review. Both new production JavaFX tests separately passed with temporary Monocle headless rendering on 2026-09-30: two executed, zero skipped or failed. They cover selected-line tagging, draft-preserving refresh, permissions, restored wide/reordered columns, narrow/scaled geometry, divider movement and the reproduced combo initialization/value-commit defect. No native desktop acceptance is claimed.

Remaining: explicit publication authorization under AGENTS §5 Step 6, exact published-head CI, D01 installed-build/layout evidence and owner acceptance/merge. No known local test failures. P24-S3 remains blocked by S2 completion. The branch includes the S1 closeout, S2 implementation and a subsequent documentation-only handoff; current content head is recorded in that handoff.

## 22. P24-S2 local handoff — 2026-10-01 (America/Denver)

Status: VERIFYING. Branch: `codex/P24-S2-journal-fund-event`. PR: none. Verified implementation head: `4f87aeb212911354e9fb533a5e4ae95c9cb2dcc4`; this documentation-only commit follows it. The branch carries `f993eb3` (accepted S1 closeout) and the S2 implementation. `origin/main` was rechecked at `ab580628740228812e32649d19e723d50e81b152`. Final local clean verification: 837 tests, zero failures/errors, 35 headless skips; the two new JavaFX tests separately passed under Monocle. Diff review and whitespace checks passed. Generated tracked manifest restored after Maven clean; no generated build output is part of the implementation commit.

Completed: canonical selected-line tagging, searchable choices, event labels in saved/reopened entries, same-split Fund/Event filters, draft-preserving refresh, combo commits, resizable/scrollable controls, regression tests, CI selection and user testing notes. Remaining: owner publication authorization, draft PR and exact-head CI, installed desktop validation and acceptance/merge. Next exact action upon publication authorization: verify the clean local branch and remote state, publish the reviewed commit sequence without force-updating, create the P24-S2 draft PR with actual validation and desktop checks, then inspect its final-head Maven PR Tests result. Do not start P24-S3 before S2 is accepted and merged.

## 23. P24-S2 authorized publication — 2026-10-01 (America/Denver)

Owner explicitly authorized publication. Draft [PR #357](https://github.com/benbaron/sca-jakarta-h2/pull/357) targets unchanged main `ab580628740228812e32649d19e723d50e81b152`. Local Git lacked credentials, so connected GitHub publication preserved the reviewed sequence/messages and verified each tree:

- Local `f993eb345b8849675ce4303d95529e9f1560333f` → remote `9153340ae0981e85b9f12412b32ea35de620c3f9`; verified tree `d0a4834fed30fdddc8b0e8fbd279a26212a721a7`.
- Local `4f87aeb212911354e9fb533a5e4ae95c9cb2dcc4` → remote `bf0637cb424c602038f5a2f068b496eabd898c69`; verified tree `9b087601b7fc57269430507826ffb85f4baf5ad9`.
- Local `0b0fd6600c036dbbe0a768be680fb85bf1cdec41` → remote `8a5964065f72fa9feff0aedb62933be3eab673f4`; verified tree `30584fb26d35077ab02e2afd50c4472d40986d18`.

All earlier publication-authorization requests are satisfied. This documentation-only record follows the verified content head above. Final-head CI is pending at preparation; inspect its Maven PR Tests workflow and record the result in the PR description. Local clean verification passed 837 tests, zero failures/errors and 35 headless skips; both new JavaFX tests separately passed. Remaining: exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. S2 remains VERIFYING; S3 remains blocked.

Publication follow-up: remote documentation head `1fd7944715361551c763f18e66d2d82bc9e43524` was fetched and its tree `b1877e25f4af379bbc8096ac26a5025a7582946e` matched local `c5e438a2491479a7651f011b772465ae8b93f652`. [Run 36950856982](https://github.com/benbaron/sca-jakarta-h2/actions/runs/36950856982), job `110663322715`, entered clean headless verification. This documentation-only follow-up synchronizes two stale current-status summaries; inspect the successor final-head run and record its outcome in PR #357.

## 24. P24-S2 closure and P24-S3 execution — 2026-10-02 (America/Denver)

Owner verified and merged S2. GitHub confirms PR #357 merged at current main `a7a7ca6964884cbadf27af8b8891d68520e939c4`. Final head `3ceef546ec38abcaf75256242e2779d5beebfa34` passed run `36951048419`: both full passes 837 tests, zero failures/errors, 35 skips; Xvfb 15 tests, zero failures/errors/skips. Prior pending S2 acceptance/merge records are historical. S2 is DONE.

S3 starts on fresh `codex/P24-S3-inventory-event-costs` from that main; no PR. Baseline compile and eight focused inventory/accounting/authorization/interchange tests passed. Required inspection: InventoryMovementCommand, InventoryService previews/writes/reversals/import seams, InventoryPanel, TxnSplit, Event Accounting/Budget actuals, inventory SCLX snapshot/import and existing tests. G6/A06 owns this slice; D06 cost-method policy remains a P26-S5 gate and does not change the existing fixed unit value here.

Design: Event and optional Budget Category apply only to the generated offset split, whose account determines accounting recognition; the inventory split remains untagged. Both lines retain the item's Fund. Financial commands require an Event or explicit Non-event confirmation; nonfinancial zero-value movements reject accounting tags. Active/company/date eligibility is checked at preview and again under the existing commit lock. History reads current labels from canonical linked splits. SCLX retains existing Event/Fund references and movement-to-transaction provenance; the round-trip regression exposed missing Budget split attribution, repaired with the bounded transactionBudgets version 1 extension documented in the inventory contract. No duplicate movement dimension columns or database migration is introduced. Reversal copies canonical dimensions. No inference from a sale, memo or item default. Existing value/correction/closed-period/authorization policies remain in force.


### P24-S3 implementation and validation

Completed locally: canonical command/preview/offset-split attribution, explicit Non-event choice, active/company/effective-date checks, current-label movement history, governed reversal, draft-preserving Event/Budget choice refresh, confirmation debit/credit and tag labels, scrollable/resizable controls, and CI JavaFX selection. See [inventory contract](inventory/inventory-and-assets.md), [interchange contract](data-exchange/inventory-sclx.md), [interface matrix](interface-operation-matrix.md), and [P24-S3 desktop checks](P24-S3-user-testing.md).

Direct prerequisite repair: the generated-cost round-trip test proved that SCLX omitted Budget split links. A bounded transactionBudgets version 1 extension now carries line ID/category code, restores company-owned categories with the existing code-as-initial-name policy, includes attribution in transaction/line conflict hashes, and rejects invalid relationships before writes. Old files remain valid; older readers cannot commit a populated unsupported extension. Inventory extension/schema are unchanged. No category master-data maintenance or costing-policy expansion is included.

Donor inspection: NonprofitAccounting `c697630ec1f784ebe8338d7300da6c9ac801b180`, InventoryService lines 30–92 uses static-map/JSON storage. That authority is unsuitable here; existing H2/JPA commands, canonical TxnSplit dimensions and transaction-correction services were extended instead. No donor import or independent future feature was needed.

Focused accounting/authorization/interchange validation: 19 tests passed with zero failures/errors/skips. The A06 case proves $500 income, $200 expense, $300 net, Budget actuals, quantity, reversal, restart, tag-preserving SCLX import and idempotence; foreign/inactive/out-of-date choices and injected late-write failures leave no partial writes. Invalid extension relationships and tag-change conflict identity have regression coverage. Final full verification and final JavaFX geometry result are recorded in the handoff below.


### P24-S3 local verification — 2026-10-02 (America/Denver)

Final `mvn --offline --settings .mvn/settings-github.xml -Duser.home=/tmp/p24s3-test-home clean verify` passed: 843 tests, zero failures/errors, 36 headless skips. The initial full run had three existing UI-test cleanup errors because the replacement container's `/root` home is read-only; rerunning with an isolated writable Java home resolved them without changing application code. Final new JavaFX test separately passed under temporary Monocle 17.0.10/JUnit Console 1.10.2 (one test, zero failures/skips), including Event/Non-event exclusivity, optional Budget, draft-preserving refresh, inactive-choice removal, viewer write restrictions, 900/600-width and larger-text controls, horizontal/vertical history scrolling and divider movement. Native desktop visual acceptance is not claimed.

Final diff review, whitespace check, and new documentation links passed. Generated tracked manifest restored after Maven clean. Current `origin/main` was rechecked at `a7a7ca6964884cbadf27af8b8891d68520e939c4`; the owner's renewed merge confirmation refers to the S2 merge present there. S3 remains local, VERIFYING, with no PR or published-head CI. Remaining: explicit publication authorization under AGENTS §5 Step 6, draft PR and exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. P24-S4 is not started.


### P24-S3 commit handoff

Branch: `codex/P24-S3-inventory-event-costs`. PR: none. Verified implementation head: `c210c742e49eb3a8683a8ede7c737a0169f5b8fa`; this documentation-only handoff follows it, and `git rev-parse HEAD` identifies the publication tip. All implementation, regression tests, CI test selection, governing documentation and desktop notes are committed. No known local test failure remains.

Next exact action after owner publication authorization: confirm the clean local branch and expected remote parent, publish both reviewed commits without force-updating (matching tree SHAs if using connected GitHub transport), open a P24-S3 draft PR with the SCLX compatibility detail and actual validation above, inspect its final-head Maven PR Tests workflow, and complete the desktop checks in `doc/P24-S3-user-testing.md`. Do not begin P24-S4 until S3 is accepted and merged.


### P24-S3 authorized publication — 2026-10-02 (America/Denver)

Owner explicitly authorized publication. Draft [PR #358](https://github.com/benbaron/sca-jakarta-h2/pull/358) is open from `codex/P24-S3-inventory-event-costs` to main. Connected GitHub publication reproduced both reviewed commits with matching trees:

- Local `c210c742e49eb3a8683a8ede7c737a0169f5b8fa` → remote `e9bb37e34b73ba129e31ef6ff02bfc89fcb46776`, tree `0b4e5f6c2f7e8137789c68bebfadd4a26ae26f3a`.
- Local `90a9a80bf14416a943a172eec4ce1813665d9918` → remote `e1631b1e4bb360ec3236e834ea41f2fc88209221`, tree `ca3500ba0aa6c25c313df56b6d203812ceeae8c3`.

Remote main remained `a7a7ca6964884cbadf27af8b8891d68520e939c4`; the new branch was created without force updates. Fetched published head/tree match the expected values. [Initial run 37080636631](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37080636631) entered Maven PR Tests. This documentation-only publication record follows the content head above. Inspect the successor final-head run and record its outcome in the PR description. The publication-authorization requirement is satisfied; earlier requests are historical. Remaining: exact final-head CI, D01 installed-build/layout evidence, owner desktop acceptance and merge. S3 remains VERIFYING; S4 is not started.


## 25. P24-S3 accepted merge and P24-S4 readiness — 2026-10-02 (America/Denver)

Owner accepted and merged PR #358. GitHub confirms merge `e5570f2d413df853a684f93db8a59616dad53321`, fetched as current main. Final PR head `1afd706d5b3600337bf828162c6a8d93c14f6ceb` has tree `b1a3f1c40baac755db8505b92181dd979c585044`, matching local publication-record commit `07574fef97dc7f7360c48e92796ffe2dd08db4a5`. [Maven PR Tests run 37080733410](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37080733410), job `111080791303`, completed successfully: both full passes 843 tests, zero failures/errors, 36 headless skips; Xvfb production JavaFX pass 16 tests, zero failures/errors/skips. Logs were inspected and PR #358 updated with the actual results. Initial superseded run 37080636631 was cancelled. Earlier pending publication, CI and acceptance records are historical.

P24-S3 is DONE. P24-S4 lookup maintenance is the first unblocked slice and is READY, not started. This documentation-only closeout is committed locally on fresh `codex/P24-S3-closeout` from the merge, without reusing the merged implementation branch. No closeout PR is open. On the next implementation run, carry this closeout ledger forward, read the P24-S4 contract and relevant authorities, and select one master-data family before implementation. No known local or GitHub test failure remains; owner acceptance is recorded without inventing additional desktop evidence.


## 26. P24-S4A Budget Category maintenance — 2026-10-02 (America/Denver)

Selected the category family under the adopted P24-S4 contract; Payee/Counterparty and Merchant are separate S4B/S4C slices. Fresh branch `codex/P24-S4A-budget-category-maintenance` starts at current main `e5570f2d413df853a684f93db8a59616dad53321` and carries S3 closeout `80bb126`. No PR. Baseline compile and three existing BudgetCategory service/authorization tests passed.

Inspected: BudgetCategory/alias model, V45/V50/V61 relationships, BudgetCategoryAdminService/LookupService, authorization wiring, canonical Budget and Journal consumers, Activity maintenance conventions, panel/navigation/command routing and UI rules. Existing code-keyed upsert cannot serve a stable-ID editor and there is no category maintenance route. Add audited stable-ID create/edit with company lock, case-insensitive duplicate detection, date validation and existing write authorization. Retain categories rather than hard-delete: UI explains deactivation preserves Journal, Budget, alias and interchange history. Existing import/upsert compatibility seams remain available; no schema or interchange-policy change. Journal and Inventory gain maintenance navigation and preserve drafts on choice refresh. Existing date fields are maintained, with optional bounds and required from <= to.

Donor `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180`: BudgetEditorPanel uses editable string account/fund rows and BudgetWorkspaceStore, with no suitable category master editor. Retain its explicit refresh intent but use existing H2 category IDs, production table/divider state and service authorization; no donor persistence import. See [Budget model](accounting/budget-model.md) for maintenance policy.

### P24-S4A implementation and local verification

Implemented on `codex/P24-S4A-budget-category-maintenance`:

- added immutable `BudgetCategoryCommand`/`BudgetCategoryView` projections and a stable-ID `BudgetCategoryAdminService.save(...)` path with company locking, ownership checks, case-insensitive duplicate-code validation, effective-date validation, and same-transaction create/update audit events;
- added the production `BudgetCategoriesPanel` with searchable active/inactive table, New/Edit/Save/Refresh behavior, company date formatting, draft-preserving refresh, deactivation guidance instead of physical Delete, write-permission gating, and Journal/Inventory return navigation;
- routed the panel through `AppPanelId`, `PanelFactory`, Planning navigation, Journal and Inventory maintenance actions, and the production command-capability contract; the existing compatibility `upsert(...)` and SCLX import seam remain unchanged;
- added service authorization/audit regression coverage, a JavaFX panel behavior/layout test, and the panel to the existing CI Xvfb selection; updated the budget model, interface matrix and manual acceptance checklist.

Validation completed locally: all production Java sources and all test sources compile with the cached Java 17 compiler (`--release 17`, no annotation processing); the focused `BudgetCategoryAuthorizationIntegrationTest` passed 2/2 tests; `git diff --check` passed. The focused JavaFX capability test had 3 successful tests and 3 display-dependent aborts; the new `BudgetCategoriesPanelTest` was display-dependent and aborted because this runtime has no `DISPLAY`/Xvfb. Maven is not installed in this runtime, so `mvn clean verify` and the full CI test gate remain pending; no native installed-build visual acceptance is claimed.

Current handoff: implementation is locally ready but not published. Implementation commit: `0458d48cef80cdc686087237cf932f4c5e906d78` (`feat(P24-S4A): add budget category maintenance`). Remaining gates are explicit owner publication authorization, draft PR/final-head GitHub checks, and owner desktop acceptance. Do not mark S4A or P24-S4 DONE until the change is merged into current `main` and those gates are satisfied.

### P24-S4A authorized publication — 2026-10-03 (America/Denver)

Owner explicitly authorized publication. Draft [PR #359](https://github.com/benbaron/sca-jakarta-h2/pull/359) is open from `codex/P24-S4A-budget-category-maintenance` to `main`. Connected GitHub publication reproduced the reviewed sequence with matching trees:

- Local `80bb1261726a55cc14304614377260ba90c2c5e2` → remote `9879d55683c9848a75f284f43cff9ee30f14ca19`, tree `f2ba36834c5a6fd045af4ae7a50143fc1321a0e7`.
- Local `0458d48cef80cdc686087237cf932f4c5e906d78` → remote `14af1c2e173a8c425b550e3683ff5e1602f23db4`, tree `1b28c900e5aa10d1682296720c8cc88cff30d4c5`.
- Local `abe69455bf4882a74542bf9c71d3664b38fe23b1` → remote `1b32e410aa83cd80f6f1527126782f98ac9dbaa2`, tree `9b252c80d4d20a1628c3964140bf80d6b431a044`.

Remote main remained `e5570f2d413df853a684f93db8a59616dad53321`; the branch was created without force updates. The draft PR remains open and unmerged. Inspect its successor final-head CI and complete the owner desktop acceptance in `doc/P24-S4A-user-testing.md`. Do not mark S4A or P24-S4 DONE until the change is merged into current `main` and those gates are satisfied.


## 27. P24-S4A merge reconciliation and P24-S4B selection — 2026-10-06 (America/Denver)

Owner selected S4B with “ok. proceed” after the repository rescan. PR #359 was merged by the owner on 2026-10-02 (America/Denver) at `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`, now current main. Final-head Maven PR Tests run [37092325277](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37092325277) completed successfully for `3d72b08e7e15e798d2d103055801c5becc44109a`. No PR remains open. The previous VERIFYING controller was stale. Owner merge and subsequent selection are the acceptance/advancement evidence; no additional desktop evidence is invented. S4A is DONE; S4B begins on fresh `codex/P24-S4B-counterparty-maintenance` from that main. S4C stays BLOCKED.

Inspected Counterparty mapping/kind/portable identity, company ownership, TransactionEntryService and reference-data service, Journal refresh, SCLX parties, Budget Category service/panel conventions, routing, authorization, and UI rules. Baseline production compilation passed with Java 17 using cached dependencies. Maven executable is unavailable; full Maven verification is not yet claimed.

Design: keep Counterparty as the sole Payee authority. Add company-scoped immutable maintenance projections and audited stable-ID create/edit, company locking and case-insensitive name duplicate checks, contact field bounds, active/inactive retention, production maintenance navigation and draft-preserving Journal choice refresh. Existing SCLX identities and import behavior stay authoritative. No schema or ledger change. Retain all parties instead of hard deletion, with a visible deactivation/history explanation. Merchant remains S4C. Governing maintenance contract: [Counterparty maintenance](accounting/counterparty-maintenance.md).

Donor review: `NonprofitAccounting` at `c697630ec1f784ebe8338d7300da6c9ac801b180` uses `CounterpartySyncAdapter` to merge/delete parties by name/kind from legacy Person/Donor records. That creates a second authority and can delete history, so it is not imported. Production Counterparty stable/portable IDs and company ownership remain the sole authority. UI refresh intent follows the established production Journal mechanism.


### P24-S4B implementation and verification handoff

Implemented audited company-scoped CounterpartyAdminService, immutable command/view, name/contact/kind validation and serialized duplicate checks. Added Payees / Counterparties maintenance with search/sort, New/Edit Selected/Save/Refresh, contact/lifecycle fields, discard protection, visible history-retention explanation, resizable scrolling regions, company table/divider state, tooltips and permission gates. Wired Accounting navigation, PanelFactory/global commands, UiServiceRegistry and Journal maintenance/return refresh. No migration or interchange source changes. Added service, concurrent duplicate, identity/restart, authorization/ownership, GUI draft/geometry and Journal choice-refresh regressions; both new GUI cases are in the CI Xvfb gate.

Actual verification:

- Current main baseline production compilation passed with Java 17; final production and test-source compilation also passed using cached dependencies and the installed Java compiler module. Existing ReportLibrary varargs warnings remain unchanged.
- JUnit launcher focused verification: **35 started, 30 successful, zero failures, five display-dependent aborts**. Includes CounterpartyAdminServiceTest (four successful), TransactionEntryServiceTest, SclxLifecycleRoundTripTest, SclxPortableIdentityTest and command capability tests. Three capability GUI tests and both CounterpartiesPanelTest cases require a display and were not executed here. No desktop or Xvfb pass is claimed.
- First new ownership assertion expected IllegalArgumentException; the established ownership guard returns CompanyOwnershipException. Corrected the assertion to the existing contract; subsequent tests passed. No production ownership behavior was changed.
- `mvn clean verify` exited 127 because Maven is unavailable. Attempt to obtain Maven from Maven Central timed out under this runtime's network restrictions. Full Maven, exact published-head GitHub CI and native owner acceptance remain pending.
- Final diff review, documentation-link validation and `git diff --check` passed. Archived proposal unchanged.

Status: VERIFYING, local branch `codex/P24-S4B-counterparty-maintenance`, based on main `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`; no PR or GitHub S4B result. User testing: [P24-S4B](P24-S4B-user-testing.md). Next: after explicit publication authorization under AGENTS §5 Step 6, confirm local/remote state, publish the reviewed sequence without force, create a draft PR, inspect exact-head Maven/Xvfb checks and fix failures. Owner desktop acceptance and merge remain required. S4C remains BLOCKED.

Reviewed implementation commit: `5dc0b20a501292ad5542bc6a05045774c3de2c94`. This documentation-only successor records its head for publication; `git rev-parse HEAD` identifies the final local tip. Both commits must be published in order with matching trees when using connected-service transport.


### P24-S4B authorized publication — 2026-10-06 (America/Denver)

Owner explicitly authorized publication. Draft [PR #360](https://github.com/benbaron/sca-jakarta-h2/pull/360) is open from `codex/P24-S4B-counterparty-maintenance` to main. Current main remained `9eeb4c239630a721d3c99ec93b49bfbb9f7b6dd3`. Connected publication preserved the reviewed commit messages, order and exact trees:

- Local `5dc0b20a501292ad5542bc6a05045774c3de2c94` → remote `8a1aeb9d5887b1167ee04e5b50bb6ee8e3b1c8a5`; tree `22653cf3255637e4a9c8ba52d0c8e889c20e00f1`.
- Local `03fd833bbda8b6241855be53fe8e4b0b65372211` → remote `48ddd15910866304f700caa10181fe3786d556e5`; tree `20862bcb69519bc7e500b6d80aa3587b4062627c`.

The new branch was created at the verified publication head, without force updates. This documentation-only successor records PR/authorization; it requires final-head CI. At publication, CI and both display-dependent CounterpartiesPanelTest cases remain pending. Inspect Maven PR Tests for the final PR head, repair failures, and record the exact workflow evidence in the PR. Owner desktop acceptance using P24-S4B-user-testing.md remains required. S4B is VERIFYING, unmerged; S4C remains BLOCKED. No S4B GitHub pass or native desktop result is claimed by this snapshot.


### P24-S4B final-head GitHub verification

Final published head `8c4b7b6c2b98d0024b48afb426456fb4289d6852` passed [Maven PR Tests run 37560761479](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37560761479), job `112597449168`. Both full passes: 851 tests, zero failures/errors, 39 headless skips. Production Xvfb gate: 19 tests, zero failures/errors/skips, including both CounterpartiesPanelTest cases. Clean verify, repeat tests, production JavaFX route compliance and cleanup all succeeded. Source publication ledger `a04daad5982a3f209b6e09756cb913a23fbf7ec8` maps to remote final head with identical tree `1e5393072bc3823050a5f90bb20c642a0d8f5806`.

PR #360 records the final result and owner testing link. This post-CI documentation handoff is local only, deliberately not published to avoid replacing the verified PR head with an unverified successor. No implementation change followed the successful CI head. Status remains VERIFYING; owner native desktop acceptance and separate merge authorization remain required. S4C stays BLOCKED. Preserve this closeout evidence when continuing from the merged PR or publishing a later documentation successor.


### P24-S4B owner merge and P24-S4C readiness — 2026-10-06 (America/Denver)

While final CI evidence was being recorded, owner `benbaron` merged PR #360 at `e60c8211094548f36a5df1d4ee76db74c9c9d580` (2026-10-07 02:19:43 UTC / 2026-10-06 20:19:43 America/Denver). Current origin/main was fetched and confirmed at that merge. Final PR-head CI passed as recorded above. Owner merge is the acceptance/advancement evidence; no separate desktop evidence is invented. S4B is DONE. S4C Merchant maintenance is the first unblocked successor, READY and not started; P24-S4 remains IN_PROGRESS.

This documentation-only closeout is local on fresh `codex/P24-S4B-closeout` based on the confirmed merge, with the local final-CI evidence cherry-picked as `ac2accb`. The merged implementation branch is not reused. No closeout PR or S4C implementation exists. Next: when the owner selects S4C, start a fresh branch from current main, carry this closeout ledger forward, inspect Merchant authority and consumers, and implement that one family. Diff whitespace checks passed; no application changes or repeated application tests were required for closeout.


## 28. P24-S4C Merchant maintenance — 2026-10-06 (America/Denver)

Owner selected S4C with “proceed.” Current main is `e60c8211094548f36a5df1d4ee76db74c9c9d580`, the confirmed owner merge of PR #360 after final-head CI passed. Fresh branch `codex/P24-S4C-merchant-maintenance` carries the local S4B final-CI/closeout records as `850d0b5` and `11dd7e6`. No PR or Merchant implementation existed at selection.

Inspected Merchant, TxnSplit, V1/V61/V63 migrations, ownership/reference-data/transaction/correction consumers, SCLX parties, Counterparty maintenance service/UI/tests, factory/navigation/global commands, adopted G8/A08 contract and UI rules. Merchant name is company-unique in the existing schema, portable identity is durable, and no merchant maintenance service or route exists. Baseline production compilation passed using Java 17 and cached dependencies; Maven remains unavailable.

Design: add an audited stable-ID MerchantAdminService and immutable command/view using the existing Merchant master. Company lock serializes case-insensitive trimmed-name duplicate checks; authorization/ownership and the existing database unique constraint remain enforced. Name and notes plus active/inactive lifecycle are the entire mapped family; no unsupported contact or settlement fields. Retain records instead of physical deletion with a visible history explanation. New/Edit/Save/Refresh/search maintenance uses existing production layout/state conventions. Journal navigation refreshes active choices without discarding draft lines, including mixed merchants. No migration, alternate master, or SCLX change; settlement assistance stays P26-S6. Governing contract: [Merchant maintenance](accounting/merchant-maintenance.md).

Donor review at `NonprofitAccounting` `c697630ec1f784ebe8338d7300da6c9ac801b180`: legacy outstanding/undeposited records use free-text `from_to_card_merchant`; no suitable company-owned Merchant master editor was found in the inspected persistence/UI paths. Do not port name-based persistence. Reuse the established production stable-ID maintenance/refresh approach instead.

Narrow S4C prerequisite repair: Journal previously projected Merchant ID without its name when reopening a transaction, making inactive historical assignments blank. TransactionView now carries the current Merchant name; reference refresh carries company-owned retained Merchant labels separately from active choices. Resolve existing selections by ID against those labels, including a rename and deactivation in one save, without offering inactive merchants for new selection or changing mixed line assignments. Existing constructors remain compatible. Regression coverage belongs to this slice.


### P24-S4C local implementation and verification handoff

Implemented company-owned MerchantAdminService, immutable command/view, atomic authenticated creation/update audit, bounded trimmed-name validation, duplicate-name checks including inactive records, exact-ID ownership checks and company-serialized concurrent creation. The existing schema and portable identities are retained. MerchantsPanel provides New/Edit/Save/Refresh, name/notes search, active lifecycle, draft protection, permissions, table-state binding, remembered dividers, overflow scrolling and hover tooltips. Accounting navigation, production factory/service bundle, global commands and Journal maintenance/return refresh are wired. No later settlement work is included.

The directly blocking historical-label defect is repaired narrowly: TransactionView.Line carries Merchant name, and Journal reference refresh resolves current retained labels by stable ID separately from active new-choice lists. Mixed line assignments and draft dirty state remain intact. Existing projection constructors are compatible. Service regression covers restart, mixed canonical balanced lines, inactive names, reversal, and portable SCLX master/line-link round trip. No SCLX format/policy change or migration. Five Merchant service tests and two JavaFX tests are added; both JavaFX cases are included in the CI Xvfb gate.

Actual local validation: Java 17 compilation of all production and test sources passed using cached dependency jars (two pre-existing ReportLibraryPanel varargs warnings). Initial focused run: 38 tests found, 33 passed, five aborted for unavailable DISPLAY, zero failures. Broader ownership/correction/authorization/SCLX/source-policy regression run: 78 found, 75 passed, three display-dependent tests aborted, zero failures. These runs overlap and are not unique-test totals. The fifth Merchant service test deliberately rejects the audit insert after Merchant flush and proves the entire update rolls back. After final naming/comment and UI stress-assertion refinements, all sources compiled again. Native JavaFX assertions remain unexecuted here. `mvn clean verify` was attempted and exited 127 because Maven is not installed. This is not a Maven or desktop visual pass. No known application failure from executed checks; full Maven, Xvfb and owner desktop acceptance are outstanding. Final diff whitespace check passed.

Documentation: [Merchant maintenance](accounting/merchant-maintenance.md), [owner acceptance](P24-S4C-user-testing.md), interface-operation matrix and editor guidelines updated alongside this plan. Branch `codex/P24-S4C-merchant-maintenance`; no PR or remote publication yet. S4C remains VERIFYING; P25-S1 remains BLOCKED until P24-S4 is accepted and merged. Next exact action: after explicit owner publication authorization under AGENTS.md, reproduce the reviewed local commit sequence through authenticated Git/GitHub transport, confirm matching tree hashes and remote/PR head, create a draft PR, run/inspect final-head Maven PR Tests including MerchantsPanelTest under Xvfb, correct any failure, and update the PR/plan with actual CI evidence. Owner desktop acceptance and separate merge authorization follow.

Reviewed implementation/source head `8c5b5a36b163f5946def133b1b4a52cbe5eaee90`, tree `7015e61f39939f8429d8c857cc66f324e532290e`. The following documentation-only handoff commit records this source head; resolve `git rev-parse HEAD` for the publication tip. There have been no application changes after this verified source head.


### P24-S4C authorized draft PR publication — 2026-10-07

Owner explicitly authorized all three requested actions: publish branch, open draft PR and verify GitHub CI. Current remote main remains `e60c8211094548f36a5df1d4ee76db74c9c9d580`; the fresh S4C branch was absent and the local worktree was clean before publication. Published the reviewed sequence without force-updating, with every tree matching its corresponding local commit. Draft [PR #361](https://github.com/benbaron/sca-jakarta-h2/pull/361) targets main; verified initial branch and PR head `849bcf95a3af399e51d13126baf5332d3a18fa7f`.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `850d0b54985bf8e5074042975f282fd2ac2a760e` | `16082457f058a2e215f7dae86dd7ef079e15fff4` | `9f33a7ac2352e96cbe496d0711483244356f35f8` |
| `11dd7e6eb75df7061d4b92eae422c40e3a3ce4bf` | `855d1f6440631662d95c7a204c0e2835be8a12cc` | `4aa540d3bcd4b80b454dfcf31fd5941ea1ebe6d7` |
| `8c5b5a36b163f5946def133b1b4a52cbe5eaee90` | `b9737623430b7a084bcc86ad81573735d5e3dc9d` | `7015e61f39939f8429d8c857cc66f324e532290e` |
| `5c0f3497e18411fb8b14d08ead7a5e9e90561f3a` | `849bcf95a3af399e51d13126baf5332d3a18fa7f` | `994771c8cf9c725f41bfd467ca63eb954ed34c49` |

This documentation-only publication ledger follows the reviewed implementation and will be fast-forwarded under the same authorization; active_head records its known parent because a commit cannot embed its own hash. Verify the resulting branch/PR tip and run CI against that final published head. Full Maven, Xvfb and owner desktop results are still pending at this checkpoint; S4C is VERIFYING, not DONE. After final CI, record evidence locally and in the PR body without publishing another evidence-only successor that would replace the verified head. No application changes were introduced by this publication ledger.


### P24-S4C first final-head CI and narrow corrections

Published publication-ledger head `a4386001b443e61446959c19f414309f9da704fc` (local `b170f02b770738b41fc16436aa5458f88dd5f978`, matching tree `d6f8ec05a555a1ff60a62280fd234c2aea886eaf`) was confirmed on branch and draft PR #361. [Run 37628321930](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37628321930), job `112815990896`, passed both full Maven runs: 858 tests each, zero failures/errors, 41 headless skips. Xvfb ran 21 tests with two errors, both MerchantsPanelTest assertions. No final CI success is claimed.

The Merchant-only assignment regression exposed a missing Merchant property dirty listener in Journal; add that narrow listener beside the existing Fund/Event listeners so assignment changes are protected as unsaved drafts. The refresh callback already suppresses loading changes and restores the previous dirty state. The overflow stress test also used a fixed 850-pixel minimum immediately after requesting a narrower Stage, before the window resize/layout pulse had completed. Size the stress content relative to the actual viewport and await real width/height overflow plus both visible scrollbars before asserting; preserve all geometry requirements rather than weakening them. Explicitly assert Merchant-only dirty state before other draft changes. These two corrections are within the authorized S4C publication/verification scope. Local Maven and DISPLAY remain unavailable. The dependency cache was subsequently rediscovered (145 cached jars); all corrected production and test sources compiled locally under Java 17, with the same two pre-existing ReportLibraryPanel warnings. Native display validation runs in CI. Whitespace checks passed; final-head CI and owner desktop acceptance remain pending.


### P24-S4C successful final-head CI — 2026-10-07

Final published correction head `6ebaea8ec851ff3bed65dbbcd07851873d174bec` passed [Maven PR Tests run 37629410014](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37629410014), job `112819446148`. Both full runs: **858 tests, zero failures/errors, 41 headless skips**. Production JavaFX/Xvfb gate: **21 tests, zero failures/errors/skips**, including both MerchantsPanelTest cases. All five Merchant service cases passed in both full runs. Clean verify, repeat tests, display compliance and job cleanup succeeded. Run head, branch ref and PR #361 head were confirmed identical after CI; draft PR is open, mergeable and unmerged. No application failure remains from executed gates. Native owner desktop testing is still required; Xvfb is not owner acceptance.

Correction source commit `106e890c6876dae2ec4fc911429af2e9f6af7ba4` maps to final published head with matching tree `a327f9f8fa15bc31519f8aafc709ed0fc7a24941`. Together with the four original commits and publication ledger recorded above, all six published commits retain reviewed messages/order and matching trees, with only expected-head fast-forward ref updates. PR description records actual final-head results and the initial failure/correction rationale.

This post-CI evidence commit is **local only**, deliberately leaving the successful published head unchanged. No application change follows the verified source head. The worktree is clean after recording this handoff; `git rev-parse HEAD` identifies the documentation tip. Carry this evidence forward from confirmed main after owner merge. S4C remains VERIFYING, P24-S4 remains IN_PROGRESS, and P25-S1 stays BLOCKED. Next exact action: owner follows [acceptance notes](P24-S4C-user-testing.md), then separately authorizes/merges PR #361. On reported merge, fetch current main, verify PR merge and final CI head, close S4C/P24-S4, and select the first unblocked dependent slice. Do not merge or advance without that evidence.


## 29. P24 completion and P25-S1 readiness — 2026-10-07 (America/Denver)

Owner reported “tested and merged.” GitHub independently confirms owner `benbaron` merged PR #361 at `a9275ad1b9a970a084829d5659d792375ed196fb` on 2026-10-07 23:34:25 UTC / 17:34:25 America/Denver. Fetched origin/main and confirmed that merge as current main. PR final head `6ebaea8ec851ff3bed65dbbcd07851873d174bec` matches successful Maven PR Tests run `37629410014`: both full runs 858 tests, zero failures/errors, 41 headless skips; Xvfb 21 tests, zero failures/errors/skips. Owner testing statement supplies desktop acceptance; no additional device/scaling evidence is invented.

P24-S4C and the Budget Category/Payee/Merchant P24-S4 family are DONE. All P24 slices are accepted and merged, so P24 is DONE. Clear obsolete active branch/PR/head fields and select P25-S1 as READY, not started. Its adopted contract is [internal fund transfers](P23-P28-runbook-correction-program.md#p25-s1--implement-one-internal-fund-transfer-operation-g7): one atomic operation linking canonical balanced accounting and existing FundTransfer reporting, with restriction, closed-period, duplicate/failure and reversal protections. A07 distinguishes internal reallocation, bank movement and inter-entity payment. D03 remains unresolved before legal-entity mappings; readiness for internal transfers does not resolve that policy or authorize external mapping implementation. P25-S2 and later slices stay BLOCKED.

This documentation-only closeout is local on fresh `codex/P24-closeout`, based on the confirmed merge; the merged implementation branch is not reused. Preserved the local final-CI evidence commit `76f8c2d` by cherry-picking it as `165446e`. No closeout PR or P25 implementation branch exists. Validation: current-main ancestry, PR merge/final-head CI confirmation, scoped governing/acceptance dependency review and diff whitespace checks; no application changes or repeated application tests. Next exact action when the owner resumes execution: fetch current main, create a focused P25-S1 branch, carry this closeout forward, read the adopted G7/A07 and D03 contracts, inspect FundTransfer/FundAdminService/FundsPanel, canonical transaction/correction/ownership/authorization services, bank-transfer authority, semantic transfer report and relevant migrations/tests, then establish baseline before design.


## 30. P25-S1 internal fund transfers — selected 2026-10-07

Owner selected P25-S1 with “Ok, proceed.” Fetched and confirmed main `a9275ad1b9a970a084829d5659d792375ed196fb`. Fresh `codex/P25-S1-internal-fund-transfers` carries P24 evidence/closeout as `82ce8a4` and `8cc7a71`. Inspected the existing FundTransfer master and report, Fund maintenance/ownership, canonical entry/correction caller-owned transactions, period/reconciliation protections, SCLX coverage, V1 and current migration history, G7/A07/D03 and production UI rules. Existing FundBalanceService sums natural-signed amounts of all account types without company scope; it is not a sound net-assets availability authority and will not be used for transfer policy.

Design and governing contract: [Internal fund transfers](funds/internal-fund-transfers.md). One atomic service operation creates existing FundTransfer and canonical Txn facts, with audit and UUID request idempotency. Four lines reallocate a selected ordinary non-bank ASSET allocation account and EQUITY net-assets account between funds; bank, income and expense company totals remain unchanged and each fund's balance sheet stays balanced. Accounts are explicitly selected from current active chart; no automatic accounting data. Both funds and their parent hierarchy must be active/effective, unrestricted or designated and free of restriction text. Ledger-defined source net assets must cover the amount. D03 remains a gate on external/legal-entity mappings, which are excluded.

Generic Journal edits/deletes of linked transfers must fail with a transfer-workflow explanation; reversal preserves original operational facts and creates the opposite-direction dated transfer fact atomically, including generic Journal reversals. No replacement shortcut may produce an unlinked transfer. New migration adds nullable request identity/hash for legacy compatibility, unique new request identity, and indexes; existing FundTransfer status remains storage vocabulary, not a new visible posting workflow. SCLX already omits FundTransfer facts: this slice must verify portable canonical line/reversal effects and visibly disclose the existing operational-link portability limit; full operational-field coverage remains P27-S4.

Donor reviewed at `c697630ec1f784ebe8338d7300da6c9ac801b180`: FundTransferPostingService supplies four-line source/destination accounting but uses separate posting/lifecycle commits and alternate JDBC ledger; adapt four-line intent with one production JPA transaction, not donor persistence/status queues. Legacy FundsPanelFX uses name-based transaction metadata; retain stable IDs instead.


### P25-S1 local implementation and verification — 2026-10-08

Implemented company-scoped FundTransferService and immutable commands/views, UUID request/hash migration V78, four-line canonical accounting and atomic audit facts. Added source net-assets checks through the transfer date and every future recorded day, company-row serialization and exact-request restart/concurrency handling. Generic Journal entry update/direct edit/delete/reverse-and-replace reject linked transfers; both public and caller-owned Journal reversals create inverse operational history with a required reason, preserving dated facts and historical retirement corrections. New-transfer restrictions do not prevent historical correction.

Added the production FUND_TRANSFERS route, Funds action, service registry authorization wiring and navigation. Entry, latest-500 history, New/Save/Refresh, dated reasoned reversal and transaction-ID Journal navigation are real operations. Saved drafts lock; New intentionally discards unsaved changes, while refresh/history selection preserve drafts. Company date/money formatting, typed sortable columns, saved table/divider preferences, independent scrolling and live write-permission gates are integrated. Visible retention and SCLX portability explanations replace deletion. Export inventory warns about deferred operational transfer links while existing canonical ledger/reversal export/import is verified. D03 and later-slice gates remain unchanged.

Documents: [governing transfer contract](funds/internal-fund-transfers.md), [desktop acceptance notes](P25-S1-user-testing.md), interface operation matrix, editor guidelines, transaction lifecycle and SCLX specification. Tests cover accounting/report reconciliation, restart and concurrent retries/overdraw, future-date availability, restrictions/ancestors/ownership/accounts/authorization/periods, generic corrections, reversal chains and audit-failure atomic rollback. V77-to-V78 in-memory upgrade and untracked-history recovery preserve legacy facts/request uniqueness. The Xvfb CI list includes real FundTransfersPanel service/layout tests.

Local baseline/final Maven commands are unavailable (`mvn: command not found`, exit 127); baseline and final all-main/all-test Java 17 compilation succeeds with the same two existing ReportLibraryPanel varargs warnings. A custom JUnit Platform 1.10.3 launcher uses cached project dependencies, with older cached Platform 1.9.3 jars excluded. Final focused regression: 18 tests started/successful, zero failures/aborts. Broad regression initially exposed replay failures in untracked-schema recovery; unapplied V78 now follows existing replay-safe migration conventions, and the recovery regression passes. A subsequent broad run hit only stale checksums in the earlier test-created default database; the final broad run uses an isolated JVM user.home and leaves that database untouched. Final isolated broad regression completed: 904 tests discovered, 873 started, 830 successful, 43 aborted because the graphical toolkit/display is unavailable, zero failures; 12 headless containers aborted and their 31 tests did not start. This custom-launcher evidence is not a Maven or Xvfb success claim. No GitHub publication or CI is claimed; owner desktop testing is pending.


**Final local handoff:** P25-S1 VERIFYING on `codex/P25-S1-internal-fund-transfers`; implementation/verified code head `0103def5449d69b6a527375fbad9ab6188b81981`, based on merged main `a9275ad1b9a970a084829d5659d792375ed196fb`. Local sequence includes P24 evidence/closeout `82ce8a4`, `8cc7a71`, the implementation commit, then this documentation-only evidence commit (resolve final branch tip with `git rev-parse HEAD`; `active_head` identifies the verified implementation). PR is null/unpublished. The final tree/whitespace review is clean and intentional. Local application compilation and focused/broad JUnit runs pass; no unresolved functional failures from the completed local runs. `mvn clean verify` remains unavailable, graphical tests/owner desktop acceptance remain pending, and GitHub has not run this branch.

Remaining exact action after explicit owner publication authorization: confirm current remote main and expected branch state, publish the complete reviewed local sequence through authenticated Git or the connected GitHub service without force updates, compare every remote tree to its local tree, create a draft P25-S1 PR, confirm its actual head and inspect the repository Maven/headless/Xvfb workflow. Repair any failures in this slice and record actual CI evidence; use [P25-S1 desktop acceptance](P25-S1-user-testing.md) for owner testing. Do not mark DONE or activate P25-S2 before confirmed merge and required acceptance. D03 is still unresolved for legal-entity mappings; no later-phase work is authorized by this handoff.


### P25-S1 authorized draft PR publication — 2026-10-07 (America/Denver)

Owner explicitly authorized branch publication, draft PR creation and CI verification. Remote main remained `a9275ad1b9a970a084829d5659d792375ed196fb`, the task branch was absent, and the local worktree was clean before publication. Published the complete reviewed local sequence with original messages and identical per-commit trees, then created the new branch at the publication head without force updates. Draft [PR #362](https://github.com/benbaron/sca-jakarta-h2/pull/362) targets main; verified initial branch/PR head `8b507cf89b9dc172ceae138980f15c4b5d334a52`.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `82ce8a48146708f5559fc5969c1b003ad830b4f2` | `4be52df7e347983c9f99acba3ea2206f4669d24c` | `e2c0daff2d1f13fcde04aa71f7057336901dbb2c` |
| `8cc7a71fb48a9227be3f12fdfe9fc6aabc9a4211` | `3402a425707ff3b294377b01689123769b5b4453` | `09b62524f9b4c8607f08ac4fbe545a12fc04ce60` |
| `0103def5449d69b6a527375fbad9ab6188b81981` | `571fb6a1a413087071ad892f2eff4a962e376414` | `e7be9e049ab684043872c68502e14007ecd8ef45` |
| `af23712447c1ed66fb2f5b81f6d78bfb9a572317` | `8b507cf89b9dc172ceae138980f15c4b5d334a52` | `55083f0c28690f092292b1c34643b735d0de0ec9` |

This publication-ledger successor is documentation only and will be fast-forwarded under the same owner authorization. active_head records its known published parent because a commit cannot embed its own SHA. Verify the resulting branch and PR tip and inspect CI for that actual final head; no Maven/Xvfb or desktop pass is claimed at this checkpoint. P25-S1 remains VERIFYING and P25-S2 remains BLOCKED. After final CI, record evidence locally and in the PR body without replacing the verified head with another evidence-only publication. Owner desktop acceptance remains required via [testing notes](P25-S1-user-testing.md).


### P25-S1 first final-head CI and compile correction

Published local publication-ledger `5011f0bd33b4ec49712aaaae07698a9726618b0f` as `3c7623a092b453aa585c3a25ff4f3228ff549625`, with matching tree `5bed2504b9dff2ed5c9af0fb81e1bca035534b1f`; fetched and verified the final branch and PR head. [Maven PR Tests run 37713786924](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37713786924), job `113105502284`, failed during main compilation before tests. Maven source lookup interpreted the panel's wildcard layout import against the existing package-local `GridPane.java` compatibility file, which declares GridPaneAlias. The earlier explicit-all-files local javac invocation did not exercise Maven's implicit source lookup. Repeat tests and Xvfb were skipped; no CI pass is claimed.

Narrow correction: replace the new FundTransfersPanel wildcard imports with explicit JavaFX control/layout imports, including javafx.scene.layout.GridPane. Preserve the existing compatibility file and all accounting/UI behavior. Final local Maven remains unavailable and the earlier container's cached dependencies are absent in this resumed runtime; current validation is import/diff review, with fresh actual-head Maven/Xvfb CI required. Publish the correction under the owner's existing authorization, verify matching tree/head with an expected-parent non-force update, inspect the replacement workflow and resolve any remaining failure before owner desktop acceptance. No later-phase feature or merge is included.


### P25-S1 explicit import completion

The import correction local `74aa82ec3d7600dec99a54fdfecca511d5fc95c3` published as `81fde3393c3e7315cd7e7b718736bff9e9def8ca`, matching tree `1ecf3ee52422a8a78b25c5bfebb60a8f61f98158`, was verified on branch/PR. [Run 37714081173](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37714081173), job `113106439364`, confirmed the GridPane source-lookup error was removed but main compilation found the omitted javafx.scene.control.Control import for the gated input array. Add that explicit import; no behavior or tests change. Full actual-head CI remains required; tests/Xvfb were skipped in this run.


### P25-S1 first complete Maven passes and Xvfb geometry correction

Import completion local `b336ecb6d135a994cacc9d1682712eb405454478` published as `170dcecdd854d505e944f06c5e69b4c1b88b9cd2`, matching tree `fe2dc19066ad84ea9bd98f8b81fdc38f80effa88`, with branch/PR head verified. [Run 37714257844](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37714257844), job `113106999871`: clean headless Maven verify and repeat tests both passed, each 873 tests with zero failures/errors and 43 headless skips. Production Xvfb ran 23 tests: 22 passed, one failed, no errors/skips. The real transfer save/retry/draft/Journal/reversal/permission test passed; scaled transfer-editor two-axis overflow timed out.

Narrow geometry correction: ordinary form Labels could compress below their preferred text width, leaving no horizontal overflow at the test's narrow/scaled viewport. Retain each field label's natural preferred minimum width while allowing the surrounding panel and ScrollPane to shrink. Existing selector minimum/preferred widths and independent dividers remain; real horizontal scrolling now exposes full labels and controls. Add actual content/viewport/minimum/preferred widths and scrollbar visibility to the geometry test's timeout diagnostic. This preserves the two-axis assertion instead of widening the panel or weakening the test. Publish and rerun final-head full Maven/Xvfb checks; native owner desktop acceptance remains pending.


### P25-S1 populated history geometry fixture

Label-width correction local `0a8b52c84568d0b876c1cd864bf7bd7d3dc2d685` published as `be6484a9c794760a5ab422c0d3e24116a839f6ce`, matching tree `0ee81c7bd53901d47ffd98dd220bd9f6562f2af6`. [Run 37715012139](https://github.com/benbaron/sca-jakarta-h2/actions/runs/37715012139), job `113109390815`, passed both full Maven runs (873 tests each, zero failures/errors, 43 skips). The editor's real two-axis overflow passed. Xvfb then failed the table horizontal scrollbar assertion because its empty history displays the TableView placeholder instead of the populated virtual flow. The functional transfer test still passed.

Correct the geometry fixture by saving one real transfer through the production UI before resizing. Keep both editor-axis and table-horizontal scrollbar assertions, company preference persistence and divider checks. No production behavior changes in this correction. Publish with matching tree and expected-parent non-force update, then verify full actual-head CI. Owner desktop acceptance and merge remain pending; P25-S1 stays VERIFYING.


## 31. P25-S1 closeout and P25-S2 implementation — 2026-10-09

Owner reported merging P25-S1 and explicitly selected S2. GitHub confirms PR #362 merged at `d20c1ba23fa57a7271860c97e5fdff4e7acf2477`; fetched current origin/main matches. Final published source head `164b73922410228ad850cd0530a31be1492bfeb2` passed run 37715654192: both full Maven passes 873 tests, zero failures/errors, 43 headless skips; Xvfb 23 tests, zero failures/errors/skips. Owner's merge is recorded as acceptance of the slice; no independent native visual validation is claimed. Carry forward the local S1 evidence-only commit by this record; do not reuse the merged branch.

Selected P25-S2 on fresh `codex/P25-S2-payment-references`, based on confirmed main. D03 continues to gate legal-entity mapping/NMR and D04 gates P25-S3 stale accounting; neither blocks structured factual references. Governing contract: [Payment references](accounting/payment-references.md). Required inspection completed: Txn/TxnSplit, TransactionCommand/LineCommand/View, TransactionEntryService and correction/reconciliation protection, JournalWorkspacePanel and compliance, bank statement reference model, SCLX export/preview/commit/correction graph, FinancialReportService and typed GL builder, existing transfer/migration/Journal tests. Root UI rules, interface matrix, editor guidelines, lifecycle and period policy read before design.

Donor reference at `c697630ec1f784ebe8338d7300da6c9ac801b180` has check/reference fields in SclxDocument and its import target. Retain textual identity intent; do not import its alternate persistence or ambiguous header-wide references. Production ownership is one optional payment-fact set per bank split. One issued check may allocate bank effects across funds with consistent shared facts and one reservation; distinct payments retain separate identities; no memo inference. Existing canonical company lock, period/reconciliation gates, atomic saves and audit remain authoritative.

Initial `mvn -DskipTests compile` unavailable (executable absent). Recovered Java compiler via `java com.sun.tools.javac.Main`, downloaded Maven 3.9.9 into temporary scratch and repaired missing/corrupt dependency jars in an isolated temporary cache. Offline Maven compile passed. First focused pass: 24 tests, one SCLX unsupported-extension error; corrected both extension support lists. Payment service round-trip run subsequently passed 7/7. Migration/UI/broader checks pending; no final validation claim yet. Branch is not published and has no PR. Next exact action: finish migration/parser/UI tests, run isolated full `mvn clean verify`, inspect final diff and write desktop testing notes before committing the slice.


### P25-S2 final focused verification

Recovered local Maven compile passed without production errors. Updated sources/tests passed the focused Maven run: 18 tests total, 17 succeeded, one display-dependent JournalPaymentReferencesTest skipped, zero failures/errors. Eight payment-service cases cover restart/text identity, same-bank/reference search, mixed bank accounts and EFT repeats, one check allocated across funds with consistent facts, reversal retention and replacement guard, ownership/authorization/closed dates, audit rollback and concurrent issuance, plus SCLX reversal/alphanumeric round-trip and idempotency. Migration upgrade/recovery and parser identity/malformed-data cases pass. The existing GL table projection test now explicitly checks the four new fields. First broad verify exposed only its obsolete column-list expectation; corrected, and the preceding full source head passed 885 tests with 44 headless skips. A later multi-fund instrument improvement is covered in the focused run; complete final-source Maven verification is now running and must finish before claiming final validation.

Required desktop acceptance: [P25-S2 testing notes](P25-S2-user-testing.md). Final JavaFX method cell uses initialization suppression and focus-loss commit; company-formatted date cells use the existing compliance renderer. Production JavaFX CI includes JournalPaymentReferencesTest. Local Xvfb/native display remains unavailable; no owner acceptance is inferred. No publication or PR exists, and S3 remains BLOCKED until S2 merges. D04 stale accounting stays unresolved for S3.


### P25-S2 final local verification and handoff

Final source passed offline `mvn clean verify` using recovered Maven 3.9.9 and an isolated temporary dependency cache/home: **886 tests, zero failures/errors, 44 headless skips**, BUILD SUCCESS on 2026-10-09. This includes the final multi-fund instrument grouping, metadata/outgoing-bank validation, SCLX preview/export/atomic commit, replay-safe V79 migration, audit snapshots, GL projection and production Journal changes. Focused final run: 18 tests, zero failures/errors, one display skip. Only brace/whitespace formatting and documentation follow this verification; no behavior change follows the final source gate. No unresolved functional failure remains from executed tests. GitHub Maven/repeat/Xvfb, the new Journal method-cell/save/draft/permission case and native owner desktop acceptance remain pending.

S2 is VERIFYING, not DONE. Branch `codex/P25-S2-payment-references`, base confirmed current main `d20c1ba23fa57a7271860c97e5fdff4e7acf2477`; no PR or remote publication. Changed documents: this plan, payment-reference accounting contract, SCLX specification, editor guidelines, interface-operation matrix and owner testing notes. Local final diff/whitespace and new documentation links are clean. Generated tracked build manifest is restored to its baseline and excluded from the slice. Next exact action after owner authorization: reproduce the reviewed local commit sequence on a new remote branch without force-updates, compare each tree hash, open a draft PR, verify actual head CI including JournalPaymentReferencesTest under Xvfb, repair failures and record evidence. Owner tests [P25-S2 notes](P25-S2-user-testing.md), then accepts/merges; confirm current main before S3 closeout. D03/D04 and later slices remain gated. Do not reuse the merged S1 branch.


Verified implementation commit `f4acf08fd0f7bbeba8b72e9261e1de1262dea4a0`, tree `f42d2350b41e3844b21d116419e49dbf214b38cc`. This documentation-only successor records that source head (a commit cannot embed its own SHA). Resolve the complete local tip with `git rev-parse HEAD`; publish the implementation and this handoff in reviewed order only after explicit owner authorization. Worktree is clean after handoff; no PR or remote S2 branch has been created.


### P25-S2 authorized draft publication — 2026-10-09 (America/Denver)

Owner explicitly authorized publication, draft PR and CI with “yes, proceed.” Local Git push had no credentials; used the connected GitHub service under AGENTS.md without force updates. Confirmed clean worktree, exact repository and current main `d20c1ba23fa57a7271860c97e5fdff4e7acf2477`, and absence of a remote S2 branch before creating it at that base. Reconstructed changed file contents from verified base blobs and local line deltas, compared all 30 blob writes with local object IDs, and preserved reviewed commit messages/order. No unrelated files were published.

| Local commit | Published commit | Verified matching tree |
|---|---|---|
| `f4acf08fd0f7bbeba8b72e9261e1de1262dea4a0` | `5257953855846b13f5a3154929bfe512c59edf08` | `f42d2350b41e3844b21d116419e49dbf214b38cc` |
| `510f24a7b2f6130e7c492d61749edba61f8d3e0b` | `4e846a6128db3e559d4e8428425f14c8af38901f` | `13eed5e4c777de37255ed8793b281d98cbc960f9` |

Expected-parent fast-forward ref update succeeded. Opened draft [PR #363](https://github.com/benbaron/sca-jakarta-h2/pull/363), head `4e846a6128db3e559d4e8428425f14c8af38901f`; fetched the published branch and independently compared its final tree. PR records local tests, current scope and remaining gates. This publication-ledger successor is documentation only and will be published under the same authorization; active_head identifies the known parent because a commit cannot embed its own SHA. Verify the resulting branch and PR tip before inspecting actual-head CI. S2 stays VERIFYING; no GitHub CI success or native acceptance is claimed yet. Owner testing and separately accepted merge follow final-head CI; S3 remains blocked.

### P25-S2 closeout / P25-S3 selection — 2026-10-10 (America/Denver)

Owner reports tests pass and PR #363 integrated and explicitly selects S3. GitHub confirms #363 merged at `5f6bec4b6c9632a25d23b9d4635fa6fd324649bc`, current origin/main. Final S2 head `bb0cb911c638d687b5364b262884e092fd2aaf46` passed run `38019118784`, including clean/repeat/Xvfb steps. The owner report is acceptance evidence; no additional native test result is invented. S2 is DONE. Carrying forward local post-CI evidence: publication ledger local `122af32e` matched remote final tree `879121fcf07618cfe5530ed2b01565e5a2cce423`; full local tests 886, zero failures/errors, 44 display skips.

S3 begins on fresh `codex/P25-S3-check-exceptions` from confirmed main. D04 gates stale-triggered accounting only: stale review records facts and never extinguishes an obligation or creates income/expense. Linked void/reissue uses canonical correction relationships, with explicit cancellation reason and new instrument identity; outstanding obligation validity requires the operator's accounting decision. No automatic stale void is available. Read AGENTS, adopted S3/A09/D04, payment contract, lifecycle/period policy and all three production UI rules. Inspect canonical entry/correction seams, payment facts, reconciliation matching/protections, Journal, SCLX payment/correction graph and tests. Donor is reference only; retain the production canonical authority. Baseline Maven initially unavailable after environment restart; recover the runtime before claiming a build result.

### P25-S3 implemented check exceptions / validation checkpoint

Implemented [check exception contract](accounting/check-exceptions.md) and [owner acceptance notes](P25-S3-user-testing.md). Canonical atomic single-instrument reissue retains both references, dated original/reversal/replacement links, unchanged allocations and supplemental details. Mixed payments/duplicate references/cleared or matched instruments reject; finalized/completed reconciliation protects both review and correction. Linked payment history cannot be directly edited/deleted. Review writes canonical per-bank-line delivery/evidence/review facts atomically, with audit and company/period/authorization protection; it never changes amounts, obligation or cleared state. Replay-safe V80 and SCLX payment version 2 retain facts, version-1 compatibility and reimport identity. Reconciliation includes reference/correction columns and reachable typed bank-filtered check/exception review with live permissions, draft protection, company formatting/state and independent scrolling/dividers.

Focused final checks: 25 service/migration/parser/reconciliation tests passed, zero failures/errors/skips. Local Xvfb was recovered via isolated downloaded runtime packages; both CheckExceptionsPaneTest and JournalPaymentReferencesTest passed, zero skips. Initial missing Maven/proxy/library conditions were environment failures, recovered without project dependency/configuration changes. The first new closed-period fixture omitted a required legacy UUID and was corrected to use the actual close service. A round-trip assertion assumed imported split order and was corrected to select the payment-bearing split by facts. No production defect was hidden by weakening an assertion. Full final clean verify and final geometry/production route gate remain pending at this checkpoint. No publication or GitHub CI is claimed. D04 remains unresolved; only stale-triggered accounting is unavailable. S4 remains BLOCKED.

### P25-S3 final review / final-source rerun

First full clean verify passed 894 tests, zero failures/errors, 45 headless skips. The strengthened CheckExceptionsPaneTest exposed a new form minimum-size defect: the form could shrink/clip rather than exposing both scrollbars. Corrected the scroll content to derive its minimum from its natural preferred size, preserving independent small viewport sizing; actual horizontal/vertical overflow and visible bars now pass. All 25 production Xvfb route tests subsequently passed without skips. Final diff review restricted CHECK_REISSUED audit to actual check operations (ordinary generic replacements retain their original audit semantics), added a regression assertion, and captured each async service/database/company context before starting its background operation. This final source is undergoing clean verify and the complete production Xvfb gate again before claiming the handoff. Undated imported statement ERROR rows remain explicit review exceptions; cancellation reversals are clearly labeled for full-chain review. No other phase is implemented and no stale-triggered accounting rule is guessed.

### P25-S3 final local verification and publication handoff — 2026-10-10

Final-source `mvn clean verify` passed **894 tests, zero failures/errors, 45 headless skips**, BUILD SUCCESS. The complete production Xvfb gate then passed **25 tests, zero failures/errors/skips**, including CheckExceptionsPaneTest, JournalPaymentReferencesTest and ProductionPanelRouteComplianceTest. New check geometry asserts actual content/viewport overflow and both visible editor scrollbars plus table horizontal scrolling/divider movement; save/draft/live VIEWER permissions pass. The ordinary-replacement audit regression, undated bank-error report and retained cancellation-reversal history pass in the full source gate. No production changes follow these results. Focused service/migration/parser/reconciliation evidence: 25 tests, zero failures/errors/skips. No known functional failure remains from executed tests. Native owner desktop acceptance is still required.

Branch `codex/P25-S3-check-exceptions`, base/current main confirmed `5f6bec4b6c9632a25d23b9d4635fa6fd324649bc`. Remote S3 branch absent; no PR/publication or GitHub S3 CI is claimed. Changed documents: this plan, [check contract](accounting/check-exceptions.md), [owner testing](P25-S3-user-testing.md), payment-reference/SCLX specifications and both interface/editor rule documents. Whitespace and documentation links are clean; generated tracked build manifest is restored to baseline and excluded. S3 stays VERIFYING, not DONE. D04 stale-triggered accounting remains unavailable; this slice’s factual age review never changes an obligation solely because of age. S4/NMR and D03 remain gated.

Next exact action after explicit owner publication authorization under AGENTS.md: confirm current main, clean local worktree and expected remote branch; publish the complete reviewed local sequence on a fresh remote S3 branch without force updates, preserving messages/order and matching every tree; open a draft PR; verify branch/PR head and actual-head clean/repeat/Xvfb workflow including CheckExceptionsPaneTest; repair failures and record actual results. Owner follows desktop notes and separately accepts/merges. Only after confirmed merge into current main close S3 and select the first unblocked dependent slice.

Verified implementation commit `3f12e60e04c7796d3390f3680c8b8601f7108806`, tree `e48ba6111aea78780746888217382deb2c9d8ef9`. This documentation-only successor records the verified source head; resolve `git rev-parse HEAD` for the complete local publication tip. Publish both commits in reviewed order with matching trees only after owner authorization. Worktree is clean after this handoff; no remote S3 branch or PR is created. No application change follows final clean/Xvfb verification.


### P25-S3 authorized draft publication — 2026-10-10 (America/Denver)

Owner explicitly authorized publication, draft PR and CI with “yes.” Confirmed clean worktree, main `5f6bec4b6c9632a25d23b9d4635fa6fd324649bc` and absent remote S3 branch before creating it at that base. Used the connected GitHub service under AGENTS.md; preserved reviewed commit messages/order, compared all published blob IDs and both tree IDs, and fast-forwarded with the expected parent and force disabled. No unrelated files were included.

| Local commit | Published commit | Verified matching tree |
|---|---|---|
| `3f12e60e04c7796d3390f3680c8b8601f7108806` | `559699bc0838fbee5e1a95dcbaf054e731157134` | `e48ba6111aea78780746888217382deb2c9d8ef9` |
| `99c7f88cec13cee2b12182907f239d54b9662e6d` | `38db5cdb889e5110334916ef1f9ade9927acd816` | `0653c5a9bc1381a8ab857e45e02ef509fc3ff7a9` |

Opened draft [PR #364](https://github.com/benbaron/sca-jakarta-h2/pull/364), head `38db5cdb889e5110334916ef1f9ade9927acd816`. This documentation-only ledger will be published under the same authorization; active_head records its known parent because the successor cannot embed its own SHA. Verify final branch and PR tip/tree before inspecting actual-head Maven clean verify, repeat tests and Xvfb. No GitHub S3 success is claimed yet. Local final-source evidence remains 894 tests with 45 headless skips and 25 production Xvfb tests without skips, zero failures/errors. Owner desktop acceptance and separately authorized merge remain pending; S3 stays VERIFYING, S4/D04 remain gated.


### P25-S3 post-merge repository reconciliation — 2026-10-11 (America/Denver)

This checkpoint supersedes the preceding S3 publication/merge-pending next actions. Local history contains GitHub merge commit `f9ee8716e545572d66cb8d9494003b8c0b4099db` for [PR #364](https://github.com/benbaron/sca-jakarta-h2/pull/364), merging S3 head `47ce0808` into base `5f6bec4b`. The supplied checkout is clean branch `work` at `27fd0046dea33355bd621624055c0613750022cc`. That successor changes only FundTransferTestFixture record-field access to its equivalent accessor; it introduces no production behavior. The old S3 task branch is merged and must not be reused or republished. `active_branch` is cleared; `active_pull_request` retains the merged PR as the verification reference, and `active_head` identifies the tested source checkpoint rather than this documentation edit.

Fresh offline `clean verify` on that checkout passed using the existing workspace Maven helper: **894 tests, zero failures, zero errors, 45 headless skips**, BUILD SUCCESS. Compilation targets Java 17; migration tests apply all 44 migrations through V80. No fresh Xvfb, repeat-test, GitHub CI or native desktop acceptance result is claimed. Earlier S3 local Xvfb evidence remains historical, not verification of this successor. Local `origin/main` still points to `5f6bec4b`; live remote main and final PR checks were not queried in this reconciliation.

S3 remains VERIFYING because required final-head CI and owner desktop acceptance evidence have not been reconciled; merge alone does not establish the repository definition of DONE. P25-S4 remains BLOCKED on S3 closeout and unresolved D03. D04 remains unresolved for stale-triggered accounting, which is unavailable; factual age review and explicit ordinary cancellation/reissue do not resolve that policy. Later slices remain blocked.

Completed: reconciled controller, phase index and slice dependencies with local merge history and current-source test evidence; retained historical handoffs. Changed document: `doc/PLAN.md` only. No production code, migration or policy decision changed. Next exact action: read live main and PR #364 final-head clean/repeat/Xvfb results, record actual evidence and owner desktop acceptance, then close S3 only if all gates are satisfied. Resolve the D03 accounting contract before activating or implementing S4.

Publication verification — 2026-10-11: owner authorized publication of this repair. The connected GitHub service confirms live `main` at `27fd0046dea33355bd621624055c0613750022cc` and PR #364 merged. Its final head `47ce0808bf7124ebe51b2349f550495888b37fd1` passed [Maven PR Tests run 38088450792](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38088450792); job `114319827665` reports successful clean headless verification, repeat tests and production JavaFX route compliance. This supersedes the preceding not-queried/CI-unreconciled statements. Native owner acceptance is still not recorded; S3 stays VERIFYING. Publish only `doc/PLAN.md` from current main on new branch `codex/P25-S3-plan-reconciliation`, with a draft PR targeting `main`; do not reuse the merged implementation branch. Local Git transport could not connect to the proxy and CLI authentication was unavailable, so publication uses the connected service with matching blob/tree verification. The repair PR is a documentation follow-up, not a new implementation slice. Next: inspect its actual-head CI and record owner S3 desktop acceptance before closure; D03/D04 gates remain unchanged.


### P17-C13 — pane and subpane Save coverage

Status: VERIFYING. Owner requested review of every pane/subpane and visible Save buttons/menu entries on 2026-10-11. This maps to P17 cross-cutting UI corrections; it does not activate P25-S4 or change D03/D04. PR #366 is confirmed merged at current main `f35179378199d8f9fd840c5f9029a34b3a683044`. Fresh branch: `codex/P17-C13-pane-save-coverage`; PR: none. P25-S3 remains VERIFYING for its separately recorded desktop acceptance.

Required reading: root AGENTS, PLAN, [UI rules](ui_design_rules.md), [interface matrix](interface-operation-matrix.md), [editor guidelines](ui/editor-guidelines.md). Required inspection: PanelFactory/AppPanelId route inventory, every production AppPanel and contained editor/dialog, AppPanel/PanelHost/ProductionWorkspaceWindow command routing, UiPermissionGate, existing command/panel/permission tests. Baseline offline compile passed using the workspace Maven helper. Existing services and persistence are retained; no new accounting save authority, policy, migration or donor model is needed in this existing UI area.

Governing audit and manual testing: [pane Save coverage](ui/pane-save-coverage.md). Read-only views retain explained disabled Save; explicit accounting operations persist through their existing action/confirmation rather than an additional save workflow. Completed: full production pane/subpane audit and save controls/routing through existing services; selected-tab/busy/permission availability, authentication draft protection, Inventory/Budget mode readiness, shared Journal save scope, and current manual/specification labels. Focused final Xvfb tests passed 18 tests without failures/errors/skips. Full clean verification passed 898 tests, zero failures/errors and 49 headless skips. The expanded production Xvfb gate passed 42 tests, zero failures/errors/skips. Final clean verification after the test-isolation cleanup also passed 898 tests, zero failures/errors and 49 headless skips. Remaining: separately authorized publication, actual-head GitHub checks and native owner acceptance.


### P17-C13 verification findings and scope

The first full suite found one old source assertion for the ownership button label; its expected text now matches Save Ownership Assignment. Running the expanded production gate exposed a pre-existing cross-test navigation-handler leak: production-window tests left their global opener installed, causing FundTransfersPanelTest to lose its one-time Journal context. ProductionWorkspaceCommandRoutingTest and the new pane-save suite now clear that opener during cleanup; the routing suite also shares the registry resource lock. The complete 42-test Xvfb gate passes after this narrow test-isolation repair. No production navigation, accounting service, migration or permission rule was changed to satisfy these tests.

User-visible changes and seven manual acceptance steps are in [the pane/subpane audit](ui/pane-save-coverage.md). Required native laptop/scaled-font checks and owner acceptance remain outstanding; automated Xvfb results do not establish that acceptance. The workflow now includes the new save coverage and shell capability/routing suites in its production Xvfb gate. No new PR, remote branch publication or GitHub CI result is claimed.


### P17-C13 final local handoff — 2026-10-11 UTC

Branch: `codex/P17-C13-pane-save-coverage`, from confirmed main/PR #366 merge `f35179378199d8f9fd840c5f9029a34b3a683044`. PR: none; local only. P17-C13 remains VERIFYING, not DONE. Completed: all production pane/subpane audit, visible Save routes, nested selected-tab menu/toolbar routing, meaningful persistence/cancellation/draft/permission tests, workflow gate expansion, current specifications and manual testing notes. Final source passed offline Maven clean verify: **898 tests, zero failures/errors, 49 headless skips**, BUILD SUCCESS; expanded production Xvfb gate: **42 tests, zero failures/errors/skips**, BUILD SUCCESS. No application or test change follows these final results. Final whitespace and changed-document links are clean; no known functional failure remains from executed tests. Tracked generated build files are excluded.

Changed documents: this plan; [pane Save audit and owner checks](ui/pane-save-coverage.md); [UI rules](ui_design_rules.md), [operation matrix](interface-operation-matrix.md), [editor guidelines](ui/editor-guidelines.md); [check contract](accounting/check-exceptions.md), [SCLX](data-exchange/sclx.md), [shared operation contract](data-exchange/shared-operation-contract.md), [production test plan](testing/production-workspace-test-plan.md); label-only updates to owner guides for P15-S5 import preview/commit, P16-C6 ownership repair, P16-S2 COA commit, P16-S3 reconciliation, P16-S4 statement import, P18-S1 depreciation and P25-S3 check review.

Remaining: authorized publication, actual-head GitHub clean/repeat/Xvfb validation, native owner acceptance from the audit's seven steps, and merge. Next exact action after owner publication authorization: confirm `git status --short` is clean and `git rev-parse HEAD` identifies the reviewed tip; confirm current main and expected remote branch parent; publish the complete local sequence with matching trees and no force updates; open a draft PR and inspect its actual-head required checks. Record actual CI/native evidence before closing the slice. P25-S3 desktop acceptance and P25-S4/D03/D04 gates are unchanged.

Verified local implementation commit `d2bd56bac841c5710a869d0ef1b3c7da04a844e0`, tree `1a83aaa0122e543dba58a9f7fb8896f06d535232`. This documentation-only successor records that verified source head because a commit cannot embed its own SHA; resolve `git rev-parse HEAD` for the complete local tip. Both commits remain local and must be published in reviewed order only after owner authorization. The worktree is clean after this handoff.


### P17-C13 authorized draft publication — 2026-10-11 UTC

Owner explicitly authorized publication with “push this as a draft.” Confirmed clean local branch/tip, GitHub main at `f35179378199d8f9fd840c5f9029a34b3a683044`, and absent remote task branch before creating it at that base. Local Git credentials were unavailable earlier in this session, so used the connected GitHub service under AGENTS.md. Preserved both reviewed commit messages and order; compared all 47 blob writes and both published trees to local object IDs, then advanced the branch with the expected parent and force disabled. No unrelated files were included.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `d2bd56bac841c5710a869d0ef1b3c7da04a844e0` | `bc382b761627682553a476290499ef22d757a66d` | `1a83aaa0122e543dba58a9f7fb8896f06d535232` |
| `46cceffdacce666cdfe1f8ab20e451d943f3c610` | `e4c0497c3ff7f9fb3e0f7163817b2922624f7fe1` | `4a77dd2cf0a17140a853d1db4833845f0f719799` |

Opened draft [PR #367](https://github.com/benbaron/sca-jakarta-h2/pull/367) targeting main on `codex/P17-C13-pane-save-coverage`, initial head `e4c0497c3ff7f9fb3e0f7163817b2922624f7fe1`. Fetched that branch and independently matched its final tree. PR records actual local tests, implemented scope and native acceptance steps. Initial-head [Maven run 38160919705](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38160919705) is in progress; no GitHub success is claimed. This documentation-only publication ledger is included under the same authorization; active_head records its known remote parent because a successor cannot embed its own SHA. Verify the resulting remote branch/PR tip and inspect that final head's clean/repeat/expanded Xvfb workflow.

P17-C13 remains VERIFYING. Completed: reviewed implementation and handoff are published as a draft with matching trees. Remaining: actual final-head GitHub checks, native owner acceptance and separately authorized merge. No known functional local failure remains; local clean verification is 898 tests with 49 headless skips, and expanded Xvfb is 42 tests without skips, zero failures/errors. No application or test change follows those results. P25-S3 desktop acceptance and P25-S4/D03/D04 gates remain unchanged.


### P25-S4A — D03 NMR contract preparation — 2026-10-13 (America/Denver)

Owner agreed to the recommended D03 proposal and PLAN reconciliation with “Agree. Proceed.” Selected one documentation-only preparatory slice, P25-S4A, on fresh `codex/P25-S4A-nmr-contract-proposal` from confirmed main `cc96e3a94703fd58bc70721513f72dd1fff0bd4b`. PR: none. This authorization prepares a reviewable proposal; it does not adopt an accounting policy or authorize blocked P25-S4 implementation. P25-S3 desktop acceptance and D03 remain implementation gates; D04 is unchanged.

Required reading: root AGENTS, PLAN, adopted P23–P28 program, S1 baseline/D03/A10, ledger authority, period/correction policy, internal fund transfers, and P21 Activity authority. Required inspection: current TxnSplit/TransactionLineCommand NMR field, Journal checkbox, TransactionEntryService persistence, existing supplemental lifecycle migration and SCLX payload, plus original runbook/template evidence where retrievable. No production behavior, schema or accounting data change is in scope. Governing proposal and owner review steps: [P25-S4A D03 contract proposal](P25-S4A-nmr-contract-proposal.md). Remaining: decision/fixture review, documentation and repository checks, precise local handoff.

### P17-C13 post-merge reconciliation carried into S4A

This supersedes the preceding draft/publication-pending next actions. GitHub confirms owner `benbaron` merged [PR #367](https://github.com/benbaron/sca-jakarta-h2/pull/367) at `cc96e3a94703fd58bc70721513f72dd1fff0bd4b`, current main (2026-10-12 02:32:54 UTC / 2026-10-11 America/Denver). Exact final PR head `8bf9da4843b922ce7b0ca02f0570c54f2e1852a5` passed [Maven run 38161041038](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38161041038), job `114533874687`: clean headless verification, repeat tests and the expanded production Xvfb gate all succeeded. Published source, handoff and ledger had trees matching the reviewed local sequence. The prior task branch is merged and must not be reused or republished; no Save implementation PR remains open.

Merged behavior and CI are confirmed. Explicit native acceptance results were not supplied in this session; do not manufacture a desktop pass from the merge. P17-C13 remains VERIFYING for that outstanding evidence under the recorded acceptance contract. Active controller now selects the owner's independently authorized S4A documentation preparation; this is not implementation of blocked S4. P25-S3 similarly retains its separately recorded native acceptance gate. No D03/D04 policy decision is inferred from the Save PR merge or the instruction to prepare this proposal.

### P25-S4A source and inspection findings

Retrieved the original runbook read-only and inspected §8/§26.3: flow-through liability statements conflict with gross-event-income entry guidance. Retrieved only the baseline-matching named workbook (11,461,550 bytes, SHA-256 recorded in the proposal), rather than another same-name copy; its Instructions C135/D136–137/B139/B143–144 confirm the legal-entity and merchant-path dependencies. This verifies reference identity/guidance, not applicable policy or official submission approval. Private financial rows and original files are excluded from source commits.

Current main saves the NMR checkbox on `TxnSplit` through the canonical entry service but has no count/rate/recipient/settlement contract. Inspected SCLX import initializes that flag to false and has no NMR-specific extension, so current round-trip completeness must not be claimed. The proposal uses existing Activity IDs, canonical accounting and OTHER_LIABILITY supplemental lifecycle authority; there is no proposed second running-balance ledger. Examples and future A10-N01–N10 specifications are synthetic and unexecuted feature acceptance, not adopted rates or passed NMR implementation tests. D03 remains UNRESOLVED.


### P25-S4A final local preparation handoff — 2026-10-13 (America/Denver)

Status: VERIFYING. Branch `codex/P25-S4A-nmr-contract-proposal`, based on current main `cc96e3a94703fd58bc70721513f72dd1fff0bd4b`; PR: none; local only. Completed: reviewable D03 custodial-liability recommendation, eight authority decision/input rows, verified runbook/workbook conflict and fingerprint record, balanced synthetic collection/merchant/remittance/carryover examples, ten future acceptance cases, existing authority/legacy/SCLX boundaries, owner review instructions and preceding Save merge/CI reconciliation. D03 is not adopted; no NMR implementation, policy configuration, migration or production-data write occurred.

Validation: original reference workbook fingerprint equals S1; document arithmetic checks passed for all four new fixture transactions, liability progression, cash result and withheld-unconfirmed variant; changed-document links, D03-01–08/A10-N01–N10 coverage and final whitespace checks passed. Fresh offline Maven clean verify on this documentation source passed **898 tests, zero failures/errors, 49 headless skips**, BUILD SUCCESS. No new feature or native desktop acceptance result is claimed; these are existing repository tests and document consistency checks. No known failure remains from executed checks. No new GitHub S4A CI is claimed; PR #367 final-head success is separately verified historical evidence. Generated tracked build files and private reference originals are excluded.

Changed documents: this plan, [S4A proposal](P25-S4A-nmr-contract-proposal.md), [adopted program](P23-P28-runbook-correction-program.md) (preparatory slice only), and [S1 baseline](P23-S1-baseline-and-acceptance.md) (D03 proposal link, still unresolved). Remaining: owner/accounting-authority review and actual policy inputs/adoption, separately authorized documentation publication and actual-head CI/merge; P25-S3 native acceptance remains required before S4 code. Next exact action: review the proposal's decision table and approved worked example with the responsible authority; record every applicable answer and its source/date without interpreting preparation or merge as policy approval. After explicit publication authorization, confirm a clean local tip/current remote base, publish the reviewed sequence without force with matching trees, open a draft PR and inspect its actual-head checks. Do not activate S4 implementation while its gates remain unresolved.

Verified local proposal commit `f4eaea0efaa94b761e274204cf19976bc6753466`, tree `7ff7b879cf3acc98686643be0c5f1684b1e6e3b6`. This documentation-only successor records that source head because a commit cannot embed its own SHA; resolve `git rev-parse HEAD` for the complete local handoff tip. Both commits remain local on the fresh S4A branch and require explicit owner publication authorization. The worktree is clean after this handoff.


### P25-S4A owner acceptance and publication authorization — 2026-10-14 (America/Denver)

Owner explicitly accepted the prepared framework and requested publication with “accept. Put out the MR.” Record that acceptance in the contract, D03 baseline and adopted program. The custodial-liability design framework is accepted; missing actual rate/exemption/date/entity/merchant values remain outstanding rather than invented. D03 is partially resolved, not complete. P25-S4 implementation and P25-S3 native acceptance gates remain unchanged. No implementation or accounting data change is authorized by publishing this documentation.

Fresh publication checks confirm a clean local S4A worktree, current GitHub main `cc96e3a94703fd58bc70721513f72dd1fff0bd4b`, and absent remote S4A branch. Preserve the reviewed proposal and verification commits, then this documentation-only acceptance record, with matching trees and no force updates. Local prior-source Maven evidence remains 898 tests, zero failures/errors, 49 headless skips; document arithmetic/source/links/whitespace are rechecked after this acceptance wording. No application/test change follows the clean verification. New MR and actual-head GitHub results will be recorded after publication.


### P25-S4A authorized MR publication — 2026-10-14 (America/Denver)

Owner accepted the framework and explicitly authorized publication with “accept. Put out the MR.” Confirmed clean local branch, repository, all reviewed commits, main `cc96e3a94703fd58bc70721513f72dd1fff0bd4b` and absent remote task branch. Used the connected GitHub service under AGENTS.md, preserving commit messages/order, comparing all nine blob writes and three commit trees, and advancing the branch with its expected parent and force disabled. No application code, private reference originals or financial rows were published.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `f4eaea0efaa94b761e274204cf19976bc6753466` | `b679f0b005b5c691fa7e8b3092304b3c1b813091` | `7ff7b879cf3acc98686643be0c5f1684b1e6e3b6` |
| `6192c20c6f81ebf96f82dd193c9002cdf44aedcc` | `dc01689e0de5207f4fb3e185898b7978fdd2418f` | `56458477a3c762c732666b0bb9349a90571f7bf1` |
| `9be4e49582cb12819d72254215fa105ae5785e60` | `77a2333a5c756e4ca11f4bdf7a1e18c7a933a51a` | `bcafd3dde770faf49a839aa80abd251f6ae6f770` |

Opened [MR/PR #368](https://github.com/benbaron/sca-jakarta-h2/pull/368) targeting main, ready for review (not a draft), initial head `77a2333a5c756e4ca11f4bdf7a1e18c7a933a51a`. The MR records owner acceptance, exact scope, existing-source local Maven evidence (898 tests, zero failures/errors, 49 headless skips), passing document arithmetic/fingerprint/link/coverage checks and remaining policy inputs. Initial workflow lookup returned no run yet; no CI success is claimed. This documentation-only ledger is included under the same authorization. Its active_head records the known remote parent because a successor cannot embed its own SHA; verify final branch/PR tip and actual-head clean/repeat/Xvfb results.

S4A stays VERIFYING until CI/merge. The custodial-liability framework is accepted, but D03 remains partially resolved while actual policy inputs are missing; P25-S4 implementation and P25-S3 native acceptance remain gated. No implementation or desktop result is inferred from this documentation MR. Next exact action: inspect the final MR head's required workflow, correct failures and record actual results before owner merge; then carry closure forward from confirmed main while retaining the outstanding input/acceptance gates.


### P25-S4A closeout / next-slice prerequisite check — 2026-10-14 (America/Denver)

Owner reports MR #368 merged and requests the next slice. GitHub confirms merge `5894e9ce46b79f34bd6e68cc3a6b6c091fd9b4f9` into current main (2026-10-15 00:16:43 UTC / 2026-10-14 America/Denver). Exact final head `7cc3dced0621c5ab688427156e04dab9118b0a3b` passed [Maven run 38657888270](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38657888270), job `116172211446`: clean headless verification, repeat tests and production Xvfb all succeeded. The owner accepted the framework before publication; the documentation-only S4A contract is DONE. Earlier MR/publication-pending next actions are historical. The merged S4A branch is not reused; this closeout is on fresh `codex/P25-S4-closeout` from confirmed main. No new PR is open.

The next planned accounting implementation is parent P25-S4, still BLOCKED by the accepted contract's actual D03 inputs and P25-S3 native acceptance. Approval of a framework or its merge cannot supply an applicable rate/exemption/date/entity/merchant value or prove a desktop pass. AGENTS §3.2 requires “BLOCKED: do not implement.” Do not bypass that gate by silently adding a configuration sub-slice.

Owner clarification requested during this run: first deliver company policy configuration with no default rates or accounting entries, or provide an approved actual policy for the full workflow. A configuration-first answer would explicitly establish a bounded independent prerequisite slice while leaving assessment, collection/remittance accounting and the parent acceptance gates blocked. No such slice is activated before the answer. Existing policy requirements remain as recorded in the accepted contract.

Completed: verified merge/final CI, closed S4A, cleared obsolete active MR/branch references and identified the exact next prerequisite. Remaining: owner's scope answer or actual policy inputs/acceptance; any subsequent implementation requires its own current-source inspection, baseline and vertically coherent tests. Changed document: PLAN only. Validation: exact-head GitHub evidence above, fresh offline `mvn -DskipTests compile` BUILD SUCCESS at the new merged base, and local document/whitespace/link/status checks passed. No new local full-suite, feature test or native acceptance is claimed. Next exact action: incorporate the owner's answer into the owning contract and slice ledger, then select one allowed slice on a focused branch from current main. If full workflow is selected, obtain the actual applicable D03 answers and P25-S3 acceptance before marking it READY.

Verified local closeout commit `58559d024f8bdeab28e4d425ebf95696958c92a3`, tree `90763bb028d1497b766942d60994b67bf8c04d33`. This documentation-only successor records the known parent; `git rev-parse HEAD` identifies the full local tip. No closeout publication is claimed. Owner scope clarification remains pending before dependent implementation.


### P25-S4B selection and bounded scope amendment — 2026-10-14 (America/Denver)

Owner answered the pending scope clarification: “Build policy configuration first, with no default rates or accounting entries (Recommended).” This explicitly permits a configuration-only prerequisite slice independently of unresolved full-workflow inputs. Select P25-S4B IN_PROGRESS on `codex/P25-S4B-nmr-policy-configuration`, a rename of the fresh unmerged closeout branch based on confirmed main `5894e9ce46b79f34bd6e68cc3a6b6c091fd9b4f9`, preserving its local S4A closeout commits. PR: none. This supersedes the preceding pending-answer statements; no actual rate, exemption or financial transaction is inferred.

Deliver H2-backed company-owned dated/sourced policy versions, real Administration New/Save/history, existing COMPANY_ADMIN write gating and authenticated factual audit, overlap/validation/concurrency protections, no default rates or sample policy, and migration/service/UI coverage. Saved policy facts are retained; rate/source history is not overwritten. Full assessment/collection/remittance, liability/cash reporting and NMR interchange remain outside this slice. Parent S4 and P25-S3/D03 financial acceptance gates remain unchanged.

Required reading: AGENTS, PLAN, adopted program/S4A contract, UI rules/editor/interface matrix, P20 authorization, existing administration/service/model/migration/test patterns. Required inspection: AdministrationPanel, UiServiceRegistry, persistence registration, Company/Merchant administration, company ownership, audit, migration constraints, company formatting/table/divider binders and existing FX/service tests. Fresh merged-base offline compile already passed before selection, with unchanged application source; inspect donor configuration patterns as reference only. Record any design choice in the S4B contract before coding.

Governing implementation/owner-check contract for selected S4B: [company NMR policy configuration](P25-S4B-nmr-policy-configuration.md). Saved terms are immutable; explicit reasoned validity shortening is the only history mutation. Donor configuration patterns were reviewed and not imported; normalized current-repository JPA/authorization/audit patterns are retained.


### P25-S4B implementation / local verification — 2026-10-14 (America/Denver)

Selected configuration-only vertical slice is implemented on `codex/P25-S4B-nmr-policy-configuration`; no PR or new remote branch has been published. The branch retains its two local S4A closeout/handoff prerequisites from current merged base `5894e9ce46b79f34bd6e68cc3a6b6c091fd9b4f9`. S4B is VERIFYING, not DONE; parent S4 financial and S3/D03 acceptance gates remain unchanged.

Completed: V81 normalized company-owned retained policy versions with no seed/default terms, explicit source/rate/currency/date validation, inclusive overlap checks serialized on the company lock, request UUID/fingerprint exact retry, same-company authenticated reads and COMPANY_ADMIN writes, reasoned validity shortening, atomic factual audit and rollback. Administration -> NMR Policies exposes blank New Version, Save Policy Version, retained read-only history, Refresh, and End Selected Version / Save End Date. Nested File/toolbar Save follows the selected draft. Company formatting preserves the exact rate through focus changes; numeric/date sorting and company-owned column/divider state use the established binders. No accounting service or ledger/supplemental/fund-transfer write is introduced. Retention and SCLX omission are visible; whole-database backup remains the supported complete transfer.

Focused checks cover restart/idempotency/changed or foreign request, company/role/session guards, inactive-company denial, explicit zero and missing/invalid input, inclusive intervals/gaps/categories/companies, concurrent overlapping saves, audit before/after/reason and induced audit-failure rollback, nondestructive V80 upgrade/repeat/untracked recovery, real nested Save, live permissions, dirty-cancel preservation, rate precision, end-date validation, typed sorting, column-state restore and scaled two-axis geometry. Development desktop checks caught and repaired end-button readiness after saved-row reselection and sort-state loss from replacing table items on reload. Desktop tests now attach newly selected/reopened ScrollPane skins before looking up controls. These were corrected during this slice, not deferred failures.

Documents updated: PLAN, S4B governing/manual-check contract, adopted program, S4A scope amendment, interface operation matrix, pane Save coverage, persistence authority inventory, and SCLX specification. Workflow includes NmrPoliciesPanelTest in the existing expanded Xvfb gate. Native visual/owner acceptance is still pending under the [manual checks](P25-S4B-nmr-policy-configuration.md); Xvfb geometry is not claimed as native owner acceptance.

Offline `mvn clean verify` passed with **911 tests, zero failures/errors, 54 display-related skips**, BUILD SUCCESS (2026-10-15 01:04 UTC). The first expanded Xvfb run exposed six existing shell-routing fixture errors: UserAppStateStore restored a previously deleted temporary database; a dummy Journal drill context then leaked into the following fixture and opened a modal prompt. That failed run was stopped after diagnosis. The narrowly scoped prerequisite fix makes ProductionWorkspaceCommandRoutingTest use a nonpersistent active-test session store and clears its dummy context during cleanup; production session behavior is unchanged. The focused configuration/routing/Save run passed **16 tests, zero failures/errors/skips**. The final post-fixture offline clean gate passed again with **911 tests, zero failures/errors, 54 display-related skips**, BUILD SUCCESS at 2026-10-15 01:13 UTC. The final expanded workflow Xvfb gate passed **47 tests, zero failures/errors/skips**, BUILD SUCCESS at 2026-10-15 01:14 UTC. This includes the corrected routing fixture, following Merchant regression (no leaked modal), new policy pane and real nested File/toolbar Save. Final local logs: `/workspace/work/nmr-clean-verify-final.log` and `/workspace/work/nmr-expanded-xvfb-final.log`; these are local evidence, not committed application data. Final diff, local documentation links, migration scope, service/UI boundaries and whitespace checks passed. No unresolved functional failure remains in executed final checks. All application and test behavior was frozen before the final clean/expanded gates; only verification documentation follows. No GitHub CI for this new source is claimed. Remaining: owner review, authorized publication/exact-head CI, native owner acceptance and merge. Next exact action: review this local configuration slice; when publication is explicitly requested, preserve its reviewed commit/tree sequence on the focused branch, open the MR and inspect the required exact-head workflow. Do not mark S4B DONE or unlock full NMR accounting from local verification.


### P25-S4B local commit handoff

Reviewed implementation commit `8df81bfbba07b30177b8701dcbede71257cd3bed`, tree `fd344fe814bd26ea509e3e650d82b98552d3569b`, includes the complete configuration slice, migration, tests, workflow fixture correction and current governing/manual-check documentation. Final gates: clean verify 911 tests / zero failures/errors / 54 display skips; expanded Xvfb 47 / zero failures/errors/skips. Source and test behavior did not change after those final gates. No known executed-check failure remains; the earlier failed desktop attempt is explained above. This documentation-only successor records the known implementation parent in active_head; `git rev-parse HEAD` identifies the full local tip.

Branch: `codex/P25-S4B-nmr-policy-configuration`. PR/remote publication: none. Status: VERIFYING pending owner review, publication/exact-head GitHub CI, native acceptance and merge. No new financial workflow is authorized or unlocked. Next exact action: review this local branch and the S4B manual checks; on an explicit publication request, publish the preserved local sequence without force updates, verify matching trees/remote tip, open the MR, run/inspect the required clean/repeat/Xvfb workflow and record actual-head results. Native acceptance is not inferred from Xvfb.


### P25-S4B owner review / publication authorization — 2026-10-15 (America/Denver)

Owner reviewed the completed local handoff and said “good. move to the next action.” The immediately recorded next action is publication and actual-head CI; proceed with that accepted configuration slice. This does not authorize merge, certify native acceptance, supply missing actual D03 inputs, or unlock financial NMR implementation.

Fresh checks confirm the clean local branch `codex/P25-S4B-nmr-policy-configuration` at `93e46b20b1cba8e1879a7002fcc222fbe1cb803f`, current GitHub main `5894e9ce46b79f34bd6e68cc3a6b6c091fd9b4f9`, and no remote task branch/MR. Preserve both local S4A closeout prerequisites, the reviewed implementation and its verification handoff, followed by this documentation-only authorization record. Use the connected GitHub service, compare every published commit tree with its local tree, and advance only the expected branch parent with force disabled. Local final clean verification remains 911 tests / zero failures/errors / 54 display skips; expanded Xvfb remains 47 / zero failures/errors/skips. No application/test behavior changes follow those gates. Native acceptance remains pending; no new GitHub success is claimed before the MR checks run.


### P25-S4B authorized MR publication — 2026-10-15 (America/Denver)

Owner accepted the local slice and requested the recorded next action with “good. move to the next action.” Published through the connected GitHub service after confirming clean local state, current main, repository, absent remote task branch/MR and the reviewed five-commit sequence. Created each remote tree from the corresponding local changes and required exact tree-SHA equality; preserved commit messages and order. Created the task branch from confirmed main and advanced it with expected parent `5894e9ce46b79f34bd6e68cc3a6b6c091fd9b4f9`, force disabled.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `58559d024f8bdeab28e4d425ebf95696958c92a3` | `55de503117f57a4da51e5876ab6ab753845e4570` | `90763bb028d1497b766942d60994b67bf8c04d33` |
| `105aa4c8b13e181bb9cb3e3e78d296a06fdffcd9` | `59d58830b0da026ad0895c4439bce64a0ec610a4` | `ba8522fbeacbe044cc9192bd89398b9d7eee0251` |
| `8df81bfbba07b30177b8701dcbede71257cd3bed` | `30a52f78dc1eccd62613ffce22912b13b12f044b` | `fd344fe814bd26ea509e3e650d82b98552d3569b` |
| `93e46b20b1cba8e1879a7002fcc222fbe1cb803f` | `5ba2830011a8c531ee186a5d08d65988ffbb1404` | `b28535d88731e7a668d3a5a142cc1b879912df21` |
| `f3f75753b204243c36d78dd014d6aaf5c7d85041` | `b62eca1dd8ced24f6085213d634b2d93d63578de` | `565a5fc8db36d5c0992cfdc15c6c299e80117358` |

Opened [MR/PR #369](https://github.com/benbaron/sca-jakarta-h2/pull/369), **Add company-owned dated NMR policy configuration**, ready for review (not draft), targeting main from `codex/P25-S4B-nmr-policy-configuration`. Initial published head `b62eca1dd8ced24f6085213d634b2d93d63578de` has matching tree `565a5fc8db36d5c0992cfdc15c6c299e80117358`. The MR records actual local clean results (911 tests, zero failures/errors, 54 display skips), expanded Xvfb results (47 tests, no failures/errors/skips), the fixture-only prerequisite correction and native acceptance notes. Initial-head [Maven run 38742195674](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38742195674) is in progress; no new GitHub success is claimed yet.

This documentation-only publication ledger follows the same authorization. active_head records its known remote parent because a successor cannot embed its own SHA. Verify the final branch/PR tip and inspect that exact head's required clean/repeat/expanded Xvfb checks. No application or test behavior changes follow the local gates. S4B remains VERIFYING; native owner acceptance, CI and separately authorized merge remain outstanding. No financial NMR workflow or default rate is introduced, and parent S4/S3/D03 gates are unchanged. Next exact action: inspect the final MR head's workflow/jobs, repair failures if any, update the MR validation with actual results and leave a precise verification handoff before owner merge.


### P25-S4B final-head GitHub verification / handoff — 2026-10-15 (America/Denver)

This supersedes the preceding CI-pending next actions. [MR/PR #369](https://github.com/benbaron/sca-jakarta-h2/pull/369) is open, ready for review, unmerged and mergeable. Final published head `c51d7f133ee8d03f97cc9f6f22120693e98c1915`, tree `08d0adbac6540e3dd6f3bdaf4e85ed03ea687ff6`, passed [Maven run 38742484938](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38742484938), job `116447616233`: clean headless verification, repeat tests and production JavaFX/Xvfb compliance all completed successfully; overall run/job conclusions are success. PR metadata was rechecked against that exact head and its description now records actual CI success. No application/test behavior changed after the local gates (911 clean tests / zero failures/errors / 54 display skips; expanded Xvfb 47 / zero failures/errors/skips). GitHub step success is confirmed independently; the counts quoted here remain the recorded local counts.

Publication ledger mapping: local `ddd426a3c29bd444b092cf004bd4057e594907a4` -> published `c51d7f133ee8d03f97cc9f6f22120693e98c1915`, matching tree `08d0adbac6540e3dd6f3bdaf4e85ed03ea687ff6`. Native Git fetched the final branch and independently verified all six published trees, messages, ordered parents and the aggregate tree against their reviewed local equivalents. Remote updates used the expected parent with force disabled. No source, test, migration or workflow file remains unpublished.

Completed: accepted S4B configuration implementation, governing/manual-check documentation, matched-tree publication, MR, final-head required GitHub checks and MR validation update. Remaining: explicit native owner acceptance and separately authorized merge; S4B remains VERIFYING until its completion contract is met. Full assessment/collection/remittance and parent S4/S3/D03 gates remain unchanged. No known functional failure remains from the executed final checks.

This verification-only local PLAN successor intentionally preserves the already-green published MR head. Carry it forward with the next authorized publication or post-merge closeout; do not rewrite the reviewed implementation history. Its active_head records the verified remote source, while `git rev-parse HEAD` identifies the complete local handoff tip. Next exact action: owner performs the [S4B manual acceptance](P25-S4B-nmr-policy-configuration.md) and merges when ready; after an owner merge report, fetch/verify current main and the merged/final-head CI evidence, then start a fresh closeout/successor branch and select only an unblocked, authorized slice.


### P25-S4B merge / next-action prerequisite check — 2026-10-15 (America/Denver)

Owner reports “merged; move to the next action.” GitHub confirms owner merge of [MR #369](https://github.com/benbaron/sca-jakarta-h2/pull/369) at `7943060ecc66cb747921ea3edfc0204cbb402218`, current main (2026-10-16 00:27:25 UTC / 2026-10-15 America/Denver). Exact final head `c51d7f133ee8d03f97cc9f6f22120693e98c1915` passed [Maven run 38742484938](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38742484938), job `116447616233`: clean headless verification, repeat tests and production JavaFX/Xvfb compliance all succeeded. The recorded publication/native-merge-pending next actions above are historical. No explicit native acceptance results were supplied, so S4B remains VERIFYING for that evidence; merged implementation and final-head CI are confirmed. The owner merge alone does not certify a desktop test or supply an accounting policy.

Started fresh `codex/P25-S4B-closeout` from confirmed origin/main; the merged task branch is not reused. Carried the local final-CI PLAN-only successor forward as `118422af`. No new PR exists. All implementation/source/test/migration/workflow content equals merged main; this run reconciles control documentation and next-slice prerequisites.

Parent P25-S4 remains BLOCKED on approved applicable D03 policy inputs and P25-S3 acceptance. D03's framework acceptance and stored free-text company policy configuration do not choose the recognition trigger, eligible/exempt/refunded-count rules, recipient/legal reporting treatment, merchant-discharge evidence or opening/correction rules. AGENTS §3.2 states “BLOCKED: do not implement.” S5 and the later sequential slices remain blocked; do not activate them from a configuration merge.

Concrete independent next-slice candidate (proposed, **not selected/authorized for implementation**): **P25-S4C — NMR policy SCLX portability**. Version the policy-only application extension; export deterministic selected-company retained versions using their intrinsic UUIDs, exact source terms/rates/dates and original request/save metadata; preview and explicitly import through the existing guarded atomic SCLX commit. Preserve immutable terms and explicit interval-conflict handling, company ownership, policy-write permission, exact replay and factual audit. No default policy/rate, NMR assessment, collection/remittance or generated accounting entry. Cover source/target scope, retained end-date history, malformed/duplicate/conflicting data, restart/retry and rollback, deterministic bytes and real preview/Save integration. Preserve older files without this extension. Amend the owning contracts explicitly if the owner selects this independent slice; do not claim the blocked full P27-S4 portability program is complete.

Initial current-source inspection confirms the bounded gap: the S4B UI/spec disclose omission; SclxCoreSnapshotQueryService reads the existing core graph, SclxExportSection has no NMR policy section, and SclxImportCommitService's supported extension keys omit policy versions. The existing audit extension is factual history rather than reconstruction of policy configuration. Whole-database backup remains the complete supported preservation route today. Further source/donor/service/test inspection is required after selection and before coding.

Completed: live merge/main/final-CI verification, fresh branch, preservation of the local verification handoff, plan reconciliation and a concrete bounded proposal. Remaining: native acceptance evidence and owner's scope choice or actual approved D03 inputs. No new policy or feature implementation is authorized by this proposal. Next exact action: obtain that scope choice while finishing the fresh merged-source compile/document checks, record actual results and leave a precise local handoff; only then activate one permitted slice.


Fresh merged-source baseline: offline `mvn -DskipTests compile` passed in 22.918 seconds (2026-10-16 UTC). The S4B governing document now records the actual merge/final-head CI instead of obsolete publication-pending text. Changed files in this closeout are PLAN and the S4B contract only; application/test/migration/workflow content remains identical to merged main. Required native checks remain visible; no native pass is invented. No new full-suite run was needed for the documentation-only reconciliation; the final merged-source CI evidence above is independently verified.

The owner was asked to choose the concrete independent policy SCLX portability scope or resolve the approved accounting policy inputs. That choice is pending; do not treat the recommendation or elapsed time as selection. This is a scope clarification required by the accepted S4 gate, not a request to reauthorize the already completed configuration work.


### P25-S4B merged-source closeout handoff

Local closeout commit `400ebc552cf2a1f78726a5814ad185a00a6959d8` is on fresh `codex/P25-S4B-closeout`, based on verified current main `7943060ecc66cb747921ea3edfc0204cbb402218`. No new PR/publication exists. It carries the preceding local CI ledger and reconciles only PLAN and the S4B governing document; source/test/migration/workflow content equals merged main. Fresh offline compile passed; live final-head clean/repeat/Xvfb CI and local documentation links/whitespace/scope checks passed. Native acceptance evidence remains unrecorded; there is no claimed new desktop acceptance or actual policy input.

The proposed S4C scope remains unselected pending the asynchronous owner choice. An additional inspected constraint for that candidate: NmrPolicyService's original request fingerprint includes the original validTo, while an audited end operation shortens current validTo without replacing the hash. Any portability design must preserve this retained retry identity and saved metadata rather than recomputing the hash from a shortened interval. This is an inspection finding, not an adopted import implementation.

Remaining: owner selects the concrete independent policy-portability slice or resolves the applicable D03 accounting inputs; separately record the required native acceptance evidence. Next exact action: apply the owner's answer to the governing contract and controller, verify its prerequisites and complete the relevant current-source/donor/service/test inspection before implementing one authorized slice. If S4C is selected, rename this fresh unmerged closeout branch to a focused S4C branch while preserving its documentation commits, then record the configuration-only amendment before coding. If accounting policy inputs are selected, complete the adopted decision table without inventing values or lifting acceptance gates. This PLAN-only successor records the known closeout parent; `git rev-parse HEAD` identifies the complete local handoff tip.


### P25-S4C selection — 2026-10-15 (America/Denver)

Owner answered the pending scope choice: “Policy SCLX portability (Recommended).” This supersedes preceding unselected/pending-answer statements. Select P25-S4C IN_PROGRESS on `codex/P25-S4C-nmr-policy-sclx`, renamed from the fresh unmerged S4B closeout branch based on confirmed main `7943060ecc66cb747921ea3edfc0204cbb402218`; preserve its three local documentation commits. PR: none; no publication authorization for this slice is inferred. S4B native acceptance remains unrecorded; parent S4/D03/P25-S3 financial gates remain unchanged.

Governing scope and required reading: [S4C portability contract](P25-S4C-nmr-policy-sclx.md), S4B configuration, S4A accepted framework, adopted correction program, SCLX specification and the three UI design rules. Required inspection: current NmrPolicyVersion/Service/UI, V81, SCLX snapshot/serializer/validator/preview/native identities/commit, authorization and existing service/interchange/preview tests. Fresh merged compile passed; focused existing policy/export/authorization baseline passed 10 tests with zero failures/errors/skips.

Deliver optional versioned extension, retained UUID/save hash/source and interval facts, strict preview validation and conflict choices, atomic COMPANY_ADMIN import through existing Save, deterministic export/counts, no defaults or generated NMR accounting. Changed existing versions are retained: only Keep target is offered; ending a target interval uses its existing audited action before retrying a successor. No silent overwrite/extension of policy history. Detailed tests and handoff follow implementation.


### P25-S4C implementation and verification checkpoint — 2026-10-15 (America/Denver)

Implemented optional version-1 `nmrPolicies` through current-company snapshot/export validation and counts, strict shared codec, native UUID ownership/content classification, source and target overlap diagnostics, explicit Keep target conflicts and existing atomic SCLX Save. Shared configuration validation and authenticated COMPANY_ADMIN remain authoritative; unguarded compatibility import cannot write policies. Retain original request hash and saved source actor/time independently of current importer audit. Under the company lock recheck actual policy content/ownership/intervals before any write; identities and operation/factual audit use the same transaction. No migration, seed/default rate, or generated NMR ledger entry was added. Old files without the extension remain valid.

Added five round-trip/strict-input/authorization/rollback/conflict/race tests and one real preview/Save JavaFX regression. Coverage includes ended history and original Save retry after target restart, distinct source/import actors, exact native reuse and no-op replay, malformed/duplicate/overlapping source data, foreign-company UUID rejection, target history protection, source-win denial, early and operation-audit-boundary rollback, no financial rows, live role changes and actual guarded Save persistence. Existing NMR pane geometry and shell tests remain in the expanded gate.

Validation checkpoint: baseline 10 tests passed; focused service/interchange rerun 12 passed (six display skips). Initial clean verify passed 917 tests, zero failures/errors, 55 display skips. First expanded Xvfb run exposed one new-test error: the existing button gate cleared the policy denial tooltip when disable changed; the other 47 cases passed. Move the tooltip assignment after disable changes. The focused real preview/Save regression then passed under Xvfb, including role restoration and durable exact-rate persistence. Final expanded Xvfb and fresh clean verify are being rerun after that correction; do not treat the earlier clean result as final-source evidence. No current known business/service failure, GitHub result or human desktop pass is claimed.

Changed documents: PLAN, selected [S4C contract](P25-S4C-nmr-policy-sclx.md), adopted program, S4B's superseded omission note, SCLX specification, operation matrix and editor guidelines. Relative document links and staged whitespace checks passed. No private workbook, donor source, generated target file or scratch asset is staged. PR: none; no publication occurred. Next: complete final gates, record actual results/commit, then hand off the local reviewed change; publish only after explicit owner authorization under AGENTS §5. Full financial NMR gates remain unchanged.


### P25-S4C final local verification and review handoff — 2026-10-15 (America/Denver)

Status: VERIFYING, local branch `codex/P25-S4C-nmr-policy-sclx`, based on merged main `7943060ecc66cb747921ea3edfc0204cbb402218`. The preceding in-progress test checkpoints are superseded by these final results. PR/publication: none in this run. Implementation and documentation are reviewable locally; full accounting remains BLOCKED on its separate D03/P25-S3 gates. S4B human native acceptance remains unrecorded.

Final offline **mvn clean verify passed 917 tests, zero failures/errors, 55 display skips**, BUILD SUCCESS (2026-10-16 01:12:38 UTC). The workflow's complete expanded **Xvfb gate passed 48 tests, zero failures/errors/skips**, BUILD SUCCESS (01:16:04 UTC), including all six PaneSaveCoverage cases and four NMR pane cases. The policy preview/Save regression now covers ADMIN -> ACCOUNTANT -> VIEWER -> ACCOUNTANT -> ADMIN, denial explanations, restored Save readiness and actual guarded H2 persistence with exact rate. Both relevant permission changes refresh through weak listeners, with the policy explanation applied after the existing button gate. Initial tooltip failure is corrected; no known failure remains from executed final checks. Source/interchange round trips and injected early/operation-audit rollback passed in the final full suite. No application/test changes follow these results.

Final scope: selected-company deterministic policy export/counts and strict optional versioned import, original save UUID/hash/actor/time and all source/current interval facts, explicit immutable-history conflicts, foreign identity/overlap/race protection, authenticated COMPANY_ADMIN writes and atomic policy/identity/audit persistence through existing Save. No schema migration, defaults, financial assessment/remittance or generated NMR entry. Older files without policy extension remain supported. Changed documents are listed in the checkpoint above; [owner checks](P25-S4C-nmr-policy-sclx.md) use separate disposable databases. Required UI rules, donor design alternatives, staged diff/whitespace and governing-document relative links were reviewed. Tracked build outputs, private originals and scratch files are excluded.

Remaining: owner review/native desktop acceptance, explicit MR publication authorization, actual published-head GitHub checks and merge. No new GitHub result or human desktop acceptance is inferred from local Xvfb. Prepared MR text is in local scratch `work/p25-s4c-mr-body.md` under `/workspace`; it records concrete behavior and these actual gates. Next exact action after authorization: confirm clean local tip/current main, repository and absent/expected remote task branch, reproduce the reviewed local commit sequence with matching trees and no force, open the authorized MR, inspect its actual-head clean/repeat/Xvfb workflow and correct any failure before owner merge. Do not advance parent S4 accounting or invent policy inputs.


Verified local implementation commit `e728debb588855527f0f3bc607e5d00d105ea2d0`, tree `8d7a0010993d6fc94f01954029924ea6a49f82f9`. This documentation-only successor records that known source head because a commit cannot embed its own SHA; `git rev-parse HEAD` identifies the complete local handoff tip. Final application/test bytes are unchanged from the successful clean/Xvfb gates. All S4C commits remain local on `codex/P25-S4C-nmr-policy-sclx`, with the three preserved S4B closeout documentation commits. Worktree is clean after the handoff; no new MR, GitHub S4C pass, merge or human native acceptance is claimed.


### P25-S4C authorized MR publication — 2026-10-15 (America/Denver)

Owner explicitly authorized publication and continued work with “submit the mr and carry on.” Confirmed clean local branch/tip, current main `7943060ecc66cb747921ea3edfc0204cbb402218`, repository, and absent remote task branch/MR. Published through the connected GitHub service, preserving all five reviewed commit messages/order and requiring every blob and tree SHA to match its local source. Created the branch from confirmed main and advanced it using the expected parent with force disabled.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `118422aff814efac6de559823ab87fcf27ad7d9e` | `27bc90518b984e5c38293d99a7ccf9071d320827` | `751588270152d253af709807eeb9a1bca51ec4cb` |
| `400ebc552cf2a1f78726a5814ad185a00a6959d8` | `57382a0c70cd9de992b9a05ca6c6eca936fc41f8` | `3a7b89dde0172cbb37982ca7febc696045edbfff` |
| `61eca28eb293616d0592951f3e3743e3a3f7f6ea` | `6fe165a4a3f8ffc53fff7b3f4260a586c8f63482` | `9810ff7a33968a647adcaa5f861650d2856405fe` |
| `e728debb588855527f0f3bc607e5d00d105ea2d0` | `189f54b6cf338e78d256d3042274aff51224cf2f` | `8d7a0010993d6fc94f01954029924ea6a49f82f9` |
| `be0d1e832511cb5cd875a966941ee38c3d1d92fc` | `dd33a52979a22a7734d25e212e5706a2b9bf1de9` | `5b5e5a09c7a349a39bd759a2f255f3a98116fbad` |

Opened [MR/PR #370](https://github.com/benbaron/sca-jakarta-h2/pull/370), **Preserve saved NMR policy versions through SCLX**, ready for review, targeting main. Verified initial MR head `dd33a52979a22a7734d25e212e5706a2b9bf1de9`; local final gates remain 917 tests/55 display skips and expanded Xvfb 48 tests, zero failures/errors. The description records actual validation, retained history/conflicts and native owner checks. No new GitHub pass or human native acceptance is claimed yet. This publication ledger is a documentation-only successor under the same authorization; verify its final remote/MR tip and actual-head CI.

Continued prerequisite assessment: S4C remains VERIFYING until required review/merge evidence; do not start a dependent implementation prematurely. Parent P25-S4 is still BLOCKED on actual approved D03-01–08 inputs and P25-S3 acceptance; P25-S5 and subsequent slices depend on S4. Read the adopted program and accepted S4A input table again. Asked for the approved policy source (rates, exemptions, dates, recipient/merchant/remittance rules) and the P25-S3 desktop result while CI proceeds. No fictional policy or independent scope amendment is inferred from “carry on.” Full NMR accounting remains blocked; next permitted work is recording the applicable authority inputs and acceptance evidence, followed by selecting one approved slice after its merge gate.


### P25-S4D — Owner correction: external operating policies — 2026-10-17 (UTC)

Owner clarified: “These don't need to be codified in the software.” This supersedes earlier requests for actual D03-01–08 policy values and all historical statements that missing rates, exemptions, effective dates, legal/merchant arrangements or remittance/refund rules block NMR development. The accepted custodial-liability framework remains; users enter amounts, dates, account/fund/event/recipient references and settlement allocations explicitly. No assessment, exemption, deadline or policy-driven settlement engine is required. Saved S4B policy versions and S4C portability remain optional reference data, with their history intact and no mandatory accounting dependency.

Selected a documentation-only corrective slice on fresh `codex/P25-S4D-nmr-scope-correction` from confirmed current main `6d3a7214c46e171411a6ecb9a6a4c0a78695ead0`. Connected GitHub confirms MR #370 merged on 2026-10-16 at 03:55:48 UTC; native fetch confirms the merge in current main. Final published head `9d49c102e0f2907072c389e571603053c6a4fd6f` passed [Maven PR Tests run 38845621962](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38845621962), job `116795765834`: clean verification, repeat tests and expanded Xvfb all succeeded. This carries forward the local-only post-CI evidence from `27c4f92b` without reusing the merged branch. Prior publication/CI/merge-pending statements are historical.

Required reading/inspection for this scope correction: root AGENTS, PLAN, adopted P23–P28 program, D03/A10 baseline, accepted S4A contract and merged S4B/S4C contracts. Reconcile these six documents only. No Java, test, migration, workflow, policy row or accounting value changes. External rate/count examples are replaced by directly entered synthetic accounting fixtures; mandatory accounting invariants and canonical authorities remain.

Separate native acceptance evidence for S3/S4B/S4C remains unrecorded; this correction does not invent it. Full S4 remains blocked on the previously recorded P25-S3 desktop acceptance prerequisite, not on external operating policies. D04/D07 and later reporting contracts are unchanged. PR: none; this fresh correction is local. Validation and final handoff follow below.


### P25-S4D local verification and handoff

The six-document scope correction is complete locally and VERIFYING, not merged. Changed documents: PLAN, adopted correction program, D03/A10 baseline and S4A/S4B/S4C contracts. Branch `codex/P25-S4D-nmr-scope-correction`; base `6d3a7214c46e171411a6ecb9a6a4c0a78695ead0`; PR: none. The active-head field records the confirmed base; `git rev-parse HEAD` identifies the complete committed local correction.

Validation passed: `git diff --check`, six-document scope review, 99 relative file links, current branch/base/controller consistency and D03/A10 scope assertions. Fresh connected GitHub queries reconfirmed MR #370's final-head successful run and all job steps. No application/test/migration/workflow files changed; no new Maven or desktop run was performed for this documentation-only amendment. No known documentation-check failure remains; source validation is the existing merged S4C evidence, not a new S4D test claim.

Next exact action: review this local documentation correction for publication, and record the separate actual P25-S3 desktop acceptance result before activating one bounded financial implementation slice. That later slice records user-entered NMR amounts and explicit canonical liability/settlement allocations, starting with current service/correction/supplemental/Journal/interchange inspection. Do not restore the superseded external-policy-input gate or make optional saved policies mandatory. No new publication or desktop result is claimed.


### P25-S4D accepted continuation and draft MR — 2026-10-17 (UTC)

Owner accepted the local correction with “good. carry on with the corrected plan.md.” Continued the recorded VERIFYING slice and published the accepted documentation on its fresh branch, without starting blocked financial implementation. Confirmed current main remains `6d3a7214c46e171411a6ecb9a6a4c0a78695ead0` and remote task branch was absent; created it at that base and advanced with the expected parent and force disabled. Local `a401b4ce1a7d6fdc77790c046794011cc1dec4b8` maps to published `94900e4e8ccfff38ec1f8663b4da9804888463cb`, matching tree `38c2c5bf8831e6703ac48a79e9f2333d2b39646d` and all six blob SHAs. Draft [MR #371](https://github.com/benbaron/sca-jakarta-h2/pull/371) targets main; its initial actual head was verified. This publication ledger follows that known content head.

Automatic approval review initially rejected the baseline-document blob because existing workbook metadata was treated as a possible new disclosure. Read the exact existing main-branch file through connected GitHub (blob `c512d1505af25d75638a5a1aff9d045dddc128af`), proved its metadata prefix unchanged, and reviewed the five scope/acceptance line changes. Retry succeeded with that evidence; no private workbook or new metadata was uploaded, and no transport workaround was used.

Fresh scope/whitespace and 99 relative file-link checks passed. Local full Maven verification and actual-head GitHub checks are running; their final results must be recorded before handoff. The owner was asked for the still-unrecorded S3 desktop result while independent verification continued. No human acceptance is inferred from this continuation.

Read-only next-slice inspection confirms `TransactionLineCommand.nmr` is already explicit and `TransactionEntryService` stores it independently of policy configuration. Existing supplemental commands provide item UUID, effect and canonical line index; `OTHER_LIABILITY` query/integrity services own cutoff balances, allocation completeness and availability. Reuse these authorities. A concrete portability gap remains: `SclxImportCommitService` builds imported line commands with NMR `false`; policy-version portability does not preserve financial line attribution. Recheck this with snapshot/conflict/correction tests in the later owning financial slice. No new feature, migration or second balance authority was implemented here.


### P25-S3 owner acceptance and next implementation gate — 2026-10-17 (UTC)

Owner answered the pending desktop-workflow question: “Passed — no issues.” This supplies the previously missing P25-S3 desktop acceptance for check issue/delivery, cancel/reissue, and reconciled/closed-period protections. Do not apply that result to unrelated S4B/S4C/P17 native checks. Fresh connected GitHub confirms MR #364 merged at `f9ee8716e545572d66cb8d9494003b8c0b4099db` and final head `47ce0808bf7124ebe51b2349f550495888b37fd1` passed [Maven PR Tests run 38088450792](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38088450792). P25-S3 is DONE; earlier acceptance-pending statements are historical. D04 stale-triggered automatic accounting remains excluded and is not adopted by this workflow result.

Reconciled current controller, S3 owner notes and S4A/program current instructions with that acceptance. P25-S4 now awaits only merge of the active S4D documentation correction before selection from current main; external operating policy values are not a prerequisite. Finish MR #371 final-head verification and owner merge, then select one bounded user-entered NMR accounting slice with canonical Journal/supplemental services, liability tie-out and explicit portability coverage. No code slice is activated before the current required slice's merge.


S4D publication ledger commit local `b6d9379219770eefc246b8045bc828f5d1dbb40a` was reproduced as remote `3cc2338c04eb76d4e49f567af67566cbd02e990c`, matching tree `8bae76310d3c3aa641971a4d207c56c4f3274650`. Native fetch confirmed the remote head and exact local/published tree equality. This successor adds only the newly supplied S3 acceptance and corresponding current instructions; final CI must run on its published successor, not a superseded head.


### P25-S4D final published head and local verification

Local acceptance-closeout commit `aa395df0bc0a53cc4e37c3ab604dfcbfb91afe81` was reproduced as remote `06e6c9fc0fc992817610c6ab94f62aefae5ff749`, matching tree `df542829e885365db28fd5fe83d29bfe868cf9ce` and all four changed blobs. Native fetch confirmed that final head/tree and zero local/published content differences. Draft MR #371 targets main and remains open/unmerged; current main remains `6d3a7214c46e171411a6ecb9a6a4c0a78695ead0`. All three reviewed commit messages/order were preserved with expected-parent, non-force updates; complete mapping is in local scratch `/workspace/work/p25-s4d-publication-map.json`.

Fresh offline `mvn clean verify` passed **917 tests, zero failures/errors, 55 display-related skips**, BUILD SUCCESS at 2026-10-17 03:41:20 UTC. No Java/test/migration/workflow change follows that result. Seven-document scope review, 100 relative file links, diff whitespace and local/published tree equality passed. The Maven helper restored the tracked build manifest; it is excluded from all commits. Final-head [CI run 38999593885](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38999593885), job `117299552158`, is running; earlier superseded-head runs are not final evidence.

No native S4B/S4C/P17 acceptance is inferred. The owner explicitly supplied S3 acceptance, and S3 is DONE. Remaining for this corrective slice: final-head clean/repeat/Xvfb CI, record actual results in the MR and local post-CI ledger, then owner review/merge. After confirmed merge, use fresh current main for one bounded user-entered NMR accounting slice; no external operating-policy values or mandatory policy record is needed. This post-publication evidence stays local so it does not replace the remote CI head with another documentation successor.


Additional read-only preparation: inspected donor NonprofitAccounting at `c697630ec1f784ebe8338d7300da6c9ac801b180` (`TxnSplit`, `PostingService.SplitInput`). It carries only an NMR boolean, not a reusable NMR item/settlement authority; no donor import is indicated. Current `SupplementalOpenItemQueryService.Row` is a generic kind/item projection without an NMR/event designation. Later implementation must explicitly identify NMR items and their canonical event/account/fund links; neither all Other Liabilities nor every legacy checked split is complete NMR detail. Preserve the existing lifecycle/balance authority and add only the missing explicit facts/projection in the owning slice.


### P25-S4D final-head CI and handoff — 2026-10-17 (UTC)

Final remote/MR head `06e6c9fc0fc992817610c6ab94f62aefae5ff749` passed [Maven PR Tests run 38999593885](https://github.com/benbaron/sca-jakarta-h2/actions/runs/38999593885), job `117299552158`. Fresh connected queries confirm the run completed successfully and every job step succeeded, including clean headless verification, repeated tests and the complete expanded JavaFX/Xvfb gate. MR #371's description records these results and local 917 tests/55 display skips. The earlier superseded-head runs are historical; the final successful head remains unchanged.

S4D is VERIFYING, not DONE: draft MR #371 remains open and unmerged at the confirmed head. Local branch `codex/P25-S4D-nmr-scope-correction` includes this local-only post-CI ledger after content parent `aa395df0bc0a53cc4e37c3ab604dfcbfb91afe81`; `git rev-parse HEAD` identifies the complete local handoff. No application/test/migration/workflow changes follow successful validation, and no known failure remains from executed checks. Source/publication scope is seven documents; relative links, whitespace, intended diff and publication tree equality passed. No new remote successor was published after the verified CI head.

Completed: accepted external-policy scope correction, optional-reference boundaries, confirmed S4C merge/CI, owner-supplied S3 desktop acceptance and DONE closeout, draft publication with exact blob/tree checks, fresh local full verification, successful final-head CI and read-only identification of the next accounting/portability gaps. Remaining: owner review/merge of MR #371. After merge confirmation, carry this local evidence forward from fresh current main, do not reuse the merged branch, and select the first bounded user-entered NMR accounting slice under the amended S4A contract. S3 desktop acceptance is satisfied; do not ask for it again. External operating-policy values and saved policy configuration are not implementation prerequisites. S4B/S4C/P17 native results remain separately unrecorded and are not inferred from the S3 response.


### P25-S4D merge and P25-S4E selection — 2026-10-16 (America/Denver)

Owner confirmed “merged.” Connected GitHub and native fetch confirm MR #371 merged at current main `33c1e99e48a78db9d0692064557cf6886de9cf4f`; final head `06e6c9fc0fc992817610c6ab94f62aefae5ff749` passed run `38999593885`. S4D is DONE, and S3 acceptance remains satisfied. Fresh `codex/P25-S4E-nmr-line-portability` carries local-only S4D verification ledger `8d7c3313` forward as `cf32b24f`; the merged branch is not reused.

Selected [S4E entered-line portability](P25-S4E-nmr-line-portability.md), the bounded existing data-loss prerequisite identified during S4D. Canonical Journal saves NMR true/false, but current import hardcodes false. Preserve user-entered flags through deterministic optional extension, strict preview, existing transaction/line conflict hashes and atomic history import. No rate/default/policy engine or liability inference. Parent S4 is IN_PROGRESS through this bounded slice, not complete; full NMR item/event detail, tie-out and explicit settlement remain subsequent work.

Read governing contract, adopted program, UI/editor rules and current snapshot/serializer/preview/native identity/import/canonical command/test authorities. Reuse budget/payment metadata patterns; donor split flag is not an interchange authority. Baseline compile and relevant existing tests are running before production edits. PR: none. Next: reproduce flag loss with a real interchange regression, implement this contract and run appropriate gates.


### P25-S4E implementation and focused verification

Implemented strict optional `transactionNmr` version 1 line-ID set, deterministic current-company export, serializer validation, source marked-line preview message, flag-aware existing transaction/line hashes and canonical import command flags. Missing extension preserves legacy new-line false semantics without clearing retained history. Changed flags require existing explicit Keep target; no separate write, migration, policy configuration, rate engine or generated NMR entry. Existing scoped history writer owns flags, amounts, dimensions, allocations, correction links and atomic audit.

Baseline compile and 23 existing budget/payment/preview tests passed. The real canonical receipt/remittance/reversal regression reproduced three marked lines becoming zero after export/import before production edits. The same history now passes with three true/four false flags, preserved event/fund dimensions and reversal relationship, zero policy versions, restart/idempotent import and a ready 20.00 outstanding liability tie-out. Final focused gate passed 13 tests, zero failures/errors/skips. New negative-test expectations were corrected to match existing preflight exceptions versus transactional rollback results; no production change was needed for those test assumptions.

The new real JavaFX Preview Save regression exercises ACCOUNTANT -> VIEWER -> ACCOUNTANT readiness and actual H2 flag/history persistence with no policy configuration; its Xvfb execution is running. Full Maven and final published-head CI remain required. Changed governing documents: PLAN, adopted program, S4A, selected S4E contract, SCLX specification, operation matrix and editor guidelines. Seven-document/110-relative-file-link validation and whitespace/scope review passed. No donor source, raw reference data, tracked build output or scratch asset is part of this implementation.


### P25-S4E local focused/UI handoff — 2026-10-16 (America/Denver)

The final focused gate passed 13 tests, zero failures/errors/skips. The real `PaneSaveCoverageTest.nmrLinePreviewSavePreservesEnteredFlagsWithBookkeepingPermission` passed under Xvfb (one test, zero failures/errors/skips), BUILD SUCCESS at 2026-10-17 04:13:38 UTC. It saves three flagged canonical lines with ACCOUNTANT permission after VIEWER denial and restores no policy versions. No production/test change follows these focused/UI results. Existing Save controls and geometry are reused.

Status VERIFYING on `codex/P25-S4E-nmr-line-portability`, based on confirmed main `33c1e99e48a78db9d0692064557cf6886de9cf4f`, with the preserved S4D post-CI ledger. The bounded implementation and 7 governing documents/110 local links are reviewed; whitespace/scope checks passed. Prepare a draft MR for this continued work, preserving both local commits and matching each published blob/tree. Run local full `mvn clean verify` and exact published-head clean/repeat/expanded-Xvfb CI before final completion; publication as draft does not claim those gates already passed. Record results locally and in the MR without replacing a successful remote head solely for evidence. No source/test change should follow passed gates unless a failure requires correction.

Full NMR item/event designation, liability tie-out and explicit settlement workflow remain subsequent parent S4 work. After this slice's merge, select that next bounded implementation from fresh main; external policies and S3 acceptance are no longer missing inputs. Owner checks are in the S4E contract; no new human desktop result is inferred.


### P25-S4E draft publication and running final gates — 2026-10-16 (America/Denver)

Continued the accepted corrected-plan work as a draft. Connected publication preserved both reviewed local commit messages/order, required each blob/tree SHA to match and advanced only the expected parent with force disabled. Fresh branch began at confirmed main `33c1e99e48a78db9d0692064557cf6886de9cf4f`. Draft [MR #372](https://github.com/benbaron/sca-jakarta-h2/pull/372) targets main; final published head `d971c7d12d55e7638726de9e3739112747dcb8cc` was confirmed through its PR metadata and native fetch, with exact local/published tree equality and zero content differences.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `cf32b24f24a4138afff406ab199aa8e5f42e09cc` | `5aef8ab2bc57b95d747955121dccd56253d20833` | `d13a04d5fbefd846aa187894356bc4669438e41b` |
| `06e24d19c3692b941349427a209b5b7f3ebfb24a` | `d971c7d12d55e7638726de9e3739112747dcb8cc` | `a68e966e1a49cdbba892101f12cb1b16dbbfba41` |

Publication review initially rejected the implementation PLAN blob, citing sensitive reference metadata and new-slice publication authorization. Fetched the exact existing public main-branch PLAN through connected GitHub (blob `46ff1503e795638ba9acbfb449eb7514cad9b705`) and proved all existing reference/history content unchanged apart from the listed NMR controller/status rows and appended slice/test/merge evidence. The exact upload retry passed with that evidence; no private source data or alternate transport was used. Complete mapping and prepared MR text remain in local scratch `/workspace/work/p25-s4e-publication-map.json` and `/workspace/work/p25-s4e-mr-body.md`.

Local full clean verification is running on unchanged source/test bytes; actual final-head GitHub checks must complete before a final pass is claimed. Current known passed gates: compile/23 existing baseline tests, primary regression reproduction, final focused 13 tests and genuine Xvfb Preview Save one test. Both final-gate results and the MR description will be updated after completion. This publication/verification ledger remains local so it does not restart remote CI solely for evidence.


### P25-S4E final local verification

Fresh offline `mvn clean verify` passed **925 tests, zero failures/errors, 56 display-related skips**, BUILD SUCCESS at 2026-10-17 04:26:40 UTC (2026-10-16 America/Denver). Final source/test bytes equal the already published MR #372 head `d971c7d12d55e7638726de9e3739112747dcb8cc`; only this local publication/evidence ledger follows. The tracked build manifest was restored by the Maven helper and remains excluded. No failure remains in executed local checks. The actual final-head GitHub run `39002064424`, job `117306898033`, passed clean verification and repeat tests; expanded JavaFX/Xvfb is still running. No final CI or human desktop pass is inferred before it completes.


### P25-S4E final-head CI and committed handoff — 2026-10-16 (America/Denver)

Final published head `d971c7d12d55e7638726de9e3739112747dcb8cc` passed [Maven PR Tests run 39002064424](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39002064424), job `117306898033`. Fresh connected queries confirm the run completed successfully and every job step succeeded: clean headless verification, repeated tests and the complete expanded JavaFX/Xvfb gate. MR #372's description records these actual results, local full 925 tests/56 display skips, focused 13 tests and the dedicated real Preview Save Xvfb test. No source/test change follows the successful gates.

Status remains VERIFYING, not DONE. Draft MR #372 is open/unmerged, base `33c1e99e48a78db9d0692064557cf6886de9cf4f`; final head and both matching publication trees are confirmed. Local branch `codex/P25-S4E-nmr-line-portability` includes this local-only evidence after implementation parent `06e24d19c3692b941349427a209b5b7f3ebfb24a`; resolve `git rev-parse HEAD` for the complete handoff tip. The successful published head is not replaced solely for this ledger. Worktree is clean after committing the evidence, tracked build artifacts remain excluded, and no known failure remains from executed checks.

Completed: S4D merge reconciliation and preserved verification ledger, scoped financial line-attribution prerequisite, strict codec/export/serializer/preview/conflict/import integration, canonical receipt/remittance/reversal and rollback/permission/ownership regressions, actual ACCOUNTANT Preview Save with VIEWER denial, seven governing documents/owner checks, matching-tree draft publication and final local/GitHub validation. Remaining: owner review/acceptance/merge of MR #372. No human S4E visual result is invented. Parent S4's explicit NMR item/event designation, liability tie-out and settlement workflow remains subsequent work; this flag fix is not full NMR completion.

Next exact action after confirmed merge: carry the local verification ledger forward onto fresh main, do not reuse the merged branch, inspect the current generic liability item/query and Journal correction authorities, and select explicit NMR item/event detail plus its GL tie-out as the next bounded slice. Preserve entered amounts and existing lifecycle balances; do not infer complete NMR detail from arbitrary legacy flags or all Other Liabilities. S3 acceptance is satisfied and external operating policies/optional policy records are not prerequisites.


### P25-S4E merge and P25-S4F selection — 2026-10-17 (UTC)

Owner confirmed “merged; proced.” Live GitHub and native fetch confirm MR #372 merged at current main `704a4a9e89e01d0ecce067c4ed274a315262b180`, final published head `d971c7d12d55e7638726de9e3739112747dcb8cc`; its clean/repeat/expanded-Xvfb run `39002064424` passed. S4E is DONE. Fresh `codex/P25-S4F-nmr-item-detail` carries the local-only S4E evidence as `2f055f26`; the merged branch is not reused.

Select [S4F explicit NMR item detail](P25-S4F-nmr-item-detail.md) IN_PROGRESS. Required reading: accepted S4A, adopted correction program, SCLX specification and UI rules/operation matrix/editor guidelines. Required inspection: canonical entry/correction, supplemental entity/DTO/integrity/query/reconciliation, Journal, Report Library, SCLX snapshot/identity/preview/commit, V81 and relevant existing tests. Preserve canonical balances; explicit recipient selection on an OTHER_LIABILITY allocation designates NMR, with account/fund/event derived from its linked ledger line. Require consistent recipient/account/fund/event for a stable NMR item; never infer designation from a legacy line checkbox or another liability. No policy/default/rate engine. Existing Journal Save/correction owns the entire atomic operation.

Baseline compile and relevant existing lifecycle/interchange/report tests are running before production edits. Next implement service validation, nondestructive schema, Journal selection/reload, as-of item detail/tie-out and versioned portability, then applicable focused/migration/JavaFX/full/CI gates. Parent S4 remains IN_PROGRESS; merchant-specific settlement attribution/reporting remains subsequent work.


### P25-S4F implementation and publication checkpoint — 2026-10-17 (UTC)

Implementation is VERIFYING on `codex/P25-S4F-nmr-item-detail`, based on confirmed main `704a4a9e89e01d0ecce067c4ed274a315262b180`, with local carry-forward `2f055f26`. The controller records the known pre-implementation head; `git rev-parse HEAD` identifies the complete implementation after commit. Publication/PR: not yet created at this checkpoint.

Completed: V82 nullable recipient FK and lifecycle/kind constraint, no data backfill; explicit NMR designation on canonical OTHER_LIABILITY allocations, stable company recipient and credit-normal liability/event requirement, consistent account/fund/event/recipient across an item; existing atomic save, correction, availability, ownership, permission and audit boundaries. Journal exposes the recipient selector and shared Save, preserves existing-item designation/reload/draft protection, and has a real company-remembered divider with independently scrolling Other Liabilities controls/table. A small directly blocking integrity repair rejects imported lifecycle inverses that change the source Event; preflight and final canonical history validation agree and the regression covers it. No unrelated panel rewrite.

NMR Liabilities in existing Report Library projects cumulative cutoff item balances using the existing supplemental lifecycle. It shows recipient/event/item/owning transaction, GL coverage by account/fund, unassigned openings, unclassified differences and actionable legacy flag/gross allocation gaps. Detail limits never truncate control totals and are disclosed in preview/text/CSV. Generic Other Liabilities and legacy flags are never automatically designated NMR. Optional versioned supplemental `nmr` metadata preserves recipient/item/event facts through deterministic export, strict preview, complete-transaction/detail conflicts, explicit Keep target, canonical atomic import and exact retry. Older files remain supported with classification unknown; no policy record or additional permission is required for accounting.

Actual validation: baseline compile plus 25 existing relevant tests passed; broader lifecycle/entry/correction/report/preview gate passed 73 tests without failures/errors/skips. Final focused gate after the last integrity/codec changes passed 13 tests, zero failures/errors/skips (`/workspace/work/p25-s4f-final-focused.log`, BUILD SUCCESS 16:01:25 UTC). Earlier broad JavaFX gate passed 11 tests without failures/errors/skips. After the Other Liabilities layout change, final real Journal Save/reload/recipient-only draft and VIEWER test passed under Xvfb, zero failures/errors/skips (`/workspace/work/p25-s4f-geometry-xvfb.log`, 16:00:14 UTC), including actual table overflow in both directions, larger text, child minima, moved real divider and control viewport overflow. The geometry test now lets JavaFX pulse nested VirtualFlow after resizing; the first immediate pre-pulse assertion failed and was corrected before this final pass. The earlier initial fixtures' audit field/user, migration kind and report catalog-count assumptions were also corrected; no failure remains from the executed final gates.

Seven governing documents and their 117 relative links, intended diff and whitespace checks passed. PLAN's published reference/history body remains unchanged except current controller/status rows and appended synthetic implementation/test/publication evidence; S4A source/reference metadata is unchanged from public main. No private donor ledger/workbook rows, scratch files, generated targets or workflow edits are included. Donor split-flag code was inspected and is not a suitable NMR item authority.

Remaining: full local `mvn clean verify`, exact published-head GitHub clean/repeat/expanded-Xvfb CI and owner review/acceptance/merge. A draft may be published while final gates run, with pending results stated explicitly. Keep post-publication/CI evidence local rather than restarting a successful remote head solely for documentation. New owner checks are in [S4F](P25-S4F-nmr-item-detail.md); no human desktop pass is invented. Parent S4 remains IN_PROGRESS: merchant/branch discharge evidence, unconfirmed withholding versus confirmed settlement and period movement columns remain subsequent work. After confirmed merge, select that bounded follow-on on fresh main, preserving explicit entered amounts and the same item/ledger authority. External operating policies and P25-S3 acceptance are not missing prerequisites.


### P25-S4F draft publication and final gates — 2026-10-17 (UTC)

Published continued authorized work as draft [MR #373](https://github.com/benbaron/sca-jakarta-h2/pull/373), targeting main `704a4a9e89e01d0ecce067c4ed274a315262b180`. Connected publication preserved both local commit messages/order, verified all uploaded blob hashes and both exact tree hashes, created the fresh branch at verified main and advanced only the expected parent with force disabled. Live PR metadata and native fetch confirm final published head `2e401a02da5909cbe62004d393698f07b4cfe3e7`, tree `78184ea3bba209b5fad65e4875db1fd3709b16ea`, draft/open/unmerged. Native content diff against local implementation is empty.

| Local commit | Published commit | Matching tree |
|---|---|---|
| `2f055f26b9b0b9ac504100c626e999b798e8392b` | `24aabcfc488eb34c1785bed06a8158cc942beae5` | `489aa6974b3d01cf08615fc634ffea91cded979f` |
| `4a05a65265f1c95c4e74eba7e67f32621fd6bbca` | `2e401a02da5909cbe62004d393698f07b4cfe3e7` | `78184ea3bba209b5fad65e4875db1fd3709b16ea` |

Full local clean verify is running on unchanged implementation bytes. Final-head [Maven PR Tests run 39047889348](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39047889348), job `117440199637`, is running clean verification, with repeat tests and expanded production JavaFX/Xvfb still pending. MR description states those gates are pending; no full pass is claimed yet. Mapping and draft text are in scratch `/workspace/work/p25-s4f-publication-map.json` and `/workspace/work/p25-s4f-mr-body.md`. This publication/final-CI evidence remains local rather than pushing a documentation successor solely to restart checks. No new native human acceptance is inferred.


### P25-S4F final local verification

Full local offline `mvn clean verify` passed **935 tests, zero failures/errors, 57 display-related skips**, BUILD SUCCESS at 2026-10-17 16:08:11 UTC, elapsed 3:04. Source, migration and test bytes equal final published MR #373 head `2e401a02da5909cbe62004d393698f07b4cfe3e7`; only this local evidence/controller follows. The helper restored the tracked manifest, so the sole worktree change is PLAN. Focused 13 tests, broad 73 tests, broad Xvfb 11 tests and final real Journal Save/geometry Xvfb test are recorded above. No failure remains in executed local final gates. Governing-document link validation now checks 118 links across seven documents. Exact-head CI is still running; no GitHub final pass or new human desktop acceptance is claimed yet.


### P25-S4F final-review correction prerequisite

Final source review found an existing writable path that could detach inherited supplemental reversal facts: `TransactionEntryService.update` could replace a lifecycle inverse's lines/details. The new real NMR regression reproduced the defect against implementation `4a05a652`: Journal accepted an added INCREASE allocation on a payment inverse instead of rejecting it (`/workspace/work/p25-s4f-reversal-edit-reproduction.log`, one expected regression failure: no exception thrown). This can duplicate item detail despite unchanged GL amounts. The initial test compile used an incorrect correction-result accessor and was corrected before that reproduction; no production change preceded the reproduced accounting failure.

Narrow prerequisite repair: entry-service updates now reject canonical inverses with inherited supplemental items before deleting any lines/details. Users can reverse the reversal through the existing correction service and enter the corrected transaction. Header-only direct correction and supported canonical reversal lineage retain their existing authority. No separate persistence or new UI command is introduced. The regression also covers a balanced attempted removal of the liability line, zero audit/metadata residue, unchanged cutoff balance and a successful dated reverse-of-reversal. This directly protects the selected NMR detail/correction contract; no unrelated correction redesign.

The previously recorded 935-test local full pass and CI run certify the preceding head only. Current repair requires its focused tests, a new complete local clean verify and a new exact published-head CI run. Do not use the preceding head's successful checks to certify the successor. Parent S4 scope and owner acceptance remain unchanged.


The inherited-reversal guard and regression passed the focused release gate: **38 tests, zero failures/errors/skips**, BUILD SUCCESS at 2026-10-17 16:15:32 UTC (`/workspace/work/p25-s4f-reversal-guard-focused.log`), including existing entry/correction/integrity/cutoff tests and NMR interchange. Publish this five-file successor with the same expected-head/tree safeguards, then rerun full local and exact-head CI; all initial slice source and schema remain unchanged beyond this directly blocking guard.


### P25-S4F correction-guard successor publication

Published reviewed local `5ceab134f84e9d39a86c5a06be84eeb534cee1cf` as `1a353fdc54cb815008b5df59527a1d89b9e8d77b`, matching tree `659714dce7e3fc16ce771cae77d7bdd66ad7f7e8`. All five blobs/tree matched; commit message/order preserved; branch advanced only expected prior head `2e401a02da5909cbe62004d393698f07b4cfe3e7` with force disabled. MR #373 remains draft/open/unmerged at the confirmed successor, verified through PR metadata and native fetch/content equality. Its description explicitly distinguishes the preceding full pass from pending successor gates. Full local clean verify is running in `/workspace/work/p25-s4f-final-clean-verify.log`; exact-head GitHub checks must pass before final handoff. The earlier run is superseded, not evidence for this successor. No further source changes are planned absent a failed check.


### P25-S4F corrected final local verification

Fresh full local offline `mvn clean verify` passed **936 tests, zero failures/errors, 57 display-related skips**, BUILD SUCCESS at 2026-10-17 16:19:56 UTC, elapsed 3:05 (`/workspace/work/p25-s4f-final-clean-verify.log`). This gate includes the reproduced reversal-edit guard and supported reverse-of-reversal regression. Final source/schema/test bytes equal local `5ceab134f84e9d39a86c5a06be84eeb534cee1cf` / published `1a353fdc54cb815008b5df59527a1d89b9e8d77b`, matching tree `659714dce7e3fc16ce771cae77d7bdd66ad7f7e8`. The Maven helper restored the tracked manifest. No failure remains in executed local final gates; subsequent changes are local verification documentation only.

Actual current-head GitHub [run 39048718804](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39048718804), job `117442627734`, is running the clean/repeat/expanded-Xvfb gate. The preceding head's run `39047889348` ultimately passed every stage, but it does not certify the guard successor. The MR description now records the corrected 936-test local pass and pending current-head CI explicitly. Remain VERIFYING pending that result and owner review/acceptance/merge; no new human desktop acceptance is inferred.


### P25-S4F final-head CI and committed handoff — 2026-10-17 (UTC)

Final published head `1a353fdc54cb815008b5df59527a1d89b9e8d77b` passed [Maven PR Tests run 39048718804](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39048718804), job `117442627734`. Fresh direct run metadata confirms that exact head, PR #373, completed/success; fresh job and step reads confirm clean headless verification, repeat tests, complete expanded production JavaFX/Xvfb and every setup/cleanup step succeeded. MR description records actual final results and the reversal-edit prerequisite, with local full **936 tests / zero failures/errors / 57 display skips**, focused 38 tests, prior broader 73-test and 11-test JavaFX gates, and the dedicated real Journal Save/geometry Xvfb test. No source/test/schema changes follow these successful gates.

Status remains VERIFYING, not DONE. Draft MR #373 is open/unmerged and mergeable at the confirmed head; owner review, native checks/acceptance and merge remain. Local `codex/P25-S4F-nmr-item-detail` carries this local-only PLAN/S4F verification ledger after implementation parent `5ceab134f84e9d39a86c5a06be84eeb534cee1cf`; `git rev-parse HEAD` identifies the complete committed handoff tip. The successful published head is not replaced solely for this ledger. All three publication mappings/trees are recorded above and in scratch; final application/test/schema bytes equal the published head. Seven governing documents / 118 relative links, intended diff and whitespace checks passed. Tracked build artifacts, scratch assets and private reference rows remain excluded. No known failure remains in executed final checks; no new human desktop acceptance is invented.

Completed: confirmed S4E merge and preserved its verification ledger; explicit stable recipient/item/event designation on existing supplemental allocations; canonical balance/correction/ownership/permission/audit protection and nondestructive V82; real shared Journal Save/dirty/reload with remembered scrolling/divider geometry; cutoff NMR detail and conservative GL coverage/export/drill-through; strict deterministic SCLX addressing/conflicts/retry/rollback; reproduced and fixed lifecycle inverse Event mismatch and inherited Journal rewrite protection; final full local and exact-head GitHub verification.

Remaining parent S4 scope: explicit merchant versus branch discharge/evidence attribution, unconfirmed withholding versus actual settlement, period movement presentation and their portability/reporting acceptance. Next exact action after owner-confirmed merge: start a fresh branch from current main, carry this local ledger forward, inspect canonical payment/merchant receivable and supplemental item/reference authorities, and select that bounded settlement-evidence slice. Preserve explicit entered amounts and existing single-ledger/item balances; do not ask for external operating policies or a saved policy prerequisite. P25-S3 acceptance remains passed. Do not activate S5 or claim full P25-S4 complete from this bounded detail slice.


### P25-S4F merge and P25-S4G selection — 2026-10-17 (America/Denver)

Owner confirmed “merged. proceed to the next slice.” Connected GitHub/native fetch confirm MR #373 merged at current main `b2ff4aa627961058ea87aa4ea35a531277f66b50`; final published head `1a353fdc54cb815008b5df59527a1d89b9e8d77b` passed run `39048718804`. S4F is DONE. Fresh `codex/P25-S4G-nmr-settlement-evidence` carries the local-only final verification as `95d371c2`, without reusing a merged branch. No additional native desktop result is invented.

Select [S4G](P25-S4G-nmr-settlement-evidence.md): optional explicit per-allocation movement/evidence, merchant discharge tied to the existing company-owned linked-line Merchant, shared Journal Save/correction, range-based opening/movement/closing projection and versioned SCLX. Existing canonical amounts and supplemental dated effects remain sole balance authority. No rate/policy engine, defaults or guessed settlement. Dedicated unconfirmed-withholding/receivable observations and gross/payout/fee tie-out remain a following bounded S4 slice; the withholding variant must prove no liability discharge is inferred.

Required reading: accepted S4A/S4F, adopted program, SCLX and three UI rules. Required inspection: supplemental entity/DTOs/entry/correction/integrity/as-of projection, NMR liability query/report, Report Library range/date pipeline, Journal fields/save/geometry, current SCLX metadata/parser/identity/commit, V82, and relevant migration/service/interchange/JavaFX tests. Donor PostingService has split flags but intentionally unwired merchant/activity dimensions; retain production stable IDs and no donor persistence. Baseline compile and existing NMR/lifecycle/correction tests run before production edits.

### P25-S4G implementation and publication checkpoint — 2026-10-17 (America/Denver)

S4G is VERIFYING on `codex/P25-S4G-nmr-settlement-evidence`, base `b2ff4aa627961058ea87aa4ea35a531277f66b50`, carrying S4F final evidence as `95d371c2`. This checkpoint records the known base; `git rev-parse HEAD` identifies the implementation after commit. PR/publication: not yet created. Completed V83 nullable user facts/constraints (no defaults/backfill), canonical atomic validation/correction/audit, existing shared Journal Save/dirty/reload and real remembered scrolling/divider, range report from existing dated effects and cutoff coverage, strict V1/V2 SCLX/hash/conflict/Keep target/permission/retry/rollback. A directly relevant import guard requires lifecycle inverses to retain source Merchant relationships, enforced by preflight and canonical history writer.

Baseline compile and 28 existing tests passed before production edits. Final focused gate passed **52 tests, zero failures/errors/skips**, BUILD SUCCESS 2026-10-18 00:37:11 UTC (`/workspace/work/p25-s4g-final-focused.log`). Real Xvfb gate passed **11 tests, zero failures/errors/skips**, BUILD SUCCESS 2026-10-18 00:36:00 UTC (`/workspace/work/p25-s4g-xvfb.log`), including actual movement/evidence selection, shared Save/reload, evidence-only draft protection and existing laptop/large-text geometry. Earlier 34/42-test gates also passed; final gates cover the final source. Tests use synthetic accepted amounts, not policy inputs. No new human desktop acceptance is claimed.

Eight governing documents are updated: PLAN, accepted S4A, S4F closeout, S4G contract/owner checks, adopted program, SCLX specification, interface matrix and editor guidelines. Existing reference/source metadata remains unchanged; no donor ledger rows, scratch files, generated targets or workflow edits are included. Remaining: reviewed matching-tree draft publication, full local clean verify and exact published-head clean/repeat/expanded-Xvfb CI, then owner review/merge. Keep post-publication evidence local and update MR results without replacing a successful remote head solely for documentation. After merge, select dedicated unconfirmed-withholding/receivable facts and gross/payout/fee presentation on fresh main. Parent S4 remains IN_PROGRESS; no policy input or repeated S3 acceptance is required.

### P25-S4G draft publication and full local verification — 2026-10-17 (America/Denver)

Draft [MR #374](https://github.com/benbaron/sca-jakarta-h2/pull/374) is open at exact head `10220a3be6654d464884a50ca9a017d7e0e53eb0`, base `b2ff4aa627961058ea87aa4ea35a531277f66b50`. Clean reviewed local history was confirmed before commit/publication; the temporary tracked manifest deletion during Maven clean was solely the wrapper's managed build behavior and restored at completion. Connected publication preserved both local commit messages/order: carry `95d371c275cb2bc82d7a5784d0d2ded9c1bc7789` → `a3080e6e5a4dcb9454d66fa05642c7e6a460d6d2`, matching tree `9c61f3bf6227dc791e9eb5a1df14612b8c64a316`; implementation `e94861fae89b39ed68c40647e2874f86173c2d82` → final published head, matching tree `2ca63d53a5e916122bb8171b6d134c93c93a54e6`. Every blob matched; fresh branch advanced only from the expected base with force false. Native fetch and empty local-implementation/remote diff independently confirmed the published tree. Mapping and MR body are in local scratch `/workspace/work/p25-s4g-publication-map.json` and `/workspace/work/p25-s4g-mr-body.md`.

Full local offline `mvn clean verify` passed: **945 tests, zero failures/errors, 57 display-dependent skips**, BUILD SUCCESS at **2026-10-18 00:44:28 UTC**, elapsed 3:11 (`/workspace/work/p25-s4g-clean-verify.log`). The separate real Xvfb gate passed all 11 tests. Eight governing documents / 126 relative links and whitespace checks passed. No production/test/schema changes followed final gates; tracked generated artifacts are excluded. MR records actual results and owner checks. Exact-head GitHub run `39083944191`, job `117546465691`, is running; no CI success is claimed yet. Status VERIFYING; owner review/acceptance/merge remain. This publication/verification evidence is local only and will not replace the successful remote head solely for documentation.

### P25-S4G final-head CI and committed handoff — 2026-10-17 (America/Denver)

Exact published head `10220a3be6654d464884a50ca9a017d7e0e53eb0` passed [Maven run 39083944191](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39083944191), job `117546465691`: clean headless verification, repeat tests and expanded production Xvfb route compliance all succeeded. The run API confirms that exact head and completed/success conclusion; the job's individual stages also confirm success. Draft [MR #374](https://github.com/benbaron/sca-jakarta-h2/pull/374) now records final local/focused/JavaFX and exact-head CI results. No application/test/schema changes followed passed gates. No known failure remains in executed checks.

Status VERIFYING, not DONE: owner review/acceptance and merge remain. Local branch `codex/P25-S4G-nmr-settlement-evidence` carries this local-only PLAN/S4G evidence after implementation parent `e94861fae89b39ed68c40647e2874f86173c2d82`; `git rev-parse HEAD` identifies the committed handoff tip. Final application/test/schema bytes equal the successful published head. The successful remote head is not replaced solely for evidence. Eight governing documents / 126 relative links and final whitespace/scope review passed. Tracked build artifacts, scratch assets and private reference rows remain excluded; worktree is clean after evidence commit. No human desktop result is invented.

Completed: S4F merge reconciliation and carry-forward, optional explicit movement/evidence with nondestructive V83 and canonical atomic validation/correction/audit, shared real Journal Save/dirty/reload and existing responsive table/divider geometry, opening-to-closing range projection from existing dated effects and GL coverage with full totals, versioned deterministic lossless SCLX, lifecycle inverse Merchant protection, synthetic withholding-without-discharge and classification/correction/import regressions, owner notes, matching-tree draft publication and final local/GitHub verification.

Next exact action: owner reviews/accepts/merges MR #374 using the S4G checks. On confirmed merge, refresh main, carry this local evidence onto a fresh branch, then inspect/select dedicated unconfirmed-withholding observations linked to merchant receivables and gross/payout/fee/remaining-receivable presentation. Parent S4 remains IN_PROGRESS. Existing entered amounts, stable item/ledger authority and withholding-without-discharge contract continue; external operating policies and repeated P25-S3 acceptance are not prerequisites. Do not implement another slice before this merge.

### P25-S4G merge and P25-S4H selection — 2026-10-18 (UTC)

Owner confirmed “merged ; proceed.” Connected GitHub/native fetch confirm MR #374 merged at main `fb37feba028237d5ae371799f85d409843c8e571`; final head `10220a3be6654d464884a50ca9a017d7e0e53eb0` passed run `39083944191`, clean/repeat/expanded JavaFX. S4G is DONE. Fresh `codex/P25-S4H-merchant-withholding` carries its local final evidence as `b173629a`; the merged branch is not reused. No new desktop result is invented.

Select [S4H](P25-S4H-merchant-withholding.md): optional gross-receipt/settlement facts on existing RECEIVABLE allocations, dated nonfinancial unconfirmed-withholding observations/evidence, normalized links to actual payout/fee/evidenced NMR-discharge splits, shared Journal Save, derived opening/gross/payout/fee/discharge/remaining report and strict lossless SCLX. Existing lifecycle alone owns availability and balances. Missing facts remain unknown; no standalone observation posting or balancing plug, policy input/default or second settlement balance. Multiple items require separate component splits so no canonical component is counted twice.

Required reading: accepted S4A, S4G, adopted program, SCLX and all three UI rules. Inspected entity/DTOs, canonical entry/update/correction/audit/availability, supplemental dated effects, Journal Save/dirty/geometry, range report pipeline, SCLX export/parser/identity/commit and existing fixture/tests/migrations. Donor PostingService retains intentionally unwired Merchant/Event; production stable IDs and canonical relations are retained, with no donor persistence or private ledger rows. Baseline compile passed; existing accounting/interchange/correction tests run before production edits.

### P25-S4H implementation and publication checkpoint — 2026-10-18 (UTC)

S4H is VERIFYING on `codex/P25-S4H-merchant-withholding`, base `fb37feba028237d5ae371799f85d409843c8e571`, with S4G local evidence carried as `b173629a`. The controller records the known base; `git rev-parse HEAD` identifies the reviewed implementation after commit. PR/publication: not yet created. Completed nullable V84 role/observation/evidence and normalized unique canonical component links, no backfill; existing company-lock/canonical Save/ownership/availability/period/reconciliation/correction/Delete/atomic audit authority; replacement link remapping and inherited dated reversal effects; shared Journal fields, optional exact company money editing, actual remembered scrolling/divider; derived opening/gross/payout/fee/NMR/remaining report with full totals/GL coverage and visibly unknown offsets; strict deterministic SCLX shape/value/link/inverse/hash/conflict/Keep target/import/retry/restart/rollback.

Baseline compile and 32 relevant existing tests passed before production edits. Final focused gate passed **63 tests, zero failures/errors/skips**, BUILD SUCCESS **2026-10-18 01:20:18 UTC** (`/workspace/work/p25-s4h-final-focused.log`). Final real Xvfb gate passed **12 tests, zero failures/errors/skips**, BUILD SUCCESS **2026-10-18 01:22:15 UTC** (`/workspace/work/p25-s4h-final-xvfb.log`), including actual role/component/observation Save/reload, evidence-only draft and shared File Save route, exact four-decimal preservation and blank-versus-zero behavior, VIEWER denial and laptop/large-text real child/control/table overflow/divider movement. Earlier preflight regressions exposed value/domain exceptions escaping Preview; malformed metadata now yields blocking messages, and strict malformed/legacy/conflict/rollback tests pass. No known failure remains from final executed gates; no human desktop result is inferred.

Eight governing documents / 133 relative links and intended diff/whitespace review passed. Existing public source/reference metadata remains unchanged; no private donor ledger/workbook rows, scratch assets, generated targets, dependency or workflow changes are included. Donor split code has intentionally unwired Merchant/Event, so production stable relations are retained. Remaining: matching-tree draft publication, full local clean verify and exact published-head clean/repeat/expanded-Xvfb CI, then owner review/acceptance/merge. Keep later evidence local and update MR results without replacing a successful remote head solely for documentation.

After confirmed S4H merge, review parent S4's A10-N01–N10 financial acceptance and remaining form-specific boundaries against current implementation before selecting its closeout or next unblocked P25 slice. Parent S4 stays IN_PROGRESS. No external operating policy, policy record, automatic assessment/settlement or repeated P25-S3 acceptance is required.

### P25-S4H draft publication and full local verification — 2026-10-18 (UTC)

Draft [MR #375](https://github.com/benbaron/sca-jakarta-h2/pull/375) is open at exact head `5fe764e8aeb26b98d4a281991daa559175df44a3`, base `fb37feba028237d5ae371799f85d409843c8e571`. Clean reviewed local history was confirmed before publication. Connected GitHub preserved messages/order: carry `b173629adac0a2e61add01b8addec4f51a34b503` → `a4e625f13894bea5fb72080b6738df41685fb691`, matching tree `4320415bd4aa264a1ae08efd600b5d240d923aed`; implementation `629ec190e35a48f066f0138708b9b9a6a525345b` → final published head, matching tree `04ab02f24cbdc028dc4ede743804a8e39e82a1f7`. Every blob matched. The fresh branch advanced from the expected base with force false; native fetch and empty local-implementation/remote diff independently confirmed final bytes. Mapping and MR body remain in local scratch `/workspace/work/p25-s4h-publication-map.json` and `/workspace/work/p25-s4h-mr-body.md`.

Full local offline `mvn clean verify` passed **957 tests, zero failures/errors, 58 display-dependent skips**, BUILD SUCCESS **2026-10-18 01:25:56 UTC**, elapsed 3:16 (`/workspace/work/p25-s4h-clean-verify.log`). Independent final Xvfb passed 12 tests and focused gate passed 63, all without failures/errors/skips. Eight governing documents / 133 relative links and whitespace/scope review passed. The wrapper restored its temporary tracked manifest at build completion; no generated artifacts are staged. No application/test/schema changes followed the final gates. MR records actual validation and owner checks. Exact-head run `39086527295`, job `117554163127`, is running; no CI success is claimed at this checkpoint. Status VERIFYING pending CI and owner review/acceptance/merge. This later publication/verification evidence is local only, preserving the successful remote source head without a documentation-only CI restart.

### P25-S4H final-head CI and committed handoff — 2026-10-18 (UTC)

Exact published head `5fe764e8aeb26b98d4a281991daa559175df44a3` passed [Maven run 39086527295](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39086527295), job `117554163127`: clean headless verification, repeat tests and expanded production Xvfb route compliance all succeeded. Run API confirms the exact head and completed/success conclusion; individual job stages also confirm success. Draft [MR #375](https://github.com/benbaron/sca-jakarta-h2/pull/375) records final local full/focused/JavaFX and exact-head CI results. No application/test/schema change followed the passed gates; no known failure remains in executed final checks.

Status VERIFYING, not DONE: owner review/acceptance and merge remain. Local `codex/P25-S4H-merchant-withholding` carries this local-only PLAN/S4H evidence after implementation parent `629ec190e35a48f066f0138708b9b9a6a525345b`; `git rev-parse HEAD` identifies the complete committed handoff tip. Final source/test/schema bytes equal the successful published head. The successful remote head is not replaced solely for evidence. Eight governing documents / 133 relative links, whitespace and intended-scope review passed; generated targets, scratch assets, private reference rows, dependency and workflow changes remain excluded. Worktree is clean after committing the evidence. No human desktop acceptance is invented.

Completed: verified S4G merge and carried its final ledger, nullable V84 facts and normalized unique canonical component links, optional dated observations without financial effects, canonical validation/ownership/availability/permissions/correction/Delete/replacement/audit, real shared Journal Save/dirty/reload and company-formatted exact optional money, remembered responsive divider/scrolling, derived range report with full totals/control gaps and dated corrections, strict deterministic lossless SCLX/conflict/Keep target/retry/restart/rollback, migration/recovery/multiple-item/permission/legacy regressions, owner notes, matching-tree draft publication and final full local/GitHub verification.

Next exact action: owner reviews/accepts/merges MR #375 using the S4H checks. On confirmed merge, refresh main and carry this local evidence onto a fresh branch. Review parent S4 A10-N01–N10 against the implemented financial behavior and distinguish any remaining form-specific presentation from accounting acceptance before selecting its closeout or next unblocked P25 slice. Parent S4 remains IN_PROGRESS. Operating-policy inputs, saved policy versions and repeated P25-S3 acceptance are not prerequisites. Do not implement another slice before this merge.

### P25-S4H merge and P25-S4I selection — 2026-10-17 (America/Denver)

Owner confirmed “merged ; proceed.” Connected GitHub/native fetch confirm MR #375 merged at main `030b7d3bed957c614a754ffe55bcc6ea9affe3ff`; final head `5fe764e8aeb26b98d4a281991daa559175df44a3` passed run `39086527295`. S4H is DONE. Fresh `codex/P25-S4I-nmr-acceptance` carries its final local ledger as `496ba7c9`; merged branches are not reused. No new human desktop result is inferred.

Review of A10-N01–N10 found the next bounded accounting gap: native Journal New Save has neither an in-flight guard nor a stable create identity; two commands before callback both call `enter(command)` and create distinct receipts/obligations. SCLX retry identity and company-lock over-application already exist, but they do not make native new-entry retries idempotent. Select [S4I](P25-S4I-nmr-acceptance.md), encompassing this directly relevant repair plus missing integrated fiscal/concurrency/protection evidence and an honest acceptance matrix. Parent S4 remains IN_PROGRESS; form-specific mappings and S5 functional-expense meanings remain separate.

Design: use one draft operation UUID as the existing Txn portable identity and the existing TRANSACTION_ENTERED AuditEvent portable identity. The immutable factual create event retains a versioned normalized command hash in its reason; it remains after direct deletion. Exact same-company retry returns the current retained transaction without writes; changed/foreign/deleted-key reuse rejects. No new request table, balance, migration, policy engine or separate posting workflow. Existing SCLX preserves both intrinsic identities and opaque audit reason; native request comparison is bound to the original database/company/reference IDs, not a cross-database client replay promise. Existing whole-database backup retains complete retry state.

Required reading/inspection: S4A, S4F–S4H, adopted program/baseline A10, ledger authority and period/lifecycle, SCLX, three UI rules; Txn/AuditEvent, command/view/entry/correction/ownership/authorization/availability, Journal/UiAsync/permission gate/command routing, projections/reports, audit export/import identity and existing NMR/merchant/interchange/protection/concurrency/JavaFX tests. Donor PostingService retains intentionally unwired Merchant/Event and no appropriate native retry seam; keep production canonical authorities, without importing donor persistence/private rows. Baseline compile passed and existing relevant tests run before production edits. Reproduce repeated actual Save before repair.

### P25-S4I implementation and focused verification — 2026-10-17 (America/Denver)

Pre-edit offline compile passed at 2026-10-18 03:53:17 UTC; 24 relevant baseline tests passed at 03:59:32 UTC. Actual JavaFX regression before repair failed exactly once: button Save plus immediate File Save produced 2 receipts where 1 was required (no errors/skips). The repaired regression passes and verifies disabled Save/New, removed shell Save capability, guarded direct Save/New, and restored capability after completion.

Implemented one UUID per native New draft, stable across failures, existing canonical create/audit identity with immutable normalized-input hash, exact no-write replay after lost response/restart/concurrency/correction/protection, changed/foreign/deleted/colliding identity rejection, atomic rollback and competing NMR remittance protection. Busy UI guards cover shared commands and edit/delete/load lifecycle without a second Save or geometry change. No schema, ledger, policy/rate engine, migration, dependency, workflow or donor data changes. Existing SCLX retains native receipt UUID/hash provenance without promising source-native-ID request replay in a remapped target.

Focused final run: 12 tests, zero failures/errors/skips, BUILD SUCCESS at 2026-10-18 04:09:03 UTC (`JournalCreateRequestTest` 7, `NmrFinancialAcceptanceTest` 1, `SclxMerchantSettlementRoundTripTest` 4). Initial combined test used nonexistent debit/credit entity attributes; corrected its independent GL assertion to canonical amountSigned/normal balance, reran and passed. The seven new replay tests passed on both runs. Actual Xvfb final run: 16 tests, zero failures/errors/skips, BUILD SUCCESS at 04:10:27 UTC (`JournalPaymentReferencesTest`, `AppPanelCommandCapabilityTest`, `ProductionWorkspaceCommandRoutingTest`). New year-boundary test ties prior opening 40, collections 100, merchant discharge 30, branch 110, closing zero; canonical bank 880/income 900/fee 20 and receivable/liability zero. Row-limited production reports and native-receipt SCLX round-trip/restart/reimport agree without policy records.

[S4I](P25-S4I-nmr-acceptance.md) maps all A10 cases to named tests and explicit limits. Updated S4A/current S4H status, adopted program, ledger/lifecycle, shared UI Save/matrix, and SCLX contracts. Reviewed production diff, existing normalized persistence semantics, permission gate/capability forwarding, retained audit/delete behavior and final scope. Full local clean verify, matching-tree draft publication and exact published-head CI remain pending; no full-suite or human desktop result is claimed yet. Next exact action: run those gates and record the concrete draft/head/results. Parent S4 remains IN_PROGRESS; existing P25-S3 desktop pass remains accepted, with no operating-policy coding prerequisite.

### P25-S4I first publication and full-suite guard correction — 2026-10-17 (America/Denver)

Draft [MR #376](https://github.com/benbaron/sca-jakarta-h2/pull/376) opened at `dca99ff7f6e2fe7891f22894ce58cabba63b95d7` from unchanged main `030b7d3b`. Local `496ba7c9` maps to remote `f0dfeb1ec11092daaf3b5d3c0501a673e41ad6c6`, tree `82ff9be92d1902300870155e13140aef72193cb4`; implementation local `6ad9bb37` maps to the draft head, tree `9970309193b4aa45482b60b2c51c5ec2d4f7418d`. Every blob/tree and both messages/order matched; expected-parent ref lease with force false, native fetch and empty tree diff verified the published content. Initial run `39096358025` is superseded by the following correction and is not final CI evidence.

Initial full clean verify completed at 2026-10-18 04:14:37 UTC: 965 tests, one failure, zero errors, 58 display-dependent skips. The sole failure was `JournalWorkspacePortSourceTest.journalDelegateRemainsServiceBacked`, which still required literal `service.enter(command)` instead of the new native retry-aware `service.enter(command, requestId)`. Updated that existing architecture guard to require the new canonical overload, retaining all service/update/correction/persistence assertions. Production behavior is unchanged. Its six tests passed at 04:15:22 UTC. The accounting, native retry, integrated GL/report/SCLX and real JavaFX gates passed; the initial full run is explicitly not a pass.

This successor includes only the corrected guard and PLAN/S4I evidence. Rerun full local clean verify and publish the matching successor tree on the same draft; inspect clean/repeat/expanded JavaFX CI on that exact new head. Ten governing documents' relative links and whitespace review pass. No further implementation changes are planned unless a final check fails. Required owner desktop/review/merge checks remain; no human result is inferred.

### P25-S4I corrected publication and full local verification — 2026-10-17 (America/Denver)

Published local guard correction `2121575ebe9bcb195bd662c93a6be8411835dcda` as `de44946b1cec58ecdd4743585eb095deaf13cf9a`, matching tree `f54b94a14094b76a7f9afead3fa1daeb5fac0a46`. All three successor blobs/tree and commit message/order matched; ref advanced from expected `dca99ff7` with force false. MR #376 remains draft/open/unmerged at the confirmed successor; native fetch/content comparison is empty. Final-head Maven run `39096513048`, job `117584021290`, owns final CI; the previous head/run is superseded. Publication maps and MR text are in scratch `/workspace/work/p25-s4i-publication-map.json` and `/workspace/work/p25-s4i-mr-body.md`.

Final local offline `mvn clean verify` passed **965 tests, zero failures/errors, 58 display-dependent skips**, BUILD SUCCESS **2026-10-18 04:19:05 UTC**, elapsed 3:20 (`/workspace/work/p25-s4i-final-clean-verify.log`). Focused 12, actual Xvfb 16 and corrected source-guard 6 tests also passed. Production/test/schema bytes match final published head; no generated files are staged and the wrapper restored the temporarily tracked manifest. Ten governing documents / 143 relative links and whitespace/scope review passed before this evidence append. Exact-head CI is still running; no final GitHub result or human desktop acceptance is claimed at this checkpoint. Later publication/verification evidence stays local to preserve the tested remote head without a documentation-only CI restart.

### P25-S4I final-head GitHub validation and handoff — 2026-10-17 (America/Denver)

Final [Maven PR Tests run 39096513048](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39096513048), job `117584021290`, completed **success** at **2026-10-18 04:28:42 UTC** for exact head `de44946b1cec58ecdd4743585eb095deaf13cf9a` and branch `codex/P25-S4I-nmr-acceptance`. Independently read run metadata plus job/step results: clean headless verification, repeat suite, expanded production JavaFX routes and cleanup all succeeded. API head/branch match the published successor, not the superseded run. MR #376 remains draft/open/unmerged at that head, with actual final validation, scope and owner checks in its description. Final full local passed 965 tests / zero failures/errors / 58 display skips; separate actual Xvfb passed 16, focused 12, source guard 6. No failure remains in final executed gates.

Completed: verified S4H merge and carried final evidence; reproduced duplicate actual Save before repair; guarded native shared Save/editor lifecycle with live capabilities; durable no-write retry acknowledgment using existing canonical/audit UUIDs and immutable input receipt; lost response/restart/concurrent/changed/foreign/deleted/colliding/protected/late rollback cases; competing NMR remittances; integrated year-boundary GL and three report paths; lossless native-receipt SCLX/restart/conflict/retry/rollback evidence; named A10 acceptance map and owner notes; matching-blob/tree nonforce draft publication and final full local/GitHub gates. No new schema, secondary persistence, operating-policy engine, defaults/rates, workflow/dependency or generated artifact change.

Remaining: owner review/native checks and merge using [S4I](P25-S4I-nmr-acceptance.md). Status VERIFYING, not DONE; parent S4 remains IN_PROGRESS and no human desktop result is inferred. Existing P25-S3 desktop pass and removal of the operating-policy gate remain accepted. Form-specific legal mappings and independent functional-expense meanings remain with P27/S5.

This final PLAN/S4I evidence is a local-only successor (commit subject `Record Journal retry publication and final verification`); active_head identifies the verified remote implementation, while `git rev-parse HEAD` identifies the complete local handoff. Preserve remote head/CI rather than publishing a documentation-only successor. Next exact action: owner reviews/accepts/merges MR #376. After a merge report, fetch/verify current main and final-head CI, carry this local evidence onto a fresh branch, and review parent S4 acceptance/closeout before selecting the next unblocked authorized slice. Do not start another slice before merge and do not reuse a merged branch.

Final documentation-only handoff checks: 10 governing documents / 144 relative links verified; whitespace and source/test/schema equality checks passed. Only PLAN/S4I verification evidence is staged.

### P25-S4 approval/merge and P25-S5A selection — 2026-10-18 (America/Denver)

Owner explicitly said “approved and merged,” then “proceed.” GitHub confirms MR #376 merged at current main `4c965490f59a59285b599262f68d0f853fcca582`; final head `de44946b1cec58ecdd4743585eb095deaf13cf9a` and run `39096513048` independently confirm completed success. Fresh `codex/P25-S5A-functional-expense-contract` carries final local S4I evidence as `7648faa2` (from `f57c850d`); the merged branch is not reused. Worktree was clean before selection. Owner approval is recorded as acceptance of the submitted financial slice; it does not invent a per-check installed-desktop test transcript.

S4I is DONE. Review of A10-N01–N10, merged S4D–S4I, full local 965-test/actual JavaFX/SCLX evidence and explicit owner approval closes parent S4's financial scope. Optional policy-reference S4B/C native check records and P27 form-specific mappings do not reopen the resolved external operating-policy gate. P25-S3 desktop acceptance remains passed. No NMR policy/default/rate engine is required.

The next adopted feature is S5 independent functional-expense classification. D07 in the [baseline contract](P23-S1-baseline-and-acceptance.md#decision-and-evidence-backlog) explicitly requires AR/OA/FR meanings, report basis and permitted defaults before S5 implementation. AGENTS §3.2 says “BLOCKED: do not implement.” Select the bounded documentation-only [S5A proposal](P25-S5A-functional-expense-contract.md) to make that choice concrete; parent S5 code remains BLOCKED, not silently authorized by proceeding. Prepare a draft for review and ask only about this functional classification proposal, never resolved NMR operating policies.

Required reading/inspection: AGENTS/PLAN, adopted program S5 and baseline G10/A10/D07, accepted S4A/S4I and three UI contracts; TxnSplit/Account/BudgetCategory, line command/view/entry/correction/native request receipt, financial report date/sign/basis paths, generated-entry writers and SCLX identity/snapshot/import boundaries. Current production has no independent function field: AccountFunction is banking/control behavior, Activity is Event, BudgetCategory is budget purpose. Donor core JournalEntry has no functional dimension; its SCLX TransactionLine/schema expose nullable free-text usedFor without adopting AR/OA/FR meanings or providing this native persistence. Record that compatibility seam in the proposal; retain canonical TxnSplit rather than import donor persistence. Read-only supplied runbook confirms AR (Events), OA (Admin.), FR (Fundr.) as purpose choices; it is reference evidence, not approval of the required submission form revision. Only generic instructions/lookup metadata were inspected; no raw workbook or real financial rows are committed.

Scope: define proposed meanings and a transaction-date net-expense report basis, blank/no automatic defaults, explicit per-expense-split classification and mixed-line amounts, unknown-history diagnostics, existing correction/protection/Save/formatting, generated-entry and lossless SCLX/native-retry requirements, executable acceptance specifications and future implementation prerequisites. No Java/test/schema/workflow/dependency/accounting-data changes. Documentation link/whitespace/scope review and merge/final-head CI verification are the appropriate local checks; prior merged code verification is not relabeled as a new implementation test. Proposal adoption and parent S5 implementation remain outstanding.

### P25-S5A proposal and local verification — 2026-10-18 (America/Denver)

Concrete S5A document is complete: three D07 choices (runbook purpose codes, transaction-date net GL expense, no automatic defaults), independent canonical-line/mixed-purpose semantics, explicit unknown diagnostics, existing shared Save/protections/corrections/generated-entry requirements, version-1 native request compatibility, donor usedFor compatibility seam, lossless SCLX/report requirements and nine future acceptance cases. Updated baseline D07, adopted program and S4A/S4I closeout links. Owner D07 choice was requested while independent work continued; no answer/approval is inferred from elapsed time or a preselected option. Parent S5 remains BLOCKED; this proposal does not implement its feature.

Final local offline `mvn clean verify` passed **965 tests, zero failures/errors, 58 display-dependent skips**, BUILD SUCCESS **2026-10-18 18:01:31 UTC**, elapsed 3:12 (`/workspace/work/p25-s5a-clean-verify.log`). This verifies unchanged application behavior, not F01–F09 future functionality. Production/test/schema/workflow/dependency bytes equal merged main; only six governing documents change. Wrapper restored the tracked manifest. Relative document links and whitespace/scope checks passed. No new UI or per-check desktop result is claimed.

Status VERIFYING on fresh `codex/P25-S5A-functional-expense-contract`, from confirmed main `4c965490`; carry commit `7648faa2` precedes this proposal. Next exact action: publish matching blobs/trees/messages/order as a draft, inspect exact published-head CI and record results. Owner adoption/correction of D07 and proposal review/merge remain before selecting a fresh S5 implementation slice. No new publication authorization is needed under the established draft-MR workflow; do not ask about NMR rates/policies.

### D07 owner adoption — 2026-10-18 (America/Denver)

Owner answered “Adopt this proposal (Recommended)” to the complete concrete D07 question. D07 is RESOLVED: AR = Events, OA = Administration, FR = Fundraising; explicit expense-line selection with no automatic defaults; net GL expense on canonical transaction date; visible Unclassified amounts, preserved corrections and SCLX. No third-party approval or official form revision is inferred. This explicit choice supersedes the pending-request checkpoints above. No further NMR operating-policy or functional-classification question is required.

Updated the S5A contract, baseline decision record, adopted program and current controller to reflect adoption. Parent S5 now waits only for the selected documentation slice's publication/review/merge before fresh implementation, not missing D07 input. This remains one documentation-only slice; no production/test/schema change or functional feature completion is claimed. Full local 965-test verification on unchanged code remains applicable; final documentation links/scope/whitespace and exact published-head CI follow.

### P25-S5A adopted-contract draft publication — 2026-10-18 (America/Denver)

Draft [MR #377](https://github.com/benbaron/sca-jakarta-h2/pull/377) targets verified main `4c965490f59a59285b599262f68d0f853fcca582`. D07 is adopted; no further classification input is pending. Connected publication preserved three reviewed local commits/messages/order and every blob/tree:

- Carry `7648faa2d5add27dcb513e9776fcaba0cfd48f53` → `459603f3a5f2af940ccf384fa6db3dd4504c1c9b`, tree `6d124a4048dfaf213a9d141139a1321432f4d9cf`.
- Proposal/closeout `4ea8fa01ddd3ae70d064a4c65a1d27278498b923` → `83612ac256107359b090af6980779da35826f22d`, tree `2e9ae4b9dc2748a8a1bcca001aac6960ed89a978`.
- Owner adoption `3d1f5a7aafe1179c89dbb5879f93a2b55a66e0d0` → `540ea03aecc291d1e6a140c9aba41a7e0016f8ff`, tree `540aed02eee6ac3691b1684ba67e14d20ec56ca5`.

Ref advanced from expected `4c965490` with force false. Native fetch and empty tree comparison confirm final remote content equals local `3d1f5a7a`; draft/open metadata confirms that head. Six governing documents / 126 relative links, whitespace and documentation-only scope checks pass; application/test/schema/workflow/dependency bytes still equal merged main. Full local 965-test clean verify passed; no new functional implementation or desktop result is claimed. Final-head Maven run `39152631112` is running clean verification, repeat suite and expanded production JavaFX. MR description records actual local results and pending CI. Map/body scratch files: `/workspace/work/p25-s5a-publication-map.json`, `/workspace/work/p25-s5a-mr-body.md`.

This post-publication verification record stays local, preserving the tested remote head. Next exact action: finish exact-head CI, update MR results and commit the local handoff; owner review/merge of this already adopted contract then enables a fresh S5 implementation slice. No new D07 approval question is needed.

### P25-S5A final-head CI and handoff — 2026-10-18 (America/Denver)

Final [Maven PR Tests run 39152631112](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39152631112), job `117749218528`, completed **success** at **2026-10-18 18:18:04 UTC** for exact head `540ea03aecc291d1e6a140c9aba41a7e0016f8ff`, branch `codex/P25-S5A-functional-expense-contract`. Run metadata and independent job-step reads confirm clean headless verification, repeat suite, expanded production JavaFX and cleanup all passed. Draft MR #377 remains open/unmerged at this head and records actual results, already-adopted D07 choices and the next implementation scope. No failure remains in final executed gates. Full local unchanged-code verification passed 965 tests, zero failures/errors, 58 display-related skips; future F01–F09 functionality is not claimed implemented.

Completed: verified S4I approval/merge/final-head CI and carried its local ledger; closed accepted parent S4 financial scope without inventing a per-check desktop transcript; inspected separate canonical dimensions and donor usedFor compatibility; prepared and obtained explicit adoption of AR/OA/FR meanings, net GL transaction-date basis and no automatic defaults; recorded Unclassified/mixed-purpose/correction/generated-entry/native retry/SCLX requirements and nine future acceptance cases; reconciled current program/baseline/PLAN/S4A/S4I contracts; published all reviewed commits with matching blobs/trees and nonforce expected-parent lease; passed full local and exact-head CI.

Remaining: owner review/merge of already adopted documentation contract MR #377. S5A stays VERIFYING, not DONE, until merged. Parent S5 waits for that selected-slice merge, not unresolved D07 or NMR operating policies. No application UI behavior changed and no new desktop visual test is required for this documentation-only slice. P26/P27 are not activated; official form mappings retain their own scope.

This final PLAN/S5A verification evidence is local-only (commit subject `Record functional expense contract publication and final verification`). active_head remains the successful remote implementation/document source; `git rev-parse HEAD` identifies the complete local handoff. Do not publish a documentation-only successor merely to restart green CI. After owner reports merge, fetch/verify main, carry this local evidence onto a fresh branch and select one vertical P25-S5 implementation slice from [the adopted contract](P25-S5A-functional-expense-contract.md). Inspect current TxnSplit/line command/view, entry/correction/native receipt, Journal/line model, report/date/sign/filter routes, every generated expense writer and SCLX parser/identity/snapshot/commit; establish a fresh compile/relevant-test baseline before code. Preserve nullable legacy unknowns, compatible old native retries and lossless current facts. No further classification/default/basis question is required. Do not reuse the merged branch.

Final handoff checks: 6 changed governing documents / 127 relative links verified, whitespace clean, production/test/schema/workflow/dependency bytes equal merged main and published head. Only PLAN/S5A final verification records follow locally.

### P25-S5A merge and S5B implementation selection — 2026-10-18 (America/Denver)

Owner confirmed “merged; contiinue.” GitHub/native fetch verify MR #377 merged at main `7bc89aefaa4176215e6adeae58dd946327996b27`; final head `540ea03aecc291d1e6a140c9aba41a7e0016f8ff` / run `39152631112` succeeded. Fresh `codex/P25-S5B-functional-expenses` carries final local evidence as `1d7334e8` (from `2451c961`), without reusing the merged branch. Worktree was clean. S5A DONE; S5/S5B IN_PROGRESS. D07 remains adopted, with no new operating-policy/classification gate.

Required reading/inspection: AGENTS/PLAN, adopted program S5, baseline G10/A10/D07, S5A/S4I, ledger/lifecycle/period, three UI contracts and SCLX; canonical split/DTOs/entry/correction/native create receipt, Journal and shared line model/Save/capabilities, financial/report/filter/drill-through routes, every canonical/generated writer, SCLX snapshot/serializer/preview/identity/history/commit and migrations/tests. Donor nullable usedFor does not define strict adopted codes; use the existing optional versioned application extension boundary, retaining one canonical TxnSplit expenseFunction. No second ledger or master dimension. Generated writers without an explicit function input remain Unclassified with report diagnostics; exact source copies/reversals preserve any accepted function.

Baseline before production edits: offline compile passed; 38 relevant entry/correction/native retry/NMR SCLX/inventory/fixed-asset accounting tests passed at 2026-10-18 18:54:32 UTC. Add actual Journal missing-choice regression before repairing production. [S5B](P25-S5B-functional-expenses.md) records the concrete technical choices and required validation. Parent S5 is not DONE from implementation alone; no new human desktop result is assumed.

### P25-S5B implementation and focused verification — 2026-10-18 (America/Denver)

Implemented nullable checked canonical AR/OA/FR expense purpose with no default/backfill, independent command/view/Journal choice, exact correction copies and audit, old-v1/new-v2 native receipt compatibility, derived company/date/Fund/Event Functional Expenses with untruncated GL/unknown-gross controls and owning-transaction drill-through, and strict sparse version-1 SCLX facts with whole-transaction identity/conflict/Keep target. Account maintenance/import now protects classified expense type/normal balance. Generic classified-inverse rewrite rejects in favor of correction/replacement. Enumerated every writer: current generated commands without an input remain visibly Unclassified; accepted inventory OA survives domain reversal and interchange. No rate/policy engine, second ledger, official form mapping or future-phase scope.

Pre-edit actual JavaFX missing-choice regression failed as expected (one test, one wrapped assertion error, no skips). Service/migration initially passed 10 tests; four SCLX tests pass after fixing an incorrect account-array lookup and source/inverse export ordering. Actual function-only Save then exposed an existing Entry Lines precision defect (10.1234 reloaded/saved as 10.1200); repaired exact Debit/Credit editing/reload while keeping formatted display. Focused actual JavaFX run passed 19 tests, no failures/errors/skips. Broad service/migration/SCLX/generated/protection/native-retry run passed 68 tests, zero failures/errors/skips, BUILD SUCCESS 2026-10-18 19:22:48 UTC. Subsequent assertions add classified deletion/audit/no-resurrection and PDF/XLSX export smoke coverage for the full gate. Expanded UI run exposed a new test polling a temporarily cleared report table without a null guard; corrected that asynchronous wait, with rerun pending. No human desktop result is claimed.

Reviewed actual model/services/migration/writers/Journal/report/SCLX changes. [S5B](P25-S5B-functional-expenses.md) maps all F01–F09 to named tests and lists owner steps/limits. Updated ledger authority, lifecycle, SCLX, editor/matrix and adopted program; nine changed governing documents / 147 relative links resolve, whitespace clean. Latest native/connected main remains `7bc89aef`. Next exact action: finish expanded actual JavaFX and full clean verify, review final diff, commit and publish the fresh draft through matching blobs/trees/messages/order with nonforce expected-parent update, then inspect exact-head clean/repeat/expanded JavaFX CI. Parent S5 remains IN_PROGRESS until merge/acceptance; no further D07/NMR input is pending.

### P25-S5B expanded desktop gate — 2026-10-18 (America/Denver)

Expanded actual Xvfb production suite passed **52 tests, zero failures/errors/skips**, BUILD SUCCESS **2026-10-18 19:30:41 UTC**, elapsed 3:26 (`/workspace/work/p25-s5b-expanded-fx-final.log`). This includes the new actual shared Journal choice/precise cell entry/function-only Save/draft/VIEWER/larger-text reach test and genuine Report Library Event/all-Event Run/drill-through, plus all existing production-route/Save/format/table/geometry tests selected by CI. The earlier expanded run's sole error was the new test's unsafe poll during temporary table replacement; its null-aware wait is repaired and the complete rerun passes. No production failure remains in this executed gate. No human desktop acceptance is inferred. S5B now VERIFYING; full local clean verify is running, including final deletion/provenance/export assertions. Publication and exact-head CI follow a passing full gate.

### P25-S5B final local verification — 2026-10-18 (America/Denver)

Final offline `mvn clean verify` passed **980 tests, zero failures/errors, 59 display-dependent skips**, BUILD SUCCESS **2026-10-18 19:34:20 UTC**, elapsed 3:32 (`/workspace/work/p25-s5b-clean-verify.log`). Final service tests include classified deletion audit/no-resurrection and real PDF/XLSX export retaining full GL control despite limited detail. Expanded actual production JavaFX separately passed 52 tests with no failures/errors/skips at 19:30:41 UTC; focused accounting passed 68. All executed final gates pass. JDK 21 builds release 17 locally; exact published-head CI will validate JDK 17 clean/repeat/expanded JavaFX.

Reviewed intended canonical migration/DTO/write/correction/hash/audit/Journal/precision/report/filter/SCLX changes, all new tests, and governing documentation. No dependency/workflow/applied-migration or donor-data change. Helper restored the tracked target manifest; generated output must not be staged. Nine governing documents / 148 relative links and whitespace/scope checks pass. S5B is VERIFYING on fresh branch `codex/P25-S5B-functional-expenses`, with implementation ready for matching-tree draft publication after carry `1d7334e8`. No PR exists yet. Known final failures: none. Remaining: publication, exact-head CI, owner review/merge and documented desktop checks. Do not close parent S5 or activate P26 from local results alone.

### P25-S5B draft publication — 2026-10-18 (America/Denver)

Draft [MR #378](https://github.com/benbaron/sca-jakarta-h2/pull/378) targets verified main `7bc89aefaa4176215e6adeae58dd946327996b27`. Connected publication preserved both reviewed local commits/messages/order and every blob/tree:

- Carry `1d7334e822c903c16a5aceffce50ed98027448bb` → `e42e5bd95b869cc2cf00d17c00a55df834ec8719`, tree `2a96ba884c6fbd07061cbe435c85a888748255b1`.
- Implementation `5bba3c46cde54d708bf68d813b703e0455b64ab9` → `378cf10284feae363a18012264820ccc76c831db`, tree `cb24bfe79a5ea114c2c52441f172a389c253d297`.

Fresh ref advanced from expected `7bc89aef` with force false. Native fetch and empty tree comparison confirm remote content equals local implementation. Draft/open metadata confirms the same head, 2 commits and 46 intended files; worktree was clean before this local record. No dependency/workflow/AGENTS change or generated output was published. Full local 980-test gate and separate 52-test actual JavaFX pass; nine governing documents / 148 relative links and whitespace/scope checks pass. Exact-head [Maven PR Tests run 39159777236](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39159777236) is in progress. MR body records executed local results, pending CI, generated-unknown/official-form limits and owner desktop checks. Publication map/body: `/workspace/work/p25-s5b-publication-map.json`, `/workspace/work/p25-s5b-mr-body.md`.

This publication/final-head evidence stays local to preserve the tested remote head. Next exact action: finish exact-head clean/repeat/expanded production JavaFX CI, update MR results, validate final docs and commit this handoff. S5B remains VERIFYING pending review/merge and documented owner checks. Do not publish a documentation-only successor merely to restart CI; do not select P26 or reopen D07/NMR operating policy gates.

### P25-S5B final-head CI and handoff — 2026-10-18 (America/Denver)

Final [Maven PR Tests run 39159777236](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39159777236), job `117770447885`, completed **success** at **2026-10-18 19:51:16 UTC** for exact head `378cf10284feae363a18012264820ccc76c831db`, branch `codex/P25-S5B-functional-expenses`. Independent run, job and step metadata confirm clean headless verification, repeat suite, expanded production JavaFX and cleanup all passed on JDK 17. Draft MR #378 remains open, unmerged and mergeable at this exact head. The optional raw-job-log fetch returned a connector Transport closed error; no raw-log-derived CI counts are claimed. Executed final checks have no remaining failure. Full local clean verification passed 980 tests, zero failures/errors, 59 display-dependent skips; separate actual production JavaFX passed 52 tests, no failures/errors/skips. MR body now records actual local/CI results and owner checks.

Completed: confirmed S5A merge and D07 adoption; carried its final evidence onto a fresh branch; established pre-edit compile/38-test baseline and actual missing-choice regression; implemented nullable canonical purpose with no defaults, expense-only/account/inverse integrity, shared Journal Save/draft/copy/reload and precise amount editing, compatible v1/new-v2 native receipts, derived GL-date/Fund/Event reporting/full controls/unknown gross/export/drill-through, strict sparse SCLX identity/conflict/Keep target/history/rollback; enumerated generated writers and tested unknown/accepted source dispositions; repaired reproduced precision/export-order issues; mapped F01–F09 and updated governing documents; published identical reviewed trees/messages/order with nonforce lease and native fetched equality; passed full local and exact-head CI.

Remaining: owner review/merge of draft MR #378 and documented user checks in [S5B](P25-S5B-functional-expenses.md). S5B remains VERIFYING; parent S5 IN_PROGRESS until merge/acceptance, not DONE from local implementation. No new human desktop transcript is inferred. Official-form mapping and P26 remain outside this slice; no unresolved D07 or NMR operating-policy gate is reopened.

This final PLAN/S5B evidence is local-only (commit subject `Record functional expense publication and final verification`). active_head remains the successful remote implementation; `git rev-parse HEAD` identifies the complete local handoff. Do not publish a documentation-only successor merely to restart green CI. After owner reports merge, fetch/verify current main, confirm required acceptance, carry this final evidence onto a fresh branch, and select the first incomplete unblocked adopted slice. Do not reuse the merged S5B branch. Final docs/whitespace/source equality checks follow before committing the handoff.

Final handoff checks passed: nine changed governing documents / 149 relative links resolve, whitespace is clean, production/test/schema/workflow/dependency/AGENTS bytes equal successful published head, tracked manifest is restored, and only PLAN/S5B publication/CI records follow locally. MR re-read confirms draft/open/unmerged/mergeable at `378cf102`, with actual passing CI results in the body.

### P25 acceptance/merge and P26-S1 selection — 2026-10-18 (America/Denver)

Owner said “reviewed and merged. proceed”. Live GitHub/native fetch confirm MR #378 merged at current main `70462b49015916bc0179143f0bc1bdd532b6e20e`; accepted head `378cf102` passed run `39159777236`. Fresh `codex/P26-S1-asset-acquisitions` carries final local evidence as `ed6ca14f` from `e02c8252`; initial worktree clean. S5B/S5/P25 DONE based on owner review/merge and full executed validation, without inventing a per-check human desktop transcript. Optional policy-reference/native evidence limitations remain factual and are not reopened as accounting gates.

Select the first unblocked adopted P26 slice, S1 acquisition/opening links (G11/A11). D05 form mapping does not govern this canonical acquisition relationship; template authority stays P27, custody S2, valuation D06 S5, and D07 remains resolved. Required inspection: current FixedAsset/command/view/service/depreciation/lifecycle and register panel; canonical Txn/Split entry/correction/audit/ownership/protection/native receipts; V55/V65/V71/V85 and persistence registration; asset reports, SCLX fixed-assets snapshot/identity/preview/commit/history; existing asset, authorization, migration, report, import and actual UI tests. Read governing adopted program/baseline A11, asset/ledger/lifecycle/period/auth contracts, SCLX and all three UI rules.

Pre-edit baseline offline compile passed at 2026-10-18 22:34:25 UTC; 32 relevant fixed-asset service/atomic depreciation/lifecycle/authorization/SCLX snapshot tests passed at 22:35:14 UTC, zero failures/errors/skips. Donor AssetRecordService/AssetRecord maintain a separate normalized register with approximate/lot costs and custody; they do not supply a canonical acquisition transaction/claim authority suitable for import. Retain production FixedAsset/Txn/Split and caller-owned atomic entry seam. [S1 contract](P26-S1-asset-acquisitions.md) records concrete choices and acceptance. No P26-S2 or later feature is started.

### P26-S1 final local verification — 2026-10-18 (America/Denver)

Implemented three explicit shared-Save routes on the existing Asset Register: atomic balanced BANK purchase, link an existing ordinary fixed-asset debit, and opening/register-only with no accounting. Existing register items can be linked or explicitly marked register-only. FixedAsset retains nullable source/split provenance; acquisitionCost remains the only claim amount. Company and asset locking, immutable request receipts, exact preview rebuild, source capacity, account/Fund/date eligibility, period/reconciliation controls and full rollback protect native writes and retries. Linked basis, source transactions and linked account classification are protected; eligible audited unlink retains both register and accounting before ordinary Journal correction.

V86 is nondestructive and leaves legacy provenance null. Strict sparse SCLX `assetAcquisitions` version 1 uses intrinsic asset/line identities, validates exact NEW_PURCHASE BANK structure and aggregate claims, participates in complete asset conflicts/Keep target, restores metadata without reposting, revalidates retained-target capacity/dependent history and rolls back late failures. Existing fixedAssets version 1 and files without the optional extension remain compatible. Custody/forms, valuation, conversion and broader lifecycle portability stay in their owning later slices.

Shared Save uses nonmutating preview, confirmation, one request identity, asynchronous busy/capability guards, retained drafts, live permission changes and real Journal navigation. Register rows expose source transaction and full source/claimed/remaining values. Actual inspection reproduced and repaired blank FixedAssetView record cells, preserving company date/money formatting, exact four-decimal editing, company table state, movable dividers and independently reachable two-axis editor/table regions. The action row scrolls within a bounded area at larger text instead of consuming the workspace.

Focused final acquisition/accounting/SCLX tests passed **14 tests**, zero failures/errors/skips. Final isolated offline `mvn clean verify` passed **999 tests, zero failures/errors, 62 display-dependent skips**, BUILD SUCCESS at **2026-10-18 18:32:29 America/Denver** (elapsed 3:41). The workflow-equivalent expanded Xvfb suite passed **55 tests, zero failures/errors/skips**, BUILD SUCCESS at **18:36:22** (elapsed 3:38), including new actual purchase/existing-link/register-only Save, preview/cancel/busy/draft/retry/precision/role/navigation and laptop/larger-text geometry coverage. An earlier invalid full-run result came from two clean Maven processes sharing `target/` after an interrupted wrapper left a child alive; both process groups were stopped, absence was verified, and the isolated full run passed. No product failure remains and no human per-check desktop transcript is inferred.

Reviewed migration/model/service/canonical-entry/correction/account-classification/UI/SCLX/test/documentation/workflow changes. Changed-document relative links resolve, `git diff --check` passes, generated output is not staged, and the tracked manifest remains restored. P26-S1 is VERIFYING; draft publication, matching-tree verification and exact-head CI remain. P26-S2 stays blocked until owner review/merge is confirmed.

### P26-S1 draft publication — 2026-10-18 (America/Denver)

Draft [MR #379](https://github.com/benbaron/sca-jakarta-h2/pull/379) targets current main `70462b49015916bc0179143f0bc1bdd532b6e20e`. A normal non-force native push created fresh branch `codex/P26-S1-asset-acquisitions` with the carried P25 evidence, implementation and local-verification commits. Remote head `c4465cadc792a4a362d95c4b295245badd14de37` and tree `6f30c73423f786d28568038cb2d074a6c5e7ec83` were fetched; both exactly match local, and the diff is empty. Live MR metadata confirms draft/open/unmerged, base/head, 3 commits and 37 intended changed files. The MR body records actual local results, bounded later-slice exclusions and owner checks. Exact-head Maven PR Tests is pending.

This publication record is the only post-tested documentation change. Publish it normally, verify the resulting final remote head/tree, and use that exact head for CI. Do not advance P26-S2 from draft publication or automated validation alone.

### P26-S1 exact-head CI and handoff — 2026-10-18 (America/Denver)

The first exact-head run and its failed-job rerun passed clean headless verification and repeat tests but failed the production JavaFX stage. The connected raw-log endpoint closed and the local CLI token could not read Actions logs, so the workflow now publishes failure-only Surefire reports for durable diagnosis. The report identified `AssetAcquisitionPanelTest`: its post-save predicate depended on a transient button-render state, then its focus/precision check exposed the acquisition confirmation as an ownerless dialog under Xvfb. The test now waits on the panel's public command-capability busy contract, and the confirmation is owned by the Assets Register window so focus returns deterministically. The focused actual class passed three consecutive runs after each correction. The repaired complete local Xvfb selector passed **55 tests, zero failures/errors/skips**, BUILD SUCCESS at **2026-10-18 19:25:17 America/Denver** (elapsed 3:40).

Final exact published-head JDK 17 [Maven PR Tests run 39189292452](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39189292452), job `117858319494`, completed **success at 2026-10-18 20:28:22 America/Denver** for `b66e0ce17c2acc31ee73c17c41341475bd0bde9f`. Independent job-step metadata confirms clean headless verification, repeat tests, production JavaFX and cleanup all passed; the failure-only report step correctly skipped. Draft MR #379 remains open, unmerged, mergeable and draft at this exact head, and its description records the final local/CI evidence and owner checks. No executed gate has a remaining failure.

This final PLAN evidence stays local to preserve the green published head. P26-S1 remains VERIFYING until owner review/merge and documented disposable-company checks; no human per-check result is inferred from automated execution. P26-S2 remains unselected. After merge confirmation, fetch and verify current main, carry this evidence onto a fresh branch, and select P26-S2 without reusing the merged branch.

### P26-S1 acceptance and merge — 2026-10-18 (America/Denver)

Owner explicitly recorded MR #379 as merged and accepted. Live GitHub and native main verification confirm [MR #379](https://github.com/benbaron/sca-jakarta-h2/pull/379) merged accepted head `b66e0ce17c2acc31ee73c17c41341475bd0bde9f` into current `main` at signed merge `2de87dc3645c69031c7de026e5430cf994c6ed33`; the merge tree `6bb26b568ba430293fda99a21ffa6df615ec0294` equals the accepted head tree. Exact-head run `39189292452` passed all required steps. P26-S1 is DONE. Acceptance is recorded as the owner's slice decision; no separate per-check human transcript is invented.

Fresh branch `codex/P26-post-S1` starts from verified merged main and carries the local final-verification record as `81957795`. P26-S2 remains unselected and no S2 implementation is started by this acceptance record. The next action is to inspect the merged authority and select the bounded custody/supplies/property-transfer slice when execution continues.

### P26-S2 selection and baseline — 2026-10-18 (America/Denver)

Owner instructed “do p26-s2.” Fresh branch `codex/P26-S2-property-custody` carries the accepted S1 evidence and selects the first unblocked adopted G11/A11 slice. [P26-S2 contract](P26-S2-property-custody.md) retains FixedAsset and InventoryItem as the only subjects; adds immutable custody events, explicit zero-value EXPENSED_SUPPLY treatment, append-only latest-event correction, source-backed removal and an internal branch report. D05 official template mapping remains P27 and D06 cost method remains P26-S5; neither is inferred. No second register, GL writer, approval engine or valuation method is introduced.

Donor `NonprofitAccounting` at `c697630e` offers current guardian, one confirmation snapshot and removal fields in a separate AssetRecord model plus JDBC-created detail tables. Only the field concepts are retained. Its parallel register, approximate/lot valuation and static JDBC authority are unsuitable for current canonical FixedAsset/InventoryItem, Flyway/JPA, accounting protections and immutable history.

Pre-edit offline compile passed at **2026-10-18 21:05:59 America/Denver**. Thirty-three relevant fixed-asset, inventory, authorization, accounting, report and SCLX tests passed at **21:06:41**, zero failures/errors/skips. Current Inventory already supports explicit zero-value nonfinancial movements; existing rows have no explicit financial-treatment field, FixedAsset has no custody fields, and neither authority retains confirmation/loan/transfer/removal custody history. Implementation and actual missing-route regression follow.

### P26-S2 implementation and local verification — 2026-10-18 (America/Denver)

Implemented the [adopted contract](P26-S2-property-custody.md) as one vertical slice. Migration V87 and the canonical service add append-only custody events for existing fixed assets and inventory items, explicit capitalized-inventory versus zero-value expensed-supply treatment, derived current/overdue state, latest-event corrections, and source-backed removals without posting accounting. The production Property Custody destination exposes New, Correct Selected, Refresh, and Save Custody Event with live role gating, dirty protection, persisted table/divider state, company date formatting, and reachable scrolling. Report Library, strict optional SCLX v1 portability, portable identity/retry behavior, and the operation matrix cover the same facts.

Final-source focused service/accounting/SCLX verification passed **20 tests**, zero failures/errors/skips. SCLX round-trip coverage includes expensed treatment, a correction chain, restart, identical retry, and an empty target ledger. Offline `mvn clean verify` passed at **2026-10-18 21:36:07 America/Denver**: **1,005 tests**, zero failures/errors, **63 headless skips**. The actual `PropertyCustodyPanelTest` then passed under Xvfb at **21:36:54**: **1 test**, zero failures/errors/skips. Changed-tree whitespace review is clean. P26-S2 remains VERIFYING until the draft MR is published, its final remote tree is matched, exact-head CI passes, and the owner reviews/merges it. No human disposable-company acceptance result is inferred from automated tests.

### P26-S2 draft publication — 2026-10-18 (America/Denver)

Published branch `codex/P26-S2-property-custody` and opened draft [PR #380](https://github.com/benbaron/sca-jakarta-h2/pull/380) targeting `main`. The PR records the bounded accounting boundary, delivered custody/UI/report/SCLX behavior, local clean/focused/actual-JavaFX evidence, and remaining owner checks. This documentation-only publication successor follows the same authorization; `active_head` identifies its known remote parent because the successor cannot embed its own SHA. Verify the final branch and PR tip/tree and require the workflow for that exact head to pass. No GitHub CI result is claimed at this checkpoint, and P26-S2 stays VERIFYING pending owner review/merge.

### P26-S2 final-head verification — 2026-10-18 (America/Denver)

Final published head `c83db0a54a822a4a4572d3e822442f8d5e90a263` and its fetched remote ref matched exactly; both resolve tree `d1569f6a4510552b10acd585f06b1d737a0fa39b`. Exact-head [Maven PR Tests run 39195692424](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39195692424) completed successfully: clean headless verification, repeat tests, and production JavaFX route compliance all passed; failed-test publication was correctly skipped. The PR validation now records this evidence. P26-S2 remains VERIFYING for owner disposable-company review and merge.

This final PLAN evidence is local only so the reviewed green PR head remains unchanged. Preserve it for post-merge closeout rather than publishing another evidence-only successor. After owner acceptance/merge, fetch and verify current `main`, carry this record onto a fresh branch, and select P26-S3 without reusing the merged P26-S2 branch.

### P26-S2 acceptance/merge and P26-S3 selection — 2026-10-19 (America/Denver)

Owner reported P26-S2 verified and merged. Fetched current `origin/main` and confirmed [PR #380](https://github.com/benbaron/sca-jakarta-h2/pull/380) merged accepted head `c83db0a54a822a4a4572d3e822442f8d5e90a263` at merge commit `21f379dd`; main contains the accepted head and its previously recorded exact-head run `39195692424` passed. Fresh branch `codex/P26-S3-budget-planning` starts from that main and carries the local S2 final-verification record as `61e4ce41`. P26-S2 is DONE.

Selected [P26-S3](P26-S3-event-fund-budgeting.md), the first unblocked adopted G12/A12 slice. `BudgetPlan`/`BudgetLine`, existing Fund/Activity, canonical TxnSplit actuals, supplemental lifecycle projection, Report Library and SCLX remain the authorities. Event scopes are mutually exclusive; null Event means general/untagged. All Funds aggregates only when it does not overlap fund-specific lines for the same category/Event/month. Supplemental item filters use opening allocation Fund/Event while keeping later cross-scope effects in the item balance and retaining company-wide control reconciliation. Donor BudgetWorkspaceStore supplies only the explicit Fund-selection idea; its sidecar/string rows and separate records are rejected.

Pre-edit offline compile passed at **2026-10-19 19:23 America/Denver**. Fifty existing budget, reversal, authorization, open-item, report-request and SCLX tests passed, zero failures/errors/skips. Implementation must add unit tests for two Events/two Funds, general and scoped actuals, overlap rollback, revision, opening-allocation filters with cross-scope settlement, migration uniqueness and optional SCLX event identity; actual JavaFX coverage remains a separate gate.

### P26-S3 implementation and local verification — 2026-10-20 (UTC)

Implemented the [adopted contract](P26-S3-event-fund-budgeting.md) on the existing budget, ledger, supplemental-item, Report Library and SCLX authorities. V88 adds an optional Event relationship and null-safe scope uniqueness to budget lines. Budget write validation preserves atomic rollback and rejects overlapping All Funds and fund-specific lines for the same category, Event and period. Variance projection keeps general and Event-specific actuals mutually exclusive, aggregates across Funds only for an applicable All Funds budget, and counts every actual once. Revisions and SCLX preserve the optional Event identity.

Budget Editor now exposes explicit Fund and Event scopes and its existing Save Draft Amount command replaces only the visible scope while preserving all other scope lines. Budget vs Actual exposes matching filters and an Event column. The six supplemental open-item reports accept Fund/Event filters based on each item's authoritative opening allocation; later cross-scope settlements and corrections remain in the selected item's balance. Filtered output includes opening dimensions and clearly retains company-wide reconciliation controls rather than implying a partial-ledger tie-out. Prepaid/deferred recognition, inventory valuation and official form mapping remain P26-S4, P26-S5 and P27 work.

The requested scenario-level unit coverage is present: two Events/two Funds/general actuals and no double counting; revision preservation; overlap rejection with atomic rollback; cross-company Event rejection; opening-allocation filters with cross-scope settlement; scoped editor merge behavior; migration uniqueness/recovery; optional/duplicate SCLX Event scope, export and import restoration; supplemental request/output dimensions; and the existing inventory Event-cost round trip updated to use an Event-scoped budget. The final focused gate passed **79 tests**, zero failures/errors/skips. A migration/recovery/inventory retest passed **18 tests**, zero failures/errors/skips after making the new foreign key recovery-idempotent.

Final offline `mvn clean verify` passed **1,013 tests**, zero failures/errors and **64 display-dependent skips**, BUILD SUCCESS at **2026-10-20 01:56:41 UTC** (elapsed 3:54). The dedicated `BudgetPlanningPanelTest` ran under Xvfb with **1 test**, zero failures/errors/skips, proving the actual editor/comparison controls, shared Save command, split layout and VIEWER write gate. The CI workflow's explicit production JavaFX selector includes this test. `git diff --check` and the intended-scope review passed. Implementation commit `0b1108a5` contains source, migration, tests, workflow and governing documentation; only this verification ledger follows locally.

P26-S3 remains VERIFYING. Next publish the reviewed branch as a draft MR, confirm its fetched remote head/tree equal local, and require clean verification, repeat tests and production JavaFX on that exact head. Owner review/acceptance and merge remain required; no human desktop result is inferred from automated execution. P26-S4 stays blocked until merge confirmation.

### P26-S3 draft publication — 2026-10-20 (UTC)

Published `codex/P26-S3-budget-planning` and opened draft [PR #381](https://github.com/benbaron/sca-jakarta-h2/pull/381) against current `main`. The PR description records the implemented scope, the full/focused/actual-JavaFX results, the requested scenario-level unit coverage and concrete owner checks. This documentation-only publication successor follows the reviewed implementation and local-verification commits; `active_head` records its known parent because a commit cannot embed its own hash. Push this record, fetch the resulting remote branch, require exact local/remote head and tree equality, and inspect CI for that final head. P26-S3 remains VERIFYING; no GitHub CI success or owner desktop result is claimed at this checkpoint.

### P26-S3 exact-head CI and handoff — 2026-10-20 (UTC)

Final published head `ee36b6410d9b37651f0ea97ae88f505727c93020` and its fetched remote branch match exactly; both resolve tree `fd0e693a35d58f06e922c0cd27e738a21c7a3733`. Exact-head [Maven PR Tests run 39356379674](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39356379674), job `118412084786`, completed successfully: clean headless verification, repeat tests and production JavaFX route compliance all passed, including the newly explicit `BudgetPlanningPanelTest`; failed-test report publication correctly skipped. Draft PR #381 is open, unmerged and mergeable at that exact head, and its description records the final local and CI evidence.

This final evidence remains local so the green remote head is not replaced solely by a documentation update. P26-S3 remains VERIFYING pending owner review/acceptance and merge; no human desktop transcript is inferred. After merge confirmation, fetch and verify current `main`, carry this record onto a fresh branch, and select P26-S4 without reusing the merged branch.

### P26-S3 acceptance/merge and P26-S4 selection — 2026-10-19 (America/Denver)

Owner reported P26-S3 verified and merged. Native fetch confirms [PR #381](https://github.com/benbaron/sca-jakarta-h2/pull/381) merged accepted head `ee36b6410d9b37651f0ea97ae88f505727c93020` into current `main` at merge commit `c7170328`; the recorded exact-head run `39356379674`, job `118412084786`, passed clean verification, repeat tests and production JavaFX. Fresh `codex/P26-S4-recognition-assistance` starts from that merge and carries the local S3 final evidence as `e05b9c0e`. P26-S3 is DONE.

Selected [P26-S4](P26-S4-prepaid-deferred-recognition.md), the first unblocked adopted G12/A12 slice. The bounded design uses transient user-entered straight-line rules, existing supplemental item balances and one explicit selected Save through `TransactionEntryService`. It creates no schedule table, automatic background posting, default account/function, or second balance. Existing SCLX transaction/supplemental portability covers accepted recognition; cancelled previews have no durable fact. Canonical reversal/replacement owns correction and cancellation after Save.

Pre-edit offline compile passed at **2026-10-19 20:45 America/Denver**. Sixty-three relevant supplemental lifecycle, entry, correction, SCLX and Save-coverage tests passed, zero failures/errors, with seven display-dependent skips. Donor inspection found grant deferral fields and report-oriented schedule configuration but no compatible canonical prepaid/deferred recognition authority; no donor persistence is adopted. Implementation must cover 1,200/12 exact recognition, partial-month and residual rounding, prior partial reductions, repeat/no-duplicate behavior, reversal reopening, deferred direction, account/function/ownership/period/concurrency failures, retained dimensions, restart/SCLX, and actual JavaFX Save/role/layout behavior.

### P26-S4 implementation and local verification — 2026-10-19 (America/Denver)

Implemented the governed Recognition Assistant through a transient `SupplementalRecognitionService` and a production Planning panel with Preview, Refresh and one explicit `Save Selected Recognition` command. Accepted installments post only through the canonical transaction entry authority, retain the source Fund/Event/Budget Category and required expense function, reduce the existing supplemental open item, support exact-request retry without duplicates, revalidate on Save, and reopen after canonical reversal. Prepaid postings debit expense and credit prepaid; deferred postings debit the deferred liability and credit income. No schedule table, background posting, default rate/account/function, or second ledger/balance authority was introduced. Existing transaction and supplemental-item SCLX records round-trip accepted recognition.

Added behavioral tests for exact 1,200/12 recognition, clipped partial months and final residuals, prior reductions, retry identity, retained dimensions, deferred direction, validation and ownership failures, stale-preview rollback, reversal reopening, SCLX round-trip, JavaFX shared Save/role/layout behavior, and command capability. The new [P25/P26 unit coverage audit](P25-P26-unit-coverage.md) maps every implemented P25 and P26 slice to concrete tests; its 48 cited test classes all resolve. P26-S5 and P26-S6 remain unimplemented and must add behavioral tests with their implementations.

Focused P25/P26 verification ran **190 tests with zero failures or errors** and 17 expected display-dependent skips. Full `mvn clean verify` completed at **2026-10-19 21:12 America/Denver** with **1,020 tests, zero failures, zero errors, 65 expected display-dependent skips, and BUILD SUCCESS**. An earlier full run reached the same assertions but three existing UI compliance tests could not create the sandboxed application-data directory; granting that test process the required workspace-external directory access and rerunning produced the clean result above. `git diff --check`, documentation-link validation, and coverage-matrix class validation pass. Local Xvfb is unavailable, so actual JavaFX execution remains assigned to the explicit `RecognitionAssistantPanelTest` selector in exact-head CI.

Published `codex/P26-S4-recognition-assistance` at implementation head `2e418baf09f02119d39b7499bd2b22de8c402957`. A native fetch confirmed the published and local heads match exactly and both resolve tree `c2f206a74a552dc8462391584af390f865098af2`. Draft creation and exact-head CI remain pending.

### P26-S4 acceptance/merge and P26-S5A selection — 2026-10-20 (America/Denver)

Owner reported the branch created and merged. Native fetch confirms [PR #382](https://github.com/benbaron/sca-jakarta-h2/pull/382) merged exact published head `70ca3b1b92df13f9b4637517fbf6c52e9eabe98c` into current `main` at `bf5bc979`. Owner acceptance plus the recorded 190-test P25/P26 audit and 1,020-test clean local verification close P26-S4; no unqueried GitHub CI detail is invented.

Selected documentation-only P26-S5A on fresh `codex/P26-S5A-inventory-valuation-contract` because D06 requires an adopted cost method and opening basis before P26-S5 code. Current production inspection confirms one fixed `InventoryItem.unitValue` drives all receipts, issues, adjustments, reversals and reports; movements persist that applied value and canonical transaction links, but varying-cost receipt authority and exact current carrying value do not exist. Donor inspection found manually entered sales unit cost and inventory presentation but no compatible FIFO, weighted-average, return-link or durable accounting authority to import.

The [P26-S5A proposal](P26-S5A-inventory-valuation-contract.md) recommends perpetual moving weighted average, exact item carrying value and signed movement value changes, legacy opening basis from quantity × persisted fixed unit value, no negative stock/backdating/standalone revaluation, and exact canonical reversal. It defines the 10@$2 + 10@$3 fixture and requires behavioral unit coverage for arithmetic/residuals, every movement direction, concurrency/authorization/period protections, rollback/retry, migration/restart, SCLX, reports and JavaFX Save. P26-S5 remains BLOCKED pending owner acceptance and contract merge.

Initial Maven baseline could not execute because the prior container's temporary proxy settings file was absent; direct-Central retries without Maven proxy configuration then failed on DNS resolution. Recreating the temporary central/proxy settings recovered the environment without repository build changes. Baseline compile passed, followed by **20 inventory service, accounting, authorization and report tests with zero failures/errors/skips**. No production code or schema changed in S5A; documentation validation remains required before publication.

Documentation validation passed: the worked valuation fixture reaches 12 units and $30 carrying value exactly, all 128 relative links in the affected governing documents resolve, and `git diff --check` passes. Full `mvn clean verify` passed at **2026-10-20 22:05 America/Denver** with **1,020 tests, zero failures, zero errors, 65 expected display-dependent skips, and BUILD SUCCESS**. P26-S5A is ready for owner decision; no cost method is adopted merely by preparing or testing this proposal.

### P26-S5A owner adoption — 2026-10-21 (America/Denver)

Owner answered “yes” to the reviewed decision and adopted perpetual moving weighted average as the sole capitalized-inventory valuation method, including the recorded legacy opening basis, negative-stock/backdating/revaluation limits, exact reversal boundary and comprehensive implementation-test requirements. D06 is resolved by this accounting decision. P26-S5 remains blocked only on publication, exact-head CI and merge of this contract; no implementation is included in S5A.

Published `codex/P26-S5A-inventory-valuation-contract` at adopted-contract head `2e968059d497900bd18100c977a6b178f0c0bd30`. A native fetch confirmed the published and local heads match exactly and both resolve tree `79fbacf3192926b20abcc5d936f3975d291c83f2`. Draft creation and exact-head CI remain pending.

### P26-S5A PR #383 CI fixture correction — 2026-10-21 (America/Denver)

Owner created draft [PR #383](https://github.com/benbaron/sca-jakarta-h2/pull/383). Its production JavaFX job correctly exposed one test-isolation error: `RecognitionAssistantPanelTest.setup` inserted company code `DEFAULT` after `UiServiceRegistry.reconnectToDatabase` had already bootstrapped that unique company. The authorization stack traces elsewhere in the log were not the failing assertion. The fixture now reuses the bootstrapped company for its chart, fund, Event and Budget Category instead of inserting a duplicate; production behavior and the adopted valuation contract are unchanged.

Recovered an isolated Xvfb runtime under `/tmp` and ran the actual test with **1 test, zero failures/errors/skips**. The complete CI-equivalent production JavaFX selector then passed **57 tests, zero failures, zero errors, zero skips** in the shared process that reproduced the collision. Final `mvn clean verify` passed at **2026-10-21 22:54 America/Denver** with **1,020 tests, zero failures, zero errors, 65 expected display-dependent skips, and BUILD SUCCESS**. Publish this correction normally to PR #383 and require new exact-head CI before merge.

Published correction head `4f96526279b2cfad1498a73064062636c454e3fd`; its fetched remote branch and PR #383 pull ref match the local head, and local/remote trees both resolve `02c916f8b2c8ce689a66f8c8b524d343d568d209`. Exact-head [Maven PR Tests run 39725056189](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39725056189), job `119646945238`, completed successfully in 13m 23s: clean headless verification, repeated tests and production JavaFX route compliance all passed. This final evidence remains local so the green remote head is not replaced solely by a ledger update. P26-S5A remains VERIFYING only for owner review/merge; no known failure remains.

### P26-S5A acceptance/merge and P26-S5 selection — 2026-10-23 (America/Denver)

Owner reported PR #383 reviewed and merged. Native fetch confirms exact accepted head `4f96526279b2cfad1498a73064062636c454e3fd` is contained in current `main` at merge commit `e750f73c`. The recorded exact-head run `39725056189`, job `119646945238`, passed clean verification, repeat tests and production JavaFX. P26-S5A and D06 are DONE.

Fresh `codex/P26-S5-inventory-valuation` starts from that merge and selects the adopted [P26-S5 contract](P26-S5A-inventory-valuation-contract.md). Implementation owns perpetual moving weighted average, exact carrying/movement value facts, legacy fixed-value migration, canonical movement/reversal/retry/concurrency behavior, historical reporting, versioned SCLX, production JavaFX cost/preview/Save behavior and the complete scenario-level unit coverage recorded in the contract and [P25/P26 coverage ledger](P25-P26-unit-coverage.md). No FIFO layers, backdating, negative stock, standalone revaluation or parallel inventory ledger is in scope.

### P26-S5 implementation and local verification — 2026-10-23 (America/Denver)

Implementation head `0c102dfd62be4c26fc444aa485d5fe26bfbf4fdb` completes the adopted moving-weighted-average behavioral unit. V89 nondestructively records exact item carrying value, the sole valuation method, signed movement value change and resulting carrying value while backfilling legacy fixed-value data. Capitalized receipts and upward adjustments require explicit positive cost; issues and downward adjustments consume the current moving average, with the final issue consuming the exact residual. Interactive backdating and negative quantity/value are rejected. Canonical reversals restore the original signed quantity and value facts exactly, and retries compare those durable facts before accepting an existing identity. Expensed supplies retain zero inventory value.

Inventory reports now use exact current and historical carrying values. Inventory SCLX version 2 exports and imports the new facts, still reads version 1 by deriving its legacy fixed-value basis, and rejects incomplete or discontinuous valuation histories before commit. The production Inventory panel exposes receipt/upward cost and shows the signed value change, carrying-value transition and new average in its confirmation; its existing shared Save Item command remains the durable item-maintenance action. The P25/P26 coverage ledger now identifies the migration, service arithmetic/residual/reversal, accounting, SCLX, report and actual JavaFX tests for P26-S5.

Local focused valuation, migration, accounting, reporting and SCLX tests passed. The final `mvn clean verify` completed at **2026-10-23 19:19 America/Denver** with **1,023 tests, zero failures, zero errors, 65 expected display-dependent skips, and BUILD SUCCESS**. The exact production JavaFX workflow selector then ran under Xvfb with **57 tests, zero failures, zero errors and zero skips**. `git diff --check` passes and there are no known failures.

User-visible testing notes: create a capitalized item, receive equal quantities at two different costs, confirm the preview and saved carrying value/average, issue part of the stock, and reverse that issue. Verify that each preview shows the expected signed value and that the Inventory Valuation report reaches the same final carrying value. Also verify that an expensed supply accepts quantity changes without a cost or inventory accounting entry. Exact-head GitHub CI and owner review remain before merge; P26-S5 is not DONE locally.

Published `codex/P26-S5-inventory-valuation` and opened draft [PR #384](https://github.com/benbaron/sca-jakarta-h2/pull/384) with the completed behavior, validation evidence and manual-review scenarios. The implementation authority remains head `0c102dfd`; the later plan-only publication record does not change production behavior. Exact-head GitHub CI and owner review remain required.

### P26-S5 merge and P26-S6A selection — 2026-10-23 (America/Denver)

Owner reported PR #384 merged. Native fetch confirms its exact published head `5b48746e79999f85a1df7a53d8215fe3a3d4fa74` is contained in current `main` at merge `7739698b`. The recorded local gates remain 1,023 clean-verification tests and 57 actual JavaFX tests with zero failures/errors. Exact-head Maven PR Tests run `40049762170` was still in progress when inspected after merge, so no passing GitHub result is invented. Owner merge plus completed local gates closes P26-S5.

Fresh `codex/P26-S6A-opening-conversion` starts from that merge. P26-S6 is split along its already adopted boundary: [S6A](P26-S6A-opening-conversion-contract.md) owns opening/carryover review and one immutable acceptance; S6B remains blocked and owns merchant-settlement assistance. S6A compares existing opening GL, configured-bank/outstanding, fixed-asset, inventory and six supplemental-schedule facts without creating balancing transactions, inferred item identities or a parallel ledger. A ready unchanged preview saves one idempotent `OPENING_CONVERSION_ACCEPTED` fact through the existing audit/SCLX authority. Baseline compilation passed; implementation and focused/full/actual-JavaFX verification remain.

### P26-S6A implementation and local verification — 2026-10-24 (America/Denver)

Implementation head `7cdbe7f1` adds the production **Opening Conversion** route and a resizable review workspace for account openings, configured bank baselines, uncleared canonical bank items, fixed-asset and inventory register/ledger ties, orphan controls, and all six supplemental schedules. Preview is non-mutating. **Save Conversion Acceptance** is exposed both locally and through shared File/toolbar Save only for a ready unchanged preview with `BOOKKEEPING_WRITE`; it revalidates the complete displayed fingerprint while holding the company lock and writes one immutable/idempotent `OPENING_CONVERSION_ACCEPTED` audit event. It creates no transaction, balancing entry, inferred item identity, provider integration, or second ledger. Existing factual audit-history SCLX owns portability.

`OpeningConversionServiceTest` covers balanced acceptance, exact retry/restart, no ledger writes, bank mismatch, orphan bank/fixed-asset/inventory controls, raw supplemental opening gaps, stale preview, Viewer denial, conflicting second acceptance and rollback after persistence. `OpeningConversionPanelTest` covers actual Preview/shared Save, live permission gating, retained acceptance, tabs/tables and resizable layout. The P25/P26 coverage ledger, command capability matrix, Help route inventory and CI Xvfb selector now include this slice.

The final local `mvn clean verify` completed with **1,030 tests, zero failures, zero errors and 67 expected display-dependent skips**. The exact workflow JavaFX selector then ran on an authenticated X display with **59 tests, zero failures, zero errors and zero skips**. `git diff --check` passes and the worktree was clean before this plan-only verification record. User testing: open **Import & Oversight -> Opening Conversion**, choose the intended cutoff, Preview, inspect each blocking/info row and every detail tab, confirm Save stays unavailable while a mismatch exists, repair and preview again, save the acceptance, then reopen/preview and confirm the retained accepted-by/as-of message and disabled Save. Draft publication, exact-head GitHub CI and owner review remain; S6A is not DONE before merge.

Published `codex/P26-S6A-opening-conversion` and opened draft [PR #385](https://github.com/benbaron/sca-jakarta-h2/pull/385). The implementation authority remains `7cdbe7f1`; plan/publication head `d45e675b` records the completed local evidence and does not change production behavior. Exact-head GitHub CI and owner review remain.

### P26-S6A exact-head CI and handoff — 2026-10-24 (America/Denver)

Final published head `bba4f944b8073189d11ba220810281a78173b70b` is the open draft PR #385 head. Exact-head [Maven PR Tests run 40114429370](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40114429370), job `120932738705`, completed successfully: clean verification, repeat tests and production JavaFX route compliance all passed. The draft description records this exact-head result together with the local 1,030-test clean verification and 59-test actual JavaFX result.

This final evidence remains local so the green reviewed PR head is not replaced solely by a documentation update. P26-S6A remains VERIFYING pending owner review and merge. P26-S6B is the next bounded slice, but its explicit prerequisite is the S6A merge; do not implement it on this branch. After merge confirmation, fetch and verify current `main`, carry this record onto a fresh branch, and select the merchant-settlement slice without reusing the merged S6A branch.

### P26-S6A merge and P26-S6B selection — 2026-10-24 (America/Denver)

Owner reported PR #385 merged. Connected GitHub and native fetch confirm accepted head `bba4f944b8073189d11ba220810281a78173b70b` is contained in current `main` at merge `e298d2e0bd5e802f8d03c452ce73813a98dc9ff6`; exact-head Maven PR Tests run `40114429370`, job `120932738705`, passed. P26-S6A is DONE. Fresh `codex/P26-S6B-merchant-settlement` starts from that merge and carries the local S6A final-CI record; the merged branch is not reused.

Selected the adopted [P26-S6B contract](P26-S6B-merchant-settlement-assistant.md). Current production already owns merchant receivable roles/components, withholding observations, NMR discharge attribution, dated movement reporting, SCLX, correction/reversal and reconciliation protection. S6B adds a guided Preview/Save workspace over those authorities: entered payout, fee and optional evidenced NMR discharge become one canonical balanced Journal transaction with one retained request identity. Unconfirmed withholding remains a nonfinancial observation. Chargebacks and refunds retain the existing explicit correction route because their accounts and legal treatment cannot be inferred. No migration, provider API, automatic rate, fee, NMR amount or second settlement balance is introduced.

Donor inspection at `c697630ec1f784ebe8338d7300da6c9ac801b180` found only free-text undeposited-fund merchant fields and generic settlement links, with no compatible canonical merchant-settlement workflow to port. Reuse the production stable-ID authorities. Baseline compilation passed after restoring the cached JDK/Maven runtime; implementation, scenario-level tests, full clean verification and actual JavaFX remain.

### P26-S6B implementation and local verification — 2026-10-24 (America/Denver)

Implementation head `dda9c050ad3dfb142c952c6c7ba017b835c9e00a` adds **Accounting -> Merchant Settlement Assistant** over the existing merchant RECEIVABLE, NMR OTHER_LIABILITY and canonical Journal authorities. The workspace lists authoritative open items and requires explicit bank payout, processor fee account/function, optional evidenced NMR discharge, settlement date and retained request identity. Preview produces one balanced transaction and revalidates its source balances and command fingerprint under the company lock before Save; exact retry and restart return the same transaction. Withholding remains a dated nonfinancial observation. Chargebacks and refunds visibly route to Journal correction, and reconciliation continues to own bank clearing. No provider feed, inferred amount/rate, migration, SCLX shape or second balance was added.

`MerchantSettlementAssistantServiceTest` covers payout/fee/withholding, later and combined NMR discharge, no duplicate income, exact retry/restart, stale preview, amount precision and bounds, account/function/evidence validation, Viewer denial and atomic failure. Existing merchant settlement, SCLX and correction/reconciliation tests retain the underlying lifecycle coverage. `MerchantSettlementAssistantPanelTest` covers actual Preview/shared Save, live permission gating, retained result, correction routing, both tables, scrolling and split geometry; the test now resets the shared drill-through opener so it remains isolated after production workspace tests. The P25/P26 unit-coverage ledger, interface-operation matrix, Help route inventory, navigation/factory/registry wiring and CI Xvfb selector include S6B.

Focused accounting regressions passed **25 tests, zero failures/errors and six expected display skips** in the headless portion; the new panel/route subset then passed **9 tests, zero failures/errors/skips** on an actual X display. Final `mvn clean verify` passed **1,035 tests, zero failures/errors and 69 expected display-dependent skips** at 2026-10-24 14:52:38 America/Denver. The exact production JavaFX workflow selector passed **61 tests, zero failures/errors/skips** under Xvfb at 14:54:42. Whitespace review is clean. P26-S6B is VERIFYING pending draft publication, exact-head CI, owner disposable-company checks and merge.

Published `codex/P26-S6B-merchant-settlement` and opened draft [PR #386](https://github.com/benbaron/sca-jakarta-h2/pull/386) against current `main`. The PR records the accounting boundary, scenario-level unit coverage, local clean/actual-JavaFX evidence and disposable-company review steps. This publication-ledger successor follows verified head `c1b24222e054062869ea247f5fec574fe296eb95`; confirm the resulting remote head and tree after pushing it, then inspect exact-head CI. No GitHub CI success or owner desktop result is claimed at this checkpoint.

Final published head `fb7f78131a8ce935dd832f3168112dff3c14234c` and its fetched remote ref match exactly; both resolve tree `aca8c8b3e029ab563ddd51491e7c2a24fab5cb9f`. Exact-head [Maven PR Tests run 40132078817](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40132078817), job `120985945930`, completed successfully: clean headless verification, repeated tests and production JavaFX route compliance all passed; failed-test publication was correctly skipped. The PR description records this evidence. This final PLAN evidence remains local so the green published head is not replaced only by a documentation update. P26-S6B remains VERIFYING pending owner review/acceptance and merge; no human desktop result is inferred.
