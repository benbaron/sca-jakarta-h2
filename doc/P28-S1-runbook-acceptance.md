# P28-S1 — Complete runbook acceptance matrix

## Purpose and boundary

P28-S1 closes the adopted A01–A14 runbook matrix with reproducible evidence on current production authorities. It creates no second ledger, test-only production route, accounting default, sample production data or internal approval system. Automated evidence uses disposable H2 companies and synthetic facts. Windows scaling, ordinary owner workflows, and external evidence/signature procedures remain explicit manual or external acceptance.

The selected branch starts from main `c79d9f50f692d171f8f636b399bf67c2de297f42`, which contains P27-C1 accepted head `2022289b16345bdd1d24bea4bbb4ac5fdf8c5986`. P27's SCLX and native recovery gates are prerequisites rather than substitutes for the business-scenario matrix.

## Executable evidence matrix

| Case | Required result | Principal executable evidence |
|---|---|---|
| A01 | Reversal/replacement actuals honor transaction dates and fiscal cutoffs | `BudgetReversalActualsTest`, `FinancialReportServiceIntegrationTest` |
| A02 | All six open-item kinds retain historical balances across partial settlement, reversal and replacement | `SupplementalOpenItemQueryServiceTest`, `SupplementalOpenItemReportIntegrationTest` |
| A03 | Missing, partial, excess and concurrent allocations reject atomically; legacy gaps stay visible | `SupplementalIntegrityServiceTest`, `AccountingReadinessTest` |
| A04 | Lifecycle identities, corrections and dated results survive SCLX retry/restart | `SclxLifecycleRoundTripTest`, `IntegratedPortabilityTest` |
| A05 | Generic quarter packet reconciles and missing sources/control differences remain blockers | `CoreAccountingPacketServiceTest`, `OperationalAccountingPacketTest`, `AccountingReadinessTest`, `CorePacketExportActionTest` |
| A06 | Event sale/cost attribution and reversal retain quantity, GL and event results | `InventoryMovementAccountingTest`, `EventAccountingQueryServiceIntegrationTest` |
| A07 | Internal fund reallocation remains distinct, balanced, protected and portable | `FundTransferServiceTest`, `FundTransferSclxTest`, `SclxFundTransfersTest` |
| A08 | Category, payee and merchant masters persist IDs/history and enforce company/role boundaries | `BudgetCategoryAdminServiceTest`, `CounterpartyAdminServiceTest`, `MerchantAdminServiceTest`, related authorization integration tests |
| A09 | Check delivery/cancel/reissue preserves searchable references without duplicate expense and respects reconciliation | `PaymentReferenceServiceTest`, `CheckExceptionServiceTest`, `TransactionCorrectionReconciliationProtectionTest`, `BankReconciliationMutationIntegrityTest` |
| A10 | Entered NMR, merchant withholding/discharge and functional classifications tie out without policy defaults or duplicate entries | `NmrFinancialAcceptanceTest`, `NmrItemDetailTest`, `MerchantSettlementTest`, `FunctionalExpenseTest` |
| A11 | Acquisition, custody, depreciation/disposal and expensed supplies retain one canonical accounting history | `FixedAssetAcquisitionTest`, `FixedAssetLifecycleAccountingTest`, `InventoryServiceTest`, `PropertyCustodySclxTest` |
| A12 | Scoped budgets, twelve-month recognition and moving-weighted-average inventory reconcile and retry safely | `BudgetPlanServiceTest`, `BudgetPlanLifecycleTest`, `SupplementalRecognitionServiceTest`, `InventoryMovementAccountingTest`, `InventoryServiceTest` |
| A13 | Missing external evidence remains visible through readiness/close/reopen; attestations are factual | `CloseReviewEvidenceServiceTest`, `AccountingReadinessTest`, `AtomicPeriodCloseTest`, `SclxPeriodCloseExportIntegrationTest` |
| A14 | Named events can be created, found, tagged independently from funds, reopened and reported | `ActivityAdminServiceTest`, `EventAccountingQueryServiceIntegrationTest`, `EventDiscoveryPanelTest`, `JournalFundEventTaggingTest` |

The P25/P26 scenario-level coverage remains enumerated in [the unit coverage ledger](P25-P26-unit-coverage.md). P28 runs those tests together so shared database/session state cannot hide isolation failures. `IntegratedPortabilityTest` and `NativeDatabaseRecoveryTest` then retain the complete supported graph across remapped SCLX and whole-database recovery.

## Required combined gates

1. Compile current main before acceptance edits.
2. Run the service/report matrix named above in one Maven invocation, including authorization, two-company isolation, non-calendar fiscal dates, cutoff/reversal, closed-period, finalized-reconciliation, retry/restart, rollback, SCLX and recovery tests.
3. Run the production JavaFX acceptance routes under Xvfb, including event discovery/tagging, packet/readiness, check/reconciliation, P25 and P26 Save workspaces, scaling/layout and live role gating.
4. Run `mvn clean verify` after final source/test/document bytes are frozen.
5. Publish a draft and require exact-final-head clean verification, repeated tests and production JavaFX route compliance.

No automated X display is recorded as Windows desktop acceptance.

## Owner and external acceptance

On the built application at ordinary laptop size and the Windows scaling setting used in production:

1. Create a disposable company and a second company. Use a non-January fiscal year, two funds, two events with similar names but distinct codes, and an inactive historical dimension.
2. Create/find an event, enter an independently funded and event-tagged receipt/expense, save, reopen, restart and filter the Event Accounting result by name.
3. Exercise a WARN_ONLY reconciliation: verify an uncleared manual match changes no data and instructs **Mark Cleared**; mark it cleared and match successfully.
4. Export the quarter-end generic accounting packet and confirm missing sources/control differences remain visible rather than receiving a complete-ready claim.
5. Confirm external receipts, signatures, custody and approval procedures follow the organization's current process. The application records references and factual attestations; it does not claim internal attachment custody or independent approval enforcement.

Record the built commit, Windows version, display size/scaling, effective role/company, saved-layout state, result, and any accepted exception. P28-S1 cannot infer these facts from CI.

## Acceptance

Every A01–A14 row must have passing executable evidence or a clearly identified owner/external procedure. All P25/P26 scenarios must remain covered by behavioral unit/integration tests. No critical/high defect may remain open. Any significant exception must be itemized with its owner and disposition. P28-S2 remains blocked until this slice is reviewed and merged.

## Local execution record

On 2026-10-31 America/Denver, current-main compilation passed. The combined service/report matrix ran 218 tests with zero failures, errors or skips in 03:13. It included the cited A01–A14 cases, every P25/P26 scenario family, company/role isolation, cutoff/reversal and fiscal cases, retry/restart/rollback, SCLX remapping and native recovery. Log: `/tmp/p28-s1-service.log`.

The production JavaFX acceptance selector ran on an actual virtual X display and passed 71 tests with zero failures, errors or skips in 02:07. It included event discovery/tagging, packet/readiness, check/reconciliation, all P25/P26 Save workspaces, command routing, geometry and scaling tests. Log: `/tmp/p28-s1-fx.log`. This is display-backed automation, not the owner Windows result required above.

Final `mvn clean verify` passed 1,109 tests with zero failures/errors and 77 expected headless display skips in 05:44. Log: `/tmp/p28-s1-clean.log`.

No critical/high or other product defect was found in these combined gates. The initial JavaFX launcher could not use the container's shared Unix X socket; the same cached Xvfb binary succeeded on a loopback-only listener and the complete selector then passed. This is an execution-environment correction, not a product workaround.
