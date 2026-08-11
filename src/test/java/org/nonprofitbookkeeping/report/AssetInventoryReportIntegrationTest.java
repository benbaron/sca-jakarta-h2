package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetInventoryReportIntegrationTest
{
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Test
    void domainReportsAreCompanyScopedAndReconcileLifecycleAndMovementFacts(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("asset-inventory-reports")))
        {
            Seed seed = seed(jpa);
            AssetInventoryReportQueryService service =
                    new AssetInventoryReportQueryService(jpa, () -> "SCA");

            AssetInventoryReportQueryService.FilterCatalog catalog = service.filterCatalog();
            assertEquals(1, catalog.assets().size());
            assertEquals(seed.assetId(), catalog.assets().get(0).id());
            assertEquals(1, catalog.inventoryItems().size());

            AssetInventoryReportQueryService.FixedAssetReportRequest assetRequest =
                    new AssetInventoryReportQueryService.FixedAssetReportRequest(
                            START, END, null, null, null, null, 100);
            AssetInventoryReportQueryService.FixedAssetRegisterResult register =
                    service.fixedAssetRegister(assetRequest);
            assertEquals(1, register.rows().size());
            assertEquals(FixedAsset.Status.ACTIVE, register.rows().get(0).status());
            assertMoney("1000.0000", register.domainGross());
            assertMoney("100.0000", register.domainContra());
            assertMoney("900.0000", register.domainNet());
            assertMoney("900.0000", register.ledgerNet());
            assertMoney("0.0000", register.difference());

            AssetInventoryReportQueryService.FixedAssetDepreciationResult depreciation =
                    service.fixedAssetDepreciation(assetRequest);
            assertTrue(depreciation.rows().stream()
                    .anyMatch(row -> row.rowType().equals("Completed depreciation")
                            && row.transactionId() != null));
            assertTrue(depreciation.rows().stream()
                    .anyMatch(row -> row.rowType().equals("Impairment")));
            assertTrue(depreciation.rows().stream()
                    .anyMatch(row -> row.rowType().equals("Impairment reversal")));
            assertTrue(depreciation.rows().stream()
                    .anyMatch(row -> row.rowType().equals("Schedule summary")
                            && row.transactionId() == null));
            assertMoney("0.0000", depreciation.difference());

            AssetInventoryReportQueryService.InventoryReportRequest inventoryRequest =
                    new AssetInventoryReportQueryService.InventoryReportRequest(
                            START, END, null, null, null, InventoryItem.Status.ACTIVE, 100);
            AssetInventoryReportQueryService.InventoryValuationResult valuation =
                    service.inventoryValuation(inventoryRequest);
            assertEquals(1, valuation.rows().size());
            assertMoney("8.0000", valuation.rows().get(0).quantity());
            assertMoney("40.0000", valuation.domainValue());
            assertMoney("50.0000", valuation.ledgerValue());
            assertMoney("-10.0000", valuation.difference());
            assertMoney("-10.0000", valuation.unlinkedMovementNet());
            assertNull(valuation.rows().get(0).transactionId());

            AssetInventoryReportQueryService.InventoryValuationResult beforeReceipt =
                    service.inventoryValuation(new AssetInventoryReportQueryService.InventoryReportRequest(
                            START,
                            LocalDate.of(2026, 1, 10),
                            null,
                            null,
                            seed.itemId(),
                            InventoryItem.Status.ACTIVE,
                            100));
            assertMoney("0.0000", beforeReceipt.rows().get(0).quantity());

            AssetInventoryReportQueryService.InventoryMovementResult movement =
                    service.inventoryMovementHistory(inventoryRequest);
            assertEquals(2, movement.rows().size());
            assertMoney("40.0000", movement.domainNet());
            assertMoney("50.0000", movement.ledgerActivity());
            assertMoney("-10.0000", movement.difference());
            assertMoney("-10.0000", movement.unlinkedMovementNet());
            assertTrue(movement.rows().stream().anyMatch(row ->
                    row.accountingState().startsWith("Nonfinancial")));

            AssetInventoryReportQueryService.InventoryMovementResult empty =
                    service.inventoryMovementHistory(
                            new AssetInventoryReportQueryService.InventoryReportRequest(
                                    LocalDate.of(2026, 4, 1),
                                    LocalDate.of(2026, 4, 30),
                                    seed.fundId(),
                                    seed.inventoryAccountId(),
                                    seed.itemId(),
                                    InventoryItem.Status.ACTIVE,
                                    100));
            assertTrue(empty.rows().isEmpty());
            assertMoney("0.0000", empty.domainNet());
            assertMoney("0.0000", empty.ledgerActivity());
            assertMoney("0.0000", empty.difference());
        }
    }

    @Test
    void typedFiltersAndSemanticPreviewExportUseTheSameResult(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("asset-inventory-parity")))
        {
            Seed seed = seed(jpa);
            AssetInventoryReportQueryService queries =
                    new AssetInventoryReportQueryService(jpa, () -> "SCA");
            FinancialReportDisplayFormat format = new FinancialReportDisplayFormat()
            {
                @Override
                public String formatDate(LocalDate value)
                {
                    return "DATE[" + value + "]";
                }

                @Override
                public String formatMoney(BigDecimal value)
                {
                    return "MONEY[" + value.toPlainString() + "]";
                }
            };
            ReportExecutionService execution = new ReportExecutionService(
                    new FinancialReportService(jpa),
                    format,
                    new SemanticAccountingReportQueryService(jpa, () -> "SCA"),
                    queries);
            ReportRequest request = new ReportRequest(
                    ReportDefinition.INVENTORY_MOVEMENT_HISTORY,
                    START,
                    END,
                    new ReportFundOption(seed.fundId(), "GEN", "General"),
                    100,
                    new ReportDomainFilter.InventorySelection(
                            seed.itemId(), seed.inventoryAccountId(), InventoryItem.Status.ACTIVE));

            ReportResult result = execution.execute(request);

            assertTrue(result.text().contains("Inventory Movement History"));
            assertTrue(result.text().contains("DATE[20"));
            assertTrue(result.text().contains("MONEY[-10"));
            assertTrue(result.text().contains("Nonfinancial / no canonical"));
            assertTrue(result.text().contains("-10.0000"));
            assertTrue(result.csv().contains("Signed Value"));
            assertTrue(result.csv().contains("Nonfinancial / no canonical transaction"));
            assertTrue(result.csv().contains("-10.0000"));
            assertFalse(result.csv().contains("MONEY["));
            assertEquals(request, result.request());
        }
    }

    private static Seed seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Scope sca = scope(em, "SCA", "SCA Branch", "1");
            Scope other = scope(em, "OTHER", "Other Branch", "2");

            FixedAsset asset = asset(sca, "Delivery Van");
            em.persist(asset);
            Txn acquisition = transaction(sca.company(), LocalDate.of(2026, 1, 2), "Asset acquisition");
            em.persist(acquisition);
            em.persist(split(acquisition, sca.assetAccount(), sca.fund(), "1000.0000"));

            Txn depreciationTxn = transaction(
                    sca.company(), LocalDate.of(2026, 2, 28), "Monthly depreciation");
            em.persist(depreciationTxn);
            em.persist(split(depreciationTxn, sca.accumulatedAccount(), sca.fund(), "100.0000"));
            FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
            run.setFixedAsset(asset);
            run.setRunDate(LocalDate.of(2026, 2, 28));
            run.setDepreciationAmount(new BigDecimal("100.0000"));
            run.setTransaction(depreciationTxn);
            run.setNotes("February depreciation");
            em.persist(run);

            Txn impairmentTxn = transaction(
                    sca.company(), LocalDate.of(2026, 3, 10), "Asset impairment");
            Txn impairmentReversal = transaction(
                    sca.company(), LocalDate.of(2026, 3, 20), "Reverse asset impairment");
            impairmentReversal.setReversalOf(impairmentTxn);
            em.persist(impairmentTxn);
            em.persist(impairmentReversal);
            em.persist(split(impairmentTxn, sca.accumulatedAccount(), sca.fund(), "50.0000"));
            em.persist(split(impairmentReversal, sca.accumulatedAccount(), sca.fund(), "-50.0000"));
            FixedAssetLifecycleEvent impairment = impairment(
                    asset, impairmentTxn, impairmentReversal);
            em.persist(impairment);

            InventoryItem item = item(sca, "Event T-shirts", new BigDecimal("8.0000"));
            em.persist(item);
            Txn receiptTxn = transaction(
                    sca.company(), LocalDate.of(2026, 1, 15), "Inventory receipt");
            em.persist(receiptTxn);
            em.persist(split(receiptTxn, sca.inventoryAccount(), sca.fund(), "50.0000"));
            em.persist(movement(item, LocalDate.of(2026, 1, 15),
                    InventoryMovement.MovementType.RECEIPT, "10", "10", receiptTxn));
            em.persist(movement(item, LocalDate.of(2026, 3, 1),
                    InventoryMovement.MovementType.ISSUE, "-2", "8", null));

            em.persist(asset(other, "Other Company Van"));
            em.persist(item(other, "Other Company Stock", new BigDecimal("3.0000")));

            em.getTransaction().commit();
            return new Seed(
                    sca.fund().getId(),
                    sca.inventoryAccount().getId(),
                    asset.getId(),
                    item.getId());
        }
    }

    private static Scope scope(EntityManager em, String code, String name, String suffix)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(name);
        em.persist(company);

        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName(name + " Chart");
        chart.setVersion("1");
        chart.setStatus(ChartStatus.ACTIVE);
        em.persist(chart);
        company.setActiveChartOfAccounts(chart);

        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GEN");
        fund.setName("General");
        fund.setFundType(FundType.UNRESTRICTED);
        em.persist(fund);

        Account asset = account(chart, "15" + suffix + "0", "Equipment",
                AccountType.ASSET, AccountSubtype.FIXED_ASSET, NormalBalance.DEBIT);
        Account accumulated = account(chart, "15" + suffix + "9", "Accumulated Depreciation",
                AccountType.ASSET, AccountSubtype.FIXED_ASSET, NormalBalance.CREDIT);
        Account expense = account(chart, "61" + suffix + "0", "Depreciation Expense",
                AccountType.EXPENSE, null, NormalBalance.DEBIT);
        Account inventory = account(chart, "13" + suffix + "0", "Inventory",
                AccountType.ASSET, AccountSubtype.INVENTORY, NormalBalance.DEBIT);
        em.persist(asset);
        em.persist(accumulated);
        em.persist(expense);
        em.persist(inventory);
        return new Scope(company, fund, asset, accumulated, expense, inventory);
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type,
            AccountSubtype subtype,
            NormalBalance normal)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setSubtype(subtype);
        account.setNormalBalance(normal);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }

    private static FixedAsset asset(Scope scope, String name)
    {
        FixedAsset asset = new FixedAsset();
        asset.setCompany(scope.company());
        asset.setAssetAccount(scope.assetAccount());
        asset.setAccumulatedDepreciationAccount(scope.accumulatedAccount());
        asset.setDepreciationExpenseAccount(scope.expenseAccount());
        asset.setFund(scope.fund());
        asset.setName(name);
        asset.setAcquisitionDate(START);
        asset.setAcquisitionCost(new BigDecimal("1000.0000"));
        asset.setSalvageValue(BigDecimal.ZERO);
        asset.setUsefulLifeMonths(60);
        asset.setDepreciationMethod(FixedAsset.DepreciationMethod.STRAIGHT_LINE);
        asset.setOpeningAccumulatedDepreciation(BigDecimal.ZERO);
        asset.setStatus(FixedAsset.Status.ACTIVE);
        return asset;
    }

    private static FixedAssetLifecycleEvent impairment(
            FixedAsset asset,
            Txn original,
            Txn reversal)
    {
        FixedAssetLifecycleEvent event = new FixedAssetLifecycleEvent();
        event.setFixedAsset(asset);
        event.setEventType(FixedAssetLifecycleEvent.EventType.IMPAIRMENT);
        event.setEventDate(original.getTxnDate());
        event.setAcquisitionCost(new BigDecimal("1000.0000"));
        event.setAccumulatedDepreciation(new BigDecimal("100.0000"));
        event.setAccumulatedImpairmentBefore(BigDecimal.ZERO);
        event.setCarryingAmountBefore(new BigDecimal("900.0000"));
        event.setProceeds(BigDecimal.ZERO);
        event.setImpairmentAmount(new BigDecimal("50.0000"));
        event.setGainAmount(BigDecimal.ZERO);
        event.setLossAmount(BigDecimal.ZERO);
        event.setTransaction(original);
        event.setAssetStatusBefore(FixedAsset.Status.ACTIVE);
        event.setAssetStatusAfter(FixedAsset.Status.ACTIVE);
        event.markReversed(reversal, Instant.now());
        return event;
    }

    private static InventoryItem item(Scope scope, String name, BigDecimal quantity)
    {
        InventoryItem item = new InventoryItem();
        item.setCompany(scope.company());
        item.setInventoryAccount(scope.inventoryAccount());
        item.setFund(scope.fund());
        item.setName(name);
        item.setItemType("Supplies");
        item.setQuantity(quantity);
        item.setUnit("each");
        item.setUnitValue(new BigDecimal("5.0000"));
        item.setAcquisitionDate(START);
        item.setCondition(InventoryItem.Condition.GOOD);
        item.setStatus(InventoryItem.Status.ACTIVE);
        return item;
    }

    private static InventoryMovement movement(
            InventoryItem item,
            LocalDate date,
            InventoryMovement.MovementType type,
            String change,
            String resulting,
            Txn txn)
    {
        InventoryMovement movement = new InventoryMovement();
        movement.setInventoryItem(item);
        movement.setMovementDate(date);
        movement.setMovementType(type);
        movement.setQuantityChange(new BigDecimal(change));
        movement.setResultingQuantity(new BigDecimal(resulting));
        movement.setUnitValue(new BigDecimal("5.0000"));
        movement.setTransaction(txn);
        movement.setNotes(type.name().toLowerCase());
        return movement;
    }

    private static Txn transaction(Company company, LocalDate date, String memo)
    {
        Txn txn = new Txn();
        txn.setCompany(company);
        txn.setTxnDate(date);
        txn.setMemo(memo);
        return txn;
    }

    private static TxnSplit split(
            Txn txn,
            Account account,
            Fund fund,
            String amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(txn);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(new BigDecimal(amount));
        return split;
    }

    private static void assertMoney(String expected, BigDecimal actual)
    {
        assertEquals(new BigDecimal(expected), actual);
    }

    private record Scope(
            Company company,
            Fund fund,
            Account assetAccount,
            Account accumulatedAccount,
            Account expenseAccount,
            Account inventoryAccount)
    {
    }

    private record Seed(Long fundId, Long inventoryAccountId, Long assetId, Long itemId)
    {
    }
}
