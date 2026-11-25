# User-manual procedure tests

The [Word manual](user-manual.docx) is the procedure source. This map records
which executable tests check its outcomes; it is not a second editable manual.
P28-C3 adds scenarios named for the manual's tasks and worked examples. Existing
tests remain the detailed coverage for individual features.

Run the new scenarios without a desktop display:

```bash
mvn '-Dtest=UserManual*Test' test
```

Run the complete regression suite, including the existing coverage below:

```bash
mvn clean verify
```

The CI workflow separately executes the actual JavaFX tests on Linux and Windows.
Headless skips do not count as executed desktop interaction coverage.

## New scenarios

| Test class | Procedure outcomes checked |
|---|---|
| [UserManualJournalRulesTest](../src/test/java/org/nonprofitbookkeeping/service/UserManualJournalRulesTest.java) | The manual's income, expense and bank-transfer debit/credit examples; whole cents; zero, negative, two-sided and fractional-cent input; four allocations; each transaction balances independently. Pure unit tests, no database. |
| [UserManualBookkeepingProcedureTest](../src/test/java/org/nonprofitbookkeeping/service/UserManualBookkeepingProcedureTest.java) | Create company, preview/import/activate chart, create Funds and two banks, enter the worked Journal examples, inspect bank journals and reports; reviewed bank versus book opening, manual rows, clearing, outstanding checks, completion and inherited opening; independent two-bank transfer; correct/retry rejected Journal save; informational dates do not clear; duplicate-code charts retain exact bank/history links; preserve legacy import receipts; date/Fund/company report scope. Persistence integration tests. |
| [UserManualImportProcedureTest](../src/test/java/org/nonprofitbookkeeping/service/UserManualImportProcedureTest.java) | Preview without writes, map all four splits, edit and omit a proposal, commit literal source dates and information, inspect Journal, reject duplicate replay; reject offsetting unbalanced candidates atomically, correct retained proposals and retry. Original CSV bytes remain unchanged. Persistence integration tests. |
| [UserManualHandoverProcedureTest](../src/test/java/org/nonprofitbookkeeping/service/UserManualHandoverProcedureTest.java) | Back up live books, verify SHA/counts, reject overwrite, add later live activity, restore a separate validated copy, inspect backup-time company/Journal/bank/report facts, preserve live books, then explicitly switch. File/recovery integration test. |

Fixtures use only synthetic data. They call the real application services and use
temporary H2 files because the supported Jpa seam accepts file paths and the
handover procedure must test physical backup/restart. They never open a user's
database. No mock ledger, copied business calculations or test-only posting path.

## Chapter and procedure map

`U` means pure unit/policy logic, `I` real persistence/service/file integration,
and `FX` actual desktop interactions requiring a display. Class names below are
under `src/test/java/org/nonprofitbookkeeping` and are selectable with Maven's
`-Dtest` option. The map identifies relevant automated checks, not a percentage
of prose, code lines or human procedure completion.

