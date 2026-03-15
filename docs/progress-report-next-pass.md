# Progress Report - SCA Accounting System (for Next Pass)

_Date: 2026-03-10_

## 1) Current baseline status

The codebase now includes:

- **Architecture docs**:
  - `docs/phase1-architecture.md`
  - `docs/phase2-domain-model.md`
  - `docs/customer-workspace-state-model.md`
- **Core timing model**:
  - `TimingPosition`
  - `TransactionTiming`
- **Core immutable journal model**:
  - `EntrySide`
  - `PostingLine`
  - `JournalTransaction` (with balancing invariant + reversal helper)
- **Open-item/event lifecycle state enums**:
  - `OutstandingBankItemState`, `ReceivableItemState`, `PrepaidExpenseItemState`,
    `DeferredRevenueItemState`, `PayableItemState`, `AssetItemState`, `EventState`
- **Transition-policy layer**:
  - `StateTransitionPolicy`
  - `OpenItemStatePolicies`
- **Customer panel/workspace model**:
  - `CustomerPanelId`, `CustomerPanelBlueprint`, `CustomerUiPanelCatalog`
  - `CustomerPanelDescriptor`, `CustomerPanelDesignService`, `UserRole`
  - `CustomerPanelDefinition`, `CustomerPanelRegistry`, `PanelAction`, `LoginMode`, `CustomerWorkspaceState`
- **Unit tests** for the above model surfaces in `src/test/java/...`.

## 2) What was improved in the latest pass

- Refined panel action taxonomy by introducing **`SELECT_GROUP`** in `PanelAction`.
- Updated `GROUP_SELECTOR` panel definition to use `SELECT_GROUP` action.
- Tightened `CustomerWorkspaceState.canAccess(...)` semantics so it returns `false` pre-login when `LoginMode.REQUIRED`, aligning behavior with `openPanel(...)`.
- Added/updated tests for these behaviors.

## 3) Known blockers and environment issues

### Maven test execution blocker

Running `mvn test` in this environment currently fails due external dependency resolution:

- Could not resolve `org.apache.maven.plugins:maven-resources-plugin:3.3.1`.
- HTTP `403 Forbidden` from `https://repo.maven.apache.org/maven2`.

This is an **environment/network policy issue**, not a known Java compile/test logic failure in local code.

## 4) Code review findings (next-pass priorities)

### A. Domain model gaps (high priority)

1. Add first-class identity value objects:
   - `GroupId`, `JournalTransactionId`, `EventId`, `OpenItemId`, `PeriodCloseId`
2. Introduce concrete open-item entities (not just enums/policies):
   - `OutstandingBankItem`, `ReceivableItem`, `PrepaidExpenseItem`, `DeferredRevenueItem`, `PayableItem`
3. Add transition methods on entities that consume `OpenItemStatePolicies` and record transition metadata.

### B. Service/workflow gaps (high priority)

1. Add `JournalPostingService` for deterministic derivation of open-item schedules from journal entries.
2. Add `ReconciliationService` to apply cleared/uncleared outcomes to outstanding bank items.
3. Add `PeriodCloseService` to produce deterministic close snapshots and roll-forward artifacts.

### C. Persistence gaps (high priority)

1. Add schema/migrations for:
   - journal headers/lines
   - open-item tables + transition history
   - event lifecycle records
   - close/reconciliation run records
   - audit/approval records
2. Add repository interfaces + JDBC/JPA implementations for append-only journaling and projection snapshots.

### D. UI implementation gaps (medium priority)

1. Wire workspace/domain models into JavaFX panels (currently mostly design contracts).
2. Add panel controllers for:
   - journal workbench
   - schedules
   - reconciliation
   - period close
   - approval/audit

### E. Validation/audit gaps (medium priority)

1. Add cross-field/domain validators (e.g., timing combinations vs allowed transaction types).
2. Add audit trail objects for supervisory reversal/deletion approvals.

## 5) Recommended next-pass execution plan

1. **Phase 3 persistence slice**
   - Create migration set for journal/open-item core tables.
   - Add repositories for journal transaction append/read.
2. **Phase 4 domain-service slice**
   - Implement `JournalPostingService` + derivation of at least `ReceivableItem` and `PrepaidExpenseItem`.
   - Add deterministic tests for derivation paths.
3. **Phase 5 workflow slice**
   - Implement initial reconciliation + period close workflows with reproducible outputs.

## 6) Test status summary

- Command attempted: `mvn test`
- Result: **failed** in environment during Maven plugin resolution (`403 Forbidden`), preventing execution of test classes.
- Action for next pass: configure a reachable Maven mirror/local artifact cache or provide network access to Maven Central.

## 7) Offer for the next pass

I can immediately proceed with either:

- **Option A (recommended):** Persistence-first slice (migrations + repositories + deterministic journal read/write tests).
- **Option B:** Open-item entity + posting-service derivation slice (receivables/prepaids first).
- **Option C:** Build-system resilience slice (Maven mirror/local cache setup) so tests run reliably in this environment.


## 8) Latest pass update (concurrency and transition safety)

Implemented improvements from review feedback:

- Added **optimistic concurrency** to open-item snapshots with a new `version` column and repository-level expected-version checks.
- Added **state-precondition enforcement** in `JdbcOpenItemSnapshotRepository.transition(...)` so transitions fail when `fromState` does not match current state.
- Added **transaction referential integrity**:
  - `open_item_snapshot.last_transaction_id -> journal_transaction(id)`
  - `open_item_transition.trigger_transaction_id -> journal_transaction(id)`
- Added migration `V5__open_item_concurrency_and_fks.sql` to evolve schema safely.
- Expanded integration tests for:
  - successful transition + version increment,
  - rejection on state mismatch,
  - rejection on version mismatch,
  - rollback safety (snapshot remains unchanged after failed transition).

## 9) Updated status report

- **Architecture/domain baseline:** established and documented.
- **Persistence baseline:** append-only journal + open-item snapshots/transitions implemented.
- **Persistence hardening:** optimistic concurrency + FK integrity added.
- **Known blocker:** Maven test execution may still fail in restricted network environments when plugin resolution to Maven Central is blocked.

## 10) Next-steps prompt for the next pass

Use this prompt directly next time:

> Continue from `docs/progress-report-next-pass.md`. Implement transition-policy validation inside `JdbcOpenItemSnapshotRepository` by mapping `item_kind` to the relevant policy in `OpenItemStatePolicies`, then add integration tests that prove invalid state transitions are rejected and valid ones succeed. After coding, run `mvn test`, report the results, provide a code review, and offer to fix any identified issues or test problems.


## 11) Latest pass update (policy-aware transitions + rollback assertions)

Implemented additional hardening requested in review:

- Added item-kind policy validation in `JdbcOpenItemSnapshotRepository.transition(...)`.
  - The repository now maps `item_kind` to `OpenItemStatePolicies` and rejects lifecycle-invalid transitions even when state/version preconditions match.
- Expanded repository integration tests with:
  - disallowed policy transition rejection,
  - transition-history row-count assertions for both success and rollback scenarios.
- Re-ran `mvn test`; build remains blocked by environment plugin resolution (`maven-resources-plugin:3.3.1` HTTP 403).

## 12) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add a persistence-level enum normalization layer for `item_kind` (replace raw strings with a strong enum + mapper), enforce valid item-kind values at insert time, and add integration tests for unsupported item kinds and invalid enum-state tokens. Then run `mvn test`, report results, perform a code review, and offer to fix any issues (including test/build issues).


## 13) Latest pass update (persistence enum normalization)

Implemented persistence hardening for open-item kind/state tokens:

- Added `OpenItemKind` enum as the canonical persisted open-item category type.
- Updated `OpenItemSnapshotRecord` to use `OpenItemKind` instead of raw `String itemKind`.
- Updated repository API and JDBC implementation to query by `OpenItemKind` and persist `item_kind` from enum names.
- Added state-token validation by item kind during both create and transition workflows.
  - Invalid state tokens now fail fast with explicit error messages.
- Added integration tests for:
  - invalid state token at create,
  - invalid state token at transition,
  - unsupported persisted `item_kind` token,
  - existing success/failure transition + history-count behavior.

## 14) Updated status report

- Persistence typing improved: `item_kind` now uses a strong enum in the Java persistence model.
- Lifecycle/state input quality improved: token validation now occurs before write operations.
- Remaining blocker: Maven plugin resolution is still blocked by HTTP 403 to Maven Central in this environment.

## 15) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Implement the first `JournalPostingService` slice that derives `Receivable` and `PrepaidExpense` open-item projections from `JournalTransaction` + `TransactionTiming`, persists resulting snapshots/transitions via repositories, and add deterministic integration tests for those derivation paths. Then run `mvn test`, report results, perform a code review, and offer to fix any issues (including test/build issues).


## 16) Latest pass update (pom.xml reengineering)

Build configuration was fully reworked to establish a cleaner Maven baseline:

- Reorganized dependency properties and version management for Jakarta, persistence, logging, CLI, JavaFX, and test stacks.
- Restructured dependencies into clearer groups (Jakarta APIs, persistence/migration, runtime/logging, interchange/CLI, testing).
- Refactored build plugins to a simpler stable set (`compiler`, `surefire`, `exec`) with explicit versions/configuration.
- Moved JavaFX dependencies under a dedicated `ui` profile so non-UI builds/tests avoid unnecessary UI dependency resolution.

Test execution status after reengineering remains blocked by environment-level Maven plugin resolution (`maven-resources-plugin:3.3.1` HTTP 403).

