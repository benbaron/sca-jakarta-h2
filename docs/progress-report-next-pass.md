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
