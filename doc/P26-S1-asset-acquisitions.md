# P26-S1 — Fixed-asset acquisition and opening links

Status DONE — owner accepted and merged [MR #379](https://github.com/benbaron/sca-jakarta-h2/pull/379) at `2de87dc3`; accepted head `b66e0ce1` passed full local and exact-head CI. [PLAN](PLAN.md) owns execution. This slice implements adopted G11/A11 acquisition scope; custody/forms, valuation and conversion remain in their owning later slices.

## Deliberate routes and authority

Keep one FixedAsset register and one canonical Txn/Split ledger. New Asset must deliberately select Opening / register only (no accounting), New bank purchase (balanced asset debit and BANK credit created atomically), or Link existing entry (claim validated existing asset debit without reposting). For payable invoices, checks/payment references, other offsets or richer allocations, enter their complete existing Journal accounting first and link it. No guessed opening GL, expense function, rate or policy is generated.

Nullable acquisition source and split reference on FixedAsset distinguish legacy unlinked history from an explicit register-only record or accepted acquisition. Claimed amount is the existing acquisitionCost, never another allocation balance. Multiple assets may share an invoice debit only while the sum of their accepted costs does not exceed that debit. Exact account, Fund, acquisition/transaction date, company, positive debit-normal ASSET/FIXED_ASSET amount, ordinary entered/non-reversal transaction and existing canonical protections apply. Existing register assets may be linked without recreating the asset or its accounting. No historical association is guessed or backfilled.

Preview is non-mutating; shared Save shows the proposal and explicit acceptance. One request UUID survives a draft failure/retry. Existing intrinsic asset/audit identities and a versioned immutable audit fingerprint acknowledge exact accepted requests without replaying accounting or overwriting subsequent edits; changed requests reject. Company locking and DB identities protect concurrency. Accepted register/accounting/link/audit facts commit or roll back together through FixedAssetService and TransactionEntryService.

Accepted links protect register basis and their source transaction against independent rewrites/deletion/reversal. A deliberate, audited Remove acquisition link leaves accounting unchanged and returns the asset to explicit register-only status; it requires an open, unreconciled source and no depreciation/lifecycle activity. Then ordinary Journal corrections apply once all claims are removed. Links supporting completed asset activity are retained; existing asset lifecycle corrections remain authoritative. No automatic disposal or deletion is invented.

## UI, reporting and interchange

Use the existing Asset Register editor and Save Asset/File Save. Explicit source/BANK/existing-line choices use stable IDs with readable labels, dirty retention, busy/permission guards and real Journal drill-through. Register rows expose source, transaction and claimed/remaining source amounts; legacy unlinked records remain visible. Preserve company money/date display without rounding four-decimal command facts, per-company table/divider state and actual two-axis reach at laptop/larger text. Retained assets continue to explain why Delete is unavailable.

Use a strict sparse version-1 `assetAcquisitions` SCLX application extension over intrinsic asset and transaction-line IDs. Preserve old fixedAssets version-1 schema and old identities when metadata is absent. Supported source/link changes participate in complete asset conflicts and explicit Keep target, with ownership/capacity revalidation against retained target accounting. Import/restart/exact retry and late rollback preserve acquisition facts and native receipt provenance. Legacy absence remains unlinked; no external consumer fidelity is claimed.

## Required verification and owner checks

Reproduce missing acquisition route with actual UI before repair. Test new purchase/register-only/link-existing/new and existing registers, shared-invoice 600 + 400 against 1,000 and excess/duplicate/concurrent rejection, exact precision, restart/native retry/changed input, foreign references/VIEWER, period/reconciliation and basis protection, source correction/unlink policy, dependency retention, late failure rollback and preserved depreciation/lifecycle links. Upgrade V85 with null legacy values and partial-DDL recovery; strict SCLX/conflict/Keep target/reimport/rollback; actual shared Save, dirty/busy capabilities, formatting, drill-through, table/divider/scroll geometry. Full clean verify and exact-head JDK 17 clean/repeat/production JavaFX are final gates.

Owner uses a disposable company: acquire equipment 1,000 by bank, Save/reopen/restart and confirm one balanced Journal entry and one linked register item; retry without duplication. Enter a shared invoice in Journal and link assets 600/400 without new accounting; reject an extra claim. Link an existing historical register, keep an opening record explicitly register-only, and inspect visible legacy/unlinked history. Check ordinary depreciation/disposal retains its acquisition link; verify protected/foreign/VIEWER changes reject, unlink/correction explanation and laptop/scaling/scroll behavior. No per-check human result is inferred from review/merge or automated tests.

## Executable verification map

| Behavior | Evidence |
|---|---|
| Exact bank purchase, GL/register tie, atomic audit and response-lost retry/restart | FixedAssetAcquisitionTest.purchaseIsAtomicPreciseAndRetryReturnsRetainedAssetAfterRestartAndMetadataEdit |
| Explicit openings and unchanged legacy provenance, including existing-register marking | FixedAssetAcquisitionTest.explicitRegisterOnlyAndLegacyRegistersCreateNoAccountingAndNoGuessedLinks |
| Shared-invoice capacity, stale/excess/foreign input and concurrency | FixedAssetAcquisitionTest.sharedInvoiceClaimsTieExactlyAndStaleExcessDuplicateAndForeignClaimsCannotWrite; concurrentSameRequestCreatesOneAssetAndPurchaseAndSharedSourceCannotBeOverclaimed |
| Existing register links, basis/classification/source correction protection, audited unlink | FixedAssetAcquisitionTest.existingRegisterLinksWithoutRepostingAndUnlinkRetainsAuditBeforeOrdinaryCorrection |
| Authorization, period/reconciliation and finalized BANK date range | FixedAssetAcquisitionTest.permissionClosedReconciledAndPrecisionGuardsRejectBeforeDurableChangesButExactAcknowledgementStillWorks; finalizedBankRangeBlocksFreshPurchaseAndStalePreviewWithoutPartialWrites |
| Late native rollback and dependency retention | FixedAssetAcquisitionTest.lateDomainAuditFailureRollsBackAssetTransactionSplitsAndReceiptBeforeCorrectedRetry; completedDepreciationAndDisposalPreserveAcquisitionAndRetainItsBasisAndSource |
| V85 upgrade/null legacy preservation, constraints/FK and partial-DDL recovery | AssetAcquisitionMigrationTest |
| Sparse round trip/restart/retry/completed depreciation/receipt provenance, malformed data, legacy absence, complete-asset Keep target, retained target capacity and late rollback | SclxAssetAcquisitionTest |
| Actual Save/preview/cancel/busy/draft/precision/live role/Journal routes and populated table/editor geometry | AssetAcquisitionPanelTest; AppPanelCommandCapabilityTest waits for initial asynchronous load |
| Existing as-of report and GL controls | AssetInventoryReportIntegrationTest plus exact purchase reconciliation assertion above |

During actual UI inspection, the existing register's PropertyValueFactory could not read FixedAssetView record accessors and rendered blank cells. This directly blocked review of acquired assets. S1 replaces those bindings with explicit projections and company date/money formatting; the populated real-UI test asserts cell values. Field labels also retain their natural widths with two-axis editor scrolling at narrow/larger-text sizes. These are bounded prerequisite repairs, not a new register/report design.

The CI production JavaFX selector now includes AssetAcquisitionPanelTest. Full local/CI outcomes and publication heads are recorded in PLAN.md. Owner checks above remain available; automated desktop execution is distinguished from a human per-check acceptance transcript.

Final local verification passed 999 tests with zero failures/errors and 62 headless display skips. The separate workflow-equivalent Xvfb suite passed 55 production JavaFX tests with zero failures/errors/skips. Exact published-head CI run `39189292452` passed, and the owner accepted/merged MR #379. No separate per-check human transcript is inferred.