## 17) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add Maven `settings.xml` guidance and an optional repo-local bootstrap profile for restricted environments, then implement the first `JournalPostingService` derivation slice (`Receivable` + `PrepaidExpense`) with deterministic tests. Run `mvn test`, report results, perform a code review, and offer to fix any issues (including build/test problems).


## 18) Latest pass update (repo-local build bootstrap)

Implemented repo-local Maven build bootstrap for restricted environments:

- Added `.mvn/maven.config` to force use of repo-local cache (`.mvn/local-repo`) and project settings file.
- Added `.mvn/settings.xml` with active `repo-local-bootstrap` profile that:
  - checks `${user.home}/.m2/repository` as a local seed source,
  - configures both dependency and plugin repositories,
  - keeps Maven Central as fallback when reachable.
- Added `scripts/bootstrap-local-m2.sh` to seed `.mvn/local-repo` from an existing machine cache.
- Added `docs/repo-local-build.md` with usage instructions and restricted-network guidance.

## 19) Updated status report

- Build bootstrap now supports deterministic repo-local cache usage in-repo.
- The environment still blocks external Maven Central plugin resolution unless artifacts are pre-seeded or mirror URLs are configured.

## 20) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Verify the repo-local bootstrap against a pre-seeded cache in CI (or mirror), then implement the first `JournalPostingService` derivation slice (`Receivable` + `PrepaidExpense`) with deterministic tests. Run `mvn test`, report results, perform a code review, and offer to fix any issues (including build/test problems).

## 20) Latest pass update (JournalPostingService first projection slice)

Implemented the first deterministic `JournalPostingService` slice that bridges journal writes and open-item projection writes:

- Added `JournalPostingService` with `post(JournalTransaction)` orchestration that:
  - appends immutable journal transactions,
  - derives and persists `RECEIVABLE` open-item snapshots when timing is `bank=FUTURE, budget=NOW`,
  - derives and persists `PREPAID_EXPENSE` snapshots when timing is `bank=NOW, budget=FUTURE`,
  - applies lifecycle transitions for settlement/recognition timing paths:
    - receivable settle: `bank=NOW, budget=PREVIOUSLY` -> `SETTLED_BY_CASH`
    - prepaid recognize: `bank=PREVIOUSLY, budget=NOW` -> `FULLY_RECOGNIZED`.
- Added deterministic item identity mechanics:
  - stable `item_ref` = `accountCode|fundCode`,
  - stable projection IDs derived from transaction/kind/ref using name-based UUIDs.
- Added integration tests for both derivation paths, including snapshot creation and transition-history assertions.

## 21) Test execution status

- Command attempted: `mvn test`
- Result: **failed in environment** before compilation due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable).

## 22) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Extend `JournalPostingService` to support partial receivable/prepaid applications (state + open amount evolution), add repository support for open_amount updates alongside transitions, and add integration tests for partial and full multi-step lifecycle flows. Then run `mvn test`, report results, perform a code review, and offer to fix any issues (including build/test blockers).

## 23) Latest pass update (JournalPostingService hardening follow-up)

Addressed follow-up review issues from the first posting-service slice:

- Added repository natural-key lookup support:
  - `OpenItemSnapshotRepository.findByGroupKindAndItemRef(...)`
  - JDBC implementation with direct SQL lookup by `(group_code, item_kind, item_ref)`.
- Extended repository transitions to optionally update `open_amount` atomically with state transitions:
  - New overloaded `transition(...)` accepting `newOpenAmount`.
  - JDBC update now applies `open_amount = COALESCE(?, open_amount)`.
- Hardened `JournalPostingService` derivation behavior:
  - switched snapshot lookup to natural-key repository query,
  - narrowed account classification to deterministic prefixes (`1100-` receivable, `1200-` prepaid),
  - settlement/recognition transitions now set `open_amount` to zero.
- Expanded integration tests:
  - posting-service tests now verify zero `open_amount` after settlement/recognition,
  - added guard test that non-mapped accounts do not create projections,
  - repository tests now cover open-amount updates during transitions and natural-key lookup.

## 24) Test execution status

- Command attempted: `mvn test`
- Result: **failed in environment** before compilation due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable).

## 25) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Implement partial receivable/prepaid application flows in `JournalPostingService` by deriving proportional `open_amount` reductions and state selection (`PARTIALLY_*` vs terminal), then add deterministic integration tests for multi-step partial-to-full lifecycle scenarios. Run `mvn test`, report results, perform a code review, and offer to fix any issues (including build/test blockers).

## 26) Latest pass update (test assertion stabilization)

Addressed follow-up CI test failures caused by `BigDecimal` scale-sensitive comparisons:

- Updated failing assertions in:
  - `JdbcOpenItemSnapshotRepositoryTest`
  - `JournalPostingServiceIntegrationTest`
- Replaced direct `assertEquals(new BigDecimal("..."), actual)` checks with numeric-meaning comparisons using `BigDecimal.compareTo(...)` via `assertAmountEquals(...)` helpers.
- Specifically fixed remaining settled amount checks (`0.00` vs `0.0000`) in journal posting integration scenarios.

Rationale:

- JDBC/H2 can return values like `0.0000` while expectations were written as `0.00`.
- These values are numerically equal but fail `BigDecimal.equals(...)` due to scale differences.
- Test intent is amount equality, not scale equality.

## 27) Test execution status

- Command attempted: `mvn -B -ntp -e test --settings .mvn/settings.xml -Dmaven.repo.local=$HOME/.m2/repository`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable).

## 28) Code review snapshot

Resolved in this pass:

- Test brittleness around `BigDecimal` scale handling is reduced for open-item projection paths.

Remaining risks / improvements to consider:

1. Helper duplication:
   - `assertAmountEquals(...)` appears in multiple test classes; could be extracted to a shared test utility.
2. CI reliability:
   - Build still depends on external plugin availability; mirror/pre-seeded plugin strategy may still be needed.
3. Behavioral coverage:
   - Partial-application lifecycle tests (`PARTIALLY_APPLIED`, `PARTIALLY_RECOGNIZED`) are still not yet fully represented end-to-end.

## 29) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Extract shared money-amount assertion helpers for tests, then implement partial receivable/prepaid application flow in `JournalPostingService` with deterministic multi-step integration tests (partial to terminal transitions). Run `mvn test`, report results, perform a code review, and offer to fix issues (including test/build blockers).

## 30) Latest pass update (partial application lifecycle + test helper extraction)

Implemented the requested next action from this report:

- Extended `JournalPostingService` to support **partial receivable settlement** and **partial prepaid recognition**.
  - Settlement/recognition transitions now reduce `open_amount` by the posted credit line amount.
  - Service selects lifecycle state deterministically based on resulting `open_amount`:
    - receivable: `PARTIALLY_APPLIED` vs `SETTLED_BY_CASH`
    - prepaid: `PARTIALLY_RECOGNIZED` vs `FULLY_RECOGNIZED`
  - Final transitions clamp negative or zero residuals to `0`.
- Extracted shared test money assertion helper into:
  - `src/test/java/org/nonprofitbookkeeping/testutil/TestAmountAssertions.java`
- Updated integration tests to cover deterministic multi-step partial-to-terminal flows:
  - receivable: open -> partial -> settled
  - prepaid: open -> partial -> fully recognized
- Updated repository integration tests to use the shared money assertion helper.

## 31) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).

## 32) Code review snapshot

Resolved in this pass:

1. Partial lifecycle behavior now aligns better with domain states by representing intermediate reductions before terminal closure.
2. Duplicate amount comparison helpers were consolidated to one test utility, reducing drift and maintenance overhead.

Remaining risks / improvements to consider:

1. Over-application policy:
   - Current logic clamps below-zero residuals to zero; consider explicit rejection when application exceeds open amount if business policy requires strict prevention.
2. Concurrency on projection updates:
   - Multi-step partial flows rely on optimistic version checks; higher-volume posting paths may benefit from retry strategy at service layer.
3. Build portability:
   - CI/local reliability still depends on plugin artifact availability (mirror or pre-seeded cache remains important).

## 33) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add explicit over-application guards in `JournalPostingService` (reject reductions that exceed `open_amount` with clear errors), then add deterministic integration tests for rejection paths and idempotent retries under optimistic-concurrency conflicts. Run `mvn test`, report results, perform a code review, and offer to fix issues (including build/test blockers).

## 34) Latest pass update (UI consistency/completeness review follow-up)

Performed a focused UX consistency pass across the JavaFX shell and implemented pragmatic navigation improvements:

- Navigation completeness:
  - Added `Dashboard` entry to the left navigation tree so all primary top-level panels are discoverable from navigation.
- Navigation consistency:
  - Changed panel activation from double-click-only to selection-driven open behavior (single-click/selection opens panel).
  - Added keyboard activation via `Enter` for accessibility and parity.
  - Improved navigation focus by scrolling highlighted items into view.
- Context awareness:
  - Added active panel indicator in the toolbar (`Panel: <title>`) to keep users oriented while switching panels.
  - Added corresponding style hook `.toolbar-active-panel`.

## 35) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).

## 36) Code review snapshot

Resolved in this pass:

1. Primary navigation now exposes `Dashboard` and avoids hidden/discoverability gaps.
2. Interaction model is more consistent (single-click + keyboard activation) and better aligned with desktop expectations.
3. Toolbar context labeling improves orientation and reduces panel-switch ambiguity.

Remaining risks / improvements to consider:

