# P23-S1 — Baseline, evidence, and acceptance contract

Selected by the owner on 2026-09-26. Base: `849dc4e8f652d47aff05c124790f78d6dc7f93dc` (merged archive PR #350). Branch: `codex/P23-S1-adopt-correction-plan`.

This is a documentation and requirements slice. It establishes reproducible case specifications; execution and correction of G1 begin in P23-S2. No application code, schema, imported accounting data, or production seed data is changed. A case below is **specified**, not passed, unless execution evidence explicitly says otherwise.

## Authority and scope

The owner adopted [the correction program](P23-P28-runbook-correction-program.md). [PLAN.md](PLAN.md) selects exactly one slice. The archived proposal remains a historical snapshot. Current `Activity`, `Fund`, `Txn`, and `TxnSplit` identities are retained. External evidence references and factual attestations are the adopted G13 baseline; attachment storage and approval queues remain conditional, unselected P27-S5 scope.

Implementation owner for each finding is the implementor of the listed slice. Acceptance owner is Ben Baron; accounting-policy decisions require the responsible exchequer/financial authority identified by him. No third party has been contacted or presumed to have approved a policy.

## Input inventory

| Input | Observed evidence | Disposition / next owner |
|---|---|---|
| Source repository | Clean checkout of merged `main` at 849dc4e; root AGENTS.md is the only repository AGENTS.md found | Implementation baseline; refresh before each slice |
| Archived plan | `doc/archive/SCA_2026_Deficiency_Correction_Action_Plan.md`, merged via #350 | Unchanged historical proposal; live adopted specification linked above |
| Runbook | `2026 Forms Runbook(20260926-185358).docx`, 154,420 bytes; extracted text has 113 pages | Read for section/field requirements; not proof of current kingdom policy |
| Exact workbook named by runbook | `SCA Exchequer Report - 2026-03 - With Data.xlsx`, 11,461,550 bytes; retrieved read-only | Structural reference acquired; owner must confirm official submission revision before P27-S2 |
| Other workbook candidates | Search also located a later branch-specific Q3 XLSM and several Q1 XLSX copies | Not substituted for the runbook's named workbook; no assertion that latest upload is official |
| Submission instructions | Named workbook's Instructions tab plus runbook close sections | Partial requirements available; current kingdom deadlines, required packet, signatures and accepted template remain owner/authority confirmation |
| Installed build / G14 | Owner reports missing usable event-name creation/tracking and Fund/Event tagging | Build/version, launch route, role, company, window size/scaling, saved layout, and screenshots not supplied; capture using user-testing checklist |
| Runtime | OpenJDK 17.0.20; no Maven executable or Maven wrapper found | `mvn clean verify` attempted and exited 127, `mvn: command not found`; no tests claimed |

Raw user workbooks and runbook documents are not committed into the public source repository. Preserve their original bytes; use synthetic fixtures for regression work.

### Workbook identity and structure

SHA-256: `f7596a2c699e79c95c919f7c6231856011e5f7378ebd0ff804b4ec7f017862c7`.

Read-only OOXML inspection found 20 visible tabs, 40 defined names, no VBA project and no external-link XML parts. This does not prove formulas recalculate correctly or that all relationships have been audited. No workbook edits, recalculation, or Excel desktop validation occurred.

Tabs, in order: Instructions, Summary, Exchequers, FinancialCommittee, Outstanding, AssetDetails, LiabilityDetails, Accounts, Assets&Inventory, Budget, Ledger, BalanceStmt, IncomeStmt, WorkbookTables, WorkbookSummary, Supplies, TransactionsList, AllChecksTfrs, FundTransfers, Notes.

The workbook Instructions tab explicitly requires event names (I24), resolution of error messages (C57–C60), and financial-report signatures by both Seneschal and Exchequer with kingdom guidance on signature method (D236–D238). Cells C135–D137 distinguish transfers from donations by legal organization, and B139 applies that distinction to NMR. B143–B144 describe a specific merchant/kingdom withholding arrangement. These are template statements to reconcile with applicable policy, not universal legal/tax conclusions. P25-S1/S4 and P27-S1 must resolve the legal-entity classification dependency before fixing mappings.

## Finding and inspection ledger

Paths below are relative to `src/main/java/org/nonprofitbookkeeping/` unless prefixed with `doc/` or `src/test/`. The “inspect before correction” column is mandatory scoped reading for the future owner, not a claim that every listed file was exhaustively reviewed in S1.

| ID / correction owner | S1 evidence and disposition | Inspect before correction / existing test starting point | Acceptance case |
|---|---|---|---|
| G1 / P23-S2 | `service/BudgetPlanService.java:variances` filters current ENTERED status; correction marks original REVERSED and creates inverse splits. Source-derived mismatch, not executed here | BudgetPlanService, TransactionCorrectionService, FinancialReportService, Txn/TxnSplit, FiscalPeriodRange; BudgetPlanServiceTest and FinancialReportServiceIntegrationTest | A01 |
| G2 / P23-S3 | `service/SupplementalOpenItemQueryService.java:query` filters current ENTERED status as well as cutoff. Existing reversal test queries after reversal, not before it. Source-derived historical defect | Query/entry/correction services, TxnSupplementalLine, V77 migration, Dashboard consumer; SupplementalOpenItemQueryServiceTest, SupplementalOpenItemLifecycleMigrationTest | A02 |
| G3 / P23-S4 | Entry validation checks supplied rows, per-split upper allocation and existing increase; no requirement that every control split be fully allocated, and no cumulative remaining-balance check in inspected path | TransactionEntryService persistSupplementalLines/requireItemIdentityConsistency; query service; transaction validation/import/generated-entry paths; TransactionEntryServiceTest | A03 |
| G4 / P23-S5, P27-S4 | `doc/P22-S5-supplemental-open-items-user-testing.md` explicitly documents lost lifecycle linkage in SCLX. Intentional prior exclusion becomes adopted future scope | `doc/data-exchange/sclx.md`, interchange/sclx snapshot/preview/commit/identity paths, V77; SclxSupplementalDetailQueryServiceTest and SclxImportCommitServiceTest | A04 |
| G5 / P27-S1–S3 | Report Library exports individual reports; guide identifies missing complete workbook/readiness. Acquired template has additional governance/control tabs | ReportDefinition, ReportLibraryPanel, export adapters, PeriodCloseRangeService; ReportExecutionServiceIntegrationTest; inspect every required template mapping | A05 |
| G6 / P24-S3 | `service/InventoryService.java:movementTransaction` supplies null Budget/Activity/Merchant on both generated lines. Source-confirmed missing attribution | Inventory command/preview/service, TxnSplit, EventAccountingQueryService, corrections/SCLX; InventoryMovementAccountingTest | A06 |
| G7 / P25-S1 | `report/SemanticAccountingReportQueryService.java:postedFundTransfers` reads explicit POSTED FundTransfer records; a Journal reallocation is not automatically such a record | FundTransfer model/constraints, FundAdminService, FundsPanel, transaction services and semantic query tests; existing bank-transfer authority | A07 |
| G8 / P24-S4 | Production route inventory exposes Funds/Activities but no dedicated category/payee/merchant maintenance destinations. Services/import choices are not operator maintenance | AppPanelId, PanelFactory, NavigationPane, reference-data/admin services and authoritative entity mappings; BudgetCategoryAdminServiceTest and authorization tests | A08 |
| G9 / P25-S2–S3 | Journal explicitly states check/reference fields are not saveable; memo is not structured payment lifecycle | JournalWorkspacePanel, TransactionCommand/View, Txn, bank statement/reference model, correction/reconciliation services; TransactionCorrectionReconciliationProtectionTest | A09 |
| G10 / P25-S4–S5 | Journal NMR boolean exists; independent functional classification/complete NMR contract remains guide gap. Runbook/template policy conflicts recorded below | TxnSplit, Journal editor, report mappings and generated-entry commands; confirm rates/classification policy before adding tests with policy assumptions | A10 |
| G11 / P26-S1–S2 | `FixedAssetService.create` persists register; acquisition accounting remains separate. Inspected FixedAsset fields omit detailed guardian/confirmation history | FixedAsset, InventoryItem, acquisition/lifecycle services, V55/V71 and later relevant migrations; FixedAssetServiceTest and lifecycle tests | A11 |
| G12 / P26-S3–S5 | Supplemental query accepts kind/as-of only; movement uses existing item value. Category-oriented budget/recognition/valuation limitations retained from guide | BudgetPlan/BudgetLine, BudgetEditorPanel, ReportDefinition, InventoryService, supplemental consumers; BudgetPlanServiceTest, InventoryMovementAccountingTest | A12 |
| G13 / P27-S3; conditional S5 | Root contract deliberately excludes attachments/approval queues/oversight roles. External references/attestations adopted; internal enforcement remains excluded | AGENTS.md, audit/authorization/period-close authorities; review external procedure with owner rather than relabel it an implemented approval system | A13 |
| G14 / P24-S1–S2 | NavigationPane/PanelFactory wire Activities and Event Accounting; Journal has Fund and Activity option columns. Owner's usability complaint remains open, not refuted by source | ActivitiesPanel, ActivityAdminService, reference data, Journal compliance/layout, EventAccountingPanel; ActivityAdminServiceTest and EventAccountingQueryServiceIntegrationTest plus owner desktop reproduction | A14 |

Preserve existing protections as positive baseline evidence. V77 requires the lifecycle triple together and positive linked amounts; entry checks reject mismatched subtype/direction and per-split excess. Event Accounting already reads canonical Activity-tagged splits. These capabilities must not be reimplemented through a parallel authority.

## Reproducible acceptance cases

Use an explicitly created disposable test database, company TEST and a second company OTHER. Use USD, January fiscal start initially, General and Restricted funds, an expense budget category, and two distinct Activities named Spring Event with different codes. Use ACCOUNTANT for ordinary writes and VIEWER for denial checks. Configure a BANK-function ASSET account for bank cases. Use actual stable IDs returned by setup; never assume production IDs or seed production with these fixtures. Amount comparisons are exact BigDecimal at canonical precision. Repeat date-sensitive tests with a non-January fiscal year and reversed/replacement/inactive historical dimensions as applicable.

| Case | Inputs and actions | Required result / execution owner |
|---|---|---|
| A01 | Feb 10: Dr expense 100 / Cr bank 100, same fund, expense category. Reverse Feb 20. Separate fixture reverses March 5. Third fixture replaces with 80 | Same-period actual 0; Feb cutoff still 100 before March reversal; March cumulative 0; replacement actual 80. GL/Income Statement agree with budget scope. P23-S2 extends BudgetPlanServiceTest; current tests include ordinary actuals/fiscal dates, not this reproducer |
| A02 | Jan 10: Dr receivable 100 / Cr income 100, item I INCREASE 100 on receivable split. Feb 10: Dr bank 25 / Cr receivable 25, I DECREASE 25. March 5 reverse settlement. Query Feb 28 before/after reversal, then March 31 | Feb 75 both times, March 100. Repeat all six kinds, opening reversal, replacement, company isolation. P23-S3 extends existing supplemental test; do not double-count invented reversal details |
| A03 | Open payable 100; try missing detail, 50 partial detail on 100 control split, applications of 60 + 60, and two concurrent applications of 60 | Missing/partial explained or rejected under adopted rule; no silent 120 settlement; atomic rollback on rejected command. Legacy deficits remain visible for reviewed repair. P23-S4 |
| A04 | Export/import A02 at partial settlement and after reversal, then repeat same import; also import legacy detail without lifecycle triple | Stable item/split/correction identities and same as-of results; no duplicate application; legacy is visibly unmatched. P23-S5, repeated for all new fields by P27-S4 |
| A05 | Assemble a quarter-end packet containing all applicable template tabs; deliberately omit one required source and leave one control difference | Export totals reconcile; omissions/differences block a complete-ready claim. Workbook opens in Excel without repair and preserves required formulas/validation; signatures remain external unless explicitly adopted otherwise. P27 |
| A06 | Receive stock, sell at event for 500, issue cost 200 with event/category/fund; reverse cost movement | Event gross 500, expense 200, net 300 before reversal; quantity/GL/tag effects reverse together. No untagged generated cost silently disappears. P24-S3 |
| A07 | Reallocate 100 General to eligible designated fund; separately transfer 100 checking to savings; separately send inter-entity payment | Internal fund/company totals and bank effects distinguish all three; operational transfer report ties to canonical transaction; invalid restrictions fail atomically. Entity classification decision required. P25-S1 |
| A08 | In a new company create category, payee, merchant during a draft Journal workflow; save, rename/deactivate, restart | Choice refresh preserves draft; saved IDs/history persist; VIEWER and cross-company writes rejected. P24-S4 |
| A09 | Issue check 000123, mark delivery, review as outstanding, void and replace by 000124; separately attempt correction after finalized reconciliation | Both references/search/history preserved; no duplicate expense; protections remain; stale treatment awaits policy. P25-S2/S3 |
| A10 | Policy-approved count/rate fixture with event NMR, merchant withholding, prior-year liability and later remittance; split expense between approved functional codes | Liability/cash/report presentation ties under signed-off policy; no double remittance; functional totals reconcile independently of event/fund. No current rate or legal classification assumed. P25-S4/S5 |
| A11 | Acquire equipment 1,000 with linked Journal; retry acceptance; record guardian, loan/return, confirmation and disposal; separately record expensed supplies | One acquisition only; cost/register tie; custody history retained; no recapitalization of expensed supplies. P26-S1/S2 |
| A12 | Two events/two funds budget; prepaid 1,200 over 12 months; stock 10 at 2 and 10 at 3 with partial issue | Scoped budgets/filters reconcile; recognition totals 1,200 and repeats are idempotent; stock result follows explicitly adopted cost method and ties to GL. P26-S3/S4/S5 |
| A13 | Record external receipt/approval/count references, leave one missing, export readiness, correct omission, reopen/reclose | Outstanding evidence stays visible; user attestations are identified as such; no claim of independent approval enforcement. P27-S3 with owner external-procedure acceptance |
| A14 | From actual launch, create named event, find by name, assign independent Fund/Event to Journal receipt/expense; save/reopen/restart/filter; test same names/different codes and mixed allocations | Owner can perform complete workflow with no SQL or hidden-code knowledge; 500 income and 200 expense yield 300 for selected event; old/inactive identities remain readable. P24-S1/S2; environment capture below is prerequisite evidence |

All A01–A14 execution results: **not executed in P23-S1**. Future owners must retain actual failures and exact test commits, not convert this table into checkmarks based on source presence.

P23-S2 subsequently reproduced A01 and added `BudgetReversalActualsTest`; see the [execution ledger](PLAN.md#p23-s2-implementation-and-validation) for failure and validation evidence and [manual acceptance](P23-S2-budget-reversal-user-testing.md) for owner checks. This does not change the historical S1 execution record or claim acceptance of A02–A14.

## Runbook action-to-test matrix

This is the baseline coverage map, including actions already supported. Section ranges group like actions; future implementation adds per-action test evidence where branches differ. Duplicate section 12 is identified by heading, not silently collapsed.

| Runbook action(s) | Program workflow | Cases / correction slices |
|---|---|---|
| §§0–2 context, setup, fields | Company/period, master data, Journal | A08, A14; P24; preserve existing account/fund setup |
| §§3, 5 ordinary spending/receipts, refunds and restricted purpose | Balanced Journal and classifications | A01, A08, A10, A14; P23-S2/P24/P25-S5; baseline ordinary-entry tests |
| §4 cash handling/gate float; §§16–21 and 23–24 bank timing, outstanding/reconciliation | Cash/advance entries, supplemental detail and reconciliation | A02/A03/A09/A13; P23/P25-S3/P27-S3; existing reconciliation tests retained |
| §6 merchant gross, fees, payout, dispute | Journal/receivable plus settlement assistant | A02/A14; P26-S6: gross 1,000, fee 30, payout 970 clears one receivable; repeated acceptance cannot double income |
| §7 transfers/custodial money; §28 funds/bank moves/closure | Fund service, canonical transfer, liability settlement | A07/A02/A13; P25-S1; company total unchanged for internal moves |
| §8 NMR and flow-through | Linked liability detail and adopted reporting policy | A10/A03; P25-S4; authority decision required |
| §9 and §§25–26 receivable/prepaid/deposit/deferred/payable carryover | Journal lifecycle, six reports, recognition/conversion | A02/A03/A04/A12; P23/P26-S4/S6; opening GL and item detail must not duplicate amounts |
| §10 and §27 stock, supplies, equipment, depreciation, removal/custody | Inventory and fixed-asset authorities | A06/A11/A12; P24-S3/P26; retain existing depreciation protections |
| §11 corrections/void/refund/stale/bank errors | Existing correction/reconciliation services, structured references | A01/A02/A09; P23/P25-S3; policy conflicts remain explicit |
| §12 close checklist; alternate §12 reconciliation guidance; §§13–14 troubleshooting/reporting | Reports, tie-outs, readiness, packet and protected close | A05/A13; P27/P28; every missing source remains visible |
| §15 worked scenarios | Combinations of preceding workflows | A01–A14; P28 runs each scenario's actual branch, not only a Trial Balance check |
| §22 | No substantive section established in supplied runbook | Document-owner clarification; no fabricated application requirement |

## Decision and evidence backlog

| ID | Required resolution | Owner / gating slice |
|---|---|---|
| D01 | Installed executable version/commit, launch route, effective role/company, display size/scaling and saved layout for G14; screenshots of missing controls | Ben Baron / before P24-S1 reproduction acceptance; does not block P23-S2 |
| D02 | Confirm which template/revision and packet/signatures/deadlines govern current submission; named reference acquired but not presumed authoritative | Owner with exchequer authority / before P27-S2; gather during P23 |
| D03 | Runbook §8 gross intake/remittance wording versus §26.3 liability treatment and workbook Instructions B139/B143–144; rates/exemptions/effective dates and legal-entity mapping | Accounting authority via owner / before P25-S1 entity mappings and P25-S4 implementation |
| D04 | Runbook §5.12 stale-check income versus §11.3 reversal of original expense; continuing legal obligation and reissue treatment | Accounting authority via owner / before P25-S3 stale-action implementation |
| D05 | §28 conflates Budget/Event and Fund creation despite describing distinct purposes; §§25–26 installment rows are workbook presentation, not new logical item identity | Program preserves independent Activity/Fund/Budget and stable item IDs; owner confirms form mapping before P26/P27; no duplicate master authorities |
| D06 | Required inventory cost method and opening historical basis | Owner/authority / before P26-S5; do not invent FIFO history |
| D07 | AR/OA/FR exact meanings, report basis and permitted defaults | Owner/authority / before P25-S5 |
| D08 | RESOLVED 2026-09-27: owner adopted the [S4 allocation policy](P23-S4-completeness-policy-proposal.md) | Full new-entry allocations; reject excess; explicitly acknowledged legacy SCLX exception with diagnostics and readiness blocking; no invented historical links or implicit credit workflow |
| D09 | Internal attachments and enforced approvals | Not adopted; P27-S5 remains conditional. External evidence baseline is adopted. Revisit only on explicit scope amendment |
| D10 | Older transaction-lifecycle/period-policy documents contain pre-P20 actor/reconciliation wording | Current P20 guards and reconciliation services win; affected future slice reconciles owning documentation before changing policy; do not restore obsolete authority |

Missing external facts are recorded dependencies, not fabricated completion. S1 delivers the inventory, collection procedure, and test specifications. It cannot certify the owner's installed desktop or the applicable accounting authority's decisions.

## P23-S2 handoff

After S1 is accepted and merged, start a fresh branch from then-current main. Read root AGENTS.md, PLAN.md, this contract, the P23-S2 section of the adopted program, and the transaction/period/report specifications. Inspect BudgetPlanService, TransactionCorrectionService, FinancialReportService, Txn/TxnSplit, FiscalPeriodRange and their tests. Reproduce A01 before changing queries. Confirm signed-income and expense conventions and historical cutoffs; do not fix G1 by indiscriminately deleting status predicates. Run focused tests then full verification. No S2 code is part of S1.

## Validation record

- Targeted source inspection confirmed the evidence described above; no new failing application test was executed.
- Attempted `mvn clean verify`: unavailable, exit 127. Java is installed; no Maven wrapper exists. CI and desktop results remain unverified for this slice.
- Required local documentation checks: all G1–G14/A01–A14 mappings, all adopted slices, local Markdown targets, unchanged archive, and `git diff --check`.
- Required owner review: [P23-S1 user testing](P23-S1-user-testing.md). No UI functionality is claimed changed by this slice.


## A02 execution follow-up

P23-S3 reproduced the current-status historical defect across all six supplemental kinds. See [S3 validation](PLAN.md#p23-s3-implementation-and-validation) for red/green evidence and [owner testing](P23-S3-historical-open-items-user-testing.md) for dated acceptance checks. The S1 execution record remains historical; this does not assert acceptance of other cases.