| Word chapter | Procedures and relevant tests | Kind |
|---|---|---|
| Start here | Sign-in/company-role context: UiSessionStateAuthTest, AuthorizationGuardTest, DatabaseCompanyRoundTripIntegrationTest. Navigation, save routing, resizing: NavigationPaneInteractionTest, ProductionWorkspaceCommandRoutingTest, PanelViewportFxTest, JournalSaveFeedbackTest. | U/I/FX |
| Understand the records | Debit/credit worked examples and cents: UserManualJournalRulesTest. Exact company/chart/account/Fund/bank identities: UserManualBookkeepingProcedureTest. | U/I |
| Create, open and switch databases | DatabaseSessionControllerTest, ConnectedDatabaseSessionAuthorityIntegrationTest, DatabaseMigrationRecoveryTest, DatabaseAdministrationAuthorizationIntegrationTest; UI commands: DatabaseTransferMenuInstallerTest, DatabaseTransferPanelTest. | I/FX |
| Back up and restore a database | UserManualHandoverProcedureTest; overwrite/corruption/active-path guards: DatabaseTransferServiceTest; live snapshots and restored integrity: OnlineBackupIntegrityTest, NativeDatabaseRecoveryTest. | I |
| Set up a company | [NativeChartServiceTest](../src/test/java/org/nonprofitbookkeeping/service/NativeChartServiceTest.java) and [NativeChartEditorPaneTest](../src/test/java/org/nonprofitbookkeeping/ui/NativeChartEditorPaneTest.java): native empty chart, full draft accounts, metadata/retirement, cross-company cloning with zero openings, definition adjuncts, permissions/history, drift/rollback, global Save/dirty/cancel/overflow and compatibility export. UserManualBookkeepingProcedureTest and CompanyAdminServiceTest; existing JSON import/mapping/review and CoaCsvImportServiceTest retain file-compatibility coverage; CompanyChartAssignmentServiceTest/ChartOfAccountsSelectionTest verify explicit activation. | I/FX |
| Manage a company and its master records | CompanyAdminServiceTest (stable ID, profile, current/last-active protection); AccountAdminServiceTest, FundAdminServiceLifecycleTest, BudgetCategoryAdminServiceTest, CounterpartyAdminServiceTest, MerchantAdminServiceTest; CompanyUiPreferencesServiceTest (report defaults). | I |
| Manage users, roles and preferences | AuthorizationPolicyTest (fixed role capabilities); UserAdminServiceLifecycleTest, UserAdminAuthorizationIntegrationTest, SecurityAdminAuthorizationIntegrationTest; CompanyUiPreferencesServiceTest; ActivePeriodContextTest and WorkflowPeriodDefaultsTest (default dates versus existing drafts). | U/I/FX |
| Enter and correct journal transactions | UserManualJournalRulesTest and UserManualBookkeepingProcedureTest; TransactionEntryServiceTest, TransactionAuthorizationIntegrationTest, TransactionEntryCloseRangeTest; TransactionCorrectionClosedPeriodTest, TransactionCorrectionReconciliationProtectionTest; JournalSaveFeedbackTest and JournalPaymentReferencesTest. | U/I/FX |
| Import files — SCLX | SclxVersionTest, SclxImportPreviewServiceTest, SclxImportCommitServiceTest, SclxImportCommitAuthorizationTest, SclxAllChartsTest, ImportPreviewPanelSclxTest (review/choices/commit). | U/I/FX |
| Import files — bank OFX/QFX and CSV | BankStatementParserTest, BankStatementImportContractMatrixTest, BankStatementReviewServiceTest, BankCsvReviewServiceTest, NormalizedBankCsvReviewServiceTest, BankImportAuthorizationIntegrationTest; ImportWorkflowPanelTest. | U/I/FX |
| Import files — Exchequer review | UserManualImportProcedureTest; ExchequerReportCsvImporterTest, ExchequerCandidateValidationTest, ExchequerImportServiceTest; ExchequerImportReviewPaneTest (edit/delete/resume/commit). | U/I/FX |
| Export files and choose the right format | SclxAllChartsTest and SclxFileExportServiceTest (selected-company/all-chart scope); ChartOfAccountsJsonServiceTest; BankStatementCsvExportServiceTest; UserManualHandoverProcedureTest contrasts complete database restoration with business-data export. | I |
| Banking and reconciliation — configure/start/manual entry | UserManualBookkeepingProcedureTest; BankConfigurationServiceTest, BankingWorkspaceServiceTest, BankingWorkspacePanelTest (inline entry, save/cancel, draft/account/session isolation). | I/FX |
| Banking and reconciliation — match/complete/continue | UserManualBookkeepingProcedureTest; BankingWorkspaceServiceTest (whole instruments/Fund splits, unmatched offsetting rows, source errors, removal); BankReconciliationMutationIntegrityTest (completed immutability); BankingWorkspaceSclxTest; CheckExceptionServiceTest and CheckExceptionsPaneTest (check review/void/reissue). | I/FX |
| Run, interpret and export reports | UserManualBookkeepingProcedureTest (cutoff/range/Fund/company); FinancialReportServiceIntegrationTest, ReportExecutionServiceIntegrationTest, FinancialReportExportAdapterTest, ReportLibraryPanelExportIntegrationTest; CoreAccountingPacketServiceTest and OperationalAccountingPacketTest; ReportLibraryPeriodSynchronizationIntegrationTest (custom dates). | I/FX |
| Opening conversion and period close | OpeningConversionServiceTest (acceptance without posting); CloseReviewEvidenceServiceTest, AccountingReadinessTest, AtomicPeriodCloseTest (stale review, reviewed close, restart/retry/reopen); PeriodCloseCoverageTest; OpeningConversionPanelTest, CloseReviewEvidencePaneTest, PeriodCloseStatePanelTest. | I/FX |
| Events, budgets and Fund transfers | EventAccountingQueryServiceIntegrationTest; BudgetPlanServiceTest, BudgetPlanLifecycleTest, BudgetReversalActualsTest; FundTransferServiceTest (balanced allocation versus physical bank movement, reversal/rollback/history). | I |
| Open items and other operating functions — tracked balances/recognition | SupplementalIntegrityServiceTest, SupplementalOpenItemQueryServiceTest, SupplementalOpenItemReportIntegrationTest; SupplementalRecognitionServiceTest and RecognitionAssistantPanelTest. | I/FX |
| Open items and other operating functions — NMR/settlement | NmrItemDetailTest, NmrFinancialAcceptanceTest, NmrSettlementTest, MerchantSettlementAssistantServiceTest; MerchantSettlementAssistantPanelTest. | I/FX |
| Open items and other operating functions — assets/inventory/custody | FixedAssetAcquisitionTest, FixedAssetLifecycleAccountingTest, FixedAssetAtomicDepreciationTest, InventoryMovementAccountingTest, InventoryServiceTest, PropertyCustodySclxTest; AssetAcquisitionPanelTest, InventoryEventAttributionPanelTest, PropertyCustodyPanelTest. | I/FX |
| Administrative reference and repair tools | NmrPolicyServiceTest (versioned reference terms without accounting writes); CompanyOwnershipServiceTest (evidence, explicit assignment, audit, foreign ownership rejection). | I |
| Troubleshooting | UserManualBookkeepingProcedureTest correct/retry and chart identity; UserManualImportProcedureTest retained corrected proposals; JournalSaveFeedbackTest, SclxExportFailureDialogTest, AtomicPeriodCloseTest stale content, DatabaseMigrationRecoveryTest and DatabaseTransferServiceTest guarded recovery. | I/FX |
| Period-end and handover checklists | UserManualHandoverProcedureTest; AtomicPeriodCloseTest; OperationalAccountingPacketTest; UserAdminServiceLifecycleTest. Individual bank/open-item/register/readiness tasks use the respective tests above. | I |