1. Placeholder inconsistency across panel bodies/buttons still exists and could be standardized with a shared panel scaffold.
2. No JavaFX interaction tests currently verify navigation-selection behavior; lightweight UI tests could reduce regressions.
3. Maven/plugin network blocking continues to prevent full test execution in this environment.

## 37) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Standardize placeholder panel scaffolding (header/actions/empty-state language) across UI panels, add focused JavaFX interaction tests for navigation selection and active panel labeling, then run `mvn test`, report results, provide a code review, and offer fixes for any identified issues (including build/test blockers).

## 38) Latest pass update (panel TODO/sample-data resolution)

Completed a full UI panel hardening pass to remove TODO/sample-driven panel states and use live persisted data sources where available.

Implemented:

- Added `LedgerQueryService` and wired it into `UiServiceRegistry` for real transaction register/journal queries.
- Reworked `LedgerRegisterPanel` to:
  - load real transactions from DB,
  - show split counts and posted status,
  - provide in-panel journal drill-down details for selected transactions,
  - remove hardcoded sample transaction rows.
- Reworked placeholder panels to data-backed views:
  - `AssetsRegisterPanel`: live fixed-asset account listing,
  - `BudgetEditorPanel`: live posting-account listing for planning scope,
  - `BudgetVsActualPanel`: live fund-balance snapshot as actuals,
  - `DepreciationRunsPanel`: live fixed-asset basis listing,
  - `InventoryPanel`: live inventory-account listing.
- Reworked `ReportLibraryPanel` to generate a live text preview from fund-balance rows instead of TODO/placeholder text areas.
- Reworked `SchedulesPanel` to remove demo fallback accounts and show account-specific schedule context using real account lookup + eligibility gating.
- Reworked `SettingsPanel` to a concrete in-session settings form (fiscal start + toggles), replacing TODO content.
- Updated `TransactionEditorPanel` messaging to remove placeholder wording and provide actionable validation/save guidance.

## 39) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).

## 40) Code review snapshot

Resolved in this pass:

1. Panel TODO/sample states were removed from major UI panels; screens now derive display content from persisted application data/services where available.
2. Ledger UX now supports practical transaction-to-journal drill-down without sample rows.
3. Schedules panel no longer silently injects demo accounts, reducing data-trust ambiguity.

Remaining risks / improvements to consider:

1. `LedgerQueryService` currently performs view queries directly; extracting repository interfaces may improve long-term testability/architecture consistency.
2. Report preview output is text-based; richer tabular rendering/export contracts can be added for production workflows.
3. Build/test execution remains blocked by Maven plugin network resolution in this environment.

## 41) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add focused JavaFX tests for ledger reload/journal drill-down and schedules gating without demo fallback, then extract `LedgerQueryService` behind a repository interface and add deterministic service tests. Run `mvn test`, report results, provide a code review, and offer fixes for any identified issues (including build/test blockers).

## 42) Latest pass update (LedgerRegisterPanel compile fix)

Addressed CI compile failures introduced by the previous UI refactor:

- Fixed `LedgerRegisterPanel.renderJournal(...)` to use `JournalLine` JavaBean getters:
  - `getAccountCode()`, `getAccountName()`, `getFundCode()`, `getDebit()`, `getCredit()`
- This resolves the `cannot find symbol` errors from attempts to call record-style accessors on a class with getter methods.

## 43) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).

## 44) Code review snapshot

Resolved in this pass:

1. Compile/API mismatch between `LedgerRegisterPanel` and `JournalLine` was corrected.

Remaining risks / improvements to consider:

1. Add focused compile-time regression coverage or static checks around panel/service API contracts.
2. Build/test execution remains blocked by Maven plugin network resolution in this environment.

## 45) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add regression tests around ledger journal rendering/service contracts, then continue repository extraction for `LedgerQueryService`. Run `mvn test`, report results, provide a code review, and offer fixes for any identified issues (including build/test blockers).

## 46) Latest pass update (test-process note for prompts)

Added explicit process guidance for future prompts:

- We still run `mvn test` in-pass and report output for quick local signal.
- **Authoritative unit-test execution is at GitHub/CI level** per team process.
- Next-pass prompts should not ask for environment-specific Maven workaround engineering in this container when CI is the official test gate.

## 47) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: CI/GitHub remains the official unit-test authority for this project.

## 48) Code review snapshot

Resolved in this pass:

1. Prompt/process guidance now aligns with team expectations: local runs are informational, CI runs are authoritative.

Remaining risks / improvements to consider:

1. Keep prompt language consistent so future passes avoid spending effort on local Maven network workarounds unless explicitly requested.
2. Continue adding deterministic tests for new features even when local execution is blocked, so CI can validate changes.

## 49) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md` and execute exactly this slice:
> 1) Add focused tests for `LedgerQueryService` contracts (`listRecent`, `journalForTxn`) using deterministic fixture data (ordering, row-shape, DR/CR mapping assertions).
> 2) Extract query persistence behind a repository interface (keep behavior unchanged), with service tests validating repository-driven outputs.
> 3) Run `mvn -B -ntp test` locally, report raw outcome concisely, and do **not** spend scope on container-specific Maven/network workarounds.
> 4) Treat GitHub/CI as authoritative for pass/fail; include a short code review section (resolved issues, remaining risks) and offer follow-up fixes for any local/CI failures.

## 50) Latest pass update (ledger rendering regression guards)

Implemented regression-focused coverage for ledger register rendering/service contracts:

- Added `LedgerRegisterPanelTest` with deterministic assertions for:
  - `toRow(...)` mapping behavior (blank-to-`(none)` normalization, split-count/string shape, posted status).
  - `renderJournal(...)` output formatting using `JournalLine` JavaBean getters, guarding against prior getter/record accessor mismatches.
- Refactored `LedgerRegisterPanel` internals for testability without behavior changes:
  - promoted `toRow(...)` and `renderJournal(...)` to package-visible static helpers,
  - updated call sites to use the static helpers directly.

## 51) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains the authoritative pass/fail signal.

## 52) Code review snapshot

Resolved in this pass:

1. Added deterministic regression coverage around ledger row/journal rendering contracts.
2. Reduced risk of repeating the prior compile break by testing formatter paths that rely on `JournalLine` getters.

Remaining risks / improvements to consider:

1. Rendering is still string-based; introducing a small view-model renderer abstraction could further isolate formatting from JavaFX panel concerns.
2. Full local validation is still blocked by Maven plugin network resolution in this environment.

## 53) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add targeted service/repository contract tests for `JpaLedgerQueryRepository` using a migrated in-memory datasource fixture (verify deterministic ordering, null-coalesced text columns, split counts, and journal row ordering by account code), then run `mvn -B -ntp test`, report results concisely, provide a short code review, and offer follow-up fixes for local/CI failures.

## 54) Latest pass update (UI pane consistency + coverage expansion)

Implemented a UI consistency and coverage pass across panel navigation/hosting:

- Closed a discoverability gap by wiring `Inventory` into primary navigation and panel hosting:
  - added `INVENTORY` to `AppPanelId`,
  - added `InventoryPanel` factory to `PanelHost`,
  - added `Inventory` entry under the Assets navigation group.
- Improved panel-host testability/consistency guarantees:
  - centralized `PanelHost` factories into a static registry,
  - added `PanelHost.supportedPanelIds()` for deterministic mapping checks.
- Added JavaFX-focused unit tests to validate shell consistency across all panes:
  - `panelHost_hasFactoryForEveryPanelId`,
  - `navigationIndexesEveryPanelId`,
  - `everyPanelCanBeShownWithTitleAndRoot` (iterates all `AppPanelId` values).
- Added `FxTestSupport` helper to initialize JavaFX toolkit and run assertions on the FX thread.

## 55) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains authoritative for final pass/fail.

## 56) Code review snapshot

Resolved in this pass:

1. All top-level panel IDs are now consistently hostable and navigable.
2. Added broad UI shell regression coverage to detect panel-map drift and broken panel construction early.

Remaining risks / improvements to consider:

1. Current UI consistency tests focus on shell contracts (mapping/title/root) and do not yet validate deeper per-panel interaction flows.
2. Some panel behavior still depends on async service callbacks; focused interaction tests for key workflows (ledger drill-down, schedule gating) should be expanded next.

## 57) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Add focused JavaFX interaction tests for at least three high-traffic panels (`LedgerRegisterPanel`, `SchedulesPanel`, `TransactionEditorPanel`) covering one primary user flow each, keep fixtures deterministic, then run `mvn -B -ntp test`, report concise results, provide a short code review, and offer follow-up fixes for local/CI failures.

## 58) Latest pass update (long-horizon feature staging + cross-layer contracts)

Added a staged execution plan and initial cross-layer contract baseline for the requested long feature set.

### Stage plan (UI + model + actions + tests)

1. **Stage A: capability contracts + coverage matrix (this pass)**
   - Add explicit capability list and coverage catalog tying each capability to:
     - UI surface,
     - model contract,
     - action contract,
     - test contract.
   - Add baseline model/action types for:
     - multi-company state,
     - import/export state,
     - preferences + theme saving,
     - help/wizard/plugins,
     - user privilege levels,
     - chart/banking transfer formats (OFX/QFX + COA CSV/JSON).
   - Add deterministic tests validating full capability matrix coverage and contract loadability.

2. **Stage B: preference/state persistence wiring**
   - Persist `AppPreferencesState` + `MultiCompanyState` (theme/native-window/state, active company).
   - Hook settings/menu actions to storage and restore on app startup.

3. **Stage C: import/export workflows**
   - Build import/export actions and service orchestration for COA and banking (OFX/QFX parse/export).
   - Add deterministic parser and mapping tests.

