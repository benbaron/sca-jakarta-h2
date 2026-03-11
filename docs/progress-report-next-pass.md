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