## Human acceptance that remains

Automated tests cannot determine the organization's actual fiscal dates, chosen
database, policy/evidence authenticity, source-file custody, external approval or
report submission. Nor does a service test prove that a person can find a button
or read a downloaded file on their installed desktop. Follow the Word procedures
for those checks; the existing FX tests check application interaction and layout,
not the external facts. Word formatting/comments remain outside these tests.

## Chart-import regression found by the procedures

Creating a second chart with account code 1000 previously failed at commit:
the stored COA_JSON ACCOUNT receipt used a company-wide external key of 1000.
P28-C3 scopes new account-receipt namespaces by the target chart's stable portable
UUID, keeping the source code as the external ID. Existing unscoped receipts
remain untouched. The new chart
stays a draft until explicitly activated; no Journal history or bank link moves.
The new procedure tests reproduce the original failure and verify both fresh
and legacy-receipt paths. Same-chart idempotency/rollback tests remain in the
adjacent chart-import suites.


## C15 import review UI coverage

ImportReferenceSuggestionTest adds exact/ambiguous/stale/cleared draft-state
coverage. ExchequerImportReviewPaneTest now checks same-field grey hints, automatic
five-field matching, native dropdowns, explicit optional clearing, whole-record
Apply/Cancel, contextual Save, Updated/Ready/grey row rendering, multi-delete and
refresh preserving both applied and unfinished edits. The Word import procedure
reflects those actions; it remains the manual source. New UI cases require a
real display and are exercised by the existing CI Import workflow gate.