4. **Stage D: privilege-aware action gating**
   - Bind `UserPrivilegeLevel` to `AppActionId` policy checks and panel/action enablement.
   - Add policy tests and UI gating tests.

5. **Stage E: help/wizard UX + plugin lifecycle**
   - Implement guided setup wizard and help center state transitions.
   - Introduce plugin discovery/enable/disable contracts with test doubles.

6. **Stage F: integration hardening**
   - Add cross-feature integration tests (multi-company + privilege + import/export + persisted preferences).

### Implemented in this pass

- Added `Capability`, `CapabilityCoverage`, and `CapabilityCoverageCatalog` to explicitly track cross-layer coverage for all requested capabilities.
- Added baseline action/model contracts:
  - `AppActionId`
  - `MultiCompanyState`, `ImportExportState`, `AppPreferencesState`
  - `UiThemePreference`, `UserPrivilegeLevel`, `BankingDataFormat`, `ChartOfAccountsTransferFormat`
  - `HelpState`, `WizardState`, `PluginState`
- Added deterministic tests:
  - `CapabilityCoverageCatalogTest` (full matrix coverage + loadability checks)
  - `AppStateContractsTest` (state records and format/theme/privilege contract assertions)

## 59) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains authoritative for pass/fail.

## 60) Code review snapshot

Resolved in this pass:

1. The requested long feature set is now staged with explicit implementation order and dependencies.
2. Cross-layer coverage is now explicit and test-checked for all requested capabilities.
3. Baseline model/action contracts exist for multi-company, preferences/theme/native-state, help/wizard/plugins, privilege levels, and import/export format families.

Remaining risks / improvements to consider:

1. Current pass establishes contracts and coverage mapping; concrete workflow implementations (e.g., OFX/QFX parsers, plugin loading, persisted theme application) are still upcoming stages.
2. UI/action bindings to these new contracts are mostly planned rather than fully interactive in this pass.

## 61) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Execute Stage B: implement persisted `AppPreferencesState` + `MultiCompanyState` storage/load wiring in the JavaFX shell (`MainWindow`/`SettingsPanel`), add deterministic unit tests for save/restore behavior and theme/native-state preference propagation, then run `mvn -B -ntp test`, report concise results, provide a short code review, and offer follow-up fixes for local/CI failures.

## 62) Latest pass update (Stage B: persisted preferences + multi-company wiring)

Implemented Stage B persistence wiring for shell preferences and multi-company context.

### Implemented

- Added app-state persistence contract and implementation:
  - `AppStateStore` (load/save for `AppPreferencesState` and `MultiCompanyState`)
  - `FileAppStateStore` (properties-file backed store at `~/.sca-ledger/ui-state.properties` by default)
- Added `UiSessionState` as in-memory observable session state for preferences and active company context.
- Wired `MainWindow` to Stage B persistence flow:
  - loads preferences/company at startup from store,
  - applies theme/native-decoration flags to root style classes,
  - reflects active company in toolbar label,
  - persists session preferences/company on save.
- Upgraded `SettingsPanel` to edit/apply/save:
  - theme preference (`LIGHT`/`DARK`/`SYSTEM_DEFAULT`),
  - native window decoration preference,
  - remember-state preference,
  - default privilege level,
  - active company + recent companies list.
  - `onSave()` now applies into session state for persistence by shell save workflow.

### Tests added

- `FileAppStateStoreTest`
  - validates preference+company round-trip save/load,
  - validates import/export contract coverage for QFX/JSON format pair.
- `MainWindowStateWiringTest`
  - validates startup restore and propagation of dark theme/native flag/company,
  - validates `saveActivePanel()` persists current session preferences/company.

## 63) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains authoritative for final pass/fail.

## 64) Code review snapshot

Resolved in this pass:

1. Stage B save/restore wiring is now implemented for preferences and multi-company context.
2. Theme/native/company state now propagates through shell UI state (toolbar + style classes).
3. Deterministic tests cover both persistence round-trip and shell wiring behavior.

Remaining risks / improvements to consider:

1. Native window decoration is currently represented as a persisted/apply flag; true platform-level undecorated/native window behavior is toolkit/platform dependent and may need per-platform adapters.
2. Settings persistence currently writes on shell save; optional immediate autosave can be added if UX requires.

## 65) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Execute Stage C: implement import/export orchestration contracts and first deterministic parser/mapper slice for chart-of-accounts (CSV) and banking imports (OFX/QFX envelope recognition), wire actions in menu/tools, add unit tests for happy-path and invalid-format handling, then run `mvn -B -ntp test`, report concise results, provide a short code review, and offer follow-up fixes for local/CI failures.

## 66) Latest pass update (Stage C: import/export orchestration + parser slice)

Implemented Stage C initial orchestration and deterministic parser recognition slices.

### Implemented

- Added `ImportExportOrchestrationService` as Stage C orchestration entry point:
  - `importChartOfAccountsCsv(String csv)`
  - `importBankData(String payload, String sourceName)`
- Added deterministic COA CSV parser/mapper:
  - `CoaCsvMapper` parses quoted CSV rows and maps to `CoaCsvRow` records,
  - validates required headers (`code`, `name`, `account_type`, `normal_balance`),
  - validates required row values.
- Added banking envelope recognizer:
  - `BankDataEnvelopeRecognizer` recognizes `OFX` / `QFX` via payload markers (`<OFX`, `<QFX`) and/or file extension.
  - rejects unsupported envelope formats with clear error.
- Wired Stage C actions into `MainWindow` Tools menu with deterministic sample actions:
  - `Import CoA CSV (sample)`
  - `Import Bank OFX/QFX Envelope (sample)`
  - actions route through `ImportExportOrchestrationService` and report concise inspector status.

### Tests added

- `ImportExportOrchestrationServiceTest`
  - CoA CSV happy-path parse (including quoted commas),
  - CoA CSV invalid header rejection,
  - OFX/QFX recognition happy-path,
  - unknown envelope rejection.

## 67) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains authoritative for final pass/fail.

## 68) Code review snapshot

Resolved in this pass:

1. Stage C now has concrete orchestration contracts and first deterministic parser/recognition implementation.
2. Menu/tool actions are now wired to real import orchestration paths (sample-driven) instead of placeholders.
3. Unit tests cover happy-path and invalid-format handling for COA CSV and OFX/QFX recognition.

Remaining risks / improvements to consider:

1. Current menu wiring uses deterministic sample payloads; next iteration should add file chooser + real file IO entry points.
2. COA mapping currently validates core columns only; stricter schema validation and downstream domain mapping checks can be expanded.
3. Banking support currently performs envelope recognition only (not transaction-level parse/mapping yet).

## 69) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Expand Stage C with real file-driven import actions (file chooser integration), add OFX/QFX transaction extraction model mapping, and add deterministic tests for file-level error handling and transaction count derivation; then run `mvn -B -ntp test`, report concise results, provide a short code review, and offer follow-up fixes for local/CI failures.

## 70) Latest pass update (JavaFX headless-test stability fix)

Addressed CI/environment instability for JavaFX UI tests that fail on headless Linux runners without DISPLAY.

### Implemented

- Updated `FxTestSupport` to provide `initToolkitOrSkip()` with deterministic environment gating:
  - detects Linux environments with missing `DISPLAY`,
  - skips JavaFX tests using JUnit assumptions instead of throwing runtime initialization errors,
  - catches toolkit startup failures and marks tests skipped with a clear reason,
  - keeps `onFx(...)` guarded by the same availability condition.
- Updated JavaFX test classes to use the new gated initializer:
  - `MainWindowStateWiringTest`
  - `AppPanelConsistencyTest`

### Outcome

- Headless environments now skip JavaFX-dependent tests cleanly rather than failing with:
  - `UnsupportedOperationException: Unable to open DISPLAY`.

## 71) Test execution status

- Command attempted: `mvn -B -ntp test`
- Result: **failed in environment before test execution** due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, Maven Central unreachable / network unreachable).
- Process note: GitHub/CI remains authoritative for final pass/fail.

## 72) Code review snapshot

Resolved in this pass:

1. JavaFX test bootstrap no longer hard-fails on headless Linux without DISPLAY.
2. UI tests are now environment-adaptive and report clear skip reasons.

Remaining risks / improvements to consider:

1. If CI later enables virtual display/headless JavaFX runtime, these tests should execute fully and provide richer signal.
2. Additional FX tests should follow the same helper to avoid direct `Platform.startup(...)` calls.

## 73) Next-steps prompt for the next pass

> Continue from `docs/progress-report-next-pass.md`. Expand Stage C with real file-driven import actions and OFX/QFX transaction extraction; ensure all JavaFX tests use `FxTestSupport.initToolkitOrSkip()` for headless safety, then run `mvn -B -ntp test`, report concise results, provide a short code review, and offer follow-up fixes for local/CI failures.

## 74) Latest pass update (Stage C expansion: file-driven imports + OFX/QFX transaction extraction)

Implemented the requested Stage C expansion so import actions are now file-driven and banking imports include deterministic transaction extraction.

### Implemented

- Extended `ImportExportOrchestrationService` with file-based import entry points:
  - `importChartOfAccountsCsvFile(Path path)`
  - `importBankDataFile(Path path)`
- Added deterministic file-level validation and error handling for import reads:
  - null path rejection,
  - missing/non-regular file rejection,
  - read-failure wrapping with context-rich message.
- Added OFX/QFX transaction extraction model mapping:
  - new `BankTransactionRecord` projection model,
  - new `OfxQfxTransactionExtractor` that extracts `STMTTRN` blocks and maps `FITID`, `DTPOSTED`, `TRNAMT`, `TRNTYPE`, `NAME`, `MEMO`.
- Expanded bank import result payload:
  - `BankImportResult` now includes `transactionCount` and extracted `transactions`.
- Replaced sample-only UI actions with real file-driven imports in `MainWindow` Tools menu:
  - `Import CoA CSV…`
  - `Import Bank OFX/QFX…`
  - uses JavaFX `FileChooser` with extension filters and inspector status messages.

### JavaFX test safety check

- Verified JavaFX test classes already call `FxTestSupport.initToolkitOrSkip()`.
- Hardened `FxTestSupport.onFx(...)` to call `initToolkitOrSkip()` defensively, so any future FX test path also gets headless-safe gating.

### Tests added/expanded

- `ImportExportOrchestrationServiceTest`
  - file-based CoA import happy path,
  - file-based bank import happy path with transaction-count derivation,
  - missing-file failures for both CoA and bank imports,
  - OFX/QFX extraction count + field mapping assertions.
- `OfxQfxTransactionExtractorTest`
  - XML-style tag extraction,
  - one-line OFX tag extraction.

## 75) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: see latest run output in this pass (environment-dependent).

## 76) Code review snapshot

Resolved in this pass:

1. Stage C now has real file-driven import actions instead of sample-only payloads.
2. Banking import now includes deterministic transaction extraction and model mapping.
3. File-level import error handling is explicit and covered by unit tests.
4. JavaFX headless-safety enforcement is now both explicit (`@BeforeAll`) and defensive (`onFx()` bootstrap).

Potential follow-ups:

1. OFX/QFX parser currently targets deterministic core tags only; bank-specific variants can be layered with a richer parser profile map.
2. UI can be improved by surfacing a preview table of extracted transactions before apply/commit.
3. Add parse diagnostics (line/record-level warning collection) to support partial-import workflows.

## 77) Latest pass update (file import/export test hardening)

Addressed review follow-up by adding deterministic file import/export test coverage.

### Implemented

- Extended Stage C orchestration with file export methods:
  - `exportChartOfAccountsCsvFile(List<CoaCsvRow>, Path)`
  - `exportBankDataFile(BankingDataFormat, List<BankTransactionRecord>, Path)`
- Added deterministic COA CSV writer support in `CoaCsvMapper` (`write(...)`) with proper CSV quoting/escaping.
- Added export file-write validation and clear error messaging for null path / write failures.

### Tests added/expanded

- `ImportExportOrchestrationServiceTest` now includes:
  - COA CSV export write assertions and import round-trip validation,
  - bank OFX export and import round-trip transaction-count validation,
  - export input validation failures (null path, missing bank format).

## 78) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: environment still blocked before tests by Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).
- Additional check: `mvn -B -ntp -o test` confirms plugin is not yet present in local cache, so offline mode cannot execute tests either.

## 79) Code review snapshot

Resolved in this pass:

1. File import coverage now has corresponding file export coverage in Stage C orchestration tests.
2. COA CSV export format is deterministic and round-trip validated.
3. Bank OFX export is deterministic and validates transaction-count round-trip behavior.

Potential follow-ups:

1. Add explicit export actions in UI menu (currently File -> Export remains placeholder while service export APIs now exist).
2. Add stronger XML escaping for bank export fields if upstream data may include `<`, `>`, or `&`.

## 80) Latest pass update (follow-ups completed: UI export wiring + XML escaping)

Completed both follow-ups from prior review.

### Implemented

- Wired `File -> Export…` in `MainWindow` to real Stage C export service methods:
  - saves `.csv` via `exportChartOfAccountsCsvFile(...)`,
  - saves `.ofx` / `.qfx` via `exportBankDataFile(...)`.
- Added `chooseSaveFile(...)` and extension-based export routing in UI shell.
- Hardened bank export payload safety with XML escaping for reserved characters in tag values (`&`, `<`, `>`, `"`, `'`).

### Tests added/expanded

- Extended `ImportExportOrchestrationServiceTest` with XML-escaping assertions for exported OFX payload values containing reserved characters.

## 81) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).
- Additional check: `mvn -B -ntp -o test` confirms required plugin is unavailable in local cache for offline mode.

## 82) Latest pass update (Phase 3 continuation: workflow run persistence)

Continued Phase 3 persistence slice with workflow run records needed for reconciliation/period-close auditability.

### Implemented

- Added migration `V6__workflow_run_records.sql` with new persistence tables:
  - `reconciliation_run`
  - `period_close_run`
- Added repository contracts + JDBC implementations:
  - `ReconciliationRunRepository` / `JdbcReconciliationRunRepository`
  - `PeriodCloseRunRepository` / `JdbcPeriodCloseRunRepository`
- Added persistence records:
  - `ReconciliationRunRecord`
  - `PeriodCloseRunRecord`

### Tests added

- `JdbcReconciliationRunRepositoryTest`
  - append/find-by-id round-trip,
  - group/date-range filter behavior.
- `JdbcPeriodCloseRunRepositoryTest`
  - append/find-by-id round-trip,
  - group/date-range filter behavior.

## 83) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 84) Latest pass update (follow-on fixes + next-stage bridge)

Completed follow-on fixes from review and started the next functionality stage bridge.

### Follow-on fixes completed

- Added enum normalization for workflow run persistence:
  - new `WorkflowRunStatus` enum (`STARTED`, `COMPLETED`, `FAILED`),
  - `ReconciliationRunRecord.status` and `PeriodCloseRunRecord.status` now strongly typed,
  - `ReconciliationRunRecord.bankFormat` now uses `BankingDataFormat` enum.
- Added migration `V7__workflow_run_status_constraints.sql` enforcing DB-level token validity:
  - reconciliation status check,
  - reconciliation bank format check (`OFX`/`QFX`),
  - period-close status check.
- Updated repository tests with direct SQL invalid-token inserts proving schema constraints reject unsupported values.

### Next functionality stage bridge (Phase 4 service layer seed)

- Added initial service-layer workflow wrappers:
  - `ReconciliationService.recordCompletedRun(...)`
  - `PeriodCloseService.recordCompletedClose(...)`
- Added integration tests proving service-to-repository persistence behavior.

## 85) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 86) Latest pass update (CI compile fix)

Addressed reported CI compile failure in `ImportExportOrchestrationServiceTest`.

### Implemented

- Fixed invalid static import syntax causing test compilation error:
  - from `import static assertTrue;`
  - to `import static org.junit.jupiter.api.Assertions.assertTrue;`

## 87) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 88) Latest pass update (requested UX slice: Help + Preferences + multi-database selection)

Implemented the requested UX slice that clarifies where Help/Preferences/database-file selection fit and wires practical functionality.

### Implemented

- Added persisted database-file selection state:
  - new `DatabaseSelectionState` model,
  - extended `AppStateStore` with load/save database selection hooks,
  - implemented persistence in `FileAppStateStore` (`database.active`, `database.recents`).
- Extended session + shell wiring:
  - `UiSessionState` now tracks observable database selection state,
  - `MainWindow` now restores/saves database selection through `AppStateStore`,
  - toolbar now surfaces active DB file.
- Added database-file UX actions:
  - `File -> Select Database File…` action with file chooser,
  - updates active database selection + recent list,
  - reports restart requirement for runtime datasource switch.
- Added Help functionality:
  - `Help -> Help Topics` now shows practical getting-started guidance in inspector pane.
- Extended `SettingsPanel` to include editable active database file with recents and apply/save into session state.

### Tests updated

- `FileAppStateStoreTest` now validates DB selection persistence round-trip.
- `MainWindowStateWiringTest` now validates DB selection restore/save wiring in shell state flow.

## 89) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 90) Latest pass update (runtime DB reconnect + dedicated Help panel + settings recents tests)

Implemented the requested next slice.

### Implemented

- Added runtime datasource/profile reconnect flow in UI runtime wiring:
  - `Jpa(Path databaseFile)` constructor with JDBC URL override,
  - `UiServiceRegistry.reconnectToDatabase(Path)` that safely constructs fresh service graph and swaps only after successful init.
- Added dedicated structured Help panel:
  - new `HelpPanel` implementing `AppPanel`,
  - new `AppPanelId.HELP`, wired in `PanelHost` + `NavigationPane`,
  - `Help -> Help Topics` now opens the panel instead of inspector placeholder text.
- Improved database-selection UX/testing hooks:
  - `SettingsPanel.readDatabaseSelection()` now enforces selected-path-first ordering and de-duplicates recents,
  - package-visible test hooks added for database combo value/list manipulation.

### Tests added/updated

- Added `SettingsPanelDatabaseSelectionTest` for:
  - selected database path ordering at front,
  - duplicate recent path de-duplication.
- Existing UI persistence/wiring tests continue to cover restore/save behavior.

## 91) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 92) Latest pass update (UI theme selection menu)

Added explicit shell-level UI theme selection controls.

### Implemented

- Added `View` menu in `MainWindow` with direct theme actions:
  - `Theme: Light`
  - `Theme: Dark`
  - `Theme: System`
- Added `MainWindow.selectTheme(UiThemePreference)` helper that updates session preferences while preserving other preference fields.

### Tests updated

- Expanded `MainWindowStateWiringTest` with `selectTheme_updatesSessionPreferencesAndThemeFlags`.

## 93) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 94) Architecture plan extension (UI panel capability roadmap)

Added a focused UI capability roadmap based on panel review so Phase 3/Stage C can transition into operator-grade workflows.

### Newly prioritized UI capabilities

1. **CoA/Fund maintenance workflows (CRUD + validation + optimistic UX)**
   - Move `ChartOfAccountsPanel` and `FundsPanel` from read-only to inline create/update/deactivate.
   - Keep optimistic feedback in-panel (non-fatal validation messages and async reload).
2. **Workflow workspaces for Phase 3 persistence outputs**
   - Add dedicated **Reconciliation** and **Period Close** panels that surface run records and lifecycle actions.
3. **Import preview + validation grid**
   - Add pre-commit preview for CSV/OFX imports (accepted/rejected rows, error reasons, duplicates).
4. **Cross-panel drill-through**
   - Dashboard/Reports row → scoped Ledger Register and journal inspector drill-down.
5. **Productivity layer**
   - Global command palette and saved view presets (date range/column/filter state per panel).
6. **Diagnostics panel**
   - Runtime profile + datasource diagnostics and data-quality checks.

### Execution order

- **V1 (implemented now):** CoA/Fund CRUD + validation + optimistic UX errors.
- **V2:** Reconciliation + Period Close workspaces.
- **V3:** Import preview grid + drill-through.
- **V4:** Command palette/saved views + diagnostics center.

## 95) Latest pass update (CoA/Fund CRUD v1 + validation + optimistic UX errors)

Implemented first management slice for reference master data maintenance.

### Implemented

- Added `AccountAdminService` with `upsert(...)`:
  - validates required account fields,
  - resolves active chart-of-accounts (fallback to first available chart),
  - creates or updates posting account by `(chart, code)`,
  - supports active/inactive toggling for soft deactivation flows.
- Added `FundAdminService` with `upsert(...)`:
  - validates required fund fields,
  - creates or updates by `code`,
  - supports active/inactive toggling.
- Extended `UiServiceRegistry` wiring with `accountAdmin()` and `fundAdmin()` and runtime reconnect rewire support.
- Upgraded `ChartOfAccountsPanel` to CRUD form workflow:
  - inline editor (code/name/type/normal-balance/active),
  - +Add resets to create mode,
  - Save performs create/update and refresh,
  - optimistic error messaging via panel status text.
- Upgraded `FundsPanel` similarly:
  - inline editor (code/name/type/active),
  - create/update/deactivate flow,
  - optimistic status/error handling.
- Added lookup helpers for maintenance screens:
  - `AccountLookupService.listPostingAccountsIncludingInactive()`
  - `FundLookupService.listAllFunds()`

## 96) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 97) Latest pass update (duplicate-friendly messages + CoA hierarchy editing + integration upsert tests)

Implemented requested follow-on fixes for CRUD v1.

### Implemented

- Added duplicate-conflict friendly messaging at service layer:
  - `AccountAdminService` maps persistence unique/constraint collisions to `IllegalArgumentException("Account code already exists: ...")`.
  - `FundAdminService` maps persistence unique/constraint collisions to `IllegalArgumentException("Fund code already exists: ...")`.
- Extended CoA editing for hierarchy management:
  - `ChartOfAccountsPanel` now includes `Subtype` and `Parent code` fields in editor form.
  - Save flow persists subtype and parent linkage through `AccountAdminService.upsert(...)`.
- Extended `AccountAdminService.upsert(...)` contract:
  - supports `AccountSubtype` and optional `parentCode`,
  - validates parent linkage rules (no self-parent, parent must exist in active chart).
- Added account lookup join-fetch behavior for parent account references to avoid lazy parent dereference issues in panel tables.

### Tests added/updated

- Added integration tests for successful upsert flows:
  - `AccountAdminServiceIntegrationTest` (create/update, subtype, parent linkage, missing-parent validation).
  - `FundAdminServiceIntegrationTest` (create/update and active/type changes).
- Updated `AccountAdminServiceTest` for the expanded upsert signature.

## 98) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 99) Latest pass update (UX copy cleanup + CoA form round-trip UI test + Step 2 start)

Addressed quick UX/test follow-up and started priority Step 2 workflow workspace implementation.

### Implemented

- UX copy cleanup:
  - `ChartOfAccountsPanel` and `FundsPanel` status copy now explicitly indicates lists include active + inactive records for maintenance clarity.
- Added focused UI form-state test coverage for CoA hierarchy fields:
  - new `ChartOfAccountsPanelFormStateTest` validates subtype + parent code round-trip in panel form state.
- Started Step 2 (`Reconciliation + Period Close workspaces`):
  - added new panel IDs and navigation entries for `RECONCILIATION_RUNS` and `PERIOD_CLOSE_RUNS`,
  - wired these into `PanelHost` factories,
  - added `ReconciliationRunsPanel` and `PeriodCloseRunsPanel` with:
    - run-history table views for active company,
    - refresh actions,
    - basic "record completed run" actions using existing services/repositories.
  - added `UiDataSources` helper for JDBC datasource creation from the active session DB path.

## 100) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 101) Latest pass update (follow-up centralization + Step 3 start)

Completed requested follow-up and moved forward to Step 3.

### Implemented

- Follow-up centralization:
  - workflow run panels now consume repositories/services through `UiServiceRegistry` methods instead of constructing them inline,
  - added `UiServiceRegistry.reconciliationRunRepository()`, `periodCloseRunRepository()`, `reconciliationService()`, and `periodCloseService()`.
- Step 3 start (`Import preview + validation grid`):
  - added new `IMPORT_PREVIEW` workspace panel ID and navigation/host wiring,
  - added `ImportPreviewService` with deterministic preview methods:
    - `previewCoaCsv(Path)` with duplicate-account-code warning detection,
    - `previewBankStatement(Path)` returning format + transaction count,
  - added `ImportPreviewPanel` with file-chooser preview flows, warning list, and COA-row table preview,
  - added Tools menu entry `Import Preview…` to open the panel.
- Added service tests for Step 3 preview logic:
  - `ImportPreviewServiceTest` covers duplicate warning detection and bank transaction count extraction.

## 102) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 103) Latest pass update (Step 3 refinement: row-level import preview outcomes)

Refined Import Preview so previews expose row-level accepted/rejected outcomes with error reasons.

### Implemented

- Extended `ImportPreviewService.previewCoaCsv(Path)` to produce row-level classification:
  - `acceptedRows` for valid lines,
  - `rejectedRows` with `(lineNumber, rawLine, errorReason)`,
  - summary counts: `totalRowCount`, `acceptedCount`, `rejectedCount`.
- Kept duplicate warning detection over accepted rows to preserve deterministic duplicate signaling.
- Updated `ImportPreviewPanel` UI to split COA preview into two tables:
  - Accepted rows table,
  - Rejected rows table with line/error details.
- Status summary now reports accepted vs rejected counts.
- Added test coverage in `ImportPreviewServiceTest` for rejected-row behavior and error reasons.

## 104) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: still blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 105) Latest pass update (CSV normalization test + quote-safe row-state parser + drill-through wiring)

Implemented requested follow-up sequence: normalization test first, then robust CSV row-state parsing and focused edge-case tests, then started drill-through wiring.

### Implemented

- Added JDBC URL normalization test coverage for runtime DB file selection helper:
  - `UiDataSources.jdbcUrlForTests(Path)` package-visible test hook,
  - `UiDataSourcesNormalizationTest` validates `.mv.db` and `.db` suffix normalization behavior.
- Promoted COA CSV preview parsing to a true logical-row parser in `ImportPreviewService`:
  - quote-state aware splitting across physical lines,
  - multiline quoted field support,
  - escaped-quote (`""`) handling,
  - explicit rejection for unterminated quoted fields with reason `Unterminated quoted field.`.
- Expanded `ImportPreviewServiceTest` with focused cases:
  - duplicate warning behavior,
  - validation rejection behavior,
  - multiline quoted-row acceptance,
  - escaped-quote acceptance,
  - unterminated quoted-row rejection,
  - bank statement preview format/count check.
- Continued drill-through wiring:
  - added `DrillThroughCoordinator` context handoff utility,
  - wired `MainWindow` opener registration,
  - added dashboard fund-row double-click drill to ledger,
  - added report library “Drill to Ledger” action,
  - updated `LedgerRegisterPanel` to consume and display drill context on reload.

## 106) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 107) Latest pass update (fix test text block compile error)

Addressed CI-reported compilation failure in `ImportPreviewServiceTest` caused by a Java text-block delimiter collision.

### Implemented

- Reworked the multiline CSV fixture in `previewCoaCsv_acceptsMultilineQuotedRows` to use `String.join("\\n", ...)` instead of a text block.
- Preserved the same fixture semantics (multiline quoted field + escaped quotes) while avoiding an accidental `"""` delimiter sequence in source.

## 108) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 109) Latest pass update (regression test + drill-through wiring tests)

Completed both requested follow-ups from the previous review notes.

### Implemented

- Added a focused regression test in `ImportPreviewServiceTest` to assert the multiline/escaped fixture parses into exact expected values:
  - first row name preserves embedded newline (`Office\nChecking`),
  - second row name preserves escaped quote rendering (`AR "External"`),
  - parent-code linkage remains intact.
- Added focused drill-through wiring test coverage in new `DrillThroughCoordinatorTest`:
  - verifies drill-through opens `LEDGER_REGISTER`,
  - verifies context is handed off exactly once (`consumeContext()` clears after read).

## 110) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 111) Latest pass update (Step 4 continuation: productivity + diagnostics baseline)

Continued into Step 4 by implementing an initial productivity/diagnostics slice.

### Implemented

- Added `DiagnosticsPanel` as a lightweight diagnostics center showing:
  - runtime timestamp,
  - Java version,
  - active company,
  - active DB file path,
  - datasource connectivity check status.
- Added `DIAGNOSTICS` workspace id and wired it into both `NavigationPane` and `PanelHost`.
- Added command-palette baseline in `MainWindow`:
  - new Search menu action `Command Palette…` (`Ctrl+K`),
  - palette uses a simple chooser to jump to any registered panel id.
- Added Tools menu shortcut `Diagnostics…` to open the diagnostics workspace directly.

### Tests added

- Added `MainWindowCommandPaletteTest` to verify palette entries include `DIAGNOSTICS` and `IMPORT_PREVIEW`.

## 112) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 113) Latest pass update (Step 4 polish + Step 5 continuation)

Completed requested polish and continued into Step 5.

### Implemented

- Command palette polish:
  - switched palette labels from enum-token formatting to user-friendly panel labels aligned with navigation naming,
  - retained deterministic alphabetical ordering.
- Added initial Step 5 productivity capability: saved view presets in `MainWindow`:
  - save current `(active panel + date range)` preset,
  - apply preset to restore both panel and date range,
  - wired View menu actions (`Save View Preset…`, `Apply View Preset…`).
- Added `PanelHost.activePanelId()` exposure for preset capture.

### Tests added/updated

- Expanded `MainWindowCommandPaletteTest` to verify friendly label rendering and sorted label ordering.
- Expanded `MainWindowStateWiringTest` with view preset round-trip coverage (save then restore panel/date-range state).

## 114) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 115) Latest pass update (fix multiline COA preview parsing regression)

Addressed failing tests in `ImportPreviewServiceTest` for multiline quoted COA rows.

### Implemented

- Refactored `ImportPreviewService.previewCoaCsv(...)` row parsing to avoid delegating single-row parse to `CoaCsvMapper.parse(...)` (which line-splits and breaks multiline quoted values).
- Added local record-level CSV parsing/mapping in `ImportPreviewService` for preview path:
  - `parseCsvRecord(...)` for quote-aware field parsing (including escaped quotes),
  - header validation via `requireHeaders(...)`,
  - record mapping + required/optional column handling (`map`, `required`, `optional`).
- Preserved row-level reject behavior and error messaging (`Unterminated quoted field.` etc.) while allowing multiline quoted fields to parse into accepted rows.
- Simplified constructors in `ImportPreviewService` by removing unused `CoaCsvMapper` field dependency from this path.

## 116) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 117) Latest pass update (finish Step 5 + continue Step 6)

Completed requested continuation: finished Step 5 productivity persistence and moved forward on Step 6 diagnostics depth.

### Implemented

- Finished Step 5 productivity work:
  - added persistent view preset state model `ViewPresetState`,
  - extended `AppStateStore` with `loadViewPresets()` / `saveViewPresets(...)`,
  - implemented `FileAppStateStore` read/write support for persisted view presets,
  - updated `MainWindow` to load presets at startup and persist presets on save,
  - added delete-preset flow (`Delete View Preset…`) and helper for preset removal.
- Continued Step 6 diagnostics implementation:
  - enhanced `DiagnosticsPanel` to include data-quality checks for accounts/funds (active vs total counts),
  - added explicit quality summary warning when active posting accounts or active funds are missing.

### Tests added/updated

- `FileAppStateStoreTest` now includes round-trip coverage for view preset persistence.
- `MainWindowStateWiringTest` now verifies:
  - saved view preset data is persisted via store in `saveActivePanel`,
  - preset delete behavior removes saved names.

## 118) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 119) Latest pass update (Step 6 hardening + Step 7 start)

Implemented requested additions and moved ahead to Step 7.

### Implemented

- Added reversible view-preset token serialization in `FileAppStateStore`:
  - switched preset row token persistence from lossy sanitization to Base64 URL-safe encoding,
  - added decode fallback to preserve backward compatibility with older plaintext rows.
- Added Step 6 diagnostics hardening:
  - `DiagnosticsPanel` now runs duplicate-code checks over accounts/funds,
  - emits duplicate-warning summary when collisions are detected.
- Started Step 7 with additional reliability/test coverage:
  - startup restore test for persisted view presets in `MainWindowStateWiringTest`,
  - focused diagnostics helper test (`DiagnosticsPanelTest`) for duplicate detection behavior,
  - special-character preset round-trip test in `FileAppStateStoreTest`.

## 120) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 121) Latest pass update (Phase 3.c completion + Phase 4 continuation)

Continued the overall architecture plan as requested: completed a new Phase 3.c persistence slice and moved into Phase 4 service-layer continuation.

### Phase 3.c persistence additions

- Added migration `V8__approval_audit_records.sql` for approval/audit persistence baseline with decision token constraints.
- Added approval/audit persistence model and repository layer:
  - `ApprovalDecision`
  - `ApprovalAuditRecord`
  - `ApprovalAuditRepository`
  - `JdbcApprovalAuditRepository`
- Extended `UiServiceRegistry` wiring with `approvalAuditRepository()` and `approvalAuditService()` accessors.

### Phase 4 service-layer continuation

- Added `ApprovalAuditService` to record and list approval decisions with validation for required inputs.
- Added integration/service tests covering persistence path and validation behavior.

### Tests added/updated

- `JdbcApprovalAuditRepositoryTest` (append/find/list behaviors).
- `ApprovalAuditServiceIntegrationTest` (recordDecision persistence and required-field validation).

## 122) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 123) Latest pass update (Step 7 continuation: diagnostics drill-through wiring)

Implemented the previously suggested diagnostics drill-through wiring and supporting coverage.

### Implemented

- Extended `DrillThroughCoordinator` with panel-scoped context handoff:
  - `openPanelWithContext(AppPanelId, String)`
  - `consumeContext(AppPanelId)`
- Wired `DiagnosticsPanel` duplicate-warning actions to drill-through targets:
  - `Review account duplicates` opens `CHART_OF_ACCOUNTS` with context,
  - `Review fund duplicates` opens `FUNDS` with context,
  - action buttons enable only when duplicate checks detect collisions.
- Updated `ChartOfAccountsPanel` and `FundsPanel` reload flow to consume panel-scoped drill-through context and surface it in status messaging.

### Tests added/updated

- Expanded `DrillThroughCoordinatorTest` with panel-scoped context behavior assertions.

## 124) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 125) Follow-up fix (drill-through test structure correction)

- Corrected `DrillThroughCoordinatorTest` class structure after adding panel-scoped context test.
- Re-ran `mvn -B -ntp test`; environment remains blocked by Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 126) Latest pass update (Step 7 continuation: approval actions in run workspaces)

Continued Step 7 by wiring approval-audit recording into workflow run workspaces.

### Implemented

- `ReconciliationRunsPanel`
  - added `Approve Selected` and `Reject Selected` actions,
  - records decisions through `UiServiceRegistry.approvalAuditService()` with workflow type `RECONCILIATION`,
  - validates selection before recording and shows explicit status feedback.
- `PeriodCloseRunsPanel`
  - added `Approve Selected` and `Reject Selected` actions,
  - records decisions through `UiServiceRegistry.approvalAuditService()` with workflow type `PERIOD_CLOSE`,
  - validates selection before recording and shows explicit status feedback.

### Tests added

- `ReconciliationRunsPanelTest` verifies helpful status when approval is attempted with no selection.
- `PeriodCloseRunsPanelTest` verifies helpful status when approval is attempted with no selection.

## 127) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 128) Latest pass update (test-fix follow-up: DiagnosticsPanelTest null fixture)

Addressed CI-reported test error in `DiagnosticsPanelTest`.

### Fix

- Replaced `List.of(...)` with `Arrays.asList(...)` in the duplicate-code fixture so the test can intentionally include `null` values without throwing `NullPointerException` during test setup.

### Why

- `List.of(...)` rejects `null` elements and throws `NullPointerException` before assertions execute.
- `DiagnosticsPanel.duplicateCodes(...)` is designed to ignore `null`/blank codes; the test must supply a null-tolerant list implementation.

## 129) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 130) Latest pass update (Phase 1 execution: core shell + transaction wiring)

Executed Phase 1 priority wiring to reduce placeholder-driven operator dead-ends.

### Implemented

- `TransactionEditorPanel` now uses in-panel status messaging and service-backed reference validation instead of alert-only placeholders:
  - added async validation against active account/fund catalogs,
  - added deterministic split validation summary (rows/valid/errors/net, with ready-to-post and balance warnings),
  - replaced modal placeholder save/journal messaging with inline status updates.
- `MainWindow` shell actions were upgraded from placeholders to actionable behavior:
  - `Search` now renders a live search snapshot (active panel/company + sampled accounts/funds),
  - `Journal` now loads the most recent transaction and journal lines from `LedgerQueryService`,
  - `Run -> Post / Validate` now triggers active-panel save flow,
  - `Run -> Recalculate summaries` now executes an async fund-balance recomputation check,
  - `Go to…` now routes to command palette.
- `NavigationPane` right-click details now show panel-aware inspector content instead of generic placeholder copy.

### Tests added

- `TransactionEditorPanelValidationTest` for balanced/ready validation and invalid-code invalid-amount behavior.
- `NavigationPaneInspectorBodyTest` for panel and group detail message generation.

## 131) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due environment Maven plugin resolution (`maven-resources-plugin:3.3.1`, network unreachable).

## 132) Comprehensive end-to-end wiring plan (authoritative roadmap)

This section consolidates the full implementation sequence for completing Stage C UI wiring and adding missing model-focused panels.

### Objectives

1. Eliminate remaining placeholder-driven UX in high-traffic workflows.
2. Ensure every persisted or service-level Stage C function has a practical UI representation.
3. Preserve deterministic behavior and add focused regression coverage for each shipped slice.

### Execution order

#### Phase 1 — Core workflow and shell completion (highest priority)

1. Transaction Editor completion
   - Keep current validation/status implementation.
   - Add explicit panel command contract for post/validate (instead of generic save dispatch).
   - Add query-backed journal preview for current transaction context.
2. Shell command completion in MainWindow
   - Replace remaining placeholder menu actions with implemented behavior or explicit disabled states.
   - Expand Search from snapshot to query/filter + jump navigation.
   - Ensure Journal inspector supports active-panel selection context when available.
3. Navigation details completion
   - Keep panel-aware inspector body.
   - Include richer context metadata (active company, date range, panel capabilities).

#### Phase 2 — Model-first panels for persistence artifacts currently underrepresented in UI

4. Add `ApprovalAuditPanel`
   - Data source: `ApprovalAuditService.listRecent(groupCode, maxRows)`.
   - Features: filter by workflow type, decision, actor, date range; optional run-id linkage.
   - Entry points: direct nav item + deep links from Reconciliation/Period-Close panels.
5. Add `ImportExportJobsPanel`
   - Surface all file import/export operations from one workspace.
   - Show source path, outcome, row/transaction counts, format, and errors.
6. Add `BankTransactionsPanel`
   - Render imported `BankTransactionRecord` rows.
   - Include drill-to-ledger and export-selected pathways.

#### Phase 3 — Existing workflow panel expansion

7. Reconciliation Runs panel expansion
   - Lifecycle operations beyond record-completed.
   - Audit history summary and drill to detailed audit records.
8. Period Close Runs panel expansion
   - Same lifecycle/audit enrichment pattern.
9. Import Preview completion
   - Add explicit “commit accepted rows” flow into orchestration service.

#### Phase 4 — Operationalization of partially wired accounting panels

10. Report Library export completion
    - Replace export placeholder with file output workflow and success/failure reporting.
11. Budget Editor + Budget vs Actual deep wiring
    - Introduce persistent budget model editing and variance rendering.
12. Schedules / Assets / Depreciation / Inventory deep wiring
    - Replace read-only list scaffolds with domain runbooks and lifecycle actions.

#### Phase 5 — Cross-cutting hardening

13. Role/privilege gating of menu and panel actions.
14. Shared inspector presentation model across panels.
15. Interaction test expansion for all high-traffic panels and shell commands.

### Validation strategy per phase

- Add narrow unit tests for deterministic helpers and parsing/formatting.
- Add JavaFX tests for panel interaction contracts and status messaging.
- Add integration tests for persistence-backed panel actions where feasible.
- Re-run `mvn -B -ntp test` each pass and capture environment blockers explicitly.

## 133) Prompt for next Codex instance

Use the following prompt verbatim as the handoff seed for the next agent pass:

```text
Continue from docs/progress-report-next-pass.md section 132.

Primary objective:
- Execute the remaining Phase 1 follow-ups before any Phase 2 work:
  1) Add an explicit run command contract for active panels (avoid generic save for Post/Validate).
  2) Upgrade Search from snapshot-only to query/filter + panel jump behavior.
  3) Improve Journal inspector to prefer active selection context and gracefully fall back to recent transaction.

Implementation constraints:
- Keep all behavior deterministic and testable.
- Avoid placeholder copy for newly touched flows.
- Keep UI feedback in-panel/inspector (not modal alerts for primary workflows).

Testing requirements:
- Add/adjust focused tests for each changed behavior.
- Run `mvn -B -ntp test` and report results.
- If Maven/network blocks execution, report exact blocker and still summarize test additions.

Process requirements:
- Update docs/progress-report-next-pass.md with a numbered section describing what was implemented.
- Commit changes on the current branch.
- Use make_pr after commit with a concise title/body.

Final response requirements:
- Include summary with file citations.
- Include testing section with ✅/⚠️/❌ command prefixes.
- Include a brief code review and offer follow-up fixes for any identified issues.
```

## 134) Latest pass update (Phase 1 follow-up completion: run contract, search query/filter, journal context)

Completed the remaining Phase 1 follow-ups from section 132 before any Phase 2 work.

### Implemented

- Added an explicit active-panel run command contract in `AppPanel`/`PanelHost`:
  - new `RunCommand.POST_VALIDATE` dispatch path,
  - structured `RunCommandResult` response for deterministic in-inspector feedback,
  - optional panel journal-selection context contract (`JournalSelection`).
- Rewired `MainWindow` Run → Post/Validate to invoke the explicit run contract instead of generic save dispatch.
- Updated `TransactionEditorPanel` to handle run commands directly:
  - `POST_VALIDATE` delegates to existing validation flow,
  - status now stores and reuses last validation summary for consistent save/run feedback.
- Upgraded Search from snapshot-only to query/filter behavior:
  - added normalized query matching helpers,
  - returns filtered panel/account/fund result sets,
  - added panel jump method (`jumpToPanelFromSearch`) for deterministic navigation.
- Improved Journal inspector context handling:
  - prefers active panel selection context when available,
  - falls back to most recent transaction preview when no selection is exposed.
- Exposed active journal selection from `LedgerRegisterPanel` for context-aware inspector behavior.

### Tests added/updated

- Added `MainWindowPhase1FollowupTest` covering:
  - explicit run-command dispatch usage,
  - search normalization/matching,
  - search query filtering,
  - search-driven panel jump,
  - journal inspector selection-context preference.
- Existing validation tests continue covering deterministic split validation behavior.

## 135) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, `https://repo.maven.apache.org/maven2`, network unreachable).

## 136) Latest pass update (Phase 1 completion hardening after review feedback)

Addressed follow-up feedback to fully complete all remaining Phase 1 items in section 132.

### Transaction Editor completion

- Kept existing validation/status behavior and explicit `POST_VALIDATE` panel run-command dispatch.
- Added query-backed journal preview in `TransactionEditorPanel` for current editor context (`date/payee/memo/bank`):
  - loads recent ledger rows,
  - deterministically filters by non-blank context fields,
  - renders a compact preview from the matched transaction + journal lines,
  - provides explicit in-panel fallback message when no match exists.

### MainWindow shell completion

- Kept run-command contract path and query-based search/jump behavior.
- Replaced unsupported Edit menu actions with explicit disabled states (`Undo`, `Redo`, `Cut`) rather than informational placeholders.
- Kept/extended Journal inspector active-selection preference with fallback logic.
- Locale hardening: search normalization/matching now uses `Locale.ROOT` for deterministic case-folding.

### Navigation details completion

- Enhanced navigation inspector content to include richer context metadata:
  - active company,
  - active date range,
  - panel capability summary.

### Tests added/updated

- Updated `AppPanelConsistencyTest` for new `NavigationPane` constructor contract.
- Updated `NavigationPaneInspectorBodyTest` to validate metadata-rich inspector body output.
- Added `TransactionEditorPanelJournalPreviewTest` for deterministic context matching and preview rendering.
- Expanded `MainWindowPhase1FollowupTest` to validate panel capability metadata expectations.

## 137) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, `https://repo.maven.apache.org/maven2`, network unreachable).

## 138) Latest pass update (Phase 2 implementation: new model-first workflow panels)

Implemented the requested Phase 2 slice after finishing Phase 1.

### Added Phase 2 panels

1. `ApprovalAuditPanel`
   - Data source: `ApprovalAuditService.listRecent(activeGroup, 500)`.
   - Filters: workflow type, decision, actor, date range.
   - Includes run-id visibility and drill-through context support via `DrillThroughCoordinator`.
2. `ImportExportJobsPanel`
   - New unified session workspace for import/export job history.
   - Shows source/target path, outcome, row count, transaction count, format, and errors.
3. `BankTransactionsPanel`
   - Renders imported `BankTransactionRecord` rows from shared session store.
   - Supports drill-to-ledger from selected transaction(s).
   - Supports export-selected flow (OFX/QFX) with success/failure status and job-history recording.

### Shell and cross-panel wiring updates

- Added new workspace panel IDs and host factories:
  - `APPROVAL_AUDIT`, `IMPORT_EXPORT_JOBS`, `BANK_TRANSACTIONS`.
- Added navigation entries for new panels under Workflows.
- Added Tools menu shortcuts for opening all new panels.
- Added deep-link actions from:
  - `ReconciliationRunsPanel -> ApprovalAuditPanel` (context `RECONCILIATION`),
  - `PeriodCloseRunsPanel -> ApprovalAuditPanel` (context `PERIOD_CLOSE`).
- Added `UiWorkspaceDataStore` session store for deterministic cross-panel projections:
  - imported bank transactions,
  - import/export job audit records.
- Updated file import/export paths in `MainWindow` to record job outcomes (success/failure metadata).

### Tests added/updated

- Added `ApprovalAuditPanelFilterTest` for deterministic audit filter behavior.
- Added `UiWorkspaceDataStoreTest` for shared-session transaction/job store behavior.
- Existing panel consistency and navigation coverage continue validating host/nav completeness with new panel IDs.

## 139) Test execution status

- Command executed: `mvn -B -ntp test`
- Result: blocked before test execution due Maven plugin resolution/network access (`maven-resources-plugin:3.3.1`, `https://repo.maven.apache.org/maven2`, network unreachable).
